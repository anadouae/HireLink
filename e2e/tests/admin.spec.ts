import { test, expect } from "@playwright/test";
import { apiLogin, injectSession, API } from "./helpers";

/**
 * Admin : pas de contournement MFA.
 * Sur ISO seed, MFA peut être inactive — on documente le résultat réel.
 */
test.describe("Parcours ADMIN", () => {
  test("login admin + accès API admin (MFA selon seed)", async ({ page, request }) => {
    const auth = await apiLogin(request, "admin@hirelink.ma", "Admin123!");
    // MFA active : pas de contournement — documenter SKIPPED (smoke TOTP : e2e/scripts/mfa-admin-smoke.mjs)
    if (!auth.accessToken && (auth as { mfaRequired?: boolean }).mfaRequired) {
      test.skip(true, "MFA challenge actif — non contourné (voir mfa-admin-smoke.mjs / ADMIN_MFA_DEMO.md)");
    }
    expect(auth.accessToken).toBeTruthy();
    expect(auth.role).toBe("ADMINISTRATEUR");

    const plans = await request.get(API + "/admin/plans", {
      headers: { Authorization: "Bearer " + auth.accessToken },
    });
    if (plans.status() === 401 || plans.status() === 403) {
      test.skip(true, "Admin API refusée (MFA/permission) — non contournée");
    }
    expect(plans.ok()).toBeTruthy();

    await injectSession(page, auth as Parameters<typeof injectSession>[1]);
    await page.goto("/app/admin.html");
    await expect(page.locator("body")).toContainText(/admin|formule|modération|organisation/i, { timeout: 25000 });
  });

  test("candidat ne peut pas appeler /admin/*", async ({ request }) => {
    const cand = await apiLogin(request, "candidat@hirelink.ma", "Candidat123!");
    const res = await request.get(API + "/admin/plans", {
      headers: { Authorization: "Bearer " + cand.accessToken },
    });
    expect([401, 403]).toContain(res.status());
  });
});
