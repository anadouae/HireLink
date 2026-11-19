import { APIRequestContext, Page, expect } from "@playwright/test";

export const API = process.env.HL_API || "http://127.0.0.1:18081/api/v1";

export async function apiLogin(
  request: APIRequestContext,
  email: string,
  password: string
) {
  const res = await request.post(API + "/auth/login", {
    data: { email, password },
    headers: { "X-HireLink-CSRF": "1" },
  });
  const body = await res.json();
  // MFA challenge : 200 sans accessToken — ne pas traiter comme erreur fatale
  if (res.status() === 200 && (body.mfaRequired || body.mfaEnrollRequired) && !body.accessToken) {
    return body as {
      accessToken?: string;
      role?: string;
      userId?: number;
      email?: string;
      mfaRequired?: boolean;
      mfaToken?: string;
      mfaEnrollRequired?: boolean;
    };
  }
  if (res.status() !== 200 || !body.accessToken) {
    throw new Error(`login ${email} → ${res.status()} ${body.message || ""}`);
  }
  return body as { accessToken: string; role: string; userId: number; email: string; fullName?: string; planTier?: string };
}

/** Injecte une session fraîche (jamais un état navigateur préexistant). */
export async function injectSession(page: Page, auth: {
  accessToken: string;
  role: string;
  userId: number;
  email: string;
  fullName?: string;
  planTier?: string;
}) {
  await page.addInitScript((a) => {
    (window as unknown as { HIRELINK_API_BASE?: string }).HIRELINK_API_BASE =
      "http://127.0.0.1:18081/api/v1";
  }, auth);
  await page.goto("/auth.html");
  await page.evaluate((a) => {
    localStorage.setItem("hirelink.token", a.accessToken);
    localStorage.setItem(
      "hirelink.session",
      JSON.stringify({
        userId: a.userId,
        email: a.email,
        fullName: a.fullName || "",
        role: a.role,
        planTier: a.planTier || null,
        emailVerified: true,
        mfaEnabled: false,
      })
    );
    try {
      sessionStorage.setItem("hirelink.api", "http://127.0.0.1:18081/api/v1");
    } catch {
      /* ignore */
    }
  }, auth);
}

export async function expectNoDemoLeak(page: Page) {
  const apiBase = await page.evaluate(() => {
    return (window as unknown as { HIRELINK_API_BASE?: string }).HIRELINK_API_BASE
      || localStorage.getItem("hirelink.api")
      || "";
  });
  expect(apiBase).not.toContain(":8081");
}
