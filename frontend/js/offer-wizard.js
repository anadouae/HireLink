/**
 * Assistant de création / modification d'offre : 4 étapes (Poste, Conditions, Profil, Publication).
 * Les champs de l'étape « Conditions » s'adaptent au type de contrat ; chaque champ est validé en ligne.
 */
(function (global) {
  const O = global.HireLinkOffer;
  const STEPS = ["Poste", "Conditions", "Profil recherché", "Publication"];

  const CONTRACTS = [
    { value: "CDI", hint: "Durée indéterminée" },
    { value: "CDD", hint: "Durée déterminée" },
    { value: "Stage", hint: "Stage classique" },
    { value: "PFE", hint: "Projet de fin d’études" },
    { value: "PFA", hint: "Projet de fin d’année" },
    { value: "Stage d'été", label: "Stage d’été", hint: "Quelques semaines" },
    { value: "Alternance", hint: "École + entreprise" },
    { value: "Freelance", hint: "Mission indépendante" },
    { value: "Temps partiel", hint: "Moins de 35 h / semaine" },
  ];
  const INTERNSHIPS = ["Stage", "PFE", "PFA", "Stage d'été"];
  const NO_DURATION = ["CDI", "Temps partiel"];
  const RHYTHMS = ["1 semaine école / 1 semaine entreprise", "2 semaines école / 2 semaines entreprise", "2 jours école / 3 jours entreprise", "1 mois école / 3 mois entreprise"];
  const BENEFITS = ["Transport", "Tickets restaurant", "Assurance santé", "Primes", "Télétravail partiel", "Formation", "Matériel fourni", "Mutuelle"];
  const SLA_DAYS = [3, 5, 7, 10, 14];

  const isInternship = (s) => INTERNSHIPS.indexOf(s.contractType) >= 0;
  const hasDuration = (s) => NO_DURATION.indexOf(s.contractType) < 0;
  const hasProbation = (s) => s.contractType === "CDI" || s.contractType === "CDD";

  const today = () => new Date().toISOString().slice(0, 10);

  function h(tag, attrs, children) {
    const e = document.createElement(tag);
    Object.keys(attrs || {}).forEach((k) => {
      if (attrs[k] == null || attrs[k] === false) return;
      if (k === "text") e.textContent = attrs[k];
      else e.setAttribute(k, attrs[k] === true ? "" : attrs[k]);
    });
    [].concat(children || []).forEach((c) => { if (c) e.append(c); });
    return e;
  }

  function legacyMode(offer) {
    if (offer.workMode) return offer.workMode;
    const hit = String(offer.location || "").match(/t[ée]l[ée]travail|remote|hybride|sur site|pr[ée]sentiel/i);
    if (!hit) return "";
    return /hybride/i.test(hit[0]) ? "HYBRID" : /sur site|pr[ée]sentiel/i.test(hit[0]) ? "ONSITE" : "REMOTE";
  }

  /** Valeurs par défaut d'une offre neuve, ou conversion d'une offre existante (anciennes offres comprises). */
  function initialState(o) {
    const known = o && CONTRACTS.some((c) => c.value === o.contractType);
    const hours = o && /(\d+)\s*h/i.exec(o.schedule || "");
    return {
      title: (o && o.title) || "",
      description: (o && o.description) || "",
      contractType: known ? o.contractType : "CDI",
      location: o ? O.placeOf(o) : "",
      workMode: o ? legacyMode(o) : "HYBRID",
      compensationType: (o && o.compensationType) || "PAID",
      salaryMin: o && o.salaryMin ? String(o.salaryMin) : "",
      salaryMax: o && o.salaryMax ? String(o.salaryMax) : "",
      salaryPeriod: (o && o.salaryPeriod) || "MONTH",
      salaryBasis: (o && o.salaryBasis) || "GROSS",
      currency: (o && o.currency) || "MAD",
      durationMonths: o && o.durationMonths ? String(o.durationMonths) : "",
      startAsap: o ? !!o.startAsap : true,
      startDate: (o && o.startDate) || "",
      deadline: (o && o.deadline) || "",
      agreementRequired: o && o.agreementRequired != null ? o.agreementRequired : true,
      hiringPossible: o && o.hiringPossible != null ? o.hiringPossible : false,
      probationMonths: o ? (o.probationMonths == null ? "" : String(o.probationMonths)) : "3",
      rhythm: o && o.contractType === "Alternance" ? o.schedule || "" : "",
      weeklyHours: o && o.contractType === "Temps partiel" && hours ? hours[1] : "",
      benefits: (o && o.benefits ? o.benefits.split(",").map((b) => b.trim()) : []),
      requiredSkills: (o && o.requiredSkills) || "",
      experienceLevel: (o && o.experienceLevel) || "",
      educationLevel: (o && o.educationLevel) || "",
      positions: o && o.positions ? String(o.positions) : "1",
      responseSlaDays: (o && o.responseSlaDays) || 7,
      published: o ? !!o.published : true,
    };
  }

  /** Règles de validation par étape ; renvoie { champ: message }. Le serveur revalide tout. */
  function validate(step, s) {
    const e = {};
    const n = (v) => (v === "" || v == null ? NaN : Number(v));
    if (step === 0) {
      if (s.title.trim().length < 3) e.title = "Donnez un intitulé clair (3 caractères minimum).";
      if (s.description.trim().length < 20) e.description = "Décrivez les missions en quelques phrases (20 caractères minimum).";
      if (!s.location.trim() && s.workMode !== "REMOTE") e.location = "Indiquez la ville ou le pays du poste.";
    }
    if (step === 1) {
      if (s.compensationType === "PAID") {
        const min = n(s.salaryMin);
        const max = n(s.salaryMax);
        if (!(min > 0)) e.salaryMin = "Indiquez le montant de la rémunération.";
        else if (!isNaN(max) && max < min) e.salaryMax = "Le maximum doit être supérieur ou égal au minimum.";
      }
      if (hasDuration(s)) {
        const d = n(s.durationMonths);
        if (isNaN(d) || d < 1) e.durationMonths = "Indiquez la durée en mois.";
        else if (isInternship(s) && d > 12) e.durationMonths = "Un stage dure au plus 12 mois.";
        else if (d > 60) e.durationMonths = "La durée ne peut pas dépasser 60 mois.";
      }
      if (s.contractType === "Alternance" && !s.rhythm.trim()) e.rhythm = "Indiquez le rythme d’alternance.";
      if (s.contractType === "Temps partiel") {
        const w = n(s.weeklyHours);
        if (isNaN(w) || w < 1 || w > 39) e.weeklyHours = "Indiquez un volume entre 1 et 39 heures par semaine.";
      }
      if (!s.startAsap && s.startDate && s.startDate < today()) e.startDate = "La date de début est déjà passée.";
      if (s.deadline && s.deadline < today()) e.deadline = "La date limite est déjà passée.";
      else if (s.deadline && !s.startAsap && s.startDate && s.deadline > s.startDate) e.deadline = "La date limite doit précéder la date de début.";
    }
    if (step === 2) {
      if (!s.requiredSkills.trim()) e.requiredSkills = "Ajoutez au moins une compétence.";
      const p = n(s.positions);
      if (isNaN(p) || p < 1 || p > 500) e.positions = "Indiquez un nombre de postes entre 1 et 500.";
    }
    return e;
  }

  function schedule(s) {
    if (s.contractType === "Alternance") return s.rhythm.trim();
    if (s.contractType === "Temps partiel") return s.weeklyHours + " h / semaine";
    return null;
  }

  function toRequest(s) {
    const paid = s.compensationType === "PAID";
    const num = (v) => (v === "" ? null : Number(v));
    return {
      title: s.title.trim(),
      description: s.description.trim(),
      requiredSkills: s.requiredSkills,
      location: s.location.trim(),
      contractType: s.contractType,
      responseSlaDays: Number(s.responseSlaDays),
      published: s.published,
      workMode: s.workMode || null,
      compensationType: s.compensationType,
      salaryMin: paid ? num(s.salaryMin) : null,
      salaryMax: paid ? num(s.salaryMax) : null,
      salaryPeriod: paid ? s.salaryPeriod : null,
      salaryBasis: paid && !isInternship(s) ? s.salaryBasis : null,
      currency: paid ? s.currency : null,
      durationMonths: hasDuration(s) ? num(s.durationMonths) : null,
      startAsap: s.startAsap,
      startDate: s.startAsap || !s.startDate ? null : s.startDate,
      deadline: s.deadline || null,
      agreementRequired: isInternship(s) ? s.agreementRequired : null,
      hiringPossible: isInternship(s) ? s.hiringPossible : null,
      probationMonths: hasProbation(s) ? num(s.probationMonths) : null,
      schedule: schedule(s),
      experienceLevel: s.experienceLevel || null,
      educationLevel: s.educationLevel || null,
      positions: num(s.positions),
      benefits: s.benefits.join(", ") || null,
    };
  }

  function start(onDone, existing) {
    const state = initialState(existing);
    const editId = existing && existing.id;
    const ui = { step: 0, touched: new Set(), submitting: false, error: "", skills: null };

    const overlay = h("div", { class: "ow-overlay" });
    const previouslyFocused = document.activeElement;
    document.body.appendChild(overlay);
    requestAnimationFrame(() => overlay.classList.add("is-open"));

    function close() {
      document.removeEventListener("keydown", onKey);
      overlay.remove();
      if (previouslyFocused && previouslyFocused.focus) previouslyFocused.focus();
    }

    function onKey(e) {
      if (e.key === "Escape" && !document.querySelector(".hl-combo-menu:not([hidden])")) close();
    }
    document.addEventListener("keydown", onKey);

    // ----- champs -----------------------------------------------------------

    function errorsNow() {
      return validate(ui.step, state);
    }

    /** Affiche les messages des champs touchés (sans reconstruire le formulaire : le focus reste en place). */
    function refreshErrors() {
      const errs = errorsNow();
      overlay.querySelectorAll("[data-field]").forEach((wrap) => {
        const key = wrap.dataset.field;
        const msg = ui.touched.has(key) ? errs[key] : "";
        wrap.classList.toggle("has-error", !!msg);
        const out = wrap.querySelector(".ow-error");
        if (out) out.textContent = msg || "";
        wrap.querySelectorAll("input,select,textarea").forEach((c) => {
          if (msg) c.setAttribute("aria-invalid", "true"); else c.removeAttribute("aria-invalid");
        });
      });
    }

    function field(key, label, control, opts) {
      const o = opts || {};
      const id = "ow-" + key;
      const wrap = h("div", { class: "ow-field" + (o.wide ? " is-wide" : ""), "data-field": key });
      const lab = h("label", { for: id, text: label });
      if (o.required) lab.append(h("span", { class: "ow-req", "aria-hidden": "true", text: " *" }));
      wrap.append(lab, control);
      if (o.hint) wrap.append(h("p", { class: "ow-hint", id: id + "-hint", text: o.hint }));
      wrap.append(h("p", { class: "ow-error", id: id + "-error", role: "alert" }));
      control.setAttribute("aria-describedby", id + "-error" + (o.hint ? " " + id + "-hint" : ""));
      return wrap;
    }

    function bind(control, key, after) {
      control.id = "ow-" + key;
      control.value = state[key];
      control.addEventListener("input", () => { state[key] = control.value; if (after) after(); refreshErrors(); });
      control.addEventListener("blur", () => { ui.touched.add(key); refreshErrors(); });
      return control;
    }

    const text = (key, attrs, after) => bind(h("input", Object.assign({ type: "text" }, attrs)), key, after);
    const number = (key, attrs) => bind(h("input", Object.assign({ type: "number", inputmode: "numeric" }, attrs)), key);
    const date = (key, attrs) => bind(h("input", Object.assign({ type: "date" }, attrs)), key);
    const select = (key, options, after) => {
      const s = h("select", {}, options.map((o) => h("option", { value: o[0], text: o[1] })));
      return bind(s, key, after);
    };

    /** Boutons radio présentés comme des pastilles : navigation clavier native. */
    function choice(key, options, onPick) {
      const group = h("div", { class: "ow-choice", role: "radiogroup" });
      options.forEach((o) => {
        const id = "ow-" + key + "-" + String(o.value).replace(/\W+/g, "_");
        const input = h("input", { type: "radio", name: "ow-" + key, id, value: o.value });
        input.checked = String(state[key]) === String(o.value);
        input.addEventListener("change", () => { state[key] = o.value; onPick(); });
        const label = h("label", { for: id }, [h("span", { text: o.label || o.value }), o.hint ? h("small", { text: o.hint }) : null]);
        group.append(input, label);
      });
      return group;
    }

    const yesNo = (key, yes, no) => choice(key, [{ value: true, label: yes }, { value: false, label: no }], () => {});

    function section(title, intro, children) {
      return h("section", { class: "ow-section" }, [h("h3", { text: title }), intro ? h("p", { class: "ow-intro", text: intro }) : null, h("div", { class: "ow-grid" }, children)]);
    }

    // ----- étapes ---------------------------------------------------------------

    function stepPoste() {
      const jobs = text("title", { maxlength: 160, placeholder: "Ex. Développeur Full-Stack Java", "data-suggest": "job", autocomplete: "off" });
      const desc = bind(h("textarea", { rows: 6, maxlength: 10000, placeholder: "Missions, contexte de l’équipe, environnement technique, ce que vous offrez…" }), "description");
      const contract = choice("contractType", CONTRACTS, () => { applyContractDefaults(); render(`#ow-contractType-${state.contractType.replace(/\W+/g, "_")}`); });
      const place = text("location", { maxlength: 120, placeholder: "Ville ou pays : Tétouan, Casablanca, Paris…", "data-suggest": "place", autocomplete: "off" });
      const mode = choice("workMode", Object.keys(O.LABELS.workMode).map((k) => ({ value: k, label: O.LABELS.workMode[k] })), refreshErrors);
      return [
        section("Le poste", null, [field("title", "Intitulé du poste", jobs, { required: true, wide: true }), field("description", "Description", desc, { required: true, wide: true })]),
        h("section", { class: "ow-section" }, [
          h("h3", { text: "Type de contrat" }),
          h("p", { class: "ow-intro", text: "Les champs de l’étape suivante s’adaptent à votre choix." }),
          contract,
        ]),
        section("Lieu et organisation", null, [
          field("location", "Lieu", place, { required: state.workMode !== "REMOTE", hint: "Les villes du Maroc sont proposées en premier, avec le drapeau du pays." }),
          field("workMode", "Mode de travail", mode),
        ]),
      ];
    }

    function applyContractDefaults() {
      if (isInternship(state)) state.salaryPeriod = "MONTH";
      else if (state.compensationType === "UNPAID") state.compensationType = "UNSPECIFIED";
      if (state.contractType === "Freelance") state.salaryPeriod = "DAY";
      else if (state.salaryPeriod === "DAY" || (state.salaryPeriod === "HOUR" && state.contractType !== "Temps partiel")) state.salaryPeriod = "MONTH";
      if (state.contractType === "CDD" && state.probationMonths === "3") state.probationMonths = "1";
      if (state.contractType === "CDI" && state.probationMonths === "1") state.probationMonths = "3";
    }

    function compensationSection() {
      const types = [{ value: "PAID", label: "Rémunéré" }];
      if (isInternship(state)) types.push({ value: "UNPAID", label: "Non rémunéré" });
      types.push({ value: "NEGOTIABLE", label: "Négociable" }, { value: "UNSPECIFIED", label: "Non précisé" });
      const kind = isInternship(state) ? "Gratification" : state.contractType === "Freelance" ? "Taux" : "Salaire";
      const children = [h("div", { class: "ow-field is-wide" }, [
        h("span", { class: "ow-label", text: "Rémunération" }),
        choice("compensationType", types, () => render("#ow-compensationType-" + state.compensationType)),
      ])];
      if (state.compensationType === "PAID") {
        const periods = isInternship(state) ? [["MONTH", "par mois"]]
          : state.contractType === "Freelance" ? [["DAY", "par jour (TJM)"], ["HOUR", "par heure"]]
            : state.contractType === "Temps partiel" ? [["HOUR", "par heure"], ["MONTH", "par mois"]]
              : [["MONTH", "par mois"], ["YEAR", "par an"]];
        const minLabel = state.contractType === "Freelance" ? "Taux journalier (TJM)" : kind + " minimum";
        children.push(
          field("salaryMin", minLabel, number("salaryMin", { min: 1, max: 10000000, placeholder: "8000" }), { required: true }),
          field("salaryMax", kind + " maximum", number("salaryMax", { min: 1, max: 10000000, placeholder: "10000" }), { hint: "Facultatif : laissez vide pour un montant fixe." }),
          field("currency", "Devise", select("currency", [["MAD", "MAD — dirham"], ["EUR", "EUR — euro"], ["USD", "USD — dollar"]])),
          field("salaryPeriod", "Période", select("salaryPeriod", periods)),
        );
        if (!isInternship(state)) {
          children.push(field("salaryBasis", "Montant", select("salaryBasis", [["GROSS", "Brut"], ["NET", "Net"]])));
        }
      }
      return h("section", { class: "ow-section ow-money" }, [
        h("h3", { text: "Rémunération" }),
        h("p", { class: "ow-intro", text: isInternship(state) ? "Pour un stage, indiquez la gratification mensuelle." : "Une fourchette aide les candidats à se projeter." }),
        h("div", { class: "ow-grid" }, children),
      ]);
    }

    function contractSection() {
      const f = [];
      if (hasDuration(state)) {
        const label = isInternship(state) ? "Durée du stage (mois)" : state.contractType === "CDD" ? "Durée du contrat (mois)" : state.contractType === "Alternance" ? "Durée de l’alternance (mois)" : "Durée de la mission (mois)";
        f.push(field("durationMonths", label, number("durationMonths", { min: 1, max: 60, placeholder: "6" }), { required: true }));
      }
      f.push(h("div", { class: "ow-field" }, [
        h("label", { for: "ow-startAsap", class: "ow-switch" }, [
          (() => { const c = h("input", { type: "checkbox", id: "ow-startAsap" }); c.checked = state.startAsap; c.addEventListener("change", () => { state.startAsap = c.checked; render("#ow-startAsap"); }); return c; })(),
          h("span", { text: "Début dès que possible" }),
        ]),
      ]));
      if (!state.startAsap) f.push(field("startDate", "Date de début", date("startDate", { min: today() })));
      f.push(field("deadline", "Date limite de candidature", date("deadline", { min: today() }), { hint: "L’offre n’est plus affichée après cette date." }));
      if (state.contractType === "Alternance") {
        const rhythm = text("rhythm", { maxlength: 120, placeholder: "Ex. 2 semaines école / 2 semaines entreprise" });
        const presets = h("div", { class: "ow-presets" }, RHYTHMS.map((r) => {
          const b = h("button", { type: "button", class: "ow-preset", text: r });
          b.addEventListener("click", () => { state.rhythm = r; rhythm.value = r; ui.touched.add("rhythm"); refreshErrors(); });
          return b;
        }));
        const wrap = field("rhythm", "Rythme d’alternance", rhythm, { required: true, wide: true });
        wrap.insertBefore(presets, wrap.querySelector(".ow-error"));
        f.push(wrap);
      }
      if (state.contractType === "Temps partiel") {
        f.push(field("weeklyHours", "Heures par semaine", number("weeklyHours", { min: 1, max: 39, placeholder: "20" }), { required: true }));
      }
      if (hasProbation(state)) {
        const opts = [["", "Non précisée"], ["0", "Aucune"], ["1", "1 mois"], ["2", "2 mois"], ["3", "3 mois"], ["4", "4 mois"], ["6", "6 mois"]];
        f.push(field("probationMonths", "Période d’essai", select("probationMonths", opts)));
      }
      if (isInternship(state)) {
        f.push(
          h("div", { class: "ow-field" }, [h("span", { class: "ow-label", text: "Convention de stage" }), yesNo("agreementRequired", "Obligatoire", "Non exigée")]),
          h("div", { class: "ow-field" }, [h("span", { class: "ow-label", text: "Embauche possible à l’issue" }), yesNo("hiringPossible", "Oui", "Non")]),
        );
      }
      return h("section", { class: "ow-section" }, [h("h3", { text: "Durée et calendrier" }), h("div", { class: "ow-grid" }, f)]);
    }

    function benefitsSection() {
      const list = h("div", { class: "ow-benefits" });
      function paint() {
        list.replaceChildren();
        BENEFITS.concat(state.benefits.filter((b) => BENEFITS.indexOf(b) < 0)).forEach((b) => {
          const on = state.benefits.indexOf(b) >= 0;
          const btn = h("button", { type: "button", class: "ow-tag" + (on ? " is-on" : ""), "aria-pressed": on ? "true" : "false", text: b });
          btn.addEventListener("click", () => {
            state.benefits = on ? state.benefits.filter((x) => x !== b) : state.benefits.concat(b);
            paint();
            list.querySelectorAll(".ow-tag").forEach((t) => { if (t.textContent === b) t.focus(); });
          });
          list.appendChild(btn);
        });
      }
      paint();
      const extra = h("input", { type: "text", class: "ow-extra", maxlength: 60, id: "ow-benefit-extra", placeholder: "Autre avantage (Entrée pour ajouter)", "aria-label": "Ajouter un autre avantage" });
      extra.addEventListener("keydown", (e) => {
        if (e.key !== "Enter") return;
        e.preventDefault();
        const v = extra.value.replace(/[<>,;]/g, "").trim();
        if (v && state.benefits.indexOf(v) < 0 && state.benefits.join(", ").length + v.length < 380) state.benefits.push(v);
        extra.value = "";
        paint();
      });
      return h("section", { class: "ow-section" }, [h("h3", { text: "Avantages" }), h("p", { class: "ow-intro", text: "Facultatif : ce qui rend le poste attractif." }), list, extra]);
    }

    function stepConditions() {
      return [compensationSection(), contractSection(), benefitsSection()];
    }

    function stepProfil() {
      const picker = h("div", { id: "ow-requiredSkills" });
      const exp = select("experienceLevel", [["", "Non précisé"]].concat(Object.keys(O.LABELS.experience).map((k) => [k, O.LABELS.experience[k]])));
      const edu = select("educationLevel", [["", "Non précisé"]].concat(Object.keys(O.LABELS.education).map((k) => [k, O.LABELS.education[k]])));
      const skills = h("div", { class: "ow-field is-wide", "data-field": "requiredSkills" }, [
        h("label", { for: "ow-requiredSkills", text: "Compétences et langues demandées" }),
        picker,
        h("p", { class: "ow-error", role: "alert" }),
      ]);
      skills.querySelector("label").append(h("span", { class: "ow-req", "aria-hidden": "true", text: " *" }));
      queueMicrotask(() => {
        ui.skills = global.HireLinkSkills.mount(picker, {
          value: state.requiredSkills,
          onChange: (csv) => { state.requiredSkills = csv; ui.touched.add("requiredSkills"); refreshErrors(); },
        });
      });
      return [
        h("section", { class: "ow-section" }, [h("h3", { text: "Compétences" }), h("div", { class: "ow-grid" }, [skills])]),
        section("Profil", null, [
          field("experienceLevel", "Niveau d’expérience", exp),
          field("educationLevel", "Niveau d’études", edu),
          field("positions", "Nombre de postes", number("positions", { min: 1, max: 500 })),
        ]),
      ];
    }

    function previewOffer() {
      return Object.assign({ id: editId || 0, companyName: existing && existing.companyName || "Votre entreprise", companyLogo: existing && existing.companyLogo, companyVerified: true }, toRequest(state));
    }

    function stepPublication() {
      const sla = select("responseSlaDays", SLA_DAYS.map((d) => [String(d), d + " jours" + (d === 7 ? " (recommandé)" : "")]));
      sla.addEventListener("change", () => { state.responseSlaDays = Number(sla.value); });
      sla.value = String(state.responseSlaDays);
      const pub = h("input", { type: "checkbox", id: "ow-published" });
      pub.checked = state.published;
      pub.addEventListener("change", () => { state.published = pub.checked; });
      const offer = previewOffer();
      return [
        h("section", { class: "ow-section" }, [
          h("h3", { text: "Aperçu de votre offre" }),
          h("p", { class: "ow-intro", text: "Voici comment les candidats la verront dans la liste." }),
          O.card(offer, { href: () => "#", onOpen: () => {} }),
          O.facts(offer),
        ]),
        section("Publication", null, [
          field("responseSlaDays", "Délai de première réponse", sla, { hint: "Engagement affiché au candidat : « nous vous répondons sous X jours »." }),
          h("div", { class: "ow-field is-wide" }, [
            h("label", { for: "ow-published", class: "ow-switch" }, [pub, h("span", { text: "Publier immédiatement" })]),
            h("p", { class: "ow-hint", text: "Une entreprise vérifiée est nécessaire pour publier. Sinon, l’offre reste en brouillon." }),
          ]),
        ]),
      ];
    }

    // ----- cadre ----------------------------------------------------------------

    function render(focusSelector) {
      const build = [stepPoste, stepConditions, stepProfil, stepPublication][ui.step];
      const body = h("div", { class: "ow-body" }, h("div", { class: "ow-panel" }, build()));
      const last = ui.step === STEPS.length - 1;
      const next = h("button", { type: "button", class: "btn btn-primary", id: "ow-next", text: ui.submitting ? "Enregistrement…" : last ? (editId ? "Enregistrer les modifications" : "Créer l’offre") : "Continuer" });
      if (ui.submitting) next.disabled = true;
      next.addEventListener("click", () => (last ? submit() : go(ui.step + 1)));
      const back = ui.step > 0 ? h("button", { type: "button", class: "btn btn-soft", text: "Retour" }) : null;
      if (back) back.addEventListener("click", () => go(ui.step - 1, true));
      const cancel = h("button", { type: "button", class: "btn btn-ghost", text: "Annuler" });
      cancel.addEventListener("click", close);

      const steps = h("ol", { class: "ow-steps" }, STEPS.map((label, i) => {
        const li = h("li", { class: i < ui.step ? "is-done" : i === ui.step ? "is-current" : "", "aria-current": i === ui.step ? "step" : null }, [h("span", { class: "ow-step-n", text: i < ui.step ? "✓" : String(i + 1) }), h("span", { text: label })]);
        return li;
      }));
      const card = h("div", { class: "ow-card", role: "dialog", "aria-modal": "true", "aria-labelledby": "ow-title-h" }, [
        h("header", { class: "ow-top" }, [h("h2", { id: "ow-title-h", text: editId ? "Modifier l’offre" : "Nouvelle offre" }), steps]),
        ui.error ? h("div", { class: "ow-alert", role: "alert", text: ui.error }) : null,
        body,
        h("footer", { class: "ow-foot" }, [cancel, h("div", { class: "ow-foot-actions" }, [back, next])]),
      ]);
      overlay.replaceChildren(card);
      overlay.onmousedown = (e) => { if (e.target === overlay) close(); };
      if (global.HireLinkSuggest) global.HireLinkSuggest.boot();
      refreshErrors();
      const target = focusSelector && overlay.querySelector(focusSelector);
      if (target) target.focus();
    }

    function go(step, backwards) {
      if (!backwards) {
        const errs = validate(ui.step, state);
        const keys = Object.keys(errs);
        if (keys.length) {
          keys.forEach((k) => ui.touched.add(k));
          refreshErrors();
          const first = overlay.querySelector('[data-field="' + keys[0] + '"] input, [data-field="' + keys[0] + '"] select, [data-field="' + keys[0] + '"] textarea');
          if (first) first.focus();
          return;
        }
      }
      ui.step = step;
      ui.error = "";
      render();
      overlay.querySelector(".ow-body").scrollTop = 0;
    }

    async function submit() {
      for (let i = 0; i < STEPS.length - 1; i++) {
        if (Object.keys(validate(i, state)).length) { ui.step = i; go(i); return; }
      }
      ui.submitting = true;
      render();
      try {
        const lang = await HireLinkAPI.api("/ai/language-check", { method: "POST", body: { title: state.title, description: state.description } });
        if (lang.flagged) {
          const list = document.createElement("ul");
          (lang.suggestions || []).forEach((x) => list.appendChild(h("li", { text: x })));
          const cont = await HireLink.openModal({ title: "Formulation à revoir", description: lang.summary, bodyHtml: list.outerHTML, confirmLabel: "Publier quand même", cancelLabel: "Corriger", large: true });
          if (!cont) { ui.submitting = false; ui.step = 0; render(); return; }
        }
        await HireLinkAPI.api(editId ? "/offers/" + editId : "/offers", { method: editId ? "PUT" : "POST", body: toRequest(state) });
        close();
        const celebrated = state.published && !editId && Mascotte.celebrate({ cle: "premiere-offre", title: "Votre première offre est en ligne !", description: "Les candidats peuvent désormais postuler." });
        if (!celebrated) {
          HireLink.toast(
            editId ? "Offre mise à jour" : "Offre créée",
            state.published ? "Publiée" : "Brouillon",
            "ok"
          );
        }
        if (typeof onDone === "function") onDone();
      } catch (err) {
        ui.submitting = false;
        ui.error = err.message;
        render();
      }
    }

    render();
    const first = overlay.querySelector("#ow-title");
    if (first) first.focus();
  }

  global.HireLinkOfferWizard = { start: start, edit: (offer, onDone) => start(onDone, offer) };
})(window);
