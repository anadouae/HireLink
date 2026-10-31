/**
 * HireLink UI — modales et panneaux ; toasts et confirmations : voir mascotte.js
 */
(function (global) {
  function ensureHosts() {
    if (!document.getElementById("hl-overlay")) {
      const overlay = document.createElement("div");
      overlay.id = "hl-overlay";
      overlay.className = "overlay";
      overlay.innerHTML =
        '<div class="modal" role="dialog" aria-modal="true" aria-labelledby="hl-modal-title" aria-describedby="hl-modal-desc"></div>';
      overlay.addEventListener("click", (e) => {
        if (e.target === overlay) closeModal(false);
      });
      document.body.appendChild(overlay);
    }
  }

  let modalResolver = null;
  let previouslyFocused = null;

  function wireActions(actions, options) {
    const cancel = document.createElement("button");
    cancel.type = "button";
    cancel.className = "btn btn-ghost";
    cancel.textContent = options.cancelLabel || "Annuler";
    cancel.onclick = () => closeModal(false);
    const ok = document.createElement("button");
    ok.type = "button";
    ok.className = "btn " + (options.danger ? "btn-danger" : "btn-primary");
    ok.textContent = options.confirmLabel || "Confirmer";
    ok.disabled = !!options.confirmDisabled;
    ok.onclick = async () => {
      if (ok.disabled) return;
      if (typeof options.beforeConfirm === "function" && options.beforeConfirm() === false) return;
      if (typeof options.onConfirm === "function") {
        // Action asynchrone : la fenêtre reste ouverte (avec le message d'erreur) tant que le serveur refuse.
        const panel = ok.closest(".modal, .drawer");
        const old = panel && panel.querySelector(".modal-error");
        if (old) old.remove();
        ok.disabled = true;
        ok.classList.add("is-loading");
        try {
          const r = await options.onConfirm();
          ok.disabled = false;
          ok.classList.remove("is-loading");
          if (r === false) return;
          closeModal(r === undefined ? true : r);
        } catch (err) {
          ok.disabled = false;
          ok.classList.remove("is-loading");
          if (panel) {
            const p = document.createElement("p");
            p.className = "modal-error";
            p.setAttribute("role", "alert");
            p.textContent = (err && err.message) || "Action impossible";
            const actions = panel.querySelector(".modal-actions");
            actions.parentNode.insertBefore(p, actions);
          }
        }
        return;
      }
      closeModal(true);
    };
    if (!options.hideCancel) actions.appendChild(cancel);
    if (!options.hideConfirm) actions.appendChild(ok);
    return ok;
  }

  function readyApi(panel, ok, options) {
    setTimeout(() => {
      if (ok && !ok.disabled && !options.hideConfirm) ok.focus();
      if (typeof options.onReady === "function") {
        options.onReady({
          root: panel,
          setConfirmEnabled: (v) => {
            if (ok) ok.disabled = !v;
          },
          getConfirmButton: () => ok,
        });
      }
    }, 40);
  }

  function dialogPanel(overlay) {
    return overlay.querySelector(".modal, .drawer");
  }

  function openModal(options) {
    ensureHosts();
    previouslyFocused = document.activeElement;
    const overlay = document.getElementById("hl-overlay");
    overlay.classList.remove("overlay--drawer");
    const modal = dialogPanel(overlay);
    modal.className = "modal" + (options.large ? " modal-lg" : "");
    modal.innerHTML =
      '<div class="dialog-lead">' +
      '<div><h3 class="modal-title" id="hl-modal-title"></h3>' +
      '<p class="modal-desc" id="hl-modal-desc"></p></div></div>' +
      '<div class="modal-body"></div>' +
      '<div class="modal-actions"></div>';
    modal.querySelector(".modal-title").textContent = options.title || "";
    const desc = modal.querySelector(".modal-desc");
    desc.textContent = options.description || "";
    if (!options.description) desc.hidden = true;
    const modalBody = modal.querySelector(".modal-body");
    if (options.bodyHtml) modalBody.innerHTML = options.bodyHtml;
    else modalBody.remove();
    const ok = wireActions(modal.querySelector(".modal-actions"), options);
    overlay.classList.add("is-open");
    readyApi(modal, ok, options);
    return new Promise((resolve) => {
      modalResolver = resolve;
    });
  }

  /** Panneau latéral droit — garde le kanban visible (ATS type Greenhouse). */
  function openDrawer(options) {
    ensureHosts();
    previouslyFocused = document.activeElement;
    const overlay = document.getElementById("hl-overlay");
    overlay.classList.add("overlay--drawer");
    const panel = dialogPanel(overlay);
    panel.className =
      "drawer" +
      (options.wide ? " drawer--wide" : "") +
      (options.danger ? " drawer--danger" : "");
    panel.innerHTML =
      '<div class="drawer-head">' +
      '<div class="drawer-head-text">' +
      '<h3 class="modal-title" id="hl-modal-title"></h3>' +
      '<p class="modal-desc" id="hl-modal-desc"></p>' +
      "</div>" +
      '<button type="button" class="drawer-close" aria-label="Fermer">✕</button>' +
      "</div>" +
      '<div class="modal-body drawer-body"></div>' +
      '<div class="modal-actions drawer-actions"></div>';
    panel.querySelector(".modal-title").textContent = options.title || "";
    const desc = panel.querySelector(".modal-desc");
    desc.textContent = options.description || "";
    if (!options.description) desc.hidden = true;
    if (options.bodyHtml) {
      panel.querySelector(".modal-body").innerHTML = options.bodyHtml;
    }
    panel.querySelector(".drawer-close").onclick = () => closeModal(false);
    const ok = wireActions(panel.querySelector(".modal-actions"), options);
    overlay.classList.add("is-open");
    readyApi(panel, ok, options);
    return new Promise((resolve) => {
      modalResolver = resolve;
    });
  }

  function closeModal(result) {
    const overlay = document.getElementById("hl-overlay");
    if (overlay) {
      overlay.classList.remove("is-open");
      setTimeout(() => {
        if (overlay && !overlay.classList.contains("is-open")) {
          overlay.classList.remove("overlay--drawer");
        }
      }, 280);
    }
    if (modalResolver) {
      modalResolver(result);
      modalResolver = null;
    }
    if (previouslyFocused && previouslyFocused.focus) {
      try {
        previouslyFocused.focus();
      } catch (_) {}
    }
  }

  document.addEventListener("keydown", (e) => {
    const overlay = document.getElementById("hl-overlay");
    if (!overlay || !overlay.classList.contains("is-open")) return;
    if (e.key === "Escape") {
      e.preventDefault();
      closeModal(false);
      return;
    }
    if (e.key !== "Tab") return;
    const panel = dialogPanel(overlay);
    if (!panel) return;
    const focusables = panel.querySelectorAll(
      'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
    );
    if (!focusables.length) return;
    const first = focusables[0];
    const last = focusables[focusables.length - 1];
    if (e.shiftKey && document.activeElement === first) {
      e.preventDefault();
      last.focus();
    } else if (!e.shiftKey && document.activeElement === last) {
      e.preventDefault();
      first.focus();
    }
  });

  function bindScrollProgress() {
    const bar = document.querySelector(".scroll-progress");
    if (!bar) return;
    window.addEventListener("scroll", () => {
      const h = document.documentElement;
      const max = h.scrollHeight - h.clientHeight;
      bar.style.width = max > 0 ? (h.scrollTop / max) * 100 + "%" : "0%";
    });
  }

  global.HireLink = {
    toast: Mascotte.toast,
    openModal,
    openDrawer,
    closeModal,
  };

  document.addEventListener("DOMContentLoaded", () => {
    ensureHosts();
    bindScrollProgress();
  });
})(window);
