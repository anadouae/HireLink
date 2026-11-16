/**
 * Génère HireLink.postman_collection.json (Postman Collection v2.1)
 * à partir des endpoints Spring réellement implémentés.
 * Exécution : node postman/scripts/build-collection.mjs
 */
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const out = path.join(__dirname, "..", "HireLink.postman_collection.json");

const CAPTCHA_PRE = `const base = pm.environment.get("baseUrl");
const csrf = [
  { key: "X-HireLink-CSRF", value: "1" },
  { key: "Content-Type", value: "application/json" },
  { key: "Accept", value: "application/json" }
];
function solve(cap) {
  if (cap.kind === "question" && cap.question) {
    const m = String(cap.question).match(/Combien font (\\d+)\\s*([+\\-−])\\s*(\\d+)/);
    if (!m) throw new Error("CAPTCHA question illisible");
    const a = Number(m[1]), b = Number(m[3]);
    return m[2] === "+" ? String(a + b) : String(a - b);
  }
  const texts = [...String(cap.svg || "").matchAll(/<text[^>]*\\sx="(\\d+)"[^>]*>([^<]+)<\\/text>/g)]
    .map((x) => ({ x: Number(x[1]), ch: x[2] }))
    .sort((a, b) => a.x - b.x);
  const ans = texts.map((t) => t.ch).join("");
  if (!ans) throw new Error("CAPTCHA image vide");
  return ans;
}
// Newman attend la fin de pm.sendRequest avant la requête principale.
pm.sendRequest({
  url: base + "/auth/captcha",
  method: "POST",
  header: csrf,
  body: { mode: "raw", raw: "{}" }
}, function (err, res) {
  if (err) throw err;
  if (res.code === 429) throw new Error("CAPTCHA throttled (429)");
  const cap = res.json();
  pm.environment.set("captchaToken", cap.token);
  pm.environment.set("captchaAnswer", solve(cap));
});`;

function hdrBearer(varName) {
  return [
    { key: "Authorization", value: `Bearer {{${varName}}}`, type: "text" },
    { key: "Accept", value: "application/json", type: "text" },
    { key: "X-HireLink-CSRF", value: "1", type: "text" },
  ];
}
function hdrPublic() {
  return [
    { key: "Accept", value: "application/json", type: "text" },
    { key: "X-HireLink-CSRF", value: "1", type: "text" },
    { key: "Content-Type", value: "application/json", type: "text" },
  ];
}

