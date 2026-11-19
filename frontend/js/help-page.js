/** Page Help : filtre Tout / Candidat / Entreprise (préselection selon la session ou ?for=). */
(function () {
  var root = document.querySelector(".help-audience");
  if (!root) return;
  var role = null;
  try {
    var s = JSON.parse(localStorage.getItem("hirelink.session") || "null");
    if (s && s.role === "CANDIDAT") role = "candidat";
    if (s && s.role === "RECRUTEUR") role = "recruteur";
  } catch (_) {}
  var asked = new URLSearchParams(location.search).get("for");
  if (asked === "entreprise") asked = "recruteur";
  var initial = asked || role || "all";
  function apply(aud) {
    root.querySelectorAll("[data-help-aud]").forEach(function (b) {
      var on = b.getAttribute("data-help-aud") === aud;
      b.classList.toggle("is-on", on);
      b.setAttribute("aria-selected", on ? "true" : "false");
    });
    document.querySelectorAll("[data-help-for]").forEach(function (el) {
      el.hidden = el.getAttribute("data-help-for").split(/\s+/).indexOf(aud) < 0;
    });
  }
  root.addEventListener("click", function (e) {
    var b = e.target.closest("[data-help-aud]");
    if (b) apply(b.getAttribute("data-help-aud"));
  });
  if (!root.querySelector('[data-help-aud="' + initial + '"]')) initial = "all";
  apply(initial);
})();
