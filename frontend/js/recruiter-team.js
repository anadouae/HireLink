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
        ? '<p class="muted" style="margin:.35rem 0 0;font-size: var(--text-sm)">' + esc(s.hint) + "</p>"
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
    $("btn-invite").hidden = !RC.isCreator();
    document.querySelectorAll("[data-creator]").forEach((n) => (n.hidden = !RC.isCreator()));
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
  /** Seuls deux rôles s'attribuent ici : celui de créateur se transfère, il ne se choisit pas. */
  const ASSIGNABLE_ROLES = ["RECRUTEUR"];
  /** Gouvernement de l'espace : inviter, changer un rôle, suspendre, retirer, transférer, choisir la formule. */
  function canGovern() { return org ? org.myRole === "OWNER" : RC.isCreator(); }

  function memberActions(m, me) {
    if (!canGovern() || me) return "";
    const label = esc(m.fullName || m.email);
    if (m.pending) {
      return '<div class="hq-desk-actions">' +
        '<button type="button" class="btn btn-ghost btn-sm" data-resend="1" data-email="' + esc(m.email || "") + '" data-role="' + esc(m.orgRole || "RECRUTEUR") + '">Renvoyer l’invitation</button>' +
        '<button type="button" class="btn btn-soft btn-sm" data-cancel="' + m.userId + '" data-email="' + esc(m.email || "") + '" data-name="' + label + '">Annuler l’invitation</button>' +
        "</div>";
    }
    if (m.suspended) {
      return '<div class="hq-desk-actions">' +
        '<button type="button" class="btn btn-soft btn-sm" data-reinstate="' + m.userId + '" data-name="' + label + '">Rétablir l’accès</button>' +
        '<button type="button" class="btn btn-ghost btn-sm" data-rescind="' + m.userId + '" data-name="' + label + '">Retirer définitivement</button>' +
        "</div>";
    }
    if (m.orgRole === "OWNER") return "";
    if (m.orgRole === "HIRING_MANAGER") {
      return '<div class="hq-desk-actions"><span class="muted">Accès limité. Aucun nouveau droit n’est accordé.</span>' +
        '<button type="button" class="btn btn-ghost btn-sm" data-suspend="' + m.userId + '" data-name="' + label + '">Mettre en pause</button>' +
        '<button type="button" class="btn btn-ghost btn-sm" data-remove="' + m.userId + '" data-name="' + label + '">Retirer</button></div>';
    }
    const opts = ASSIGNABLE_ROLES.map((r) =>
      '<option value="' + r + '"' + (m.orgRole === r ? " selected" : "") + ">" + esc(RC.ROLE_FR[r]) + "</option>").join("");
    return '<div class="hq-desk-actions">' +
      '<label class="hq-role-set">Rôle<select data-role="' + m.userId + '" data-name="' + label + '" aria-label="Rôle de ' + label + '">' + opts + "</select></label>" +
      '<button type="button" class="btn btn-ghost btn-sm" data-suspend="' + m.userId + '" data-name="' + label + '">Mettre en pause</button>' +
      '<button type="button" class="btn btn-ghost btn-sm" data-remove="' + m.userId + '" data-name="' + label + '">Retirer</button>' +
      '<button type="button" class="btn btn-ghost btn-sm" data-transfer="' + m.userId + '" data-name="' + label + '">Transférer le rôle de créateur</button>' +
      "</div>";
  }

  /** Un compte en pause garde son siège : il occupe une place de l'équipe, même sans accès. */
  const seatOnly = (l) => (l || []).filter((m) => !m.pending);
  /** Seuls les membres en cours comptent dans le bureau : ni la pause, ni l'attente d'une invitation. */
  const activeOnly = (l) => seatOnly(l).filter((m) => !m.suspended);
  const statusLine = (m) => (m.suspended ? "En pause · " : "") + esc(m.roleLabel || RC.ROLE_FR[m.orgRole] || m.orgRole);

  function paintMembers(list) {
    const box = $("members-list");
    const order = ["OWNER", "RECRUTEUR", "HIRING_MANAGER"];
    const titles = { OWNER: "Créateur de l’espace", RECRUTEUR: "Recrutement", HIRING_MANAGER: "Accès limité" };
    const rows = order.map((r) => ({ r, list: list.filter((m) => (m.orgRole || "RECRUTEUR") === r && !m.suspended && !m.pending) })).filter((g) => g.list.length);
    const paused = list.filter((m) => m.suspended && !m.pending);
    const waiting = list.filter((m) => m.pending);
    box.innerHTML = '<div class="hq-chart">' + rows.map((g, gi) =>
      '<div class="hq-floor" style="--d:' + gi * 90 + 'ms"><h3>' + titles[g.r] + " <small>" + g.list.length + '</small></h3><div class="hq-desks">' +
      g.list.map((m, i) => {
        const name = (m.fullName || m.email).replace(/\s*\(alias\)/, "");
        const me = String(m.userId) === String(session.userId);
        return '<article class="hq-desk ' + (ROLE_CLS[m.orgRole] || "") + (String(m.userId) === String(newMemberId) ? " is-new" : "") + '" style="--i:' + i + '" data-focus="member:' + m.userId + '">' +
          '<span class="hq-ava">' + esc(RC.initials(name)) + '<i class="hq-presence" title="Membre actif"></i></span>' +
          '<div class="hq-desk-txt"><strong>' + esc(name) + (me ? " <em>(vous)</em>" : "") + "</strong><span>" + esc(m.email) + '</span><span class="hq-role ' + (ROLE_CLS[m.orgRole] || "") + '">' + esc(m.roleLabel || RC.ROLE_FR[m.orgRole] || m.orgRole) + "</span></div>" +
          memberActions(m, me) +
          "</article>";
      }).join("") + "</div></div>").join("") +
      (paused.length ? '<div class="hq-floor" style="--d:' + rows.length * 90 + 'ms"><h3>Accès en pause <small>' + paused.length + '</small></h3><div class="hq-desks">' +
        paused.map((m, i) => {
          const name = (m.fullName || m.email).replace(/\s*\(alias\)/, "");
          return '<article class="hq-desk is-paused" style="--i:' + i + '">' +
            '<span class="hq-ava">' + esc(RC.initials(name)) + '<i class="hq-presence hq-presence--paused" title="Accès en pause"></i></span>' +
            '<div class="hq-desk-txt"><strong>' + esc(name) + "</strong><span>" + esc(m.email) + '</span><span class="hq-role">En pause · ' + esc(m.roleLabel || RC.ROLE_FR[m.orgRole] || m.orgRole) + "</span></div>" +
            memberActions(m, false) + "</article>";
        }).join("") + "</div></div>" : "") +
      (waiting.length ? '<div class="hq-floor" style="--d:' + (rows.length + paused.length) * 90 + 'ms"><h3>Invitations en attente <small>' + waiting.length + '</small></h3><div class="hq-desks">' +
        waiting.map((m, i) => {
          const name = (m.fullName || m.email).replace(/\s*\(alias\)/, "");
          return '<article class="hq-desk is-waiting" style="--i:' + i + '">' +
            '<span class="hq-ava">' + esc(RC.initials(name)) + '<i class="hq-presence hq-presence--waiting" title="Invitation non reçue"></i></span>' +
            '<div class="hq-desk-txt"><strong>' + esc(name) + "</strong><span>" + esc(m.email) + '</span><span class="hq-role">Invitation envoyée · ' + statusLine(m) + "</span></div>" +
            memberActions(m, false) + "</article>";
        }).join("") + "</div></div>" : "") +
      "</div>" +
      (canGovern() ? '<button type="button" class="hq-add btn-invite-trigger" id="hq-add-desk"><span>+</span>Inviter un membre d’équipe</button>'
        : '<p class="hq-govern-note">Seul le créateur de l’espace invite, change un rôle ou retire un membre.</p>');
    const add = $("hq-add-desk");
    if (add) add.addEventListener("click", () => openDrawer());
    if (newMemberId) {
      const el = box.querySelector(".hq-desk.is-new");
      if (el) el.scrollIntoView({ behavior: "smooth", block: "center" });
      setTimeout(() => { newMemberId = null; }, 100);
    }
  }

  function paintSeats(o, memberCount) {
    const tier = o && o.planTier ? o.planTier : "ACCES";
    const max = o && o.maxMembers ? o.maxMembers : (tier === "PREMIUM" ? 25 : 5);
    const count = memberCount != null ? memberCount : (o && o.memberCount != null ? o.memberCount : (members ? seatOnly(members).length : 1));
    const rem = Math.max(0, max - count);

    const kpiM = $("kpi-members");
    if (kpiM) kpiM.textContent = count + " / " + max;
    const kpiS = $("kpi-seats");
    if (kpiS) kpiS.textContent = rem + " place" + (rem > 1 ? "s" : "") + " restante" + (rem > 1 ? "s" : "") + " · " + (tier === "PREMIUM" ? "Plan Premium" : "Plan Accès");

    const sTxt = $("hq-seats-txt");
    if (sTxt) sTxt.innerHTML = "Capacité d’équipe : <b>" + count + " / " + max + "</b> (" + rem + " place" + (rem > 1 ? "s" : "") + " disponible" + (rem > 1 ? "s" : "") + ")";
    const sPill = $("hq-seats-pill");
    if (sPill) {
      sPill.textContent = tier === "PREMIUM" ? "Plan Premium (25 places)" : "Plan Accès (5 places)";
      sPill.className = "badge " + (rem === 0 ? "badge-danger" : rem <= 1 ? "badge-warn" : "badge-ok");
    }
  }

  function loadMembers() {
    const box = $("members-list");
    RC.skeleton(box, 3, 52);
    HireLinkAPI.api("/organizations/mine/members").then((list) => {
      members = list;
      $("members-count").textContent = activeOnly(list).length;
      paintSeats(org, seatOnly(list).length);
      paintMembers(list);
      paintHq();
    }).catch((err) => RC.errorState(box, err.message, loadMembers));
  }

  /** Une action d'équipe : confirmation, appel, message clair, relecture du compteur de places. */
  async function memberAction(opts) {
    const ok = await Mascotte.confirm({
      title: opts.title, description: opts.description,
      confirmLabel: opts.confirmLabel, cancelLabel: opts.cancelLabel, danger: !!opts.danger,
    });
    if (!ok) return;
    try {
      await HireLinkAPI.api("/organizations/mine/members/" + opts.userId + opts.path, { method: opts.method, body: opts.body });
      RC.toast("Équipe", opts.done, "ok");
      RC.cacheClear("members");
      loadMembers();
    } catch (err) { RC.toast("Équipe", err.message, "danger"); }
  }

  $("members-list").addEventListener("click", (e) => {
    const b = e.target.closest("[data-resend],[data-remove],[data-cancel],[data-rescind],[data-suspend],[data-reinstate],[data-transfer]");
    if (!b || !canGovern()) return;
    const who = b.dataset.name;
    if (b.dataset.resend) {
      openDrawer({ email: b.dataset.email, role: b.dataset.role });
      return;
    }
    if (b.dataset.cancel) {
      return memberAction({
        userId: b.dataset.cancel, path: "", method: "DELETE", title: "Annuler cette invitation ?", danger: true, confirmLabel: "Retirer l’invitation", cancelLabel: "Garder l’invitation",
        description: who + " n’a pas encore accepté l’invitation. Vous pourrez toujours lui en renvoyer une plus tard.",
        done: "Invitation annulée",
      });
    }
    if (b.dataset.rescind) {
      return memberAction({
        userId: b.dataset.rescind, path: "", method: "DELETE", title: "Retirer définitivement ce compte en pause ?", danger: true, confirmLabel: "Retirer",
        description: who + " perdra sa place dans l’équipe et ne pourra plus revenir sans une nouvelle invitation.",
        done: "Compte retiré de l’équipe",
      });
    }
    if (b.dataset.remove) {
      return memberAction({
        userId: b.dataset.remove, path: "", method: "DELETE", title: "Retirer ce membre ?", danger: true, confirmLabel: "Retirer",
        description: who + " perdra l’accès aux offres et dossiers de l’organisation. Retirer est définitif : préférez « Mettre en pause » si vous voulez pouvoir revenir en arrière.",
        done: "Membre retiré de l’équipe",
      });
    }
    if (b.dataset.suspend) {
      return memberAction({
        userId: b.dataset.suspend, path: "/suspend", method: "PATCH", title: "Mettre ce membre en pause ?", confirmLabel: "Mettre en pause",
        description: who + " ne pourra plus ouvrir l’espace, mais gardera son rôle et l’historique de ses dossiers. Vous pourrez rétablir l’accès à tout moment.",
        done: "Accès mis en pause",
      });
    }
    if (b.dataset.reinstate) {
      return memberAction({
        userId: b.dataset.reinstate, path: "/reinstate", method: "PATCH", title: "Rétablir l’accès ?", confirmLabel: "Rétablir",
        description: who + " retrouvera immédiatement l’accès à l’espace, avec le même rôle qu’avant la pause.",
        done: "Accès rétabli",
      });
    }
    return memberAction({
      userId: b.dataset.transfer, path: "/ownership", method: "PATCH", title: "Transférer le rôle de créateur ?", danger: true, confirmLabel: "Transférer",
      description: who + " deviendra le seul compte à gérer l’équipe, la fiche et la formule. Vous deviendrez Recruteur : vous ne pourrez plus inviter ni retirer de membre.",
      done: "Rôle de créateur transféré",
    });
  });

  $("members-list").addEventListener("change", async (e) => {
    const sel = e.target.closest("[data-role]");
    if (!sel || !canGovern()) return;
    const previous = sel.value;
    try {
      await HireLinkAPI.api("/organizations/mine/members/" + sel.dataset.role + "/role", { method: "PATCH", body: { orgRole: sel.value } });
      RC.toast("Équipe", (sel.dataset.name || "Le membre") + " est désormais " + RC.ROLE_FR[sel.value] + ".", "ok");
      RC.cacheClear("members");
      loadMembers();
    } catch (err) {
      sel.value = previous;
      RC.toast("Équipe", err.message, "danger");
    }
  });

  const drawer = $("hq-drawer");
  let lastFocus = null;
  /** @param {{email?:string, role?:string}} [prefill] préremplit le panneau quand on renvoie une invitation sans réponse */
  function openDrawer(prefill) {
    lastFocus = document.activeElement;
    const fb = $("invite-feedback");
    if (fb) { fb.hidden = true; fb.innerHTML = ""; }
    const emailInput = $("invite-email");
    if (prefill && prefill.email && emailInput) {
      emailInput.value = prefill.email;
      const radio = document.querySelector('input[name="invite-role"][value="' + (prefill.role || "RECRUTEUR") + '"]');
      if (radio) radio.checked = true;
    } else if (emailInput) emailInput.value = "";
    if (org) paintSeats(org, members ? seatOnly(members).length : null);
    drawer.classList.add("is-open"); drawer.setAttribute("aria-hidden", "false");
    // Contrat clavier partagé : Tab reste dans le tiroir, Escape le referme, le focus revient au déclencheur.
    if (drawer._hlRelease) drawer._hlRelease();
    if (window.HireLink && HireLink.trapDialog)
      drawer._hlRelease = HireLink.trapDialog(drawer.querySelector(".hq-drawer-card"), closeDrawer);
    setTimeout(() => emailInput && emailInput.focus(), 220);
  }
  function closeDrawer() {
    drawer.classList.remove("is-open"); drawer.setAttribute("aria-hidden", "true");
    if (lastFocus && lastFocus.focus) lastFocus.focus();
    if (drawer._hlRelease) { drawer._hlRelease(); drawer._hlRelease = null; }
  }
  document.querySelectorAll(".btn-invite-trigger").forEach((btn) => btn.addEventListener("click", () => openDrawer()));
  const topInvite = $("btn-invite");
  if (topInvite) topInvite.addEventListener("click", () => openDrawer());
  $("hq-drawer-close").addEventListener("click", closeDrawer);
  $("hq-drawer-cancel").addEventListener("click", closeDrawer);
  drawer.addEventListener("click", (e) => { if (e.target === drawer) closeDrawer(); });

  async function submitInvite(e) {
    if (e && e.preventDefault) e.preventDefault();
    const fb = $("invite-feedback");
    if (fb) { fb.hidden = true; fb.innerHTML = ""; }
    const emailInput = $("invite-email");
    const email = (emailInput.value || "").trim();
    if (!email || !email.includes("@")) {
      const msg = "Saisissez une adresse e-mail professionnelle valide.";
      if (fb) {
        fb.className = "rc-banner rc-banner--warn";
        fb.innerHTML = "<div><strong>E-mail requis</strong><span>" + esc(msg) + "</span></div>";
        fb.hidden = false;
      }
      RC.toast("E-mail requis", msg, "warn");
      emailInput.focus();
      return;
    }
    const role = (document.querySelector('input[name="invite-role"]:checked') || {}).value || "RECRUTEUR";
    const btn = $("btn-submit-invite") || (e && e.target && e.target.querySelector && e.target.querySelector("button[type=submit]"));
    RC.busy(btn, true);
    try {
      const m = await HireLinkAPI.api("/organizations/mine/invite", { method: "POST", body: { email: email, orgRole: role } });
      const roleFr = m.roleLabel || RC.ROLE_FR[m.orgRole] || m.orgRole;
      if (fb) {
        fb.className = "rc-banner rc-banner--ok";
        fb.innerHTML = "<div><strong>Invitation envoyée</strong><span>" + esc(m.email) + " recevra le rôle " + esc(roleFr) +
          " dès qu’il aura accepté l’invitation, reçue par e-mail et valable trois jours. Suivez-la ici, dans « Invitations en attente ».</span></div>";
        fb.hidden = false;
      }
      RC.toast("Invitation envoyée", m.email + " · " + roleFr, "ok");
      newMemberId = m.userId;
      emailInput.value = "";
      RC.cacheClear("members");
      await loadMembers();
      const invited = (members || []).find((x) => (x.email || "").toLowerCase() === m.email.toLowerCase());
      if (invited) newMemberId = invited.userId;
      setTimeout(() => {
        closeDrawer();
        selectTab("members");
      }, 1000);
    } catch (err) {
      if (fb) {
        fb.className = "rc-banner rc-banner--danger";
        fb.innerHTML = "<div><strong>Ajout impossible</strong><span>" + esc(err.message || "Erreur lors de l'invitation") + "</span></div>";
        fb.hidden = false;
      }
      RC.toast("Ajout impossible", err.message, "danger");
    } finally {
      RC.busy(btn, false);
    }
  }

  $("hq-invite-form").addEventListener("submit", submitInvite);
  const btnSub = $("btn-submit-invite");
  if (btnSub) btnSub.addEventListener("click", submitInvite);

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

  HireLinkCredits.load(true)
    .then((c) => {
      if (!c) { $("kpi-credits").textContent = "—"; return; }
      $("kpi-credits").textContent = c.remaining + " / " + c.limit;
      $("kpi-plan").textContent =
        "Formule " + (c.planLabel || (c.plan === "PREMIUM" ? "Premium" : "Accès")) +
        " · votre siège, ce mois · recharge dans " + c.daysToReset + " j";
    })
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
  /** « 11 à 50 salariés » : la tranche effectue la même reformulation que la fiche publique. */
  function staffLabel(v) { return v ? String(v).replace("-", " à ") + " salariés" : ""; }
  /** « 🇲🇦 Tétouan » : la ville de l'offre, jamais un pays ou un mode de travail. */
  function placeOf(o) {
    const p = HireLinkPlaces.placeLabel(o.location, o.companyCity);
    return p ? (flag(p) + " " + p).trim() : "";
  }
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
    const staff = activeOnly(members);
    const n = staff.length;
    const roles = { OWNER: 0, RECRUTEUR: 0, HIRING_MANAGER: 0 };
    staff.forEach((m) => { roles[m.orgRole] = (roles[m.orgRole] || 0) + 1; });
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
      '<div class="hq-stats"><span><b>' + n + "</b> membre" + (n > 1 ? "s" : "") + "</span><span><b>" + (roles.RECRUTEUR || 0) + "</b> recruteur" + ((roles.RECRUTEUR || 0) > 1 ? "s" : "") + "</span><span><b>" + (roles.HIRING_MANAGER || 0) + "</b> accès limité" + ((roles.HIRING_MANAGER || 0) > 1 ? "s" : "") + "</span><span><b>" + offersOpen + "</b> offre" + (offersOpen > 1 ? "s" : "") + " ouverte" + (offersOpen > 1 ? "s" : "") + "</span><span><b>" + followers() + "</b> candidat" + (followers() > 1 ? "s suivent" : " suit") + "</span></div></div>";
    bindLogoFallback(box);
  }

  /** Combien de candidats suivent l'entreprise : un total, jamais leurs identités. */
  function followers() {
    const v = Number(org && org.followerCount);
    return Number.isFinite(v) && v > 0 ? v : 0;
  }
  function paintPreview() {
    const box = $("co-preview");
    if (!box || !org) return;
    const d = live();
    box.style.setProperty("--brand", d.color);
    const meta = [d.sector, d.city ? (flag(d.city) + " " + d.city).trim() : "", staffLabel(org.employees)]
      .filter(Boolean)
      .map(esc)
      .join(" · ");
    const signal = d.verified
      ? '<span class="hq-preview-signal hq-preview-signal--ok">Identité vérifiée</span>'
      : '<span class="hq-preview-signal hq-preview-signal--todo">Vérification à compléter</span>';
    const bio = d.bio
      ? esc(d.bio)
      : '<span class="rc-muted">Aucune présentation : ajoutez-en une dans Paramètres, c’est le texte lu par les candidats.</span>';
    const n = followers();
    const suivi = n
      ? "<b>" + n + "</b> candidat" + (n > 1 ? "s suivent" : " suit") + " votre entreprise"
      : "Aucun candidat ne suit encore votre entreprise";
    box.innerHTML =
      '<article class="hq-preview">' +
      '<div class="hq-preview-cover" aria-hidden="true"></div>' +
      '<div class="hq-preview-id">' + logoMark(d, "hq-preview-logo") +
      '<div class="hq-preview-name"><h3>' + esc(d.name) + "</h3>" +
      (meta ? "<p>" + meta + "</p>" : "") +
      '<div class="hq-preview-signals">' + signal + "</div></div>" +
      '<a class="btn btn-soft btn-sm hq-preview-cta" href="../company.html?name=' + encodeURIComponent(org.name || "") + '">Voir la page publique</a></div>' +
      '<p class="hq-preview-bio">' + bio + "</p>" +
      '<p class="hq-preview-follow">' + suivi +
      "<span>Chiffre anonyme, visible uniquement ici : la fiche publique ne l’affiche pas.</span></p>" +
      "</article>";
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
  document.addEventListener("hirelink:focus-request", (e) => {
    const k = String((e.detail && (e.detail.key || e.detail)) || "");
    if (/^(member|team)/.test(k)) selectTab("members");
  });
  (function initialTab() {
    const q = new URLSearchParams(location.search);
    const f = q.get("focus") || "";
    selectTab(/^(member|team)/.test(f) ? "members" : q.get("tab") || (location.hash === "#new-member" ? "members" : "overview"));
    if (location.hash === "#new-member" && canGovern()) setTimeout(openDrawer, 400);
  })();

  /* ------------------------------------------------------------------ offres */
  let offersOpen = 0;
  RC.data.offers((list) => {
    list = list || [];
    offersOpen = list.filter(RC.isLiveOffer).length;
    $("offers-count").textContent = list.length;
    const box = $("hq-offers");
    if (!list.length) { RC.empty(box, { title: "Aucune offre", text: "Publiez une première offre pour recevoir des candidatures.", action: { href: "recruiter-offers.html#new", label: "Nouvelle offre" } }); paintHq(); paintPreview(); return; }
    function renderOffers() {
      box.innerHTML = '<div class="rc-list">' + list.map((o, i) =>
        '<a class="rc-item hq-offer" style="--i:' + i + '" href="recruiter-pipeline.html?offer=' + o.id + '"><div class="rc-item-main"><strong>' + esc(o.title) + "</strong><span>" +
        [placeOf(o), o.contractType, o.deadline ? "Clôture " + RC.fmtDate(o.deadline) : ""].filter(Boolean).map(esc).join(" · ") +
        '</span></div><span class="rc-pill' + (RC.isLiveOffer(o) ? " is-ok" : "") + '">' +
        (o.suspended ? "Suspendue" : o.archivedAt ? "Archivée" : o.closedAt ? "Clôturée" : o.published ? "Publiée" : "Brouillon") + "</span></a>").join("") + "</div>";
    }
    renderOffers();
    // le catalogue des lieux arrive en parallèle : sans cette seconde passe, les drapeaux manqueraient
    HireLinkPlaces.ready().then(() => { renderOffers(); paintHq(); paintPreview(); });
    paintHq();
    // le compteur d'offres de l'aperçu attend cette réponse : sans second rappel, il resterait à 0
    paintPreview();
  }, (err) => {
    // swr() se tait sans second callback : le panneau « Offres » afficherait 0 au lieu d'un échec.
    const count = $("offers-count");
    if (count) count.textContent = "—";
    RC.errorState($("hq-offers"), err.message, () => {
      RC.data.invalidate();
      window.location.reload();
    });
  });

  loadOrg();
  loadMembers();
})();
