(function () {
  const params = new URLSearchParams(location.search);
  let mode = params.get("mode") === "login" ? "login" : "register";
  let role = params.get("role") === "RECRUTEUR" ? "RECRUTEUR" : "CANDIDAT";
  let pendingEmail = "";
  let otpPurpose = "REGISTER";
  /** Panneau qui porte l'épreuve anti-robot du prochain envoi de code (email ou mot de passe). */
  let otpHostPanel = null;
  let viaGoogle = false;
  let profileStep = "role";
  const COUNTRIES = [
    ["🇲🇦", "Maroc", "212"], ["🇫🇷", "France", "33"], ["🇪🇸", "Espagne", "34"], ["🇵🇹", "Portugal", "351"],
    ["🇧🇪", "Belgique", "32"], ["🇬🇧", "Royaume-Uni", "44"], ["🇩🇪", "Allemagne", "49"], ["🇮🇹", "Italie", "39"],
    ["🇳🇱", "Pays-Bas", "31"], ["🇨🇭", "Suisse", "41"], ["🇨🇦", "Canada", "1"], ["🇺🇸", "États-Unis", "1"],
    ["🇦🇪", "Émirats arabes unis", "971"], ["🇹🇳", "Tunisie", "216"], ["🇩🇿", "Algérie", "213"],
    ["🇸🇳", "Sénégal", "221"], ["🇪🇬", "Égypte", "20"], ["🇨🇮", "Côte d'Ivoire", "225"], ["🇲🇱", "Mali", "223"],
    ["🇲🇷", "Mauritanie", "222"], ["🇱🇧", "Liban", "961"], ["🇹🇷", "Turquie", "90"], ["🇮🇳", "Inde", "91"],
    ["🇨🇳", "Chine", "86"], ["🇸🇦", "Arabie saoudite", "966"], ["🇶🇦", "Qatar", "974"]
  ];
  let phoneDial = "212";
  let phoneCountryName = "Maroc";
  const LEGAL_DRAFT = { termsVersion: "cgu:2026-11-12", privacyVersion: "privacy:2026-11-12" };
  let legalVersions = Object.assign({}, LEGAL_DRAFT);
  const CONSENT_STORE = "hirelink.auth.draft";

  const pill = document.getElementById("tabs-pill");
  const pipe = document.getElementById("pipe-steps");
  const title = document.getElementById("auth-title");
  const sub = document.getElementById("auth-sub");
  const googleBlock = document.getElementById("google-block");

  const panels = {
    email: document.getElementById("form-email"),
    otp: document.getElementById("form-otp"),
    profile: document.getElementById("form-profile"),
    password: document.getElementById("form-password"),
    passkey: document.getElementById("form-passkey"),
    mfa: document.getElementById("form-mfa"),
    enroll: document.getElementById("form-enroll"),
    backup: document.getElementById("form-backup"),
    forgot: document.getElementById("form-forgot"),
    reset: document.getElementById("form-reset"),
  };
  /* Aucun client Google ni mode démo : le bloc Google reste masqué. */
  let googleOff = false;
  const NO_GOOGLE = ["otp", "profile", "mfa", "enroll", "backup", "forgot", "reset", "passkey"];
  const NO_PIPE = ["password", "profile", "mfa", "enroll", "backup", "forgot", "reset", "passkey"];
  let mfaToken = "";
  let enrollToken = "";
  let pendingAuth = null;
  let resendTimer = null;

  /** Un code en cours d'attente rend le renvoi immédiat inutile : le délai est affiché plutôt que caché. */
  function startResendCooldown() {
    const btn = document.getElementById("btn-otp-resend");
    const clock = document.getElementById("otp-clock");
    if (!btn) return;
    let left = 60;
    if (resendTimer) clearInterval(resendTimer);
    btn.disabled = true;
    btn.textContent = "Renvoyer le code";
    const tick = () => {
      if (left <= 0) {
        clearInterval(resendTimer);
        resendTimer = null;
        btn.disabled = false;
        if (clock) { clock.hidden = true; clock.textContent = ""; }
        return;
      }
      if (clock) { clock.hidden = false; clock.textContent = "Un nouveau code pourra être demandé dans " + left + " s."; }
      left -= 1;
    };
    tick();
    resendTimer = setInterval(tick, 1000);
  }

  /* ------------------------------------------------------------ contrôle anti-robot */

  /**
   * Épreuve fabriquée par le serveur : image SVG de caractères brouillés, ou question de calcul en repli.
   * Aucune dépendance externe, donc la démonstration passe sans connexion ni clé à enregistrer.
   */
  function mountCaptcha(host) {
    if (!host) return null;
    if (host.__cap) return host.__cap;
    host.innerHTML =
      '<p class="cap-note">Contrôle anti-robot</p>' +
      '<div class="cap-challenge" aria-live="polite"></div>' +
      '<div class="cap-line">' +
      '<input class="cap-answer" type="text" maxlength="12" autocomplete="off" spellcheck="false" placeholder="Votre réponse" aria-label="Réponse au contrôle anti-robot" />' +
      '<button type="button" class="cap-refresh">Nouvelle question</button>' +
      '</div>';
    const challenge = host.querySelector(".cap-challenge");
    const answer = host.querySelector(".cap-answer");
    const cap = {
      draw: async function () {
        try {
          const c = await HireLinkAPI.api("/auth/captcha", { method: "POST", body: {} });
          host.__token = c.token;
          if (c.kind === "image") challenge.innerHTML = c.svg;
          else challenge.textContent = c.question;
          answer.value = "";
        } catch (err) {
          challenge.textContent = "Contrôle indisponible pour le moment.";
        }
      },
      show: function () {
        host.hidden = false;
        return host.__token ? Promise.resolve() : cap.draw();
      },
      hide: function () { host.hidden = true; host.__token = ""; challenge.innerHTML = ""; answer.value = ""; },
      value: function () { return { captchaToken: host.__token || "", captchaAnswer: answer.value.trim() }; },
    };
    host.querySelector(".cap-refresh").addEventListener("click", cap.draw);
    host.__cap = cap;
    return cap;
  }

  const captchaOf = (panel) => mountCaptcha(panel && panel.querySelector("[data-captcha]"));

  /** Le serveur répond 428 quand l'épreuve manque ou est ratée : on la remet en avant, neuve. */
  function isCaptchaChallenge(err) { return err && err.status === 428; }

  async function sendOtp(email, purpose) {
    const host = otpHostPanel || panels.email;
    const cap = captchaOf(host);
    try {
      await HireLinkAPI.api("/auth/request-otp", {
        method: "POST",
        body: Object.assign({ email, purpose }, cap.value()),
      });
    } catch (err) {
      if (isCaptchaChallenge(err)) {
        cap.hide();
        showPanel(host === panels.password ? "password" : "email");
        cap.show();
      }
      throw err;
    }
    document.getElementById("otp-hint").textContent =
      (purpose === "LOGIN" ? "Code de connexion envoyé à " : "Code envoyé à ") + email +
      ". Valable 15 minutes, vérifiez vos indésirables s’il n’arrive pas.";
    startResendCooldown();
    HireLink.toast("Code envoyé", email, "ok");
  }

  function showPanel(name) {
    Object.keys(panels).forEach((key) => {
      if (panels[key]) panels[key].classList.toggle("is-active", key === name);
    });
    if (name !== "otp" && resendTimer) { clearInterval(resendTimer); resendTimer = null; }
    if (googleBlock) googleBlock.hidden = googleOff || NO_GOOGLE.indexOf(name) >= 0;
    if (pipe) pipe.classList.toggle("is-hidden", mode !== "register" || NO_PIPE.indexOf(name) >= 0);
    const card = document.querySelector(".auth-card");
    if (card) card.classList.toggle("is-wide", name === "profile");
    // Envoi d'e-mail = cible d'abus : l'épreuve est exigée d'emblée sur ces deux écrans.
    if (name === "email" || name === "forgot") captchaOf(panels[name]).show();
  }

  function setPipe(n) {
    if (!pipe) return;
    pipe.querySelectorAll(".pipe-step").forEach((li) => {
      const s = Number(li.dataset.step);
      li.classList.toggle("is-active", s === n);
      li.classList.toggle("is-done", s < n);
    });
  }

  function fieldValue(id) {
    const el = document.getElementById(id);
    return el ? String(el.value || "").replace(/\s+/g, " ").trim() : "";
  }

  function filled(id) {
    return fieldValue(id).length > 0;
  }

  function selectedMissions() {
    return Array.from(document.querySelectorAll(".mission-card.is-on")).map((btn) => btn.dataset.contract);
  }

  function paintProfile() {
    const recruiter = role === "RECRUTEUR";
    const onSheet = profileStep !== "role";
    document.getElementById("profile-role").classList.toggle("is-hidden", onSheet);
    document.getElementById("profile-sheet").classList.toggle("is-hidden", !onSheet);
    document.getElementById("sheet-identity").classList.toggle("is-hidden", profileStep === "missions");
    document.getElementById("sheet-missions").classList.toggle("is-hidden", profileStep !== "missions");
    document.getElementById("fields-recruteur").classList.toggle("is-hidden", !recruiter);
    document.getElementById("field-nationality").classList.toggle("is-hidden", recruiter);
    document.getElementById("field-birth").classList.toggle("is-hidden", recruiter);
    document.getElementById("step-kicker").classList.toggle("is-hidden", !recruiter);
    document.getElementById("sheet-label").classList.toggle("is-hidden", !recruiter);
    document.getElementById("field-password").classList.toggle("is-hidden", viaGoogle || profileStep === "missions");

    if (!onSheet) {
      title.textContent = "Précisez votre profil";
      sub.textContent = "Le formulaire qui suit dépend de ce choix.";
      return;
    }
    if (profileStep === "missions") {
      title.textContent = "Types de mission proposés";
      sub.textContent = "Sélectionnez un ou plusieurs types de contrats que vous proposez.";
      document.getElementById("progress-label").textContent = "Sélection des missions";
      const n = selectedMissions().length;
      document.getElementById("progress-pct").textContent = n + (n > 1 ? " sélectionnés" : " sélectionné");
      document.getElementById("progress-bar").style.width = Math.min(100, Math.max(8, n * 18)) + "%";
      document.getElementById("btn-profile-save").textContent = "Suivant";
      return;
    }
    title.textContent = "Complétez votre profil";
    sub.textContent = recruiter
      ? "Ces informations nous aideront à personnaliser votre expérience."
      : "Ces informations nous aideront à vous proposer les meilleures opportunités.";
    document.getElementById("progress-label").textContent = "Progression du profil";
    const keys = recruiter
      ? ["reg-first", "reg-last", "reg-phone", "reg-city", "companyName", "reg-job"]
      : ["reg-first", "reg-last", "reg-phone", "reg-city", "reg-nationality", "reg-birth"];
    const pct = Math.round((keys.filter(filled).length / keys.length) * 100);
    document.getElementById("progress-pct").textContent = pct + "%";
    document.getElementById("progress-bar").style.width = pct + "%";
    document.getElementById("btn-profile-save").textContent = recruiter ? "Enregistrer" : "Accéder au dashboard";
  }

  function selectRole(r) {
    role = r;
    document.querySelectorAll(".role-option").forEach((btn) => {
      btn.classList.toggle("is-selected", btn.dataset.role === r);
    });
    if (profileStep !== "role") paintProfile();
  }

  function persistAuthDraft() {
    try {
      const draft = {
        email: (document.getElementById("email") || {}).value || pendingEmail || "",
        role,
        acceptTerms: !!(document.getElementById("accept-terms") || {}).checked
          || !!(document.getElementById("accept-terms-profile") || {}).checked,
        acceptPrivacy: !!(document.getElementById("accept-privacy") || {}).checked
          || !!(document.getElementById("accept-privacy-profile") || {}).checked,
        termsVersion: legalVersions.termsVersion,
        privacyVersion: legalVersions.privacyVersion,
      };
      sessionStorage.setItem(CONSENT_STORE, JSON.stringify(draft));
    } catch (_) { /* ignore quota */ }
  }

  function restoreAuthDraft() {
    try {
      const raw = sessionStorage.getItem(CONSENT_STORE);
      if (!raw) return;
      const draft = JSON.parse(raw);
      if (draft.email && document.getElementById("email") && !document.getElementById("email").value) {
        document.getElementById("email").value = draft.email;
      }
      ["accept-terms", "accept-terms-profile"].forEach((id) => {
        const el = document.getElementById(id);
        if (el) el.checked = !!draft.acceptTerms;
      });
      ["accept-privacy", "accept-privacy-profile"].forEach((id) => {
        const el = document.getElementById(id);
        if (el) el.checked = !!draft.acceptPrivacy;
      });
    } catch (_) { /* ignore */ }
  }

  function consentPayload(requireChecked) {
    const termsOk = !!(document.getElementById("accept-terms") || {}).checked
      || !!(document.getElementById("accept-terms-profile") || {}).checked;
    const privacyOk = !!(document.getElementById("accept-privacy") || {}).checked
      || !!(document.getElementById("accept-privacy-profile") || {}).checked;
    if (requireChecked && (!termsOk || !privacyOk)) {
      HireLink.toast("Accords requis", "Cochez les Conditions générales et la Politique de confidentialité.", "warn");
      return null;
    }
    if (!termsOk || !privacyOk) return null;
    return {
      acceptTermsVersion: legalVersions.termsVersion,
      acceptPrivacyVersion: legalVersions.privacyVersion,
    };
  }

  function wireConsentUi() {
    document.querySelectorAll(".auth-consent").forEach((row) => {
      const link = row.querySelector("a[data-legal]");
      if (link) {
        link.addEventListener("click", () => persistAuthDraft());
        link.addEventListener("keydown", (e) => {
          if (e.key === "Enter" || e.key === " ") persistAuthDraft();
        });
      }
      const toggleTip = () => {
        const open = row.classList.contains("is-open");
        document.querySelectorAll(".auth-consent.is-open").forEach((r) => r.classList.remove("is-open"));
        if (!open) row.classList.add("is-open");
      };
      row.addEventListener("click", (e) => {
        if (e.target.closest("a") || e.target.closest("input")) return;
        if (window.matchMedia("(hover: none)").matches) toggleTip();
      });
      row.addEventListener("focusin", () => row.classList.add("is-open"));
    });
    ["accept-terms", "accept-privacy", "accept-terms-profile", "accept-privacy-profile"].forEach((id) => {
      const el = document.getElementById(id);
      if (el) el.addEventListener("change", persistAuthDraft);
    });
  }

  async function loadLegalVersions() {
    try {
      const cfg = await HireLinkAPI.api("/auth/legal");
      if (cfg && cfg.termsVersion) legalVersions.termsVersion = cfg.termsVersion;
      if (cfg && cfg.privacyVersion) legalVersions.privacyVersion = cfg.privacyVersion;
    } catch (_) {
      legalVersions = Object.assign({}, LEGAL_DRAFT);
    }
  }

  function applyMode() {
    const isReg = mode === "register";
    document.getElementById("tab-register").classList.toggle("is-active", isReg);
    document.getElementById("tab-login").classList.toggle("is-active", !isReg);
    if (pill) pill.classList.toggle("is-login", !isReg);
    const consents = document.getElementById("reg-consents");
    if (consents) consents.hidden = !isReg;
    if (isReg) {
      title.textContent = "Créer un compte";
      sub.textContent = "Avec Google, ou avec votre email puis un code.";
      setPipe(1);
      showPanel("email");
    } else {
      title.textContent = "Connexion";
      sub.textContent = "Email et mot de passe, code par e-mail, ou Google.";
      showPanel("password");
    }
  }

  /** Retour vers la page demandée avant la connexion (même espace uniquement, chemin relatif sûr). */
  function nextTarget(authRole) {
    const n = params.get("next");
    if (!n || !/^[a-z0-9-]+\.html([?#].*)?$/i.test(n)) return null;
    const file = n.split(/[?#]/)[0].toLowerCase();
    const prefix = authRole === "RECRUTEUR" ? "recruiter" : authRole === "ADMINISTRATEUR" ? "admin" : "candidate";
    const shared = {
      RECRUTEUR: ["notifications.html", "security.html"],
      CANDIDAT: ["notifications.html", "security.html"],
      ADMINISTRATEUR: ["account.html", "security.html"],
    };
    const ok = file.indexOf(prefix) === 0 || (shared[authRole] || []).indexOf(file) >= 0;
    return ok ? "app/" + n : null;
  }

  if (params.get("expired") === "1") {
    setTimeout(() => HireLink.toast("Session expirée", "Reconnectez-vous pour continuer.", "warn"), 400);
  }

  function enterApp(auth) {
    HireLinkAPI.setAuth(auth);
    setTimeout(() => {
      location.href = nextTarget(auth.role) || HireLinkAPI.redirectForRole(auth.role);
    }, 280);
  }

  function openProfile(auth) {
    HireLinkAPI.setAuth(auth);
    if (auth.role === "RECRUTEUR" || auth.role === "CANDIDAT") selectRole(auth.role);
    const parts = (auth.fullName || "").trim().split(/\s+/).filter(Boolean);
    const first = document.getElementById("reg-first");
    const last = document.getElementById("reg-last");
    if (first && !first.value && parts[0] && parts[0].indexOf("@") === -1) first.value = parts[0];
    if (last && !last.value && parts.length > 1) last.value = parts.slice(1).join(" ");
    mode = "register";
    document.getElementById("tab-register").classList.add("is-active");
    document.getElementById("tab-login").classList.remove("is-active");
    if (pill) pill.classList.remove("is-login");
    profileStep = "role";
    setPipe(3);
    showPanel("profile");
    paintProfile();
  }

  /** Réponse d'authentification : défi TOTP, enrôlement obligatoire ou session ouverte. */
  function afterAuth(auth, viaProfile) {
    if (auth.mfaRequired && auth.mfaToken) {
      mfaToken = auth.mfaToken;
      mode = "login";
      title.textContent = "Double authentification";
      sub.textContent = "Un second facteur est demandé pour protéger ce compte.";
      showPanel("mfa");
      setTimeout(() => document.getElementById("mfa-code").focus(), 50);
      return;
    }
    if (auth.mfaEnrollRequired && auth.enrollToken) {
      startEnroll(auth.enrollToken);
      return;
    }
    if (viaProfile) enterApp(auth);
    else land(auth);
  }

  async function startEnroll(token) {
    enrollToken = token;
    try {
      const r = await HireLinkAPI.api("/auth/mfa/enroll/start", { method: "POST", body: { enrollToken: token } });
      document.getElementById("enroll-qr").src = r.qr;
      document.getElementById("enroll-secret").value = r.secret;
      title.textContent = "Activez la double authentification";
      sub.textContent = "Obligatoire pour ce compte.";
      showPanel("enroll");
      setTimeout(() => document.getElementById("enroll-code").focus(), 50);
    } catch (err) {
      HireLink.toast("Double authentification", err.message, "danger");
    }
  }

  function backToLogin() {
    mode = "login";
    history.replaceState(null, "", "auth.html?mode=login");
    mfaToken = "";
    enrollToken = "";
    applyMode();
  }
  document.querySelectorAll("[data-back-login]").forEach((b) => b.addEventListener("click", backToLogin));

  panels.mfa.addEventListener("submit", async (e) => {
    e.preventDefault();
    const code = document.getElementById("mfa-code").value.trim();
    try {
      const auth = await HireLinkAPI.api("/auth/mfa/verify", { method: "POST", body: { mfaToken, code } });
      document.getElementById("mfa-code").value = "";
      enterApp(auth);
    } catch (err) {
      HireLink.toast("Code refusé", err.message, "danger");
    }
  });

  panels.enroll.addEventListener("submit", async (e) => {
    e.preventDefault();
    const code = document.getElementById("enroll-code").value.trim();
    try {
      const auth = await HireLinkAPI.api("/auth/mfa/enroll/finish", { method: "POST", body: { enrollToken, code } });
      pendingAuth = auth;
      HireLinkAPI.setAuth(auth);
      const list = document.getElementById("backup-list");
      list.innerHTML = "";
      (auth.backupCodes || []).forEach((c) => {
        const li = document.createElement("li");
        li.textContent = c;
        list.appendChild(li);
      });
      title.textContent = "Codes de secours";
      sub.textContent = "À conserver en lieu sûr.";
      showPanel("backup");
    } catch (err) {
      HireLink.toast("Code refusé", err.message, "danger");
    }
  });

  document.getElementById("btn-backup-copy").addEventListener("click", async () => {
    const text = Array.from(document.querySelectorAll("#backup-list li")).map((li) => li.textContent).join("\n");
    try {
      await navigator.clipboard.writeText(text);
      HireLink.toast("Copié", "Codes de secours copiés.", "ok");
    } catch (_) {
      HireLink.toast("Copie impossible", "Sélectionnez les codes et copiez-les à la main.", "warn");
    }
  });
  panels.backup.addEventListener("submit", (e) => {
    e.preventDefault();
    if (pendingAuth) enterApp(pendingAuth);
  });

  document.getElementById("btn-forgot").addEventListener("click", () => {
    mode = "login";
    title.textContent = "Mot de passe oublié";
    sub.textContent = "Nous vous envoyons un lien de réinitialisation.";
    const typed = document.getElementById("email-pw").value.trim();
    if (typed) document.getElementById("forgot-email").value = typed;
    showPanel("forgot");
  });
  panels.forgot.addEventListener("submit", async (e) => {
    e.preventDefault();
    try {
      const r = await HireLinkAPI.api("/auth/forgot-password", { method: "POST", body: Object.assign({ email: document.getElementById("forgot-email").value.trim() }, captchaOf(panels.forgot).value()) });
      HireLink.toast("Demande envoyée", r.message, "ok");
    } catch (err) {
      if (isCaptchaChallenge(err)) { const cap = captchaOf(panels.forgot); cap.hide(); cap.show(); }
      HireLink.toast("Échec", err.message, "danger");
    }
  });
  panels.reset.addEventListener("submit", async (e) => {
    e.preventDefault();
    try {
      const r = await HireLinkAPI.api("/auth/reset-password", {
        method: "POST",
        body: { token: params.get("token") || "", newPassword: document.getElementById("reset-password").value },
      });
      HireLink.toast("Mot de passe modifié", r.message, "ok");
      history.replaceState(null, "", "auth.html?mode=login");
      document.getElementById("reset-password").value = "";
      backToLogin();
    } catch (err) {
      HireLink.toast("Réinitialisation impossible", err.message, "danger");
    }
  });

  const passkeyBtn = document.getElementById("btn-passkey");
  const pkFeedback = document.getElementById("pk-feedback");
  const pkEmailInput = document.getElementById("pk-email");
  const pkModeLogin = document.getElementById("pk-mode-login");
  const pkModeCreate = document.getElementById("pk-mode-create");
  const pkCreatePw = document.getElementById("pk-create-pw");

  function setPkFeedback(msg, tone) {
    if (!pkFeedback) return;
    if (!msg) { pkFeedback.hidden = true; pkFeedback.innerHTML = ""; return; }
    pkFeedback.className = "rc-banner " + (tone === "ok" ? "rc-banner--ok" : tone === "warn" ? "rc-banner--warn" : "rc-banner--danger");
    pkFeedback.innerHTML = "<div>" + msg + "</div>";
    pkFeedback.hidden = false;
  }

  function showPasskeyPanel() {
    title.textContent = "Connexion par Passkey";
    sub.textContent = "Empreinte, reconnaissance faciale ou code de l’appareil.";
    const pwEmail = (document.getElementById("email-pw") || {}).value || "";
    if (pkEmailInput && pwEmail && !pkEmailInput.value) pkEmailInput.value = pwEmail.trim();
    if (pkModeLogin) pkModeLogin.classList.remove("is-hidden");
    if (pkModeCreate) pkModeCreate.classList.add("is-hidden");
    setPkFeedback(null);
    showPanel("passkey");
    if (pkEmailInput) setTimeout(() => pkEmailInput.focus(), 100);
  }

  if (passkeyBtn && window.HireLinkWebAuthn && HireLinkWebAuthn.supported()) {
    passkeyBtn.hidden = false;
    passkeyBtn.addEventListener("click", showPasskeyPanel);
  }

  const btnPkRun = document.getElementById("btn-pk-run");
  if (btnPkRun) {
    btnPkRun.addEventListener("click", async () => {
      const email = (pkEmailInput ? pkEmailInput.value : "").trim();
      setPkFeedback(null);
      btnPkRun.disabled = true;
      try {
        if (email) {
          const st = await HireLinkAPI.api("/auth/passkey/status", { method: "POST", body: { email } });
          if (!st.hasPasskey) {
            setPkFeedback("<strong>Aucune passkey enregistrée</strong> pour ce compte sur cet appareil. Vous pouvez la créer immédiatement ci-dessous.", "warn");
            if (pkModeCreate) pkModeCreate.classList.remove("is-hidden");
            if (pkModeLogin) pkModeLogin.classList.add("is-hidden");
            if (pkCreatePw) setTimeout(() => pkCreatePw.focus(), 150);
            return;
          }
        }
        const opts = await HireLinkAPI.api("/auth/passkey/login/options", { method: "POST", body: email ? { email } : {} });
        const credential = await HireLinkWebAuthn.get(opts);
        const auth = await HireLinkAPI.api("/auth/passkey/login/finish", { method: "POST", body: { requestId: opts.requestId, credential } });
        setPkFeedback("<strong>Authentification réussie !</strong> Connexion en cours…", "ok");
        setTimeout(() => enterApp(auth), 400);
      } catch (err) {
        setPkFeedback("<strong>Échec de la passkey :</strong> " + (err.message || "Opération annulée ou refusée."), "danger");
      } finally {
        btnPkRun.disabled = false;
      }
    });
  }

  const btnPkSwitchCreate = document.getElementById("btn-pk-switch-create");
  if (btnPkSwitchCreate) {
    btnPkSwitchCreate.addEventListener("click", () => {
      setPkFeedback(null);
      if (pkModeCreate) pkModeCreate.classList.remove("is-hidden");
      if (pkModeLogin) pkModeLogin.classList.add("is-hidden");
      if (pkCreatePw) setTimeout(() => pkCreatePw.focus(), 100);
    });
  }

  const btnPkSwitchLogin = document.getElementById("btn-pk-switch-login");
  if (btnPkSwitchLogin) {
    btnPkSwitchLogin.addEventListener("click", () => {
      setPkFeedback(null);
      if (pkModeLogin) pkModeLogin.classList.remove("is-hidden");
      if (pkModeCreate) pkModeCreate.classList.add("is-hidden");
    });
  }

  const btnPkDoCreate = document.getElementById("btn-pk-do-create");
  if (btnPkDoCreate) {
    btnPkDoCreate.addEventListener("click", async () => {
      const email = (pkEmailInput ? pkEmailInput.value : "").trim();
      const pw = (pkCreatePw ? pkCreatePw.value : "").trim();
      if (!email || !email.includes("@")) {
        setPkFeedback("Saisissez l’adresse e-mail de votre compte.", "warn");
        if (pkEmailInput) pkEmailInput.focus();
        return;
      }
      if (!pw) {
        setPkFeedback("Saisissez votre mot de passe pour autoriser l'enregistrement de la passkey.", "warn");
        if (pkCreatePw) pkCreatePw.focus();
        return;
      }
      btnPkDoCreate.disabled = true;
      setPkFeedback("Préparation de l'enregistrement biométrique…", "ok");
      try {
        const opts = await HireLinkAPI.api("/auth/passkey/enroll/options", { method: "POST", body: { email, password: pw } });
        const credential = await HireLinkWebAuthn.create(opts);
        const auth = await HireLinkAPI.api("/auth/passkey/enroll/finish", {
          method: "POST",
          body: { email, requestId: opts.requestId, credential, name: "Passkey principale" }
        });
        setPkFeedback("<strong>Passkey enregistrée avec succès !</strong> Connexion en cours…", "ok");
        setTimeout(() => enterApp(auth), 600);
      } catch (err) {
        setPkFeedback("<strong>Création impossible :</strong> " + (err.message || "Erreur de configuration biométrique"), "danger");
      } finally {
        btnPkDoCreate.disabled = false;
      }
    });
  }

  function land(auth) {
    if (!auth.profileComplete) {
      openProfile(auth);
      return;
    }
    enterApp(auth);
  }

  document.querySelectorAll(".auth-tab").forEach((tab) => {
    tab.addEventListener("click", () => {
      mode = tab.dataset.mode;
      history.replaceState(null, "", "auth.html?mode=" + mode);
      applyMode();
    });
  });

  document.querySelectorAll(".role-option").forEach((btn) => {
    btn.addEventListener("click", () => selectRole(btn.dataset.role));
  });

  document.getElementById("btn-role-continue").addEventListener("click", () => {
    profileStep = "identity";
    paintProfile();
  });

  document.getElementById("btn-profile-back").addEventListener("click", () => {
    profileStep = profileStep === "missions" ? "identity" : "role";
    paintProfile();
  });

  document.querySelectorAll(".mission-card").forEach((btn) => {
    btn.addEventListener("click", () => {
      btn.classList.toggle("is-on");
      paintProfile();
    });
  });

  panels.profile.addEventListener("input", () => {
    if (profileStep === "identity") paintProfile();
  });

  document.getElementById("btn-back-email").addEventListener("click", () => {
    if (otpPurpose === "LOGIN") {
      document.getElementById("email-pw").value = pendingEmail;
      otpPurpose = "REGISTER";
      otpHostPanel = panels.password;
      applyMode();
      return;
    }
    setPipe(1);
    title.textContent = "Créer un compte";
    sub.textContent = "Avec Google, ou avec votre email puis un code.";
    showPanel("email");
  });

  panels.email.addEventListener("submit", async (e) => {
    e.preventDefault();
    const email = document.getElementById("email").value.trim();
    if (!email) {
      HireLink.toast("Email requis", "Indiquez une adresse valide.", "warn");
      return;
    }
    if (mode === "register" && !consentPayload(true)) return;
    pendingEmail = email;
    otpPurpose = "REGISTER";
    otpHostPanel = panels.email;
    viaGoogle = false;
    persistAuthDraft();
    try {
      await sendOtp(email, "REGISTER");
      setPipe(2);
      title.textContent = "Vérification";
      sub.textContent = "Saisissez le code reçu par email.";
      showPanel("otp");
      HireLink.toast("Code envoyé", email, "ok");
    } catch (err) {
      HireLink.toast("Échec", err.message, "danger");
    }
  });

  panels.otp.addEventListener("submit", async (e) => {
    e.preventDefault();
    const code = document.getElementById("otp").value.trim();
    try {
      let body;
      if (otpPurpose === "LOGIN") {
        body = { email: pendingEmail, code };
      } else {
        const consents = consentPayload(true);
        if (!consents) return;
        body = Object.assign({ email: pendingEmail, code, role: "CANDIDAT" }, consents);
      }
      const auth = await HireLinkAPI.api(otpPurpose === "LOGIN" ? "/auth/verify-login-otp" : "/auth/verify-register-otp", {
        method: "POST",
        body,
      });
      afterAuth(auth);
    } catch (err) {
      HireLink.toast("Code invalide", err.message, "danger");
    }
  });

  document.getElementById("btn-login-otp").addEventListener("click", async () => {
    const email = document.getElementById("email-pw").value.trim();
    if (!email) {
      HireLink.toast("Email requis", "Indiquez votre adresse avant de demander un code.", "warn");
      document.getElementById("email-pw").focus();
      return;
    }
    pendingEmail = email;
    otpPurpose = "LOGIN";
    otpHostPanel = panels.password;
    try {
      await sendOtp(email, "LOGIN");
      title.textContent = "Connexion par code";
      sub.textContent = "Saisissez le code reçu par e-mail.";
      showPanel("otp");
    } catch (err) {
      if (isCaptchaChallenge(err)) {
        HireLink.toast("Contrôle anti-robot", "Répondez à la question affichée, puis relancez l’envoi du code.", "warn");
        return;
      }
      HireLink.toast("Échec", err.message, "danger");
    }
  });

  document.getElementById("btn-otp-resend").addEventListener("click", async () => {
    if (!pendingEmail) return;
    const btn = document.getElementById("btn-otp-resend");
    btn.disabled = true;
    try {
      await sendOtp(pendingEmail, otpPurpose);
      const field = document.getElementById("otp");
      field.value = "";
      field.focus();
    } catch (err) {
      btn.disabled = false;
      HireLink.toast("Échec", err.message, "danger");
    }
  });

  function identityReady() {
    const need = ["reg-first", "reg-last", "reg-phone", "reg-city"];
    if (role === "CANDIDAT") need.push("reg-nationality", "reg-birth");
    else need.push("companyName", "reg-job");
    if (need.some((id) => !filled(id))) {
      HireLink.toast("Profil incomplet", "Les champs marqués d'une étoile sont requis.", "warn");
      return false;
    }
    const digits = document.getElementById("reg-phone").value.replace(/\D/g, "").replace(/^0/, "");
    if (digits.length < 6 || digits.length > 12) {
      HireLink.toast("Téléphone", "Indiquez le numéro sans l'indicatif, par exemple 6 12 34 56 78.", "warn");
      return false;
    }
    if (!viaGoogle && document.getElementById("reg-password").value.length < 12) {
      HireLink.toast("Mot de passe", "12 caractères minimum : une phrase de passe convient très bien (ex. Atlas-Vert-Soleil-2026).", "warn");
      return false;
    }
    return true;
  }

  panels.profile.addEventListener("submit", async (e) => {
    e.preventDefault();
    if (profileStep === "role") return;
    if (profileStep === "identity") {
      if (!identityReady()) return;
      if (role === "RECRUTEUR") {
        profileStep = "missions";
        paintProfile();
        return;
      }
    }
    if (role === "RECRUTEUR" && selectedMissions().length === 0) {
      HireLink.toast("Missions", "Choisissez au moins un type de contrat.", "warn");
      return;
    }
    const firstName = fieldValue("reg-first");
    const lastName = fieldValue("reg-last");
    if (!firstName || !lastName) {
      HireLink.toast("Profil", "Écrivez le prénom dans le champ Prénom, et le nom dans le champ Nom.", "warn");
      return;
    }
    const localPhone = document.getElementById("reg-phone").value.replace(/\D/g, "").replace(/^0/, "");
    const consents = consentPayload(true);
    if (!consents) return;
    try {
      const auth = await HireLinkAPI.api("/auth/complete-profile", {
        method: "POST",
        body: Object.assign({
          role,
          firstName,
          lastName,
          fullName: firstName + " " + lastName,
          phone: "+" + phoneDial + localPhone,
          phoneCountry: phoneCountryName,
          city: fieldValue("reg-city"),
          nationality: role === "CANDIDAT" ? fieldValue("reg-nationality") : null,
          birthDate: role === "CANDIDAT" ? document.getElementById("reg-birth").value : null,
          jobTitle: role === "RECRUTEUR" ? fieldValue("reg-job") : null,
          company: role === "RECRUTEUR" ? fieldValue("companyName") : null,
          contracts: role === "RECRUTEUR" ? selectedMissions().join(", ") : null,
          password: viaGoogle ? null : document.getElementById("reg-password").value,
        }, consents),
      });
      try { sessionStorage.removeItem(CONSENT_STORE); } catch (_) {}
      enterApp(auth);
    } catch (err) {
      HireLink.toast("Profil", err.message, "danger");
    }
  });

  panels.password.addEventListener("submit", async (e) => {
    e.preventDefault();
    const pwEmail = document.getElementById("email-pw").value.trim();
    if (!pwEmail || !document.getElementById("password").value) {
      HireLink.toast("Champs requis", "Indiquez votre e-mail et votre mot de passe.", "warn");
      (pwEmail ? document.getElementById("password") : document.getElementById("email-pw")).focus();
      return;
    }
    try {
      const auth = await HireLinkAPI.api("/auth/login", {
        method: "POST",
        body: Object.assign({
          email: document.getElementById("email-pw").value.trim(),
          password: document.getElementById("password").value,
        }, captchaOf(panels.password).value()),
      });
      afterAuth(auth, true);
    } catch (err) {
      if (isCaptchaChallenge(err)) await captchaOf(panels.password).show();
      HireLink.toast("Connexion refusée", err.message, "danger");
    }
  });

  function loadGoogleScript() {
    return new Promise((resolve, reject) => {
      if (window.google && google.accounts && google.accounts.id) {
        resolve();
        return;
      }
      const existing = document.querySelector("script[data-gsi]");
      if (existing) {
        existing.addEventListener("load", () => resolve());
        existing.addEventListener("error", () => reject(new Error("Script Google indisponible")));
        return;
      }
      const s = document.createElement("script");
      s.src = "https://accounts.google.com/gsi/client";
      s.async = true;
      s.defer = true;
      s.dataset.gsi = "1";
      s.onload = () => resolve();
      s.onerror = () => reject(new Error("Impossible de charger Google Identity"));
      document.head.appendChild(s);
    });
  }

  async function finishGoogle(idToken, extra) {
    viaGoogle = true;
    const body = Object.assign({ idToken: idToken || null, role: null }, extra || {});
    if (mode === "register") {
      const consents = consentPayload(true);
      if (!consents) throw new Error("Cochez les accords avant de continuer avec Google.");
      Object.assign(body, consents);
    }
    const auth = await HireLinkAPI.api("/auth/google", {
      method: "POST",
      body,
    });
    afterAuth(auth);
  }

  async function mountGoogleButton(clientId) {
    const host = document.getElementById("google-btn-host");
    const fallback = document.getElementById("btn-google");
    if (!host) return;
    await loadGoogleScript();
    host.hidden = false;
    if (fallback) fallback.hidden = true;
    host.innerHTML = "";
    google.accounts.id.initialize({
      client_id: clientId,
      callback: async (response) => {
        try {
          if (!response || !response.credential) {
            HireLink.toast("Google", "Jeton manquant", "danger");
            return;
          }
          await finishGoogle(response.credential);
        } catch (err) {
          HireLink.toast("Google", err.message || "Échec", "danger");
        }
      },
      auto_select: false,
      cancel_on_tap_outside: true,
    });
    const width = Math.min(360, Math.max(280, host.clientWidth || 320));
    google.accounts.id.renderButton(host, {
      type: "standard",
      theme: "outline",
      size: "large",
      text: mode === "register" ? "signup_with" : "signin_with",
      shape: "pill",
      width,
      logo_alignment: "left",
    });
  }

  document.getElementById("btn-google").addEventListener("click", async () => {
    try {
      const ok = await HireLink.openModal({
        title: "Continuer avec Google",
        description: "Indiquez le compte Google à utiliser.",
        bodyHtml:
          '<div class="stack">' +
          '<div class="field"><label for="g-email">Email</label><input id="g-email" type="email" /></div>' +
          '<div class="field"><label for="g-name">Nom</label><input id="g-name" /></div>' +
          "</div>",
        confirmLabel: "Continuer",
        large: true,
      });
      if (!ok) return;
      await finishGoogle(null, {
        devEmail: (document.getElementById("g-email") || {}).value,
        devName: (document.getElementById("g-name") || {}).value,
      });
    } catch (err) {
      HireLink.toast("Google", err.message || "Échec", "danger");
    }
  });

  HireLinkAPI.api("/auth/config")
    .then(async (cfg) => {
      const hint = document.getElementById("google-hint");
      const fallback = document.getElementById("btn-google");
      if (hint) hint.textContent = "";
      if (cfg.googleConfigured && cfg.googleClientId) {
        try {
          await mountGoogleButton(cfg.googleClientId);
        } catch (err) {
          if (hint) hint.textContent = "Google temporairement indisponible.";
          if (fallback) fallback.hidden = false;
        }
      } else if (cfg.googleDevBypass) {
        if (fallback) fallback.hidden = false;
      } else {
        googleOff = true;
        if (googleBlock) googleBlock.hidden = true;
      }
    })
    .catch(() => {
      googleOff = true;
      if (googleBlock) googleBlock.hidden = true;
    });

  function foldText(value) {
    return String(value || "").toLowerCase().normalize("NFD").replace(/[\u0300-\u036f]/g, "");
  }

  function paintCountries(q) {
    const menu = document.getElementById("phone-menu");
    const needle = foldText(q);
    if (!menu.dataset.built) {
      menu.dataset.built = "1";
      menu.innerHTML = '<input id="phone-filter" aria-label="Filtrer les pays" placeholder="Pays ou indicatif" />' + COUNTRIES.map(function (row, i) {
        return '<button type="button" class="phone-opt" data-i="' + i + '"><span>' + row[0] + '</span><span>' + row[1] + '</span><small>+' + row[2] + '</small></button>';
      }).join("");
      const filter = menu.querySelector("#phone-filter");
      filter.addEventListener("input", function () { paintCountries(filter.value); });
      filter.addEventListener("click", function (e) { e.stopPropagation(); });
      menu.addEventListener("click", function (e) { e.stopPropagation(); });
      menu.querySelectorAll(".phone-opt").forEach(function (btn) {
        btn.addEventListener("click", function (e) {
          e.preventDefault();
          const row = COUNTRIES[Number(btn.dataset.i)];
          phoneDial = row[2];
          phoneCountryName = row[1];
          document.getElementById("phone-flag").textContent = row[0];
          document.getElementById("phone-code").textContent = "+" + phoneDial;
          menu.hidden = true;
          document.getElementById("phone-country").setAttribute("aria-expanded", "false");
          document.getElementById("reg-phone").focus();
        });
      });
    }
    menu.querySelectorAll(".phone-opt").forEach(function (btn) {
      const row = COUNTRIES[Number(btn.dataset.i)];
      const blob = foldText(row[1] + " " + row[2]);
      btn.hidden = !!(needle && blob.indexOf(needle) < 0);
      btn.classList.toggle("is-on", row[2] === phoneDial && row[1] === phoneCountryName);
    });
  }

  document.getElementById("phone-country").addEventListener("click", function (e) {
    e.preventDefault();
    e.stopPropagation();
    const menu = document.getElementById("phone-menu");
    const open = menu.hidden;
    menu.hidden = !open;
    document.getElementById("phone-country").setAttribute("aria-expanded", open ? "true" : "false");
    if (open) {
      paintCountries("");
      const filter = document.getElementById("phone-filter");
      if (filter) { filter.value = ""; filter.focus(); }
    }
  });
  document.addEventListener("click", function () {
    const menu = document.getElementById("phone-menu");
    if (menu) menu.hidden = true;
  });

  selectRole(role);
  wireConsentUi();
  restoreAuthDraft();
  loadLegalVersions();
  if (params.get("mode") === "reset" && params.get("token")) {
    mode = "login";
    document.getElementById("tab-login").classList.add("is-active");
    document.getElementById("tab-register").classList.remove("is-active");
    if (pill) pill.classList.add("is-login");
    title.textContent = "Nouveau mot de passe";
    sub.textContent = "Choisissez un mot de passe que vous n’utilisez nulle part ailleurs.";
    showPanel("reset");
  } else {
    applyMode();
  }
})();
