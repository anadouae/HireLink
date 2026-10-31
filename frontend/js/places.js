/**
 * Lieux et drapeaux : le catalogue (villes marocaines d'abord, grandes villes et pays) vient du serveur, une seule fois ;
 * le drapeau est un émoji (Unicode), sans image ni service externe.
 */
(function (global) {
  const KEY = "hirelink.places.v1";
  let byName = null;
  let loading = null;

  function base() {
    return ((global.HireLinkAPI && HireLinkAPI.API_BASE) || global.HIRELINK_API_BASE || "http://127.0.0.1:8081/api/v1").replace(/\/$/, "");
  }

  function fold(value) {
    return global.HireLinkSearch ? HireLinkSearch.fold(value) : String(value || "").toLowerCase();
  }

  function index(rows) {
    byName = new Map();
    rows.forEach(function (p) {
      byName.set(fold(p.city || p.country), p.flag);
      if (!byName.has(fold(p.country))) byName.set(fold(p.country), p.flag);
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

  global.HireLinkPlaces = { ready: ready, flagOf: flagOf };
})(window);
