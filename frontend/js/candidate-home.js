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
    "f-country", "f-city", "f-contract", "f-source", "f-salary", "f-experience",
    "f-sector", "f-since", "f-mode", "f-match", "f-origin", "f-company", "f-skill", "f-sent", "f-pace",
  ];
  const PAGE_SIZE = 24;
  const MATCH_KEY = "hirelink.matchOnly." + (session.userId || session.id || "me");

  let page = 0;
  let totalElements = 0;
  let totalPages = 0;
  let loading = false;
  let cached = [];
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
    "f-country": "Pays", "f-city": "Ville", "f-contract": "Contrat", "f-mode": "Mode",
    "f-since": "Date", "f-experience": "Expérience", "f-company": "Entreprise",
    "f-source": "Source", "f-salary": "Salaire", "f-sector": "Secteur", "f-match": "Compatibilité",
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

  function openReport(o) {
    HireLink.openModal({
      title: "Signaler cette offre",
      description: "Décrivez le problème : la modération reçoit votre texte exact avec l'offre.",
      bodyHtml: '<label class="field-label" for="hl-report-reason">Motif (500 caractères maximum)</label>'
        + '<textarea id="hl-report-reason" class="input" rows="4" maxlength="500"></textarea>',
      confirmLabel: "Envoyer le signalement",
      onConfirm: async () => {
        const field = document.getElementById("hl-report-reason");
        if (!field || field.value.trim().length < 5) { if (field) field.focus(); return false; }
        await HireLinkAPI.api("/offers/" + o.id + "/report", { method: "POST", body: { reason: field.value.trim() } });
        HireLink.toast("Signalement envoyé", "Merci.", "success");
        load(true, false);
      },
    });
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

  async function fillCitiesFromDirectory() {
    await HireLinkPlaces.ready();
    const country = val("f-country");
    const rows = typeof HireLinkPlaces.listCities === "function"
      ? HireLinkPlaces.listCities(country === "ALL" ? "" : country)
      : [];
    if (rows.length) {
      fillSelect("f-city", rows, (c) => (HireLinkPlaces.flagOf(c) + " " + c).trim());
      return;
    }
    // Repli : villes du catalogue places brut
    try {
      const all = await fetch((HireLinkAPI.API_BASE || "").replace(/\/$/, "") + "/catalog/places").then((r) => r.ok ? r.json() : []);
      const codeMap = { MA: "Maroc", FR: "France", DE: "Allemagne", GB: "Royaume-Uni", ES: "Espagne", BE: "Belgique", CH: "Suisse", NL: "Pays-Bas", CA: "Canada", US: "États-Unis", AE: "Émirats", SN: "Sénégal", TN: "Tunisie", CI: "Côte d'Ivoire" };
      const want = country === "ALL" || !country ? null : (codeMap[country] || country);
      const cities = Array.from(new Set((all || [])
        .filter((p) => p && p.city && (!want || String(p.country || "").toLowerCase() === String(want).toLowerCase()))
        .map((p) => p.city))).sort((a, b) => a.localeCompare(b, "fr"));
      fillSelect("f-city", cities, (c) => (HireLinkPlaces.flagOf(c) + " " + c).trim());
    } catch (_) { /* ignore */ }
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
    if (reload && hasSearched) load(false, false);
  }

  function buildCatalogQuery(pageNum) {
    const params = new URLSearchParams();
    params.set("page", String(pageNum));
    params.set("size", String(PAGE_SIZE));
    params.set("country", val("f-country") || "ALL");
    params.set("forYou", matchOnly ? "true" : "false");
    const search = searchVal().trim();
    if (search) params.set("q", search);
    if (val("f-city")) params.set("city", val("f-city"));
    if (val("f-contract")) params.set("contract", val("f-contract"));
    if (val("f-mode")) params.set("workMode", val("f-mode"));
    if (val("f-source")) params.set("source", val("f-source"));
    if (val("f-since")) params.set("sinceDays", val("f-since"));
    const minSal = Number(val("f-salary") || 0);
    if (minSal > 0) params.set("minSalary", String(minSal));
    if (val("f-experience")) params.set("experience", val("f-experience"));
    if (val("f-sector")) params.set("sector", val("f-sector"));
    if (val("f-company").trim()) params.set("company", val("f-company").trim());
    const scope = currentScope();
    if (scope === "hirelink") params.set("origin", "internal");
    else if (scope === "external") params.set("origin", "external");
    const minMatch = Number(val("f-match") || 0);
    if (minMatch > 0) params.set("minMatch", String(minMatch));
    return params.toString();
  }

  function clearAll() {
    FILTERS.forEach((id) => {
      if (!$(id)) return;
      if (id === "f-match") $(id).value = "0";
      else if (id === "f-country") $(id).value = "ALL";
      else $(id).value = "";
    });
    setSearchVal("");
    setScope("all", false);
    hasSearched = false;
    paintActiveChips();
  }

  function filterDisplayValue(id) {
    const el = $(id);
    if (!el) return "";
    if (el.tagName === "SELECT") {
      const opt = el.options[el.selectedIndex];
      return opt ? String(opt.textContent || "").trim() : "";
    }
    return String(el.value || "").trim();
  }

  function paintActiveChips() {
    const box = $("cs-active-chips");
    if (!box) return;
    box.replaceChildren();
    const chips = [];
    FILTERS.forEach((id) => {
      if (id === "f-origin" || id === "f-skill" || id === "f-sent" || id === "f-pace") return;
      const raw = val(id);
      if (!raw || (id === "f-country" && raw === "ALL") || (id === "f-match" && raw === "0")) return;
      chips.push({ id: id, label: FILTER_CHIP_LABELS[id] || id, value: filterDisplayValue(id) || raw });
    });
    if (matchOnly) chips.unshift({ id: "match-only", label: "Profil", value: "Reclassement" });
    if (!chips.length) { box.hidden = true; return; }
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
          $(c.id).value = c.id === "f-country" ? "ALL" : (c.id === "f-match" ? "0" : "");
          if (c.id === "f-country") fillCitiesFromDirectory();
          paintActiveChips();
          if (hasSearched) load(false, false);
        }
      });
      box.appendChild(btn);
    });
  }

  function paintEmptyFirst() {
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
    if (data && data.webSearchCoverage) bits.push(String(data.webSearchCoverage));
    else if (data && data.webSearchAvailable === false) {
      bits.push("Recherche Web inactive — catalogue indexé uniquement.");
    }
    if (bits.length) { el.hidden = false; el.textContent = bits.join(" "); }
    else { el.hidden = true; el.textContent = ""; }
    if (fresh) {
      if (data && data.catalogCacheAt) {
        fresh.hidden = false;
        const t = new Date(data.catalogCacheAt);
        fresh.textContent = "Fraîcheur cache catalogue : "
          + (isNaN(t.getTime()) ? data.catalogCacheAt : t.toLocaleString("fr-FR"))
          + (data.catalogCacheNote ? " — " + data.catalogCacheNote : "");
      } else fresh.hidden = true;
    }
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
    if (a != null && b != null && a !== b) return fmt(a) + " – " + fmt(b) + " MAD";
    return fmt(a != null ? a : b) + " MAD";
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
    if (o.external) {
      const badge = document.createElement("span");
      badge.className = "cs-origin-badge";
      badge.textContent = "Source externe";
      meta.appendChild(badge);
    } else {
      const badge = document.createElement("span");
      badge.className = "cs-origin-badge";
      badge.textContent = "Publiée sur HireLink";
      meta.appendChild(badge);
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
      const flag = document.createElement("button");
      flag.type = "button";
      flag.className = "btn btn-ghost";
      flag.textContent = "Signaler";
      flag.addEventListener("click", () => openReport(o));
      actions.appendChild(flag);
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

  function renderGrid(resetDetail) {
    const list = filtered();
    offerList.className = "cs-grid";
    offerList.replaceChildren();
    if (!list.length) {
      const box = document.createElement("div");
      box.className = "cs-empty";
      box.innerHTML = "<h2>Aucun résultat</h2><p>Aucune offre pour cette recherche. Essayez un autre mot-clé ou élargissez les filtres.</p>";
      offerList.appendChild(box);
      if (resetDetail !== false) closeDrawer();
      return;
    }
    list.forEach((o) => offerList.appendChild(card(o, String(o.id) === String(selectedId))));
    S.mark(offerList, searchVal().trim());
  }

  function paintPager() {
    const box = $("catalog-pager");
    if (!box) return;
    box.replaceChildren();
    if (!hasSearched || totalPages <= 1) { box.hidden = true; return; }
    box.hidden = false;
    const label = document.createElement("span");
    label.className = "muted";
    label.textContent = "Page " + (page + 1) + " / " + totalPages + " · " + totalElements + " offre(s)";
    box.appendChild(label);
    if (page + 1 < totalPages) {
      const more = document.createElement("button");
      more.type = "button";
      more.className = "btn btn-soft btn-sm";
      more.textContent = "Afficher la suite";
      more.addEventListener("click", () => { page += 1; load(true, true); });
      box.appendChild(more);
    }
  }

  async function load(quiet, append) {
    if (!hasSearched) { paintEmptyFirst(); return; }
    if (loading) return;
    loading = true;
    if (!quiet && !append) {
      offerList.className = "";
      offerList.replaceChildren();
      const sk = document.createElement("div");
      sk.className = "skeleton";
      sk.style.height = "120px";
      offerList.appendChild(sk);
    }
    try {
      if (!append) page = 0;
      const qs = buildCatalogQuery(page);
      const [data, mine] = await Promise.all([
        HireLinkAPI.api("/catalog/offers?" + qs),
        HireLinkAPI.api("/applications/mine").catch(() => []),
        HireLinkPlaces.ready(),
      ]);
      if (!data || !Array.isArray(data.items)) throw new Error("Catalogue temporairement indisponible");
      totalElements = Number(data.totalElements || 0);
      totalPages = Number(data.totalPages || 0);
      cached = append ? cached.concat(data.items) : data.items;
      appliedByOffer = new Map((mine || []).map((a) => [a.jobOfferId, { appId: a.id, status: a.status }]));
      matchToggle.checked = matchOnly;
      paintWebBanner(data);
      $("filter-summary").textContent = totalElements + " offre" + (totalElements > 1 ? "s" : "")
        + " · page " + (page + 1);
      $("match-hint").textContent = matchOnly
        ? "Reclassement par compatibilité + compétences présentes/manquantes (pas un filtre magique)."
        : (totalElements + " résultat" + (totalElements > 1 ? "s" : "") + " catalogue (+ Web si activé).");
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
    } catch (err) {
      offerList.className = "";
      offerList.replaceChildren();
      const p = document.createElement("p");
      p.className = "muted";
      p.setAttribute("role", "alert");
      p.textContent = "Catalogue temporairement indisponible";
      offerList.appendChild(p);
    } finally {
      loading = false;
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
    paintActiveChips();
    if (hasSearched) load(false, false);
  }

  matchToggle.addEventListener("change", () => setMatchOnly(matchToggle.checked));
  $("btn-clear-filters").addEventListener("click", () => { clearAll(); paintEmptyFirst(); fillCitiesFromDirectory(); });
  FILTERS.forEach((id) => {
    const el = $(id);
    if (!el) return;
    const reload = () => {
      if (id === "f-country") fillCitiesFromDirectory();
      paintActiveChips();
      // Tous les filtres visibles (salaire, expérience, secteur, entreprise…) sont serveur.
      if (hasSearched) load(false, false);
    };
    el.addEventListener("change", reload);
    if (id === "f-company") {
      let companyTimer = 0;
      el.addEventListener("input", () => {
        clearTimeout(companyTimer);
        companyTimer = setTimeout(reload, 400);
      });
    }
  });

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

  fillCitiesFromDirectory();
  if (hasSearched) load(false, false);
  else paintEmptyFirst();
  document.addEventListener("visibilitychange", () => {
    if (!document.hidden && hasSearched) load(true, false);
  });
})();
