/**
 * Guide candidat : le CV d'abord, le profil ensuite.
 *  - Bouton de rappel dans la barre du haut, dont le libellé suit l'état réel du dossier
 *    (« Ajouter votre CV », « Compléter votre profil », « Vérifier votre profil ») ; il disparaît
 *    quand le dossier est complet.
 *  - Sur la page Profil & CV : symbole ∞ centré, boucle gauche = CV, boucle droite = Profil ;
 *    un clic sur une boucle ouvre la vue correspondante.
 * L'état vient de GET /me et GET /me/career (mêmes critères que la progression de la page Profil).
 * Les pages peuvent prévenir le guide : document.dispatchEvent(new CustomEvent("hirelink:guide", { detail })).
 *   detail.phase = "extracting" | "review" (CV lu, pas encore confirmé) ; detail.refresh = true (relire le serveur).
 */
(function (global) {
  "use strict";

  var CACHE = "hirelink.candGuide.v5";
  var INF_PATH = "M18,18 C18,8 30,8 36,18 C42,28 54,28 54,18 C54,8 42,8 36,18 C30,28 18,28 18,18";
  var state = { phase: "unknown", missing: [], transient: "" };
  var listening = false;

  function session() {
    return (global.HireLinkApp && HireLinkApp.getSession && HireLinkApp.getSession())
      || (global.HireLinkAPI && HireLinkAPI.getSession && HireLinkAPI.getSession())
      || null;
  }

  function isCandidate() {
    var s = session();
    return !!(s && s.role === "CANDIDAT");
  }

  function onProfilePage() {
    return /candidate-profile\.html$/.test(location.pathname);
  }

  function readCache() {
    try {
      var raw = sessionStorage.getItem(CACHE);
      if (!raw) return;
      var c = JSON.parse(raw);
      if (c && typeof c.phase === "string") {
        state.phase = c.phase;
        state.missing = Array.isArray(c.missing) ? c.missing : [];
      }
    } catch (_) {}
  }

  function writeCache() {
    try { sessionStorage.setItem(CACHE, JSON.stringify({ phase: state.phase, missing: state.missing })); } catch (_) {}
  }

  /** Mêmes six repères que la progression affichée sur la page Profil. */
  function evaluate(me, career) {
    me = me || {};
    career = career || {};
    var skills = String(me.skills || "").split(/[,;]/).filter(function (x) { return x.trim(); });
    var missing = [];
    if (!(String(me.firstName || "").trim() && String(me.lastName || "").trim())) missing.push("identité");
    if (!(Array.isArray(career.experiences) && career.experiences.length)) missing.push("expérience");
    if (!(Array.isArray(career.educations) && career.educations.length)) missing.push("formation");
    if (skills.length < 3) missing.push("compétences");
    if (!String(me.soughtRoles || "").trim()) missing.push("métiers recherchés");
    state.missing = missing;
    if (!me.cvFileName) state.phase = "cv";
    else state.phase = missing.length ? "profil" : "done";
    writeCache();
  }

  function phase() { return state.transient || state.phase; }

  function label() {
    switch (phase()) {
      case "extracting": return "Lecture du CV…";
      case "review": return "Confirmer votre CV";
      case "cv": return "Ajouter votre CV";
      case "profil": return state.missing.length > 1 ? "Compléter votre profil" : "Vérifier votre profil";
      case "done": return "Profil prêt";
      default: return "";
    }
  }

  function hint() {
    switch (phase()) {
      case "extracting": return "Nous lisons votre CV, cela prend quelques secondes.";
      case "review": return "Gardez les propositions justes, puis confirmez votre CV.";
      case "cv": return "Commencez par votre CV : il sert à remplir votre profil et à postuler.";
      case "profil": return "Il reste : " + state.missing.join(", ") + ".";
      case "done": return "CV et profil sont en place. Bonne recherche !";
      default: return "";
    }
  }

  function targetView() {
    var p = phase();
    return (p === "cv" || p === "extracting" || p === "review") ? "cv" : "profile";
  }

  function hrefFor(view) {
    return "candidate-profile.html" + (view === "cv" ? "#cv" : "#profil");
  }

  function goView(view) {
    if (onProfilePage() && typeof global.setCandidateProfileView === "function") {
      global.setCandidateProfileView(view);
      var main = document.getElementById("rc-content");
      if (main && main.scrollIntoView) main.scrollIntoView({ block: "start", behavior: "smooth" });
      return;
    }
    location.href = hrefFor(view);
  }

  /* ------------------------------------------------------------ barre du haut */
  function ensureNudge() {
    var bar = document.getElementById("rc-topbar");
    if (!bar) return null;
    var el = document.getElementById("cg-nav-nudge");
    if (el && bar.contains(el)) return el;
    el = document.createElement("a");
    el.id = "cg-nav-nudge";
    el.className = "cg-nav-nudge";
    el.hidden = true;
    el.innerHTML = '<span class="cg-nav-dot" aria-hidden="true"></span><strong></strong>';
    el.addEventListener("click", function (e) {
      if (!onProfilePage()) return;
      e.preventDefault();
      goView(targetView());
    });
    var actions = bar.querySelector(".rc-actions");
    if (actions) bar.insertBefore(el, actions);
    else bar.appendChild(el);
    return el;
  }

  function paintNudge() {
    var el = ensureNudge();
    if (!el) return;
    var p = phase();
    if (!isCandidate() || p === "unknown" || p === "done") { el.hidden = true; return; }
    var text = label();
    el.hidden = false;
    el.href = hrefFor(targetView());
    el.title = hint();
    el.setAttribute("aria-label", text + ". " + hint());
    el.classList.toggle("is-busy", p === "extracting");
    el.querySelector("strong").textContent = text;
  }

  /* ------------------------------------------------------- symbole ∞ (profil) */
  function nodeClass(kind) {
    var p = phase();
    if (kind === "cv") {
      if (p === "cv") return "is-active";
      if (p === "extracting" || p === "review") return "is-busy is-active";
      if (p === "profil" || p === "done") return "is-done";
      return "is-pending";
    }
    if (p === "profil") return "is-active";
    if (p === "done") return "is-done";
    return "is-pending";
  }

  function paintHub() {
    var host = document.getElementById("cg-hub");
    if (!host) return;
    if (!isCandidate() || phase() === "unknown") { host.hidden = true; return; }
    host.hidden = false;
    var uid = "cgh" + Math.random().toString(36).slice(2, 8);
    host.className = "cg-hub";
    host.setAttribute("data-phase", phase());
    host.innerHTML =
      '<div class="cg-hub-card">' +
        '<p class="cg-hub-kicker">Votre dossier</p>' +
        '<p class="cg-hub-title"></p>' +
        '<div class="cg-hub-infinity" role="group" aria-label="Étape 1 : CV, étape 2 : profil">' +
          '<svg class="cg-inf-svg cg-inf-svg--lg" viewBox="0 0 72 36" aria-hidden="true">' +
            '<defs><linearGradient id="' + uid + '" x1="0" y1="0" x2="1" y2="0">' +
              '<stop offset="0%" stop-color="#7a2032"/><stop offset="55%" stop-color="#a67c52"/><stop offset="100%" stop-color="#9a7b4f"/>' +
            "</linearGradient></defs>" +
            '<path class="cg-inf-track" d="' + INF_PATH + '"/>' +
            '<path class="cg-inf-active" d="' + INF_PATH + '" stroke="url(#' + uid + ')"/>' +
            '<g class="cg-inf-node ' + nodeClass("cv") + '" data-kind="cv"><circle cx="18" cy="18" r="7.2"/>' +
              '<text class="cg-inf-label" x="18" y="19.7" text-anchor="middle">CV</text></g>' +
            '<g class="cg-inf-node ' + nodeClass("profil") + '" data-kind="profil"><circle cx="54" cy="18" r="7.2"/>' +
              '<text class="cg-inf-label cg-inf-label--sm" x="54" y="19.4" text-anchor="middle">Profil</text></g>' +
            '<circle class="cg-inf-dot" r="2.2"/>' +
          "</svg>" +
          '<button type="button" class="cg-hub-hit cg-hub-hit--cv" data-view="cv" aria-label="Ouvrir Mon CV"></button>' +
          '<button type="button" class="cg-hub-hit cg-hub-hit--profil" data-view="profile" aria-label="Ouvrir Mon profil"></button>' +
        "</div>" +
        '<div class="cg-hub-legend"><span><b>1</b> CV</span><span class="cg-hub-arrow" aria-hidden="true">→</span><span><b>2</b> Profil</span></div>' +
        '<p class="cg-hub-hint"></p>' +
      "</div>";
    host.querySelector(".cg-hub-title").textContent = label();
    host.querySelector(".cg-hub-hint").textContent = hint();
    host.querySelectorAll("[data-view]").forEach(function (btn) {
      btn.addEventListener("click", function () { goView(btn.getAttribute("data-view")); });
    });
  }

  function paint() {
    paintNudge();
    paintHub();
  }

  function refresh() {
    if (!global.HireLinkAPI || !HireLinkAPI.api) return Promise.resolve();
    return Promise.all([
      HireLinkAPI.api("/me"),
      HireLinkAPI.api("/me/career").catch(function () { return {}; }),
    ]).then(function (pack) {
      var before = state.phase;
      evaluate(pack[0], pack[1]);
      paint();
      if (before === "profil" && state.phase === "done" && global.Mascotte && Mascotte.celebrate) {
        try { Mascotte.celebrate({ title: "Profil prêt !", description: "CV et profil sont en place. Bonne recherche !" }); } catch (_) {}
      }
    }).catch(function () { paint(); });
  }

  function mount() {
    if (!isCandidate()) return;
    readCache();
    paint();
    refresh();
    if (listening) return;
    listening = true;
    document.addEventListener("hirelink:guide", function (ev) {
      var d = (ev && ev.detail) || {};
      if (d.phase === "extracting" || d.phase === "review") state.transient = d.phase;
      else if (d.phase === "clear") state.transient = "";
      if (d.refresh) { state.transient = d.phase === "review" ? "review" : ""; refresh(); }
      else paint();
    });
    // La barre du haut peut être reconstruite (changement de thème, de formule) : le rappel revient à sa place.
    var top = document.getElementById("rc-topbar");
    if (top && global.MutationObserver) {
      new MutationObserver(function () {
        if (!document.getElementById("cg-nav-nudge")) paintNudge();
      }).observe(top, { childList: true });
    }
  }

  global.CandidateGuide = { mount: mount, refresh: refresh, paint: paint };

  function boot() { if (global.HireLinkApp && document.getElementById("rc-topbar")) mount(); }
  // Le shell monte la barre du haut de façon synchrone dans le script de page ; on passe juste après.
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", function () { setTimeout(boot, 0); });
  else setTimeout(boot, 0);
})(window);
