(function () {
  const root = document.getElementById("public-offers");
  if (!root) return;
  const base = window.HIRELINK_API_BASE || "http://127.0.0.1:8081/api/v1";
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
      .replace(/>/g, "&gt;");
  }

  function fold(value) {
    return String(value || "")
      .normalize("NFD")
      .replace(/[\u0300-\u036f]/g, "")
      .toLowerCase();
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
    const q = fold(document.getElementById("q-text").value);
    const city = document.getElementById("q-city").value;
    const contract = document.getElementById("q-contract").value;
    return offers.filter(function (o) {
      const blob = fold(o.title + " " + companyOf(o) + " " + (o.requiredSkills || "") + " " + cityOf(o));
      if (q && blob.indexOf(q) < 0) return false;
      if (city && fold(cityOf(o)).indexOf(fold(city)) < 0) return false;
      if (contract && String(o.contractType || "") !== contract) return false;
      return true;
    });
  }

  function paintOffers(list) {
    const lead = document.getElementById("offers-lead");
    if (lead) {
      lead.textContent = list.length
        ? list.length + " offre" + (list.length > 1 ? "s" : "") + " correspondent à la recherche. Le délai affiché est celui promis par l’entreprise."
        : "Aucune offre pour cette recherche.";
    }
    if (!list.length) {
      root.innerHTML = "<p class='muted'>Aucune offre publiée pour ce filtre.</p>";
      return;
    }
    root.innerHTML = list.map(function (o) {
      const company = companyOf(o);
      const href = "company.html?name=" + encodeURIComponent(company);
      const modePart = String(o.location || "").split("·")[1];
      return (
        "<article class='job-card'>" +
        "<h3>" + esc(o.title) + "</h3>" +
        "<p class='job-co'><a href='" + href + "'>" + esc(company) + "</a></p>" +
        "<p class='job-meta'>" +
        "<span>" + esc(cityOf(o)) + "</span>" +
        (o.contractType ? "<span>" + esc(o.contractType) + "</span>" : "") +
        (modePart && modePart.trim() ? "<span>" + esc(modePart.trim()) + "</span>" : "") +
        (o.responseSlaDays != null ? "<span>Réponse " + o.responseSlaDays + " j</span>" : "") +
        (o.companyVerified ? "<span>Entreprise vérifiée</span>" : "") +
        "</p>" +
        "<footer><span>" + esc(ago(o.createdAt)) + "</span>" +
        "<a class='btn btn-primary btn-sm' href='auth.html?mode=login'>Voir</a></footer>" +
        "</article>"
      );
    }).join("");
  }

  function paintCompanies() {
    const box = document.getElementById("company-grid");
    const tabs = document.getElementById("company-tabs");
    if (!box || !tabs) return;
    const map = new Map();
    offers.forEach(function (o) {
      const name = companyOf(o);
      const row = map.get(name) || { name: name, city: cityOf(o), n: 0, verified: false, logo: o.companyLogo || "", sector: o.companySector || "" };
      row.n += 1;
      row.verified = row.verified || !!o.companyVerified;
      if (!row.logo && o.companyLogo) row.logo = o.companyLogo;
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
    box.innerHTML = rows.map(function (c) {
      const href = "company.html?name=" + encodeURIComponent(c.name);
      return (
        "<article class='job-card'>" +
        (c.logo ? "<img class='co-landing-logo' alt='' src='" + esc((window.HireLinkAPI && HireLinkAPI.mediaUrl) ? HireLinkAPI.mediaUrl(c.logo) : c.logo) + "' />" : "") +
        "<h3>" + esc(c.name) + "</h3>" +
        "<p class='job-meta'><span>" + esc(c.city) + "</span>" +
        (c.verified ? "<span>Identité vérifiée</span>" : "<span>Fiche entreprise</span>") +
        "</p>" +
        "<footer><span>" + c.n + " offre" + (c.n > 1 ? "s" : "") + "</span>" +
        "<a class='btn btn-primary btn-sm' href='" + href + "'>Consulter</a></footer>" +
        "</article>"
      );
    }).join("") || "<p class='muted'>Aucune entreprise pour cette ville.</p>";
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
      return "<button type='button' data-q='" + esc(title) + "'>" + esc(title) + "</button>";
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
    if (target) target.scrollIntoView({ behavior: "smooth", block: "start" });
  }

  function setMode(next) {
    mode = next;
    document.querySelectorAll(".search-tabs button").forEach(function (btn) {
      btn.classList.toggle("is-on", btn.getAttribute("data-find") === next);
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

  paintMetiers();
  fetch(base + "/offers/public")
    .then(function (r) {
      if (!r.ok) throw new Error("offres");
      return r.json();
    })
    .then(function (data) {
      offers = Array.isArray(data) ? data : [];
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
    })
    .catch(function () {
      root.innerHTML = "<p class='muted'>Les offres s’afficheront dès que l’API est disponible.</p>";
      const count = document.getElementById("offer-count");
      if (count) count.textContent = "nos";
    });
})();
