/**
 * Adresse de l'API. Par défaut : le même nom d'hôte que la page (localhost ou 127.0.0.1), port 8081.
 * (Le cookie de session est SameSite=Strict : page et API doivent partager le même nom d'hôte.)
 * Pour un autre serveur : définir window.HIRELINK_API_BASE avant ce script.
 */
(function () {
  if (window.HIRELINK_API_BASE) return;
  var h = location.hostname;
  if (location.protocol.indexOf("http") !== 0 || !h) h = "localhost";
  window.HIRELINK_API_BASE = location.protocol.replace("https:", "http:") + "//" + h + ":8081/api/v1";
  if (location.protocol.indexOf("http") !== 0) window.HIRELINK_API_BASE = "http://localhost:8081/api/v1";
})();
