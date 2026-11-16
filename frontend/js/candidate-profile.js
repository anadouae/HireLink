(function () {
  const session = HireLinkApp.mount({});
  if (!session) return;

  let dirty = false;
  const saveBar = document.getElementById("profile-save-bar");
  let experiences = [];
  let educations = [];
  let extractProposals = [];
  let synthesizeBusy = false;

  function setDirty(v) {
    dirty = !!v;
    if (saveBar) saveBar.classList.toggle("is-clean", !dirty);
  }

  function bindDirty() {
    ["pf-level", "pf-first", "pf-last", "pf-phone", "pf-city", "pf-country", "pf-job-title", "pf-presentation", "pf-availability", "pf-email-edit"].forEach((id) => {
      const node = document.getElementById(id);
      if (!node) return;
      node.addEventListener("input", () => setDirty(true));
      node.addEventListener("change", () => setDirty(true));
    });
    const emailEdit = document.getElementById("pf-email-edit");
    const emailBox = document.getElementById("pf-email-change");
    if (emailEdit && emailBox) {
      emailEdit.addEventListener("input", () => {
        const current = (document.getElementById("pf-email") || {}).textContent || "";
        emailBox.hidden = emailEdit.value.trim().toLowerCase() === current.trim().toLowerCase();
      });
    }
  }

  async function showAi(endpoint, body) {
    const box = document.getElementById("ai-result");
    if (!box) return;
    box.hidden = false;
    box.innerHTML = Mascotte.waiting("Génération en cours…");
    try {
      const data = await HireLinkCredits.run(endpoint, body, { timeoutMs: 60000 });
      const row = document.createElement("article");
      row.className = "coach-msg";
      const title = document.createElement("strong");
      title.textContent = data.title || "Suggestions";
      const content = document.createElement("div");
      content.textContent = data.content || "";
      row.append(title, content);
      if (data.tips && data.tips.length) {
        const tips = document.createElement("p");
        tips.className = "muted";
        tips.textContent = data.tips.join(" · ");
        row.appendChild(tips);
      }
      const applyHint = document.createElement("p");
      applyHint.className = "muted";
      applyHint.textContent = "Copiez ce qui vous convient dans le formulaire, puis enregistrez.";
      row.appendChild(applyHint);
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
        paintProgress();
      },
    });
    paintSkillBoxes();
  }

  const MODES = ["Remote", "Hybride", "Présentiel"];
  const pickers = {};

  let meId = session.userId || session.id || "me";
  let pendingFile = null;
  let cvObjectUrl = "";
  let hasCv = false;

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
      paintProgress();
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
    const bits = [p.jobTitle || p.experienceLevel, list(p.preferredLocations), list(p.preferredContracts)].filter(Boolean);
    box.textContent = bits.join(" · ") || "Complétez la recherche et les compétences.";
  }

  function paintProgress() {
    const el = document.getElementById("pf-progress");
    if (!el) return;
    const checks = [
      { ok: !!(document.getElementById("pf-first") || {}).value && !!(document.getElementById("pf-last") || {}).value, label: "identité" },
      { ok: experiences.length > 0, label: "expérience" },
      { ok: educations.length > 0, label: "formation" },
      { ok: ((document.getElementById("pf-skills") || {}).value || "").split(/[,;]/).filter((x) => x.trim()).length >= 3, label: "compétences" },
      { ok: !!(document.getElementById("pf-roles") || {}).value, label: "métiers" },
      { ok: hasCv, label: "CV" },
    ];
    const done = checks.filter((c) => c.ok).length;
    const missing = checks.filter((c) => !c.ok).map((c) => c.label);
    el.textContent = missing.length
      ? "Progression : " + done + "/" + checks.length + " — il reste : " + missing.join(", ") + "."
      : "Dossier prêt : identité, parcours, compétences et CV sont en place.";
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

  function careerField(label, value, opts) {
    const wrap = document.createElement("label");
    if (opts && opts.full) wrap.className = "field-full";
    wrap.appendChild(document.createTextNode(label));
    let input;
    if (opts && opts.area) {
      input = document.createElement("textarea");
      input.rows = opts.rows || 2;
      input.value = value || "";
      if (opts.max) input.maxLength = opts.max;
      input.className = "form-control";
    } else if (opts && opts.check) {
      input = document.createElement("input");
      input.type = "checkbox";
      input.checked = !!value;
    } else {
      input = document.createElement("input");
      input.type = (opts && opts.type) || "text";
      input.value = value || "";
      if (opts && opts.max) input.maxLength = opts.max;
      input.className = "form-control";
    }
    input.dataset.field = opts.field;
    input.addEventListener("input", () => { setDirty(true); syncCareerFromDom(); });
    input.addEventListener("change", () => { setDirty(true); syncCareerFromDom(); });
    wrap.appendChild(input);
    return wrap;
  }

  function syncCareerFromDom() {
    experiences = Array.from(document.querySelectorAll("#pf-exp-list .career-card")).map((card) => {
      const v = (f) => { const n = card.querySelector("[data-field='" + f + "']"); return n ? (n.type === "checkbox" ? n.checked : n.value.trim()) : ""; };
      return {
        title: v("title"),
        company: v("company"),
        location: v("location"),
        startDate: v("startDate"),
        endDate: v("endDate"),
        current: !!v("current"),
        missions: v("missions"),
        achievements: v("achievements"),
      };
    }).filter((r) => r.title || r.company);
    educations = Array.from(document.querySelectorAll("#pf-edu-list .career-card")).map((card) => {
      const v = (f) => { const n = card.querySelector("[data-field='" + f + "']"); return n ? n.value.trim() : ""; };
      return {
        school: v("school"),
        degree: v("degree"),
        domain: v("domain"),
        startDate: v("startDate"),
        endDate: v("endDate"),
        description: v("description"),
      };
    }).filter((r) => r.school || r.degree);
    const ee = document.getElementById("pf-exp-empty");
    const ed = document.getElementById("pf-edu-empty");
    if (ee) ee.hidden = experiences.length > 0;
    if (ed) ed.hidden = educations.length > 0;
    paintProgress();
  }

  function renderCareer() {
    const expList = document.getElementById("pf-exp-list");
    const eduList = document.getElementById("pf-edu-list");
    if (expList) {
      expList.replaceChildren();
      experiences.forEach((row, idx) => {
        const card = document.createElement("div");
        card.className = "career-card";
        const grid = document.createElement("div");
        grid.className = "career-grid";
        grid.append(
          careerField("Poste", row.title, { field: "title", max: 120 }),
          careerField("Entreprise", row.company, { field: "company", max: 120 }),
          careerField("Lieu", row.location, { field: "location", max: 120 }),
          careerField("Début", row.startDate, { field: "startDate", type: "month" }),
          careerField("Fin", row.endDate, { field: "endDate", type: "month" }),
          careerField("Poste actuel", row.current, { field: "current", check: true }),
          careerField("Missions", row.missions, { field: "missions", area: true, full: true, max: 2000 }),
          careerField("Réalisations", row.achievements, { field: "achievements", area: true, full: true, max: 2000 })
        );
        card.appendChild(grid);
        const actions = document.createElement("div");
        actions.className = "career-actions";
        const improve = document.createElement("button");
        improve.type = "button";
        improve.className = "btn btn-ghost btn-sm";
        improve.textContent = "Reformuler (IA)";
        improve.setAttribute("data-ai-cost", "");
        improve.addEventListener("click", () => {
          const draft = [row.title, row.company, row.missions, row.achievements].filter(Boolean).join("\n");
          if (!draft.trim()) {
            HireLink.toast("IA", "Renseignez d’abord le poste ou les missions.", "info");
            return;
          }
          improve.disabled = true;
          showAi("/ai/profile-rewrite", { experienceDraft: draft }).finally(() => { improve.disabled = false; });
        });
        const remove = document.createElement("button");
        remove.type = "button";
        remove.className = "btn btn-ghost btn-sm";
        remove.textContent = "Supprimer";
        remove.addEventListener("click", () => {
          experiences.splice(idx, 1);
          setDirty(true);
          renderCareer();
        });
        actions.append(improve, remove);
        card.appendChild(actions);
        expList.appendChild(card);
      });
    }
    if (eduList) {
      eduList.replaceChildren();
      educations.forEach((row, idx) => {
        const card = document.createElement("div");
        card.className = "career-card";
        const grid = document.createElement("div");
        grid.className = "career-grid";
        grid.append(
          careerField("Établissement", row.school, { field: "school", max: 160 }),
          careerField("Diplôme", row.degree, { field: "degree", max: 120 }),
          careerField("Domaine", row.domain, { field: "domain", max: 120 }),
          careerField("Début", row.startDate, { field: "startDate", type: "month" }),
          careerField("Fin", row.endDate, { field: "endDate", type: "month" }),
          careerField("Description", row.description, { field: "description", area: true, full: true, max: 1000 })
        );
        card.appendChild(grid);
        const actions = document.createElement("div");
        actions.className = "career-actions";
        const remove = document.createElement("button");
        remove.type = "button";
        remove.className = "btn btn-ghost btn-sm";
        remove.textContent = "Supprimer";
        remove.addEventListener("click", () => {
          educations.splice(idx, 1);
          setDirty(true);
          renderCareer();
        });
        actions.appendChild(remove);
        card.appendChild(actions);
        eduList.appendChild(card);
      });
    }
    syncCareerFromDom();
    HireLinkCredits.paintCosts(document.getElementById("view-profile"));
  }

  function fillSections(sections, experienceRows, note) {
    document.getElementById("cv-identity").value = sectionValue(sections, "identity");
    document.getElementById("cv-experience").value = sectionValue(sections, "experience");
    document.getElementById("cv-education").value = sectionValue(sections, "education");
    document.getElementById("cv-skills-read").value = sectionValue(sections, "skills");
    document.getElementById("cv-languages").value = sectionValue(sections, "languages");
    const projects = document.getElementById("cv-projects");
    if (projects) projects.value = sectionValue(sections, "projects");
    const rows = Array.isArray(experienceRows) ? experienceRows : (sections && Array.isArray(sections.experiences) ? sections.experiences : []);
    renderCvExperiences(rows, note || (sections && sections.extractionNote) || "");
    buildExtractProposals(sections || {}, rows);
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

  function renderCvExperiences(rows, note) {
    const list = document.getElementById("cv-exp-list");
    const noteEl = document.getElementById("cv-exp-note");
    if (!list) return;
    list.innerHTML = "";
    (rows || []).forEach(function (row) { list.appendChild(cvExperienceRow(row)); });
    if (noteEl) noteEl.textContent = note || "";
    syncExperienceText();
  }

  function cvExperienceRow(row) {
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
    const experienceRows = readExperienceRows();
    const summary = experienceSummary(experienceRows);
    if (summary) document.getElementById("cv-experience").value = summary;
    return {
      identity: document.getElementById("cv-identity").value.trim(),
      experience: document.getElementById("cv-experience").value.trim(),
      education: document.getElementById("cv-education").value.trim(),
      skills: document.getElementById("cv-skills-read").value.trim(),
      languages: document.getElementById("cv-languages").value.trim(),
      projects: (document.getElementById("cv-projects") || {}).value ? document.getElementById("cv-projects").value.trim() : "",
      experiences: experienceRows
    };
  }

  function buildExtractProposals(sections, rows) {
    extractProposals = [];
    const skillsNow = (document.getElementById("pf-skills") || {}).value || "";
    const skillsNew = sectionValue(sections, "skills");
    if (skillsNew && skillsNew.trim() && skillsNew.trim() !== skillsNow.trim()) {
      extractProposals.push({
        id: "skills",
        label: "Compétences",
        current: skillsNow,
        proposed: skillsNew,
        accepted: false,
      });
    }
    const eduNew = sectionValue(sections, "education");
    if (eduNew && eduNew.trim().length > 8) {
      extractProposals.push({
        id: "education_text",
        label: "Formation (texte extrait)",
        current: educations.map((e) => [e.degree, e.school].filter(Boolean).join(" · ")).join(" ; "),
        proposed: eduNew,
        accepted: false,
      });
    }
    (rows || []).forEach((row, i) => {
      if (!row.title && !row.organization) return;
      extractProposals.push({
        id: "exp_" + i,
        label: "Expérience détectée",
        current: "",
        proposed: [row.title, row.organization, row.start, row.end].filter(Boolean).join(" · "),
        accepted: false,
        experience: {
          title: row.title || "",
          company: row.organization || "",
          location: "",
          startDate: row.start || "",
          endDate: row.end || "",
          current: false,
          missions: "",
          achievements: "",
        },
      });
    });
    paintExtractReview();
  }

  function paintExtractReview() {
    const box = document.getElementById("cv-extract-review");
    const items = document.getElementById("cv-extract-items");
    if (!box || !items) return;
    if (!extractProposals.length) {
      box.hidden = true;
      items.replaceChildren();
      return;
    }
    box.hidden = false;
    items.replaceChildren();
    extractProposals.forEach((p, idx) => {
      const row = document.createElement("div");
      row.className = "cv-extract-item";
      const diff = document.createElement("div");
      diff.className = "diff";
      const title = document.createElement("strong");
      title.textContent = p.label;
      const body = document.createElement("div");
      if (p.current) {
        const was = document.createElement("span");
        was.className = "was";
        was.textContent = p.current.slice(0, 160);
        body.appendChild(was);
      }
      const prop = document.createElement("span");
      prop.textContent = p.proposed.slice(0, 240);
      body.append(title, document.createElement("br"), prop);
      diff.appendChild(body);
      const toggle = document.createElement("button");
      toggle.type = "button";
      toggle.className = "btn btn-sm " + (p.accepted ? "btn-primary" : "btn-soft");
      toggle.textContent = p.accepted ? "Acceptée" : "Accepter";
      toggle.addEventListener("click", () => {
        extractProposals[idx].accepted = !extractProposals[idx].accepted;
        paintExtractReview();
      });
      row.append(diff, toggle);
      items.appendChild(row);
    });
  }

  function applyAcceptedExtract() {
    let changed = false;
    extractProposals.forEach((p) => {
      if (!p.accepted) return;
      changed = true;
      if (p.id === "skills") {
        const merged = mergeCsv((document.getElementById("pf-skills") || {}).value, p.proposed);
        mountSkills(merged);
      } else if (p.experience) {
        experiences.push(p.experience);
      } else if (p.id === "education_text") {
        educations.push({
          school: "",
          degree: p.proposed.slice(0, 120),
          domain: "",
          startDate: "",
          endDate: "",
          description: "Issu de l’extraction CV — à vérifier.",
        });
      }
    });
    if (changed) {
      setDirty(true);
      renderCareer();
      HireLink.toast("Extraction", "Propositions acceptées ajoutées au profil. Enregistrez pour conserver.", "ok");
    } else {
      HireLink.toast("Extraction", "Aucune proposition acceptée.", "info");
    }
    extractProposals = extractProposals.filter((p) => !p.accepted);
    paintExtractReview();
  }

  function mergeCsv(a, b) {
    const set = new Set();
    String(a || "").split(/[,;\n]/).concat(String(b || "").split(/[,;\n]+/)).forEach((x) => {
      const t = x.trim();
      if (t) set.add(t);
    });
    return Array.from(set).join(", ");
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

  function setConfirmCv(enabled) {
    const b = document.getElementById("btn-confirm-cv");
    if (!b) return;
    b.disabled = !enabled;
    b.title = enabled ? "" : "Déposez d’abord un CV (PDF ou image) pour confirmer ses sections.";
  }

  function setCvDeleteVisible(on) {
    const b = document.getElementById("cv-delete");
    if (b) b.hidden = !on;
  }

  function showCvEmpty() {
    const empty = document.getElementById("cv-empty");
    const bench = document.getElementById("cv-workbench");
    if (bench) bench.hidden = false;
    if (empty) empty.innerHTML = "<p class='muted'>Aucun CV pour le moment. Déposez un PDF pour le prévisualiser et le réutiliser en candidature.</p>";
    hasCv = false;
    setCvDeleteVisible(false);
    paintAnalysis();
    paintProgress();
  }

  async function showSavedCv(profile) {
    const label = document.getElementById("cv-file-label");
    let sections = null;
    try {
      sections = profile.cvSections ? JSON.parse(profile.cvSections) : null;
    } catch (_) {}
    setConfirmCv(!!profile.cvFileName);
    hasCv = !!profile.cvFileName;
    setCvDeleteVisible(!!profile.cvFileName);
    if (!profile.cvFileName) {
      if (label) label.textContent = "Formats : PDF (texte), TXT, MD, image. Pas de DOCX.";
      showCvEmpty();
      return;
    }
    if (label) label.textContent = profile.cvFileName;
    document.getElementById("cv-workbench").hidden = false;
    document.getElementById("cv-empty").innerHTML = "";
    fillSections(sections);
    try {
      const res = await HireLinkAPI.authFetch("/me/cv", {});
      if (!res.ok) return;
      const blob = await res.blob();
      if (cvObjectUrl) URL.revokeObjectURL(cvObjectUrl);
      cvObjectUrl = URL.createObjectURL(blob);
      const name = profile.cvFileName.toLowerCase();
      if (/\.(png|jpe?g|webp|gif)$/.test(name)) showDocument(cvObjectUrl, "image");
      else if (name.endsWith(".pdf")) showDocument(cvObjectUrl, "pdf");
      else showDocument(await blob.text(), "text");
    } catch (_) {}
    paintProgress();
  }

  async function loadCareer() {
    try {
      const c = await HireLinkAPI.api("/me/career");
      experiences = Array.isArray(c.experiences) ? c.experiences : [];
      educations = Array.isArray(c.educations) ? c.educations : [];
      if (c.presentation != null) document.getElementById("pf-presentation").value = c.presentation;
      if (c.jobTitle != null && !(document.getElementById("pf-job-title").value || "").trim()) {
        document.getElementById("pf-job-title").value = c.jobTitle;
      }
      if (c.country != null && !(document.getElementById("pf-country").value || "").trim()) {
        document.getElementById("pf-country").value = c.country;
      }
      renderCareer();
    } catch (_) {
      experiences = [];
      educations = [];
      renderCareer();
    }
  }

  async function paintQuality() {
    try {
      const q = await HireLinkAPI.api("/me/cv-quality");
      const scoreEl = document.getElementById("cv-score");
      const bar = document.getElementById("cv-score-bar");
      const legend = document.getElementById("cv-score-legend");
      const list = document.getElementById("cv-findings");
      if (scoreEl) scoreEl.textContent = (q.passed || 0) + " / " + (q.total || 0);
      if (bar) bar.style.width = q.total ? Math.round((100 * q.passed) / q.total) + "%" : "0%";
      if (legend) legend.textContent = q.disclaimer || "Vérification technique déterministe.";
      if (list) {
        list.innerHTML = (q.checks || []).map((c) => {
          return '<li class="' + (c.ok ? "is-ok" : "is-ko") + '"><span><strong>' +
            String(c.label || "").replace(/</g, "") + "</strong> — " +
            String(c.detail || "").replace(/</g, "") + "</span></li>";
        }).join("");
      }
    } catch (_) {
      paintAnalysisLocal();
    }
  }

  function paintAnalysisLocal() {
    const s = sectionState();
    const checks = [
      { label: "Document CV", ok: hasCv, detail: hasCv ? "Fichier associé." : "Aucun CV déposé." },
      { label: "Texte / sections", ok: (s.identity + s.experience + s.education + s.skills).length > 40, detail: "Sections confirmées ou à compléter." },
      { label: "Compétences", ok: s.skills.split(/[,;\n]/).filter((x) => x.trim()).length >= 3, detail: "Au moins 3 compétences." },
    ];
    const ok = checks.filter((c) => c.ok).length;
    const scoreEl = document.getElementById("cv-score");
    const bar = document.getElementById("cv-score-bar");
    const list = document.getElementById("cv-findings");
    if (scoreEl) scoreEl.textContent = ok + " / " + checks.length;
    if (bar) bar.style.width = Math.round((100 * ok) / checks.length) + "%";
    if (list) {
      list.innerHTML = checks.map((c) =>
        '<li class="' + (c.ok ? "is-ok" : "is-ko") + '"><span><strong>' + c.label + "</strong> — " + c.detail + "</span></li>"
      ).join("");
    }
  }

  function sectionState() {
    return {
      identity: (document.getElementById("cv-identity") || {}).value || "",
      experience: (document.getElementById("cv-experience") || {}).value || "",
      education: (document.getElementById("cv-education") || {}).value || "",
      skills: (document.getElementById("cv-skills-read") || {}).value || (document.getElementById("pf-skills") || {}).value || "",
      languages: (document.getElementById("cv-languages") || {}).value || "",
    };
  }

  function paintAnalysis() {
    paintQuality();
  }

  function toastSaved(title, message) {
    HireLink.toast(title, message, "ok");
  }

  async function load() {
    try {
      const p = await HireLinkAPI.api("/me");
      meId = p.id || meId;
      document.getElementById("pf-name").textContent = p.fullName || "Profil";
      document.getElementById("pf-email").textContent = p.email || "";
      document.getElementById("pf-email-edit").value = p.email || "";
      document.getElementById("pf-first").value = p.firstName || "";
      document.getElementById("pf-last").value = p.lastName || "";
      document.getElementById("pf-phone").value = p.phone || "";
      document.getElementById("pf-city").value = p.city || "";
      document.getElementById("pf-country").value = p.country || "";
      document.getElementById("pf-job-title").value = p.jobTitle || "";
      document.getElementById("pf-presentation").value = p.companyPresentation || "";
      document.getElementById("pf-availability").value = p.availability || "";
      if (p.pendingEmail) {
        const pend = document.getElementById("pf-email-pending");
        const box = document.getElementById("pf-email-change");
        const code = document.getElementById("pf-email-code");
        const conf = document.getElementById("pf-email-confirm");
        if (box) box.hidden = false;
        if (pend) { pend.hidden = false; pend.textContent = "Changement en cours vers " + p.pendingEmail; }
        if (code) code.hidden = false;
        if (conf) conf.hidden = false;
      }
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
      await loadCareer();
      await showSavedCv(p);
      setDirty(false);
      paintProgress();
    } catch (err) {
      HireLink.toast("Profil", err.message, "danger");
    }
  }

  document.getElementById("profile-form").addEventListener("submit", async (e) => {
    e.preventDefault();
    syncCareerFromDom();
    const skills = document.getElementById("pf-skills").value;
    const btn = document.getElementById("btn-save-profile");
    if (btn) btn.disabled = true;
    try {
      await HireLinkAPI.api("/me", {
        method: "PATCH",
        body: {
          firstName: document.getElementById("pf-first").value.trim(),
          lastName: document.getElementById("pf-last").value.trim(),
          phone: document.getElementById("pf-phone").value.trim(),
          city: document.getElementById("pf-city").value.trim(),
          country: document.getElementById("pf-country").value.trim(),
          jobTitle: document.getElementById("pf-job-title").value.trim(),
          companyPresentation: document.getElementById("pf-presentation").value.trim(),
          availability: document.getElementById("pf-availability").value.trim(),
          skills,
          soughtRoles: document.getElementById("pf-roles").value,
          preferredLocations: document.getElementById("pf-loc").value,
          preferredContracts: document.getElementById("pf-contracts").value,
          experienceLevel: document.getElementById("pf-level").value,
        },
      });
      await HireLinkAPI.api("/me/career", {
        method: "PUT",
        body: {
          experiences,
          educations,
          presentation: document.getElementById("pf-presentation").value.trim(),
          jobTitle: document.getElementById("pf-job-title").value.trim(),
          country: document.getElementById("pf-country").value.trim(),
        },
      });
      const name = (document.getElementById("pf-first").value + " " + document.getElementById("pf-last").value).trim();
      if (name) document.getElementById("pf-name").textContent = name;
      setDirty(false);
      toastSaved("Profil enregistré", "Parcours et préférences à jour");
      paintProgress();
      paintAnalysis();
    } catch (err) {
      HireLink.toast("Erreur", err.message, "danger");
    } finally {
      if (btn) btn.disabled = false;
    }
  });

  const emailRequest = document.getElementById("pf-email-request");
  if (emailRequest) {
    emailRequest.addEventListener("click", async () => {
      const email = document.getElementById("pf-email-edit").value.trim();
      const password = document.getElementById("pf-email-password").value;
      try {
        const data = await HireLinkAPI.api("/account/email/change-request", {
          method: "POST",
          body: { email, currentPassword: password },
        });
        document.getElementById("pf-email-code").hidden = false;
        document.getElementById("pf-email-confirm").hidden = false;
        const pend = document.getElementById("pf-email-pending");
        if (pend) { pend.hidden = false; pend.textContent = data.message || "Code envoyé."; }
        HireLink.toast("E-mail", "Code envoyé à la nouvelle adresse", "ok");
      } catch (err) {
        HireLink.toast("E-mail", err.message, "danger");
      }
    });
  }
  const emailConfirm = document.getElementById("pf-email-confirm");
  if (emailConfirm) {
    emailConfirm.addEventListener("click", async () => {
      try {
        const data = await HireLinkAPI.api("/account/email/confirm", {
          method: "POST",
          body: { code: document.getElementById("pf-email-code").value.trim() },
        });
        document.getElementById("pf-email").textContent = data.email || "";
        document.getElementById("pf-email-edit").value = data.email || "";
        document.getElementById("pf-email-change").hidden = true;
        HireLink.toast("E-mail", data.message || "E-mail mis à jour", "ok");
      } catch (err) {
        HireLink.toast("E-mail", err.message, "danger");
      }
    });
  }

  document.getElementById("pf-exp-add").addEventListener("click", () => {
    experiences.push({ title: "", company: "", location: "", startDate: "", endDate: "", current: false, missions: "", achievements: "" });
    setDirty(true);
    renderCareer();
  });
  document.getElementById("pf-edu-add").addEventListener("click", () => {
    educations.push({ school: "", degree: "", domain: "", startDate: "", endDate: "", description: "" });
    setDirty(true);
    renderCareer();
  });

  const photoBtn = document.getElementById("btn-photo");
  const photoFile = document.getElementById("pf-photo-file");
  if (photoBtn) photoBtn.addEventListener("click", openPhotoPicker);
  if (photoFile) photoFile.addEventListener("change", (e) => {
    const file = e.target.files && e.target.files[0];
    if (!file) return;
    if (!/^image\/(jpeg|png|webp)$/.test(file.type)) {
      HireLink.toast("Photo", "Formats acceptés : JPEG, PNG, WebP.", "danger");
      return;
    }
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
    const name = file.name.toLowerCase();
    if (/\.docx?$/.test(name)) {
      HireLink.toast("Import CV", "Le format Word n’est pas pris en charge. Utilisez un PDF texte, TXT ou une image.", "danger");
      e.target.value = "";
      return;
    }
    if (file.size > 8 * 1024 * 1024) {
      HireLink.toast("Import CV", "Fichier trop volumineux (max. 8 Mo).", "danger");
      e.target.value = "";
      return;
    }
    pendingFile = file;
    setConfirmCv(true);
    document.getElementById("cv-file-label").textContent = file.name + " · lecture en cours";
    const fd = new FormData();
    fd.append("file", file);
    try {
      const res = await HireLinkAPI.authFetch("/ocr/cv?applySkills=false", {
        method: "POST",
        body: fd,
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.message || data.error || "Lecture échouée");
      document.getElementById("cv-workbench").hidden = false;
      document.getElementById("cv-empty").innerHTML = "";
      const chars = data.chars || 0;
      document.getElementById("cv-file-label").textContent = file.name + " · " + chars + " caractères lus";
      if (chars < 20 && (name.endsWith(".pdf") || /\.(png|jpe?g|webp|gif)$/.test(name))) {
        HireLink.toast("Extraction", "Document peu ou pas lisible (scan sans OCR). Complétez le profil à la main.", "info");
      }
      fillSections(data.sections || {}, data.experiences || [], data.extractionNote || "");
      if (cvObjectUrl) URL.revokeObjectURL(cvObjectUrl);
      cvObjectUrl = URL.createObjectURL(file);
      if (file.type.indexOf("image/") === 0 || /\.(png|jpe?g|webp|gif)$/.test(name)) showDocument(cvObjectUrl, "image");
      else if (name.endsWith(".pdf") || file.type === "application/pdf") showDocument(cvObjectUrl, "pdf");
      else showDocument(data.textPreview || "", "text");
      hasCv = true;
      setCvDeleteVisible(false);
      const edit = document.querySelector("#cv-section .cv-edit") || document.querySelector(".cv-edit");
      if (edit) {
        edit.open = true;
        edit.scrollIntoView({ behavior: "smooth", block: "center" });
      }
      HireLink.toast("CV lu", "Relisez les propositions, acceptez ou refusez, puis confirmez", "ok");
      paintProgress();
    } catch (err) {
      document.getElementById("cv-file-label").textContent = file.name + " · " + err.message;
      HireLink.toast("Import CV", err.message, "danger");
    }
  });

  const extractApply = document.getElementById("cv-extract-apply");
  if (extractApply) extractApply.addEventListener("click", applyAcceptedExtract);
  const extractRefuse = document.getElementById("cv-extract-refuse-all");
  if (extractRefuse) extractRefuse.addEventListener("click", () => {
    extractProposals = [];
    paintExtractReview();
    HireLink.toast("Extraction", "Toutes les propositions ont été refusées.", "info");
  });

  const cvDelete = document.getElementById("cv-delete");
  if (cvDelete) {
    cvDelete.addEventListener("click", async () => {
      const ok = window.confirm("Retirer le CV de votre espace ? Les candidatures déjà envoyées conservent leur pièce jointe.");
      if (!ok) return;
      try {
        await HireLinkAPI.api("/me/cv", { method: "DELETE" });
        pendingFile = null;
        if (cvObjectUrl) URL.revokeObjectURL(cvObjectUrl);
        cvObjectUrl = "";
        showCvEmpty();
        setConfirmCv(false);
        HireLink.toast("CV", "CV retiré de votre espace", "ok");
      } catch (err) {
        HireLink.toast("CV", err.message, "danger");
      }
    });
  }

  const addExperience = document.getElementById("cv-exp-add");
  if (addExperience) addExperience.addEventListener("click", function () {
    const list = document.getElementById("cv-exp-list");
    if (list) list.appendChild(cvExperienceRow({ kind: "EMPLOI", datesIncomplete: true }));
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
      const res = await HireLinkAPI.authFetch("/me/cv", {
        method: "POST",
        body: fd,
      });
      const data = await res.json();
      if (!res.ok) throw new Error(data.message || data.error || "Confirmation impossible");
      pendingFile = null;
      hasCv = true;
      setCvDeleteVisible(true);
      if (data.skills) {
        document.getElementById("pf-skills").value = data.skills;
        mountSkills(data.skills);
      }
      const box = document.getElementById("cv-empty");
      if (box) box.innerHTML = "<p class='muted'>Sections ajoutées au profil.</p>";
      toastSaved("CV confirmé", "Les sections font partie du profil");
      paintAnalysis();
      paintProgress();
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

  async function summarizeCv() {
    if (synthesizeBusy) return;
    const text = cvText();
    if (!text.trim()) {
      HireLink.toast("Synthèse", "Déposez d’abord un CV et confirmez les sections.", "info");
      return;
    }
    const send = document.getElementById("coach-analyze");
    const status = document.getElementById("coach-status");
    synthesizeBusy = true;
    if (send) send.disabled = true;
    if (status) status.textContent = "Synthèse en cours (jusqu’à ~1 min)…";
    pushCoach("HireLink", "Analyse en cours…");
    try {
      const data = await HireLinkCredits.run("/ai/cv-summary", { cvText: text }, { timeoutMs: 60000 });
      pushCoach("Synthèse", data.content || "Pas de synthèse.");
      if (status) status.textContent = "Synthèse terminée.";
    } catch (err) {
      pushCoach("Erreur", err.message || "Échec de la synthèse.");
      if (status) status.textContent = "";
      if (!err.handled) HireLink.toast("Synthèse", err.message, "danger");
    } finally {
      synthesizeBusy = false;
      if (send) send.disabled = false;
    }
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
    if (isCv) paintAnalysis();
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
