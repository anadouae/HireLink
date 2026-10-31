(function () {
  const params = new URLSearchParams(location.search);
  let mode = params.get("mode") === "login" ? "login" : "register";
  let role = params.get("role") === "RECRUTEUR" ? "RECRUTEUR" : "CANDIDAT";
  let pendingEmail = "";
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
    mfa: document.getElementById("form-mfa"),
    enroll: document.getElementById("form-enroll"),
    backup: document.getElementById("form-backup"),
    forgot: document.getElementById("form-forgot"),
    reset: document.getElementById("form-reset"),
  };
  const NO_GOOGLE = ["otp", "profile", "mfa", "enroll", "backup", "forgot", "reset"];
  const NO_PIPE = ["password", "profile", "mfa", "enroll", "backup", "forgot", "reset"];
  let mfaToken = "";
  let enrollToken = "";
  let pendingAuth = null;

  function showPanel(name) {
    Object.keys(panels).forEach((key) => {
      if (panels[key]) panels[key].classList.toggle("is-active", key === name);
    });
    if (googleBlock) googleBlock.hidden = NO_GOOGLE.indexOf(name) >= 0;
    if (pipe) pipe.classList.toggle("is-hidden", mode !== "register" || NO_PIPE.indexOf(name) >= 0);
    const card = document.querySelector(".auth-card");
    if (card) card.classList.toggle("is-wide", name === "profile");
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

  function applyMode() {
    const isReg = mode === "register";
    document.getElementById("tab-register").classList.toggle("is-active", isReg);
    document.getElementById("tab-login").classList.toggle("is-active", !isReg);
    if (pill) pill.classList.toggle("is-login", !isReg);
    if (isReg) {
      title.textContent = "Créer un compte";
      sub.textContent = "Avec Google, ou avec votre email puis un code.";
      setPipe(1);
      showPanel("email");
    } else {
      title.textContent = "Connexion";
      sub.textContent = "Email et mot de passe, ou Google.";
      showPanel("password");
    }
  }

  /** Retour vers la page demandée avant la connexion (même espace uniquement, chemin relatif sûr). */
  function nextTarget(authRole) {
    const n = params.get("next");
    if (!n || !/^[a-z0-9-]+\.html([?#].*)?$/i.test(n)) return null;
    const prefix = authRole === "RECRUTEUR" ? "recruiter" : authRole === "ADMINISTRATEUR" ? "admin" : "candidate";
    return n.indexOf(prefix) === 0 ? "app/" + n : null;
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
      const r = await HireLinkAPI.api("/auth/forgot-password", { method: "POST", body: { email: document.getElementById("forgot-email").value.trim() } });
      HireLink.toast("Demande envoyée", r.message, "ok");
    } catch (err) {
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
  if (passkeyBtn && window.HireLinkWebAuthn && HireLinkWebAuthn.supported()) {
    passkeyBtn.hidden = false;
    passkeyBtn.addEventListener("click", async () => {
      try {
        const opts = await HireLinkAPI.api("/auth/passkey/login/options", { method: "POST", body: {} });
        const credential = await HireLinkWebAuthn.get(opts);
        const auth = await HireLinkAPI.api("/auth/passkey/login/finish", { method: "POST", body: { requestId: opts.requestId, credential } });
        enterApp(auth);
      } catch (err) {
        HireLink.toast("Passkey", err.message || "Échec", "danger");
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
    pendingEmail = email;
    viaGoogle = false;
    try {
      await HireLinkAPI.api("/auth/request-otp", {
        method: "POST",
        body: { email, purpose: "REGISTER" },
      });
      document.getElementById("otp-hint").textContent = "Code envoyé à " + email + ".";
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
      const auth = await HireLinkAPI.api("/auth/verify-register-otp", {
        method: "POST",
        body: { email: pendingEmail, code, role: "CANDIDAT" },
      });
      afterAuth(auth);
    } catch (err) {
      HireLink.toast("Code invalide", err.message, "danger");
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
    try {
      const auth = await HireLinkAPI.api("/auth/complete-profile", {
        method: "POST",
        body: {
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
        },
      });
      enterApp(auth);
    } catch (err) {
      HireLink.toast("Profil", err.message, "danger");
    }
  });

  panels.password.addEventListener("submit", async (e) => {
    e.preventDefault();
    try {
      const auth = await HireLinkAPI.api("/auth/login", {
        method: "POST",
        body: {
          email: document.getElementById("email-pw").value.trim(),
          password: document.getElementById("password").value,
        },
      });
      afterAuth(auth, true);
    } catch (err) {
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
    const auth = await HireLinkAPI.api("/auth/google", {
      method: "POST",
      body: Object.assign({ idToken: idToken || null, role: null }, extra || {}),
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
          '<div class="field"><label>Email</label><input id="g-email" type="email" /></div>' +
          '<div class="field"><label>Nom</label><input id="g-name" /></div>' +
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
      } else {
        if (fallback) fallback.hidden = false;
      }
    })
    .catch(() => {
      const fallback = document.getElementById("btn-google");
      if (fallback) fallback.hidden = false;
    });

  function foldText(value) {
    return String(value || "").toLowerCase().normalize("NFD").replace(/[\u0300-\u036f]/g, "");
  }

  function paintCountries(q) {
    const menu = document.getElementById("phone-menu");
    const needle = foldText(q);
    if (!menu.dataset.built) {
      menu.dataset.built = "1";
      menu.innerHTML = '<input id="phone-filter" placeholder="Pays ou indicatif" />' + COUNTRIES.map(function (row, i) {
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
