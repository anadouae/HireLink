/** Offres côté candidat : recherche intelligente, filtres, option « correspond à mon profil », cartes et détail. */
(function () {
  const session = HireLinkApp.mount({ search: { placeholder: "Entreprise, poste, ville, compétence, contrat…", onQuery: () => render() } });
  if (!session) return;

  const O = HireLinkOffer;
  const S = HireLinkSearch;
  const offerList = document.getElementById("offer-list");
  const matchToggle = document.getElementById("match-only");
  const FILTERS = ["f-city", "f-company", "f-skill", "f-contract", "f-mode", "f-sla", "f-sent", "f-match"];
  const MATCH_KEY = "hirelink.matchOnly." + (session.userId || session.id || "me");

  let cached = [];
  let appliedByOffer = new Map();
  let selectedId = new URLSearchParams(location.search).get("offer");

  function readMatchOnly() {
    if (new URLSearchParams(location.search).get("scope") === "mine") return true;
    try { return localStorage.getItem(MATCH_KEY) === "1"; } catch (_) { return false; }
  }
  let matchOnly = readMatchOnly();

  const $ = (id) => document.getElementById(id);
  const val = (id) => ($(id) || {}).value || "";
  const companyOf = (o) => o.companyName || o.recruiterName || "Entreprise";
  const appState = (id) => appliedByOffer.get(id) || null;
  const isOpenToApply = (id) => { const st = appState(id); return !st || st.status === "WITHDRAWN"; };
  const modeKey = (o) => ({ "Télétravail": "REMOTE", "Hybride": "HYBRID", "Sur site": "ONSITE" })[O.workModeOf(o)] || "";

  function tokenize(csv) {
    return String(csv || "").split(/[,;]+/).map((s) => s.trim()).filter(Boolean);
  }

  function openApply(o) {
    if (!isOpenToApply(o.id)) {
      HireLink.toast("Déjà envoyée", "Ouvrez-la dans Mes candidatures. Le statut Retirée est le seul qui permet de renvoyer.", "info");
      return;
    }
    if (!window.HireLinkApply) { HireLink.toast("Erreur", "Assistant de candidature indisponible", "danger"); return; }
    HireLinkApply.start(o, load);
  }

  function fillSelect(id, values, label) {
    const sel = $(id);
    const keep = sel.value;
    const first = sel.querySelector("option");
    sel.replaceChildren(first);
    values.forEach((v) => {
      const opt = document.createElement("option");
      opt.value = v;
      opt.textContent = label ? label(v) : v;
      sel.appendChild(opt);
    });
    if ([].some.call(sel.options, (o) => o.value === keep)) sel.value = keep;
  }

  function rebuildFacets() {
    const uniq = (list) => Array.from(new Set(list.filter(Boolean))).sort((a, b) => a.localeCompare(b, "fr"));
    fillSelect("f-city", uniq(cached.map(O.placeOf)), (c) => (HireLinkPlaces.flagOf(c) + " " + c).trim());
    fillSelect("f-contract", uniq(cached.map((o) => (o.contractType || "").trim())));
    fillSelect("f-company", uniq(cached.map((o) => companyOf(o).trim())));
    fillSelect("f-skill", uniq(cached.flatMap((o) => tokenize(o.requiredSkills))));
  }

  /** Filtres précis (listes), puis recherche libre classée par pertinence. */
  function filtered(query) {
    const city = val("f-city"), company = val("f-company"), skill = val("f-skill"), contract = val("f-contract");
    const mode = val("f-mode"), sentFilter = val("f-sent");
    const slaMax = Number(val("f-sla") || 0), minMatch = Number(val("f-match") || 0);
    const kept = cached.filter((o) => {
      const score = o.matchScore == null ? 0 : o.matchScore;
      const st = appState(o.id);
      if (matchOnly && !o.matchesProfile) return false;
      if (minMatch && score < minMatch) return false;
      if (city && O.placeOf(o) !== city) return false;
      if (company && companyOf(o) !== company) return false;
      if (skill && !tokenize(o.requiredSkills).some((s) => s.toLowerCase() === skill.toLowerCase())) return false;
      if (contract && (o.contractType || "") !== contract) return false;
      if (mode && modeKey(o) !== mode) return false;
      if (slaMax && (o.responseSlaDays == null || o.responseSlaDays > slaMax)) return false;
      if (sentFilter === "open" && st && st.status !== "WITHDRAWN") return false;
      if (sentFilter === "sent" && (!st || st.status === "WITHDRAWN")) return false;
      if (sentFilter === "withdrawn" && (!st || st.status !== "WITHDRAWN")) return false;
      return true;
    });
    if (query) {
      return S.rank(kept, query, [
        { get: (o) => o.title, weight: 3 },
        { get: companyOf, weight: 2 },
        { get: (o) => o.requiredSkills, weight: 2 },
        { get: (o) => O.placeOf(o) + " " + O.workModeOf(o), weight: 2 },
        { get: (o) => o.contractType, weight: 1 },
        { get: (o) => o.description, weight: 1 },
      ]);
    }
    return kept.sort((a, b) =>
      (isOpenToApply(b.id) - isOpenToApply(a.id)) || (b.matchScore || 0) - (a.matchScore || 0) || String(a.title || "").localeCompare(String(b.title || ""), "fr"));
  }

  function clearAll() {
    FILTERS.forEach((id) => { if ($(id)) $(id).value = id === "f-match" ? "0" : ""; });
    const search = $("rc-search");
    if (search) search.value = "";
    render();
  }

  function setMatchOnly(on) {
    matchOnly = on;
    matchToggle.checked = on;
    try { localStorage.setItem(MATCH_KEY, on ? "1" : "0"); } catch (_) { /* stockage refusé : le choix vaut pour cette page seulement */ }
    render();
  }

  function paintEmpty(query) {
    if (matchOnly && !query) {
      S.emptyState(offerList, "", {
        emptyTitle: "Aucune offre ne correspond encore à votre profil",
        emptyHint: "Ajoutez vos compétences à votre profil pour être orienté vers les bonnes offres, ou affichez toutes les offres.",
      });
      const row = document.createElement("div");
      row.className = "row";
      const a = document.createElement("a");
      a.className = "btn btn-soft btn-sm";
      a.href = "candidate-profile.html";
      a.textContent = "Compléter mon profil";
      const b = document.createElement("button");
      b.type = "button";
      b.className = "btn btn-primary btn-sm";
      b.textContent = "Voir toutes les offres";
      b.addEventListener("click", () => setMatchOnly(false));
      row.append(a, b);
      offerList.querySelector(".hl-noresult").appendChild(row);
      return;
    }
    S.emptyState(offerList, query, { emptyTitle: "Aucune offre pour ces filtres", emptyHint: "Modifiez ou effacez les filtres pour élargir la recherche.", onClear: clearAll });
    if (!query) {
      const b = document.createElement("button");
      b.type = "button";
      b.className = "btn btn-soft btn-sm";
      b.textContent = "Effacer les filtres";
      b.addEventListener("click", clearAll);
      offerList.querySelector(".hl-noresult").appendChild(b);
    }
  }

  function updateHint() {
    const n = cached.filter((o) => o.matchesProfile).length;
    $("match-hint").textContent = n
      ? n + " offre" + (n > 1 ? "s" : "") + " sur " + cached.length + " correspondent à vos compétences."
      : "Complétez vos compétences dans votre profil pour voir les offres adaptées.";
  }

  function render() {
    const query = val("rc-search").trim();
    const list = filtered(query);
    const active = FILTERS.filter((id) => val(id) && val(id) !== "0").length;
    $("filter-summary").textContent = list.length + " offre" + (list.length > 1 ? "s" : "") + (active ? " · " + active + " filtre" + (active > 1 ? "s" : "") : "");
    offerList.replaceChildren();
    const detail = $("offer-detail");
    if (!list.length) {
      paintEmpty(query);
      detail.replaceChildren();
      const p = document.createElement("p");
      p.className = "muted";
      p.textContent = "Aucune offre à afficher.";
      detail.appendChild(p);
      return;
    }
    const current = list.find((o) => String(o.id) === String(selectedId)) || list[0];
    selectedId = current.id;
    list.forEach((o) => offerList.appendChild(card(o, o.id === current.id)));
    S.mark(offerList, query);
    showDetail(current);
  }

  function card(o, selected) {
    const st = appState(o.id);
    const badge = o.matchScore != null ? document.createElement("span") : null;
    if (badge) {
      badge.textContent = Math.round(o.matchScore) + " %";
      badge.title = "Compatibilité : part des compétences demandées que vous possédez.";
    }
    return O.card(o, {
      selected,
      badge,
      href: (x) => "candidate.html?offer=" + encodeURIComponent(x.id),
      onOpen: (x) => { selectedId = x.id; offerList.querySelectorAll(".offer-card").forEach((n) => n.classList.toggle("is-selected", n.dataset.offerId === String(x.id))); showDetail(x); },
      statusChip: st ? (st.status === "WITHDRAWN" ? "Candidature retirée" : "Candidature envoyée") : "",
    });
  }

  function showDetail(o) {
    const detail = $("offer-detail");
    const st = appState(o.id);
    const sent = !!(st && st.status !== "WITHDRAWN");
    detail.replaceChildren();

    const head = document.createElement("div");
    head.className = "detail-head";
    const titles = document.createElement("div");
    const h2 = document.createElement("h2");
    h2.textContent = o.title || "Offre";
    const company = document.createElement("a");
    company.className = "detail-company";
    company.href = "../company.html?name=" + encodeURIComponent(companyOf(o));
    company.textContent = companyOf(o);
    const place = O.placeOf(o);
    const where = document.createElement("p");
    where.className = "muted";
    where.textContent = [(HireLinkPlaces.flagOf(place) + " " + place).trim(), o.contractType, o.responseSlaDays != null ? "Réponse sous " + o.responseSlaDays + " jours" : ""].filter(Boolean).join(" · ");
    titles.append(h2, company, where);
    head.appendChild(titles);
    if (o.matchScore != null) {
      const meter = document.createElement("div");
      meter.className = "match-meter";
      meter.setAttribute("role", "img");
      meter.setAttribute("aria-label", "Compatibilité " + Math.round(o.matchScore) + " %");
      const pct = document.createElement("strong");
      pct.textContent = Math.round(o.matchScore) + " %";
      const bar = document.createElement("div");
      bar.className = "match-bar";
      const fill = document.createElement("span");
      fill.style.width = Math.min(100, Math.round(o.matchScore)) + "%";
      bar.appendChild(fill);
      const label = document.createElement("small");
      label.textContent = "compatible avec votre profil";
      meter.append(pct, bar, label);
      head.appendChild(meter);
    }

    const body = document.createElement("div");
    body.className = "split-copy";
    body.textContent = o.description || "Le recruteur n’a pas encore rédigé la description.";

    const skillsTitle = document.createElement("h3");
    skillsTitle.className = "detail-sub";
    skillsTitle.textContent = "Compétences demandées";
    const boxes = document.createElement("div");
    HireLinkSkills.renderBoxes(boxes, o.requiredSkills, { empty: "Aucune compétence précisée." }).then(() => {
      const have = new Set((o.matchedSkills || []).map(S.compact));
      const lack = new Set((o.missingSkills || []).map(S.compact));
      boxes.querySelectorAll(".sk-chip").forEach((chip) => {
        const key = S.compact(chip.firstChild.textContent);
        if (have.has(key)) { chip.classList.add("is-match"); chip.title = "Dans votre profil"; }
        else if (lack.has(key)) { chip.classList.add("is-missing"); chip.title = "Absente de votre profil"; }
      });
    });

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
    detail.append(head, O.facts(o), body, skillsTitle, boxes, actions);
    S.mark(detail, val("rc-search").trim());
  }

  let offerStamp = "";

  async function load(quiet) {
    if (!quiet) { offerList.replaceChildren(); const sk = document.createElement("div"); sk.className = "skeleton"; sk.style.height = "120px"; offerList.appendChild(sk); }
    try {
      const [offers, mine] = await Promise.all([
        HireLinkAPI.api("/offers/public"),
        HireLinkAPI.api("/applications/mine").catch(() => []),
        HireLinkPlaces.ready(),
      ]);
      const stamp = (offers || []).map((o) => o.id + ":" + (o.matchScore == null ? "" : o.matchScore)).join("|") + "~" + (mine || []).map((a) => a.id + ":" + a.status).join("|");
      if (quiet && stamp === offerStamp) return;
      offerStamp = stamp;
      cached = offers || [];
      appliedByOffer = new Map((mine || []).map((a) => [a.jobOfferId, { appId: a.id, status: a.status }]));
      matchToggle.checked = matchOnly;
      rebuildFacets();
      updateHint();
      render();
      const applyId = new URLSearchParams(location.search).get("apply");
      if (applyId && !quiet) {
        history.replaceState(null, "", location.pathname);
        const target = cached.find((o) => String(o.id) === String(applyId));
        if (target) openApply(target);
      }
    } catch (err) {
      if (!quiet) {
        offerList.replaceChildren();
        const p = document.createElement("p");
        p.className = "muted";
        p.textContent = err.message;
        offerList.appendChild(p);
      }
    }
  }

  matchToggle.addEventListener("change", () => setMatchOnly(matchToggle.checked));
  $("btn-clear-filters").addEventListener("click", clearAll);
  FILTERS.forEach((id) => $(id).addEventListener("change", render));
  load();
  document.addEventListener("visibilitychange", () => { if (!document.hidden) load(true); });
})();
