(function () {
  const params = new URLSearchParams(location.search);
  let mode = params.get("mode") === "login" ? "login" : "register";
  let role = params.get("role") === "RECRUTEUR" ? "RECRUTEUR" : "CANDIDAT";
  let pendingEmail = "";
  let authConfig = { googleConfigured: false, googleDevBypass: true };
  /** register: role | email | otp — login: password | email | otp */
  let panel = "role";
  if (params.get("ref")) {
    try {
      localStorage.setItem("hirelink.referral.code", params.get("ref"));
    } catch (_) {}
  }

  const pill = document.getElementById("tabs-pill");
  const pipe = document.getElementById("pipe-steps");
  const title = document.getElementById("auth-title");
  const sub = document.getElementById("auth-sub");
  const fieldCompany = document.getElementById("field-company");
  const fieldIce = document.getElementById("field-ice");

  const panels = {
    role: document.getElementById("step-role"),
    email: document.getElementById("form-email"),
    otp: document.getElementById("form-otp"),
    password: document.getElementById("form-password"),
  };

  function showPanel(name) {
    panel = name;
    Object.keys(panels).forEach((key) => {
      panels[key].classList.toggle("is-active", key === name);
    });
  }

  function setPipe(n) {
    if (!pipe) return;
    pipe.querySelectorAll(".pipe-step").forEach((li) => {
      const s = Number(li.dataset.step);
      li.classList.toggle("is-active", s === n);
      li.classList.toggle("is-done", s < n);
    });
  }

  function syncRecruiterFields() {
    const show = mode === "register" && role === "RECRUTEUR" && panel === "email";
    fieldCompany.classList.toggle("is-hidden", !show);
    fieldIce.classList.toggle("is-hidden", !show);
    document.getElementById("email-label").textContent =
      role === "RECRUTEUR" && mode === "register" ? "Email professionnel" : "Email";
    document.getElementById("email").placeholder =
      role === "RECRUTEUR" && mode === "register" ? "vous@entreprise.ma" : "vous@exemple.ma";
  }

  function selectRole(r) {
    role = r;
    document.querySelectorAll(".role-option").forEach((btn) => {
      btn.classList.toggle("is-selected", btn.dataset.role === r);
    });
    syncRecruiterFields();
  }

  function applyMode() {
    const isReg = mode === "register";
    document.getElementById("tab-register").classList.toggle("is-active", isReg);
    document.getElementById("tab-login").classList.toggle("is-active", !isReg);
    if (pill) pill.classList.toggle("is-login", !isReg);
    if (pipe) pipe.classList.toggle("is-hidden", !isReg);

    if (isReg) {
      title.textContent = "Créer un compte";
      sub.textContent = "Trois étapes — le profil se complète ensuite.";
      setPipe(1);
      showPanel("role");
      syncRecruiterFields();
    } else {
      title.textContent = "Connexion";
      sub.textContent = "Retrouvez votre espace HireLink.";
      showPanel("password");
    }
  }

  function afterAuth(auth) {
    HireLinkAPI.setAuth(auth);
    HireLink.toast("Bienvenue", auth.fullName || auth.email, "ok");
    setTimeout(() => {
      if (auth.mustSetPassword && !localStorage.getItem("hirelink.pw.skip")) {
        location.href = "set-password.html";
      } else {
        location.href = HireLinkAPI.redirectForRole(auth.role);
      }
    }, 280);
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

  document.getElementById("btn-role-next").addEventListener("click", () => {
    setPipe(2);
    if (role === "RECRUTEUR") {
      title.textContent = "Votre entreprise";
      sub.textContent = "Raison sociale + ICE — vérification anti-fraude.";
    } else {
      title.textContent = "Votre email";
      sub.textContent = "Nous envoyons un code sécurisé à 6 chiffres.";
    }
    showPanel("email");
    syncRecruiterFields();
  });

  document.getElementById("btn-back-role").addEventListener("click", () => {
    if (mode === "login") {
      title.textContent = "Connexion";
      sub.textContent = "Retrouvez votre espace HireLink.";
      showPanel("password");
      return;
    }
    setPipe(1);
    title.textContent = "Créer un compte";
    sub.textContent = "Trois étapes — le profil se complète ensuite.";
    showPanel("role");
    syncRecruiterFields();
  });

  document.getElementById("btn-back-email").addEventListener("click", () => {
    setPipe(2);
    if (mode === "register") {
      title.textContent = role === "RECRUTEUR" ? "Votre entreprise" : "Votre email";
      sub.textContent =
        role === "RECRUTEUR"
          ? "Raison sociale + ICE — vérification anti-fraude."
          : "Nous envoyons un code sécurisé à 6 chiffres.";
    } else {
      title.textContent = "Connexion par code e-mail";
      sub.textContent = "Recevez un code à 6 chiffres sur votre boîte mail.";
    }
    showPanel("email");
    syncRecruiterFields();
  });

  panels.email.addEventListener("submit", async (e) => {
    e.preventDefault();
    const email = document.getElementById("email").value.trim();
    if (!email) {
      HireLink.toast("Email requis", "Indiquez une adresse valide.", "warn");
      return;
    }
    if (role === "RECRUTEUR" && mode === "register") {
      const ice = (document.getElementById("companyIce").value || "").replace(/\s/g, "");
      const name = (document.getElementById("companyName").value || "").trim();
      if (!name) {
        HireLink.toast("Nom requis", "Indiquez la raison sociale officielle.", "warn");
        return;
      }
      if (!/^\d{9,15}$/.test(ice)) {
        HireLink.toast("ICE invalide", "9 à 15 chiffres attendus.", "warn");
        return;
      }
    }
    pendingEmail = email;
    try {
      await HireLinkAPI.api("/auth/request-otp", {
        method: "POST",
        body: { email, purpose: mode === "register" ? "REGISTER" : "LOGIN" },
      });
      document.getElementById("otp-hint").textContent =
        "Code envoyé à " + email + ".";
      if (mode === "register") setPipe(3);
      title.textContent = "Vérification";
      sub.textContent = "Saisissez le code reçu.";
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
      let auth;
      if (mode === "register") {
        auth = await HireLinkAPI.api("/auth/verify-register-otp", {
          method: "POST",
          body: {
            email: pendingEmail,
            code,
            role,
            companyIce:
              role === "RECRUTEUR"
                ? (document.getElementById("companyIce").value || "").replace(/\s/g, "")
                : null,
            company:
              role === "RECRUTEUR" ? document.getElementById("companyName").value.trim() : null,
          },
        });
      } else {
        auth = await HireLinkAPI.api("/auth/verify-login-otp", {
          method: "POST",
          body: { email: pendingEmail, code },
        });
      }
      afterAuth(auth);
    } catch (err) {
      HireLink.toast("Code invalide", err.message, "danger");
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
      afterAuth(auth);
    } catch (err) {
      HireLink.toast("Connexion refusée", err.message, "danger");
    }
  });

  document.getElementById("btn-otp-login").addEventListener("click", () => {
    mode = "login";
    if (pill) pill.classList.add("is-login");
    document.getElementById("tab-register").classList.remove("is-active");
    document.getElementById("tab-login").classList.add("is-active");
    if (pipe) pipe.classList.add("is-hidden");
    title.textContent = "Connexion par code e-mail";
    sub.textContent = "Recevez un code à 6 chiffres (affiché aussi dans la console backend).";
    showPanel("email");
    syncRecruiterFields();
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

  async function finishGoogleLogin(idToken) {
    let company = null;
    let companyIce = null;
    const chosen = mode === "register" ? role : null;

    if (mode === "register" && role === "RECRUTEUR") {
      const ok = await HireLink.openModal({
        title: "Infos entreprise",
        description: "Dernière étape pour le compte recruteur.",
        bodyHtml:
          '<div class="stack">' +
          '<div class="field"><label>Raison sociale</label><input id="g-co" /></div>' +
          '<div class="field"><label>ICE</label><input id="g-ice" placeholder="9 à 15 chiffres" /></div>' +
          "</div>",
        confirmLabel: "Créer mon compte",
        large: true,
      });
      if (!ok) return;
      company = ((document.getElementById("g-co") || {}).value || "").trim();
      companyIce = ((document.getElementById("g-ice") || {}).value || "").replace(/\s/g, "");
      if (!company || !/^\d{9,15}$/.test(companyIce)) {
        HireLink.toast("Infos entreprise", "Nom officiel + ICE requis.", "warn");
        return;
      }
    }

    const auth = await HireLinkAPI.api("/auth/google", {
      method: "POST",
      body: { idToken, role: chosen, company, companyIce },
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
          await finishGoogleLogin(response.credential);
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
      text: "continue_with",
      shape: "pill",
      width,
      logo_alignment: "left",
    });
  }

  document.getElementById("btn-google").addEventListener("click", async () => {
    try {
      const ok = await HireLink.openModal({
        title: "Continuer avec Google",
        description: "Connexion Google — configurez le Client ID OAuth si besoin.",
        bodyHtml:
          '<div class="stack">' +
          '<div class="field"><label>Email</label><input id="g-email" type="email" /></div>' +
          '<div class="field"><label>Nom</label><input id="g-name" /></div>' +
          (mode === "register"
            ? '<div class="field"><label>Je suis</label><select id="g-role"><option value="CANDIDAT">Candidat</option><option value="RECRUTEUR">Recruteur</option></select></div>' +
              '<div class="field" id="g-co-wrap" hidden><label>Raison sociale</label><input id="g-co" /></div>' +
              '<div class="field" id="g-ice-wrap" hidden><label>ICE</label><input id="g-ice" /></div>'
            : "") +
          "</div>",
        confirmLabel: "Continuer",
        large: true,
      });
      if (!ok) return;
      const gRole = document.getElementById("g-role");
      const chosen = gRole ? gRole.value : role;
      const iceEl = document.getElementById("g-ice");
      const coEl = document.getElementById("g-co");
      if (chosen === "RECRUTEUR" && mode === "register") {
        const ice = (iceEl && iceEl.value ? iceEl.value : "").replace(/\s/g, "");
        const co = (coEl && coEl.value ? coEl.value : "").trim();
        if (!co || !/^\d{9,15}$/.test(ice)) {
          HireLink.toast("Infos entreprise", "Nom officiel + ICE requis.", "warn");
          return;
        }
      }
      const auth = await HireLinkAPI.api("/auth/google", {
        method: "POST",
        body: {
          devEmail: (document.getElementById("g-email") || {}).value,
          devName: (document.getElementById("g-name") || {}).value,
          role: mode === "register" ? chosen : null,
          company: coEl ? coEl.value.trim() : null,
          companyIce: iceEl ? iceEl.value.replace(/\s/g, "") : null,
        },
      });
      afterAuth(auth);
    } catch (err) {
      HireLink.toast("Google", err.message || "Échec", "danger");
    }
  });

  document.addEventListener("change", (e) => {
    if (e.target && e.target.id === "g-role") {
      const show = e.target.value === "RECRUTEUR";
      const a = document.getElementById("g-ice-wrap");
      const b = document.getElementById("g-co-wrap");
      if (a) a.hidden = !show;
      if (b) b.hidden = !show;
    }
  });

  HireLinkAPI.api("/auth/config")
    .then(async (cfg) => {
      authConfig = cfg;
      const hint = document.getElementById("google-hint");
      const fallback = document.getElementById("btn-google");
      if (hint) hint.textContent = "";
      if (cfg.googleConfigured && cfg.googleClientId) {
        try {
          await mountGoogleButton(cfg.googleClientId);
        } catch (err) {
          if (hint) hint.textContent = "Google temporairement indisponible — utilisez e-mail / mot de passe.";
          if (fallback) fallback.hidden = false;
        }
      } else {
        if (hint) hint.textContent = "Connexion Google optionnelle — e-mail / mot de passe disponibles.";
        if (fallback) fallback.hidden = false;
      }
    })
    .catch(() => {
      const hint = document.getElementById("google-hint");
      if (hint) hint.textContent = "Backend hors ligne (port 8081)";
      const fallback = document.getElementById("btn-google");
      if (fallback) fallback.hidden = false;
    });

  selectRole(role);
  applyMode();
})();
