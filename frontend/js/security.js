/**
 * Page « Sécurité » (tous les rôles) : mot de passe, passkeys, appareils connectés,
 * export / désactivation / suppression du compte.
 * Toute action sensible redemande l'identité (mot de passe, ou code e-mail).
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

  function originHint() {
    try {
      if (location.hostname === "127.0.0.1") {
        return '<p class="rc-muted" style="margin:.5rem 0 0;font-size:.82rem">Astuce passkeys : ouvrez le site via <strong>http://localhost:5500</strong> (pas 127.0.0.1).</p>';
      }
    } catch (_) {}
    return "";
  }

  async function load() {
    try {
      [sum, sessions] = await Promise.all([api("/account/security"), api("/account/sessions")]);
      render();
    } catch (err) {
      App.errorState(root, err.message, load);
    }
  }

  function render() {
    const admin = sum.role === "ADMINISTRATEUR";
    let h = "";
    if (admin) {
      h +=
        '<div class="rc-banner rc-banner--ok"><div><strong>Compte administrateur</strong><span>Utilisez un mot de passe fort et une passkey pour sécuriser l’accès.</span></div></div>';
    }

    h +=
      '<section class="rc-card" id="sec-password"><div class="rc-card-head"><div><h2>Mot de passe</h2><p>' +
      esc(sum.passwordRules) +
      "</p></div></div>" +
      '<div class="rc-card-body"><div class="sec-row"><div><strong>' +
      (sum.hasPassword ? "Mot de passe défini" : "Aucun mot de passe (connexion par code ou Google)") +
      '</strong><span class="rc-muted">' +
      (sum.passwordChangedAt
        ? "Dernier changement : " + esc(fmt(sum.passwordChangedAt))
        : "Jamais modifié depuis la création du compte") +
      '</span></div><div class="sec-actions"><button type="button" class="btn btn-primary btn-sm" data-act="password">' +
      (sum.hasPassword ? "Changer le mot de passe" : "Définir un mot de passe") +
      "</button></div></div></div></section>";

    const canPasskey = window.HireLinkWebAuthn && HireLinkWebAuthn.supported();
    h +=
      '<section class="rc-card" id="sec-passkeys"><div class="rc-card-head"><div><h2>Passkeys</h2><p>Connexion sans mot de passe avec l’empreinte, le visage, le code de l’appareil ou une clé de sécurité. Le mot de passe reste disponible.</p></div>' +
      (canPasskey
        ? '<button type="button" class="btn btn-soft btn-sm" data-act="passkey-add">Ajouter une passkey</button>'
        : pill("Non pris en charge ici", "warn")) +
      "</div>" +
      '<div class="rc-card-body">' +
      (sum.passkeys.length
        ? sum.passkeys
            .map(
              (k) =>
                '<div class="sec-row"><div><strong>' +
                esc(k.name) +
                '</strong><span class="rc-muted">Ajoutée le ' +
                esc(fmt(k.createdAt)) +
                " · dernière utilisation : " +
                esc(fmt(k.lastUsedAt)) +
                '</span></div><div class="sec-actions"><button type="button" class="btn btn-ghost btn-sm" data-act="passkey-del" data-id="' +
                k.id +
                '">Retirer</button></div></div>'
            )
            .join("")
        : '<p class="rc-muted" style="margin:0">Aucune passkey enregistrée.</p>') +
      originHint() +
      "</div></section>";

    h +=
      '<section class="rc-card" id="sec-sessions"><div class="rc-card-head"><div><h2>Appareils connectés</h2><p>Chaque connexion est une session. Déconnectez celles que vous ne reconnaissez pas.</p></div><div class="sec-actions">' +
      '<button type="button" class="btn btn-soft btn-sm" data-act="revoke-others"' +
      (sessions.length < 2 ? " disabled" : "") +
      '>Déconnecter les autres</button>' +
      '<button type="button" class="btn btn-ghost btn-sm" data-act="revoke-all">Se déconnecter partout</button></div></div><div class="rc-card-body">' +
      sessions
        .map(
          (s) =>
            '<div class="sec-row"><div><strong>' +
            esc(s.device || "Appareil inconnu") +
            (s.current ? " " + pill("Cet appareil", "ok") : "") +
            '</strong><span class="rc-muted">IP ' +
            esc(s.ip || "—") +
            " · connecté " +
            esc(fmt(s.createdAt)) +
            " · dernière activité " +
            esc(fmt(s.lastUsedAt)) +
            '</span></div><div class="sec-actions">' +
            (s.current
              ? ""
              : '<button type="button" class="btn btn-ghost btn-sm" data-act="revoke" data-id="' +
                esc(s.id) +
                '">Déconnecter</button>') +
            "</div></div>"
        )
        .join("") +
      "</div></section>";

    if (!admin) {
      h +=
        '<section class="rc-card" id="sec-data"><div class="rc-card-head"><div><h2>Mes données et mon compte</h2><p>Droits d’accès et d’effacement (loi 09-08 / RGPD).</p></div></div><div class="rc-card-body">' +
        '<div class="sec-row"><div><strong>Exporter mes données</strong><span class="rc-muted">Fichier JSON : profil, candidatures, consentements.</span></div><div class="sec-actions"><button type="button" class="btn btn-soft btn-sm" data-act="export">Télécharger</button></div></div>' +
        '<div class="sec-row"><div><strong>Désactiver mon compte</strong><span class="rc-muted">Connexion impossible ; le support peut le réactiver. Vos données sont conservées.</span></div><div class="sec-actions"><button type="button" class="btn btn-ghost btn-sm" data-act="deactivate">Désactiver</button></div></div>' +
        '<div class="sec-row"><div><strong>Supprimer mon compte et anonymiser mes données</strong><span class="rc-muted">Irréversible : nom, e-mail, CV, passkeys et appareils sont effacés.</span></div><div class="sec-actions"><button type="button" class="btn btn-danger btn-sm" data-act="erase">Supprimer</button></div></div>' +
        "</div></section>";
    }
    root.innerHTML = h;
  }

  function reauthModal(o) {
    let html = '<div class="sec-stack">';
    if (sum.hasPassword) {
      html +=
        '<div class="rc-field"><label for="ra-pw">Votre mot de passe actuel</label><input id="ra-pw" type="password" autocomplete="current-password" /></div>';
    } else {
      html +=
        '<div class="rc-field"><label for="ra-otp">Code reçu par e-mail</label><div class="rc-row"><input id="ra-otp" inputmode="numeric" maxlength="6" autocomplete="one-time-code" style="flex:1" /><button type="button" class="btn btn-soft btn-sm" id="ra-send">Envoyer le code</button></div></div>';
    }
    html += (o.extraHtml || "") + "</div>";
    let modalRoot = null;
    return HireLink.openModal({
      title: o.title,
      description: o.description,
      confirmLabel: o.confirmLabel || "Confirmer",
      danger: o.danger,
      bodyHtml: html,
      onReady: (api2) => {
        modalRoot = api2.root;
        if (window.HireLinkPassword) HireLinkPassword.bindAll(modalRoot);
        const send = modalRoot.querySelector("#ra-send");
        if (send)
          send.addEventListener("click", async () => {
            try {
              const r = await api("/account/otp", { method: "POST", body: {} });
              App.toast("Code envoyé", r.message, "ok");
            } catch (err) {
              App.toast("Échec", err.message, "danger");
            }
          });
        const first = modalRoot.querySelector("input");
        if (first) first.focus();
      },
      onConfirm: async () => {
        const v = (id) => {
          const el = modalRoot.querySelector(id);
          return el ? el.value.trim() : "";
        };
        const reauth = {
          currentPassword: v("#ra-pw") || null,
          mfaCode: null,
          otp: v("#ra-otp") || null,
        };
        if (!reauth.currentPassword && !reauth.otp) {
          throw new Error(sum.hasPassword ? "Saisissez votre mot de passe actuel." : "Saisissez le code reçu par e-mail.");
        }
        return o.run(reauth, modalRoot);
      },
    });
  }

  const done = (msg) => {
    App.toast("Sécurité", msg, "ok");
    return load();
  };

  const actions = {
    async password() {
      const r = await reauthModal({
        title: sum.hasPassword ? "Changer le mot de passe" : "Définir un mot de passe",
        description: "Confirmez votre identité, puis choisissez le nouveau mot de passe. Vos autres appareils seront déconnectés.",
        confirmLabel: "Enregistrer",
        extraHtml:
          '<div class="rc-field"><label for="ra-new">Nouveau mot de passe</label><input id="ra-new" type="password" autocomplete="new-password" /></div><p class="rc-muted" style="margin:0;font-size:.8rem">' +
          esc(sum.passwordRules) +
          "</p>",
        run: (re, m) =>
          api("/account/password", {
            method: "POST",
            body: Object.assign({ newPassword: m.querySelector("#ra-new").value }, re),
          }),
      });
      if (r) await done("Mot de passe modifié. Vos autres appareils ont été déconnectés.");
    },
    async "passkey-add"() {
      if (location.hostname === "127.0.0.1") {
        App.toast(
          "Passkey",
          "Ouvrez HireLink via http://localhost:5500 (pas 127.0.0.1), puis réessayez.",
          "warn"
        );
      }
      const r = await reauthModal({
        title: "Ajouter une passkey",
        description: "Après confirmation, votre appareil vous demandera votre empreinte, votre visage ou votre code.",
        confirmLabel: "Créer la passkey",
        extraHtml:
          '<div class="rc-field"><label for="pk-name">Nom de la passkey</label><input id="pk-name" maxlength="60" placeholder="Ex. Mon ordinateur" /></div>',
        run: async (re, m) => {
          let opts;
          try {
            opts = await api("/account/passkeys/options", {
              method: "POST",
              body: re,
              timeoutMs: 45000,
            });
          } catch (err) {
            if (err && err.message === "Réseau indisponible") {
              throw new Error("API injoignable. Vérifiez que le serveur tourne, puis réessayez.");
            }
            throw err;
          }
          const credential = await HireLinkWebAuthn.create(opts);
          const name = m.querySelector("#pk-name").value.trim() || "Ma passkey";
          return api("/account/passkeys", {
            method: "POST",
            body: { requestId: opts.requestId, credential, name },
            timeoutMs: 45000,
          });
        },
      });
      if (r) await done("Passkey ajoutée. Vous pouvez maintenant l’utiliser sur la page de connexion.");
    },
    async "passkey-del"(btn) {
      const r = await reauthModal({
        title: "Retirer cette passkey",
        confirmLabel: "Retirer",
        danger: true,
        run: (re) => api("/account/passkeys/" + btn.dataset.id + "/remove", { method: "POST", body: re }),
      });
      if (r) await done("Passkey retirée.");
    },
    async revoke(btn) {
      await api("/account/sessions/" + encodeURIComponent(btn.dataset.id), { method: "DELETE" });
      await done("Appareil déconnecté.");
    },
    async "revoke-others"() {
      const ok = await Mascotte.confirm({
        title: "Déconnecter les autres appareils ?",
        description: "Seule cette session restera ouverte.",
        confirmLabel: "Déconnecter",
      });
      if (!ok) return;
      await api("/account/sessions/revoke-others", { method: "POST", body: {} });
      await done("Les autres appareils ont été déconnectés.");
    },
    async "revoke-all"() {
      const ok = await Mascotte.confirm({
        title: "Se déconnecter partout ?",
        description: "Toutes les sessions, y compris celle-ci, seront fermées.",
        confirmLabel: "Tout fermer",
        danger: true,
      });
      if (!ok) return;
      await api("/account/sessions/revoke-all", { method: "POST", body: {} });
      Mascotte.goodbye(HireLinkAPI.getSession());
      HireLinkAPI.clearAuth();
      location.href = "../auth.html?mode=login";
    },
    async export() {
      const res = await HireLinkAPI.authFetch("/me/export");
      if (!res.ok) throw new Error("Export impossible");
      const url = URL.createObjectURL(await res.blob());
      const a = document.createElement("a");
      a.href = url;
      a.download = "hirelink-mes-donnees.json";
      document.body.appendChild(a);
      a.click();
      a.remove();
      setTimeout(() => URL.revokeObjectURL(url), 5000);
      App.toast("Export", "Fichier téléchargé.", "ok");
    },
    async deactivate() {
      const r = await reauthModal({
        title: "Désactiver mon compte",
        description: "Vous serez déconnecté partout. Le support peut réactiver le compte.",
        confirmLabel: "Désactiver",
        danger: true,
        run: (re) => api("/account/deactivate", { method: "POST", body: re }),
      });
      if (r) {
        HireLinkAPI.clearAuth();
        location.href = "../auth.html?mode=login";
      }
    },
    async erase() {
      const r = await reauthModal({
        title: "Supprimer mon compte",
        description: "Vos données personnelles seront anonymisées définitivement. Cette action est irréversible.",
        confirmLabel: "Supprimer définitivement",
        danger: true,
        extraHtml:
          '<div class="rc-field"><label for="er-word">Tapez SUPPRIMER pour confirmer</label><input id="er-word" autocomplete="off" /></div>',
        run: (re, m) => {
          if (m.querySelector("#er-word").value.trim() !== "SUPPRIMER") {
            throw new Error("Tapez le mot SUPPRIMER pour confirmer.");
          }
          return api("/account/erase", { method: "POST", body: re });
        },
      });
      if (r) {
        HireLinkAPI.clearAuth();
        location.href = "../auth.html?mode=login";
      }
    },
  };

  root.addEventListener("click", async (e) => {
    const btn = e.target.closest("[data-act]");
    if (!btn || btn.disabled) return;
    const fn = actions[btn.dataset.act];
    if (!fn) return;
    try {
      await fn(btn);
    } catch (err) {
      App.toast("Sécurité", err.message, "danger");
    }
  });

  load();
})();
