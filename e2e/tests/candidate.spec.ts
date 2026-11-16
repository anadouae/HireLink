import { test, expect } from "@playwright/test";
import { apiLogin, injectSession, expectNoDemoLeak, API } from "./helpers";

test.describe("Parcours candidat", () => {
  test("connexion session + dashboard + candidatures", async ({ page, request }) => {
    const auth = await apiLogin(request, "candidat@hirelink.ma", "Candidat123!");
    expect(auth.role).toBe("CANDIDAT");
    await injectSession(page, auth);
    await page.goto("/app/candidate-dashboard.html");
    await expect(page.locator("body")).toContainText(/Espace candidat|Bonjour|offres/i, { timeout: 20000 });
    await expectNoDemoLeak(page);

    await page.goto("/app/candidate-applications.html");
    await expect(page.locator("body")).toContainText(/candidature|dossier|statut/i, { timeout: 20000 });
  });

  test("profil & CV page charge sans erreur fatale", async ({ page, request }) => {
    const auth = await apiLogin(request, "a.tazi@gmail.com", "Candidat123!");
    await injectSession(page, auth);
    await page.goto("/app/candidate-profile.html");
    await expect(page.locator("body")).toContainText(/profil|expérience|CV|formation/i, { timeout: 20000 });
  });

  test("IDOR : candidat B ne lit pas le CV d'une candidature étrangère", async ({ request }) => {
    const a = await apiLogin(request, "s.elamrani@gmail.com", "Candidat123!");
    const mine = await request.get(API + "/applications/mine", {
      headers: { Authorization: "Bearer " + a.accessToken },
    });
    expect(mine.ok()).toBeTruthy();
    const rows = await mine.json();
    const appId = Array.isArray(rows) && rows[0] ? rows[0].id : null;
    test.skip(!appId, "aucune candidature seed pour Sara");

    const b = await apiLogin(request, "a.tazi@gmail.com", "Candidat123!");
    const cv = await request.get(API + `/applications/${appId}/cv`, {
      headers: { Authorization: "Bearer " + b.accessToken },
    });
    expect([403, 404]).toContain(cv.status());
  });
});
