(function () {
  const session = HireLinkAPI.requireAuth(["ADMINISTRATEUR"]);
  if (!session) return;

  document.getElementById("side-user").textContent = session.email;
  document.getElementById("avatar").textContent = (session.fullName || "A").slice(0, 1).toUpperCase();

  const STATUS_FR = {
    SUBMITTED: "Envoyée",
    SCREENING: "Présélection",
    SHORTLISTED: "Retenue",
    INTERVIEW: "Entretien",
    REJECTED: "Refusée",
    WITHDRAWN: "Retirée",
    OFFER: "Offre",
    ACCEPTED: "Acceptée",
  };
  const PAY_FR = { EMISE: "Émise", REGLEE: "Réglée", OFFERTE: "Incluse" };

  let orgs = [];
  let plans = [];
  let invoices = [];
  let users = [];
  let stats = {};
  let charts = { applicationsByStatus: {} };

  function esc(s) {
    return String(s == null ? "" : s)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  function money(n) {
    return Number(n).toLocaleString("fr-FR", { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + " MAD";
  }

  function planLabel(code) {
    const found = plans.find((p) => p.code === code);
    return found ? found.label : code;
  }

  function show(view) {
    ["dash", "orgs", "plans", "payments", "invoices", "users"].forEach((id) => {
      const node = document.getElementById("view-" + id);
      if (node) node.hidden = id !== view;
    });
    document.querySelectorAll("#admin-nav .side-link").forEach((a) => {
      a.classList.toggle("is-active", a.getAttribute("data-view") === view);
    });
  }

  document.getElementById("admin-nav").addEventListener("click", (e) => {
    const link = e.target.closest("[data-view]");
    if (!link) return;
    e.preventDefault();
    show(link.getAttribute("data-view"));
  });

  function chart(items) {
    const max = Math.max(1, ...items.map((item) => Number(item.value) || 0));
    if (!items.length) return "<p class='muted'>Aucune donnée.</p>";
    return (
      '<div class="admin-chart">' +
      items
        .map(
          (item) =>
            '<div class="admin-chart-row"><span>' +
            esc(item.label) +
            '</span><div class="admin-chart-track"><span style="width:' +
            Math.round(((Number(item.value) || 0) / max) * 100) +
            '%"></span></div><strong>' +
            esc(item.value) +
            "</strong></div>"
        )
        .join("") +
      "</div>"
    );
  }

  function paintDash() {
    const premium = orgs.filter((o) => o.planTier === "PREMIUM").length;
    const pending = orgs.filter((o) => !o.verified).length;
    const rows = [
      ["Utilisateurs", stats.users],
      ["Candidats", stats.candidates],
      ["Recruteurs", stats.recruiters],
      ["Entreprises", orgs.length],
      ["À vérifier", pending],
      ["Premium", premium],
      ["Offres", stats.offers],
      ["Candidatures", stats.applications],
    ];
    document.getElementById("stats").innerHTML = rows
      .map(
        ([label, value]) =>
          '<div class="stat"><div class="label">' + esc(label) + '</div><div class="value">' + esc(value) + "</div></div>"
      )
      .join("");
    const pendingBox = document.getElementById("dash-pending");
    if (pendingBox) {
      const waiting = orgs.filter((o) => !o.verified);
      pendingBox.innerHTML = waiting.length
        ? "<ul class='faq-list'>" +
          waiting
            .map(
              (o) =>
                "<li><strong>" +
                esc(o.name) +
                "</strong> · " +
                esc(o.statusLabel || o.verificationStatus || "En attente") +
                " · ICE " +
                esc(o.ice || "—") +
                "</li>"
            )
            .join("") +
          "</ul>"
        : "<p class='muted'>Aucune entreprise en attente. Les badges automatiques (ICE + e-mail pro) passent sans revue.</p>";
    }
    const chartRoles = document.getElementById("chart-roles");
    if (chartRoles) chartRoles.innerHTML = chart([
      { label: "Candidats", value: stats.candidates || 0 },
      { label: "Recruteurs", value: stats.recruiters || 0 },
      { label: "Autres", value: Math.max(0, (stats.users || 0) - (stats.candidates || 0) - (stats.recruiters || 0)) },
    ]);
    const byPlan = {};
    plans.forEach((p) => {
      byPlan[p.code] = 0;
    });
    orgs.forEach((o) => {
      byPlan[o.planTier] = (byPlan[o.planTier] || 0) + 1;
    });
    const chartPlans = document.getElementById("chart-plans");
    if (chartPlans) {
      chartPlans.innerHTML = chart(
        Object.keys(byPlan).map((code) => ({ label: planLabel(code), value: byPlan[code] }))
      );
    }
    const byStatus = charts.applicationsByStatus || {};
    const chartApps = document.getElementById("chart-apps");
    if (chartApps) {
      chartApps.innerHTML = chart(
        Object.keys(byStatus)
          .filter((key) => byStatus[key] > 0)
          .map((key) => ({ label: STATUS_FR[key] || key, value: byStatus[key] }))
      );
    }
    const box = document.getElementById("dash-orgs");
    if (!orgs.length) {
      box.innerHTML = "<p class='muted'>Aucune entreprise.</p>";
      return;
    }
    box.innerHTML =
      "<ul class='faq-list'>" +
      orgs
        .slice(0, 8)
        .map(
          (o) =>
            "<li><strong>" +
            esc(o.name) +
            "</strong> · " +
            esc(planLabel(o.planTier)) +
            " · " +
            (o.verified ? "Vérifiée" : "En attente") +
            " · " +
            o.memberCount +
            " membre(s)</li>"
        )
        .join("") +
      "</ul>";
  }

  function paintOrgs() {
    const body = document.getElementById("orgs-body");
    body.innerHTML = "";
    orgs.forEach((o) => {
      const tr = document.createElement("tr");
      tr.innerHTML =
        "<td></td><td></td><td></td><td></td><td></td><td></td><td class='row wrap'></td>";
      const logoSrc = o.logoUrl && HireLinkAPI.mediaUrl ? HireLinkAPI.mediaUrl(o.logoUrl) : o.logoUrl;
      tr.children[0].innerHTML = logoSrc
        ? '<span class="admin-co"><img alt="" src="' + esc(logoSrc) + '" /><span></span></span>'
        : "";
      if (logoSrc) tr.children[0].querySelector("span span").textContent = o.name;
      else tr.children[0].textContent = o.name;
      tr.children[1].textContent = o.ice || "—";
      tr.children[2].textContent = planLabel(o.planTier);
      tr.children[3].textContent = o.statusLabel || (o.verified ? "Vérifiée" : "En attente");
      tr.children[4].textContent = String(o.memberCount);
      tr.children[5].textContent = String(o.offerCount);
      const actions = tr.children[6];
      const detail = document.createElement("button");
      detail.type = "button";
      detail.className = "btn btn-sm btn-soft";
      detail.textContent = "Détails";
      detail.onclick = () => openOrg(o.id);
      const verify = document.createElement("button");
      verify.type = "button";
      verify.className = "btn btn-sm btn-ghost";
      verify.textContent = o.verified ? "Retirer la vérification" : "Vérifier";
      verify.onclick = () => confirmVerify(o);
      actions.appendChild(detail);
      actions.appendChild(verify);
      body.appendChild(tr);
    });
  }

  async function confirmVerify(o) {
    const ok = await HireLink.openModal({
      title: o.verified ? "Retirer la vérification ?" : "Vérifier cette entreprise ?",
      description: o.name + (o.ice ? " · ICE " + o.ice : ""),
      confirmLabel: "Confirmer",
      danger: !!o.verified,
    });
    if (!ok) return;
    await patchOrg(o.id, { verified: !o.verified });
  }

  async function openOrg(id) {
    try {
      const org = await HireLinkAPI.api("/admin/organizations/" + id);
      const members = (org.members || [])
        .map(
          (m) =>
            "<li><strong>" +
            esc(m.fullName) +
            "</strong> · " +
            esc(m.email) +
            " · " +
            esc(m.role) +
            "</li>"
        )
        .join("");
      let next = "";
      await HireLink.openModal({
        title: org.name,
        description: org.verified ? "Entreprise vérifiée" : "Vérification en attente",
        large: true,
        hideConfirm: true,
        cancelLabel: "Fermer",
        bodyHtml:
          "<p><strong>Responsable.</strong> " +
          esc(org.ownerName || "—") +
          " · " +
          esc(org.ownerEmail || "") +
          "</p>" +
          "<p><strong>ICE.</strong> " +
          esc(org.ice || "—") +
          " · <strong>Plan.</strong> " +
          esc(planLabel(org.planTier)) +
          " · <strong>Offres.</strong> " +
          esc(org.offerCount) +
          "</p>" +
          "<h3 style='margin:1rem 0 .4rem;font-size:1rem'>Membres</h3>" +
          (members ? "<ul class='faq-list'>" + members + "</ul>" : "<p class='muted'>Aucun membre actif.</p>") +
          "<div class='row' style='margin-top:1rem'><button type='button' class='btn btn-primary btn-sm' id='org-change-plan'>Changer le plan</button></div>",
        onReady: (api) => {
          const btn = api.root.querySelector("#org-change-plan");
          if (btn) {
            btn.onclick = () => {
              next = "plan";
              HireLink.closeModal(true);
            };
          }
        },
      });
      if (next === "plan") await changePlan(org);
    } catch (err) {
      HireLink.toast("Entreprise", err.message, "danger");
    }
  }

  async function changePlan(org) {
    const options = plans
      .map(
        (p) =>
          "<option value='" +
          esc(p.code) +
          "'" +
          (p.code === org.planTier ? " selected" : "") +
          ">" +
          esc(p.label) +
          " · " +
          esc(p.monthlyMad) +
          " MAD / mois</option>"
      )
      .join("");
    const ok = await HireLink.openModal({
      title: "Changer le plan",
      description: "Le paiement et la facture sont enregistrés avec ce changement.",
      confirmLabel: "Confirmer le changement",
      bodyHtml:
        "<label class='field'><span>Plan</span><select id='org-plan'>" +
        options +
        "</select></label>" +
        "<label class='field'><span>Période</span><select id='org-cycle'><option value='month'>Mensuelle</option><option value='year'>Annuelle</option></select></label>",
    });
    if (!ok) return;
    const planTier = document.getElementById("org-plan").value;
    const cycle = document.getElementById("org-cycle").value;
    await patchOrg(org.id, { planTier, cycle });
  }

  async function patchOrg(id, body) {
    try {
      await HireLinkAPI.api("/admin/organizations/" + id, { method: "PATCH", body });
      HireLink.toast("Entreprise mise à jour", "", "ok");
      await refreshOrgs();
      invoices = await HireLinkAPI.api("/admin/invoices");
      paintMoney();
      paintDash();
    } catch (err) {
      HireLink.toast("Entreprise", err.message, "danger");
    }
  }

  function paintPlans() {
    document.getElementById("plan-cards").innerHTML = plans
      .map((p) => {
        const count = orgs.filter((o) => o.planTier === p.code).length;
        const features = String(p.features || "")
          .split("\n")
          .map((line) => line.trim())
          .filter(Boolean);
        return (
          '<article class="plan-admin-card"><p class="muted" style="margin:0">' +
          esc(p.audience === "CANDIDAT" ? "Candidat" : "Recruteur") +
          "</p><h3>" +
          esc(p.label) +
          "</h3><p><strong>" +
          esc(p.monthlyMad) +
          "</strong> MAD / mois · <strong>" +
          esc(p.yearlyMad) +
          "</strong> MAD / an</p><p>" +
          esc(p.credits) +
          " crédits</p><ul>" +
          (features.length ? features.map((line) => "<li>" + esc(line) + "</li>").join("") : "<li>Aucune fonctionnalité</li>") +
          "</ul><p class='muted'>" +
          count +
          " entreprise(s)</p><button type='button' class='btn btn-soft btn-sm' data-plan='" +
          esc(p.code) +
          "'>Modifier</button></article>"
        );
      })
      .join("");
    document.querySelectorAll("#plan-cards [data-plan]").forEach((btn) => {
      btn.onclick = () => editPlan(btn.getAttribute("data-plan"));
    });
  }

  async function editPlan(code) {
    const plan = plans.find((p) => p.code === code);
    if (!plan) return;
    const ok = await HireLink.openModal({
      title: "Modifier " + plan.label,
      description: "Nom, prix, fonctionnalités et crédits de ce plan.",
      confirmLabel: "Enregistrer",
      large: true,
      bodyHtml:
        "<label class='field'><span>Nom</span><input id='plan-label' value='" +
        esc(plan.label) +
        "' /></label>" +
        "<div class='row' style='gap:.6rem'>" +
        "<label class='field'><span>Prix mensuel (MAD)</span><input id='plan-month' type='number' min='0' value='" +
        esc(plan.monthlyMad) +
        "' /></label>" +
        "<label class='field'><span>Prix annuel (MAD)</span><input id='plan-year' type='number' min='0' value='" +
        esc(plan.yearlyMad) +
        "' /></label>" +
        "<label class='field'><span>Crédits</span><input id='plan-credits' type='number' min='0' value='" +
        esc(plan.credits) +
        "' /></label></div>" +
        "<label class='field'><span>Fonctionnalités, une par ligne</span><textarea id='plan-features' rows='5'>" +
        esc(plan.features || "") +
        "</textarea></label>",
    });
    if (!ok) return;
    try {
      await HireLinkAPI.api("/admin/plans/" + code, {
        method: "PATCH",
        body: {
          label: document.getElementById("plan-label").value,
          monthlyMad: Number(document.getElementById("plan-month").value || 0),
          yearlyMad: Number(document.getElementById("plan-year").value || 0),
          credits: Number(document.getElementById("plan-credits").value || 0),
          features: document.getElementById("plan-features").value,
        },
      });
      plans = await HireLinkAPI.api("/admin/plans");
      paintPlans();
      paintDash();
      HireLink.toast("Plan enregistré", planLabel(code), "ok");
    } catch (err) {
      HireLink.toast("Plan", err.message, "danger");
    }
  }

  function paintMoney() {
    const pay = document.getElementById("payments-body");
    const inv = document.getElementById("invoices-body");
    if (!pay || !inv) return;
    pay.innerHTML = "";
    inv.innerHTML = "";
    if (!invoices.length) {
      pay.innerHTML = "<tr><td colspan='6' class='muted'>Aucun paiement pour le moment.</td></tr>";
      inv.innerHTML = "<tr><td colspan='7' class='muted'>Aucune facture. Elles apparaissent avec les changements de plan.</td></tr>";
      return;
    }
    invoices.forEach((row) => {
      const when = row.issuedAt ? new Date(row.issuedAt).toLocaleString("fr-FR") : "";
      const tr = document.createElement("tr");
      tr.innerHTML = "<td></td><td></td><td></td><td></td><td></td><td></td>";
      tr.children[0].textContent = when;
      tr.children[1].textContent = row.organization;
      tr.children[2].textContent = row.planLabel;
      tr.children[3].textContent = row.cycle === "year" ? "Annuelle" : "Mensuelle";
      tr.children[4].textContent = money(row.amountTtc);
      tr.children[5].textContent = PAY_FR[row.status] || row.status;
      pay.appendChild(tr);

      const doc = document.createElement("tr");
      doc.innerHTML = "<td></td><td></td><td></td><td></td><td></td><td></td><td></td>";
      doc.children[0].textContent = row.number;
      doc.children[1].textContent = when;
      doc.children[2].textContent = row.organization;
      doc.children[3].textContent = money(row.amountHt);
      doc.children[4].textContent = money(row.tva);
      doc.children[5].textContent = money(row.amountTtc);
      const open = document.createElement("button");
      open.type = "button";
      open.className = "btn btn-sm btn-soft";
      open.textContent = "Ouvrir";
      open.onclick = () => printInvoice(row);
      doc.children[6].appendChild(open);
      inv.appendChild(doc);
    });
  }

  function printInvoice(row) {
    const cycle = row.cycle === "year" ? "Annuelle" : "Mensuelle";
    const html =
      "<!DOCTYPE html><html lang='fr'><head><meta charset='utf-8'><title>Facture " +
      esc(row.number) +
      "</title><style>body{font-family:Georgia,serif;color:#1a1214;padding:2rem;max-width:720px;margin:auto}h1{margin:0}table{width:100%;border-collapse:collapse;margin-top:1.5rem}td{padding:.45rem 0;border-bottom:1px solid #eee}.total{font-weight:700}</style></head><body>" +
      "<p>HireLink</p><h1>Facture " +
      esc(row.number) +
      "</h1><p>Émise le " +
      esc(new Date(row.issuedAt).toLocaleString("fr-FR")) +
      " · " +
      esc(PAY_FR[row.status] || row.status) +
      "</p><p><strong>" +
      esc(row.organization) +
      "</strong><br>ICE " +
      esc(row.ice || "—") +
      "</p><table><tr><td>" +
      esc(row.planLabel) +
      " · " +
      cycle +
      "</td><td style='text-align:right'>" +
      money(row.amountHt) +
      " HT</td></tr><tr><td>TVA 20 %</td><td style='text-align:right'>" +
      money(row.tva) +
      "</td></tr><tr class='total'><td>Total TTC</td><td style='text-align:right'>" +
      money(row.amountTtc) +
      "</td></tr></table></body></html>";
    const w = window.open("", "_blank");
    if (!w) {
      HireLink.toast("Facture", "Autorisez les fenêtres pour afficher la facture", "warn");
      return;
    }
    w.document.open();
    w.document.write(html);
    w.document.close();
  }

  function paintUsers() {
    const q = (document.getElementById("user-search").value || "").trim().toLowerCase();
    const body = document.getElementById("users-body");
    body.innerHTML = "";
    const rows = users.filter((u) => {
      if (!q) return true;
      return (u.fullName + " " + u.email + " " + u.role).toLowerCase().indexOf(q) >= 0;
    });
    if (!rows.length) {
      body.innerHTML = "<tr><td colspan='5' class='muted'>Aucun compte ne correspond.</td></tr>";
      return;
    }
    rows.forEach((u) => {
      const tr = document.createElement("tr");
      tr.innerHTML = "<td></td><td></td><td></td><td></td><td></td>";
      tr.children[0].textContent = u.fullName;
      tr.children[1].textContent = u.email;
      tr.children[2].textContent = u.role;
      tr.children[3].textContent = u.active ? "Oui" : "Non";
      const tgl = document.createElement("button");
      tgl.type = "button";
      tgl.className = "btn btn-sm btn-ghost";
      tgl.textContent = u.active ? "Désactiver" : "Activer";
      tgl.onclick = () => confirmUser(u);
      tr.children[4].appendChild(tgl);
      body.appendChild(tr);
    });
  }

  async function confirmUser(u) {
    const ok = await HireLink.openModal({
      title: u.active ? "Désactiver ce compte ?" : "Activer ce compte ?",
      description: u.fullName + " · " + u.email,
      confirmLabel: "Confirmer",
      danger: !!u.active,
    });
    if (!ok) return;
    try {
      await HireLinkAPI.api("/admin/users/" + u.id + "/active", {
        method: "PATCH",
        body: { active: !u.active },
      });
      HireLink.toast("Compte mis à jour", u.email, "ok");
      users = await HireLinkAPI.api("/admin/users");
      paintUsers();
    } catch (err) {
      HireLink.toast("Compte", err.message, "danger");
    }
  }

  async function refreshOrgs() {
    orgs = await HireLinkAPI.api("/admin/organizations");
    paintOrgs();
    paintPlans();
  }

  const userSearch = document.getElementById("user-search");
  if (userSearch) userSearch.addEventListener("input", paintUsers);

  document.getElementById("btn-logout").addEventListener("click", async () => {
    const ok = await HireLink.openModal({
      title: "Se déconnecter ?",
      confirmLabel: "Déconnexion",
      danger: true,
    });
    if (!ok) return;
    HireLinkAPI.clearAuth();
    location.href = "../auth.html?mode=login";
  });

  async function load() {
    try {
      const [statBody, orgList, planList, invoiceList, userList, chartBody] = await Promise.all([
        HireLinkAPI.api("/admin/stats"),
        HireLinkAPI.api("/admin/organizations"),
        HireLinkAPI.api("/admin/plans"),
        HireLinkAPI.api("/admin/invoices").catch(() => []),
        HireLinkAPI.api("/admin/users"),
        HireLinkAPI.api("/admin/charts"),
      ]);
      stats = statBody || {};
      orgs = orgList || [];
      plans = planList || [];
      invoices = invoiceList || [];
      users = userList || [];
      charts = chartBody || { applicationsByStatus: {} };
      paintDash();
      paintOrgs();
      paintPlans();
      paintMoney();
      paintUsers();
    } catch (err) {
      HireLink.toast("Admin", err.message, "danger");
    }
  }

  load();
})();
