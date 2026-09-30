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

  function scoreTier(score) {
    if (score == null) return { label: "—", cls: "" };
    if (score >= 75) return { label: "Hot", cls: "is-hot" };
    if (score >= 50) return { label: "Fit", cls: "" };
    return { label: "Low", cls: "" };
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
        "<span class='badge badge-rose'></span>" +
        "<span class='sla-pill'></span>" +
        "<span class='badge badge-chrome'></span></div></div>" +
        "<div class='stack'><a class='btn btn-sm btn-primary' href='recruiter-pipeline.html'>Traiter</a></div>";
      item.querySelector("strong").textContent = c.candidateName || "Candidat";
      item.querySelector("p").textContent = (c.jobTitle || "") + " · " + (c.status || "");
      item.querySelector(".badge-rose").textContent =
        c.matchScore != null ? Math.round(c.matchScore) + "% · " + tier.label : "Score —";
      const pill = item.querySelector(".sla-pill");
      pill.textContent = "SLA " + slaTxt;
      const sc = slaClass(sla);
      if (sc) pill.classList.add(sc);
      item.querySelector(".badge-chrome").textContent = c.status;
      root.appendChild(item);
    });
  }

  let cachedApps = [];

  async function load() {
    try {
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
          " n’est pas traité avant l’échéance SLA, il basculera en dépassement visible du candidat (intention si-alors).";
      } else {
        document.getElementById("sla-nudge-sub").textContent =
          "Tous vos délais sont sous contrôle — engagement de confiance tenu.";
      }

      const decided = apps.filter((a) => ["ACCEPTED", "REJECTED"].includes(a.status)).length;
      const interviews = apps.filter((a) => a.status === "INTERVIEW" || a.interviewType).length;
      const wins = document.getElementById("wins-line");
      if (wins) {
        wins.textContent =
          decided +
          " décision" +
          (decided > 1 ? "s" : "") +
          " explicites · " +
          interviews +
          " entretien" +
          (interviews > 1 ? "s" : "") +
          " · " +
          urgent.length +
          " à traiter avant rupture SLA";
      }

      try {
        const rel = await HireLinkAPI.api("/reliability/me");
        const relEl = document.getElementById("trust-score");
        if (relEl) relEl.textContent = rel.score + "/100 · " + rel.label;
      } catch (_) {}

      const counts = { SUBMITTED: 0, SCREENING: 0, SHORTLISTED: 0, INTERVIEW: 0, OFFER: 0 };
      apps.forEach((a) => {
        if (counts[a.status] != null) counts[a.status]++;
      });
      const max = Math.max(1, ...Object.values(counts));
      const keys = ["SUBMITTED", "SCREENING", "SHORTLISTED", "INTERVIEW", "OFFER"];
      const labels = ["Soumises", "Screen", "Shortlist", "Entretien", "Offre"];
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
