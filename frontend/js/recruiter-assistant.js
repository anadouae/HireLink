/** Assistant IA recruteur : synthèse structurée de CV (grille d’entretien retirée de l’UI ; API conservée). */
(function () {
  const RC = HireLinkApp;
  const session = RC.mount({ active: "assistant", title: "Assistant IA" });
  if (!session) return;
  const $ = (id) => document.getElementById(id);
  const esc = RC.esc;
  let apps = [];

  function splitSkills(raw) {
    return String(raw || "")
      .split(/[,;|/·•\n]+/)
      .map((s) => s.trim())
      .filter(Boolean)
      .filter((s, i, arr) => arr.findIndex((x) => x.toLowerCase() === s.toLowerCase()) === i)
      .slice(0, 24);
  }

  function paintExtracted(app) {
    const skills = splitSkills(app.candidateSkills);
    const missing = [];
    if (!skills.length) missing.push("Compétences non renseignées dans le dossier");
    if (!(app.coverLetter || "").trim()) missing.push("Lettre de motivation absente");
    if (!(app.cvFileName || "").trim()) missing.push("Fichier CV non attaché côté plateforme");
    const match = app.matchScore != null ? Math.round(app.matchScore) : null;
    $("cv-extracted").innerHTML =
      '<div class="ai-cv-meta"><strong>' + esc(app.candidateName || "Candidat") + "</strong>" +
      '<span class="rc-muted">' + esc(app.jobTitle || "Offre") +
      (match != null ? " · correspondance actuelle " + match + " %" : " · correspondance non calculée") +
      "</span></div>" +
      '<div class="ai-cv-block"><h3>Compétences identifiées (dossier)</h3>' +
      (skills.length
        ? '<div class="ai-cv-badges">' + skills.map((s) => '<span class="ai-cv-badge">' + esc(s) + "</span>").join("") + "</div>"
        : '<p class="rc-muted">Aucune compétence structurée dans ce dossier.</p>') +
      "</div>" +
      '<div class="ai-cv-block"><h3>Lettre / texte joint</h3><p class="ai-cv-quote">' +
      esc((app.coverLetter || "").trim() || "—") +
      "</p></div>" +
      '<div class="ai-cv-block"><h3>Informations manquantes ou incertaines</h3><ul class="ai-cv-miss">' +
      (missing.length ? missing.map((m) => "<li>" + esc(m) + "</li>").join("") : "<li>Rien d’évident côté champs plateforme.</li>") +
      '</ul><p class="rc-muted" style="font-size:var(--text-xs)">Expériences, formations et technologies détaillées ne sont affichées ici que si présentes dans le texte envoyé à la synthèse — elles ne sont pas inventées.</p></div>';
  }

  function showSummary(data) {
    const out = $("cv-out");
    out.innerHTML =
      '<p class="ai-cv-label">Interprétation IA</p>' +
      '<div class="ai-cv-summary">' + esc(data.content || "").replace(/\n/g, "<br/>") + "</div>" +
      '<div class="hl-kit-foot"><button type="button" class="btn btn-ghost btn-sm" id="cv-copy">Copier</button>' +
      '<span class="rc-muted" style="font-size:var(--text-xs)">' +
      esc(data.note || "Proposition à relire — la décision reste humaine.") +
      (data.provider ? " · " + esc(data.provider) : "") +
      "</span></div>";
    const copy = out.querySelector("#cv-copy");
    if (copy) {
      copy.onclick = () =>
        navigator.clipboard.writeText(String(data.content || "").trim()).then(
          () => RC.toast("Copié", "Synthèse copiée", "ok"),
          () => RC.toast("Copie impossible", "Sélectionnez le texte manuellement", "warn")
        );
    }
  }

  function showBoard(show) {
    $("cv-empty").classList.toggle("is-hidden", show);
    $("cv-board").classList.toggle("is-hidden", !show);
  }

  $("cv-app").addEventListener("change", () => {
    const a = apps.find((x) => String(x.id) === $("cv-app").value);
    if (!a) {
      showBoard(false);
      $("cv-out").innerHTML = "";
      return;
    }
    showBoard(true);
    paintExtracted(a);
    $("cv-out").innerHTML = '<p class="rc-muted" style="margin:0">Cliquez sur « Générer la synthèse » pour obtenir une lecture assistée.</p>';
  });

  $("cv-go").addEventListener("click", async (e) => {
    const a = apps.find((x) => String(x.id) === $("cv-app").value);
    if (!a) {
      RC.toast("Candidat requis", "Choisissez un dossier.", "warn");
      return;
    }
    if (!HireLinkCredits.preflight()) return;
    showBoard(true);
    paintExtracted(a);
    const btn = e.currentTarget;
    RC.busy(btn, true);
    $("cv-out").innerHTML = Mascotte.waiting("Synthèse en cours…");
    try {
      const cvText = [a.candidateSkills, a.coverLetter, a.cvFileName].filter(Boolean).join("\n");
      const data = await HireLinkAPI.api("/ai/cv-summary", { method: "POST", body: { cvText }, timeoutMs: 60000 });
      showSummary(data);
      HireLinkCredits.spend();
    } catch (err) {
      $("cv-out").innerHTML = '<p class="rc-muted">Impossible de générer la synthèse.</p>';
      if (!HireLinkCredits.guard(err)) RC.toast("Assistant IA", err.message, "danger");
    } finally {
      RC.busy(btn, false);
    }
  });

  function fillSelects() {
    $("cv-app").innerHTML =
      '<option value="">— Choisir un dossier —</option>' +
      apps.map((a) => '<option value="' + a.id + '">' + esc(a.candidateName) + " — " + esc(a.jobTitle || "") + "</option>").join("");
    const pre = new URLSearchParams(location.search).get("app");
    if (pre && apps.some((a) => String(a.id) === pre)) {
      $("cv-app").value = pre;
      $("cv-app").dispatchEvent(new Event("change"));
    }
  }

  RC.data.pipeline((a) => {
    apps = a || [];
    fillSelects();
  }, (err) => {
    $("cv-app").innerHTML = '<option value="">Indisponible — ' + esc(err.message) + "</option>";
  });
  HireLinkCredits.paintCosts();
})();
