/**
 * Agenda recruteur : vrai calendrier (Mois / Semaine / Jour / Liste) des entretiens de l'organisation.
 * Données : GET /applications/pipeline (mêmes dossiers que le pipeline, Hiring Manager = dossiers assignés).
 * Actions (reporter, annuler, .ics) : carte d'entretien partagée (interview.js), mêmes API que la page Entretiens.
 */
(function () {
  const RC = HireLinkApp;
  const S = HireLinkSearch;
  const session = RC.mount({
    active: "agenda",
    title: "Agenda",
    search: { placeholder: "Candidat, offre, lieu…", onQuery: () => render() },
  });
  if (!session) return;
  const $ = (id) => document.getElementById(id);
  const esc = RC.esc;
  const DAY = 864e5;
  const HOUR_PX = 52;
  const mobile = () => window.matchMedia("(max-width: 720px)").matches;
  const reduce = () => window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  const TYPE_FR = { VISIO: "Visio", ONSITE: "Sur site", PHONE: "Téléphone", ASYNC: "Asynchrone" };
  const KIND_FR = { HR: "RH", TECHNICAL: "Technique", MANAGER: "Manager", CASE: "Étude de cas", TEAM: "Équipe", FINAL: "Final" };
  const VIEWS = { month: "Mois", week: "Semaine", day: "Jour", list: "Liste" };

  let apps = [];
  let loaded = false;
  let view = localStorage.getItem("hirelink.agenda.view") || (mobile() ? "list" : "week");
  if (!VIEWS[view]) view = "week";
  let cursor = startOfDay(new Date());
  // Lien partageable / bouton Retour : ?view=week&date=2026-11-05&type=VISIO&kind=TECHNICAL&offer=3&range=week
  const qp = new URLSearchParams(location.search);
  if (VIEWS[qp.get("view")]) view = qp.get("view");
  if (/^\d{4}-\d{2}-\d{2}$/.test(qp.get("date") || "")) { const p = qp.get("date").split("-"); cursor = new Date(+p[0], +p[1] - 1, +p[2]); }
  let lastDir = 0;
  let selectedId = null;
  const filters = { type: "all", kind: "all", offer: "all", cancelled: false };

  /* ------------------------------------------------------------ dates */
  function startOfDay(d) { const x = new Date(d); x.setHours(0, 0, 0, 0); return x; }
  function addDays(d, n) { const x = new Date(d); x.setDate(x.getDate() + n); return x; }
  function startOfWeek(d) { const x = startOfDay(d); const wd = (x.getDay() + 6) % 7; return addDays(x, -wd); }
  function sameDay(a, b) { return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate(); }
  const fmt = (d, o) => d.toLocaleDateString("fr-FR", o);
  const hhmm = (d) => d.toLocaleTimeString("fr-FR", { hour: "2-digit", minute: "2-digit" });
  const cap = (s) => s.charAt(0).toUpperCase() + s.slice(1);

  /* ------------------------------------------------------------ données */
  function eventsAll() {
    return apps
      .filter((a) => a.interviewAt && !Number.isNaN(new Date(a.interviewAt).getTime()))
      .filter((a) => filters.cancelled || a.interviewStatus !== "CANCELLED")
      .filter((a) => filters.type === "all" || a.interviewType === filters.type)
      .filter((a) => filters.kind === "all" || (a.interviewKind || "HR") === filters.kind)
      .filter((a) => filters.offer === "all" || String(a.jobOfferId) === String(filters.offer))
      .map((a) => {
        const start = new Date(a.interviewAt);
        const end = new Date(start.getTime() + (a.interviewDurationMinutes || 45) * 60000);
        return { a, start, end, cancelled: a.interviewStatus === "CANCELLED", past: end.getTime() < Date.now() };
      })
      .sort((x, y) => x.start - y.start);
  }
  function events() {
    const q = (($("rc-search") || {}).value || "").trim();
    const all = eventsAll();
    if (!q) return all;
    const ranked = S.rank(all.map((e) => e.a), q, [
      { get: (a) => a.candidateName, weight: 3 },
      { get: (a) => a.jobTitle, weight: 2 },
      { get: (a) => (a.interviewLocation || "") + " " + (a.interviewParticipants || ""), weight: 1 },
    ]);
    const ids = new Set(ranked.map((a) => a.id));
    return all.filter((e) => ids.has(e.a.id));
  }
  function inRange(list, from, to) { return list.filter((e) => e.start < to && e.end > from); }

  /* ------------------------------------------------------------ rendu commun */
  function evClass(e) {
    return "ag-ev t-" + (e.a.interviewType || "ASYNC").toLowerCase() + (e.cancelled ? " is-cancelled" : "") + (e.past ? " is-past" : "") +
      (e.a.interviewConfirmation === "CONFIRMED" ? " is-confirmed" : "") + (String(e.a.id) === String(selectedId) ? " is-selected" : "");
  }
  function evAttrs(e) {
    return ' data-ev="' + e.a.id + '" data-focus="interview:' + e.a.id + " application:" + e.a.id + '" tabindex="0" role="button" aria-label="' +
      esc((e.a.candidateName || "Candidat") + ", " + fmt(e.start, { weekday: "long", day: "numeric", month: "long" }) + " " + hhmm(e.start) + (e.cancelled ? ", annulé" : "")) + '"';
  }
  function titleText() {
    if (view === "month") return cap(fmt(cursor, { month: "long", year: "numeric" }));
    if (view === "day") return cap(fmt(cursor, { weekday: "long", day: "numeric", month: "long", year: "numeric" }));
    if (view === "list") return "À venir";
    const a = startOfWeek(cursor), b = addDays(a, 6);
    return a.getMonth() === b.getMonth()
      ? a.getDate() + " – " + b.getDate() + " " + fmt(b, { month: "long", year: "numeric" })
      : fmt(a, { day: "numeric", month: "short" }) + " – " + fmt(b, { day: "numeric", month: "short", year: "numeric" });
  }

  function render() {
    if (!loaded) return;
    syncUrl();
    $("ag-title").textContent = titleText();
    $("ag-views").querySelectorAll("button").forEach((b) => b.setAttribute("aria-pressed", String(b.dataset.view === view)));
    $("ag-nav").hidden = view === "list";
    const list = events();
    paintStats(list);
    const root = $("ag-cal");
    root.className = "ag-cal ag-v-" + view;
    root.replaceChildren();
    const stage = document.createElement("div");
    stage.className = "ag-stage" + (reduce() ? "" : lastDir > 0 ? " in-next" : lastDir < 0 ? " in-prev" : " in-fade");
    root.appendChild(stage);
    lastDir = 0;
    if (view === "month") renderMonth(stage, list);
    else if (view === "list") renderList(stage, list);
    else renderTimeGrid(stage, list, view === "day" ? [cursor] : Array.from({ length: 7 }, (_, i) => addDays(startOfWeek(cursor), i)));
    const q = (($("rc-search") || {}).value || "").trim();
    if (q) S.mark(stage, q);
  }

  function paintStats(list) {
    const now = Date.now();
    const t0 = startOfDay(new Date()).getTime();
    const live = list.filter((e) => !e.cancelled);
    const today = live.filter((e) => e.start.getTime() >= t0 && e.start.getTime() < t0 + DAY).length;
    const week = live.filter((e) => e.start.getTime() >= now && e.start.getTime() < now + 7 * DAY).length;
    const pending = live.filter((e) => !e.past && e.a.interviewConfirmation !== "CONFIRMED").length;
    const next = live.find((e) => e.end.getTime() >= now);
    $("ag-stats").innerHTML =
      stat(today, "aujourd’hui") + stat(week, "sur 7 jours") + stat(pending, "à confirmer") +
      '<div class="ag-stat is-next"><span>Prochain</span><strong>' +
      (next ? esc(next.a.candidateName || "Candidat") + " · " + esc(cap(fmt(next.start, { weekday: "short", day: "numeric" }))) + " " + hhmm(next.start) : "—") + "</strong></div>";
  }
  const stat = (n, l) => '<div class="ag-stat"><strong>' + n + "</strong><span>" + l + "</span></div>";

  /* ------------------------------------------------------------ mois */
  function renderMonth(stage, list) {
    const first = new Date(cursor.getFullYear(), cursor.getMonth(), 1);
    const start = startOfWeek(first);
    const today = new Date();
    let html = '<div class="ag-month"><div class="ag-month-head">';
    ["lun.", "mar.", "mer.", "jeu.", "ven.", "sam.", "dim."].forEach((d) => (html += "<span>" + d + "</span>"));
    html += '</div><div class="ag-month-grid">';
    for (let i = 0; i < 42; i++) {
      const d = addDays(start, i);
      const dayEv = inRange(list, d, addDays(d, 1));
      const out = d.getMonth() !== cursor.getMonth();
      html += '<div class="ag-mcell' + (out ? " is-out" : "") + (sameDay(d, today) ? " is-today" : "") + ((d.getDay() + 6) % 7 >= 5 ? " is-weekend" : "") +
        '" data-day="' + d.getTime() + '"><button type="button" class="ag-mnum" data-goday="' + d.getTime() + '" aria-label="' + esc(fmt(d, { weekday: "long", day: "numeric", month: "long" })) + '">' + d.getDate() + "</button>";
      const max = mobile() ? 0 : 3;
      dayEv.slice(0, max).forEach((e) => {
        html += '<div class="' + evClass(e) + ' is-chip"' + evAttrs(e) + "><i></i><time>" + hhmm(e.start) + "</time><b>" + esc(e.a.candidateName || "Candidat") + "</b></div>";
      });
      if (mobile() && dayEv.length) html += '<span class="ag-dots">' + dayEv.slice(0, 4).map((e) => '<i class="t-' + (e.a.interviewType || "ASYNC").toLowerCase() + '"></i>').join("") + "</span>";
      else if (dayEv.length > max) html += '<button type="button" class="ag-more" data-goday="' + d.getTime() + '">+ ' + (dayEv.length - max) + " autre" + (dayEv.length - max > 1 ? "s" : "") + "</button>";
      html += "</div>";
    }
    html += "</div></div>";
    stage.innerHTML = html;
    if (!inRange(list, start, addDays(start, 42)).length) stage.appendChild(emptyBanner("Aucun entretien ce mois-ci"));
  }

  /* ------------------------------------------------------------ semaine / jour */
  function layout(dayEv) {
    // Colonnes pour les chevauchements (algorithme glouton par grappes).
    const cols = [];
    let cluster = [], clusterEnd = 0;
    const out = [];
    function flush() {
      const n = Math.max(1, ...cluster.map((x) => x.col + 1));
      cluster.forEach((x) => { x.cols = n; out.push(x); });
      cluster = []; cols.length = 0;
    }
    dayEv.forEach((e) => {
      if (cluster.length && e.start.getTime() >= clusterEnd) flush();
      let c = cols.findIndex((end) => end <= e.start.getTime());
      if (c < 0) { c = cols.length; cols.push(0); }
      cols[c] = e.end.getTime();
      cluster.push({ e, col: c });
      clusterEnd = Math.max(clusterEnd, e.end.getTime());
    });
    if (cluster.length) flush();
    return out;
  }

  function renderTimeGrid(stage, list, days) {
    const from = days[0], to = addDays(days[days.length - 1], 1);
    const rangeEv = inRange(list, from, to);
    let h0 = 7, h1 = 20;
    rangeEv.forEach((e) => { h0 = Math.min(h0, e.start.getHours()); h1 = Math.max(h1, Math.min(24, e.end.getHours() + (e.end.getMinutes() ? 1 : 0))); });
    const today = new Date();
    let html = '<div class="ag-tg" style="--cols:' + days.length + ";--hour:" + HOUR_PX + 'px"><div class="ag-tg-head"><span class="ag-tg-gutter"></span>';
    days.forEach((d) => {
      html += '<button type="button" class="ag-tg-day' + (sameDay(d, today) ? " is-today" : "") + '" data-goday="' + d.getTime() + '"><small>' +
        esc(fmt(d, { weekday: "short" })) + "</small><b>" + d.getDate() + "</b></button>";
    });
    html += '</div><div class="ag-tg-scroll" id="ag-scroll"><div class="ag-tg-body" style="height:' + (h1 - h0) * HOUR_PX + 'px"><div class="ag-tg-hours">';
    for (let h = h0; h < h1; h++) html += '<span style="top:' + (h - h0) * HOUR_PX + 'px">' + String(h).padStart(2, "0") + ":00</span>";
    html += "</div>";
    days.forEach((d) => {
      const dayEv = inRange(rangeEv, d, addDays(d, 1));
      html += '<div class="ag-tg-col' + (sameDay(d, today) ? " is-today" : "") + ((d.getDay() + 6) % 7 >= 5 ? " is-weekend" : "") + '">';
      for (let h = h0; h < h1; h++) html += '<i class="ag-tg-line" style="top:' + (h - h0) * HOUR_PX + 'px"></i>';
      layout(dayEv).forEach(({ e, col, cols }) => {
        const top = ((e.start.getHours() - h0) + e.start.getMinutes() / 60) * HOUR_PX;
        const height = Math.max(24, ((e.end - e.start) / 3600e3) * HOUR_PX - 3);
        html += '<div class="' + evClass(e) + ' is-block' + (height < 44 ? " is-short" : "") + '"' + evAttrs(e) +
          ' style="top:' + top + "px;height:" + height + "px;left:calc(" + (col / cols) * 100 + "% + 3px);width:calc(" + 100 / cols + '% - 6px)">' +
          "<b>" + esc(e.a.candidateName || "Candidat") + "</b><time>" + hhmm(e.start) + " – " + hhmm(e.end) + "</time>" +
          '<span class="ag-ev-meta">' + esc([KIND_FR[e.a.interviewKind] || "", TYPE_FR[e.a.interviewType] || "", e.a.jobTitle || ""].filter(Boolean).join(" · ")) + "</span></div>";
      });
      if (sameDay(d, today)) {
        const mins = (today.getHours() - h0) * 60 + today.getMinutes();
        if (mins >= 0 && mins <= (h1 - h0) * 60) html += '<div class="ag-now" style="top:' + (mins / 60) * HOUR_PX + 'px"><i></i></div>';
      }
      html += "</div>";
    });
    html += "</div></div></div>";
    stage.innerHTML = html;
    const sc = stage.querySelector("#ag-scroll");
    const nowIn = days.some((d) => sameDay(d, today));
    const target = nowIn ? (today.getHours() - h0 - 1.5) * HOUR_PX : rangeEv.length ? ((rangeEv[0].start.getHours() - h0) - 0.5) * HOUR_PX : (8 - h0) * HOUR_PX;
    sc.scrollTop = Math.max(0, target);
    if (!rangeEv.length) stage.appendChild(emptyBanner(view === "day" ? "Journée libre" : "Aucun entretien cette semaine"));
  }

  /* ------------------------------------------------------------ liste */
  function renderList(stage, list) {
    const t0 = startOfDay(new Date());
    const upcoming = list.filter((e) => e.end >= t0);
    if (!upcoming.length) {
      const q = (($("rc-search") || {}).value || "").trim();
      if (q) { S.emptyState(stage, q, { onClear: () => { $("rc-search").value = ""; render(); } }); return; }
      RC.empty(stage, {
        title: "Aucun entretien à venir",
        text: "Passez un dossier de Shortlist à Entretien dans le pipeline pour planifier un échange : il apparaîtra ici automatiquement.",
        action: { href: "recruiter-pipeline.html", label: "Ouvrir le pipeline" },
      });
      return;
    }
    const byDay = new Map();
    upcoming.forEach((e) => { const k = startOfDay(e.start).getTime(); if (!byDay.has(k)) byDay.set(k, []); byDay.get(k).push(e); });
    let html = '<div class="ag-list">';
    byDay.forEach((items, k) => {
      const d = new Date(k);
      const rel = sameDay(d, new Date()) ? "Aujourd’hui · " : sameDay(d, addDays(new Date(), 1)) ? "Demain · " : "";
      html += '<section class="ag-lday"><h2><span class="ag-lnum' + (rel.startsWith("Aujourd") ? " is-today" : "") + '">' + d.getDate() + "</span>" + esc(rel + cap(fmt(d, { weekday: "long", month: "long", year: "numeric" }))) + "</h2>";
      items.forEach((e) => {
        html += '<div class="' + evClass(e) + ' is-row"' + evAttrs(e) + '><time>' + hhmm(e.start) + "<small>" + hhmm(e.end) + "</small></time><i></i>" +
          "<div><b>" + esc(e.a.candidateName || "Candidat") + "</b><span>" + esc([e.a.jobTitle, KIND_FR[e.a.interviewKind], TYPE_FR[e.a.interviewType], e.a.interviewLocation].filter(Boolean).join(" · ")) + "</span></div>" +
          badge(e) + "</div>";
      });
      html += "</section>";
    });
    stage.innerHTML = html + "</div>";
  }
  function badge(e) {
    if (e.cancelled) return '<span class="badge badge-danger">Annulé</span>';
    if (e.past) return '<span class="badge badge-chrome">Passé</span>';
    if (e.a.interviewConfirmation === "CONFIRMED") return '<span class="badge badge-ok">Confirmé</span>';
    if (e.a.interviewConfirmation === "UNAVAILABLE") return '<span class="badge badge-warn">Indisponible</span>';
    return '<span class="badge badge-info">En attente</span>';
  }
  function emptyBanner(title) {
    const el = document.createElement("div");
    el.className = "ag-empty-banner";
    el.innerHTML = '<div class="ag-empty-m">' + (window.Mascotte && Mascotte.markup ? Mascotte.markup({ pose: "vide" }) : "") + "</div><div><strong>" + esc(title) +
      '</strong><span>Planifiez depuis le pipeline, ou passez en vue Liste pour voir tous les rendez-vous à venir.</span></div><button type="button" class="btn btn-ghost btn-sm" data-view-go="list">Voir la liste</button>';
    return el;
  }

  /* ------------------------------------------------------------ panneau détail */
  function openDetail(id) {
    const a = apps.find((x) => String(x.id) === String(id));
    if (!a) return;
    selectedId = a.id;
    document.querySelectorAll(".ag-ev.is-selected").forEach((n) => n.classList.remove("is-selected"));
    document.querySelectorAll('[data-ev="' + a.id + '"]').forEach((n) => n.classList.add("is-selected"));
    const sheet = $("ag-sheet");
    const body = $("ag-sheet-body");
    const start = new Date(a.interviewAt);
    const past = start.getTime() + (a.interviewDurationMinutes || 45) * 60000 < Date.now();
    $("ag-sheet-title").textContent = a.candidateName || "Candidat";
    $("ag-sheet-sub").textContent = [a.jobTitle, KIND_FR[a.interviewKind] && "Entretien " + KIND_FR[a.interviewKind]].filter(Boolean).join(" · ");
    sheet.className = "ag-sheet t-" + (a.interviewType || "ASYNC").toLowerCase();
    body.replaceChildren(
      HireLinkInterview.card(a, { role: past || a.status !== "INTERVIEW" ? "readonly" : "recruiter", onChange: () => { closeDetail(); load(); } })
    );
    const links = document.createElement("div");
    links.className = "ag-sheet-links";
    links.innerHTML =
      '<a class="btn btn-secondary btn-sm" href="recruiter-pipeline.html?focus=application:' + a.id + '">Voir dans le pipeline</a>' +
      '<a class="btn btn-ghost btn-sm" href="recruiter-pipeline.html?app=' + a.id + '&focus=application:' + a.id + '">Voir le dossier</a>';
    body.appendChild(links);
    sheet.hidden = false;
    $("ag-sheet-scrim").hidden = false;
    requestAnimationFrame(() => { sheet.classList.add("is-open"); $("ag-sheet-scrim").classList.add("is-open"); });
    $("ag-sheet-close").focus({ preventScroll: true });
  }
  function closeDetail() {
    const sheet = $("ag-sheet");
    if (sheet.hidden) return;
    selectedId = null;
    document.querySelectorAll(".ag-ev.is-selected").forEach((n) => n.classList.remove("is-selected"));
    sheet.classList.remove("is-open");
    $("ag-sheet-scrim").classList.remove("is-open");
    setTimeout(() => { sheet.hidden = true; $("ag-sheet-scrim").hidden = true; }, reduce() ? 0 : 260);
  }

  /* ------------------------------------------------------------ navigation */
  function setView(v) {
    if (!VIEWS[v]) return;
    view = v;
    localStorage.setItem("hirelink.agenda.view", v);
    render();
  }
  function step(dir) {
    lastDir = dir;
    if (view === "month") cursor = new Date(cursor.getFullYear(), cursor.getMonth() + dir, 1);
    else cursor = addDays(cursor, (view === "week" ? 7 : 1) * dir);
    render();
  }
  function goToday() { cursor = startOfDay(new Date()); render(); }

  $("ag-prev").addEventListener("click", () => step(-1));
  $("ag-next").addEventListener("click", () => step(1));
  $("ag-today").addEventListener("click", goToday);
  $("ag-views").addEventListener("click", (e) => { const b = e.target.closest("button[data-view]"); if (b) setView(b.dataset.view); });
  $("ag-cal").addEventListener("click", (e) => {
    const go = e.target.closest("[data-view-go]");
    if (go) return setView(go.dataset.viewGo);
    const day = e.target.closest("[data-goday]");
    if (day) { cursor = new Date(Number(day.dataset.goday)); return setView("day"); }
    const ev = e.target.closest("[data-ev]");
    if (ev) return openDetail(ev.dataset.ev);
    const cell = e.target.closest(".ag-mcell");
    if (cell && mobile()) { cursor = new Date(Number(cell.dataset.day)); setView("day"); }
  });
  $("ag-cal").addEventListener("keydown", (e) => {
    const ev = e.target.closest("[data-ev]");
    if (ev && (e.key === "Enter" || e.key === " ")) { e.preventDefault(); openDetail(ev.dataset.ev); }
  });
  $("ag-sheet-close").addEventListener("click", closeDetail);
  $("ag-sheet-scrim").addEventListener("click", closeDetail);
  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape") return closeDetail();
    if (e.target.closest("input, select, textarea, [contenteditable]") || e.metaKey || e.ctrlKey || e.altKey) return;
    if (document.querySelector("#hl-overlay.is-open")) return;
    const k = e.key.toLowerCase();
    if (k === "t") goToday();
    else if (k === "arrowleft" || k === "j") step(-1);
    else if (k === "arrowright" || k === "k") step(1);
    else if (k === "m") setView("month");
    else if (k === "s" || k === "w") setView("week");
    else if (k === "d") setView("day");
    else if (k === "l") setView("list");
  });

  /* ------------------------------------------------------------ filtres */
  function rebuildOfferFilter() {
    const sel = $("ag-f-offer");
    const prev = sel.dataset.pending || sel.value || "all";
    delete sel.dataset.pending;
    const map = new Map();
    apps.forEach((a) => { if (a.jobOfferId != null && !map.has(a.jobOfferId)) map.set(a.jobOfferId, a.jobTitle || "Offre #" + a.jobOfferId); });
    sel.innerHTML = ['<option value="all">Toutes les offres</option>']
      .concat(Array.from(map.entries()).sort((a, b) => String(a[1]).localeCompare(String(b[1]), "fr")).map(([id, t]) => '<option value="' + id + '">' + esc(t) + "</option>"))
      .join("");
    sel.value = map.has(Number(prev)) || map.has(prev) ? prev : "all";
    filters.offer = sel.value;
  }
  function syncUrl() {
    const u = new URL(location.href);
    ["type", "kind", "offer"].forEach((k) => { if (filters[k] && filters[k] !== "all") u.searchParams.set(k, filters[k]); else u.searchParams.delete(k); });
    u.searchParams.set("view", view);
    u.searchParams.set("date", cursor.getFullYear() + "-" + String(cursor.getMonth() + 1).padStart(2, "0") + "-" + String(cursor.getDate()).padStart(2, "0"));
    history.replaceState(history.state, "", u.pathname + u.search + u.hash);
  }
  ["type", "kind"].forEach((k) => { const v = qp.get(k); if (v) { filters[k] = v; $("ag-f-" + k).value = v; } });
  if (qp.get("offer")) $("ag-f-offer").dataset.pending = qp.get("offer");
  ["ag-f-type", "ag-f-kind", "ag-f-offer"].forEach((id) => $(id).addEventListener("change", () => { filters[id.replace("ag-f-", "")] = $(id).value || "all"; render(); }));
  $("ag-f-cancelled").addEventListener("change", (e) => { filters.cancelled = e.target.checked; render(); });
  $("ag-f-reset").addEventListener("click", () => {
    Object.assign(filters, { type: "all", kind: "all", offer: "all", cancelled: false });
    $("ag-f-type").value = $("ag-f-kind").value = $("ag-f-offer").value = "all";
    $("ag-f-cancelled").checked = false;
    render();
  });

  /* ------------------------------------------------------------ halo depuis une notification */
  let pendingFocus = null;
  function prepareFocus(key) {
    const id = String(key || "").split(":")[1];
    const a = apps.find((x) => String(x.id) === id);
    if (!a || !a.interviewAt) return false;
    if (a.interviewStatus === "CANCELLED" && !filters.cancelled) { filters.cancelled = true; $("ag-f-cancelled").checked = true; }
    filters.type = filters.kind = filters.offer = "all";
    $("ag-f-type").value = $("ag-f-kind").value = $("ag-f-offer").value = "all";
    if ($("rc-search")) $("rc-search").value = "";
    cursor = startOfDay(new Date(a.interviewAt));
    view = view === "list" || view === "month" ? (mobile() ? "day" : "week") : view;
    render();
    return true;
  }
  document.addEventListener("hirelink:focus-request", (e) => {
    const key = e.detail && e.detail.key;
    if (!/^(interview|application):/.test(key || "")) return;
    if (!loaded) pendingFocus = key;
    else prepareFocus(key);
  });

  /* ------------------------------------------------------------ chargement */
  function load() {
    if (!loaded) RC.skeleton($("ag-cal"), 4, 96);
    RC.data.invalidate();
    RC.data.pipeline(
      (a) => {
        apps = a || [];
        const first = !loaded;
        loaded = true;
        rebuildOfferFilter();
        if (first && pendingFocus) { const k = pendingFocus; pendingFocus = null; if (!prepareFocus(k)) render(); }
        else render();
        if (selectedId && !$("ag-sheet").hidden) openDetail(selectedId);
      },
      (err) => RC.errorState($("ag-cal"), err.message, load)
    );
  }
  load();
  document.addEventListener("hirelink:push", load);
  // L'indicateur d'heure actuelle avance toute seule (vue semaine / jour).
  setInterval(() => {
    const now = document.querySelector(".ag-now");
    if (!now || document.hidden) return;
    const body = now.closest(".ag-tg-body");
    const first = body && body.querySelector(".ag-tg-hours span");
    if (!first) return;
    const h0 = parseInt(first.textContent, 10);
    const d = new Date();
    now.style.top = (((d.getHours() - h0) * 60 + d.getMinutes()) / 60) * HOUR_PX + "px";
  }, 60000);
  let lastMobile = mobile();
  window.addEventListener("resize", RC.debounce(() => { if (mobile() !== lastMobile) { lastMobile = mobile(); render(); } }, 200));
})();
