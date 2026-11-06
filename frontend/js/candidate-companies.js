(function () {
  let query = "";
  const session = HireLinkApp.mount({ search: { placeholder: "Rechercher une entreprise…", onQuery: (q) => { query = String(q || "").trim(); paintList(); } } });
  if (!session) return;

  function esc(s) {
    return String(s == null ? "" : s)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;").replace(/'/g, "&#39;");
  }

  function logoHtml(c) {
    const src = HireLinkAPI.mediaUrl(c.logoUrl || "");
    const letter = String(c.name || "E").trim().charAt(0).toUpperCase();
    if (!src) return '<span class="co-mark-fallback">' + esc(letter) + "</span>";
    return (
      '<img src="' +
      esc(src) +
      '" alt="" data-fallback="1" />' +
      '<span class="co-mark-fallback" hidden>' +
      esc(letter) +
      "</span>"
    );
  }

  // La politique CSP interdit les attributs onerror : repli de l'image géré ici (écoute en phase de capture).
  document.addEventListener("error", function (e) {
    const t = e.target;
    if (t && t.tagName === "IMG" && t.dataset.fallback) {
      t.style.display = "none";
      if (t.nextElementSibling) t.nextElementSibling.hidden = false;
    }
  }, true);

  let companies = [];
  let selected = null;
  const params = new URLSearchParams(location.search);
  const wanted = params.get("name") || "";
  const FOLLOW_KEY = "hirelink.follow.companies";
  function followed() { try { return JSON.parse(localStorage.getItem(FOLLOW_KEY) || "[]"); } catch (_) { return []; } }
  function isFollowed(n) { return followed().indexOf(n) >= 0; }
  function toggleFollow(n) {
    const list = followed(); const i = list.indexOf(n);
    if (i >= 0) list.splice(i, 1); else list.push(n);
    try { localStorage.setItem(FOLLOW_KEY, JSON.stringify(list)); } catch (_) {}
    if (window.HireLink && HireLink.toast) HireLink.toast(i >= 0 ? "Entreprise retirée" : "Entreprise suivie", i >= 0 ? n + " ne figure plus dans vos entreprises suivies." : "Les nouvelles offres de " + n + " apparaîtront en tête de liste.", "info");
  }
  function syncUrl() {
    const u = new URL(location.href);
    if (selected) u.searchParams.set("name", selected.name); else u.searchParams.delete("name");
    u.searchParams.delete("focus");
    history.replaceState(null, "", u.pathname + u.search + u.hash);
  }

  function filtered() {
    const q = query;
    const city = ((document.getElementById("co-city") || {}).value || "").trim().toLowerCase();
    const sector = ((document.getElementById("co-sector") || {}).value || "").trim().toLowerCase();
    return HireLinkSearch.rank(companies, q, [{ get: function (c) { return c.name; }, weight: 3 }, { get: function (c) { return c.city; }, weight: 2 }, { get: function (c) { return c.sector; }, weight: 1 }]).filter(function (c) {
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
    sel.innerHTML = '<option value="">Tous les secteurs</option>' +
      sectors.sort().map(function (s) {
        return "<option" + (s === current ? " selected" : "") + ">" + esc(s) + "</option>";
      }).join("");
  }

  function paintList() {
    const root = document.getElementById("co-list");
    const rows = filtered();
    document.getElementById("co-summary").textContent = HireLinkApp.plural(rows.length, "entreprise") + " qui recrutent actuellement sur HireLink.";
    root.innerHTML = rows.length
      ? rows.map(function (c) {
          const on = selected && selected.name === c.name ? " is-selected" : "";
          return (
            '<article class="offer co-row' + on + '" data-name="' + esc(c.name) + '">' +
            '<span class="co-mark">' + logoHtml(c) + "</span>" +
            "<div><strong>" + esc(c.name) + (isFollowed(c.name) ? ' <span class="co-follow-dot" title="Entreprise suivie">Suivie</span>' : "") + "</strong><p>" +
            esc(c.city || "") +
            (c.sector ? " · " + esc(c.sector) : "") +
            " · " + (c.offerCount || 0) + " offre" + ((c.offerCount || 0) > 1 ? "s" : "") +
            "</p></div></article>"
          );
        }).join("")
      : "";
    if (!rows.length) HireLinkSearch.emptyState(root, query, { emptyTitle: "Aucune entreprise ne correspond", onClear: function () { document.getElementById("rc-search").value = ""; query = ""; paintList(); } });
    else HireLinkSearch.mark(root, query);
    root.querySelectorAll(".offer").forEach(function (el) {
      el.addEventListener("click", function () {
        selected = companies.filter(function (c) { return c.name === el.dataset.name; })[0] || null;
        paintList();
        paintDetail();
        syncUrl();
        const box = document.getElementById("co-detail");
        if (box && window.innerWidth < 900) box.scrollIntoView({ behavior: "smooth", block: "start" });
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
      const score = o.matchScore == null ? "" : '<span class="rc-pill">' + Math.round(o.matchScore) + " %</span>";
      return '<div class="rc-item co-job"><div class="rc-item-main"><strong>' +
        esc(o.title) + "</strong><span>" + esc(HireLinkPlaces.cityOf(o.location, o.companyCity)) +
        (o.contractType ? " · " + esc(o.contractType) : "") + "</span></div>" + score +
        '<a class="btn btn-soft btn-sm" href="candidate.html?offer=' + encodeURIComponent(o.id) + '">Voir</a>' +
        '<a class="btn btn-primary btn-sm" href="candidate.html?apply=' + encodeURIComponent(o.id) + '">Postuler</a></div>';
    }).join("");
    const on = isFollowed(selected.name);
    box.innerHTML =
      '<div class="co-detail-anim" data-focus="company:' + esc(selected.name) + '"><div class="co-detail-cover" aria-hidden="true"></div><div class="co-detail-head"><span class="co-mark co-mark-lg">' + logoHtml(selected) + "</span><div>" +
      "<h2>" + esc(selected.name) + "</h2>" +
      "<p class='muted'>" + esc(selected.city || "") +
      (selected.sector ? " · " + esc(selected.sector) : "") +
      (selected.employees ? " · " + esc(selected.employees) : "") +
      (selected.verified ? " · Identité vérifiée" : "") +
      "</p></div>" +
      '<a class="btn btn-soft btn-sm" href="../company.html?name=' + encodeURIComponent(selected.name) + '" target="_blank" rel="noopener">Page publique</a>' +
      '<button type="button" class="btn btn-sm ' + (on ? "btn-soft" : "btn-primary") + ' co-follow" aria-pressed="' + on + '">' + (on ? "✓ Suivie" : "+ Suivre") + "</button></div>" +
      "<p>" + esc(selected.presentation || "") + "</p>" +
      (selected.website ? '<p><a href="' + esc(selected.website) + '" target="_blank" rel="noopener">Site web</a></p>' : "") +
      '<h3 class="co-jobs-title">' + ((selected.offers || []).length ? HireLinkApp.plural(selected.offers.length, "offre ouverte", "offres ouvertes") : "Offres") + "</h3>" +
      (jobs ? '<div class="rc-list">' + jobs + "</div>" : "<p class='muted'>Aucune offre ouverte pour le moment. Suivez l’entreprise pour la retrouver en tête de liste.</p>") + "</div>";
    const fb = box.querySelector(".co-follow");
    if (fb) fb.addEventListener("click", function () { toggleFollow(selected.name); paintList(); paintDetail(); });
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
      if (wanted && selected && selected.name === wanted) {
        const row = document.querySelector('.co-row.is-selected');
        if (row) row.scrollIntoView({ behavior: "smooth", block: "center" });
        const d = document.querySelector(".co-detail-anim");
        if (d) { d.classList.add("hl-spot-once"); setTimeout(function () { d.classList.remove("hl-spot-once"); }, 2400); }
      }
    }).catch(function () {
      paintList();
      paintDetail();
    });
  }

  HireLinkAPI.api("/organizations/public").then(function (rows) {
    companies = rows || [];
    selected = companies.filter(function (c) { return c.name === wanted; })[0] || companies[0] || null;
    paintFilters();
    attachOffers(companies);
  }).catch(function (err) {
    HireLinkApp.errorState(document.getElementById("co-list"), err.message, function () { location.reload(); });
  });

  ["co-city", "co-sector"].forEach(function (id) {
    const el = document.getElementById(id);
    if (el) el.addEventListener("input", paintList);
    if (el) el.addEventListener("change", paintList);
  });
})();
