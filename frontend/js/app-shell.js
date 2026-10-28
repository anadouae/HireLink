/**
 * Shell partagé des trois espaces (recruteur, candidat, administrateur) :
 * sidebar, topbar, notifications, menu profil, thème, mobile,
 * cache stale-while-revalidate, états (chargement / vide / erreur) et helpers.
 * Une seule définition de la navigation par rôle ; le rôle vient de <body data-role>.
 */
(function (global) {
  "use strict";

  /* ------------------------------------------------------------------ utils */
  var STATUS_FR = {
    SUBMITTED: "Envoyée", SCREENING: "Présélection", SHORTLISTED: "Shortlist", INTERVIEW: "Entretien",
    REJECTED: "Refusée", WITHDRAWN: "Retirée",
  };
  var ACTIVE = ["SUBMITTED", "SCREENING", "SHORTLISTED", "INTERVIEW"];
  var INTERVIEW_TYPE_FR = { PHONE: "Téléphone", VISIO: "Visio", ONSITE: "Présentiel", ASYNC: "Asynchrone" };
  var ROLE_FR = { OWNER: "Owner", RECRUTEUR: "Recruteur", HIRING_MANAGER: "Hiring Manager" };

  function esc(s) {
    return String(s == null ? "" : s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#39;");
  }
  function initials(name) {
    var p = String(name || "?").replace(/\s*\([^)]*\)\s*/g, " ").trim().split(/\s+/).filter(Boolean);
    if (!p.length) return "?";
    return (p.length === 1 ? p[0].slice(0, 2) : p[0][0] + p[p.length - 1][0]).toUpperCase();
  }
  function fmtDate(v) {
    if (!v) return "—";
    var d = new Date(v);
    return isNaN(d) ? "—" : d.toLocaleDateString("fr-FR", { day: "2-digit", month: "short", year: "numeric" });
  }
  function fmtDateTime(v) {
    if (!v) return "—";
    var d = new Date(v);
    return isNaN(d) ? "—" : d.toLocaleString("fr-FR", { day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit" });
  }
  function ago(v) {
    if (!v) return "";
    var s = Math.round((Date.now() - new Date(v).getTime()) / 1000);
    if (s < 60) return "à l’instant";
    if (s < 3600) return "il y a " + Math.floor(s / 60) + " min";
    if (s < 86400) return "il y a " + Math.floor(s / 3600) + " h";
    if (s < 86400 * 7) return "il y a " + Math.floor(s / 86400) + " j";
    return fmtDate(v);
  }
  function plural(n, one, many) { return n + " " + (n > 1 ? many || one + "s" : one); }
  function debounce(fn, ms) {
    var t;
    return function () { var a = arguments, c = this; clearTimeout(t); t = setTimeout(function () { fn.apply(c, a); }, ms || 160); };
  }

  /* ------------------------------------------------------------------ icônes */
  var P = {
    home: '<path d="M3.5 11 12 4l8.5 7"/><path d="M5.5 9.8V20h13V9.8"/><path d="M10 20v-5h4v5"/>',
    columns: '<rect x="4" y="4" width="4.5" height="16" rx="1.2"/><rect x="10" y="4" width="4.5" height="10" rx="1.2"/><rect x="16" y="4" width="4.5" height="13" rx="1.2"/>',
    users: '<circle cx="9" cy="8.5" r="3.2"/><path d="M3.2 19c.6-3 2.8-4.7 5.8-4.7s5.2 1.7 5.8 4.7"/><path d="M16 5.6a3 3 0 0 1 0 5.8M17.6 14.6c1.8.5 3 2 3.4 4.4"/>',
    calendar: '<rect x="3.5" y="5" width="17" height="15" rx="2.2"/><path d="M3.5 10h17M8 3v4M16 3v4"/>',
    briefcase: '<rect x="3.5" y="7.5" width="17" height="12" rx="2"/><path d="M9 7.5V6.2A1.7 1.7 0 0 1 10.7 4.5h2.6A1.7 1.7 0 0 1 15 6.2v1.3M3.5 13h17"/>',
    spark: '<path d="M12 3.5 13.6 9 19 10.6 13.6 12.2 12 17.7 10.4 12.2 5 10.6 10.4 9z"/><path d="M18.5 16.5l.7 1.8 1.8.7-1.8.7-.7 1.8-.7-1.8-1.8-.7 1.8-.7z"/>',
    bell: '<path d="M6.2 16.2h11.6l-1.1-1.5V11a4.7 4.7 0 1 0-9.4 0v3.7l-1.1 1.5z"/><path d="M10 16.2a2 2 0 0 0 4 0"/>',
    building: '<path d="M5 20.5V5.2A1.2 1.2 0 0 1 6.2 4h7.1a1.2 1.2 0 0 1 1.2 1.2v15.3"/><path d="M14.5 9.5h3.3a1.2 1.2 0 0 1 1.2 1.2v9.8M8 8h2M8 12h2M8 16h2M3.5 20.5h17"/>',
    settings: '<circle cx="12" cy="12" r="3"/><path d="M12 3.2v2.3M12 18.5v2.3M3.2 12h2.3M18.5 12h2.3M5.8 5.8l1.6 1.6M16.6 16.6l1.6 1.6M18.2 5.8l-1.6 1.6M7.4 16.6l-1.6 1.6"/>',
    search: '<circle cx="11" cy="11" r="6.2"/><path d="m16 16 4.5 4.5"/>',
    crown: '<path d="M4.5 16.5 7 8.5l5 4.2 5-4.2 2.5 8H4.5z"/><path d="M5 18.8h14"/>',
    moon: '<path d="M21 12.8A9 9 0 1 1 11.2 3 7 7 0 0 0 21 12.8z"/>',
    sun: '<circle cx="12" cy="12" r="4"/><path d="M12 2.5v2M12 19.5v2M4.6 4.6 6 6M18 18l1.4 1.4M2.5 12h2M19.5 12h2M4.6 19.4 6 18M18 6l1.4-1.4"/>',
    menu: '<path d="M4 7h16M4 12h16M4 17h16"/>',
    help: '<circle cx="12" cy="12" r="8.5"/><path d="M9.6 9.5a2.5 2.5 0 1 1 3.4 2.3c-.8.4-1.2 1-1.2 1.8v.4M12 17.2h.01"/>',
    logout: '<path d="M9.5 7V5.4A1.4 1.4 0 0 1 10.9 4h7.2a1.4 1.4 0 0 1 1.4 1.4v13.2a1.4 1.4 0 0 1-1.4 1.4h-7.2a1.4 1.4 0 0 1-1.4-1.4V17"/><path d="M13.5 12H4.8M7.2 9.2 4.5 12l2.7 2.8"/>',
    user: '<circle cx="12" cy="8" r="3.4"/><path d="M5.2 19.5c.7-3 3-4.6 6.8-4.6s6.1 1.6 6.8 4.6"/>',
    inbox: '<path d="M4 13.5 6.2 5.5h11.6L20 13.5v5a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 18.5z"/><path d="M4 13.5h4.5l1 2.2h5l1-2.2H20"/>',
    alert: '<path d="M12 4 3.5 19h17z"/><path d="M12 10v4M12 16.8h.01"/>',
    chart: '<path d="M4 20V10M10 20V4M16 20v-7M22 20H2"/>',
    file: '<path d="M7 3.5h7.2L19.5 9v11.5A1.5 1.5 0 0 1 18 22H7a1.5 1.5 0 0 1-1.5-1.5v-17A1.5 1.5 0 0 1 7 3.5z"/><path d="M14 3.5V9h5.5M8.5 13h7M8.5 16.5h7"/>',
    card: '<rect x="3" y="5.5" width="18" height="13" rx="2.2"/><path d="M3 10h18M7 15h3"/>',
    ban: '<circle cx="12" cy="12" r="8.5"/><path d="m6 6 12 12"/>',
    shield: '<path d="M12 3.2 5 6v5.6c0 4.2 2.9 7.6 7 9.2 4.1-1.6 7-5 7-9.2V6z"/><path d="m9.2 12.2 2 2 3.6-4"/>',
  };
  function svg(name) {
    return '<svg viewBox="0 0 24 24" aria-hidden="true">' + (P[name] || "") + "</svg>";
  }

  /* ----------------------------------------------------------------- cache */
  var CK = "hirelink.rc.cache.";
  function cacheGet(key, maxAge) {
    try {
      var raw = sessionStorage.getItem(CK + key);
      if (!raw) return null;
      var o = JSON.parse(raw);
      if (maxAge && Date.now() - o.t > maxAge) return null;
      return o.v;
    } catch (_) { return null; }
  }
  function cacheSet(key, v) {
    try { sessionStorage.setItem(CK + key, JSON.stringify({ t: Date.now(), v: v })); } catch (_) {}
  }
  function cacheClear(prefix) {
    try {
      Object.keys(sessionStorage).forEach(function (k) {
        if (k.indexOf(CK + (prefix || "")) === 0) sessionStorage.removeItem(k);
      });
    } catch (_) {}
  }
  var inflight = {};
  /**
   * Stale-while-revalidate : appelle onData(cache) tout de suite si présent, puis
   * onData(frais) quand le réseau répond (si différent). Requêtes identiques dédupliquées.
   */
  function swr(key, path, onData, onError) {
    var cached = cacheGet(key, 5 * 60 * 1000);
    var sig = null;
    if (cached != null) { sig = JSON.stringify(cached); onData(cached, true); }
    var p = inflight[key] || (inflight[key] = HireLinkAPI.api(path).finally(function () { delete inflight[key]; }));
    return p.then(function (fresh) {
      cacheSet(key, fresh);
      if (JSON.stringify(fresh) !== sig) onData(fresh, false);
      return fresh;
    }).catch(function (err) {
      if (cached == null && onError) onError(err);
      else if (cached != null) toast("Connexion", "Impossible d’actualiser : " + err.message, "warn");
      return null;
    });
  }
  var data = {
    pipeline: function (cb, err) { return swr("pipeline", "/applications/pipeline", cb, err); },
    offers: function (cb, err) { return swr("offers", "/offers/mine", cb, err); },
    org: function (cb, err) { return swr("org", "/organizations/mine", cb, err); },
    invalidate: function () { cacheClear("pipeline"); cacheClear("offers"); },
  };

  /* ---------------------------------------------------------------- états */
  function skeleton(el, rows, h) {
    var s = "";
    for (var i = 0; i < (rows || 4); i++) s += '<span class="rc-skel" style="height:' + (h || 54) + 'px"></span>';
    el.innerHTML = '<div class="rc-skel-list" aria-busy="true" aria-label="Chargement">' + s + "</div>";
  }
  function empty(el, o) {
    o = o || {};
    el.innerHTML =
      '<div class="rc-state rc-fade"><div class="rc-state-ico">' + svg(o.icon || "inbox") + "</div><h3>" + esc(o.title || "Rien à afficher") + "</h3><p>" +
      esc(o.text || "") + "</p>" + (o.action ? '<a class="btn btn-primary btn-sm" href="' + esc(o.action.href) + '">' + esc(o.action.label) + "</a>" : "") + "</div>";
  }
  function errorState(el, message, retry) {
    el.innerHTML =
      '<div class="rc-state is-error rc-fade"><div class="rc-state-ico">' + svg("alert") + "</div><h3>Chargement impossible</h3><p>" + esc(message || "Erreur réseau") +
      '</p><button type="button" class="btn btn-primary btn-sm" data-retry>Réessayer</button></div>';
    var b = el.querySelector("[data-retry]");
    if (b && retry) b.addEventListener("click", retry);
  }
  function toast(t, m, tone) { if (global.HireLink) HireLink.toast(t, m, tone); }
  function busy(btn, on) { if (btn) { btn.classList.toggle("is-loading", !!on); btn.disabled = !!on; } }

  /* --------------------------------------------------------------- session */
  var session = null;
  var orgInfo = null;
  function role() { return orgInfo && orgInfo.myRole; }
  function isHM() { return role() === "HIRING_MANAGER"; }
  function isOwner() { return role() === "OWNER"; }
  function canManage() { return !isHM(); }

  /* ------------------------------------------------------------------- nav */
  var ROLES = {
    RECRUTEUR: {
      home: "recruiter.html", label: "Espace recruteur", plans: "recruiter-settings.html#plans", premiumKey: "recruiter",
      search: "Rechercher un candidat, une offre…", searchGo: function (q) { return "recruiter-candidates.html?q=" + encodeURIComponent(q); },
      nav: [
        { id: "dashboard", href: "recruiter.html", label: "Tableau de bord", icon: "home" },
        { id: "pipeline", href: "recruiter-pipeline.html", label: "Pipeline", icon: "columns" },
        { id: "candidates", href: "recruiter-candidates.html", label: "Candidats", icon: "users" },
        { id: "interviews", href: "recruiter-interviews.html", label: "Entretiens", icon: "calendar" },
        { id: "offers", href: "recruiter-offers.html", label: "Offres", icon: "briefcase", manage: true },
        { id: "assistant", href: "recruiter-assistant.html", label: "Assistant IA", icon: "spark" },
        { section: "Organisation" },
        { id: "notifications", href: "notifications.html", label: "Notifications", icon: "bell", badge: true },
        { id: "team", href: "recruiter-team.html", label: "Entreprise & équipe", icon: "building" },
        { id: "security", href: "security.html", label: "Sécurité", icon: "shield" },
        { id: "settings", href: "recruiter-settings.html", label: "Paramètres", icon: "settings" },
      ],
      menu: [["recruiter-settings.html#compte", "user", "Mon compte"], ["recruiter-team.html", "building", "Entreprise & équipe"], ["security.html", "shield", "Sécurité"], ["recruiter-settings.html", "settings", "Paramètres"]],
    },
    CANDIDAT: {
      home: "candidate-dashboard.html", label: "Espace candidat", plans: "candidate-settings.html#plans", premiumKey: "candidate",
      search: "Poste, entreprise, ville, compétence…", searchGo: function (q) { return "candidate.html?q=" + encodeURIComponent(q); },
      nav: [
        { id: "dashboard", href: "candidate-dashboard.html", label: "Tableau de bord", icon: "home" },
        { id: "offers", href: "candidate.html", label: "Offres", icon: "briefcase" },
        { id: "companies", href: "candidate-companies.html", label: "Entreprises", icon: "building" },
        { id: "applications", href: "candidate-applications.html", label: "Mes candidatures", icon: "inbox" },
        { id: "profile", href: "candidate-profile.html", label: "Profil & CV", icon: "file" },
        { section: "Compte" },
        { id: "notifications", href: "notifications.html", label: "Notifications", icon: "bell", badge: true },
        { id: "security", href: "security.html", label: "Sécurité", icon: "shield" },
        { id: "settings", href: "candidate-settings.html", label: "Paramètres", icon: "settings" },
      ],
      menu: [["candidate-profile.html", "user", "Mon profil"], ["security.html", "shield", "Sécurité"], ["candidate-settings.html", "settings", "Paramètres"]],
    },
    ADMINISTRATEUR: {
      home: "admin.html", label: "Administration", plans: null, premiumKey: null, noBell: true, noSearch: true,
      nav: [
        { id: "dash", href: "admin.html#dash", label: "Tableau de bord", icon: "chart" },
        { id: "orgs", href: "admin.html#orgs", label: "Entreprises", icon: "building" },
        { id: "users", href: "admin.html#users", label: "Comptes", icon: "users" },
        { section: "Référentiel" },
        { id: "plans", href: "admin.html#plans", label: "Plans & factures", icon: "card" },
        { id: "refusals", href: "admin.html#refusals", label: "Motifs de refus", icon: "ban" },
        { section: "Sécurité" },
        { id: "audit", href: "admin.html#audit", label: "Journal d’audit", icon: "file" },
        { id: "security", href: "security.html", label: "Mon compte", icon: "shield" },
      ],
      menu: [["security.html", "shield", "Sécurité du compte"]],
    },
  };
  var CFG = ROLES.RECRUTEUR;

  function setActive(id) {
    document.querySelectorAll("#rc-sidebar .rc-link").forEach(function (a) {
      var on = a.getAttribute("data-nav") === id;
      a.classList.toggle("is-active", on);
      if (on) a.setAttribute("aria-current", "page"); else a.removeAttribute("aria-current");
    });
  }

  function buildSidebar(active) {
    var side = document.getElementById("rc-sidebar");
    if (!side) return;
    var logo = (location.pathname.indexOf("/app/") >= 0 ? "../" : "") + "assets/logo-hirelink-icon.png";
    var html = '<a class="rc-brand" href="' + CFG.home + '"><img src="' + logo + '" alt="" width="30" height="30" /><span>Hire<b>Link</b></span></a><nav class="rc-nav" aria-label="Navigation principale">';
    CFG.nav.forEach(function (n) {
      if (n.section) { html += '<div class="rc-nav-label">' + n.section + "</div>"; return; }
      html += '<a class="rc-link' + (n.id === active ? " is-active" : "") + '" href="' + n.href + '" data-nav="' + n.id + '"' + (n.manage ? ' data-manage="1"' : "") +
        (n.id === active ? ' aria-current="page"' : "") + ">" + svg(n.icon) + "<span>" + n.label + "</span>" +
        (n.badge ? '<b class="rc-count" id="rc-nav-count" hidden>0</b>' : "") + "</a>";
    });
    var isRec = session.role === "RECRUTEUR";
    var cardHref = isRec ? "recruiter-team.html" : session.role === "CANDIDAT" ? "candidate-profile.html" : "admin.html#dash";
    html += '</nav><a class="rc-orgcard" href="' + cardHref + '" id="rc-orgcard"><div class="rc-orgmark" id="rc-orgmark">·</div><div><strong id="rc-orgname">' + esc(isRec ? "Organisation" : session.fullName || CFG.label) + '</strong><span id="rc-orgrole">' + (isRec ? "&nbsp;" : esc(CFG.label)) + "</span></div></a>";
    side.innerHTML = html;
    if (isRec) paintOrg();
    else document.getElementById("rc-orgmark").textContent = initials(session.fullName || CFG.label).charAt(0);
  }

  function paintOrg() {
    var name = document.getElementById("rc-orgname");
    if (!name) return;
    var o = orgInfo || cacheGet("org");
    if (!o) return;
    name.textContent = o.name || "Organisation";
    document.getElementById("rc-orgrole").textContent = (ROLE_FR[o.myRole] || "Recruteur") + (o.verified ? " · vérifiée" : " · non vérifiée");
    var mark = document.getElementById("rc-orgmark");
    var src = o.logoUrl && o.logoUrl.indexOf("google.com/s2/favicons") < 0 ? HireLinkAPI.mediaUrl(o.logoUrl) : "";
    if (src) {
      mark.innerHTML = '<img alt="" src="' + esc(src) + '" />';
      mark.firstChild.onerror = function () { mark.textContent = initials(o.name).charAt(0); };
    } else mark.textContent = initials(o.name).charAt(0);
    document.querySelectorAll("[data-manage]").forEach(function (a) { a.hidden = !canManage(); });
  }

  /* --------------------------------------------------------------- topbar */
  function photoUrl() {
    try { return localStorage.getItem("hirelink.photo." + (session.userId || "")) || ""; } catch (_) { return ""; }
  }
  function paintProfile() {
    var b = document.getElementById("rc-profile");
    if (!b) return;
    var p = photoUrl();
    b.innerHTML = p ? '<img alt="" src="' + esc(p) + '" />' : esc(initials(session.fullName));
  }
  function premiumOn() { return !!CFG.premiumKey && HireLinkAPI.isPremium(); }

  function buildTopbar(opts) {
    var bar = document.getElementById("rc-topbar");
    if (!bar) return;
    var dark = global.HireLinkTheme && HireLinkTheme.current() === "dark";
    bar.innerHTML =
      '<button type="button" class="rc-iconbtn rc-burger" id="rc-burger" aria-label="Ouvrir le menu">' + svg("menu") + "</button>" +
      (CFG.noSearch ? '<span class="rc-spacer"></span>' : '<label class="rc-search">' + svg("search") + '<input type="search" id="rc-search" placeholder="' + esc((opts.search && opts.search.placeholder) || CFG.search) + '" autocomplete="off" aria-label="Rechercher" /></label>') +
      '<div class="rc-actions">' +
      (CFG.plans ? '<a class="rc-crown' + (premiumOn() ? " is-on" : "") + '" href="' + CFG.plans + '" title="Formules">' + svg("crown") + "<span>" + (premiumOn() ? "Premium" : "Passer Premium") + "</span></a>" : "") +
      '<button type="button" class="rc-iconbtn" id="rc-theme" aria-label="Changer de thème">' + svg(dark ? "sun" : "moon") + "</button>" +
      (CFG.noBell ? "" : '<button type="button" class="rc-iconbtn" id="rc-bell" aria-label="Notifications" aria-haspopup="true">' + svg("bell") + '<span class="rc-dot" id="rc-bell-dot" hidden>0</span></button>') +
      '<button type="button" class="rc-profile" id="rc-profile" aria-label="Menu du compte" aria-haspopup="true"></button></div>';
    paintProfile();

    var side = document.getElementById("rc-sidebar");
    var scrim = document.getElementById("rc-scrim");
    function closeSide() { side.classList.remove("is-open"); document.body.classList.remove("rc-menu-open"); }
    document.getElementById("rc-burger").addEventListener("click", function () { side.classList.add("is-open"); document.body.classList.add("rc-menu-open"); });
    if (scrim) scrim.addEventListener("click", closeSide);
    side.addEventListener("click", function (e) { if (e.target.closest("a")) closeSide(); });

    document.getElementById("rc-theme").addEventListener("click", function () {
      if (!global.HireLinkTheme) return;
      HireLinkTheme.toggle();
      this.innerHTML = svg(HireLinkTheme.current() === "dark" ? "sun" : "moon");
    });

    var input = document.getElementById("rc-search");
    if (!input) { buildPopovers(); return; }
    var q = new URLSearchParams(location.search).get("q");
    if (q && opts.search) input.value = q;
    if (opts.search && opts.search.onQuery) {
      input.addEventListener("input", debounce(function () { opts.search.onQuery(input.value.trim()); }, 120));
      input.addEventListener("search", function () { opts.search.onQuery(input.value.trim()); });
      input.addEventListener("keydown", function (e) { if (e.key === "Enter") e.preventDefault(); });
    } else {
      input.addEventListener("keydown", function (e) {
        if (e.key === "Enter" && input.value.trim()) location.href = CFG.searchGo(input.value.trim());
      });
    }
    buildPopovers();
  }

  var pops = {};
  function makePop(id, cls) {
    var el = document.createElement("div");
    el.className = "rc-pop " + (cls || "");
    el.id = id; el.setAttribute("role", "dialog");
    document.body.appendChild(el);
    pops[id] = el;
    return el;
  }
  function placePop(pop, anchor) {
    var r = anchor.getBoundingClientRect();
    var w = pop.offsetWidth || 340;
    pop.style.top = Math.round(r.bottom + 8) + "px";
    pop.style.left = Math.max(12, Math.min(window.innerWidth - w - 12, r.right - w)) + "px";
  }
  function togglePop(pop, anchor, on) {
    Object.keys(pops).forEach(function (k) { if (pops[k] !== pop) pops[k].classList.remove("is-open"); });
    var open = on == null ? !pop.classList.contains("is-open") : on;
    if (open) placePop(pop, anchor);
    pop.classList.toggle("is-open", open);
  }

  var notifRows = [];
  function paintBell(unread) {
    var dot = document.getElementById("rc-bell-dot");
    if (dot) { dot.hidden = !unread; dot.textContent = unread > 9 ? "9+" : String(unread); }
    var c = document.getElementById("rc-nav-count");
    if (c) { c.hidden = !unread; c.textContent = unread > 99 ? "99+" : String(unread); }
  }
  function pullNotifs() {
    if (document.hidden) return Promise.resolve();
    return HireLinkAPI.api("/notifications").then(function (rows) {
      notifRows = rows || [];
      paintBell(notifRows.filter(function (n) { return !n.read; }).length);
      if (pops["rc-pop-notif"] && pops["rc-pop-notif"].classList.contains("is-open")) renderNotifPop();
      document.dispatchEvent(new CustomEvent("rc:notifications", { detail: notifRows }));
    }).catch(function () {});
  }
  function renderNotifPop() {
    var pop = pops["rc-pop-notif"];
    var rows = notifRows.slice(0, 6);
    pop.innerHTML =
      '<div class="rc-pop-head"><span>Notifications</span><button type="button" id="rc-readall">Tout marquer comme lu</button></div><div class="rc-pop-body">' +
      (rows.length ? rows.map(function (n) {
        return '<div class="rc-notif' + (n.read ? "" : " is-unread") + '" data-id="' + n.id + '"><i></i><div><strong>' + esc(n.title) + "</strong><p>" + esc(n.body) + "</p><time>" + esc(ago(n.at)) + "</time></div></div>";
      }).join("") : '<div class="rc-state" style="padding:1.6rem"><p style="margin:0">Aucune notification pour le moment.</p></div>') +
      '</div><div class="rc-pop-foot"><a href="notifications.html">Voir toutes les notifications</a></div>';
    pop.querySelectorAll(".rc-notif").forEach(function (row) {
      row.addEventListener("click", function () {
        var n = notifRows.find(function (x) { return String(x.id) === row.getAttribute("data-id"); });
        markRead(row.getAttribute("data-id")).then(function () { if (n) location.href = notifTarget(n); });
      });
    });
    var all = pop.querySelector("#rc-readall");
    if (all) all.addEventListener("click", markAllRead);
  }
  /** Page où mène une notification, selon le rôle et le type d'événement. */
  function notifTarget(n) {
    var k = n && n.kind;
    if (session.role === "CANDIDAT") return "candidate-applications.html";
    if (session.role === "ADMINISTRATEUR") return "admin.html";
    if (k === "INTERVIEW") return "recruiter-interviews.html";
    if (k === "MODERATION") return "recruiter-offers.html";
    if (k === "SYSTEM") return "recruiter-team.html";
    return "recruiter-pipeline.html";
  }
  function markRead(id) {
    var n = notifRows.find(function (x) { return String(x.id) === String(id); });
    if (!n || n.read) return Promise.resolve();
    n.read = true;
    paintBell(notifRows.filter(function (x) { return !x.read; }).length);
    if (pops["rc-pop-notif"] && pops["rc-pop-notif"].classList.contains("is-open")) renderNotifPop();
    return HireLinkAPI.api("/notifications/" + id + "/read", { method: "POST" }).catch(function () {});
  }
  function markAllRead() {
    var unread = notifRows.filter(function (n) { return !n.read; });
    return Promise.all(unread.map(function (n) { return markRead(n.id); })).then(function () {
      document.dispatchEvent(new CustomEvent("rc:notifications", { detail: notifRows }));
    });
  }

  function buildPopovers() {
    var bell = document.getElementById("rc-bell");
    if (bell) {
      var np = makePop("rc-pop-notif");
      bell.addEventListener("click", function (e) { e.stopPropagation(); renderNotifPop(); togglePop(np, bell); pullNotifs(); });
    }
    var mp = makePop("rc-pop-menu", "rc-menu");
    var prof = document.getElementById("rc-profile");
    mp.innerHTML =
      '<div class="rc-menu-who"><strong>' + esc(session.fullName || "Compte") + "</strong><span>" + esc(session.email || "") + "</span></div>" +
      CFG.menu.map(function (m) { return '<a href="' + m[0] + '">' + svg(m[1]) + esc(m[2]) + "</a>"; }).join("") +
      (global.HireLinkHelp && session.role !== "ADMINISTRATEUR" ? '<button type="button" data-help>' + svg("help") + "Aide</button>" : "") +
      '<button type="button" data-logout>' + svg("logout") + "Déconnexion</button>";
    prof.addEventListener("click", function (e) { e.stopPropagation(); togglePop(mp, prof); });
    var helpBtn = mp.querySelector("[data-help]");
    if (helpBtn) helpBtn.addEventListener("click", function () { togglePop(mp, prof, false); HireLinkHelp.openHelp(session.role); });
    mp.querySelector("[data-logout]").addEventListener("click", function () { togglePop(mp, prof, false); logout(); });
    Object.keys(pops).forEach(function (k) { pops[k].addEventListener("click", function (e) { e.stopPropagation(); }); });
    document.addEventListener("click", function () { Object.keys(pops).forEach(function (k) { pops[k].classList.remove("is-open"); }); });
    document.addEventListener("keydown", function (e) { if (e.key === "Escape") Object.keys(pops).forEach(function (k) { pops[k].classList.remove("is-open"); }); });
    window.addEventListener("resize", function () { Object.keys(pops).forEach(function (k) { pops[k].classList.remove("is-open"); }); });
  }

  function logout() {
    HireLink.openModal({ title: "Se déconnecter ?", description: "Vous devrez vous reconnecter pour accéder à votre espace.", confirmLabel: "Déconnexion", danger: true }).then(function (ok) {
      if (!ok) return;
      cacheClear("");
      HireLinkAPI.logout().then(function () { location.href = "../auth.html?mode=login"; });
    });
  }

  /** Le plan peut changer côté serveur (administration, propriétaire) : on resynchronise la couronne. */
  function syncPlan() {
    HireLinkAPI.api("/ai/credits").then(function (c) {
      var before = HireLinkAPI.isPremium();
      HireLinkAPI.setPlanTier(c.plan);
      if (before !== HireLinkAPI.isPremium()) {
        var crown = document.querySelector(".rc-crown");
        if (crown) {
          crown.classList.toggle("is-on", HireLinkAPI.isPremium());
          crown.querySelector("span").textContent = HireLinkAPI.isPremium() ? "Premium" : "Passer Premium";
        }
      }
    }).catch(function () {});
  }

  /* ----------------------------------------------------------------- mount */
  function mount(opts) {
    opts = opts || {};
    var want = document.body.getAttribute("data-role") || "RECRUTEUR";
    session = HireLinkAPI.getSession();
    if (want === "ANY" && session) want = session.role;
    if (!session || !HireLinkAPI.getToken() || session.role !== want) {
      HireLinkAPI.requireAuth([want]);
      return null;
    }
    CFG = ROLES[want];
    document.body.classList.add("rc");
    if (want === "RECRUTEUR") orgInfo = cacheGet("org");
    buildSidebar(opts.active || document.body.getAttribute("data-active"));
    buildTopbar(opts);
    document.documentElement.classList.remove("rc-pending");
    if (opts.title) document.title = opts.title + " — HireLink";

    if (want === "RECRUTEUR") data.org(function (o) { orgInfo = o; paintOrg(); document.dispatchEvent(new CustomEvent("rc:org", { detail: o })); });
    if (!CFG.noBell) {
      pullNotifs();
      setInterval(pullNotifs, 45000);
      document.addEventListener("visibilitychange", function () { if (!document.hidden) pullNotifs(); });
    }
    window.addEventListener("storage", function (e) { if (String(e.key || "").indexOf("hirelink.photo.") === 0) paintProfile(); });
    if (CFG.premiumKey) syncPlan();
    return session;
  }

  /** Charge une fonction page : exécute run(), capture toute erreur synchrone dans l'élément cible. */
  global.HireLinkApp = {
    mount: mount, setActive: setActive, STATUS_FR: STATUS_FR, ACTIVE: ACTIVE, INTERVIEW_TYPE_FR: INTERVIEW_TYPE_FR, ROLE_FR: ROLE_FR,
    esc: esc, initials: initials, fmtDate: fmtDate, fmtDateTime: fmtDateTime, ago: ago, plural: plural, debounce: debounce,
    svg: svg, data: data, swr: swr, cacheGet: cacheGet, cacheSet: cacheSet, cacheClear: cacheClear,
    skeleton: skeleton, empty: empty, errorState: errorState, toast: toast, busy: busy,
    isHM: isHM, isOwner: isOwner, canManage: canManage, getOrg: function () { return orgInfo; }, getSession: function () { return session; },
    notifications: { target: notifTarget, all: function () { return notifRows; }, markRead: markRead, markAllRead: markAllRead, refresh: pullNotifs },
    paintProfile: paintProfile, logout: logout,
  };
})(window);
