/** Offres candidat — recherche à la demande, grille responsive, panneau latéral. */
(function () {
  let scheduleSearch = function () {};
  const session = HireLinkApp.mount({
    search: {
      placeholder: "développeur, infirmier, commercial…",
      onQuery: (q) => { setSearchVal(q || ""); scheduleSearch(); },
    },
  });
  if (!session) return;

  const O = HireLinkOffer;
  const S = HireLinkSearch;
  const offerList = document.getElementById("offer-list");
  const detail = document.getElementById("offer-detail");
  const scrim = document.getElementById("cs-scrim");
  const matchToggle = document.getElementById("match-only");
  const FILTERS = [
    "f-where", "f-contract", "f-mode", "f-since", "f-company",
    "f-experience", "f-sector", "f-salary", "f-origin", "f-skill", "f-sent", "f-pace",
  ];
  /** Filtres rangés sous « Plus de filtres ». */
  const MORE_FILTERS = ["f-experience", "f-sector", "f-salary"];
  /** Filtres sans pastille (provenance gérée par les onglets, champs techniques masqués). */
  const NO_CHIP = ["f-origin", "f-skill", "f-sent", "f-pace"];
  const PAGE_SIZE = 24;
  const MATCH_KEY = "hirelink.matchOnly." + (session.userId || session.id || "me");

  let page = 0;
  let totalElements = 0;
  let totalPages = 0;
  let loading = false;
  let cached = [];
  /** Affichage progressif : offres HireLink d'abord, offres web ajoutées à la suite quand elles arrivent. */
  let hlItems = [];
  let webItems = [];
  let hlTotal = 0;
  let webTotal = 0;
  let hlFacets = null;
  let webFacets = null;
  let hlSalaryAvailable = undefined;
  let webState = "off"; // off | loading | done | failed
  let webMeta = null;
  let loadSeq = 0;
  let reloadTimer = 0;
  /** Délai unique appliqué à chaque changement de filtre (évite une requête par clic). */
  const FILTER_DEBOUNCE_MS = 300;
  let appliedByOffer = new Map();
  let selectedId = new URLSearchParams(location.search).get("offer");
  let hasSearched = !!(selectedId || new URLSearchParams(location.search).get("apply") || new URLSearchParams(location.search).get("q"));
  let matchOnly = false;
  try { matchOnly = localStorage.getItem(MATCH_KEY) === "1"; } catch (_) {}
  if (new URLSearchParams(location.search).get("scope") === "mine") matchOnly = true;

  const $ = (id) => document.getElementById(id);
  const val = (id) => ($(id) || {}).value || "";
  function searchVal() {
    const a = $("cs-search");
    const b = $("rc-search");
    const av = a ? String(a.value || "").trim() : "";
    const bv = b ? String(b.value || "").trim() : "";
    return av || bv || (a ? a.value : "") || (b ? b.value : "") || "";
  }
  function setSearchVal(v) {
    const a = $("cs-search");
    const b = $("rc-search");
    if (a) a.value = v;
    if (b) b.value = v;
  }
  const companyOf = (o) => o.companyName || o.recruiterName || "Entreprise";
  const appState = (id) => appliedByOffer.get(id) || null;
  const isOpenToApply = (id) => { const st = appState(id); return !st || st.status === "WITHDRAWN"; };

  const WEB_SRC_LABEL = {
    REMOTIVE: "Remotive", ARBEITNOW: "Arbeitnow", REMOTE_OK: "Remote OK", JOOBLE: "Jooble",
    WE_WORK_REMOTELY: "We Work Remotely", GREENHOUSE: "Greenhouse", LEVER: "Lever", ASHBY: "Ashby",
    HIMALAYAS: "Himalayas", JOBICY: "Jobicy", ADZUNA: "Adzuna",
    WEB_FALLBACK: "Web", TAVILY: "Tavily", SERPER: "Serper",
    HIRELINK: "HireLink",
  };
  const WEB_SRC_HOME = {
    REMOTIVE: "https://remotive.com", ARBEITNOW: "https://www.arbeitnow.com",
    REMOTE_OK: "https://remoteok.com", JOOBLE: "https://ma.jooble.org",
    WE_WORK_REMOTELY: "https://weworkremotely.com",
    HIMALAYAS: "https://himalayas.app", JOBICY: "https://jobicy.com",
    ADZUNA: "https://www.adzuna.com",
  };
  const FILTER_CHIP_LABELS = {
    "f-where": "Où", "f-contract": "Contrat", "f-mode": "Mode", "f-since": "Date",
    "f-company": "Entreprise", "f-experience": "Expérience", "f-salary": "Salaire", "f-sector": "Secteur",
  };
  /** Liste déroulante → clé des compteurs renvoyés par le serveur (data.facets). */
  const FACET_KEYS = {
    "f-contract": "contract", "f-mode": "workMode", "f-since": "since",
    "f-experience": "experience", "f-sector": "sector", "f-salary": "minSalary",
  };

  function webSourceLabel(o) {
    const code = String((o && o.source) || "").toUpperCase();
    const raw = (o && o.sourceLabel) || "";
    if (raw && raw !== "Recherche Web") return raw;
    return WEB_SRC_LABEL[code] || raw || code || "Web";
  }
  function webSourceHome(o) {
    const code = String((o && o.source) || "").toUpperCase();
    return (o && o.sourceHome) || WEB_SRC_HOME[code] || "";
  }

  function openApply(o) {
    if (o && o.external) return;
    if (!isOpenToApply(o.id)) {
      HireLink.toast("Déjà envoyée", "Ouvrez-la dans Mes candidatures.", "info");
      return;
    }
    if (!window.HireLinkApply) { HireLink.toast("Erreur", "Assistant de candidature indisponible", "danger"); return; }
    if (window.HireLinkApplyGate && typeof HireLinkApplyGate.ensureCompleteThenApply === "function") {
      HireLinkApplyGate.ensureCompleteThenApply(o, () => load(true, false));
      return;
    }
    HireLinkApply.start(o, () => load(true, false));
  }

  function fillSelect(id, values, labelFn) {
    const sel = $(id);
    if (!sel) return;
    const keep = sel.value;
    const first = sel.querySelector("option");
    sel.replaceChildren(first);
    values.forEach((v) => {
      const opt = document.createElement("option");
      opt.value = v;
      opt.textContent = labelFn ? labelFn(v) : v;
      sel.appendChild(opt);
    });
    if ([].some.call(sel.options, (o) => o.value === keep)) sel.value = keep;
  }

  function filtered() {
    // Salaire / expérience / secteur : filtrés côté serveur (/catalog/offers).
    return cached;
  }

  function currentScope() {
    const track = $("cs-scope");
    return (track && track.getAttribute("data-scope")) || "all";
  }

  function syncScopeThumb() {
    const track = $("cs-scope");
    if (!track) return;
    const thumb = track.querySelector(".cs-scope-thumb");
    const active = track.querySelector(".cs-scope-btn.is-active") || track.querySelector(".cs-scope-btn");
    if (!thumb || !active) return;
    // Mesure réelle du bouton actif (évite le bug translateX % relatif au thumb).
    const left = active.offsetLeft;
    const width = active.offsetWidth;
    track.style.setProperty("--cs-thumb-left", left + "px");
    track.style.setProperty("--cs-thumb-width", width + "px");
  }

  function setScope(scope, reload) {
    const next = scope === "hirelink" || scope === "external" ? scope : "all";
    const track = $("cs-scope");
    if (track) track.setAttribute("data-scope", next);
    track && track.querySelectorAll(".cs-scope-btn").forEach((btn) => {
      const on = btn.getAttribute("data-scope") === next;
      btn.classList.toggle("is-active", on);
      btn.setAttribute("aria-pressed", on ? "true" : "false");
    });
    syncScopeThumb();
    const origin = $("f-origin");
    if (origin) {
      origin.value = next === "hirelink" ? "hirelink" : (next === "external" ? "external" : "");
    }
    if (reload && hasSearched) scheduleReload();
  }

  function buildCatalogQuery(pageNum) {
    const params = new URLSearchParams();
    params.set("page", String(pageNum));
    params.set("size", String(PAGE_SIZE));
    params.set("forYou", matchOnly ? "true" : "false");
    params.set("facets", "true");
    const search = searchVal().trim();
    if (search) params.set("q", search);
    if (val("f-where").trim()) params.set("where", val("f-where").trim());
    if (val("f-contract")) params.set("contract", val("f-contract"));
    if (val("f-mode")) params.set("workMode", val("f-mode"));
    if (val("f-since")) params.set("sinceDays", val("f-since"));
    const minSal = Number(val("f-salary") || 0);
    if (minSal > 0) params.set("minSalary", String(minSal));
    if (val("f-experience")) params.set("experience", val("f-experience"));
    if (val("f-sector")) params.set("sector", val("f-sector"));
    if (val("f-company").trim()) params.set("company", val("f-company").trim());
    const scope = currentScope();
    if (scope === "hirelink") params.set("origin", "internal");
    else if (scope === "external") params.set("origin", "external");
    const sort = val("f-sort");
    if (sort && sort !== "relevance") params.set("sort", sort);
    return params.toString();
  }

  /** Mêmes critères pour /catalog/web-offers (sans pagination ni classement profil). */
  function buildWebQuery() {
    const params = new URLSearchParams(buildCatalogQuery(0));
    ["page", "size", "forYou"].forEach((k) => params.delete(k));
    return params.toString();
  }

  /** Recharge après un changement de filtre, une seule fois si plusieurs changements se suivent en 300 ms. */
  function scheduleReload() {
    paintActiveChips();
    clearTimeout(reloadTimer);
    reloadTimer = setTimeout(() => { if (hasSearched) load(false, false); }, FILTER_DEBOUNCE_MS);
  }

  /** Vide les filtres (pas la recherche ni le tri). */
  function clearFilters() {
    FILTERS.forEach((id) => { if ($(id)) { $(id).value = ""; $(id).dataset.applied = ""; } });
    setScope("all", false);
  }

  function clearAll() {
    clearFilters();
    if ($("f-sort")) $("f-sort").value = "relevance";
    setSearchVal("");
    hasSearched = false;
    paintActiveChips();
  }

  /** Libellé d'origine d'une option, sans le compteur « (12) ». */
  function baseLabel(opt) {
    if (!opt) return "";
    if (!opt.dataset.label) opt.dataset.label = String(opt.textContent || "").trim();
    return opt.dataset.label;
  }

  function filterDisplayValue(id) {
    const el = $(id);
    if (!el) return "";
    if (el.tagName === "SELECT") return baseLabel(el.options[el.selectedIndex]);
    return String(el.value || "").trim();
  }

  function paintMoreCount() {
    const n = MORE_FILTERS.filter((id) => val(id)).length;
    const badge = $("more-count");
    if (!badge) return;
    badge.hidden = n === 0;
    badge.textContent = n ? String(n) : "";
  }

  function paintActiveChips() {
    const box = $("cs-active-chips");
    const bar = $("cs-active-bar");
    if (!box) return;
    box.replaceChildren();
    paintMoreCount();
    const chips = [];
    FILTERS.forEach((id) => {
      if (NO_CHIP.indexOf(id) >= 0) return;
      const raw = String(val(id)).trim();
      if (!raw) return;
      chips.push({ id: id, label: FILTER_CHIP_LABELS[id] || id, value: filterDisplayValue(id) || raw });
    });
    if (matchOnly) chips.unshift({ id: "match-only", label: "Profil", value: "Correspond à mon profil" });
    if (!chips.length) { if (bar) bar.hidden = true; box.hidden = true; return; }
    if (bar) bar.hidden = false;
    box.hidden = false;
    chips.forEach((c) => {
      const btn = document.createElement("button");
      btn.type = "button";
      btn.className = "cs-active-chip";
      btn.setAttribute("aria-label", "Retirer le filtre " + c.label);
      const lab = document.createElement("span");
      lab.textContent = c.label + " : " + c.value;
      const x = document.createElement("span");
      x.setAttribute("aria-hidden", "true");
      x.textContent = "×";
      btn.append(lab, x);
      btn.addEventListener("click", () => {
        if (c.id === "match-only") setMatchOnly(false);
        else if ($(c.id)) {
          $(c.id).value = "";
          $(c.id).dataset.applied = "";
          scheduleReload();
        }
      });
      box.appendChild(btn);
    });
  }

  /** Compteurs serveur à côté de chaque choix (« Stage (12) ») ; ils tiennent compte des autres filtres actifs. */
  function paintFacets(facets, salaryAvailable) {
    Object.keys(FACET_KEYS).forEach((id) => {
      const sel = $(id);
      if (!sel) return;
      const counts = facets ? (facets[FACET_KEYS[id]] || {}) : null;
      const lookup = {};
      if (counts) Object.keys(counts).forEach((k) => { lookup[S.fold(k)] = counts[k]; });
      // Contrats présents dans les résultats mais absents de la liste : ajoutés à la volée.
      [].slice.call(sel.querySelectorAll("option[data-dynamic]")).forEach((opt) => {
        if (opt.value !== sel.value) opt.remove();
      });
      if (id === "f-contract" && counts) {
        const known = new Set([].map.call(sel.options, (o) => S.fold(o.value)));
        Object.keys(counts).forEach((label) => {
          if (!label || label === "NON-PRECISE" || known.has(S.fold(label)) || !(counts[label] > 0)) return;
          const opt = document.createElement("option");
          opt.value = label;
          const pretty = String(label).replace(/[_-]+/g, " ").trim();
          opt.textContent = pretty.charAt(0).toUpperCase() + pretty.slice(1);
          opt.dataset.dynamic = "1";
          sel.appendChild(opt);
          known.add(S.fold(label));
        });
      }
      [].forEach.call(sel.options, (opt) => {
        const label = baseLabel(opt);
        if (!opt.value || !counts) { opt.textContent = label; return; }
        const n = lookup[S.fold(opt.value)] || 0;
        opt.textContent = label + " (" + n + ")";
      });
    });
    const wrap = $("f-salary-wrap");
    const note = $("f-salary-note");
    const hideSalary = !!facets && salaryAvailable === false && !val("f-salary");
    if (wrap) wrap.hidden = hideSalary;
    if (note) note.hidden = !hideSalary;
  }

  function paintEmptyFirst() {
    // Annule une recherche (ou une réponse web) encore en route.
    loadSeq++;
    clearTimeout(reloadTimer);
    webState = "off";
    offerList.className = "";
    offerList.replaceChildren();
    const box = document.createElement("div");
    box.className = "cs-empty";
    box.innerHTML = "<h2>Commencez par une recherche</h2>"
      + "<p>Aucune offre n’est affichée tant que vous n’avez pas saisi un métier. Exemples : développeur, infirmier, commercial.</p>";
    offerList.appendChild(box);
    closeDrawer();
    $("filter-summary").textContent = "En attente d’une recherche";
    $("match-hint").textContent = "Lancez une recherche pour voir les résultats.";
    const pager = $("catalog-pager");
    if (pager) pager.hidden = true;
  }

  function paintWebBanner(data) {
    const el = $("web-search-banner");
    const fresh = $("catalog-freshness");
    if (!el) return;
    const bits = [];
    if (webState === "loading") bits.push("Recherche web en direct en cours sur les sites d’emploi publics…");
    else if (webState === "failed") bits.push("Recherche web momentanément indisponible : les offres HireLink restent affichées.");
    else if (webState === "done" && data && Array.isArray(data.webSearchSources) && data.webSearchAvailable !== false) {
      // Bilan lisible : offres web réellement affichées, sites qui ont répondu, sites ignorés.
      const used = data.webSearchSources.filter((s) => s && s.status === "OK" && s.hits > 0).map((s) => s.label || s.code);
      const down = data.webSearchSources.filter((s) => s && (s.status === "ERROR" || s.status === "TIMEOUT"
        || (s.status === "SKIPPED" && /échecs/.test(s.note || "")))).length;
      let txt = webTotal === 0
        ? "Aucune offre web en direct pour ces critères."
        : webTotal + " offre" + (webTotal > 1 ? "s" : "") + " web en direct" + (used.length ? " (" + used.join(", ") + ")" : "") + ".";
      if (down) txt += " " + down + " site" + (down > 1 ? "s n’ont" : " n’a") + " pas répondu à temps : résultats partiels.";
      txt += " Rien n’est stocké chez nous.";
      bits.push(txt);
    }
    else if (data && data.webSearchAvailable === false && data.webSearchStatus !== "SKIPPED" && data.webSearchStatus !== "DEFERRED") {
      bits.push("Recherche web indisponible pour le moment : seules les offres HireLink sont affichées.");
    }
    if (bits.length) { el.hidden = false; el.textContent = bits.join(" "); }
    else { el.hidden = true; el.textContent = ""; }
    // Plus de mention de « cache » : les offres HireLink sont lues en base, les offres web en direct.
    if (fresh) fresh.hidden = true;
  }

  function fmtDate(o) {
    const raw = o.publishedAt || o.createdAt || o.cacheFreshnessAt;
    if (!raw) return "";
    const d = new Date(raw);
    if (isNaN(d.getTime())) return "";
    return d.toLocaleDateString("fr-FR");
  }

  function fmtSalary(o) {
    const a = o.salaryMin;
    const b = o.salaryMax;
    if (a == null && b == null) return "";
    const fmt = (n) => Number(n).toLocaleString("fr-FR");
    const cur = o.external ? (o.salaryCurrency || "") : (o.salaryCurrency || "MAD");
    let text = (a != null && b != null && a !== b)
      ? fmt(a) + " – " + fmt(b)
      : fmt(a != null ? a : b);
    if (cur) text += " " + cur;
    // Période : seulement pour les offres web, et seulement si la source la dit.
    if (o.external) {
      const labels = { YEAR: "par an", MONTH: "par mois", WEEK: "par semaine", DAY: "par jour", HOUR: "par heure" };
      text += " " + (labels[o.salaryPeriod] || "période non précisée");
    }
    return text;
  }

  /** Score affiché sur la carte seulement si le profil du candidat permet de le calculer. */
  function compatPercent(o) {
    if (!o || !o.matchAvailable || o.matchScore == null) return null;
    const n = Math.round(Number(o.matchScore));
    return isNaN(n) ? null : Math.max(0, Math.min(100, n));
  }

  function originBadge(o) {
    if (o.external) return webSourceLabel(o);
    return "HireLink";
  }

  function openDrawer() {
    detail.classList.add("is-open");
    if (scrim) { scrim.hidden = false; scrim.classList.add("is-open"); }
  }
  function closeDrawer() {
    detail.classList.remove("is-open");
    detail.replaceChildren();
    if (scrim) { scrim.classList.remove("is-open"); scrim.hidden = true; }
    selectedId = null;
  }

  function applyBtn(o, compact) {
    const st = appState(o.id);
    if (o.external) {
      const policy = O.externalLinkPolicy ? O.externalLinkPolicy(o) : { trusted: /^https:\/\//i.test(o.applyUrl || o.originUrl || ""), href: o.applyUrl || o.originUrl, label: "Postuler" };
      if (!policy.trusted || !policy.href) return null;
      const a = document.createElement("a");
      a.className = compact ? "btn btn-soft btn-sm" : "btn btn-primary";
      a.href = policy.href;
      a.target = "_blank";
      a.rel = "noopener";
      a.textContent = compact ? "Postuler" : "Postuler sur le site d'origine";
      a.addEventListener("click", (e) => e.stopPropagation());
      return a;
    }
    if (st && st.status !== "WITHDRAWN") {
      const a = document.createElement("a");
      a.className = compact ? "btn btn-soft btn-sm" : "btn btn-primary";
      a.href = "candidate-applications.html?id=" + st.appId;
      a.textContent = compact ? "Voir" : "Voir ma candidature";
      a.addEventListener("click", (e) => e.stopPropagation());
      return a;
    }
    const b = document.createElement("button");
    b.type = "button";
    b.className = compact ? "btn btn-primary btn-sm" : "btn btn-primary";
    b.textContent = st && st.status === "WITHDRAWN" ? "Postuler à nouveau" : (compact ? "Postuler" : "Postuler sur HireLink");
    b.addEventListener("click", (e) => { e.stopPropagation(); openApply(o); });
    return b;
  }

  function card(o, selected) {
    const btn = document.createElement("article");
    btn.className = "cs-card" + (selected ? " is-selected" : "");
    btn.tabIndex = 0;
    btn.setAttribute("role", "button");
    const co = document.createElement("p");
    co.className = "cs-co";
    co.textContent = companyOf(o);
    const h3 = document.createElement("h3");
    h3.textContent = o.title || "Offre";
    const meta = document.createElement("div");
    meta.className = "cs-meta";
    const bits = [
      o.location || o.city || "",
      o.contractType || "",
      fmtDate(o),
      fmtSalary(o),
    ].filter(Boolean);
    bits.forEach((t) => {
      const s = document.createElement("span");
      s.textContent = t;
      meta.appendChild(s);
    });
    const src = document.createElement("span");
    src.className = "cs-src";
    src.textContent = originBadge(o);
    meta.appendChild(src);
    // Badge d'origine : Offre HireLink (candidature dans HireLink) / Offre web (en direct, non stockée).
    const badge = document.createElement("span");
    badge.className = "cs-origin-badge";
    badge.textContent = o.external ? "Offre web" : "Offre HireLink";
    meta.appendChild(badge);
    const compat = compatPercent(o);
    if (compat != null) {
      const c = document.createElement("span");
      c.className = "cs-compat" + (compat >= 70 ? " is-high" : (compat >= 40 ? " is-mid" : ""));
      c.textContent = "Compatible à " + compat + " %";
      c.title = o.matchLabel || "Estimation de compatibilité avec votre profil.";
      meta.appendChild(c);
    }
    const actions = document.createElement("div");
    actions.className = "cs-card-actions";
    const apply = applyBtn(o, true);
    if (apply) actions.appendChild(apply);
    btn.append(co, h3, meta, actions);
    btn.addEventListener("click", () => showDetail(o));
    btn.addEventListener("keydown", (e) => {
      if (e.key === "Enter" || e.key === " ") { e.preventDefault(); showDetail(o); }
    });
    return btn;
  }

  function showDetail(o) {
    selectedId = o.id;
    offerList.querySelectorAll(".cs-card").forEach((n) => n.classList.toggle("is-selected", false));
    // re-render selection lightly
    renderGrid(false);
    detail.replaceChildren();
    const head = document.createElement("div");
    head.className = "cs-drawer-head";
    const titles = document.createElement("div");
    const h2 = document.createElement("h2");
    h2.textContent = o.title || "Offre";
    const co = document.createElement("p");
    co.className = "muted";
    co.textContent = [companyOf(o), o.location || o.city, o.contractType, fmtSalary(o)].filter(Boolean).join(" · ");
    titles.append(h2, co);
    const close = document.createElement("button");
    close.type = "button";
    close.className = "btn btn-ghost btn-sm";
    close.textContent = "Fermer";
    close.addEventListener("click", closeDrawer);
    head.append(titles, close);

    const body = document.createElement("div");
    body.className = "cs-drawer-body";
    const desc = document.createElement("div");
    desc.className = "split-copy";
    desc.textContent = o.description || "Description non fournie.";
    const skillsTitle = document.createElement("h3");
    skillsTitle.className = "detail-sub";
    skillsTitle.textContent = "Compétences";
    const boxes = document.createElement("div");
    HireLinkSkills.renderBoxes(boxes, o.requiredSkills, { empty: "Aucune compétence précisée." });
    const actions = document.createElement("div");
    actions.className = "row";
    const apply = applyBtn(o, false);
    if (apply) actions.appendChild(apply);
    if (!o.external) {
    } else if (/^https:\/\//i.test(webSourceHome(o))) {
      const source = document.createElement("a");
      source.className = "btn btn-ghost";
      source.href = webSourceHome(o);
      source.target = "_blank";
      source.rel = "noopener";
      source.textContent = "Source : " + webSourceLabel(o);
      actions.appendChild(source);
    }
    const matchBlock = document.createElement("div");
    matchBlock.className = "cs-match-block";
    if (o.matchBreakdown && window.HireLinkMatch && typeof HireLinkMatch.panel === "function") {
      const panel = HireLinkMatch.panel(o.matchBreakdown, { audience: "candidate" });
      if (panel) matchBlock.appendChild(panel);
    } else if ((o.skillsPresent && o.skillsPresent.length) || (o.skillsMissing && o.skillsMissing.length)) {
      const h = document.createElement("h3");
      h.className = "detail-sub";
      h.textContent = o.matchAvailable
        ? ("Compatibilité " + (o.matchScore != null ? Math.round(o.matchScore) + " %" : ""))
        : "Compétences vs profil";
      matchBlock.appendChild(h);
      const note = document.createElement("p");
      note.className = "muted";
      note.textContent = o.matchLabel || "Reclassement transparent : compétences présentes et manquantes.";
      matchBlock.appendChild(note);
      if (o.skillsPresent && o.skillsPresent.length) {
        const ok = document.createElement("p");
        ok.innerHTML = "<strong>Présentes :</strong> ";
        ok.appendChild(document.createTextNode(o.skillsPresent.join(", ")));
        matchBlock.appendChild(ok);
      }
      if (o.skillsMissing && o.skillsMissing.length) {
        const miss = document.createElement("p");
        miss.innerHTML = "<strong>Manquantes :</strong> ";
        miss.appendChild(document.createTextNode(o.skillsMissing.join(", ")));
        matchBlock.appendChild(miss);
      }
    } else if (matchOnly || o.matchLabel) {
      const note = document.createElement("p");
      note.className = "muted";
      note.textContent = o.matchLabel || "Activez des compétences dans votre profil pour voir présentes / manquantes.";
      matchBlock.appendChild(note);
    }
    const srcLine = document.createElement("p");
    srcLine.className = "cs-src-line muted";
    srcLine.textContent = "Source : " + originBadge(o)
      + (o.provenance && o.provenance.url ? " · " + o.provenance.url : "");

    if (o.external) {
      const notice = document.createElement("p");
      notice.className = "muted";
      notice.textContent = o.applyHint || "Candidature sur le site d’origine — HireLink ne suit pas ce dossier.";
      body.append(desc, skillsTitle, boxes, matchBlock, srcLine, notice, actions);
    } else {
      body.append(desc, skillsTitle, boxes, matchBlock, srcLine, actions);
    }
    detail.append(head, body);
    openDrawer();
  }

  /** Filtres actifs qui peuvent être retirés depuis l'état vide, avec le nombre d'offres attendu si connu. */
  function emptySuggestions(facets) {
    const out = [];
    FILTERS.forEach((id) => {
      if (NO_CHIP.indexOf(id) >= 0) return;
      const raw = String(val(id)).trim();
      if (!raw) return;
      let n = null;
      const key = FACET_KEYS[id];
      // Compteurs d'une dimension = résultats sans ce filtre (ils tiennent compte des autres filtres).
      if (facets && key && key !== "since" && key !== "minSalary" && facets[key]) {
        n = Object.keys(facets[key]).reduce((sum, k) => sum + (Number(facets[key][k]) || 0), 0);
      }
      out.push({ id: id, label: FILTER_CHIP_LABELS[id] || id, value: filterDisplayValue(id) || raw, n: n });
    });
    out.sort((x, y) => (y.n == null ? -1 : y.n) - (x.n == null ? -1 : x.n));
    return out;
  }

  function paintEmptyResults(box, facets) {
    const q = searchVal().trim();
    const h = document.createElement("h2");
    h.textContent = "Aucun résultat";
    const p = document.createElement("p");
    const sugg = emptySuggestions(facets);
    p.textContent = sugg.length
      ? "Aucune offre pour « " + q + " » avec ces filtres. Retirez un filtre pour élargir la recherche :"
      : "Aucune offre pour « " + q + " ». Essayez un mot-clé plus général ou un synonyme (ex. « développeur » plutôt que « développeur React senior »).";
    box.append(h, p);
    const actions = document.createElement("div");
    actions.className = "cs-empty-actions";
    sugg.forEach((s) => {
      const btn = document.createElement("button");
      btn.type = "button";
      btn.className = "cs-active-chip";
      const lab = document.createElement("span");
      lab.textContent = "Retirer « " + s.label + " : " + s.value + " »";
      btn.appendChild(lab);
      if (s.n != null) {
        const n = document.createElement("small");
        n.textContent = s.n === 0 ? "aucune offre" : s.n + " offre" + (s.n > 1 ? "s" : "");
        btn.appendChild(n);
      }
      btn.addEventListener("click", () => {
        const el = $(s.id);
        if (!el) return;
        el.value = "";
        el.dataset.applied = "";
        scheduleReload();
      });
      actions.appendChild(btn);
    });
    if (sugg.length > 1) {
      const all = document.createElement("button");
      all.type = "button";
      all.className = "btn btn-ghost btn-sm";
      all.textContent = "Retirer tous les filtres";
      all.addEventListener("click", () => { clearFilters(); scheduleReload(); });
      actions.appendChild(all);
    }
    if (currentScope() !== "all") {
      const scope = document.createElement("button");
      scope.type = "button";
      scope.className = "btn btn-ghost btn-sm";
      scope.textContent = "Chercher dans toutes les offres";
      scope.addEventListener("click", () => setScope("all", true));
      actions.appendChild(scope);
    }
    if (actions.childNodes.length) box.appendChild(actions);
  }

  /** Indicateur de chargement : messages qui se relaient tant que le nœud est affiché (hauteur fixe, pas de saut). */
  const LOADER_STEPS = {
    main: ["Recherche dans les offres HireLink…", "Prise en compte de vos critères…", "Tri par pertinence…"],
    web: ["Interrogation des sites d’emploi…", "Vérification des offres trouvées…", "Tri par pertinence…"],
  };
  function loaderNode(kind) {
    const steps = LOADER_STEPS[kind] || LOADER_STEPS.main;
    const n = document.createElement("div");
    n.className = "cs-loader cs-loader--" + kind;
    n.setAttribute("role", "status");
    n.setAttribute("aria-label", kind === "web" ? "Recherche d’offres supplémentaires en cours" : "Recherche en cours");
    const dots = document.createElement("span");
    dots.className = "cs-loader-dots";
    dots.setAttribute("aria-hidden", "true");
    for (let i = 0; i < 3; i++) dots.appendChild(document.createElement("i"));
    const msg = document.createElement("span");
    msg.className = "cs-loader-msg";
    msg.setAttribute("aria-hidden", "true");
    msg.textContent = steps[0];
    n.append(dots, msg);
    let i = 0;
    const timer = setInterval(() => {
      if (!n.isConnected) { clearInterval(timer); return; }
      i = (i + 1) % steps.length;
      msg.classList.remove("is-in");
      void msg.offsetWidth;
      msg.textContent = steps[i];
      msg.classList.add("is-in");
    }, 1800);
    return n;
  }

  function webPendingNode() {
    const n = loaderNode("web");
    n.classList.add("cs-web-pending");
    n.id = "cs-web-pending";
    return n;
  }

  function renderGrid(resetDetail) {
    const list = filtered();
    offerList.className = "cs-grid";
    offerList.replaceChildren();
    if (!list.length) {
      if (webState === "loading") {
        offerList.appendChild(webPendingNode());
        return;
      }
      const box = document.createElement("div");
      box.className = "cs-empty";
      paintEmptyResults(box, mergedFacets());
      offerList.appendChild(box);
      if (resetDetail !== false) closeDrawer();
      return;
    }
    list.forEach((o) => offerList.appendChild(card(o, String(o.id) === String(selectedId))));
    if (webState === "loading") offerList.appendChild(webPendingNode());
    S.mark(offerList, searchVal().trim());
  }

  /** Ajoute les cartes web à la suite des cartes HireLink déjà affichées (pas de re-rendu, pas de saut). */
  function appendWebCards() {
    const pending = $("cs-web-pending");
    if (!hlItems.length || !offerList.classList.contains("cs-grid") || !pending) { renderGrid(false); return; }
    pending.remove();
    const frag = document.createDocumentFragment();
    webItems.forEach((o) => frag.appendChild(card(o, String(o.id) === String(selectedId))));
    offerList.appendChild(frag);
    S.mark(offerList, searchVal().trim());
  }

  /** Compteurs HireLink + web additionnés (« Stage (12) »). */
  function mergedFacets() {
    if (!hlFacets) return webFacets;
    if (!webFacets) return hlFacets;
    const out = {};
    Object.keys(hlFacets).forEach((k) => { out[k] = hlFacets[k]; });
    Object.keys(FACET_KEYS).forEach((id) => {
      const key = FACET_KEYS[id];
      const a = hlFacets[key] || {};
      const b = webFacets[key] || {};
      const sum = {};
      const byFold = {};
      [a, b].forEach((m) => Object.keys(m).forEach((label) => {
        const f = S.fold(label);
        const target = byFold[f] || label;
        byFold[f] = target;
        sum[target] = (sum[target] || 0) + (Number(m[label]) || 0);
      }));
      out[key] = sum;
    });
    out.salaryAvailable = !!(hlFacets.salaryAvailable || webFacets.salaryAvailable);
    out.total = (Number(hlFacets.total) || 0) + (Number(webFacets.total) || 0);
    return out;
  }

  function paintCounts() {
    totalElements = hlTotal + webTotal;
    const facets = mergedFacets();
    paintFacets(facets || null, facets ? facets.salaryAvailable : hlSalaryAvailable);
    let summary = totalElements + " résultat" + (totalElements > 1 ? "s" : "");
    if (totalPages > 1) summary += " · page " + (page + 1) + " / " + totalPages;
    if (webState === "loading") summary += " · recherche web en cours…";
    $("filter-summary").textContent = summary;
    const parts = [hlTotal + " offre" + (hlTotal > 1 ? "s" : "") + " HireLink"];
    if (webState === "done") parts.push(webTotal + " offre" + (webTotal > 1 ? "s" : "") + " web");
    $("match-hint").textContent = matchOnly
      ? "Reclassement par compatibilité + compétences présentes/manquantes (pas un filtre magique)."
      : parts.join(" · ") + ".";
    paintWebBanner(webMeta);
  }

  function paintPager() {
    const box = $("catalog-pager");
    if (!box) return;
    box.replaceChildren();
    if (!hasSearched || totalPages <= 1) { box.hidden = true; return; }
    box.hidden = false;
    const label = document.createElement("span");
    label.className = "muted";
    label.textContent = "Page " + (page + 1) + " / " + totalPages + " · " + hlTotal + " offre(s) HireLink";
    box.appendChild(label);
    if (page + 1 < totalPages) {
      const more = document.createElement("button");
      more.type = "button";
      more.className = "btn btn-soft btn-sm";
      more.textContent = "Charger plus d’offres";
      more.addEventListener("click", () => { page += 1; load(true, true); });
      box.appendChild(more);
    }
  }

  async function loadWeb(seq) {
    try {
      const w = await HireLinkAPI.api("/catalog/web-offers?" + buildWebQuery());
      if (seq !== loadSeq) return;
      webItems = (w && Array.isArray(w.items)) ? w.items : [];
      webTotal = Number((w && w.totalElements) || webItems.length);
      webFacets = (w && w.facets) || null;
      webMeta = w || null;
      webState = "done";
    } catch (_) {
      if (seq !== loadSeq) return;
      webItems = [];
      webTotal = 0;
      webFacets = null;
      webState = "failed";
    }
    cached = hlItems.concat(webItems);
    paintCounts();
    if (webItems.length) appendWebCards();
    else renderGrid(false);
    const pending = $("cs-web-pending");
    if (pending) pending.remove();
    if (selectedId) {
      const target = cached.find((o) => String(o.id) === String(selectedId));
      if (target && !detail.classList.contains("is-open")) showDetail(target);
    }
  }

  async function load(quiet, append) {
    if (!hasSearched) { paintEmptyFirst(); return; }
    if (append && loading) return;
    // Toute nouvelle recherche remplace la précédente : une réponse arrivée en retard est ignorée.
    const seq = ++loadSeq;
    loading = true;
    if (!quiet && !append) {
      offerList.className = "";
      offerList.replaceChildren();
      offerList.appendChild(loaderNode("main"));
    }
    try {
      if (!append) page = 0;
      const qs = buildCatalogQuery(page);
      const [data, mine] = await Promise.all([
        HireLinkAPI.api("/catalog/offers?" + qs + "&web=false"),
        HireLinkAPI.api("/applications/mine").catch(() => []),
        HireLinkPlaces.ready(),
      ]);
      if (seq !== loadSeq) return;
      if (!data || !Array.isArray(data.items)) throw new Error("Catalogue temporairement indisponible");
      hlTotal = Number(data.totalElements || 0);
      totalPages = Number(data.totalPages || 0);
      hlItems = append ? hlItems.concat(data.items) : data.items;
      if (!append) {
        webItems = [];
        webTotal = 0;
        webFacets = null;
        hlFacets = data.facets || null;
        hlSalaryAvailable = data.salaryAvailable;
        webMeta = data;
        webState = data.webDeferred ? "loading" : "off";
      }
      cached = hlItems.concat(webItems);
      appliedByOffer = new Map((mine || []).map((a) => [a.jobOfferId, { appId: a.id, status: a.status }]));
      matchToggle.checked = matchOnly;
      paintCounts();
      paintActiveChips();
      renderGrid();
      paintPager();
      const applyId = new URLSearchParams(location.search).get("apply");
      if (applyId && !quiet && !append) {
        history.replaceState(null, "", location.pathname);
        const target = cached.find((o) => String(o.id) === String(applyId));
        if (target) openApply(target);
      } else if (selectedId) {
        const target = cached.find((o) => String(o.id) === String(selectedId));
        if (target) showDetail(target);
      }
      if (!append && data.webDeferred) loadWeb(seq);
    } catch (err) {
      if (seq !== loadSeq) return;
      offerList.className = "";
      offerList.replaceChildren();
      const p = document.createElement("p");
      p.className = "muted";
      p.setAttribute("role", "alert");
      p.textContent = "Catalogue temporairement indisponible";
      offerList.appendChild(p);
    } finally {
      if (seq === loadSeq) loading = false;
    }
  }

  scheduleSearch = function () {
    const q = searchVal().trim();
    setSearchVal(q);
    hasSearched = q.length > 0;
    if (!hasSearched) { cached = []; paintEmptyFirst(); return; }
    page = 0;
    load(false, false);
  };

  function setMatchOnly(on) {
    matchOnly = on;
    matchToggle.checked = on;
    try { localStorage.setItem(MATCH_KEY, on ? "1" : "0"); } catch (_) {}
    scheduleReload();
  }

  matchToggle.addEventListener("change", () => setMatchOnly(matchToggle.checked));
  $("btn-clear-filters").addEventListener("click", () => { clearAll(); paintEmptyFirst(); });
  const clearChips = $("btn-clear-chips");
  if (clearChips) {
    clearChips.addEventListener("click", () => {
      clearFilters();
      if (matchOnly) { setMatchOnly(false); return; }
      scheduleReload();
    });
  }
  const moreBtn = $("btn-more-filters");
  if (moreBtn) {
    moreBtn.addEventListener("click", () => {
      const panel = $("cs-more-filters");
      const open = moreBtn.getAttribute("aria-expanded") !== "true";
      moreBtn.setAttribute("aria-expanded", open ? "true" : "false");
      moreBtn.classList.toggle("is-open", open);
      if (panel) panel.hidden = !open;
    });
  }
  const sortSel = $("f-sort");
  if (sortSel) sortSel.addEventListener("change", () => scheduleReload());
  FILTERS.forEach((id) => {
    const el = $(id);
    if (!el) return;
    const reload = () => {
      // Champs texte (Où, Entreprise) : appliqués à la validation (choix, Entrée, sortie du champ).
      const now = String(el.value || "").trim();
      if (el.tagName === "INPUT" && now === (el.dataset.applied || "")) return;
      el.dataset.applied = now;
      // Tous les filtres sont appliqués côté serveur, 300 ms après le dernier changement.
      scheduleReload();
    };
    el.addEventListener("change", reload);
    // Croix native du champ de recherche : retire le filtre.
    if (el.tagName === "INPUT") el.addEventListener("search", () => { if (!el.value) reload(); });
  });
  // Entreprise : propositions limitées aux noms d'entreprises (serveur) : entreprises HireLink actives
  // + entreprises des offres web affichées.
  const companyInput = $("f-company");
  if (companyInput) {
    companyInput.hlSuggestParams = function () {
      const names = [];
      const seen = new Set();
      cached.forEach((o) => {
        if (!o || !o.external || !o.companyName) return;
        const k = S.fold(o.companyName);
        if (!k || seen.has(k) || o.companyName.length > 80) return;
        seen.add(k);
        names.push(o.companyName.replace(/\|/g, " "));
      });
      return { context: names.slice(0, 40).join("|") };
    };
  }

  document.querySelectorAll(".cs-chip[data-q]").forEach((btn) => {
    btn.addEventListener("click", () => {
      setSearchVal(btn.getAttribute("data-q") || "");
      scheduleSearch();
    });
  });

  const scopeTrack = $("cs-scope");
  if (scopeTrack) {
    scopeTrack.querySelectorAll(".cs-scope-btn").forEach((btn) => {
      btn.addEventListener("click", () => setScope(btn.getAttribute("data-scope") || "all", true));
    });
    setScope(scopeTrack.getAttribute("data-scope") || "all", false);
    window.addEventListener("resize", () => syncScopeThumb());
    // Polices / layout : repositionne une fois après paint.
    requestAnimationFrame(() => syncScopeThumb());
    setTimeout(syncScopeThumb, 120);
  }

  let timer = 0;
  const heroSearch = $("cs-search");
  if (heroSearch) {
    const bootQ = new URLSearchParams(location.search).get("q");
    if (bootQ) setSearchVal(bootQ);
    heroSearch.addEventListener("input", () => {
      setSearchVal(heroSearch.value);
      clearTimeout(timer);
      timer = setTimeout(scheduleSearch, 320);
    });
    heroSearch.addEventListener("keydown", (e) => {
      if (e.key === "Enter") { e.preventDefault(); clearTimeout(timer); scheduleSearch(); }
    });
  }
  if (scrim) scrim.addEventListener("click", closeDrawer);

  if (hasSearched) load(false, false);
  else paintEmptyFirst();
  document.addEventListener("visibilitychange", () => {
    if (!document.hidden && hasSearched) load(true, false);
  });
})();
