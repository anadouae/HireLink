const API = process.env.HL_API || "http://127.0.0.1:28081/api/v1";

async function solveCaptcha() {
  for (let i = 0; i < 8; i++) {
    const res = await fetch(API + "/auth/captcha", {
      method: "POST",
      headers: { "Content-Type": "application/json", "X-HireLink-CSRF": "1" },
      body: "{}",
    });
    const cap = await res.json();
    if (cap.kind === "question" && cap.question) {
      const m = String(cap.question).match(/Combien font (\d+)\s*([+−\-])\s*(\d+)/);
      if (!m) continue;
      const a = Number(m[1]);
      const b = Number(m[3]);
      return { token: cap.token, answer: m[2] === "+" ? String(a + b) : String(a - b) };
    }
    const texts = [...String(cap.svg || "").matchAll(/<text[^>]*\sx="(\d+)"[^>]*>([^<]+)<\/text>/g)]
      .map((m) => ({ x: Number(m[1]), ch: m[2] }))
      .sort((a, b) => a.x - b.x);
    const ans = texts.map((t) => t.ch).join("");
    if (ans) return { token: cap.token, answer: ans };
  }
  throw new Error("captcha");
}

async function login(email, password) {
  const c = await solveCaptcha();
  const res = await fetch(API + "/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-HireLink-CSRF": "1" },
    body: JSON.stringify({
      email,
      password,
      captchaToken: c.token,
      captchaAnswer: c.answer,
    }),
  });
  const body = await res.json().catch(() => ({}));
  console.log(
    email,
    "status=" + res.status,
    "role=" + (body.role || "-"),
    "mfa=" + !!body.mfaRequired,
    "token=" + !!body.accessToken
  );
  return { status: res.status, body };
}

const accounts = [
  ["candidat@hirelink.ma", "Candidat123!"],
  ["n.bennani@atlasdigital.ma", "Recruteur123!"],
  ["recruteur@hirelink.ma", "Recruteur123!"],
  ["admin@hirelink.ma", "Admin123!"],
];

for (const [e, p] of accounts) {
  await login(e, p);
}
