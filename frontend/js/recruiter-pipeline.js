(function () {
  const RC = HireLinkApp;
  const session = RC.mount({
    active: "pipeline",
    title: "Pipeline",
    search: { placeholder: "Candidat, offre, statut…", onQuery: () => paint() },
  });
  if (!session) return;

  // Parcours positif : Reçues → Présélection → Shortlist → Entretien (fin sur HireLink).
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
    INTERVIEW: "Phase entretien : le créneau se pose depuis le dossier du candidat.",
  };

  let allApps = [];
  let refusalCatalog = [];
  let ownedOffers = [];
  let ownedOffersReady = false;
  const ACTIVE = new Set(["SUBMITTED", "SCREENING", "SHORTLISTED", "INTERVIEW"]);
  const RENDERED = new Set(["SUBMITTED", "SCREENING", "SHORTLISTED", "INTERVIEW", "OFFER", "HIRED"]);

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
    // Une offre sans dossier doit quand même être filtrable : les pages Offres et Équipe lient
    // chacune de leurs lignes au pipeline, y compris une offre qui vient d'être publiée.
    ownedOffers.forEach((o) => {
      const id = Number(o.id);
      if (!id || o.archivedAt || map.has(id)) return;
      map.set(id, { id, title: o.title || "Offre #" + id, count: 0, active: 0 });
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
    // Un lien profond vers une offre sans dossier ne peut être jugé que si /offers/mine a répondu :
    // sinon la demande reste en attente du catalogue, au lieu de retomber en silence sur « toutes ».
    if (pending && (still || ownedOffersReady)) delete sel.dataset.pending;
    sel.value = still ? prev : "all";
    sel.classList.toggle("is-filtered", sel.value !== "all");
    if (pending && !still && ownedOffersReady)
      RC.toast("Offre hors pipeline", "Aucun dossier ne correspond à cette offre dans votre périmètre.", "warn");
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
    ["REF01", "Profil non conforme aux exigences."],
    ["REF02", "Expérience insuffisante."],
    ["REF03", "Compétences techniques insuffisantes."],
    ["REF04", "Candidature incomplète."],
    ["REF05", "Poste déjà pourvu."],
    ["REF06", "Autre."],
  ];

  function refusalSelectHtml() {
    return (
      '<div class="hl-dossier-section"><h5>Motif du refus</h5>' +
      '<p class="muted" style="margin:0 0 .55rem">Le candidat voit ce motif. « Autre » peut être précisé. Le message plus bas lui est envoyé seulement si vous le rédigez.</p>' +
      '<div class="field"><label>Motif</label>' +
      '<select id="reject-reason"><option value="">— Choisir —</option>' +
      FIXED_REASONS.map((r) => '<option value="' + r[0] + '">' + esc(r[1]) + "</option>").join("") +
      "</select></div>" +
      '<div class="field" id="reject-other-wrap" hidden><label for="reject-other">Précision</label>' +
      '<textarea id="reject-other" rows="3" placeholder="Ce que le candidat doit lire"></textarea></div></div>' +
      '<div class="hl-dossier-section"><h5>Message au candidat</h5>' +
      '<p class="muted" style="margin:0 0 .55rem">Rédigez-le vous-même, ou demandez une aide IA puis modifiez librement. Ce texte part en e-mail.</p>' +
      '<div class="rc-row" style="gap:.4rem;flex-wrap:wrap;margin-bottom:.55rem">' +
      '<button type="button" class="btn btn-soft btn-sm" id="reject-ai">Aide IA — proposer un brouillon' + HireLinkCredits.costNote().outerHTML + "</button>" +
      '<button type="button" class="btn btn-ghost btn-sm" id="reject-clear">Vider</button></div>' +
      '<div class="field"><label for="reject-message">Message</label>' +
      '<textarea id="reject-message" rows="7" maxlength="4000" placeholder="Bonjour,…&#10;&#10;Nous vous remercions pour votre candidature…"></textarea></div>' +
      '<p class="muted" id="reject-ai-hint" style="margin:.35rem 0 0;font-size: var(--text-xs)">Choisissez d’abord un motif pour activer l’aide IA.</p></div>'
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
    if (!entries || !entries.length) return "";
    const rows = criteria.map((cr) => {
      const list = entries.filter((e) => e.criterionId === cr.id);
      if (!list.length) return "";
      const avg = list.reduce((t, e) => t + e.score, 0) / list.length;
      return '<div class="hl-fact"><span class="k">' + esc(cr.label) + '</span><span class="v">' + avg.toFixed(1) + " / 5 · " + RC.plural(list.length, "avis", "avis") + "</span></div>";
    }).join("");
    return rows ? '<div class="hl-dossier-section"><h5>Synthèse des évaluateurs</h5><div class="hl-fact-grid">' + rows + "</div></div>" : "";
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

  async function fetchAppCv(appId) {
    const res = await HireLinkAPI.authFetch("/applications/" + appId + "/cv");
    if (!res.ok) {
      let msg = "CV introuvable";
      try {
        msg = (await res.json()).message || msg;
      } catch (_) {}
      throw new Error(msg);
    }
    return res.blob();
  }

  let currentDossierCandidate = null;

  function showBlobPreview(title, blob, fileName) {
    const type = (blob.type || "").toLowerCase();
    const name = fileName || "fichier";
    const url = URL.createObjectURL(blob);
    const done = () => setTimeout(() => URL.revokeObjectURL(url), 1000);
    const cand = currentDossierCandidate;
    const opts = {
      title,
      url,
      downloadName: name,
      onClose: () => {
        if (cand) {
          const overlay = document.getElementById("hl-overlay");
          if (!overlay || !overlay.classList.contains("is-open")) {
            openDossier(cand);
          }
        }
      }
    };
    if (type.indexOf("pdf") >= 0 || /\.pdf$/i.test(name)) {
      opts.bodyHtml = '<iframe src="' + url + '" title="Aperçu du document" class="hl-preview-frame"></iframe>';
      return HireLink.openPreview(opts).then(done);
    }
    if (type.indexOf("text") >= 0 || /\.(txt|md)$/i.test(name)) {
      return blob.text().then((text) => {
        opts.bodyHtml = '<pre class="hl-preview-text">' + esc(String(text || "").slice(0, 12000)) + "</pre>";
        return HireLink.openPreview(opts).then(done);
      });
    }
    opts.bodyHtml = "<p><strong>" + esc(name) + "</strong> (" + Math.round(blob.size / 1024) + " Ko)</p><p class='muted'>L’aperçu intégré est disponible pour les PDF et les textes. Utilisez Télécharger pour ouvrir ce fichier.</p>";
    return HireLink.openPreview(opts).then(done);
  }

  function downloadBlob(blob, fileName) {
    const a = document.createElement("a");
    a.href = URL.createObjectURL(blob);
    a.download = fileName || "fichier";
    a.click();
    setTimeout(() => URL.revokeObjectURL(a.href), 4000);
  }

  document.addEventListener("click", (e) => {
    const preview = e.target.closest("[data-preview-cv]");
    if (preview) {
      const id = preview.getAttribute("data-preview-cv");
      const name = preview.getAttribute("data-cv-name") || "cv.pdf";
      fetchAppCv(id)
        .then((blob) => showBlobPreview("Aperçu — " + name, blob, name))
        .catch((err) => HireLink.toast("CV", err.message, "danger"));
      return;
    }
    const dl = e.target.closest("[data-download-cv]");
    if (dl) {
      const id = dl.getAttribute("data-download-cv");
      const name = dl.getAttribute("data-cv-name") || "cv.pdf";
      fetchAppCv(id)
        .then((blob) => downloadBlob(blob, name))
        .catch((err) => HireLink.toast("CV", err.message, "danger"));
      return;
    }
    const letterPrev = e.target.closest("[data-preview-letter]");
    if (letterPrev) {
      const src = document.getElementById(letterPrev.getAttribute("data-preview-letter") || "");
      const text = (src && src.textContent) || "";
      HireLink.openPreview({ title: "Aperçu — Lettre de motivation", bodyHtml: '<pre class="hl-preview-text">' + esc(text) + "</pre>" });
      return;
    }
    const letterDl = e.target.closest("[data-download-letter]");
    if (letterDl) {
      const src = document.getElementById(letterDl.getAttribute("data-download-letter") || "");
      const text = (src && src.textContent) || "";
      const name = letterDl.getAttribute("data-letter-name") || "lettre-motivation.txt";
      downloadBlob(new Blob([text], { type: "text/plain;charset=utf-8" }), name);
    }
  });

  /** Disponibilité, préavis, prétentions… saisis par le candidat dans le parcours de candidature. */
  function detailsHtml(c) {
    let d = null;
    try { d = c.applicationDetails ? JSON.parse(c.applicationDetails) : null; } catch (_) { d = null; }
    if (!d) return "";
    let when = "—";
    if (d.availability === "NOW") when = "Immédiatement";
    else if (d.availableFrom) { const p = String(d.availableFrom).split("-"); when = "À partir du " + new Date(+p[0], +p[1] - 1, +p[2]).toLocaleDateString("fr-FR", { day: "numeric", month: "long", year: "numeric" }); }
    return '<div class="hl-dossier-section"><h5>Disponibilité et attentes</h5><div class="hl-fact-grid">' +
      fact("Disponible", when) +
      fact("Préavis", d.notice || "—") +
      fact("Prétentions", d.salary ? Number(d.salary).toLocaleString("fr-FR") + " MAD / mois" : "Non communiquées") +
      fact("Mode de travail", d.workMode || "—") +
      fact("Mobilité", d.relocation ? "Prêt(e) à déménager" : "Non précisée") +
      fact("Source", d.source || "—") +
      "</div></div>";
  }

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
          '" title="Compatibilité (détail dans le dossier)">' +
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
        "</div>" +
        (c.matchBreakdown ? '<div style="margin-top:.75rem" data-match-slot></div>' : "") +
        "</div>" +
        '<div class="hl-dossier-section"><h5>2. Profil</h5><div class="hl-fact-grid">' +
        fact("Nom", c.candidateName || "—") +
        fact("E-mail", c.candidateEmail || "—") +
        "</div>" +
        (c.candidateSkills
          ? "<div style='margin-top:.65rem' data-sk-slot></div>"
          : "<p class='muted'>Aucune compétence renseignée.</p>") +
        "</div>" +
        detailsHtml(c) +
        '<div class="hl-dossier-section"><h5>3. CV</h5><article class="doc-card"><strong>' +
        esc(cvTxt) +
        "</strong><p class='muted'>" +
        esc(cvOrigin) +
        "</p>" +
        (c.hasCvFile
          ? '<div class="rc-row" style="margin-top:.55rem;gap:.4rem;flex-wrap:wrap">' +
            '<button type="button" class="btn btn-soft btn-sm" data-preview-cv="' +
            c.id +
            '" data-cv-name="' +
            esc(c.cvFileName || "cv.pdf") +
            '">Aperçu</button>' +
            '<button type="button" class="btn btn-ghost btn-sm" data-download-cv="' +
            c.id +
            '" data-cv-name="' +
            esc(c.cvFileName || "cv.pdf") +
            '">Télécharger</button></div>'
          : "") +
        "</article></div>";
      if (!opts.hideLetter) {
        const letterId = "hl-letter-" + c.id;
        const letterName =
          "lettre-" + String(c.candidateName || "candidat").replace(/\s+/g, "-").toLowerCase() + ".txt";
        html +=
          '<div class="hl-dossier-section"><h5>4. Lettre de motivation</h5>' +
          (c.coverLetter
            ? letterFile
              ? '<article class="doc-card"><strong>' +
                esc(c.coverLetter) +
                "</strong><p class='muted'>Fichier joint (nom enregistré)</p></article>"
              : '<p class="hl-letter-box" id="' +
                letterId +
                '">' +
                esc(c.coverLetter) +
                "</p>" +
                '<div class="rc-row" style="margin-top:.55rem;gap:.4rem;flex-wrap:wrap">' +
                '<button type="button" class="btn btn-soft btn-sm" data-preview-letter="' +
                letterId +
                '">Aperçu</button>' +
                '<button type="button" class="btn btn-ghost btn-sm" data-download-letter="' +
                letterId +
                '" data-letter-name="' +
                esc(letterName) +
                '">Télécharger</button></div>'
            : "<p class='muted'>Aucune lettre jointe.</p>") +
          "</div>";
      }
      if (opts.interviewSlot && c.interviewAt) {
        html += '<div class="hl-dossier-section"><h5>5. Entretien</h5><div data-iv-slot></div></div>';
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

  const CHECK_IDS = ["chk-id", "chk-cv", "chk-sla"];
  function checkedCount() {
    return CHECK_IDS.filter((id) => { const b = document.getElementById(id); return b && b.checked; }).length;
  }
  function checkOn(id) {
    const b = document.getElementById(id);
    return !!(b && b.checked);
  }
  /** Le badge « n / 3 » et le bouton suivre suivent les cases réellement cochées ; rien n'est pré-affirmé au serveur. */
  function syncChecks(api) {
    const n = checkedCount();
    const badge = document.getElementById("chk-count");
    if (badge) badge.textContent = n + " / " + CHECK_IDS.length;
    const ok = n === CHECK_IDS.length;
    if (api && api.setConfirmEnabled) api.setConfirmEnabled(ok);
    return ok;
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

  async function advance(c) {
    const next = NEXT[c.status];
    if (!next) {
      HireLink.toast("Dossier clôturé", "Cette candidature n’a plus d’étape suivante.", "info");
      return;
    }
    const leavingInterview = c.status === "INTERVIEW";
    const body =
      '<div class="hl-step-banner"><div><strong>' +
      esc(RC.STATUS_FR[c.status] || c.status) + " → " + esc(RC.STATUS_FR[next] || next) +
      "</strong>" + esc(STEP_HINT[next] || "") + "</div></div>" +
      dossierHtml(c, { hideLetter: true, showNote: false, compact: true }) +
      checksHtml(c, "advance") +
      (next === "INTERVIEW"
        ? '<p class="hl-step-where">' +
          (c.interviewAt
            ? "Créneau déjà posé : " + esc(HireLinkInterview.summary(c)) + " — le candidat reçoit la convocation."
            : "Aucun créneau pour l’instant : le bouton « Planifier l’entretien » du dossier enverra la convocation.") +
          "</p>"
        : "") +
      (leavingInterview
        ? '<details class="hl-opt-block" open><summary>Retour d’entretien <small>facultatif</small></summary>' +
          '<div class="hl-feedback-pick" role="radiogroup" aria-label="Impression générale">' +
          [["++", "Très favorable"], ["+", "Favorable"], ["=", "Mitigé"], ["-", "Réservé"]]
            .map((x) => '<label><input type="radio" name="adv-feel" value="' + esc(x[1]) + '"/><span>' + x[1] + "</span></label>").join("") +
          "</div>" +
          '<textarea id="adv-note" rows="3" aria-label="Retour d’entretien écrit" placeholder="Ce qui a convaincu, points à vérifier, suite proposée…"></textarea></details>'
        : '<details class="hl-opt-block"><summary>Ajouter une note interne <small>facultatif</small></summary>' +
          '<textarea id="adv-note" rows="2" aria-label="Note interne" placeholder="Visible uniquement par votre équipe"></textarea></details>');

    const ok = await HireLink.openDrawer({
      title: "Avancer · " + c.candidateName,
      description: "Confirmez le passage à l’étape suivante",
      bodyHtml: body,
      confirmLabel: "Confirmer l’étape",
      confirmDisabled: true,
      wide: true,
      onReady: (api) => {
        syncChecks(api);
        api.root.addEventListener("change", () => syncChecks(api));
      },
    });
    if (!ok) return;

    const payload = {
      status: next,
      advanceNote: null,
      confirmIdentity: checkOn("chk-id"),
      confirmCvReviewed: checkOn("chk-cv"),
      confirmSlaAware: checkOn("chk-sla"),
    };
    const parts = [];
    const feel = document.querySelector('input[name="adv-feel"]:checked');
    if (feel) parts.push("Retour d’entretien : " + feel.value);
    const rawNote = ((document.getElementById("adv-note") || {}).value || "").trim();
    if (rawNote) parts.push(rawNote);
    if (parts.length) payload.advanceNote = parts.join("\n");

    try {
      await HireLinkAPI.api("/applications/" + c.id + "/status", { method: "PATCH", body: payload });
      HireLink.toast(
        "Étape validée",
        next === "INTERVIEW" && !c.interviewAt
          ? c.candidateName + " → Entretien. Planifiez le créneau dans l’Agenda."
          : c.candidateName + " → " + (RC.STATUS_FR[next] || next),
        "ok"
      );
      load(true);
    } catch (err) {
      HireLink.toast("Erreur", err.message, "danger");
    }
  }

  async function rejectFlow(c) {
    const body =
      dossierHtml(c, { refuse: true, hideLetter: true, showNote: false }) +
      refusalSelectHtml() +
      checksHtml(c, "reject");

    const ok = await HireLink.openDrawer({
      title: "Refuser · " + c.candidateName,
      description: "Motif catalogue + message e-mail (modifiable).",
      bodyHtml: body,
      confirmLabel: "Confirmer le refus",
      danger: true,
      confirmDisabled: true,
      wide: true,
      onReady: (api) => {
        const reasonEl = () => document.getElementById("reject-reason");
        const msgEl = () => document.getElementById("reject-message");
        const hint = document.getElementById("reject-ai-hint");
        const refresh = () => {
          const reason = ((reasonEl() || {}).value || "").trim();
          const otherWrap = document.getElementById("reject-other-wrap");
          if (otherWrap) otherWrap.hidden = reason !== "REF06";
          const other = ((document.getElementById("reject-other") || {}).value || "").trim();
          const motifOk = !!reason;
          api.setConfirmEnabled(motifOk && syncChecks());
          if (hint) {
            hint.textContent = reason
              ? "Vous pouvez rédiger le message ou cliquer « Aide IA », puis modifier le texte."
              : "Choisissez d’abord un motif pour activer l’aide IA.";
          }
        };
        api.root.addEventListener("change", refresh);
        api.root.addEventListener("input", refresh);
        const clearBtn = document.getElementById("reject-clear");
        if (clearBtn) {
          clearBtn.onclick = () => {
            const ta = msgEl();
            if (ta) ta.value = "";
            refresh();
          };
        }
        const aiBtn = document.getElementById("reject-ai");
        if (aiBtn) {
          aiBtn.onclick = async () => {
            const code = ((reasonEl() || {}).value || "").trim();
            if (!code) {
              HireLink.toast("Motif requis", "Choisissez un motif avant l’aide IA.", "warn");
              return;
            }
            const label =
              code === "REF06"
                ? ((document.getElementById("reject-other") || {}).value || "").trim() || "Autre"
                : (FIXED_REASONS.find((r) => r[0] === code) || [, code])[1];
            if (!HireLinkCredits.preflight()) return;
            RC.busy(aiBtn, true);
            try {
              const data = await HireLinkAPI.api("/ai/refusal-draft", {
                method: "POST",
                body: {
                  reasonCode: code,
                  reasonLabel: label,
                  jobTitle: c.jobTitle || "",
                },
                timeoutMs: 60000,
              });
              const ta = msgEl();
              if (ta) {
                ta.value = String(data.content || "").trim();
                ta.focus();
              }
              HireLink.toast("Brouillon prêt", "Modifiez le texte avant de confirmer.", "ok");
              HireLinkCredits.spend();
            } catch (err) {
              if (!HireLinkCredits.guard(err)) HireLink.toast("Aide IA", err.message, "danger");
            } finally {
              RC.busy(aiBtn, false);
              refresh();
            }
          };
        }
        refresh();
      },
    });
    if (!ok) return;
    let reason = (document.getElementById("reject-reason") || {}).value || "";
    if (!reason.trim()) {
      HireLink.toast("Motif requis", "Choisissez un motif", "warn");
      return;
    }
    if (reason === "REF06") {
      const other = ((document.getElementById("reject-other") || {}).value || "").trim();
      if (other) reason = "REF06:" + other;
    }
    const message = ((document.getElementById("reject-message") || {}).value || "").trim();
    try {
      await HireLinkAPI.api("/applications/" + c.id + "/status", {
        method: "PATCH",
        body: {
          status: "REJECTED",
          rejectionReason: reason,
          advanceNote: message || null,
          confirmIdentity: checkOn("chk-id"),
          confirmCvReviewed: checkOn("chk-cv"),
          confirmSlaAware: checkOn("chk-sla"),
        },
      });
      HireLink.toast(
        "Candidature refusée",
        message ? "Motif enregistré · message envoyé au candidat" : "Motif enregistré · e-mail type envoyé",
        "ok"
      );
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
        "<h5>Responsable du poste assigné</h5>" +
        '<div class="rc-row"><select id="hm-select" class="rc-select" style="flex:1"><option value="">— Aucun —</option>' +
        eligible.map((m) => '<option value="' + m.userId + '"' + (Number(c.assignedHmUserId) === Number(m.userId) ? " selected" : "") + ">" +
          esc(m.fullName || m.email) + " · " + esc(RC.ROLE_FR[m.orgRole] || m.orgRole) + "</option>").join("") +
        '</select><button type="button" class="btn btn-soft btn-sm" id="hm-save">Assigner</button></div>' +
        '<p class="muted" style="margin:.4rem 0 0;font-size: var(--text-xs)">Le Responsable du poste se connecte au même espace recruteur (dossiers filtrés), avec notification in-app et e-mail pour les entretiens — pas besoin d’un espace séparé.</p>';
      document.getElementById("hm-save").onclick = async (e) => {
        const v = document.getElementById("hm-select").value;
        RC.busy(e.currentTarget, true);
        try {
          const updated = await HireLinkAPI.api("/applications/" + c.id + "/assign-hm", { method: "PATCH", body: { hmUserId: v ? Number(v) : null } });
          c.assignedHmUserId = updated.assignedHmUserId;
          RC.data.invalidate();
          RC.toast("Assignation", v ? "Responsable du poste assigné" : "Assignation retirée", "ok");
        } catch (err) {
          RC.toast("Assignation", err.message, "danger");
        } finally {
          RC.busy(e.currentTarget, false);
        }
      };
    });
  }

  async function openDossier(c) {
    currentDossierCandidate = c;
    const canAdvance = !!NEXT[c.status];
    await HireLink.openDrawer({
      title: "Dossier · " + c.candidateName,
      description: (c.jobTitle || "") + " · " + (RC.STATUS_FR[c.status] || c.status),
      bodyHtml:
        dossierHtml(c, { interviewSlot: true }) +
        '<div class="hl-drawer-actions-inline">' +
        '<button type="button" class="btn btn-soft btn-sm" id="btn-tl">Historique</button>' +
        (canAdvance
          ? '<button type="button" class="btn btn-primary btn-sm" id="btn-adv-from">Avancer</button>'
          : "") +
        (c.status === "INTERVIEW" && !c.interviewAt
          ? '<button type="button" class="btn btn-secondary btn-sm" id="btn-plan-from">Planifier l’entretien</button>'
          : "") +
        '<button type="button" class="btn btn-ghost btn-sm" id="btn-rej-from">Refuser</button>' +
        "</div>" +
        (RC.canManage() ? '<div class="hl-dossier-section" id="hm-slot"></div>' : ""),
      confirmLabel: "Fermer",
      hideCancel: true,
      wide: true,
      onReady: () => {
        mountHmAssign(c);
        const skSlot = document.querySelector("[data-sk-slot]");
        if (skSlot) HireLinkSkills.renderBoxes(skSlot, c.candidateSkills);
        const mSlot = document.querySelector("[data-match-slot]");
        if (mSlot && window.HireLinkMatch) {
          const p = HireLinkMatch.panel(c.matchBreakdown, { audience: "recruiter", compact: true });
          if (p) mSlot.appendChild(p);
        }
        const ivSlot = document.querySelector("[data-iv-slot]");
        if (ivSlot) {
          // Le créneau se gère dans l'Agenda : le pipeline affiche, il ne planifie pas.
          ivSlot.appendChild(HireLinkInterview.card(c, { role: "readonly" }));
          ivSlot.insertAdjacentHTML("beforeend", '<a class="btn btn-secondary btn-sm" href="recruiter-agenda.html">Ouvrir dans l’Agenda</a>');
        }
        const tl = document.getElementById("btn-tl");
        if (tl)
          tl.onclick = () => {
            HireLink.closeModal(false);
            setTimeout(() => showTimeline(c.id), 120);
          };
        const planBtn = document.getElementById("btn-plan-from");
        if (planBtn)
          planBtn.onclick = () => {
            HireLink.closeModal(false);
            setTimeout(() => HireLinkInterview.plan(c, { onDone: () => load(true) }), 120);
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
    // « loaded » et non « allApps.length » : un recruteur sans aucun dossier doit quand même
    // voir l'offre qu'il a filtrée dans l'URL et dans la puce de contexte.
    if (loaded || allApps.length) { syncPipeUrl(); paintCtxChip(); }
    const root = document.getElementById("kanban");
    const filter = (document.getElementById("pipe-filter") || {}).value || "all";
    const scope = appsForScope().filter((a) => RENDERED.has(a.status));
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
    const q = ((document.getElementById("rc-search") || {}).value || "").trim();
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
    filtered = HireLinkSearch.rank(filtered, q, [
      { get: (a) => a.candidateName, weight: 3 }, { get: (a) => a.jobTitle, weight: 2 },
      { get: (a) => RC.STATUS_FR[a.status], weight: 1 }, { get: (a) => a.candidateEmail, weight: 1 },
    ]);
    const offerSel = document.getElementById("pipe-offer");
    if (offerSel) offerSel.classList.toggle("is-filtered", offerSel.value !== "all");
    refreshFunnel();
    root.innerHTML = "";
    COLUMNS.forEach((col) => {
      const cards = filtered
        .filter((a) =>
          col.key === "INTERVIEW"
            ? a.status === "INTERVIEW" || a.status === "OFFER" || a.status === "HIRED"
            : a.status === col.key
        )
        .sort((a, b) => {
          if (a.slaBreached !== b.slaBreached) return a.slaBreached ? -1 : 1;
          const sa = a.slaRemainingDays == null ? 99 : a.slaRemainingDays;
          const sb = b.slaRemainingDays == null ? 99 : b.slaRemainingDays;
          if (sa !== sb) return sa - sb;
          return (b.matchScore || 0) - (a.matchScore || 0);
        });
      const el = document.createElement("div");
      el.className = "rc-col rc-col--" + (col.tone || "neutral");
      el.dataset.focus = "stage:" + col.key;
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
        card.dataset.focus = "application:" + c.id;
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
          (canAdvance ? "<button type='button' class='btn btn-sm btn-primary js-adv'>Avancer →</button><button type='button' class='btn btn-sm btn-soft js-rate'>Évaluer</button>" : "") +
          "<button type='button' class='btn btn-sm btn-ghost rc-ico-only js-rej' title='Refuser' aria-label='Refuser'>✕</button></div>";
        card.querySelector("h3").textContent = c.candidateName || "Candidat";
        const scoped = selectedOfferId() != null;
        let sub = scoped ? "" : c.jobTitle || "";
        if (c.interviewType && c.status === "INTERVIEW") {
          sub += (sub ? " · " : "") + (HireLinkInterview.TYPE_FR[c.interviewType] || c.interviewType) +
            (c.interviewAt ? " " + new Date(c.interviewAt).toLocaleDateString("fr-FR") : "") +
            (c.interviewStatus === "CANCELLED" ? " (annulé)" : "");
        } else if (c.status === "INTERVIEW") {
          sub += (sub ? " · " : "") + "Entretien à planifier";
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
        const rateBtn = card.querySelector(".js-rate");
        if (rateBtn) rateBtn.onclick = (e) => { e.stopPropagation(); rateScorecard(c); };
        card.querySelector(".js-rej").onclick = (e) => { e.stopPropagation(); rejectFlow(c); };
        body.appendChild(card);
      });
      root.appendChild(el);
    });
    HireLinkSearch.mark(root, q);
    const totalEl = document.getElementById("kanban-total");
    if (totalEl) {
      totalEl.textContent = filtered.filter((a) => COLUMNS.some((c) => c.key === a.status)).length + " dossiers";
    }
  }

  // Nouvelle candidature, entretien planifié ou reporté… : le tableau se met à jour dès la notification.
  document.addEventListener("hirelink:push", () => load(true));

  let pendingApp = new URLSearchParams(location.search).get("app");
  let loaded = false;

  const hadFocus = /(^|[?&])focus=/.test(location.search);
  function syncPipeUrl() {
    const u = new URL(location.href);
    const oid = selectedOfferId();
    if (oid != null && !Number.isNaN(oid)) u.searchParams.set("offer", oid); else u.searchParams.delete("offer");
    u.searchParams.delete("offerId");
    const f = (document.getElementById("pipe-filter") || {}).value || "all";
    if (f !== "all") u.searchParams.set("view", f); else u.searchParams.delete("view");
    history.replaceState(history.state, "", u.pathname + u.search + u.hash);
  }
  function paintCtxChip() {
    let box = document.getElementById("pipe-ctx");
    const sel = document.getElementById("pipe-offer");
    if (!box && sel) {
      box = document.createElement("div");
      box.id = "pipe-ctx"; box.className = "hl-ctx-row";
      const anchor = document.querySelector(".rc-head") || sel.parentNode;
      anchor.insertAdjacentElement("afterend", box);
    }
    if (!box) return;
    box.replaceChildren();
    const oid = selectedOfferId();
    if (oid == null || Number.isNaN(oid)) return;
    const o = offerCatalog().find((x) => x.id === oid);
    const chip = document.createElement("button");
    chip.type = "button"; chip.className = "hl-ctx-chip";
    chip.setAttribute("aria-label", "Retirer le filtre offre");
    chip.innerHTML = '<span>Offre : <b></b></span><i aria-hidden="true">×</i>';
    chip.querySelector("b").textContent = o ? o.title : "#" + oid;
    chip.addEventListener("click", () => { sel.value = "all"; paint(); });
    box.appendChild(chip);
  }
  document.addEventListener("hirelink:focus-request", (e) => {
    const k = String((e.detail && e.detail.key) || "");
    if (!/^application:/.test(k)) return;
    const appId = k.split(":")[1];
    const a = allApps.find((x) => String(x.id) === appId);
    const sel = document.getElementById("pipe-offer");
    let needRepaint = false;
    if (sel && sel.value !== "all") { sel.value = "all"; needRepaint = true; }
    const qInput = document.getElementById("rc-search");
    if (qInput && qInput.value) { qInput.value = ""; needRepaint = true; }
    const fInput = document.getElementById("pipe-filter");
    if (fInput && fInput.value !== "all") { fInput.value = "all"; needRepaint = true; }
    if (needRepaint) paint();
    if (a) {
      setTimeout(() => {
        const card = document.querySelector('[data-focus~="application:' + a.id + '"]');
        if (card && card.getClientRects().length) {
          card.scrollIntoView({ behavior: "smooth", block: "center" });
          RC.spotlight(card, 5500);
        } else {
          openDossier(a);
          document.dispatchEvent(new CustomEvent("hirelink:focus-handled", { detail: { key: k } }));
        }
      }, 150);
    }
  });

  function onData(apps) {
    allApps = apps || [];
    loaded = true;
    rebuildOfferSelect();
    paint();
    if (pendingApp) {
      const found = allApps.find((a) => String(a.id) === String(pendingApp));
      pendingApp = null;
      const u = new URL(location.href); u.searchParams.delete("app");
      history.replaceState(history.state, "", u.pathname + u.search + u.hash);
      if (found) {
        const card = document.querySelector('[data-focus="application:' + found.id + '"]');
        if (card) {
          card.scrollIntoView({ behavior: "smooth", block: "center" });
          RC.spotlight(card, 5500);
        }
        setTimeout(() => openDossier(found), hadFocus ? 800 : 0);
      }
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
    if (force || !ownedOffersReady) {
      RC.data.offers(
        (rows) => {
          ownedOffers = rows || [];
          ownedOffersReady = true;
          if (!loaded) return;
          const sel = document.getElementById("pipe-offer");
          const before = sel ? sel.value : "all";
          rebuildOfferSelect();
          if (sel && sel.value !== before) paint();
        },
        () => { ownedOffersReady = true; }
      );
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
  const viewParam = params.get("view");
  if (viewParam && filt && Array.from(filt.options).some((o) => o.value === viewParam)) filt.value = viewParam;
  load();
})();
