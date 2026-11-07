(function () {
  const name = new URLSearchParams(location.search).get("name") || "";
  const base = window.HIRELINK_API_BASE || "http://localhost:8081/api/v1";
  const clean = (u) => (/google\.com\/s2\/favicons|gstatic\.com\/faviconV2/i.test(u || "") ? "" : (u || ""));

  const view = {
    logo: document.getElementById("co-logo"),
    title: document.getElementById("co-name"),
    sub: document.getElementById("co-sub"),
    signals: document.getElementById("co-signals"),
    actions: document.getElementById("co-actions"),
    about: document.getElementById("co-about"),
    count: document.getElementById("co-count"),
    offers: document.getElementById("co-offers"),
  };

  view.title.textContent = name || "Entreprise";
  document.title = (name || "Entreprise") + " — HireLink";

  function esc(s) {
    return String(s == null ? "" : s)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/'/g, "&#39;");
  }

  function initials(value) {
    const parts = String(value || "E").trim().split(/\s+/).filter(Boolean);
    const a = (parts[0] || "E")[0];
    const b = parts[1] ? parts[1][0] : (parts[0] || "E").slice(1, 2);
    return (a + b).toUpperCase();
  }

  let session = null;
  try {
    session = JSON.parse(localStorage.getItem("hirelink.session") || "null");
  } catch (_) {}
  const candidate = session && session.role === "CANDIDAT" && localStorage.getItem("hirelink.token");
  // Un visiteur connecté n'est pas un inconnu : un recruteur qui ouvre une offre ici doit rentrer chez lui,
  // pas devant un formulaire de connexion qui laisse croire que sa session a sauté.
  const space = !session ? null : session.role === "ADMINISTRATEUR" ? "app/admin.html"
    : session.role === "RECRUTEUR" ? "app/recruiter.html"
    : "app/candidate-dashboard.html";
  view.logo.textContent = initials(name);

  function paintLogo(url) {
    const src = HireLinkOffer.mediaUrl(clean(url));
    if (!src) return;
    const img = document.createElement("img");
    img.alt = "";
    img.addEventListener("error", () => {
      img.remove();
      view.logo.textContent = initials(name);
    });
    img.src = src;
    view.logo.replaceChildren(img);
  }

  function push(list, value) {
    if (value && list.indexOf(value) < 0) list.push(value);
    return list;
  }

  // l'effectif est saisi en tranche (« 11-50 ») : la fiche publique la lit en français
  const staff = (v) => (v ? String(v).replace("-", " à ") + " salariés" : "");

  fetch(base + "/organizations/public/company?name=" + encodeURIComponent(name))
    .then((r) => (r.ok ? r.json() : Promise.reject(new Error("http " + r.status))))
    .then((sheet) => {
      const org = sheet.organization || {};
      const offers = sheet.offers || [];
      const cities = [];
      offers.forEach((o) => push(cities, HireLinkPlaces.placeLabel(o.location, o.companyCity)));
      const pace = HireLinkOffer.paceOf(offers.find((o) => o.responsiveness) || {});
      paintLogo(org.logoUrl || (offers[0] ? offers[0].companyLogo : ""));

      // Une seule règle : la fiche publique montre ce que le recruteur remplit dans Paramètres, rien d'autre.
      function paintHead() {
        const flag = window.HireLinkPlaces && org.city ? HireLinkPlaces.flagOf(org.city) : "";
        const headline = [org.sector, org.city ? (flag + " " + org.city).trim() : "", staff(org.employees)].filter(Boolean).join(" · ");
        view.sub.textContent = headline || cities.join(", ");
      }
      paintHead();
      HireLinkPlaces.ready().then(paintHead);

      view.signals.innerHTML = [
        org.verified ? '<span class="co-signal co-signal--ok">Identité vérifiée</span>' : "",
        pace.badge ? '<span class="co-signal" title="' + esc(pace.title) + '">' + esc(pace.label) + "</span>" : "",
      ].join("");

      if (org.website && /^https?:\/\//i.test(org.website)) {
        view.actions.innerHTML =
          '<a class="btn btn-soft btn-sm" href="' + esc(org.website) + '" target="_blank" rel="noopener">Site web</a>';
      }

      view.about.textContent = !sheet.organization && !offers.length
        ? "Aucune entreprise ne porte ce nom sur HireLink."
        : offers.length
          ? org.presentation || name + " recrute sur HireLink." + (pace.sentence ? " " + pace.sentence : "")
          : "Cette entreprise n’a pas d’offre en ligne en ce moment.";

      view.count.textContent = offers.length ? offers.length + (offers.length > 1 ? " offres" : " offre") : "";
      view.offers.replaceChildren();
      if (!offers.length) {
        const p = document.createElement("p");
        p.className = "co-empty";
        p.textContent = name
          ? "Aucune offre publiée sous le nom « " + name + " »."
          : "Aucune entreprise ne correspond à cette adresse.";
        view.offers.appendChild(p);
        return;
      }
      offers.forEach((o) =>
        view.offers.appendChild(
          HireLinkOffer.card(o, {
            href: (x) => (candidate ? "app/candidate.html?apply=" + encodeURIComponent(x.id) : space || "auth.html?mode=login"),
          })
        )
      );
    })
    .catch(() => {
      view.sub.textContent = "Impossible de charger cette entreprise.";
    });
})();
