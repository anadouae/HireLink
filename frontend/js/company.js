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
    stats: document.getElementById("co-stats"),
    about: document.getElementById("co-about"),
    keys: document.getElementById("co-keys"),
    keysBox: document.getElementById("co-keys-box"),
    skillsBox: document.getElementById("co-skills-box"),
    skills: document.getElementById("co-facts"),
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

  function lastPost(offers) {
    const at = offers.reduce((m, o) => Math.max(m, Date.parse(o.createdAt) || 0), 0);
    if (!at) return "";
    const days = Math.floor((Date.now() - at) / 86400000);
    if (days <= 0) return "aujourd’hui";
    if (days === 1) return "hier";
    if (days < 14) return "il y a " + days + " j";
    if (days < 60) return "il y a " + Math.round(days / 7) + " sem.";
    return "il y a " + Math.round(days / 30) + " mois";
  }

  fetch(base + "/organizations/public/company?name=" + encodeURIComponent(name))
    .then((r) => (r.ok ? r.json() : Promise.reject(new Error("http " + r.status))))
    .then((sheet) => {
      const org = sheet.organization || {};
      const offers = sheet.offers || [];
      const cities = [];
      const contracts = [];
      const skills = [];
      offers.forEach((o) => {
        push(cities, HireLinkPlaces.placeLabel(o.location, o.companyCity));
        push(contracts, o.contractType);
        String(o.requiredSkills || "")
          .split(",")
          .map((s) => s.trim())
          .filter(Boolean)
          .forEach((s) => {
            if (skills.length < 10) push(skills, s);
          });
      });
      const pace = HireLinkOffer.paceOf(offers.find((o) => o.responsiveness) || {});
      paintLogo(org.logoUrl || (offers[0] ? offers[0].companyLogo : ""));

      const headline = [org.sector, staff(org.employees)].filter(Boolean).join(" · ");
      view.sub.textContent = headline || cities.join(", ");

      view.signals.innerHTML = [
        org.verified ? '<span class="co-signal co-signal--ok">Identité vérifiée</span>' : "",
        pace.badge ? '<span class="co-signal" title="' + esc(pace.title) + '">' + esc(pace.label) + "</span>" : "",
      ].join("");

      if (org.website && /^https?:\/\//i.test(org.website)) {
        view.actions.innerHTML =
          '<a class="btn btn-soft btn-sm" href="' + esc(org.website) + '" target="_blank" rel="noopener">Site web</a>';
      }

      const stat = (n, one, many) =>
        n ? '<div class="co-stat"><strong>' + n + "</strong><span>" + (n > 1 ? many : one) + "</span></div>" : "";
      const since = lastPost(offers);
      view.stats.innerHTML =
        stat(offers.length, "Offre en ligne", "Offres en ligne") +
        stat(cities.length, "Ville d’accueil", "Villes d’accueil") +
        (since
          ? '<div class="co-stat"><strong>' + esc(since) + "</strong><span>Dernière publication</span></div>"
          : "");
      view.stats.hidden = !view.stats.children.length;

      view.about.textContent = !sheet.organization && !offers.length
        ? "Aucune entreprise ne porte ce nom sur HireLink."
        : offers.length
          ? org.presentation || name + " recrute sur HireLink." + (pace.sentence ? " " + pace.sentence : "")
          : "Cette entreprise n’a pas d’offre en ligne en ce moment.";

      const keys = [["Présence", cities.join(", ")], ["Contrats", contracts.join(", ")]].filter((k) => k[1]);
      view.keysBox.hidden = !keys.length;
      view.keys.innerHTML = keys.map((k) => "<div><dt>" + esc(k[0]) + "</dt><dd>" + esc(k[1]) + "</dd></div>").join("");

      view.skillsBox.hidden = !skills.length;
      view.skills.innerHTML = skills.map((s) => '<span class="co-skill">' + esc(s) + "</span>").join("");

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
            href: (x) => (candidate ? "app/candidate.html?apply=" + encodeURIComponent(x.id) : "auth.html?mode=login"),
          })
        )
      );
    })
    .catch(() => {
      view.sub.textContent = "Impossible de charger cette entreprise.";
    });
})();
