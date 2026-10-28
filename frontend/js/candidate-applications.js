(function () {
  const session = HireLinkApp.mount({ search: { placeholder: "Rechercher une candidature…", onQuery: () => renderList() } });
  if (!session) return;

  const STATUS_LABEL = HireLinkApp.STATUS_FR;

  /** Fin positive produit = INTERVIEW (pas d'étape Offre / Accepté). */
  const PIPELINE = ["SUBMITTED", "SCREENING", "SHORTLISTED", "INTERVIEW"];

  let apps = [];
  let selectedId = null;
  let statusFilter = "all";

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

  function showDetail(app, scroll) {
    selectedId = app.id;
    if (viewDetail) viewDetail.hidden = false;
    paintDetail(app);
    history.replaceState(null, "", "?id=" + app.id);
    if (scroll && window.matchMedia("(max-width: 980px)").matches && viewDetail) viewDetail.scrollIntoView({ behavior: "smooth", block: "start" });
    document.querySelectorAll(".cand-app-row").forEach(function (row) {
      row.classList.toggle("is-selected", row.dataset.id === String(app.id));
    });
  }

  function renderList() {
    if (!listRoot) return;
    const filter = el("rc-search");
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
      summary.textContent = HireLinkApp.plural(filtered.length, "candidature") + (statusFilter === "all" && !q ? "" : " affichée" + (filtered.length > 1 ? "s" : ""));
    }

    if (viewDetail && !filtered.some((a) => a.id === selectedId)) {
      if (filtered.length) showDetail(filtered[0]);
      else { selectedId = null; viewDetail.hidden = true; }
    }

    if (!filtered.length) {
      setHtml(
        listRoot,
        q
          ? '<p class="muted">Aucun résultat pour « ' + q + " ».</p>"
          : '<p class="muted">Aucune candidature — <a href="candidate.html?scope=mine">voir les offres</a>.</p>'
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
        ["INTERVIEW", "REJECTED", "WITHDRAWN"].includes(a.status)
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
      row.addEventListener("click", () => showDetail(a, true));
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

  const INTERVIEW_TYPE_FR = HireLinkApp.INTERVIEW_TYPE_FR;

  /** Bloc « Votre entretien » : date, format, lieu ou lien, consignes de l'entreprise. */
  function paintInterview(app) {
    const box = el("d-interview");
    if (!box) return;
    if (app.status !== "INTERVIEW" || !app.interviewAt) { box.hidden = true; return; }
    const loc = app.interviewLocation || "";
    const where = /^https?:\/\//i.test(loc)
      ? '<a href="' + HireLinkApp.esc(loc) + '" target="_blank" rel="noopener">Rejoindre l’entretien</a>'
      : HireLinkApp.esc(loc || "À préciser par l’entreprise");
    box.hidden = false;
    box.className = "cand-interview";
    box.innerHTML =
      '<h3 class="timeline-title" style="margin-top:0">Votre entretien</h3>' +
      '<p style="margin:0 0 .25rem"><strong>' + HireLinkApp.esc(HireLinkApp.fmtDateTime(app.interviewAt)) + "</strong> · " + HireLinkApp.esc(INTERVIEW_TYPE_FR[app.interviewType] || app.interviewType || "") + "</p>" +
      '<p style="margin:0 0 .25rem">' + where + "</p>" +
      (app.interviewNotes ? '<p class="muted" style="margin:0">' + HireLinkApp.esc(app.interviewNotes) + "</p>" : "");
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

    let rows = [];
    try {
      rows = await HireLinkAPI.api("/applications/" + app.id + "/timeline");
    } catch (_) {
      rows = [];
    }

    paintInterview(app);

    const tl = el("d-timeline");
    if (tl) {
      tl.innerHTML = "";
      const when = {};
      rows.forEach((h) => { if (h.to) when[h.to] = h.at; });
      PIPELINE.forEach((key) => {
        const state = stepState(app.status, key);
        const div = document.createElement("div");
        div.className = "hl-tstep " + state;
        div.innerHTML = '<div class="hl-ttime"></div><strong></strong>';
        setText(div.querySelector(".hl-ttime"), when[key] ? fmtWhen(when[key]) : state === "now" ? "En cours" : "");
        setText(div.querySelector("strong"), STATUS_LABEL[key]);
        tl.appendChild(div);
      });
      if (app.status === "REJECTED" || app.status === "WITHDRAWN") {
        const div = document.createElement("div");
        div.className = "hl-tstep now";
        div.innerHTML = '<div class="hl-ttime"></div><strong></strong><div class="muted" style="font-size:0.82rem"></div>';
        setText(div.querySelector(".hl-ttime"), fmtWhen(when[app.status]));
        setText(div.querySelector("strong"), STATUS_LABEL[app.status]);
        setText(div.querySelector(".muted"), app.status === "REJECTED" ? app.rejectionReason || "" : "Vous avez retiré cette candidature");
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
        body: {
          cvText: cvText,
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
    } catch (err) {
      HireLink.toast("Retrait impossible", err.message || "Réessayez", "danger");
    }
  }

  async function load() {
    try {
      const mine = await HireLinkAPI.api("/applications/mine");
      apps = Array.isArray(mine) ? mine : [];
      const wanted = apps.find((x) => String(x.id) === new URLSearchParams(location.search).get("id"));
      const statusBar = el("app-status-filter");
      if (statusBar) {
        statusBar.querySelectorAll("button").forEach((b) => {
          b.classList.toggle("is-on", (b.getAttribute("data-st") || "all") === statusFilter);
        });
      }
      renderList();
      if (wanted && wanted.id !== selectedId) showDetail(wanted);
    } catch (err) {
      HireLink.toast("Candidatures", err.message, "danger");
      setHtml(listRoot, '<p class="muted">Impossible de charger les candidatures.</p>');
    }
  }

  const statusBar = el("app-status-filter");
  if (statusBar) {
    statusBar.addEventListener("click", (e) => {
      const btn = e.target.closest("button[data-st]");
      if (!btn) return;
      statusFilter = btn.getAttribute("data-st") || "all";
      statusBar.querySelectorAll("button").forEach((b) => b.classList.toggle("is-on", b === btn));
      renderList();
    });
  }

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
