/** Candidats : vue à plat de tous les dossiers de l'organisation (filtres, tri, recherche). */
(function () {
  const RC = HireLinkApp;
  const session = RC.mount({
    active: "candidates",
    title: "Candidats",
    search: { placeholder: "Nom, e-mail, offre, compétence…", onQuery: () => paint() },
  });
  if (!session) return;
  const $ = (id) => document.getElementById(id);
  const esc = RC.esc;
  let apps = [];
  let view = "active";
  let loaded = false;

  const GROUPS = {
    active: (a) => RC.ACTIVE.includes(a.status),
    interview: (a) => a.status === "INTERVIEW",
    rejected: (a) => a.status === "REJECTED",
    withdrawn: (a) => a.status === "WITHDRAWN",
    all: () => true,
  };
  const TONE = { SUBMITTED: "", SCREENING: "warn", SHORTLISTED: "info", INTERVIEW: "ok", REJECTED: "danger", WITHDRAWN: "" };

  function offerOptions() {
    const m = new Map();
    apps.forEach((a) => { if (a.jobOfferId != null) m.set(a.jobOfferId, a.jobTitle || "Offre #" + a.jobOfferId); });
    const sel = $("cand-offer");
    const prev = sel.value || new URLSearchParams(location.search).get("offer") || "all";
    sel.innerHTML = '<option value="all">Toutes les offres</option>' +
      Array.from(m.entries()).sort((a, b) => String(a[1]).localeCompare(String(b[1]), "fr")).map(([id, t]) => '<option value="' + id + '">' + esc(t) + "</option>").join("");
    sel.value = m.has(Number(prev)) ? prev : "all";
  }

  function paint() {
    if (!loaded) return;
    const q = (($("rc-search") || {}).value || "").trim().toLowerCase();
    const oid = $("cand-offer").value;
    const scoped = apps.filter((a) => oid === "all" || String(a.jobOfferId) === oid);
    $("cand-chips").querySelectorAll("button").forEach((b) => {
      const k = b.dataset.v;
      b.classList.toggle("is-on", k === view);
      let em = b.querySelector("em");
      if (!em) { em = document.createElement("em"); b.appendChild(em); }
      em.textContent = scoped.filter(GROUPS[k]).length;
    });
    let list = scoped.filter(GROUPS[view]);
    if (q) list = list.filter((a) => ((a.candidateName || "") + " " + (a.candidateEmail || "") + " " + (a.jobTitle || "") + " " + (a.candidateSkills || "")).toLowerCase().includes(q));
    const sort = $("cand-sort").value;
    list.sort((a, b) => {
      if (sort === "match") return (b.matchScore || 0) - (a.matchScore || 0);
      if (sort === "sla") return (a.slaRemainingDays == null ? 999 : a.slaRemainingDays) - (b.slaRemainingDays == null ? 999 : b.slaRemainingDays);
      if (sort === "name") return String(a.candidateName).localeCompare(String(b.candidateName), "fr");
      return new Date(b.submittedAt || 0) - new Date(a.submittedAt || 0);
    });
    $("cand-summary").textContent = RC.plural(list.length, "candidature");
    const body = $("cand-body");
    if (!list.length) {
      body.innerHTML = '<tr><td colspan="7"></td></tr>';
      RC.empty(body.firstChild.firstChild, apps.length
        ? { icon: "search", title: "Aucun candidat ne correspond", text: "Changez de filtre ou de recherche." }
        : { icon: "users", title: "Aucune candidature reçue", text: "Les candidatures apparaîtront ici dès que des candidats postuleront à vos offres publiées." });
      return;
    }
    body.innerHTML = list.map((a) => {
      const sla = a.slaRemainingDays;
      const late = a.slaBreached || (sla != null && sla < 0);
      const open = RC.ACTIVE.includes(a.status);
      const slaCell = !open ? "—" : late ? '<span class="rc-pill rc-pill--danger">En retard</span>' : sla == null ? "—" : '<span class="rc-pill' + (sla <= 2 ? " rc-pill--warn" : "") + '">' + (sla === 0 ? "Dernier jour" : sla + " j") + "</span>";
      const m = a.matchScore != null ? Math.round(a.matchScore) : null;
      return '<tr class="rc-fade"><td><div class="rc-person"><div class="rc-avatar">' + esc(RC.initials(a.candidateName)) + '</div><div><span class="rc-cell-main">' + esc(a.candidateName) +
        '</span><span class="rc-cell-sub">' + esc(a.candidateEmail || "") + "</span></div></div></td><td>" + esc(a.jobTitle || "—") +
        '</td><td><span class="rc-pill' + (TONE[a.status] ? " rc-pill--" + TONE[a.status] : "") + '">' + esc(RC.STATUS_FR[a.status] || a.status) + "</span></td><td>" +
        (m != null ? '<span class="badge ' + (m >= 80 ? "badge-ok" : m >= 55 ? "badge-info" : "badge-rose") + '">' + m + " %</span>" : "—") +
        "</td><td>" + slaCell + "</td><td>" + RC.fmtDate(a.submittedAt) + '</td><td><div class="rc-row-actions"><a class="btn btn-soft btn-sm" href="recruiter-pipeline.html?app=' + a.id + '">Ouvrir le dossier</a></div></td></tr>';
    }).join("");
  }

  function load(force) {
    if (force) RC.data.invalidate();
    if (!loaded) $("cand-body").innerHTML = '<tr><td colspan="7" style="padding:0"><div class="rc-skel-list"><span class="rc-skel" style="height:46px"></span><span class="rc-skel" style="height:46px"></span><span class="rc-skel" style="height:46px"></span><span class="rc-skel" style="height:46px"></span></div></td></tr>';
    RC.data.pipeline((a) => { apps = a || []; loaded = true; offerOptions(); paint(); }, (err) => {
      $("cand-body").innerHTML = '<tr><td colspan="7"></td></tr>';
      RC.errorState($("cand-body").firstChild.firstChild, err.message, () => load(true));
    });
  }

  $("cand-chips").addEventListener("click", (e) => { const b = e.target.closest("button[data-v]"); if (b) { view = b.dataset.v; paint(); } });
  $("cand-offer").addEventListener("change", paint);
  $("cand-sort").addEventListener("change", paint);
  load();
})();
