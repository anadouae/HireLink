/** Entretiens : agenda (à venir / à planifier / passés), détails et export .ics. */
(function () {
  const RC = HireLinkApp;
  const session = RC.mount({ active: "interviews", title: "Entretiens", search: { placeholder: "Candidat, offre, lieu…", onQuery: () => paint() } });
  if (!session) return;
  const $ = (id) => document.getElementById(id);
  const esc = RC.esc;
  let apps = [];
  let tab = "upcoming";
  let loaded = false;

  function ics(a) {
    const start = new Date(a.interviewAt);
    const end = new Date(start.getTime() + 3600e3);
    const f = (d) => d.toISOString().replace(/[-:]/g, "").replace(/\.\d{3}/, "");
    const text = ["BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//HireLink//FR", "BEGIN:VEVENT", "UID:hirelink-" + a.id + "@hirelink.ma", "DTSTAMP:" + f(new Date()), "DTSTART:" + f(start), "DTEND:" + f(end),
      "SUMMARY:Entretien — " + (a.candidateName || "") + " (" + (a.jobTitle || "") + ")", "LOCATION:" + (a.interviewLocation || ""), "DESCRIPTION:" + String(a.interviewNotes || "").replace(/\n/g, "\\n"), "END:VEVENT", "END:VCALENDAR"].join("\r\n");
    const link = document.createElement("a");
    link.href = URL.createObjectURL(new Blob([text], { type: "text/calendar" }));
    link.download = "entretien-" + String(a.candidateName || "candidat").replace(/\s+/g, "-") + ".ics";
    link.click();
    URL.revokeObjectURL(link.href);
  }

  function groups() {
    const now = Date.now() - 3600e3;
    const iv = apps.filter((a) => a.status === "INTERVIEW");
    return {
      upcoming: iv.filter((a) => a.interviewAt && new Date(a.interviewAt).getTime() >= now).sort((a, b) => new Date(a.interviewAt) - new Date(b.interviewAt)),
      todo: iv.filter((a) => !a.interviewAt),
      past: apps.filter((a) => a.interviewAt && (a.status !== "INTERVIEW" || new Date(a.interviewAt).getTime() < now)).sort((a, b) => new Date(b.interviewAt) - new Date(a.interviewAt)),
    };
  }

  function paint() {
    if (!loaded) return;
    const g = groups();
    const q = (($("rc-search") || {}).value || "").trim().toLowerCase();
    $("iv-tabs").querySelectorAll("button").forEach((b) => {
      b.classList.toggle("is-on", b.dataset.t === tab);
      let em = b.querySelector("em");
      if (!em) { em = document.createElement("em"); b.appendChild(em); }
      em.textContent = g[b.dataset.t].length;
    });
    let list = g[tab];
    if (q) list = list.filter((a) => ((a.candidateName || "") + " " + (a.jobTitle || "") + " " + (a.interviewLocation || "")).toLowerCase().includes(q));
    const root = $("iv-list");
    if (!list.length) {
      RC.empty(root, tab === "upcoming"
        ? { icon: "calendar", title: "Aucun entretien à venir", text: "Passez un dossier de Shortlist à Entretien dans le pipeline pour planifier un échange.", action: { href: "recruiter-pipeline.html", label: "Ouvrir le pipeline" } }
        : tab === "todo" ? { icon: "calendar", title: "Rien à planifier", text: "Tous les dossiers à l’étape Entretien ont une date." }
        : { icon: "calendar", title: "Aucun entretien passé", text: "L’historique des entretiens s’affichera ici." });
      return;
    }
    let lastDay = "";
    root.innerHTML = list.map((a) => {
      const d = a.interviewAt ? new Date(a.interviewAt) : null;
      const day = d ? d.toLocaleDateString("fr-FR", { weekday: "long", day: "numeric", month: "long" }) : "";
      const sep = d && day !== lastDay ? '<div class="rc-day">' + esc(day) + "</div>" : "";
      lastDay = day || lastDay;
      return sep + '<div class="rc-item"><div class="rc-datebox">' + (d ? "<b>" + d.getDate() + "</b><small>" + d.toLocaleDateString("fr-FR", { month: "short" }) + "</small>" : "<b>?</b><small>date</small>") + '</div><div class="rc-item-main"><strong>' + esc(a.candidateName) +
        "</strong><span>" + esc(a.jobTitle || "") + (d ? " · " + d.toLocaleTimeString("fr-FR", { hour: "2-digit", minute: "2-digit" }) : "") + " · " +
        esc(RC.INTERVIEW_TYPE_FR[a.interviewType] || a.interviewType || "Type à définir") + (a.interviewLocation ? " · " + esc(a.interviewLocation) : "") + "</span>" +
        (a.interviewNotes ? '<span style="white-space:normal">' + esc(a.interviewNotes) + "</span>" : "") + '</div><div class="rc-row-actions">' +
        (a.interviewAt ? '<button type="button" class="btn btn-ghost btn-sm" data-ics="' + a.id + '">Ajouter à l’agenda</button>' : "") +
        '<a class="btn btn-soft btn-sm" href="recruiter-pipeline.html?app=' + a.id + '">Dossier</a></div></div>';
    }).join("");
  }

  $("iv-list").addEventListener("click", (e) => {
    const b = e.target.closest("[data-ics]");
    if (b) ics(apps.find((a) => String(a.id) === b.dataset.ics));
  });
  $("iv-tabs").addEventListener("click", (e) => { const b = e.target.closest("button[data-t]"); if (b) { tab = b.dataset.t; paint(); } });

  function load() {
    if (!loaded) RC.skeleton($("iv-list"), 3, 62);
    RC.data.pipeline((a) => { apps = a || []; loaded = true; paint(); }, (err) => RC.errorState($("iv-list"), err.message, load));
  }
  load();
})();
