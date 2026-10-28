/** Tableau de bord recruteur — KPI, inbox prioritaire, répartition, entretiens à venir. */
(function () {
  const RC = HireLinkApp;
  const session = RC.mount({
    active: "dashboard",
    title: "Tableau de bord",
    search: { placeholder: "Filtrer l’inbox : candidat, offre, statut…", onQuery: () => renderInbox() },
  });
  if (!session) return;
  const $ = (id) => document.getElementById(id);
  const esc = RC.esc;
  const CLOSED = ["REJECTED", "WITHDRAWN"];
  let apps = [];
  let offers = [];
  let ready = { apps: false, offers: false };

  const first = String(session.fullName || "").replace(/\s*\(alias\)\s*/i, "").trim().split(/\s+/)[0] || "";
  $("hello").textContent = "Bonjour" + (first ? ", " + first : "");
  if (!RC.canManage()) document.querySelectorAll("[data-manage]").forEach((n) => (n.hidden = true));

  function scoreTier(score) {
    if (score == null) return { label: "—", cls: "badge-chrome" };
    if (score >= 80) return { label: "Excellent", cls: "badge-ok" };
    if (score >= 55) return { label: "Bon", cls: "badge-info" };
    return { label: "Faible", cls: "badge-rose" };
  }

  function renderInbox() {
    const root = $("inbox-list");
    if (!ready.apps) return;
    const q = (($("rc-search") || {}).value || "").trim().toLowerCase();
    const open = apps.filter((a) => !CLOSED.includes(a.status));
    const matched = q
      ? open.filter((a) => ((a.candidateName || "") + " " + (a.jobTitle || "") + " " + (RC.STATUS_FR[a.status] || "")).toLowerCase().includes(q))
      : open;
    const sorted = matched.slice().sort((a, b) => {
      const sa = a.slaRemainingDays == null ? 99 : a.slaRemainingDays;
      const sb = b.slaRemainingDays == null ? 99 : b.slaRemainingDays;
      return sa !== sb ? sa - sb : (b.matchScore || 0) - (a.matchScore || 0);
    });
    const top = sorted.slice(0, q ? 20 : 7);
    if (!top.length) {
      RC.empty(root, q
        ? { icon: "search", title: "Aucun résultat", text: "Aucun dossier ne correspond à « " + q + " »." }
        : { icon: "inbox", title: "Inbox vide", text: "Publiez une offre pour recevoir des candidatures.", action: RC.canManage() ? { href: "recruiter-offers.html#new", label: "Nouvelle offre" } : null });
      return;
    }
    root.innerHTML = top.map((c) => {
      const tier = scoreTier(c.matchScore);
      const sla = c.slaRemainingDays;
      const late = c.slaBreached || (sla != null && sla < 0);
      const slaTxt = late ? "En retard" : sla == null ? "—" : sla === 0 ? "Dernier jour" : "sous " + sla + " j";
      const slaCls = late ? "rc-pill--danger" : sla != null && sla <= 2 ? "rc-pill--warn" : "";
      return '<div class="rc-item rc-fade"><div class="rc-avatar">' + esc(RC.initials(c.candidateName)) + '</div><div class="rc-item-main"><strong>' +
        esc(c.candidateName || "Candidat") + "</strong><span>" + esc(c.jobTitle || "") + " · " + esc(RC.STATUS_FR[c.status] || c.status) + "</span></div>" +
        '<span class="badge ' + tier.cls + '">' + (c.matchScore != null ? Math.round(c.matchScore) + " %" : "—") + '</span><span class="rc-pill ' + slaCls + '">' + slaTxt +
        '</span><a class="btn btn-sm btn-soft" href="recruiter-pipeline.html?app=' + encodeURIComponent(c.id) + '">Ouvrir</a></div>';
    }).join("");
  }

  function renderStats() {
    if (!ready.apps) return;
    const published = offers.filter((o) => o.published).length;
    const screening = apps.filter((a) => a.status === "SCREENING").length;
    const withMatch = apps.filter((a) => a.matchScore != null);
    const avg = withMatch.length ? Math.round(withMatch.reduce((s, a) => s + a.matchScore, 0) / withMatch.length) : null;
    const activeApps = apps.filter((a) => !CLOSED.includes(a.status));
    const watched = activeApps.filter((a) => a.slaRemainingDays != null);
    const onTime = watched.filter((a) => !a.slaBreached && a.slaRemainingDays >= 0).length;
    const slaPct = watched.length ? Math.round((onTime / watched.length) * 100) : 100;
    const urgent = activeApps.filter((a) => a.slaBreached || (a.slaRemainingDays != null && a.slaRemainingDays <= 2));
    const interviews = apps.filter((a) => a.status === "INTERVIEW").length;

    $("st-offers").textContent = published;
    $("st-offers-d").textContent = RC.plural(offers.length, "offre") + " au total";
    $("st-apps").textContent = activeApps.length;
    $("st-apps-d").textContent = screening + " en présélection";
    $("st-sla").textContent = slaPct + " %";
    $("st-match").textContent = avg == null ? "—" : avg + " %";
    document.querySelectorAll(".rc-kpi").forEach((k) => k.classList.remove("is-skel"));

    $("wins-line").textContent =
      RC.plural(interviews, "entretien") + " · " + RC.plural(urgent.length, "dossier") + " à traiter en priorité · " + RC.plural(activeApps.length, "candidature") + " en cours";

    const nudge = $("sla-nudge");
    if (urgent.length) {
      nudge.hidden = false;
      $("sla-nudge-title").textContent = RC.plural(urgent.length, "dossier") + " proche" + (urgent.length > 1 ? "s" : "") + " du délai promis";
      $("sla-nudge-sub").textContent = "Le dépassement du délai de 1ʳᵉ réponse est visible côté candidat. Traitez d’abord : " + (urgent[0].candidateName || "ce candidat") + ".";
    } else nudge.hidden = true;

    const keys = ["SUBMITTED", "SCREENING", "SHORTLISTED", "INTERVIEW"];
    const labels = ["Reçues", "Présélection", "Shortlist", "Entretien"];
    const counts = keys.map((k) => apps.filter((a) => a.status === k).length);
    const max = Math.max(1, ...counts);
    $("chart-bars").innerHTML = keys.map((k, i) =>
      '<div class="rc-barrow"><span>' + labels[i] + '</span><div class="rc-track"><i data-w="' + Math.round((counts[i] / max) * 100) + '"></i></div><b>' + counts[i] + "</b></div>").join("");
    requestAnimationFrame(() => requestAnimationFrame(() => document.querySelectorAll("#chart-bars i").forEach((i) => (i.style.width = i.dataset.w + "%"))));
    const received = counts.reduce((a, b) => a + b, 0);
    const moved = counts[1] + counts[2] + counts[3];
    $("conversion").textContent = !received
      ? "Aucune candidature ouverte pour l’instant."
      : RC.plural(moved, "dossier") + " " + (moved > 1 ? "ont" : "a") + " quitté la réception, sur " + received + " ouverts.";

    renderInbox();
    renderInterviews();
  }

  function renderInterviews() {
    const root = $("upcoming");
    const now = Date.now();
    const list = apps.filter((a) => a.status === "INTERVIEW" && a.interviewAt && new Date(a.interviewAt).getTime() >= now - 3600e3)
      .sort((a, b) => new Date(a.interviewAt) - new Date(b.interviewAt)).slice(0, 4);
    if (!list.length) {
      RC.empty(root, { icon: "calendar", title: "Aucun entretien planifié", text: "Les entretiens apparaissent ici dès qu’un dossier passe à l’étape Entretien.", action: { href: "recruiter-pipeline.html", label: "Ouvrir le pipeline" } });
      return;
    }
    root.innerHTML = list.map((a) => {
      const d = new Date(a.interviewAt);
      return '<div class="rc-item"><div class="rc-datebox"><b>' + d.getDate() + "</b><small>" + d.toLocaleDateString("fr-FR", { month: "short" }) +
        '</small></div><div class="rc-item-main"><strong>' + esc(a.candidateName) + "</strong><span>" + esc(a.jobTitle || "") + " · " +
        esc(RC.INTERVIEW_TYPE_FR[a.interviewType] || a.interviewType || "") + " · " + d.toLocaleTimeString("fr-FR", { hour: "2-digit", minute: "2-digit" }) +
        '</span></div><a class="btn btn-sm btn-ghost" href="recruiter-pipeline.html?app=' + a.id + '">Dossier</a></div>';
    }).join("");
  }

  function renderActivity(rows) {
    const root = $("activity");
    const list = (rows || []).slice(0, 5);
    if (!list.length) { RC.empty(root, { icon: "bell", title: "Rien de nouveau", text: "Les nouvelles candidatures et rappels s’afficheront ici." }); return; }
    root.innerHTML = list.map((n) =>
      '<div class="rc-item"><div class="rc-avatar" style="' + (n.read ? "" : "background:var(--burgundy);color:#fff") + '">' + RC.svg("bell").replace("<svg", '<svg width="16" height="16" fill="none" stroke="currentColor" stroke-width="1.8"') +
      '</div><div class="rc-item-main"><strong>' + esc(n.title) + "</strong><span>" + esc(n.body) + '</span></div><small class="rc-muted">' + esc(RC.ago(n.at)) + "</small></div>").join("");
  }

  function verifyBanner(org) {
    const box = $("verify-banner");
    if (!org || org.verified) { box.hidden = true; return; }
    box.hidden = false;
  }

  function fail(err) {
    ["inbox-list", "upcoming"].forEach((id) => RC.errorState($(id), err.message, load));
  }

  function load() {
    RC.skeleton($("inbox-list"), 4, 52);
    RC.data.pipeline((a) => { apps = a || []; ready.apps = true; renderStats(); }, fail);
    RC.data.offers((o) => { offers = o || []; ready.offers = true; renderStats(); }, () => {});
  }

  document.addEventListener("rc:org", (e) => verifyBanner(e.detail));
  verifyBanner(RC.getOrg());
  document.addEventListener("rc:notifications", (e) => renderActivity(e.detail));
  renderActivity(RC.notifications.all());
  load();
})();
