/** Offres côté candidat : recherche intelligente, filtres, option « correspond à mon profil », cartes et détail. */
(function () {
  const session = HireLinkApp.mount({ search: { placeholder: "Entreprise, poste, ville, compétence, contrat…", onQuery: () => { if (typeof reloadFromFilters === "function") reloadFromFilters(); else render(); } } });
  if (!session) return;

  const O = HireLinkOffer;
  const S = HireLinkSearch;
  const offerList = document.getElementById("offer-list");
  const matchToggle = document.getElementById("match-only");
  const FILTERS = ["f-country", "f-city", "f-company", "f-skill", "f-contract", "f-origin", "f-since", "f-mode", "f-pace", "f-sent", "f-match"];
  const PAGE_SIZE = 20;
  let page = 0;
  let totalElements = 0;
  let totalPages = 0;
  let loading = false;
  let loadError = "";
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
  const WEB_SRC_LABEL = { REMOTIVE: "Remotive", ARBEITNOW: "Arbeitnow", REMOTE_OK: "Remote OK", JOOBLE: "Jooble" };
  const WEB_SRC_HOME = { REMOTIVE: "https://remotive.com", ARBEITNOW: "https://www.arbeitnow.com", REMOTE_OK: "https://remoteok.com", JOOBLE: "https://ma.jooble.org" };
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

  function tokenize(csv) {
    return String(csv || "").split(/[,;]+/).map((s) => s.trim()).filter(Boolean);
  }

  function openApply(o) {
    if (o && o.external) return;
    if (!isOpenToApply(o.id)) {
      HireLink.toast("Déjà envoyée", "Ouvrez-la dans Mes candidatures. Le statut Retirée est le seul qui permet de renvoyer.", "info");
      return;
    }
    if (!window.HireLinkApply) { HireLink.toast("Erreur", "Assistant de candidature indisponible", "danger"); return; }
    if (window.HireLinkApplyGate && typeof HireLinkApplyGate.ensureCompleteThenApply === "function") {
      HireLinkApplyGate.ensureCompleteThenApply(o, load);
      return;
    }
    HireLinkApply.start(o, load);
  }

  /** Le motif saisi ici est celui que la modération lit dans Administration › Signalements. */
  function openReport(o) {
    HireLink.openModal({
      title: "Signaler cette offre",
      description: "Décrivez le problème : la modération reçoit votre texte exact avec l'offre.",
      bodyHtml: '<label class="field-label" for="hl-report-reason">Motif (500 caractères maximum)</label>'
        + '<textarea id="hl-report-reason" class="input" rows="4" maxlength="500" placeholder="Ex. : l’entreprise demande un paiement pour postuler."></textarea>',
      confirmLabel: "Envoyer le signalement",
      onConfirm: async () => {
        const field = document.getElementById("hl-report-reason");
        if (!field || field.value.trim().length < 5) { if (field) field.focus(); return false; }
        await HireLinkAPI.api("/offers/" + o.id + "/report", { method: "POST", body: { reason: field.value.trim() } });
        HireLink.toast("Signalement envoyé", "Merci. Trois signalements suspendent automatiquement l’offre.", "success");
        load(true);
      },
    });
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

  /** Filtres résiduels client (candidature / réactivité). Compatibilité min. = serveur (minMatch). */
  function filtered(query) {
    const company = val("f-company"), skill = val("f-skill");
    const sentFilter = val("f-sent");
    const pace = val("f-pace");
    const kept = cached.filter((o) => {
      const st = appState(o.id);
      if (company && companyOf(o) !== company) return false;
      if (skill && !tokenize(o.requiredSkills).some((s) => s.toLowerCase() === skill.toLowerCase())) return false;
      if (pace === "answers" && !o.responsiveness) return false;
      if (pace === "fast" && o.responsiveness !== "REACTIF") return false;
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
    return kept;
  }

  function buildCatalogQuery(pageNum) {
    const params = new URLSearchParams();
    params.set("page", String(pageNum));
    params.set("size", String(PAGE_SIZE));
    params.set("country", val("f-country") || "ALL");
    params.set("forYou", matchOnly ? "true" : "false");
    const qParts = [];
    const search = val("rc-search").trim();
    if (search) qParts.push(search);
    if (val("f-company")) qParts.push(val("f-company"));
    if (val("f-skill")) qParts.push(val("f-skill"));
    if (qParts.length) params.set("q", qParts.join(" "));
    if (val("f-city")) params.set("city", val("f-city"));
    if (val("f-contract")) params.set("contract", val("f-contract"));
    if (val("f-mode")) params.set("workMode", val("f-mode"));
    if (val("f-origin")) params.set("origin", val("f-origin") === "hirelink" ? "internal" : val("f-origin"));
    if (val("f-since")) params.set("sinceDays", val("f-since"));
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
    const search = $("rc-search");
    if (search) search.value = "";
  }

  function setMatchOnly(on) {
    matchOnly = on;
    matchToggle.checked = on;
    try { localStorage.setItem(MATCH_KEY, on ? "1" : "0"); } catch (_) { /* stockage refuse */ }
    reloadFromFilters();
  }

  function paintEmpty(query) {
    if (val("f-origin") === "external" && !totalElements) {
      S.emptyState(offerList, "", {
        emptyTitle: "Aucune source externe active actuellement",
        emptyHint: "Les connecteurs externes sont désactivés sur cette démonstration. Affichez les offres HireLink ou toutes les provenances.",
      });
      return;
    }
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

  function paintWebBanner(data) {
    const el = $("web-search-banner");
    if (!el) return;
    if (data && data.webSearchAvailable === false && data.webSearchMessage) {
      el.hidden = false;
      el.textContent = data.webSearchMessage;
    } else if (data && (data.webSearchCoverage || data.webSearchMessage)) {
      el.hidden = false;
      const bits = [];
      if (data.webSearchCoverage) bits.push(String(data.webSearchCoverage));
      else if (data.webSearchMessage) bits.push(String(data.webSearchMessage));
      if (Array.isArray(data.webSearchSources) && data.webSearchSources.length) {
        bits.push("Sources : " + data.webSearchSources.map((s) => s.code + "=" + s.status + "(" + (s.hits || 0) + ")").join(", "));
      }
      if (data.rankedByRelevance) bits.push("Classement par pertinence (fautes et synonymes tolérés).");
      el.textContent = bits.join(" ");
    } else if (data && data.rankedByRelevance) {
      el.hidden = false;
      el.textContent = "Résultats classés par pertinence. Les offres externes s’ouvrent sur le site d’origine — HireLink ne gère pas ces candidatures.";
    } else {
      el.hidden = true;
      el.textContent = "";
    }
  }

  function updateHint() {
    if (matchOnly) {
      $("match-hint").textContent = totalElements
        ? totalElements + " offre" + (totalElements > 1 ? "s" : "") + " pour vous (score >= 50 %, pagination serveur)."
        : "Aucune offre n'atteint 50 % avec votre profil actuel.";
      return;
    }
    const scoring = cached.some((o) => o.matchScore != null);
    if (!scoring) {
      $("match-hint").textContent = totalElements
        ? totalElements + " offre" + (totalElements > 1 ? "s" : "") + " au catalogue (recherche unifiée)."
        : "Correspondance non disponible : ajoutez au moins une competence dans votre profil.";
      return;
    }
    $("match-hint").textContent = totalElements + " offre" + (totalElements > 1 ? "s" : "") + " au catalogue (page serveur).";
  }

  function render() {
    const query = val("rc-search").trim();
    const list = filtered(query);
    const active = FILTERS.filter((id) => val(id) && val(id) !== "0").length;
    const countryLabel = !val("f-country") || val("f-country") === "ALL" ? "tous pays" : val("f-country");
    $("filter-summary").textContent = totalElements + " offre" + (totalElements > 1 ? "s" : "") + " · " + countryLabel + " · page " + (page + 1) + (active ? " · " + active + " filtre" + (active > 1 ? "s" : "") : "");
    offerList.replaceChildren();
    const detail = $("offer-detail");
    const requestedId = selectedId;
    if (!list.length) {
      paintEmpty(query);
      if (requestedId != null && requestedId !== "") {
        // Offre demandée absente et liste vide (ou filtrée) : message dédié, pas le placeholder HTML.
        showMissingOffer(requestedId);
      } else {
        detail.replaceChildren();
        const p = document.createElement("p");
        p.className = "muted";
        p.textContent = "Aucune offre à afficher.";
        detail.appendChild(p);
      }
      return;
    }
    const matched = requestedId != null && requestedId !== ""
      ? list.find((o) => String(o.id) === String(requestedId))
      : null;
    if (requestedId != null && requestedId !== "" && !matched) {
      // Lien notification / URL vers une offre absente (supprimée, dépubliée, etc.) : ne pas ouvrir une autre fiche en silence.
      list.forEach((o) => offerList.appendChild(card(o, false)));
      S.mark(offerList, query);
      showMissingOffer(requestedId);
      return;
    }
    const current = matched || list[0];
    selectedId = current.id;
    list.forEach((o) => offerList.appendChild(card(o, o.id === current.id)));
    S.mark(offerList, query);
    showDetail(current);
  }

  function showMissingOffer(id) {
    const detail = $("offer-detail");
    detail.replaceChildren();
    const head = document.createElement("div");
    head.className = "detail-head";
    const h2 = document.createElement("h2");
    h2.textContent = "Offre indisponible";
    const p = document.createElement("p");
    p.className = "muted";
    p.textContent =
      "Cette offre n’est plus publiée ou a été retirée. La notification reste dans votre historique ; choisissez une autre offre dans la liste.";
    head.append(h2, p);
    const actions = document.createElement("div");
    actions.className = "row";
    const clear = document.createElement("button");
    clear.type = "button";
    clear.className = "btn btn-soft";
    clear.textContent = "Voir les offres disponibles";
    clear.addEventListener("click", () => {
      selectedId = null;
      try {
        const u = new URL(location.href);
        u.searchParams.delete("offer");
        history.replaceState(null, "", u.pathname + (u.search || ""));
      } catch (_) {}
      render();
    });
    actions.appendChild(clear);
    detail.append(head, actions);
    if (!showMissingOffer._told) {
      showMissingOffer._told = true;
      HireLink.toast("Offre indisponible", "L’offre demandée n’est plus accessible.", "info");
    }
  }

  function card(o, selected) {
    const st = appState(o.id);
    const badge = o.matchScore != null ? document.createElement("span") : null;
    if (badge) {
      badge.textContent = Math.round(o.matchScore) + " %";
      badge.title = o.matchLabel
        || (o.external
          ? "Indicatif : compétences citées par la source uniquement."
          : "Compatibilité multicritère HireLink (compétences, langues, expérience, contrat, lieu).");
    }
    return O.card(o, {
      selected,
      badge,
      href: (x) => {
        if (!x.external) return "candidate.html?offer=" + encodeURIComponent(x.id);
        const p = O.externalLinkPolicy(x);
        return p.trusted ? p.href : "#";
      },
      companyHref: (x) => x.external ? "" : "candidate-companies.html?name=" + encodeURIComponent(companyOf(x)),
      onOpen: (x) => { selectedId = x.id; offerList.querySelectorAll(".offer-card").forEach((n) => n.classList.toggle("is-selected", n.dataset.offerId === String(x.id))); showDetail(x); },
      statusChip: o.external
        ? (o.ephemeral ? ((webSourceLabel(o) || "Web") + " · annonce d’origine") : "Offre externe")
        : (st ? (st.status === "WITHDRAWN" ? "Candidature retirée" : "Candidature envoyée") : "Offre HireLink"),
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
    const company = document.createElement(o.external ? "p" : "a");
    company.className = "detail-company";
    if (!o.external) {
      company.href = "candidate-companies.html?name=" + encodeURIComponent(companyOf(o));
      company.title = "Voir la page de l’entreprise";
    }
    company.textContent = companyOf(o);
    const place = o.external ? (o.location || "Non précisée") : O.placeWithCountry(o);
    const where = document.createElement("p");
    where.className = "muted";
    where.textContent = [(HireLinkPlaces.flagOf(place) + " " + place).trim(), o.contractType, O.paceOf(o).label].filter(Boolean).join(" · ");
    titles.append(h2, company, where);
    head.appendChild(titles);
    if (o.external && !(o.matchAvailable && o.matchScore != null)) {
      const missing = document.createElement("p");
      missing.className = "match-why";
      missing.textContent = o.matchLabel || "Correspondance non disponible";
      head.appendChild(missing);
    } else if (o.matchScore != null) {
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
      label.textContent = o.matchLabel
        || (o.external ? "Indicatif" : "Compatibilité multicritère HireLink");
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
    if (o.external) {
      const policy = O.externalLinkPolicy(o);
      if (policy.trusted && policy.href) {
        const a = document.createElement("a");
        a.className = "btn btn-primary";
        a.href = policy.href;
        a.target = "_blank";
        a.rel = "noopener";
        a.textContent = policy.label;
        actions.appendChild(a);
      } else if (policy.href && o.urlQuality === "UNKNOWN") {
        const a = document.createElement("a");
        a.className = "btn btn-ghost";
        a.href = policy.href;
        a.target = "_blank";
        a.rel = "noopener noreferrer";
        a.textContent = policy.label;
        actions.appendChild(a);
        const note = document.createElement("p");
        note.className = "muted";
        note.textContent = "Ce lien n’est pas garanti comme l’annonce exacte. HireLink n’invente pas de candidature.";
        actions.appendChild(note);
      } else {
        const unavailable = document.createElement("p");
        unavailable.className = "muted";
        unavailable.textContent = "Lien d’annonce insuffisamment fiable — candidature directe désactivée.";
        actions.appendChild(unavailable);
      }
      if (/^https:\/\//i.test(webSourceHome(o))) {
        const source = document.createElement("a");
        source.className = "btn btn-ghost";
        source.href = webSourceHome(o);
        source.target = "_blank";
        source.rel = "noopener";
        source.textContent = "Source : " + webSourceLabel(o);
        actions.appendChild(source);
      }
    } else if (sent) {
      const a = document.createElement("a");
      a.className = "btn btn-primary";
      a.href = "candidate-applications.html?id=" + st.appId;
      a.textContent = "Voir ma candidature";
      actions.appendChild(a);
    } else {
      const b = document.createElement("button");
      b.type = "button";
      b.className = "btn btn-primary";
      b.textContent = st && st.status === "WITHDRAWN" ? "Postuler à nouveau sur HireLink" : "Postuler sur HireLink";
      b.addEventListener("click", () => openApply(o));
      actions.appendChild(b);
    }
    if (!o.external) {
      const flag = document.createElement("button");
      flag.type = "button";
      flag.className = "btn btn-ghost";
      flag.textContent = "Signaler cette offre";
      flag.title = "Alerter la modération sur cette offre";
      flag.addEventListener("click", () => openReport(o));
      actions.appendChild(flag);
    }
    const extra = [];
    if (o.niceToHaveSkills) {
      const niceTitle = document.createElement("h3");
      niceTitle.className = "detail-sub";
      niceTitle.textContent = "Compétences appréciées";
      const niceBoxes = document.createElement("div");
      HireLinkSkills.renderBoxes(niceBoxes, o.niceToHaveSkills, { empty: "" });
      extra.push(niceTitle, niceBoxes);
    }
    const why = window.HireLinkMatch && o.matchBreakdown ? HireLinkMatch.panel(o.matchBreakdown, { audience: "candidate" }) : null;
    if (why) {
      const whyTitle = document.createElement("h3");
      whyTitle.className = "detail-sub";
      whyTitle.textContent = "Pourquoi ce score ?";
      extra.push(whyTitle, why);
    }
    const notice = o.external ? document.createElement("div") : null;
    if (notice) {
      notice.className = "match-why";
      const p = document.createElement("p");
      p.textContent = (o.applyHint || "Cette offre est publiée sur un site externe. HireLink ne crée pas de candidature pour cette annonce.");
      notice.appendChild(p);
      const src = document.createElement("p");
      src.className = "muted";
      const srcName = webSourceLabel(o);
      const srcHome = webSourceHome(o);
      src.appendChild(document.createTextNode("Source : " + srcName + " — "));
      const a = document.createElement("a");
      const href = (O.externalLinkPolicy(o).trusted && O.externalLinkPolicy(o).href) || o.originUrl || o.applyUrl || srcHome || "";
      a.href = href || "#";
      a.target = "_blank";
      // Attribution Remotive / Remote OK / Arbeitnow : lien suivi (pas de nofollow).
      a.rel = "noopener";
      a.textContent = href ? "ouvrir l’annonce d’origine" : "lien indisponible";
      if (!href) a.removeAttribute("href");
      src.appendChild(a);
      notice.appendChild(src);
      if (srcHome) {
        const home = document.createElement("p");
        home.className = "muted";
        const ha = document.createElement("a");
        ha.href = srcHome;
        ha.target = "_blank";
        ha.rel = "noopener";
        ha.textContent = srcHome.replace(/^https?:\/\//, "");
        home.appendChild(document.createTextNode("Site : "));
        home.appendChild(ha);
        notice.appendChild(home);
      }
    }
    detail.append(head, O.facts(o), body, skillsTitle, boxes, ...extra, ...(notice ? [notice] : []), actions);
    S.mark(detail, val("rc-search").trim());
  }

  let offerStamp = "";

  async function load(quiet, append) {
    if (loading) return;
    loading = true;
    loadError = "";
    if (!quiet && !append) {
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
        HireLinkAPI.api("/catalog/offers?" + qs).catch(() => null),
        HireLinkAPI.api("/applications/mine").catch(() => []),
        HireLinkPlaces.ready(),
      ]);
      // B0 fail-closed : pas de repli /offers/public + /external-offers.
      // /external-offers n'applique pas exclude-sources ; /offers/public n'applique pas les filtres catalogue.
      if (!data || !Array.isArray(data.items)) {
        throw new Error("Catalogue temporairement indisponible");
      }
      const items = data.items;
      totalElements = Number(data.totalElements || 0);
      totalPages = Number(data.totalPages || 0);
      const stamp = items.map((o) => o.id).join(",") + "~" + totalElements + "~" + (mine || []).map((a) => a.id + ":" + a.status).join("|") + "~" + matchOnly + "~" + qs;
      if (quiet && !append && stamp === offerStamp) { loading = false; return; }
      offerStamp = stamp;
      cached = append ? cached.concat(items) : items;
      appliedByOffer = new Map((mine || []).map((a) => [a.jobOfferId, { appId: a.id, status: a.status }]));
      matchToggle.checked = matchOnly;
      if (!append) rebuildFacets();
      paintWebBanner(data);
      updateHint();
      render();
      paintPager();
      const applyId = new URLSearchParams(location.search).get("apply");
      if (applyId && !quiet && !append) {
        history.replaceState(null, "", location.pathname);
        const target = cached.find((o) => String(o.id) === String(applyId));
        if (target) openApply(target);
      }
    } catch (err) {
      const msg = (err && err.message) || "Erreur reseau";
      loadError = msg.indexOf("Catalogue temporairement") >= 0 ? msg : "Catalogue temporairement indisponible";
      if (!quiet) {
        cached = [];
        totalElements = 0;
        totalPages = 0;
        offerList.replaceChildren();
        const p = document.createElement("p");
        p.className = "muted";
        p.setAttribute("role", "alert");
        p.textContent = "Catalogue temporairement indisponible";
        offerList.appendChild(p);
        const hint = $("match-hint");
        if (hint) hint.textContent = "Catalogue temporairement indisponible.";
        paintPager();
      }
    } finally {
      loading = false;
    }
  }

  function paintPager() {
    let box = document.getElementById("catalog-pager");
    if (!box) {
      box = document.createElement("div");
      box.id = "catalog-pager";
      box.className = "row";
      box.style.marginTop = "12px";
      if (offerList.parentNode) offerList.parentNode.appendChild(box);
    }
    box.replaceChildren();
    if (totalPages <= 1) { box.hidden = true; return; }
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

  function reloadFromFilters() {
    page = 0;
    load(false, false);
  }

  matchToggle.addEventListener("change", () => setMatchOnly(matchToggle.checked));
  $("btn-clear-filters").addEventListener("click", () => { clearAll(); reloadFromFilters(); });
  FILTERS.forEach((id) => {
    const el = $(id);
    if (!el) return;
    el.addEventListener("change", reloadFromFilters);
  });
  const searchInput = $("rc-search");
  if (searchInput) {
    searchInput.placeholder = "Métier, compétence, phrase… (fautes et synonymes tolérés)";
    searchInput.setAttribute("aria-label", "Recherche intelligente d’offres");
    let timer = 0;
    searchInput.addEventListener("input", () => { clearTimeout(timer); timer = setTimeout(reloadFromFilters, 280); });
  }
  load();
  document.addEventListener("visibilitychange", () => { if (!document.hidden) load(true, false); });
})();
