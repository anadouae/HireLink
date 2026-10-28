(function () {
  const session = HireLinkApp.mount({ search: { placeholder: "Entreprise, poste, ville, compétence, contrat…", onQuery: () => render() } });
  if (!session) return;

  const offerList = document.getElementById("offer-list");
  let cached = [];
  let scope = "all";
  let appliedByOffer = new Map();
  let mySkills = "";

  const params = new URLSearchParams(location.search);
  if (params.get("scope") === "mine") scope = "mine";
  let focusId = params.get("offer");

  function inferWorkMode(location, contract) {
    const blob = ((location || "") + " " + (contract || "")).toLowerCase();
    if (/remote|télétravail|teletravail|full\s*remote/.test(blob)) return "remote";
    if (/hybride|hybrid/.test(blob)) return "hybrid";
    if (/présentiel|presentiel|sur\s*site|onsite|bureau/.test(blob)) return "onsite";
    return "";
  }

  function workLabel(mode, location) {
    if (mode === "remote") return "Remote";
    if (mode === "hybrid") return "Hybride";
    if (mode === "onsite") return "Présentiel";
    return location ? "Sur site" : "Mode à préciser";
  }

  function cityOf(location) {
    const raw = String(location || "").split(/[·,|/]/)[0].trim();
    return raw || "";
  }

  function appState(offerId) {
    return appliedByOffer.get(offerId) || null;
  }

  function isOpenToApply(offerId) {
    const st = appState(offerId);
    return !st || st.status === "WITHDRAWN";
  }

  function openApply(o) {
    if (!isOpenToApply(o.id)) {
      HireLink.toast("Déjà envoyée", "Ouvrez-la dans Mes candidatures. Le statut Retirée est le seul qui permet de renvoyer.", "info");
      return;
    }
    if (!window.HireLinkApply) {
      HireLink.toast("Erreur", "Assistant de candidature indisponible", "danger");
      return;
    }
    HireLinkApply.start(o, load);
  }

  function companyHref(o) {
    const name = o.companyName || o.recruiterName || "";
    return "../company.html?name=" + encodeURIComponent(name);
  }

  function initials(value) {
    const parts = String(value || "E").trim().split(/\s+/).filter(Boolean);
    const a = (parts[0] || "E")[0];
    const b = parts[1] ? parts[1][0] : (parts[0] || "E")[1] || "";
    return (a + b).toUpperCase();
  }

  function esc(s) {
    return String(s == null ? "" : s)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/\'/g, "&#39;");
  }

  function fillSelect(id, values, current) {
    const sel = document.getElementById(id);
    if (!sel) return;
    const keep = current != null ? current : sel.value;
    const first = sel.querySelector("option");
    sel.innerHTML = "";
    if (first) sel.appendChild(first);
    values.forEach((v) => {
      const opt = document.createElement("option");
      opt.value = v;
      opt.textContent = v;
      sel.appendChild(opt);
    });
    if ([].some.call(sel.options, (o) => o.value === keep)) sel.value = keep;
  }

  function tokenize(csv) {
    return String(csv || "")
      .split(/[,;|/]+/)
      .map((s) => s.trim())
      .filter(Boolean);
  }

  function explainMatch(requiredCsv) {
    const candidate = new Set(tokenize(mySkills).map((s) => s.toLowerCase()));
    const required = tokenize(requiredCsv);
    const covered = [];
    const missing = [];
    required.forEach((r) => {
      if (candidate.has(r.toLowerCase())) covered.push(r);
      else missing.push(r);
    });
    return { covered, missing };
  }

  function rebuildFacets() {
    const cities = [];
    const contracts = [];
    const companies = [];
    const skills = [];
    cached.forEach((o) => {
      const c = cityOf(o.location);
      if (c && cities.indexOf(c) < 0) cities.push(c);
      const ct = (o.contractType || "").trim();
      if (ct && contracts.indexOf(ct) < 0) contracts.push(ct);
      const co = (o.companyName || o.recruiterName || "").trim();
      if (co && companies.indexOf(co) < 0) companies.push(co);
      tokenize(o.requiredSkills).forEach((sk) => {
        if (skills.indexOf(sk) < 0) skills.push(sk);
      });
    });
    const sortFr = (a, b) => a.localeCompare(b, "fr");
    cities.sort(sortFr);
    contracts.sort(sortFr);
    companies.sort(sortFr);
    skills.sort(sortFr);
    fillSelect("f-city", cities);
    fillSelect("f-contract", contracts);
    fillSelect("f-company", companies);
    fillSelect("f-skill", skills);
  }

  function filtered() {
    const q = ((document.getElementById("rc-search") || {}).value || "").trim().toLowerCase();
    const city = (document.getElementById("f-city") || {}).value || "";
    const company = (document.getElementById("f-company") || {}).value || "";
    const skill = (document.getElementById("f-skill") || {}).value || "";
    const contract = (document.getElementById("f-contract") || {}).value || "";
    const mode = (document.getElementById("f-mode") || {}).value || "";
    const slaMax = Number((document.getElementById("f-sla") || {}).value || 0);
    const sentFilter = (document.getElementById("f-sent") || {}).value || "";
    const minMatch = Number((document.getElementById("f-match") || {}).value || 0);
    return cached.filter((o) => {
      const score = o.matchScore == null ? 0 : o.matchScore;
      const st = appState(o.id);
      if (scope === "mine" && score < 55) return false;
      if (minMatch && score < minMatch) return false;
      if (city && cityOf(o.location) !== city) return false;
      if (company && (o.companyName || o.recruiterName || "") !== company) return false;
      if (skill && !tokenize(o.requiredSkills).some((s) => s.toLowerCase() === skill.toLowerCase())) return false;
      if (contract && (o.contractType || "") !== contract) return false;
      if (mode && inferWorkMode(o.location, o.contractType) !== mode) return false;
      if (slaMax && (o.responseSlaDays == null || o.responseSlaDays > slaMax)) return false;
      if (sentFilter === "open" && st && st.status !== "WITHDRAWN") return false;
      if (sentFilter === "sent" && (!st || st.status === "WITHDRAWN")) return false;
      if (sentFilter === "withdrawn" && (!st || st.status !== "WITHDRAWN")) return false;
      if (!q) return true;
      const blob = [
        o.title,
        o.description,
        o.requiredSkills,
        o.location,
        o.contractType,
        o.companyName,
        o.recruiterName,
      ]
        .join(" ")
        .toLowerCase();
      return blob.includes(q);
    });
  }

  function render() {
    const list = filtered().slice().sort((a, b) => {
      return (isOpenToApply(b.id) - isOpenToApply(a.id)) || (b.matchScore || 0) - (a.matchScore || 0) || String(a.title || "").localeCompare(String(b.title || ""), "fr");
    });
    const summary = document.getElementById("filter-summary");
    if (summary) {
      const active = ["f-city", "f-company", "f-skill", "f-contract", "f-mode", "f-sla", "f-sent", "f-match"].filter(function (id) {
        const node = document.getElementById(id);
        return node && node.value && node.value !== "0";
      }).length;
      summary.textContent = list.length + " offre" + (list.length > 1 ? "s" : "") + (active ? " · " + active + " filtre" + (active > 1 ? "s" : "") : "");
    }
    const lead = document.getElementById("offers-lead");
    if (lead) {
      lead.textContent =
        scope === "mine"
          ? "Offres compatibles à au moins 55 % avec votre profil."
          : "La compatibilité compare vos compétences à celles demandées par le poste.";
    }
    offerList.innerHTML = "";
    const detail = document.getElementById("offer-detail");
    if (!list.length) {
      offerList.innerHTML =
        scope === "mine"
          ? '<div class="empty"><p>Aucune offre assez proche de votre profil. Passez sur « Toutes les offres », ou complétez votre CV.</p><p><a class="btn btn-soft btn-sm" href="candidate-profile.html">Compléter mon CV</a></p></div>'
          : '<div class="empty"><p>Aucune offre pour ces filtres.</p></div>';
      if (detail) detail.innerHTML = "<p class='muted'>Aucune offre pour ces filtres.</p>";
      return;
    }
    list.forEach((o) => {
      const el = document.createElement("article");
      el.className = "offer";
      const matchPct = o.matchScore != null ? Math.round(o.matchScore) : null;
      const matchCls =
        matchPct == null ? "badge-chrome" : matchPct >= 80 ? "badge-ok match-hot" : matchPct >= 55 ? "badge-info" : "badge-rose";
      const mode = inferWorkMode(o.location, o.contractType);
      const st = appState(o.id);
      const sent = !!(st && st.status !== "WITHDRAWN");
      const withdrawn = !!(st && st.status === "WITHDRAWN");
      const detail = explainMatch(o.requiredSkills);
      const why =
        detail.covered.length || detail.missing.length
          ? (detail.covered.length ? "En commun : " + detail.covered.slice(0, 4).join(", ") : "Aucune compétence en commun") +
            (detail.missing.length ? " · Manque : " + detail.missing.slice(0, 4).join(", ") : "")
          : "";
      const company = o.companyName || o.recruiterName || "Entreprise";
      el.innerHTML =
        "<div><div class='offer-co'>" +
        "<a class='offer-co-logo' href='" + companyHref(o) + "'>" + esc(initials(company)) + "</a>" +
        "<div><a class='co-name' href='" + companyHref(o) + "'></a><h3></h3></div></div>" +
        '<p class="offer-essentials muted"></p>' +
        '<div class="offer-meta"></div>' +
        (matchPct != null
          ? '<div class="match-bar" title="Compatibilité compétences"><span style="width:' + Math.min(100, matchPct) + '%"></span></div>'
          : "") +
        (why ? '<p class="match-why muted"></p>' : "") +
        "</div><div class='stack'>" +
        "<span class='badge " +
        matchCls +
        "'></span>" +
                (sent
          ? "<a class='btn btn-primary btn-sm' href='candidate-applications.html?id=" +
            st.appId +
            "'>Voir ma candidature</a>"
          : "<button type='button' class='btn btn-primary btn-sm js-apply'>" +
            (withdrawn ? "Postuler à nouveau" : "Postuler") +
            "</button>") +
        "</div>";
      el.querySelector(".co-name").textContent = company;
      el.querySelector("h3").textContent = o.title;
      el.querySelector(".offer-essentials").textContent = [
        o.location || "Lieu à préciser",
        new RegExp(workLabel(mode, o.location), "i").test(o.location || "") ? null : workLabel(mode, o.location),
        o.contractType || null,
        o.responseSlaDays != null ? "Réponse " + o.responseSlaDays + " j" : null,
        sent ? "Candidature en cours" : withdrawn ? "Statut : Retirée" : null,
      ]
        .filter(Boolean)
        .join(" · ");
      el.querySelector(".badge").textContent = matchPct != null ? "Compat. " + matchPct + " %" : "—";
      el.querySelector(".badge").title =
        "Part des compétences en commun, rapportée à l’ensemble de votre profil et de l’offre.";
      const whyEl = el.querySelector(".match-why");
      if (whyEl) whyEl.textContent = why;
      const meta = el.querySelector(".offer-meta");
      String(o.requiredSkills || "")
        .split(",")
        .slice(0, 5)
        .forEach((t) => {
          if (!String(t).trim()) return;
          const b = document.createElement("span");
          b.className = "badge badge-chrome";
          b.textContent = String(t).trim();
          meta.appendChild(b);
        });
      const applyBtn = el.querySelector(".js-apply");
      if (applyBtn) applyBtn.addEventListener("click", (e) => { e.stopPropagation(); openApply(o); });
      el.querySelector(".co-name").addEventListener("click", (e) => e.stopPropagation());
      el.addEventListener("click", () => selectOffer(el, o));
      offerList.appendChild(el);
    });
    const focus = list.findIndex((o) => String(o.id) === String(focusId));
    const target = offerList.querySelectorAll(".offer")[Math.max(0, focus)];
    if (target) selectOffer(target, list[Math.max(0, focus)]);
    if (focus >= 0 && target) { target.scrollIntoView({ block: "center" }); focusId = null; }
  }

  function selectOffer(el, o) {
    const detail = document.getElementById("offer-detail");
    offerList.querySelectorAll(".offer").forEach(function (node) { node.classList.remove("is-selected"); });
    el.classList.add("is-selected");
    if (!detail) return;
    const company = o.companyName || o.recruiterName || "Entreprise";
    const st = appState(o.id);
    const sent = !!(st && st.status !== "WITHDRAWN");
    detail.innerHTML = "";
    const h = document.createElement("h2");
    h.textContent = o.title || "Offre";
    const sub = document.createElement("p");
    sub.className = "muted";
    sub.textContent = [company, o.location || "Lieu à préciser", o.contractType, o.responseSlaDays != null ? "Réponse " + o.responseSlaDays + " j" : ""].filter(Boolean).join(" · ");
    const body = document.createElement("div");
    body.className = "split-copy";
    body.textContent = o.description || "Le recruteur n’a pas encore rédigé la description.";
    const skills = document.createElement("div");
    skills.className = "tag-row";
    const mt = explainMatch(o.requiredSkills);
    mt.covered.forEach((t) => { const b = document.createElement("span"); b.className = "badge badge-ok"; b.title = "Dans votre profil"; b.textContent = "✓ " + t; skills.appendChild(b); });
    mt.missing.forEach((t) => { const b = document.createElement("span"); b.className = "badge badge-chrome"; b.title = "Absente de votre profil"; b.textContent = t; skills.appendChild(b); });
    const actions = document.createElement("div");
    actions.className = "row";
    if (sent) {
      const a = document.createElement("a");
      a.className = "btn btn-primary";
      a.href = "candidate-applications.html?id=" + st.appId;
      a.textContent = "Voir ma candidature";
      actions.appendChild(a);
    } else {
      const b = document.createElement("button");
      b.type = "button";
      b.className = "btn btn-primary";
      b.textContent = st && st.status === "WITHDRAWN" ? "Postuler à nouveau" : "Postuler";
      b.addEventListener("click", () => openApply(o));
      actions.appendChild(b);
    }
    detail.appendChild(h);
    detail.appendChild(sub);
    detail.appendChild(body);
    if (skills.children.length) detail.appendChild(skills);
    detail.appendChild(actions);
  }

  let offerStamp = "";

  async function load(quiet) {
    if (!quiet) offerList.innerHTML = '<div class="skeleton" style="height:80px"></div>';
    try {
      const [offers, mine, me] = await Promise.all([
        HireLinkAPI.api("/offers/public"),
        HireLinkAPI.api("/applications/mine").catch(() => []),
        HireLinkAPI.api("/me").catch(() => null),
      ]);
      cached = offers;
      const nextStamp = (offers || [])
        .map((o) => o.id + ":" + (o.matchScore == null ? "" : o.matchScore) + ":" + (o.published === false ? "0" : "1"))
        .join("|") + "~" + (mine || []).map((a) => a.id + ":" + a.status).join("|");
      if (quiet && nextStamp === offerStamp) return;
      offerStamp = nextStamp;
      mySkills = (me && me.skills) || "";
      appliedByOffer = new Map(
        (mine || []).map((a) => [a.jobOfferId, { appId: a.id, status: a.status }])
      );
      rebuildFacets();
      const scopeBar = document.getElementById("offer-scope");
      if (scopeBar) {
        scopeBar.querySelectorAll("button").forEach((b) => {
          b.classList.toggle("is-on", b.getAttribute("data-scope") === scope);
        });
      }
      render();
      if (quiet) return;
      const applyId = new URLSearchParams(location.search).get("apply");
      if (applyId) {
        history.replaceState(null, "", location.pathname);
        const target = cached.find((o) => String(o.id) === String(applyId));
        if (target) openApply(target);
      }
    } catch (err) {
      if (!quiet && offerList) offerList.innerHTML = "<p class='muted'>" + err.message + "</p>";
    }
  }

  const scopeBar = document.getElementById("offer-scope");
  if (scopeBar) {
    scopeBar.addEventListener("click", (e) => {
      const btn = e.target.closest("button[data-scope]");
      if (!btn) return;
      scope = btn.getAttribute("data-scope") || "all";
      scopeBar.querySelectorAll("button").forEach((b) => b.classList.toggle("is-on", b === btn));
      render();
    });
  }
  const clearFilters = document.getElementById("btn-clear-filters");
  if (clearFilters) {
    clearFilters.addEventListener("click", () => {
      ["f-city", "f-company", "f-skill", "f-contract", "f-mode", "f-sla", "f-sent", "f-match"].forEach((id) => {
        const node = document.getElementById(id);
        if (node) node.value = id === "f-match" ? "0" : "";
      });
      render();
    });
  }
  ["f-city", "f-company", "f-skill", "f-contract", "f-mode", "f-sla", "f-sent", "f-match"].forEach((id) => {
    const el = document.getElementById(id);
    if (el) el.addEventListener("change", render);
  });
  load();
  document.addEventListener("visibilitychange", () => { if (!document.hidden) load(true); });
})();
