(function () {
  const RC = HireLinkApp;
  const session = RC.mount({});
  if (!session) return;

  const $ = (id) => document.getElementById(id);
  const esc = RC.esc;
  const CLOSED = ["REJECTED", "WITHDRAWN"];

  function profileChecks(me) {
    const has = (v) => !!(v && String(v).trim());
    return [
      [has(me && me.cvFileName), "Déposez votre CV", "candidate-settings.html#cv"],
      [has(me && me.cvSections), "Confirmez les sections de votre CV", "candidate-profile.html"],
      [has(me && me.skills), "Renseignez vos compétences", "candidate-profile.html"],
      [has(me && me.soughtRoles), "Indiquez le métier recherché", "candidate-profile.html"],
    ];
  }

  function statusCls(st) {
    return st === "INTERVIEW" ? "badge-ok" : st === "REJECTED" || st === "WITHDRAWN" ? "badge-danger" : st === "SHORTLISTED" ? "badge-rose" : "badge-info";
  }

  function render(me, apps, offers) {
    const sent = {};
    apps.forEach((a) => { if (a.status !== "WITHDRAWN") sent[a.jobOfferId] = true; });
    const active = apps.filter((a) => CLOSED.indexOf(a.status) < 0);
    const interviews = apps.filter((a) => a.status === "INTERVIEW");
    const reco = offers.filter((o) => !sent[o.id]).sort((a, b) => (b.matchScore || 0) - (a.matchScore || 0)).slice(0, 4);
    const checks = profileChecks(me);
    const missing = checks.filter((c) => !c[0]);
    const pct = Math.round(((checks.length - missing.length) / checks.length) * 100);

    $("hello").textContent = "Bonjour " + (String((me && me.fullName) || session.fullName || "").trim().split(/\s+/)[0] || "");
    $("hello-line").textContent = interviews.length
      ? RC.plural(interviews.length, "entretien") + " à préparer."
      : active.length
        ? RC.plural(active.length, "candidature") + " en cours. Vous serez notifié à chaque changement d’étape."
        : "Complétez votre profil puis postulez à une première offre.";
    $("st-active").textContent = active.length;
    $("st-interviews").textContent = interviews.length;
    $("st-offers").textContent = reco.length;

    const banners = [];
    interviews.filter((a) => a.interviewAt).sort((a, b) => new Date(a.interviewAt) - new Date(b.interviewAt)).slice(0, 1).forEach((a) => {
      banners.push('<div class="rc-banner rc-banner--ok"><div><strong>Entretien : ' + esc(a.jobTitle) + "</strong><span>" + esc(RC.fmtDateTime(a.interviewAt)) + " · " +
        esc(RC.INTERVIEW_TYPE_FR[a.interviewType] || "") + '</span></div><a class="btn btn-primary btn-sm" href="candidate-applications.html?id=' + a.id + '">Voir les détails</a></div>');
    });
    if (missing.length) {
      banners.push('<div class="rc-banner"><div><strong>Profil complété à ' + pct + " %</strong><span>" + esc(missing[0][1]) +
        ". Le score de compatibilité en dépend.</span></div><a class=\"btn btn-primary btn-sm\" href=\"" + missing[0][2] + '">Compléter</a></div>');
    }
    $("dash-banners").innerHTML = banners.join("");

    if (reco.length) $("reco-list").innerHTML = reco.map((o) => {
          const place = (o.location || "").split(/[·,]/)[0].trim();
          const score = o.matchScore != null ? '<span class="badge ' + (o.matchScore >= 60 ? "badge-ok" : "badge-info") + '">' + Math.round(o.matchScore) + " %</span>" : "";
          return '<div class="rc-item"><div class="rc-item-main"><strong>' + esc(o.title) + "</strong><span>" + esc(o.companyName || "Entreprise") + (place ? " · " + esc(place) : "") +
            (o.contractType ? " · " + esc(o.contractType) : "") + "</span></div>" + score + '<a class="btn btn-primary btn-sm" href="candidate.html?apply=' + encodeURIComponent(o.id) + '">Postuler</a></div>';
        }).join("");
    else RC.empty($("reco-list"), { title: "Aucune nouvelle offre", text: "Revenez bientôt : de nouvelles offres sont publiées chaque semaine." });

    if (apps.length) $("recent-list").innerHTML = apps.slice(0, 5).map((a) =>
          '<a class="rc-item" href="candidate-applications.html?id=' + a.id + '"><div class="rc-item-main"><strong>' + esc(a.jobTitle || "Candidature") + "</strong><span>" +
          (a.status === "INTERVIEW" && a.interviewAt ? "Entretien le " + esc(RC.fmtDateTime(a.interviewAt)) : "Envoyée " + esc(RC.ago(a.submittedAt))) +
          '</span></div><span class="badge ' + statusCls(a.status) + '">' + esc(RC.STATUS_FR[a.status] || a.status) + "</span></a>").join("");
    else RC.empty($("recent-list"), { title: "Aucune candidature", text: "Postulez à une offre pour suivre ici chaque étape.", action: { href: "candidate.html", label: "Parcourir les offres" } });
  }

  RC.skeleton($("reco-list"), 3);
  RC.skeleton($("recent-list"), 3);
  Promise.all([
    HireLinkAPI.api("/me").catch(() => null),
    HireLinkAPI.api("/applications/mine"),
    HireLinkAPI.api("/offers/public").catch(() => []),
  ]).then((p) => render(p[0], p[1] || [], p[2] || []))
    .catch((err) => RC.errorState($("recent-list"), err.message, () => location.reload()));
})();
