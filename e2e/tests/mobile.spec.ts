import { test, expect } from "@playwright/test";
import { apiLogin, injectSession } from "./helpers";

test.describe("Affichage mobile", () => {
  test("dashboard candidat mobile", async ({ page, request }) => {
    const auth = await apiLogin(request, "candidat@hirelink.ma", "Candidat123!");
    await injectSession(page, auth);
    await page.goto("/app/candidate-dashboard.html");
    await expect(page.locator("body")).toBeVisible();
    await expect(page.locator("body")).toContainText(/HireLink|candidat|offre/i, { timeout: 20000 });
  });
});
