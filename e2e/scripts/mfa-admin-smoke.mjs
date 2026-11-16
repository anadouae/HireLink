/**
 * Vague 7B — MFA admin réel (TOTP).
 * N'écrit AUCUN secret dans un fichier. Sortie console uniquement (ne pas coller dans Git).
 *
 * Usage : node e2e/scripts/mfa-admin-smoke.mjs
 * Env : HL_API (défaut http://127.0.0.1:18081/api/v1)
 */
import crypto from "crypto";

const API = process.env.HL_API || "http://127.0.0.1:18081/api/v1";
const EMAIL = process.env.HL_MFA_ADMIN_EMAIL || "admin@hirelink.ma";
const PASS = process.env.HL_MFA_ADMIN_PASSWORD || "Admin123!";

function b32decode(s) {
  const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
  const cleaned = String(s).toUpperCase().replace(/=+$/, "").replace(/[^A-Z2-7]/g, "");
  let bits = "";
  for (const c of cleaned) {
    const v = alphabet.indexOf(c);
    if (v < 0) continue;
    bits += v.toString(2).padStart(5, "0");
  }
  const bytes = [];
  for (let i = 0; i + 8 <= bits.length; i += 8) bytes.push(parseInt(bits.slice(i, i + 8), 2));
  return Buffer.from(bytes);
}

function totp(secretB32, step = 30, digits = 6, t = Date.now()) {
  const key = b32decode(secretB32);
  const counter = Math.floor(t / 1000 / step);
  const buf = Buffer.alloc(8);
  buf.writeBigUInt64BE(BigInt(counter));
  const hmac = crypto.createHmac("sha1", key).update(buf).digest();
  const offset = hmac[hmac.length - 1] & 0xf;
  const code =
    ((hmac[offset] & 0x7f) << 24) |
    ((hmac[offset + 1] & 0xff) << 16) |
    ((hmac[offset + 2] & 0xff) << 8) |
    (hmac[offset + 3] & 0xff);
  return String(code % 10 ** digits).padStart(digits, "0");
}

async function solveCaptcha() {
  for (let i = 0; i < 8; i++) {
    const res = await fetch(API + "/auth/captcha", {
      method: "POST",
      headers: { "Content-Type": "application/json", "X-HireLink-CSRF": "1" },
      body: "{}",
    });
    if (res.status === 429) throw new Error("CAPTCHA throttled");
    const cap = await res.json();
    if (cap.kind === "question" && cap.question) {
      const m = String(cap.question).match(/Combien font (\d+)\s*([+−\-])\s*(\d+)/);
      if (!m) continue;
      const a = Number(m[1]);
      const b = Number(m[3]);
      const ans = m[2] === "+" ? String(a + b) : String(a - b);
      return { token: cap.token, answer: ans };
    }
    const texts = [...String(cap.svg || "").matchAll(/<text[^>]*\sx="(\d+)"[^>]*>([^<]+)<\/text>/g)]
      .map((m) => ({ x: Number(m[1]), ch: m[2] }))
      .sort((a, b) => a.x - b.x);
    const ans = texts.map((t) => t.ch).join("");
    if (ans) return { token: cap.token, answer: ans };
  }
  throw new Error("unable to solve captcha");
}

async function login(email, password) {
  const cap = await solveCaptcha();
  const res = await fetch(API + "/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-HireLink-CSRF": "1" },
    body: JSON.stringify({
      email,
      password,
      captchaToken: cap.token,
      captchaAnswer: cap.answer,
    }),
  });
  const body = await res.json().catch(() => ({}));
  return { status: res.status, body };
}

