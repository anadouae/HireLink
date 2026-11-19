/**
 * Suggestions commerciales contextuelles — discrètes, fermables, cadence limitée.
 * Prix et plafonds depuis le catalogue serveur quand disponible.
 */
(function (global) {
  const DISMISS_KEY = "hl_growth_dismiss";
  const DISMISS_MS = 7 * 24 * 60 * 60 * 1000;
  const NUDGE_SESSION = "hl_growth_nudge_shown";

  const FALLBACK = {
    candidate: {
      title: "Performance · 39 MAD / mois",
      subtitle: "40 crédits IA par mois pour le CV et les entretiens",
      bullets: [
        "40 crédits IA / mois (1 action = 1 crédit)",
        "Réécriture de profil et préparation d’entretien",
        "Sans plafond de candidatures",
      ],
      cta: "Voir Performance",
      priceNote: "Gratuit reste à 5 crédits IA / 30 jours · résiliable à tout moment",
      planCode: "PERFORMANCE",
    },
    recruiter: {
      title: "Professionnel · 299 MAD / mois",
      subtitle: "20 offres actives, 5 sièges, 100 crédits IA",
      bullets: [
        "20 offres actives (3 en Essentiel)",
        "5 sièges d’équipe (1 en Essentiel)",
        "100 crédits IA / mois",
      ],
      cta: "Voir Professionnel",
      priceNote: "Entreprise (699 MAD / mois) : offres illimitées, 25 sièges, 200 crédits IA",
      planCode: "PROFESSIONNEL",
    },
    recruiterEnterprise: {
      title: "Entreprise · 699 MAD / mois",
      subtitle: "Capacité élargie pour les équipes structurées",
      bullets: [
        "Offres actives illimitées",
        "25 sièges",
        "200 crédits IA / mois",
        "Une facture à chaque paiement",
      ],
      cta: "Voir Entreprise",
      priceNote: "Les membres déjà présents au-delà d’un plafond inférieur sont conservés",
      planCode: "ENTREPRISE",
    },
  };

  let catalog = null;

  function ensureCss() {
    if (document.getElementById("hl-growth-css")) return;
    const s = document.createElement("style");
    s.id = "hl-growth-css";
    s.textContent =
      ".hl-premium-overlay{position:fixed;inset:0;background:rgba(18,14,22,.58);z-index:9000;display:flex;align-items:center;justify-content:center;padding:1.25rem;opacity:0;pointer-events:none;visibility:hidden;transition:opacity .25s ease,visibility 0s .25s}" +
      ".hl-premium-overlay.is-open{opacity:1;pointer-events:auto;visibility:visible;transition:opacity .25s ease,visibility 0s 0s}" +
      ".hl-premium-card{background:#fff;border-radius:18px;max-width:440px;width:100%;padding:1.75rem;box-shadow:0 24px 60px rgba(0,0,0,.22);border:1px solid rgba(122,32,50,.25);transform:translateY(12px);transition:transform .28s ease}" +
      ".hl-premium-overlay.is-open .hl-premium-card{transform:none}" +
      ".hl-premium-card .eyebrow{color:#7a2032;font-size:var(--text-xs);font-weight:700;letter-spacing:.08em;text-transform:uppercase;margin:0 0 .35rem}" +
      ".hl-premium-card h3{font-family:var(--font-display);font-size:var(--text-xl);margin:0 0 .35rem;color:#1a1214}" +
      ".hl-premium-card .sub{color:#5e5558;margin:0 0 1rem;font-size:var(--text-md)}" +
      ".hl-premium-card ul{margin:0 0 1.25rem;padding:0;list-style:none}" +
      ".hl-premium-card li{padding:.45rem 0 .45rem 1.4rem;position:relative;font-size:var(--text-base);border-bottom:1px solid #eee}" +
      ".hl-premium-card li:before{content:'✓';position:absolute;left:0;color:#7a2032;font-weight:700}" +
      ".hl-premium-card li:last-child{border:0}" +
      ".hl-premium-actions{display:flex;flex-direction:column;gap:.55rem}" +
      ".hl-premium-actions .btn-primary{background:linear-gradient(135deg,#7a2032,#e87030)!important;border:0}" +
      ".hl-premium-note{font-size:var(--text-xs);color:#8a8285;text-align:center;margin-top:.35rem}" +
      ".hl-nudge{position:fixed;z-index:8800;right:1rem;bottom:1rem;max-width:min(360px,calc(100vw - 2rem));background:var(--surface,#fff);border:1px solid var(--line,rgba(122,32,50,.2));border-radius:14px;box-shadow:0 12px 36px rgba(0,0,0,.14);padding:1rem 1.1rem;opacity:0;transform:translateY(10px);transition:opacity .28s ease,transform .28s ease;pointer-events:none}" +
      ".hl-nudge.is-open{opacity:1;transform:none;pointer-events:auto}" +
      ".hl-nudge h4{margin:0 0 .25rem;font-family:var(--font-display);font-size:var(--text-md);color:var(--ink,#1a1214)}" +
      ".hl-nudge p{margin:0 0 .75rem;font-size:var(--text-xs);color:var(--ink-soft,#5e5558);line-height:1.45}" +
      ".hl-nudge-actions{display:flex;gap:.45rem;flex-wrap:wrap}" +
      ".hl-nudge-actions .btn{flex:1 1 auto}" +
      "@media (prefers-reduced-motion:reduce){.hl-premium-overlay,.hl-premium-card,.hl-nudge{transition:none!important}.hl-premium-card,.hl-nudge{transform:none!important}}";
    document.head.appendChild(s);
  }

  function dismissMap() {
    try {
      return JSON.parse(localStorage.getItem(DISMISS_KEY) || "{}") || {};
    } catch (_) {
      return {};
    }
  }

  function isDismissed(reason) {
    const until = dismissMap()[reason];
    return until && Date.now() < Number(until);
  }

  function markDismissed(reason) {
    const map = dismissMap();
    map[reason] = Date.now() + DISMISS_MS;
    try {
      localStorage.setItem(DISMISS_KEY, JSON.stringify(map));
    } catch (_) { /* private mode */ }
  }

  function loadCatalog() {
    if (catalog) return Promise.resolve(catalog);
    if (!global.HireLinkAPI) return Promise.resolve([]);
    return HireLinkAPI.api("/billing/plans")
      .then(function (rows) {
        catalog = Array.isArray(rows) ? rows : [];
        return catalog;
      })
      .catch(function () {
        catalog = [];
        return catalog;
      });
  }

  function confFromCatalog(role, planCode) {
    const key = role === "recruiter"
      ? (planCode === "ENTREPRISE" ? "recruiterEnterprise" : "recruiter")
      : "candidate";
    const base = Object.assign({}, FALLBACK[key] || FALLBACK.candidate);
    const code = planCode || base.planCode;
    const row = (catalog || []).find(function (p) {
      return String(p.code).toUpperCase() === String(code).toUpperCase();
    });
    if (!row) return base;
    const mad = Number(row.monthlyMad);
    if (!(mad > 0)) return null; // ne pas proposer une formule gratuite / expirée
    base.planCode = row.code;
    base.title = (row.label || row.code) + " · " + mad + " MAD / mois";
    if (row.credits != null) {
      base.subtitle = row.credits + " crédits IA / mois";
    }
    if (row.features) {
      const feats = String(row.features).split(/\n+/).filter(Boolean).slice(0, 4);
      if (feats.length) base.bullets = feats;
    }
    base.cta = "Voir " + (row.label || row.code);
    base.priceNote = "Sans engagement · résiliable à tout moment";
    return base;
  }

  function checkoutHref(role, planCode) {
    const plan = role === "recruiter" ? "recruiter" : "candidate";
    const inApp = /\/app\//.test(location.pathname);
    const file = (location.pathname.split("/").pop() || "index.html").split("?")[0];
    const back = /^[\w.-]+\.html$/.test(file) ? file : "";
    const code = planCode || (role === "recruiter" ? "PROFESSIONNEL" : "PERFORMANCE");
    return (
      (inApp ? "../" : "") +
      "checkout.html?plan=" +
      plan +
      "&planCode=" +
      encodeURIComponent(code) +
      (back ? "&back=" + encodeURIComponent(back) : "")
    );
  }

  function showPremium(role, planCode, opts) {
    const options = opts || {};
    const reason = options.reason || "explicit";
    if (options.respectCooldown && isDismissed(reason)) return;

    loadCatalog().then(function () {
      const conf = confFromCatalog(role, planCode);
      if (!conf) {
        if (global.HireLink) HireLink.toast("Formule", "Aucune formule payante disponible pour cette situation.", "info");
        return;
      }
      if (global.HireLinkAPI && HireLinkAPI.isPremium() && role === "candidate" && conf.planCode === "PERFORMANCE") {
        if (global.HireLink) HireLink.toast("Formule", "Performance est déjà active.", "ok");
        return;
      }
      ensureCss();
      let overlay = document.getElementById("hl-premium-overlay");
      if (!overlay) {
        overlay = document.createElement("div");
        overlay.id = "hl-premium-overlay";
        overlay.className = "hl-premium-overlay";
        document.body.appendChild(overlay);
      }
      overlay.innerHTML =
        '<div class="hl-premium-card" role="dialog" aria-modal="true" aria-labelledby="hl-prem-title">' +
        '<p class="eyebrow">Suggestion de formule</p>' +
        '<h3 id="hl-prem-title"></h3><p class="sub"></p><ul></ul>' +
        '<div class="hl-premium-actions">' +
        '<button type="button" class="btn btn-primary btn-block" id="hl-prem-cta"></button>' +
        '<button type="button" class="btn btn-ghost btn-block" id="hl-prem-later">Pas maintenant</button>' +
        "</div><p class='hl-premium-note'></p></div>";
      overlay.querySelector("h3").textContent = conf.title;
      overlay.querySelector(".sub").textContent = conf.subtitle;
      const ul = overlay.querySelector("ul");
      conf.bullets.forEach(function (b) {
        const li = document.createElement("li");
        li.textContent = b;
        ul.appendChild(li);
      });
      overlay.querySelector("#hl-prem-cta").textContent = conf.cta;
      overlay.querySelector(".hl-premium-note").textContent = conf.priceNote;
      requestAnimationFrame(function () {
        overlay.classList.add("is-open");
      });

      const card = overlay.querySelector(".hl-premium-card");
      function dismiss() {
        overlay.classList.remove("is-open");
        markDismissed(reason);
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
      overlay.querySelector("#hl-prem-cta").onclick = function () {
        markDismissed(reason);
        location.href = checkoutHref(role, conf.planCode);
      };
      overlay.onclick = function (e) {
        if (e.target === overlay) dismiss();
      };
    });
  }

  /**
   * Bandeau non bloquant (jamais sur candidature / entretien).
   * reason: offers_near_limit | seats_near_limit | ai_credits_exhausted | paid_feature
   */
  function showNudge(opts) {
    const o = opts || {};
    const reason = o.reason || "nudge";
    if (isDismissed(reason)) return false;
    try {
      if (sessionStorage.getItem(NUDGE_SESSION + ":" + reason) === "1") return false;
    } catch (_) { /* ignore */ }

    const role = o.role || "recruiter";
    const planCode = o.planCode || (role === "candidate" ? "PERFORMANCE" : "PROFESSIONNEL");
    ensureCss();
    loadCatalog().then(function () {
      const conf = confFromCatalog(role, planCode);
      if (!conf) return;
      let el = document.getElementById("hl-growth-nudge");
      if (!el) {
        el = document.createElement("aside");
        el.id = "hl-growth-nudge";
        el.className = "hl-nudge";
        el.setAttribute("role", "status");
        document.body.appendChild(el);
      }
      el.innerHTML =
        "<h4></h4><p></p>" +
        '<div class="hl-nudge-actions">' +
        '<button type="button" class="btn btn-soft btn-sm" data-nudge-cta></button>' +
        '<button type="button" class="btn btn-ghost btn-sm" data-nudge-later>Fermer</button>' +
        "</div>";
      el.querySelector("h4").textContent = o.title || conf.title;
      el.querySelector("p").textContent = o.body || conf.subtitle;
      el.querySelector("[data-nudge-cta]").textContent = o.cta || conf.cta;
      el.querySelector("[data-nudge-cta]").onclick = function () {
        markDismissed(reason);
        location.href = checkoutHref(role, conf.planCode);
      };
      el.querySelector("[data-nudge-later]").onclick = function () {
        el.classList.remove("is-open");
        markDismissed(reason);
      };
      try {
        sessionStorage.setItem(NUDGE_SESSION + ":" + reason, "1");
      } catch (_) { /* ignore */ }
      requestAnimationFrame(function () {
        el.classList.add("is-open");
      });
    });
    return true;
  }

  /** Déclencheurs contextuels depuis l’état organisation / crédits (non bloquants). */
  function maybeSuggest(context) {
    const ctx = context || {};
    // Formule sans crédits IA (Essentiel) : rien n’est « épuisé », on ne montre pas ce message.
    if (ctx.aiExhausted && ctx.aiLimit != null && Number(ctx.aiLimit) <= 0) return false;
    if (ctx.aiExhausted) {
      return showNudge({
        reason: "ai_credits_exhausted",
        role: ctx.role === "CANDIDAT" || ctx.role === "candidate" ? "candidate" : "recruiter",
        planCode: ctx.role === "CANDIDAT" || ctx.role === "candidate" ? "PERFORMANCE" : "PROFESSIONNEL",
        title: "Crédits IA épuisés ce mois",
        body: "La formule suivante relève le plafond sans attendre le 1er du mois. Les candidatures et entretiens restent disponibles.",
        cta: "Voir les formules",
      });
    }
    if (ctx.offersUsed != null && ctx.offersMax != null && ctx.offersMax > 0) {
      const left = ctx.offersMax - ctx.offersUsed;
      if (left <= 1 && left >= 0) {
        return showNudge({
          reason: "offers_near_limit",
          role: "recruiter",
          planCode: ctx.tier === "PROFESSIONNEL" || ctx.tier === "PREMIUM" ? "ENTREPRISE" : "PROFESSIONNEL",
          title: left === 0 ? "Plafond d’offres atteint" : "Plus qu’une offre active disponible",
          body: "Passer à la formule supérieure augmente le nombre d’offres actives. Aucune urgence : publiez quand vous êtes prêt.",
          cta: "Voir les formules",
        });
      }
    }
    if (ctx.seatsUsed != null && ctx.seatsMax != null && ctx.seatsMax > 0) {
      const left = ctx.seatsMax - ctx.seatsUsed;
      if (left <= 1 && left >= 0) {
        return showNudge({
          reason: "seats_near_limit",
          role: "recruiter",
          planCode: ctx.tier === "PROFESSIONNEL" || ctx.tier === "PREMIUM" ? "ENTREPRISE" : "PROFESSIONNEL",
          title: left === 0 ? "Plus de place d’équipe" : "Dernière place d’équipe disponible",
          body: "Inviter davantage de recruteurs nécessite une capacité d’équipe plus large.",
          cta: "Voir les formules",
        });
      }
    }
    return false;
  }

  global.HireLinkGrowth = {
    showPremium: showPremium,
    showNudge: showNudge,
    maybeSuggest: maybeSuggest,
  };
})(window);
