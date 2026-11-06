(function () {
  const session = HireLinkApp.mount({ search: { placeholder: "Rechercher une candidature…", onQuery: () => renderList() } });
  if (!session) return;

  const STATUS_LABEL = HireLinkApp.STATUS_FR;

  // Parcours HireLink : s’arrête à l’entretien (pas d’offre / embauche sur la plateforme).
  const PIPELINE = ["SUBMITTED", "SCREENING", "SHORTLISTED", "INTERVIEW"];

  let apps = [];
  let selectedId = null;
  const qst = new URLSearchParams(location.search).get("status");
  let statusFilter = ["all", "active", "INTERVIEW", "REJECTED", "WITHDRAWN"].includes(qst) ? qst : "all";

  const viewDetail = document.getElementById("view-detail");
  const listRoot = document.getElementById("apps-list");

  function statusBadgeClass(st) {
    if (st === "REJECTED" || st === "WITHDRAWN") return "badge-danger";
    if (st === "INTERVIEW" || st === "OFFER" || st === "HIRED") return "badge-ok"; // OFFER/HIRED = données legacy
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
    const q = ((filter && filter.value) || "").trim();
    const byStatus = apps.filter((a) => {
      if (statusFilter === "active") return !["REJECTED", "WITHDRAWN"].includes(a.status);
      return statusFilter === "all" || a.status === statusFilter;
    });
    const filtered = HireLinkSearch.rank(byStatus, q, [
      { get: (a) => a.jobTitle, weight: 3 },
      { get: (a) => STATUS_LABEL[a.status], weight: 2 },
      { get: (a) => a.rejectionReason, weight: 1 },
    ]);

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
      if (q) {
        HireLinkSearch.emptyState(listRoot, q, { onClear: () => { filter.value = ""; renderList(); } });
      } else {
        HireLinkApp.empty(listRoot, { title: "Aucune candidature", text: "Postulez à une offre pour suivre ici chaque étape.", action: { href: "candidate.html?scope=mine", label: "Voir les offres" } });
      }
      return;
    }

    listRoot.innerHTML = "";
    filtered.forEach((a) => {
      const row = document.createElement("button");
      row.type = "button";
      row.className = "cand-app-row" + (selectedId === a.id ? " is-selected" : "");
      row.dataset.id = String(a.id);
      row.dataset.focus = "application:" + a.id + " interview:" + a.id;
      const pace = HireLinkOffer.paceOf(a);
      const slaTxt = pace.badge;
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
        // Pastille du dossier en cours : le retard de réponse du recruteur, sinon la réputation de l'entreprise.
        const pending = ["SUBMITTED", "SCREENING", "SHORTLISTED", "INTERVIEW"].indexOf(a.status) >= 0;
        const days = a.slaRemainingDays == null ? null : Math.round(a.slaRemainingDays);
        const late = pending && (a.slaBreached || (days != null && days < 0));
        const soon = pending && !late && days != null && days <= 2;
        pill.textContent = late
          ? "Réponse attendue" + (days == null ? "" : " depuis " + Math.abs(days) + " j")
          : soon
            ? "Réponse attendue dans " + days + " j"
            : slaTxt;
        pill.hidden = !pill.textContent;
        pill.classList.toggle("danger", late);
        pill.classList.toggle("warn", soon);
      }
      row.addEventListener("click", () => showDetail(a, true));
      listRoot.appendChild(row);
    });
    HireLinkSearch.mark(listRoot, q);
  }

  function stepState(status, step) {
    const closed = ["REJECTED", "WITHDRAWN"].includes(status);
    if (closed) {
      if (step === "SUBMITTED") return "done";
      return "todo";
    }
    // Anciens dossiers Offre / Embauché : tout le parcours jusqu’à Entretien est considéré terminé.
    const effective =
      status === "OFFER" || status === "HIRED" ? "INTERVIEW" : status;
    const idx = PIPELINE.indexOf(effective);
    const sIdx = PIPELINE.indexOf(step);
    if (sIdx < 0 || idx < 0) return "todo";
    if ((status === "OFFER" || status === "HIRED") && step === "INTERVIEW") return "done";
    if (sIdx < idx) return "done";
    if (sIdx === idx) return "now";
    return "todo";
  }

  function paintDetails(app) {
    const box = el("d-details");
    if (!box) return;
    let d = null;
    try { d = app.applicationDetails ? JSON.parse(app.applicationDetails) : null; } catch (_) { d = null; }
    if (!d) { box.hidden = true; box.innerHTML = ""; return; }
    let when = "—";
    if (d.availability === "NOW") when = "Immédiatement";
    else if (d.availableFrom) {
      const p = String(d.availableFrom).split("-");
      when = "À partir du " + new Date(+p[0], +p[1] - 1, +p[2]).toLocaleDateString("fr-FR", { day: "numeric", month: "long", year: "numeric" });
    }
    const facts = [
      ["Disponibilité", when],
      ["Préavis", d.notice || "—"],
      d.salary ? ["Prétentions", Number(d.salary).toLocaleString("fr-FR") + " MAD / mois"] : null,
      ["Mode de travail", d.workMode || "—"],
      d.relocation ? ["Mobilité", "Prêt(e) à déménager"] : null,
    ].filter(Boolean);

    box.hidden = false;
    box.innerHTML = '<h4 style="margin:0 0 .55rem;font-size: var(--text-sm);text-transform:uppercase;letter-spacing:.08em;color:var(--ink-soft)">Mes souhaits &amp; disponibilité transmis</h4>' +
      '<div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(140px,1fr));gap:.65rem;font-size: var(--text-base)">' +
      facts.map(f => '<div><span class="muted" style="display:block;font-size: var(--text-xs);margin-bottom:.15rem">' + f[0] + '</span><strong style="color:var(--ink)">' + f[1] + '</strong></div>').join('') +
      '</div>';
  }

  /** Bloc « Votre entretien » : carte complète avec confirmation de présence et export calendrier. */
  function paintInterview(app) {
    const box = el("d-interview");
    if (!box) return;
    box.replaceChildren();
    if (!app.interviewAt) { box.hidden = true; return; }
    box.hidden = false;
    const title = document.createElement("h3");
    title.className = "timeline-title";
    title.textContent = "Votre entretien";
    const reload = () => load();
    box.append(title, HireLinkInterview.card(app, { role: app.status === "INTERVIEW" ? "candidate" : "readonly", onChange: reload }));
    const prep = document.createElement("button");
    prep.type = "button";
    prep.className = "btn btn-ghost btn-sm";
    prep.setAttribute("data-ai-cost", "");
    prep.textContent = "Se préparer à cet entretien";
    prep.addEventListener("click", () => {
      aiAsk(box, "/ai/interview-prep", { jobTitle: app.jobTitle || "", skills: app.candidateSkills || "" }, prep);
    });
    box.appendChild(prep);
    HireLinkCredits.paintCosts(box);
  }

  /** Réponse IA sous le bouton qui l'appelle : le prix est déjà écrit sur le bouton, le texte arrive ici. */
  async function aiAsk(host, endpoint, body, trigger) {
    let out = host.querySelector(".hl-ai-out");
    if (!out) {
      out = document.createElement("div");
      out.className = "coach-log hl-ai-out";
      host.appendChild(out);
    }
    out.hidden = false;
    out.innerHTML = Mascotte.waiting("Génération en cours…");
    trigger.disabled = true;
    try {
      const data = await HireLinkCredits.run(endpoint, body);
      const row = document.createElement("article");
      row.className = "coach-msg";
      const t = document.createElement("strong");
      t.textContent = data.title || "Réponse de l’IA";
      const c = document.createElement("div");
      c.textContent = data.content || "";
      row.append(t, c);
      out.replaceChildren(row);
    } catch (err) {
      out.remove();
      if (!err.handled) HireLink.toast("IA", err.message, "danger");
    } finally {
      trigger.disabled = false;
    }
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
    const why = el("d-match-why");
    if (why) {
      why.replaceChildren();
      const panel = window.HireLinkMatch && app.matchBreakdown ? HireLinkMatch.panel(app.matchBreakdown, { audience: "candidate", compact: true }) : null;
      if (panel) why.appendChild(panel);
      if (panel) {
        const bd = app.matchBreakdown || {};
        const required = (bd.covered || []).concat(bd.missing || []);
        if (required.length) {
          const explain = document.createElement("button");
          explain.type = "button";
          explain.className = "btn btn-ghost btn-sm";
          explain.setAttribute("data-ai-cost", "");
          explain.textContent = "Expliquer ce score";
          explain.addEventListener("click", () => {
            aiAsk(why, "/ai/match-explain",
              { skills: app.candidateSkills || "", requiredSkills: required.join(", ") }, explain);
          });
          why.appendChild(explain);
        }
        HireLinkCredits.paintCosts(why);
      }
      why.hidden = !panel;
    }

    let rows = [];
    try {
      rows = await HireLinkAPI.api("/applications/" + app.id + "/timeline");
    } catch (_) {
      rows = [];
    }

    paintInterview(app);
    paintDetails(app);

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
        div.innerHTML = '<div class="hl-ttime"></div><strong></strong><div class="muted" style="font-size: var(--text-sm)"></div>';
        setText(div.querySelector(".hl-ttime"), fmtWhen(when[app.status]));
        setText(div.querySelector("strong"), STATUS_LABEL[app.status]);
        setText(div.querySelector(".muted"), app.status === "REJECTED" ? app.rejectionReason || "" : "Vous avez retiré cette candidature");
        tl.appendChild(div);
      }
    }

    const actions = el("d-actions");
    if (actions) {
      actions.innerHTML = "";
      if (!["REJECTED", "WITHDRAWN", "HIRED"].includes(app.status)) {
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
    if (!HireLinkCredits.preflight()) return;
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
      HireLinkCredits.spend();
    } catch (err) {
      if (!HireLinkCredits.guard(err)) HireLink.toast("Synthèse", err.message, "danger");
    }
  }

  async function withdraw(c) {
    const ok = await Mascotte.confirm({
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
      else {
        const current = apps.find((x) => x.id === selectedId);
        if (current) paintDetail(current);
      }
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
  HireLinkCredits.paintCosts();
  // Une notification en direct (statut, entretien) met la liste à jour sans recharger la page.
  document.addEventListener("hirelink:push", () => load());
})();
