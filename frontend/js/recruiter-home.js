(function () {
  const session = HireLinkShell.mountShell({ roles: ["RECRUTEUR"] });
  if (!session) return;
  if (window.HireLinkProduct) HireLinkProduct.mountPremiumBar("recruiter");

  function slaClass(days) {
    if (days == null) return "";
    if (days < 0) return "danger";
    if (days <= 2) return "warn";
    return "";
  }

  const STATUS_FR = {
    SUBMITTED: "Soumise",
    SCREENING: "Présélection",
    SHORTLISTED: "Shortlist",
    INTERVIEW: "Entretien",
    REJECTED: "Refusée",
    WITHDRAWN: "Retirée",
  };

  function scoreTier(score) {
    if (score == null) return { label: "—", cls: "badge-chrome" };
    if (score >= 80) return { label: "Excellent", cls: "badge-ok match-hot" };
    if (score >= 55) return { label: "Bon", cls: "badge-info" };
    return { label: "Faible", cls: "badge-rose" };
  }

  function renderInbox(apps) {
    const root = document.getElementById("inbox-list");
    if (!root) return;
    const q = ((document.getElementById("search-home") || {}).value || "").trim().toLowerCase();
    const open = apps.filter((a) => !["REJECTED", "WITHDRAWN", "ACCEPTED"].includes(a.status));
    const matched = q
      ? open.filter((a) =>
          ((a.candidateName || "") + " " + (a.jobTitle || "") + " " + (a.status || ""))
            .toLowerCase()
            .includes(q)
        )
      : open;
    const sorted = matched.slice().sort((a, b) => {
      const sa = a.slaRemainingDays == null ? 99 : a.slaRemainingDays;
      const sb = b.slaRemainingDays == null ? 99 : b.slaRemainingDays;
      if (sa !== sb) return sa - sb;
      return (b.matchScore || 0) - (a.matchScore || 0);
    });
    const top = sorted.slice(0, q ? 20 : 8);
    if (!top.length) {
      root.innerHTML = q
        ? '<p class="muted">Aucun résultat pour « ' + q + ' ».</p>'
        : '<p class="muted">Inbox vide — publiez une offre pour recevoir des candidatures.</p>';
      return;
    }
    root.innerHTML = "";
    top.forEach((c) => {
      const item = document.createElement("article");
      const urgent = c.slaRemainingDays != null && c.slaRemainingDays <= 2;
      const tier = scoreTier(c.matchScore);
      item.className = "inbox-item" + (urgent ? " is-urgent" : "") + (tier.cls ? " " + tier.cls : "");
      const sla = c.slaRemainingDays;
      const slaTxt = sla == null ? "—" : sla < 0 ? "Retard " + Math.abs(sla) + " j" : sla + " j";
      item.innerHTML =
        "<div><strong></strong><p class='muted'></p>" +
        "<div class='kanban-meta' style='margin-top:0.45rem'>" +
        "<span class='badge js-match'></span>" +
        "<span class='sla-pill'></span>" +
        "<span class='badge badge-chrome js-status'></span></div></div>" +
        "<div class='stack'><a class='btn btn-sm btn-primary' href='recruiter-pipeline.html'>Traiter</a></div>";
      item.querySelector("strong").textContent = c.candidateName || "Candidat";
      item.querySelector("p").textContent =
        (c.jobTitle || "") + " · " + (STATUS_FR[c.status] || c.status || "");
      const matchBadge = item.querySelector(".js-match");
      matchBadge.className = "badge " + tier.cls;
      matchBadge.textContent =
        c.matchScore != null ? Math.round(c.matchScore) + "% · " + tier.label : "Match —";
      const pill = item.querySelector(".sla-pill");
      pill.textContent = "Réponse " + slaTxt;
      const sc = slaClass(sla);
      if (sc) pill.classList.add(sc);
      item.querySelector(".js-status").textContent = STATUS_FR[c.status] || c.status;
      root.appendChild(item);
    });
  }

  let cachedApps = [];

  async function showVerifyBanner() {
    try {
      const me = await HireLinkAPI.api("/me");
      const host = document.querySelector(".content") || document.querySelector("main");
      if (!host || me.companyVerified) {
        const old = document.getElementById("hl-verify-banner");
        if (old) old.remove();
        return;
      }
      let ban = document.getElementById("hl-verify-banner");
      if (!ban) {
        ban = document.createElement("div");
        ban.id = "hl-verify-banner";
        ban.className = "panel";
        ban.style.cssText = "margin:0 0 1rem;padding:0.9rem 1.1rem;border:1px solid #e8b4a0;background:#fff7f2";
        host.insertBefore(ban, host.firstChild);
      }
      const st = me.verificationStatus || "PENDING";
      ban.innerHTML =
        "<strong>Inscription libre ≠ publication libre</strong>" +
        '<p class="muted" style="margin:0.35rem 0 0;font-size:0.9rem">Statut : <code>' +
        st +
        "</code>. Vous pouvez créer des brouillons d’offres, mais la publication reste bloquée tant que l’entreprise n’est pas vérifiée (e-mail pro + matching registre / validation admin).</p>";
    } catch (_) {}
  }

  async function load() {
    try {
      showVerifyBanner();
      const [apps, offers] = await Promise.all([
        HireLinkAPI.api("/applications/pipeline"),
        HireLinkAPI.api("/offers/mine"),
      ]);
      cachedApps = apps;
      const published = offers.filter((o) => o.published).length;
      const screening = apps.filter((a) => a.status === "SCREENING").length;
      const withMatch = apps.filter((a) => a.matchScore != null);
      const avg = withMatch.length
        ? Math.round(withMatch.reduce((s, a) => s + a.matchScore, 0) / withMatch.length)
        : 0;
      const onTime = apps.filter((a) => a.slaRemainingDays == null || a.slaRemainingDays >= 0).length;
      const slaPct = apps.length ? Math.round((onTime / apps.length) * 100) : 100;
      const urgent = apps.filter(
        (a) =>
          a.slaRemainingDays != null &&
          a.slaRemainingDays <= 2 &&
          !["REJECTED", "WITHDRAWN", "ACCEPTED"].includes(a.status)
      );

      document.getElementById("st-offers").textContent = String(published);
      document.getElementById("st-offers-d").textContent = offers.length + " au total";
      document.getElementById("st-apps").textContent = String(apps.length);
      document.getElementById("st-apps-d").textContent = screening + " en présélection";
      document.getElementById("st-sla").textContent = slaPct + "%";
      document.getElementById("st-match").textContent = withMatch.length ? avg + "%" : "—";
      document.getElementById("sla-nudge-title").textContent =
        urgent.length +
        " candidature" +
        (urgent.length > 1 ? "s" : "") +
        " proche" +
        (urgent.length > 1 ? "s" : "") +
        " du délai";
      if (urgent.length) {
        const first = urgent[0];
        document.getElementById("sla-nudge-sub").textContent =
          "Si le dossier de " +
          (first.candidateName || "ce candidat") +
          " n’est pas traité avant l’échéance SLA, le dépassement sera visible côté candidat.";
      } else {
        document.getElementById("sla-nudge-sub").textContent =
          "Tous vos délais sont sous contrôle — engagement de confiance tenu.";
      }

      const decided = apps.filter((a) => ["INTERVIEW", "REJECTED"].includes(a.status)).length;
      const interviews = apps.filter((a) => a.status === "INTERVIEW" || a.interviewType).length;
      const wins = document.getElementById("wins-line");
      if (wins) {
        wins.textContent =
          interviews +
          " entretien" +
          (interviews > 1 ? "s" : "") +
          " · " +
          decided +
          " dossier" +
          (decided > 1 ? "s" : "") +
          " en phase finale · " +
          urgent.length +
          " urgent" +
          (urgent.length > 1 ? "s" : "") +
          " SLA";
      }

      try {
        const rel = await HireLinkAPI.api("/reliability/me");
        const relEl = document.getElementById("trust-score");
        if (relEl) relEl.textContent = rel.score + "/100 · " + rel.label;
      } catch (_) {}

      const counts = { SUBMITTED: 0, SCREENING: 0, SHORTLISTED: 0, INTERVIEW: 0 };
      apps.forEach((a) => {
        if (counts[a.status] != null) counts[a.status]++;
      });
      const max = Math.max(1, ...Object.values(counts));
      const keys = ["SUBMITTED", "SCREENING", "SHORTLISTED", "INTERVIEW"];
      const labels = ["Soumises", "Screen", "Shortlist", "Entretien"];
      document.getElementById("chart-bars").innerHTML = keys
        .map((k, i) => {
          const h = Math.round((counts[k] / max) * 100);
          return (
            '<div class="chart-bar"><span style="--h:' +
            Math.max(8, h) +
            "%;--i:" +
            i +
            '"></span><small>' +
            labels[i] +
            "</small></div>"
          );
        })
        .join("");

      renderInbox(cachedApps);
    } catch (err) {
      HireLink.toast("Tableau de bord", err.message, "danger");
    }
  }

  HireLinkShell.bindSearch(document.getElementById("search-home"), () => renderInbox(cachedApps));
  const searchHome = document.getElementById("search-home");
  if (searchHome) {
    searchHome.addEventListener("keydown", (e) => {
      if (e.key === "Enter") {
        const q = (searchHome.value || "").trim();
        if (q) location.href = "recruiter-pipeline.html?q=" + encodeURIComponent(q);
      }
    });
  }

  document.getElementById("btn-upgrade").addEventListener("click", () => {
    if (window.HireLinkGrowth) HireLinkGrowth.showPremium("recruiter");
  });
  if (window.HireLinkGrowth) HireLinkGrowth.scheduleNudge("recruiter", 28000);
  load();
})();
