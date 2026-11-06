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

  /** Panneau ouvert = dialog pour les lecteurs d'écran, et le clavier doit y entrer : sans focus initial il reste derrière le voile. */
  function markDialog(panel) {
    panel.setAttribute("role", "dialog");
    panel.setAttribute("aria-modal", "true");
    panel.setAttribute("aria-labelledby", "hl-modal-title");
    if (!panel.hasAttribute("tabindex")) panel.setAttribute("tabindex", "-1");
  }

  function focusInto(panel) {
    if (!panel.contains(document.activeElement)) {
      try { panel.focus(); } catch (_) {}
    }
  }

  /** Un panneau qui en remplace un autre détruit son propre déclencheur : ne pas le mémoriser. */
  function rememberOpener() {
    const el = document.activeElement;
    const overlay = document.getElementById("hl-overlay");
    if (!el || el === document.body) return;
    if (overlay && overlay.contains(el)) return;
    previouslyFocused = el;
  }

  function openModal(options) {
    ensureHosts();
    rememberOpener();
    const overlay = document.getElementById("hl-overlay");
    overlay.classList.remove("overlay--drawer");
    const modal = dialogPanel(overlay);
    markDialog(modal);
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
    // Au-dessus des wizards (postulation ~9200, gate ~9300) si demandé.
    if (options.zIndex != null) overlay.style.zIndex = String(options.zIndex);
    else overlay.style.zIndex = "";
    overlay.classList.add("is-open");
    readyApi(modal, ok, options);
    focusInto(modal);
    return new Promise((resolve) => {
      modalResolver = resolve;
    });
  }

  /** Panneau latéral droit — garde le kanban visible (ATS type Greenhouse). */
  function openDrawer(options) {
    ensureHosts();
    rememberOpener();
    const overlay = document.getElementById("hl-overlay");
    overlay.classList.add("overlay--drawer");
    const panel = dialogPanel(overlay);
    markDialog(panel);
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
    focusInto(panel);
    return new Promise((resolve) => {
      modalResolver = resolve;
    });
  }

  function closeModal(result) {
    const overlay = document.getElementById("hl-overlay");
    if (overlay) {
      overlay.classList.remove("is-open");
      overlay.style.zIndex = "";
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
    const back = previouslyFocused && previouslyFocused.isConnected ? previouslyFocused : null;
    previouslyFocused = null;
    if (back) {
      try {
        back.focus();
      } catch (_) {}
    } else if (overlay && overlay.contains(document.activeElement)) {
      document.activeElement.blur();
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
    const focusables = panel.querySelectorAll(FOCUSABLE);
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

  const FOCUSABLE =
    'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

  /* Voiles dessinés hors de cette page (assistant de postulation, popup d'offre) : pile pour qu'une
     couche posée sur une autre soit la seule à répondre, sinon Escape fermerait les deux. */
  const trapStack = [];

  function layeredAbove(panel) {
    const ov = document.getElementById("hl-overlay");
    if (ov && ov.classList.contains("is-open")) return true;
    return trapStack.length && trapStack[trapStack.length - 1] !== panel;
  }

  /**
   * Contrat clavier d'un voile aria-modal : Tab ne sort pas du panneau, Escape déclenche la sortie
   * prévue par l'hôte, le focus entre à l'ouverture et revient au déclencheur à la fermeture.
   * Renvoie la fonction de libération — l'hôte doit l'appeler dans son propre close().
   */
  function trapDialog(panel, onClose) {
    if (!panel) return function () {};
    const opener = document.activeElement;
    if (!panel.hasAttribute("tabindex")) panel.setAttribute("tabindex", "-1");
    trapStack.push(panel);
    function onKey(e) {
      if (trapStack[trapStack.length - 1] !== panel || layeredAbove(panel)) return;
      if (e.key === "Escape") {
        // onClose peut répondre `false` : un widget imbriqué (menu déroulant, combo) garde la main sur Escape.
        if (onClose(e) === false) return;
        e.preventDefault();
        e.stopPropagation();
        return;
      }
      if (e.key !== "Tab") return;
      const f = panel.querySelectorAll(FOCUSABLE);
      if (!f.length) {
        e.preventDefault();
        panel.focus();
        return;
      }
      const first = f[0];
      const last = f[f.length - 1];
      const active = document.activeElement;
      if (!panel.contains(active)) {
        e.preventDefault();
        (e.shiftKey ? last : first).focus();
      } else if (e.shiftKey && (active === first || active === panel)) {
        e.preventDefault();
        last.focus();
      } else if (!e.shiftKey && active === last) {
        e.preventDefault();
        first.focus();
      }
    }
    document.addEventListener("keydown", onKey, true);
    setTimeout(() => {
      if (!panel.isConnected) return;
      const d = panel.matches('[role="dialog"], [role="alertdialog"]')
        ? panel
        : panel.querySelector('[role="dialog"], [role="alertdialog"]');
      const target = d || panel;
      if (target !== panel && !target.hasAttribute("tabindex")) target.setAttribute("tabindex", "-1");
      if (!panel.contains(document.activeElement)) {
        try { target.focus(); } catch (_) {}
      }
    }, 60);
    let done = false;
    return function release() {
      if (done) return;
      done = true;
      document.removeEventListener("keydown", onKey, true);
      const at = trapStack.indexOf(panel);
      if (at !== -1) trapStack.splice(at, 1);
      // Le piège ne rend le focus que s'il le détient encore : un hôte qui l'a déjà restauré n'est pas dédoublé.
      const held = panel.contains(document.activeElement);
      if (held && opener && opener.isConnected && opener.focus) {
        try { opener.focus(); } catch (_) {}
      }
    };
  }

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
    // La référence se prend à l'appel : une page qui charge ui.js avant mascotte.js doit quand même avoir ses toasts.
    toast: function () { return Mascotte.toast.apply(Mascotte, arguments); },
    openModal,
    openDrawer,
    openPreview: function (o) {
      if (global.HLUI && global.HLUI.openPreview) return global.HLUI.openPreview(o);
      return openModal(Object.assign({ large: true, hideConfirm: true, cancelLabel: "Fermer" }, o));
    },
    closeModal,
    trapDialog,
  };

  document.addEventListener("DOMContentLoaded", () => {
    ensureHosts();
    bindScrollProgress();
  });
})(window);
