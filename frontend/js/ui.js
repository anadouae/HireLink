/**
 * HireLink UI — toasts, modals, onboarding (pas de second système d'auth)
 */
(function (global) {
  function artBase() {
    return location.pathname.indexOf("/app/") >= 0 ? "../img/" : "img/";
  }

  function hashSeed(value) {
    let h = 0;
    const str = String(value || "hirelink");
    for (let i = 0; i < str.length; i++) h = (h * 31 + str.charCodeAt(i)) >>> 0;
    return h;
  }

  function moodOf(text, danger) {
    if (danger) return "danger";
    const s = String(text || "").toLowerCase();
    if (/refus|retir|désactiv|desactiv|erreur|dépass|depass|suspend|impossible|échou|echou/.test(s)) return "danger";
    if (/envoy|enregistr|confirm|vérif|verif|réussi|reussi|ajout|premium|bravo/.test(s)) return "ok";
    if (/\?|revoir|attente|attention|délai|delai|limite/.test(s)) return "warn";
    return "info";
  }

  function avatarSrc(seed, tone) {
    const moods = { ok: 1, info: 1, warn: 1, danger: 1 };
    const mood = moods[tone] ? tone : "info";
    const n = (hashSeed(seed) % 16) + 1;
    return artBase() + "avatars/p" + String(n).padStart(2, "0") + "-" + mood + ".svg";
  }

  function avatarHtml() {
    return "";
  }

  function sceneHtml() {
    return "";
  }

  global.HireLinkArt = { avatarSrc: avatarSrc, avatarHtml: avatarHtml, sceneHtml: sceneHtml, moodOf: moodOf };

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
    const toastMood = tone === "danger" ? "danger" : tone === "warn" ? "warn" : tone === "ok" ? "ok" : moodOf((title || "") + " " + (message || ""));
    el.innerHTML = avatarHtml(title || "message", "msg-avatar", toastMood) + "<div><strong></strong><span></span></div>";
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
    ok.onclick = () => {
      if (ok.disabled) return;
      if (typeof options.beforeConfirm === "function" && options.beforeConfirm() === false) return;
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

  function renderBrief(raw) {
    const lines = String(raw || "").replace(/\r/g, "").split("\n");
    const html = [];
    lines.forEach((line) => {
      let t = line.trim();
      if (!t) return;
      if (/^\|?\s*:?-{3,}/.test(t)) return;
      const heading = /^#{1,6}\s+/.test(t);
      t = t.replace(/^#{1,6}\s+/, "");
      if (t.indexOf("|") >= 0) {
        const cells = t
          .split("|")
          .map((c) => c.trim())
          .filter(Boolean);
        if (!cells.length || cells[0] === "#" || /^question/i.test(cells[0])) return;
        t = cells.join(" — ");
      }
      t = t.replace(/\*\*/g, "").replace(/\*/g, "").replace(/[#`]/g, "").replace(/^[-•]\s+/, "").trim();
      if (!t) return;
      const safe = t
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;");
      if (heading || (/^[A-ZÀ-Ü0-9].{0,42}$/.test(t) && !/[.?!]$/.test(t) && t.split(" ").length <= 6)) {
        html.push('<p class="brief-title">' + safe + "</p>");
      } else if (t.indexOf("?") >= 0) {
        html.push('<p class="brief-q">' + safe + "</p>");
      } else {
        html.push('<p class="brief-line">' + safe + "</p>");
      }
    });
    return '<article class="brief-sheet">' + (html.join("") || '<p class="brief-line">Aucun texte.</p>') + "</article>";
  }

  function mountLottie(root) {
    const slot = root.querySelector("[data-lottie]");
    if (!slot) return;
    const path = slot.getAttribute("data-lottie");
    function play() {
      if (!global.lottie) return;
      global.lottie.loadAnimation({
        container: slot,
        renderer: "svg",
        loop: true,
        autoplay: true,
        path: path,
      });
    }
    if (global.lottie) {
      play();
      return;
    }
    const script = document.createElement("script");
    script.src = "https://cdnjs.cloudflare.com/ajax/libs/lottie-web/5.12.2/lottie.min.js";
    script.onload = play;
    document.head.appendChild(script);
  }

  function openModal(options) {
    ensureHosts();
    previouslyFocused = document.activeElement;
    const overlay = document.getElementById("hl-overlay");
    overlay.classList.remove("overlay--drawer");
    const modal = dialogPanel(overlay);
    modal.className = "modal" + (options.large ? " modal-lg" : "");
    const anim = options.animationSource || "";
    modal.innerHTML =
      (anim ? '<div class="dialog-lottie" data-lottie="' + anim.replace(/"/g, "") + '"></div>' : "") +
      '<div class="dialog-lead">' +
      avatarHtml(options.title || "hirelink", "dialog-avatar", options.tone || moodOf((options.title || "") + " " + (options.description || ""), options.danger)) +
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
    mountLottie(modal);
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
      avatarHtml(options.title || "hirelink", "dialog-avatar", options.tone || moodOf((options.title || "") + " " + (options.description || ""), options.danger)) +
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
    openDrawer,
    renderBrief,
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
