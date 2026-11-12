/** Tableau de bord recruteur : actions du jour, priorités, prochains événements, entonnoir et indicateurs cliquables. */
(function (global) {
  const RC = HireLinkApp;
  const session = RC.mount({
    active: "dashboard",
    title: "Tableau de bord",
    search: { placeholder: "Filtrer les priorités : candidat, offre…", onQuery: () => renderInbox() },
  });
  if (!session) return;
  const $ = (id) => document.getElementById(id);
  const esc = RC.esc;
  const ico = (n) => (global.HLIcons ? HLIcons.svg(n) : "");
  const CLOSED = ["REJECTED", "WITHDRAWN", "HIRED"];
  const KIND_FR = { HR: "Entretien RH", TECHNICAL: "Entretien technique", MANAGER: "Avec le manager", CASE: "Étude de cas", TEAM: "Rencontre d’équipe", FINAL: "Entretien final" };
  let apps = [];
  let offers = [];
  let pace = null;
  let org = null;
  let ready = { apps: false, offers: false };
  const PACE_FR = { REACTIF: "Réactif", ATTENTIF: "Attentif" };

  const first = String(session.fullName || "").replace(/\s*\(alias\)\s*/i, "").trim().split(/\s+/)[0] || "";
  $("hello").textContent = "Bonjour" + (first ? ", " + first : "");
  if (!RC.canManage()) document.querySelectorAll("[data-manage]").forEach((n) => (n.hidden = true));

  const ymd = (d) => d.getFullYear() + "-" + String(d.getMonth() + 1).padStart(2, "0") + "-" + String(d.getDate()).padStart(2, "0");
  const sameDay = (a, b) => a.toDateString() === b.toDateString();
  const isLive = (a) => a.status === "INTERVIEW" && a.interviewAt && a.interviewStatus !== "CANCELLED";
  const isLate = (a) => a.slaBreached || (a.slaRemainingDays != null && a.slaRemainingDays < 0);
  const isUrgent = (a) => !CLOSED.includes(a.status) && (isLate(a) || (a.slaRemainingDays != null && a.slaRemainingDays <= 2));
  const daysTo = (iso) => Math.ceil((new Date(iso + "T23:59:59") - Date.now()) / 864e5);

  function tile(o) {
    const zero = !o.count;
    return '<a class="db-action' + (zero ? " is-zero" : "") + (o.tone ? " is-" + o.tone : "") + '" href="' + o.href + '">' +
      '<span class="db-action-ico" aria-hidden="true">' + ico(o.icon) + "</span>" +
      '<span class="db-action-txt"><b>' + (zero ? "—" : o.count) + "</b><strong>" + esc(o.title) + "</strong><small>" + esc(zero ? o.calm : o.sub) + "</small></span>" +
      '<span class="db-go" aria-hidden="true">' + ico("right") + "</span></a>";
  }

  function renderActions() {
    const today = new Date();
    const toSort = apps.filter((a) => a.status === "SUBMITTED");
    const ivToday = apps.filter((a) => isLive(a) && sameDay(new Date(a.interviewAt), today));
    const urgent = apps.filter(isUrgent);
    const expiring = offers.filter((o) => RC.isLiveOffer(o) && o.deadline && daysTo(o.deadline) >= 0 && daysTo(o.deadline) <= 7)
      .sort((x, y) => String(x.deadline).localeCompare(String(y.deadline)));
    const nextIv = ivToday.slice().sort((x, y) => new Date(x.interviewAt) - new Date(y.interviewAt))[0];
    const tiles = [
      { icon: "inbox", title: "Candidatures à trier", count: toSort.length, sub: "Nouvelles, jamais ouvertes", calm: "Tout est trié", href: "recruiter-pipeline.html?focus=stage:SUBMITTED" },
      { icon: "calendar", title: "Entretiens aujourd’hui", count: ivToday.length, tone: "info",
        sub: nextIv ? "Prochain à " + new Date(nextIv.interviewAt).toLocaleTimeString("fr-FR", { hour: "2-digit", minute: "2-digit" }) + " · " + (nextIv.candidateName || "") : "",
        calm: "Journée sans entretien", href: "recruiter-agenda.html?view=day&date=" + ymd(today) },
      { icon: "clock", title: "Réponses à risque", count: urgent.length, tone: urgent.some(isLate) ? "danger" : "warn",
        sub: urgent.some(isLate) ? RC.plural(urgent.filter(isLate).length, "délai") + " déjà dépassé" + (urgent.filter(isLate).length > 1 ? "s" : "") : "Délai promis dans 2 jours ou moins",
        calm: "Tous les délais sont tenus", href: "recruiter-pipeline.html?view=urgent" },
    ];
    if (RC.canManage()) tiles.push({ icon: "briefcase", title: "Offres qui expirent", count: expiring.length, tone: "warn",
      sub: expiring[0] ? (expiring[0].title + " · " + (daysTo(expiring[0].deadline) === 0 ? "aujourd’hui" : "dans " + RC.plural(daysTo(expiring[0].deadline), "jour"))) : "",
      calm: "Aucune clôture cette semaine", href: expiring[0] ? "recruiter-offers.html?focus=offer:" + expiring[0].id : "recruiter-offers.html" });
    $("db-actions").innerHTML = tiles.map(tile).join("");
    // Les tuiles se recouvrent : un dossier soumis déjà en retard est à la fois « à trier » et « à risque ».
    // Le titre compte des dossiers distincts, pas la somme des tuiles.
    const todo = new Set();
    [toSort, ivToday, urgent].forEach((list) => list.forEach((a) => todo.add(a.id)));
    const total = todo.size + (RC.canManage() ? expiring.length : 0);
    $("wins-line").textContent = total
      ? new Date().toLocaleDateString("fr-FR", { weekday: "long", day: "numeric", month: "long" }).replace(/^./, (c) => c.toUpperCase()) + " · " + RC.plural(total, "action") + " en attente"
      : "Rien d’urgent aujourd’hui. Bonne journée !";
  }

  function renderInbox() {
    const root = $("inbox-list");
    if (!ready.apps) return;
    const q = (($("rc-search") || {}).value || "").trim();
    const open = apps.filter((a) => !CLOSED.includes(a.status) && a.status !== "OFFER");
    const matched = HireLinkSearch.rank(open, q, [
      { get: (a) => a.candidateName, weight: 3 }, { get: (a) => a.jobTitle, weight: 2 }, { get: (a) => RC.STATUS_FR[a.status], weight: 1 },
    ]);
    const sorted = q ? matched : matched.slice().sort((a, b) => {
      const sa = a.slaRemainingDays == null ? 99 : a.slaRemainingDays;
      const sb = b.slaRemainingDays == null ? 99 : b.slaRemainingDays;
      return sa - sb;
    });
    const top = sorted.slice(0, q ? 12 : 5);
    if (!top.length) {
      if (q) { HireLinkSearch.emptyState(root, q, { onClear: () => { $("rc-search").value = ""; renderInbox(); } }); return; }
      root.innerHTML = '<div class="db-empty">' + ico("check") + "<strong>Aucun dossier en attente</strong><span>Les nouvelles candidatures apparaîtront ici.</span>" +
        (RC.canManage() ? '<a class="btn btn-soft btn-sm" href="recruiter-offers.html#new">Publier une offre</a>' : "") + "</div>";
      return;
    }
    root.innerHTML = top.map((c) => {
      const sla = c.slaRemainingDays;
      const late = isLate(c);
      const slaTxt = late ? "En retard" : sla == null ? "" : sla === 0 ? "Aujourd’hui" : "Sous " + sla + " j";
      const slaCls = late ? "rc-pill--danger" : sla != null && sla <= 2 ? "rc-pill--warn" : "";
      return '<a class="rc-item db-row" href="recruiter-pipeline.html?app=' + c.id + "&focus=application:" + c.id + '"><div class="rc-avatar">' + esc(RC.initials(c.candidateName)) +
        '</div><div class="rc-item-main"><strong>' + esc(c.candidateName || "Candidat") + "</strong><span>" + esc(c.jobTitle || "") + " · " + esc(RC.STATUS_FR[c.status] || c.status) + "</span></div>" +
        (slaTxt ? '<span class="rc-pill ' + slaCls + '">' + slaTxt + "</span>" : "") + '<span class="db-go" aria-hidden="true">' + ico("right") + "</span></a>";
    }).join("");
  }

  function renderFunnel() {
    const keys = ["SUBMITTED", "SCREENING", "SHORTLISTED", "INTERVIEW", "OFFER", "HIRED"];
    const labels = ["Reçues", "Présélection", "Shortlist", "Entretien", "Offre", "Embauchés"];
    const counts = keys.map((k) => apps.filter((a) => a.status === k).length);
    const max = Math.max(1, ...counts);
    const total = counts.reduce((a, b) => a + b, 0);
    $("chart-bars").innerHTML = keys.map((k, i) => {
      const reached = counts.slice(i).reduce((a, b) => a + b, 0), prev = i ? counts.slice(i - 1).reduce((a, b) => a + b, 0) : 0;
      return '<a class="db-stage" href="' + (k === "HIRED" ? "recruiter-pipeline.html?view=list&status=hired" : "recruiter-pipeline.html?focus=stage:" + (k === "OFFER" || k === "HIRED" ? "INTERVIEW" : k)) + '">' +
        "<small>" + labels[i] + "</small><b>" + counts[i] + '</b><span class="db-stage-bar"><i style="--w:' + Math.max(4, Math.round((counts[i] / max) * 100)) + '%"></i></span>' +
        // Le pourcentage porte sur TOUS les dossiers, sinon « 55 % » se lirait comme une part du total alors
        // qu'il ne s'agit que de l'étape précédente.
        (i && prev ? "<em>" + Math.round((reached / total) * 100) + " % des dossiers en cours vont au moins jusque-là</em>" : "<em>&nbsp;</em>") + "</a>";
    }).join("");
    const open = counts.slice(0, 5).reduce((a, b) => a + b, 0);
    $("conversion").textContent = open ? RC.plural(open, "dossier") + " ouvert" + (open > 1 ? "s" : "") + " · cliquez une étape pour l’ouvrir dans le pipeline" : "Aucun dossier ouvert pour l’instant";
  }

  function renderKpis() {
    const active = apps.filter((a) => !CLOSED.includes(a.status));
    const watched = active.filter((a) => a.slaRemainingDays != null);
    const onTime = watched.filter((a) => !isLate(a)).length;
    const slaPct = watched.length ? Math.round((onTime / watched.length) * 100) : 100;
    const now = new Date(); const start = new Date(now); start.setHours(0, 0, 0, 0); start.setDate(start.getDate() - ((start.getDay() + 6) % 7));
    const end = new Date(start); end.setDate(end.getDate() + 7);
    const week = apps.filter((a) => isLive(a) && new Date(a.interviewAt) >= start && new Date(a.interviewAt) < end).length;
    const hired30 = apps.filter((a) => a.status === "HIRED" && a.updatedAt && Date.now() - new Date(a.updatedAt) < 30 * 864e5).length;
    const k = [
      { t: "Candidatures actives", v: active.length, s: "Tous postes confondus", h: "recruiter-pipeline.html?view=list&status=active" },
      { t: "Entretiens cette semaine", v: week, s: "Du lundi au dimanche", h: "recruiter-agenda.html?view=week&date=" + ymd(now) },
      { t: "Délais de réponse tenus", v: slaPct + " %", s: watched.length - onTime ? (watched.length - onTime) + " en retard" : "Aucun retard", h: "recruiter-pipeline.html?view=breached" },
      { t: "Embauches (30 jours)", v: hired30, s: "Dossiers finalisés", h: "recruiter-pipeline.html?view=list&status=hired" },
    ];
    if (RC.canManage()) k.splice(1, 0, { t: "Offres en ligne", v: offers.filter(RC.isLiveOffer).length, s: RC.plural(offers.length, "offre") + " au total", h: "recruiter-offers.html" });
    $("db-kpis").innerHTML = k.map((x) => '<a class="db-kpi" href="' + x.h + '"><small>' + esc(x.t) + "</small><b>" + esc(String(x.v)) + "</b><span>" + esc(x.s) + "</span></a>").join("");
  }

  function renderEvents() {
    const root = $("upcoming");
    const now = Date.now();
    const list = apps.filter((a) => isLive(a) && new Date(a.interviewAt).getTime() >= now - 3600e3 && new Date(a.interviewAt).getTime() <= now + 7 * 864e5)
      .sort((a, b) => new Date(a.interviewAt) - new Date(b.interviewAt)).slice(0, 5);
    if (!list.length) {
      root.innerHTML = '<div class="db-empty">' + ico("calendar") + "<strong>Semaine libre</strong><span>Aucun entretien prévu dans les 7 prochains jours.</span>" +
        '<a class="btn btn-soft btn-sm" href="recruiter-pipeline.html?focus=stage:SHORTLISTED">Planifier depuis la shortlist</a></div>';
      return;
    }
    const today = new Date();
    root.innerHTML = list.map((a) => {
      const d = new Date(a.interviewAt);
      const t = RC.interviewTypeUi(a.interviewType);
      const soon = d.getTime() - now < 2 * 3600e3 && d.getTime() > now - 3600e3;
      const day = sameDay(d, today) ? "Aujourd’hui" : sameDay(d, new Date(now + 864e5)) ? "Demain" : d.toLocaleDateString("fr-FR", { weekday: "short", day: "numeric", month: "short" });
      return '<a class="db-ev ' + t.cls + (soon ? " is-soon" : "") + '" href="recruiter-agenda.html?view=day&date=' + ymd(d) + "&focus=interview:" + a.id + '">' +
        '<span class="db-ev-ico" aria-hidden="true">' + ico(t.icon) + "</span>" +
        '<span class="db-ev-main"><strong>' + esc(a.candidateName || "Candidat") + "</strong><span>" + [KIND_FR[a.interviewKind] || "Entretien", t.label, esc(a.jobTitle || "")].filter((v, i, all) => v && all.indexOf(v) === i).join(" · ") + "</span></span>" +
        '<span class="db-ev-when"><b>' + d.toLocaleTimeString("fr-FR", { hour: "2-digit", minute: "2-digit" }) + "</b><small>" + esc(day) + "</small></span></a>";
    }).join("");
  }

  function renderAll() {
    if (!ready.apps) return;
    renderActions(); renderInbox(); renderEvents(); renderFunnel(); renderKpis();
  }

  function verifyBanner(org) { $("verify-banner").hidden = !org || !!org.verified; }

  /** Les deux seules distinctions du tableau de bord, calculées sur des délais déjà constatés. */
  function renderSignals() {
    const root = $("db-signals");
    if (!root) return;
    const bits = [];
    const label = pace ? PACE_FR[pace.badge] : "";
    if (label) {
      const decided = RC.plural(pace.answered, "dossier") + " traité" + (pace.answered > 1 ? "s" : "");
      const title = pace.medianDays != null && pace.medianDays <= 3
        ? "Première réponse en " + (Math.round(Number(pace.medianDays) * 10) / 10).toLocaleString("fr-FR") +
          " j en médiane sur vos " + decided + "."
        : "Vous répondez à la majorité de vos dossiers en moins de 3 jours (" + decided + ").";
      bits.push('<span class="badge badge-brass badge-pop" title="' + esc(title) + '">' + ico("hourglass") + esc(label) + "</span>");
    }
    if (org && org.verified) bits.push('<span class="badge badge-ok badge-pop" title="' + esc("Identité de l’entreprise confirmée par HireLink.") + '">' + ico("shield") + "Vérifiée</span>");
    root.innerHTML = bits.length ? " " + bits.join(" ") : "";
  }

  function load() {
    RC.skeleton($("inbox-list"), 4, 52);
    RC.data.pipeline((a) => { apps = a || []; ready.apps = true; renderAll(); },
      (err) => ["inbox-list", "upcoming"].forEach((id) => RC.errorState($(id), err.message, load)));
    RC.data.offers((o) => { offers = o || []; ready.offers = true; renderAll(); }, () => {});
    RC.data.pace((p) => { pace = p; renderSignals(); }, () => {});
  }

  document.addEventListener("rc:org", (e) => { org = e.detail; verifyBanner(org); renderSignals(); });
  org = RC.getOrg();
  verifyBanner(org);
  load();
  document.addEventListener("hirelink:push", () => { RC.data.invalidate(); load(); });
})(window);
