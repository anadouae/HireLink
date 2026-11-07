/**
 * Agenda recruteur : calendrier des entretiens et des événements de l'organisation.
 * Données : GET /applications/pipeline + GET /organizations/mine/events.
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

  const TYPE_FR = { VISIO: "Visioconférence", ONSITE: "Sur site", PHONE: "Téléphone", EVENT: "Agenda" };
  const KIND_FR = { HR: "RH", TECHNICAL: "Technique", MANAGER: "Manager", CASE: "Étude de cas", TEAM: "Équipe", FINAL: "Final" };
  const CATEGORY_FR = { MEETING: "Réunion", APPOINTMENT: "Rendez-vous", PERSONAL: "Personnel", PROFESSIONAL: "Professionnel", OTHER: "Autre" };
  const CATEGORY_CODES = Object.keys(CATEGORY_FR);
  const DURATION_PRESETS = [15, 30, 45, 60, 90, 120];
  const VIEWS = { month: "Mois", week: "Semaine", day: "Jour", list: "Liste" };
  const typeCls = (a) => "t-" + String(a.interviewType || "async").toLowerCase();

  let apps = [];
  let orgEvents = [];
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
  let workspace = "calendar";
  let notes = [];
  let editingId = null;
  let openMenuId = null;
  let justCreated = null;
  const penIds = new Set();
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
    const interviews = apps
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
    const otherEvents = orgEvents
      .filter((e) => e.startsAt && !Number.isNaN(new Date(e.startsAt).getTime()))
      .map((e) => {
        const start = new Date(e.startsAt);
        const end = new Date(start.getTime() + (e.durationMinutes || 60) * 60000);
        return {
          a: {
            id: "event-" + e.id, eventId: e.id, isOrganizationEvent: true,
            candidateName: e.title, jobTitle: e.description || "",
            interviewKind: e.category || "OTHER", interviewType: "EVENT",
            interviewLocation: e.location || "", interviewVideoUrl: e.videoUrl || "",
            interviewConfirmation: "CONFIRMED",
          },
          source: e, start, end, cancelled: false, past: end.getTime() < Date.now(),
        };
      });
    return interviews.concat(otherEvents).sort((x, y) => x.start - y.start);
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
    return "ag-ev " + (e.a.isOrganizationEvent ? "t-event" : typeCls(e.a)) + (e.cancelled ? " is-cancelled" : "") + (e.past ? " is-past" : "") +
      (e.a.interviewConfirmation === "CONFIRMED" ? " is-confirmed" : "") + (String(e.a.id) === String(selectedId) ? " is-selected" : "");
  }
  function evAttrs(e) {
    return ' data-ev="' + e.a.id + '" data-focus="' + (e.a.isOrganizationEvent ? "event:" + e.a.eventId : "interview:" + e.a.id + " application:" + e.a.id) + '" tabindex="0" role="button" aria-label="' +
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
    if (workspace === "notes") { renderNotes(); return; }
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

  /* ------------------------------------------------------------ Post-it */
  const NOTE_PAPERS = [
    { name: "Paille", bg: "#fdf0a8" }, { name: "Menthe", bg: "#d5f2df" }, { name: "Ciel", bg: "#d6e8fb" },
    { name: "Rose", bg: "#fbdce8" }, { name: "Lavande", bg: "#e6ddf9" }, { name: "Sable", bg: "#f7e6cf" },
  ];
  const NOTE_INKS = [
    { name: "Ardoise", fg: "#243044" }, { name: "Bordeaux", fg: "#7a2032" },
    { name: "Forêt", fg: "#1f5136" }, { name: "Nuit", fg: "#1e3a5f" },
  ];

  // positionX porte l'ordre de dépôt sur le tableau ; à égalité, le plus récent d'abord.
  function noteSorted() {
    return notes.slice().sort((a, b) => (a.positionX - b.positionX) || String(b.updatedAt).localeCompare(String(a.updatedAt)));
  }
  const tiltFor = (id) => (((Math.abs(Number(id) || 0) % 5) - 2) * 0.7).toFixed(2) + "deg";
  function relTime(iso) {
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return "";
    const days = Math.round((Date.now() - d.getTime()) / DAY);
    if (days <= 0) return "modifié aujourd’hui";
    if (days === 1) return "modifié hier";
    return "modifié " + fmt(d, { day: "numeric", month: "short" });
  }
  const noteEl = (id) => document.querySelector('.ag-note[data-note="' + id + '"]');
  async function postNote(body) { return HireLinkAPI.api("/organizations/mine/notes", { method: "POST", body }); }
  function payload(note, patch) {
    return Object.assign({
      title: note.title, content: note.content, inkColor: note.inkColor, noteColor: note.noteColor,
      drawingData: note.drawingData, positionX: note.positionX, positionY: note.positionY,
    }, patch, { id: note.id });
  }
  async function patchNote(note, patch, silent) {
    try {
      const saved = await postNote(payload(note, patch));
      notes = notes.map((n) => (n.id === saved.id ? saved : n));
      if (!silent) renderNotes();
      return saved;
    } catch (err) {
      RC.toast("Post-it", (err && err.message) || "Enregistrement impossible.", "danger");
      return null;
    }
  }

  function renderNotes() {
    const root = $("ag-notes");
    root.replaceChildren();
    const bar = document.createElement("div");
    bar.className = "ag-notes-toolbar";
    bar.innerHTML = '<div class="ag-notes-heading">' + HLIcons.svg("note", "ag-notes-mark") +
      "<div><h2>Mes Post-it</h2><p>Écrivez, dessinez, changez la couleur, glissez une note pour la ranger.</p></div></div>";
    const add = document.createElement("button");
    add.type = "button";
    add.className = "btn btn-primary";
    add.innerHTML = HLIcons.svg("plus") + "Nouveau Post-it";
    add.addEventListener("click", createNote);
    bar.appendChild(add);
    root.appendChild(bar);

    const board = document.createElement("div");
    board.className = "ag-notes-board";
    root.appendChild(board);
    const list = noteSorted();
    if (!list.length) {
      board.innerHTML = '<div class="ag-notes-empty rc-fade"><div class="rc-state-mascotte">' + Mascotte.markup({ pose: "vide" }) +
        "</div><h3>Aucun Post-it pour l’instant</h3><p>Votre tableau est vide — la première note est à un clic.</p></div>";
      return;
    }
    list.forEach((note) => board.appendChild(noteCard(note)));
    justCreated = null;
  }

  function noteCard(note) {
    const editing = editingId === note.id;
    const hasSketch = !!note.drawingData || penIds.has(note.id);
    const card = document.createElement("article");
    card.className = "ag-note" + (editing ? " is-editing" : "") + (justCreated === note.id ? " is-new" : "") + (hasSketch ? " has-sketch" : "");
    card.dataset.note = String(note.id);
    card.style.setProperty("--paper", note.noteColor || NOTE_PAPERS[0].bg);
    card.style.setProperty("--ink", note.inkColor || NOTE_INKS[0].fg);
    card.style.setProperty("--tilt", tiltFor(note.id));

    const head = document.createElement("header");
    head.className = "ag-note-head";
    head.innerHTML = '<span class="ag-note-clip" aria-hidden="true">' + HLIcons.svg("paperclip") + "</span>" +
      '<button type="button" class="ag-note-grip" aria-label="Déplacer la note">' + HLIcons.svg("grip") + "</button>" +
      '<button type="button" class="ag-note-more" aria-label="Options de la note" aria-haspopup="true" aria-expanded="' + (openMenuId === note.id) + '">' + HLIcons.svg("dots") + "</button>";
    card.appendChild(head);

    const title = document.createElement(editing ? "input" : "h3");
    title.className = "ag-note-title";
    title.setAttribute("aria-label", "Titre");
    if (editing) { title.type = "text"; title.value = note.title || ""; title.maxLength = 120; }
    else title.textContent = note.title || "Sans titre";
    card.appendChild(title);

    const text = document.createElement(editing ? "textarea" : "p");
    text.className = "ag-note-text";
    text.setAttribute("aria-label", "Contenu");
    if (editing) { text.value = note.content || ""; text.rows = 4; text.maxLength = 8000; }
    else {
      text.textContent = note.content || "Aucune note — cliquez sur le stylo pour écrire.";
      if (!note.content) text.classList.add("is-void");
    }
    card.appendChild(text);

    const sketch = document.createElement("div");
    sketch.className = "ag-note-sketch";
    sketch.innerHTML = '<div class="ag-note-sketch-head"><span class="ag-note-sketch-label">' + HLIcons.svg("pen") + "Crayon</span>" +
      '<button type="button" class="btn btn-ghost btn-sm ag-note-clear">Effacer</button></div>' +
      '<canvas class="ag-note-canvas" aria-label="Zone de dessin"></canvas>';
    card.appendChild(sketch);

    const foot = document.createElement("footer");
    foot.className = "ag-note-foot";
    foot.innerHTML = editing
      ? '<button type="button" class="btn btn-primary btn-sm ag-note-save">' + HLIcons.svg("save") + "Enregistrer</button>" +
        '<button type="button" class="btn btn-ghost btn-sm ag-note-cancel">Annuler</button>'
      : '<span class="ag-note-when">' + relTime(note.updatedAt) + "</span>" +
        '<button type="button" class="ag-note-edit" aria-label="Écrire sur ce Post-it">' + HLIcons.svg("pencil") + "</button>";
    card.appendChild(foot);

    if (openMenuId === note.id) card.appendChild(noteMenu(note));

    head.querySelector(".ag-note-more").addEventListener("click", () => {
      openMenuId = openMenuId === note.id ? null : note.id;
      renderNotes();
    });
    const edit = foot.querySelector(".ag-note-edit");
    if (edit) edit.addEventListener("click", () => {
      editingId = note.id; openMenuId = null;
      renderNotes();
      const first = noteEl(note.id);
      if (first) { const t = first.querySelector(".ag-note-title"); t.focus(); if (t.select) t.select(); }
    });
    const save = foot.querySelector(".ag-note-save");
    if (save) save.addEventListener("click", async () => {
      RC.busy(save, true);
      // editingId part avant l'appel : le rendu déclenché par patchNote passe la carte en lecture.
      editingId = null;
      const saved = await patchNote(note, { title: title.value.trim() || "Sans titre", content: text.value });
      RC.busy(save, false);
      if (saved) RC.toast("Post-it", "Note enregistrée.", "ok");
      else editingId = note.id;
    });
    const cancel = foot.querySelector(".ag-note-cancel");
    if (cancel) cancel.addEventListener("click", () => { editingId = null; renderNotes(); });
    sketch.querySelector(".ag-note-clear").addEventListener("click", () => {
      const canvas = sketch.querySelector(".ag-note-canvas");
      canvas.getContext("2d").clearRect(0, 0, canvas.width, canvas.height);
      patchNote(note, { drawingData: null }, true);
    });

    if (hasSketch) setupNoteCanvas(sketch.querySelector(".ag-note-canvas"), note, card);
    enableDrag(card);
    return card;
  }

  function noteMenu(note) {
    const menu = document.createElement("div");
    menu.className = "ag-note-menu";
    menu.innerHTML = '<p class="ag-note-menu-title">Couleur du Post-it</p><div class="ag-note-swatches">' +
      NOTE_PAPERS.map((p) => '<button type="button" class="ag-note-swatch' + (p.bg === note.noteColor ? " is-on" : "") +
        '" style="--sw:' + p.bg + '" data-paper="' + p.bg + '" title="' + esc(p.name) + '" aria-label="Fond ' + esc(p.name) + '"></button>').join("") +
      '</div><p class="ag-note-menu-title">Couleur d’écriture</p><div class="ag-note-swatches">' +
      NOTE_INKS.map((p) => '<button type="button" class="ag-note-swatch' + (p.fg === note.inkColor ? " is-on" : "") +
        '" style="--sw:' + p.fg + '" data-ink="' + p.fg + '" title="' + esc(p.name) + '" aria-label="Encre ' + esc(p.name) + '"></button>').join("") +
      '</div><div class="ag-note-menu-actions">' +
      '<button type="button" class="btn btn-ghost btn-sm' + (penIds.has(note.id) ? " is-on" : "") + '" data-pen>' + HLIcons.svg("pen") + "Crayon</button>" +
      '<button type="button" class="btn btn-danger btn-sm" data-del>' + HLIcons.svg("trash") + "Supprimer</button></div>";
    menu.addEventListener("click", async (e) => {
      const paper = e.target.closest("[data-paper]");
      if (paper) { await patchNote(note, { noteColor: paper.dataset.paper }); return; }
      const ink = e.target.closest("[data-ink]");
      if (ink) { await patchNote(note, { inkColor: ink.dataset.ink }); return; }
      if (e.target.closest("[data-pen]")) {
        if (penIds.has(note.id)) penIds.delete(note.id); else penIds.add(note.id);
        renderNotes();
        const canvas = noteEl(note.id) && noteEl(note.id).querySelector(".ag-note-canvas");
        if (canvas) fitCanvas(canvas);
        return;
      }
      if (e.target.closest("[data-del]")) { openMenuId = null; await deleteNote(note); }
    });
    return menu;
  }

  async function createNote() {
    const at = noteSorted().reduce((m, n) => Math.max(m, (n.positionX || 0) + 1), 0);
    try {
      const saved = await postNote({
        title: "Nouvelle note", content: "", noteColor: NOTE_PAPERS[at % NOTE_PAPERS.length].bg,
        inkColor: NOTE_INKS[0].fg, positionX: at, positionY: 0,
      });
      notes.push(saved);
      editingId = saved.id; justCreated = saved.id; openMenuId = saved.id;
      renderNotes();
      const card = noteEl(saved.id);
      if (card) card.scrollIntoView({ block: "nearest", behavior: reduce() ? "auto" : "smooth" });
      const t = card && card.querySelector(".ag-note-title");
      if (t) { t.focus(); if (t.select) t.select(); }
    } catch (err) { RC.toast("Post-it", (err && err.message) || "Création impossible.", "danger"); }
  }

  async function deleteNote(note) {
    if (!await Mascotte.confirm({ title: "Supprimer ce Post-it ?", description: "Cette action est définitive.", confirmLabel: "Supprimer", danger: true })) return;
    const card = noteEl(note.id);
    if (card && !reduce()) card.classList.add("is-leaving");
    try {
      await HireLinkAPI.api("/organizations/mine/notes/" + note.id, { method: "DELETE" });
      notes = notes.filter((n) => n.id !== note.id);
      penIds.delete(note.id);
      if (editingId === note.id) editingId = null;
      setTimeout(renderNotes, card && !reduce() ? 240 : 0);
      RC.toast("Post-it", "Note supprimée.", "ok");
    } catch (err) {
      if (card) card.classList.remove("is-leaving");
      RC.toast("Post-it", (err && err.message) || "Suppression impossible.", "danger");
    }
  }

  /* ------------------------------------------------------------- crayon */
  function fitCanvas(canvas) {
    const w = Math.max(140, Math.round(canvas.clientWidth || 240));
    const h = 132;
    canvas.width = w;
    canvas.height = h;
    const ctx = canvas.getContext("2d");
    ctx.lineCap = "round";
    ctx.lineJoin = "round";
    ctx.lineWidth = 3;
    return ctx;
  }
  const canvasData = (canvas) => { try { return canvas.toDataURL("image/png"); } catch (_) { return ""; } };
  function setupNoteCanvas(canvas, note, card) {
    const ctx = fitCanvas(canvas);
    const ink = () => (card.style.getPropertyValue("--ink") || NOTE_INKS[0].fg).trim();
    if (note.drawingData) {
      const image = new Image();
      image.onload = () => ctx.drawImage(image, 0, 0, canvas.width, canvas.height);
      image.src = note.drawingData;
    }
    let drawing = false, touched = false, timer = null;
    const point = (event) => {
      const rect = canvas.getBoundingClientRect();
      return {
        x: (event.clientX - rect.left) * canvas.width / rect.width,
        y: (event.clientY - rect.top) * canvas.height / rect.height,
      };
    };
    canvas.addEventListener("pointerdown", (event) => {
      event.preventDefault();
      drawing = true;
      touched = true;
      try { canvas.setPointerCapture(event.pointerId); } catch (_) {}
      ctx.strokeStyle = ink();
      const p = point(event);
      ctx.beginPath();
      ctx.moveTo(p.x, p.y);
      ctx.lineTo(p.x + 0.01, p.y);
      ctx.stroke();
    });
    canvas.addEventListener("pointermove", (event) => {
      if (!drawing) return;
      const p = point(event);
      ctx.lineTo(p.x, p.y);
      ctx.stroke();
    });
    const stop = () => {
      if (!drawing) return;
      drawing = false;
      if (!touched) return;
      touched = false;
      clearTimeout(timer);
      timer = setTimeout(() => patchNote(note, { drawingData: canvasData(canvas) }, true), 700);
    };
    canvas.addEventListener("pointerup", stop);
    canvas.addEventListener("pointercancel", stop);
  }

  /* ---------------------------------------------------- déplacer (glisser) */
  function dropTarget(board, dragged, x, y) {
    let best = null, bestD = Infinity;
    Array.from(board.children).forEach((c) => {
      if (c === dragged) return;
      const r = c.getBoundingClientRect();
      const d = Math.hypot(x - (r.left + r.width / 2), y - (r.top + r.height / 2));
      if (d < bestD) { bestD = d; best = c; }
    });
    return best;
  }
  async function commitOrder(board, dragged, target, before) {
    const cards = Array.from(board.children);
    const from = cards.indexOf(dragged);
    if (from < 0 || from === cards.indexOf(target)) return;
    if (from < cards.indexOf(target)) board.insertBefore(dragged, target.nextSibling);
    else board.insertBefore(dragged, target);
    if (!reduce()) {
      Array.from(board.children).forEach((c) => {
        const b = before.get(c), a = c.getBoundingClientRect();
        const dx = b.left - a.left, dy = b.top - a.top;
        if (!dx && !dy) return;
        c.style.transition = "none";
        c.style.transform = "translate(" + dx + "px," + dy + "px)";
        requestAnimationFrame(() => { c.style.transition = "transform .34s var(--ease)"; c.style.transform = ""; });
      });
    }
    const byId = new Map(notes.map((n) => [String(n.id), n]));
    for (let i = 0; i < board.children.length; i++) {
      const n = byId.get(board.children[i].dataset.note);
      if (n && (n.positionX || 0) !== i) await patchNote(n, { positionX: i }, true);
    }
  }
  function enableDrag(card) {
    const grip = card.querySelector(".ag-note-grip");
    if (!grip) return;
    grip.addEventListener("pointerdown", (event) => {
      if (mobile()) return;
      event.preventDefault();
      const board = card.parentElement;
      const start = card.getBoundingClientRect();
      const offX = event.clientX - start.left, offY = event.clientY - start.top;
      try { grip.setPointerCapture(event.pointerId); } catch (_) {}
      card.classList.add("is-dragging");
      let target = null;
      const move = (e) => {
        card.style.transform = "translate(" + (e.clientX - start.left - offX) + "px," + (e.clientY - start.top - offY) + "px) rotate(1.4deg) scale(1.03)";
        target = dropTarget(board, card, e.clientX, e.clientY);
        Array.from(board.children).forEach((c) => c.classList.toggle("is-drop-target", c !== card && c === target));
      };
      const up = async () => {
        window.removeEventListener("pointermove", move);
        window.removeEventListener("pointerup", up);
        window.removeEventListener("pointercancel", up);
        const before = new Map(Array.from(board.children).map((c) => [c, c.getBoundingClientRect()]));
        card.classList.remove("is-dragging");
        card.style.transform = "";
        Array.from(board.children).forEach((c) => c.classList.remove("is-drop-target"));
        if (target) await commitOrder(board, card, target, before);
      };
      // Écouter la fenêtre : le glisser-sortie hors de la poignée doit continuer à suivre le pointeur.
      window.addEventListener("pointermove", move);
      window.addEventListener("pointerup", up);
      window.addEventListener("pointercancel", up);
    });
  }

  async function loadNotes() {
    RC.skeleton($("ag-notes"), 3, 200);
    try {
      notes = (await HireLinkAPI.api("/organizations/mine/notes")) || [];
      return true;
    } catch (err) {
      notes = [];
      RC.errorState($("ag-notes"), (err && err.message) || "Le serveur de notes est indisponible.", loadNotes);
      return false;
    }
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
        html += '<div class="' + evClass(e) + ' is-chip"' + evAttrs(e) + '><i class="iv-light-green" aria-hidden="true"></i><time>' + hhmm(e.start) + "</time><b>" + esc(e.a.candidateName || "Événement") + "</b></div>";
      });
      if (mobile() && dayEv.length) html += '<span class="ag-dots">' + dayEv.slice(0, 4).map((e) => '<i class="' + (e.a.isOrganizationEvent ? "t-event" : typeCls(e.a)) + '"></i>').join("") + "</span>";
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
          "<b><i class=\"iv-light-green\" aria-hidden=\"true\"></i>" + esc(e.a.candidateName || "Événement") + "</b><time>" + hhmm(e.start) + " – " + hhmm(e.end) + "</time>" +
          '<span class="ag-ev-meta">' + esc(e.a.isOrganizationEvent ? [CATEGORY_FR[e.a.interviewKind] || "Événement", e.a.interviewLocation].filter(Boolean).join(" · ") : [KIND_FR[e.a.interviewKind] || "", TYPE_FR[e.a.interviewType] || "", e.a.jobTitle || ""].filter(Boolean).join(" · ")) + "</span></div>";
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
          "<div><b><i class=\"iv-light-green\" aria-hidden=\"true\"></i>" + esc(e.a.candidateName || "Événement") + "</b><span>" + esc(e.a.isOrganizationEvent ? [CATEGORY_FR[e.a.interviewKind] || "Événement", e.a.interviewLocation].filter(Boolean).join(" · ") : [e.a.jobTitle, KIND_FR[e.a.interviewKind], TYPE_FR[e.a.interviewType], e.a.interviewLocation].filter(Boolean).join(" · ")) + "</span></div>" +
          badge(e) + "</div>";
      });
      html += "</section>";
    });
    stage.innerHTML = html + "</div>";
  }
  function badge(e) {
    if (e.a.isOrganizationEvent) return '<span class="badge badge-info">Événement</span>';
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
    if (!a) {
      const event = orgEvents.find((x) => "event-" + x.id === String(id));
      if (event) return openOrganizationEvent(event);
      return;
    }
    selectedId = a.id;
    document.querySelectorAll(".ag-ev.is-selected").forEach((n) => n.classList.remove("is-selected"));
    document.querySelectorAll('[data-ev="' + a.id + '"]').forEach((n) => n.classList.add("is-selected"));
    const sheet = $("ag-sheet");
    const body = $("ag-sheet-body");
    const start = new Date(a.interviewAt);
    const past = start.getTime() + (a.interviewDurationMinutes || 45) * 60000 < Date.now();
    $("ag-sheet-title").textContent = a.candidateName || "Candidat";
    $("ag-sheet-sub").textContent = [a.jobTitle, KIND_FR[a.interviewKind] && "Entretien " + KIND_FR[a.interviewKind]].filter(Boolean).join(" · ");
    sheet.className = "ag-sheet " + typeCls(a);
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
  function openOrganizationEvent(event) {
    selectedId = "event-" + event.id;
    const start = new Date(event.startsAt);
    const end = new Date(start.getTime() + (event.durationMinutes || 60) * 60000);
    const sheet = $("ag-sheet");
    $("ag-sheet-title").textContent = event.title;
    $("ag-sheet-sub").textContent = [fmt(start, { weekday: "long", day: "numeric", month: "long" }), hhmm(start) + " – " + hhmm(end)].join(" · ");
    sheet.className = "ag-sheet t-event";
    $("ag-sheet-body").innerHTML =
      '<div class="ag-event-note"><strong>Notes</strong><p>' + esc(event.description || "Aucune note.") + "</p></div>" +
      (event.location ? "<p><strong>Lieu :</strong> " + esc(event.location) + "</p>" : "") +
      (event.videoUrl ? '<p><a class="btn btn-soft btn-sm" href="' + esc(event.videoUrl) + '" target="_blank" rel="noopener">Ouvrir le lien</a></p>' : "") +
      // Le serveur refuse un événement passé (« doit commencer dans le futur ») : proposer la modification uniquement quand elle peut aboutir.
      (!RC.isHM() && new Date(event.startsAt) > new Date() ? '<button type="button" class="btn btn-soft btn-sm" id="ag-edit-event">Modifier</button> ' : "") +
      (RC.isHM() ? "" : '<button type="button" class="btn btn-danger btn-sm" id="ag-delete-event">Supprimer l’événement</button>');
    const editEventBtn = $("ag-edit-event");
    if (editEventBtn) editEventBtn.addEventListener("click", () => { closeDetail(); createOrganizationEvent(event); });
    const delEventBtn = $("ag-delete-event");
    if (delEventBtn) delEventBtn.addEventListener("click", async () => {
      if (!await Mascotte.confirm({ title: "Supprimer cet événement ?", description: "Cette action est définitive.", confirmLabel: "Supprimer", danger: true })) return;
      try {
        await HireLinkAPI.api("/organizations/mine/events/" + event.id, { method: "DELETE" });
        closeDetail(); load();
        RC.toast("Agenda", "Événement supprimé.", "ok");
      } catch (err) { RC.toast("Agenda", err.message, "danger"); }
    });
    sheet.hidden = false; $("ag-sheet-scrim").hidden = false;
    requestAnimationFrame(() => { sheet.classList.add("is-open"); $("ag-sheet-scrim").classList.add("is-open"); });
    $("ag-sheet-close").focus({ preventScroll: true });
  }
  /** Le pipeline décide de l'étape, l'Agenda pose le créneau : ici atterrissent les dossiers passés en entretien. */
  async function planInterview() {
    const pool = apps
      .filter((a) => a.status === "INTERVIEW" && a.interviewStatus !== "CANCELLED")
      .sort((x, y) => (x.interviewAt ? 1 : 0) - (y.interviewAt ? 1 : 0));
    if (!pool.length) {
      RC.toast("Agenda", "Aucun dossier à l’étape Entretien : avancez d’abord un dossier du pipeline.", "info");
      return;
    }
    const ok = await HireLink.openDrawer({
      title: "Planifier un entretien",
      description: "Choisissez le dossier — les sans-créneau sont proposés en premier.",
      bodyHtml: '<div class="form-grid"><label class="form-grid-wide">Dossier<select id="ag-plan-app" class="rc-select"></select></label></div>',
      confirmLabel: "Choisir un créneau",
      wide: true,
      onReady: (api) => {
        const sel = api.root.querySelector("#ag-plan-app");
        pool.forEach((a) => {
          const opt = document.createElement("option");
          opt.value = a.id;
          opt.textContent = (a.candidateName || "Candidat") + " — " + (a.jobTitle || "Offre") +
            (a.interviewAt ? " (déjà un créneau)" : " (sans créneau)");
          sel.appendChild(opt);
        });
      },
    });
    if (!ok) return;
    const chosen = pool.find((a) => String(a.id) === String($("ag-plan-app").value)) || pool[0];
    HireLinkInterview.plan(chosen, { onDone: () => load() });
  }

  /** « 45 min », « 1 h », « 1 h 30 » : le libellé court d'une durée, aligné sur ce que saisit le champ. */
  function durationLabel(m) {
    const h = Math.floor(m / 60);
    const rest = m % 60;
    if (!h) return m + " min";
    return rest ? h + " h " + rest : h + " h";
  }

  /** Le créneau réellement créé : date locale, heure de début, heure de fin. */
  function slotLabel(startValue, minutes) {
    const from = new Date(startValue);
    if (!startValue || isNaN(from.getTime())) return "";
    const to = new Date(from.getTime() + Math.max(0, minutes) * 60000);
    const day = new Intl.DateTimeFormat("fr-FR", { weekday: "long", day: "numeric", month: "long" }).format(from);
    const hour = (d) => new Intl.DateTimeFormat("fr-FR", { hour: "2-digit", minute: "2-digit" }).format(d);
    return day.charAt(0).toUpperCase() + day.slice(1) + " · " + hour(from) + " – " + hour(to);
  }

  function eventCategory() {
    const picked = document.querySelector('input[name="ag-event-type"]:checked');
    if (!picked || picked.value !== "OTHER") return picked ? picked.value : CATEGORY_CODES[0];
    const custom = ($("ag-event-category") || {}).value || "";
    return custom.trim() || "OTHER";
  }

  async function createOrganizationEvent(existing) {
    const base = existing ? new Date(existing.startsAt) : new Date(Date.now() + 3600000);
    base.setSeconds(0, 0);
    const value = new Date(base.getTime() - base.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
    const minutes = existing && existing.durationMinutes ? Number(existing.durationMinutes) : 60;
    const stored = existing && existing.category ? String(existing.category).toUpperCase() : "MEETING";
    const known = CATEGORY_CODES.indexOf(stored) >= 0;
    const picked = known ? stored : "OTHER";
    const typeChips = CATEGORY_CODES.map((code) =>
      '<label><input type="radio" name="ag-event-type" value="' + code + '"' + (code === picked ? " checked" : "") + ">" +
      "<span>" + esc(CATEGORY_FR[code]) + "</span></label>").join("");
    const durChips = DURATION_PRESETS.map((m) =>
      '<button type="button" class="rc-chip' + (m === minutes ? " is-on" : "") + '" data-min="' + m + '">' + durationLabel(m) + "</button>").join("");
    const ok = await HireLink.openModal({
      title: existing ? "Modifier l’événement" : "Nouvel événement",
      description: existing
        ? "Les participants voient immédiatement le nouvel horaire sur leur agenda."
        : "Réunion, rendez-vous, événement personnel ou autre activité professionnelle.",
      bodyHtml:
        '<div class="agx">' +
        '<label class="agx-title">Titre<input id="ag-event-title" required maxlength="180" placeholder="Réunion d’équipe" value="' + esc(existing ? existing.title || "" : "") + '"></label>' +
        '<div class="agx-block"><span class="agx-cap">Type</span><div class="agx-types">' + typeChips + "</div></div>" +
        '<label class="agx-custom" id="ag-event-custom-wrap"' + (picked === "OTHER" ? "" : " hidden") + ">Nom du type personnalisé" +
        '<input id="ag-event-category" maxlength="32" placeholder="Ex. point produit, revue de code…" value="' + esc(known ? "" : stored) + '"></label>' +
        '<div class="agx-row">' +
        '<label>Commence<input id="ag-event-start" type="datetime-local" value="' + value + '" required></label>' +
        '<label>Durée (minutes)<input id="ag-event-duration" type="number" min="5" max="1440" value="' + minutes + '" required></label>' +
        "</div>" +
        '<div class="rc-chips agx-durs" role="group" aria-label="Durées courtes">' + durChips + "</div>" +
        '<p class="agx-slot" id="ag-event-slot" aria-live="polite"></p>' +
        '<div class="agx-row">' +
        '<label>Lieu<input id="ag-event-location" maxlength="120" placeholder="Bureau, salle…" value="' + esc(existing ? existing.location || "" : "") + '"></label>' +
        '<label>Lien de visioconférence<input id="ag-event-video" type="url" maxlength="500" placeholder="https://meet… (facultatif)" value="' + esc(existing ? existing.videoUrl || "" : "") + '"></label>' +
        "</div>" +
        '<label>Notes<textarea id="ag-event-description" maxlength="2000" rows="4" placeholder="Ordre du jour, participants, préparation…">' +
        esc(existing ? existing.description || "" : "") + "</textarea></label>" +
        "</div>",
      confirmLabel: existing ? "Enregistrer les modifications" : "Créer l’événement",
      large: true,
      onReady: ({ root }) => {
        const start = root.querySelector("#ag-event-start");
        const dur = root.querySelector("#ag-event-duration");
        const slot = root.querySelector("#ag-event-slot");
        const custom = root.querySelector("#ag-event-custom-wrap");
        const paintSlot = () => {
          const m = Math.max(0, Number(dur.value) || 0);
          slot.textContent = slotLabel(start.value, m) || "Choisissez une date pour voir le créneau.";
          root.querySelectorAll(".agx-durs .rc-chip").forEach((chip) => {
            chip.classList.toggle("is-on", Number(chip.dataset.min) === m);
          });
        };
        const paintType = () => {
          const pickedChip = root.querySelector('input[name="ag-event-type"]:checked');
          custom.hidden = !pickedChip || pickedChip.value !== "OTHER";
        };
        start.addEventListener("input", paintSlot);
        dur.addEventListener("input", paintSlot);
        root.querySelectorAll('.agx-types input').forEach((r) => r.addEventListener("change", paintType));
        root.querySelectorAll(".agx-durs .rc-chip").forEach((chip) => {
          chip.addEventListener("click", () => { dur.value = chip.dataset.min; paintSlot(); });
        });
        paintSlot();
        paintType();
      },
    });
    if (!ok) return;
    const local = $("ag-event-start").value;
    try {
      await HireLinkAPI.api(existing ? "/organizations/mine/events/" + existing.id : "/organizations/mine/events",
        { method: existing ? "PUT" : "POST", body: {
        title: $("ag-event-title").value, category: eventCategory(),
        startsAt: new Date(local).toISOString(), durationMinutes: Number($("ag-event-duration").value),
        location: $("ag-event-location").value, videoUrl: $("ag-event-video").value,
        description: $("ag-event-description").value
      }});
      await load();
      RC.toast("Agenda", existing ? "Événement modifié." : "Événement créé.", "ok");
    } catch (err) { RC.toast("Agenda", err.message, "danger"); }
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
  $("ag-add-event").addEventListener("click", () => createOrganizationEvent());
  $("ag-plan-interview").addEventListener("click", planInterview);
  const WORKSPACE_COPY = {
    calendar: "Tous les entretiens de l’équipe dans un calendrier : mois, semaine, jour ou liste.",
    notes: "Vos notes personnelles : couleur, crayon, déplacement — rien n’est partagé avec l’équipe.",
  };
  async function setWorkspace(next) {
    workspace = next;
    const isNotes = next === "notes";
    document.querySelectorAll("[data-ag-workspace]").forEach((t) => {
      const active = t.dataset.agWorkspace === next;
      t.classList.toggle("is-active", active);
      t.setAttribute("aria-selected", String(active));
    });
    // Rien du calendrier ne doit rester visible au-dessus du tableau de notes.
    document.querySelector(".ag-bar").hidden = isNotes;
    document.querySelector(".ag-filters").hidden = isNotes;
    $("ag-cal").hidden = isNotes;
    $("ag-stats").hidden = isNotes;
    $("ag-add-event").hidden = isNotes || RC.isHM();
    $("ag-plan-interview").hidden = isNotes;
    $("ag-head-sub").textContent = WORKSPACE_COPY[next];
    $("ag-notes").hidden = !isNotes;
    if (isNotes) { if (await loadNotes()) renderNotes(); }
    else render();
  }
  document.querySelectorAll("[data-ag-workspace]").forEach((tab) => tab.addEventListener("click", () => setWorkspace(tab.dataset.agWorkspace)));
  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape") return closeDetail();
    if (e.target.closest("input, select, textarea, [contenteditable]") || e.metaKey || e.ctrlKey || e.altKey) return;
    if (document.querySelector("#hl-overlay.is-open")) return;
    if (workspace === "notes") return;
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
    Promise.all([
      new Promise((resolve, reject) => RC.data.pipeline(resolve, reject)),
      HireLinkAPI.api("/organizations/mine/events")
    ]).then(([a, events]) => {
        apps = a || [];
        orgEvents = events || [];
        const first = !loaded;
        loaded = true;
        rebuildOfferFilter();
        if (first && pendingFocus) { const k = pendingFocus; pendingFocus = null; if (!prepareFocus(k)) render(); }
        else render();
        if (selectedId && !$("ag-sheet").hidden) openDetail(selectedId);
      }).catch((err) => RC.errorState($("ag-cal"), err.message, load));
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
