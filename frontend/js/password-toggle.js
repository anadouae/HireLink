/**
 * Œil afficher / masquer mot de passe
 */
(function (global) {
  function wrap(input) {
    if (!input || input.dataset.pwToggle === "1") return;
    input.dataset.pwToggle = "1";
    const wrapEl = document.createElement("div");
    wrapEl.className = "pw-field";
    input.parentNode.insertBefore(wrapEl, input);
    wrapEl.appendChild(input);
    const btn = document.createElement("button");
    btn.type = "button";
    btn.className = "pw-toggle";
    btn.setAttribute("aria-label", "Afficher le mot de passe");
    btn.setAttribute("title", "Afficher / masquer");
    btn.innerHTML =
      '<svg class="pw-eye" viewBox="0 0 24 24" width="20" height="20" aria-hidden="true">' +
      '<path fill="currentColor" d="M12 5c-7 0-10 7-10 7s3 7 10 7 10-7 10-7-3-7-10-7zm0 12a5 5 0 1 1 0-10 5 5 0 0 1 0 10zm0-2.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z"/>' +
      "</svg>" +
      '<svg class="pw-eye-off" viewBox="0 0 24 24" width="20" height="20" aria-hidden="true" hidden>' +
      '<path fill="currentColor" d="M2.1 3.5 3.5 2.1l18.4 18.4-1.4 1.4-3.1-3.1A11.6 11.6 0 0 1 12 19c-7 0-10-7-10-7a18.7 18.7 0 0 1 5.2-5.6L2.1 3.5zM12 7a5 5 0 0 1 5 5c0 .7-.1 1.3-.4 1.9l-1.6-1.6A2.5 2.5 0 0 0 12 9.5V7zm-7.3 4.2A16 16 0 0 0 12 17c1.5 0 2.8-.3 4-.8l-2.1-2.1A5 5 0 0 1 8.9 9.1L4.7 11.2z"/>' +
      "</svg>";
    wrapEl.appendChild(btn);
    btn.addEventListener("click", () => {
      const show = input.type === "password";
      input.type = show ? "text" : "password";
      btn.querySelector(".pw-eye").hidden = show;
      btn.querySelector(".pw-eye-off").hidden = !show;
      btn.setAttribute("aria-label", show ? "Masquer le mot de passe" : "Afficher le mot de passe");
      input.focus();
    });
  }

  function bindAll(root) {
    (root || document).querySelectorAll('input[type="password"]').forEach(wrap);
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", () => bindAll());
  } else {
    bindAll();
  }

  global.HireLinkPassword = { wrap, bindAll };
})(window);
