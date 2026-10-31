/** Entretiens : agenda (à venir / à replanifier / passés) avec report, annulation et export calendrier. */
(function () {
  const RC = HireLinkApp;
  const S = HireLinkSearch;
  const session = RC.mount({ active: "interviews", title: "Entretiens", search: { placeholder: "Candidat, offre, lieu…", onQuery: () => paint() } });
  if (!session) return;
  const $ = (id) => document.getElementById(id);
  let apps = [];
  let tab = "upcoming";
  let loaded = false;

  const cancelled = (a) => a.interviewStatus === "CANCELLED";

  function groups() {
    const limit = Date.now() - 3600e3;
    const withInterview = apps.filter((a) => a.interviewAt);
    const live = withInterview.filter((a) => a.status === "INTERVIEW" && !cancelled(a));
    const byDate = (x, y) => new Date(x.interviewAt) - new Date(y.interviewAt);
    return {
      upcoming: live.filter((a) => new Date(a.interviewAt).getTime() >= limit).sort(byDate),
      todo: withInterview.filter((a) => a.status === "INTERVIEW" && cancelled(a)).sort(byDate),
      past: withInterview.filter((a) => !cancelled(a) && (a.status !== "INTERVIEW" || new Date(a.interviewAt).getTime() < limit)).sort((x, y) => byDate(y, x)),
    };
  }

  function item(a) {
    const wrap = document.createElement("article");
    wrap.className = "iv-item";
    const head = document.createElement("header");
    const who = document.createElement("div");
    const name = document.createElement("strong");
    name.textContent = a.candidateName || "Candidat";
    const job = document.createElement("span");
    job.textContent = a.jobTitle || "";
    who.append(name, job);
    const dossier = document.createElement("a");
    dossier.className = "btn btn-ghost btn-sm";
    dossier.href = "recruiter-pipeline.html?app=" + a.id;
    dossier.textContent = "Ouvrir le dossier";
    head.append(who, dossier);
    wrap.append(head, HireLinkInterview.card(a, { role: tab === "past" ? "readonly" : "recruiter", onChange: load }));
    return wrap;
  }

  function paint() {
    if (!loaded) return;
    const g = groups();
    $("iv-tabs").querySelectorAll("button").forEach((b) => {
      b.classList.toggle("is-on", b.dataset.t === tab);
      let em = b.querySelector("em");
      if (!em) { em = document.createElement("em"); b.appendChild(em); }
      em.textContent = g[b.dataset.t].length;
    });
    const q = (($("rc-search") || {}).value || "").trim();
    const list = S.rank(g[tab], q, [
      { get: (a) => a.candidateName, weight: 3 }, { get: (a) => a.jobTitle, weight: 2 },
      { get: (a) => (a.interviewLocation || "") + " " + (a.interviewParticipants || ""), weight: 1 },
    ]);
    const root = $("iv-list");
    root.replaceChildren();
    if (!list.length) {
      if (q) { S.emptyState(root, q, { onClear: () => { $("rc-search").value = ""; paint(); } }); return; }
      RC.empty(root, tab === "upcoming"
        ? { title: "Aucun entretien à venir", text: "Passez un dossier de Shortlist à Entretien dans le pipeline pour planifier un échange.", action: { href: "recruiter-pipeline.html", label: "Ouvrir le pipeline" } }
        : tab === "todo" ? { title: "Aucun entretien à replanifier", text: "Les entretiens annulés dont le dossier est toujours à l’étape Entretien apparaissent ici." }
        : { title: "Aucun entretien passé", text: "L’historique des entretiens s’affichera ici." });
      return;
    }
    list.forEach((a) => root.appendChild(item(a)));
    S.mark(root, q);
  }

  $("iv-tabs").addEventListener("click", (e) => { const b = e.target.closest("button[data-t]"); if (b) { tab = b.dataset.t; paint(); } });

  function load() {
    if (!loaded) RC.skeleton($("iv-list"), 3, 120);
    RC.data.invalidate();
    RC.data.pipeline((a) => { apps = a || []; loaded = true; paint(); }, (err) => RC.errorState($("iv-list"), err.message, load));
  }
  load();
  document.addEventListener("hirelink:push", load);
})();
