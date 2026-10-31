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
    const raw = String(o.location || "").split(/[,·]/)[0].trim();
    return raw || "Lieu à préciser";
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

  function filtered() {
    const city = document.getElementById("q-city").value.trim();
    const contract = document.getElementById("q-contract").value;
    const pool = offers.filter(function (o) {
      if (city && HireLinkSearch.score(city, cityOf(o)) <= 0) return false;
      return !contract || String(o.contractType || "") === contract;
    });
    return HireLinkSearch.rank(pool, document.getElementById("q-text").value.trim(), [
      { get: function (o) { return o.title; }, weight: 3 },
      { get: companyOf, weight: 2 },
      { get: function (o) { return o.requiredSkills; }, weight: 2 },
      { get: cityOf, weight: 1 },
    ]);
  }

  /* Les offres s’affichent par lots : les plus récentes d’abord, la suite à la demande. */
  function showMoreOffers() {
    const next = matches.slice(shown, shown + PAGE_SIZE);
    next.forEach(function (o) {
      root.appendChild(HireLinkOffer.card(o, {
        variant: "showcase",
        footText: ago(o.createdAt),
        href: function () { return "auth.html?mode=login"; },
      }));
    });
    shown += next.length;
    HireLinkSearch.mark(root, document.getElementById("q-text").value.trim());
    const left = matches.length - shown;
    moreBox.hidden = left <= 0;
    moreBtn.textContent = "Afficher " + Math.min(left, PAGE_SIZE) + " offre" + (Math.min(left, PAGE_SIZE) > 1 ? "s" : "") + " de plus";
  }

  function paintOffers(list) {
    const lead = document.getElementById("offers-lead");
    if (lead) {
      lead.textContent = list.length
        ? list.length + " offre" + (list.length > 1 ? "s" : "") + " correspondent à la recherche. Le délai affiché est celui promis par l’entreprise."
        : "Aucune offre pour cette recherche.";
    }
    root.replaceChildren();
    root.classList.remove("card-grid");
    matches = list;
    shown = 0;
    moreBox.hidden = true;
    if (!list.length) {
      HireLinkSearch.emptyState(root, document.getElementById("q-text").value.trim(), {
        emptyTitle: "Aucune offre pour cette recherche",
        onClear: function () {
          ["q-text", "q-city", "q-contract"].forEach(function (id) { document.getElementById(id).value = ""; });
          paintOffers(filtered());
        },
      });
      return;
    }
    showMoreOffers();
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
    const slas = offers.map(function (o) { return o.responseSlaDays; }).filter(function (d) { return d != null; });
    const values = {
      offers: offers.length,
      companies: companies.size,
      cities: cities.size,
      sla: slas.length ? Math.round(slas.reduce(function (a, b) { return a + b; }, 0) / slas.length) : 0
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
    paintOffers(filtered());
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
    if (input) input.placeholder = people ? "Métier ou compétence…" : "Rechercher un poste, une entreprise…";
    if (hint) {
      hint.textContent = people
        ? "Les dossiers ne sont pas publics. Le recruteur les ouvre après connexion : CV, texte extrait, synthèse, étapes."
        : "Trouvez un poste parmi les offres publiées. La connexion sert à postuler avec votre CV.";
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
    document.getElementById(id).addEventListener("input", function () {
      clearTimeout(timer);
      timer = setTimeout(function () { paintOffers(filtered()); }, 200);
    });
  });
  HireLinkPlaces.ready().then(function () { if (offers.length) paintOffers(filtered()); });
  fetch(base + "/offers/public")
    .then(function (r) {
      if (!r.ok) throw new Error("offres");
      return r.json();
    })
    .then(function (data) {
      offers = (Array.isArray(data) ? data : []).sort(function (a, b) {
        return String(b.createdAt || "").localeCompare(String(a.createdAt || ""));
      });
      const count = document.getElementById("offer-count");
      if (count) count.textContent = offers.length ? String(offers.length) : "nos";
      const cities = [];
      const contracts = Array.from(new Set(offers.map(function (o) { return o.contractType; }).filter(Boolean)));
      fillSelect("q-city", cities, "Lieu");
      fillSelect("q-contract", contracts, "Type de contrat");
      paintChips();
      paintCompanies();
      paintCities();
      paintOffers(offers);
      paintStats();
      document.dispatchEvent(new CustomEvent("landing:offers-loaded"));
    })
    .catch(function () {
      root.innerHTML = "<p class='muted'>Les offres s’afficheront dès que l’API est disponible.</p>";
      const count = document.getElementById("offer-count");
      if (count) count.textContent = "nos";
    });
})();
