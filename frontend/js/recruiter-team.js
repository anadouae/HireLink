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
    $("org-sub").textContent = "Votre rôle : " + (RC.ROLE_FR[o.myRole] || o.myRole || "—") + " · formule " + (o.planTier === "PREMIUM" ? "Premium" : "Accès");
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
    if (!colorTouched) $("co-color").value = savedColor(o) || $("co-color").value;
    paintHq();
    paintPreview();
    if (!savedColor(o)) extractColor();
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

  let members = [];
  let newMemberId = null;
  const ROLE_CLS = { OWNER: "r-owner", RECRUTEUR: "r-rec", HIRING_MANAGER: "r-hm" };
  function paintMembers(list, meOwner) {
    const box = $("members-list");
    const order = ["OWNER", "RECRUTEUR", "HIRING_MANAGER"];
    const titles = { OWNER: "Direction", RECRUTEUR: "Recrutement", HIRING_MANAGER: "Managers" };
    const rows = order.map((r) => ({ r, list: list.filter((m) => (m.orgRole || "RECRUTEUR") === r) })).filter((g) => g.list.length);
    box.innerHTML = '<div class="hq-chart">' + rows.map((g, gi) =>
      '<div class="hq-floor" style="--d:' + gi * 90 + 'ms"><h3>' + titles[g.r] + " <small>" + g.list.length + '</small></h3><div class="hq-desks">' +
      g.list.map((m, i) => {
        const name = (m.fullName || m.email).replace(/\s*\(alias\)/, "");
        const me = m.userId === session.userId;
        return '<article class="hq-desk ' + (ROLE_CLS[m.orgRole] || "") + (String(m.userId) === String(newMemberId) ? " is-new" : "") + '" style="--i:' + i + '" data-focus="member:' + m.userId + '">' +
          '<span class="hq-ava">' + esc(RC.initials(name)) + '<i class="hq-presence" title="Membre actif"></i></span>' +
          '<div class="hq-desk-txt"><strong>' + esc(name) + (me ? " <em>(vous)</em>" : "") + "</strong><span>" + esc(m.email) + '</span><span class="hq-role ' + (ROLE_CLS[m.orgRole] || "") + '">' + esc(RC.ROLE_FR[m.orgRole] || m.orgRole) + "</span></div>" +
          (meOwner && !me ? '<button type="button" class="hq-remove" data-remove="' + m.userId + '" data-name="' + esc(name) + '" aria-label="Retirer ' + esc(name) + '" title="Retirer de l’équipe">×</button>' : "") +
          "</article>";
      }).join("") + "</div></div>").join("") + "</div>" +
      (meOwner ? '<button type="button" class="hq-add" id="hq-add-desk"><span>+</span>Ajouter un membre</button>' : "");
    const add = $("hq-add-desk");
    if (add) add.addEventListener("click", openDrawer);
    if (newMemberId) {
      const el = box.querySelector(".hq-desk.is-new");
      if (el) el.scrollIntoView({ behavior: "smooth", block: "center" });
      setTimeout(() => { newMemberId = null; }, 100);
    }
  }

  function loadMembers() {
    const box = $("members-list");
    RC.skeleton(box, 3, 52);
    HireLinkAPI.api("/organizations/mine/members").then((list) => {
      $("members-count").textContent = list.length;
      $("kpi-members").textContent = list.length;
      const meOwner = org && org.myRole === "OWNER";
      members = list;
      paintMembers(list, meOwner);
      paintHq();
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

  const drawer = $("hq-drawer");
  let lastFocus = null;
  function openDrawer() {
    lastFocus = document.activeElement;
    drawer.classList.add("is-open"); drawer.setAttribute("aria-hidden", "false");
    setTimeout(() => $("invite-email").focus(), 220);
  }
  function closeDrawer() {
    drawer.classList.remove("is-open"); drawer.setAttribute("aria-hidden", "true");
    if (lastFocus && lastFocus.focus) lastFocus.focus();
  }
  $("btn-invite").addEventListener("click", openDrawer);
  $("hq-drawer-close").addEventListener("click", closeDrawer);
  $("hq-drawer-cancel").addEventListener("click", closeDrawer);
  drawer.addEventListener("click", (e) => { if (e.target === drawer) closeDrawer(); });
  document.addEventListener("keydown", (e) => { if (e.key === "Escape" && drawer.classList.contains("is-open")) closeDrawer(); });
  $("hq-invite-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const email = ($("invite-email").value || "").trim();
    if (!email) { RC.toast("E-mail requis", "Saisissez l’adresse du collègue.", "warn"); return; }
    const role = (document.querySelector('input[name="invite-role"]:checked') || {}).value || "RECRUTEUR";
    const btn = e.submitter || e.target.querySelector("button[type=submit]");
    RC.busy(btn, true);
    try {
      const m = await HireLinkAPI.api("/organizations/mine/invite", { method: "POST", body: { email: email, orgRole: role } });
      RC.toast("Bienvenue dans l’équipe", m.email + " · " + (RC.ROLE_FR[m.orgRole] || m.orgRole), "ok");
      newMemberId = m.userId;
      e.target.reset();
      closeDrawer();
      selectTab("members");
      RC.cacheClear("members");
      loadMembers();
    } catch (err) { RC.toast("Ajout impossible", err.message, "danger"); } finally { RC.busy(btn, false); }
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
    let file = e.target.files && e.target.files[0];
    if (!file) return;
    // Recadrage avant l'envoi : le logo s'adapte toujours à son cadre.
    if (window.HLUI && HLUI.cropImage && /^image\/(png|jpe?g|webp)$/i.test(file.type)) {
      const blob = await HLUI.cropImage(file, { shape: "square", contain: true });
      if (!blob) { e.target.value = ""; return; }
      file = new File([blob], (file.name || "logo").replace(/\.[^.]+$/, "") + ".png", { type: "image/png" });
    }
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
      extractColor(true);
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
    .then((c) => { $("kpi-credits").textContent = c.remaining + " / " + c.limit; $("kpi-plan").textContent = "Formule " + (c.plan === "PREMIUM" ? "Premium" : "Accès") + " · ce mois"; })
    .catch(() => { $("kpi-credits").textContent = "—"; });


  /* ---------------------------------------------------------- siège animé */
  let colorTouched = false;
  const COLOR_KEY = "hirelink.org.color.";
  function savedColor(o) { try { return localStorage.getItem(COLOR_KEY + ((o || org || {}).id || "x")) || ""; } catch (_) { return ""; } }
  function brand() { return $("co-color").value || "#7a2032"; }
  function logoSrc(o) { const u = o && o.logoUrl; return u && !/google\.com\/s2\/favicons|gstatic\.com\/faviconV2/i.test(u) ? HireLinkAPI.mediaUrl(u) : ""; }
  function live() {
    const o = org || {};
    return { name: $("co-name").value || o.name || "Entreprise", sector: $("co-sector").value || o.sector || "", city: $("co-city").value || o.city || "",
      bio: $("co-bio").value || o.presentation || "", logo: logoSrc(o), verified: !!o.verified, color: brand() };
  }
  function flag(city) { return window.HireLinkPlaces && city ? HireLinkPlaces.flagOf(city) : ""; }
  function logoMark(d, cls) {
    const letters = esc(RC.initials(d.name));
    return '<span class="' + cls + '">' + (d.logo ? '<img alt="" src="' + esc(d.logo) + '" data-mono="' + letters + '"/>' : "<b>" + letters + "</b>") + "</span>";
  }
  function bindLogoFallback(root) {
    root.querySelectorAll("img[data-mono]").forEach((img) => img.addEventListener("error", () => { const b = document.createElement("b"); b.textContent = img.dataset.mono; img.replaceWith(b); }));
  }
  function windowsSvg() {
    let w = "";
    for (let r = 0; r < 4; r++) for (let c = 0; c < 6; c++) {
      if (r === 3 && (c === 2 || c === 3)) continue;
      w += '<rect class="hq-win" x="' + (64 + c * 34) + '" y="' + (78 + r * 30) + '" width="20" height="16" rx="2" style="--w:' + ((r * 6 + c) * 37 % 11) + '"/>';
    }
    return w;
  }
  function paintHq() {
    const box = $("hq");
    if (!box || !org) return;
    const d = live();
    const n = members.length;
    const roles = { OWNER: 0, RECRUTEUR: 0, HIRING_MANAGER: 0 };
    members.forEach((m) => { roles[m.orgRole] = (roles[m.orgRole] || 0) + 1; });
    box.style.setProperty("--brand", d.color);
    box.innerHTML =
      '<div class="hq-sky" aria-hidden="true"><i></i><i></i><i></i></div>' +
      '<div class="hq-bwrap"><svg class="hq-building" viewBox="0 0 330 250" role="img" aria-label="Siège de ' + esc(d.name) + '">' +
      '<rect x="20" y="236" width="290" height="8" rx="4" class="hq-ground"/>' +
      '<rect x="50" y="40" width="230" height="198" rx="8" class="hq-facade"/>' +
      '<rect x="50" y="40" width="230" height="26" rx="8" class="hq-roof"/>' +
      windowsSvg() +
      '<rect x="138" y="190" width="54" height="48" rx="4" class="hq-door"/><path d="M165 190v48" class="hq-door-line"/>' +
      '<rect x="245" y="14" width="3" height="28" class="hq-mast"/><path d="M248 15h22l-6 6 6 6h-22z" class="hq-flag"/>' +
      "</svg>" +
      '<div class="hq-sign">' + logoMark(d, "hq-sign-logo") + "</div></div>" +
      '<div class="hq-id"><small>Siège</small><h2>' + esc(d.name) + (d.verified ? ' <span class="hq-verified">' + (window.HLIcons ? HLIcons.svg("shield", 14) : "") + "Vérifiée</span>" : "") + "</h2>" +
      "<p>" + [d.sector ? esc(d.sector) : "", d.city ? (flag(d.city) + " " + esc(d.city)).trim() : ""].filter(Boolean).join(" · ") + "</p>" +
      '<div class="hq-stats"><span><b>' + n + "</b> membre" + (n > 1 ? "s" : "") + "</span><span><b>" + (roles.RECRUTEUR || 0) + "</b> recruteur" + ((roles.RECRUTEUR || 0) > 1 ? "s" : "") + "</span><span><b>" + (roles.HIRING_MANAGER || 0) + "</b> manager" + ((roles.HIRING_MANAGER || 0) > 1 ? "s" : "") + "</span><span><b>" + offersOpen + "</b> offre" + (offersOpen > 1 ? "s" : "") + " ouverte" + (offersOpen > 1 ? "s" : "") + "</span></div></div>";
    bindLogoFallback(box);
  }
  function paintPreview() {
    const box = $("co-preview");
    if (!box || !org) return;
    const d = live();
    box.style.setProperty("--brand", d.color);
    box.innerHTML = '<article class="hq-preview"><div class="hq-preview-cover"></div><div class="hq-preview-head">' + logoMark(d, "hq-preview-logo") +
      "<div><strong>" + esc(d.name) + "</strong><span>" + [d.sector, d.city ? (flag(d.city) + " " + d.city).trim() : ""].filter(Boolean).map(esc).join(" · ") + "</span></div>" +
      '<a class="btn btn-soft btn-sm" href="../company.html?name=' + encodeURIComponent(org.name || "") + '" target="_blank" rel="noopener">Page publique</a></div>' +
      "<p>" + (d.bio ? esc(d.bio) : '<span class="rc-muted">Ajoutez une présentation dans Paramètres : elle s’affichera ici.</span>') + "</p></article>";
    bindLogoFallback(box);
  }
  let liveTimer = null;
  ["co-name", "co-sector", "co-city", "co-bio"].forEach((id) => $(id).addEventListener("input", () => { clearTimeout(liveTimer); liveTimer = setTimeout(() => { paintHq(); paintPreview(); }, 120); }));
  $("co-color").addEventListener("input", () => {
    colorTouched = true;
    try { localStorage.setItem(COLOR_KEY + (org && org.id || "x"), brand()); } catch (_) {}
    paintHq(); paintPreview();
  });
  $("co-color-auto").addEventListener("click", () => extractColor(true));

  /** Couleur dominante du logo (pixels saturés, ni trop clairs ni trop sombres). */
  function extractColor(force) {
    const src = logoSrc(org);
    if (!src) return;
    const img = new Image();
    img.crossOrigin = "anonymous";
    img.onload = () => {
      try {
        const c = document.createElement("canvas"); c.width = c.height = 48;
        const x = c.getContext("2d"); x.drawImage(img, 0, 0, 48, 48);
        const px = x.getImageData(0, 0, 48, 48).data;
        const buckets = {};
        for (let i = 0; i < px.length; i += 4) {
          if (px[i + 3] < 200) continue;
          const r = px[i], g = px[i + 1], b = px[i + 2], mx = Math.max(r, g, b), mn = Math.min(r, g, b);
          if (mx > 235 && mn > 215) continue;
          if (mx < 35) continue;
          if (mx - mn < 40) continue;
          const k = (r >> 5) + "," + (g >> 5) + "," + (b >> 5);
          const e = buckets[k] || (buckets[k] = { n: 0, r: 0, g: 0, b: 0 });
          e.n++; e.r += r; e.g += g; e.b += b;
        }
        const best = Object.values(buckets).sort((a, b) => b.n - a.n)[0];
        if (!best) { if (force) RC.toast("Couleur", "Logo trop neutre : choisissez une couleur à la main.", "info"); return; }
        const hex = "#" + [best.r, best.g, best.b].map((v) => Math.round(v / best.n).toString(16).padStart(2, "0")).join("");
        $("co-color").value = hex;
        try { localStorage.setItem(COLOR_KEY + (org && org.id || "x"), hex); } catch (_) {}
        paintHq(); paintPreview();
      } catch (_) { if (force) RC.toast("Couleur", "Couleur du logo illisible : choisissez-la à la main.", "info"); }
    };
    img.src = src + (src.indexOf("?") >= 0 ? "&" : "?") + "c=1";
  }

  /* ---------------------------------------------------------------- onglets */
  const TABS = ["overview", "members", "offers", "settings"];
  function selectTab(name, push) {
    if (TABS.indexOf(name) < 0) name = "overview";
    document.querySelectorAll(".hq-tab").forEach((t) => {
      const on = t.dataset.tab === name;
      t.setAttribute("aria-selected", String(on));
      t.tabIndex = on ? 0 : -1;
      if (on) {
        const ink = document.querySelector(".hq-tab-ink");
        if (ink) { ink.style.width = t.offsetWidth + "px"; ink.style.transform = "translateX(" + t.offsetLeft + "px)"; }
      }
    });
    document.querySelectorAll(".hq-panel").forEach((p) => {
      const on = p.dataset.panel === name;
      if (on && p.hidden) { p.hidden = false; p.classList.remove("is-in"); void p.offsetWidth; p.classList.add("is-in"); }
      else if (!on) p.hidden = true;
    });
    const u = new URL(location.href);
    if (name === "overview") u.searchParams.delete("tab"); else u.searchParams.set("tab", name);
    history[push ? "pushState" : "replaceState"](null, "", u.pathname + u.search + u.hash);
  }
  document.querySelectorAll(".hq-tab").forEach((t, i, all) => {
    t.addEventListener("click", () => selectTab(t.dataset.tab, true));
    t.addEventListener("keydown", (e) => {
      if (e.key !== "ArrowRight" && e.key !== "ArrowLeft") return;
      const n = all[(i + (e.key === "ArrowRight" ? 1 : all.length - 1)) % all.length];
      n.focus(); selectTab(n.dataset.tab, true);
    });
  });
  window.addEventListener("popstate", () => selectTab(new URLSearchParams(location.search).get("tab") || "overview"));
  window.addEventListener("resize", () => selectTab(new URLSearchParams(location.search).get("tab") || "overview"));
  window.addEventListener("hirelink:focus-request", (e) => {
    const k = String((e.detail && (e.detail.key || e.detail)) || "");
    if (/^(member|team)/.test(k)) selectTab("members");
  });
  (function initialTab() {
    const q = new URLSearchParams(location.search);
    const f = q.get("focus") || "";
    selectTab(/^(member|team)/.test(f) ? "members" : q.get("tab") || (location.hash === "#new-member" ? "members" : "overview"));
    if (location.hash === "#new-member") setTimeout(openDrawer, 400);
  })();

  /* ------------------------------------------------------------------ offres */
  let offersOpen = 0;
  RC.data.offers((list) => {
    list = list || [];
    offersOpen = list.filter((o) => o.published && !o.suspended).length;
    $("offers-count").textContent = list.length;
    const box = $("hq-offers");
    if (!list.length) { RC.empty(box, { title: "Aucune offre", text: "Publiez une première offre pour recevoir des candidatures.", action: { href: "recruiter-offers.html#new", label: "Nouvelle offre" } }); paintHq(); return; }
    box.innerHTML = '<div class="rc-list">' + list.map((o, i) =>
      '<a class="rc-item hq-offer" style="--i:' + i + '" href="recruiter-pipeline.html?offer=' + o.id + '"><div class="rc-item-main"><strong>' + esc(o.title) + "</strong><span>" +
      [o.location ? (flag(o.location) + " " + o.location).trim() : "", o.contractType, o.deadline ? "Clôture " + RC.fmtDate(o.deadline) : ""].filter(Boolean).map(esc).join(" · ") +
      '</span></div><span class="rc-pill' + (o.published && !o.suspended ? " is-ok" : "") + '">' + (o.suspended ? "Suspendue" : o.published ? "Publiée" : "Brouillon") + "</span></a>").join("") + "</div>";
    paintHq();
  }, () => {});

  loadOrg();
  loadMembers();
})();
