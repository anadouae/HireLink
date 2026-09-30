(function () {
  const session = HireLinkShell.mountShell({ roles: ["RECRUTEUR"], hello: false });
  if (!session) return;
  if (window.HireLinkProduct) HireLinkProduct.mountPremiumBar("recruiter");

  function setText(id, v) {
    const el = document.getElementById(id);
    if (el) el.textContent = v;
  }

  function loadOrg() {
    HireLinkAPI.api("/organizations/mine")
      .then((o) => {
        setText("org-name", o.name || "Organisation");
        setText(
          "org-sub",
          "Mon rôle : " + (o.myRole || "—") + " · palier " + (o.planTier || "ACCES")
        );
        setText("kpi-status", o.verified ? "Vérifiée" : "À vérifier");
        setText("kpi-ice", o.ice ? "ICE " + o.ice : "ICE non renseigné");
        setText("kpi-plan", "Plan " + (o.planTier || "ACCES"));
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

  HireLinkAPI.api("/growth/reliability-badge")
    .then((b) => {
      if (b.score) setText("kpi-trust", b.score.score + "/100");
      const pre = document.getElementById("badge-html");
      if (pre) pre.textContent = b.htmlSnippet || "";
    })
    .catch(() => {});

  document.getElementById("btn-upgrade").onclick = () => {
    if (window.HireLinkGrowth) HireLinkGrowth.showPremium("recruiter");
  };

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

  const kitBtn = document.getElementById("btn-interview-kit");
  if (kitBtn) {
    kitBtn.onclick = async () => {
      try {
        const premium = HireLinkProduct && HireLinkProduct.isPremium("recruiter");
        const data = await HireLinkAPI.api("/ai/interview-kit", {
          method: "POST",
          headers: HireLinkProduct ? HireLinkProduct.premiumHeaders("recruiter") : {},
          body: { jobTitle: "Poste ouvert", skills: "Java, communication", premium },
        });
        await HireLink.openModal({
          title: data.title || "Grille entretien",
          description: data.note || "",
          bodyHtml:
            (data.content
              ? "<pre class='ai-result' style='white-space:pre-wrap'>" + data.content + "</pre>"
              : "") +
            "<ol>" +
            (data.questions || []).map((q) => "<li>" + q + "</li>").join("") +
            "</ol>",
          confirmLabel: "Fermer",
          hideCancel: true,
          large: true,
        });
      } catch (err) {
        HireLink.toast("IA", err.message, "danger");
      }
    };
  }

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