function req(name, method, pathTpl, opts = {}) {
  const { body, authVar, tests = [], description = "", preRequest, query, disableCookies } = opts;
  const headers = authVar ? hdrBearer(authVar) : hdrPublic();
  if (body && !headers.find((h) => h.key === "Content-Type")) {
    headers.push({ key: "Content-Type", value: "application/json", type: "text" });
  }
  const item = {
    name,
    request: {
      method,
      header: headers,
      url: {
        raw: `{{baseUrl}}${pathTpl.split("?")[0]}${query ? "?" + query.map((q) => q.key + "=" + q.value).join("&") : ""}`,
        host: ["{{baseUrl}}"],
        path: pathTpl.replace(/^\//, "").split("?")[0].split("/").filter(Boolean),
      },
      description,
    },
    event: [],
  };
  if (disableCookies) {
    item.protocolProfileBehavior = { disableCookies: true };
  }
  if (query) item.request.url.query = query.map((q) => ({ key: q.key, value: q.value }));
  if (body !== undefined) {
    item.request.body = { mode: "raw", raw: typeof body === "string" ? body : JSON.stringify(body, null, 2) };
  }
  const events = [];
  if (preRequest) {
    events.push({ listen: "prerequest", script: { type: "text/javascript", exec: preRequest.split("\n") } });
  }
  if (tests.length) {
    events.push({
      listen: "test",
      script: {
        type: "text/javascript",
        exec: tests.flatMap((t) => (Array.isArray(t) ? t : [t])),
      },
    });
  }
  item.event = events;
  return item;
}

const statusTest = (code) =>
  `pm.test("HTTP ${code}", function () { pm.response.to.have.status(${code}); });`;

const jsonOk = `pm.test("JSON parseable", function () { pm.response.to.be.json; });`;

const collection = {
  info: {
    name: "HireLink API — ISO",
    description:
      "Suite API professionnelle HireLink (endpoints Spring réels uniquement).\n\n" +
      "**Cible :** instance ISO (`baseUrl`, défaut http://127.0.0.1:18081/api/v1).\n" +
      "**Interdit :** base démo `hirelink` / port 8081 / JAR F3C2.\n" +
      "**Auth :** CAPTCHA one-shot résolu côté client (pas de bypass). OTP / MFA / reset token = manuels.\n" +
      "**Comptes seed :** documentés dans DataSeeder (mots de passe via variables d'environnement Newman).",
    schema: "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
  },
  variable: [
    { key: "baseUrl", value: "http://127.0.0.1:18081/api/v1" },
  ],
  item: [
    {
      name: "0. Prérequis & Santé",
      description: "Vérifie que l'API ISO répond. Aucune auth.",
      item: [
        req("Health", "GET", "/health", {
          description: "GET /api/v1/health — public",
          tests: [
            statusTest(200),
            jsonOk,
            `pm.test("status UP", () => pm.expect(pm.response.json().status).to.eql("UP"));`,
            `pm.test("pas de fuite 8081", () => pm.expect(pm.environment.get("baseUrl")).to.not.include(":8081"));`,
          ],
        }),
        req("Auth config", "GET", "/auth/config", {
          description: "GET /api/v1/auth/config — méthodes auth, legal versions",
          tests: [
            statusTest(200),
            jsonOk,
            `pm.test("methods présentes", () => pm.expect(pm.response.json().methods).to.be.an("array").that.includes("password"));`,
          ],
        }),
        req("Legal versions", "GET", "/auth/legal", {
          tests: [statusTest(200), jsonOk],
        }),
      ],
    },
    {
      name: "1. Authentification et sécurité",
      description:
        "CAPTCHA obligatoire pour login/forgot. Admin MFA : assert challenge sans jeton. Pas de contournement OTP/MFA.",
      item: [
        req("CAPTCHA challenge", "POST", "/auth/captcha", {
          body: {},
          description: "POST /auth/captcha — épreuve serveur one-shot",
          tests: [
            statusTest(200),
            jsonOk,
            `pm.test("token captcha", () => { const j = pm.response.json(); pm.expect(j.token).to.be.a("string").that.is.not.empty; pm.environment.set("captchaToken", j.token); });`,
          ],
        }),
        req("Login candidat (CAPTCHA)", "POST", "/auth/login", {
          preRequest: CAPTCHA_PRE,
          body: {
            email: "{{candidateEmail}}",
            password: "{{candidatePassword}}",
            captchaToken: "{{captchaToken}}",
            captchaAnswer: "{{captchaAnswer}}",
          },
          description: "POST /auth/login — rôle CANDIDAT seed. CAPTCHA résolu en prerequest.",
          tests: [
            statusTest(200),
            jsonOk,
            `pm.test("accessToken candidat", () => { const j = pm.response.json(); pm.expect(j.accessToken).to.be.a("string").that.is.not.empty; pm.expect(j.role).to.eql("CANDIDAT"); pm.environment.set("candidateToken", j.accessToken); pm.environment.set("candidateUserId", String(j.userId)); });`,
          ],
        }),
        req("Login recruteur (CAPTCHA)", "POST", "/auth/login", {
          preRequest: CAPTCHA_PRE,
          body: {
            email: "{{recruiterEmail}}",
            password: "{{recruiterPassword}}",
            captchaToken: "{{captchaToken}}",
            captchaAnswer: "{{captchaAnswer}}",
          },
          tests: [
            statusTest(200),
            `pm.test("accessToken recruteur", () => { const j = pm.response.json(); pm.expect(j.accessToken).to.be.a("string").that.is.not.empty; pm.expect(j.role).to.eql("RECRUTEUR"); pm.environment.set("recruiterToken", j.accessToken); });`,
          ],
        }),
        req("Login admin → MFA challenge (pas de bypass)", "POST", "/auth/login", {
          preRequest: CAPTCHA_PRE,
          body: {
            email: "{{adminEmail}}",
            password: "{{adminPassword}}",
            captchaToken: "{{captchaToken}}",
            captchaAnswer: "{{captchaAnswer}}",
          },
          description:
            "Si MFA admin activé : 200 + mfaRequired sans accessToken. Ne pas appeler /mfa/verify sans TOTP manuel.",
          tests: [
            statusTest(200),
            `pm.test("MFA requis OU token (selon état seed)", () => {
  const j = pm.response.json();
  if (j.mfaRequired) {
    pm.expect(j.accessToken).to.not.ok;
    pm.expect(j.mfaToken).to.be.a("string");
    pm.environment.set("adminMfaToken", j.mfaToken);
    pm.environment.unset("adminToken");
  } else if (j.accessToken) {
    pm.environment.set("adminToken", j.accessToken);
  } else {
    throw new Error("Réponse login admin inattendue");
  }
});`,
          ],
        }),
        req("Login mot de passe invalide", "POST", "/auth/login", {
          preRequest: CAPTCHA_PRE,
          body: {
            email: "{{candidateEmail}}",
            password: "WrongPassword-NotSeed-000!",
            captchaToken: "{{captchaToken}}",
            captchaAnswer: "{{captchaAnswer}}",
          },
          tests: [
            `pm.test("refus 401/400/403", () => pm.expect([400,401,403]).to.include(pm.response.code));`,
          ],
        }),
        req("GET /me sans jeton → 401", "GET", "/me", {
          description: "Endpoint authentifié sans Authorization",
          tests: [`pm.test("401", () => pm.expect(pm.response.code).to.eql(401));`],
        }),
        req("Refresh sans cookie → 401", "POST", "/auth/refresh", {
          body: {},
          disableCookies: true,
          description:
            "Cookies désactivés sur cette requête (jar Newman sinon réutilise le refresh des logins précédents).",
          tests: [`pm.test("401", () => pm.expect(pm.response.code).to.eql(401));`],
        }),
        req("Admin stats avec jeton candidat → 403", "GET", "/admin/stats", {
          authVar: "candidateToken",
          description: "hasRole(ADMINISTRATEUR) — candidat refusé",
          tests: [`pm.test("403", () => pm.expect(pm.response.code).to.eql(403));`],
        }),
      ],
    },
    {
      name: "2. Profil candidat et CV",
      description: "Rôle CANDIDAT. Upload CV multipart = manuel / hors Newman par défaut.",
      item: [
        req("GET /me", "GET", "/me", {
          authVar: "candidateToken",
          tests: [
            statusTest(200),
            `pm.test("email / rôle", () => { const j = pm.response.json(); pm.expect(j.email || j.user?.email).to.be.ok; });`,
          ],
        }),
        req("GET /me/career", "GET", "/me/career", {
          authVar: "candidateToken",
          tests: [statusTest(200), jsonOk],
        }),
        req("GET /me/cv-quality", "GET", "/me/cv-quality", {
          authVar: "candidateToken",
          tests: [statusTest(200), jsonOk],
        }),
        req("GET /account/profile", "GET", "/account/profile", {
          authVar: "candidateToken",
          tests: [statusTest(200), jsonOk],
        }),
        req("GET /account/security", "GET", "/account/security", {
          authVar: "candidateToken",
          tests: [statusTest(200), jsonOk],
        }),
        req("GET /account/sessions", "GET", "/account/sessions", {
          authVar: "candidateToken",
          tests: [
            statusTest(200),
            `pm.test("liste sessions", () => pm.expect(pm.response.json()).to.be.an("array"));`,
          ],
        }),
        req("GET /me/cv (peut 404 si aucun CV)", "GET", "/me/cv", {
          authVar: "candidateToken",
          description: "200 si CV présent, 404 sinon — les deux acceptés.",
          tests: [
            `pm.test("200 ou 404", () => pm.expect([200,404]).to.include(pm.response.code));`,
          ],
        }),
      ],
    },
    {
      name: "3. Recherche et catalogue",
      description: "Endpoints publics catalogue (SecurityConfig permitAll GET).",
      item: [
        req("GET /catalog/offers", "GET", "/catalog/offers", {
          query: [
            { key: "q", value: "java" },
            { key: "page", value: "0" },
            { key: "size", value: "10" },
          ],
          tests: [
            statusTest(200),
            jsonOk,
            `pm.test("contenu catalogue", () => { const j = pm.response.json(); pm.expect(j).to.be.ok; });`,
          ],
        }),
        req("GET /catalog/suggest", "GET", "/catalog/suggest", {
          query: [{ key: "q", value: "dev" }],
          tests: [statusTest(200)],
        }),
        req("GET /catalog/places", "GET", "/catalog/places", {
          query: [{ key: "q", value: "tet" }],
          tests: [statusTest(200)],
        }),
        req("POST /catalog/reindex sans auth → 401", "POST", "/catalog/reindex", {
          body: {},
          description: "Reindex admin/auth — non public",
          tests: [`pm.test("401", () => pm.expect(pm.response.code).to.eql(401));`],
        }),
      ],
    },
    {
      name: "4. Offres et candidatures",
      item: [
        req("GET /offers/public", "GET", "/offers/public", {
          tests: [
            statusTest(200),
            `pm.test("tableau ou page", () => { const j = pm.response.json(); pm.expect(j).to.be.ok; });`,
          ],
        }),
        req("GET /external-offers/sources", "GET", "/external-offers/sources", {
          tests: [statusTest(200)],
        }),
        req("GET /applications/mine (candidat)", "GET", "/applications/mine", {
          authVar: "candidateToken",
          tests: [
            statusTest(200),
            `pm.test("liste", () => pm.expect(pm.response.json()).to.be.an("array"));`,
            `const apps = pm.response.json(); if (Array.isArray(apps) && apps.length) { const id = apps[0].id || apps[0].applicationId; if (id) pm.environment.set("sampleApplicationId", String(id)); }`,
          ],
        }),
        req("GET /offers/mine (recruteur)", "GET", "/offers/mine", {
          authVar: "recruiterToken",
          tests: [
            statusTest(200),
            `const offers = pm.response.json(); const list = Array.isArray(offers) ? offers : (offers.content || []); if (list.length) { pm.environment.set("sampleOfferId", String(list[0].id)); }`,
          ],
        }),
        req("GET /applications/pipeline (recruteur)", "GET", "/applications/pipeline", {
          authVar: "recruiterToken",
          tests: [statusTest(200)],
        }),
        req("GET /applications/pipeline (candidat) → 403 ou vide métier", "GET", "/applications/pipeline", {
          authVar: "candidateToken",
          description: "Pipeline recruteur : candidat doit être refusé (403) ou erreur métier.",
          tests: [
            `pm.test("refus candidat", () => pm.expect([401,403,400]).to.include(pm.response.code));`,
          ],
        }),
        req("POST candidature sans auth → 401", "POST", "/applications", {
          body: { offerId: 1 },
          tests: [`pm.test("401", () => pm.expect(pm.response.code).to.eql(401));`],
        }),
      ],
    },
    {
      name: "5. ATS et entretiens",
      description: "Lecture pipeline / ICS. Planification destructive = manuelle.",
      item: [
        req("GET /applications/pace (recruteur)", "GET", "/applications/pace", {
          authVar: "recruiterToken",
          tests: [statusTest(200), jsonOk],
        }),
        req("GET interview.ics (id pipeline ou 0)", "GET", "/applications/{{sampleApplicationId}}/interview.ics", {
          authVar: "recruiterToken",
          description:
            "Si aucune candidature candidat seed : sampleApplicationId=0 → 404/400 attendu. Pas de planification créée ici.",
          preRequest: `if (!pm.environment.get("sampleApplicationId")) { pm.environment.set("sampleApplicationId", "0"); }`,
          tests: [
            `pm.test("ics 200 ou erreur métier", () => pm.expect([200,400,403,404]).to.include(pm.response.code));`,
          ],
        }),
        req("GET timeline candidature (candidat)", "GET", "/applications/{{sampleApplicationId}}/timeline", {
          authVar: "candidateToken",
          preRequest: `if (!pm.environment.get("sampleApplicationId")) { pm.environment.set("sampleApplicationId", "0"); }`,
          tests: [
            `pm.test("200/403/404/400", () => pm.expect([200,400,403,404]).to.include(pm.response.code));`,
          ],
        }),
        req("GET /notifications (candidat)", "GET", "/notifications", {
          authVar: "candidateToken",
          tests: [statusTest(200)],
        }),
      ],
    },
    {
      name: "6. Organisations",
      item: [
        req("GET /organizations/public", "GET", "/organizations/public", {
          tests: [statusTest(200)],
        }),
        req("GET /organizations/mine (recruteur)", "GET", "/organizations/mine", {
          authVar: "recruiterToken",
          tests: [statusTest(200), jsonOk],
        }),
        req("GET /organizations/mine/members", "GET", "/organizations/mine/members", {
          authVar: "recruiterToken",
          tests: [statusTest(200)],
        }),
        req("GET /organizations/mine (candidat) → 403", "GET", "/organizations/mine", {
          authVar: "candidateToken",
          tests: [`pm.test("403", () => pm.expect(pm.response.code).to.eql(403));`],
        }),
        req("GET /follows (candidat)", "GET", "/follows", {
          authVar: "candidateToken",
          tests: [statusTest(200)],
        }),
      ],
    },
    {
      name: "7. Abonnements et crédits IA",
      description: "Lecture seule. subscribe / payments = manuels (effets facturation).",
      item: [
        req("GET /billing/plans (candidat)", "GET", "/billing/plans", {
          authVar: "candidateToken",
          tests: [
            statusTest(200),
            `pm.test("plans array", () => pm.expect(pm.response.json()).to.be.an("array"));`,
          ],
        }),
        req("GET /billing/quote (candidat → PERFORMANCE)", "GET", "/billing/quote", {
          authVar: "candidateToken",
          query: [
            { key: "cycle", value: "month" },
            { key: "plan", value: "PERFORMANCE" },
          ],
          description:
            "Contrat PaymentService : candidat payant = PERFORMANCE uniquement. " +
            "ESSENTIEL est une formule RECRUTEUR → 400 « Formule candidat payante : Performance uniquement. » (comportement métier normal, pas un bug).",
          tests: [
            statusTest(200),
            jsonOk,
            `pm.test("plan PERFORMANCE", () => pm.expect(pm.response.json().plan).to.eql("PERFORMANCE"));`,
          ],
        }),
        req("GET /billing/quote ESSENTIEL en candidat → 400 métier", "GET", "/billing/quote", {
          authVar: "candidateToken",
          query: [
            { key: "cycle", value: "month" },
            { key: "plan", value: "ESSENTIEL" },
          ],
          description: "Preuve du refus métier documenté (requête Postman initiale incorrecte pour le rôle).",
          tests: [
            statusTest(400),
            `pm.test("message Performance uniquement", () => pm.expect(String(pm.response.json().message || "")).to.match(/Performance uniquement/i));`,
          ],
        }),
        req("GET /billing/quote (recruteur → PROFESSIONNEL)", "GET", "/billing/quote", {
          authVar: "recruiterToken",
          query: [
            { key: "cycle", value: "month" },
            { key: "plan", value: "PROFESSIONNEL" },
          ],
          description:
            "Devis payant recruteur : PROFESSIONNEL ou ENTREPRISE. ESSENTIEL est catalogue gratuit → 400 si passé à /quote.",
          tests: [
            statusTest(200),
            `pm.test("plan PROFESSIONNEL", () => pm.expect(pm.response.json().plan).to.eql("PROFESSIONNEL"));`,
          ],
        }),
        req("GET /ai/credits (candidat)", "GET", "/ai/credits", {
          authVar: "candidateToken",
          tests: [statusTest(200), jsonOk],
        }),
        req("GET /billing/invoices", "GET", "/billing/invoices", {
          authVar: "candidateToken",
          tests: [statusTest(200)],
        }),
        req("GET /billing/plans sans auth → 401", "GET", "/billing/plans", {
          tests: [`pm.test("401", () => pm.expect(pm.response.code).to.eql(401));`],
        }),
      ],
    },
    {
      name: "8. Administration",
      description:
        "Sans TOTP admin : pas d'accès stats. Candidat/recruteur → 403. Endpoints admin mutateurs = manuels.",
      item: [
        req("GET /admin/stats sans token → 401", "GET", "/admin/stats", {
          tests: [`pm.test("401", () => pm.expect(pm.response.code).to.eql(401));`],
        }),
        req("GET /admin/stats recruteur → 403", "GET", "/admin/stats", {
          authVar: "recruiterToken",
          tests: [`pm.test("403", () => pm.expect(pm.response.code).to.eql(403));`],
        }),
        req("GET /admin/users candidat → 403", "GET", "/admin/users", {
          authVar: "candidateToken",
          tests: [`pm.test("403", () => pm.expect(pm.response.code).to.eql(403));`],
        }),
        req("GET /admin/promotions candidat → 403", "GET", "/admin/promotions", {
          authVar: "candidateToken",
          tests: [`pm.test("403", () => pm.expect(pm.response.code).to.eql(403));`],
        }),
        req("GET /admin/external-offers/status candidat → 403", "GET", "/admin/external-offers/status", {
          authVar: "candidateToken",
          tests: [`pm.test("403", () => pm.expect(pm.response.code).to.eql(403));`],
        }),
        req("GET /admin/stats si adminToken (optionnel MFA)", "GET", "/admin/stats", {
          authVar: "adminToken",
          description:
            "Une seule assertion exécutée selon présence de adminToken (MFA off → 200 ; MFA on → skip documenté). " +
            "Explique l'écart historique 75 définies / 74 exécutées quand deux pm.test étaient en branches exclusives.",
          tests: [
            `pm.test("admin stats selon MFA", () => {
  const tok = pm.environment.get("adminToken");
  if (!tok) {
    // MFA admin actif : pas de bypass TOTP — stats non automatisées
    pm.expect(pm.response.code).to.be.oneOf([401, 403]);
    return;
  }
  pm.expect(pm.response.code).to.eql(200);
});`,
          ],
        }),
      ],
    },
  ],
};

fs.writeFileSync(out, JSON.stringify(collection, null, 2), "utf8");

function countItems(items) {
  let n = 0;
  for (const it of items) {
    if (it.item) n += countItems(it.item);
    else n += 1;
  }
  return n;
}
function countTests(items) {
  let n = 0;
  for (const it of items) {
    if (it.item) n += countTests(it.item);
    else {
      const ev = (it.event || []).find((e) => e.listen === "test");
      if (ev) {
        const exec = ev.script.exec.join("\n");
        n += (exec.match(/pm\.test\(/g) || []).length;
      }
    }
  }
  return n;
}
console.log(JSON.stringify({ file: out, requests: countItems(collection.item), assertions: countTests(collection.item) }));
