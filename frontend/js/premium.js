/**
 * Interface commune à toutes les pages :
 *  - HLIcons : un seul jeu d'icônes SVG (trait 1.7, 24 px), inline, compatible avec la politique de sécurité ;
 *  - HLFlags : drapeaux en image (les émojis drapeaux ne s'affichent pas sous Windows) ;
 *  - listes de choix enrichies : recherche tolérante (accents, majuscules, fautes), toutes les options visibles,
 *    icône, drapeau ou logo devant chaque choix, ajout d'une valeur personnelle quand le champ l'accepte ;
 *  - sélecteurs de date et d'heure maison (clavier, mobile, thème sombre) ;
 *  - images : cadrage automatique et monogramme si l'image est cassée ;
 *  - HireLink.openPreview : aperçu de fichier superposé, qui laisse ouvert le panneau en dessous.
 * Un champ peut garder le contrôle natif avec l'attribut data-native.
 */
(function (global) {
  "use strict";
  var doc = document;
  var reduce = function () { return global.matchMedia && global.matchMedia("(prefers-reduced-motion: reduce)").matches; };
  function esc(s) { return String(s == null ? "" : s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#39;"); }
  function fold(s) {
    return String(s == null ? "" : s).normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase().replace(/[^a-z0-9+#]+/g, " ").trim();
  }

  /* =============================================================== icônes */
  var P = {
    building: '<path d="M5 20.5V5.2A1.2 1.2 0 0 1 6.2 4h7.1a1.2 1.2 0 0 1 1.2 1.2v15.3"/><path d="M14.5 9.5h3.3a1.2 1.2 0 0 1 1.2 1.2v9.8M8 8h2M8 12h2M8 16h2M3.5 20.5h17"/>',
    video: '<rect x="3" y="6.5" width="12.5" height="11" rx="2.2"/><path d="m15.5 10.5 5-3v9l-5-3z"/>',
    phone: '<path d="M6.4 3.6h2.2l1.6 4-2 1.5a11.6 11.6 0 0 0 5.2 5.2l1.5-2 4 1.6v2.2a2 2 0 0 1-2.2 2A15.6 15.6 0 0 1 4.4 5.8a2 2 0 0 1 2-2.2z"/>',
    pin: '<path d="M12 21s-6.5-5.6-6.5-11a6.5 6.5 0 0 1 13 0c0 5.4-6.5 11-6.5 11z"/><circle cx="12" cy="10" r="2.4"/>',
    calendar: '<rect x="3.5" y="5" width="17" height="15" rx="2.2"/><path d="M3.5 10h17M8 3v4M16 3v4"/>',
    clock: '<circle cx="12" cy="12" r="8.5"/><path d="M12 7.5V12l3 2"/>',
    users: '<circle cx="9" cy="8.5" r="3.2"/><path d="M3.2 19c.6-3 2.8-4.7 5.8-4.7s5.2 1.7 5.8 4.7"/><path d="M16 5.6a3 3 0 0 1 0 5.8M17.6 14.6c1.8.5 3 2 3.4 4.4"/>',
    user: '<circle cx="12" cy="8" r="3.4"/><path d="M5.2 19.5c.7-3 3-4.6 6.8-4.6s6.1 1.6 6.8 4.6"/>',
    code: '<path d="m8.5 7.5-5 4.5 5 4.5M15.5 7.5l5 4.5-5 4.5M13.5 5l-3 14"/>',
    heart: '<path d="M12 20s-7.5-4.4-7.5-10A4.3 4.3 0 0 1 12 7.4 4.3 4.3 0 0 1 19.5 10c0 5.6-7.5 10-7.5 10z"/>',
    briefcase: '<rect x="3.5" y="7.5" width="17" height="12" rx="2"/><path d="M9 7.5V6.2A1.7 1.7 0 0 1 10.7 4.5h2.6A1.7 1.7 0 0 1 15 6.2v1.3M3.5 13h17"/>',
    flag: '<path d="M5.5 21V4M5.5 4.5h11l-2 4 2 4h-11"/>',
    star: '<path d="m12 4 2.4 5 5.4.6-4 3.7 1.1 5.4L12 16l-4.9 2.7 1.1-5.4-4-3.7 5.4-.6z"/>',
    puzzle: '<path d="M9 4.5h3.5a2 2 0 1 1 4 0H20v4a2 2 0 1 1 0 4v4H16.5a2 2 0 1 0-4 0H9v-3.5a2 2 0 1 1 0-4z"/>',
    inbox: '<path d="M3.5 13.5 6 5.5h12l2.5 8v5a1.5 1.5 0 0 1-1.5 1.5H5a1.5 1.5 0 0 1-1.5-1.5z"/><path d="M3.5 13.5h5l1 2.5h5l1-2.5h5"/>',
    check: '<path d="m5 12.5 4.2 4.2L19 7"/>',
    plus: '<path d="M12 5v14M5 12h14"/>',
    chevron: '<path d="m7 10 5 5 5-5"/>',
    left: '<path d="m14.5 6-6 6 6 6"/>',
    right: '<path d="m9.5 6 6 6-6 6"/>',
    search: '<circle cx="11" cy="11" r="6.2"/><path d="m16 16 4.5 4.5"/>',
    x: '<path d="M6 6l12 12M18 6 6 18"/>',
    money: '<rect x="3" y="6.5" width="18" height="11" rx="2"/><circle cx="12" cy="12" r="2.6"/><path d="M6.5 9.5v5M17.5 9.5v5"/>',
    globe: '<circle cx="12" cy="12" r="8.5"/><path d="M3.5 12h17M12 3.5c2.5 2.6 3.6 5.4 3.6 8.5s-1.1 5.9-3.6 8.5c-2.5-2.6-3.6-5.4-3.6-8.5S9.5 6.1 12 3.5z"/>',
    home: '<path d="M3.5 11 12 4l8.5 7"/><path d="M5.5 9.8V20h13V9.8"/><path d="M10 20v-5h4v5"/>',
    laptop: '<rect x="5" y="5" width="14" height="10" rx="1.5"/><path d="M3 18.5h18"/>',
    shuffle: '<path d="M4 7h4l8 10h4M4 17h4l2-2.5M14 9.5 16 7h4M18 5l2 2-2 2M18 15l2 2-2 2"/>',
    cap: '<path d="M2.5 9.5 12 5l9.5 4.5L12 14z"/><path d="M6.5 11.5V16c1.5 1.4 3.4 2 5.5 2s4-.6 5.5-2v-4.5M21.5 9.5v5"/>',
    layers: '<path d="m12 4 8.5 4.5L12 13 3.5 8.5z"/><path d="m3.5 12.5 8.5 4.5 8.5-4.5M3.5 16.5 12 21l8.5-4.5"/>',
    chart: '<path d="M4 20V10M10 20V4M16 20v-7M22 20H2"/>',
    sort: '<path d="M7 4v16M3.5 16.5 7 20l3.5-3.5M17 20V4M13.5 7.5 17 4l3.5 3.5"/>',
    filter: '<path d="M4 5h16l-6 7.5V19l-4 1.5v-8z"/>',
    file: '<path d="M7 3.5h7.2L19.5 9v11.5A1.5 1.5 0 0 1 18 22H7a1.5 1.5 0 0 1-1.5-1.5v-17A1.5 1.5 0 0 1 7 3.5z"/><path d="M14 3.5V9h5.5"/>',
    mail: '<rect x="3" y="5.5" width="18" height="13" rx="2"/><path d="m3.5 7 8.5 6 8.5-6"/>',
    shield: '<path d="M12 3.2 5 6v5.6c0 4.2 2.9 7.6 7 9.2 4.1-1.6 7-5 7-9.2V6z"/>',
    ban: '<circle cx="12" cy="12" r="8.5"/><path d="m6 6 12 12"/>',
    spark: '<path d="M12 3.5 13.6 9 19 10.6 13.6 12.2 12 17.7 10.4 12.2 5 10.6 10.4 9z"/>',
    language: '<path d="M4 5.5h9M8.5 4v1.5c0 4-2 7-4.5 8.5M6 9.5c1 2 3 3.6 5.5 4.5"/><path d="m12.5 20 4-9 4 9M14 17h5"/>',
    truck: '<path d="M3 6.5h11v9H3zM14 10h4l3 3v2.5h-7"/><circle cx="7" cy="17.5" r="1.8"/><circle cx="17" cy="17.5" r="1.8"/>',
    tool: '<path d="M14.5 5.5a4 4 0 0 0 4.9 4.9l-9.9 9.9a2 2 0 0 1-2.8-2.8l9.9-9.9a4 4 0 0 0-2.1-2.1z"/>',
    bank: '<path d="M3.5 9.5 12 4.5l8.5 5M5 10v7.5M9.5 10v7.5M14.5 10v7.5M19 10v7.5M3.5 20h17"/>',
    target: '<circle cx="12" cy="12" r="8.5"/><circle cx="12" cy="12" r="4.5"/><circle cx="12" cy="12" r="1"/>',
    hourglass: '<path d="M7 3.5h10M7 20.5h10M8 3.5c0 4.5 8 4.5 8 8.5s-8 4-8 8.5M16 3.5c0 4.5-8 4.5-8 8.5s8 4 8 8.5"/>',
    eye: '<path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z"/><circle cx="12" cy="12" r="2.8"/>',
    bookmark: '<path d="M6.5 4h11v16.5L12 16.5l-5.5 4z"/>',
    link: '<path d="M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1.3 1.3M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1.3-1.3"/>',
    dot: '<circle cx="12" cy="12" r="3"/>',
    dots: '<circle cx="5.5" cy="12" r="1.4"/><circle cx="12" cy="12" r="1.4"/><circle cx="18.5" cy="12" r="1.4"/>',
    pen: '<path d="M15.2 4.9 19.1 8.8M4 20l1-4.2L15.9 5.1a2.1 2.1 0 0 1 3 3L8.2 18.9z"/><path d="m13.4 7.4 3.2 3.2"/>',
    pencil: '<path d="M4 20l1.2-4.6L16.6 4.4a2 2 0 0 1 2.9 2.9L8 18.8z"/><path d="M14.6 6.4 17.7 9.5"/>',
    trash: '<path d="M4.5 7h15M9.5 7V5.3A1.3 1.3 0 0 1 10.8 4h2.4a1.3 1.3 0 0 1 1.3 1.3V7"/><path d="m6.8 7 .8 12.2A1.6 1.6 0 0 0 9.2 20.7h5.6a1.6 1.6 0 0 0 1.6-1.5L17.2 7"/><path d="M10.3 11v6M13.7 11v6"/>',
    paperclip: '<path d="M19 10.5 11.4 18a4 4 0 0 1-5.7-5.7l8-8a2.7 2.7 0 0 1 3.8 3.8l-7.9 7.9a1.4 1.4 0 0 1-2-2l7.1-7"/>',
    palette: '<path d="M12 3.5a8.5 8.5 0 1 0 0 17c1.3 0 2-.9 2-1.9s-.7-1.8-.4-2.6c.3-.9 1.1-1 2-1h1.4a3.5 3.5 0 0 0 3.5-3.6C20.5 6.7 16.6 3.5 12 3.5z"/><circle cx="8" cy="11" r="1.2"/><circle cx="12" cy="8" r="1.2"/><circle cx="16" cy="11" r="1.2"/>',
    grip: '<circle cx="9" cy="6.5" r="1.3"/><circle cx="15" cy="6.5" r="1.3"/><circle cx="9" cy="12" r="1.3"/><circle cx="15" cy="12" r="1.3"/><circle cx="9" cy="17.5" r="1.3"/><circle cx="15" cy="17.5" r="1.3"/>',
    note: '<path d="M18.5 14.5V5.2A1.2 1.2 0 0 0 17.3 4H6.7A1.2 1.2 0 0 0 5.5 5.2v13.6A1.2 1.2 0 0 0 6.7 20h7.8z"/><path d="M18.5 14.5h-3.2a1.8 1.8 0 0 0-1.8 1.8v3.7"/><path d="M9 9h6M9 12.5h4"/>',
    save: '<path d="M6.5 4h8.6L19 8.4V19a1.5 1.5 0 0 1-1.5 1.5H6.5A1.5 1.5 0 0 1 5 19V5.5A1.5 1.5 0 0 1 6.5 4z"/><path d="M8.5 4v5h6V4M8 15.5l2.4 2.4 4.6-4.8"/>',
    move: '<path d="M12 3.5v17M3.5 12h17"/><path d="m9.3 6.3 2.7-2.8 2.7 2.8M9.3 17.7l2.7 2.8 2.7-2.8M6.3 9.3 3.5 12l2.8 2.7M17.7 9.3 20.5 12l-2.8 2.7"/>',
  };
  function svg(name, cls) {
    return '<svg class="hl-ico' + (cls ? " " + cls : "") + '" viewBox="0 0 24 24" aria-hidden="true" focusable="false">' + (P[name] || P.dot) + "</svg>";
  }
  /** Icône déduite du texte d'une option (« Sur site » → bâtiment, « CDI » → mallette…). */
  var GUESS = [
    [/visio|video|meet|teams|zoom|en ligne|distance/, "video"],
    [/sur site|presentiel|locaux|onsite/, "building"],
    [/teletravail|remote|full remote/, "laptop"],
    [/hybride/, "shuffle"],
    [/rh\b|ressources humaines|recrut/, "users"],
    [/techni|developp|dev\b|logiciel|informatique|code|java|data/, "code"],
    [/manager|direction|responsable/, "briefcase"],
    [/final/, "flag"],
    [/etude de cas|cas pratique|test/, "puzzle"],
    [/equipe|team|culture/, "heart"],
    [/cdi|cdd|stage|alternance|freelance|interim|contrat|emploi|poste/, "briefcase"],
    [/bac|master|licence|doctorat|ecole|universit|ensa|encg|formation|diplome|niveau/, "cap"],
    [/salaire|remuner|mad|dh\b|eur|prime|budget/, "money"],
    [/francais|anglais|arabe|espagnol|allemand|langue|amazigh/, "language"],
    [/immediat|tout de suite|des que/, "spark"],
    [/semaine|mois|jour|preavis|delai|duree|min\b|heure/, "hourglass"],
    [/fuseau|utc|casablanca \(|paris$|londres/, "globe"],
    [/recent|date|plus ancien|nouveau/, "calendar"],
    [/score|compatib|match|pertinence|meilleur/, "target"],
    [/tous|toutes|tout\b|indifferent/, "layers"],
    [/refus|rejet|annul/, "ban"],
    [/entretien/, "users"],
    [/offre|annonce/, "file"],
    [/logistique|transport/, "truck"],
    [/banque|finance|compta|assurance/, "bank"],
    [/industrie|genie|mecani|electri|btp|civil/, "tool"],
    [/marketing|commerce|vente|communication/, "chart"],
    [/sante|medecin|pharma|infirm/, "heart"],
    [/securite|cyber/, "shield"],
    [/oui\b/, "check"], [/non\b/, "x"],
  ];
  function guess(text) {
    var f = fold(text);
    for (var i = 0; i < GUESS.length; i++) if (GUESS[i][0].test(f)) return GUESS[i][1];
    return "";
  }
  global.HLIcons = { svg: svg, guess: guess, names: Object.keys(P) };

  /* ============================================================== drapeaux */
  var SVG_FLAGS = {
    ma: '<svg class="hl-flag hl-flag--ma" viewBox="0 0 900 600" width="20" height="15" aria-label="Maroc" role="img"><rect width="900" height="600" fill="#c1272d"/><path d="M450 195 L490 320 L385 243 L515 243 L410 320 Z" fill="none" stroke="#006233" stroke-width="19" stroke-linejoin="round"/></svg>',
    fr: '<svg class="hl-flag hl-flag--fr" viewBox="0 0 900 600" width="20" height="15" aria-label="France" role="img"><rect width="300" height="600" fill="#002654"/><rect x="300" width="300" height="600" fill="#ffffff"/><rect x="600" width="300" height="600" fill="#ce1126"/></svg>',
    es: '<svg class="hl-flag hl-flag--es" viewBox="0 0 900 600" width="20" height="15" aria-label="Espagne" role="img"><rect width="900" height="150" fill="#aa151b"/><rect y="150" width="900" height="300" fill="#f1bf00"/><rect y="450" width="900" height="150" fill="#aa151b"/></svg>',
    de: '<svg class="hl-flag hl-flag--de" viewBox="0 0 900 600" width="20" height="15" aria-label="Allemagne" role="img"><rect width="900" height="200" fill="#000000"/><rect y="200" width="900" height="200" fill="#dd0000"/><rect y="400" width="900" height="200" fill="#ffce00"/></svg>',
    gb: '<svg class="hl-flag hl-flag--gb" viewBox="0 0 900 600" width="20" height="15" aria-label="Royaume-Uni" role="img"><rect width="900" height="600" fill="#012169"/><path d="M0 0 L900 600 M900 0 L0 600" stroke="#fff" stroke-width="60"/><path d="M0 0 L900 600 M900 0 L0 600" stroke="#c8102e" stroke-width="36"/><path d="M450 0 V600 M0 300 H900" stroke="#fff" stroke-width="100"/><path d="M450 0 V600 M0 300 H900" stroke="#c8102e" stroke-width="60"/></svg>',
    us: '<svg class="hl-flag hl-flag--us" viewBox="0 0 900 600" width="20" height="15" aria-label="USA" role="img"><rect width="900" height="600" fill="#b22234"/><rect y="46" width="900" height="46" fill="#fff"/><rect y="138" width="900" height="46" fill="#fff"/><rect y="230" width="900" height="46" fill="#fff"/><rect y="322" width="900" height="46" fill="#fff"/><rect y="414" width="900" height="46" fill="#fff"/><rect y="506" width="900" height="46" fill="#fff"/><rect width="360" height="322" fill="#3c3b6e"/></svg>',
    ca: '<svg class="hl-flag hl-flag--ca" viewBox="0 0 900 600" width="20" height="15" aria-label="Canada" role="img"><rect width="225" height="600" fill="#d80621"/><rect x="225" width="450" height="600" fill="#ffffff"/><rect x="675" width="225" height="600" fill="#d80621"/><path d="M450 180 L470 260 L520 250 L480 300 L500 360 L450 330 L400 360 L420 300 L380 250 L430 260 Z" fill="#d80621"/></svg>'
  };
  var COUNTRY = {
    maroc: "ma", morocco: "ma", france: "fr", espagne: "es", spain: "es", belgique: "be", suisse: "ch", canada: "ca",
    allemagne: "de", germany: "de", "royaume uni": "gb", angleterre: "gb", "etats unis": "us", usa: "us", italie: "it", portugal: "pt",
    "pays bas": "nl", tunisie: "tn", algerie: "dz", senegal: "sn", "cote d ivoire": "ci", egypte: "eg", "emirats arabes unis": "ae",
    qatar: "qa", "arabie saoudite": "sa", irlande: "ie", luxembourg: "lu", mauritanie: "mr", turquie: "tr", chine: "cn", japon: "jp",
    marocain: "ma", marocaine: "ma", francais: "fr", francaise: "fr", espagnol: "es", espagnole: "es",
  };
  var MA_CITIES = ("casablanca rabat tanger tangier tetouan fes fez marrakech marrakesh agadir kenitra meknes oujda sale temara mohammedia " +
    "el jadida safi nador beni mellal khouribga settat berrechid laayoune dakhla errachidia ouarzazate essaouira larache ksar el kebir " +
    "al hoceima chefchaouen taza guelmim tiznit berkane khemisset sidi kacem fnideq mdiq martil benguerir bouskoura nouaceur skhirat " +
    "ifrane azrou midelt taroudant inezgane ait melloul tan tan zagora tinghir youssoufia sefrou").split(" ");
  var MA_SET = {};
  // villes de plusieurs mots
  ["el jadida", "beni mellal", "ksar el kebir", "al hoceima", "sidi kacem", "ait melloul", "tan tan"].forEach(function (c) { MA_SET[c] = 1; });
  MA_CITIES.forEach(function (c) { MA_SET[c] = 1; });
  var WORLD = { paris: "fr", lyon: "fr", marseille: "fr", toulouse: "fr", lille: "fr", bordeaux: "fr", nantes: "fr", madrid: "es", barcelone: "es",
    barcelona: "es", seville: "es", malaga: "es", bruxelles: "be", geneve: "ch", zurich: "ch", montreal: "ca", toronto: "ca", berlin: "de",
    munich: "de", londres: "gb", london: "gb", dublin: "ie", lisbonne: "pt", amsterdam: "nl", tunis: "tn", alger: "dz", dakar: "sn",
    abidjan: "ci", "le caire": "eg", dubai: "ae", doha: "qa", riyad: "sa", milan: "it", rome: "it", "new york": "us", istanbul: "tr" };
  function isoFromEmoji(e) {
    var cps = Array.from(String(e || "")).map(function (c) { return c.codePointAt(0); }).filter(function (c) { return c >= 0x1f1e6 && c <= 0x1f1ff; });
    return cps.length === 2 ? String.fromCharCode(cps[0] - 0x1f1e6 + 97, cps[1] - 0x1f1e6 + 97) : "";
  }
  /** Code pays (ISO 3166, minuscules) déduit d'un texte de lieu : « Tanger · Hybride » → « ma ». */
  function isoOf(text) {
    var fromEmoji = isoFromEmoji(text);
    if (fromEmoji) return fromEmoji;
    var parts = String(text || "").split(/[·,|/()\-–]/);
    for (var i = parts.length - 1; i >= 0; i--) {
      var f = fold(parts[i]);
      if (!f) continue;
      if (COUNTRY[f]) return COUNTRY[f];
      if (MA_SET[f]) return "ma";
      if (WORLD[f]) return WORLD[f];
      var w = f.split(" ");
      for (var j = 0; j < w.length; j++) { if (MA_SET[w[j]]) return "ma"; if (COUNTRY[w[j]]) return COUNTRY[w[j]]; if (WORLD[w[j]]) return WORLD[w[j]]; }
    }
    return "";
  }
  function flagHtml(isoOrEmoji, title) {
    var iso = /^[a-z]{2}$/i.test(isoOrEmoji || "") ? String(isoOrEmoji).toLowerCase() : isoFromEmoji(isoOrEmoji) || isoOf(isoOrEmoji);
    if (!iso) return "";
    if (SVG_FLAGS[iso]) return SVG_FLAGS[iso];
    return '<img class="hl-flag" src="https://flagcdn.com/w40/' + iso + '.png" srcset="https://flagcdn.com/w80/' + iso + '.png 2x" width="20" height="15" alt="" loading="lazy"' +
      (title ? ' title="' + esc(title) + '"' : "") + ' />';
  }
  function flagEl(isoOrEmoji) {
    var t = doc.createElement("span");
    t.innerHTML = flagHtml(isoOrEmoji);
    return t.firstChild;
  }
  global.HLFlags = { isoOf: isoOf, isoFromEmoji: isoFromEmoji, html: flagHtml, el: flagEl };

  /* =========================================================== écoles */
  var SCHOOLS = [
    [/ensa\s*(de\s*)?tetouan|ensa\s*tetouan/, "ensa-tetouan.ac.ma"], [/ensa\s*(de\s*)?tanger|ensat\b/, "ensat.ac.ma"],
    [/ensa\s*agadir/, "ensa-agadir.ac.ma"], [/ensa\s*fes/, "ensaf.ac.ma"], [/ensa\s*kenitra/, "ensa.uit.ac.ma"], [/ensa\s*marrakech/, "ensa.ac.ma"],
    [/ensa\s*oujda/, "ensao.ump.ma"], [/ensa\s*el jadida/, "ensaj.ucd.ac.ma"], [/ensa\s*safi/, "ensas.uca.ma"], [/ensa\s*khouribga/, "ensak.usms.ac.ma"],
    [/ensa\s*berrechid/, "ensab.ac.ma"], [/ensa\s*al hoceima/, "ensah.ma"], [/encg\s*casa/, "encgcasa.ma"], [/encg\s*tanger/, "encgt.ma"],
    [/encg\s*settat/, "encg-settat.ma"], [/ensias/, "ensias.um5.ac.ma"], [/\bemi\b|mohammadia d ingenieurs/, "emi.ac.ma"], [/ehtp/, "ehtp.ac.ma"],
    [/inpt/, "inpt.ac.ma"], [/ensem/, "ensem.ac.ma"], [/ensam\s*casa/, "ensam-casa.ma"], [/ensam\s*meknes/, "ensam-umi.ac.ma"],
    [/fst\s*tanger/, "fstt.ac.ma"], [/fst\s*mohammedia/, "fstm.ac.ma"], [/iscae/, "groupeiscae.ma"], [/\bhem\b/, "hem.ac.ma"],
    [/esca/, "esca.ma"], [/\buir\b|internationale de rabat/, "uir.ac.ma"], [/um6p|mohammed vi polytechnique/, "um6p.ma"],
    [/akhawayn|\baui\b/, "aui.ma"], [/hassan ii/, "univh2c.ma"], [/mohammed v\b/, "um5.ac.ma"], [/abdelmalek essaadi/, "uae.ac.ma"],
    [/cadi ayyad/, "uca.ma"], [/ibn tofail/, "uit.ac.ma"], [/ibn zohr/, "uiz.ac.ma"], [/sidi mohamed ben abdellah/, "usmba.ac.ma"],
    [/mohammed premier|mohammed 1er/, "ump.ma"], [/est\s*casa/, "est-uh2c.ac.ma"], [/inseec|polytechnique|centrale|hec|essec|insa/, ""],
  ];
  function schoolDomain(text) {
    var f = fold(text);
    for (var i = 0; i < SCHOOLS.length; i++) if (SCHOOLS[i][0].test(f)) return SCHOOLS[i][1];
    return "";
  }
  function monogram(text) {
    var w = String(text || "?").replace(/[()]/g, " ").trim().split(/\s+/).filter(Boolean);
    var m = w.length > 1 ? (w[0][0] + w[1][0]) : (w[0] || "?").slice(0, 2);
    return m.toUpperCase();
  }
  /** Logo d'école : logo local (ENSA / UAE) ou logo officiel du site, sinon monogramme de prestige. */
  function schoolLogoHtml(text) {
    var f = fold(text);
    var assetPath = (location.pathname.indexOf("/app/") >= 0 ? "../" : "") + "assets/";
    if (/ensa|ensat/.test(f)) {
      return '<span class="hl-logo-chip hl-school-chip" title="ENSA"><img src="' + assetPath + 'logo-ensa.png" alt="ENSA" onerror="this.onerror=null;this.src=\'' + assetPath + 'logo-hirelink-icon.png\'" /></span>';
    }
    if (/uae|abdelmalek/.test(f)) {
      return '<span class="hl-logo-chip hl-school-chip" title="UAE"><img src="' + assetPath + 'logo-uae.png" alt="UAE" onerror="this.onerror=null;this.src=\'' + assetPath + 'logo-hirelink-icon.png\'" /></span>';
    }
    var d = schoolDomain(text);
    var mono = '<span class="hl-mono hl-school-mono" aria-hidden="true" title="' + esc(text) + '">' + svg("cap") + '<b style="font-size: var(--text-2xs);margin-left:3px">' + esc(monogram(text)) + "</b></span>";
    if (!d) return mono;
    return '<span class="hl-logo-chip hl-school-chip"><img src="https://www.google.com/s2/favicons?domain=' + d + '&sz=64" alt="" loading="lazy" data-mono="' + esc(monogram(text)) + '" onerror="this.replaceWith(document.createRange().createContextualFragment(\'' + mono.replace(/'/g, "\\'") + '\'))" /></span>';
  }
  global.HLSchools = { domain: schoolDomain, logoHtml: schoolLogoHtml, monogram: monogram };

  /** Visuel d'une option : drapeau (lieu), logo (école), sinon icône du jeu commun. */
  function visualFor(text, ctx, explicitIcon) {
    if (explicitIcon) return svg(explicitIcon);
    var c = ctx || "";
    if (/school|ecole|etabl/.test(c)) return schoolLogoHtml(text);
    if (/place|ville|city|pays|country|lieu|nation|location/.test(c)) { var fl = flagHtml(text); if (fl) return fl; return svg("pin"); }
    var fl2 = isoOf(text) ? flagHtml(text) : "";
    if (fl2) return fl2;
    if (schoolDomain(text)) return schoolLogoHtml(text);
    var g = guess(text);
    return g ? svg(g) : "";
  }
  global.HLVisual = visualFor;

  // Images cassées : monogramme propre (délégation, fonctionne aussi pour le contenu ajouté plus tard).
  doc.addEventListener("error", function (e) {
    var img = e.target;
    if (!img || img.tagName !== "IMG" || img.dataset.hlBroken) return;
    if (img.classList.contains("hl-flag")) { img.remove(); return; }
    var mono = img.getAttribute("data-mono") || (img.alt ? monogram(img.alt) : "");
    var framed = img.closest(".hl-logo-chip, .rc-avatar, .hl-avatar, .co-logo, .oc-logo, .org-logo, [class*='logo'], [class*='avatar']");
    if (!mono && !framed) return;
    img.dataset.hlBroken = "1";
    var s = doc.createElement("span");
    s.className = "hl-mono";
    s.textContent = mono || "•";
    img.replaceWith(s);
  }, true);

  /* ===================================================== listes de choix */
  var openPop = null;
  function closePop() {
    if (!openPop) return;
    var p = openPop;
    openPop = null;
    p.el.classList.remove("is-open");
    p.btn.setAttribute("aria-expanded", "false");
    setTimeout(function () { p.el.remove(); }, reduce() ? 0 : 160);
    if (p.onClose) p.onClose();
  }
  function placePop(pop, anchor, minW) {
    var r = anchor.getBoundingClientRect();
    var vw = global.innerWidth, vh = global.innerHeight;
    var mobile = vw < 560;
    pop.classList.toggle("is-sheet", mobile);
    if (mobile) { pop.style.left = pop.style.top = pop.style.width = ""; return; }
    var w = Math.max(r.width, minW || 260);
    pop.style.width = Math.min(w, vw - 16) + "px";
    pop.style.left = Math.max(8, Math.min(r.left, vw - w - 8)) + "px";
    var h = pop.offsetHeight || 320;
    var below = vh - r.bottom;
    pop.style.top = (below < h + 12 && r.top > below ? Math.max(8, r.top - h - 6) : r.bottom + 6) + "px";
  }
  function openPopover(btn, build, minW, onClose) {
    closePop();
    var pop = doc.createElement("div");
    pop.className = "hl-pop";
    pop.setAttribute("role", "dialog");
    doc.body.appendChild(pop);
    // au-dessus des panneaux et assistants (z-index jusqu'à 9800)
    pop.style.zIndex = "10050";
    build(pop);
    placePop(pop, btn, minW);
    openPopover.anchor = btn;
    openPop = { el: pop, btn: btn, onClose: onClose, minW: minW };
    btn.setAttribute("aria-expanded", "true");
    requestAnimationFrame(function () { pop.classList.add("is-open"); });
    return pop;
  }
  doc.addEventListener("mousedown", function (e) {
    if (openPop && !openPop.el.contains(e.target) && !openPop.btn.contains(e.target)) closePop();
  }, true);
  global.addEventListener("resize", function () { if (openPop) placePop(openPop.el, openPop.btn, openPop.minW); });
  global.addEventListener("scroll", function (e) {
    if (openPop && !openPop.el.contains(e.target)) placePop(openPop.el, openPop.btn, openPop.minW);
  }, true);
  doc.addEventListener("keydown", function (e) {
    if (e.key === "Escape" && openPop) { e.preventDefault(); e.stopImmediatePropagation(); var b = openPop.btn; closePop(); b.focus(); }
  }, true);

  var NO_CUSTOM = /sort|tri|period|periode|view|vue|order/i;
  function allowsCustom(sel) {
    if (sel.hasAttribute("data-allow-custom")) return true;
    if (sel.hasAttribute("data-no-custom")) return false;
    var key = (sel.id || "") + " " + (sel.name || "") + " " + (sel.className || "");
    if (NO_CUSTOM.test(key)) return false;
    return true;
  }
  function ctxOf(sel) {
    var lab = sel.id ? doc.querySelector('label[for="' + sel.id + '"]') : null;
    var l = (lab && lab.textContent) || (sel.closest("label") && sel.closest("label").textContent) || "";
    return fold((sel.getAttribute("data-kind") || "") + " " + (sel.id || "") + " " + (sel.name || "") + " " + l);
  }
  function optVisual(o, ctx) {
    return visualFor(o.text, ctx, o.getAttribute("data-icon"));
  }
  function paintBtn(sel) {
    var b = sel._hlBtn;
    if (!b) return;
    var o = sel.options[sel.selectedIndex];
    var label = o ? o.text : "";
    var ph = !o || (!o.value && /choisi|selection|--|—/i.test(o.text));
    b.querySelector(".hl-sel-value").innerHTML = (o ? '<span class="hl-sel-vis">' + optVisual(o, sel._hlCtx) + "</span>" : "") +
      '<span class="hl-sel-text">' + esc(label || "Choisir…") + "</span>";
    b.classList.toggle("is-placeholder", !!ph);
    b.disabled = sel.disabled;
    b.hidden = sel.hidden || (sel.style.display === "none");
    b.title = label;
  }
  function enhanceSelect(sel) {
    if (sel._hlBtn || sel.multiple || sel.hasAttribute("data-native") || sel.closest("[data-native]") || (sel.size && sel.size > 1)) return;
    var b = doc.createElement("button");
    b.type = "button";
    b.className = "hl-sel " + (sel.className || "").replace(/\bhl-native\b/g, "");
    b.setAttribute("aria-haspopup", "listbox");
    b.setAttribute("aria-expanded", "false");
    var lab = sel.getAttribute("aria-label") || (sel.id && doc.querySelector('label[for="' + sel.id + '"]') ? doc.querySelector('label[for="' + sel.id + '"]').textContent.trim() : "");
    if (lab) b.setAttribute("aria-label", lab);
    b.innerHTML = '<span class="hl-sel-value"></span>' + svg("chevron", "hl-sel-chev");
    sel.parentNode.insertBefore(b, sel.nextSibling);
    sel.classList.add("hl-native");
    sel.tabIndex = -1;
    sel.setAttribute("aria-hidden", "true");
    sel._hlBtn = b;
    sel._hlCtx = ctxOf(sel);
    if (sel.id) { var l2 = doc.querySelector('label[for="' + sel.id + '"]'); if (l2) l2.addEventListener("click", function (e) { e.preventDefault(); b.focus(); b.click(); }); }
    paintBtn(sel);
    sel.addEventListener("change", function () { paintBtn(sel); });
    new MutationObserver(function () { paintBtn(sel); }).observe(sel, { childList: true, subtree: true, attributes: true, attributeFilter: ["disabled", "hidden", "style"] });
    b.addEventListener("click", function () { if (openPop && openPop.btn === b) closePop(); else openSelect(sel); });
    b.addEventListener("keydown", function (e) {
      if (e.key === "ArrowDown" || e.key === "ArrowUp" || e.key === "Enter" || e.key === " ") { e.preventDefault(); openSelect(sel); }
      else if (e.key.length === 1 && /\S/.test(e.key)) { e.preventDefault(); openSelect(sel, e.key); }
    });
  }
  function choose(sel, value, text) {
    var found = Array.prototype.find.call(sel.options, function (o) { return o.value === value; });
    if (!found) {
      found = new Option(text, value);
      found.setAttribute("data-custom", "1");
      sel.appendChild(found);
    }
    sel.value = value;
    sel.dispatchEvent(new Event("input", { bubbles: true }));
    sel.dispatchEvent(new Event("change", { bubbles: true }));
    paintBtn(sel);
  }
  function openSelect(sel, firstChar) {
    var b = sel._hlBtn;
    var custom = allowsCustom(sel);
    var all = Array.prototype.map.call(sel.options, function (o, i) {
      var g = o.parentNode && o.parentNode.tagName === "OPTGROUP" ? o.parentNode.label : "";
      return { o: o, i: i, text: o.text, value: o.value, group: g, disabled: o.disabled };
    });
    var many = true; // Toujours proposer la recherche intelligente
    var active = 0, rows = [];
    openPopover(b, function (pop) {
      pop.classList.add("hl-pop-select");
      pop.innerHTML = '<div class="hl-pop-search">' + svg("search") + '<input type="text" placeholder="Rechercher ou saisir une option…" aria-label="Rechercher dans la liste" autocomplete="off" spellcheck="false" /></div>' +
        '<div class="hl-pop-list" role="listbox"></div><div class="hl-pop-foot"></div>';
      var input = pop.querySelector("input");
      var list = pop.querySelector(".hl-pop-list");
      var foot = pop.querySelector(".hl-pop-foot");
      function render() {
        var q = input ? input.value.trim() : "";
        var items = all;
        if (q) {
          if (global.HireLinkSearch && HireLinkSearch.rank) items = HireLinkSearch.rank(all, q, [function (x) { return x.text + " " + x.group; }]);
          else items = all.filter(function (x) { return fold(x.text).indexOf(fold(q)) >= 0; });
        }
        rows = items.slice();
        var exact = all.some(function (x) { return fold(x.text) === fold(q); });
        if (q && custom && !exact) rows.push({ add: true, text: q, value: q });
        list.innerHTML = "";
        var lastGroup = null;
        rows.forEach(function (x, n) {
          if (!x.add && !q && x.group && x.group !== lastGroup) {
            var gh = doc.createElement("div"); gh.className = "hl-pop-group"; gh.textContent = x.group; list.appendChild(gh); lastGroup = x.group;
          }
          var r = doc.createElement("div");
          r.className = "hl-pop-opt" + (x.add ? " is-add" : "") + (x.disabled ? " is-disabled" : "") + (!x.add && x.i === sel.selectedIndex ? " is-selected" : "");
          r.setAttribute("role", "option");
          r.id = "hl-opt-" + n;
          r.innerHTML = x.add
            ? '<span class="hl-pop-vis">' + svg("plus") + '</span><span class="hl-pop-txt">Ajouter « ' + esc(x.text) + " »</span>"
            : '<span class="hl-pop-vis">' + optVisual(x.o, sel._hlCtx) + '</span><span class="hl-pop-txt">' + esc(x.text || "—") + "</span>" + (x.i === sel.selectedIndex ? svg("check", "hl-pop-check") : "");
          if (!x.add && q && global.HireLinkSearch && HireLinkSearch.mark) HireLinkSearch.mark(r.querySelector(".hl-pop-txt"), q);
          r.addEventListener("mousedown", function (e) { e.preventDefault(); });
          r.addEventListener("click", function () { pick(n); });
          r.addEventListener("mousemove", function () { setActive(n, true); });
          list.appendChild(r);
        });
        if (!rows.length) list.innerHTML = '<div class="hl-pop-empty">Aucun résultat pour « ' + esc(q) + " ». Vérifiez l’orthographe.</div>";
        foot.textContent = rows.filter(function (x) { return !x.add; }).length + " sur " + all.length + (custom ? " · Entrée pour ajouter votre valeur" : "");
        var cur = rows.findIndex(function (x) { return !x.add && x.i === sel.selectedIndex; });
        setActive(q ? 0 : Math.max(0, cur));
      }
      function setActive(n, noScroll) {
        var els = list.querySelectorAll(".hl-pop-opt");
        if (!els.length) return;
        active = (n + els.length) % els.length;
        els.forEach(function (el, k) { el.classList.toggle("is-active", k === active); });
        if (!noScroll) els[active].scrollIntoView({ block: "nearest" });
        (input || b).setAttribute("aria-activedescendant", els[active].id);
      }
      function pick(n) {
        var x = rows[n];
        if (!x || x.disabled) return;
        if (x.add) choose(sel, x.value, x.text); else choose(sel, x.value, x.text);
        closePop();
        b.focus();
      }
      function keys(e) {
        if (e.key === "ArrowDown") { e.preventDefault(); setActive(active + 1); }
        else if (e.key === "ArrowUp") { e.preventDefault(); setActive(active - 1); }
        else if (e.key === "Home" && !input) { e.preventDefault(); setActive(0); }
        else if (e.key === "End" && !input) { e.preventDefault(); setActive(rows.length - 1); }
        else if (e.key === "Enter") { e.preventDefault(); pick(active); }
        else if (e.key === "Tab") closePop();
      }
      if (input) {
        input.addEventListener("input", render);
        input.addEventListener("keydown", keys);
        if (firstChar) input.value = firstChar;
        setTimeout(function () { input.focus(); }, 20);
      } else {
        pop.tabIndex = -1;
        pop.addEventListener("keydown", keys);
        setTimeout(function () { pop.focus(); }, 20);
      }
      render();
    }, 240);
  }

  /* ================================================== dates et heures */
  var MONTHS = ["janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre", "novembre", "décembre"];
  var DAYS = ["L", "M", "M", "J", "V", "S", "D"];
  var pad = function (n) { return String(n).padStart(2, "0"); };
  function iso(d) { return d.getFullYear() + "-" + pad(d.getMonth() + 1) + "-" + pad(d.getDate()); }
  function parseDate(s) { var m = /^(\d{4})-(\d{2})-(\d{2})/.exec(s || ""); return m ? new Date(+m[1], +m[2] - 1, +m[3]) : null; }
  function human(type, v) {
    if (!v) return "";
    if (type === "time") return v.slice(0, 5).replace(":", " h ");
    var d = parseDate(v);
    if (!d) return v;
    var t = d.toLocaleDateString("fr-FR", { weekday: "short", day: "numeric", month: "long", year: "numeric" });
    if (type === "datetime-local" && v.length >= 16) t += " · " + v.slice(11, 16).replace(":", " h ");
    return t.charAt(0).toUpperCase() + t.slice(1);
  }
  var nativeValue = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, "value");
  function setVal(input, v) {
    nativeValue.set.call(input, v);
    input.dispatchEvent(new Event("input", { bubbles: true }));
    input.dispatchEvent(new Event("change", { bubbles: true }));
    paintDate(input);
  }
  function paintDate(input) {
    var b = input._hlBtn;
    if (!b) return;
    var v = nativeValue.get.call(input);
    var type = input._hlType;
    b.querySelector(".hl-sel-text").textContent = v ? human(type, v) : (input.getAttribute("placeholder") || (type === "time" ? "Choisir l’heure" : "Choisir une date"));
    b.classList.toggle("is-placeholder", !v);
    b.disabled = input.disabled || input.readOnly;
    b.hidden = input.hidden;
    if (input.getAttribute("aria-invalid") === "true") b.setAttribute("aria-invalid", "true"); else b.removeAttribute("aria-invalid");
  }
  function enhanceDate(input) {
    var type = input.type;
    if (input._hlBtn || input.hasAttribute("data-native") || input.closest("[data-native]")) return;
    var b = doc.createElement("button");
    b.type = "button";
    b.className = "hl-sel hl-datebtn " + (input.className || "");
    b.setAttribute("aria-haspopup", "dialog");
    b.innerHTML = '<span class="hl-sel-value"><span class="hl-sel-vis">' + svg(type === "time" ? "clock" : "calendar") + '</span><span class="hl-sel-text"></span></span>' + svg("chevron", "hl-sel-chev");
    var lab = input.id && doc.querySelector('label[for="' + input.id + '"]');
    if (lab) { b.setAttribute("aria-label", lab.textContent.trim()); lab.addEventListener("click", function (e) { e.preventDefault(); b.focus(); b.click(); }); }
    input.parentNode.insertBefore(b, input.nextSibling);
    input.classList.add("hl-native");
    input.tabIndex = -1;
    input._hlBtn = b;
    input._hlType = type;
    paintDate(input);
    input.addEventListener("input", function () { paintDate(input); });
    input.addEventListener("change", function () { paintDate(input); });
    new MutationObserver(function () { paintDate(input); }).observe(input, { attributes: true, attributeFilter: ["disabled", "hidden", "aria-invalid", "readonly", "min", "max"] });
    b.addEventListener("click", function () { if (openPop && openPop.btn === b) closePop(); else openDate(input); });
    b.addEventListener("keydown", function (e) { if (e.key === "ArrowDown") { e.preventDefault(); openDate(input); } });
  }
  function openDate(input) {
    var type = input._hlType;
    var v = nativeValue.get.call(input);
    var min = parseDate(input.min), max = parseDate(input.max);
    var sel = parseDate(v);
    var view = sel ? new Date(sel) : (min && min > new Date() ? new Date(min) : new Date());
    view.setDate(1);
    var time = type === "time" ? (v || "") : (type === "datetime-local" && v.length >= 16 ? v.slice(11, 16) : "");
    var step = Math.max(5, Math.round((parseInt(input.step, 10) || 900) / 60));
    if (step > 60) step = 15;
    var focusDay = sel || new Date();
    openPopover(input._hlBtn, function (pop) {
      pop.classList.add("hl-pop-date");
      function commit() {
        if (type === "date") setVal(input, sel ? iso(sel) : "");
        else if (type === "time") setVal(input, time);
        else if (sel) setVal(input, iso(sel) + "T" + (time || "10:00"));
      }
      function cal() {
        if (type === "time") return "";
        var first = new Date(view.getFullYear(), view.getMonth(), 1);
        var offset = (first.getDay() + 6) % 7;
        var days = new Date(view.getFullYear(), view.getMonth() + 1, 0).getDate();
        var today = iso(new Date());
        var h = '<div class="hl-cal"><div class="hl-cal-head"><button type="button" class="hl-cal-nav" data-nav="-1" aria-label="Mois précédent">' + svg("left") +
          '</button><strong aria-live="polite">' + MONTHS[view.getMonth()] + " " + view.getFullYear() + '</strong><button type="button" class="hl-cal-nav" data-nav="1" aria-label="Mois suivant">' + svg("right") +
          '</button></div><div class="hl-cal-grid" role="grid">' + DAYS.map(function (d) { return '<span class="hl-cal-dow">' + d + "</span>"; }).join("");
        for (var i = 0; i < offset; i++) h += "<span></span>";
        for (var d = 1; d <= days; d++) {
          var dt = new Date(view.getFullYear(), view.getMonth(), d);
          var k = iso(dt);
          var off = (min && dt < min) || (max && dt > max);
          var we = dt.getDay() === 0 || dt.getDay() === 6;
          h += '<button type="button" class="hl-cal-day' + (k === today ? " is-today" : "") + (sel && k === iso(sel) ? " is-selected" : "") + (we ? " is-weekend" : "") +
            '" data-day="' + k + '"' + (off ? " disabled" : "") + ' tabindex="' + (k === iso(focusDay) ? 0 : -1) + '" aria-label="' + dt.toLocaleDateString("fr-FR", { weekday: "long", day: "numeric", month: "long", year: "numeric" }) + '">' + d + "</button>";
        }
        return h + "</div></div>";
      }
      function clock() {
        if (type === "date") return "";
        var hh = time ? +time.slice(0, 2) : -1, mm = time ? +time.slice(3, 5) : -1;
        var h = '<div class="hl-clock"><div class="hl-clock-col" aria-label="Heures"><span class="hl-clock-lbl">Heure</span>';
        for (var i = 0; i < 24; i++) h += '<button type="button" class="hl-clock-cell' + (i === hh ? " is-selected" : "") + (i < 7 || i > 20 ? " is-quiet" : "") + '" data-h="' + i + '">' + pad(i) + "</button>";
        h += '</div><div class="hl-clock-col" aria-label="Minutes"><span class="hl-clock-lbl">Min</span>';
        for (var m = 0; m < 60; m += step) h += '<button type="button" class="hl-clock-cell' + (m === mm ? " is-selected" : "") + '" data-m="' + m + '">' + pad(m) + "</button>";
        return h + "</div></div>";
      }
      function quick() {
        if (type === "time") return '<div class="hl-pop-quick">' + ["09:00", "10:00", "11:00", "14:00", "15:00", "16:30"].map(function (t) { return '<button type="button" data-t="' + t + '">' + t.replace(":", " h ") + "</button>"; }).join("") + "</div>";
        var td = new Date(), tm = new Date(); tm.setDate(tm.getDate() + 1);
        var nm = new Date(); nm.setDate(nm.getDate() + ((8 - nm.getDay()) % 7 || 7));
        var q = [["Aujourd’hui", td], ["Demain", tm], ["Lundi prochain", nm]];
        return '<div class="hl-pop-quick">' + q.map(function (x) { var off = (min && x[1] < new Date(min.getTime())) && iso(x[1]) !== iso(min); return '<button type="button" data-q="' + iso(x[1]) + '"' + (off ? " disabled" : "") + ">" + x[0] + "</button>"; }).join("") + "</div>";
      }
      function render() {
        pop.innerHTML = quick() + '<div class="hl-date-body">' + cal() + clock() + '</div><div class="hl-pop-actions">' +
          (input.required ? "" : '<button type="button" class="hl-link" data-clear>Effacer</button>') + '<span></span><button type="button" class="btn btn-primary btn-sm" data-ok>Valider</button></div>';
        var cs = pop.querySelectorAll(".hl-clock-col .is-selected");
        cs.forEach(function (c) { c.parentNode.scrollTop = c.offsetTop - c.parentNode.clientHeight / 2 + 14; });
        if (!cs.length) pop.querySelectorAll(".hl-clock-col").forEach(function (c) { var n = c.querySelector('[data-h="9"]'); if (n) c.scrollTop = n.offsetTop - 30; });
      }
      pop.addEventListener("click", function (e) {
        var t = e.target.closest("button");
        if (!t || t.disabled) return;
        if (t.dataset.nav) { view.setMonth(view.getMonth() + +t.dataset.nav); render(); return; }
        if (t.dataset.day) { sel = parseDate(t.dataset.day); focusDay = sel; if (type === "date") { commit(); closePop(); input._hlBtn.focus(); } else render(); return; }
        if (t.dataset.q) { sel = parseDate(t.dataset.q); view = new Date(sel); view.setDate(1); focusDay = sel; if (type === "date") { commit(); closePop(); input._hlBtn.focus(); } else render(); return; }
        if (t.dataset.t) { time = t.dataset.t; commit(); closePop(); input._hlBtn.focus(); return; }
        if (t.dataset.h != null) { time = pad(+t.dataset.h) + ":" + (time ? time.slice(3, 5) : "00"); render(); if (type === "time") commit(); return; }
        if (t.dataset.m != null) { time = (time ? time.slice(0, 2) : "10") + ":" + pad(+t.dataset.m); render(); if (type === "time") commit(); return; }
        if (t.hasAttribute("data-clear")) { sel = null; time = ""; setVal(input, ""); closePop(); return; }
        if (t.hasAttribute("data-ok")) { commit(); closePop(); input._hlBtn.focus(); }
      });
      pop.addEventListener("keydown", function (e) {
        var day = e.target.closest && e.target.closest(".hl-cal-day");
        if (!day) return;
        var delta = { ArrowLeft: -1, ArrowRight: 1, ArrowUp: -7, ArrowDown: 7 }[e.key];
        if (delta) {
          e.preventDefault();
          var d = parseDate(day.dataset.day); d.setDate(d.getDate() + delta); focusDay = d;
          if (d.getMonth() !== view.getMonth()) { view = new Date(d); view.setDate(1); render(); }
          else { pop.querySelectorAll(".hl-cal-day").forEach(function (b) { b.tabIndex = b.dataset.day === iso(d) ? 0 : -1; }); }
          var n = pop.querySelector('[data-day="' + iso(d) + '"]'); if (n) n.focus();
        }
      });
      render();
      setTimeout(function () { var f = pop.querySelector('.hl-cal-day[tabindex="0"]') || pop.querySelector(".hl-clock-cell.is-selected") || pop.querySelector("button"); if (f) f.focus(); }, 30);
    }, type === "datetime-local" ? 420 : type === "time" ? 240 : 300);
  }

  // Valeur fixée par le code (sel.value = …, input.value = …) : l'affichage suit.
  var selDesc = Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype, "value");
  Object.defineProperty(HTMLSelectElement.prototype, "value", {
    configurable: true, get: function () { return selDesc.get.call(this); },
    set: function (v) { selDesc.set.call(this, v); if (this._hlBtn) paintBtn(this); },
  });
  var idxDesc = Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype, "selectedIndex");
  Object.defineProperty(HTMLSelectElement.prototype, "selectedIndex", {
    configurable: true, get: function () { return idxDesc.get.call(this); },
    set: function (v) { idxDesc.set.call(this, v); if (this._hlBtn) paintBtn(this); },
  });
  Object.defineProperty(HTMLInputElement.prototype, "value", {
    configurable: true, get: function () { return nativeValue.get.call(this); },
    set: function (v) { nativeValue.set.call(this, v); if (this._hlBtn) paintDate(this); },
  });

  /* ================================================= aperçu superposé */
  /** Aperçu de fichier au-dessus de tout, sans fermer le dossier ou l'assistant ouvert dessous. */
  function openPreview(o) {
    var prevFocus = doc.activeElement;
    var ov = doc.createElement("div");
    ov.className = "hl-preview";
    ov.setAttribute("role", "dialog");
    ov.setAttribute("aria-modal", "true");
    ov.innerHTML = '<div class="hl-preview-card">' +
      '<div class="hl-preview-head">' +
        '<button type="button" class="btn btn-secondary btn-sm hl-preview-back" aria-label="Retour au dossier">' + svg("left") + ' <span>Retour au dossier</span></button>' +
        svg("file") + '<strong style="margin-left:.4rem"></strong>' +
        (o.downloadName && o.url ? '<a class="btn btn-soft btn-sm" download="' + esc(o.downloadName) + '" href="' + o.url + '">Télécharger</a>' : "") +
        '<button type="button" class="hl-preview-close" aria-label="Fermer l’aperçu">' + svg("x") + '</button>' +
      '</div>' +
      '<div class="hl-preview-body"></div>' +
      '<div class="hl-preview-foot" style="display:flex;justify-content:space-between;align-items:center;padding:.75rem 1rem;border-top:1px solid var(--line,rgba(26,18,20,.08));background:var(--surface-2,#faf7f3)">' +
        '<span style="font-size: var(--text-sm);color:var(--ink-soft)">Aperçu sécurisé du CV / document</span>' +
        '<button type="button" class="btn btn-primary btn-sm hl-preview-back-foot">← Fermer et retourner au dossier</button>' +
      '</div></div>';
    ov.querySelector("strong").textContent = o.title || "Aperçu";
    ov.querySelector(".hl-preview-body").innerHTML = o.bodyHtml || "";
    doc.body.appendChild(ov);
    requestAnimationFrame(function () { ov.classList.add("is-open"); });
    return new Promise(function (resolve) {
      function close(e) {
        if (e && e.stopPropagation) e.stopPropagation();
        if (trapRelease) trapRelease();
        ov.classList.remove("is-open");
        setTimeout(function () { ov.remove(); }, reduce() ? 0 : 220);
        if (prevFocus && prevFocus.focus) try { prevFocus.focus({ preventScroll: true }); } catch (_) { /* élément retiré */ }
        if (typeof o.onClose === "function") {
          try { o.onClose(); } catch (_) {}
        }
        resolve(true);
      }
      // Contrat clavier partagé (Tab enfermé, Escape, retour du focus) ; la sortie garde son scroll et son callback.
      var trapRelease = window.HireLink && HireLink.trapDialog
        ? HireLink.trapDialog(ov, close)
        : (function () {
            function onKey(e) {
              if (e.key === "Escape") { e.preventDefault(); e.stopPropagation(); e.stopImmediatePropagation(); close(e); }
            }
            doc.addEventListener("keydown", onKey, true);
            return function () { doc.removeEventListener("keydown", onKey, true); };
          })();
      ov.addEventListener("click", function (e) {
        if (e.target === ov) {
          e.stopPropagation();
          close(e);
        }
      });
      ov.querySelector(".hl-preview-close").addEventListener("click", function (e) {
        e.stopPropagation();
        close(e);
      });
      var backBtn = ov.querySelector(".hl-preview-back");
      if (backBtn) backBtn.addEventListener("click", function (e) { e.stopPropagation(); close(e); });
      var backFoot = ov.querySelector(".hl-preview-back-foot");
      if (backFoot) backFoot.addEventListener("click", function (e) { e.stopPropagation(); close(e); });
      setTimeout(function () { (backBtn || ov.querySelector(".hl-preview-close")).focus(); }, 30);
    });
  }

  /* ======================================== recadrage d'image à l'envoi */
  /**
   * Recadre une image avant l'envoi : aperçu dans le cadre final (rond ou carré), déplacement à la souris / au doigt,
   * zoom. Résout un Blob PNG carré (512 px) ou null si annulé. opts.shape = "circle" | "square" ; opts.contain = logo.
   */
  function cropImage(file, opts) {
    opts = opts || {};
    return new Promise(function (resolve) {
      var url = URL.createObjectURL(file);
      var img = new Image();
      img.onload = function () {
        var S = 260, zoom = 1, ox = 0, oy = 0;
        var contain = !!opts.contain;
        var base = contain ? Math.min(S / img.width, S / img.height) * 0.86 : Math.max(S / img.width, S / img.height);
        var ov = doc.createElement("div");
        ov.className = "hl-preview hl-crop";
        ov.innerHTML = '<div class="hl-preview-card hl-crop-card"><div class="hl-preview-head">' + svg("eye") + "<strong>" + esc(opts.title || "Ajuster l’image") +
          '</strong></div><div class="hl-crop-stage ' + (opts.shape === "circle" ? "is-circle" : "is-square") + '"><canvas width="' + S * 2 + '" height="' + S * 2 + '"></canvas></div>' +
          '<label class="hl-crop-zoom">' + svg("search") + '<input type="range" min="0.5" max="3" step="0.01" value="1" aria-label="Zoom" data-native /></label>' +
          '<p class="hl-crop-hint">Faites glisser l’image pour la centrer. L’image est ' + (contain ? "entièrement visible, avec une marge propre." : "centrée automatiquement.") + "</p>" +
          '<div class="hl-pop-actions"><button type="button" class="btn btn-ghost btn-sm" data-cancel>Annuler</button><span></span><button type="button" class="btn btn-soft btn-sm" data-auto>Centrer automatiquement</button><button type="button" class="btn btn-primary btn-sm" data-ok>Utiliser cette image</button></div></div>';
        doc.body.appendChild(ov);
        requestAnimationFrame(function () { ov.classList.add("is-open"); });
        var cv = ov.querySelector("canvas"), ctx = cv.getContext("2d");
        function draw(target, size) {
          var c = target.getContext("2d"), k = size / S;
          c.clearRect(0, 0, size, size);
          if (contain) { c.fillStyle = "#ffffff"; c.fillRect(0, 0, size, size); }
          var w = img.width * base * zoom * k, h = img.height * base * zoom * k;
          c.imageSmoothingQuality = "high";
          c.drawImage(img, (size - w) / 2 + ox * k, (size - h) / 2 + oy * k, w, h);
        }
        function paint() { draw(cv, S * 2); }
        paint();
        var drag = null;
        cv.addEventListener("pointerdown", function (e) { drag = { x: e.clientX - ox, y: e.clientY - oy }; cv.setPointerCapture(e.pointerId); });
        cv.addEventListener("pointermove", function (e) { if (!drag) return; ox = e.clientX - drag.x; oy = e.clientY - drag.y; paint(); });
        cv.addEventListener("pointerup", function () { drag = null; });
        ov.querySelector("input").addEventListener("input", function (e) { zoom = +e.target.value; paint(); });
        function done(blob) { ov.classList.remove("is-open"); setTimeout(function () { ov.remove(); URL.revokeObjectURL(url); }, 200); resolve(blob); }
        ov.querySelector("[data-cancel]").onclick = function () { done(null); };
        ov.querySelector("[data-auto]").onclick = function () { zoom = 1; ox = oy = 0; ov.querySelector("input").value = 1; paint(); };
        ov.querySelector("[data-ok]").onclick = function () {
          var out = doc.createElement("canvas"); out.width = out.height = 512; draw(out, 512);
          out.toBlob(function (b) { done(b); }, "image/png");
        };
      };
      img.onerror = function () { URL.revokeObjectURL(url); resolve(file); };
      img.src = url;
    });
  }

  /* ============================================================ démarrage */
  // Émojis drapeaux (illisibles sous Windows) remplacés par l'image du drapeau, partout dans la page.
  var FLAG_RE = /([\u{1F1E6}-\u{1F1FF}]{2})/u;
  function swapFlags(root) {
    if (!root) return;
    if (root.nodeType === 3) { swapText(root); return; }
    if (root.nodeType !== 1 || /^(OPTION|SELECT|TEXTAREA|INPUT|SCRIPT|STYLE|TITLE)$/.test(root.tagName)) return;
    if (!FLAG_RE.test(root.textContent || "")) return;
    var w = doc.createTreeWalker(root, NodeFilter.SHOW_TEXT), list = [], n;
    while ((n = w.nextNode())) if (FLAG_RE.test(n.nodeValue)) list.push(n);
    list.forEach(swapText);
  }
  function swapText(t) {
    var p = t.parentNode;
    if (!p || /^(OPTION|SELECT|TEXTAREA|SCRIPT|STYLE|TITLE)$/.test(p.tagName) || !FLAG_RE.test(t.nodeValue)) return;
    var parts = t.nodeValue.split(FLAG_RE), frag = doc.createDocumentFragment();
    parts.forEach(function (x, i) {
      if (i % 2) { var im = flagEl(x); if (im) frag.appendChild(im); }
      else if (x) frag.appendChild(doc.createTextNode(i === parts.length - 1 || !x ? x : x));
    });
    p.replaceChild(frag, t);
  }
  // Options de liste : le drapeau-émoji est retiré du libellé (le visuel est ajouté par la liste enrichie).
  function cleanOptions(root) {
    (root.tagName === "SELECT" ? [root] : root.querySelectorAll("select")).forEach(function (sel) {
      Array.prototype.forEach.call(sel.options, function (o) { if (FLAG_RE.test(o.text)) o.text = o.text.replace(/[\u{1F1E6}-\u{1F1FF}]{2}\s*/gu, "").trim(); });
    });
  }

  function scan(root) {
    if (!root || !root.querySelectorAll) return;
    cleanOptions(root);
    swapFlags(root);
    if (root.tagName === "SELECT") enhanceSelect(root);
    root.querySelectorAll("select").forEach(enhanceSelect);
    root.querySelectorAll('input[type="date"], input[type="time"], input[type="datetime-local"]').forEach(enhanceDate);
  }
  function boot() {
    scan(doc.body);
    new MutationObserver(function (muts) {
      muts.forEach(function (m) {
        if (m.type === "characterData") { swapFlags(m.target); return; }
        m.addedNodes.forEach(function (n) { if (n.nodeType === 1) scan(n); else if (n.nodeType === 3) swapFlags(n); });
        if (m.target && m.target.tagName === "SELECT") { cleanOptions(m.target); if (m.target._hlBtn) paintBtn(m.target); }
      });
    }).observe(doc.body, { childList: true, subtree: true, characterData: true });
    // Apparition douce des cartes au défilement (désactivée si l'utilisateur réduit les animations).
    if (!reduce() && "IntersectionObserver" in global) {
      var io = new IntersectionObserver(function (es) { es.forEach(function (e) { if (e.isIntersecting) { e.target.classList.add("is-in"); io.unobserve(e.target); } }); }, { rootMargin: "0px 0px -6% 0px" });
      var tag = function () { doc.querySelectorAll(".rc-card:not(.hl-rise), .card:not(.hl-rise)").forEach(function (el, i) { el.classList.add("hl-rise"); el.style.setProperty("--hl-d", Math.min(i, 6) * 40 + "ms"); io.observe(el); }); };
      tag();
      setTimeout(tag, 1200);
    }
  }
  if (doc.readyState === "loading") doc.addEventListener("DOMContentLoaded", boot); else boot();

  global.HLUI = { enhance: scan, cropImage: cropImage, openPreview: openPreview, closePopover: closePop, fold: fold, esc: esc };
  global.HireLink = global.HireLink || {};
  // ui.js redéfinit window.HireLink : on rattache l'aperçu après coup.
  doc.addEventListener("DOMContentLoaded", function () { if (global.HireLink) global.HireLink.openPreview = openPreview; });
})(window);
