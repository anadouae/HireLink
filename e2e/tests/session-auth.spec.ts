import { test, expect } from "@playwright/test";
import { apiLogin, API } from "./helpers";

/**
 * Vague 7B — sessions : 403 ≠ déconnexion ; 401 = réauth ; réseau conservé (côté FE).
 */
test.describe("Sessions & autorisations", () => {
  test("403 d'autorisation ne révoque pas la session candidat", async ({ request }) => {
    const cand = await apiLogin(request, "candidat@hirelink.ma", "Candidat123!");
    const forbidden = await request.get(API + "/admin/plans", {
      headers: { Authorization: "Bearer " + cand.accessToken },
    });
    expect([401, 403]).toContain(forbidden.status());

    const mine = await request.get(API + "/applications/mine", {
      headers: { Authorization: "Bearer " + cand.accessToken },
    });
    expect(mine.ok(), `session encore valide après 403 — reçu ${mine.status()}`).toBeTruthy();
  });

  test("jeton inventé → 401 (réauthentification requise)", async ({ request }) => {
    const res = await request.get(API + "/applications/mine", {
      headers: { Authorization: "Bearer totally.invalid.token" },
    });
    expect(res.status()).toBe(401);
  });

  test("recruteur ne lit pas une route admin", async ({ request }) => {
    const rec = await apiLogin(request, "n.bennani@atlasdigital.ma", "Recruteur123!");
    const res = await request.get(API + "/admin/plans", {
      headers: { Authorization: "Bearer " + rec.accessToken },
    });
    expect([401, 403]).toContain(res.status());
    const offers = await request.get(API + "/offers/mine", {
      headers: { Authorization: "Bearer " + rec.accessToken },
    });
    expect(offers.ok()).toBeTruthy();
  });
});
