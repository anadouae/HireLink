/**
 * Détail du score de compatibilité, critère par critère (compétences obligatoires et appréciées, langues,
 * expérience, contrat, lieu). Le score aide à trier ; il ne prend aucune décision.
 *
 *   HireLinkMatch.panel(breakdown, { audience: "candidate" | "recruiter", compact }) → HTMLElement | null
 *   HireLinkMatch.tier(score) → { cls, label }
 */
(function (global) {
  function el(tag, cls, text) {
    const n = document.createElement(tag);
    if (cls) n.className = cls;
    if (text != null) n.textContent = text;
    return n;
  }

  function tier(score) {
    if (score == null) return { cls: "is-none", label: "Non calculé" };
    if (score >= 80) return { cls: "is-high", label: "Très compatible" };
    if (score >= 60) return { cls: "is-good", label: "Compatible" };
    if (score >= 40) return { cls: "is-mid", label: "Partiellement compatible" };
    return { cls: "is-low", label: "Peu compatible" };
  }

  function fmt(n) {
    return (Math.round(n * 10) / 10).toLocaleString("fr-FR");
  }

  function chips(list, cls) {
    const wrap = el("div", "hm-chips");
    (list || []).forEach((s) => wrap.appendChild(el("span", "hm-chip " + cls, s)));
    return wrap;
  }

  function panel(b, opts) {
    if (!b || !Array.isArray(b.criteria)) return null;
    opts = opts || {};
    const cand = opts.audience === "candidate";
    const t = tier(b.score);
    const root = el("section", "hm-panel " + t.cls + (opts.compact ? " is-compact" : ""));
    root.setAttribute("aria-label", "Détail de la compatibilité");

    const head = el("div", "hm-head");
    const ring = el("div", "hm-ring");
    ring.style.setProperty("--m", Math.max(0, Math.min(100, b.score)));
    ring.appendChild(el("strong", null, Math.round(b.score) + " %"));
    const txt = el("div", "hm-head-txt");
    txt.appendChild(el("h4", null, t.label));
    txt.appendChild(el("p", "muted", cand
      ? "Calculé à partir de votre profil et des exigences de l’offre."
      : "Calculé à partir du profil du candidat et des exigences de l’offre."));
    head.append(ring, txt);
    root.appendChild(head);

    if (b.capped) {
      const raw = b.rawScore != null ? Math.round(b.rawScore) : null;
      root.appendChild(el("p", "hm-cap",
        (raw != null ? "Total des critères : " + raw + " %. " : "")
        + "Plafond appliqué : moins de la moitié des compétences obligatoires sont couvertes. Score retenu : "
        + Math.round(b.score) + " %. Les points de chaque critère restent ceux d'avant le plafond."));
    }

    const list = el("ol", "hm-list");
    const applicable = b.criteria.filter((c) => c.applicable);
    const totalW = applicable.reduce((s, c) => s + c.weight, 0) || 1;
    b.criteria.forEach((c) => {
      const li = el("li", "hm-row" + (c.applicable ? "" : " is-na"));
      const top = el("div", "hm-row-top");
      top.appendChild(el("span", "hm-label", c.label));
      const w = c.applicable ? Math.round((100 * c.weight) / totalW) : 0;
      top.appendChild(el("span", "hm-weight", c.applicable ? "poids " + w + " %" : "non pris en compte"));
      top.appendChild(el("span", "hm-pts", c.applicable ? "+" + fmt(c.points) + " / " + w : "—"));
      li.appendChild(top);
      if (c.applicable) {
        const bar = el("div", "hm-bar");
        const fill = el("i");
        const r = Math.round((c.ratio || 0) * 100);
        fill.style.width = r + "%";
        fill.className = r >= 80 ? "is-high" : r >= 50 ? "is-mid" : "is-low";
        bar.appendChild(fill);
        bar.setAttribute("role", "img");
        bar.setAttribute("aria-label", c.label + " : " + r + " %");
        li.appendChild(bar);
      }
      if (c.detail) li.appendChild(el("p", "hm-detail", c.detail));
      if ((c.matched && c.matched.length) || (c.missing && c.missing.length)) {
        const row = el("div", "hm-skill-row");
        if (c.matched && c.matched.length) row.appendChild(chips(c.matched, "is-ok"));
        if (c.missing && c.missing.length) row.appendChild(chips(c.missing, "is-miss"));
        li.appendChild(row);
      }
      list.appendChild(li);
    });
    root.appendChild(list);
    root.appendChild(el("p", "hm-foot", cand
      ? "Ce score sert uniquement à classer les offres. Le recruteur lit chaque dossier : aucune décision n’est automatique."
      : "Aide au tri uniquement : aucune décision automatique. Lisez le dossier avant de trancher."));
    return root;
  }

  global.HireLinkMatch = { panel, tier };
})(window);
