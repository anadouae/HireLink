/**
 * Garde d'accès des espaces (rôle lu dans data-role du <script>) — exécuté dans <head>, avant le premier rendu :
 * pas de flash de contenu protégé, redirection immédiate selon le rôle.
 */
(function () {
  try {
    var want = (document.currentScript && document.currentScript.getAttribute("data-role")) || "RECRUTEUR";
    var token = localStorage.getItem("hirelink.token");
    var session = JSON.parse(localStorage.getItem("hirelink.session") || "null");
    var base = location.pathname.indexOf("/app/") >= 0 ? "../" : "";
    if (!token || !session) {
      var here = location.pathname.split("/").pop() + location.search + location.hash;
      location.replace(base + "auth.html?mode=login&next=" + encodeURIComponent(here));
      return;
    }
    if (want !== "ANY" && session.role !== want) {
      location.replace(base + (session.role === "ADMINISTRATEUR" ? "app/admin.html" : session.role === "RECRUTEUR" ? "app/recruiter.html" : "app/candidate-dashboard.html"));
      return;
    }
    if (session.mustSetPassword && localStorage.getItem("hirelink.pw.skip") !== "1") {
      location.replace(base + "set-password.html");
      return;
    }
    document.documentElement.classList.add("rc-pending");
    // Filet de sécurité : ne jamais laisser la page masquée si un script échoue.
    setTimeout(function () { document.documentElement.classList.remove("rc-pending"); }, 4000);
  } catch (e) { /* localStorage indisponible : api.js prendra le relais */ }
})();
