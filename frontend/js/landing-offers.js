/**
 * Page d’accueil : entreprises qui publient sur HireLink (offres HireLink uniquement, tous pays)
 * et familles de métiers. Aucune recherche sur la page d’accueil : elle se fait dans l’espace candidat.
 */
(function () {
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

  function esc(s) {
    return String(s == null ? "" : s)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }

  function companyOf(o) {
    return o.companyName || o.recruiterName || "Entreprise";
  }

  function cityOf(o) {
    return (window.HireLinkPlaces && HireLinkPlaces.cityOf(o.location, o.companyCity)) || o.companyCity || "";
  }

  function isCandidate() {
    try {
      const s = JSON.parse(localStorage.getItem("hirelink.session") || "null");
      return !!(s && s.role === "CANDIDAT");
    } catch (_) {
      return false;
    }
  }

  /** Un candidat connecté arrive directement sur sa recherche ; un visiteur crée son espace puis y arrive. */
  function metierHref(q) {
    const target = "app/candidate.html?q=" + encodeURIComponent(q);
    if (isCandidate()) return target;
    return "auth.html?mode=register&role=CANDIDAT&next=" + encodeURIComponent(target);
  }

  function paintMetiers() {
    const box = document.getElementById("metier-grid");
    if (!box) return;
    box.innerHTML = METIERS.map(function (pair) {
      return "<a class='metier-chip' href='" + esc(metierHref(pair[1])) + "'>" + esc(pair[0]) + "</a>";
    }).join("");
  }

  function paintCompanies(offers) {
    const box = document.getElementById("company-grid");
    if (!box || !window.HireLinkOffer) return;
    const map = new Map();
    offers.forEach(function (o) {
      if (o.external) return;
      const name = companyOf(o);
      const row = map.get(name) || {
        name: name,
        city: cityOf(o),
        n: 0,
        verified: false,
        logo: HireLinkOffer.mediaUrl(o.companyLogo) || "",
        sector: o.companySector || ""
      };
      row.n += 1;
      row.verified = row.verified || !!o.companyVerified;
      if (!row.logo && HireLinkOffer.mediaUrl(o.companyLogo)) row.logo = HireLinkOffer.mediaUrl(o.companyLogo);
      if (!row.city) row.city = cityOf(o);
      map.set(name, row);
    });
    const rows = Array.from(map.values()).sort(function (a, b) { return b.n - a.n; }).slice(0, 24);
    box.replaceChildren();
    if (!rows.length) {
      box.innerHTML = "<p class='muted'>Les entreprises qui recrutent apparaîtront ici.</p>";
      return;
    }
    rows.forEach(function (c) {
      box.appendChild(HireLinkOffer.companyCard(c, {
        href: function () { return "company.html?name=" + encodeURIComponent(c.name); }
      }));
    });
    document.dispatchEvent(new CustomEvent("landing:companies-loaded"));
  }

  function loadCompanies() {
    const box = document.getElementById("company-grid");
    if (!box) return;
    fetch(base + "/catalog/offers?page=0&size=50&country=ALL&origin=internal", { credentials: "omit" })
      .then(function (r) { return r.ok ? r.json() : Promise.reject(new Error("catalog")); })
      .then(function (data) {
        paintCompanies((data && Array.isArray(data.items)) ? data.items : []);
      })
      .catch(function () {
        box.innerHTML = "<p class='muted'>Les entreprises qui recrutent apparaîtront ici.</p>";
      });
  }

  paintMetiers();
  if (window.HireLinkPlaces && HireLinkPlaces.ready) {
    HireLinkPlaces.ready().then(loadCompanies, loadCompanies);
  } else {
    loadCompanies();
  }
})();
