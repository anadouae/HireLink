/**
 * Page « Sécurité » (tous les rôles) : mot de passe, double authentification (TOTP), passkeys,
 * appareils connectés, export / désactivation / suppression du compte.
 * Toute action sensible redemande l'identité (mot de passe, ou code e-mail, + code TOTP si activé).
 */
(function () {
  const App = HireLinkApp;
  const session = App.mount({ active: "security", title: "Sécurité" });
  if (!session) return;
  const esc = App.esc;
  const api = (path, opts) => HireLinkAPI.api(path, Object.assign({ noDedupe: true }, opts || {}));
  const root = document.getElementById("sec-root");
  let sum = null;
  let sessions = [];

  const fmt = (v) => (v ? App.fmtDateTime(v) : "jamais");
  const pill = (txt, tone) => '<span class="rc-pill' + (tone ? " rc-pill--" + tone : "") + '">' + esc(txt) + "</span>";

  async function load() {
    try {
      [sum, sessions] = await Promise.all([api("/account/security"), api("/account/sessions")]);
      render();
    } catch (err) {
      App.errorState(root, err.message, load);
    }
  }

  /* --------------------------------------------------------------- rendu */
  function render() {
    const admin = sum.role === "ADMINISTRATEUR";
    let h = "";
    if (admin) {
      h += '<div class="rc-banner rc-banner--ok"><div><strong>Double authentification obligatoire pour l’administrateur</strong><span>Elle est active : un mot de passe seul ne suffit jamais. Une passkey est une alternative.</span></div></div>';
    } else if (!sum.mfaEnabled) {
      h += '<div class="rc-banner"><div><strong>Renforcez la sécurité de votre compte</strong><span>Activez la double authentification : un code à usage unique s’ajoute à votre mot de passe. Vous pouvez aussi ajouter une passkey.</span></div></div>';
    }

    h += '<section class="rc-card" id="sec-password"><div class="rc-card-head"><div><h2>Mot de passe</h2><p>' + esc(sum.passwordRules) + "</p></div></div>" +
      '<div class="rc-card-body"><div class="sec-row"><div><strong>' + (sum.hasPassword ? "Mot de passe défini" : "Aucun mot de passe (connexion par code ou Google)") +
      '</strong><span class="rc-muted">' + (sum.passwordChangedAt ? "Dernier changement : " + esc(fmt(sum.passwordChangedAt)) : "Jamais modifié depuis la création du compte") +
      '</span></div><div class="sec-actions"><button type="button" class="btn btn-primary btn-sm" data-act="password">' + (sum.hasPassword ? "Changer le mot de passe" : "Définir un mot de passe") + "</button></div></div></div></section>";

    h += '<section class="rc-card" id="sec-mfa"><div class="rc-card-head"><div><h2>Double authentification (TOTP)</h2><p>Un code à 6 chiffres renouvelé toutes les 30 secondes, généré par une application (Google Authenticator, Microsoft Authenticator, Aegis…) ou par le script <code>docs/outils/totp.py</code>.</p></div>' +
      pill(sum.mfaEnabled ? "Activée" : "Désactivée", sum.mfaEnabled ? "ok" : "warn") + "</div>" +
      '<div class="rc-card-body"><div class="sec-row"><div><strong>' + (sum.mfaEnabled ? "Codes de secours restants : " + sum.backupCodesLeft : "Protégez-vous si votre mot de passe fuite") +
      '</strong><span class="rc-muted">' + (sum.mfaEnabled ? "À utiliser si vous perdez l’application. Chaque code ne sert qu’une fois." : "L’activation prend moins d’une minute.") + '</span></div><div class="sec-actions">' +
      (sum.mfaEnabled
        ? '<button type="button" class="btn btn-soft btn-sm" data-act="backup">Régénérer les codes de secours</button>' + (admin ? "" : '<button type="button" class="btn btn-ghost btn-sm" data-act="mfa-off">Désactiver</button>')
        : '<button type="button" class="btn btn-primary btn-sm" data-act="mfa-on">Activer</button>') +
      "</div></div></div></section>";

    const canPasskey = window.HireLinkWebAuthn && HireLinkWebAuthn.supported();
    h += '<section class="rc-card" id="sec-passkeys"><div class="rc-card-head"><div><h2>Passkeys</h2><p>Connexion sans mot de passe avec l’empreinte, le visage, le code de l’appareil ou une clé de sécurité. Le mot de passe reste disponible.</p></div>' +
      (canPasskey ? '<button type="button" class="btn btn-soft btn-sm" data-act="passkey-add">Ajouter une passkey</button>' : pill("Non pris en charge ici", "warn")) + "</div>" +
      '<div class="rc-card-body">' +
      (sum.passkeys.length
        ? sum.passkeys.map((k) => '<div class="sec-row"><div><strong>' + esc(k.name) + '</strong><span class="rc-muted">Ajoutée le ' + esc(fmt(k.createdAt)) + " · dernière utilisation : " + esc(fmt(k.lastUsedAt)) +
            '</span></div><div class="sec-actions"><button type="button" class="btn btn-ghost btn-sm" data-act="passkey-del" data-id="' + k.id + '">Retirer</button></div></div>').join("")
        : '<p class="rc-muted" style="margin:0">Aucune passkey enregistrée.</p>') + "</div></section>";

    h += '<section class="rc-card" id="sec-sessions"><div class="rc-card-head"><div><h2>Appareils connectés</h2><p>Chaque connexion est une session. Déconnectez celles que vous ne reconnaissez pas.</p></div><div class="sec-actions">' +
      '<button type="button" class="btn btn-soft btn-sm" data-act="revoke-others"' + (sessions.length < 2 ? " disabled" : "") + '>Déconnecter les autres</button>' +
      '<button type="button" class="btn btn-ghost btn-sm" data-act="revoke-all">Se déconnecter partout</button></div></div><div class="rc-card-body">' +
      sessions.map((s) => '<div class="sec-row"><div><strong>' + esc(s.device || "Appareil inconnu") + (s.current ? " " + pill("Cet appareil", "ok") : "") + '</strong><span class="rc-muted">IP ' + esc(s.ip || "—") +
        " · connecté " + esc(fmt(s.createdAt)) + " · dernière activité " + esc(fmt(s.lastUsedAt)) + '</span></div><div class="sec-actions">' +
        (s.current ? "" : '<button type="button" class="btn btn-ghost btn-sm" data-act="revoke" data-id="' + esc(s.id) + '">Déconnecter</button>') + "</div></div>").join("") + "</div></section>";

    if (!admin) {
      h += '<section class="rc-card" id="sec-data"><div class="rc-card-head"><div><h2>Mes données et mon compte</h2><p>Droits d’accès et d’effacement (loi 09-08 / RGPD).</p></div></div><div class="rc-card-body">' +
        '<div class="sec-row"><div><strong>Exporter mes données</strong><span class="rc-muted">Fichier JSON : profil, candidatures, consentements.</span></div><div class="sec-actions"><button type="button" class="btn btn-soft btn-sm" data-act="export">Télécharger</button></div></div>' +
        '<div class="sec-row"><div><strong>Désactiver mon compte</strong><span class="rc-muted">Connexion impossible ; le support peut le réactiver. Vos données sont conservées.</span></div><div class="sec-actions"><button type="button" class="btn btn-ghost btn-sm" data-act="deactivate">Désactiver</button></div></div>' +
        '<div class="sec-row"><div><strong>Supprimer mon compte et anonymiser mes données</strong><span class="rc-muted">Irréversible : nom, e-mail, CV, passkeys et appareils sont effacés.</span></div><div class="sec-actions"><button type="button" class="btn btn-danger btn-sm" data-act="erase">Supprimer</button></div></div>' +
        "</div></section>";
    }
    root.innerHTML = h;
  }

  /* -------------------------------------------------- confirmation d'identité */
  /**
   * Fenêtre « Confirmez que c'est bien vous » : mot de passe (ou code e-mail), code TOTP si activé.
   * run(reauth, modalRoot) est exécuté sans fermer la fenêtre ; une erreur s'affiche dans la fenêtre.
   */
  function reauthModal(o) {
    let html = '<div class="sec-stack">';
    if (sum.hasPassword) {
      html += '<div class="rc-field"><label for="ra-pw">Votre mot de passe actuel</label><input id="ra-pw" type="password" autocomplete="current-password" /></div>';
    } else {
      html += '<div class="rc-field"><label for="ra-otp">Code reçu par e-mail</label><div class="rc-row"><input id="ra-otp" inputmode="numeric" maxlength="6" autocomplete="one-time-code" style="flex:1" /><button type="button" class="btn btn-soft btn-sm" id="ra-send">Envoyer le code</button></div></div>';
    }
    if (sum.mfaEnabled && !o.skipMfa) {
      html += '<div class="rc-field"><label for="ra-mfa">Code de l’application (ou code de secours)</label><input id="ra-mfa" inputmode="text" maxlength="12" autocomplete="one-time-code" /></div>';
    }
    html += (o.extraHtml || "") + "</div>";
    let modalRoot = null;
    return HireLink.openModal({
      title: o.title, description: o.description, confirmLabel: o.confirmLabel || "Confirmer", danger: o.danger, bodyHtml: html,
      onReady: (api2) => {
        modalRoot = api2.root;
        const send = modalRoot.querySelector("#ra-send");
        if (send) send.addEventListener("click", async () => {
          try { const r = await api("/account/otp", { method: "POST", body: {} }); App.toast("Code envoyé", r.message, "ok"); } catch (err) { App.toast("Échec", err.message, "danger"); }
        });
        const first = modalRoot.querySelector("input");
        if (first) first.focus();
      },
      onConfirm: async () => {
        const v = (id) => { const el = modalRoot.querySelector(id); return el ? el.value.trim() : ""; };
        const reauth = { currentPassword: v("#ra-pw") || null, mfaCode: v("#ra-mfa") || null, otp: v("#ra-otp") || null };
        if (!reauth.currentPassword && !reauth.otp) throw new Error(sum.hasPassword ? "Saisissez votre mot de passe actuel." : "Saisissez le code reçu par e-mail.");
        if (sum.mfaEnabled && !o.skipMfa && !reauth.mfaCode) throw new Error("Saisissez le code de votre application d’authentification.");
        return o.run(reauth, modalRoot);
      },
    });
  }

  function showCodes(codes, title) {
    return HireLink.openModal({
      title: title || "Codes de secours", description: "Notez-les maintenant : ils ne seront plus affichés. Chacun ne sert qu’une fois.", hideCancel: true, confirmLabel: "J’ai noté mes codes",
      bodyHtml: '<ul class="sec-codes">' + codes.map((c) => "<li>" + esc(c) + "</li>").join("") + '</ul><div style="margin-top:.6rem"><button type="button" class="btn btn-soft btn-sm" id="codes-copy">Copier</button></div>',
      onReady: (a) => a.root.querySelector("#codes-copy").addEventListener("click", async () => {
        try { await navigator.clipboard.writeText(codes.join("\n")); App.toast("Copié", "Codes de secours copiés.", "ok"); } catch (_) { App.toast("Copie impossible", "Copiez-les à la main.", "warn"); }
      }),
    });
  }

  const done = (msg) => { App.toast("Sécurité", msg, "ok"); return load(); };

  /* -------------------------------------------------------------- actions */
  const actions = {
    async password() {
      const r = await reauthModal({
        title: sum.hasPassword ? "Changer le mot de passe" : "Définir un mot de passe", description: "Confirmez votre identité, puis choisissez le nouveau mot de passe. Vos autres appareils seront déconnectés.",
        confirmLabel: "Enregistrer",
        extraHtml: '<div class="rc-field"><label for="ra-new">Nouveau mot de passe</label><input id="ra-new" type="password" autocomplete="new-password" /></div><p class="rc-muted" style="margin:0;font-size:.8rem">' + esc(sum.passwordRules) + "</p>",
        run: (re, m) => api("/account/password", { method: "POST", body: Object.assign({ newPassword: m.querySelector("#ra-new").value }, re) }),
      });
      if (r) await done("Mot de passe modifié. Vos autres appareils ont été déconnectés.");
    },
    async "mfa-on"() {
      const setup = await reauthModal({
        title: "Activer la double authentification", description: "Confirmez d’abord votre identité.", confirmLabel: "Continuer",
        run: (re) => api("/account/mfa/setup", { method: "POST", body: re }),
      });
      if (!setup) return;
      const codes = await HireLink.openModal({
        title: "Scannez le QR code", description: "Ouvrez votre application d’authentification, scannez ce QR code (ou saisissez la clé), puis entrez le code à 6 chiffres.", confirmLabel: "Activer",
        bodyHtml: '<div class="sec-stack"><div class="sec-qr"><img alt="QR code d’activation de la double authentification" src="' + esc(setup.qr) + '" /></div>' +
          '<div class="rc-field"><label for="mf-secret">Clé manuelle</label><input id="mf-secret" class="sec-secret" readonly value="' + esc(setup.secret) + '" /></div>' +
          '<div class="rc-field"><label for="mf-code">Code à 6 chiffres</label><input id="mf-code" inputmode="numeric" maxlength="6" autocomplete="one-time-code" /></div></div>',
        onReady: (a) => { a.root.querySelector("#mf-code").focus(); },
        onConfirm: async () => {
          const code = document.getElementById("mf-code").value.trim();
          if (!code) throw new Error("Saisissez le code à 6 chiffres.");
          const r = await api("/account/mfa/enable", { method: "POST", body: { code } });
          return r.backupCodes;
        },
      });
      if (codes && codes.length) await showCodes(codes);
      await done("Double authentification activée.");
    },
    async "mfa-off"() {
      const r = await reauthModal({
        title: "Désactiver la double authentification", description: "Votre compte ne sera plus protégé que par le mot de passe.", confirmLabel: "Désactiver", danger: true,
        run: (re) => api("/account/mfa/disable", { method: "POST", body: { currentPassword: re.currentPassword, otp: re.otp, code: re.mfaCode } }),
      });
      if (r) await done("Double authentification désactivée.");
    },
    async backup() {
      let codes = null;
      const r = await reauthModal({
        title: "Régénérer les codes de secours", description: "Les anciens codes seront invalidés.", confirmLabel: "Générer",
        run: async (re) => { const x = await api("/account/mfa/backup-codes", { method: "POST", body: { currentPassword: re.currentPassword, otp: re.otp, code: re.mfaCode } }); codes = x.backupCodes; return x; },
      });
      if (r && codes) await showCodes(codes, "Nouveaux codes de secours");
      if (r) await load();
    },
    async "passkey-add"() {
      const r = await reauthModal({
        title: "Ajouter une passkey", description: "Après confirmation, votre appareil vous demandera votre empreinte, votre visage ou votre code.", confirmLabel: "Créer la passkey",
        extraHtml: '<div class="rc-field"><label for="pk-name">Nom de la passkey</label><input id="pk-name" maxlength="60" placeholder="Ex. Mon ordinateur" /></div>',
        run: async (re, m) => {
          const opts = await api("/account/passkeys/options", { method: "POST", body: re });
          const credential = await HireLinkWebAuthn.create(opts);
          const name = m.querySelector("#pk-name").value.trim() || "Ma passkey";
          return api("/account/passkeys", { method: "POST", body: { requestId: opts.requestId, credential, name } });
        },
      });
      if (r) await done("Passkey ajoutée. Vous pouvez maintenant l’utiliser sur la page de connexion.");
    },
    async "passkey-del"(btn) {
      const r = await reauthModal({
        title: "Retirer cette passkey", confirmLabel: "Retirer", danger: true,
        run: (re) => api("/account/passkeys/" + btn.dataset.id + "/remove", { method: "POST", body: re }),
      });
      if (r) await done("Passkey retirée.");
    },
    async revoke(btn) {
      await api("/account/sessions/" + encodeURIComponent(btn.dataset.id), { method: "DELETE" });
      await done("Appareil déconnecté.");
    },
    async "revoke-others"() {
      const ok = await HireLink.openModal({ title: "Déconnecter les autres appareils ?", description: "Seule cette session restera ouverte.", confirmLabel: "Déconnecter" });
      if (!ok) return;
      await api("/account/sessions/revoke-others", { method: "POST", body: {} });
      await done("Les autres appareils ont été déconnectés.");
    },
    async "revoke-all"() {
      const ok = await HireLink.openModal({ title: "Se déconnecter partout ?", description: "Toutes les sessions, y compris celle-ci, seront fermées.", confirmLabel: "Tout fermer", danger: true });
      if (!ok) return;
      await api("/account/sessions/revoke-all", { method: "POST", body: {} });
      HireLinkAPI.clearAuth();
      location.href = "../auth.html?mode=login";
    },
    async export() {
      const res = await HireLinkAPI.authFetch("/me/export");
      if (!res.ok) throw new Error("Export impossible");
      const url = URL.createObjectURL(await res.blob());
      const a = document.createElement("a");
      a.href = url; a.download = "hirelink-mes-donnees.json";
      document.body.appendChild(a); a.click(); a.remove();
      setTimeout(() => URL.revokeObjectURL(url), 5000);
      App.toast("Export", "Fichier téléchargé.", "ok");
    },
    async deactivate() {
      const r = await reauthModal({
        title: "Désactiver mon compte", description: "Vous serez déconnecté partout. Le support peut réactiver le compte.", confirmLabel: "Désactiver", danger: true,
        run: (re) => api("/account/deactivate", { method: "POST", body: re }),
      });
      if (r) { HireLinkAPI.clearAuth(); location.href = "../auth.html?mode=login"; }
    },
    async erase() {
      const r = await reauthModal({
        title: "Supprimer mon compte", description: "Vos données personnelles seront anonymisées définitivement. Cette action est irréversible.", confirmLabel: "Supprimer définitivement", danger: true,
        extraHtml: '<div class="rc-field"><label for="er-word">Tapez SUPPRIMER pour confirmer</label><input id="er-word" autocomplete="off" /></div>',
        run: (re, m) => {
          if (m.querySelector("#er-word").value.trim() !== "SUPPRIMER") throw new Error("Tapez le mot SUPPRIMER pour confirmer.");
          return api("/account/erase", { method: "POST", body: re });
        },
      });
      if (r) { HireLinkAPI.clearAuth(); location.href = "../auth.html?mode=login"; }
    },
  };

  root.addEventListener("click", async (e) => {
    const btn = e.target.closest("[data-act]");
    if (!btn || btn.disabled) return;
    const fn = actions[btn.dataset.act];
    if (!fn) return;
    try { await fn(btn); } catch (err) { App.toast("Sécurité", err.message, "danger"); }
  });

  load();
})();
