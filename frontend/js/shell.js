/**
 * Shell partagé — sidebar, topbar, logout (candidat / recruteur)
 */
(function (global) {
  function mountShell(opts) {
    const session = HireLinkAPI.requireAuth(opts.roles);
    if (!session) return null;

    const sideUser = document.getElementById("side-user");
    const avatar = document.getElementById("avatar");
    if (sideUser) sideUser.textContent = session.email;
    if (avatar) avatar.textContent = (session.fullName || "?").slice(0, 1).toUpperCase();

    const hello = document.getElementById("hello");
    if (hello && opts.hello !== false) {
      hello.textContent = "Bonjour, " + (session.fullName || "");
    }

    const logout = document.getElementById("btn-logout");
    if (logout) {
      logout.addEventListener("click", async () => {
        const ok = await HireLink.openModal({
          title: "Se déconnecter ?",
          confirmLabel: "Déconnexion",
          danger: true,
        });
        if (ok) {
          HireLinkAPI.clearAuth();
          location.href = "../auth.html?mode=login";
        }
      });
    }

    const side = document.getElementById("sidebar");
    const open = document.getElementById("open-side");
    const close = document.getElementById("close-side");
    const mq = window.matchMedia("(max-width:900px)");

    function syncMobileToggle() {
      if (!open || !side) return;
      if (mq.matches) {
        open.style.display = "inline-flex";
      } else {
        open.style.display = "none";
        side.classList.remove("is-open");
      }
    }

    if (open && side) {
      open.addEventListener("click", () => side.classList.add("is-open"));
      syncMobileToggle();
      if (mq.addEventListener) mq.addEventListener("change", syncMobileToggle);
      else mq.addListener(syncMobileToggle);
    }
    if (close && side) close.addEventListener("click", () => side.classList.remove("is-open"));

    const roleHint = (opts.roles && opts.roles[0]) || "";
    if (global.HireLinkHelp) {
      HireLinkHelp.bindHelpLinks(roleHint);
    }

    ensureThemeToggle();

    return session;
  }

  /** Bouton mode sombre sur toutes les topbars d’espaces app. */
  function ensureThemeToggle() {
    const actions = document.querySelector(".topbar-actions");
    if (!actions) return;

    let btn = document.getElementById("btn-theme");
    if (!btn) {
      btn = document.createElement("button");
      btn.type = "button";
      btn.id = "btn-theme";
      btn.className = "icon-btn theme-toggle";
      btn.setAttribute("data-theme-toggle", "1");
      actions.insertBefore(btn, actions.firstChild);
    }

    if (!global.HireLinkTheme) {
      // charge theme.js à la volée si oublié dans le HTML
      const s = document.createElement("script");
      s.src = (location.pathname.includes("/app/") ? "../" : "") + "js/theme.js";
      s.onload = () => {
        if (global.HireLinkTheme) {
          global.HireLinkTheme.syncButtons();
          btn.onclick = () => global.HireLinkTheme.toggle();
        }
      };
      document.head.appendChild(s);
    } else {
      global.HireLinkTheme.syncButtons();
      btn.onclick = () => global.HireLinkTheme.toggle();
    }
  }

  /** Bind a search input: input + Enter + clear (search event). */
  function bindSearch(el, onQuery) {
    if (!el || typeof onQuery !== "function") return;
    const run = () => onQuery((el.value || "").trim());
    el.addEventListener("input", run);
    el.addEventListener("search", run);
    el.addEventListener("keydown", (e) => {
      if (e.key === "Enter") {
        e.preventDefault();
        run();
      }
    });
  }

  global.HireLinkShell = { mountShell, bindSearch, ensureThemeToggle };
})(window);
