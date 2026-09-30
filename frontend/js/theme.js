/**
 * Thème clair / sombre — localStorage hirelink.theme
 */
(function (global) {
  const KEY = "hirelink.theme";

  function current() {
    return localStorage.getItem(KEY) || "light";
  }

  function apply(theme) {
    const t = theme === "dark" ? "dark" : "light";
    document.documentElement.setAttribute("data-theme", t);
    localStorage.setItem(KEY, t);
    document.querySelectorAll("[data-theme-label]").forEach((el) => {
      el.textContent = t === "dark" ? "Sombre" : "Clair";
    });
    document.querySelectorAll("[data-theme-toggle]").forEach((el) => {
      if (el.type === "checkbox") el.checked = t === "dark";
    });
  }

  function toggle() {
    apply(current() === "dark" ? "light" : "dark");
  }

  apply(current());
  global.HireLinkTheme = { current, apply, toggle };
})(window);
