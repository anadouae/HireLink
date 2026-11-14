(function () {
  const root = document.getElementById("public-offers");
  if (!root) return;
  const base = window.HIRELINK_API_BASE || "http://127.0.0.1:8081/api/v1";
  const PAGE_SIZE = 9;
  const moreBox = document.getElementById("offers-more");
  const moreBtn = document.getElementById("offers-more-btn");
  let matches = [];
  let shown = 0;
  const METIERS = [
    ["Développement", "developpeur"],
    ["Data", "data"],
    ["Design", "design"],
    ["Ressources humaines", "recrutement"],
    ["Finance", "comptable"],
    ["Commerce", "commercial"],
    ["Industrie", "ingenieur"],
    ["Support", "support"]
  ];
  let offers = [];
  let mode = "offers";
  let companyCity = "";

  function esc(s) {
    return String(s == null ? "" : s)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/\'/g, "&#39;");
  }

  function companyOf(o) {
    return o.companyName || o.recruiterName || "Entreprise";
  }

  function cityOf(o) {
    if (o.external) return o.location || "";
    return HireLinkPlaces.cityOf(o.location, o.companyCity) || "Lieu à préciser";
  }

  function ago(iso) {
    if (!iso) return "";
    const then = new Date(iso).getTime();
    if (!then) return "";
    const hours = Math.max(1, Math.round((Date.now() - then) / 3600000));
    if (hours < 24) return "il y a " + hours + " h";
    const days = Math.round(hours / 24);
    return days <= 1 ? "il y a 1 jour" : "il y a " + days + " jours";
  }

  function catalogParams(pageNum) {
    const params = new URLSearchParams();
    params.set("page", String(pageNum));
    params.set("size", String(PAGE_SIZE));
    params.set("country", ((document.getElementById("q-country") || {}).value) || "MA");
    const q = (document.getElementById("q-text") || {}).value || "";
    if (q.trim()) params.set("q", q.trim());
    const city = (document.getElementById("q-city") || {}).value || "";
    if (city.trim()) params.set("city", city.trim());
    const contract = (document.getElementById("q-contract") || {}).value || "";
    if (contract) params.set("contract", contract);
    const origin = (document.getElementById("q-origin") || {}).value || "";
    if (origin === "hirelink") params.set("origin", "internal");
    else if (origin === "external") params.set("origin", "external");
    const since = (document.getElementById("q-since") || {}).value || "";
    if (since) params.set("sinceDays", since);
    return params.toString();
  }

  let page = 0;
  let totalElements = 0;
  let totalPages = 0;
  let loading = false;

  function showMoreOffers() {
    if (loading) return;
    if (page + 1 >= totalPages) {
      moreBox.hidden = true;
      return;
    }
    page += 1;
    fetchPage(true);
  }

  function paintOffers(list, reset) {
    const lead = document.getElementById("offers-lead");
    if (lead) {
      lead.textContent = totalElements
        ? totalElements + " offre" + (totalElements > 1 ? "s" : "") + " correspondent a la recherche (pagination serveur)."
        : "Aucune offre pour cette recherche.";
    }
    if (reset) {
      root.replaceChildren();
      root.classList.remove("card-grid");
      shown = 0;
    }
    moreBox.hidden = true;
    if (!list.length && reset) {
      HireLinkSearch.emptyState(root, document.getElementById("q-text").value.trim(), {
        emptyTitle: "Aucune offre pour cette recherche",
        onClear: function () {
          ["q-text", "q-city", "q-contract", "q-origin", "q-since"].forEach(function (id) {
            const el = document.getElementById(id);
            if (el) el.value = "";
          });
          const c = document.getElementById("q-country");
          if (c) c.value = "MA";
          applyOffers();
        },
      });
      return;
    }
    list.forEach(function (o) {
      const published = o.publishedAtTrusted ? o.publishedAt : null;
      root.appendChild(HireLinkOffer.card(o, {
        variant: "showcase",
        badge: o.external ? "Offre externe" : "Offre HireLink",
        footText: o.external
          ? (HireLinkOffer.externalLinkPolicy(o).trusted ? HireLinkOffer.externalLinkPolicy(o).label : "Lien non garanti")
          : ("Postuler sur HireLink" + (ago(published) ? " · " + ago(published) : "")),
        href: function () {
          if (!o.external) return "auth.html?mode=login";
          const p = HireLinkOffer.externalLinkPolicy(o);
          return p.trusted ? p.href : (p.href || "#");
        },
      }));
    });
    shown += list.length;
    HireLinkSearch.mark(root, document.getElementById("q-text").value.trim());
    const leftPages = totalPages - (page + 1);
    moreBox.hidden = leftPages <= 0;
    moreBtn.textContent = leftPages > 0
      ? ("Afficher " + Math.min(PAGE_SIZE, Math.max(0, totalElements - shown)) + " offre(s) de plus")
      : "Fin du catalogue";
  }

  function fetchPage(append) {
    loading = true;
    if (!append) {
      root.replaceChildren();
      const sk = document.createElement("div");
      sk.className = "skeleton";
      sk.style.height = "96px";
      root.appendChild(sk);
      page = 0;
      shown = 0;
    }
    return fetch(base + "/catalog/offers?" + catalogParams(page))
      .then(function (r) {
        if (r.status === 404 || r.status === 401 || r.status === 403 || !r.ok) {
          return Promise.all([
            fetch(base + "/offers/public").then(function (x) { if (!x.ok) throw new Error("offres"); return x.json(); }),
            fetch(base + "/external-offers").then(function (x) { return x.ok ? x.json() : []; }).catch(function () { return []; })
          ]).then(function (parts) {
            const internal = (Array.isArray(parts[0]) ? parts[0] : []).map(function (o) { o.external = false; return o; });
            const external = (Array.isArray(parts[1]) ? parts[1] : []).map(function (o) { o.external = true; return o; });
            const all = internal.concat(external);
            totalElements = all.length;
            totalPages = Math.max(1, Math.ceil(totalElements / PAGE_SIZE));
            const start = page * PAGE_SIZE;
            return { items: all.slice(start, start + PAGE_SIZE), totalElements: totalElements, totalPages: totalPages, _legacy: true, _all: all };
          });
        }
        return r.json();
      })
      .then(function (data) {
        if (data && data._legacy && !append) offers = data._all || [];
        const items = Array.isArray(data.items) ? data.items : [];
        totalElements = Number(data.totalElements || 0);
        totalPages = Number(data.totalPages || 0);
        if (!append) {
          offers = items.slice();
          matches = items.slice();
        } else {
          offers = offers.concat(items);
          matches = matches.concat(items);
        }
        paintOffers(items, !append);
        const count = document.getElementById("offer-count");
        if (count) count.textContent = totalElements ? String(totalElements) : "nos";
        if (!append) {
          const contracts = Array.from(new Set(offers.map(function (o) { return o.contractType; }).filter(Boolean)));
          fillSelect("q-contract", contracts, "Type de contrat");
          paintChips();
          paintCompanies();
          paintCities();
          paintStats();
          document.dispatchEvent(new CustomEvent("landing:offers-loaded"));
        }
      })
      .catch(function () {
        if (!append) {
          root.innerHTML = "<p class='muted'>Impossible de charger le catalogue (API indisponible ou erreur reseau).</p>";
          const count = document.getElementById("offer-count");
          if (count) count.textContent = "nos";
        }
      })
      .finally(function () { loading = false; });
  }

  function paintCompanies() {
    const box = document.getElementById("company-grid");
    const tabs = document.getElementById("company-tabs");
    if (!box || !tabs) return;
    const map = new Map();
    offers.forEach(function (o) {
      const name = companyOf(o);
      const row = map.get(name) || { name: name, city: cityOf(o), n: 0, verified: false, logo: HireLinkOffer.mediaUrl(o.companyLogo) || "", sector: o.companySector || "" };
      row.n += 1;
      row.verified = row.verified || !!o.companyVerified;
      if (!row.logo && HireLinkOffer.mediaUrl(o.companyLogo)) row.logo = HireLinkOffer.mediaUrl(o.companyLogo);
      if (row.city === "Lieu à préciser") row.city = cityOf(o);
      map.set(name, row);
    });
    const cities = Array.from(new Set(Array.from(map.values()).map(function (c) { return c.city; })));
    const tabItems = [""].concat(cities);
    tabs.innerHTML = tabItems.map(function (city) {
      const on = city === companyCity ? " is-on" : "";
      const label = city || "Toutes";
      return "<button type='button' class='" + on + "' data-city='" + esc(city) + "'>" + esc(label) + "</button>";
    }).join("");
    tabs.querySelectorAll("button").forEach(function (btn) {
      btn.addEventListener("click", function () {
        companyCity = btn.getAttribute("data-city") || "";
        paintCompanies();
      });
    });
    const rows = Array.from(map.values()).filter(function (c) {
      return !companyCity || c.city === companyCity;
    });
    box.replaceChildren();
    if (!rows.length) {
      box.innerHTML = "<p class='muted'>Aucune entreprise pour cette ville.</p>";
      return;
    }
    rows.forEach(function (c) {
      box.appendChild(HireLinkOffer.companyCard(c, { href: function () { return "company.html?name=" + encodeURIComponent(c.name); } }));
    });
  }

  function fillSelect(id, values, placeholder) {
    const el = document.getElementById(id);
    if (!el || el.tagName !== "SELECT") return;
    const current = el.value;
    el.innerHTML = "<option value=''>" + esc(placeholder) + "</option>" +
      values.map(function (v) { return "<option value='" + esc(v) + "'>" + esc(v) + "</option>"; }).join("");
    if (values.indexOf(current) >= 0) el.value = current;
  }

  function paintCities() {
    const row = document.getElementById("city-row");
    const foot = document.getElementById("footer-cities");
    const counts = new Map();
    offers.forEach(function (o) {
      const city = cityOf(o);
      counts.set(city, (counts.get(city) || 0) + 1);
    });
    const cities = Array.from(counts.entries()).sort(function (a, b) { return b[1] - a[1]; });
    if (row) {
      row.innerHTML = cities.slice(0, 6).map(function (pair, i) {
        return (
          "<button type='button' class='city-tile tone-" + (i % 4) + "' data-city='" + esc(pair[0]) + "'>" +
          "<strong>" + esc(pair[0]) + "</strong>" +
          "<span>" + pair[1] + " offre" + (pair[1] > 1 ? "s" : "") + "</span></button>"
        );
      }).join("") || "<p class='muted'>Les villes apparaîtront avec les offres.</p>";
      row.querySelectorAll("button").forEach(function (btn) {
        btn.addEventListener("click", function () {
          document.getElementById("q-city").value = btn.getAttribute("data-city") || "";
          applyOffers();
        });
      });
    }
    if (foot) {
      const label = foot.querySelector(".footer-label");
      foot.innerHTML = "";
      if (label) foot.appendChild(label);
      cities.slice(0, 6).forEach(function (pair) {
        const a = document.createElement("a");
        a.href = "#offres";
        a.textContent = pair[0];
        a.addEventListener("click", function (e) {
          e.preventDefault();
          document.getElementById("q-city").value = pair[0];
          applyOffers();
        });
        foot.appendChild(a);
      });
    }
  }

  /* Chiffres de la bande d’accueil, calculés sur les offres publiées. */
  function paintStats() {
    const companies = new Set(offers.map(companyOf));
    const cities = new Set(offers.map(cityOf));
    const quick = new Set(
      offers
        .filter(function (o) { return o.responsiveness === "REACTIF"; })
        .map(companyOf)
    );
    const values = {
      offers: totalElements || offers.length,
      companies: companies.size,
      cities: cities.size,
      pace: quick.size
    };
    document.querySelectorAll("[data-stat]").forEach(function (el) {
      el.textContent = String(values[el.getAttribute("data-stat")]);
    });
  }

  function paintMetiers() {
    const box = document.getElementById("metier-grid");
    if (!box) return;
    box.innerHTML = METIERS.map(function (pair) {
      return "<button type='button' data-q='" + esc(pair[1]) + "'>" + esc(pair[0]) + "</button>";
    }).join("");
    box.querySelectorAll("button").forEach(function (btn) {
      btn.addEventListener("click", function () {
        setMode("offers");
        document.getElementById("q-text").value = btn.getAttribute("data-q") || "";
        applyOffers();
      });
    });
  }

  function paintChips() {
    const box = document.getElementById("quick-chips");
    if (!box) return;
    const titles = [];
    offers.forEach(function (o) {
      if (o.title && titles.indexOf(o.title) < 0) titles.push(o.title);
    });
    const chips = titles.slice(0, 8);
    box.innerHTML = chips.map(function (title) {
      return "<button type='button' data-q='" + esc(title) + "' title='" + esc(title) + "'>" + esc(title) + "</button>";
    }).join("");
    box.querySelectorAll("button").forEach(function (btn) {
      btn.addEventListener("click", function () {
        setMode("offers");
        document.getElementById("q-text").value = btn.getAttribute("data-q") || "";
        document.getElementById("q-city").value = "";
        document.getElementById("q-contract").value = "";
        applyOffers();
      });
    });
  }

  function applyOffers() {
    fetchPage(false);
    const target = document.getElementById("liste-offres");
    if (target) target.dispatchEvent(new CustomEvent("landing:scroll-to", { bubbles: true }));
  }

  function setMode(next) {
    mode = next;
    document.querySelectorAll(".search-tabs button").forEach(function (btn) {
      const on = btn.getAttribute("data-find") === next;
      btn.classList.toggle("is-on", on);
      btn.setAttribute("aria-selected", on ? "true" : "false");
    });
    const hint = document.getElementById("search-hint");
    const contract = document.getElementById("q-contract");
    const submit = document.getElementById("q-submit");
    const input = document.getElementById("q-text");
    const people = next === "people";
    if (contract) contract.hidden = people;
    if (submit) submit.textContent = people ? "Voir les dossiers" : "Rechercher";
    if (input) input.placeholder = people ? "Metier ou competence…" : "Rechercher un poste, une entreprise…";
    if (hint) {
      hint.textContent = people
        ? "Les dossiers ne sont pas publics. Le recruteur les ouvre apres connexion : CV, texte extrait, synthese, etapes."
        : "Trouvez un poste parmi les offres publiees. La connexion sert a postuler avec votre CV.";
    }
  }

  const form = document.getElementById("finder");
  if (form) {
    form.addEventListener("submit", function (e) {
      e.preventDefault();
      if (mode === "people") {
        location.href = "auth.html?mode=register&role=RECRUTEUR";
        return;
      }
      applyOffers();
    });
  }
  document.querySelectorAll(".search-tabs button").forEach(function (btn) {
    btn.addEventListener("click", function () { setMode(btn.getAttribute("data-find") || "offers"); });
  });
  document.querySelectorAll("[data-contract]").forEach(function (link) {
    link.addEventListener("click", function (e) {
      e.preventDefault();
      setMode("offers");
      document.getElementById("q-contract").value = link.getAttribute("data-contract") || "";
      applyOffers();
    });
  });

  moreBtn.addEventListener("click", showMoreOffers);
  paintMetiers();
  let timer = 0;
  ["q-text", "q-city"].forEach(function (id) {
    const el = document.getElementById(id);
    if (!el) return;
    el.addEventListener("input", function () {
      clearTimeout(timer);
      timer = setTimeout(function () { applyOffers(); }, 280);
    });
  });
  ["q-origin", "q-since", "q-contract", "q-country"].forEach(function (id) {
    const el = document.getElementById(id);
    if (el) el.addEventListener("change", function () { applyOffers(); });
  });
  HireLinkPlaces.ready().then(function () { applyOffers(); });
})();
