import { test, expect } from "@playwright/test";
import { issueSolvedCaptcha, API } from "./helpers";

test.describe("Auth / CAPTCHA / session", () => {
  /**
   * Cause exacte Vague 7 / 7B : le scénario « forgot-password » échouait sur le *premier*
   * appel (pas sur la réutilisation) à cause du throttle `forgot:ip` (10/h) et/ou
   * `forgot:email` (3/h) — AuthThrottle en mémoire. Le CAPTCHA one-shot lui-même est sain
   * (CaptchaServiceTest + assertValid identique sur toutes les routes protégées).
   *
   * Ce test exerce la même API `captcha.assertValid` via `/auth/request-otp`
   * (limites otp plus hautes) pour vérifier l'usage unique sans désactiver le CAPTCHA.
   */
  test("CAPTCHA one-shot (request-otp)", async ({ request }) => {
    const solved = await issueSolvedCaptcha(request);
    const email = `captcha-oneshot-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@test.hirelink.local`;

    const first = await request.post(API + "/auth/request-otp", {
      data: {
        email,
        purpose: "REGISTER",
        captchaToken: solved.token,
        captchaAnswer: solved.answer,
      },
      headers: { "X-HireLink-CSRF": "1" },
    });
    expect(
      first.status(),
      `1er request-otp attendu 200 (CAPTCHA OK). Reçu ${first.status()} ${(await first.text()).slice(0, 200)}`
    ).toBe(200);

    const reuse = await request.post(API + "/auth/request-otp", {
      data: {
        email,
        purpose: "REGISTER",
        captchaToken: solved.token,
        captchaAnswer: solved.answer,
      },
      headers: { "X-HireLink-CSRF": "1" },
    });
    expect(
      reuse.status(),
      `réutilisation CAPTCHA attendue 428. Reçu ${reuse.status()} ${(await reuse.text()).slice(0, 200)}`
    ).toBe(428);
  });

  test("forgot-password exige un CAPTCHA frais (refus sans jeton)", async ({ request }) => {
    const noCap = await request.post(API + "/auth/forgot-password", {
      data: { email: `nocap-${Date.now()}@test.hirelink.local` },
      headers: { "X-HireLink-CSRF": "1" },
    });
    expect([400, 428]).toContain(noCap.status());
  });

  test("UI forgot affiche le contrôle anti-robot", async ({ page }) => {
    await page.goto("/auth.html?mode=login");
    await page.locator("#btn-forgot").click();
    await expect(page.locator("[data-captcha]:not([hidden])")).toBeVisible({ timeout: 15000 });
    await expect(page.getByText(/Contrôle anti-robot/i).first()).toBeVisible();
  });
});
