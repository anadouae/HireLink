/**
 * Compétences : sélecteur à saisie libre (suggestions tolérantes aux fautes, ajout d'une compétence nouvelle)
 * et cartes de synthèse par catégorie. La valeur échangée est la liste texte « Java, React, Anglais (B2) ».
 */
(function (global) {
  const LEVELS = ["A1", "A2", "B1", "B2", "C1", "C2", "Natif"];
  const MAX_SKILLS = 40;
  const LEVEL_RE = /^(.+?)\s*\(([^)]+)\)\s*$/;
  const groupCache = new Map();

  function api(path, options) {
    return HireLinkAPI.api(path, options);
  }

  function parse(raw) {
    const m = String(raw).trim().match(LEVEL_RE);
    return m ? { name: m[1].trim(), level: m[2].trim() } : { name: String(raw).trim(), level: "" };
  }

  function split(csv) {
    return String(csv || "").split(/[,;]/).map(function (s) { return s.trim(); }).filter(Boolean);
  }

  /** Regroupe une liste de compétences par catégorie (serveur), avec mémoire des réponses. */
  function classify(csv) {
    const key = split(csv).join(",");
    if (!key) return Promise.resolve([]);
    if (!groupCache.has(key)) {
      groupCache.set(key, api("/skills/classify", { method: "POST", body: { skills: key } }).catch(function () {
        groupCache.delete(key);
        return [];
      }));
    }
    return groupCache.get(key);
  }

  function chip(item, code, extra) {
    const el = document.createElement("span");
    el.className = "sk-chip sk-cat-" + code + (extra ? " " + extra : "");
    el.textContent = item.name;
    if (item.level) {
      const lv = document.createElement("small");
      lv.textContent = item.level;
      el.appendChild(lv);
    }
    return el;
  }

  /**
   * Cartes colorées par catégorie.
   * @param opts.highlight  compétences à mettre en avant (déjà possédées) ; les autres sont atténuées si opts.dimOthers
   * @param opts.empty      texte si aucune compétence
   */
  function renderBoxes(container, csv, opts) {
    const o = opts || {};
    container.classList.add("sk-boxes");
    return classify(csv).then(function (groups) {
      container.replaceChildren();
      if (!groups.length) {
        const p = document.createElement("p");
        p.className = "muted sk-empty";
        p.textContent = o.empty || "Aucune compétence renseignée.";
        container.appendChild(p);
        return;
      }
      groups.forEach(function (g) {
        const box = document.createElement("section");
        box.className = "sk-box sk-cat-" + g.code;
        const head = document.createElement("header");
        const title = document.createElement("h4");
        title.textContent = g.label;
        const count = document.createElement("span");
        count.className = "sk-box-count";
        count.textContent = String(g.skills.length);
        count.setAttribute("aria-label", g.skills.length + (g.skills.length > 1 ? " compétences" : " compétence"));
        head.append(title, count);
        const list = document.createElement("div");
        list.className = "sk-box-list";
        g.skills.forEach(function (s) { list.appendChild(chip(s, g.code)); });
        box.append(head, list);
        container.appendChild(box);
      });
    });
  }

  /**
   * Sélecteur de compétences.
   * @param opts.value     liste initiale (texte)
   * @param opts.onChange  (liste texte) => void
   * @param opts.placeholder
   * @returns {{ value: () => string, set: (csv) => void }}
   */
  function mount(root, opts) {
    let items = split(opts.value).map(parse).map(function (p) { return { name: p.name, level: p.level, code: "OTHER" }; });

    root.classList.add("sk-picker");
    root.replaceChildren();
    const input = document.createElement("input");
    input.type = "text";
    input.className = "sk-input";
    input.maxLength = 80;
    input.placeholder = opts.placeholder || "Tapez une compétence : Java, React, anglais, Docker…";
    input.setAttribute("aria-label", "Ajouter une compétence");
    root.appendChild(input);
    const hint = document.createElement("p");
    hint.className = "field-hint";
    hint.textContent = "Tapez quelques lettres, même avec une faute : les compétences les plus proches apparaissent. Aucune correspondance ? Ajoutez la vôtre.";
    root.appendChild(hint);
    const list = document.createElement("div");
    list.className = "sk-selected";
    list.setAttribute("aria-live", "polite");
    root.appendChild(list);

    function csv() {
      return items.map(function (i) { return i.level ? i.name + " (" + i.level + ")" : i.name; }).join(", ");
    }

    function changed() {
      paint();
      if (opts.onChange) opts.onChange(csv());
    }

    function has(name) {
      const k = HireLinkSearch.compact(name);
      return items.some(function (i) { return HireLinkSearch.compact(i.name) === k; });
    }

    function add(name, code) {
      const clean = String(name).replace(/\s+/g, " ").trim();
      if (!clean || has(clean) || items.length >= MAX_SKILLS) return;
      items.push({ name: clean, code: code || "OTHER", level: code === "SPOKEN" ? "B2" : "" });
      input.value = "";
      changed();
    }

    function remove(index) {
      items.splice(index, 1);
      changed();
      input.focus();
    }

    function paint() {
      list.replaceChildren();
      if (!items.length) {
        const p = document.createElement("span");
        p.className = "muted";
        p.textContent = "Aucune compétence ajoutée pour l’instant.";
        list.appendChild(p);
        return;
      }
      items.forEach(function (item, index) {
        const el = chip({ name: item.name }, item.code, "is-editable");
        if (item.code === "SPOKEN") {
          const sel = document.createElement("select");
          sel.setAttribute("aria-label", "Niveau en " + item.name);
          LEVELS.forEach(function (lv) {
            const op = document.createElement("option");
            op.value = lv;
            op.textContent = lv;
            op.selected = lv === (item.level || "B2");
            sel.appendChild(op);
          });
          sel.addEventListener("change", function () { item.level = sel.value; if (opts.onChange) opts.onChange(csv()); });
          el.appendChild(sel);
        }
        const rm = document.createElement("button");
        rm.type = "button";
        rm.className = "sk-chip-remove";
        rm.setAttribute("aria-label", "Retirer " + item.name);
        rm.textContent = "×";
        rm.addEventListener("click", function () { remove(index); });
        el.appendChild(rm);
        list.appendChild(el);
      });
    }

    function suggestions(q) {
      return api("/skills/suggest?q=" + encodeURIComponent(q)).then(function (rows) {
        const out = rows.map(function (r) {
          return {
            key: r.name, label: r.name, code: r.category, badge: r.categoryLabel,
            hint: r.alias && !r.exact ? "aussi connu sous « " + r.alias + " »" : "",
            disabled: has(r.name),
          };
        });
        out.forEach(function (o) { if (o.disabled) o.badge = "Déjà ajoutée"; });
        const exact = rows.some(function (r) { return r.exact || HireLinkSearch.compact(r.name) === HireLinkSearch.compact(q); });
        if (!exact && q && !has(q)) out.push({ key: "custom", label: "Ajouter « " + q + " » comme nouvelle compétence", custom: true, name: q });
        return out;
      });
    }

    HireLinkSearch.combobox(input, {
      fetch: suggestions,
      delay: 120,
      onPick: function (item) { add(item.custom ? item.name : item.label, item.custom ? "OTHER" : item.code); },
      onEnter: function (text) { add(text, "OTHER"); },
      emptyText: "Aucune compétence proche : appuyez sur Entrée pour l’ajouter.",
    });

    // Catégories des compétences déjà saisies (couleurs des pastilles)
    function colour() {
      if (!items.length) return paint();
      paint();
      return classify(csv()).then(function (groups) {
        const known = new Map();
        groups.forEach(function (g) { g.skills.forEach(function (s) { known.set(HireLinkSearch.compact(s.name), g.code); }); });
        items.forEach(function (i) {
          i.code = known.get(HireLinkSearch.compact(i.name)) || i.code;
          if (i.code === "SPOKEN" && !i.level) i.level = "B2";
        });
        paint();
      });
    }
    colour();

    return {
      value: csv,
      set: function (value) {
        items = split(value).map(parse).map(function (p) { return { name: p.name, level: p.level, code: "OTHER" }; });
        colour();
      },
    };
  }

  global.HireLinkSkills = { mount: mount, renderBoxes: renderBoxes, classify: classify, split: split };
})(window);
