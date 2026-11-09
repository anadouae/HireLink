/** Offres : liste de l'organisation, filtres, publication et assistant de création. */
(function () {
  const RC = HireLinkApp;
  const session = RC.mount({
    active: "offers",
    title: "Offres",
    search: { placeholder: "Titre, lieu, compétences…", onQuery: () => paint() },
  });
  if (!session) return;
  const $ = (id) => document.getElementById(id);
  const esc = RC.esc;
  let offers = [];
  let apps = [];
  let view = "all";
  let loaded = false;

  function counts() {
    const c = {};
    apps.forEach((a) => { c[a.jobOfferId] = (c[a.jobOfferId] || 0) + 1; });
    return c;
  }

  /** Un seul test de filtre pour les compteurs et pour la liste : ils ne peuvent plus se contredire. */
  function inView(filter, state, apps) {
    if (filter === "archived") return state === "archived";
    if (state === "archived") return false;
    if (filter === "all") return true;
    if (filter === "published") return state === "online";
    if (filter === "draft") return state === "draft" || state === "suspended";
    if (filter === "closed") return state === "closed";
    if (filter === "with") return apps > 0;
    return true;
  }

  function paint() {
    if (!$("offers-grid")) return;
    if (!loaded) return;
    const c = counts();
    const q = (($("rc-search") || {}).value || "").trim();
    const searched = HireLinkSearch.rank(offers, q, [
      { get: (o) => o.title, weight: 3 }, { get: (o) => o.requiredSkills, weight: 2 },
      { get: (o) => o.location + " " + o.contractType, weight: 2 }, { get: (o) => o.description, weight: 1 },
    ]);
    const vc = {};
    $("offer-filter").querySelectorAll("button").forEach((b) => {
      const key = b.dataset.ov;
      vc[key] = searched.reduce((n, o) => n + (inView(key, stateOf(o).key, c[o.id] || 0) ? 1 : 0), 0);
      b.classList.toggle("is-on", key === view);
      let em = b.querySelector("em");
      if (!em) { em = document.createElement("em"); b.appendChild(em); }
      em.textContent = vc[key] || 0;
    });
    const list = searched.filter((o) => inView(view, stateOf(o).key, c[o.id] || 0));
    $("offers-summary").textContent = RC.plural(list.length, "offre");
    const grid = $("offers-grid");
    grid.replaceChildren();
    if (!list.length) {
      if (q) { HireLinkSearch.emptyState(grid, q, { onClear: () => { $("rc-search").value = ""; paint(); } }); return; }
      RC.empty(grid, offers.length
        ? { title: "Aucune offre pour ce filtre", text: "Modifiez le filtre pour retrouver vos offres." }
        : { title: "Aucune offre pour l’instant", text: "Créez votre première offre : poste, conditions, compétences et ville.", action: null });
      return;
    }
    list.forEach((o) => grid.appendChild(managed(o, c[o.id] || 0)));
    HireLinkSearch.mark(grid, q);
  }

  /** Ce que le candidat verra : la vélocité réellement mesurée, jamais un délai promis. */
  function paintPace() {
    const box = $("rc-pace");
    if (!box) return;
    const src = offers.find((o) => o.responsiveness) || offers[0];
    if (!src) { box.hidden = true; return; }
    const pace = HireLinkOffer.paceOf(src);
    box.hidden = false;
    box.textContent = pace.badge
      ? "Reconnaissance affichée aux candidats : « " + pace.badge + " » — " + pace.sentence
      : "Aucune reconnaissance pour le moment. Elle s’affiche dès que trois premières réponses sont enregistrées sur vos offres.";
  }

  // Trois leviers indépendants — en ligne, close, rangée — réduits à un seul état, partagé avec l'administration.
  const stateOf = RC.offerState;

  function stateLabel(o, n) {
    return stateOf(o).label + " · " + RC.plural(n, "candidature");
  }

  /** Ce que l'on voit d'abord : l'action qui répond à la question du jour. Le reste est dans « ⋯ ». */
  function actionsFor(o, n) {
    const key = stateOf(o).key;
    const menu = [];
    let primary;
    if (key === "online") {
      primary = [{ act: "unpublish", label: "Dépublier", cls: "btn-soft" }, { act: "close", label: "Clôturer", cls: "btn-ghost" }];
      menu.push({ act: "extend", label: "Prolonger la date limite" }, { act: "duplicate", label: "Dupliquer" }, { act: "archive", label: "Archiver" });
    } else if (key === "closed") {
      primary = [{ act: "reopen", label: "Rouvrir les candidatures", cls: "btn-primary" }, { act: "extend", label: "Prolonger", cls: "btn-ghost" }];
      menu.push({ act: "duplicate", label: "Dupliquer" }, { act: "archive", label: "Archiver" });
    } else if (key === "archived") {
      primary = [{ act: "unarchive", label: "Restaurer", cls: "btn-primary" }];
      menu.push({ act: "duplicate", label: "Dupliquer" });
    } else if (key === "suspended") {
      primary = [{ act: "why", label: "Voir le motif", cls: "btn-ghost" }];
      menu.push({ act: "duplicate", label: "Dupliquer" });
    } else if (peutPublier()) {
      primary = [{ act: "publish", label: "Publier", cls: "btn-primary" }];
      menu.push({ act: "duplicate", label: "Dupliquer" });
    } else {
      primary = [{ act: "verify", label: "Vérifier l’entreprise", cls: "btn-primary" }];
      menu.push({ act: "duplicate", label: "Dupliquer" });
    }
    // Une offre avec des candidatures ne se supprime pas : on archive, l'historique du candidat reste intact.
    if (!n) menu.push({ act: "del", label: "Supprimer", cls: "is-danger" });
    return { primary, menu };
  }

  /** assertCanPublish refuse toute publication tant que l'entreprise n'est pas vérifiée : proposer l'action qui débloque. */
  function peutPublier() {
    const o = RC.getOrg && RC.getOrg();
    return !o || o.verified !== false;
  }

  /** Carte cliquable (détail dans un panneau) et, dessous, les actions de gestion. */
  function managed(o, n) {
    const state = stateOf(o);
    const wrap = document.createElement("div");
    wrap.className = "offer-managed is-" + state.key;
    wrap.dataset.id = o.id;
    wrap.dataset.focus = "offer:" + o.id;
    wrap.appendChild(HireLinkOffer.card(o, {
      href: (x) => "recruiter-offers.html?offer=" + encodeURIComponent(x.id),
      onOpen: (x) => openDetail(x, n),
      statusChip: stateLabel(o, n),
    }));
    const bar = document.createElement("div");
    bar.className = "offer-managed-actions";
    const apps = document.createElement("a");
    apps.className = "btn btn-soft btn-sm";
    apps.href = "recruiter-pipeline.html?offer=" + encodeURIComponent(o.id);
    apps.textContent = "Candidatures";
    const edit = document.createElement("button");
    edit.type = "button";
    edit.className = "btn btn-ghost btn-sm";
    edit.dataset.act = "edit";
    edit.textContent = "Modifier";
    const acts = actionsFor(o, n);
    const buttons = acts.primary.map((a) =>
      '<button type="button" class="btn ' + (a.cls || "btn-ghost") + ' btn-sm" data-act="' + a.act + '">' + esc(a.label) + "</button>");
    const more = document.createElement("details");
    more.className = "rc-more";
    more.innerHTML = '<summary class="btn btn-ghost btn-sm" aria-label="Plus d’actions">⋯</summary><div class="rc-more-menu">' +
      acts.menu.map((a) => '<button type="button" data-act="' + a.act + '"' + (a.cls ? ' class="' + a.cls + '"' : "") + ">" + esc(a.label) + "</button>").join("") +
      "</div>";
    bar.append(apps, edit);
    buttons.forEach((html) => bar.insertAdjacentHTML("beforeend", html));
    bar.appendChild(more);
    wrap.appendChild(bar);
    return wrap;
  }

  function openDetail(o, n) {
    const body = document.createElement("div");
    body.className = "offer-detail-panel";
    const where = document.createElement("p");
    where.className = "muted";
    const place = HireLinkOffer.placeWithCountry(o);
    where.textContent = [(HireLinkPlaces.flagOf(place) + " " + place).trim(), o.contractType, stateLabel(o, n)].filter(Boolean).join(" · ");
    const text = document.createElement("div");
    text.className = "split-copy";
    text.textContent = o.description;
    const skillsTitle = document.createElement("h3");
    skillsTitle.className = "detail-sub";
    skillsTitle.textContent = "Compétences demandées";
    const boxes = document.createElement("div");
    HireLinkSkills.renderBoxes(boxes, o.requiredSkills, { empty: "Aucune compétence précisée." });
    body.append(where, HireLinkOffer.facts(o), text, skillsTitle, boxes);
    HireLink.openDrawer({ title: o.title, description: o.companyName || "", wide: true, hideCancel: true, confirmLabel: "Fermer",
      bodyHtml: '<div id="offer-detail-slot"></div>',
      onReady: (api) => api.root.querySelector("#offer-detail-slot").appendChild(body) });
  }

  function offerById(id) { return offers.find((o) => String(o.id) === String(id)); }

  /** Chaque geste de gestion a son adresse : rien ne repasse par le formulaire complet de l'offre. */
  const LIFECYCLE = {
    publish: { path: "/publish", done: "Offre publiée", detail: "Les candidats peuvent désormais postuler." },
    unpublish: { path: "/unpublish", done: "Offre dépubliée", detail: "Elle reste dans votre espace, sans être visible." },
    close: { path: "/close", done: "Candidatures clôturées", detail: "Les dossiers reçus sont conservés." },
    reopen: { path: "/reopen", done: "Candidatures rouvertes", detail: "L’offre accepte de nouveau des dossiers." },
    archive: { path: "/archive", done: "Offre archivée", detail: "Retrouvez-la dans le filtre « Archivées »." },
    unarchive: { path: "/unarchive", done: "Offre restaurée", detail: "Elle revient dans votre espace de travail." },
  };

  function confirmFor(act, o, n) {
    if (act === "close") return {
      title: "Clôturer cette offre ?",
      description: "« " + o.title + " » n’acceptera plus de candidature" +
        (n ? " ; vos " + RC.plural(n, "dossier") + " en cours restent accessibles." : "."),
      confirmLabel: "Clôturer", cancelLabel: "Garder ouverte",
    };
    if (act === "archive") return {
      title: "Archiver cette offre ?",
      description: "Elle quitte votre espace de travail et la vitrine des candidats. Rien n’est supprimé.",
      confirmLabel: "Archiver", cancelLabel: "Ne pas archiver",
    };
    if (act === "del") return {
      title: "Supprimer cette offre ?",
      description: "« " + o.title + " » n’a aucune candidature : la suppression est définitive.",
      confirmLabel: "Supprimer", cancelLabel: "Annuler", danger: true,
    };
    return null;
  }

  async function runLifecycle(act, o, btn) {
    const n = counts()[o.id] || 0;
    const ask = confirmFor(act, o, n);
    if (ask) { const ok = await Mascotte.confirm(ask); if (!ok) return; }
    RC.busy(btn, true);
    try {
      const next = await HireLinkAPI.api("/offers/" + o.id + LIFECYCLE[act].path, { method: "POST" });
      if (act === "publish" && !next.published) {
        RC.toast("Publication refusée", next.quarantineReason || "L’offre doit être complétée avant publication.", "warn");
      } else if (act === "publish") {
        if (!Mascotte.celebrate({ cle: "premiere-offre", title: "Votre première offre est en ligne !", description: LIFECYCLE[act].detail }))
          RC.toast("Offre", LIFECYCLE[act].done, "ok");
      } else {
        RC.toast("Offre", LIFECYCLE[act].done + " — " + LIFECYCLE[act].detail, "ok");
      }
      load(true);
    } catch (err) {
      RC.toast("Action impossible", err.message, "danger");
      RC.busy(btn, false);
    }
  }

  async function duplicate(o, btn) {
    RC.busy(btn, true);
    try {
      const copy = await HireLinkAPI.api("/offers/" + o.id + "/duplicate", { method: "POST" });
      RC.toast("Offre", "Copie créée en brouillon — complétez-la puis publiez-la.", "ok");
      load(true);
      HireLinkOfferWizard.edit(copy, () => load(true));
    } catch (err) {
      RC.toast("Duplication impossible", err.message, "danger");
      RC.busy(btn, false);
    }
  }

  async function extendDeadline(o) {
    let chosen = 15;
    const base = o.deadline && new Date(o.deadline) > new Date() ? o.deadline : null;
    await HireLink.openModal({
      title: "Prolonger la date limite",
      description: base
        ? "Date limite actuelle : " + HireLinkOffer.dateFr(base) + ". Le report s’ajoute à cette date."
        : "Aucune date limite en cours : la nouvelle échéance part d’aujourd’hui.",
      bodyHtml: '<label class="rc-muted" style="font-size: var(--text-sm)">Prolonger de<select id="ov-days" class="rc-select" style="width:100%;margin-top:.35rem">' +
        '<option value="7">7 jours</option><option value="15" selected>15 jours</option><option value="30">30 jours</option><option value="60">60 jours</option>' +
        "</select></label>",
      confirmLabel: "Prolonger",
      onReady: (api) => {
        const sel = api.root.querySelector("#ov-days");
        if (sel) sel.addEventListener("change", () => { chosen = Number(sel.value); });
      },
      onConfirm: async () => {
        const next = await HireLinkAPI.api("/offers/" + o.id + "/extend", { method: "POST", body: { days: chosen } });
        RC.toast("Offre", "Date limite repoussée au " + HireLinkOffer.dateFr(next.deadline) + ".", "ok");
        load(true);
      },
    });
  }

  function showSuspension(o) {
    HireLink.openModal({
      title: "Offre suspendue",
      description: o.quarantineReason
        ? o.quarantineReason
        : "Cette offre a été suspendue après plusieurs signalements de candidats.",
      hideCancel: true, confirmLabel: "Compris",
    });
  }

  async function remove(o) {
    const ask = confirmFor("del", o, 0);
    const ok = await Mascotte.confirm(ask);
    if (!ok) return;
    try {
      await HireLinkAPI.api("/offers/" + o.id, { method: "DELETE" });
      RC.toast("Offre", "Offre supprimée", "ok");
      load(true);
    } catch (err) {
      RC.toast("Suppression impossible", err.message, "danger");
    }
  }


  function onGridClick(e) {
    const btn = e.target.closest("button[data-act]");
    if (!btn) return;
    const o = offerById(btn.closest(".offer-managed").dataset.id);
    if (!o) return;
    const act = btn.dataset.act;
    if (act === "edit") HireLinkOfferWizard.edit(o, () => load(true));
    else if (act === "del") remove(o);
    else if (act === "duplicate") duplicate(o, btn);
    else if (act === "extend") extendDeadline(o);
    else if (act === "why") showSuspension(o);
    else if (act === "verify") location.href = "recruiter-team.html?tab=settings";
    else if (LIFECYCLE[act]) runLifecycle(act, o, btn);
  }
  $("offers-grid").addEventListener("click", onGridClick);
  $("offer-filter").addEventListener("click", (e) => {
    const b = e.target.closest("button[data-ov]");
    if (!b) return;
    view = b.dataset.ov;
    paint();
  });
  $("btn-new-offer").addEventListener("click", () => HireLinkOfferWizard.start(() => load(true)));

  function skeletonRows() {
    $("offers-grid").innerHTML = '<div class="rc-skel-list"><span class="rc-skel" style="height:140px"></span><span class="rc-skel" style="height:140px"></span><span class="rc-skel" style="height:140px"></span></div>';
  }

  function load(force) {
    if (force) RC.data.invalidate();
    if (RC.isHM()) return;
    if (!loaded) skeletonRows();
    RC.data.offers((o) => { offers = o || []; const first = !loaded; loaded = true; paint(); paintPace(); if (first) document.dispatchEvent(new Event("rc:offers-ready")); }, (err) => {
      RC.errorState($("offers-grid"), err.message, () => load(true));
    });
    RC.data.pipeline((a) => { apps = a || []; paint(); }, () => {});
  }

  function restrictIfHM() {
    if (!RC.isHM()) return;
    const c = document.getElementById("rc-content");
    RC.empty(c, { title: "Accès réservé", text: "La gestion des offres est réservée au créateur de l’espace et aux recruteurs de l’organisation.", action: { href: "recruiter-pipeline.html", label: "Ouvrir le pipeline" } });
  }
  document.addEventListener("rc:org", () => { restrictIfHM(); if (!RC.isHM() && loaded) paint(); });
  if (RC.isHM()) { restrictIfHM(); return; }
  load();
  HireLinkPlaces.ready().then(() => paint());
  const wanted = new URLSearchParams(location.search).get("offer");
  if (wanted) document.addEventListener("rc:offers-ready", () => { const o = offerById(wanted); if (o) openDetail(o, counts()[o.id] || 0); }, { once: true });
  if (location.hash === "#new") {
    history.replaceState(null, "", location.pathname);
    setTimeout(() => HireLinkOfferWizard.start(() => load(true)), 60);
  }
})();
document.addEventListener("click", (e) => {
  document.querySelectorAll("details.rc-more[open]").forEach((d) => { if (!d.contains(e.target) || e.target.closest("[data-act]")) d.open = false; });
});
