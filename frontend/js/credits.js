/**
 * Crédits IA : ce que coûte chaque action, ce qui reste, et ce que la formule suivante apporte.
 * Le solde vient toujours du serveur (/ai/credits) — jamais d'un compteur local.
 */
(function (global) {
  const FEATURES = {
    "cv-summary": "Synthèse de CV",
    "interview-kit": "Grille d’entretien",
    "interview-prep": "Préparation d’entretien",
    "refusal-draft": "Brouillon de refus",
    "profile-rewrite": "Réécriture de profil",
    "match-explain": "Explication du matching",
    "language-check": "Relecture de l’offre",
    coach: "Coach CV",
  };

  const PLANS_HREF = { RECRUTEUR: "recruiter-settings.html#plans", CANDIDAT: "candidate-settings.html#plans", ADMIN: "admin.html#plans" };
  /** Repli si le catalogue n'est pas joignable : le palier payant de chaque espace. */
  const NEXT_PLAN = { RECRUTEUR: "Premium · 299 MAD / mois", CANDIDAT: "Performance · 39 MAD / mois" };

  let status = null;
  let plans = null;
  let pending = null;
  let chip = null;
  let panel = null;
  let shown = 0; // valeur affichée par l'anneau, animée vers le solde réel

  function esc(s) {
    return String(s == null ? "" : s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
  }

  function label(feature) {
    return FEATURES[feature] || feature;
  }

  function cost() {
    return status && status.costPerAction != null ? status.costPerAction : 1;
  }

  function load(force) {
    if (status && !force) return Promise.resolve(status);
    if (pending && !force) return pending;
    pending = global.HireLinkAPI.api("/ai/credits")
      .then(function (c) { pending = null; status = c; paint(); return c; })
      .catch(function () { pending = null; return null; });
    loadPlans();
    return pending;
  }

  /** Prix et plafond du catalogue administrable — la comparaison gratuit/payant ne doit pas être un texte figé. */
  function loadPlans() {
    if (plans) return Promise.resolve(plans);
    return global.HireLinkAPI.api("/billing/plans")
      .then(function (rows) { plans = rows || []; return plans; })
      .catch(function () { return []; });
  }

  /** Le palier payant de la même audience, avec son prix et son plafond réels. */
  function nextPlan() {
    const audience = roleNow() === "CANDIDAT" ? "CANDIDAT" : "RECRUTEUR";
    const up = (plans || [])
      .filter(function (p) { return p.audience === audience && Number(p.monthlyMad) > 0 && (!status || p.code !== status.plan); })
      .sort(function (a, b) { return Number(a.monthlyMad) - Number(b.monthlyMad); })[0];
    if (!up) return NEXT_PLAN[audience === "CANDIDAT" ? "CANDIDAT" : "RECRUTEUR"];
    return up.label + " · " + up.monthlyMad + " MAD / mois · " + up.credits + " crédits";
  }

  /** Après une action IA : on resynchronise et l'anneau montre la baisse. */
  function spend() {
    return load(true);
  }

  function blocked() {
    if (!status) return false;
    return status.allowed === false || status.remaining < cost();
  }

  /* ------------------------------------------------------------------ compteur */

  function pct(value) {
    if (!status || !status.limit) return 0;
    return Math.max(0, Math.min(100, (value / status.limit) * 100));
  }

  function applyValue(value) {
    if (!chip) return;
    chip.style.setProperty("--p", String(pct(value)));
    const num = chip.querySelector(".rc-credits-num");
    if (num) num.textContent = String(Math.round(value));
  }

  function tween(target) {
    const from = shown;
    // un onglet caché ne reçoit pas requestAnimationFrame : on pose directement la valeur finale
    if (document.hidden || from === target) { shown = target; applyValue(target); return; }
    const start = performance.now();
    const step = function (now) {
      const k = Math.min(1, (now - start) / 650);
      const eased = 1 - Math.pow(1 - k, 3);
      shown = from + (target - from) * eased;
      applyValue(shown);
      if (k < 1) requestAnimationFrame(step);
      else { shown = target; applyValue(target); }
    };
    requestAnimationFrame(step);
  }

  function paint() {
    if (!chip || !status) return;
    const low = status.remaining <= 1;
    chip.classList.toggle("is-low", low);
    chip.classList.toggle("is-out", !status.allowed);
    chip.title = summary();
    const lim = chip.querySelector(".rc-credits-limit");
    if (lim) lim.textContent = "/ " + status.limit;
    tween(status.remaining);
    if (panel && panel.classList.contains("is-open")) panel.innerHTML = panelHtml();
  }

  function summary() {
    if (!status) return "Crédits IA";
    const paid = status.monthlyMad > 0;
    return (
      "Formule " + status.planLabel + (paid ? " (" + status.monthlyMad + " MAD / mois)" : " (gratuite)") +
      " · " + status.remaining + " crédit" + (status.remaining > 1 ? "s" : "") + " restant" + (status.remaining > 1 ? "s" : "") +
      " sur " + status.limit + " ce mois"
    );
  }

  function panelHtml() {
    const s = status;
    if (!s) return "<p>Crédits IA indisponibles.</p>";
    const rows = (s.byFeature || []).slice(0, 6).map(function (f) {
      return '<li><span>' + esc(label(f.feature)) + '</span><b>' + f.actions +
        " action" + (f.actions > 1 ? "s" : "") + " · " + f.credits + " crédit" + (f.credits > 1 ? "s" : "") + '</b></li>';
    }).join("");
    const history = s.used
      ? '<h4>Ce mois-ci</h4><ul class="rc-credits-list">' + rows + "</ul>"
      : "<h4>Ce mois-ci</h4><p>Rien n’a encore été consommé.</p>";
    const upsell = s.allowed ? "" :
      '<p class="rc-credits-warn">Plus de crédit avant le rechargement, dans ' + s.daysToReset +
      " jour" + (s.daysToReset > 1 ? "s" : "") + ".</p>";
    const free = !s.monthlyMad;
    return (
      "<h3>" + esc(s.planLabel) + (s.monthlyMad > 0 ? " · " + s.monthlyMad + " MAD / mois" : " · gratuite") + "</h3>" +
      '<p class="rc-credits-balance"><b>' + s.remaining + "</b> / " + s.limit + " crédits restants</p>" +
      "<p>" + s.costPerAction + " crédit par action IA réussie. Une action qui échoue n’est jamais débitée.</p>" +
      history + upsell +
      (free || !s.allowed ? '<p class="rc-credits-next">Formule suivante : ' + esc(nextPlan()) + "</p>" : "") +
      '<a class="btn btn-soft btn-sm" href="" data-credits-plans>Voir les formules</a>'
    );
  }

  function mountMeter(host) {
    if (!host || chip) return;
    chip = document.createElement("button");
    chip.type = "button";
    chip.id = "rc-credits";
    chip.className = "rc-credits";
    chip.setAttribute("aria-haspopup", "true");
    chip.innerHTML =
      '<span class="rc-credits-ring"></span>' +
      '<span class="rc-credits-num">0</span>' +
      '<span class="rc-credits-limit">/ —</span>';
    host.insertBefore(chip, host.firstChild);
    panel = document.createElement("div");
    panel.className = "rc-credits-panel";
    panel.hidden = true;
    host.appendChild(panel);

    chip.addEventListener("click", function (e) {
      e.stopPropagation();
      const open = !panel.hidden;
      panel.hidden = open;
      chip.classList.toggle("is-open", open);
      if (!open) { panel.innerHTML = panelHtml(); wirePanel(); }
    });
    document.addEventListener("click", function () { panel.hidden = true; chip.classList.remove("is-open"); });
    document.addEventListener("keydown", function (e) { if (e.key === "Escape") { panel.hidden = true; chip.classList.remove("is-open"); } });
    load();
  }

  function wirePanel() {
    const link = panel.querySelector("[data-credits-plans]");
    if (!link) return;
    link.href = PLANS_HREF[roleNow()] || PLANS_HREF.RECRUTEUR;
  }

  /* ------------------------------------------------------------------ appels */

  /** Étiquette de prix réutilisable pour les boutons construits en JavaScript. */
  function costNote() {
    const tag = document.createElement("small");
    tag.className = "rc-ai-cost";
    const c = cost();
    tag.textContent = c + " crédit" + (c > 1 ? "s" : "");
    return tag;
  }

  /**
   * Les boutons qui déclenchent une action IA portent data-ai-cost : on y inscrit le prix réel.
   * Un bouton sans crédit disponible est neutralisé avec l'accès aux formules, pas désactivé en silence.
   */
  function paintCosts(root) {
    const scope = root || document;
    return load().then(function () {
      scope.querySelectorAll("[data-ai-cost]").forEach(function (btn) {
        if (!status || btn.querySelector(".rc-ai-cost")) return;
        btn.appendChild(costNote());
      });
    });
  }

  function roleNow() {
    const attr = document.body.getAttribute("data-role");
    if (attr && attr !== "ANY") return attr;
    return (global.HireLinkAPI.getSession() || {}).role || "";
  }

  /** Ouvre la passerelle de formule quand le serveur a répondu 402. Renvoie true si c'était un 402. */
  function guard(err) {
    if (!err || err.status !== 402) return false;
    load(true);
    const s = status || {};
    const role = roleNow();
    const next = nextPlan();
    global.HireLink.openModal({
      title: "Crédits IA épuisés pour ce mois",
      description: err.message || "",
      bodyHtml:
        "<p>Chaque action IA réussie consomme 1 crédit. Le compteur repart le 1er du mois, dans " +
        (s.daysToReset || 30) + " jours.</p>" +
        "<p>" + esc(next) + " relève le plafond sans attendre.</p>",
      confirmLabel: "Voir les formules",
      cancelLabel: "Plus tard",
      onConfirm: function () {
        location.href = PLANS_HREF[role] || PLANS_HREF.RECRUTEUR;
        return false;
      },
    });
    return true;
  }

  /** Avant de lancer une action : vrai si le crédit est là, sinon la passerelle s'ouvre et ça retourne faux. */
  function preflight() {
    if (!blocked()) return true;
    const s = status || {};
    guard({
      status: 402,
      message: "Il vous reste " + (s.remaining != null ? s.remaining : 0) + " crédit" + (s.remaining > 1 ? "s" : "") +
        " sur " + (s.limit || 0) + " ce mois.",
    });
    return false;
  }

  /** Une action IA de bout en bout : prévol, appel, resynchronisation du solde, passerelle sur 402. */
  function run(endpoint, body) {
    if (!preflight()) {
      return Promise.reject(Object.assign(new Error("Crédits IA épuisés pour ce mois"), { handled: true }));
    }
    return global.HireLinkAPI.api(endpoint, { method: "POST", body: body || {} }).then(function (data) {
      spend();
      return data;
    }, function (err) {
      if (guard(err)) err.handled = true;
      throw err;
    });
  }

  global.HireLinkCredits = {
    load: load,
    spend: spend,
    run: run,
    status: function () { return status; },
    cost: cost,
    blocked: blocked,
    label: label,
    mountMeter: mountMeter,
    paintCosts: paintCosts,
    costNote: costNote,
    guard: guard,
    preflight: preflight,
  };
})(window);
