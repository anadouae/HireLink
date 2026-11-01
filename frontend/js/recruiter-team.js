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

  function paintLogo(o, bust) {
    const box = $("co-logo-box");
    let src = o.logoUrl ? HireLinkAPI.mediaUrl(o.logoUrl) : "";
    if (src && bust) src += (src.indexOf("?") >= 0 ? "&" : "?") + "t=" + Date.now();
    const letter = String(o.name || "E").charAt(0).toUpperCase();
    if (src) {
      box.innerHTML = '<img alt="Logo ' + esc(o.name) + '" src="' + esc(src) + '" />';
      box.firstChild.onerror = () => {
        box.textContent = letter;
        RC.toast("Logo", "Image enregistrée mais aperçu impossible — rechargez la page.", "warn");
      };
    } else box.textContent = letter;
  }

  const ICE_PORTALS = [
    { label: "ice.gov.ma", url: "https://www.ice.gov.ma/" },
    { label: "DirectInfo OMPIC", url: "https://www.directinfo.ma/" },
    { label: "OMPIC — ICE", url: "http://www.ompic.ma/fr/content/identifiant-commun-de-lentreprise" },
  ];

  function fmtWhen(iso) {
    if (!iso) return "";
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return String(iso);
    return d.toLocaleString("fr-MA", { dateStyle: "short", timeStyle: "short" });
  }

  function icePortalsHtml(list) {
    const links = (list && list.length ? list : ICE_PORTALS)
      .map((p) => '<a href="' + esc(p.url) + '" target="_blank" rel="noopener noreferrer">' + esc(p.label) + "</a>")
      .join("");
    return '<div class="ice-status-links">' + links + "</div>";
  }

  function paintIceFromOrg(o) {
    const ice = (o.ice || "").trim();
    if (!ice) {
      paintIcePanel({ empty: true });
      return;
    }
    if (o.iceFormatValid === false) {
      paintIcePanel({
        tone: "danger",
        title: "Format ICE invalide",
        message: "L’ICE doit contenir exactement 15 chiffres (9 + 4 + 2). Les zéros de tête sont conservés.",
        ice: ice,
        portals: true,
      });
      return;
    }
    if (o.iceLegalName) {
      paintIcePanel({
        tone: "ok",
        title: "ICE vérifié : entreprise identifiée",
        message: "Identité confirmée via le référentiel HireLink (pas un extrait OMPIC en direct).",
        ice: ice,
        legalName: o.iceLegalName,
        address: o.iceAddress || o.city || "",
        source: o.iceSource || "Référentiel HireLink",
        checkedAt: o.iceCheckedAt,
      });
      return;
    }
    if (o.iceFormatValid === true) {
      paintIcePanel({
        tone: "warn",
        title: "Format valide, entreprise non confirmée",
        message: "Aucune API OMPIC / DirectInfo / ice.gov.ma gratuite n’est disponible. Vérifiez manuellement sur un portail officiel, puis demandez une validation admin si besoin.",
        ice: ice,
        checkedAt: o.iceCheckedAt,
        portals: true,
      });
      return;
    }
    paintIcePanel({ empty: true, hint: "Saisissez l’ICE pour lancer la vérification automatique." });
  }

  function paintIcePanel(s) {
    const box = $("ice-status");
    if (!box) return;
    if (s.empty) {
      box.innerHTML = s.hint
        ? '<p class="muted" style="margin:.35rem 0 0;font-size:.84rem">' + esc(s.hint) + "</p>"
        : "";
      return;
    }
    const tone = s.tone === "ok" ? "ok" : s.tone === "danger" ? "danger" : s.tone === "info" ? "info" : "";
    const cls = "rc-banner" + (tone ? " rc-banner--" + tone : "");
    const rows = [];
    if (s.ice) rows.push("<li><strong>ICE</strong>" + esc(s.ice) + "</li>");
    if (s.legalName) rows.push("<li><strong>Dénomination légale</strong>" + esc(s.legalName) + "</li>");
    if (s.address) rows.push("<li><strong>Adresse / ville</strong>" + esc(s.address) + "</li>");
    if (s.source) rows.push("<li><strong>Source</strong>" + esc(s.source) + "</li>");
    if (s.checkedAt) rows.push("<li><strong>Vérifié le</strong>" + esc(fmtWhen(s.checkedAt)) + "</li>");
    box.innerHTML =
      '<div class="' + cls + '">' +
      "<div><strong>" + esc(s.title || "ICE") + "</strong>" +
      (s.message ? "<span>" + esc(s.message) + "</span>" : "") + "</div>" +
      (rows.length ? '<ul class="ice-status-meta">' + rows.join("") + "</ul>" : "") +
      (s.portals ? icePortalsHtml(s.manualPortals) : "") +
      "</div>";
  }

  let iceCheckTimer = null;
  let iceCheckSeq = 0;

  async function runIceCheck() {
    const ice = ($("co-ice").value || "").trim();
    const name = ($("co-name").value || "").trim();
    if (!ice) {
      paintIcePanel({ empty: true, hint: "Saisissez l’ICE pour lancer la vérification automatique." });
      return;
    }
    const seq = ++iceCheckSeq;
    paintIcePanel({ tone: "info", title: "Vérification en cours…", message: "Contrôle du format puis recherche d’identité.", ice: ice });
    try {
      const r = await HireLinkAPI.api("/organizations/mine/ice/check", {
        method: "POST",
        body: { ice: ice, name: name },
      });
      if (seq !== iceCheckSeq) return;
      if (!r.formatValid) {
        paintIcePanel({
          tone: "danger",
          title: "Format ICE invalide",
          message: r.formatMessage || "L’ICE doit contenir exactement 15 chiffres.",
          ice: r.normalized || ice,
          portals: true,
          manualPortals: r.manualPortals,
        });
        return;
      }
      if (r.identityFound) {
        paintIcePanel({
          tone: "ok",
          title: r.identityMessage || "ICE vérifié : entreprise identifiée",
          message: "Structure 9 + 4 + 2 respectée. Identité confirmée dans le référentiel HireLink.",
          ice: r.normalized || ice,
          legalName: r.legalName || "",
          address: [r.address, r.city].filter(Boolean).join(" · "),
          source: r.source || "",
          checkedAt: r.checkedAt,
        });
        return;
      }
      paintIcePanel({
        tone: "warn",
        title: "Format valide, entreprise non confirmée",
        message: r.identityMessage || "Vérifiez manuellement sur un portail officiel.",
        ice: r.normalized || ice,
        checkedAt: r.checkedAt,
        portals: true,
        manualPortals: r.manualPortals,
      });
    } catch (err) {
      if (seq !== iceCheckSeq) return;
      paintIcePanel({
        tone: "danger",
        title: "Vérification impossible",
        message: err.message || "Réessayez dans un instant.",
        ice: ice,
        portals: true,
      });
    }
  }

  function scheduleIceCheck() {
    clearTimeout(iceCheckTimer);
    iceCheckTimer = setTimeout(runIceCheck, 420);
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
      : "Saisissez un ICE à 15 chiffres : le format est contrôlé immédiatement, puis HireLink tente d’identifier l’entreprise. Sans confirmation automatique, utilisez les portails officiels ou la validation admin.";
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
    paintLogo(o, false);
    paintIceFromOrg(o);
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
    const ok = await Mascotte.confirm({ title: "Retirer ce membre ?", description: b.dataset.name + " perdra l’accès aux offres et dossiers de l’organisation.", confirmLabel: "Retirer", danger: true });
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
      description: "Le collègue crée (ou utilise) son compte HireLink avec son e-mail et son mot de passe, puis se connecte au même espace recruteur.",
      bodyHtml:
        '<div class="field"><label>E-mail</label><input id="invite-email" type="email" placeholder="manager@entreprise.ma" /></div>' +
        '<div class="field"><label>Rôle</label><select id="invite-role">' +
        '<option value="RECRUTEUR">Recruteur — pipeline, offres, agenda</option>' +
        '<option value="HIRING_MANAGER">Hiring Manager — voit seulement les dossiers qu’on lui assigne</option>' +
        "</select></div>" +
        '<p class="muted" style="margin:.35rem 0 0;font-size:.82rem">Pas d’espace séparé ni de mot de passe spécial : même connexion, droits filtrés. Vous assignez ensuite un dossier depuis le pipeline.</p>',
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

  $("co-ice").addEventListener("input", scheduleIceCheck);
  $("co-ice").addEventListener("blur", runIceCheck);
  $("co-name").addEventListener("change", () => {
    if (($("co-ice").value || "").trim()) scheduleIceCheck();
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
    // Aperçu immédiat (blob) pendant l’envoi.
    const localUrl = URL.createObjectURL(file);
    const box = $("co-logo-box");
    box.innerHTML = '<img alt="Aperçu logo" src="' + localUrl + '" />';
    const fd = new FormData();
    fd.append("file", file);
    try {
      const o = await HireLinkAPI.api("/organizations/mine/logo", { method: "POST", formData: fd, timeoutMs: 45000 });
      RC.cacheSet("org", o);
      org = o;
      paintLogo(o, true);
      RC.toast("Logo", "Image enregistrée — visible côté candidats.", "ok");
    } catch (err) {
      paintLogo(org || {}, false);
      RC.toast("Logo", err.message, "danger");
    } finally {
      URL.revokeObjectURL(localUrl);
      e.target.value = "";
    }
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
      if (!o.verified) RC.toast("Vérification", o.message || "Dossier transmis à l’administration.", "warn");
      else if (!Mascotte.celebrate({ cle: "organisation-validee", title: "Organisation validée !", description: "Votre badge « Entreprise vérifiée » est visible côté candidats." })) {
        RC.toast("Vérification", "Badge attribué automatiquement.", "ok");
      }
    } catch (err) { RC.toast("Vérification", err.message, "danger"); } finally { RC.busy(e.currentTarget, false); }
  });

  HireLinkAPI.api("/ai/credits")
    .then((c) => { $("kpi-credits").textContent = c.remaining + " / " + c.limit; $("kpi-plan").textContent = "Palier " + (c.plan || "ACCES") + " · ce mois"; })
    .catch(() => { $("kpi-credits").textContent = "—"; });

  loadOrg();
  loadMembers();
})();
