/**
 * Recherche partagée de HireLink : normalisation (accents, casse), correspondance tolérante aux fautes de frappe,
 * classement par pertinence, surlignage et liste de suggestions accessible (clavier, lecteur d'écran).
 * Même échelle de scores que le service Java TextMatcher : 1 égalité, ~0,9 préfixe, 0,75 début de mot,
 * 0,6 sous-chaîne, 0,2 à 0,5 faute de frappe.
 */
(function (global) {
  const DIRECT = 0.6;

  function fold(value) {
    return String(value == null ? "" : value)
      .normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase()
      .replace(/[^\p{L}\p{N}+#]+/gu, " ").trim();
  }

  function compact(value) {
    return fold(value).replace(/ /g, "");
  }

  function tolerance(length) {
    return length < 4 ? 0 : length < 6 ? 1 : 2;
  }

  /** Distance de Damerau-Levenshtein ; renvoie max + 1 au-delà de max. */
  function distance(a, b, max) {
    if (Math.abs(a.length - b.length) > max) return max + 1;
    const d = [];
    for (let i = 0; i <= a.length; i++) { d[i] = [i]; }
    for (let j = 1; j <= b.length; j++) d[0][j] = j;
    for (let i = 1; i <= a.length; i++) {
      for (let j = 1; j <= b.length; j++) {
        const cost = a[i - 1] === b[j - 1] ? 0 : 1;
        d[i][j] = Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost);
        if (i > 1 && j > 1 && a[i - 1] === b[j - 2] && a[i - 2] === b[j - 1]) d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
      }
    }
    return d[a.length][b.length];
  }

  function single(q, t, words) {
    if (!q || !t) return 0;
    if (q === t) return 1;
    if (t.startsWith(q)) return 0.85 + (0.05 * q.length) / t.length;
    if (words.some(function (w) { return w.startsWith(q); })) return 0.75;
    if (q.length >= 2 && t.indexOf(q) >= 0) return 0.6;
    const tol = tolerance(q.length);
    if (!tol) return 0;
    let best = distance(q, t, tol);
    for (let len = q.length - 1; len <= q.length + 1 && len <= t.length; len++) best = Math.min(best, distance(q, t.slice(0, len), tol));
    words.forEach(function (w) { best = Math.min(best, distance(q, w, tol)); });
    return best <= tol ? 0.5 - 0.1 * best : 0;
  }

  /** Score de 0 à 1 ; une saisie de plusieurs mots doit retrouver chacun de ses mots (ordre libre). */
  function score(query, text) {
    const t = compact(text);
    const words = fold(text).split(" ");
    const whole = single(compact(query), t, words);
    const tokens = fold(query).split(" ");
    if (tokens.length < 2) return whole;
    let sum = 0;
    for (let i = 0; i < tokens.length; i++) {
      const s = single(tokens[i], t, words);
      if (!s) return whole;
      sum += s;
    }
    return Math.max(whole, (0.95 * sum) / tokens.length);
  }

  /**
   * Filtre et classe des éléments par pertinence.
   * @param fields fonctions renvoyant un texte, ou {get, weight} (le poids de 1 à 3 favorise le titre, le nom…)
   * Sans saisie, renvoie la liste telle quelle ; les ex æquo gardent leur ordre d'origine.
   */
  function rank(items, query, fields) {
    if (!fold(query)) return items.slice();
    const specs = fields.map(function (f) { return typeof f === "function" ? { get: f, weight: 1 } : f; });
    const top = Math.max.apply(null, specs.map(function (f) { return f.weight; }));
    return items
      .map(function (item, index) {
        const texts = specs.map(function (f) { return String(f.get(item) == null ? "" : f.get(item)); });
        let best = score(query, texts.join(" ")) * 0.8;
        specs.forEach(function (f, i) { best = Math.max(best, score(query, texts[i]) * (0.7 + (0.3 * f.weight) / top)); });
        return { item: item, score: best, index: index };
      })
      .filter(function (r) { return r.score > 0; })
      .sort(function (a, b) { return b.score - a.score || a.index - b.index; })
      .map(function (r) { return r.item; });
  }

  // ------------------------------------------------------------ surlignage

  function matchSpan(word, tokens) {
    const folded = fold(word);
    for (let i = 0; i < tokens.length; i++) {
      const t = tokens[i];
      const at = folded.indexOf(t);
      if (at >= 0 && (t.length >= 2 || at === 0)) return [at, at + t.length];
      const tol = tolerance(t.length);
      if (tol && distance(t, folded, tol) <= tol) return [0, word.length];
    }
    return null;
  }

  function unmark(root) {
    root.querySelectorAll("mark.hl-mark").forEach(function (m) {
      m.replaceWith(document.createTextNode(m.textContent));
    });
    root.normalize();
  }

  /** Surligne dans {@code root} les mots qui correspondent à la saisie (nœuds de texte uniquement : aucun HTML injecté). */
  function mark(root, query) {
    if (!root) return;
    unmark(root);
    const tokens = fold(query).split(" ").filter(Boolean);
    if (!tokens.length) return;
    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, {
      acceptNode: function (n) {
        return n.parentElement && !n.parentElement.closest("script,style,textarea,select,option,mark") && n.nodeValue.trim()
          ? NodeFilter.FILTER_ACCEPT : NodeFilter.FILTER_REJECT;
      },
    });
    const nodes = [];
    while (walker.nextNode()) nodes.push(walker.currentNode);
    nodes.forEach(function (node) {
      const text = node.nodeValue;
      const frag = document.createDocumentFragment();
      const re = /[\p{L}\p{N}+#]+/gu;
      let last = 0;
      let found = false;
      let m;
      while ((m = re.exec(text))) {
        const span = matchSpan(m[0], tokens);
        if (!span) continue;
        found = true;
        frag.appendChild(document.createTextNode(text.slice(last, m.index + span[0])));
        const mk = document.createElement("mark");
        mk.className = "hl-mark";
        mk.textContent = text.slice(m.index + span[0], m.index + span[1]);
        frag.appendChild(mk);
        last = m.index + span[1];
      }
      if (!found) return;
      frag.appendChild(document.createTextNode(text.slice(last)));
      node.replaceWith(frag);
    });
  }

  /** Message « aucun résultat » : rappelle la saisie, conseille et propose d'effacer. */
  function emptyState(container, query, options) {
    const o = options || {};
    container.replaceChildren();
    const box = document.createElement("div");
    box.className = "hl-noresult";
    if (global.Mascotte) {
      const face = document.createElement("div");
      face.className = "hl-noresult-mascotte";
      face.setAttribute("aria-hidden", "true");
      face.innerHTML = global.Mascotte.markup({ pose: "vide" });
      box.appendChild(face);
    }
    const title = document.createElement("strong");
    title.textContent = fold(query) ? "Aucun résultat pour « " + String(query).trim() + " »" : (o.emptyTitle || "Rien à afficher");
    const hint = document.createElement("p");
    hint.textContent = fold(query)
      ? (o.hint || "Vérifiez l’orthographe ou essayez un mot plus court. Les accents et les majuscules n’ont pas d’importance.")
      : (o.emptyHint || "");
    box.append(title, hint);
    if (fold(query) && o.onClear) {
      const b = document.createElement("button");
      b.type = "button";
      b.className = "btn btn-soft btn-sm";
      b.textContent = "Effacer la recherche";
      b.addEventListener("click", o.onClear);
      box.appendChild(b);
    }
    container.appendChild(box);
  }

  // ----------------------------------------------------------- combobox

  let comboSeq = 0;

  /**
   * Liste de suggestions sous un champ de saisie : saisie différée, requêtes non bloquantes (les réponses périmées
   * sont ignorées), flèches / Entrée / Échap / Tab, surlignage, état vide.
   * @param opts.fetch    (texte) => Promise<[{key, label, flag?, sub?, badge?, hint?, disabled?, custom?}]>
   * @param opts.onPick   (item, texte) => void
   * @param opts.onEnter  (texte) => void — Entrée sans suggestion active
   * @param opts.minChars nombre minimal de caractères (0 par défaut)
   */
  function combobox(input, opts) {
    const id = "hl-combo-" + ++comboSeq;
    const wrap = document.createElement("div");
    wrap.className = "hl-combo";
    input.parentNode.insertBefore(wrap, input);
    wrap.appendChild(input);
    const menu = document.createElement("div");
    menu.className = "hl-combo-menu";
    menu.id = id;
    menu.setAttribute("role", "listbox");
    menu.hidden = true;
    wrap.appendChild(menu);
    input.setAttribute("role", "combobox");
    input.setAttribute("aria-autocomplete", "list");
    input.setAttribute("aria-expanded", "false");
    input.setAttribute("aria-controls", id);
    input.setAttribute("autocomplete", "off");

    let items = [];
    let active = -1;
    let timer = 0;
    let seq = 0;
    const minChars = opts.minChars || 0;

    function close() {
      menu.hidden = true;
      active = -1;
      input.setAttribute("aria-expanded", "false");
      input.removeAttribute("aria-activedescendant");
    }

    function setActive(i) {
      const options = menu.querySelectorAll("[role=option]");
      if (!options.length) return;
      active = (i + options.length) % options.length;
      options.forEach(function (el, n) {
        el.classList.toggle("is-active", n === active);
        el.setAttribute("aria-selected", n === active ? "true" : "false");
      });
      input.setAttribute("aria-activedescendant", options[active].id);
      options[active].scrollIntoView({ block: "nearest" });
    }

    function pick(i) {
      const item = items[i];
      if (!item || item.disabled) return;
      close();
      opts.onPick(item, input.value.trim());
    }

    function paint(list, query) {
      items = list;
      menu.replaceChildren();
      if (!list.length) {
        const empty = document.createElement("div");
        empty.className = "hl-combo-empty";
        empty.textContent = opts.emptyText || "Aucune proposition";
        menu.appendChild(empty);
      }
      list.forEach(function (item, i) {
        const row = document.createElement("div");
        row.id = id + "-" + i;
        row.className = "hl-combo-option" + (item.custom ? " is-custom" : "") + (item.disabled ? " is-disabled" : "");
        row.setAttribute("role", "option");
        row.setAttribute("aria-selected", "false");
        if (item.disabled) row.setAttribute("aria-disabled", "true");
        if (item.flag) {
          const f = document.createElement("span");
          f.className = "hl-combo-flag";
          f.textContent = item.flag;
          row.appendChild(f);
        }
        const text = document.createElement("span");
        text.className = "hl-combo-label";
        text.textContent = item.label;
        row.appendChild(text);
        if (item.hint) {
          const h = document.createElement("small");
          h.className = "hl-combo-hint";
          h.textContent = item.hint;
          text.appendChild(h);
        }
        if (item.badge || item.sub) {
          const b = document.createElement("span");
          b.className = "hl-combo-sub" + (item.badgeClass ? " " + item.badgeClass : "");
          b.textContent = item.badge || item.sub;
          row.appendChild(b);
        }
        row.addEventListener("mousedown", function (e) { e.preventDefault(); pick(i); });
        row.addEventListener("mousemove", function () { if (active !== i) setActive(i); });
        menu.appendChild(row);
        if (!item.custom) mark(text, query);
      });
      menu.hidden = false;
      input.setAttribute("aria-expanded", "true");
      if (list.length) setActive(0);
    }

    function load() {
      const query = input.value.trim();
      if (query.length < minChars) { close(); return; }
      const mine = ++seq;
      Promise.resolve(opts.fetch(query)).then(function (list) {
        if (mine !== seq || document.activeElement !== input) return;
        paint(list || [], query);
      }).catch(close);
    }

    input.addEventListener("input", function () {
      clearTimeout(timer);
      timer = setTimeout(load, opts.delay == null ? 140 : opts.delay);
    });
    input.addEventListener("focus", load);
    input.addEventListener("blur", function () { seq++; setTimeout(close, 120); });
    input.addEventListener("keydown", function (e) {
      const open = !menu.hidden;
      if (e.key === "ArrowDown") { e.preventDefault(); if (open) setActive(active + 1); else load(); }
      else if (e.key === "ArrowUp" && open) { e.preventDefault(); setActive(active - 1); }
      else if (e.key === "Home" && open) { e.preventDefault(); setActive(0); }
      else if (e.key === "End" && open) { e.preventDefault(); setActive(items.length - 1); }
      else if (e.key === "Escape" && open) { e.preventDefault(); e.stopPropagation(); close(); }
      else if (e.key === "Enter") {
        if (open && active >= 0 && items[active] && !items[active].disabled) { e.preventDefault(); pick(active); }
        else if (opts.onEnter && input.value.trim()) { e.preventDefault(); close(); opts.onEnter(input.value.trim()); }
      } else if (e.key === "Tab") close();
    });
    return { close: close, wrap: wrap };
  }

  global.HireLinkSearch = { fold: fold, compact: compact, score: score, rank: rank, mark: mark, unmark: unmark, emptyState: emptyState, combobox: combobox, DIRECT: DIRECT };
})(window);
