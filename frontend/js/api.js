/**
 * HireLink API client — JWT session in localStorage
 */
(function (global) {
  const TOKEN_KEY = "hirelink.token";
  const SESSION_KEY = "hirelink.session";

  function apiBase() {
    if (global.HIRELINK_API_BASE) return global.HIRELINK_API_BASE;
    // même nom d'hôte que la page (cookie de session SameSite=Strict) ; repli sur localhost (fichier ouvert directement)
    var h = global.location && /^https?:$/.test(global.location.protocol) && global.location.hostname ? global.location.hostname : "localhost";
    return "http://" + h + ":8081/api/v1";
  }

  // Jeton d'accès (15 min) renouvelé en silence grâce au cookie HttpOnly de rafraîchissement.
  // En-tête X-HireLink-CSRF : protection CSRF de /auth/refresh et /auth/logout (un formulaire externe ne peut pas l'envoyer).
  let refreshing = null;
  function refreshAccess() {
    if (refreshing) return refreshing;
    refreshing = fetch(apiBase() + "/auth/refresh", {
      method: "POST",
      credentials: "include",
      headers: { "X-HireLink-CSRF": "1" },
    })
      .then(async (res) => {
        if (!res.ok) return false;
        const auth = await res.json();
        if (!auth || !auth.accessToken) return false;
        localStorage.setItem(TOKEN_KEY, auth.accessToken);
        return true;
      })
      .catch(() => false)
      .finally(() => {
        refreshing = null;
      });
    return refreshing;
  }

  function goToLogin() {
    clearAuth();
    const prefix = location.pathname.includes("/app/") ? "../" : "";
    const here = location.pathname.split("/").pop() + location.search;
    location.replace(prefix + "auth.html?mode=login&expired=1&next=" + encodeURIComponent(here));
    return new Promise(function () {});
  }

  /** fetch authentifié (téléversements de fichiers, etc.) : ajoute le jeton, tente un renouvellement sur 401. */
  async function authFetch(path, init) {
    const run = () => {
      const o = Object.assign({}, init || {});
      o.headers = Object.assign({}, o.headers || {});
      const t = getToken();
      if (t) o.headers.Authorization = "Bearer " + t;
      o.credentials = "include";
      return fetch(path.indexOf("http") === 0 ? path : apiBase() + path, o);
    };
    let res = await run();
    if (res.status === 401 && getToken() && (await refreshAccess())) res = await run();
    if (res.status === 401 && getToken()) return goToLogin();
    return res;
  }

  /** Déconnexion : révoque la session côté serveur (cookie + jeton), puis nettoie le navigateur. */
  async function logout() {
    try {
      await fetch(apiBase() + "/auth/logout", {
        method: "POST",
        credentials: "include",
        headers: { "X-HireLink-CSRF": "1" },
      });
    } catch (_) {
      /* hors ligne : on nettoie quand même */
    }
    clearAuth();
  }

  function getToken() {
    return localStorage.getItem(TOKEN_KEY);
  }

  function setAuth(auth) {
    if (!auth || !auth.accessToken) return; // défi MFA ou enrôlement : pas encore de session
    localStorage.setItem(TOKEN_KEY, auth.accessToken);
    localStorage.setItem(
      SESSION_KEY,
      JSON.stringify({
        userId: auth.userId,
        email: auth.email,
        fullName: auth.fullName,
        role: auth.role,
        mustSetPassword: auth.mustSetPassword,
        emailVerified: auth.emailVerified,
        planTier: auth.planTier || null,
        mfaEnabled: !!auth.mfaEnabled,
      })
    );
  }

  function clearAuth() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(SESSION_KEY);
    document.dispatchEvent(new Event("hirelink:signout"));
  }

  function getSession() {
    try {
      return JSON.parse(localStorage.getItem(SESSION_KEY) || "null");
    } catch {
      return null;
    }
  }

  function mediaUrl(path) {
    if (!path) return "";
    const s = String(path);
    // Services de favicon tiers : requêtes inutiles (404 pour les domaines inconnus) → on affiche l'initiale
    if (/google\.com\/s2\/favicons|gstatic\.com\/faviconV2/i.test(s)) return "";
    if (/^https?:\/\//i.test(s) || s.startsWith("data:")) return s;
    if (s.startsWith("/api/")) {
      const base = apiBase().replace(/\/api\/v1\/?$/, "");
      return base + s;
    }
    if (s.startsWith("/")) return apiBase().replace(/\/api\/v1\/?$/, "") + s;
    return s;
  }

  // Les GET identiques lancés en même temps (ex. cloche + page) partagent une seule requête réseau.
  const inflight = new Map();
  function api(path, options) {
    const opts = options || {};
    if (opts.method && opts.method !== "GET") return request(path, opts);
    if (opts.formData || opts.noDedupe) return request(path, opts);
    const key = (getToken() || "") + path;
    if (inflight.has(key)) return inflight.get(key).then((d) => (d && typeof d === "object" ? structuredClone(d) : d));
    const p = request(path, opts).finally(() => inflight.delete(key));
    inflight.set(key, p);
    return p.then((d) => (d && typeof d === "object" ? structuredClone(d) : d));
  }

  async function request(path, options, retried) {
    const opts = options || {};
    const headers = Object.assign({}, opts.headers || {});
    if (!opts.formData) headers["Content-Type"] = "application/json";
    const token = getToken();
    if (token) headers.Authorization = "Bearer " + token;
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), opts.timeoutMs || 20000);
    if (path.indexOf("/auth/") === 0 && (opts.method || "GET") !== "GET") headers["X-HireLink-CSRF"] = "1";
    let res;
    try {
      res = await fetch(apiBase() + path, {
        method: opts.method || "GET",
        credentials: "include",
        headers,
        body: opts.formData
          ? opts.formData
          : opts.body
            ? JSON.stringify(opts.body)
            : undefined,
        signal: controller.signal,
      });
    } catch (e) {
      clearTimeout(timer);
      throw new Error(e.name === "AbortError" ? "Délai dépassé" : "Réseau indisponible");
    }
    clearTimeout(timer);
    const text = await res.text();
    let data = null;
    try {
      data = text ? JSON.parse(text) : null;
    } catch {
      data = { message: text };
    }
    if (!res.ok) {
      // Session expirée / jeton invalide : retour propre à la connexion (hors endpoints d'auth)
      if (res.status === 401 && token && path.indexOf("/auth/") !== 0) {
        if (!retried && (await refreshAccess())) return request(path, options, true);
        return goToLogin();
      }
      const msg =
        (data && (data.message || data.error || data.detail)) ||
        "Erreur HTTP " + res.status;
      const err = new Error(typeof msg === "string" ? msg : JSON.stringify(msg));
      err.status = res.status;
      err.data = data;
      throw err;
    }
    return data;
  }

  function redirectForRole(role) {
    if (role === "ADMINISTRATEUR") return "app/admin.html";
    if (role === "RECRUTEUR") return "app/recruiter.html";
    return "app/candidate-dashboard.html";
  }

  function requireAuth(roles) {
    const s = getSession();
    const t = getToken();
    const prefix = location.pathname.includes("/app/") ? "../" : "";
    if (!s || !t) {
      location.href = prefix + "auth.html?mode=login";
      return null;
    }
    const skipped = localStorage.getItem("hirelink.pw.skip") === "1";
    if (
      s.mustSetPassword &&
      !skipped &&
      !location.pathname.includes("set-password")
    ) {
      location.href = prefix + "set-password.html";
      return null;
    }
    if (roles && roles.length && !roles.includes(s.role)) {
      const target = redirectForRole(s.role);
      location.href = location.pathname.includes("/app/")
        ? target.replace("app/", "")
        : target;
      return null;
    }
    return s;
  }

  /** Le plan vient du serveur (connexion, abonnement, /ai/credits) ; jamais d'un indicateur local. */
  function setPlanTier(tier) {
    const s = getSession();
    if (!s) return;
    s.planTier = tier || null;
    localStorage.setItem(SESSION_KEY, JSON.stringify(s));
  }
  function isPremium() {
    const s = getSession();
    return !!s && (s.planTier === "PREMIUM" || s.planTier === "PERFORMANCE");
  }

  /** Télécharge un fichier protégé (JWT) puis l'ouvre dans un nouvel onglet. */
  async function openFile(path) {
    const res = await authFetch(path);
    if (!res.ok) {
      let msg = "Fichier introuvable";
      try { msg = (await res.json()).message || msg; } catch (_) {}
      throw new Error(msg);
    }
    const url = URL.createObjectURL(await res.blob());
    window.open(url, "_blank");
    setTimeout(() => URL.revokeObjectURL(url), 60000);
  }

  global.HireLinkAPI = {
    get API_BASE() {
      return apiBase();
    },
    api,
    openFile,
    authFetch,
    logout,
    setPlanTier,
    isPremium,
    mediaUrl,
    setAuth,
    clearAuth,
    getSession,
    getToken,
    requireAuth,
    redirectForRole,
  };
})(window);
