(function () {
  const session = HireLinkShell.mountShell({ roles: ["RECRUTEUR"], hello: false });
  if (!session) return;
  if (window.HireLinkProduct) HireLinkProduct.mountPremiumBar("recruiter");

  // Périmètre : fin positive à INTERVIEW (pas d'étape Offre / Accepté).
  const COLUMNS = [
    { key: "SUBMITTED", label: "Soumises" },
    { key: "SCREENING", label: "Présélection" },
    { key: "SHORTLISTED", label: "Shortlist" },
    { key: "INTERVIEW", label: "Entretien (fin)" },
  ];
  const NEXT = {
    SUBMITTED: "SCREENING",
    SCREENING: "SHORTLISTED",
    SHORTLISTED: "INTERVIEW",
  };
  const STEP_HINT = {
    SCREENING: "Présélection : CV et compétences vs fiche de poste.",
    SHORTLISTED: "Shortlist : candidat retenu pour échange humain.",
    INTERVIEW: "Entretien : dernière étape gérée. Ensuite : refus possible, pas d'offre in-app.",
  };

  let allApps = [];
  let refusalCatalog = [];

  function slaClass(days, breached) {
    if (breached || (days != null && days < 0)) return "danger";
    if (days != null && days <= 2) return "warn";
    return "";
  }

  function esc(s) {
    return String(s == null ? "" : s)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;");
  }

  function refusalSelectHtml() {
    if (!refusalCatalog.length) {
      return (
        '<div class="field"><label>Motif de refus (catalogue) *</label>' +
        '<textarea id="reject-reason" rows="2" placeholder="Code catalogue indisponible"></textarea></div>'
      );
    }
    return (
      '<div class="field"><label>Motif de refus (catalogue R04) *</label>' +
      '<select id="reject-reason"><option value="">— Choisir —</option>' +
      refusalCatalog
        .map((r) => '<option value="' + esc(r.code) + '">' + esc(r.label) + " (" + esc(r.code) + ")</option>")
        .join("") +
      "</select></div>" +
      '<div class="row" style="gap:0.5rem;margin-top:0.5rem;flex-wrap:wrap">' +
      '<button type="button" class="btn btn-soft btn-sm" id="btn-ai-refuse">Brouillon IA (R15)</button>' +
      '<span class="muted" style="font-size:0.75rem">Motif + intitulé poste uniquement — pas de PII</span></div>' +
      '<div id="ai-refuse-box" class="ai-result" style="display:none;margin-top:0.5rem;max-height:140px"></div>'
    );
  }

  async function loadFunnel() {
    const el = document.getElementById("funnel-strip");
    if (!el) return;
    try {
      const f = await HireLinkAPI.api("/growth/pipeline-funnel");
      const by = f.byStatus || {};
      const nudge = (f.growth && f.growth.nudge) || "";
      el.innerHTML =
        '<div class="row between" style="flex-wrap:wrap;gap:0.75rem;align-items:flex-start">' +
        '<div style="display:flex;flex-wrap:wrap;gap:0.65rem">' +
        metric("Actifs", f.active) +
        metric("SLA ⚠", f.slaBreached) +
        metric("Hot ≥75%", f.hotMatches) +
        metric("→ Entretien", (f.conversionToInterviewPct || 0) + "%") +
        metric("Hire rate", (f.hireRatePct || 0) + "%") +
        "</div>" +
        '<div class="muted" style="font-size:0.78rem;max-width:28rem;line-height:1.4">' +
        esc((f.growth && f.growth.hint) || "") +
        (nudge ? "<br/><strong style='color:var(--rose,#7a2032)'>" + esc(nudge) + "</strong>" : "") +
        "</div></div>" +
        '<div class="row" style="gap:0.35rem;margin-top:0.65rem;flex-wrap:wrap;font-size:0.72rem">' +
        ["SUBMITTED", "SCREENING", "SHORTLISTED", "INTERVIEW", "REJECTED", "WITHDRAWN"]
          .map((k) => '<span class="badge badge-chrome">' + k + " " + (by[k] || 0) + "</span>")
          .join("") +
        "</div>";
    } catch (err) {
      el.innerHTML = '<p class="muted" style="margin:0;font-size:0.8rem">' + esc(err.message) + "</p>";
    }
  }

  function metric(label, val) {
    return (
      '<div style="min-width:4.5rem"><div class="muted" style="font-size:0.65rem;text-transform:uppercase;letter-spacing:.04em">' +
      esc(label) +
      '</div><div style="font-weight:700;font-size:1.15rem">' +
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
          : "<ol>" +
            rows
              .map(
                (h) =>
                  "<li><strong>" +
                  esc(h.from || "—") +
                  " → " +
                  esc(h.to) +
                  "</strong> · " +
                  esc(h.at) +
                  (h.payload ? "<br/><code style='font-size:0.75rem'>" + esc(h.payload) + "</code>" : "") +
                  "</li>"
              )
              .join("") +
            "</ol>";
      await HireLink.openModal({
        title: "Historique (F13)",
        description: "Append-only — non modifiable (R12)",
        bodyHtml: html,
        confirmLabel: "Fermer",
        hideCancel: true,
        large: true,
      });
    } catch (err) {
      HireLink.toast("Timeline", err.message, "danger");
    }
  }

  async function rateScorecard(c) {
    try {
      let criteria = await HireLinkAPI.api("/offers/" + c.jobOfferId + "/scorecard-criteria");
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
        '<p class="muted">Scorecard F12 — notes 1 à 5</p>' +
        criteria
          .map(
            (cr) =>
              '<div class="field"><label>' +
              esc(cr.label) +
              (cr.veto ? " (veto)" : "") +
              '</label><input type="number" min="1" max="5" data-crit="' +
              cr.id +
              '" class="sc-score" value="3" /></div>'
          )
          .join("");
      const ok = await HireLink.openModal({
        title: "Noter " + c.candidateName,
        description: c.jobTitle,
        bodyHtml: body,
        confirmLabel: "Enregistrer",
        large: true,
      });
      if (!ok) return;
      const inputs = document.querySelectorAll(".sc-score");
      for (const inp of inputs) {
        await HireLinkAPI.api("/applications/" + c.id + "/scorecard", {
          method: "POST",
          body: {
            criterionId: Number(inp.getAttribute("data-crit")),
            score: Number(inp.value) || 3,
            comment: null,
          },
        });
      }
      HireLink.toast("Scorecard", "Notes enregistrées", "ok");
    } catch (err) {
      HireLink.toast("Scorecard", err.message, "danger");
    }
  }

  function dossierHtml(c) {
    return (
      '<div class="hl-review-list" style="margin-bottom:0.85rem">' +
      "<div><strong>Candidat</strong><span>" +
      esc(c.candidateName) +
      "</span></div>" +
      "<div><strong>Email</strong><span>" +
      esc(c.candidateEmail || "—") +
      "</span></div>" +
      "<div><strong>Poste</strong><span>" +
      esc(c.jobTitle) +
      "</span></div>" +
      "<div><strong>Match</strong><span>" +
      (c.matchScore != null ? Math.round(c.matchScore) + "%" : "—") +
      "</span></div>" +
      "<div><strong>CV</strong><span>" +
      esc((c.cvSource || "") + (c.cvFileName ? " · " + c.cvFileName : " —")) +
      "</span></div>" +
      "<div><strong>Lettre</strong><span>" +
      (c.coverLetter ? "Oui (" + c.coverLetter.length + " car.)" : "Non") +
      "</span></div>" +
      "<div><strong>Photo</strong><span>" +
      esc(c.photoFileName || "Non") +
      "</span></div>" +
      "<div><strong>SLA restant</strong><span>" +
      (c.slaBreached
        ? "Dépassé"
        : c.slaRemainingDays == null
          ? "—"
          : c.slaRemainingDays + " j") +
      "</span></div>" +
      (c.candidateSkills
        ? "<div><strong>Skills</strong><span>" + esc(c.candidateSkills) + "</span></div>"
        : "") +
      (c.interviewType
        ? "<div><strong>Entretien</strong><span>" +
          esc(c.interviewType) +
          (c.interviewAt ? " · " + new Date(c.interviewAt).toLocaleString("fr-FR") : "") +
          "</span></div>"
        : "") +
      "</div>"
    );
  }

  function checksHtml() {
    return (
      '<div class="field" style="gap:0.45rem">' +
      '<label class="row" style="align-items:flex-start;gap:0.5rem"><input type="checkbox" id="chk-id" /> ' +
      "<span>Je confirme l’identité et les coordonnées du candidat</span></label>" +
      '<label class="row" style="align-items:flex-start;gap:0.5rem"><input type="checkbox" id="chk-cv" /> ' +
      "<span>J’ai relu le CV / dossier (lettre &amp; pièces)</span></label>" +
      '<label class="row" style="align-items:flex-start;gap:0.5rem"><input type="checkbox" id="chk-sla" /> ' +
      "<span>Je tiens compte du SLA de réponse de l’offre</span></label>" +
      "</div>"
    );
  }

  async function advance(c) {
    const next = NEXT[c.status];
    if (!next) {
      HireLink.toast("Fin", "Étape terminale", "info");
      return;
    }
    const needInterview = next === "INTERVIEW";
    const needOfferNote = next === "OFFER";
    const body =
      dossierHtml(c) +
      '<p class="muted" style="margin:0 0 0.65rem">' +
      esc(STEP_HINT[next] || "") +
      "</p>" +
      checksHtml() +
      '<div class="field" style="margin-top:0.75rem"><label>Note RH interne' +
      (needOfferNote ? " *" : "") +
      "</label>" +
      '<textarea id="adv-note" rows="2" placeholder="' +
      (needOfferNote
        ? "Décision d’offre obligatoire (pourquoi ce profil)…"
        : "Décision, points à vérifier…") +
      '"></textarea></div>' +
      '<div class="row" style="gap:0.75rem;flex-wrap:wrap;margin-top:0.65rem">' +
      '<div class="field" style="flex:1;min-width:120px"><label>Score tech (1–5)</label>' +
      '<input type="number" id="score-tech" min="1" max="5" placeholder="—" /></div>' +
      '<div class="field" style="flex:1;min-width:120px"><label>Score soft (1–5)</label>' +
      '<input type="number" id="score-soft" min="1" max="5" placeholder="—" /></div>' +
      '<div class="field" style="flex:1;min-width:120px"><label>Culture fit (1–5)</label>' +
      '<input type="number" id="score-culture" min="1" max="5" placeholder="—" /></div>' +
      "</div>" +
      (needInterview
        ? '<div class="stack" style="margin-top:0.75rem;padding-top:0.75rem;border-top:1px solid var(--line)">' +
          '<div class="row between" style="align-items:center"><p class="muted" style="margin:0;font-size:0.8rem;font-weight:700;text-transform:uppercase;letter-spacing:.05em">Organisation entretien</p>' +
          '<button type="button" class="btn btn-soft btn-sm" id="btn-ai-kit">Kit IA entretien</button></div>' +
          '<div id="ai-kit-box" class="ai-result" style="display:none;margin:0.5rem 0;max-height:120px"></div>' +
          '<div class="field"><label>Type *</label><select id="int-type">' +
          '<option value="">— Choisir —</option>' +
          '<option value="PHONE">Téléphone</option>' +
          '<option value="VISIO">Visio</option>' +
          '<option value="ONSITE">Présentiel</option>' +
          '<option value="ASYNC">Asynchrone (vidéo / test)</option>' +
          "</select></div>" +
          '<div class="field"><label>Date &amp; heure *</label><input type="datetime-local" id="int-at" /></div>' +
          '<div class="field"><label>Lieu / lien</label><input id="int-loc" placeholder="Bureau Casablanca · Meet · Zoom…" /></div>' +
          '<div class="field"><label>Brief candidat (notes)</label><textarea id="int-notes" rows="2" placeholder="Interlocuteurs, durée, livrables…"></textarea></div>' +
          "</div>"
        : "");

    const okPromise = HireLink.openModal({
      title: "Avancer · " + c.candidateName,
      description: c.status + " → " + next + " · vérifications obligatoires",
      bodyHtml: body,
      confirmLabel: "Confirmer l’étape",
      large: true,
    });

    setTimeout(() => {
      const kitBtn = document.getElementById("btn-ai-kit");
      if (!kitBtn) return;
      kitBtn.onclick = async (e) => {
        e.preventDefault();
        e.stopPropagation();
        try {
          const r = await HireLinkAPI.api("/ai/interview-kit", {
            method: "POST",
            body: { jobTitle: c.jobTitle, skills: c.candidateSkills, premium: true },
          });
          const box = document.getElementById("ai-kit-box");
          if (box) {
            box.style.display = "block";
            box.textContent = r.content || (r.questions || []).join("\n");
          }
          HireLink.toast("Kit IA", "Proposition — le manager décide (R07)", "ok");
        } catch (err) {
          HireLink.toast("IA", err.message, "warn");
        }
      };
    }, 80);

    const ok = await okPromise;
    if (!ok) return;

    const confirmIdentity = !!(document.getElementById("chk-id") || {}).checked;
    const confirmCvReviewed = !!(document.getElementById("chk-cv") || {}).checked;
    const confirmSlaAware = !!(document.getElementById("chk-sla") || {}).checked;
    if (!confirmIdentity || !confirmCvReviewed || !confirmSlaAware) {
      HireLink.toast("Bloqué", "Cochez les 3 vérifications de sécurité", "warn");
      return;
    }

    const payload = {
      status: next,
      advanceNote: null,
      confirmIdentity,
      confirmCvReviewed,
      confirmSlaAware,
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
    if (needOfferNote && !rawNote) {
      HireLink.toast("Offre", "Note de décision obligatoire", "warn");
      return;
    }
    if (noteParts.length) payload.advanceNote = noteParts.join("\n");

    if (needInterview) {
      const type = (document.getElementById("int-type") || {}).value || "";
      const atLocal = (document.getElementById("int-at") || {}).value || "";
      if (!type || !atLocal) {
        HireLink.toast("Entretien", "Type et date/heure obligatoires", "warn");
        return;
      }
      payload.interviewType = type;
      payload.interviewAt = new Date(atLocal).toISOString();
      payload.interviewLocation = (document.getElementById("int-loc") || {}).value || null;
      payload.interviewNotes = (document.getElementById("int-notes") || {}).value || null;
    }

    try {
      await HireLinkAPI.api("/applications/" + c.id + "/status", {
        method: "PATCH",
        body: payload,
      });
      HireLink.toast("Pipeline sécurisé", c.candidateName + " → " + next, "ok");
      load();
    } catch (err) {
      HireLink.toast("Erreur", err.message, "danger");
    }
  }

  async function rejectFlow(c) {
    const letterBlock = c.coverLetter
      ? '<p class="muted" style="font-size:0.8rem;margin:0 0 0.35rem">Lettre jointe</p><div class="ai-result" style="max-height:100px;margin-bottom:0.75rem">' +
        esc(c.coverLetter) +
        "</div>"
      : "";
    const okPromise = HireLink.openModal({
      title: "Refuser " + c.candidateName,
      description: "Motif catalogue obligatoire (R04) — visible par le candidat.",
      bodyHtml: dossierHtml(c) + letterBlock + checksHtml() + refusalSelectHtml(),
      confirmLabel: "Confirmer le refus",
      danger: true,
      large: true,
    });

    setTimeout(() => {
      const aiBtn = document.getElementById("btn-ai-refuse");
      if (!aiBtn) return;
      aiBtn.onclick = async (e) => {
        e.preventDefault();
        e.stopPropagation();
        const code = (document.getElementById("reject-reason") || {}).value || "";
        if (!code) {
          HireLink.toast("Motif", "Choisissez d’abord un motif catalogue", "warn");
          return;
        }
        const label =
          (refusalCatalog.find((r) => r.code === code) || {}).label || code;
        try {
          const r = await HireLinkAPI.api("/ai/refusal-draft", {
            method: "POST",
            body: {
              reasonCode: code,
              reasonLabel: label,
              jobTitle: c.jobTitle,
              premium: true,
            },
          });
          const box = document.getElementById("ai-refuse-box");
          if (box) {
            box.style.display = "block";
            box.textContent = r.content || "";
          }
          HireLink.toast("IA", "Brouillon prêt — validation humaine (R07)", "ok");
        } catch (err) {
          HireLink.toast("IA", err.message, "warn");
        }
      };
    }, 80);

    const ok = await okPromise;
    if (!ok) return;
    const reason = (document.getElementById("reject-reason") || {}).value || "";
    if (!reason.trim()) {
      HireLink.toast("Motif requis", "Choisissez un motif du catalogue", "warn");
      return;
    }
    const confirmIdentity = !!(document.getElementById("chk-id") || {}).checked;
    const confirmCvReviewed = !!(document.getElementById("chk-cv") || {}).checked;
    const confirmSlaAware = !!(document.getElementById("chk-sla") || {}).checked;
    if (!confirmIdentity || !confirmCvReviewed || !confirmSlaAware) {
      HireLink.toast("Bloqué", "Cochez les 3 vérifications", "warn");
      return;
    }
    try {
      await HireLinkAPI.api("/applications/" + c.id + "/status", {
        method: "PATCH",
        body: {
          status: "REJECTED",
          rejectionReason: reason,
          confirmIdentity,
          confirmCvReviewed,
          confirmSlaAware,
        },
      });
      HireLink.toast("Candidature refusée", "Motif catalogue enregistré", "ok");
      load();
    } catch (err) {
      HireLink.toast("Erreur", err.message, "danger");
    }
  }

  async function openDossier(c) {
    await HireLink.openModal({
      title: "Dossier candidat",
      description: c.jobTitle + " · " + c.status,
      bodyHtml:
        dossierHtml(c) +
        (c.coverLetter
          ? '<p class="muted" style="font-size:0.8rem">Lettre</p><div class="ai-result">' +
            esc(c.coverLetter) +
            "</div>"
          : "") +
        (c.advanceNote
          ? '<p class="muted" style="margin-top:0.75rem;font-size:0.8rem">Dernière note RH</p><p>' +
            esc(c.advanceNote) +
            "</p>"
          : "") +
        '<div class="row" style="gap:0.5rem;margin-top:0.75rem;flex-wrap:wrap">' +
        '<button type="button" class="btn btn-soft btn-sm" id="btn-tl">Historique F13</button>' +
        '<button type="button" class="btn btn-primary btn-sm" id="btn-sc">Scorecard F12</button>' +
        "</div>",
      confirmLabel: "Fermer",
      hideCancel: true,
      large: true,
    });
    const tl = document.getElementById("btn-tl");
    if (tl) tl.onclick = () => showTimeline(c.id);
    const sc = document.getElementById("btn-sc");
    if (sc) sc.onclick = () => rateScorecard(c);
  }

  function paint() {
    const root = document.getElementById("kanban");
    const filter = (document.getElementById("pipe-filter") || {}).value || "all";
    const q = ((document.getElementById("search-pipeline") || {}).value || "").trim().toLowerCase();
    let filtered = allApps;
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
        const urgent =
          c.slaBreached || (c.slaRemainingDays != null && c.slaRemainingDays <= 2);
        card.className =
          "kanban-card" + (urgent ? " is-urgent" : "") + ((c.matchScore || 0) >= 75 ? " is-hot" : "");
        const sla = c.slaRemainingDays;
        const slaTxt = c.slaBreached ? "Retard" : sla == null ? "—" : sla < 0 ? "Retard" : sla + " j";
        card.innerHTML =
          "<h3></h3><p></p><div class='match-bar'><span></span></div>" +
          "<div class='kanban-meta'><span class='badge badge-rose'></span>" +
          "<span class='sla-pill'></span></div>" +
          "<div class='row-actions'>" +
          "<button type='button' class='btn btn-sm btn-soft js-view'>Dossier</button>" +
          "<button type='button' class='btn btn-sm btn-primary js-adv'>Avancer</button>" +
          "<button type='button' class='btn btn-sm btn-ghost js-rej'>Refuser</button></div>";
        card.querySelector("h3").textContent = c.candidateName;
        let sub = c.jobTitle || "";
        if (c.interviewType) {
          sub +=
            " · " +
            c.interviewType +
            (c.interviewAt ? " " + new Date(c.interviewAt).toLocaleDateString("fr-FR") : "");
        }
        card.querySelector("p").textContent = sub;
        card.querySelector(".match-bar > span").style.width = Math.min(100, c.matchScore || 0) + "%";
        card.querySelector(".badge").textContent =
          c.matchScore != null ? Math.round(c.matchScore) + "%" : "—";
        const pill = card.querySelector(".sla-pill");
        pill.textContent = "SLA " + slaTxt;
        const sc = slaClass(sla, c.slaBreached);
        if (sc) pill.classList.add(sc);
        card.querySelector(".js-view").onclick = (e) => {
          e.stopPropagation();
          openDossier(c);
        };
        card.querySelector(".js-adv").onclick = (e) => {
          e.stopPropagation();
          advance(c);
        };
        card.querySelector(".js-rej").onclick = (e) => {
          e.stopPropagation();
          rejectFlow(c);
        };
        body.appendChild(card);
      });
      root.appendChild(el);
    });
  }

  async function load() {
    const root = document.getElementById("kanban");
    root.innerHTML = '<div class="skeleton" style="height:200px;grid-column:1/-1"></div>';
    try {
      if (!refusalCatalog.length) {
        try {
          refusalCatalog = await HireLinkAPI.api("/refusal-reasons");
        } catch (_) {
          refusalCatalog = [];
        }
      }
      allApps = await HireLinkAPI.api("/applications/pipeline");
      paint();
      loadFunnel();
    } catch (err) {
      root.innerHTML = "<p class='muted'>" + err.message + "</p>";
    }
  }

  const qParam = new URLSearchParams(location.search).get("q");
  if (qParam) {
    const inp = document.getElementById("search-pipeline");
    if (inp) inp.value = qParam;
  }

  document.getElementById("btn-refresh").addEventListener("click", load);
  const filt = document.getElementById("pipe-filter");
  if (filt) filt.addEventListener("change", paint);
  HireLinkShell.bindSearch(document.getElementById("search-pipeline"), () => paint());
  load();
})();
