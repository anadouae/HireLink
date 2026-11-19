const API = process.env.HL_API || "http://127.0.0.1:28081/api/v1";

async function login(email, password) {
  const res = await fetch(API + "/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-HireLink-CSRF": "1" },
    body: JSON.stringify({
      email,
      password,
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
