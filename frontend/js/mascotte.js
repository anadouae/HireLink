/**
 * Mascotte HireLink — la tête est le logo, l'écran sombre sert de visage.
 * Module autonome (aucune dépendance, aucun CDN, pas de script inline) :
 *   Mascotte.markup(opts)   SVG en couches (yeux, paupières, bras, mains, jambes, tenue, accessoires)
 *   Mascotte.toast()        notification avec mascotte (aria-live)
 *   Mascotte.confirm()      boîte de confirmation (focus piégé, Échap) → Promise<boolean>
 *   Mascotte.waiting()      bloc « réflexion en cours » (chargement long, IA)
 *   Mascotte.celebrate()    grand moment : carte + confettis légers, durée plafonnée
 *   Mascotte.welcome() / Mascotte.goodbye()   accueil (une fois par session) et au revoir
 * Styles : css/mascotte.css. prefers-reduced-motion : pose statique, pas de confettis.
 */
(function (global) {
  "use strict";

  /* ------------------------------------------------------------ contexte */
  var SESSION_KEY = "hirelink.session";
  var WELCOME_KEY = "mascotte.bienvenue";
  var FAREWELL_KEY = "mascotte.aurevoir";
  var SEEN_PREFIX = "mascotte.vu.";

  var TENUE_BY_ROLE = { CANDIDAT: "candidat", RECRUTEUR: "recruteur", ADMINISTRATEUR: "admin" };
  var POSE_BY_TONE = { ok: "succes", success: "succes", info: "notification", warn: "avertissement", warning: "avertissement", danger: "erreur", error: "erreur" };
  var TONE_CLASS = { ok: "ok", success: "ok", info: "info", warn: "warn", warning: "warn", danger: "danger", error: "danger" };

  function readSession() {
    try { return JSON.parse(localStorage.getItem(SESSION_KEY) || "null"); } catch (_) { return null; }
  }
  function currentTenue() {
    var s = readSession();
    return (s && TENUE_BY_ROLE[s.role]) || "neutre";
  }
  function firstName(fullName) {
    var clean = String(fullName || "").replace(/\s*\([^)]*\)\s*/g, " ").trim();
    return clean.split(/\s+/)[0] || "";
  }
  function reducedMotion() {
    return global.matchMedia && global.matchMedia("(prefers-reduced-motion: reduce)").matches;
  }
  function esc(s) {
    return String(s == null ? "" : s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
  }
  function safeStorage(store, op, key, value) {
    try { return op === "get" ? store.getItem(key) : op === "set" ? store.setItem(key, value) : store.removeItem(key); } catch (_) { return null; }
  }

  /* ------------------------------------------------------------- dessin */
  var DEFS =
    '<svg class="mascotte-defs" width="0" height="0" aria-hidden="true" focusable="false"><defs>' +
    '<linearGradient id="mascotte-fond" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#a24a3e"/><stop offset="1" stop-color="#c6a778"/></linearGradient>' +
    radial("bordeaux", 0, 0.28, "#7a2234") + radial("orange", 0.74, 0.04, "#e76f30") +
    radial("champagne", 0.98, 0.76, "#e2c687") + radial("ardoise", 0.26, 1, "#8c9298") +
    '<clipPath id="mascotte-ecran-forme"><rect x="55" y="31" width="90" height="90" rx="14"/></clipPath>' +
    '<g id="mascotte-cadre">' +
    '<rect x="40" y="16" width="120" height="120" rx="22" fill="url(#mascotte-fond)"/>' +
    ["bordeaux", "orange", "champagne", "ardoise"].map(function (n) {
      return '<rect x="40" y="16" width="120" height="120" rx="22" fill="url(#mascotte-' + n + ')"/>';
    }).join("") +
    "</g></defs></svg>";

  function radial(name, cx, cy, color) {
    return '<radialGradient id="mascotte-' + name + '" cx="' + cx + '" cy="' + cy + '" r="0.62">' +
      '<stop offset="0" stop-color="' + color + '"/><stop offset="1" stop-color="' + color + '" stop-opacity="0"/></radialGradient>';
  }

  function eye(cx) {
    return '<ellipse class="m-oeil" cx="' + cx + '" cy="70" rx="9" ry="12"/>' +
      '<g class="m-pupille"><circle class="m-iris" cx="' + cx + '" cy="71" r="6"/><circle class="m-trou" cx="' + cx + '" cy="71" r="3"/>' +
      '<circle class="m-eclat" cx="' + (cx + 2) + '" cy="68" r="1.6"/></g>';
  }
  function eyelids(cx, side) {
    return '<g class="m-paupiere-haut m-paupiere-haut-' + side + '"><rect x="' + (cx - 13) + '" y="34" width="26" height="50" rx="6"/></g>' +
      '<g class="m-paupiere-bas"><circle cx="' + cx + '" cy="96" r="13"/></g>';
  }

  /** Une main (repère local : le « devant » de la main est +y ; la main suit l'avant-bras). */
  function hand(x, cote) {
    return '<g class="m-main m-main-' + cote + '">' +
      '<ellipse class="m-pouce" cx="' + x + '" cy="215" rx="3" ry="5.4"/>' +
      '<rect class="m-index" x="' + (x - 1.7) + '" y="207" width="3.4" height="10" rx="1.7"/>' +
      '<g class="m-doigts"><rect x="' + (x - 6) + '" y="208" width="3.4" height="8" rx="1.7"/><rect x="' + (x - 1.7) + '" y="209" width="3.4" height="9" rx="1.7"/>' +
      '<rect x="' + (x + 2.6) + '" y="208" width="3.4" height="8" rx="1.7"/></g>' +
      '<circle class="m-paume" cx="' + x + '" cy="205" r="6.6"/></g>';
  }

  /** Un bras en deux segments (épaule puis coude) ; 'g' = gauche de l'écran, 'd' = droite. */
  function arm(cote) {
    var x = cote === "g" ? 72 : 128;
    var envelope = cote === "d"
      ? '<g class="m-enveloppe"><rect x="116" y="212" width="24" height="16" rx="2.5"/><path d="M116.8 213.6 128 221.6l11.2-8"/></g>'
      : "";
    return '<g class="m-bras m-bras-' + cote + '">' +
      '<line class="m-manche-bord" x1="' + x + '" y1="148" x2="' + x + '" y2="176"/><line class="m-manche" x1="' + x + '" y1="148" x2="' + x + '" y2="176"/>' +
      '<g class="m-avant-bras m-avant-bras-' + cote + '">' +
      '<line class="m-manche-bord" x1="' + x + '" y1="176" x2="' + x + '" y2="201"/><line class="m-manche" x1="' + x + '" y1="176" x2="' + x + '" y2="201"/>' +
      hand(x, cote) + envelope + "</g></g>";
  }

  function leg(cote) {
    var x = cote === "g" ? 88 : 112;
    var out = cote === "g" ? -3 : 3;
    return '<g class="m-jambe m-jambe-' + cote + '"><line class="m-jambe-trait" x1="' + x + '" y1="190" x2="' + x + '" y2="216"/>' +
      '<ellipse class="m-pied" cx="' + (x + out) + '" cy="221" rx="11" ry="6"/></g>';
  }

  var OUTFITS = {
    neutre: '<path class="m-col" d="M91 138q9 8 18 0"/>',
    candidat:
      '<path class="m-col-chemise" d="M100 138 88 135l3 12zM100 138l12-3-3 12z"/>' +
      '<path class="m-cravate-noeud" d="M96.5 138h7l-1 6h-5z"/><path class="m-cravate" d="M97 144h6l3.4 25-6.4 8-6.4-8z"/>',
    recruteur:
      '<path class="m-chemise" d="M91 136h18l-9 26z"/><path class="m-revers" d="M88 136l11 27-14-8zM112 136l-11 27 14-8z"/>' +
      '<circle class="m-bouton" cx="100" cy="182" r="1.8"/>' + badge("m-badge-carte", "m-badge-photo"),
    admin:
      '<path class="m-col" d="M91 138q9 8 18 0"/>' + badge("m-badge-bouclier", "m-badge-serrure"),
  };

  function badge(cardClass, markClass) {
    var card = cardClass === "m-badge-bouclier"
      ? '<path class="' + cardClass + '" d="M100 160l10 3.6v8.6c0 6-4.4 9.4-10 12-5.6-2.6-10-6-10-12v-8.6z"/><circle class="' + markClass + '" cx="100" cy="171" r="2.7"/><rect class="' + markClass + '" x="99" y="171" width="2" height="6"/>'
      : '<rect class="' + cardClass + '" x="91" y="160" width="18" height="22" rx="3.4"/><rect class="m-badge-bandeau" x="91" y="160" width="18" height="6" rx="3"/><circle class="' + markClass + '" cx="100" cy="173" r="3.4"/><rect class="m-badge-ligne" x="94.5" y="178" width="11" height="1.8" rx=".9"/>';
    return '<path class="m-cordon" d="M93 138l7 22M107 138l-7 22"/>' + card;
  }

  var HEAD_ACCESSORIES = {
    admin:
      '<path class="m-serre-tete" d="M41 86C37-12 163-12 159 86"/>' +
      '<rect class="m-ecouteur" x="30" y="70" width="14" height="30" rx="6"/><rect class="m-ecouteur" x="156" y="70" width="14" height="30" rx="6"/>' +
      '<path class="m-micro" d="M34 98c-2 18 14 26 32 20"/><circle class="m-micro-bout" cx="68" cy="118" r="4.2"/>',
  };
  var BAGGAGE = {
    candidat:
      '<g class="m-valise"><path class="m-valise-anse" d="M138 205v-4.5q0-3 3-3h6q3 0 3 3V205"/>' +
      '<rect class="m-valise-corps" x="130" y="205" width="30" height="20" rx="4"/><rect class="m-valise-bande" x="130" y="212" width="30" height="2.4"/><rect class="m-valise-fermoir" x="142" y="210.5" width="6" height="5.4" rx="1.4"/></g>',
  };

  var PROPS =
    '<g class="m-accessoires">' +
    '<g class="m-gouttes"><path d="M168 30c6 10 8 14 4 19-4 4-10 1-10-4 0-5 3-9 6-15z"/></g>' +
    '<g class="m-points"><circle cx="166" cy="34" r="2.2"/><circle cx="175" cy="24" r="3.2"/><circle cx="187" cy="10" r="4.8"/></g>' +
    '<g class="m-etincelles"><path d="M26 34l2.6 7.4 7.4 2.6-7.4 2.6-2.6 7.4-2.6-7.4-7.4-2.6 7.4-2.6z"/>' +
    '<path d="M176 52l2 5.6 5.6 2-5.6 2-2 5.6-2-5.6-5.6-2 5.6-2z"/><path d="M168 8l1.6 4.4 4.4 1.6-4.4 1.6-1.6 4.4-1.6-4.4-4.4-1.6 4.4-1.6z"/></g>' +
    '<g class="m-exclamation"><circle cx="174" cy="36" r="10"/><path d="M174 30v8M174 41.4v.2"/></g>' +
    "</g>";

  /**
   * SVG de la mascotte.
   * opts.pose    repos | bienvenue | succes | celebration | reflexion | erreur | avertissement | aurevoir | vide | notification
   * opts.tenue   neutre | candidat | recruteur | admin (défaut : selon le rôle connecté)
   * opts.variante  a | b | c — teinte du blazer (équipe recruteur) ; opts.extra : classe CSS supplémentaire
   */
  function markup(opts) {
    opts = opts || {};
    var pose = opts.pose || "repos";
    var tenue = opts.tenue || currentTenue();
    var variante = opts.variante || "a";
    if (document.body) ensureDefs();
    return '<svg class="mascotte mascotte--pose-' + pose + " mascotte--tenue-" + tenue + " mascotte--variante-" + variante + (opts.extra ? " " + opts.extra : "") +
      '" viewBox="0 0 200 244" aria-hidden="true" focusable="false">' +
      '<ellipse class="m-ombre" cx="100" cy="236" rx="40" ry="4"/>' +
      '<g class="m-saut"><g class="m-respire">' +
      '<g class="m-jambes">' + leg("g") + leg("d") + "</g>" +
      '<rect class="m-torse" x="72" y="130" width="56" height="68" rx="18"/>' +
      '<g class="m-tenue">' + (OUTFITS[tenue] || OUTFITS.neutre) + "</g>" +
      '<g class="m-tete"><use href="#mascotte-cadre"/>' +
      '<rect class="m-ecran" x="55" y="31" width="90" height="90" rx="14"/>' +
      '<g class="m-yeux m-yeux-g">' + eye(82) + '</g><g class="m-yeux m-yeux-d">' + eye(118) + "</g>" +
      '<g class="m-paupieres">' + eyelids(82, "g") + eyelids(118, "d") + "</g>" +
      '<g class="m-bouche"><path class="m-b-sourire" d="M90 98q10 8 20 0"/><path class="m-b-ouvert" d="M89 96q11 16 22 0z"/>' +
      '<ellipse class="m-b-o" cx="100" cy="101" rx="4.6" ry="5.6"/><path class="m-b-plat" d="M92 101h16"/><path class="m-b-triste" d="M91 104q9-8 18 0"/></g>' +
      '<rect class="m-reflet" x="55" y="31" width="90" height="90" rx="14"/>' + (HEAD_ACCESSORIES[tenue] || "") + "</g>" +
      '<g class="m-bras">' + arm("g") + arm("d") + "</g>" +
      (BAGGAGE[tenue] || "") + PROPS + "</g></g></svg>";
  }

  /** Trio du recruteur : une petite équipe pour les grands moments (accueil, célébrations). */
  function team(pose) {
    var support = pose === "celebration" ? "celebration" : "bienvenue";
    return '<div class="mascotte-equipe">' +
      markup({ pose: support, tenue: "recruteur", variante: "b", extra: "mascotte--second-plan" }) +
      markup({ pose: pose, tenue: "recruteur", variante: "a" }) +
      markup({ pose: support, tenue: "recruteur", variante: "c", extra: "mascotte--second-plan" }) +
      "</div>";
  }

  function ensureDefs() {
    if (document.querySelector(".mascotte-defs")) return;
    document.body.insertAdjacentHTML("afterbegin", DEFS);
  }

  /* ------------------------------------------------------------ toasts */
  var MAX_TOASTS = 3;
  var TOAST_MS = { ok: 4500, info: 4500, warn: 6000, danger: 7500 };
  var toastHost = null;

  function ensureToastHost() {
    if (toastHost && document.body.contains(toastHost)) return toastHost;
    toastHost = document.createElement("div");
    toastHost.className = "mascotte-toasts";
    toastHost.setAttribute("aria-live", "polite");
    toastHost.setAttribute("aria-atomic", "false");
    document.body.appendChild(toastHost);
    return toastHost;
  }

  function dismiss(el) {
    if (!el || el.classList.contains("mascotte-sortant")) return;
    el.classList.add("mascotte-sortant");
    setTimeout(function () { el.remove(); }, reducedMotion() ? 0 : 260);
  }

  /**
   * Même signature que l'ancien HireLink.toast : (titre, message, ton) avec ton ok | info | warn | danger.
   * options.pose / options.equipe pour les accueils.
   */
  function toast(title, message, tone, options) {
    options = options || {};
    var kind = TONE_CLASS[tone] || "info";
    var host = ensureToastHost();
    // Au-delà du maximum, on fait partir les plus anciennes ; celles déjà en train de sortir ne comptent plus.
    var actives = host.querySelectorAll(".mascotte-toast:not(.mascotte-sortant)");
    for (var i = 0; actives.length - i >= MAX_TOASTS; i++) dismiss(actives[i]);
    var el = document.createElement("div");
    el.className = "mascotte-toast mascotte-toast--" + kind + (options.equipe ? " mascotte-toast--equipe" : "");
    if (kind === "danger") el.setAttribute("role", "alert");
    el.innerHTML =
      '<div class="mascotte-toast-visuel">' + (options.equipe ? team(options.pose) : markup({ pose: options.pose || POSE_BY_TONE[tone] || "notification", tenue: options.tenue })) + "</div>" +
      '<div class="mascotte-toast-texte"><strong></strong><span></span></div>' +
      '<button type="button" class="mascotte-fermer" aria-label="Fermer la notification">✕</button>';
    el.querySelector("strong").textContent = title;
    var detail = el.querySelector(".mascotte-toast-texte span");
    detail.textContent = message || "";
    if (!message) detail.hidden = true;
    el.querySelector(".mascotte-fermer").addEventListener("click", function () { dismiss(el); });
    host.appendChild(el);
    armDismissTimer(el, options.duree || TOAST_MS[kind]);
    return el;
  }

  /** Disparition automatique, suspendue tant que la souris ou le clavier est sur la notification. */
  function armDismissTimer(el, ms) {
    var timer = null;
    function start() { timer = setTimeout(function () { dismiss(el); }, ms); }
    function stop() { clearTimeout(timer); }
    el.addEventListener("mouseenter", stop);
    el.addEventListener("focusin", stop);
    el.addEventListener("mouseleave", start);
    el.addEventListener("focusout", start);
    start();
  }

  /** Bloc de chargement : la mascotte réfléchit. */
  function waiting(text) {
    return '<div class="mascotte-attente" role="status"><div class="mascotte-attente-visuel">' + markup({ pose: "reflexion" }) + "</div><span>" + esc(text) + "</span></div>";
  }

  /* ------------------------------------------------------ confirmation */
  var FOCUSABLE = 'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';
  var dialogCounter = 0;

  /**
   * Boîte de confirmation : { title, description, confirmLabel, cancelLabel, danger } → Promise<boolean>.
   * Avec `field` ({ label, placeholder, maxlength, required }) : un champ de texte est ajouté et la promesse
   * donne le texte saisi (ou null si annulé) ; « Confirmer » reste grisé tant qu'un champ obligatoire est vide.
   * Focus piégé dans la boîte, Échap ou clic hors de la boîte = annuler, focus restitué à la fermeture.
   */
  function confirm(options) {
    options = options || {};
    var id = "mascotte-dialogue-" + (++dialogCounter);
    var previous = document.activeElement;
    var backdrop = document.createElement("div");
    backdrop.className = "mascotte-voile";
    backdrop.innerHTML =
      '<div class="mascotte-dialogue' + (options.danger ? " is-danger" : "") + '" role="alertdialog" aria-modal="true" aria-labelledby="' + id + '-titre" aria-describedby="' + id + '-texte">' +
      '<div class="mascotte-dialogue-visuel">' + markup({ pose: "avertissement" }) + "</div>" +
      '<div class="mascotte-dialogue-corps"><h3 id="' + id + '-titre"></h3><p id="' + id + '-texte"></p>' +
      '<div class="mascotte-dialogue-actions"><button type="button" class="btn btn-ghost" data-annuler></button>' +
      '<button type="button" class="btn ' + (options.danger ? "btn-danger" : "btn-primary") + '" data-confirmer></button></div></div></div>';
    var dialog = backdrop.firstChild;
    dialog.querySelector("h3").textContent = options.title || "Confirmer ?";
    var text = dialog.querySelector("p");
    text.textContent = options.description || "";
    if (!options.description) text.hidden = true;
    var cancelBtn = dialog.querySelector("[data-annuler]");
    var okBtn = dialog.querySelector("[data-confirmer]");
    cancelBtn.textContent = options.cancelLabel || "Annuler";
    okBtn.textContent = options.confirmLabel || "Confirmer";
    var input = null;
    if (options.field) {
      var field = document.createElement("label");
      field.className = "mascotte-champ";
      var caption = document.createElement("span");
      caption.textContent = options.field.label || "";
      input = document.createElement("textarea");
      input.rows = 3;
      input.maxLength = options.field.maxlength || 300;
      input.placeholder = options.field.placeholder || "";
      field.append(caption, input);
      dialog.querySelector(".mascotte-dialogue-actions").before(field);
      okBtn.disabled = !!options.field.required;
      input.addEventListener("input", function () { okBtn.disabled = !!options.field.required && !input.value.trim(); });
    }

    return new Promise(function (resolve) {
      function close(result) {
        document.removeEventListener("keydown", onKey, true);
        backdrop.classList.add("mascotte-sortant");
        setTimeout(function () { backdrop.remove(); }, reducedMotion() ? 0 : 200);
        if (previous && previous.focus) previous.focus();
        resolve(input ? (result ? input.value.trim() : null) : result);
      }
      function onKey(e) {
        if (e.key === "Escape") { e.preventDefault(); e.stopPropagation(); close(false); return; }
        if (e.key !== "Tab") return;
        var items = dialog.querySelectorAll(FOCUSABLE);
        var first = items[0];
        var last = items[items.length - 1];
        if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); }
        else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
      }
      cancelBtn.addEventListener("click", function () { close(false); });
      okBtn.addEventListener("click", function () { close(true); });
      backdrop.addEventListener("mousedown", function (e) { if (e.target === backdrop) close(false); });
      document.addEventListener("keydown", onKey, true);
      document.body.appendChild(backdrop);
      (input || (options.danger ? cancelBtn : okBtn)).focus();
    });
  }

  /* ------------------------------------------------------- célébration */
  var CELEBRATION_MS = 7000;
  var CONFETTI_MS = 3200;
  var CONFETTI_COUNT = 110;
  var CONFETTI_COLORS = ["#7a2032", "#e76f30", "#e2c687", "#f5f1ec", "#8c9298", "#b5485c"];
  var celebrationEl = null;

  function seen(key) {
    var s = readSession();
    return safeStorage(localStorage, "get", SEEN_PREFIX + key + "." + (s ? s.userId : "anonyme")) === "1";
  }
  function markSeen(key) {
    var s = readSession();
    safeStorage(localStorage, "set", SEEN_PREFIX + key + "." + (s ? s.userId : "anonyme"), "1");
  }

  /** Confettis sur un canvas qui ne capte aucun clic ; s'arrête seul et se retire. */
  function launchConfetti() {
    var canvas = document.createElement("canvas");
    canvas.className = "mascotte-confettis";
    canvas.setAttribute("aria-hidden", "true");
    var dpr = Math.min(global.devicePixelRatio || 1, 2);
    var w = global.innerWidth;
    var h = global.innerHeight;
    canvas.width = w * dpr;
    canvas.height = h * dpr;
    var ctx = canvas.getContext("2d");
    ctx.scale(dpr, dpr);
    document.body.appendChild(canvas);
    var pieces = [];
    for (var i = 0; i < CONFETTI_COUNT; i++) {
      pieces.push({
        x: w * (0.2 + Math.random() * 0.6), y: h * 0.62 + Math.random() * 30,
        vx: (Math.random() - 0.5) * 11, vy: -(6 + Math.random() * 11),
        size: 5 + Math.random() * 6, spin: Math.random() * 6, vspin: (Math.random() - 0.5) * 0.4,
        color: CONFETTI_COLORS[i % CONFETTI_COLORS.length], petal: i % 3 === 0,
      });
    }
    var start = performance.now();
    (function frame(now) {
      var elapsed = now - start;
      ctx.clearRect(0, 0, w, h);
      ctx.globalAlpha = Math.max(0, Math.min(1, (CONFETTI_MS - elapsed) / 700));
      pieces.forEach(function (p) {
        p.vy += 0.28; p.vx *= 0.99; p.x += p.vx; p.y += p.vy; p.spin += p.vspin;
        ctx.save();
        ctx.translate(p.x, p.y);
        ctx.rotate(p.spin);
        ctx.fillStyle = p.color;
        if (p.petal) { ctx.beginPath(); ctx.ellipse(0, 0, p.size * 0.9, p.size * 0.45, 0, 0, Math.PI * 2); ctx.fill(); }
        else ctx.fillRect(-p.size / 2, -p.size / 4, p.size, p.size / 2);
        ctx.restore();
      });
      if (elapsed < CONFETTI_MS) requestAnimationFrame(frame); else canvas.remove();
    })(start);
  }

  function closeCelebration() {
    if (!celebrationEl) return;
    var el = celebrationEl;
    celebrationEl = null;
    document.removeEventListener("keydown", onCelebrationKey);
    dismiss(el);
    document.querySelectorAll(".mascotte-confettis").forEach(function (c) { c.remove(); });
  }
  function onCelebrationKey(e) { if (e.key === "Escape") closeCelebration(); }

  /**
   * Grand moment (profil complet, première offre, organisation validée…) : { title, description, cle }.
   * Avec `cle`, la célébration n'a lieu qu'une fois par utilisateur ; retourne false si elle a déjà eu lieu.
   * Ne bloque rien : carte non modale, fermable, disparition automatique.
   */
  function celebrate(options) {
    options = options || {};
    if (options.cle && seen(options.cle)) return false;
    if (options.cle) markSeen(options.cle);
    closeCelebration();
    var tenue = currentTenue();
    var el = document.createElement("div");
    el.className = "mascotte-celebration";
    el.setAttribute("role", "status");
    el.innerHTML =
      '<div class="mascotte-celebration-visuel">' + (tenue === "recruteur" ? team("celebration") : markup({ pose: "celebration", tenue: tenue })) + "</div>" +
      '<div class="mascotte-celebration-texte"><strong></strong><span></span></div>' +
      '<button type="button" class="mascotte-fermer" aria-label="Fermer">✕</button>';
    el.querySelector("strong").textContent = options.title || "Bravo !";
    el.querySelector("span").textContent = options.description || "";
    el.querySelector(".mascotte-fermer").addEventListener("click", closeCelebration);
    document.body.appendChild(el);
    celebrationEl = el;
    document.addEventListener("keydown", onCelebrationKey);
    if (!reducedMotion()) launchConfetti();
    setTimeout(function () { if (celebrationEl === el) closeCelebration(); }, CELEBRATION_MS);
    return true;
  }

  /* ------------------------------------------------------ accueil / au revoir */
  /** « Bienvenue, <prénom> ! » — une seule fois par session de navigation ; équipe pour l'espace recruteur. */
  function welcome(session) {
    if (!session || safeStorage(sessionStorage, "get", WELCOME_KEY) === "fait") return;
    safeStorage(sessionStorage, "set", WELCOME_KEY, "fait");
    var name = firstName(session.fullName);
    toast(name ? "Bienvenue, " + name + " !" : "Bienvenue !", "Ravi de vous retrouver sur HireLink.", "ok",
      { pose: "bienvenue", equipe: session.role === "RECRUTEUR", duree: 5000 });
  }

  /** À appeler juste avant la redirection de déconnexion : le message s'affiche sur la page suivante. */
  function goodbye(session) {
    safeStorage(sessionStorage, "remove", WELCOME_KEY);
    var role = session && session.role;
    safeStorage(sessionStorage, "set", FAREWELL_KEY, JSON.stringify({ name: firstName(session && session.fullName), role: role }));
  }

  function showPendingFarewell() {
    var raw = safeStorage(sessionStorage, "get", FAREWELL_KEY);
    if (!raw) return;
    safeStorage(sessionStorage, "remove", FAREWELL_KEY);
    var farewell;
    try { farewell = JSON.parse(raw); } catch (_) { return; }
    toast(farewell.name ? "À bientôt, " + farewell.name + " !" : "À bientôt !", "Vous êtes bien déconnecté(e).", "info",
      { pose: "aurevoir", tenue: TENUE_BY_ROLE[farewell.role] || "neutre" });
  }

  document.addEventListener("DOMContentLoaded", showPendingFarewell);

  global.Mascotte = { markup: markup, team: team, toast: toast, waiting: waiting, confirm: confirm, celebrate: celebrate, welcome: welcome, goodbye: goodbye };
})(window);