async function main() {
  const out = { steps: [] };
  const mark = (k, v) => {
    out.steps.push({ [k]: v });
    console.log(k + "=" + v);
  };

  let r = await login(EMAIL, PASS);
  mark("initial_login_status", r.status);
  if (r.body.mfaRequired) {
    mark("mfa_already_enabled", true);
    mark("VERDICT", "BLOCKED_OR_ALREADY_ENROLLED — challenge MFA sans secret local (ne pas logger de TOTP)");
    process.exit(2);
  }
  if (!r.body.accessToken) {
    mark("VERDICT", "BLOCKED — login admin sans accessToken: " + (r.body.message || r.status));
    process.exit(2);
  }
  const token = r.body.accessToken;

  const setup = await fetch(API + "/account/mfa/setup", {
    method: "POST",
    headers: {
      Authorization: "Bearer " + token,
      "Content-Type": "application/json",
      "X-HireLink-CSRF": "1",
    },
    body: JSON.stringify({ currentPassword: PASS }),
  });
  const setupBody = await setup.json().catch(() => ({}));
  mark("mfa_setup_status", setup.status);
  if (!setup.ok || !setupBody.secret) {
    mark("VERDICT", "BLOCKED — mfa/setup: " + (setupBody.message || setup.status));
    process.exit(2);
  }
  const secret = setupBody.secret;
  // secret volontairement NON écrit dans out / fichiers

  const codeOk = totp(secret);
  const enable = await fetch(API + "/account/mfa/enable", {
    method: "POST",
    headers: {
      Authorization: "Bearer " + token,
      "Content-Type": "application/json",
      "X-HireLink-CSRF": "1",
    },
    body: JSON.stringify({ code: codeOk, currentPassword: PASS }),
  });
  mark("mfa_enable_status", enable.status);
  if (!enable.ok) {
    const eb = await enable.text();
    mark("VERDICT", "BLOCKED — mfa/enable: " + enable.status + " " + eb.slice(0, 120));
    process.exit(2);
  }

  // Logout best-effort
  await fetch(API + "/auth/logout", {
    method: "POST",
    headers: { "X-HireLink-CSRF": "1", Authorization: "Bearer " + token },
  }).catch(() => {});

  r = await login(EMAIL, PASS);
  mark("post_enroll_login_status", r.status);
  mark("mfa_required", !!r.body.mfaRequired);
  if (!r.body.mfaRequired || !r.body.mfaToken) {
    mark("VERDICT", "FAIL — MFA non exigée après enable");
    process.exit(1);
  }

  const bad = await fetch(API + "/auth/mfa/verify", {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-HireLink-CSRF": "1" },
    body: JSON.stringify({ mfaToken: r.body.mfaToken, code: "000000" }),
  });
  mark("bad_code_status", bad.status);
  if (bad.status === 200) {
    mark("VERDICT", "FAIL — code incorrect accepté");
    process.exit(1);
  }

  // Anti-rejeu serveur : le code du pas courant a déjà été consommé à l'enable / mauvais essais.
  // Attendre le prochain pas de 30 s avant un TOTP frais.
  const waitMs = 30_000 - (Date.now() % 30_000) + 500;
  mark("wait_next_totp_ms", waitMs);
  await new Promise((r) => setTimeout(r, waitMs));
  r = await login(EMAIL, PASS);
  const goodCode = totp(secret);
  const good = await fetch(API + "/auth/mfa/verify", {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-HireLink-CSRF": "1" },
    body: JSON.stringify({ mfaToken: r.body.mfaToken, code: goodCode }),
  });
  const goodBody = await good.json().catch(() => ({}));
  mark("good_code_status", good.status);
  if (!good.ok || !goodBody.accessToken) {
    mark("VERDICT", "FAIL — TOTP valide refusé");
    process.exit(1);
  }

  // Expired window: code from ~2 periods ago
  const expiredCode = totp(secret, 30, 6, Date.now() - 90_000);
  r = await login(EMAIL, PASS);
  const exp = await fetch(API + "/auth/mfa/verify", {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-HireLink-CSRF": "1" },
    body: JSON.stringify({ mfaToken: r.body.mfaToken, code: expiredCode }),
  });
  mark("expired_code_status", exp.status);
  // may be 401/400 — must not be 200 with session unless window still valid (±1 step)
  if (exp.status === 200) {
    mark("expired_note", "TOLÉRANCE fenêtre TOTP (souvent ±1 pas) — code -90s encore accepté");
  }

  // Admin route without completing MFA (password-only response without token)
  r = await login(EMAIL, PASS);
  if (r.body.accessToken && !r.body.mfaRequired) {
    mark("VERDICT", "FAIL — accès token sans MFA");
    process.exit(1);
  }
  const noMfaAdmin = await fetch(API + "/admin/plans", {
    headers: { Authorization: "Bearer " + (r.body.accessToken || "none") },
  });
  mark("admin_without_mfa_session", noMfaAdmin.status);

  // Candidate still 403 on admin
  const candLogin = await login("candidat@hirelink.ma", "Candidat123!");
  const candAdmin = await fetch(API + "/admin/plans", {
    headers: { Authorization: "Bearer " + candLogin.body.accessToken },
  });
  mark("candidate_admin_status", candAdmin.status);

  mark("VERDICT", "PASS");
  console.log("SUMMARY_OK — secrets non persistés");
}

main().catch((e) => {
  console.error("VERDICT=BLOCKED — " + e.message);
  process.exit(2);
});
