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
  let statusFilter = "all";

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
    selectedId = apps[0] ? apps[0].id : null;
    if (viewDetail) viewDetail.hidden = !selectedId;
    history.replaceState(null, "", location.pathname);
    if (selectedId) paintDetail(apps[0]);
  }

  function showDetail(app) {
    selectedId = app.id;
    if (viewDetail) viewDetail.hidden = false;
    paintDetail(app);
    history.replaceState(null, "", "?id=" + app.id);
    document.querySelectorAll(".cand-app-row").forEach(function (row) {
      row.classList.toggle("is-selected", row.dataset.id === String(app.id));
    });
  }

  function renderList() {
    if (!listRoot) return;
    const filter = el("filter-apps");
    const q = ((filter && filter.value) || "").trim().toLowerCase();
    const filtered = apps.filter((a) => {
      if (statusFilter === "active" && ["REJECTED", "WITHDRAWN"].includes(a.status)) return false;
      if (statusFilter !== "all" && statusFilter !== "active" && a.status !== statusFilter) return false;
      if (!q) return true;
      return ((a.jobTitle || "") + " " + (a.status || "") + " " + (a.rejectionReason || ""))
        .toLowerCase()
        .includes(q);
    });

    const statusBar = el("app-status-filter");
    if (statusBar) {
      const counts = {
        all: apps.length,
        active: apps.filter((a) => !["REJECTED", "WITHDRAWN"].includes(a.status)).length,
        INTERVIEW: apps.filter((a) => a.status === "INTERVIEW").length,
        REJECTED: apps.filter((a) => a.status === "REJECTED").length,
        WITHDRAWN: apps.filter((a) => a.status === "WITHDRAWN").length,
      };
      statusBar.querySelectorAll("button").forEach((b) => {
        const key = b.getAttribute("data-st") || "all";
        let badge = b.querySelector(".filter-count");
        if (!badge) {
          badge = document.createElement("span");
          badge.className = "filter-count";
          b.appendChild(badge);
        }
        badge.textContent = String(counts[key] || 0);
      });
    }
    const summary = el("apps-summary");
    if (summary) {
      summary.textContent = filtered.length + " dossier" + (filtered.length > 1 ? "s" : "") + " affiché" + (filtered.length > 1 ? "s" : "");
    }

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
      row.className = "cand-app-row" + (selectedId === a.id ? " is-selected" : "");
      row.dataset.id = String(a.id);
      const sla = a.slaRemainingDays;
      const slaTxt =
        ["REJECTED", "WITHDRAWN"].includes(a.status)
          ? ""
          : sla == null
            ? ""
            : sla < 0
              ? "Délai dépassé"
              : "Réponse " + sla + " j";
      const reasonBit =
        a.status === "REJECTED" && a.rejectionReason ? " · " + a.rejectionReason : "";
      row.innerHTML =
        '<div class="cand-app-row-main">' +
        "<strong></strong>" +
        '<span class="muted row-sub"></span>' +
        "</div>" +
        '<span class="badge status-badge"></span>' +
        '<span class="sla-pill"></span>';
      setText(row.querySelector("strong"), a.jobTitle || "Offre");
      setText(
        row.querySelector(".row-sub"),
        (a.matchScore != null ? Math.round(a.matchScore) + "% · " : "") +
          "déposée " +
          fmtWhen(a.submittedAt || a.appliedAt) +
          reasonBit
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
        pill.hidden = !slaTxt;
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
        wd.className = "btn btn-danger btn-sm";
        wd.textContent = "Retirer ma candidature";
        wd.onclick = (e) => {
          e.preventDefault();
          e.stopPropagation();
          withdraw(app);
        };
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
      const me = await HireLinkAPI.api("/me");
      const cvText = [me && me.cvSections, me && me.skills, c.jobTitle ? "Poste visé : " + c.jobTitle : ""]
        .filter(function (part) { return part && String(part).trim(); })
        .join("\n");
      if (!cvText.trim()) {
        HireLink.toast("Synthèse", "Déposez et confirmez le CV dans le profil.", "info");
        return;
      }
      const data = await HireLinkAPI.api("/ai/cv-summary", {
        method: "POST",
        headers: HireLinkProduct ? HireLinkProduct.premiumHeaders("candidate") : {},
        body: {
          cvText: cvText,
          premium: !!(HireLinkProduct && HireLinkProduct.isPremium("candidate")),
        },
      });
      const escaped = (data.content || "Pas de synthèse.")
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/\n/g, "<br>");
      await HireLink.openModal({
        title: "Synthèse du CV",
        description: c.jobTitle || "Lecture du document déposé. Elle ne décide pas.",
        bodyHtml: '<div class="ai-result">' + escaped + "</div>",
        confirmLabel: "Fermer",
        hideCancel: true,
      });
    } catch (err) {
      HireLink.toast("Synthèse", err.message, "danger");
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
      history.replaceState(null, "", location.pathname);
      statusFilter = "WITHDRAWN";
      HireLink.toast(
        "Statut : Retirée",
        "Le dossier reste dans Mes candidatures, filtre Retirées. Vous pouvez postuler à nouveau sur cette offre.",
        "ok"
      );
      await load();
      showList();
    } catch (err) {
      HireLink.toast("Retrait impossible", err.message || "Réessayez", "danger");
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
      const statusBar = el("app-status-filter");
      if (statusBar) {
        statusBar.querySelectorAll("button").forEach((b) => {
          b.classList.toggle("is-on", (b.getAttribute("data-st") || "all") === statusFilter);
        });
      }
      renderList();
      const params = new URLSearchParams(location.search);
      const id = params.get("id");
      if (id) {
        const found = apps.find((a) => String(a.id) === String(id));
        if (found) showDetail(found);
        else if (apps[0]) showDetail(apps[0]);
      } else if (apps[0]) {
        showDetail(apps[0]);
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
  const statusBar = el("app-status-filter");
  if (statusBar) {
    statusBar.addEventListener("click", (e) => {
      const btn = e.target.closest("button[data-st]");
      if (!btn) return;
      statusFilter = btn.getAttribute("data-st") || "all";
      statusBar.querySelectorAll("button").forEach((b) => b.classList.toggle("is-on", b === btn));
      showList();
      renderList();
    });
  }
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
