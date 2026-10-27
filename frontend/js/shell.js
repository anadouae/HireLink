/**
 * Shell partagé — sidebar, topbar, logout (candidat / recruteur)
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

  function chosenIndex(id, name) {
    let raw = "";
    try { raw = localStorage.getItem("hirelink.avatar." + id) || ""; } catch (_) {}
    const n = parseInt(raw, 10);
    if (n >= 1 && n <= 16) return n;
    return (hashSeed(name || id || "hirelink") % 16) + 1;
  }

  function indexSrc(n, tone) {
    const moods = { ok: 1, info: 1, warn: 1, danger: 1 };
    const mood = moods[tone] ? tone : "info";
    const num = Math.min(16, Math.max(1, Number(n) || 1));
    return artBase() + "avatars/p" + String(num).padStart(2, "0") + "-" + mood + ".svg";
  }

  function mountPicker(root, userId, name, onChange) {
    if (!root) return;
    function paint() {
      const current = chosenIndex(userId, name);
      root.innerHTML =
        '<button type="button" class="avatar-open"><img alt="" src="' +
        indexSrc(current, "info") +
        '" /><span>Changer l’avatar</span></button>';
      root.querySelector("button").onclick = openChooser;
    }
    function openChooser() {
      let layer = document.getElementById("avatar-layer");
      if (!layer) {
        layer = document.createElement("div");
        layer.id = "avatar-layer";
        layer.className = "avatar-layer";
        document.body.appendChild(layer);
      }
      const current = chosenIndex(userId, name);
      let grid = "";
      for (let i = 1; i <= 16; i++) {
        grid +=
          '<button type="button" class="avatar-choice' +
          (i === current ? " is-on" : "") +
          '" data-n="' +
          i +
          '" style="animation-delay:' +
          i * 35 +
          'ms"><img alt="" src="' +
          indexSrc(i, "info") +
          '" /></button>';
      }
      layer.innerHTML =
        '<div class="avatar-stage" role="dialog" aria-modal="true" aria-label="Choisir un avatar">' +
        "<h2>Choisissez votre avatar</h2>" +
        '<div class="avatar-picker">' +
        grid +
        "</div>" +
        '<button type="button" class="btn btn-ghost" id="avatar-close">Fermer</button></div>';
      layer.classList.add("is-open");
      layer.querySelector("#avatar-close").onclick = () => layer.classList.remove("is-open");
      layer.onclick = (e) => {
        if (e.target === layer) layer.classList.remove("is-open");
      };
      layer.querySelectorAll(".avatar-choice").forEach((btn) => {
        btn.onclick = () => {
          const n = btn.getAttribute("data-n");
          try {
            localStorage.setItem("hirelink.avatar." + userId, n);
            localStorage.removeItem("hirelink.photo." + userId);
          } catch (_) {}
          layer.classList.remove("is-open");
          paint();
          if (typeof onChange === "function") onChange();
        };
      });
    }
    paint();
    if (location.hash === "#avatar-choice") setTimeout(openChooser, 280);
  }

  global.HireLinkArt = {
    avatarSrc: avatarSrc,
    avatarHtml: avatarHtml,
    sceneHtml: sceneHtml,
    moodOf: moodOf,
    chosenIndex: chosenIndex,
    indexSrc: indexSrc,
    mountPicker: mountPicker,
  };

  function mountShell(opts) {
    const session = HireLinkAPI.requireAuth(opts.roles);
    if (!session) return null;

    const sideUser = document.getElementById("side-user");
    const avatar = document.getElementById("avatar");
    if (sideUser) sideUser.textContent = session.email;
    if (avatar) avatar.remove();

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

    completeNav(session.role);
    if (session.role === "CANDIDAT" || session.role === "RECRUTEUR") polishChrome(session);
    else {
      ensureThemeToggle();
      mountNotifications();
      linkAvatar(session.role);
    }

    return session;
  }

  function completeNav(role) {
    const nav = document.querySelector(".side-nav");
    if (!nav || role !== "CANDIDAT") return;
    const path = location.pathname.replace(/\\/g, "/");
    const hash = location.hash || "";
    const items = [
      ["candidate-dashboard.html", "Tableau de bord"],
      ["candidate.html", "Offres"],
      ["candidate-companies.html", "Entreprises"],
      ["candidate-applications.html", "Mes candidatures"],
      ["candidate-settings.html#cv", "CV"],
    ];
    nav.innerHTML = items.map(function (item) {
      const href = item[0];
      const file = href.split("#")[0];
      let on = path.endsWith("/" + file);
      if (file === "candidate-settings.html") on = on && (hash === "#cv" || hash === "#set-cv");
      return '<a class="side-link' + (on ? " is-active" : "") + '" href="' + href + '">' + item[1] + "</a>";
    }).join("");
  }

  function polishChrome(session) {
    const side = document.getElementById("sidebar");
    const nav = document.querySelector(".side-nav");
    if (nav) {
      const path = location.pathname.replace(/\\/g, "/");
      Array.prototype.forEach.call(nav.querySelectorAll("a"), function (a) {
        const href = a.getAttribute("href") || "";
        const file = href.split("?")[0].split("#")[0].split("/").pop();
        const hash = href.indexOf("#") >= 0 ? href.slice(href.indexOf("#")) : "";
        let on = path.endsWith("/" + file);
        if (hash === "#cv") on = on && (location.hash === "#cv" || location.hash === "#set-cv");
        else if (file === "candidate-settings.html") on = on && location.hash !== "#cv" && location.hash !== "#set-cv";
        a.classList.toggle("is-active", on);
      });
    }
    document.querySelectorAll("a.brand").forEach(function (a) {
      a.setAttribute("href", session.role === "CANDIDAT" ? "candidate-dashboard.html" : "recruiter.html");
    });
    const foot = document.querySelector(".side-footer");
    if (foot) foot.hidden = true;
    const help = document.getElementById("btn-help");
    if (help) help.remove();
    const theme = document.getElementById("btn-theme");
    if (theme) theme.remove();

    if (!side) return;
    let cluster = document.getElementById("nav-user");
    if (cluster && cluster.dataset.ready === "1") return;
    if (!cluster) {
      cluster = document.createElement("div");
      cluster.id = "nav-user";
      cluster.className = "nav-user";
      side.appendChild(cluster);
    }
    cluster.dataset.ready = "1";

    const leftoverPlan = document.getElementById("nav-plan");
    if (leftoverPlan) leftoverPlan.remove();
    const advice = document.getElementById("side-advice");
    if (advice) advice.remove();
    cluster.innerHTML = "";
    cluster.hidden = true;

    const topbar = document.querySelector(".topbar");
    if (topbar) {
      topbar.hidden = false;
      topbar.classList.add("topbar-search-only");
    }
    mountTopRight(session);
    mountRail(side);
  }

  /** Ordre topbar droite : Passer Premium → cloche → profil */
  function mountTopRight(session) {
    const topbar = document.querySelector(".topbar");
    if (!topbar) return;
    let actions = topbar.querySelector(".topbar-actions");
    if (!actions) {
      actions = document.createElement("div");
      actions.className = "topbar-actions";
      topbar.appendChild(actions);
    }
    actions.querySelectorAll("#btn-upgrade, #btn-premium, #btn-crown, #btn-notif, .user-chip, #avatar").forEach(function (n) {
      n.remove();
    });

    const page = session.role === "RECRUTEUR" ? "recruiter-settings.html" : "candidate-settings.html";
    const roleKey = session.role === "RECRUTEUR" ? "recruiter" : "candidate";
    const active = localStorage.getItem("hirelink.premium.active." + roleKey) === "1";
    const href = page + "#plans";

    const crown = document.createElement("a");
    crown.id = "btn-crown";
    crown.href = href;
    crown.className = "top-crown" + (active ? " is-on" : "");
    crown.setAttribute("title", active ? "Formule supérieure active" : "Passer au plan supérieur");
    crown.innerHTML = icon("crown") + "<span>" + (active ? "Premium" : "Passer Premium") + "</span>";
    crown.addEventListener("click", function (e) {
      const path = location.pathname.replace(/\\/g, "/");
      if (path.endsWith("/" + page)) {
        e.preventDefault();
        const tab = document.querySelector('[data-settings="plans"]');
        if (tab) tab.click();
        location.hash = "#plans";
      }
    });
    actions.appendChild(crown);

    const bell = document.createElement("button");
    bell.type = "button";
    bell.id = "btn-notif";
    bell.className = "top-bell";
    bell.setAttribute("aria-label", "Notifications");
    bell.innerHTML = icon("bell") + '<span class="nav-bell-count" hidden>0</span>';
    bell.addEventListener("click", function (e) {
      e.stopPropagation();
      openNotifications();
    });
    actions.appendChild(bell);

    const avatar = document.getElementById("avatar");
    if (avatar) avatar.remove();
    actions.appendChild(buildUserChip(session, null));

    function pullBell() {
      if (document.hidden) return;
      HireLinkAPI.api("/notifications")
        .then(function (rows) {
          paintBell((rows || []).filter(function (n) { return !n.read; }).length);
        })
        .catch(function () {});
    }
    pullBell();
    setInterval(pullBell, 3000);
  }

  function mountRail(side) {
    if (!side || side.dataset.rail === "1") return;
    side.dataset.rail = "1";
    side.classList.add("is-rail");
    const panel = document.createElement("div");
    panel.className = "sidebar-panel";
    while (side.firstChild) panel.appendChild(side.firstChild);
    side.appendChild(panel);
    panel.querySelectorAll(".side-link").forEach(function (link) {
      const href = (link.getAttribute("href") || "").toLowerCase();
      const label = (link.textContent || "").trim();
      let name = "grid";
      if (href.indexOf("pipeline") >= 0) name = "columns";
      else if (href.indexOf("application") >= 0) name = "folder";
      else if (href.indexOf("companies") >= 0 || href.indexOf("team") >= 0) name = "building";
      else if (href.indexOf("settings") >= 0 || label.indexOf("Param") >= 0) name = "settings";
      else if (href.indexOf("#cv") >= 0 || label === "CV") name = "file";
      else if (href.indexOf("offer") >= 0 || /\/candidate\.html/.test(href) || href.endsWith("candidate.html")) name = "briefcase";
      else if (href.indexOf("dashboard") >= 0 || href.endsWith("recruiter.html") || label.indexOf("Tableau") >= 0) name = "grid";
      link.innerHTML = icon(name) + '<span class="side-label"></span>';
      link.querySelector(".side-label").textContent = label;
      link.setAttribute("title", label);
    });
    const pin = document.createElement("button");
    pin.type = "button";
    pin.className = "side-pin";
    pin.setAttribute("aria-label", "Garder le menu ouvert");
    pin.innerHTML = icon("chevron");
    const nav = panel.querySelector(".side-nav");
    if (nav) panel.insertBefore(pin, nav);
    else panel.appendChild(pin);
    try {
      if (localStorage.getItem("hirelink.sidebar.pinned") === "1") side.classList.add("is-pinned");
    } catch (_) {}
    pin.addEventListener("click", function (e) {
      e.preventDefault();
      e.stopPropagation();
      side.classList.toggle("is-pinned");
      try {
        localStorage.setItem("hirelink.sidebar.pinned", side.classList.contains("is-pinned") ? "1" : "0");
      } catch (_) {}
    });
  }

  function paintBell(unread) {
    const bell = document.getElementById("btn-notif");
    if (!bell) return;
    const count = bell.querySelector(".nav-bell-count");
    if (!count) return;
    count.hidden = !unread;
    count.textContent = unread > 9 ? "9+" : String(unread);
  }

  function buildUserChip(session, avatar) {
    const wrap = document.createElement("div");
    wrap.className = "user-chip user-chip-top";
    const btn = document.createElement("button");
    btn.type = "button";
    btn.className = "user-chip-btn top-profile";
    btn.setAttribute("aria-label", "Mon profil");
    btn.setAttribute("title", "Mon profil");
    btn.insertAdjacentHTML("afterbegin", icon("user"));
    const menu = document.createElement("div");
    menu.className = "user-menu";
    menu.hidden = true;
    const profile = session.role === "RECRUTEUR" ? "recruiter-settings.html" : "candidate-profile.html";
    const settings = session.role === "RECRUTEUR" ? "recruiter-settings.html" : "candidate-settings.html";
    menu.innerHTML =
      '<div class="user-menu-head"><strong></strong><span></span></div>' +
      '<a href="' + profile + '">' + icon("user") + "Mon profil</a>" +
      '<a href="' + settings + '">' + icon("settings") + "Paramètres</a>" +
      '<button type="button" data-help="1">' + icon("help") + "Aide</button>" +
      '<button type="button" data-logout="1">' + icon("logout") + "Déconnexion</button>";
    menu.querySelector("strong").textContent = session.fullName || "Compte";
    menu.querySelector("span").textContent = session.email || "";
    document.body.appendChild(menu);
    function placeMenu() {
      const rect = btn.getBoundingClientRect();
      const width = 240;
      const height = menu.offsetHeight || 220;
      let left = rect.right - width;
      if (left < 12) left = 12;
      if (left + width > window.innerWidth - 12) left = window.innerWidth - width - 12;
      let top = rect.bottom + 8;
      if (top + height > window.innerHeight - 12) top = Math.max(12, rect.top - height - 8);
      menu.style.top = Math.round(top) + "px";
      menu.style.left = Math.round(left) + "px";
      menu.style.zIndex = "200";
    }
    btn.addEventListener("click", function (e) {
      e.stopPropagation();
      menu.hidden = !menu.hidden;
      if (!menu.hidden) placeMenu();
    });
    menu.addEventListener("click", function (e) { e.stopPropagation(); });
    document.addEventListener("click", function () { menu.hidden = true; });
    window.addEventListener("resize", function () { menu.hidden = true; });
    window.addEventListener("scroll", function () { menu.hidden = true; }, true);
    menu.querySelector("[data-help]").addEventListener("click", function () {
      menu.hidden = true;
      if (global.HireLinkHelp) HireLinkHelp.openHelp(session.role);
    });
    menu.querySelector("[data-logout]").addEventListener("click", function () {
      const logout = document.getElementById("btn-logout");
      if (logout) logout.click();
      else {
        HireLinkAPI.clearAuth();
        location.href = "../auth.html?mode=login";
      }
    });
    wrap.appendChild(btn);
    return wrap;
  }

  function linkAvatar(role) {
    const avatar = document.getElementById("avatar");
    if (!avatar || avatar.dataset.linked) return;
    avatar.dataset.linked = "1";
    avatar.style.cursor = "pointer";
    avatar.title = "Mon profil";
    avatar.addEventListener("click", () => {
      const href = role === "RECRUTEUR" ? "recruiter-settings.html#photo-upload" : role === "CANDIDAT" ? "candidate-profile.html#photo-upload" : "admin.html";
      const file = href.split("#")[0];
      if (location.pathname.indexOf(file) >= 0) {
        const node = document.getElementById("photo-upload");
        if (node) node.scrollIntoView({ behavior: "smooth", block: "start" });
        return;
      }
      location.href = href;
    });
  }

  function mountNotifications() {
    const actions = document.querySelector(".topbar-actions");
    if (!actions || document.getElementById("btn-notif") || !global.HireLinkAPI) return;
    const btn = document.createElement("button");
    btn.type = "button";
    btn.id = "btn-notif";
    btn.className = "btn btn-ghost btn-sm";
    btn.textContent = "Notifications";
    const premium = document.getElementById("btn-premium") || document.getElementById("btn-upgrade");
    if (premium) actions.insertBefore(btn, premium);
    else actions.insertBefore(btn, actions.firstChild);
    btn.addEventListener("click", openNotifications);
    HireLinkAPI.api("/notifications")
      .then((rows) => {
        const unread = (rows || []).filter((n) => !n.read).length;
        if (unread) btn.textContent = "Notifications (" + unread + ")";
      })
      .catch(() => {});
  }

  async function openNotifications() {
    let rows = [];
    try {
      rows = await HireLinkAPI.api("/notifications");
    } catch (err) {
      if (global.HireLink) HireLink.toast("Notifications", err.message, "danger");
      return;
    }
    const list = (rows || []).slice(0, 12);
    const body = list.length
      ? list
          .map((n) => {
            const when = n.at ? new Date(n.at).toLocaleString("fr-FR") : "";
            return (
              "<article class='notif-row' data-id='" +
              n.id +
              "'>" +
              avatarHtml(n.title || n.id, "msg-avatar", moodOf((n.title || "") + " " + (n.body || ""))) +
              "<div><strong>" +
              esc(n.title) +
              "</strong><p>" +
              esc(n.body) +
              "</p><span>" +
              esc(when) +
              (n.read ? "" : " · Non lue") +
              "</span></div></article>"
            );
          })
          .join("")
      : sceneHtml("empty") + "<p class='muted'>Aucune notification pour le moment.</p>";
    await HireLink.openModal({
      title: "Notifications",
      description: "Candidatures, délais et messages du compte.",
      bodyHtml: "<div class='notif-list'>" + body + "</div>",
      confirmLabel: "Fermer",
      hideCancel: true,
      large: true,
      onReady: (api) => {
        api.root.querySelectorAll(".notif-row").forEach((row) => {
          row.addEventListener("click", async () => {
            const id = row.getAttribute("data-id");
            try {
              await HireLinkAPI.api("/notifications/" + id + "/read", { method: "POST" });
            } catch (_) {}
            row.querySelector("span").textContent = row.querySelector("span").textContent.replace(" · Non lue", "");
          });
        });
      },
    });
    const again = await HireLinkAPI.api("/notifications").catch(() => []);
    const unread = (again || []).filter((n) => !n.read).length;
    paintBell(unread);
  }

  function icon(name) {
    const paths = {
      user: '<circle cx="12" cy="8" r="3.25"/><path d="M5.2 19.2c.7-2.8 3-4.4 6.8-4.4s6.1 1.6 6.8 4.4"/>',
      settings: '<circle cx="12" cy="12" r="3"/><path d="M12 3.2v2M12 18.8v2M3.2 12h2M18.8 12h2M5.8 5.8l1.4 1.4M16.8 16.8l1.4 1.4M18.2 5.8l-1.4 1.4M7.2 16.8l-1.4 1.4"/>',
      help: '<circle cx="12" cy="12" r="8"/><path d="M9.5 9.4a2.5 2.5 0 1 1 3.4 2.3c-.8.4-1.2 1-1.2 1.8V14.2"/><path d="M12 17.2h.01"/>',
      logout: '<path d="M9.5 7V5.4A1.4 1.4 0 0 1 10.9 4h7.2A1.4 1.4 0 0 1 19.5 5.4v13.2a1.4 1.4 0 0 1-1.4 1.4h-7.2a1.4 1.4 0 0 1-1.4-1.4V17"/><path d="M13.5 12H4.8M7.2 9.2 4.5 12l2.7 2.8"/>',
      bell: '<path d="M6.2 16.2h11.6l-1.1-1.5V11a4.7 4.7 0 1 0-9.4 0v3.7l-1.1 1.5z"/><path d="M10 16.2a2 2 0 0 0 4 0"/>',
      chevron: '<path d="m7 10 5 5 5-5"/>',
      grid: '<rect x="4" y="4" width="6.5" height="6.5" rx="1.4"/><rect x="13.5" y="4" width="6.5" height="6.5" rx="1.4"/><rect x="4" y="13.5" width="6.5" height="6.5" rx="1.4"/><rect x="13.5" y="13.5" width="6.5" height="6.5" rx="1.4"/>',
      briefcase: '<rect x="3.5" y="7.5" width="17" height="12" rx="2"/><path d="M9 7.5V6.2A1.7 1.7 0 0 1 10.7 4.5h2.6A1.7 1.7 0 0 1 15 6.2v1.3"/>',
      folder: '<path d="M3.5 8.2V18a1.8 1.8 0 0 0 1.8 1.8h13.4A1.8 1.8 0 0 0 20.5 18V9.6a1.8 1.8 0 0 0-1.8-1.8h-6.2L10.2 5.6H5.3A1.8 1.8 0 0 0 3.5 7.4v.8z"/>',
      columns: '<rect x="4" y="4" width="4.5" height="16" rx="1.2"/><rect x="10" y="4" width="4.5" height="10" rx="1.2"/><rect x="16" y="4" width="4.5" height="13" rx="1.2"/>',
      building: '<path d="M5 20.5V5.2A1.2 1.2 0 0 1 6.2 4h7.1A1.2 1.2 0 0 1 14.5 5.2V20.5"/><path d="M14.5 9.5h3.3A1.2 1.2 0 0 1 19 10.7v9.8"/><path d="M8 8h2M8 12h2M8 16h2"/>',
      spark: '<path d="M12 3.5 13.4 9 19 10.5 13.4 12 12 17.5 10.6 12 5 10.5 10.6 9z"/>',
      crown: '<path d="M4.5 16.5 7 8.5l5 4.2 5-4.2 2.5 8H4.5z"/><path d="M5 18.5h14"/><circle cx="7" cy="7.2" r="1.3"/><circle cx="12" cy="5.4" r="1.3"/><circle cx="17" cy="7.2" r="1.3"/>',
      file: '<path d="M7 3.5h7.2L19.5 9v11.5A1.5 1.5 0 0 1 18 22H7a1.5 1.5 0 0 1-1.5-1.5v-17A1.5 1.5 0 0 1 7 3.5z"/><path d="M14 3.5V9h5.5"/><path d="M8.5 13h7M8.5 16.5h7"/>',
    };
    return '<svg class="menu-ico" viewBox="0 0 24 24" aria-hidden="true">' + (paths[name] || "") + "</svg>";
  }

  function watch(fn) {
    setInterval(function () {
      if (document.hidden || typeof fn !== "function") return;
      const overlay = document.getElementById("hl-overlay");
      if (overlay && overlay.classList.contains("is-open")) return;
      Promise.resolve()
        .then(fn)
        .catch(function () {});
    }, 3000);
  }

  function esc(s) {
    return String(s == null ? "" : s)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;");
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

  global.HireLinkShell = { mountShell, bindSearch, ensureThemeToggle, watch };
})(window);
