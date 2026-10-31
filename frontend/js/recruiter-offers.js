/** Offres : liste de l'organisation, filtres, publication, critères de scorecard, assistant de création. */
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

  function paint() {
    if (!$("offers-grid")) return; // page remplacée (Hiring Manager)
    if (!loaded) return;
    const c = counts();
    const q = (($("rc-search") || {}).value || "").trim();
    const searched = HireLinkSearch.rank(offers, q, [
      { get: (o) => o.title, weight: 3 }, { get: (o) => o.requiredSkills, weight: 2 },
      { get: (o) => o.location + " " + o.contractType, weight: 2 }, { get: (o) => o.description, weight: 1 },
    ]);
    const vc = {
      all: searched.length,
      published: searched.filter((o) => o.published).length,
      draft: searched.filter((o) => !o.published).length,
      with: searched.filter((o) => (c[o.id] || 0) > 0).length,
    };
    $("offer-filter").querySelectorAll("button").forEach((b) => {
      const key = b.dataset.ov;
      b.classList.toggle("is-on", key === view);
      let em = b.querySelector("em");
      if (!em) { em = document.createElement("em"); b.appendChild(em); }
      em.textContent = vc[key] || 0;
    });
    const list = searched.filter((o) =>
      view === "published" ? o.published : view === "draft" ? !o.published : view === "with" ? (c[o.id] || 0) > 0 : true);
    $("offers-summary").textContent = RC.plural(list.length, "offre");
    const grid = $("offers-grid");
    grid.replaceChildren();
    if (!list.length) {
      if (q) { HireLinkSearch.emptyState(grid, q, { onClear: () => { $("rc-search").value = ""; paint(); } }); return; }
      RC.empty(grid, offers.length
        ? { title: "Aucune offre pour ce filtre", text: "Modifiez le filtre pour retrouver vos offres." }
        : { title: "Aucune offre pour l’instant", text: "Créez votre première offre : poste, conditions, compétences et délai de première réponse.", action: null });
      return;
    }
    list.forEach((o) => grid.appendChild(managed(o, c[o.id] || 0)));
    HireLinkSearch.mark(grid, q);
  }

  function stateLabel(o, n) {
    const state = o.suspended ? "Suspendue" : o.published ? "Publiée" : "Brouillon";
    return state + " · " + RC.plural(n, "candidature");
  }

  /** Carte cliquable (détail dans un panneau) et, dessous, les actions de gestion. */
  function managed(o, n) {
    const wrap = document.createElement("div");
    wrap.className = "offer-managed" + (o.published ? "" : " is-draft");
    wrap.dataset.id = o.id;
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
    const more = document.createElement("details");
    more.className = "rc-more";
    more.innerHTML = '<summary class="btn btn-ghost btn-sm" aria-label="Plus d’actions">⋯</summary><div class="rc-more-menu">' +
      '<button type="button" data-act="score">Critères de scorecard</button>' +
      '<button type="button" data-act="toggle"></button>' +
      '<button type="button" data-act="del" class="is-danger">Supprimer</button></div>';
    more.querySelector('[data-act="toggle"]').textContent = o.published ? "Dépublier" : "Publier";
    bar.append(apps, edit, more);
    wrap.appendChild(bar);
    return wrap;
  }

  function openDetail(o, n) {
    const body = document.createElement("div");
    body.className = "offer-detail-panel";
    const where = document.createElement("p");
    where.className = "muted";
    const place = HireLinkOffer.placeOf(o);
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

  async function togglePublish(o, btn) {
    RC.busy(btn, true);
    try {
      await HireLinkAPI.api("/offers/" + o.id, {
        method: "PUT",
        body: HireLinkOffer.toRequest(o, { published: !o.published }),
      });
      const celebrated = !o.published && Mascotte.celebrate({ cle: "premiere-offre", title: "Votre première offre est en ligne !", description: "Les candidats peuvent désormais postuler." });
      if (!celebrated) RC.toast("Offre", o.published ? "Offre dépubliée" : "Offre publiée", "ok");
      load(true);
    } catch (err) {
      RC.toast("Publication impossible", err.message, "danger");
      RC.busy(btn, false);
    }
  }

  async function remove(o) {
    const ok = await Mascotte.confirm({
      title: "Supprimer cette offre ?",
      description: "« " + o.title + " » et ses candidatures seront définitivement supprimées.",
      confirmLabel: "Supprimer",
      danger: true,
    });
    if (!ok) return;
    try {
      await HireLinkAPI.api("/offers/" + o.id, { method: "DELETE" });
      RC.toast("Offre", "Offre supprimée", "ok");
      load(true);
    } catch (err) {
      RC.toast("Suppression impossible", err.message, "danger");
    }
  }

  async function editScorecard(o) {
    let criteria = [];
    try { criteria = await HireLinkAPI.api("/offers/" + o.id + "/scorecard-criteria"); } catch (err) { RC.toast("Scorecard", err.message, "danger"); return; }
    if (!criteria.length) criteria = [{ label: "Technique", weight: 3 }, { label: "Soft skills", weight: 2 }, { label: "Culture fit", weight: 1 }];
    const row = (c) =>
      '<div class="rc-row sc-row" style="margin-bottom:.5rem"><input class="rc-select sc-label" style="flex:1;min-width:140px" placeholder="Critère" value="' + esc(c.label || "") + '" />' +
      '<input class="rc-select sc-weight" type="number" min="1" max="5" style="width:74px" value="' + esc(c.weight || 1) + '" title="Poids" />' +
      '<label class="rc-muted" style="font-size:.82rem"><input type="checkbox" class="sc-veto"' + (c.veto ? " checked" : "") + ' /> Veto</label>' +
      '<button type="button" class="btn btn-ghost btn-sm sc-del" aria-label="Retirer">✕</button></div>';
    const res = await HireLink.openModal({
      title: "Critères de scorecard",
      description: o.title + " — chaque évaluateur note de 1 à 5 sur ces critères.",
      bodyHtml: '<div id="sc-rows">' + criteria.map(row).join("") + '</div><button type="button" class="btn btn-soft btn-sm" id="sc-add">+ Ajouter un critère</button>',
      confirmLabel: "Enregistrer",
      onReady: (api) => {
        const rows = api.root.querySelector("#sc-rows");
        api.root.addEventListener("click", (e) => {
          if (e.target.id === "sc-add") rows.insertAdjacentHTML("beforeend", row({ label: "", weight: 1 }));
          if (e.target.classList.contains("sc-del")) e.target.closest(".sc-row").remove();
        });
      },
    });
    if (!res) return;
    const items = Array.from(document.querySelectorAll("#sc-rows .sc-row")).map((r) => ({
      label: r.querySelector(".sc-label").value.trim(),
      weight: Math.max(1, Math.min(5, Number(r.querySelector(".sc-weight").value) || 1)),
      veto: r.querySelector(".sc-veto").checked,
    })).filter((i) => i.label);
    if (!items.length) { RC.toast("Scorecard", "Ajoutez au moins un critère.", "warn"); return; }
    try {
      await HireLinkAPI.api("/offers/" + o.id + "/scorecard-criteria", { method: "PUT", body: { criteria: items } });
      RC.toast("Scorecard", "Critères enregistrés", "ok");
    } catch (err) { RC.toast("Scorecard", err.message, "danger"); }
  }

  function onGridClick(e) {
    const btn = e.target.closest("button[data-act]");
    if (!btn) return;
    const o = offerById(btn.closest(".offer-managed").dataset.id);
    if (!o) return;
    const act = btn.dataset.act;
    if (act === "edit") HireLinkOfferWizard.edit(o, () => load(true));
    else if (act === "toggle") togglePublish(o, btn);
    else if (act === "del") remove(o);
    else if (act === "score") editScorecard(o);
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
    RC.data.offers((o) => { offers = o || []; const first = !loaded; loaded = true; paint(); if (first) document.dispatchEvent(new Event("rc:offers-ready")); }, (err) => {
      RC.errorState($("offers-grid"), err.message, () => load(true));
    });
    RC.data.pipeline((a) => { apps = a || []; paint(); }, () => {});
  }

  function restrictIfHM() {
    if (!RC.isHM()) return;
    const c = document.getElementById("rc-content");
    RC.empty(c, { title: "Accès réservé", text: "La gestion des offres est réservée aux Owners et Recruteurs de l’organisation.", action: { href: "recruiter-pipeline.html", label: "Ouvrir le pipeline" } });
  }
  document.addEventListener("rc:org", restrictIfHM);
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
