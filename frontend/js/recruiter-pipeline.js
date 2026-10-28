(function () {
  const RC = HireLinkApp;
  const session = RC.mount({
    active: "pipeline",
    title: "Pipeline",
    search: { placeholder: "Candidat, offre, statut…", onQuery: () => paint() },
  });
  if (!session) return;

  // Périmètre : fin positive à INTERVIEW (pas d'étape Offre / Accepté).
  const COLUMNS = [
    { key: "SUBMITTED", label: "Reçues", tone: "neutral" },
    { key: "SCREENING", label: "Présélection", tone: "warn" },
    { key: "SHORTLISTED", label: "Shortlist", tone: "info" },
    { key: "INTERVIEW", label: "Entretien", tone: "ok", end: true },
  ];
  const NEXT = {
    SUBMITTED: "SCREENING",
    SCREENING: "SHORTLISTED",
    SHORTLISTED: "INTERVIEW",
  };
  const STEP_HINT = {
    SCREENING: "Présélection : CV et compétences vs fiche de poste.",
    SHORTLISTED: "Shortlist : candidat retenu pour un échange humain.",
    INTERVIEW: "Planifiez l’entretien : type, date et lieu ou lien.",
  };

  let allApps = [];
  let refusalCatalog = [];
  const ACTIVE = new Set(["SUBMITTED", "SCREENING", "SHORTLISTED", "INTERVIEW"]);

  function selectedOfferId() {
    const v = (document.getElementById("pipe-offer") || {}).value || "all";
    return v === "all" || !v ? null : Number(v);
  }

  function appsForScope() {
    const oid = selectedOfferId();
    if (oid == null || Number.isNaN(oid)) return allApps.slice();
    return allApps.filter((a) => Number(a.jobOfferId) === oid);
  }

  function offerCatalog() {
    const map = new Map();
    allApps.forEach((a) => {
      if (a.jobOfferId == null) return;
      const id = Number(a.jobOfferId);
      const cur = map.get(id) || { id, title: a.jobTitle || "Offre #" + id, count: 0, active: 0 };
      cur.count += 1;
      if (ACTIVE.has(a.status)) cur.active += 1;
      if (a.jobTitle) cur.title = a.jobTitle;
      map.set(id, cur);
    });
    return Array.from(map.values()).sort((a, b) => {
      if (b.active !== a.active) return b.active - a.active;
      return String(a.title).localeCompare(String(b.title), "fr");
    });
  }

  function rebuildOfferSelect() {
    const sel = document.getElementById("pipe-offer");
    if (!sel) return;
    const pending = sel.dataset.pending;
    const prev = pending || sel.value || "all";
    if (pending) delete sel.dataset.pending;
    const offers = offerCatalog();
    const totalActive = allApps.filter((a) => ACTIVE.has(a.status)).length;
    sel.innerHTML =
      '<option value="all">Toutes les offres (' +
      totalActive +
      " actifs)</option>" +
      offers
        .map(
          (o) =>
            '<option value="' +
            o.id +
            '">' +
            esc(o.title) +
            " (" +
            o.active +
            ")</option>"
        )
        .join("");
    const still =
      prev === "all" || offers.some((o) => String(o.id) === String(prev));
    sel.value = still ? prev : "all";
    sel.classList.toggle("is-filtered", sel.value !== "all");
  }

  function renderFunnelFromApps(scopeApps, scopeLabel) {
    const el = document.getElementById("funnel-strip");
    if (!el) return;
    const by = {};
    ["SUBMITTED", "SCREENING", "SHORTLISTED", "INTERVIEW", "REJECTED", "WITHDRAWN"].forEach(
      (k) => (by[k] = 0)
    );
    let active = 0;
    let slaBreached = 0;
    let hotMatches = 0;
    scopeApps.forEach((a) => {
      by[a.status] = (by[a.status] || 0) + 1;
      if (ACTIVE.has(a.status)) {
        active += 1;
        if (a.slaBreached || (a.slaRemainingDays != null && a.slaRemainingDays < 0)) slaBreached += 1;
        if ((a.matchScore || 0) >= 75) hotMatches += 1;
      }
    });
    const interviews = by.INTERVIEW || 0;
    const refused = by.REJECTED || 0;
    const entered = active + refused + (by.WITHDRAWN || 0);
    const conv =
      entered > 0 ? Math.round((interviews / entered) * 1000) / 10 : 0;
    const stat = (label, val, cls) =>
      '<div class="' + (cls || "") + '"><small>' + esc(label) + "</small><strong>" + esc(String(val)) + "</strong></div>";
    el.innerHTML =
      '<div class="rc-funnel">' +
      '<div style="flex:1 1 100%;flex-direction:row;gap:.5rem;align-items:center"><span class="rc-pill rc-pill--info">' +
      (scopeLabel ? "Vue filtrée · " + esc(scopeLabel) : "Toutes les offres") + "</span></div>" +
      stat("Dossiers actifs", active) +
      stat("Retards de réponse", slaBreached, slaBreached ? "is-danger" : "") +
      stat("Match ≥ 75 %", hotMatches) +
      stat("En entretien", interviews) +
      stat("Conversion → entretien", conv + " %") +
      stat("Refus", refused) +
      "</div>";
  }

  function refreshFunnel() {
    const oid = selectedOfferId();
    const scope = appsForScope();
    let label = "";
    if (oid != null) {
      const offer = offerCatalog().find((o) => o.id === oid);
      label = offer ? offer.title : "Offre #" + oid;
    }
    renderFunnelFromApps(scope, label);
  }

  function slaClass(days, breached) {
    if (breached || (days != null && days < 0)) return "danger";
    if (days != null && days <= 2) return "warn";
    return "";
  }

  function matchBadgeClass(score) {
    if (score == null) return "badge-chrome";
    if (score >= 80) return "badge-ok match-hot";
    if (score >= 55) return "badge-info";
    return "badge-rose";
  }

  function esc(s) {
    return String(s == null ? "" : s)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;")
      .replace(/\'/g, "&#39;");
  }

  function initials(name) {
    const parts = String(name || "?")
      .trim()
      .split(/\s+/)
      .filter(Boolean);
    if (!parts.length) return "?";
    if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  }

  function skillTags(skills) {
    if (!skills) return "";
    const parts = String(skills)
      .split(/[,;|/]+/)
      .map((s) => s.trim())
      .filter(Boolean)
      .slice(0, 12);
    if (!parts.length) return "";
    return (
      '<div class="hl-skill-tags">' +
      parts.map((s) => "<span>" + esc(s) + "</span>").join("") +
      "</div>"
    );
  }

  function starsHtml(id, label) {
    return (
      '<div class="hl-score-item">' +
      "<label>" +
      esc(label) +
      "</label>" +
      '<div class="hl-stars" data-for="' +
      id +
      '" role="group" aria-label="' +
      esc(label) +
      '">' +
      [1, 2, 3, 4, 5]
        .map(
          (n) =>
            '<button type="button" data-v="' +
            n +
            '" aria-label="' +
            n +
            ' sur 5">★</button>'
        )
        .join("") +
      "</div>" +
      '<input type="hidden" id="' +
      id +
      '" value="" />' +
      "</div>"
    );
  }

  function bindStars(root) {
    (root || document).querySelectorAll(".hl-stars").forEach((group) => {
      const hid = document.getElementById(group.getAttribute("data-for"));
      group.querySelectorAll("button").forEach((btn) => {
        btn.onclick = (e) => {
          e.preventDefault();
          const v = Number(btn.getAttribute("data-v"));
          if (hid) hid.value = String(v);
          group.querySelectorAll("button").forEach((b) => {
            b.classList.toggle("is-on", Number(b.getAttribute("data-v")) <= v);
          });
          group.dispatchEvent(new CustomEvent("hl-score-change", { bubbles: true }));
        };
      });
    });
  }

  const FIXED_REASONS = [
    ["PROFILE_MISMATCH", "Compétences pas assez proches du poste"],
    ["EXPERIENCE_LEVEL", "Expérience insuffisante pour le poste"],
    ["LANGUAGE_GAP", "Langue insuffisante pour le poste"],
    ["LOCATION_UNAVAILABLE", "Lieu ou mobilité incompatible"],
    ["SALARY_EXPECTATION", "Prétentions hors budget"],
    ["PROCESS_WITHDRAW_ORG", "Poste pourvu ou recrutement clos"],
    ["OTHER_CATALOG", "Autre"],
  ];

  function refusalSelectHtml() {
    return (
      '<div class="hl-dossier-section"><h5>Motif du refus</h5>' +
      '<p class="muted" style="margin:0 0 .55rem">Le candidat voit ce motif. « Autre » permet de le préciser.</p>' +
      '<div class="field"><label>Motif</label>' +
      '<select id="reject-reason"><option value="">— Choisir —</option>' +
      FIXED_REASONS.map((r) => '<option value="' + r[0] + '">' + esc(r[1]) + "</option>").join("") +
      "</select></div>" +
      '<div class="field" id="reject-other-wrap" hidden><label>Précision</label>' +
      '<textarea id="reject-other" rows="3" placeholder="Ce que le candidat doit lire"></textarea></div></div>'
    );
  }

  function metric(label, val, tone) {
    return (
      '<div class="kpi-card' +
      (tone ? " kpi-card--" + tone : "") +
      '"><div class="kpi-card-label">' +
      esc(label) +
      '</div><div class="kpi-card-value">' +
      esc(String(val == null ? "—" : val)) +
      "</div></div>"
    );
  }

  async function showTimeline(appId) {
    try {
      const rows = await HireLinkAPI.api("/applications/" + appId + "/timeline");
      const html =
        !rows.length
          ? "<p class='muted'>Aucun événement.</p>"
          : '<ol class="rc-timeline">' +
            rows
              .map((h) => {
                let extra = "";
                try { extra = h.payload ? JSON.parse(h.payload).reason || "" : ""; } catch (_) {}
                const from = RC.STATUS_FR[h.from] || h.from;
                return "<li><strong>" + (from ? esc(from) + " → " : "") + esc(RC.STATUS_FR[h.to] || h.to) + "</strong> · <span class='muted'>" +
                  esc(RC.fmtDateTime(h.at)) + "</span>" + (extra ? "<br/><span class='muted'>Motif : " + esc(extra) + "</span>" : "") + "</li>";
              })
              .join("") +
            "</ol>";
      await HireLink.openModal({
        title: "Historique du dossier",
        description: "Journal chronologique — non modifiable",
        bodyHtml: html,
        confirmLabel: "Fermer",
        hideCancel: true,
        large: true,
      });
    } catch (err) {
      HireLink.toast("Timeline", err.message, "danger");
    }
  }

  function scoreSummaryHtml(criteria, entries) {
    if (!entries.length) return '<p class="muted" style="margin:.2rem 0 .8rem">Aucune évaluation enregistrée pour ce dossier.</p>';
    const rows = criteria.map((cr) => {
      const list = entries.filter((e) => e.criterionId === cr.id);
      if (!list.length) return "";
      const avg = list.reduce((t, e) => t + e.score, 0) / list.length;
      return '<div class="hl-fact"><span class="k">' + esc(cr.label) + '</span><span class="v">' + avg.toFixed(1) + " / 5 · " + RC.plural(list.length, "avis", "avis") + "</span></div>";
    }).join("");
    return '<div class="hl-dossier-section"><h5>Synthèse des évaluateurs</h5><div class="hl-fact-grid">' + rows + "</div></div>";
  }

  async function saveQuickScores(c, scores) {
    try {
      const criteria = await HireLinkAPI.api("/offers/" + c.jobOfferId + "/scorecard-criteria");
      const map = { tech: /tech/i, soft: /soft/i, culture: /cultur/i };
      for (const cr of criteria) {
        const key = Object.keys(map).find((k) => map[k].test(cr.label));
        if (key && scores[key]) {
          await HireLinkAPI.api("/applications/" + c.id + "/scorecard", {
            method: "POST",
            body: { criterionId: Number(cr.id), score: Number(scores[key]), comment: null },
          });
        }
      }
    } catch (_) { /* best effort : la note texte reste enregistrée */ }
  }

  async function rateScorecard(c) {
    try {
      let criteria = await HireLinkAPI.api("/offers/" + c.jobOfferId + "/scorecard-criteria");
      if (!criteria.length && RC.isHM()) {
        HireLink.toast("Scorecard", "Aucun critère défini pour cette offre — demandez à un recruteur de les créer.", "warn");
        return;
      }
      const entries = await HireLinkAPI.api("/applications/" + c.id + "/scorecard").catch(() => []);
      if (!criteria.length) {
        criteria = await HireLinkAPI.api("/offers/" + c.jobOfferId + "/scorecard-criteria", {
          method: "PUT",
          body: {
            criteria: [
              { label: "Technique", weight: 3, veto: false },
              { label: "Soft skills", weight: 2, veto: false },
              { label: "Culture fit", weight: 1, veto: false },
            ],
          },
        });
      }
      const body =
        dossierHtml(c, { compact: true }) +
        scoreSummaryHtml(criteria, entries) +
        '<div class="hl-dossier-section"><h5>Ma notation · 1 à 5</h5>' +
        '<div class="hl-score-row">' +
        criteria
          .map((cr) =>
            starsHtml(
              "sc-" + cr.id,
              cr.label + (cr.veto ? " (veto)" : "")
            )
          )
          .join("") +
        "</div></div>";

      const ok = await HireLink.openDrawer({
        title: "Scorecard · " + c.candidateName,
        description: c.jobTitle,
        bodyHtml: body,
        confirmLabel: "Enregistrer les notes",
        confirmDisabled: true,
        wide: true,
        onReady: (api) => {
          bindStars(api.root);
          criteria.forEach((cr) => {
            const mine = entries.filter((e) => e.criterionId === cr.id && e.evaluatorUserId === session.userId).pop();
            const btn = mine && api.root.querySelector('.hl-stars[data-for="sc-' + cr.id + '"] button[data-v="' + mine.score + '"]');
            if (btn) btn.click();
          });
          const refresh = () => {
            const okAll = criteria.every((cr) => {
              const el = document.getElementById("sc-" + cr.id);
              return el && el.value;
            });
            api.setConfirmEnabled(okAll);
          };
          api.root.addEventListener("hl-score-change", refresh);
          refresh();
        },
      });
      if (!ok) return;
      for (const cr of criteria) {
        const el = document.getElementById("sc-" + cr.id);
        await HireLinkAPI.api("/applications/" + c.id + "/scorecard", {
          method: "POST",
          body: {
            criterionId: Number(cr.id),
            score: Number((el && el.value) || 3),
            comment: null,
          },
        });
      }
      HireLink.toast("Scorecard", "Notes enregistrées", "ok");
    } catch (err) {
      HireLink.toast("Scorecard", err.message, "danger");
    }
  }

  function fact(k, v, tone) {
    return (
      '<div class="hl-fact"><span class="k">' +
      esc(k) +
      '</span><span class="v' +
      (tone ? " " + tone : "") +
      '">' +
      esc(v) +
      "</span></div>"
    );
  }

  document.addEventListener("click", (e) => {
    const b = e.target.closest("[data-open-cv]");
    if (!b) return;
    HireLinkAPI.openFile("/applications/" + b.getAttribute("data-open-cv") + "/cv").catch((err) => HireLink.toast("CV", err.message, "danger"));
  });

  function dossierHtml(c, opts) {
    opts = opts || {};
    const matchPct = c.matchScore != null ? Math.round(c.matchScore) : null;
    const slaBreached = !!(c.slaBreached || (c.slaRemainingDays != null && c.slaRemainingDays < 0));
    const slaTxt = slaBreached
      ? "Délai dépassé"
      : c.slaRemainingDays == null
        ? "Sans objet"
        : c.slaRemainingDays === 0
          ? "Dernier jour"
          : c.slaRemainingDays + " j restants";
    const cvTxt = c.cvFileName
      ? c.cvFileName
      : c.cvSource
        ? String(c.cvSource)
        : "Non fourni";
    const letterFile = c.coverLetter && /\.(pdf|docx?|txt)$/i.test(String(c.coverLetter).trim());
    const letterTxt = !c.coverLetter ? "Non jointe" : letterFile ? c.coverLetter : "Texte joint";
    const statusLabel = RC.STATUS_FR[c.status] || c.status;

    let html =
      '<div class="hl-dossier' +
      (opts.refuse ? " hl-refuse-only" : "") +
      '">' +
      '<div class="hl-dossier-hero">' +
      '<div class="hl-dossier-avatar" aria-hidden="true">' +
      esc(initials(c.candidateName)) +
      "</div>" +
      '<div class="hl-dossier-hero-main">' +
      "<h4>" +
      esc(c.candidateName || "Candidat") +
      "</h4>" +
      '<div class="hl-dossier-mail">' +
      esc(c.candidateEmail || "Email non renseigné") +
      "</div>" +
      '<div style="margin-top:0.35rem"><span class="badge badge-chrome">' +
      esc(statusLabel) +
      "</span></div>" +
      "</div>" +
      '<div class="hl-dossier-hero-side">' +
      (matchPct != null
        ? '<div class="hl-match-ring" style="--m:' +
          matchPct +
          '" title="Match compétences">' +
          matchPct +
          "%</div>"
        : "") +
      "</div></div>";

    if (!opts.compact) {
      const sent = c.submittedAt ? new Date(c.submittedAt).toLocaleString("fr-FR") : "—";
      const cvOrigin = c.cvSource === "UPLOAD" ? "Fichier envoyé avec la candidature" : "Profil HireLink";
      html +=
        '<div class="hl-dossier-section"><h5>1. Candidature</h5><div class="hl-fact-grid">' +
        fact("Poste", c.jobTitle || "—") +
        fact("Envoyée le", sent) +
        fact("Statut", statusLabel) +
        fact("Compatibilité", matchPct != null ? matchPct + " %" : "—") +
        fact("Délai de réponse", slaTxt, slaBreached ? "is-danger" : "is-ok") +
        "</div></div>" +
        '<div class="hl-dossier-section"><h5>2. Profil</h5><div class="hl-fact-grid">' +
        fact("Nom", c.candidateName || "—") +
        fact("E-mail", c.candidateEmail || "—") +
        "</div>" +
        (c.candidateSkills
          ? "<div style='margin-top:.65rem'>" + skillTags(c.candidateSkills) + "</div>"
          : "<p class='muted'>Aucune compétence renseignée.</p>") +
        "</div>" +
        '<div class="hl-dossier-section"><h5>3. CV</h5><article class="doc-card"><strong>' +
        esc(cvTxt) +
        "</strong><p class='muted'>" +
        esc(cvOrigin) +
        "</p>" +
        (c.hasCvFile ? '<button type="button" class="btn btn-soft btn-sm" style="margin-top:.5rem" data-open-cv="' + c.id + '">Ouvrir le CV</button>' : "") +
        "</article></div>";
      if (!opts.hideLetter) {
        html +=
          '<div class="hl-dossier-section"><h5>4. Lettre de motivation</h5>' +
          (c.coverLetter
            ? letterFile
              ? '<article class="doc-card"><strong>' + esc(c.coverLetter) + "</strong><p class='muted'>Fichier joint</p></article>"
              : '<p class="hl-letter-box">' + esc(c.coverLetter) + "</p>"
            : "<p class='muted'>Aucune lettre jointe.</p>") +
          "</div>";
      }
      if (c.interviewType || c.interviewAt || c.interviewNotes) {
        html +=
          '<div class="hl-dossier-section"><h5>5. Entretien</h5><div class="hl-fact-grid">' +
          fact("Type", c.interviewType || "—") +
          fact("Date", c.interviewAt ? new Date(c.interviewAt).toLocaleString("fr-FR") : "—") +
          fact("Lieu", c.interviewLocation || "—") +
          "</div>" +
          (c.interviewNotes ? '<p class="hl-letter-box">' + esc(c.interviewNotes) + "</p>" : "") +
          "</div>";
      }
      if ((c.advanceNote && opts.showNote !== false) || c.rejectionReason) {
        html += '<div class="hl-dossier-section"><h5>6. Suivi</h5>';
        if (c.rejectionReason) {
          html += "<p><strong>Motif de refus.</strong> " + esc(c.rejectionReason) + "</p>";
        }
        if (c.advanceNote && opts.showNote !== false) {
          html += '<p class="hl-letter-box">' + esc(c.advanceNote) + "</p>";
        }
        html += "</div>";
      }
    }

    html += "</div>";
    return html;
  }

  function checksState(c) {
    return {
      id: !!(c.candidateName && c.candidateEmail),
      cv: !!(c.cvFileName || c.cvSource),
      sla: !c.slaBreached,
    };
  }

  function checksHtml(c, mode) {
    const pre = checksState(c || {});
    const count = (pre.id ? 1 : 0) + (pre.cv ? 1 : 0) + (pre.sla ? 1 : 0);
    const sub =
      mode === "reject"
        ? "Confirmez la relecture avant d’enregistrer un motif catalogue."
        : "Pré-coché si les données dossier le permettent — validez avant d’avancer.";
    function row(id, checked, title, small, prefilled) {
      return (
        '<label class="hl-check' +
        (prefilled ? " is-pre" : "") +
        '"><input type="checkbox" id="' +
        id +
        '"' +
        (checked ? " checked" : "") +
        " />" +
        "<span><strong>" +
        title +
        (prefilled ? '<span class="pre-tag">Auto</span>' : "") +
        "</strong><small>" +
        small +
        "</small></span></label>"
      );
    }
    return (
      '<div class="hl-dossier-section hl-checklist-v2" role="group" aria-label="Checklist">' +
      '<div class="hl-checklist-head"><strong>Checklist</strong>' +
      '<span class="badge badge-chrome" id="chk-count">' +
      count +
      " / 3</span></div>" +
      '<p class="muted hl-checklist-sub">' +
      sub +
      "</p>" +
      row("chk-id", pre.id, "Identité vérifiée", "Nom et email présents dans le dossier", pre.id) +
      row(
        "chk-cv",
        pre.cv,
        "Dossier relu",
        "CV / pièces déjà joints — confirmez la relecture",
        pre.cv
      ) +
      row(
        "chk-sla",
        pre.sla,
        "Délai de 1ʳᵉ réponse",
        pre.sla
          ? "Engagement encore dans les temps"
          : "Délai dépassé — cochez pour assumer la décision",
        pre.sla
      ) +
      "</div>"
    );
  }

  function readChecks() {
    return {
      confirmIdentity: !!(document.getElementById("chk-id") || {}).checked,
      confirmCvReviewed: !!(document.getElementById("chk-cv") || {}).checked,
      confirmSlaAware: !!(document.getElementById("chk-sla") || {}).checked,
    };
  }

  function updateCheckCount(root) {
    const n =
      (!!(document.getElementById("chk-id") || {}).checked ? 1 : 0) +
      (!!(document.getElementById("chk-cv") || {}).checked ? 1 : 0) +
      (!!(document.getElementById("chk-sla") || {}).checked ? 1 : 0);
    const el = (root || document).querySelector("#chk-count");
    if (el) el.textContent = n + " / 3";
    return n;
  }

  function advanceValid(needInterview) {
    const note = ((document.getElementById("adv-note") || {}).value || "").trim();
    const st = (document.getElementById("score-tech") || {}).value;
    const ss = (document.getElementById("score-soft") || {}).value;
    const scul = (document.getElementById("score-culture") || {}).value;
    const scoresOk = !!(st && ss && scul);
    if (!note && !scoresOk) return false;
    if (needInterview) {
      const type = (document.getElementById("int-type") || {}).value || "";
      const atLocal = (document.getElementById("int-at") || {}).value || "";
      if (!type || !atLocal) return false;
    }
    return true;
  }

  async function advance(c) {
    const next = NEXT[c.status];
    if (!next) {
      HireLink.toast("Fin", "Étape terminale — planifiez l’entretien hors pipeline", "info");
      return;
    }
    const needInterview = next === "INTERVIEW";
    const body =
      '<div class="hl-step-banner"><div><strong>' +
      esc(RC.STATUS_FR[c.status] || c.status) +
      " → " +
      esc(RC.STATUS_FR[next] || next) +
      "</strong>" +
      esc(STEP_HINT[next] || "") +
      "</div></div>" +
      dossierHtml(c, { hideLetter: true, showNote: false }) +
      (c.coverLetter && !/\.(pdf|docx?|txt)$/i.test(String(c.coverLetter).trim())
        ? '<div class="hl-dossier-section"><h5>Lettre de motivation</h5><p class="hl-letter-box">' +
          esc(c.coverLetter) +
          "</p></div>"
        : "") +
      '<div class="hl-dossier-section"><h5>Évaluation</h5>' +
      '<div class="field"><label>Note interne</label>' +
      '<textarea id="adv-note" rows="2" placeholder="Points forts, risques, suite…"></textarea></div>' +
      '<div class="hl-score-row" style="margin-top:0.65rem">' +
      starsHtml("score-tech", "Technique") +
      starsHtml("score-soft", "Soft skills") +
      starsHtml("score-culture", "Culture fit") +
      "</div>" +
      '<p class="muted" style="margin:0.55rem 0 0;font-size:0.75rem">Renseignez la note <em>ou</em> les 3 scores pour débloquer la confirmation.</p></div>' +
      (needInterview
        ? '<div class="hl-interview-block">' +
          '<strong style="font-size:0.78rem;text-transform:uppercase;letter-spacing:.05em">Organisation de l’entretien</strong>' +
          '<div class="field"><label>Type *</label><select id="int-type">' +
          '<option value="">— Choisir —</option>' +
          '<option value="PHONE">Téléphone</option>' +
          '<option value="VISIO">Visio</option>' +
          '<option value="ONSITE">Présentiel</option>' +
          '<option value="ASYNC">Asynchrone (vidéo / test)</option>' +
          "</select></div>" +
          '<div class="field"><label>Date &amp; heure *</label><input type="datetime-local" id="int-at" /></div>' +
          '<div class="field"><label>Lieu / lien</label><input id="int-loc" placeholder="Bureau Casablanca · Meet · Zoom…" /></div>' +
          '<div class="field"><label>Brief candidat</label><textarea id="int-notes" rows="2" placeholder="Interlocuteurs, durée, livrables…"></textarea></div>' +
          "</div>"
        : "");

    const ok = await HireLink.openDrawer({
      title: "Avancer · " + c.candidateName,
      description: "Note ou scores, puis confirmation",
      bodyHtml: body,
      confirmLabel: "Confirmer l’étape",
      confirmDisabled: true,
      wide: true,
      onReady: (api) => {
        bindStars(api.root);
        const refresh = () => api.setConfirmEnabled(advanceValid(needInterview));
        api.root.addEventListener("change", refresh);
        api.root.addEventListener("input", refresh);
        api.root.addEventListener("hl-score-change", refresh);
        refresh();
      },
    });
    if (!ok) return;

    if (!advanceValid(needInterview)) {
      HireLink.toast("Incomplet", "Note ou scores + entretien si requis", "warn");
      return;
    }

    const payload = {
      status: next,
      advanceNote: null,
      confirmIdentity: true,
      confirmCvReviewed: true,
      confirmSlaAware: true,
    };
    const noteParts = [];
    const rawNote = ((document.getElementById("adv-note") || {}).value || "").trim();
    if (rawNote) noteParts.push(rawNote);
    const st = (document.getElementById("score-tech") || {}).value;
    const ss = (document.getElementById("score-soft") || {}).value;
    const scul = (document.getElementById("score-culture") || {}).value;
    if (st || ss || scul) {
      noteParts.push(
        "Scorecard · tech " +
          (st || "—") +
          "/5 · soft " +
          (ss || "—") +
          "/5 · culture " +
          (scul || "—") +
          "/5"
      );
    }
    if (noteParts.length) payload.advanceNote = noteParts.join("\n");

    if (needInterview) {
      payload.interviewType = (document.getElementById("int-type") || {}).value || "";
      payload.interviewAt = new Date((document.getElementById("int-at") || {}).value).toISOString();
      payload.interviewLocation = (document.getElementById("int-loc") || {}).value || null;
      payload.interviewNotes = (document.getElementById("int-notes") || {}).value || null;
    }

    try {
      await HireLinkAPI.api("/applications/" + c.id + "/status", {
        method: "PATCH",
        body: payload,
      });
      if (st || ss || scul) saveQuickScores(c, { tech: st, soft: ss, culture: scul });
      HireLink.toast("Étape validée", c.candidateName + " → " + (RC.STATUS_FR[next] || next), "ok");
      load(true);
    } catch (err) {
      HireLink.toast("Erreur", err.message, "danger");
    }
  }

  async function rejectFlow(c) {
    const body =
      dossierHtml(c, { refuse: true, hideLetter: true, showNote: false }) +
      refusalSelectHtml();

    const ok = await HireLink.openDrawer({
      title: "Refuser · " + c.candidateName,
      description: "Le candidat verra le motif choisi.",
      bodyHtml: body,
      confirmLabel: "Confirmer le refus",
      danger: true,
      confirmDisabled: true,
      wide: true,
      onReady: (api) => {
        const refresh = () => {
          const reason = ((document.getElementById("reject-reason") || {}).value || "").trim();
          const otherWrap = document.getElementById("reject-other-wrap");
          if (otherWrap) otherWrap.hidden = reason !== "OTHER_CATALOG";
          const other = ((document.getElementById("reject-other") || {}).value || "").trim();
          api.setConfirmEnabled(!!reason && (reason !== "OTHER_CATALOG" || other.length > 2));
        };
        api.root.addEventListener("change", refresh);
        api.root.addEventListener("input", refresh);
        refresh();
      },
    });
    if (!ok) return;
    let reason = (document.getElementById("reject-reason") || {}).value || "";
    if (!reason.trim()) {
      HireLink.toast("Motif requis", "Choisissez un motif", "warn");
      return;
    }
    if (reason === "OTHER_CATALOG") {
      const other = ((document.getElementById("reject-other") || {}).value || "").trim();
      if (other.length < 3) {
        HireLink.toast("Motif requis", "Précisez le motif", "warn");
        return;
      }
      reason = "OTHER:" + other;
    }
    try {
      await HireLinkAPI.api("/applications/" + c.id + "/status", {
        method: "PATCH",
        body: {
          status: "REJECTED",
          rejectionReason: reason,
          confirmIdentity: true,
          confirmCvReviewed: true,
          confirmSlaAware: true,
        },
      });
      HireLink.toast("Candidature refusée", "Motif catalogue enregistré", "ok");
      load(true);
    } catch (err) {
      HireLink.toast("Erreur", err.message, "danger");
    }
  }

  function mountHmAssign(c) {
    const slot = document.getElementById("hm-slot");
    if (!slot) return;
    RC.swr("members", "/organizations/mine/members", (members) => {
      const eligible = (members || []).filter((m) => m.orgRole === "HIRING_MANAGER" || m.orgRole === "RECRUTEUR");
      if (!eligible.length || !document.getElementById("hm-slot")) return;
      slot.innerHTML =
        "<h5>Hiring Manager assigné</h5>" +
        '<div class="rc-row"><select id="hm-select" class="rc-select" style="flex:1"><option value="">— Aucun —</option>' +
        eligible.map((m) => '<option value="' + m.userId + '"' + (Number(c.assignedHmUserId) === Number(m.userId) ? " selected" : "") + ">" +
          esc(m.fullName || m.email) + " · " + esc(RC.ROLE_FR[m.orgRole] || m.orgRole) + "</option>").join("") +
        '</select><button type="button" class="btn btn-soft btn-sm" id="hm-save">Assigner</button></div>' +
        '<p class="muted" style="margin:.4rem 0 0;font-size:.78rem">Le Hiring Manager ne voit que les dossiers qui lui sont assignés et reçoit une notification.</p>';
      document.getElementById("hm-save").onclick = async (e) => {
        const v = document.getElementById("hm-select").value;
        RC.busy(e.currentTarget, true);
        try {
          const updated = await HireLinkAPI.api("/applications/" + c.id + "/assign-hm", { method: "PATCH", body: { hmUserId: v ? Number(v) : null } });
          c.assignedHmUserId = updated.assignedHmUserId;
          RC.data.invalidate();
          RC.toast("Assignation", v ? "Hiring Manager assigné" : "Assignation retirée", "ok");
        } catch (err) {
          RC.toast("Assignation", err.message, "danger");
        } finally {
          RC.busy(e.currentTarget, false);
        }
      };
    });
  }

  async function openDossier(c) {
    const canAdvance = !!NEXT[c.status];
    await HireLink.openDrawer({
      title: "Dossier · " + c.candidateName,
      description: (c.jobTitle || "") + " · " + (RC.STATUS_FR[c.status] || c.status),
      bodyHtml:
        dossierHtml(c) +
        '<div class="hl-drawer-actions-inline">' +
        '<button type="button" class="btn btn-soft btn-sm" id="btn-dl">Télécharger le dossier</button>' +
        '<button type="button" class="btn btn-soft btn-sm" id="btn-sum">Synthèse du CV</button>' +
        '<button type="button" class="btn btn-soft btn-sm" id="btn-tl">Historique</button>' +
        '<button type="button" class="btn btn-soft btn-sm" id="btn-sc">Scorecard</button>' +
        (canAdvance
          ? '<button type="button" class="btn btn-primary btn-sm" id="btn-adv-from">Avancer</button>'
          : "") +
        '<button type="button" class="btn btn-ghost btn-sm" id="btn-rej-from">Refuser</button>' +
        "</div>" +
        (RC.canManage() ? '<div class="hl-dossier-section" id="hm-slot"></div>' : ""),
      confirmLabel: "Fermer",
      hideCancel: true,
      wide: true,
      onReady: () => {
        mountHmAssign(c);
        const sum = document.getElementById("btn-sum");
        if (sum) {
          sum.onclick = async () => {
            const cvText = [
              c.candidateName || "",
              c.candidateSkills || "",
              c.coverLetter || "",
              c.cvFileName || "",
            ].filter(Boolean).join("\n");
            sum.disabled = true;
            try {
              const data = await HireLinkAPI.api("/ai/cv-summary", {
                method: "POST",
                body: { cvText },
              });
              HireLink.openModal({
                title: "Synthèse du CV",
                description: c.candidateName || "",
                bodyHtml: "<p style='white-space:pre-wrap;margin:0'></p>",
                confirmLabel: "Fermer",
                hideCancel: true,
              });
              const p = document.querySelector("#hl-overlay .modal-body p");
              if (p) p.textContent = data.content || "";
            } catch (err) {
              HireLink.toast("Synthèse", err.message, "danger");
            } finally {
              sum.disabled = false;
            }
          };
        }
        const dl = document.getElementById("btn-dl");
        if (dl) {
          dl.onclick = () => {
            const lines = [
              c.candidateName || "",
              c.candidateEmail || "",
              "Poste : " + (c.jobTitle || ""),
              "Statut : " + (RC.STATUS_FR[c.status] || c.status || ""),
              "CV : " + (c.cvFileName || c.cvSource || "Non fourni"),
              "Lettre : " + (c.coverLetter || "Non jointe"),
              "Compétences : " + (c.candidateSkills || ""),
              c.interviewType ? "Entretien : " + c.interviewType : "",
              c.interviewAt ? "Date : " + new Date(c.interviewAt).toLocaleString("fr-FR") : "",
              c.interviewLocation ? "Lieu : " + c.interviewLocation : "",
              c.interviewNotes ? "Notes : " + c.interviewNotes : "",
              c.rejectionReason ? "Motif : " + c.rejectionReason : "",
              c.advanceNote ? "Note RH : " + c.advanceNote : "",
            ]
              .filter(Boolean)
              .join("\n");
            const blob = new Blob([lines], { type: "text/plain;charset=utf-8" });
            const a = document.createElement("a");
            a.href = URL.createObjectURL(blob);
            a.download =
              "dossier-" + String(c.candidateName || "candidat").replace(/\s+/g, "-") + ".txt";
            a.click();
            URL.revokeObjectURL(a.href);
          };
        }
        const tl = document.getElementById("btn-tl");
        if (tl)
          tl.onclick = () => {
            HireLink.closeModal(false);
            setTimeout(() => showTimeline(c.id), 120);
          };
        const sc = document.getElementById("btn-sc");
        if (sc)
          sc.onclick = () => {
            HireLink.closeModal(false);
            setTimeout(() => rateScorecard(c), 120);
          };
        const adv = document.getElementById("btn-adv-from");
        if (adv)
          adv.onclick = () => {
            HireLink.closeModal(false);
            setTimeout(() => advance(c), 120);
          };
        const rej = document.getElementById("btn-rej-from");
        if (rej)
          rej.onclick = () => {
            HireLink.closeModal(false);
            setTimeout(() => rejectFlow(c), 120);
          };
      },
    });
  }

  function paint() {
    const root = document.getElementById("kanban");
    const filter = (document.getElementById("pipe-filter") || {}).value || "all";
    const scope = appsForScope();
    const chipCounts = {
      all: scope.length,
      urgent: scope.filter((a) => a.slaRemainingDays != null && a.slaRemainingDays <= 2 && !a.slaBreached).length,
      breached: scope.filter((a) => a.slaBreached).length,
      hot: scope.filter((a) => (a.matchScore || 0) >= 75).length,
      interview: scope.filter((a) => a.status === "INTERVIEW").length,
    };
    const chips = document.getElementById("pipe-chips");
    if (chips) {
      chips.querySelectorAll("button").forEach((b) => {
        const key = b.getAttribute("data-pipe") || "all";
        b.classList.toggle("is-on", key === filter);
        let badge = b.querySelector(".filter-count");
        if (!badge) {
          badge = document.createElement("span");
          badge.className = "filter-count";
          b.appendChild(badge);
        }
        badge.textContent = String(chipCounts[key] || 0);
      });
    }
    const q = ((document.getElementById("rc-search") || {}).value || "").trim().toLowerCase();
    let filtered = appsForScope();
    if (filter === "urgent") {
      filtered = filtered.filter((a) => a.slaRemainingDays != null && a.slaRemainingDays <= 2 && !a.slaBreached);
    } else if (filter === "breached") {
      filtered = filtered.filter((a) => a.slaBreached);
    } else if (filter === "hot") {
      filtered = filtered.filter((a) => (a.matchScore || 0) >= 75);
    } else if (filter === "interview") {
      filtered = filtered.filter((a) => a.status === "INTERVIEW");
    }
    if (q) {
      filtered = filtered.filter((a) =>
        ((a.candidateName || "") + " " + (a.jobTitle || "") + " " + (a.status || "") + " " + (a.candidateEmail || ""))
          .toLowerCase()
          .includes(q)
      );
    }
    const offerSel = document.getElementById("pipe-offer");
    if (offerSel) offerSel.classList.toggle("is-filtered", offerSel.value !== "all");
    refreshFunnel();
    root.innerHTML = "";
    COLUMNS.forEach((col) => {
      const cards = filtered
        .filter((a) => a.status === col.key)
        .sort((a, b) => {
          if (a.slaBreached !== b.slaBreached) return a.slaBreached ? -1 : 1;
          const sa = a.slaRemainingDays == null ? 99 : a.slaRemainingDays;
          const sb = b.slaRemainingDays == null ? 99 : b.slaRemainingDays;
          if (sa !== sb) return sa - sb;
          return (b.matchScore || 0) - (a.matchScore || 0);
        });
      const el = document.createElement("div");
      el.className = "rc-col rc-col--" + (col.tone || "neutral");
      el.innerHTML = '<div class="rc-col-head"><span></span><em></em></div><div class="rc-col-body"></div>';
      el.querySelector(".rc-col-head span").textContent = col.label;
      el.querySelector(".rc-col-head em").textContent = String(cards.length);
      const body = el.querySelector(".rc-col-body");
      if (!cards.length) {
        body.innerHTML = '<p class="rc-empty-col">Aucun dossier</p>';
      }
      cards.forEach((c, idx) => {
        const card = document.createElement("article");
        const breached = !!(c.slaBreached || (c.slaRemainingDays != null && c.slaRemainingDays < 0));
        const urgent = !breached && c.slaRemainingDays != null && c.slaRemainingDays <= 2;
        card.className = "rc-card-k" + (breached ? " is-late" : urgent ? " is-urgent" : "");
        card.style.animationDelay = Math.min(idx, 8) * 30 + "ms";
        card.tabIndex = 0;
        card.setAttribute("role", "button");
        const sla = c.slaRemainingDays;
        const slaTxt = breached ? "Retard" : sla == null ? "—" : sla === 0 ? "Dernier jour" : "sous " + sla + " j";
        const canAdvance = !!NEXT[c.status];
        const matchPct = c.matchScore != null ? Math.round(c.matchScore) : null;
        card.innerHTML =
          "<h3></h3><p></p><div class='rc-bar'><i></i></div>" +
          "<div class='rc-card-meta'><span class='badge'></span><span class='rc-pill js-sla'></span></div>" +
          "<div class='rc-card-act'>" +
          "<button type='button' class='btn btn-sm btn-soft js-view'>Dossier</button>" +
          (canAdvance ? "<button type='button' class='btn btn-sm btn-primary js-adv'>Avancer →</button>" : "") +
          "<button type='button' class='btn btn-sm btn-ghost rc-ico-only js-rej' title='Refuser' aria-label='Refuser'>✕</button></div>";
        card.querySelector("h3").textContent = c.candidateName || "Candidat";
        const scoped = selectedOfferId() != null;
        let sub = scoped ? "" : c.jobTitle || "";
        if (c.interviewType) {
          sub += (sub ? " · " : "") + (RC.INTERVIEW_TYPE_FR[c.interviewType] || c.interviewType) +
            (c.interviewAt ? " " + new Date(c.interviewAt).toLocaleDateString("fr-FR") : "");
        }
        card.querySelector("p").textContent = sub || (scoped ? "Dossier sur ce poste" : "");
        card.querySelector(".rc-bar > i").style.width = Math.min(100, c.matchScore || 0) + "%";
        const mb = card.querySelector(".badge");
        mb.className = "badge " + matchBadgeClass(matchPct);
        mb.textContent = matchPct != null ? matchPct + " % match" : "Match —";
        const pill = card.querySelector(".js-sla");
        if (sla == null && !breached) pill.remove();
        else {
          pill.textContent = "Réponse " + slaTxt;
          const sc = slaClass(sla, c.slaBreached);
          if (sc) pill.classList.add("rc-pill--" + sc);
        }
        card.onclick = () => openDossier(c);
        card.onkeydown = (e) => { if (e.key === "Enter") openDossier(c); };
        card.querySelector(".js-view").onclick = (e) => { e.stopPropagation(); openDossier(c); };
        const advBtn = card.querySelector(".js-adv");
        if (advBtn) advBtn.onclick = (e) => { e.stopPropagation(); advance(c); };
        card.querySelector(".js-rej").onclick = (e) => { e.stopPropagation(); rejectFlow(c); };
        body.appendChild(card);
      });
      root.appendChild(el);
    });
    const totalEl = document.getElementById("kanban-total");
    if (totalEl) {
      totalEl.textContent = filtered.filter((a) => COLUMNS.some((c) => c.key === a.status)).length + " dossiers";
    }
  }

  let pendingApp = new URLSearchParams(location.search).get("app");
  let loaded = false;

  function onData(apps) {
    allApps = apps || [];
    rebuildOfferSelect();
    paint();
    loaded = true;
    if (pendingApp) {
      const found = allApps.find((a) => String(a.id) === String(pendingApp));
      pendingApp = null;
      history.replaceState(null, "", location.pathname);
      if (found) openDossier(found);
      else RC.toast("Dossier introuvable", "Ce dossier n’existe plus ou n’est pas dans votre périmètre.", "warn");
    }
  }

  function load(force) {
    const root = document.getElementById("kanban");
    if (force) RC.data.invalidate();
    if (!loaded) {
      root.innerHTML = '<span class="rc-skel" style="height:240px;grid-column:1/-1"></span>';
    }
    if (!refusalCatalog.length) {
      HireLinkAPI.api("/refusal-reasons").then((r) => { refusalCatalog = r || []; }).catch(() => {});
    }
    return RC.data.pipeline(onData, (err) => {
      document.getElementById("funnel-strip").innerHTML = "";
      RC.errorState(root, err.message, () => load(true));
      root.style.display = "block";
    });
  }

  const params = new URLSearchParams(location.search);
  const offerParam = params.get("offer") || params.get("offerId");
  if (offerParam) {
    const offerSel = document.getElementById("pipe-offer");
    if (offerSel) offerSel.dataset.pending = String(offerParam);
  }

  const refreshBtn = document.getElementById("btn-refresh");
  if (refreshBtn) refreshBtn.addEventListener("click", async () => { RC.busy(refreshBtn, true); await load(true); RC.busy(refreshBtn, false); });
  const filt = document.getElementById("pipe-filter");
  const pipeChips = document.getElementById("pipe-chips");
  if (pipeChips && filt) {
    pipeChips.addEventListener("click", (e) => {
      const btn = e.target.closest("button[data-pipe]");
      if (!btn) return;
      filt.value = btn.getAttribute("data-pipe") || "all";
      paint();
    });
  }
  const offerFilt = document.getElementById("pipe-offer");
  if (offerFilt) offerFilt.addEventListener("change", paint);
  load();
})();
