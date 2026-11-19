/* Espace administration — vue d’ensemble, entreprises, comptes, offres, formules, journaux */
(function () {
  const App = HireLinkApp;
  const session = App.mount({});
  if (!session) return;

  const esc = App.esc;
  const VIEWS = ["dash", "orgs", "users", "support", "offers", "external", "plans", "audit", "emails"];
  /** Un seul fichier pour plusieurs vues : l'onglet du navigateur dit laquelle est ouverte. */
  const VIEW_TITLE = {
    dash: "Vue d’ensemble", orgs: "Entreprises", users: "Comptes", support: "Messages du support", offers: "Offres",
    plans: "Formules et factures", audit: "Journal d’audit", emails: "Journal des e-mails", external: "Offres hors HireLink",
  };
  const STATUS_FR = App.STATUS_FR;
  const PAY_FR = { NON_PAYEE: "Non payée", PAYEE: "Payée", ANNULEE: "Annulée", AVOIR: "Avoir", EMISE: "Non payée", REGLEE: "Payée", OFFERTE: "Payée" };
  const ROLE_FR = { CANDIDAT: "Candidat", RECRUTEUR: "Recruteur", ADMINISTRATEUR: "Administrateur" };
  const ORG_ROLE_FR = { OWNER: "Créateur", RECRUTEUR: "Recruteur", HIRING_MANAGER: "Accès limité" };
  const ORG_ROLE_TONE = { OWNER: "rc-pill--rose", RECRUTEUR: "", HIRING_MANAGER: "rc-pill--info" };
  /** Filtre de rôle : rôle plateforme, rôle dans l'entreprise (OWNER) ou comptes désactivés. */
  function userMatches(u, f) {
    if (f === "all") return true;
    if (f === "inactive") return u.active === false;
    if (f === "OWNER") return u.orgRole === f;
    return u.role === f;
  }

  const state = { orgs: [], plans: [], invoices: [], users: [], aiUsage: {}, overview: null, granularity: "week", orgFilter: "all", userFilter: "all", offers: [], reports: { queue: [], handled: [] }, offerFilter: "all", invoiceFilter: "all", planPane: "plans" };

  const money = (n) => Number(n || 0).toLocaleString("fr-FR", { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + " MAD";
  const mad = (n) => Math.round(Number(n || 0)).toLocaleString("fr-FR") + " MAD";
  const num = (n) => Number(n || 0).toLocaleString("fr-FR");
  const planLabel = (code) => (state.plans.find((p) => p.code === code) || {}).label || ({ ACCES: "Essentiel", PREMIUM: "Professionnel" })[code] || code;
  const $ = (id) => document.getElementById(id);
  const fail = (title, err) => App.toast(title, (err && err.message) || "Action impossible pour le moment.", "danger");
  const ico = (n) => (window.HLIcons ? HLIcons.svg(n) : "");

  /* ------------------------------------------------------------- navigation */
  function show(view) {
    // Lien contextuel : #orgs/pending, #users/CANDIDAT… ouvre l'onglet déjà filtré.
    const sub = String(view || "").split("/")[1];
    view = String(view || "").split("/")[0];
    if (VIEWS.indexOf(view) < 0) view = "dash";
    if (sub) setTimeout(() => {
      const bar = $(({ orgs: "org-filter", users: "user-filter", offers: "offer-filter" })[view] || "");
      const chip = bar && bar.querySelector('[data-f="' + sub + '"]');
      if (chip) chip.click();
      if (view === "plans") setPlanPane(sub);
    }, 0);
    VIEWS.forEach((id) => { $("view-" + id).hidden = id !== view; });
    document.title = (VIEW_TITLE[view] || "Administration") + " · Administration — HireLink";
    App.setActive(view);
    if (view === "audit") loadAudit();
    if (view === "emails") loadEmails();
    if (view === "external") loadExternal();
    if (view === "support") loadSupport();
    window.scrollTo(0, 0);
  }
  window.addEventListener("hashchange", () => show((location.hash || "").slice(1)));

  const verifyPill = (o) => '<span class="rc-pill ' + (o.verified ? "rc-pill--ok" : "rc-pill--warn") + '">' + esc(o.verified ? "Vérifiée" : (o.statusLabel || "Non vérifiée")) + "</span>";
  const accessPill = (o) => {
    const st = o.accessStatus || "ACTIVE";
    return '<span class="rc-pill ' + (st === "ACTIVE" ? "rc-pill--ok" : st === "DISABLED" ? "rc-pill--warn" : "rc-pill--danger") + '">' + esc(o.accessLabel || "Active") + "</span>";
  };

  /* ------------------------------------------------------- graphiques (SVG) */
  const SVGNS = 'xmlns="http://www.w3.org/2000/svg"';
  const DONUT_COLORS = ["#d9b98a", "#a8455a", "#7a2032"];
  const DONUT_COLORS_CAND = ["#e8dccd", "#7a2032"];

  function hbar(value, max) {
    const w = max ? Math.max(value ? 2 : 0, Math.round((value / max) * 100)) : 0;
    return "<svg " + SVGNS + ' viewBox="0 0 100 18" preserveAspectRatio="none" aria-hidden="true">' +
      '<rect x="0" y="3" width="100" height="12" rx="6" fill="var(--paper-2, #f3efe9)"></rect>' +
      '<rect x="0" y="3" width="' + w + '" height="12" rx="6" fill="#7a2032"></rect></svg>';
  }

  function donut(title, rows, colors, unit, swatch) {
    const total = rows.reduce((a, r) => a + Number(r.count || 0), 0);
    const R = 52, C = 2 * Math.PI * R;
    let offset = 0;
    const arcs = total ? rows.map((r, i) => {
      const len = (Number(r.count || 0) / total) * C;
      const arc = len > 0 ? '<circle cx="75" cy="75" r="' + R + '" fill="none" stroke="' + colors[i % colors.length] + '" stroke-width="20" stroke-dasharray="' +
        len.toFixed(2) + " " + (C - len).toFixed(2) + '" stroke-dashoffset="' + (-offset).toFixed(2) + '" transform="rotate(-90 75 75)"><title>' + esc(r.label + " : " + r.count) + "</title></circle>" : "";
      offset += len;
      return arc;
    }).join("") : '<circle cx="75" cy="75" r="' + R + '" fill="none" stroke="var(--line)" stroke-width="20"></circle>';
    return '<div class="adm-donut"><h3>' + esc(title) + "</h3><svg " + SVGNS + ' viewBox="0 0 150 150" role="img" aria-label="' + esc(title) + '">' + arcs +
      '<text x="75" y="76" text-anchor="middle" class="adm-donut-total">' + num(total) + '</text><text x="75" y="94" text-anchor="middle" class="adm-donut-sub">' + esc(unit) + "</text></svg>" +
      '<ul class="adm-legend">' + rows.map((r, i) => '<li><i class="' + swatch + i + '"></i>' + esc(r.label) + "<b>" + num(r.count) + "</b></li>").join("") + "</ul></div>";
  }

  function histogram(buckets, granularity) {
    const W = 760, H = 230, padL = 34, padB = 28, padT = 16;
    const n = buckets.length || 1;
    const max = Math.max(1, ...buckets.map((b) => b.candidates + b.recruiters));
    const step = Math.max(1, Math.ceil(max / 4));
    const top = step * 4;
    const plotH = H - padB - padT;
    const slot = (W - padL - 8) / n;
    const bw = Math.min(38, slot * 0.62);
    const y = (v) => padT + plotH - (v / top) * plotH;
    let g = "";
    for (let t = 0; t <= top; t += step) {
      g += '<line class="adm-grid-line" x1="' + padL + '" x2="' + (W - 4) + '" y1="' + y(t).toFixed(1) + '" y2="' + y(t).toFixed(1) + '"></line>' +
        '<text x="' + (padL - 8) + '" y="' + (y(t) + 4).toFixed(1) + '" text-anchor="end">' + t + "</text>";
    }
    const fmt = (iso) => {
      const d = new Date(iso + "T00:00:00");
      return granularity === "month"
        ? d.toLocaleDateString("fr-FR", { month: "short" }).replace(".", "")
        : d.toLocaleDateString("fr-FR", { day: "numeric", month: "short" }).replace(".", "");
    };
    const bars = buckets.map((b, i) => {
      const x = padL + i * slot + (slot - bw) / 2;
      const yc = y(b.candidates), yr = y(b.candidates + b.recruiters);
      const label = (granularity === "month" ? "Mois de " : "Semaine du ") + fmt(b.start) + " : " + b.candidates + " candidat(s), " + b.recruiters + " recruteur(s)";
      return "<g><title>" + esc(label) + "</title>" +
        '<rect class="adm-bar-c" x="' + x.toFixed(1) + '" y="' + yc.toFixed(1) + '" width="' + bw.toFixed(1) + '" height="' + (padT + plotH - yc).toFixed(1) + '" rx="3"></rect>' +
        '<rect class="adm-bar-r" x="' + x.toFixed(1) + '" y="' + yr.toFixed(1) + '" width="' + bw.toFixed(1) + '" height="' + (yc - yr).toFixed(1) + '" rx="3"></rect>' +
        (b.candidates + b.recruiters ? '<text class="adm-val" x="' + (x + bw / 2).toFixed(1) + '" y="' + (yr - 5).toFixed(1) + '" text-anchor="middle">' + (b.candidates + b.recruiters) + "</text>" : "") +
        '<text x="' + (x + bw / 2).toFixed(1) + '" y="' + (H - 9) + '" text-anchor="middle">' + esc(fmt(b.start)) + "</text></g>";
    }).join("");
    return "<svg " + SVGNS + ' viewBox="0 0 ' + W + " " + H + '" role="img" aria-label="Inscriptions">' + g +
      '<line class="adm-axis" x1="' + padL + '" x2="' + (W - 4) + '" y1="' + (padT + plotH) + '" y2="' + (padT + plotH) + '"></line>' + bars + "</svg>";
  }

  /* -------------------------------------------------------------- vue d'ensemble */
  function paintDash() {
    const o = state.overview;
    if (!o) return;
    const rev = o.revenue || {}, people = o.people || {}, co = o.companies || {}, act = o.activity || {}, pend = o.pending || {};
    const kpi = (label, value, hint, href, cls) => '<a class="adm-kpi' + (cls ? " " + cls : "") + '" href="' + href + '"><small>' + esc(label) + "</small><b>" + esc(value) + "</b><span>" + esc(hint || "") + "</span></a>";
    $("dash-kpis").innerHTML =
      kpi("Revenu mensuel des formules actives", mad(rev.monthlyActive), "Somme des prix mensuels des abonnements en cours", "#plans/plans", "is-money") +
      kpi("Encaissé sur 30 jours", money(rev.collectedLast30Days), "Total encaissé depuis le lancement : " + money(rev.collectedTotal), "#plans/invoices", "is-money") +
      kpi("Entreprises actives", num(co.active), num(co.verified) + " vérifiées · " + num(co.disabled) + " désactivées", "#orgs/all") +
      kpi("Comptes actifs", num(people.active), num(people.seenLast30Days) + " connectés ces 30 derniers jours", "#users/all") +
      kpi("Offres en ligne", num(act.publishedOffers), num(act.offers) + " offres au total", "#offers/online") +
      kpi("Candidatures", num(act.applications), "Toutes offres confondues", "#offers/all");

    const byPlan = rev.byPlan || [];
    const maxRev = Math.max(0, ...byPlan.map((p) => p.monthlyTotal));
    $("dash-revenue").innerHTML = byPlan.map((p) =>
      '<div class="adm-hbar"><div class="adm-hbar-label"><strong>' + esc(p.label) + "</strong><span>" + esc(p.audience) + " · " + App.plural(p.subscribers, "abonné", "abonnés") + "</span></div>" +
      hbar(p.monthlyTotal, maxRev) + '<div class="adm-hbar-val">' + mad(p.monthlyTotal) + "<small>" + (p.monthlyMad ? mad(p.monthlyMad) + " / mois" : "Gratuite") + "</small></div></div>").join("");
    const collected = (rev.collectedByPlan || []).map((c) => c.label + " " + money(c.amount)).join(" · ");
    $("dash-revenue-note").textContent = "Encaissé par formule depuis le lancement : " + (collected || "aucune facture réglée pour le moment") + ".";

    const dist = o.distribution || {};
    $("dash-plans").innerHTML = donut("Entreprises", dist.companies || [], DONUT_COLORS, "entreprises", "adm-sw-c") +
      donut("Candidats", dist.candidates || [], DONUT_COLORS_CAND, "candidats", "adm-sw-k");

    const s = o.signups || { buckets: [] };
    $("dash-signups").innerHTML = histogram(s.buckets || [], s.granularity);

    const tile = (t) => '<a class="db-action' + (!t.count ? " is-zero" : "") + (t.tone && t.count ? " is-" + t.tone : "") + '" href="' + t.href + '"><span class="db-action-ico" aria-hidden="true">' + ico(t.icon) +
      '</span><span class="db-action-txt"><b>' + (t.count ? num(t.count) : "0") + "</b><strong>" + esc(t.title) + "</strong><small>" + esc(t.count ? t.sub : t.calm) + '</small></span><span class="db-go" aria-hidden="true">' + ico("right") + "</span></a>";
    $("db-actions").innerHTML = [
      { icon: "card", title: "Factures non payées", count: pend.unpaidInvoices, tone: "warn", sub: "Reste à recevoir : " + money(pend.unpaidAmount), calm: "Aucune facture en attente", href: "#plans/invoices" },
      { icon: "shield", title: "Entreprises sans badge", count: pend.companiesWithoutBadge, tone: "info", sub: "E-mail professionnel non confirmé", calm: "Toutes les entreprises actives sont vérifiées", href: "#orgs/pending" },
      { icon: "inbox", title: "E-mails en échec", count: pend.failedEmails7Days, tone: "danger", sub: "Ces 7 derniers jours", calm: "Aucun échec d’envoi cette semaine", href: "#emails" },
      { icon: "ban", title: "Offres suspendues", count: pend.suspendedOffers, tone: "warn", sub: "En attente d’une décision", calm: "Aucune offre suspendue", href: "#offers/suspended" },
    ].map(tile).join("");
  }
  $("dash-refresh").addEventListener("click", () => refresh("overview").catch((err) => fail("Vue d’ensemble", err)));
  $("dash-gran").addEventListener("click", (e) => {
    const b = e.target.closest("[data-g]");
    if (!b || b.dataset.g === state.granularity) return;
    state.granularity = b.dataset.g;
    $("dash-gran").querySelectorAll(".rc-seg-btn").forEach((x) => x.classList.toggle("is-on", x === b));
    refresh("overview").catch((err) => fail("Inscriptions", err));
  });

  /* ------------------------------------------------------------ entreprises */
  const PAID = ["PROFESSIONNEL", "ENTREPRISE", "PREMIUM"];
  function paintOrgs() {
    const q = ($("org-search").value || "").trim();
    const f = state.orgFilter;
    const rows = HireLinkSearch.rank(state.orgs.filter((o) => {
      const st = o.accessStatus || "ACTIVE";
      if (f === "removed") return st === "REMOVED";
      if (st === "REMOVED") return false;
      if (f === "disabled") return st === "DISABLED";
      if (f === "verified") return o.verified;
      if (f === "pending") return !o.verified && st === "ACTIVE";
      if (f === "paid") return PAID.indexOf(o.planTier) >= 0;
      return true;
    }), q, [{ get: (o) => o.name, weight: 3 }, { get: (o) => o.city, weight: 2 }, { get: (o) => o.ownerEmail, weight: 1 }, { get: (o) => o.ice, weight: 2 }]);
    const body = $("orgs-body");
    if (!rows.length) {
      body.innerHTML = '<tr><td colspan="7"></td></tr>';
      HireLinkSearch.emptyState(body.firstChild.firstChild, q, { emptyTitle: "Aucune entreprise ne correspond", onClear: () => { $("org-search").value = ""; paintOrgs(); } });
      return;
    }
    body.innerHTML = rows.map((o) => {
      const logo = o.logoUrl && HireLinkAPI.mediaUrl ? HireLinkAPI.mediaUrl(o.logoUrl) : "";
      return '<tr><td><div class="rc-logo-row"><span class="rc-orgmark rc-orgmark--md">' + (logo ? '<img alt="" loading="lazy" data-initials="' + esc(App.initials(o.name)) + '" src="' + esc(logo) + '" />' : esc(App.initials(o.name))) + "</span>" +
        '<div class="rc-cell-main"><strong>' + esc(o.name) + '</strong><span class="rc-cell-sub">' + esc([o.sector, o.city].filter(Boolean).join(" · ") || o.ownerEmail || "—") + "</span></div></div></td>" +
        "<td>" + esc(o.planLabel || planLabel(o.planTier)) + "</td><td>" + verifyPill(o) + "</td><td>" + accessPill(o) + "</td><td>" + o.memberCount + "</td><td>" + o.offerCount + "</td>" +
        '<td><div class="rc-row-actions"><button type="button" class="btn btn-soft btn-sm" data-open="' + o.id + '">Détails</button></div></td></tr>';
    }).join("");
    body.querySelectorAll("img[data-initials]").forEach((img) => {
      img.addEventListener("error", () => { if (img.parentNode) img.parentNode.textContent = img.dataset.initials; });
    });
    HireLinkSearch.mark(body, q);
  }
  $("orgs-body").addEventListener("click", (e) => {
    const b = e.target.closest("[data-open]");
    if (b) openOrg(Number(b.dataset.open));
  });
  $("org-search").addEventListener("input", App.debounce(paintOrgs, 120));
  $("org-filter").addEventListener("click", (e) => {
    const b = e.target.closest("[data-f]");
    if (!b) return;
    state.orgFilter = b.dataset.f;
    $("org-filter").querySelectorAll(".rc-chip").forEach((c) => c.classList.toggle("is-on", c === b));
    paintOrgs();
  });

  /** Confirmation, appel, rafraîchissement : chaque bouton de gestion passe par ici. */
  const ORG_ACTIONS = {
    unverify: { title: "Retirer le badge « Entreprise vérifiée » ?", text: "Le badge disparaît des offres et de la page entreprise, et la publication de nouvelles offres est bloquée. L’entreprise ne pourra pas le récupérer seule : vous pourrez relancer la vérification plus tard.", label: "Retirer le badge", danger: true,
      run: (o) => HireLinkAPI.api("/admin/organizations/" + o.id, { method: "PATCH", body: { verified: false } }), done: "Badge retiré" },
    recheck: { title: "Relancer la vérification ?", text: "Le contrôle automatique est refait sur l’e-mail du créateur de l’espace. Le badge revient seulement si cette adresse est professionnelle.", label: "Relancer", danger: false,
      run: (o) => HireLinkAPI.api("/admin/organizations/" + o.id + "/recheck", { method: "POST" }), done: "Vérification relancée" },
    disable: { title: "Désactiver cette entreprise ?", text: "Les membres ne peuvent plus se connecter (leurs sessions sont fermées) et les offres en ligne sont retirées de la vitrine. Rien n’est supprimé : la réactivation rétablit les comptes et les offres.", label: "Désactiver", danger: true,
      run: (o) => HireLinkAPI.api("/admin/organizations/" + o.id + "/active", { method: "PATCH", body: { active: false } }), done: "Entreprise désactivée" },
    enable: { title: "Réactiver cette entreprise ?", text: "Les comptes et les offres mis en pause par la désactivation sont rétablis.", label: "Réactiver", danger: false,
      run: (o) => HireLinkAPI.api("/admin/organizations/" + o.id + "/active", { method: "PATCH", body: { active: true } }), done: "Entreprise réactivée" },
    remove: { title: "Retirer définitivement cette entreprise ?", text: "Les membres perdent l’accès et les offres quittent la vitrine. Les factures et l’historique sont conservés. Cette action ne peut pas être annulée depuis l’administration.", label: "Retirer l’entreprise", danger: true,
      run: (o) => HireLinkAPI.api("/admin/organizations/" + o.id, { method: "DELETE" }), done: "Entreprise retirée" },
  };

  async function openOrg(id) {
    let org;
    try { org = await HireLinkAPI.api("/admin/organizations/" + id); } catch (err) { return fail("Entreprise", err); }
    const st = org.accessStatus || "ACTIVE";
    const members = (org.members || []).map((m) =>
      '<li class="rc-item"><div class="rc-item-main"><strong>' + esc(m.fullName) + '</strong><span class="rc-muted">' + esc(m.email) + "</span></div>" +
      '<span class="rc-pill ' + (ORG_ROLE_TONE[m.role] || "") + '">' + esc(ORG_ROLE_FR[m.role] || m.role) + "</span>" +
      (m.active ? "" : ' <span class="rc-pill rc-pill--danger">Désactivé</span>') + "</li>").join("");
    const meta = (k, v) => "<div><dt>" + esc(k) + "</dt><dd>" + v + "</dd></div>";
    const actions = [];
    if (st !== "REMOVED") {
      if (org.verified) actions.push(["unverify", "btn-ghost btn-danger-ghost", "Retirer la vérification"]);
      else if (st === "ACTIVE") actions.push(["recheck", "btn-soft", "Relancer la vérification"]);
      actions.push(st === "DISABLED" ? ["enable", "btn-soft", "Réactiver l’entreprise"] : ["disable", "btn-ghost", "Désactiver l’entreprise"]);
      actions.push(["remove", "btn-ghost btn-danger-ghost", "Retirer l’entreprise"]);
    }
    const body =
      '<dl class="adm-org-meta">' +
      meta("Badge", verifyPill(org)) + meta("État", accessPill(org)) +
      meta("Créateur de l’espace", esc(org.ownerName || "—") + '<br><span class="rc-muted">' + esc(org.ownerEmail || "") + "</span>") +
      meta("Domaine de l’e-mail", esc(org.emailDomain || "—")) +
      meta("Formule", esc(org.planLabel || planLabel(org.planTier))) + meta("Offres", esc(String(org.offerCount))) +
      meta("ICE", esc(org.ice || "Non renseigné")) + meta("Inscrite le", esc(org.createdAt ? App.fmtDate(org.createdAt) : "—")) +
      "</dl>" +
      '<h4 class="adm-section-title">Équipe</h4><ul class="rc-list adm-members">' + (members || "<li class='rc-muted'>Aucun membre actif.</li>") + "</ul>" +
      '<p class="adm-note">La formule est en lecture seule : un passage à une formule payante se fait par le règlement de la facture.</p>' +
      (actions.length ? '<div class="adm-org-actions">' + actions.map((a) => '<button type="button" class="btn ' + a[1] + ' btn-sm" data-org-act="' + a[0] + '">' + esc(a[2]) + "</button>").join("") + "</div>" : "");
    let chosen = null;
    const ok = await HireLink.openModal({
      title: org.name, description: "Fiche entreprise", large: true, bodyHtml: body, confirmLabel: "Fermer", hideCancel: true,
      onReady: (api) => {
        api.root.querySelectorAll("[data-org-act]").forEach((b) => {
          b.onclick = () => { chosen = b.dataset.orgAct; HireLink.closeModal(true); };
        });
      },
    });
    if (!ok || !chosen) return;
    const a = ORG_ACTIONS[chosen];
    const sure = await Mascotte.confirm({ title: a.title, description: org.name + " — " + a.text, confirmLabel: a.label, cancelLabel: "Annuler", danger: a.danger });
    if (!sure) return;
    try {
      const res = await a.run(org);
      App.toast(a.done, chosen === "recheck" ? (res && res.verified ? org.name + " est de nouveau vérifiée." : org.name + " : e-mail professionnel non confirmé, pas de badge.") : org.name, "ok");
      await refresh("orgs", "users", "overview", "offers");
    } catch (err) { fail("Entreprise", err); }
  }

  /* ----------------------------------------------------------------- comptes */
  function paintUsers() {
    const q = ($("user-search").value || "").trim();
    const org = ($("user-org").value || "");
    const rows = HireLinkSearch.rank(state.users.filter((u) => userMatches(u, state.userFilter) && (!org || String(u.orgId) === org)), q,
      [{ get: (u) => u.fullName, weight: 3 }, { get: (u) => u.email, weight: 2 }, { get: (u) => u.orgName, weight: 1 }]);
    const body = $("users-body");
    if (!rows.length) {
      body.innerHTML = '<tr><td colspan="6"></td></tr>';
      HireLinkSearch.emptyState(body.firstChild.firstChild, q, { emptyTitle: "Aucun compte ne correspond", onClear: () => { $("user-search").value = ""; $("user-org").value = ""; paintUsers(); } });
      return;
    }
    body.innerHTML = rows.map((u) => {
      const self = u.id === session.userId;
      const acts = self ? '<span class="rc-cell-sub">Votre compte</span>'
        : '<button type="button" class="btn btn-ghost btn-sm" data-user="' + u.id + '" data-act="revoke">Déconnecter</button>' +
          '<button type="button" class="btn ' + (u.active ? "btn-ghost btn-danger-ghost" : "btn-soft") + ' btn-sm" data-user="' + u.id + '" data-act="toggle">' + (u.active ? "Désactiver" : "Réactiver") + "</button>";
      return '<tr><td><div class="rc-logo-row"><span class="rc-orgmark">' + esc(App.initials(u.fullName)) + '</span><div class="rc-cell-main"><strong>' + esc(u.fullName) + (self ? " (vous)" : "") +
        '</strong><span class="rc-cell-sub">' + esc(u.email) + "</span></div></div></td><td>" + esc(ROLE_FR[u.role] || u.role) +
        (u.orgRole ? ' <span class="rc-pill ' + (ORG_ROLE_TONE[u.orgRole] || "") + '">' + esc(ORG_ROLE_FR[u.orgRole] || u.orgRole) + "</span>" : "") + "</td>" +
        "<td>" + (u.orgName ? esc(u.orgName) : '<span class="rc-cell-sub">—</span>') + "</td>" +
        "<td>" + esc(u.createdAt ? App.fmtDate(u.createdAt) : "—") + "</td>" +
        '<td><span class="rc-pill ' + (u.active ? "rc-pill--ok" : "rc-pill--danger") + '">' + (u.active ? "Actif" : "Désactivé") + "</span>" +
        (u.mustSetPassword ? ' <span class="rc-pill rc-pill--info">Invitation en attente</span>' : "") + "</td>" +
        '<td><div class="rc-row-actions">' + acts + "</div></td></tr>";
    }).join("");
    HireLinkSearch.mark(body, q);
  }
  $("users-body").addEventListener("click", async (e) => {
    const b = e.target.closest("[data-user]");
    const u = b && state.users.find((x) => String(x.id) === b.dataset.user);
    if (!u) return;
    if (b.dataset.act === "revoke") {
      const ok = await Mascotte.confirm({
        title: "Déconnecter ce compte ?", description: u.fullName + " · " + u.email + " — toutes ses sessions sont fermées sur tous ses appareils. Le compte reste actif : la personne peut se reconnecter.",
        confirmLabel: "Déconnecter", cancelLabel: "Annuler", danger: true,
      });
      if (!ok) return;
      try {
        const r = await HireLinkAPI.api("/admin/users/" + u.id + "/revoke-sessions", { method: "POST" });
        App.toast("Compte déconnecté", u.email + " · " + App.plural(r.revoked || 0, "session fermée", "sessions fermées"), "ok");
      } catch (err) { fail("Compte", err); }
      return;
    }
    const ok = await Mascotte.confirm({
      title: u.active ? "Désactiver ce compte ?" : "Réactiver ce compte ?",
      description: u.fullName + " · " + u.email + (u.active ? " — la personne est déconnectée et ne peut plus se connecter." : " — la personne pourra de nouveau se connecter."),
      confirmLabel: u.active ? "Désactiver" : "Réactiver", cancelLabel: "Annuler", danger: !!u.active,
    });
    if (!ok) return;
    try {
      await HireLinkAPI.api("/admin/users/" + u.id + "/active", { method: "PATCH", body: { active: !u.active } });
      App.toast(u.active ? "Compte désactivé" : "Compte réactivé", u.email, "ok");
      await refresh("users", "overview");
    } catch (err) { fail("Compte", err); }
  });
  $("user-search").addEventListener("input", App.debounce(paintUsers, 120));
  $("user-org").addEventListener("change", paintUsers);
  /** Liste des entreprises présentes parmi les comptes (filtre). */
  function paintUserOrgs() {
    const sel = $("user-org"), keep = sel.value, seen = new Map();
    state.users.forEach((u) => { if (u.orgId && !seen.has(u.orgId)) seen.set(u.orgId, u.orgName); });
    sel.innerHTML = '<option value="">Toutes les entreprises</option>' + [...seen].sort((a, b) => String(a[1]).localeCompare(String(b[1]), "fr"))
      .map(([id, n]) => '<option value="' + id + '">' + esc(n) + "</option>").join("");
    sel.value = keep && seen.has(Number(keep)) ? keep : "";
  }
  $("user-filter").addEventListener("click", (e) => {
    const b = e.target.closest("[data-f]");
    if (!b) return;
    state.userFilter = b.dataset.f;
    $("user-filter").querySelectorAll(".rc-chip").forEach((c) => c.classList.toggle("is-on", c === b));
    paintUsers();
  });

  /* ------------------------------------------------------------- modération */
  const DECISION_FR = { FONDE: "Sanction confirmée", ECARTE: "Signalement écarté" };

  function paintModeration() {
    const queue = state.reports.queue || [];
    const box = $("mod-queue");
    $("mod-note").textContent = queue.length
      ? App.plural(queue.length, "offre signalée", "offres signalées") + " · "
        + App.plural(state.reports.pendingCount || 0, "signalement à trancher", "signalements à trancher")
      : "Aucun signalement en attente.";
    if (!queue.length) {
      box.innerHTML = '<div class="db-empty">' + (window.HLIcons ? HLIcons.svg("check") : "") +
        "<strong>Rien à modérer</strong><span>Les offres signalées par les candidats arrivent ici, avec tous leurs signalements.</span></div>";
    } else {
      box.innerHTML = '<div class="rc-list mod-list">' + queue.map((o) => {
        const st = App.offerState(o);
        return '<div class="rc-item mod-item"><div class="rc-item-main"><strong>' + esc(o.title) + "</strong>" +
          '<span class="rc-muted">' + esc(o.organization || "compte individuel") + " · " + esc(o.recruiter) + " · " +
          App.plural(o.applications, "candidature") + "</span>" +
          '<ul class="mod-reasons">' + o.reports.map((r) =>
            "<li><b>" + esc(r.reason) + "</b><span>" + esc(r.reporter) + " · " + esc(App.fmtDate(r.at)) + "</span></li>").join("") + "</ul>" +
          (o.quarantineReason ? '<p class="mod-why">Motif retenu : ' + esc(o.quarantineReason) + "</p>" : "") +
          '</div><div class="mod-actions"><span class="rc-pill ' + (st.key === "suspended" ? "rc-pill--danger" : st.key === "online" ? "rc-pill--ok" : "") + '">' + esc(st.label) + "</span>" +
          (st.key === "suspended"
            ? '<button type="button" class="btn btn-primary btn-sm" data-mod="restore" data-id="' + o.id + '">Rétablir l’offre</button>'
            : '<button type="button" class="btn btn-soft btn-sm" data-mod="suspend" data-id="' + o.id + '">Suspendre l’offre</button>') +
          '<button type="button" class="btn btn-ghost btn-sm" data-mod="detail" data-id="' + o.id + '">Voir les détails</button>' +
          '<button type="button" class="btn btn-ghost btn-sm" data-mod="dismiss" data-id="' + o.id + '">' +
          (o.reports.length > 1 ? "Écarter les signalements" : "Écarter le signalement") + "</button>" +
          "</div></div>";
      }).join("") + "</div>";
    }
    const done = state.reports.handled || [];
    $("mod-handled").innerHTML = done.length
      ? done.map((r) => "<tr><td><strong>" + esc(r.offerTitle) + "</strong></td><td>" + esc(r.reason || "—") +
        '</td><td><span class="rc-pill ' + (r.decision === "FONDE" ? "rc-pill--danger" : "rc-pill--ok") + '">' + esc(DECISION_FR[r.decision] || r.decision) + "</span></td><td>" +
        esc(r.resolvedAt ? App.fmtDateTime(r.resolvedAt) : "—") + "</td><td class=\"rc-muted\">" + esc(r.handledBy || "—") + "</td></tr>").join("")
      : '<tr><td colspan="5" class="adm-empty-cell">Aucune décision enregistrée pour le moment.</td></tr>';
  }

  $("mod-queue").addEventListener("click", async (e) => {
    const btn = e.target.closest("[data-mod]");
    const id = btn && Number(btn.dataset.id);
    if (!id) return;
    const offer = (state.reports.queue || []).find((o) => o.id === id);
    if (!offer) return;
    try {
      if (btn.dataset.mod === "detail") {
        openOfferDetail(offer);
        return;
      }
      if (btn.dataset.mod === "suspend") {
        const reason = await Mascotte.confirm({
          title: "Suspendre cette offre ?",
          description: "« " + offer.title + " » quitte la vitrine et n'accepte plus de candidature. Le recruteur est prévenu avec le motif.",
          confirmLabel: "Suspendre", cancelLabel: "Y renoncer", danger: true,
          field: { label: "Motif visible par le recruteur", placeholder: "Annonce qui demande un paiement avant l'embauche", maxlength: 200, required: true },
        });
        if (!reason) return;
        await HireLinkAPI.api("/admin/offers/" + id + "/suspend", { method: "POST", body: { reason } });
        App.toast("Offre suspendue", offer.title, "danger");
      } else if (btn.dataset.mod === "restore") {
        const ok = await Mascotte.confirm({
          title: "Rétablir cette offre ?",
          description: "La suspension est levée. L'offre reste hors ligne : le recruteur la republie lui-même.",
          confirmLabel: "Rétablir", cancelLabel: "Garder suspendue",
        });
        if (!ok) return;
        await HireLinkAPI.api("/admin/offers/" + id + "/restore", { method: "POST" });
        App.toast("Offre rétablie", offer.title, "ok");
      } else {
        const ok = await Mascotte.confirm({
          title: "Écarter ce signalement ?",
          description: "Aucune sanction : l'offre garde son état et les signalements sont clos comme non confirmés.",
          confirmLabel: "Écarter", cancelLabel: "Y réfléchir",
        });
        if (!ok) return;
        const r = await HireLinkAPI.api("/admin/offers/" + id + "/dismiss-reports", { method: "POST" });
        App.toast("Signalements écartés", App.plural(r.handled || 0, "signalement clôturé", "signalements clôturés"), "ok");
      }
      await refresh("reports", "offers");
    } catch (err) { fail("Modération", err); }
  });
  $("mod-refresh").addEventListener("click", () => refresh("reports", "offers"));

  /* ------------------------------------------------------------------ offres */
  function paintOffers() {
    const q = ($("offer-search").value || "").trim();
    const f = state.offerFilter;
    const rows = HireLinkSearch.rank(state.offers.filter((o) => {
      if (f === "all") return true;
      return App.offerState(o).key === f;
    }), q, [{ get: (o) => o.title, weight: 3 }, { get: (o) => o.organization, weight: 2 }, { get: (o) => o.recruiter, weight: 2 }, { get: (o) => o.location, weight: 1 }]);
    const body = $("offers-body");
    if (!rows.length) {
      body.innerHTML = '<tr><td colspan="6"></td></tr>';
      HireLinkSearch.emptyState(body.firstChild.firstChild, q, { emptyTitle: "Aucune offre ne correspond", onClear: () => { $("offer-search").value = ""; paintOffers(); } });
      return;
    }
    body.innerHTML = rows.map((o) => {
      const st = App.offerState(o);
      const acts = st.key === "suspended"
        ? '<button type="button" class="btn btn-soft btn-sm" data-offer="restore" data-id="' + o.id + '">Rétablir</button>'
        : o.published
          ? '<button type="button" class="btn btn-ghost btn-sm" data-offer="unpublish" data-id="' + o.id + '">Dépublier</button>' +
            '<button type="button" class="btn btn-ghost btn-sm" data-offer="suspend" data-id="' + o.id + '">Suspendre</button>'
          : "";
      return '<tr><td><div class="rc-cell-main"><strong>' + esc(o.title) + "</strong>" +
        '<span class="rc-cell-sub">' + esc([o.recruiter, o.location].filter(Boolean).join(" · ") || "—") + "</span></div></td>" +
        "<td>" + (o.organization ? esc(o.organization) : '<span class="rc-cell-sub">compte individuel</span>') + "</td>" +
        '<td><span class="rc-pill ' + (st.key === "online" ? "rc-pill--ok" : st.key === "suspended" ? "rc-pill--danger" : "") + '"' +
        (o.quarantineReason ? ' title="' + esc(o.quarantineReason) + '"' : "") + ">" + esc(st.label) + "</span></td>" +
        "<td>" + o.applications + "</td>" +
        '<td><div class="rc-row-actions"><button type="button" class="btn btn-soft btn-sm" data-offer="detail" data-id="' + o.id + '">Voir les détails</button>' + acts + "</div></td></tr>";
    }).join("");
    HireLinkSearch.mark(body, q);
  }

  function openOfferDetail(offer) {
    if (!offer) return;
    const st = App.offerState(offer);
    const html =
      '<dl class="admin-detail">' +
      "<div><dt>Titre</dt><dd>" + esc(offer.title) + "</dd></div>" +
      "<div><dt>Entreprise</dt><dd>" + esc(offer.organization || "compte individuel") + "</dd></div>" +
      "<div><dt>Recruteur</dt><dd>" + esc(offer.recruiter || "—") + "</dd></div>" +
      "<div><dt>Lieu</dt><dd>" + esc(offer.location || "—") + "</dd></div>" +
      "<div><dt>État</dt><dd>" + esc(st.label) + "</dd></div>" +
      "<div><dt>Candidatures</dt><dd>" + esc(String(offer.applications || 0)) + "</dd></div>" +
      (offer.quarantineReason ? "<div><dt>Motif de suspension</dt><dd>" + esc(offer.quarantineReason) + "</dd></div>" : "") +
      (offer.description ? "<div><dt>Description</dt><dd class='admin-detail-body'>" + esc(offer.description).replace(/\n/g, "<br/>") + "</dd></div>" : "") +
      "</dl>";
    HireLink.openModal({
      title: "Détail de l’offre",
      description: "Lecture seule avant décision de modération.",
      bodyHtml: html,
      confirmLabel: "Fermer",
      hideCancel: true,
      large: true,
    });
  }

  $("offers-body").addEventListener("click", async (e) => {
    const btn = e.target.closest("[data-offer]");
    const id = btn && Number(btn.dataset.id);
    if (!id) return;
    const offer = state.offers.find((o) => o.id === id);
    if (!offer) return;
    try {
      if (btn.dataset.offer === "detail") {
        openOfferDetail(offer);
        return;
      }
      if (btn.dataset.offer === "suspend") {
        const reason = await Mascotte.confirm({
          title: "Suspendre cette offre ?",
          description: "« " + offer.title + " » quitte la vitrine. Le recruteur reçoit le motif que vous indiquez.",
          confirmLabel: "Suspendre", cancelLabel: "Y renoncer", danger: true,
          field: { label: "Motif visible par le recruteur", placeholder: "Coordonnées bancaires demandées dans l'annonce", maxlength: 200, required: true },
        });
        if (!reason) return;
        await HireLinkAPI.api("/admin/offers/" + id + "/suspend", { method: "POST", body: { reason } });
        App.toast("Offre suspendue", offer.title, "danger");
      } else if (btn.dataset.offer === "restore") {
        const ok = await Mascotte.confirm({
          title: "Rétablir cette offre ?",
          description: "« " + offer.title + " » n'est plus suspendue. Elle reste hors ligne jusqu'à ce que le recruteur la publie.",
          confirmLabel: "Rétablir", cancelLabel: "Garder suspendue",
        });
        if (!ok) return;
        await HireLinkAPI.api("/admin/offers/" + id + "/restore", { method: "POST" });
        App.toast("Offre rétablie", offer.title, "ok");
      } else {
        const ok = await Mascotte.confirm({
          title: "Retirer cette offre de la vitrine ?",
          description: "Les candidats ne la voient plus ; le recruteur peut la republier lui-même.",
          confirmLabel: "Dépublier", cancelLabel: "Laisser en ligne",
        });
        if (!ok) return;
        await HireLinkAPI.api("/admin/offers/" + id + "/unpublish", { method: "POST" });
        App.toast("Offre dépubliée", offer.title, "warn");
      }
      await refresh("offers", "reports");
    } catch (err) { fail("Offre", err); }
  });
  $("offer-search").addEventListener("input", App.debounce(paintOffers, 120));
  $("offer-filter").addEventListener("click", (e) => {
    const b = e.target.closest("[data-f]");
    if (!b) return;
    state.offerFilter = b.dataset.f;
    $("offer-filter").querySelectorAll(".rc-chip").forEach((c) => c.classList.toggle("is-on", c === b));
    paintOffers();
  });

  /* ------------------------------------------------- recherche transversale */
  function searchGroups(r) {
    return [
      { title: "Comptes", rows: r.users, main: (u) => u.fullName, sub: (u) => (ROLE_FR[u.role] || u.role) + " · " + u.email + (u.active ? "" : " · désactivé"), go: () => ["users", "user-search", r.term] },
      { title: "Entreprises", rows: r.organizations, main: (o) => o.name, sub: (o) => "ICE " + (o.ice || "—") + " · " + (o.city || "—"), go: () => ["orgs", "org-search", r.term] },
      { title: "Offres", rows: r.offers, main: (o) => o.title, sub: (o) => (o.organization || "compte individuel") + " · " + App.offerState(o).label + " · " + App.plural(o.applications, "candidature"), go: () => ["offers", "offer-search", r.term] },
      { title: "Candidatures", rows: r.applications, main: (a) => a.candidate + " → " + a.offerTitle, sub: (a) => (STATUS_FR[a.status] || a.status) + " · " + a.candidateEmail + " · " + App.fmtDate(a.submittedAt), go: () => ["offers", "offer-search", ""] },
    ];
  }

  async function runSearch() {
    const q = ($("gs-query").value || "").trim();
    const box = $("gs-results");
    if (q.length < 2) { box.innerHTML = ""; return; }
    let r;
    try {
      r = await HireLinkAPI.api("/admin/search?q=" + encodeURIComponent(q), { noDedupe: true });
    } catch (err) {
      box.innerHTML = '<p class="rc-muted">' + esc(err.message || "Recherche impossible") + "</p>";
      return;
    }
    state.lastSearch = r;
    const groups = searchGroups(r).filter((g) => g.rows && g.rows.length);
    if (!groups.length) {
      box.innerHTML = '<p class="rc-muted">Rien qui ressemble à « ' + esc(q) + " ».</p>";
      return;
    }
    box.innerHTML = groups.map((g) => '<div class="gs-group"><h3>' + esc(g.title) + " · " + g.rows.length + "</h3>" +
      '<div class="rc-list">' + g.rows.slice(0, 6).map((row, i) =>
        '<div class="rc-item"><div class="rc-item-main"><strong>' + esc(g.main(row)) + '</strong><span class="rc-muted">' + esc(g.sub(row)) +
        '</span></div><button type="button" class="btn btn-soft btn-sm" data-gs="' + groups.indexOf(g) + ":" + i + '">Ouvrir</button></div>').join("") +
      "</div></div>").join("");
  }
  /** Ouvrir le bon onglet avec la recherche déjà posée : l'admin rebondit, il ne recopie pas. */
  $("gs-results").addEventListener("click", (e) => {
    const b = e.target.closest("[data-gs]");
    if (!b) return;
    const [gi, ri] = b.dataset.gs.split(":").map(Number);
    const groups = searchGroups(state.lastSearch || { users: [], organizations: [], offers: [], applications: [], term: "" }).filter((g) => g.rows && g.rows.length);
    const g = groups[gi];
    if (!g) return;
    const [view, inputId, term] = g.go();
    if (inputId) $(inputId).value = term;
    show(view);
    if (view === "orgs") paintOrgs();
    if (view === "users") paintUsers();
    if (view === "offers") paintOffers();
  });
  $("gs-query").addEventListener("input", App.debounce(runSearch, 250));

  /* ------------------------------------------------------- formules et factures */
  function setPlanPane(pane) {
    if (["plans", "invoices", "ai"].indexOf(pane) < 0) return;
    state.planPane = pane;
    $("plans-switch").querySelectorAll("[data-pv]").forEach((b) => {
      const on = b.dataset.pv === pane;
      b.classList.toggle("is-on", on);
      b.setAttribute("aria-selected", on ? "true" : "false");
    });
    document.querySelectorAll("#view-plans [data-pane]").forEach((p) => { p.hidden = p.dataset.pane !== pane; });
  }
  $("plans-switch").addEventListener("click", (e) => {
    const b = e.target.closest("[data-pv]");
    if (b) setPlanPane(b.dataset.pv);
  });

  function subscribersOf(code) {
    const dist = (state.overview && state.overview.distribution) || {};
    const row = (dist.companies || []).concat(dist.candidates || []).find((r) => r.code === code);
    return row ? row.count : null;
  }

  function paintPlans() {
    $("plan-cards").innerHTML = state.plans.map((p) => {
      const count = subscribersOf(p.code);
      const feats = String(p.features || "").split("\n").map((l) => l.trim()).filter(Boolean);
      const who = p.audience === "CANDIDAT" ? "candidat" : "entreprise";
      return '<article class="rc-card rc-fade"><div class="rc-card-head"><div><h2>' + esc(p.label) + "</h2><p>" + (p.audience === "CANDIDAT" ? "Candidats" : "Entreprises") +
        '</p></div><button type="button" class="btn btn-soft btn-sm" data-plan="' + esc(p.code) + '">Modifier</button></div><div class="rc-card-body">' +
        "<p class='adm-plan-price'><strong>" + (p.monthlyMad ? p.monthlyMad + "</strong> MAD / mois <span class='rc-muted'>· " + p.yearlyMad + " MAD / an" : "Gratuite</strong> <span class='rc-muted'>") + " · " + p.credits + " crédits IA / mois</span></p>" +
        "<ul class='adm-plan-feats'>" + (feats.map((f) => "<li>" + esc(f) + "</li>").join("") || "<li class='rc-muted'>Aucune ligne de description</li>") + "</ul>" +
        (count == null ? "" : "<p class='rc-muted adm-plan-count'>" + App.plural(count, who + " active", who + "s actives") + " sur cette formule</p>") + "</div></article>";
    }).join("");
  }
  $("plan-cards").addEventListener("click", (e) => {
    const b = e.target.closest("[data-plan]");
    if (b) editPlan(b.dataset.plan);
  });

  async function editPlan(code) {
    const p = state.plans.find((x) => x.code === code);
    if (!p) return;
    const ok = await HireLink.openModal({
      title: "Modifier « " + p.label + " »", description: "Nom, prix, crédits IA et fonctionnalités.", confirmLabel: "Enregistrer", large: true,
      bodyHtml: "<div class='rc-form'><div class='rc-field is-full'><label for='plan-label'>Nom</label><input id='plan-label' value='" + esc(p.label) + "' /></div>" +
        "<div class='rc-field'><label for='plan-month'>Prix mensuel (MAD)</label><input id='plan-month' type='number' min='0' value='" + p.monthlyMad + "' /></div>" +
        "<div class='rc-field'><label for='plan-year'>Prix annuel (MAD)</label><input id='plan-year' type='number' min='0' value='" + p.yearlyMad + "' /></div>" +
        "<div class='rc-field'><label for='plan-credits'>Crédits IA</label><input id='plan-credits' type='number' min='0' value='" + p.credits + "' /></div>" +
        "<div class='rc-field is-full'><label for='plan-features'>Fonctionnalités (une par ligne)</label><textarea id='plan-features' rows='5'>" + esc(p.features || "") + "</textarea></div></div>",
    });
    if (!ok) return;
    try {
      await HireLinkAPI.api("/admin/plans/" + code, { method: "PATCH", body: {
        label: $("plan-label").value, monthlyMad: Number($("plan-month").value || 0), yearlyMad: Number($("plan-year").value || 0),
        credits: Number($("plan-credits").value || 0), features: $("plan-features").value } });
      App.toast("Plan enregistré", p.label, "ok");
      await refresh("plans", "aiUsage");
    } catch (err) { fail("Plan", err); }
  }


  /* --------------------------------------------------- consommation IA du mois */
  function paintAiUsage() {
    const body = $("ai-usage-body");
    if (!body) return;
    const data = state.aiUsage || {};
    const rows = data.rows || [];
    const used = rows.reduce((a, r) => a + Number(r.credits || 0), 0);
    const envelope = rows.reduce((a, r) => a + Number(r.limit || 0), 0) + Number(data.quietEnvelope || 0);
    const near = rows.filter((r) => r.limit && r.credits / r.limit >= 0.8).length;
    const k = (label, value, hint) => '<div class="adm-kpi"><small>' + esc(label) + "</small><b>" + esc(value) + "</b><span>" + esc(hint) + "</span></div>";
    $("ai-kpis").innerHTML =
      k("Crédits utilisés ce mois", num(used), envelope ? Math.round((used / envelope) * 100) + " % de l’ensemble des crédits disponibles" : "Aucun crédit disponible") +
      k("Comptes qui utilisent l’IA", num(rows.length), App.plural(Number(data.quietOrganizations || 0), "entreprise sans utilisation", "entreprises sans utilisation")) +
      k("Proches de la limite", num(near), "80 % ou plus de leurs crédits du mois");
    if (!rows.length) {
      body.innerHTML = '<tr><td colspan="6" class="adm-empty-cell">Aucune utilisation de l’IA ce mois-ci.</td></tr>';
      return;
    }
    body.innerHTML = rows.map((r) => {
      const seats = Math.max(1, Number(r.seats || 0));
      const limit = Number(r.limit || 0);
      const usedR = Number(r.credits || 0);
      const pct = limit ? Math.min(100, Math.round((usedR / limit) * 100)) : 0;
      const tone = pct >= 100 ? " is-out" : pct >= 80 ? " is-high" : "";
      const who = (r.people || []).slice(0, 3).map((p) => esc(r.organization === p.fullName ? p.email : p.fullName) + " (" + p.credits + ")").join(" · ");
      const more = (r.people || []).length > 3 ? " <span class='rc-muted'>+ " + ((r.people || []).length - 3) + "</span>" : "";
      return "<tr><td><strong>" + esc(r.organization) + "</strong>" +
        (who ? "<div class='rc-cell-sub'>" + who + more + "</div>" : "") +
        "</td><td>" + esc(r.planLabel) + "</td><td>" + seats + "</td><td>" + limit + "</td><td>" + r.actions +
        '</td><td><span class="rc-meter-val">' + usedR + " / " + limit + " · " + pct + " %</span><div class=\"rc-meter" + tone + '" data-w="' + pct + '"><i></i></div></td></tr>';
    }).join("");
    // Largeur posée par le CSSOM (aucun style en ligne dans le HTML généré).
    body.querySelectorAll(".rc-meter[data-w]").forEach((m) => m.style.setProperty("--w", m.dataset.w + "%"));
  }

  function paintInvoices() {
    const body = $("invoices-body");
    const q = (($("invoice-q") && $("invoice-q").value) || "").trim().toLowerCase();
    const rows = state.invoices.filter((r) => (state.invoiceFilter === "all" || r.status === state.invoiceFilter)
      && (!q || (r.number + " " + (r.organization || "") + " " + (r.debtorName || "") + " " + (r.debtorEmail || "")).toLowerCase().includes(q)));
    if (!state.invoices.length) {
      body.innerHTML = '<tr><td colspan="7" class="adm-empty-cell">Aucune facture.</td></tr>';
      return;
    }
    if (!rows.length) {
      body.innerHTML = '<tr><td colspan="7" class="adm-empty-cell">Aucune facture dans ce filtre.</td></tr>';
      return;
    }
    body.innerHTML = rows.map((r) => {
      const tone = r.status === "PAYEE" ? "rc-pill--ok" : r.status === "NON_PAYEE" ? "rc-pill--warn" : "";
      const acts = '<button type="button" class="btn btn-soft btn-sm" data-pdf="' + r.id + '" data-mode="view">Voir</button>' +
        '<button type="button" class="btn btn-ghost btn-sm" data-pdf="' + r.id + '" data-mode="download">Télécharger</button>' +
        '<button type="button" class="btn btn-ghost btn-sm" data-pdf="' + r.id + '" data-mode="print">Imprimer</button>';
      return "<tr><td>" + esc(r.number) + "</td><td>" + esc(App.fmtDate(r.issuedAt)) + "</td><td>" + esc(r.organization || "—") +
        "</td><td>" + esc(r.debtorName || "—") + (r.debtorEmail ? '<div class="rc-cell-sub">' + esc(r.debtorEmail) + "</div>" : "") +
        "</td><td>" + money(r.amountTtc) + '</td><td><span class="rc-pill ' + tone + '">' + esc(PAY_FR[r.status] || r.status) + "</span></td>" +
        '<td><div class="rc-row-actions">' + acts + "</div></td></tr>";
    }).join("");
    const paid = state.invoices.filter((r) => r.status === "PAYEE").reduce((s, r) => s + Number(r.amountTtc || 0), 0);
    const due = state.invoices.filter((r) => r.status === "NON_PAYEE").reduce((s, r) => s + Number(r.amountTtc || 0), 0);
    $("invoices-note").textContent = money(paid) + " payés · reste à recevoir " + money(due);
  }
  $("invoice-filter").addEventListener("click", (e) => {
    const b = e.target.closest("[data-f]");
    if (!b) return;
    state.invoiceFilter = b.dataset.f;
    $("invoice-filter").querySelectorAll(".rc-chip").forEach((c) => c.classList.toggle("is-on", c === b));
    paintInvoices();
  });
  if ($("invoice-q")) $("invoice-q").addEventListener("input", paintInvoices);
  $("invoices-body").addEventListener("click", async (e) => {
    const btn = e.target.closest("[data-pdf]");
    if (!btn) return;
    try {
      const res = await HireLinkAPI.authFetch("/admin/invoices/" + btn.dataset.pdf + "/pdf");
      if (!res.ok) throw new Error("PDF indisponible");
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      if (btn.dataset.mode === "download") {
        const a = document.createElement("a");
        a.href = url;
        a.download = "facture-" + btn.dataset.pdf + ".pdf";
        a.click();
      } else {
        if (btn.dataset.mode === "view") {
          HireLink.openModal({
            title: "Aperçu de facture",
            description: "Consultez le PDF, puis téléchargez-le ou fermez cette fenêtre.",
            confirmLabel: "Fermer",
            cancelLabel: "Télécharger",
            large: true,
            bodyHtml: '<div class="adm-invoice-preview"><iframe src="' + esc(url) + '" title="Aperçu du PDF"></iframe></div>',
            onCancel: () => {
              const a = document.createElement("a");
              a.href = url;
              a.download = "facture-" + btn.dataset.pdf + ".pdf";
              a.click();
            },
          });
        } else {
          const w = window.open(url);
          if (btn.dataset.mode === "print" && w) w.addEventListener("load", () => { try { w.print(); } catch (_) {} });
        }
      }
      setTimeout(() => URL.revokeObjectURL(url), 60000);
    } catch (err) { fail("Facture", err); }
  });


  /* --------------------------------------------------- filtres de période partagés */
  const isoDay = (d) => d.getFullYear() + "-" + String(d.getMonth() + 1).padStart(2, "0") + "-" + String(d.getDate()).padStart(2, "0");
  function wireRange(prefix, reload) {
    const box = document.querySelector('[data-range="' + prefix + '"]');
    const from = $(prefix + "-from"), to = $(prefix + "-to");
    const mark = (days) => box.querySelectorAll("button").forEach((b) => b.classList.toggle("is-on", days != null && b.dataset.days === String(days)));
    box.addEventListener("click", (e) => {
      const b = e.target.closest("[data-days]");
      if (!b) return;
      const days = Number(b.dataset.days);
      if (days) {
        const start = new Date();
        start.setDate(start.getDate() - (days - 1));
        from.value = isoDay(start);
        to.value = isoDay(new Date());
      } else { from.value = ""; to.value = ""; }
      mark(days);
      reload(true);
    });
    [from, to].forEach((i) => i.addEventListener("change", () => {
      if (from.value && to.value && from.value > to.value) {
        App.toast("Période", "La date de début doit précéder la date de fin.", "warn");
        return;
      }
      mark(null);
      reload(true);
    }));
    return { mark, qs: () => (from.value ? "&from=" + from.value : "") + (to.value ? "&to=" + to.value : "") };
  }

  function pager(el, page, pages, total, unit, go) {
    const p = Math.max(1, pages || 1);
    el.innerHTML = "<span>" + App.plural(total || 0, unit, unit + "s") + (total ? " · page " + (page + 1) + " sur " + p : "") + "</span>" +
      '<span class="adm-pager-nav"><button type="button" class="btn btn-ghost btn-sm" data-pg="prev"' + (page > 0 ? "" : " disabled") + ">Précédente</button>" +
      '<button type="button" class="btn btn-ghost btn-sm" data-pg="next"' + (page + 1 < p ? "" : " disabled") + ">Suivante</button></span>";
    el.querySelector('[data-pg="prev"]').onclick = () => go(page - 1);
    el.querySelector('[data-pg="next"]').onclick = () => go(page + 1);
  }

  /* ----------------------------------------------------------- journal d'audit */
  const AUDIT_CATS = [
    { key: "", label: "Tout", cls: "other" },
    { key: "auth", label: "Connexions et sécurité", cls: "auth" },
    { key: "account", label: "Comptes", cls: "account" },
    { key: "org", label: "Équipes", cls: "org" },
    { key: "offer", label: "Offres et candidatures", cls: "offer" },
    { key: "billing", label: "Facturation", cls: "billing" },
    { key: "admin", label: "Administration", cls: "admin" },
  ];
  const AUDIT_FR = {
    auth: {
      LOGIN_SUCCESS: "Connexion", LOGIN_FAILURE: "Connexion refusée", LOGIN_LOCKED: "Connexion bloquée temporairement", LOGOUT: "Déconnexion",
      LOGOUT_ALL: "Déconnexion de tous les appareils", OTP_FAILURE: "Code de connexion erroné", MFA_FAILURE: "Double authentification refusée",
      MFA_ENABLED: "Double authentification activée", MFA_DISABLED: "Double authentification désactivée", MFA_BACKUP_CODE_USED: "Code de secours utilisé",
      MFA_BACKUP_CODES_REGENERATED: "Codes de secours renouvelés", PASSKEY_ADDED: "Clé d’accès ajoutée", PASSKEY_ENROLL: "Clé d’accès enregistrée",
      PASSKEY_REMOVED: "Clé d’accès supprimée", SESSION_REVOKED: "Appareil déconnecté", SESSION_TOKEN_REUSE: "Session suspecte bloquée",
      REAUTH_FAILURE: "Confirmation d’identité refusée", PASSWORD_CHANGE: "Mot de passe modifié", PASSWORD_RESET: "Mot de passe réinitialisé",
      PASSWORD_RESET_REQUEST: "Réinitialisation demandée", PASSWORD_SET: "Mot de passe défini", ACCESS_DENIED: "Accès refusé",
    },
    account: {
      REGISTER: "Inscription", PROFILE_UPDATED: "Profil modifié", EMAIL_CHANGED: "E-mail modifié", EMAIL_CHANGE_REQUESTED: "Changement d’e-mail demandé",
      AVATAR_UPDATED: "Photo modifiée", AVATAR_REMOVED: "Photo supprimée", CONSENT_ACCEPTED: "Conditions acceptées", DATA_EXPORT: "Export des données personnelles",
      ACCOUNT_DEACTIVATED: "Compte fermé par son titulaire", ACCOUNT_ERASED: "Compte supprimé", ROLE_CHANGE: "Rôle modifié",
    },
    org: {
      MEMBER_INVITED: "Membre invité", MEMBER_JOINED: "Membre arrivé", MEMBER_REINSTATED: "Membre rétabli", MEMBER_RELEASED: "Membre libéré",
      MEMBER_REMOVED: "Membre retiré", MEMBER_ROLE_CHANGED: "Rôle d’équipe modifié", MEMBER_SUSPENDED: "Membre suspendu", INVITATION_SENT: "Invitation envoyée",
      INVITATION_REVOKED: "Invitation annulée", OWNER_TRANSFERRED: "Espace transmis à un autre créateur",
    },
    offer: {
      OFFER_PUBLISHED: "Offre publiée", OFFER_UPDATED: "Offre modifiée", OFFER_CLOSED: "Offre clôturée", OFFER_REOPENED: "Offre rouverte",
      OFFER_ARCHIVED: "Offre archivée", OFFER_UNARCHIVED: "Offre désarchivée", OFFER_DELETED: "Offre supprimée", OFFER_DUPLICATED: "Offre dupliquée",
      OFFER_DEADLINE_EXTENDED: "Date limite prolongée", OFFER_SUSPENDED: "Offre suspendue", OFFER_RESTORED: "Offre rétablie", OFFER_UNPUBLISHED: "Offre retirée de la vitrine",
      OFFER_REPORTS_DISMISSED: "Signalements écartés", APPLICATION_REFUSED: "Candidature refusée", STAGE_CHANGE: "Étape de candidature modifiée",
      INTERVIEW_CANCELLED: "Entretien annulé",
    },
    billing: {
      INVOICE_CREATED: "Facture émise", INVOICE_STATUS_CHANGED: "Statut de facture modifié", PAYMENT_SUCCEEDED: "Paiement accepté", PAYMENT_FAILED: "Paiement échoué",
      PAYMENT_DENIED: "Paiement refusé", SUBSCRIPTION_ACTIVATED: "Formule activée", SUBSCRIPTION_CANCEL_REQUESTED: "Résiliation demandée", SUBSCRIPTION_ENDED: "Formule terminée",
    },
    admin: {
      USER_SUSPENDED: "Compte désactivé par l’administration", USER_REACTIVATED: "Compte réactivé par l’administration", ADMIN_REVOKE_SESSIONS: "Compte déconnecté par l’administration",
      ORG_VERIFIED: "Entreprise vérifiée", ORG_VERIFICATION_REMOVED: "Badge d’entreprise retiré", ORG_VERIFICATION_RECHECKED: "Vérification relancée",
      ORG_DEACTIVATED: "Entreprise désactivée", ORG_REACTIVATED: "Entreprise réactivée", ORG_REMOVED: "Entreprise retirée", AUDIT_EXPORTED: "Export du journal",
    },
  };
  const ACTION_INFO = {};
  Object.keys(AUDIT_FR).forEach((cat) => Object.keys(AUDIT_FR[cat]).forEach((code) => { ACTION_INFO[code] = { cat, label: AUDIT_FR[cat][code] }; }));
  const TARGET_FR = { USER: "Compte", OFFER: "Offre", ORGANIZATION: "Entreprise", INVOICE: "Facture", APPLICATION: "Candidature", AUDIT_LOG: "Journal", INTERVIEW: "Entretien", SESSION: "Session" };
  const ROLE_SHORT = { CANDIDAT: "candidat", RECRUTEUR: "recruteur", ADMINISTRATEUR: "administration" };
  function targetFr(t) {
    if (!t) return "—";
    const [type, id] = String(t).split(":");
    return (TARGET_FR[type] || "Élément") + (id && id !== "null" ? " n° " + id : "");
  }

  const audit = { page: 0, cat: "" };
  $("audit-cat").innerHTML = AUDIT_CATS.map((c, i) => '<button type="button" class="rc-chip' + (i ? "" : " is-on") + '" data-cat="' + c.key + '">' + esc(c.label) + "</button>").join("");
  $("audit-cat").addEventListener("click", (e) => {
    const b = e.target.closest("[data-cat]");
    if (!b) return;
    audit.cat = b.dataset.cat;
    $("audit-cat").querySelectorAll(".rc-chip").forEach((c) => c.classList.toggle("is-on", c === b));
    loadAudit(true);
  });
  function auditQuery() {
    const codes = audit.cat ? Object.keys(AUDIT_FR[audit.cat]) : [];
    const q = ($("audit-q").value || "").trim();
    return (codes.length ? "&actions=" + encodeURIComponent(codes.join(",")) : "") + auditRange.qs() +
      ($("audit-result").value ? "&result=" + $("audit-result").value : "") + (q ? "&q=" + encodeURIComponent(q) : "");
  }
  async function loadAudit(reset) {
    if (reset === true) audit.page = 0;
    const body = $("audit-body");
    body.innerHTML = '<tr><td colspan="6" class="adm-empty-cell">Chargement…</td></tr>';
    try {
      const r = await HireLinkAPI.api("/admin/audit?page=" + audit.page + "&size=25" + auditQuery(), { noDedupe: true });
      const rows = r.rows || [];
      body.innerHTML = rows.length
        ? rows.map((x) => {
            const info = ACTION_INFO[x.action] || { cat: "other", label: "Autre opération" };
            return "<tr><td>" + esc(App.fmtDateTime(x.at)) + '</td><td><span class="adm-tag adm-tag--' + info.cat + (x.success ? "" : " is-fail") + '">' + esc(info.label) + "</span></td><td>" +
              esc(x.actor || "—") + (x.actorRole ? '<div class="rc-cell-sub">' + esc(ROLE_SHORT[x.actorRole] || "") + "</div>" : "") + "</td><td>" + esc(targetFr(x.target)) +
              '</td><td><span class="adm-result ' + (x.success ? "is-ok" : "is-fail") + '">' + (x.success ? "Réussi" : "Échec") + '</span></td><td class="adm-detail">' + esc(x.detail || "") + "</td></tr>";
          }).join("")
        : '<tr><td colspan="6" class="adm-empty-cell">Aucun évènement pour ces filtres.</td></tr>';
      pager($("audit-pager"), r.page || 0, r.pages, r.total, "évènement", (p) => { audit.page = Math.max(0, p); loadAudit(); });
    } catch (err) {
      body.innerHTML = '<tr><td colspan="6"></td></tr>';
      App.errorState(body.firstChild.firstChild, err.message, loadAudit);
    }
  }
  const auditRange = wireRange("audit", loadAudit);
  $("audit-result").addEventListener("change", () => loadAudit(true));
  $("audit-q").addEventListener("input", App.debounce(() => loadAudit(true), 300));
  $("audit-refresh").addEventListener("click", () => loadAudit());
  /** authFetch, pas un lien : l'en-tête d'autorisation ne voyage pas dans une navigation. */
  $("audit-export").addEventListener("click", async () => {
    const btn = $("audit-export");
    btn.disabled = true;
    try {
      const res = await HireLinkAPI.authFetch("/admin/audit/export?x=1" + auditQuery());
      if (!res.ok) throw new Error("Export impossible pour le moment.");
      const url = URL.createObjectURL(await res.blob());
      const a = document.createElement("a");
      a.href = url;
      a.download = "journal-audit-hirelink-" + App.localDate() + ".csv";
      document.body.appendChild(a);
      a.click();
      a.remove();
      setTimeout(() => URL.revokeObjectURL(url), 5000);
      App.toast("Export prêt", "Jusqu’à 2 000 évènements, filtres en cours inclus.", "ok");
      loadAudit();
    } catch (err) {
      fail("Export", err);
    }
    btn.disabled = false;
  });

  /* ------------------------------------------------------------ journal des e-mails */
  const MAIL_STATE = { SENT: ["Envoyé", "ok"], LOGGED: ["Non envoyé", "warn"], FAILED: ["Échec", "danger"] };
  const MAIL_KIND = { SECURITY: "Sécurité", ACCOUNT: "Compte", APPLICATION: "Candidature", INTERVIEW: "Entretien" };
  const mail = { page: 0, status: "", kindsLoaded: false };
  function paintMailChips(counts) {
    const c = counts || {};
    const all = Object.keys(c).reduce((a, k) => a + Number(c[k] || 0), 0);
    const chips = [["", "Tous", all]].concat(Object.keys(MAIL_STATE).map((k) => [k, MAIL_STATE[k][0], c[k] || 0]));
    $("mail-status").innerHTML = chips.map((x) => '<button type="button" class="rc-chip' + (mail.status === x[0] ? " is-on" : "") + '" data-st="' + x[0] + '">' + esc(x[1]) +
      '<span class="adm-chip-count">' + num(x[2]) + "</span></button>").join("");
  }
  $("mail-status").addEventListener("click", (e) => {
    const b = e.target.closest("[data-st]");
    if (!b) return;
    mail.status = b.dataset.st;
    loadEmails(true);
  });
  async function loadEmails(reset) {
    if (reset === true) mail.page = 0;
    const body = $("mail-body");
    body.innerHTML = '<tr><td colspan="6" class="adm-empty-cell">Chargement…</td></tr>';
    const q = ($("mail-q").value || "").trim();
    const kind = $("mail-kind").value;
    try {
      const r = await HireLinkAPI.api("/admin/emails?page=" + mail.page + "&size=25" + mailRange.qs() + (mail.status ? "&status=" + mail.status : "") +
        (kind ? "&kind=" + encodeURIComponent(kind) : "") + (q ? "&q=" + encodeURIComponent(q) : ""), { noDedupe: true });
      if (!mail.kindsLoaded) {
        $("mail-kind").innerHTML = '<option value="">Tous les types</option>' + (r.kinds || []).map((k) => '<option value="' + esc(k) + '">' + esc(MAIL_KIND[k] || "Autre") + "</option>").join("");
        mail.kindsLoaded = true;
      }
      paintMailChips(r.counts);
      const rows = r.rows || [];
      body.innerHTML = rows.length
        ? rows.map((x) => {
            const st = MAIL_STATE[x.status] || ["Autre", ""];
            return "<tr><td>" + esc(App.fmtDateTime(x.at)) + "</td><td>" + esc(x.recipient) + "</td><td>" + esc(x.subject) + "</td><td>" + esc(MAIL_KIND[x.kind] || "Autre") +
              '</td><td><span class="rc-pill' + (st[1] ? " rc-pill--" + st[1] : "") + '">' + esc(st[0]) + "</span>" +
              (x.status === "FAILED" && x.error ? '<div class="rc-cell-sub">Envoi refusé par le service de messagerie</div>' : "") + "</td><td>" +
              (x.hasBody ? '<button type="button" class="btn btn-ghost btn-sm" data-mail="' + x.id + '">Voir</button>' : '<span class="rc-cell-sub">Contenu non conservé</span>') + "</td></tr>";
          }).join("")
        : '<tr><td colspan="6" class="adm-empty-cell">Aucun e-mail pour ces filtres.</td></tr>';
      pager($("mail-pager"), r.page || 0, r.pages, r.total, "e-mail", (p) => { mail.page = Math.max(0, p); loadEmails(); });
    } catch (err) {
      body.innerHTML = '<tr><td colspan="6"></td></tr>';
      App.errorState(body.firstChild.firstChild, err.message, loadEmails);
    }
  }
  const mailRange = wireRange("mail", loadEmails);
  $("mail-kind").addEventListener("change", () => loadEmails(true));
  $("mail-q").addEventListener("input", App.debounce(() => loadEmails(true), 300));
  $("mail-refresh").addEventListener("click", () => loadEmails());
  $("mail-body").addEventListener("click", async (e) => {
    const b = e.target.closest("[data-mail]");
    if (!b) return;
    try {
      const m = await HireLinkAPI.api("/admin/emails/" + b.dataset.mail);
      // Aperçu isolé : aucun script ni lien actif ne peut s'exécuter dans le message.
      const frame = document.createElement("iframe");
      frame.setAttribute("sandbox", "");
      frame.title = m.subject;
      frame.className = "mail-preview";
      frame.srcdoc = m.html;
      HireLink.openModal({ title: m.subject, bodyHtml: '<div id="mail-slot"></div>', confirmLabel: "Fermer", hideCancel: true, large: true,
        onReady: (api) => api.root.querySelector("#mail-slot").appendChild(frame) });
    } catch (err) { fail("E-mail", err); }
  });

  /* ------------------------------------------------------------ messages du support */
  const SUP_TOPIC = { QUESTION: "Question", COMPTE: "Compte", ENTREPRISE: "Entreprise", AUTRE: "Autre" };
  const SUP_TONE = { COMPTE: "rc-pill--info", ENTREPRISE: "rc-pill--rose" };
  const sup = { page: 0, status: "NOUVEAU", rows: [] };
  function paintSupChips(open) {
    const chips = [["NOUVEAU", "À traiter", open], ["TRAITE", "Traités", null], ["", "Tous", null]];
    $("sup-filter").innerHTML = chips.map((x) => '<button type="button" class="rc-chip' + (sup.status === x[0] ? " is-on" : "") + '" data-st="' + x[0] + '">' + esc(x[1]) +
      (x[2] != null ? '<span class="adm-chip-count">' + num(x[2]) + "</span>" : "") + "</button>").join("");
  }
  $("sup-filter").addEventListener("click", (e) => {
    const b = e.target.closest("[data-st]");
    if (!b) return;
    sup.status = b.dataset.st;
    sup.page = 0;
    loadSupport();
  });
  const supReply = (x) => "mailto:" + encodeURIComponent(x.email || "") + "?subject=" + encodeURIComponent("Re : " + (x.subject || "votre message"));
  async function loadSupport() {
    const body = $("sup-body");
    body.innerHTML = '<tr><td colspan="6" class="adm-empty-cell">Chargement…</td></tr>';
    try {
      const r = await HireLinkAPI.api("/admin/support?page=" + sup.page + "&size=25" + (sup.status ? "&status=" + sup.status : ""), { noDedupe: true });
      paintSupChips(r.openCount || 0);
      sup.rows = r.items || [];
      body.innerHTML = sup.rows.length
        ? sup.rows.map((x) => {
            const done = x.status === "TRAITE";
            const who = x.userRole ? (ROLE_FR[x.userRole] || "Membre") : "Visiteur";
            const excerpt = String(x.message || "").length > 110 ? String(x.message).slice(0, 110) + "…" : String(x.message || "");
            return "<tr><td>" + esc(App.fmtDateTime(x.createdAt)) + "</td><td><strong>" + esc(x.name) + '</strong><div class="rc-cell-sub">' + esc(x.email) + " · " + esc(who) + "</div></td>" +
              '<td><span class="rc-pill ' + (SUP_TONE[x.topic] || "") + '">' + esc(SUP_TOPIC[x.topic] || "Autre") + "</span><div>" + esc(x.subject) + "</div></td>" +
              "<td>" + esc(excerpt) + "</td>" +
              '<td><span class="rc-pill ' + (done ? "rc-pill--ok" : "rc-pill--warn") + '">' + (done ? "Traité" : "À traiter") + "</span></td>" +
              '<td class="adm-row-actions"><button type="button" class="btn btn-ghost btn-sm" data-sup-read="' + x.id + '">Lire</button>' +
              '<button type="button" class="btn btn-soft btn-sm" data-sup-set="' + x.id + '" data-to="' + (done ? "NOUVEAU" : "TRAITE") + '">' + (done ? "Rouvrir" : "Marquer traité") + "</button></td></tr>";
          }).join("")
        : '<tr><td colspan="6" class="adm-empty-cell">' + (sup.status === "NOUVEAU" ? "Aucun message en attente." : "Aucun message.") + "</td></tr>";
      pager($("sup-pager"), sup.page, r.totalPages, r.totalElements, "message", (p) => { sup.page = Math.max(0, p); loadSupport(); });
    } catch (err) {
      body.innerHTML = '<tr><td colspan="6"></td></tr>';
      App.errorState(body.firstChild.firstChild, err.message, loadSupport);
    }
  }
  async function setSupportStatus(id, to) {
    try {
      await HireLinkAPI.api("/admin/support/" + id, { method: "PATCH", body: { status: to } });
      App.toast(to === "TRAITE" ? "Message traité" : "Message rouvert", "", "ok");
      loadSupport();
    } catch (err) { fail("Support", err); }
  }
  $("sup-refresh").addEventListener("click", () => loadSupport());
  $("sup-body").addEventListener("click", (e) => {
    const set = e.target.closest("[data-sup-set]");
    if (set) { setSupportStatus(set.dataset.supSet, set.dataset.to); return; }
    const read = e.target.closest("[data-sup-read]");
    if (!read) return;
    const x = sup.rows.find((r) => String(r.id) === read.dataset.supRead);
    if (!x) return;
    const done = x.status === "TRAITE";
    HireLink.openModal({
      title: x.subject || "Message",
      large: true,
      bodyHtml: '<div class="adm-sup-meta"><span class="rc-pill ' + (SUP_TONE[x.topic] || "") + '">' + esc(SUP_TOPIC[x.topic] || "Autre") + "</span>" +
        "<span>" + esc(x.name) + " · " + esc(x.email) + "</span><span>" + esc(App.fmtDateTime(x.createdAt)) + "</span></div>" +
        '<p class="adm-sup-text">' + esc(x.message) + "</p>" +
        (x.reference ? '<p class="rc-muted">Élément concerné : ' + esc(x.reference) + "</p>" : "") +
        '<p><a class="btn btn-soft btn-sm" href="' + esc(supReply(x)) + '">Répondre par e-mail</a></p>',
      confirmLabel: done ? "Rouvrir" : "Marquer traité",
      cancelLabel: "Fermer",
      onConfirm: () => setSupportStatus(x.id, done ? "NOUVEAU" : "TRAITE"),
    });
  });

  /* ------------------------------------------------------- offres hors HireLink */
  async function loadExternal() {
    const boxes = $("ext-boxes"), body = $("ext-body");
    try {
      const s = await HireLinkAPI.api("/admin/external-offers/status", { noDedupe: true });
      const imp = s.importCatalog || {}, web = s.webDirectSearch || {};
      const box = (on, title, text, extra) => '<article class="adm-source ' + (on ? "is-on" : "is-off") + '"><div class="adm-source-head"><h2>' + esc(title) + "</h2>" +
        '<span class="rc-pill ' + (on ? "rc-pill--ok" : "") + '">' + (on ? "Active" : "En pause") + "</span></div><p>" + esc(text) + "</p>" + extra + "</article>";
      boxes.innerHTML =
        box(!!imp.enabled, "Offres importées", imp.summary || "",
          '<div class="adm-source-stats"><div><b>' + num(s.availableCount) + "</b><span>visibles par les candidats</span></div><div><b>" + num(s.hiddenCount) + "</b><span>masquées</span></div></div>") +
        box(!!web.enabled, "Recherche web en direct", web.summary || "",
          '<div class="adm-provider-list">' + (web.providers || []).map((p) => "<span>" + esc(p) + "</span>").join("") + "</div>");
      const sources = (s.sources || []).filter((x) => x.kind !== "web-search" && x.kind !== "web-fallback");
      body.innerHTML = sources.length ? sources.map((x) => {
        const last = x.last && x.last.finishedAt ? App.fmtDateTime(x.last.finishedAt) : "Jamais";
        return "<tr><td><strong>" + esc(x.label) + "</strong></td><td><span class=\"rc-pill " + (x.enabled ? "rc-pill--ok" : "") + "\">" + (x.enabled ? "Active" : "En pause") + "</span></td><td>" +
          num(x.availableCount) + "</td><td>" + num(x.hiddenCount) + "</td><td>" + esc(last) + "</td></tr>";
      }).join("") : '<tr><td colspan="5" class="adm-empty-cell">Aucune source d’import.</td></tr>';
      $("ext-sync").disabled = !imp.enabled;
    } catch (err) {
      boxes.innerHTML = "";
      body.innerHTML = '<tr><td colspan="5"></td></tr>';
      App.errorState(body.firstChild.firstChild, err.message, loadExternal);
    }
  }
  $("ext-refresh").addEventListener("click", loadExternal);
  $("ext-sync").addEventListener("click", async () => {
    const ok = await Mascotte.confirm({ title: "Mettre à jour les offres importées ?", description: "HireLink interroge les sources actives. Les offres retirées à la source disparaissent, les nouvelles apparaissent.", confirmLabel: "Mettre à jour", cancelLabel: "Annuler" });
    if (!ok) return;
    const btn = $("ext-sync");
    btn.disabled = true;
    try {
      await HireLinkAPI.api("/external-offers/sync", { method: "POST" });
      App.toast("Mise à jour terminée", "Les offres importées sont à jour.", "ok");
    } catch (err) { fail("Mise à jour", err); }
    btn.disabled = false;
    loadExternal();
  });

  /* ------------------------------------------------------------------ données */
  const SOURCES = {
    overview: [() => "/admin/overview?granularity=" + state.granularity, (d) => { state.overview = d || null; }],
    orgs: [() => "/admin/organizations", (d) => { state.orgs = d || []; }],
    plans: [() => "/admin/plans", (d) => { state.plans = d || []; }],
    invoices: [() => "/admin/invoices", (d) => { state.invoices = d || []; }],
    users: [() => "/admin/users", (d) => { state.users = d || []; paintUserOrgs(); }],
    aiUsage: [() => "/admin/ai-usage", (d) => { state.aiUsage = d || {}; }],
    offers: [() => "/admin/offers", (d) => { state.offers = d || []; }],
    reports: [() => "/admin/reports", (d) => { state.reports = d || { queue: [], handled: [] }; }],
  };
  function paintAll() { paintDash(); paintOrgs(); paintUsers(); paintModeration(); paintOffers(); paintPlans(); paintAiUsage(); paintInvoices(); }
  async function refresh(...names) {
    const list = names.length ? names : Object.keys(SOURCES);
    await Promise.all(list.map(async (k) => SOURCES[k][1](await HireLinkAPI.api(SOURCES[k][0](), { noDedupe: true }))));
    paintAll();
  }

  function init() {
    App.skeleton($("dash-kpis"), 2, 80);
    refresh().catch((err) => {
      App.errorState($("dash-kpis"), err.message, init);
    });
  }
  init();
  show((location.hash || "").slice(1));
})();
