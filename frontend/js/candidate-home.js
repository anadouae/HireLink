(function () {
  const session = HireLinkShell.mountShell({ roles: ["CANDIDAT"] });
  if (!session) return;
  if (window.HireLinkProduct) HireLinkProduct.mountPremiumBar("candidate");

  const PROFILE_STEPS = [
    { key: "name", label: "Identité", hint: "Nom renseigné" },
    { key: "skills", label: "Compétences", hint: "CV / skills" },
    { key: "roles", label: "Métiers", hint: "Cibles" },
    { key: "loc", label: "Lieux", hint: "Préférences" },
    { key: "level", label: "Niveau", hint: "Expérience" },
  ];

  const APP_STEPS = [
    { key: "SUBMITTED", label: "Soumises" },
    { key: "SCREENING", label: "Screen" },
    { key: "SHORTLISTED", label: "Shortlist" },
    { key: "INTERVIEW", label: "Entretien" },
    { key: "OFFER", label: "Offre" },
  ];

  function renderProfilePipe(profile) {
    const checks = {
      name: !!(profile.fullName && profile.fullName.trim()),
      skills: !!(profile.skills && profile.skills.trim()),
      roles: !!(profile.soughtRoles && profile.soughtRoles.trim()),
      loc: !!(profile.preferredLocations && profile.preferredLocations.trim()),
      level: !!(profile.experienceLevel && profile.experienceLevel.trim()),
    };
    const done = PROFILE_STEPS.filter((s) => checks[s.key]).length;
    const pct = Math.round((done / PROFILE_STEPS.length) * 100);
    const ring = document.getElementById("profile-ring");
    const pctEl = document.getElementById("profile-pct");
    const hint = document.getElementById("profile-hint");
    const nudge = document.getElementById("nudge");
    if (ring) ring.style.setProperty("--p", String(pct));
    if (pctEl) pctEl.textContent = pct + "%";
    if (hint) {
      hint.textContent =
        pct >= 100
          ? "Profil prêt — le matching peut prioriser les meilleures offres."
          : done + "/" + PROFILE_STEPS.length + " étapes. Continuez pour maximiser votre ROI personnel.";
    }
    if (nudge) nudge.hidden = pct >= 80;

    let currentSet = false;
    document.getElementById("profile-pipe").innerHTML = PROFILE_STEPS.map((s, i) => {
      const ok = checks[s.key];
      let cls = "pipe-step-card";
      if (ok) cls += " is-done";
      else if (!currentSet) {
        cls += " is-current";
        currentSet = true;
      }
      return (
        '<div class="' +
        cls +
        '"><div class="dot">' +
        (ok ? "✓" : i + 1) +
        "</div><strong>" +
        s.label +
        "</strong><small>" +
        s.hint +
        "</small></div>"
      );
    }).join("");
  }

  function renderAppsPipe(counts) {
    const maxKey = APP_STEPS.reduce((best, s) => (counts[s.key] > (counts[best] || 0) ? s.key : best), APP_STEPS[0].key);
    document.getElementById("apps-pipe").innerHTML = APP_STEPS.map((s) => {
      const n = counts[s.key] || 0;
      let cls = "pipe-step-card";
      if (n > 0) cls += " is-done";
      if (s.key === maxKey && n > 0) cls += " is-current";
      return (
        '<div class="' +
        cls +
        '"><div class="dot">' +
        n +
        "</div><strong>" +
        s.label +
        "</strong><small>" +
        (n ? "actifs" : "—") +
        "</small></div>"
      );
    }).join("");
  }

  async function uploadCv(file) {
    const status = document.getElementById("cv-status");
    if (status) status.textContent = "Analyse en cours…";
    const fd = new FormData();
    fd.append("file", file);
    const token = HireLinkAPI.getToken();
    const res = await fetch(HireLinkAPI.API_BASE + "/ocr/cv", {
      method: "POST",
      headers: token ? { Authorization: "Bearer " + token } : {},
      body: fd,
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) throw new Error(data.message || data.error || "Import échoué");
    const skills = (data.suggestedSkills || []).join(", ");
    if (status) {
      status.textContent = skills
        ? "Compétences extraites : " + skills
        : "Fichier lu — enrichissez le profil manuellement.";
    }
    HireLink.toast("CV analysé", skills || "Texte extrait", "ok");
    load();
  }

  async function load() {
    try {
      const [apps, profile, offers] = await Promise.all([
        HireLinkAPI.api("/applications/mine"),
        HireLinkAPI.api("/me"),
        HireLinkAPI.api("/offers/public").catch(() => []),
      ]);
      renderProfilePipe(profile || {});
      if (window.HireLinkProduct) {
        HireLinkProduct.renderMatchAlerts(document.getElementById("match-alerts"), offers, { limit: 4 });
      }

      const active = apps.filter((a) => a.status !== "WITHDRAWN");
      const interviews = active.filter((a) => a.status === "INTERVIEW").length;
      const withMatch = active.filter((a) => a.matchScore != null);
      const avg = withMatch.length
        ? Math.round(withMatch.reduce((s, a) => s + a.matchScore, 0) / withMatch.length)
        : 0;
      const onTime = active.filter((a) => a.slaRemainingDays == null || a.slaRemainingDays >= 0).length;
      document.getElementById("st-apps").textContent = String(active.length);
      document.getElementById("st-apps-d").textContent = active.length ? "Dossiers ouverts" : "Aucune pour l’instant";
      document.getElementById("st-interview").textContent = String(interviews);
      document.getElementById("st-match").textContent = withMatch.length ? avg + "%" : "—";
      document.getElementById("st-sla").textContent = active.length ? onTime + "/" + active.length : "—";

      try {
        const rel = await HireLinkAPI.api("/reliability/me");
        const relEl = document.getElementById("trust-score");
        if (relEl) relEl.textContent = rel.score + "/100 · " + rel.label;
      } catch (_) {}

      const counts = { SUBMITTED: 0, SCREENING: 0, SHORTLISTED: 0, INTERVIEW: 0, OFFER: 0 };
      active.forEach((a) => {
        if (counts[a.status] != null) counts[a.status]++;
      });
      renderAppsPipe(counts);
      const max = Math.max(1, ...Object.values(counts));
      const keys = APP_STEPS.map((s) => s.key);
      const labels = APP_STEPS.map((s) => s.label);
      const chart = document.getElementById("chart-bars");
      if (chart) {
        chart.innerHTML = keys
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
      }
    } catch (err) {
      HireLink.toast("Tableau de bord", err.message, "danger");
    }
  }

  document.getElementById("btn-premium").addEventListener("click", () => {
    if (window.HireLinkGrowth) HireLinkGrowth.showPremium("candidate");
  });
  const qs = document.getElementById("quick-search");
  if (qs) {
    const goOffers = () => {
      location.href = "candidate-offers.html?q=" + encodeURIComponent((qs.value || "").trim());
    };
    qs.addEventListener("keydown", (e) => {
      if (e.key === "Enter") {
        e.preventDefault();
        goOffers();
      }
    });
    qs.addEventListener("search", () => {
      if ((qs.value || "").trim()) goOffers();
    });
  }

  const fileInput = document.getElementById("cv-file-home");
  if (fileInput) {
    fileInput.addEventListener("change", async (e) => {
      const file = e.target.files && e.target.files[0];
      if (!file) return;
      try {
        await uploadCv(file);
      } catch (err) {
        HireLink.toast("Import CV", err.message, "danger");
      } finally {
        e.target.value = "";
      }
    });
  }

  if (window.HireLinkGrowth) HireLinkGrowth.scheduleNudge("candidate", 25000);
  load();
})();
