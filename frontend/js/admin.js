/* Espace administration — tableau de bord, entreprises, comptes, plans & factures, motifs de refus */
(function () {
  const App = HireLinkApp;
  const session = App.mount({});
  if (!session) return;

  const esc = App.esc;
  const VIEWS = ["dash", "orgs", "users", "plans", "refusals", "audit", "emails"];
  const STATUS_FR = App.STATUS_FR;
  const PAY_FR = { EMISE: "Émise", REGLEE: "Réglée", OFFERTE: "Incluse" };
  const ROLE_FR = { CANDIDAT: "Candidat", RECRUTEUR: "Recruteur", ADMINISTRATEUR: "Administrateur" };
  const ORG_ROLE_FR = { OWNER: "Owner", RECRUTEUR: "Recruteur", HIRING_MANAGER: "Hiring Manager" };
  const ORG_ROLE_TONE = { OWNER: "rc-pill--rose", RECRUTEUR: "", HIRING_MANAGER: "rc-pill--info" };
  /** Filtre de rôle : rôle plateforme (CANDIDAT, RECRUTEUR, ADMINISTRATEUR) ou rôle dans l'organisation (OWNER, HIRING_MANAGER). */
  function userMatches(u, f) {
    if (f === "all") return true;
    if (f === "OWNER" || f === "HIRING_MANAGER") return u.orgRole === f;
    return u.role === f;
  }

  const state = { orgs: [], plans: [], invoices: [], users: [], stats: {}, charts: {}, orgFilter: "all", userFilter: "all", reasons: null };
  const loaded = {};

  const money = (n) => Number(n || 0).toLocaleString("fr-FR", { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + " MAD";
  const planLabel = (code) => (state.plans.find((p) => p.code === code) || {}).label || code;
  const $ = (id) => document.getElementById(id);
  const fail = (title, err) => App.toast(title, (err && err.message) || "Erreur", "danger");

  /* ------------------------------------------------------------- navigation */
  function show(view) {
    // Lien contextuel : #orgs/pending, #users/CANDIDAT… ouvre l'onglet déjà filtré.
    const sub = String(view || "").split("/")[1];
    view = String(view || "").split("/")[0];
    if (VIEWS.indexOf(view) < 0) view = "dash";
    if (sub) setTimeout(() => {
      const bar = $(view === "orgs" ? "org-filter" : view === "users" ? "user-filter" : "");
      const chip = bar && bar.querySelector('[data-f="' + sub + '"]');
      if (chip) chip.click();
    }, 0);
    VIEWS.forEach((id) => { $("view-" + id).hidden = id !== view; });
    App.setActive(view);
    if (view === "refusals") loadRefusals();
    if (view === "audit") loadAudit();
    if (view === "emails") loadEmails();
    window.scrollTo(0, 0);
  }
  window.addEventListener("hashchange", () => show((location.hash || "").slice(1)));

  function bars(el, items) {
    const max = Math.max(1, ...items.map((i) => i.value));
    el.innerHTML = items.length
      ? items.map((i) => '<div class="rc-barrow"><span>' + esc(i.label) + '</span><div class="rc-track"><i data-w="' + Math.round((i.value / max) * 100) + '"></i></div><b>' + i.value + "</b></div>").join("")
      : '<p class="rc-muted">Aucune donnée.</p>';
    requestAnimationFrame(() => el.querySelectorAll("[data-w]").forEach((n) => { n.style.width = n.dataset.w + "%"; }));
  }

  const statusPill = (o) => '<span class="rc-pill ' + (o.verified ? "rc-pill--ok" : "rc-pill--warn") + '">' + esc(o.statusLabel || (o.verified ? "Vérifiée" : "En attente")) + "</span>";

  /* -------------------------------------------------------------- dashboard */
  function paintDash() {
    const { stats, orgs } = state;
    const ico = (n) => (window.HLIcons ? HLIcons.svg(n) : "");
    const pending = orgs.filter((o) => !o.verified);
    const recent = orgs.filter((o) => o.createdAt && Date.now() - new Date(o.createdAt) < 7 * 864e5);
    const inactive = state.users.filter((u) => u.active === false);
    const unset = state.users.filter((u) => u.mustSetPassword);
    const tile = (o) => '<a class="db-action' + (!o.count ? " is-zero" : "") + (o.tone ? " is-" + o.tone : "") + '" href="' + o.href + '"><span class="db-action-ico" aria-hidden="true">' + ico(o.icon) +
      '</span><span class="db-action-txt"><b>' + (o.count || "—") + "</b><strong>" + esc(o.title) + "</strong><small>" + esc(o.count ? o.sub : o.calm) + '</small></span><span class="db-go" aria-hidden="true">' + ico("right") + "</span></a>";
    $("db-actions").innerHTML = [
      { icon: "shield", title: "Entreprises à vérifier", count: pending.length, tone: "warn", sub: pending[0] ? pending[0].name + (pending.length > 1 ? " et " + (pending.length - 1) + " autre(s)" : "") : "", calm: "Tout est vérifié", href: "#orgs/pending" },
      { icon: "building", title: "Nouvelles entreprises", count: recent.length, tone: "info", sub: "Inscrites ces 7 derniers jours", calm: "Aucune cette semaine", href: "#orgs/all" },
      { icon: "user", title: "Invitations en attente", count: unset.length, sub: "Mot de passe pas encore défini", calm: "Aucune invitation en attente", href: "#users/all" },
      { icon: "ban", title: "Comptes désactivés", count: inactive.length, tone: "danger", sub: "À examiner si besoin", calm: "Aucun compte désactivé", href: "#users/all" },
    ].map(tile).join("");
    const kpi = (label, value, hint, href) => '<a class="db-kpi" href="' + href + '"><small>' + esc(label) + "</small><b>" + esc(String(value == null ? "—" : value)) + "</b><span>" + esc(hint || "") + "</span></a>";
    $("stats").innerHTML =
      kpi("Comptes", stats.users, (stats.candidates || 0) + " candidats · " + (stats.recruiters || 0) + " recruteurs", "#users/all") +
      kpi("Entreprises", orgs.length, orgs.filter((o) => o.planTier === "PREMIUM").length + " en Premium", "#orgs/all") +
      kpi("Offres publiées", stats.publishedOffers, (stats.offers || 0) + " au total", "#orgs/all") +
      kpi("Candidatures", stats.applications, "Toutes offres confondues", "#dash");

    const byStatus = state.charts.applicationsByStatus || {};
    const keys = ["SUBMITTED", "SCREENING", "SHORTLISTED", "INTERVIEW", "OFFER", "HIRED"];
    const max = Math.max(1, ...keys.map((k) => byStatus[k] || 0));
    $("chart-apps").innerHTML = keys.map((k, i) => {
      const v = byStatus[k] || 0, sumFrom = (j) => keys.slice(j).reduce((a, x) => a + (byStatus[x] || 0), 0), reached = sumFrom(i), prev = i ? sumFrom(i - 1) : 0;
      return '<div class="db-stage"><small>' + esc(STATUS_FR[k] || k) + "</small><b>" + v + '</b><span class="db-stage-bar"><i style="--w:' + Math.max(4, Math.round((v / max) * 100)) + '%"></i></span><em>' +
        (i && prev ? Math.round((reached / prev) * 100) + " % ont atteint cette étape" : "&nbsp;") + "</em></div>";
    }).join("");

    const box = $("dash-pending");
    box.closest("section").hidden = !pending.length;
    if (!pending.length) {
      box.innerHTML = '<div class="db-empty">' + ico("check") + "<strong>Aucune entreprise en attente</strong><span>Les entreprises dont l’ICE et l’e-mail professionnel concordent sont vérifiées automatiquement.</span></div>";
      return;
    }
    box.innerHTML = '<div class="rc-list">' + pending.slice(0, 5).map((o) =>
      '<div class="rc-item"><div class="rc-item-main"><strong>' + esc(o.name) + '</strong><span class="rc-muted">ICE ' + esc(o.ice || "non renseigné") + " · " + esc(o.ownerEmail || "—") +
      '</span></div><button type="button" class="btn btn-soft btn-sm" data-open="' + o.id + '">Examiner</button></div>').join("") + "</div>";
  }
  $("dash-pending").addEventListener("click", (e) => {
    const b = e.target.closest("[data-open]");
    if (b) openOrg(Number(b.dataset.open));
  });

  /* ------------------------------------------------------------ entreprises */
  function paintOrgs() {
    const q = ($("org-search").value || "").trim();
    const f = state.orgFilter;
    const rows = HireLinkSearch.rank(state.orgs.filter((o) => {
      if (f === "pending" && o.verified) return false;
      if (f === "verified" && !o.verified) return false;
      return !(f === "premium" && o.planTier !== "PREMIUM");
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
        '<div class="rc-cell-main"><strong>' + esc(o.name) + '</strong><span class="rc-cell-sub">' + esc([o.sector, o.city].filter(Boolean).join(" · ") || "—") + "</span></div></div></td>" +
        "<td>" + esc(o.ice || "—") + "</td><td>" + esc(planLabel(o.planTier)) + "</td><td>" + statusPill(o) + "</td><td>" + o.memberCount + "</td><td>" + o.offerCount + "</td>" +
        '<td><div class="rc-row-actions"><button type="button" class="btn btn-soft btn-sm" data-open="' + o.id + '">Détails</button></div></td></tr>';
    }).join("");
    body.querySelectorAll("img[data-initials]").forEach((img) => {
      img.addEventListener("error", () => { img.parentNode.textContent = img.dataset.initials; });
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

  async function openOrg(id) {
    let org;
    try { org = await HireLinkAPI.api("/admin/organizations/" + id); } catch (err) { return fail("Entreprise", err); }
    const members = (org.members || []).map((m) =>
      '<li class="rc-item"><div class="rc-item-main"><strong>' + esc(m.fullName) + '</strong><span class="rc-muted">' + esc(m.email) + '</span></div><span class="rc-pill">' + esc(m.role) + "</span></li>").join("");
    const plansOpts = state.plans.filter((p) => p.audience === "RECRUTEUR").map((p) =>
      "<option value='" + esc(p.code) + "'" + (p.code === org.planTier ? " selected" : "") + ">" + esc(p.label) + " · " + p.monthlyMad + " MAD / mois</option>").join("");
    const body =
      "<p style='margin:0 0 .6rem'>" + statusPill(org) + " <span class='rc-muted'>ICE " + esc(org.ice || "—") + " · " + esc(org.offerCount) + " offre(s)</span></p>" +
      "<p style='margin:0 0 1rem'><strong>Responsable.</strong> " + esc(org.ownerName || "—") + " · " + esc(org.ownerEmail || "") + "</p>" +
      "<h4 style='margin:0 0 .4rem'>Équipe</h4><ul class='rc-list' style='list-style:none;padding:0;margin:0 0 1rem'>" + (members || "<li class='rc-muted'>Aucun membre actif.</li>") + "</ul>" +
      "<h4 style='margin:0 0 .4rem'>Plan</h4><div class='rc-form'><div class='rc-field'><label for='org-plan'>Plan</label><select id='org-plan'>" + plansOpts + "</select></div>" +
      "<div class='rc-field'><label for='org-cycle'>Période de facturation</label><select id='org-cycle'><option value='month'>Mensuelle</option><option value='year'>Annuelle</option></select></div></div>" +
      "<p class='rc-muted' style='margin:.5rem 0 0'>Un changement de plan émet automatiquement une facture.</p>" +
      "<div class='rc-row-actions' style='margin-top:1rem;justify-content:flex-start'><button type='button' class='btn " + (org.verified ? "btn-ghost" : "btn-soft") + " btn-sm' id='org-toggle-verify'>" + (org.verified ? "Retirer la vérification" : "Marquer comme vérifiée") + "</button></div>";
    let verifyToggle = false;
    const ok = await HireLink.openModal({
      title: org.name, description: "Fiche entreprise", large: true, bodyHtml: body, confirmLabel: "Enregistrer le plan", cancelLabel: "Fermer",
      onReady: (api) => {
        const b = api.root.querySelector("#org-toggle-verify");
        if (b) b.onclick = () => { verifyToggle = true; HireLink.closeModal(true); };
      },
    });
    if (!ok) return;
    const payload = verifyToggle ? { verified: !org.verified } : { planTier: $("org-plan").value, cycle: $("org-cycle").value };
    if (!verifyToggle && payload.planTier === org.planTier) return;
    try {
      await HireLinkAPI.api("/admin/organizations/" + id, { method: "PATCH", body: payload });
      App.toast("Entreprise mise à jour", org.name, "ok");
      await Promise.all([refresh("orgs"), refresh("invoices")]);
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
      body.innerHTML = '<tr><td colspan="5"></td></tr>';
      HireLinkSearch.emptyState(body.firstChild.firstChild, q, { emptyTitle: "Aucun compte ne correspond", onClear: () => { $("user-search").value = ""; $("user-org").value = ""; paintUsers(); } });
      return;
    }
    body.innerHTML = rows.map((u) => {
      const self = u.id === session.userId;
      return '<tr><td><div class="rc-logo-row"><span class="rc-orgmark">' + esc(App.initials(u.fullName)) + '</span><div class="rc-cell-main"><strong>' + esc(u.fullName) + (self ? " (vous)" : "") +
        '</strong><span class="rc-cell-sub">' + esc(u.email) + "</span></div></div></td><td>" + esc(ROLE_FR[u.role] || u.role) +
        (u.orgRole ? ' <span class="rc-pill ' + (ORG_ROLE_TONE[u.orgRole] || "") + '">' + esc(ORG_ROLE_FR[u.orgRole] || u.orgRole) + "</span>" : "") + "</td>" +
        "<td>" + (u.orgName ? esc(u.orgName) : '<span class="rc-cell-sub">—</span>') + "</td>" +
        '<td><span class="rc-pill ' + (u.active ? "rc-pill--ok" : "rc-pill--danger") + '">' + (u.active ? "Actif" : "Désactivé") + "</span></td>" +
        '<td><div class="rc-row-actions">' + (self ? "" : '<button type="button" class="btn btn-ghost btn-sm" data-user="' + u.id + '">' + (u.active ? "Désactiver" : "Réactiver") + "</button>") + "</div></td></tr>";
    }).join("");
    HireLinkSearch.mark(body, q);
  }
  $("users-body").addEventListener("click", async (e) => {
    const b = e.target.closest("[data-user]");
    const u = b && state.users.find((x) => String(x.id) === b.dataset.user);
    if (!u) return;
    const ok = await Mascotte.confirm({
      title: u.active ? "Désactiver ce compte ?" : "Réactiver ce compte ?",
      description: u.fullName + " · " + u.email, confirmLabel: u.active ? "Désactiver" : "Réactiver", danger: !!u.active,
    });
    if (!ok) return;
    try {
      await HireLinkAPI.api("/admin/users/" + u.id + "/active", { method: "PATCH", body: { active: !u.active } });
      App.toast("Compte mis à jour", u.email, "ok");
      await refresh("users");
    } catch (err) { fail("Compte", err); }
  });
  $("user-search").addEventListener("input", App.debounce(paintUsers, 120));
  $("user-org").addEventListener("change", paintUsers);
  /** Liste des organisations présentes parmi les comptes (filtre). */
  function paintUserOrgs() {
    const sel = $("user-org"), keep = sel.value, seen = new Map();
    state.users.forEach((u) => { if (u.orgId && !seen.has(u.orgId)) seen.set(u.orgId, u.orgName); });
    sel.innerHTML = '<option value="">Toutes les organisations</option>' + [...seen].sort((a, b) => String(a[1]).localeCompare(String(b[1]), "fr"))
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

  /* ------------------------------------------------------- plans et factures */
  function paintPlans() {
    $("plan-cards").innerHTML = state.plans.map((p) => {
      const count = p.audience === "RECRUTEUR" ? state.orgs.filter((o) => o.planTier === p.code).length : null;
      const feats = String(p.features || "").split("\n").map((l) => l.trim()).filter(Boolean);
      return '<article class="rc-card rc-fade"><div class="rc-card-head"><div><h2>' + esc(p.label) + "</h2><p>" + (p.audience === "CANDIDAT" ? "Candidats" : "Entreprises") +
        '</p></div><button type="button" class="btn btn-soft btn-sm" data-plan="' + esc(p.code) + '">Modifier</button></div><div class="rc-card-body">' +
        "<p style='margin:0 0 .6rem'><strong style='font-size:1.4rem'>" + p.monthlyMad + "</strong> MAD / mois <span class='rc-muted'>· " + p.yearlyMad + " MAD / an · " + p.credits + " crédits IA</span></p>" +
        "<ul style='margin:0 0 .6rem;padding-left:1.1rem'>" + (feats.map((f) => "<li>" + esc(f) + "</li>").join("") || "<li class='rc-muted'>Aucune fonctionnalité renseignée</li>") + "</ul>" +
        (count == null ? "" : "<p class='rc-muted' style='margin:0'>" + count + " entreprise(s) sur ce plan</p>") + "</div></article>";
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
      await refresh("plans");
    } catch (err) { fail("Plan", err); }
  }

  function paintInvoices() {
    const body = $("invoices-body");
    if (!state.invoices.length) {
      body.innerHTML = '<tr><td colspan="7" class="rc-muted" style="text-align:center;padding:1.6rem">Aucune facture. Elles apparaissent à chaque changement de plan d’une entreprise.</td></tr>';
      return;
    }
    body.innerHTML = state.invoices.map((r, i) =>
      "<tr><td>" + esc(r.number) + "</td><td>" + esc(App.fmtDate(r.issuedAt)) + "</td><td>" + esc(r.organization) + "</td><td>" + esc(r.planLabel) + " · " + (r.cycle === "year" ? "annuel" : "mensuel") + "</td><td>" + money(r.amountTtc) +
      '</td><td><span class="rc-pill ' + (r.status === "REGLEE" ? "rc-pill--ok" : "") + '">' + esc(PAY_FR[r.status] || r.status) + '</span></td><td><div class="rc-row-actions"><button type="button" class="btn btn-soft btn-sm" data-inv="' + i + '">Ouvrir</button></div></td></tr>').join("");
  }
  $("invoices-body").addEventListener("click", (e) => {
    const b = e.target.closest("[data-inv]");
    if (b) printInvoice(state.invoices[Number(b.dataset.inv)]);
  });

  function printInvoice(row) {
    const w = window.open("", "_blank");
    if (!w) return App.toast("Facture", "Autorisez les fenêtres pour afficher la facture", "warn");
    w.document.open();
    w.document.write("<!DOCTYPE html><html lang='fr'><head><meta charset='utf-8'><title>Facture " + esc(row.number) +
      "</title><style>body{font-family:Inter,system-ui,sans-serif;color:#1a1214;padding:2.5rem;max-width:720px;margin:auto}h1{margin:.2rem 0}table{width:100%;border-collapse:collapse;margin-top:1.5rem}td{padding:.55rem 0;border-bottom:1px solid #eee}.total td{font-weight:700;border-bottom:0}</style></head><body><p><strong>HireLink</strong></p><h1>Facture " +
      esc(row.number) + "</h1><p>Émise le " + esc(new Date(row.issuedAt).toLocaleString("fr-FR")) + " · " + esc(PAY_FR[row.status] || row.status) + "</p><p><strong>" + esc(row.organization) + "</strong><br>ICE " + esc(row.ice || "—") +
      "</p><table><tr><td>" + esc(row.planLabel) + " · " + (row.cycle === "year" ? "annuel" : "mensuel") + "</td><td style='text-align:right'>" + money(row.amountHt) + " HT</td></tr><tr><td>TVA 20 %</td><td style='text-align:right'>" + money(row.tva) +
      "</td></tr><tr class='total'><td>Total TTC</td><td style='text-align:right'>" + money(row.amountTtc) + "</td></tr></table></body></html>");
    w.document.close();
  }

  /* ----------------------------------------------------------- journal d'audit */
  async function loadAudit() {
    const body = $("audit-body");
    const action = $("audit-filter").value;
    body.innerHTML = '<tr><td colspan="6" class="rc-muted" style="text-align:center;padding:1.4rem">Chargement…</td></tr>';
    try {
      const rows = await HireLinkAPI.api("/admin/audit" + (action ? "?action=" + encodeURIComponent(action) : ""), { noDedupe: true });
      body.innerHTML = rows.length
        ? rows.map((r) =>
            "<tr><td>" + esc(App.fmtDateTime(r.at)) + '</td><td><span class="rc-pill ' + (r.success ? "rc-pill--ok" : "rc-pill--danger") + '">' + esc(r.action) + "</span></td><td>" +
            esc(r.actor || "—") + "</td><td>" + esc(r.target || "—") + "</td><td>" + esc(r.ip || "—") + '</td><td class="rc-muted">' + esc(r.detail || "") + "</td></tr>").join("")
        : '<tr><td colspan="6" class="rc-muted" style="text-align:center;padding:1.6rem">Aucun évènement.</td></tr>';
    } catch (err) {
      body.innerHTML = '<tr><td colspan="6"></td></tr>';
      App.errorState(body.firstChild.firstChild, err.message, loadEmails);
    }
  }
  $("audit-filter").addEventListener("change", loadAudit);
  $("audit-refresh").addEventListener("click", loadAudit);

  /* ------------------------------------------------------------ e-mails envoyés */
  const MAIL_STATE = { SENT: ["Envoyé", "ok"], LOGGED: ["Journalisé (sans SMTP)", ""], FAILED: ["Échec d’envoi", "danger"] };
  const MAIL_KIND = { SECURITY: "Sécurité", ACCOUNT: "Compte", APPLICATION: "Candidature", INTERVIEW: "Entretien" };

  async function loadEmails() {
    const body = $("mail-body");
    body.innerHTML = '<tr><td colspan="6" class="rc-muted" style="text-align:center;padding:1.4rem">Chargement…</td></tr>';
    try {
      const rows = await HireLinkAPI.api("/admin/emails", { noDedupe: true });
      if (!rows.length) {
        body.innerHTML = '<tr><td colspan="6"></td></tr>';
        App.empty(body.firstChild.firstChild, { title: "Aucun e-mail pour le moment", text: "Les messages envoyés par HireLink (candidatures, entretiens, sécurité) apparaîtront ici." });
        return;
      }
      body.innerHTML = rows.map((r) => {
        const st = MAIL_STATE[r.status] || [r.status, ""];
        return "<tr><td>" + esc(App.fmtDateTime(r.at)) + "</td><td>" + esc(r.recipient) + "</td><td>" + esc(r.subject) + "</td><td>" + esc(MAIL_KIND[r.kind] || r.kind) +
          '</td><td><span class="rc-pill' + (st[1] ? " rc-pill--" + st[1] : "") + '">' + esc(st[0]) + "</span></td><td>" +
          (r.hasBody ? '<button type="button" class="btn btn-ghost btn-sm" data-mail="' + r.id + '">Voir</button>' : "") + "</td></tr>";
      }).join("");
    } catch (err) {
      body.innerHTML = '<tr><td colspan="6"></td></tr>';
      App.errorState(body.firstChild.firstChild, err.message, loadEmails);
    }
  }
  $("mail-refresh").addEventListener("click", loadEmails);
  $("mail-body").addEventListener("click", async (e) => {
    const b = e.target.closest("[data-mail]");
    if (!b) return;
    try {
      const mail = await HireLinkAPI.api("/admin/emails/" + b.dataset.mail);
      // Aperçu isolé : aucun script ni lien actif ne peut s'exécuter dans le message.
      const frame = document.createElement("iframe");
      frame.setAttribute("sandbox", "");
      frame.title = mail.subject;
      frame.className = "mail-preview";
      frame.srcdoc = mail.html;
      HireLink.openModal({ title: mail.subject, bodyHtml: '<div id="mail-slot"></div>', confirmLabel: "Fermer", hideCancel: true, large: true,
        onReady: (api) => api.root.querySelector("#mail-slot").appendChild(frame) });
    } catch (err) { fail("E-mail", err); }
  });

  /* ---------------------------------------------------------- motifs de refus */
  async function loadRefusals(force) {
    const body = $("rr-body");
    if (state.reasons && !force) return;
    try {
      state.reasons = await HireLinkAPI.api("/refusal-reasons?all=true");
      body.innerHTML = state.reasons.length
        ? state.reasons.map((r) =>
            "<tr><td><code>" + esc(r.code) + "</code></td><td>" + esc(r.label) + '</td><td><span class="rc-pill ' + (r.active ? "rc-pill--ok" : "") + '">' + (r.active ? "Actif" : "Inactif") +
            '</span></td><td><div class="rc-row-actions"><button class="btn btn-ghost btn-sm" data-rr="edit" data-id="' + r.id + '">Renommer</button><button class="btn btn-ghost btn-sm" data-rr="toggle" data-id="' + r.id + '">' +
            (r.active ? "Désactiver" : "Activer") + '</button><button class="btn btn-ghost btn-sm" data-rr="del" data-id="' + r.id + '">Supprimer</button></div></td></tr>').join("")
        : '<tr><td colspan="4" class="rc-muted" style="text-align:center;padding:1.6rem">Aucun motif.</td></tr>';
    } catch (err) {
      body.innerHTML = '<tr><td colspan="4" class="rc-muted">' + esc(err.message) + "</td></tr>";
    }
  }
  $("rr-body").addEventListener("click", async (e) => {
    const btn = e.target.closest("[data-rr]");
    const r = btn && (state.reasons || []).find((x) => String(x.id) === btn.dataset.id);
    if (!r) return;
    const act = btn.dataset.rr;
    try {
      if (act === "edit") {
        const ok = await HireLink.openModal({ title: "Renommer le motif", confirmLabel: "Enregistrer",
          bodyHtml: "<div class='rc-field'><label for='rr-edit-label'>Libellé</label><input id='rr-edit-label' value='" + esc(r.label) + "' /></div>" });
        const label = ok && $("rr-edit-label").value.trim();
        if (!label) return;
        await HireLinkAPI.api("/refusal-reasons/" + r.id, { method: "PUT", body: { label, active: r.active } });
      } else if (act === "toggle") {
        await HireLinkAPI.api("/refusal-reasons/" + r.id, { method: "PUT", body: { label: r.label, active: !r.active } });
      } else {
        const ok = await Mascotte.confirm({ title: "Supprimer « " + r.code + " » ?", description: "Les refus déjà enregistrés conservent leur libellé.", confirmLabel: "Supprimer", danger: true });
        if (!ok) return;
        await HireLinkAPI.api("/refusal-reasons/" + r.id, { method: "DELETE" });
      }
      App.toast("Motifs de refus", "Catalogue mis à jour", "ok");
      loadRefusals(true);
    } catch (err) { fail("Motifs de refus", err); }
  });
  $("rr-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    try {
      await HireLinkAPI.api("/refusal-reasons", { method: "POST", body: { code: $("rr-code").value.trim(), label: $("rr-label").value.trim(), active: true } });
      e.target.reset();
      App.toast("Motifs de refus", "Motif ajouté", "ok");
      loadRefusals(true);
    } catch (err) { fail("Motifs de refus", err); }
  });

  /* ------------------------------------------------------------------ données */
  const SOURCES = {
    stats: ["/admin/stats", (d) => { state.stats = d || {}; }],
    charts: ["/admin/charts", (d) => { state.charts = d || {}; }],
    orgs: ["/admin/organizations", (d) => { state.orgs = d || []; }],
    plans: ["/admin/plans", (d) => { state.plans = d || []; }],
    invoices: ["/admin/invoices", (d) => { state.invoices = d || []; }],
    users: ["/admin/users", (d) => { state.users = d || []; paintUserOrgs(); }],
  };
  function paintAll() { paintDash(); paintOrgs(); paintUsers(); paintPlans(); paintInvoices(); }
  async function refresh(...names) {
    const list = names.length ? names : Object.keys(SOURCES);
    await Promise.all(list.map(async (k) => SOURCES[k][1](await HireLinkAPI.api(SOURCES[k][0]))));
    paintAll();
  }

  function init() {
    App.skeleton($("dash-pending"), 3, 56);
    refresh().catch((err) => {
      App.errorState($("dash-pending"), err.message, init);
    });
  }
  init();
  show((location.hash || "").slice(1));
})();
