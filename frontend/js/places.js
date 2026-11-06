/**
 * Lieux et drapeaux : le catalogue (villes marocaines d'abord, grandes villes et pays) vient du serveur, une seule fois ;
 * le drapeau est un émoji (Unicode), sans image ni service externe.
 */
(function (global) {
  const KEY = "hirelink.places.v1";
  let byName = null;
  let countryByCity = null;
  let countryNames = null;
  let loading = null;

  function base() {
    return ((global.HireLinkAPI && HireLinkAPI.API_BASE) || global.HIRELINK_API_BASE || "http://127.0.0.1:8081/api/v1").replace(/\/$/, "");
  }

  function fold(value) {
    return global.HireLinkSearch ? HireLinkSearch.fold(value) : String(value || "").toLowerCase();
  }

  function index(rows) {
    byName = new Map();
    countryByCity = new Map();
    countryNames = new Set();
    rows.forEach(function (p) {
      byName.set(fold(p.city || p.country), p.flag);
      if (!byName.has(fold(p.country))) byName.set(fold(p.country), p.flag);
      if (p.city) countryByCity.set(fold(p.city), p.country);
      countryNames.add(fold(p.country));
    });
  }

  /** Charge le catalogue (mémorisé pour la session) ; toujours résolu, même sans réseau. */
  function ready() {
    if (byName) return Promise.resolve();
    if (loading) return loading;
    try {
      const cached = JSON.parse(sessionStorage.getItem(KEY) || "null");
      if (cached) { index(cached); return Promise.resolve(); }
    } catch (_) { /* cache illisible : on recharge */ }
    loading = fetch(base() + "/catalog/places")
      .then(function (r) { return r.ok ? r.json() : []; })
      .then(function (rows) {
        index(rows);
        try { sessionStorage.setItem(KEY, JSON.stringify(rows)); } catch (_) { /* stockage plein ou refusé */ }
      })
      .catch(function () { index([]); });
    return loading;
  }

  /** Drapeau d'un lieu (« Casablanca », « Tétouan · Hybride », « Paris, France »), ou chaîne vide. */
  function flagOf(text) {
    if (!byName) return "";
    const parts = String(text || "").split(/[·,|/]/);
    for (let i = 0; i < parts.length; i++) {
      const flag = byName.get(fold(parts[i]));
      if (flag) return flag;
    }
    return "";
  }

  /** Le pays déduit d'une ville (« Tétouan » -> « Maroc »), ou chaîne vide si le catalogue ne la connaît pas. */
  function countryOf(city) {
    if (!countryByCity) return "";
    return countryByCity.get(fold(String(city || "").split(/[·,|/]/)[0].trim())) || "";
  }

  /** Vrai pour « Maroc », « France »… : un pays ne doit jamais tenir la place d'une ville. */
  function isCountry(text) {
    if (!countryNames) return false;
    return countryNames.has(fold(String(text || "").trim()));
  }

  /** Segments d'un lieu qui décrivent l'organisation du travail et non une ville. */
  const MODE_SEGMENT = /t[ée]l[ée]travail|remote|hybride|sur site|pr[ée]sentiel|[àa] distance|onsite|full[- ]?remote/i;

  /**
   * La ville d'un lieu saisi librement. Un segment qui nomme un mode de travail (« Remote Maroc »)
   * ou un pays n'est pas une ville : on se rabat sur `fallback`, la ville de l'entreprise.
   */
  function cityOf(text, fallback) {
    const parts = String(text || "").split(/[,·|/]/).map(function (s) { return s.trim(); }).filter(Boolean);
    for (let i = 0; i < parts.length; i++) {
      if (MODE_SEGMENT.test(parts[i]) || isCountry(parts[i])) continue;
      return parts[i];
    }
    return String(fallback || "").trim();
  }

  /** « Tétouan » ou « Montréal · Canada » : le pays se déduit de la ville et ne alourdit pas une adresse marocaine. */
  function placeLabel(text, fallback) {
    const city = cityOf(text, fallback);
    if (!city) return "";
    const country = countryOf(city);
    if (!country || fold(country) === fold(city) || /maroc/i.test(country)) return city;
    return city + " · " + country;
  }

  global.HireLinkPlaces = {
    ready: ready, flagOf: flagOf, countryOf: countryOf, isCountry: isCountry, cityOf: cityOf, placeLabel: placeLabel
  };
})(window);
