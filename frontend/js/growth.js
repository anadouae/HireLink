/**
 * SaaS growth — popups Premium (candidat / recruteur)
 */
(function (global) {

  const COPY = {
    candidate: {
      title: "Candidat Performance",
      subtitle: "40 usages de l’assistant IA par mois, au lieu de 5",
      bullets: [
        "Réécriture de profil et pitch",
        "Préparation d’entretien détaillée",
        "Explication du score de matching",
        "Contrôle de la langue sur le profil",
      ],
      cta: "Continuer vers le paiement",
      priceNote: "Essentiel reste à 5 usages IA par mois · résiliable à tout moment",
    },
    recruiter: {
      title: "Recruteur Premium",
      subtitle: "25 sièges d’équipe et 200 usages IA par mois",
      bullets: [
        "Jusqu’à 25 comptes d’équipe (5 sur Accès)",
        "Alertes de délai sur les dossiers en attente",
        "200 usages IA par mois, 1 usage = 1 crédit",
        "Export et reporting clairs",
      ],
      cta: "Continuer vers le paiement",
      priceNote: "Le plan Accès reste inclus : 20 usages IA par mois",
    },
  };

  function ensureCss() {
    if (document.getElementById("hl-growth-css")) return;
    const s = document.createElement("style");
    s.id = "hl-growth-css";
    s.textContent =
      ".hl-premium-overlay{position:fixed;inset:0;background:rgba(18,14,22,.58);z-index:9000;display:flex;align-items:center;justify-content:center;padding:1.25rem;opacity:0;pointer-events:none;visibility:hidden;transition:opacity .25s ease,visibility 0s .25s}" +
      ".hl-premium-overlay.is-open{opacity:1;pointer-events:auto;visibility:visible;transition:opacity .25s ease,visibility 0s 0s}" +
      ".hl-premium-card{background:#fff;border-radius:18px;max-width:440px;width:100%;padding:1.75rem;box-shadow:0 24px 60px rgba(0,0,0,.22);border:1px solid rgba(122,32,50,.25);transform:translateY(12px);transition:transform .28s ease}" +
      ".hl-premium-overlay.is-open .hl-premium-card{transform:none}" +
      ".hl-premium-card .eyebrow{color:#7a2032;font-size: var(--text-xs);font-weight:700;letter-spacing:.08em;text-transform:uppercase;margin:0 0 .35rem}" +
      ".hl-premium-card h3{font-family:var(--font-display);font-size: var(--text-xl);margin:0 0 .35rem;color:#1a1214}" +
      ".hl-premium-card .sub{color:#5e5558;margin:0 0 1rem;font-size: var(--text-md)}" +
      ".hl-premium-card ul{margin:0 0 1.25rem;padding:0;list-style:none}" +
      ".hl-premium-card li{padding:.45rem 0 .45rem 1.4rem;position:relative;font-size: var(--text-base);border-bottom:1px solid #eee}" +
      ".hl-premium-card li:before{content:'✓';position:absolute;left:0;color:#7a2032;font-weight:700}" +
      ".hl-premium-card li:last-child{border:0}" +
      ".hl-premium-actions{display:flex;flex-direction:column;gap:.55rem}" +
      ".hl-premium-actions .btn-primary{background:linear-gradient(135deg,#7a2032,#e87030)!important;border:0}" +
      ".hl-premium-note{font-size: var(--text-xs);color:#8a8285;text-align:center;margin-top:.35rem}";
    document.head.appendChild(s);
  }

  function showPremium(role) {
    if (global.HireLinkAPI && HireLinkAPI.isPremium()) {
      if (global.HireLink) HireLink.toast("Premium", "Votre offre Premium est déjà active.", "ok");
      return;
    }
    ensureCss();
    const conf = COPY[role] || COPY.candidate;
    let overlay = document.getElementById("hl-premium-overlay");
    if (!overlay) {
      overlay = document.createElement("div");
      overlay.id = "hl-premium-overlay";
      overlay.className = "hl-premium-overlay";
      document.body.appendChild(overlay);
    }
    overlay.innerHTML =
      '<div class="hl-premium-card" role="dialog" aria-modal="true">' +
      '<p class="eyebrow">Changement de formule</p>' +
      "<h3></h3><p class='sub'></p><ul></ul>" +
      '<div class="hl-premium-actions">' +
      '<button type="button" class="btn btn-primary btn-block" id="hl-prem-cta"></button>' +
      '<button type="button" class="btn btn-ghost btn-block" id="hl-prem-later">Pas maintenant</button>' +
      "</div><p class='hl-premium-note'></p></div>";
    overlay.querySelector("h3").textContent = conf.title;
    overlay.querySelector(".sub").textContent = conf.subtitle;
    const ul = overlay.querySelector("ul");
    conf.bullets.forEach((b) => {
      const li = document.createElement("li");
      li.textContent = b;
      ul.appendChild(li);
    });
    overlay.querySelector("#hl-prem-cta").textContent = conf.cta;
    overlay.querySelector(".hl-premium-note").textContent = conf.priceNote;
    overlay.classList.add("is-open");

    // Le voile est dessiné ici, pas dans ui.js : il lui faut le même contrat clavier (Escape, Tab, retour du focus).
    const card = overlay.querySelector(".hl-premium-card");
    function dismiss() {
      overlay.classList.remove("is-open");
      if (overlay._hlRelease) {
        overlay._hlRelease();
        overlay._hlRelease = null;
      }
    }
    if (overlay._hlRelease) overlay._hlRelease();
    if (global.HireLink && HireLink.trapDialog) {
      overlay._hlRelease = HireLink.trapDialog(card, dismiss);
    }

    overlay.querySelector("#hl-prem-later").onclick = dismiss;
    overlay.querySelector("#hl-prem-cta").onclick = () => {
      const plan = role === "recruiter" ? "recruiter" : "candidate";
      const inApp = /\/app\//.test(location.pathname);
      const file = (location.pathname.split("/").pop() || "index.html").split("?")[0];
      const back = /^[\w.-]+\.html$/.test(file) ? file : "";
      location.href =
        (inApp ? "../" : "") +
        "checkout.html?plan=" +
        plan +
        (back ? "&back=" + encodeURIComponent(back) : "");
    };
    overlay.onclick = (e) => {
      if (e.target === overlay) dismiss();
    };
  }

  global.HireLinkGrowth = { showPremium };
})(window);
