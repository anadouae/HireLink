/**
 * HireLink API client — JWT session in localStorage
 */
(function (global) {
  const TOKEN_KEY = "hirelink.token";
  const SESSION_KEY = "hirelink.session";

  function apiBase() {
    return global.HIRELINK_API_BASE || "http://localhost:8081/api/v1";
  }

  function getToken() {
    return localStorage.getItem(TOKEN_KEY);
  }

  function setAuth(auth) {
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
      })
    );
  }

  function clearAuth() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(SESSION_KEY);
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
    if (/^https?:\/\//i.test(s) || s.startsWith("data:")) return s;
    if (s.startsWith("/api/")) {
      const base = apiBase().replace(/\/api\/v1\/?$/, "");
      return base + s;
    }
    if (s.startsWith("/")) return apiBase().replace(/\/api\/v1\/?$/, "") + s;
    return s;
  }

  async function api(path, options) {
    const opts = options || {};
    const headers = Object.assign({}, opts.headers || {});
    if (!opts.formData) headers["Content-Type"] = "application/json";
    const token = getToken();
    if (token) headers.Authorization = "Bearer " + token;
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), opts.timeoutMs || 20000);
    let res;
    try {
      res = await fetch(apiBase() + path, {
        method: opts.method || "GET",
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

  global.HireLinkAPI = {
    get API_BASE() {
      return apiBase();
    },
    api,
    mediaUrl,
    setAuth,
    clearAuth,
    getSession,
    getToken,
    requireAuth,
    redirectForRole,
  };
})(window);
