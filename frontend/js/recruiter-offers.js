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
    if (!document.getElementById("offers-body")) return; // page remplacée (Hiring Manager)
    if (!loaded) return;
    const c = counts();
    const q = (($("rc-search") || {}).value || "").trim().toLowerCase();
    const searched = q
      ? offers.filter((o) => ((o.title || "") + " " + (o.location || "") + " " + (o.requiredSkills || "") + " " + (o.contractType || "")).toLowerCase().includes(q))
      : offers.slice();
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
    const body = $("offers-body");
    if (!list.length) {
      body.innerHTML = '<tr><td colspan="5"></td></tr>';
      RC.empty(body.firstChild.firstChild, offers.length
        ? { icon: "search", title: "Aucune offre pour ce filtre", text: "Modifiez la recherche ou le filtre pour retrouver vos offres." }
        : { icon: "briefcase", title: "Aucune offre pour l’instant", text: "Créez votre première offre : titre, compétences, lieu et délai de première réponse.", action: null });
      return;
    }
    body.innerHTML = list.map((o) =>
      '<tr class="rc-fade" data-id="' + o.id + '"><td><span class="rc-cell-main">' + esc(o.title) + '</span><span class="rc-cell-sub">' +
      esc([o.location, o.contractType].filter(Boolean).join(" · ") || "—") + (o.recruiterName ? " · " + esc(o.recruiterName) : "") + "</span></td>" +
      "<td>" + esc(o.responseSlaDays) + " j</td><td>" + (c[o.id] || 0) + "</td><td>" +
      (o.suspended ? '<span class="badge badge-danger">Suspendue</span>' : o.published ? '<span class="badge badge-ok">Publiée</span>' : '<span class="badge badge-warn">Brouillon</span>') +
      '</td><td><div class="rc-row-actions">' +
      '<a class="btn btn-soft btn-sm" href="recruiter-pipeline.html?offer=' + encodeURIComponent(o.id) + '">Candidatures</a>' +
      '<button type="button" class="btn btn-ghost btn-sm" data-act="edit">Modifier</button>' +
      '<details class="rc-more"><summary class="btn btn-ghost btn-sm" aria-label="Plus d’actions">⋯</summary><div class="rc-more-menu">' +
      '<button type="button" data-act="score">Critères de scorecard</button>' +
      '<button type="button" data-act="toggle">' + (o.published ? "Dépublier" : "Publier") + "</button>" +
      '<button type="button" data-act="del" class="is-danger">Supprimer</button></div></details>' +
      "</div></td></tr>").join("");
  }

  function offerById(id) { return offers.find((o) => String(o.id) === String(id)); }

  async function togglePublish(o, btn) {
    RC.busy(btn, true);
    try {
      await HireLinkAPI.api("/offers/" + o.id, {
        method: "PUT",
        body: { title: o.title, description: o.description, requiredSkills: o.requiredSkills, location: o.location, contractType: o.contractType, responseSlaDays: o.responseSlaDays, published: !o.published },
      });
      RC.toast("Offre", o.published ? "Offre dépubliée" : "Offre publiée", "ok");
      load(true);
    } catch (err) {
      RC.toast("Publication impossible", err.message, "danger");
      RC.busy(btn, false);
    }
  }

  async function remove(o) {
    const ok = await HireLink.openModal({
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

  function onTableClick(e) {
    const btn = e.target.closest("button[data-act]");
    if (!btn) return;
    const o = offerById(btn.closest("tr").dataset.id);
    if (!o) return;
    const act = btn.dataset.act;
    if (act === "edit") HireLinkOfferWizard.edit(o, () => load(true));
    else if (act === "toggle") togglePublish(o, btn);
    else if (act === "del") remove(o);
    else if (act === "score") editScorecard(o);
  }
  $("offers-body").addEventListener("click", onTableClick);
  $("offer-filter").addEventListener("click", (e) => {
    const b = e.target.closest("button[data-ov]");
    if (!b) return;
    view = b.dataset.ov;
    paint();
  });
  $("btn-new-offer").addEventListener("click", () => HireLinkOfferWizard.start(() => load(true)));

  function skeletonRows() {
    $("offers-body").innerHTML = '<tr><td colspan="5" style="padding:0"><div class="rc-skel-list"><span class="rc-skel" style="height:44px"></span><span class="rc-skel" style="height:44px"></span><span class="rc-skel" style="height:44px"></span></div></td></tr>';
  }

  function load(force) {
    if (force) RC.data.invalidate();
    if (RC.isHM()) return;
    if (!loaded) skeletonRows();
    RC.data.offers((o) => { offers = o || []; loaded = true; paint(); }, (err) => {
      $("offers-body").innerHTML = '<tr><td colspan="5"></td></tr>';
      RC.errorState($("offers-body").firstChild.firstChild, err.message, () => load(true));
    });
    RC.data.pipeline((a) => { apps = a || []; paint(); }, () => {});
  }

  function restrictIfHM() {
    if (!RC.isHM()) return;
    const c = document.getElementById("rc-content");
    RC.empty(c, { icon: "briefcase", title: "Accès réservé", text: "La gestion des offres est réservée aux Owners et Recruteurs de l’organisation.", action: { href: "recruiter-pipeline.html", label: "Ouvrir le pipeline" } });
  }
  document.addEventListener("rc:org", restrictIfHM);
  if (RC.isHM()) { restrictIfHM(); return; }
  load();
  if (location.hash === "#new") {
    history.replaceState(null, "", location.pathname);
    setTimeout(() => HireLinkOfferWizard.start(() => load(true)), 60);
  }
})();
document.addEventListener("click", (e) => {
  document.querySelectorAll("details.rc-more[open]").forEach((d) => { if (!d.contains(e.target) || e.target.closest("[data-act]")) d.open = false; });
});
