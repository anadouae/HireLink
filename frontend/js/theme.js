/**
 * Thème clair / sombre — localStorage hirelink.theme
 */
(function (global) {
  const KEY = "hirelink.theme";

  function current() {
    return localStorage.getItem(KEY) || "light";
  }

  function syncButtons() {
    const dark = current() === "dark";
    document.querySelectorAll("[data-theme-toggle], #btn-theme").forEach((el) => {
      if (el.type === "checkbox") {
        el.checked = dark;
        return;
      }
      el.setAttribute("aria-pressed", dark ? "true" : "false");
      el.setAttribute("title", dark ? "Passer en mode clair" : "Passer en mode sombre");
      el.setAttribute("aria-label", dark ? "Mode clair" : "Mode sombre");
      // Moon = go dark ; Sun = go light
      if (!el.dataset.keepLabel) {
        el.innerHTML = dark
          ? '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/></svg>'
          : '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="M21 12.8A9 9 0 1 1 11.2 3 7 7 0 0 0 21 12.8z"/></svg>';
      }
    });
    document.querySelectorAll("[data-theme-label]").forEach((el) => {
      el.textContent = dark ? "Sombre" : "Clair";
    });
  }

  function apply(theme) {
    const t = theme === "dark" ? "dark" : "light";
    document.documentElement.setAttribute("data-theme", t);
    localStorage.setItem(KEY, t);
    syncButtons();
  }

  function toggle() {
    apply(current() === "dark" ? "light" : "dark");
  }

  apply(current());
  global.HireLinkTheme = { current, apply, toggle, syncButtons };
})(window);
