(function () {
  const session = HireLinkShell.mountShell({ roles: ["CANDIDAT"], hello: false });
  if (!session) return;
  if (window.HireLinkProduct) HireLinkProduct.mountPremiumBar("candidate");

  const STATUS_LABEL = {
    SUBMITTED: "Soumise",
    SCREENING: "Présélection",
    SHORTLISTED: "Shortlist",
    INTERVIEW: "Entretien",
    REJECTED: "Refusée",
    WITHDRAWN: "Retirée",
  };

  /** Fin positive produit = INTERVIEW (pas d'étape Offre / Accepté). */
  const PIPELINE = ["SUBMITTED", "SCREENING", "SHORTLISTED", "INTERVIEW"];

  let apps = [];
  let profile = null;
  let selectedId = null;

  const viewList = document.getElementById("view-list");
  const viewDetail = document.getElementById("view-detail");
  const listRoot = document.getElementById("apps-list");

  function statusBadgeClass(st) {
    if (st === "REJECTED" || st === "WITHDRAWN") return "badge-danger";
    if (st === "INTERVIEW") return "badge-ok";
    if (st === "SHORTLISTED") return "badge-rose";
    if (st === "SCREENING") return "badge-info";
    return "badge-chrome";
  }

  function fmtWhen(iso) {
    if (!iso) return "—";
    try {
      return new Date(iso).toLocaleString("fr-FR", {
        day: "2-digit",
        month: "short",
        hour: "2-digit",
        minute: "2-digit",
      });
    } catch {
      return String(iso);
    }
  }

  function el(id) {
    return document.getElementById(id);
  }

  function setHtml(node, html) {
    if (node) node.innerHTML = html;
  }

  function setText(node, text) {
    if (node) node.textContent = text;
  }

  function showList() {
    selectedId = null;
    if (viewDetail) viewDetail.hidden = true;
    if (viewList) viewList.hidden = false;
    history.replaceState(null, "", location.pathname);
  }

  function showDetail(app) {
    selectedId = app.id;
    if (viewList) viewList.hidden = true;
    if (viewDetail) viewDetail.hidden = false;
    paintDetail(app);
    history.replaceState(null, "", "?id=" + app.id);
  }

  function renderList() {
    if (!listRoot) return;
    const filter = el("filter-apps");
    const q = ((filter && filter.value) || "").trim().toLowerCase();
    const filtered = q
      ? apps.filter((a) =>
          ((a.jobTitle || "") + " " + (a.status || "") + " " + (a.rejectionReason || ""))
            .toLowerCase()
            .includes(q)
        )
      : apps;

    if (!filtered.length) {
      setHtml(
        listRoot,
        q
          ? '<p class="muted">Aucun résultat pour « ' + q + " ».</p>"
          : '<p class="muted">Aucune candidature — <a href="candidate-offers.html">voir les offres</a>.</p>'
      );
      return;
    }

    listRoot.innerHTML = "";
    filtered.forEach((a) => {
      const row = document.createElement("button");
      row.type = "button";
      row.className = "cand-app-row";
      const sla = a.slaRemainingDays;
      const slaTxt = sla == null ? "—" : sla < 0 ? "SLA dépassé" : "SLA " + sla + " j";
      row.innerHTML =
        '<div class="cand-app-row-main">' +
        "<strong></strong>" +
        '<span class="muted row-sub"></span>' +
        '<span class="row-timeline-hint muted">Historique →</span>' +
        "</div>" +
        '<span class="badge status-badge"></span>' +
        '<span class="sla-pill"></span>' +
        '<span class="muted row-open" style="font-size:0.85rem">Détail</span>';
      setText(row.querySelector("strong"), a.jobTitle || "Offre");
      setText(
        row.querySelector(".row-sub"),
        (a.matchScore != null ? Math.round(a.matchScore) + "% match · " : "") +
          "déposée " +
          fmtWhen(a.submittedAt || a.appliedAt)
      );
      const badge = row.querySelector(".badge");
      if (badge) {
        badge.className = "badge status-badge " + statusBadgeClass(a.status);
        badge.innerHTML =
          '<span class="pulse" aria-hidden="true"></span> ' +
          (STATUS_LABEL[a.status] || a.status || "—");
      }
      const pill = row.querySelector(".sla-pill");
      if (pill) {
        pill.textContent = slaTxt;
        if (sla != null && sla < 0) pill.classList.add("danger");
        else if (sla != null && sla <= 2) pill.classList.add("warn");
      }
      row.addEventListener("click", () => showDetail(a));
      listRoot.appendChild(row);
    });
  }

  function stepState(status, step) {
    const closed = ["REJECTED", "WITHDRAWN"].includes(status);
    if (closed) {
      if (step === "SUBMITTED") return "done";
      return "todo";
    }
    const idx = PIPELINE.indexOf(status);
    const sIdx = PIPELINE.indexOf(step);
    if (sIdx < 0 || idx < 0) return "todo";
    if (status === "INTERVIEW" && step === "INTERVIEW") return "done";
    if (sIdx < idx) return "done";
    if (sIdx === idx) return "now";
    return "todo";
  }

  async function paintDetail(app) {
    setText(el("d-title"), app.jobTitle || "Candidature");
    setText(
      el("d-sub"),
      (app.cvSource === "UPLOAD" ? "CV joint" : "Profil HireLink") +
        (app.submittedAt ? " · déposée " + fmtWhen(app.submittedAt) : "")
    );

    const st = el("d-status");
    if (st) {
      st.className = "badge " + statusBadgeClass(app.status);
      st.innerHTML = '<span class="pulse"></span> ' + (STATUS_LABEL[app.status] || app.status);
    }

    const pct = app.matchScore != null ? Math.round(app.matchScore) : null;
    setText(el("d-match-pct"), pct != null ? pct + "%" : "—");
    const bar = el("d-match-bar");
    if (bar) bar.style.width = (pct != null ? pct : 0) + "%";

    const skillsEl = el("d-skills");
    const skillsRaw = (profile && profile.skills) || "";
    const tags = skillsRaw
      .split(/[,;|]/)
      .map((s) => s.trim())
      .filter(Boolean)
      .slice(0, 12);
    setHtml(
      skillsEl,
      tags.length
        ? tags.map((t) => '<span class="tag">' + t + "</span>").join("")
        : '<span class="muted" style="font-size:0.85rem">Complète ton profil pour afficher tes compétences.</span>'
    );

    let rows = [];
    try {
      rows = await HireLinkAPI.api("/applications/" + app.id + "/timeline");
    } catch (_) {
      rows = [];
    }

    const tl = el("d-timeline");
    if (tl) tl.innerHTML = "";

    if (tl && rows && rows.length) {
      rows.forEach((h, i) => {
        const last = i === rows.length - 1;
        const div = document.createElement("div");
        div.className = "hl-tstep " + (last ? "now" : "done");
        div.innerHTML =
          '<div class="hl-ttime"></div><strong></strong><div class="muted" style="font-size:0.82rem"></div>';
        setText(div.querySelector(".hl-ttime"), fmtWhen(h.at || h.occurredAt || h.createdAt));
        setText(
          div.querySelector("strong"),
          (h.to && (STATUS_LABEL[h.to] || h.to)) ||
            (h.from
              ? (STATUS_LABEL[h.from] || h.from) + " → " + (STATUS_LABEL[h.to] || h.to)
              : "Mise à jour")
        );
        setText(
          div.querySelector(".muted"),
          (h.from ? (STATUS_LABEL[h.from] || h.from) + " → " : "") +
            (STATUS_LABEL[h.to] || h.to || "")
        );
        tl.appendChild(div);
      });
    } else if (tl) {
      const steps = [
        { key: "SUBMITTED", label: "Soumise", note: app.cvFileName || app.cvSource || "Dossier reçu" },
        { key: "SCREENING", label: "Présélection", note: pct != null ? "Match " + pct + "%" : "En revue" },
        { key: "SHORTLISTED", label: "Shortlist", note: "Sélection RH" },
        {
          key: "INTERVIEW",
          label: "Entretien",
          note: app.interviewType
            ? app.interviewType + (app.interviewAt ? " · " + fmtWhen(app.interviewAt) : "")
            : "Planification",
        },
      ];
      steps.forEach((s) => {
        const stt = stepState(app.status, s.key);
        const div = document.createElement("div");
        div.className = "hl-tstep " + stt;
        div.innerHTML =
          '<div class="hl-ttime"></div><strong></strong><div class="muted" style="font-size:0.82rem"></div>';
        setText(
          div.querySelector(".hl-ttime"),
          stt === "now" ? "En cours" : stt === "done" ? "Validé" : ""
        );
        setText(div.querySelector("strong"), s.label);
        setText(div.querySelector(".muted"), s.note || "");
        tl.appendChild(div);
      });
      if (app.status === "REJECTED") {
        const div = document.createElement("div");
        div.className = "hl-tstep now";
        div.innerHTML =
          '<div class="hl-ttime">Refus</div><strong>Refusée</strong><div class="muted" style="font-size:0.82rem"></div>';
        setText(div.querySelector(".muted"), app.rejectionReason || "Motif communiqué");
        tl.appendChild(div);
      } else if (app.status === "WITHDRAWN") {
        const div = document.createElement("div");
        div.className = "hl-tstep now";
        div.innerHTML =
          '<div class="hl-ttime">Clôture</div><strong>Retirée</strong><div class="muted" style="font-size:0.82rem">Vous avez retiré cette candidature</div>';
        tl.appendChild(div);
      }
    }

    const actions = el("d-actions");
    if (actions) {
      actions.innerHTML = "";
      if (!["REJECTED", "WITHDRAWN"].includes(app.status)) {
        const wd = document.createElement("button");
        wd.type = "button";
        wd.className = "btn btn-ghost btn-sm";
        wd.textContent = "Retirer ma candidature";
        wd.onclick = () => withdraw(app);
        actions.appendChild(wd);
      }
    }

    const aiBtn = el("d-ai-prep");
    if (aiBtn) {
      aiBtn.disabled = false;
      aiBtn.onclick = () => interviewPrep(app);
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
      await load();
      showList();
    } catch (err) {
      HireLink.toast("Erreur", err.message, "danger");
    }
  }

  async function load() {
    try {
      const [mine, me] = await Promise.all([
        HireLinkAPI.api("/applications/mine"),
        HireLinkAPI.api("/me").catch(() => null),
      ]);
      apps = Array.isArray(mine) ? mine : [];
      profile = me;
      renderList();
      const params = new URLSearchParams(location.search);
      const id = params.get("id");
      if (id) {
        const found = apps.find((a) => String(a.id) === String(id));
        if (found) showDetail(found);
        else showList();
      }
    } catch (err) {
      HireLink.toast("Candidatures", err.message, "danger");
      setHtml(listRoot, '<p class="muted">Impossible de charger les candidatures.</p>');
    }
  }

  const back = el("btn-back-list");
  if (back) back.addEventListener("click", showList);
  HireLinkShell.bindSearch(el("filter-apps"), () => {
    if (!viewList || !viewList.hidden) renderList();
  });
  const prem = el("btn-premium");
  if (prem) prem.onclick = () => HireLinkGrowth && HireLinkGrowth.showPremium("candidate");

  if (!listRoot) {
    HireLink.toast(
      "Candidatures",
      "Page obsolète en cache — forcez un rechargement (Ctrl+F5).",
      "danger"
    );
    return;
  }
  load();
})();
