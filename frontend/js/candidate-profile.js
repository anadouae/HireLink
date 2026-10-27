(function () {
  const session = HireLinkShell.mountShell({ roles: ["CANDIDAT"], hello: false });
  if (!session) return;
  if (window.HireLinkProduct) HireLinkProduct.mountPremiumBar("candidate");

  let skillsApi = null;
  let dirty = false;
  const saveBar = document.getElementById("profile-save-bar");

  function setDirty(v) {
    dirty = !!v;
    if (saveBar) saveBar.classList.toggle("is-clean", !dirty);
  }

  function bindDirty() {
    ["pf-roles", "pf-loc", "pf-contracts", "pf-level"].forEach((id) => {
      const node = document.getElementById(id);
      if (node) node.addEventListener("input", () => setDirty(true));
      if (node) node.addEventListener("change", () => setDirty(true));
    });
  }

  async function showAi(endpoint, body) {
    const box = document.getElementById("ai-result");
    box.hidden = false;
    box.textContent = "Génération…";
    try {
      const premium = HireLinkProduct && HireLinkProduct.isPremium("candidate");
      const data = await HireLinkAPI.api(endpoint, {
        method: "POST",
        headers: HireLinkProduct ? HireLinkProduct.premiumHeaders("candidate") : {},
        body: Object.assign({}, body, { premium }),
      });
      box.textContent = (data.title ? data.title + "\n\n" : "") + (data.content || "");
      if (data.tips && data.tips.length) {
        box.textContent += "\n\n— Tips —\n• " + data.tips.join("\n• ");
      }
      if (data.premiumLocked && window.HireLinkGrowth) {
        HireLink.toast("Aperçu freemium", "Premium débloque le script complet", "info");
      }
    } catch (err) {
      box.textContent = err.message;
      HireLink.toast("IA", err.message, "danger");
    }
  }

  function mountSkills(csv) {
    const root = document.getElementById("pf-skills-picker");
    const parts = (csv || "")
      .split(",")
      .map((x) => x.trim())
      .filter(Boolean);
    const langs = [];
    const skills = [];
    const knownLangs = new Set(
      (window.HireLinkSkills && HireLinkSkills.LANGUAGES) || ["Français", "Arabe", "Anglais"]
    );
    parts.forEach((p) => {
      const m = p.match(/^(.+?)\s*\(([^)]+)\)\s*$/);
      const name = m ? m[1].trim() : p;
      if (knownLangs.has(name) || m) {
        langs.push({ name, level: m ? m[2].trim() : "B2" });
      } else {
        skills.push(name);
      }
    });
    if (window.HireLinkSkills) {
      skillsApi = HireLinkSkills.mount(root, {
        selected: skills,
        languages: langs.length ? langs : [],
        onChange: (v) => {
          document.getElementById("pf-skills").value = [v.skillsCsv, v.languagesCsv]
            .filter(Boolean)
            .join(", ");
          setDirty(true);
        },
      });
    }
  }

  const ROLE_CHOICES = [
    "Développeuse Full-Stack Java",
    "Développeur Full-Stack",
    "Data Analyst",
    "Ingénieur DevOps",
    "QA / Test",
    "Product Manager",
    "UX Designer",
  ];
  const LOC_CHOICES = ["Tétouan", "Tanger", "Casablanca", "Rabat", "Fès", "Marrakech", "Remote", "Hybride", "Présentiel"];
  const CONTRACT_CHOICES = ["CDI", "CDD", "Stage", "Alternance", "Freelance"];
  let meId = session.userId || session.id || "me";
  let pendingFile = null;
  let cvObjectUrl = "";

  function photoKey() {
    return "hirelink.photo." + meId;
  }
  function cvKey() {
    return "hirelink.cv." + meId;
  }

  function initials(name) {
    const parts = String(name || "C").trim().split(/\s+/).filter(Boolean);
    return ((parts[0] || "C")[0] + (parts[1] ? parts[1][0] : "")).toUpperCase();
  }

  function showPhoto(dataUrl, name) {
    const img = document.getElementById("pf-photo");
    const fallback = document.getElementById("pf-photo-fallback");
    const avatar = document.getElementById("avatar");
    if (dataUrl && img) {
      img.src = dataUrl;
      img.hidden = false;
      if (fallback) fallback.hidden = true;
      if (avatar) {
        avatar.textContent = "";
        avatar.style.backgroundImage = "url(" + dataUrl + ")";
        avatar.style.backgroundSize = "cover";
      }
    } else {
      if (img) img.hidden = true;
      if (fallback) {
        fallback.hidden = false;
        fallback.textContent = initials(name);
      }
      if (avatar) {
        avatar.classList.remove("has-art");
        avatar.style.backgroundImage = "";
        avatar.textContent = initials(name);
      }
    }
  }

  function openPhotoPicker() {
    const input = document.getElementById("pf-photo-file");
    if (input) input.click();
  }

  function paintChips(rootId, inputId, options) {
    const root = document.getElementById(rootId);
    const input = document.getElementById(inputId);
    if (!root || !input) return;
    const have = input.value
      .split(",")
      .map((s) => s.trim().toLowerCase())
      .filter(Boolean);
    root.innerHTML = options
      .map((opt) => {
        const label = typeof opt === "object" && opt ? (opt.label || "") : String(opt || "");
        const flag = typeof opt === "object" && opt && opt.flag ? opt.flag + " " : "";
        const on = have.indexOf(label.toLowerCase()) >= 0;
        return '<button type="button" class="choice-chip' + (on ? " is-on" : "") + '" data-v="' + label.replace(/"/g, "") + '">' + flag + label + "</button>";
      })
      .join("");
    root.querySelectorAll("button").forEach((btn) => {
      btn.onclick = () => {
        const v = btn.getAttribute("data-v");
        const cur = input.value.split(",").map((s) => s.trim()).filter(Boolean);
        const i = cur.findIndex((x) => x.toLowerCase() === v.toLowerCase());
        if (i >= 0) cur.splice(i, 1);
        else cur.push(v);
        input.value = cur.join(", ");
        setDirty(true);
        paintChips(rootId, inputId, options);
      };
    });
  }

  function bindSuggest(valueId, chipsId, kind) {
    const chips = document.getElementById(chipsId);
    if (!chips || chips.dataset.suggest === "1") return;
    chips.dataset.suggest = "1";
    const box = document.createElement("input");
    box.type = "search";
    box.className = "form-control";
    box.placeholder = kind === "place" ? "Rechercher une ville…" : "Rechercher un métier…";
    box.setAttribute("autocomplete", "off");
    box.style.margin = "0.35rem 0 0.5rem";
    chips.parentElement.insertBefore(box, chips);
    let timer = 0;
    box.addEventListener("input", () => {
      clearTimeout(timer);
      const q = box.value.trim();
      if (q.length < 2) return;
      timer = setTimeout(() => {
        HireLinkAPI.api("/catalog/suggest?kind=" + kind + "&q=" + encodeURIComponent(q))
          .then((rows) => paintChips(chipsId, valueId, rows || []))
          .catch(() => {});
      }, 220);
    });
  }

  function paintFacts(p) {
    const box = document.getElementById("pf-facts");
    if (!box) return;
    const bits = [p.experienceLevel, p.preferredLocations, p.preferredContracts].filter(Boolean);
    box.textContent = bits.join(" · ") || "Complétez la recherche et les compétences.";
  }

  function sectionValue(sections, key) {
    if (!sections) return "";
    const value = sections[key];
    return value == null ? "" : String(value);
  }

  function fillSections(sections) {
    document.getElementById("cv-identity").value = sectionValue(sections, "identity");
    document.getElementById("cv-experience").value = sectionValue(sections, "experience");
    document.getElementById("cv-education").value = sectionValue(sections, "education");
    document.getElementById("cv-skills-read").value = sectionValue(sections, "skills");
    document.getElementById("cv-languages").value = sectionValue(sections, "languages");
    paintAnalysis();
  }

  function readSections() {
    return {
      identity: document.getElementById("cv-identity").value.trim(),
      experience: document.getElementById("cv-experience").value.trim(),
      education: document.getElementById("cv-education").value.trim(),
      skills: document.getElementById("cv-skills-read").value.trim(),
      languages: document.getElementById("cv-languages").value.trim(),
    };
  }

  function showDocument(url, kind) {
    const frame = document.getElementById("cv-frame");
    const image = document.getElementById("cv-image");
    const raw = document.getElementById("cv-raw");
    frame.hidden = kind !== "pdf";
    image.hidden = kind !== "image";
    raw.hidden = kind !== "text";
      if (kind === "pdf") frame.src = url;
      if (kind === "image") image.src = url;
      if (kind === "text") raw.textContent = url;
      const open = document.getElementById("cv-open");
      if (open && kind !== "text") open.href = url;
  }

  function showCvEmpty() {
    const empty = document.getElementById("cv-empty");
    const bench = document.getElementById("cv-workbench");
    if (bench) bench.hidden = false;
    if (empty) empty.innerHTML = "<p class='muted'>Aucun CV pour le moment. Déposez un PDF pour le prévisualiser.</p>";
    paintAnalysis();
  }

  async function showSavedCv(profile) {
    const label = document.getElementById("cv-file-label");
    let sections = null;
    try {
      sections = profile.cvSections ? JSON.parse(profile.cvSections) : null;
    } catch (_) {}
    if (!profile.cvFileName) {
      if (label) label.textContent = "";
      showCvEmpty();
      return;
    }
    if (label) label.textContent = profile.cvFileName;
    document.getElementById("cv-workbench").hidden = false;
    document.getElementById("cv-empty").innerHTML = "";
    fillSections(sections);
    try {
      const token = HireLinkAPI.getToken();
      const res = await fetch(HireLinkAPI.API_BASE + "/me/cv", {
        headers: token ? { Authorization: "Bearer " + token } : {},
      });
      if (!res.ok) return;
      const blob = await res.blob();
      if (cvObjectUrl) URL.revokeObjectURL(cvObjectUrl);
      cvObjectUrl = URL.createObjectURL(blob);
      const name = profile.cvFileName.toLowerCase();
      if (/\.(png|jpe?g|webp|gif)$/.test(name)) showDocument(cvObjectUrl, "image");
      else if (name.endsWith(".pdf")) showDocument(cvObjectUrl, "pdf");
      else showDocument(await blob.text(), "text");
    } catch (_) {}
  }

  async function load() {
    try {
      const p = await HireLinkAPI.api("/me");
      meId = p.id || meId;
      document.getElementById("pf-name").textContent = p.fullName || "Profil";
      document.getElementById("pf-email").textContent = p.email || "";
      document.getElementById("pf-skills").value = p.skills || "";
      mountSkills(p.skills || "");
      document.getElementById("pf-roles").value = p.soughtRoles || "";
      document.getElementById("pf-loc").value = p.preferredLocations || "";
      document.getElementById("pf-contracts").value = p.preferredContracts || "";
      document.getElementById("pf-level").value = p.experienceLevel || "";
      paintChips("pf-role-chips", "pf-roles", []);
      paintChips("pf-loc-chips", "pf-loc", []);
      paintChips("pf-contract-chips", "pf-contracts", CONTRACT_CHOICES);
      bindSuggest("pf-roles", "pf-role-chips", "job");
      bindSuggest("pf-loc", "pf-loc-chips", "place");
      paintFacts(p);
      showPhoto(localStorage.getItem(photoKey()), p.fullName);
      if (location.hash === "#photo-upload") {
        const node = document.getElementById("photo-upload");
        if (node) node.scrollIntoView({ behavior: "smooth", block: "start" });
      }
      await showSavedCv(p);
      setDirty(false);
    } catch (err) {
      HireLink.toast("Profil", err.message, "danger");
    }
  }

  document.getElementById("profile-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const skills =
      (skillsApi && skillsApi.getCombinedCsv()) ||
      document.getElementById("pf-skills").value;
    const btn = document.getElementById("btn-save-profile");
    if (btn) btn.disabled = true;
    try {
      await HireLinkAPI.api("/me", {
        method: "PATCH",
        body: {
          skills,
          soughtRoles: document.getElementById("pf-roles").value,
          preferredLocations: document.getElementById("pf-loc").value,
          preferredContracts: document.getElementById("pf-contracts").value,
          experienceLevel: document.getElementById("pf-level").value,
        },
      });
      setDirty(false);
      HireLink.toast("Profil enregistré", "Vos offres seront mises à jour", "ok");
    } catch (err) {
      HireLink.toast("Erreur", err.message, "danger");
    } finally {
      if (btn) btn.disabled = false;
    }
  });

  const photoBtn = document.getElementById("btn-photo");
  const photoFile = document.getElementById("pf-photo-file");
  if (photoBtn) photoBtn.addEventListener("click", openPhotoPicker);
  if (photoFile) photoFile.addEventListener("change", (e) => {
    const file = e.target.files && e.target.files[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      const img = new Image();
      img.onload = () => {
        const size = 320;
        const canvas = document.createElement("canvas");
        canvas.width = size;
        canvas.height = size;
        const ctx = canvas.getContext("2d");
        const side = Math.min(img.width, img.height);
        ctx.drawImage(img, (img.width - side) / 2, (img.height - side) / 2, side, side, 0, 0, size, size);
        const url = canvas.toDataURL("image/jpeg", 0.82);
        try {
          localStorage.setItem(photoKey(), url);
        } catch (_) {
          HireLink.toast("Photo", "Image trop lourde pour ce navigateur", "danger");
          return;
        }
        showPhoto(url, document.getElementById("pf-name").textContent);
      };
      img.src = reader.result;
    };
    reader.readAsDataURL(file);
  });

  document.getElementById("cv-file").addEventListener("change", async (e) => {
    const file = e.target.files && e.target.files[0];
    if (!file) return;
    pendingFile = file;
    document.getElementById("cv-file-label").textContent = file.name + " · lecture en cours";
    const fd = new FormData();
    fd.append("file", file);
    try {
      const token = HireLinkAPI.getToken();
      const res = await fetch(HireLinkAPI.API_BASE + "/ocr/cv?applySkills=false", {
        method: "POST",
        headers: token ? { Authorization: "Bearer " + token } : {},
        body: fd,
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.message || data.error || "Lecture échouée");
      document.getElementById("cv-workbench").hidden = false;
      document.getElementById("cv-empty").innerHTML = "";
      document.getElementById("cv-file-label").textContent = file.name + " · " + (data.chars || 0) + " caractères lus";
      fillSections(data.sections || {});
      if (cvObjectUrl) URL.revokeObjectURL(cvObjectUrl);
      cvObjectUrl = URL.createObjectURL(file);
      const name = file.name.toLowerCase();
      if (file.type.indexOf("image/") === 0 || /\.(png|jpe?g|webp|gif)$/.test(name)) showDocument(cvObjectUrl, "image");
      else if (name.endsWith(".pdf") || file.type === "application/pdf") showDocument(cvObjectUrl, "pdf");
      else showDocument(data.textPreview || "", "text");
      HireLink.toast("CV lu", "Relisez les sections, puis confirmez", "ok");
    } catch (err) {
      HireLink.toast("Import CV", err.message, "danger");
    }
  });

  document.getElementById("cv-sections").addEventListener("submit", async (e) => {
    e.preventDefault();
    const btn = document.getElementById("btn-confirm-cv");
    if (btn) btn.disabled = true;
    const fd = new FormData();
    if (pendingFile) fd.append("file", pendingFile);
    fd.append("sections", JSON.stringify(readSections()));
    try {
      const token = HireLinkAPI.getToken();
      const res = await fetch(HireLinkAPI.API_BASE + "/me/cv", {
        method: "POST",
        headers: token ? { Authorization: "Bearer " + token } : {},
        body: fd,
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.message || data.error || "Confirmation impossible");
      pendingFile = null;
      if (data.skills) {
        document.getElementById("pf-skills").value = data.skills;
        mountSkills(data.skills);
      }
      const box = document.getElementById("cv-empty");
      if (box && window.HireLinkArt) {
        box.innerHTML = HireLinkArt.sceneHtml("success") + "<p class='muted'>Sections ajoutées au profil.</p>";
      }
      HireLink.toast("CV confirmé", "Les sections font partie du profil", "ok");
    } catch (err) {
      HireLink.toast("CV", err.message, "danger");
    } finally {
      if (btn) btn.disabled = false;
    }
  });

  const prem = document.getElementById("btn-premium");
  if (prem) prem.onclick = () => HireLinkGrowth && HireLinkGrowth.showPremium("candidate");

  const coachLog = document.getElementById("coach-log");
  function cvText() {
    const parts = ["identity", "experience", "education", "skills", "languages"].map((key) => {
      const el = document.getElementById("cv-" + (key === "skills" ? "skills-read" : key));
      return el && el.value ? key + " : " + el.value : "";
    }).filter(Boolean);
    return parts.join("\n") || (document.getElementById("pf-skills") || {}).value || "";
  }
  function pushCoach(who, text) {
    if (!coachLog) return;
    coachLog.hidden = false;
    coachLog.innerHTML = "";
    const row = document.createElement("article");
    row.className = "coach-msg";
    const title = document.createElement("strong");
    title.textContent = who;
    const body = document.createElement("div");
    body.textContent = text;
    row.appendChild(title);
    row.appendChild(body);
    coachLog.appendChild(row);
  }

  const FINDINGS = {
    skills: {
      label: "Compétences",
      points: function (s) {
        const n = (s.skills || "").split(/[,;\n]/).filter(function (x) { return x.trim(); }).length;
        if (!s.skills) return ["Ajoutez une liste de compétences concrètes, pas seulement le titre du poste.", "Nommez les outils que vous utilisez vraiment."];
        if (n < 4) return ["La liste est courte. Ajoutez les outils et les langages déjà pratiqués."];
        return ["Les compétences sont lisibles. Gardez-les alignées avec le métier visé."];
      },
    },
    experience: {
      label: "Expériences",
      points: function (s) {
        if (!s.experience || s.experience.length < 40) return ["Décrivez au moins une expérience avec le contexte, votre rôle et un résultat.", "Évitez une liste de tâches sans ce que vous avez produit."];
        return ["Les expériences ont assez de matière. Précisez un résultat chiffré quand vous en avez un."];
      },
    },
    education: {
      label: "Formation",
      points: function (s) {
        if (!s.education) return ["Indiquez l'école, le diplôme et l'année prévue ou obtenue."];
        return ["La formation est présente. Vérifiez que l'école et l'année sont explicites."];
      },
    },
    structure: {
      label: "Structure",
      points: function (s) {
        const missing = ["identity", "experience", "education", "skills"].filter(function (k) { return !s[k]; });
        if (missing.length) return ["Il manque encore : " + missing.join(", ") + ".", "Un CV lisible sépare identité, formation, expériences et compétences."];
        return ["Les grandes parties sont là. Le recruteur peut les parcourir sans chercher."];
      },
    },
    match: {
      label: "Compatibilité",
      points: function (s) {
        const role = (document.getElementById("pf-roles") || {}).value || "";
        if (!role) return ["Indiquez le métier recherché. Le classement des offres s'appuie dessus."];
        if (!s.skills) return ["Sans compétences, le score de compatibilité avec les offres reste faible."];
        return ["Le métier visé et les compétences peuvent être comparés aux offres. La synthèse ne postule pas."];
      },
    },
  };

  let analysisTab = "skills";

  function sectionState() {
    return {
      identity: (document.getElementById("cv-identity") || {}).value || "",
      experience: (document.getElementById("cv-experience") || {}).value || "",
      education: (document.getElementById("cv-education") || {}).value || "",
      skills: (document.getElementById("cv-skills-read") || {}).value || (document.getElementById("pf-skills") || {}).value || "",
      languages: (document.getElementById("cv-languages") || {}).value || "",
    };
  }

  function scoreBreakdown(s) {
    const skillsN = s.skills.split(/[,;\n]/).filter(function (x) { return x.trim(); }).length;
    const identity = s.identity.trim().length > 8 ? 15 : 0;
    const experience = s.experience.trim().length > 40 ? 25 : 0;
    const education = s.education.trim().length > 8 ? 20 : 0;
    const skills = Math.min(25, skillsN * 5);
    const languages = s.languages.trim() ? 15 : 0;
    const total = Math.max(0, Math.min(100, identity + experience + education + skills + languages));
    return { identity, experience, education, skills, languages, total, skillsN };
  }

  function scoreOf(s) {
    return scoreBreakdown(s).total;
  }

  function paintAnalysis() {
    const s = sectionState();
    const parts = scoreBreakdown(s);
    const score = parts.total;
    const scoreEl = document.getElementById("cv-score");
    const bar = document.getElementById("cv-score-bar");
    const level = document.getElementById("cv-level");
    const legend = document.getElementById("cv-score-legend");
    if (scoreEl) scoreEl.textContent = score + "/100";
    if (bar) bar.style.width = score + "%";
    if (level) {
      const grade = score >= 80 ? "Niveau A" : score >= 55 ? "Niveau B" : "Niveau C";
      level.textContent = grade;
    }
    if (legend) {
      legend.innerHTML =
        "<strong>Comment le score est calculé</strong>" +
        "Identité " + parts.identity + "/15 · Expériences " + parts.experience + "/25 · Formation " + parts.education + "/20 · " +
        "Compétences " + parts.skills + "/25 · Langues " + parts.languages + "/15.<br/>" +
        (score < 55
          ? "Niveau C : complétez les sections Expériences et Formation dans « Voir le détail », puis confirmez. La synthèse lit ces textes."
          : score < 80
            ? "Niveau B : le CV est exploitable. Ajoutez un résultat chiffré et les langues pour viser A."
            : "Niveau A : le CV est prêt pour la lecture recruteur et le matching.");
    }
    const tabs = document.getElementById("cv-tabs");
    if (tabs && !tabs.dataset.ready) {
      tabs.dataset.ready = "1";
      tabs.innerHTML = Object.keys(FINDINGS).map(function (key) {
        return '<button type="button" data-tab="' + key + '">' + FINDINGS[key].label + "</button>";
      }).join("");
      tabs.addEventListener("click", function (e) {
        const btn = e.target.closest("[data-tab]");
        if (!btn) return;
        analysisTab = btn.dataset.tab;
        paintAnalysis();
      });
    }
    if (tabs) {
      tabs.querySelectorAll("[data-tab]").forEach(function (btn) {
        btn.classList.toggle("is-on", btn.dataset.tab === analysisTab);
      });
    }
    const list = document.getElementById("cv-findings");
    if (list) {
      const points = FINDINGS[analysisTab].points(s);
      list.innerHTML = points.map(function (text) {
        return "<li><span>" + text.replace(/</g, "") + '</span><button type="button" class="cv-fix" data-fix="' + analysisTab + '">à corriger</button></li>';
      }).join("");
    }
  }

  document.addEventListener("click", function (e) {
    const fix = e.target.closest(".cv-fix");
    if (!fix) return;
    const map = { skills: "cv-skills-read", experience: "cv-experience", education: "cv-education", structure: "cv-identity", match: "pf-roles" };
    const target = document.getElementById(map[fix.dataset.fix] || "cv-identity");
    const details = document.querySelector(".cv-edit");
    if (details) details.open = true;
    if (target) target.focus();
  });

  let cvZoom = 100;
  function applyZoom() {
    const scale = document.getElementById("cv-scale");
    const label = document.getElementById("cv-zoom-label");
    if (scale) scale.style.transform = "scale(" + (cvZoom / 100) + ")";
    if (label) label.textContent = cvZoom + "%";
  }
  const zoomIn = document.getElementById("cv-zoom-in");
  const zoomOut = document.getElementById("cv-zoom-out");
  if (zoomIn) zoomIn.addEventListener("click", function () { cvZoom = Math.min(160, cvZoom + 10); applyZoom(); });
  if (zoomOut) zoomOut.addEventListener("click", function () { cvZoom = Math.max(70, cvZoom - 10); applyZoom(); });
  const more = document.getElementById("cv-more");
  if (more) more.addEventListener("click", function () {
    const details = document.querySelector(".cv-edit");
    if (details) details.open = true;
    details.scrollIntoView({ behavior: "smooth", block: "start" });
  });
  async function askCoach(message) {
    const text = cvText();
    if (!text.trim()) {
      HireLink.toast("Synthèse", "Déposez d’abord un CV et confirmez les sections.", "info");
      return;
    }
    const send = document.getElementById("coach-analyze");
    if (send) send.disabled = true;
    try {
      const data = await HireLinkAPI.api("/ai/cv-summary", {
        method: "POST",
        headers: HireLinkProduct ? HireLinkProduct.premiumHeaders("candidate") : {},
        body: {
          cvText: text,
          premium: !!(HireLinkProduct && HireLinkProduct.isPremium("candidate")),
        },
      });
      pushCoach("Synthèse", data.content || "Pas de synthèse.");
    } catch (err) {
      HireLink.toast("Synthèse", err.message, "danger");
    } finally {
      if (send) send.disabled = false;
    }
  }
  const coachForm = document.getElementById("coach-form");
  if (coachForm) {
    coachForm.addEventListener("submit", (e) => {
      e.preventDefault();
      const input = document.getElementById("coach-input");
      const message = (input.value || "").trim();
      if (!message) return;
      input.value = "";
      askCoach(message);
    });
  }
  const analyze = document.getElementById("coach-analyze");
  if (analyze) {
    analyze.addEventListener("click", () => askCoach("Quelles améliorations concrètes apporter à ce CV ?"));
  }

  bindDirty();
  setDirty(false);
  load();
})();
