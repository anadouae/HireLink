/**
 * SaaS growth — popups Premium (candidat / recruteur)
 */
(function (global) {
  const KEY_DISMISS = "hirelink.premium.dismiss.";
  const KEY_SHOWN = "hirelink.premium.shown.";

  const COPY = {
    candidate: {
      title: "Candidat Premium",
      subtitle: "Matching fort + assist IA complète",
      bullets: [
        "Alertes matching prioritaires",
        "Réécriture pitch & prep entretien (script complet)",
        "Profil mis en avant auprès des recruteurs",
        "Priorité sur les nouvelles offres",
      ],
      cta: "Continuer vers le paiement",
      priceNote: "Essentiel reste gratuit · résiliable à tout moment",
    },
    recruiter: {
      title: "Recruteur Premium",
      subtitle: "Inbox SLA, scoring, équipe RH entreprise",
      bullets: [
        "Alertes inbox SLA & hot candidates",
        "Plusieurs sièges pour votre équipe RH",
        "Offres mises en avant",
        "Export & reporting clair",
      ],
      cta: "Continuer vers le paiement",
      priceNote: "Le plan Gratuit reste disponible sans engagement",
    },
  };

  function ensureCss() {
    if (document.getElementById("hl-growth-css")) return;
    const s = document.createElement("style");
    s.id = "hl-growth-css";
    s.textContent =
      ".hl-premium-overlay{position:fixed;inset:0;background:rgba(18,14,22,.58);z-index:9000;display:flex;align-items:center;justify-content:center;padding:1.25rem;opacity:0;pointer-events:none;transition:opacity .25s ease}" +
      ".hl-premium-overlay.is-open{opacity:1;pointer-events:auto}" +
      ".hl-premium-card{background:#fff;border-radius:18px;max-width:440px;width:100%;padding:1.75rem;box-shadow:0 24px 60px rgba(0,0,0,.22);border:1px solid rgba(122,32,50,.25);transform:translateY(12px);transition:transform .28s ease}" +
      ".hl-premium-overlay.is-open .hl-premium-card{transform:none}" +
      ".hl-premium-card .eyebrow{color:#7a2032;font-size:.72rem;font-weight:700;letter-spacing:.08em;text-transform:uppercase;margin:0 0 .35rem}" +
      ".hl-premium-card h3{font-family:Fraunces,Georgia,serif;font-size:1.55rem;margin:0 0 .35rem;color:#1a1214}" +
      ".hl-premium-card .sub{color:#5e5558;margin:0 0 1rem;font-size:.95rem}" +
      ".hl-premium-card ul{margin:0 0 1.25rem;padding:0;list-style:none}" +
      ".hl-premium-card li{padding:.45rem 0 .45rem 1.4rem;position:relative;font-size:.92rem;border-bottom:1px solid #eee}" +
      ".hl-premium-card li:before{content:'✓';position:absolute;left:0;color:#7a2032;font-weight:700}" +
      ".hl-premium-card li:last-child{border:0}" +
      ".hl-premium-actions{display:flex;flex-direction:column;gap:.55rem}" +
      ".hl-premium-actions .btn-primary{background:linear-gradient(135deg,#7a2032,#e87030)!important;border:0}" +
      ".hl-premium-note{font-size:.78rem;color:#8a8285;text-align:center;margin-top:.35rem}";
    document.head.appendChild(s);
  }

  function showPremium(role) {
    if (localStorage.getItem("hirelink.premium.active." + role) === "1") {
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
      '<p class="eyebrow">Offre Premium</p>' +
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
    localStorage.setItem(KEY_SHOWN + role, String(Date.now()));

    overlay.querySelector("#hl-prem-later").onclick = () => {
      localStorage.setItem(KEY_DISMISS + role, String(Date.now()));
      overlay.classList.remove("is-open");
    };
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
      if (e.target === overlay) overlay.classList.remove("is-open");
    };
  }

  function scheduleNudge(role, delayMs) {
    const dismissed = Number(localStorage.getItem(KEY_DISMISS + role) || 0);
    if (Date.now() - dismissed < 86400000) return;
    if (localStorage.getItem("hirelink.premium.active." + role)) return;
    setTimeout(() => showPremium(role), delayMs || 18000);
  }

  global.HireLinkGrowth = { showPremium, scheduleNudge };
})(window);
