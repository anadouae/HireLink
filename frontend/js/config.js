/**
 * Adresse de l'API. Par défaut : le même nom d'hôte que la page (localhost ou 127.0.0.1), port 8081.
 * (Le cookie de session est SameSite=Strict : page et API doivent partager le même nom d'hôte.)
 * Pour un autre serveur : définir window.HIRELINK_API_BASE avant ce script.
 */
(function () {
  if (window.HIRELINK_API_BASE) return;
  var h = location.hostname;
  if (location.protocol.indexOf("http") !== 0 || !h) h = "localhost";
  var base = location.protocol.replace("https:", "http:") + "//" + h + ":8081/api/v1";
  if (location.protocol.indexOf("http") !== 0) base = "http://localhost:8081/api/v1";
  // Opt-in, un seul onglet : ?api=http://localhost:8091 pointe cet onglet vers une autre instance.
  // ?api=reset revient au port 8081. Sans paramètre, le comportement par défaut ne change pas.
  try {
    var q = new URLSearchParams(location.search).get("api");
    if (q === "reset") sessionStorage.removeItem("hirelink.api");
    else if (q && /^https?:\/\/[a-z0-9.-]+(:\d+)?$/i.test(q)) {
      base = q.replace(/\/$/, "") + "/api/v1";
      sessionStorage.setItem("hirelink.api", base);
    } else {
      var stored = sessionStorage.getItem("hirelink.api");
      if (stored) base = stored;
    }
  } catch (e) {}
  window.HIRELINK_API_BASE = base;
})();
