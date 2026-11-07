(function () {
  /*
   * La vitrine doit dire la même chose que l'application : si une session existe dans ce navigateur,
   * le visiteur n'est pas un inconnu. Sans ce script, une page publique ouverte depuis l'espace
   * connecté proposait « Connexion / Commencer » — l'impression d'être déconnecté à tort.
   */
  var header = document.querySelector(".site-header");
  if (!header) return;

  var session = null;
  try {
    session = JSON.parse(localStorage.getItem("hirelink.session") || "null");
  } catch (_) {}
  var token = null;
  try {
    token = localStorage.getItem("hirelink.token");
  } catch (_) {}
  if (!session || !token) return;

  var home =
    session.role === "ADMINISTRATEUR" ? "app/admin.html"
    : session.role === "RECRUTEUR" ? "app/recruiter.html"
    : "app/candidate-dashboard.html";

  var links = header.querySelectorAll('a[href*="auth.html"]');
  if (!links.length) return;
  var first = links[0];
  first.setAttribute("href", home);
  first.setAttribute("class", "btn btn-primary btn-sm");
  first.textContent = "Mon espace";
  first.title = session.fullName
    ? "Revenir dans votre espace" + " · " + session.fullName
    : "Revenir dans votre espace";
  for (var i = 1; i < links.length; i++) links[i].remove();
})();
