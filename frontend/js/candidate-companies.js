(function () {
  const session = HireLinkShell.mountShell({ roles: ["CANDIDAT"], hello: false });
  if (!session) return;

  function esc(s) {
    return String(s == null ? "" : s)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  function logoHtml(c) {
    const src = c.logoUrl || "";
    const letter = String(c.name || "E").trim().charAt(0).toUpperCase();
    if (!src) return '<span class="co-mark-fallback">' + esc(letter) + "</span>";
    return (
      '<img src="' +
      esc(src) +
      '" alt="" onerror="this.style.display=\'none\';this.nextElementSibling.hidden=false" />' +
      '<span class="co-mark-fallback" hidden>' +
      esc(letter) +
      "</span>"
    );
  }

  let companies = [];
  let selected = null;

  function filtered() {
    const q = ((document.getElementById("co-search") || {}).value || "").trim().toLowerCase();
    const city = ((document.getElementById("co-city") || {}).value || "").trim().toLowerCase();
    const sector = ((document.getElementById("co-sector") || {}).value || "").trim().toLowerCase();
    return companies.filter(function (c) {
      if (q && String(c.name || "").toLowerCase().indexOf(q) < 0 && String(c.city || "").toLowerCase().indexOf(q) < 0) return false;
      if (city && String(c.city || "").toLowerCase().indexOf(city) < 0) return false;
      if (sector && String(c.sector || "").toLowerCase().indexOf(sector) < 0) return false;
      return true;
    });
  }

  function paintFilters() {
    const sel = document.getElementById("co-sector");
    if (!sel) return;
    const current = sel.value;
    const sectors = [];
    companies.forEach(function (c) {
      if (c.sector && sectors.indexOf(c.sector) < 0) sectors.push(c.sector);
    });
    sel.innerHTML = '<option value="">Secteur d’activité</option>' +
      sectors.sort().map(function (s) {
        return "<option" + (s === current ? " selected" : "") + ">" + esc(s) + "</option>";
      }).join("");
  }

  function paintList() {
    const root = document.getElementById("co-list");
    const rows = filtered();
    root.innerHTML = rows.length
      ? rows.map(function (c) {
          const on = selected && selected.name === c.name ? " is-selected" : "";
          return (
            '<article class="offer co-row' + on + '" data-name="' + esc(c.name) + '">' +
            '<span class="co-mark">' + logoHtml(c) + "</span>" +
            "<div><strong>" + esc(c.name) + "</strong><p>" +
            esc(c.city || "") +
            (c.sector ? " · " + esc(c.sector) : "") +
            " · " + (c.offerCount || 0) + " offre" + ((c.offerCount || 0) > 1 ? "s" : "") +
            "</p></div></article>"
          );
        }).join("")
      : '<p class="muted">Aucune entreprise.</p>';
    root.querySelectorAll(".offer").forEach(function (el) {
      el.addEventListener("click", function () {
        selected = companies.filter(function (c) { return c.name === el.dataset.name; })[0] || null;
        paintList();
        paintDetail();
      });
    });
  }

  function paintDetail() {
    const box = document.getElementById("co-detail");
    if (!selected) {
      box.innerHTML = '<p class="muted">Choisissez une entreprise.</p>';
      return;
    }
    const jobs = (selected.offers || []).map(function (o) {
      return '<a class="reco-card" href="candidate.html?offer=' + encodeURIComponent(o.id) + '"><div><strong>' +
        esc(o.title) + "</strong><p>" + esc(o.location || "") +
        (o.contractType ? " · " + esc(o.contractType) : "") + "</p></div></a>";
    }).join("");
    box.innerHTML =
      '<div class="co-detail-head"><span class="co-mark co-mark-lg">' + logoHtml(selected) + "</span><div>" +
      "<h2>" + esc(selected.name) + "</h2>" +
      "<p class='muted'>" + esc(selected.city || "") +
      (selected.sector ? " · " + esc(selected.sector) : "") +
      (selected.employees ? " · " + esc(selected.employees) : "") +
      (selected.verified ? " · Identité vérifiée" : "") +
      "</p></div></div>" +
      "<p>" + esc(selected.presentation || "") + "</p>" +
      (selected.website ? '<p><a href="' + esc(selected.website) + '" target="_blank" rel="noopener">Site web</a></p>' : "") +
      (jobs || "<p class='muted'>Aucune offre listée pour le moment.</p>");
  }

  function attachOffers(list) {
    HireLinkAPI.api("/offers/public").then(function (offers) {
      const byName = {};
      (offers || []).forEach(function (o) {
        const name = o.companyName || o.recruiterName || "";
        if (!byName[name]) byName[name] = [];
        byName[name].push(o);
      });
      list.forEach(function (c) {
        c.offers = byName[c.name] || [];
        if (!c.offerCount) c.offerCount = c.offers.length;
      });
      paintList();
      paintDetail();
    }).catch(function () {
      paintList();
      paintDetail();
    });
  }

  HireLinkAPI.api("/organizations/public").then(function (rows) {
    companies = rows || [];
    if (companies[0]) selected = companies[0];
    paintFilters();
    attachOffers(companies);
  }).catch(function () {
    HireLinkAPI.api("/offers/public").then(function (offers) {
      const map = {};
      (offers || []).forEach(function (o) {
        const name = o.companyName || o.recruiterName || "Entreprise";
        if (!map[name]) {
          map[name] = {
            name: name,
            logoUrl: o.companyLogo || "",
            website: o.companyWebsite || "",
            sector: o.companySector || "",
            city: String(o.location || "").split(/[·,]/)[0].trim(),
            verified: !!o.companyVerified,
            offers: [],
            offerCount: 0,
            presentation: name + " recrute actuellement sur HireLink.",
          };
        }
        map[name].offers.push(o);
        map[name].offerCount += 1;
      });
      companies = Object.keys(map).sort().map(function (k) { return map[k]; });
      if (companies[0]) selected = companies[0];
      paintFilters();
      paintList();
      paintDetail();
    }).catch(function (err) {
      HireLink.toast("Entreprises", err.message, "danger");
    });
  });

  ["co-search", "co-city", "co-sector"].forEach(function (id) {
    const el = document.getElementById(id);
    if (el) el.addEventListener("input", paintList);
    if (el) el.addEventListener("change", paintList);
  });
  const filterBtn = document.getElementById("co-filter");
  if (filterBtn) filterBtn.addEventListener("click", paintList);
})();
