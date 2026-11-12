(function () {
  const session = HireLinkApp.mount({});
  if (!session) return;

  let dirty = false;
  const saveBar = document.getElementById("profile-save-bar");

  function setDirty(v) {
    dirty = !!v;
    if (saveBar) saveBar.classList.toggle("is-clean", !dirty);
  }

  function bindDirty() {
    ["pf-level"].forEach((id) => {
      const node = document.getElementById(id);
      if (node) node.addEventListener("input", () => setDirty(true));
      if (node) node.addEventListener("change", () => setDirty(true));
    });
  }

  async function showAi(endpoint, body) {
    const box = document.getElementById("ai-result");
    if (!box) return;
    box.hidden = false;
    box.innerHTML = Mascotte.waiting("Génération en cours…");
    try {
      const data = await HireLinkCredits.run(endpoint, body);
      const row = document.createElement("article");
      row.className = "coach-msg";
      const title = document.createElement("strong");
      title.textContent = data.title || "Suggestions de l’IA";
      const content = document.createElement("div");
      content.textContent = data.content || "";
      row.append(title, content);
      if (data.tips && data.tips.length) {
        const tips = document.createElement("p");
        tips.className = "muted";
        tips.textContent = data.tips.join(" · ");
        row.appendChild(tips);
      }
      box.replaceChildren(row);
    } catch (err) {
      box.hidden = true;
      if (!err.handled) HireLink.toast("IA", err.message, "danger");
    }
  }

  function paintSkillBoxes() {
    HireLinkSkills.renderBoxes(document.getElementById("pf-skill-boxes"), document.getElementById("pf-skills").value,
      { empty: "Ajoutez vos compétences : elles seront classées automatiquement par catégorie." });
  }

  function mountSkills(csv) {
    document.getElementById("pf-skills").value = csv;
    HireLinkSkills.mount(document.getElementById("pf-skills-picker"), {
      value: csv,
      onChange: (v) => {
        document.getElementById("pf-skills").value = v;
        paintSkillBoxes();
        setDirty(true);
      },
    });
    paintSkillBoxes();
  }

  const MODES = ["Remote", "Hybride", "Présentiel"];
  const pickers = {};

  let meId = session.userId || session.id || "me";
  let pendingFile = null;
  let cvObjectUrl = "";

  function photoKey() {
    return "hirelink.photo." + meId;
  }

  function initials(name) {
    const parts = String(name || "C").trim().split(/\s+/).filter(Boolean);
    return ((parts[0] || "C")[0] + (parts[1] ? parts[1][0] : "")).toUpperCase();
  }

  function showPhoto(dataUrl, name) {
    const img = document.getElementById("pf-photo");
    const fallback = document.getElementById("pf-photo-fallback");
    if (dataUrl && img) {
      img.src = dataUrl;
      img.hidden = false;
      if (fallback) fallback.hidden = true;
    } else {
      if (img) img.hidden = true;
      if (fallback) {
        fallback.hidden = false;
        fallback.textContent = initials(name);
      }
    }
    if (window.HireLinkApp) HireLinkApp.paintProfile();
  }

  function openPhotoPicker() {
    const input = document.getElementById("pf-photo-file");
    if (input) input.click();
  }

  /**
   * Sélection multiple : pastilles retirables, propositions pendant la saisie (avec drapeaux pour les lieux),
   * saisie libre acceptée avec Entrée, et choix rapides (modes de travail, types de contrat).
   * La valeur est conservée dans un champ caché, séparée par des virgules.
   */
  function multiPicker(rootId, hiddenId, o) {
    const root = document.getElementById(rootId);
    const hidden = document.getElementById(hiddenId);
    root.classList.add("mp");
    const selected = document.createElement("div");
    selected.className = "choice-chips";
    selected.setAttribute("aria-live", "polite");
    const quick = document.createElement("div");
    quick.className = "choice-chips";
    root.append(selected);
    let input = null;
    if (o.kind) {
      input = document.createElement("input");
      input.type = "text";
      input.className = "form-control mp-input";
      input.placeholder = o.placeholder;
      input.setAttribute("aria-label", o.label);
      root.append(input);
    }
    if (o.quick) root.append(quick);

    const values = () => hidden.value.split(",").map((x) => x.trim()).filter(Boolean);
    const has = (v) => values().some((x) => HireLinkSearch.compact(x) === HireLinkSearch.compact(v));
    function write(list) {
      hidden.value = list.join(", ");
      setDirty(true);
      paint();
    }
    function toggle(v) {
      const list = values();
      const i = list.findIndex((x) => HireLinkSearch.compact(x) === HireLinkSearch.compact(v));
      if (i >= 0) list.splice(i, 1);
      else list.push(v);
      write(list);
    }
    function chip(label, on, flag) {
      const b = document.createElement("button");
      b.type = "button";
      b.className = "choice-chip" + (on ? " is-on" : "");
      b.setAttribute("aria-pressed", on ? "true" : "false");
      b.textContent = (flag ? flag + " " : "") + label;
      b.addEventListener("click", () => toggle(label));
      return b;
    }
    function paint() {
      selected.replaceChildren();
      values().forEach((v) => {
        const b = chip(v, true, o.flags ? HireLinkPlaces.flagOf(v) : "");
        b.textContent += "  ✕";
        b.setAttribute("aria-label", "Retirer " + v);
        selected.appendChild(b);
      });
      quick.replaceChildren();
      (o.quick || []).forEach((v) => { if (!has(v)) quick.appendChild(chip(v, false, "")); });
    }
    function add(v) {
      const clean = String(v || "").replace(/\s+/g, " ").trim();
      if (clean && !has(clean)) write(values().concat(clean));
      if (input) input.value = "";
    }
    if (input) {
      HireLinkSearch.combobox(input, {
        minChars: 1,
        emptyText: "Aucune proposition : Entrée pour ajouter votre saisie.",
        fetch: (q) => HireLinkAPI.api("/catalog/suggest?kind=" + o.kind + "&q=" + encodeURIComponent(q))
          .then((rows) => (rows || []).map((r) => ({ key: r.label, label: r.label, flag: r.flag, sub: r.country })))
          .catch(() => []),
        onPick: (item) => add(item.label),
        onEnter: add,
      });
    }
    paint();
    return { refresh: paint };
  }

  function paintFacts(p) {
    const box = document.getElementById("pf-facts");
    if (!box) return;
    const list = (v) => String(v || "").split(",").map((x) => x.trim()).filter(Boolean).join(", ");
    const bits = [p.experienceLevel, list(p.preferredLocations), list(p.preferredContracts)].filter(Boolean);
    box.textContent = bits.join(" · ") || "Complétez la recherche et les compétences.";
  }

  function sectionValue(sections, key) {
    if (!sections) return "";
    const value = sections[key];
    return value == null ? "" : String(value);
  }

  const EXP_KINDS = [
    ["STAGE", "Stage"],
    ["ALTERNANCE", "Alternance"],
    ["EMPLOI", "Emploi"],
    ["PROJET", "Projet académique"]
  ];

  function fillSections(sections, experiences, note) {
    document.getElementById("cv-identity").value = sectionValue(sections, "identity");
    document.getElementById("cv-experience").value = sectionValue(sections, "experience");
    document.getElementById("cv-education").value = sectionValue(sections, "education");
    document.getElementById("cv-skills-read").value = sectionValue(sections, "skills");
    document.getElementById("cv-languages").value = sectionValue(sections, "languages");
    const projects = document.getElementById("cv-projects");
    if (projects) projects.value = sectionValue(sections, "projects");
    const rows = Array.isArray(experiences) ? experiences : (sections && Array.isArray(sections.experiences) ? sections.experiences : []);
    renderExperiences(rows, note || (sections && sections.extractionNote) || "");
    paintAnalysis();
  }

  function experienceSummary(rows) {
    return rows.filter(function (row) { return row.kind !== "PROJET"; }).map(function (row) {
      return [row.kind, row.title, row.organization, row.start ? (row.start + (row.end ? "–" + row.end : row.datesIncomplete ? " (dates incomplètes)" : "")) : ""]
        .filter(Boolean).join(" · ");
    }).join("\n");
  }

  function readExperienceRows() {
    return Array.from(document.querySelectorAll("#cv-exp-list .cv-exp-row")).map(function (row) {
      const value = function (name) { return (row.querySelector("[data-field='" + name + "']") || {}).value || ""; };
      return {
        title: value("title").trim(),
        organization: value("organization").trim(),
        kind: value("kind") || "EMPLOI",
        start: value("start").trim(),
        end: value("end").trim(),
        datesIncomplete: !value("start").trim() || (!value("end").trim() && !/depuis|présent|present/i.test(value("start")))
      };
    }).filter(function (row) { return row.title || row.organization; });
  }

  function renderExperiences(rows, note) {
    const list = document.getElementById("cv-exp-list");
    const noteEl = document.getElementById("cv-exp-note");
    if (!list) return;
    list.innerHTML = "";
    (rows || []).forEach(function (row) { list.appendChild(experienceRow(row)); });
    if (noteEl) noteEl.textContent = note || "";
    syncExperienceText();
  }

  function experienceRow(row) {
    const box = document.createElement("div");
    box.className = "cv-exp-row";
    const field = function (name, label, value) {
      const wrap = document.createElement("label");
      wrap.textContent = label;
      const input = document.createElement("input");
      input.dataset.field = name;
      input.value = value || "";
      input.maxLength = 160;
      input.addEventListener("input", syncExperienceText);
      wrap.appendChild(input);
      return wrap;
    };
    box.appendChild(field("title", "Poste", row.title));
    box.appendChild(field("organization", "Entreprise", row.organization));
    const kindWrap = document.createElement("label");
    kindWrap.appendChild(document.createTextNode("Type"));
    const kind = document.createElement("select");
    kind.dataset.field = "kind";
    EXP_KINDS.forEach(function (item) {
      const option = document.createElement("option");
      option.value = item[0];
      option.textContent = item[1];
      if (item[0] === (row.kind || "EMPLOI")) option.selected = true;
      kind.appendChild(option);
    });
    kind.addEventListener("change", syncExperienceText);
    kindWrap.appendChild(kind);
    box.appendChild(kindWrap);
    box.appendChild(field("start", "Début", row.start));
    box.appendChild(field("end", "Fin", row.end));
    const remove = document.createElement("button");
    remove.type = "button";
    remove.className = "btn btn-ghost btn-sm";
    remove.textContent = "Retirer";
    remove.addEventListener("click", function () { box.remove(); syncExperienceText(); });
    box.appendChild(remove);
    if (!row.organization) {
      const warn = document.createElement("p");
      warn.className = "muted cv-exp-warn";
      warn.textContent = "Entreprise non lue : à compléter, ou à retirer si la détection est fausse.";
      box.appendChild(warn);
    } else if (row.datesIncomplete) {
      const warn = document.createElement("p");
      warn.className = "muted cv-exp-warn";
      warn.textContent = "Dates incomplètes : rien n'a été inventé.";
      box.appendChild(warn);
    }
    return box;
  }

  function syncExperienceText() {
    const summary = experienceSummary(readExperienceRows());
    const field = document.getElementById("cv-experience");
    if (field && summary) field.value = summary;
    paintAnalysis();
  }

  function readSections() {
    const experiences = readExperienceRows();
    const summary = experienceSummary(experiences);
    if (summary) document.getElementById("cv-experience").value = summary;
    return {
      identity: document.getElementById("cv-identity").value.trim(),
      experience: document.getElementById("cv-experience").value.trim(),
      education: document.getElementById("cv-education").value.trim(),
      skills: document.getElementById("cv-skills-read").value.trim(),
      languages: document.getElementById("cv-languages").value.trim(),
      projects: (document.getElementById("cv-projects") || {}).value ? document.getElementById("cv-projects").value.trim() : "",
      experiences: experiences
    };
  }

  function showDocument(url, kind) {
    const frame = document.getElementById("cv-frame");
    const image = document.getElementById("cv-image");
    const raw = document.getElementById("cv-raw");
    const stage = document.getElementById("cv-workbench");
    frame.hidden = kind !== "pdf";
    image.hidden = kind !== "image";
    raw.hidden = kind !== "text";
    if (kind === "pdf") frame.src = url;
    if (kind === "image") image.src = url;
    if (kind === "text") raw.textContent = url || "";
    if (stage) stage.classList.toggle("is-text", kind === "text");
    const open = document.getElementById("cv-open");
    if (open) {
      open.hidden = kind === "text";
      if (kind !== "text") open.href = url;
    }
    const zoom = document.querySelector(".cv-zoom");
    if (zoom) zoom.hidden = kind === "text";
  }

  /** Sans CV déposé, le serveur refuse la confirmation : le bouton reste désactivé avec une explication. */
  function setConfirmCv(enabled) {
    const b = document.getElementById("btn-confirm-cv");
    if (!b) return;
    b.disabled = !enabled;
    b.title = enabled ? "" : "Déposez d’abord un CV (PDF ou image) pour confirmer ses sections.";
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
    setConfirmCv(!!profile.cvFileName);
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
      const res = await HireLinkAPI.authFetch("/me/cv", {
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
      const lvlSel = document.getElementById("pf-level");
      const lvl = p.experienceLevel || "";
      if (lvl && ![].some.call(lvlSel.options, (o) => o.value === lvl)) {
        const opt = document.createElement("option");
        opt.value = lvl;
        opt.textContent = lvl;
        lvlSel.appendChild(opt);
      }
      lvlSel.value = lvl;
      await HireLinkPlaces.ready();
      const contracts = await HireLinkAPI.api("/catalog/suggest?kind=contract&q=").catch(() => []);
      if (!pickers.roles) {
        pickers.roles = multiPicker("pf-role-chips", "pf-roles", { kind: "job", label: "Ajouter un métier recherché", placeholder: "Un métier : Développeuse Full-Stack, Data Analyst…" });
        pickers.loc = multiPicker("pf-loc-chips", "pf-loc", { kind: "place", flags: true, quick: MODES, label: "Ajouter un lieu", placeholder: "Une ville ou un pays : Tétouan, Paris…" });
        pickers.contracts = multiPicker("pf-contract-chips", "pf-contracts", { quick: (contracts || []).map((c) => c.label) });
      }
      Object.values(pickers).forEach((x) => x.refresh());
      paintFacts(p);
      showPhoto(localStorage.getItem(photoKey()), p.fullName);
      await showSavedCv(p);
      setDirty(false);
    } catch (err) {
      HireLink.toast("Profil", err.message, "danger");
    }
  }

  document.getElementById("profile-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    const skills = document.getElementById("pf-skills").value;
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
      toastSaved("Profil enregistré", "Vos offres seront mises à jour");
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
    setConfirmCv(true);
    document.getElementById("cv-file-label").textContent = file.name + " · lecture en cours";
    const fd = new FormData();
    fd.append("file", file);
    try {
      const token = HireLinkAPI.getToken();
      const res = await HireLinkAPI.authFetch("/ocr/cv?applySkills=false", {
        method: "POST",
        body: fd,
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.message || data.error || "Lecture échouée");
      document.getElementById("cv-workbench").hidden = false;
      document.getElementById("cv-empty").innerHTML = "";
      document.getElementById("cv-file-label").textContent = file.name + " · " + (data.chars || 0) + " caractères lus";
      fillSections(data.sections || {}, data.experiences || [], data.extractionNote || "");
      if (cvObjectUrl) URL.revokeObjectURL(cvObjectUrl);
      cvObjectUrl = URL.createObjectURL(file);
      const name = file.name.toLowerCase();
      if (file.type.indexOf("image/") === 0 || /\.(png|jpe?g|webp|gif)$/.test(name)) showDocument(cvObjectUrl, "image");
      else if (name.endsWith(".pdf") || file.type === "application/pdf") showDocument(cvObjectUrl, "pdf");
      else showDocument(data.textPreview || "", "text");
      const edit = document.querySelector("#cv-section .cv-edit") || document.querySelector(".cv-edit");
      if (edit) {
        edit.open = true;
        edit.scrollIntoView({ behavior: "smooth", block: "center" });
      }
      HireLink.toast("CV lu", "Relisez les sections ci-dessous, puis confirmez", "ok");
    } catch (err) {
      document.getElementById("cv-file-label").textContent = file.name + " · " + err.message;
      HireLink.toast("Import CV", err.message, "danger");
    }
  });

  const addExperience = document.getElementById("cv-exp-add");
  if (addExperience) addExperience.addEventListener("click", function () {
    const list = document.getElementById("cv-exp-list");
    if (list) list.appendChild(experienceRow({ kind: "EMPLOI", datesIncomplete: true }));
    syncExperienceText();
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
      const res = await HireLinkAPI.authFetch("/me/cv", {
        method: "POST",
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
      if (box) box.innerHTML = "<p class='muted'>Sections ajoutées au profil.</p>";
      toastSaved("CV confirmé", "Les sections font partie du profil");
    } catch (err) {
      HireLink.toast("CV", err.message, "danger");
    } finally {
      if (btn) btn.disabled = false;
    }
  });


  const coachLog = document.getElementById("coach-log");
  function cvText() {
    const parts = ["identity", "experience", "education", "skills", "languages"].map((key) => {
      const el = document.getElementById("cv-" + (key === "skills" ? "skills-read" : key));
      return el && el.value ? key + " : " + el.value : "";
    }).filter(Boolean);
    return parts.join("\n") || (document.getElementById("pf-skills") || {}).value || "";
  }
  function pushCoach(who, text, keep) {
    if (!coachLog) return;
    coachLog.hidden = false;
    if (!keep) coachLog.innerHTML = "";
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
        if (!s.skills) return ["Correspondance non disponible tant qu'aucune compétence n'est confirmée. Le métier recherché n'entre pas dans le score."];
        return ["Les compétences confirmées alimentent le score, avec le niveau, le contrat et le lieu s'ils sont renseignés. Le métier recherché ne classe pas les offres."];
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

  /** Profil à 100 % : grand moment (une seule fois par compte) ; sinon simple confirmation. */
  function toastSaved(title, message) {
    const complete = scoreOf(sectionState()) === 100 &&
      Mascotte.celebrate({ cle: "profil-complet", title: "Profil à 100 % !", description: "Votre dossier est complet : les recruteurs vous découvrent sous votre meilleur jour." });
    if (!complete) HireLink.toast(title, message, "ok");
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
        "Compétences " + parts.skills + "/25 · Langues " + parts.languages + "/15. " +
        "Ce score mesure la lecture du CV, pas la correspondance avec une offre.<br/>" +
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
      HireLink.toast("Coach", "Déposez d’abord un CV et confirmez les sections.", "info");
      return;
    }
    const send = document.querySelector("#coach-form button[type=submit]");
    if (send) send.disabled = true;
    pushCoach("Vous", message, true);
    try {
      const data = await HireLinkCredits.run("/ai/coach", { message: message, cvText: text });
      const local = data.provider && String(data.provider).toLowerCase().indexOf("local") === 0;
      pushCoach(local ? "Coach local" : "Coach", data.content || "Pas de réponse.", true);
    } catch (err) {
      if (!err.handled) HireLink.toast("Coach", err.message, "danger");
    } finally {
      if (send) send.disabled = false;
    }
  }
  async function summarizeCv() {
    const text = cvText();
    if (!text.trim()) {
      HireLink.toast("Synthèse", "Déposez d’abord un CV et confirmez les sections.", "info");
      return;
    }
    const send = document.getElementById("coach-analyze");
    if (send) send.disabled = true;
    try {
      const data = await HireLinkCredits.run("/ai/cv-summary", { cvText: text });
      const local = data.provider && String(data.provider).toLowerCase().indexOf("local") === 0;
      pushCoach(local ? "Synthèse locale" : "Synthèse", data.content || "Pas de synthèse.");
    } catch (err) {
      if (!err.handled) HireLink.toast("Synthèse", err.message, "danger");
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
  if (analyze) analyze.addEventListener("click", summarizeCv);

  const rewrite = document.getElementById("profile-rewrite");
  if (rewrite) {
    rewrite.addEventListener("click", () => {
      rewrite.disabled = true;
      showAi("/ai/profile-rewrite", {
        skills: document.getElementById("pf-skills").value,
        roles: document.getElementById("pf-roles").value,
        level: document.getElementById("pf-level").value,
      }).finally(() => { rewrite.disabled = false; });
    });
  }

  function setProfileView(view) {
    const isCv = view === "cv";
    const vp = document.getElementById("view-profile");
    const vc = document.getElementById("view-cv");
    if (vp) vp.classList.toggle("is-hidden", isCv);
    if (vc) vc.classList.toggle("is-hidden", !isCv);
    document.querySelectorAll("#profile-view-seg [data-profile-view]").forEach((b) => {
      const on = b.getAttribute("data-profile-view") === view;
      b.classList.toggle("is-on", on);
      b.setAttribute("aria-selected", on ? "true" : "false");
    });
    try {
      history.replaceState(null, "", isCv ? "#cv" : "#profil");
    } catch (_) {}
  }
  const viewSeg = document.getElementById("profile-view-seg");
  if (viewSeg) {
    viewSeg.addEventListener("click", (e) => {
      const btn = e.target.closest("[data-profile-view]");
      if (btn) setProfileView(btn.getAttribute("data-profile-view"));
    });
  }
  if (location.hash === "#cv") setProfileView("cv");

  bindDirty();
  setDirty(false);
  load();
  HireLinkCredits.paintCosts();
})();
