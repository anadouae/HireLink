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
  let view = "all";
  let followBusy = false;
  // Id d'organisation → date de l'abonnement, relue sur le serveur. Rien en localStorage :
  // sans persistance, la promesse du bouton « Suivre » serait fausse des le changement de machine.
  const follows = new Map();
  const params = new URLSearchParams(location.search);
  const wanted = params.get("name") || "";

  function followSince(c) {
    return c && c.id != null && follows.has(String(c.id)) ? follows.get(String(c.id)) : null;
  }
  function syncUrl() {
    const u = new URL(location.href);
    if (selected) u.searchParams.set("name", selected.name); else u.searchParams.delete("name");
    u.searchParams.delete("focus");
    history.replaceState(null, "", u.pathname + u.search + u.hash);
  }

  /** Lignes retenues par la recherche et par les filtres ville / secteur, hors filtre de suivi. */
  function visible() {
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

  /** Le compteur d'une pastille egale les lignes que cette pastille affiche vraiment. */
  function paintChips(allCount, followedCount) {
    const box = document.getElementById("co-chips");
    if (!box) return;
    box.querySelectorAll(".rc-chip").forEach(function (b) {
      const on = b.dataset.v === view;
      b.classList.toggle("is-on", on);
      b.setAttribute("aria-pressed", on ? "true" : "false");
      let em = b.querySelector("em");
      if (!em) {
        em = document.createElement("em");
        b.appendChild(em);
      }
      em.textContent = b.dataset.v === "following" ? followedCount : allCount;
    });
  }

  function rowsFor(base) {
    const followed = base.filter(function (c) { return followSince(c) != null; });
    const others = base.filter(function (c) { return followSince(c) == null; });
    if (view === "following") return followed;
    return followed.concat(others);
  }

  function paintList() {
    const root = document.getElementById("co-list");
    const base = visible();
    const followedCount = base.filter(function (c) { return followSince(c) != null; }).length;
    paintChips(base.length, followedCount);
    const rows = rowsFor(base);
    document.getElementById("co-summary").textContent = view === "following"
      ? HireLinkApp.plural(rows.length, "entreprise suivie", "entreprises suivies") + " — une notification vous prévient à chaque nouvelle offre."
      : HireLinkApp.plural(rows.length, "entreprise") + " qui recrutent actuellement sur HireLink.";
    root.innerHTML = rows.length
      ? rows.map(function (c) {
          const on = selected && selected.name === c.name ? " is-selected" : "";
          return (
            '<article class="offer co-row' + on + '" data-name="' + esc(c.name) + '">' +
            '<span class="co-mark">' + logoHtml(c) + "</span>" +
            "<div><strong>" + esc(c.name) + (followSince(c) ? ' <span class="co-follow-dot" title="Entreprise suivie">Suivie</span>' : "") + "</strong><p>" +
            esc(c.city || "") +
            (c.sector ? " · " + esc(c.sector) : "") +
            " · " + (c.offerCount || 0) + " offre" + ((c.offerCount || 0) > 1 ? "s" : "") +
            "</p></div></article>"
          );
        }).join("")
      : "";
    if (!rows.length) {
      if (view === "following" && !query) {
        root.innerHTML = '<div class="rc-state rc-fade"><h3>Aucune entreprise suivie</h3><p>Suivez une entreprise depuis sa fiche pour retrouver ses nouvelles offres ici et dans vos notifications.</p><button type="button" class="btn btn-soft btn-sm" data-show-all>Toutes les entreprises</button></div>';
        const b = root.querySelector("[data-show-all]");
        if (b) b.addEventListener("click", function () { view = "all"; paintList(); });
      } else {
        HireLinkSearch.emptyState(root, query, { emptyTitle: "Aucune entreprise ne correspond", onClear: function () { document.getElementById("rc-search").value = ""; query = ""; paintList(); } });
      }
    } else HireLinkSearch.mark(root, query);
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
        '<a class="btn btn-primary btn-sm" href="candidate.html?apply=' + encodeURIComponent(o.id) + '">Postuler sur HireLink</a></div>';
    }).join("");
    const since = followSince(selected);
    // Fiche sans identifiant d'organisation (offre d'un recruteur sorti de son equipe) : rien a suivre cote serveur.
    const canFollow = selected.id != null;
    const openCount = (selected.offers || []).length;
    const stat = function (value, label, isDate) {
      return '<div class="co-detail-stat"><strong' + (isDate ? ' class="is-date"' : "") + ">" + value + "</strong><span>" + label + "</span></div>";
    };
    box.innerHTML =
      '<div class="co-detail-anim" data-focus="company:' + esc(selected.name) + '"><div class="co-detail-cover" aria-hidden="true"></div><div class="co-detail-head"><span class="co-mark co-mark-lg">' + logoHtml(selected) + "</span><div>" +
      "<h2>" + esc(selected.name) + "</h2>" +
      "<p class='muted'>" + esc(selected.city || "") +
      (selected.sector ? " · " + esc(selected.sector) : "") +
      (selected.employees ? " · " + esc(String(selected.employees).replace("-", " à ") + " salariés") : "") +
      (selected.verified ? " · Identité vérifiée" : "") +
      "</p>" +
      "</div>" +
      '<a class="btn btn-soft btn-sm" href="../company.html?name=' + encodeURIComponent(selected.name) + '">Page publique</a>' +
      (canFollow ? '<button type="button" class="btn btn-sm ' + (since ? "btn-soft" : "btn-primary") + ' co-follow">' + (since ? "✓ Suivie" : "+ Suivre") + "</button>" : "") +
      "</div>" +
      '<div class="co-detail-stats">' +
      stat(openCount, openCount > 1 ? "offres en ligne" : "offre en ligne") +
      (since ? stat(esc(HireLinkApp.fmtDate(since)), "suivie depuis", true) : "") +
      "</div>" +
      "<p>" + esc(selected.presentation || "") + "</p>" +
      (selected.website ? '<p><a href="' + esc(selected.website) + '" target="_blank" rel="noopener">Site web</a></p>' : "") +
      '<h3 class="co-jobs-title">' + ((selected.offers || []).length ? HireLinkApp.plural(selected.offers.length, "offre ouverte", "offres ouvertes") : "Offres") + "</h3>" +
      (jobs ? '<div class="rc-list">' + jobs + "</div>" : (since ? "<p class='muted'>Aucune offre ouverte pour le moment. Vous serez prévenu dès qu'une nouvelle offre est publiée.</p>" : "<p class='muted'>Aucune offre ouverte pour le moment. Suivez l’entreprise pour être prévenu à chaque nouvelle offre.</p>")) + "</div>";
    const fb = box.querySelector(".co-follow");
    if (fb) fb.addEventListener("click", function () { toggleFollow(selected); });
  }

  async function toggleFollow(company) {
    if (followBusy || !company || company.id == null) return;
    followBusy = true;
    HireLinkApp.busy(document.querySelector(".co-follow"), true);
    const wasFollowed = followSince(company) != null;
    try {
      await HireLinkAPI.api("/follows/" + company.id, { method: wasFollowed ? "DELETE" : "POST" });
      if (wasFollowed) follows.delete(String(company.id));
      else follows.set(String(company.id), new Date().toISOString());
      HireLink.toast(
        wasFollowed ? "Entreprise retirée" : "Entreprise suivie",
        wasFollowed
          ? company.name + " ne figure plus dans vos entreprises suivies."
          : "Vous recevrez une notification dès qu’une nouvelle offre sera publiée par " + company.name + ".",
        "info"
      );
    } catch (err) {
      // En cas de desaccord, le serveur gagne : on ressynchronise l'affichage au lieu de le contredire.
      if (err.status === 409 && !wasFollowed) follows.set(String(company.id), new Date().toISOString());
      else if (err.status === 404 && wasFollowed) follows.delete(String(company.id));
      else if (err.status !== 401) HireLink.toast("Le suivi n'a pas été enregistré", err.message, "warn");
    } finally {
      followBusy = false;
      paintList();
      paintDetail();
    }
  }

  async function loadFollows() {
    try {
      const rows = (await HireLinkAPI.api("/follows")) || [];
      rows.forEach(function (f) { if (f && f.organizationId != null) follows.set(String(f.organizationId), f.since); });
    } catch (err) {
      // 403 = role non candidat : la liste des entreprises reste utilisable sans les suivis.
      if (err.status !== 401 && err.status !== 403) HireLink.toast("Vos suivis sont indisponibles", err.message, "warn");
    }
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

  async function boot() {
    try {
      companies = (await HireLinkAPI.api("/organizations/public")) || [];
    } catch (err) {
      HireLinkApp.errorState(document.getElementById("co-list"), err.message, function () { location.reload(); });
      return;
    }
    selected = companies.filter(function (c) { return c.name === wanted; })[0] || companies[0] || null;
    paintFilters();
    await loadFollows();
    attachOffers(companies);
  }

  const chips = document.getElementById("co-chips");
  if (chips) {
    chips.querySelectorAll(".rc-chip").forEach(function (b) {
      b.addEventListener("click", function () {
        view = b.dataset.v === "following" ? "following" : "all";
        paintList();
      });
    });
  }

  boot();

  ["co-city", "co-sector"].forEach(function (id) {
    const el = document.getElementById(id);
    if (el) el.addEventListener("input", paintList);
    if (el) el.addEventListener("change", paintList);
  });
})();
