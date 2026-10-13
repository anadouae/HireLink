(function () {
  const session = HireLinkShell.mountShell({ roles: ["CANDIDAT"], hello: false });
  if (!session) return;
  if (window.HireLinkProduct) HireLinkProduct.mountPremiumBar("candidate");

  const COLUMNS = [
    { key: "SUBMITTED", label: "Soumises" },
    { key: "SCREENING", label: "Présélection" },
    { key: "SHORTLISTED", label: "Shortlist" },
    { key: "INTERVIEW", label: "Entretien (fin)" },
    { key: "CLOSED", label: "Clôturées" },
  ];

  let apps = [];

  function slaClass(days) {
    if (days == null) return "";
    if (days < 0) return "danger";
    if (days <= 2) return "warn";
    return "";
  }

  function render() {
    const q = (document.getElementById("filter-apps").value || "").trim().toLowerCase();
    const filtered = q
      ? apps.filter((a) =>
          ((a.jobTitle || "") + " " + (a.status || "") + " " + (a.rejectionReason || ""))
            .toLowerCase()
            .includes(q)
        )
      : apps;
    const root = document.getElementById("kanban");
    root.innerHTML = "";
    COLUMNS.forEach((col) => {
      const cards =
        col.key === "CLOSED"
          ? filtered.filter((a) => ["REJECTED", "WITHDRAWN", "ACCEPTED"].includes(a.status))
          : filtered.filter((a) => a.status === col.key);
      const el = document.createElement("div");
      el.className = "kanban-col";
      el.innerHTML =
        '<div class="kanban-col-head"><span></span><span class="badge badge-chrome"></span></div>' +
        '<div class="kanban-col-body"></div>';
      el.querySelector(".kanban-col-head span").textContent = col.label;
      el.querySelector(".badge").textContent = String(cards.length);
      const body = el.querySelector(".kanban-col-body");
      if (!cards.length) {
        body.innerHTML = '<p class="muted" style="font-size:0.8rem;padding:0.35rem">Vide</p>';
      }
      cards.forEach((c) => {
        const card = document.createElement("article");
        card.className = "kanban-card";
        const sla = c.slaRemainingDays;
        const slaTxt = sla == null ? "—" : sla < 0 ? "En retard" : sla + " j";
        card.innerHTML =
          "<h3></h3><p></p>" +
          '<div class="match-bar"><span></span></div>' +
          "<div class='kanban-meta'><span class='badge badge-rose'></span>" +
          "<span class='sla-pill'></span></div>" +
          "<div class='row-actions'></div>";
        card.querySelector("h3").textContent = c.jobTitle || "Offre";
        let statusLine = c.status + (c.rejectionReason ? " · " + c.rejectionReason : "");
        if (c.interviewType) {
          const when = c.interviewAt ? new Date(c.interviewAt).toLocaleString("fr-FR") : "";
          statusLine =
            "Entretien " +
            c.interviewType +
            (when ? " · " + when : "") +
            (c.interviewLocation ? " · " + c.interviewLocation : "");
        }
        card.querySelector("p").textContent = statusLine;
        card.querySelector(".match-bar > span").style.width = Math.min(100, c.matchScore || 0) + "%";
        card.querySelector(".badge").textContent =
          c.matchScore != null ? Math.round(c.matchScore) + "% match" : "—";
        const pill = card.querySelector(".sla-pill");
        pill.textContent = "SLA " + slaTxt;
        const sc = slaClass(sla);
        if (sc) pill.classList.add(sc);
        const actions = card.querySelector(".row-actions");
        const view = document.createElement("button");
        view.type = "button";
        view.className = "btn btn-sm btn-soft";
        view.textContent = "Détails";
        view.onclick = () => openMyDossier(c);
        actions.appendChild(view);
        if (c.status === "INTERVIEW") {
          const prep = document.createElement("button");
          prep.type = "button";
          prep.className = "btn btn-sm btn-primary";
          prep.textContent = "Prep IA";
          prep.onclick = () => interviewPrep(c);
          actions.appendChild(prep);
        }
        if (!["REJECTED", "WITHDRAWN", "ACCEPTED"].includes(c.status)) {
          const wd = document.createElement("button");
          wd.type = "button";
          wd.className = "btn btn-sm btn-ghost";
          wd.textContent = "Retirer";
          wd.onclick = () => withdraw(c);
          actions.appendChild(wd);
        }
        body.appendChild(card);
      });
      root.appendChild(el);
    });
  }

  async function openMyDossier(c) {
    const when = c.interviewAt ? new Date(c.interviewAt).toLocaleString("fr-FR") : "—";
    const html =
      '<div class="hl-review-list">' +
      "<div><strong>Statut</strong><span>" +
      (c.status || "—") +
      "</span></div>" +
      "<div><strong>Match</strong><span>" +
      (c.matchScore != null ? Math.round(c.matchScore) + "%" : "—") +
      "</span></div>" +
      "<div><strong>CV</strong><span>" +
      (c.cvFileName || c.cvSource || "—") +
      "</span></div>" +
      "<div><strong>Lettre</strong><span>" +
      (c.coverLetter ? "Envoyée" : "Non") +
      "</span></div>" +
      "<div><strong>SLA</strong><span>" +
      (c.slaRemainingDays == null ? "—" : c.slaRemainingDays + " j restants") +
      "</span></div>" +
      (c.interviewType
        ? "<div><strong>Entretien</strong><span>" +
          c.interviewType +
          " · " +
          when +
          "</span></div>" +
          "<div><strong>Lieu / lien</strong><span>" +
          (c.interviewLocation || "—") +
          "</span></div>"
        : "") +
      (c.rejectionReason
        ? "<div><strong>Motif</strong><span>" + c.rejectionReason + "</span></div>"
        : "") +
      "</div>";
    await HireLink.openModal({
      title: c.jobTitle || "Ma candidature",
      description: "Suivi bilatéral — même vérité que le recruteur.",
      bodyHtml:
        html +
        '<button type="button" class="btn btn-soft btn-sm" id="btn-my-tl" style="margin-top:0.75rem">Voir l’historique (F13)</button>',
      confirmLabel: "Fermer",
      hideCancel: true,
      large: true,
    });
    const btn = document.getElementById("btn-my-tl");
    if (btn) {
      btn.onclick = async () => {
        try {
          const rows = await HireLinkAPI.api("/applications/" + c.id + "/timeline");
          const tl =
            !rows.length
              ? "<p class='muted'>Aucun événement.</p>"
              : "<ol>" +
                rows
                  .map(
                    (h) =>
                      "<li><strong>" +
                      (h.from || "—") +
                      " → " +
                      h.to +
                      "</strong> · " +
                      h.at +
                      "</li>"
                  )
                  .join("") +
                "</ol>";
          await HireLink.openModal({
            title: "Historique",
            bodyHtml: tl,
            confirmLabel: "Fermer",
            hideCancel: true,
          });
        } catch (err) {
          HireLink.toast("Timeline", err.message, "danger");
        }
      };
    }
  }

  async function interviewPrep(c) {
    try {
      const premium = HireLinkProduct && HireLinkProduct.isPremium("candidate");
      const data = await HireLinkAPI.api("/ai/interview-prep", {
        method: "POST",
        headers: HireLinkProduct ? HireLinkProduct.premiumHeaders("candidate") : {},
        body: { jobTitle: c.jobTitle, premium },
      });
      const escaped = (data.content || "")
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;");
      const ok = await HireLink.openModal({
        title: data.title || "Prep entretien",
        description: data.premiumLocked
          ? "Aperçu freemium — Premium pour le script complet."
          : "Script prêt.",
        bodyHtml:
          '<div class="ai-result">' +
          escaped +
          "</div>" +
          (data.premiumLocked
            ? '<p class="muted" style="margin-top:0.75rem">Passez Premium pour débloquer le script réponses.</p>'
            : ""),
        confirmLabel: data.premiumLocked ? "Voir Premium" : "Fermer",
        hideCancel: !data.premiumLocked,
        large: true,
      });
      if (ok && data.premiumLocked && window.HireLinkGrowth) HireLinkGrowth.showPremium("candidate");
    } catch (err) {
      HireLink.toast("IA", err.message, "danger");
    }
  }

  async function withdraw(c) {
    const ok = await HireLink.openModal({
      title: "Retirer cette candidature ?",
      description: c.jobTitle,
      confirmLabel: "Retirer",
      danger: true,
    });
    if (!ok) return;
    try {
      await HireLinkAPI.api("/applications/" + c.id + "/status", {
        method: "PATCH",
        body: { status: "WITHDRAWN" },
      });
      HireLink.toast("Retirée", "", "ok");
      load();
    } catch (err) {
      HireLink.toast("Erreur", err.message, "danger");
    }
  }

  async function load() {
    try {
      apps = await HireLinkAPI.api("/applications/mine");
      render();
    } catch (err) {
      HireLink.toast("Candidatures", err.message, "danger");
    }
  }

  HireLinkShell.bindSearch(document.getElementById("filter-apps"), () => render());
  const prem = document.getElementById("btn-premium");
  if (prem) prem.onclick = () => HireLinkGrowth && HireLinkGrowth.showPremium("candidate");
  load();
})();
