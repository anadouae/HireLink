/**
 * Thème clair / sombre des pages publiques — même clé que l'application (hirelink.theme).
 * Chargé dans <head> (synchrone) pour appliquer le thème avant le premier rendu.
 */
(function (global) {
  var KEY = "hirelink.theme";
  function read() { try { return localStorage.getItem(KEY) === "dark" ? "dark" : "light"; } catch (_) { return "light"; } }
  function set(t) {
    document.documentElement.setAttribute("data-theme", t);
    try { localStorage.setItem(KEY, t); } catch (_) {}
  }
  set(read());

  var MOON = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M21 12.8A9 9 0 1 1 11.2 3 7 7 0 0 0 21 12.8z"/></svg>';
  var SUN = '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/></svg>';

  function paint(btn) {
    var dark = read() === "dark";
    btn.innerHTML = dark ? SUN : MOON;
    btn.setAttribute("aria-pressed", dark ? "true" : "false");
    btn.setAttribute("aria-label", dark ? "Passer en mode clair" : "Passer en mode sombre");
    btn.title = btn.getAttribute("aria-label");
  }

  function mount() {
    if (document.querySelector(".pub-theme")) return;
    var btn = document.createElement("button");
    btn.type = "button";
    btn.className = "pub-theme";
    btn.id = "pub-theme";
    paint(btn);
    btn.addEventListener("click", function () {
      set(read() === "dark" ? "light" : "dark");
      paint(btn);
      document.dispatchEvent(new CustomEvent("hl:theme", { detail: read() }));
    });
    var header = document.querySelector(".site-header");
    var pay = document.querySelector(".pay-top");
    if (header) {
      var row = header.querySelector(":scope > .row");
      var cta = header.querySelector(":scope > .btn");
      if (row) row.insertBefore(btn, row.firstChild);
      else if (cta) header.insertBefore(btn, cta);
      else header.appendChild(btn);
    } else if (pay) {
      pay.appendChild(btn);
    } else {
      btn.classList.add("is-floating");
      document.body.appendChild(btn);
    }
  }
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", mount); else mount();
  global.HireLinkPublicTheme = { current: read, apply: set };
})(window);
