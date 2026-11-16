import { test, expect } from "@playwright/test";
import { apiLogin, injectSession, API } from "./helpers";

test.describe("Parcours recruteur / OWNER", () => {
  test("pipeline + entretiens chargent", async ({ page, request }) => {
    const auth = await apiLogin(request, "n.bennani@atlasdigital.ma", "Recruteur123!");
    expect(auth.role).toBe("RECRUTEUR");
    await injectSession(page, auth);
    await page.goto("/app/recruiter-pipeline.html");
    await expect(page.locator("body")).toContainText(/pipeline|candidature|dossier|offre/i, { timeout: 25000 });
    await page.goto("/app/recruiter-interviews.html");
    await expect(page.locator("body")).toContainText(/entretien|planifier|agenda|à planifier/i, { timeout: 25000 });
  });

  test("équipe + quotas org exposés", async ({ page, request }) => {
    const auth = await apiLogin(request, "n.bennani@atlasdigital.ma", "Recruteur123!");
    const org = await request.get(API + "/organizations/mine", {
      headers: { Authorization: "Bearer " + auth.accessToken },
    });
    expect(org.ok()).toBeTruthy();
    const body = await org.json();
    expect(body.maxMembers).toBeTruthy();
    expect(body.activeOfferLimit != null || body.offersUnlimited === true).toBeTruthy();

    await injectSession(page, auth);
    await page.goto("/app/recruiter-team.html");
    await expect(page.locator("body")).toContainText(/équipe|siège|membre|invitation/i, { timeout: 20000 });
  });

  test("isolation inter-org : offre étrangère non modifiable", async ({ request }) => {
    const atlas = await apiLogin(request, "n.bennani@atlasdigital.ma", "Recruteur123!");
    const mine = await request.get(API + "/offers/mine", {
      headers: { Authorization: "Bearer " + atlas.accessToken },
    });
    expect(mine.ok()).toBeTruthy();
    const offers = await mine.json();
    const offerId = Array.isArray(offers) && offers[0] ? offers[0].id : null;
    test.skip(!offerId, "aucune offre Atlas");

    // Recruteur OCP / autre org seed
    const other = await apiLogin(request, "recrutement.ocpgroup@hirelink.ma", "Recruteur123!").catch(() => null);
    test.skip(!other, "2e organisation seed absente");

    const put = await request.put(API + `/offers/${offerId}`, {
      headers: {
        Authorization: "Bearer " + other!.accessToken,
        "Content-Type": "application/json",
        "X-HireLink-CSRF": "1",
      },
      data: {
        title: "Intrusion",
        description: "Ne doit pas passer — test isolation Vague 6.",
        contractType: "CDI",
        published: true,
      },
    });
    expect([403, 404]).toContain(put.status());
  });
});
