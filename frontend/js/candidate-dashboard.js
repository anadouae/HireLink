/** Tableau de bord candidat : l'essentiel, suivi des candidatures, prochaines étapes, profil et offres conseillées. */
(function (global) {
  const RC = HireLinkApp;
  const session = RC.mount({});
  if (!session) return;

  const $ = (id) => document.getElementById(id);
  const esc = RC.esc;
  const ico = (n) => (global.HLIcons ? HLIcons.svg(n) : "");
  const CLOSED = ["REJECTED", "WITHDRAWN"];
  const STEPS = [["SUBMITTED", "Envoyée"], ["SCREENING", "Étude"], ["SHORTLISTED", "Shortlist"], ["INTERVIEW", "Entretien"], ["OFFER", "Décision"]];
  const KIND_FR = { HR: "Entretien RH", TECHNICAL: "Entretien technique", MANAGER: "Avec le manager", CASE: "Étude de cas", TEAM: "Rencontre d’équipe", FINAL: "Entretien final" };
  const NEXT = {
    SUBMITTED: "Le recruteur va étudier votre dossier.",
    SCREENING: "Votre dossier est en cours d’étude.",
    SHORTLISTED: "Vous êtes présélectionné : un entretien peut vous être proposé.",
    INTERVIEW: "Préparez votre entretien.",
    OFFER: "Une proposition est en préparation.",
    HIRED: "Félicitations, vous êtes recruté !",
    REJECTED: "Candidature non retenue.",
    WITHDRAWN: "Vous avez retiré cette candidature.",
  };

  function profileChecks(me) {
    const has = (v) => !!(v && String(v).trim());
    return [
      [has(me && me.cvFileName), "Déposer votre CV", "candidate-settings.html#cv"],
      [has(me && me.cvSections), "Confirmer les sections de votre CV", "candidate-profile.html"],
      [has(me && me.skills), "Renseigner vos compétences", "candidate-profile.html"],
      [has(me && me.soughtRoles), "Indiquer le métier recherché", "candidate-profile.html"],
    ];
  }

  function tile(o) {
    const zero = !o.count;
    return '<a class="db-action' + (zero ? " is-zero" : "") + (o.tone ? " is-" + o.tone : "") + '" href="' + o.href + '"><span class="db-action-ico" aria-hidden="true">' + ico(o.icon) +
      '</span><span class="db-action-txt"><b>' + (o.big != null ? o.big : zero ? "—" : o.count) + "</b><strong>" + esc(o.title) + "</strong><small>" + esc(zero ? o.calm : o.sub) +
      '</small></span><span class="db-go" aria-hidden="true">' + ico("right") + "</span></a>";
  }

  function tracker(a) {
    const closed = CLOSED.includes(a.status);
    let idx = STEPS.findIndex((s) => s[0] === a.status);
    if (a.status === "HIRED") idx = STEPS.length;
    if (closed) idx = -1;
    const dots = STEPS.map((s, i) => '<li class="' + (i < idx ? "is-done" : i === idx ? "is-now" : "") + '"><i></i><span>' + s[1] + "</span></li>").join("");
    return '<a class="db-trk' + (closed ? " is-closed" : "") + '" href="candidate-applications.html?id=' + a.id + '">' +
      '<div class="db-trk-head"><strong>' + esc(a.jobTitle || "Candidature") + '</strong><span class="rc-pill' + (closed ? " rc-pill--danger" : a.status === "INTERVIEW" || a.status === "HIRED" ? " rc-pill--ok" : "") + '">' +
      esc(RC.STATUS_FR[a.status] || a.status) + "</span></div>" +
      (closed ? "" : '<ol class="db-steps">' + dots + "</ol>") +
      '<small class="db-trk-next">' + esc(NEXT[a.status] || "") + " · envoyée " + esc(RC.ago(a.submittedAt)) + "</small></a>";
  }

  function render(me, apps, offers) {
    const sent = {};
    apps.forEach((a) => { if (a.status !== "WITHDRAWN") sent[a.jobOfferId] = true; });
    const active = apps.filter((a) => !CLOSED.includes(a.status) && a.status !== "HIRED");
    const now = Date.now();
    const upcoming = apps.filter((a) => a.status === "INTERVIEW" && a.interviewAt && a.interviewStatus !== "CANCELLED" && new Date(a.interviewAt).getTime() > now - 3600e3)
      .sort((a, b) => new Date(a.interviewAt) - new Date(b.interviewAt));
    const reco = offers.filter((o) => !sent[o.id]).sort((a, b) => (b.matchScore || 0) - (a.matchScore || 0)).slice(0, 4);
    const checks = profileChecks(me);
    const missing = checks.filter((c) => !c[0]);
    const pct = Math.round(((checks.length - missing.length) / checks.length) * 100);
    const next = upcoming[0];

    $("hello").textContent = "Bonjour " + (String((me && me.fullName) || session.fullName || "").trim().split(/\s+/)[0] || "");
    $("hello-line").textContent = next
      ? "Prochain entretien " + RC.fmtDateTime(next.interviewAt) + " · " + (next.jobTitle || "")
      : active.length ? RC.plural(active.length, "candidature") + " en cours. Vous êtes notifié à chaque étape." : "Complétez votre profil puis postulez à une première offre.";

    $("db-actions").innerHTML = [
      { icon: "calendar", title: "Entretiens à venir", count: upcoming.length, tone: "info", sub: next ? "Le " + RC.fmtDateTime(next.interviewAt) : "",
        calm: "Aucun entretien prévu", href: next ? "candidate-applications.html?id=" + next.id : "candidate-applications.html?status=INTERVIEW" },
      { icon: "hourglass", title: "Réponses attendues", count: active.length, sub: "Dossiers en cours d’étude", calm: "Aucune candidature en cours", href: "candidate-applications.html?status=active" },
      { icon: "user", title: "Profil complété", big: pct + " %", count: missing.length, tone: "warn", sub: missing[0] ? missing[0][1] : "", calm: "Profil complet, bravo", href: missing[0] ? missing[0][2] : "candidate-profile.html" },
      { icon: "target", title: "Offres pour vous", count: reco.length, sub: "Pas encore postulées", calm: "Revenez bientôt", href: "candidate.html" },
    ].map(tile).join("");

    // Prochaines étapes
    const steps = upcoming.slice(0, 3).map((a) => {
      const d = new Date(a.interviewAt);
      const t = RC.interviewTypeUi(a.interviewType);
      const soon = d.getTime() - now < 24 * 3600e3;
      return '<a class="db-ev ' + t.cls + (soon ? " is-soon" : "") + '" href="candidate-applications.html?id=' + a.id + '"><span class="db-ev-ico" aria-hidden="true">' + ico(t.icon) +
        '</span><span class="db-ev-main"><strong>' + esc(KIND_FR[a.interviewKind] || "Entretien") + "</strong><span>" + [t.label, esc(a.jobTitle || "")].filter((v) => v && v !== (KIND_FR[a.interviewKind] || "Entretien")).join(" · ") +
        '</span></span><span class="db-ev-when"><b>' + d.toLocaleTimeString("fr-FR", { hour: "2-digit", minute: "2-digit" }) + "</b><small>" + d.toLocaleDateString("fr-FR", { weekday: "short", day: "numeric", month: "short" }) + "</small></span></a>";
    });
    $("next-steps").innerHTML = steps.length ? steps.join("")
      : '<div class="db-empty">' + ico("calendar") + "<strong>Rien de prévu pour l’instant</strong><span>Vos entretiens apparaîtront ici dès qu’un recruteur vous en propose un.</span></div>";

    $("profile-pct").textContent = pct === 100 ? "Profil complet" : "Complété à " + pct + " % · le score de compatibilité en dépend";
    $("profile-bar").style.setProperty("--w", pct + "%");
    $("profile-checks").innerHTML = checks.map((c) => "<li" + (c[0] ? ' class="is-ok"' : "") + ">" + ico(c[0] ? "check" : "dot") + (c[0] ? "<span>" + esc(c[1]) + "</span>" : '<a href="' + c[2] + '">' + esc(c[1]) + "</a>") + "</li>").join("");

    const ordered = apps.slice().sort((a, b) => (CLOSED.includes(a.status) - CLOSED.includes(b.status)) || new Date(b.updatedAt || b.submittedAt) - new Date(a.updatedAt || a.submittedAt));
    $("recent-list").innerHTML = ordered.length ? ordered.slice(0, 5).map(tracker).join("")
      : '<div class="db-empty">' + ico("briefcase") + '<strong>Aucune candidature</strong><span>Postulez à une offre pour suivre ici chaque étape.</span><a class="btn btn-soft btn-sm" href="candidate.html">Parcourir les offres</a></div>';

    if (reco.length) $("reco-list").innerHTML = reco.map((o) => {
      const place = HireLinkPlaces.placeLabel(o.location, o.companyCity);
      const score = o.matchScore != null ? '<span class="badge ' + (o.matchScore >= 60 ? "badge-ok" : "badge-info") + '">' + Math.round(o.matchScore) + " %</span>" : "";
      return '<div class="rc-item"><div class="rc-item-main"><strong>' + esc(o.title) + "</strong><span>" + '<a class="db-co-link" href="candidate-companies.html?name=' + encodeURIComponent(o.companyName || "") + '">' + esc(o.companyName || "Entreprise") + "</a>" + (place ? " · " + esc(place) : "") +
        (o.contractType ? " · " + esc(o.contractType) : "") + "</span></div>" + score + '<a class="btn btn-primary btn-sm" href="candidate.html?apply=' + encodeURIComponent(o.id) + '">Postuler</a></div>';
    }).join("");
    else $("reco-list").innerHTML = '<div class="db-empty">' + ico("target") + "<strong>Aucune nouvelle offre</strong><span>De nouvelles offres sont publiées chaque semaine.</span></div>";
  }

  RC.skeleton($("reco-list"), 3);
  RC.skeleton($("recent-list"), 3);
  Promise.all([
    HireLinkAPI.api("/me").catch(() => null),
    HireLinkAPI.api("/applications/mine"),
    HireLinkAPI.api("/offers/public").catch(() => []),
  ]).then((p) => render(p[0], p[1] || [], p[2] || []))
    .catch((err) => RC.errorState($("recent-list"), err.message, () => location.reload()));
})(window);
