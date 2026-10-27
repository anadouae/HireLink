(function () {
  const session = HireLinkShell.mountShell({ roles: ["RECRUTEUR"], hello: false });
  if (!session) return;
  if (window.HireLinkProduct) HireLinkProduct.mountPremiumBar("recruiter");

  function setText(id, v) {
    const el = document.getElementById(id);
    if (el) el.textContent = v;
  }

  function paintLogo(url, name) {
    const box = document.getElementById("co-logo-box");
    if (!box) return;
    if (url) {
      box.innerHTML = '<img alt="" src="' + String(url).replace(/"/g, "") + '" onerror="this.remove()" />';
    } else {
      box.textContent = String(name || "E").charAt(0).toUpperCase();
    }
  }

  function fillForm(o) {
    const name = document.getElementById("co-name");
    const ice = document.getElementById("co-ice");
    const bio = document.getElementById("co-bio");
    const web = document.getElementById("co-website");
    const sector = document.getElementById("co-sector");
    const city = document.getElementById("co-city");
    if (name && o.name) name.value = o.name;
    if (ice && o.ice) ice.value = o.ice;
    if (bio && o.presentation) bio.value = o.presentation;
    if (web) web.value = o.website || "";
    if (sector) sector.value = o.sector || "";
    if (city) city.value = o.city || "";
  }

  function loadOrg() {
    HireLinkAPI.api("/organizations/mine")
      .then((o) => {
        setText("org-name", o.name || "Organisation");
        setText(
          "org-sub",
          "Mon rôle : " + (o.myRole || "—") + " · palier " + (o.planTier || "ACCES")
        );
        setText("kpi-status", o.statusLabel || (o.verified ? "Vérifiée" : "À vérifier"));
        setText("kpi-ice", o.ice ? "ICE " + o.ice : "ICE non renseigné");
        setText("kpi-plan", "Plan " + (o.planTier || "ACCES"));
        const hint = document.getElementById("co-verify-hint");
        if (hint) {
          hint.textContent = o.verified
            ? "Badge actif. Vous pouvez publier des offres."
            : "Le badge se déclenche tout seul si l’ICE et l’e-mail professionnel correspondent au registre. Sinon l’admin valide dans Administration → Entreprises.";
        }
        paintLogo(o.logoUrl, o.name);
        fillForm(o);
      })
      .catch(() => {
        HireLinkAPI.api("/me").then((p) => {
          setText("org-name", p.company || "Organisation");
          setText("org-sub", p.companyVerified ? "Entreprise vérifiée" : "Vérification en cours");
          setText("kpi-status", p.companyVerified ? "Vérifiée" : "À vérifier");
          setText("kpi-ice", p.companyIce ? "ICE " + p.companyIce : "ICE —");
        }).catch(() => {});
      });
  }

  function loadMembers() {
    const box = document.getElementById("members-list");
    HireLinkAPI.api("/organizations/mine/members")
      .then((list) => {
        setText("members-count", String(list.length));
        if (!list.length) {
          box.innerHTML = "<p class='muted'>Aucun membre pour l’instant.</p>";
          return;
        }
        box.innerHTML = list
          .map(
            (m) =>
              '<div class="member-row"><div class="member-meta"><strong>' +
              (m.fullName || m.email) +
              "</strong><span class='muted'>" +
              m.email +
              "</span></div><span class='badge badge-chrome'>" +
              m.orgRole +
              "</span></div>"
          )
          .join("");
      })
      .catch((err) => {
        box.innerHTML = "<p class='muted'>" + err.message + "</p>";
      });
  }

  loadOrg();
  loadMembers();

  HireLinkAPI.api("/ai/credits", {
    headers: HireLinkProduct ? HireLinkProduct.premiumHeaders("recruiter") : {},
  })
    .then((c) => {
      setText("kpi-credits", c.remaining + " / " + c.limit);
      setText("kpi-plan", (c.plan || "ACCES") + " · ce mois");
    })
    .catch(() => {});

  const companyForm = document.getElementById("company-form");
  if (companyForm) {
    companyForm.addEventListener("submit", async (e) => {
      e.preventDefault();
      try {
        await HireLinkAPI.api("/organizations/mine", {
          method: "PUT",
          body: {
            name: document.getElementById("co-name").value,
            ice: document.getElementById("co-ice").value,
            website: (document.getElementById("co-website") || {}).value,
            sector: (document.getElementById("co-sector") || {}).value,
            city: (document.getElementById("co-city") || {}).value,
            presentation: document.getElementById("co-bio").value,
          },
        });
        HireLink.toast("Entreprise", "Fiche enregistrée. La vérification a été relancée.", "ok");
        loadOrg();
      } catch (err) {
        HireLink.toast("Entreprise", err.message, "danger");
      }
    });
  }

  const recheck = document.getElementById("btn-recheck");
  if (recheck) {
    recheck.onclick = async () => {
      try {
        const o = await HireLinkAPI.api("/organizations/mine/verify", { method: "POST" });
        HireLink.toast("Vérification", o.verified ? "Badge attribué automatiquement." : (o.message || "Dossier envoyé à l’admin."), o.verified ? "ok" : "warn");
        loadOrg();
      } catch (err) {
        HireLink.toast("Vérification", err.message, "danger");
      }
    };
  }

  const toggle = document.getElementById("btn-toggle-badge");
  if (toggle) {
    toggle.onclick = () => {
      document.getElementById("badge-box").classList.toggle("is-open");
    };
  }

  document.getElementById("btn-invite").onclick = async () => {
    const ok = await HireLink.openModal({
      title: "Inviter un membre",
      description: "Owner uniquement — Recruteur ou Hiring Manager.",
      bodyHtml:
        '<div class="field"><label>Email</label><input id="invite-email" type="email" placeholder="rh@entreprise.ma" /></div>' +
        '<div class="field"><label>Rôle</label><select id="invite-role">' +
        '<option value="RECRUTEUR">Recruteur</option>' +
        '<option value="HIRING_MANAGER">Hiring Manager</option></select></div>',
      confirmLabel: "Inviter",
    });
    if (!ok) return;
    const email = (document.getElementById("invite-email") || {}).value || "";
    const orgRole = (document.getElementById("invite-role") || {}).value || "RECRUTEUR";
    if (!email.trim()) {
      HireLink.toast("Email requis", "", "warn");
      return;
    }
    try {
      const m = await HireLinkAPI.api("/organizations/mine/invite", {
        method: "POST",
        body: { email: email.trim(), orgRole },
      });
      HireLink.toast("Invitation", m.email + " · " + m.orgRole, "ok");
      loadMembers();
    } catch (err) {
      HireLink.toast("Invitation", err.message, "danger");
    }
  };


  const copyBadge = document.getElementById("btn-copy-badge");
  if (copyBadge) {
    copyBadge.onclick = () => {
      const t = document.getElementById("badge-html");
      if (!t || !t.textContent) return;
      navigator.clipboard.writeText(t.textContent).then(
        () => HireLink.toast("Badge", "Snippet copié", "ok"),
        () => HireLink.toast("Badge", "Copie manuelle", "warn")
      );
    };
  }
})();
