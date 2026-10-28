/** Entreprise & équipe : fiche entreprise, vérification (ICE), logo, membres de l'organisation. */
(function () {
  const RC = HireLinkApp;
  const session = RC.mount({ active: "team", title: "Entreprise & équipe" });
  if (!session) return;
  const $ = (id) => document.getElementById(id);
  const esc = RC.esc;
  let org = null;

  const VERIF = {
    REGISTRY_MATCH: { label: "Vérifiée (registre)", tone: "ok" },
    ADMIN_APPROVED: { label: "Vérifiée par l’administration", tone: "ok" },
    MANUAL_REVIEW: { label: "En revue par l’administration", tone: "warn" },
    PENDING: { label: "Vérification en attente", tone: "warn" },
    REJECTED: { label: "Vérification refusée", tone: "danger" },
  };

  function paintLogo(o) {
    const box = $("co-logo-box");
    const src = o.logoUrl && o.logoUrl.indexOf("google.com/s2/favicons") < 0 ? HireLinkAPI.mediaUrl(o.logoUrl) : "";
    const letter = String(o.name || "E").charAt(0).toUpperCase();
    if (src) {
      box.innerHTML = '<img alt="Logo ' + esc(o.name) + '" src="' + esc(src) + '" />';
      box.firstChild.onerror = () => { box.textContent = letter; };
    } else box.textContent = letter;
  }

  function paintOrg(o) {
    org = o;
    const canEdit = o.myRole === "OWNER" || o.myRole === "RECRUTEUR";
    $("org-name").textContent = o.name || "Organisation";
    $("org-sub").textContent = "Votre rôle : " + (RC.ROLE_FR[o.myRole] || o.myRole || "—") + " · palier " + (o.planTier || "ACCES");
    const v = o.verified ? { label: o.statusLabel || "Entreprise vérifiée", tone: "ok" } : VERIF[o.verificationStatus] || { label: o.statusLabel || "À vérifier", tone: "warn" };
    $("kpi-status").textContent = v.label;
    $("kpi-status").closest(".rc-kpi").className = "rc-kpi rc-kpi--" + v.tone;
    $("kpi-ice").textContent = o.ice ? "ICE " + o.ice : "ICE non renseigné";
    $("verify-hint").innerHTML = o.verified
      ? "Badge actif : vos offres peuvent être publiées et le badge apparaît côté candidats."
      : "Renseignez un ICE valide : la vérification est automatique si l’ICE et l’e-mail professionnel correspondent au registre, sinon l’administration valide votre dossier (Administration → Entreprises).";
    $("btn-recheck").hidden = !!o.verified || !canEdit;
    const f = $("company-form");
    ["co-name", "co-ice", "co-website", "co-sector", "co-city", "co-bio"].forEach((id) => { $(id).disabled = !canEdit; });
    $("co-name").value = o.name || "";
    $("co-ice").value = o.ice || "";
    $("co-website").value = o.website || "";
    $("co-sector").value = o.sector || "";
    $("co-city").value = o.city || "";
    $("co-bio").value = o.presentation || "";
    f.querySelectorAll("button[type=submit]").forEach((b) => (b.hidden = !canEdit));
    document.querySelectorAll("[data-edit]").forEach((n) => (n.hidden = !canEdit));
    $("btn-invite").hidden = o.myRole !== "OWNER";
    document.querySelectorAll("[data-manage]").forEach((n) => (n.hidden = !RC.canManage()));
    paintLogo(o);
    $("co-skel").hidden = true;
    $("co-body").hidden = false;
  }

  function loadOrg() {
    RC.data.org(paintOrg, (err) => {
      $("co-skel").hidden = true;
      RC.errorState($("co-error"), err.message, loadOrg);
    });
  }

  function loadMembers() {
    const box = $("members-list");
    RC.skeleton(box, 3, 52);
    HireLinkAPI.api("/organizations/mine/members").then((list) => {
      $("members-count").textContent = list.length;
      $("kpi-members").textContent = list.length;
      const meOwner = org && org.myRole === "OWNER";
      box.innerHTML = list.map((m) =>
        '<div class="rc-member rc-fade"><div class="rc-avatar">' + esc(RC.initials(m.fullName || m.email)) + '</div><div class="rc-item-main"><strong>' + esc((m.fullName || m.email).replace(/\s*\(alias\)/, "")) +
        "</strong><span>" + esc(m.email) + '</span></div><span class="badge ' + (m.orgRole === "OWNER" ? "badge-rose" : m.orgRole === "HIRING_MANAGER" ? "badge-info" : "badge-chrome") + '">' +
        esc(RC.ROLE_FR[m.orgRole] || m.orgRole) + "</span>" +
        (meOwner && m.userId !== session.userId ? '<button type="button" class="btn btn-ghost btn-sm" data-remove="' + m.userId + '" data-name="' + esc(m.fullName || m.email) + '">Retirer</button>' : "") + "</div>").join("");
    }).catch((err) => RC.errorState(box, err.message, loadMembers));
  }

  $("members-list").addEventListener("click", async (e) => {
    const b = e.target.closest("[data-remove]");
    if (!b) return;
    const ok = await HireLink.openModal({ title: "Retirer ce membre ?", description: b.dataset.name + " perdra l’accès aux offres et dossiers de l’organisation.", confirmLabel: "Retirer", danger: true });
    if (!ok) return;
    try {
      await HireLinkAPI.api("/organizations/mine/members/" + b.dataset.remove, { method: "DELETE" });
      RC.toast("Équipe", "Membre retiré", "ok");
      RC.cacheClear("members");
      loadMembers();
    } catch (err) { RC.toast("Équipe", err.message, "danger"); }
  });

  $("btn-invite").addEventListener("click", async () => {
    const ok = await HireLink.openModal({
      title: "Inviter un membre",
      description: "Le compte doit déjà exister avec le profil recruteur.",
      bodyHtml: '<div class="field"><label>E-mail</label><input id="invite-email" type="email" placeholder="rh@entreprise.ma" /></div>' +
        '<div class="field"><label>Rôle</label><select id="invite-role"><option value="RECRUTEUR">Recruteur — pipeline et offres</option><option value="HIRING_MANAGER">Hiring Manager — dossiers assignés, scorecard</option></select></div>',
      confirmLabel: "Inviter",
    });
    if (!ok) return;
    const email = ($("invite-email") || {}).value || "";
    if (!email.trim()) { RC.toast("E-mail requis", "Saisissez l’adresse du collègue.", "warn"); return; }
    try {
      const m = await HireLinkAPI.api("/organizations/mine/invite", { method: "POST", body: { email: email.trim(), orgRole: ($("invite-role") || {}).value || "RECRUTEUR" } });
      RC.toast("Invitation", m.email + " · " + (RC.ROLE_FR[m.orgRole] || m.orgRole), "ok");
      RC.cacheClear("members");
      loadMembers();
    } catch (err) { RC.toast("Invitation", err.message, "danger"); }
  });

  $("company-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const btn = e.submitter || e.target.querySelector("button[type=submit]");
    RC.busy(btn, true);
    try {
      const o = await HireLinkAPI.api("/organizations/mine", { method: "PUT", body: {
        name: $("co-name").value, ice: $("co-ice").value, website: $("co-website").value, sector: $("co-sector").value, city: $("co-city").value, presentation: $("co-bio").value,
      } });
      RC.cacheSet("org", o);
      paintOrg(o);
      RC.toast("Entreprise", "Fiche enregistrée — la vérification a été relancée.", "ok");
    } catch (err) { RC.toast("Entreprise", err.message, "danger"); } finally { RC.busy(btn, false); }
  });

  $("co-logo-file").addEventListener("change", async (e) => {
    const file = e.target.files && e.target.files[0];
    if (!file) return;
    const fd = new FormData();
    fd.append("file", file);
    try {
      const o = await HireLinkAPI.api("/organizations/mine/logo", { method: "POST", formData: fd, timeoutMs: 45000 });
      RC.cacheSet("org", o);
      paintOrg(o);
      RC.toast("Logo", "Image enregistrée — visible côté candidats.", "ok");
    } catch (err) { RC.toast("Logo", err.message, "danger"); } finally { e.target.value = ""; }
  });
  $("co-logo-clear").addEventListener("click", async () => {
    try {
      const o = await HireLinkAPI.api("/organizations/mine", { method: "PUT", body: { logoUrl: "" } });
      RC.cacheSet("org", o);
      paintOrg(o);
      RC.toast("Logo", "Logo retiré.", "ok");
    } catch (err) { RC.toast("Logo", err.message, "danger"); }
  });
  $("btn-recheck").addEventListener("click", async (e) => {
    RC.busy(e.currentTarget, true);
    try {
      const o = await HireLinkAPI.api("/organizations/mine/verify", { method: "POST" });
      RC.cacheSet("org", o);
      paintOrg(o);
      RC.toast("Vérification", o.verified ? "Badge attribué automatiquement." : o.message || "Dossier transmis à l’administration.", o.verified ? "ok" : "warn");
    } catch (err) { RC.toast("Vérification", err.message, "danger"); } finally { RC.busy(e.currentTarget, false); }
  });

  HireLinkAPI.api("/ai/credits")
    .then((c) => { $("kpi-credits").textContent = c.remaining + " / " + c.limit; $("kpi-plan").textContent = "Palier " + (c.plan || "ACCES") + " · ce mois"; })
    .catch(() => { $("kpi-credits").textContent = "—"; });

  loadOrg();
  loadMembers();
})();
