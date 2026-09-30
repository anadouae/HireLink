/**
 * HireLink UI — toasts, modals, onboarding (pas de second système d'auth)
 */
(function (global) {
  function apiBase() {
    return (global.HIRELINK_API_BASE || "http://localhost:8081/api/v1");
  }

  function ensureHosts() {
    if (!document.querySelector(".toast-host")) {
      const host = document.createElement("div");
      host.className = "toast-host";
      host.setAttribute("aria-live", "polite");
      document.body.appendChild(host);
    }
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

  function toast(title, message, tone) {
    ensureHosts();
    const el = document.createElement("div");
    el.className = "toast" + (tone ? " toast-" + tone : "");
    el.innerHTML = "<div><strong></strong><span></span></div>";
    el.querySelector("strong").textContent = title;
    el.querySelector("span").textContent = message || "";
    document.querySelector(".toast-host").appendChild(el);
    setTimeout(() => {
      el.style.opacity = "0";
      el.style.transform = "translateY(8px)";
      el.style.transition = "0.25s ease";
      setTimeout(() => el.remove(), 260);
    }, 3800);
  }

  let modalResolver = null;
  let previouslyFocused = null;

  function openModal(options) {
    ensureHosts();
    previouslyFocused = document.activeElement;
    const overlay = document.getElementById("hl-overlay");
    const modal = overlay.querySelector(".modal");
    modal.className = "modal" + (options.large ? " modal-lg" : "");
    modal.innerHTML =
      '<h3 class="modal-title" id="hl-modal-title"></h3>' +
      '<p class="modal-desc" id="hl-modal-desc"></p>' +
      '<div class="modal-body"></div>' +
      '<div class="modal-actions"></div>';
    modal.querySelector(".modal-title").textContent = options.title || "";
    modal.querySelector(".modal-desc").textContent = options.description || "";
    if (options.bodyHtml) {
      modal.querySelector(".modal-body").innerHTML = options.bodyHtml;
    }
    const actions = modal.querySelector(".modal-actions");
    const cancel = document.createElement("button");
    cancel.type = "button";
    cancel.className = "btn btn-ghost";
    cancel.textContent = options.cancelLabel || "Annuler";
    cancel.onclick = () => closeModal(false);
    const ok = document.createElement("button");
    ok.type = "button";
    ok.className = "btn " + (options.danger ? "btn-danger" : "btn-primary");
    ok.textContent = options.confirmLabel || "Confirmer";
    ok.onclick = () => closeModal(true);
    if (!options.hideCancel) actions.appendChild(cancel);
    actions.appendChild(ok);
    overlay.classList.add("is-open");
    setTimeout(() => ok.focus(), 30);
    return new Promise((resolve) => {
      modalResolver = resolve;
    });
  }

  function closeModal(result) {
    const overlay = document.getElementById("hl-overlay");
    if (overlay) overlay.classList.remove("is-open");
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
    const modal = overlay.querySelector(".modal");
    const focusables = modal.querySelectorAll(
      'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
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

  async function apiHealth() {
    try {
      const res = await fetch(apiBase() + "/health");
      if (!res.ok) throw new Error("HTTP " + res.status);
      return await res.json();
    } catch (e) {
      return null;
    }
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

  function initOnboarding(key, options) {
    if (localStorage.getItem(key)) return;
    setTimeout(async () => {
      const ok = await openModal({
        title: options.title,
        description: options.description,
        bodyHtml: options.bodyHtml || "",
        confirmLabel: options.confirmLabel || "Commencer",
        cancelLabel: "Plus tard",
        large: true,
      });
      if (ok) localStorage.setItem(key, "1");
      if (ok && options.onConfirm) options.onConfirm();
    }, 700);
  }

  global.HireLink = {
    get API_BASE() {
      return apiBase();
    },
    toast,
    openModal,
    closeModal,
    apiHealth,
    bindScrollProgress,
    initOnboarding,
  };

  document.addEventListener("DOMContentLoaded", () => {
    ensureHosts();
    bindScrollProgress();
  });
})(window);
