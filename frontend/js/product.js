/**
 * Matching alerts + premium helpers
 */
(function (global) {
  function isPremium(role) {
    return !!localStorage.getItem("hirelink.premium.active." + (role || "candidate"));
  }

  function premiumHeaders(role) {
    return isPremium(role) ? { "X-HireLink-Premium": "1" } : {};
  }

  function renderMatchAlerts(root, offers, opts) {
    if (!root) return;
    const list = (offers || [])
      .filter((o) => o.matchScore != null)
      .sort((a, b) => (b.matchScore || 0) - (a.matchScore || 0))
      .slice(0, opts && opts.limit ? opts.limit : 3);
    if (!list.length) {
      root.innerHTML =
        '<div class="alert-empty">Complétez vos compétences pour recevoir des alertes de matching.</div>';
      return;
    }
    root.innerHTML =
      '<div class="alert-head"><span class="ai-chip">Matching</span><strong>Meilleures opportunités</strong></div>' +
      list
        .map((o) => {
          const pct = Math.round(o.matchScore || 0);
          return (
            '<a class="alert-row" href="candidate-offers.html?q=' +
            encodeURIComponent(o.title || "") +
            '"><div><strong></strong><span class="muted"></span></div>' +
            '<span class="badge badge-rose">' +
            pct +
            "%</span></a>"
          );
        })
        .join("");
    Array.from(root.querySelectorAll(".alert-row")).forEach((row, i) => {
      row.querySelector("strong").textContent = list[i].title;
      row.querySelector(".muted").textContent =
        (list[i].location || "—") + " · SLA " + (list[i].responseSlaDays || "—") + " j";
    });
  }

  function mountPremiumBar(_role) {
    /* Bannière pleine largeur retirée — le bouton topbar « Passer Premium » suffit. */
    const existing = document.getElementById("hl-prem-bar");
    if (existing) existing.remove();
  }

  global.HireLinkProduct = { isPremium, premiumHeaders, renderMatchAlerts, mountPremiumBar };
})(window);
