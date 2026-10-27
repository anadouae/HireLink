(function () {
  const name = new URLSearchParams(location.search).get("name") || "";
  const base = window.HIRELINK_API_BASE || "http://localhost:8081/api/v1";
  const title = document.getElementById("co-name");
  const meta = document.getElementById("co-meta");
  const about = document.getElementById("co-about");
  const facts = document.getElementById("co-facts");
  const logo = document.getElementById("co-logo");
  const root = document.getElementById("co-offers");
  if (title) title.textContent = name || "Entreprise";
  document.title = (name || "Entreprise") + " — HireLink";

  function esc(s) {
    return String(s == null ? "" : s)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;");
  }

  function initials(value) {
    const parts = String(value || "E").trim().split(/\s+/).filter(Boolean);
    const a = (parts[0] || "E")[0];
    const b = parts[1] ? parts[1][0] : (parts[0] || "E")[1] || "";
    return (a + b).toUpperCase();
  }

  let session = null;
  try {
    session = JSON.parse(localStorage.getItem("hirelink.session") || "null");
  } catch (_) {}
  const candidate = session && session.role === "CANDIDAT" && localStorage.getItem("hirelink.token");
  if (logo) logo.textContent = initials(name);

  function setLogo(url) {
    if (!logo || !url) return;
    const src =
      window.HireLinkAPI && HireLinkAPI.mediaUrl
        ? HireLinkAPI.mediaUrl(url)
        : /^https?:\/\//i.test(url)
          ? url
          : (base.replace(/\/api\/v1\/?$/, "") + (url.startsWith("/") ? url : "/" + url));
    logo.innerHTML = '<img alt="" src="' + esc(src) + '" onerror="this.remove()" />';
  }

  fetch(base + "/offers/public")
    .then((r) => r.json())
    .then((offers) => {
      const list = (offers || []).filter((o) => (o.companyName || o.recruiterName || "") === name);
      const cities = [];
      const contracts = [];
      const skills = [];
      list.forEach((o) => {
        const city = String(o.location || "").split(/[·,|/]/)[0].trim();
        if (city && cities.indexOf(city) < 0) cities.push(city);
        if (o.contractType && contracts.indexOf(o.contractType) < 0) contracts.push(o.contractType);
        String(o.requiredSkills || "")
          .split(",")
          .map((s) => s.trim())
          .filter(Boolean)
          .forEach((s) => {
            if (skills.indexOf(s) < 0 && skills.length < 8) skills.push(s);
          });
      });
      const verified = list[0] && list[0].companyVerified;
      if (list[0] && list[0].companyLogo) setLogo(list[0].companyLogo);
      if (meta) {
        meta.textContent = list.length
          ? (verified ? "Entreprise vérifiée · " : "") +
            list.length +
            " publication" +
            (list.length > 1 ? "s" : "") +
            (cities.length ? " · " + cities.join(", ") : "")
          : "Aucune publication pour le moment.";
      }
      if (about) {
        about.textContent = list.length
          ? name +
            " publie ses recrutements sur HireLink. Chaque offre indique un délai de réponse. " +
            (cities.length ? "Présence : " + cities.join(", ") + ". " : "") +
            (skills.length ? "Compétences recherchées : " + skills.join(", ") + "." : "")
          : "Cette entreprise n’a pas d’offre publique en ce moment.";
      }
      if (facts) {
        const chips = []
          .concat(verified ? ["Vérifiée"] : [])
          .concat(cities)
          .concat(contracts)
          .concat(skills.slice(0, 6));
        facts.innerHTML = chips.map((c) => "<span>" + esc(c) + "</span>").join("");
      }
      if (!root) return;
      if (!list.length) {
        root.innerHTML = "<p class='muted'>Aucune offre publiée.</p>";
        return;
      }
      root.innerHTML = list
        .map((o) => {
          const href = candidate
            ? "app/candidate.html?apply=" + encodeURIComponent(o.id)
            : "auth.html?mode=login";
          return (
            "<article class='public-offer'><h3>" +
            esc(o.title) +
            "</h3><p>" +
            esc(o.location || "Lieu à préciser") +
            (o.contractType ? " · " + esc(o.contractType) : "") +
            (o.responseSlaDays != null ? " · Réponse " + o.responseSlaDays + " j" : "") +
            "</p><p style='margin-top:.45rem'>" +
            esc((o.description || "").slice(0, 220)) +
            "</p><div class='row'><a class='btn btn-primary btn-sm' href='" +
            href +
            "'>Postuler</a></div></article>"
          );
        })
        .join("");
    })
    .catch(() => {
      if (meta) meta.textContent = "Impossible de charger cette entreprise.";
    });
})();