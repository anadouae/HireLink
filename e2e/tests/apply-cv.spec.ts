import { test, expect } from "@playwright/test";
import { apiLogin, API } from "./helpers";

/**
 * Vague 7 — candidature avec CV (API réelle ISO).
 * Permissions : candidat crée ; autre candidat 403/404 ; recruteur org peut lire le CV.
 */
test.describe("Candidature avec CV", () => {
  test("appliquer + joindre CV + accès recruteur / refus IDOR", async ({ request }) => {
    const cand = await apiLogin(request, "s.elamrani@gmail.com", "Candidat123!");
    const offersRes = await request.get(API + "/offers/public", {
      headers: { Authorization: "Bearer " + cand.accessToken },
    });
    expect(offersRes.ok()).toBeTruthy();
    const list = (await offersRes.json()) as { id?: number; title?: string }[];
    expect(Array.isArray(list)).toBeTruthy();
    test.skip(!list.length, "aucune offre publique");

    const preferred =
      list.find((o) => /Java|Full-Stack|ATS|Spring/i.test(String(o.title || "")))?.id ||
      list[0]?.id;
    expect(preferred).toBeTruthy();
    const offerId = preferred as number;

    const apply = await request.post(API + "/applications", {
      headers: {
        Authorization: "Bearer " + cand.accessToken,
        "Content-Type": "application/json",
        "X-HireLink-CSRF": "1",
      },
      data: {
        jobOfferId: offerId,
        coverLetter: "Candidature Vague 7 — test automatisé ISO (données fictives).",
        details: JSON.stringify({ availability: "Immediate", notice: "0" }),
      },
    });

    let appId: number | null = null;
    if (apply.status() === 200 || apply.status() === 201) {
      const created = await apply.json();
      appId = created.id;
    } else {
      const mine = await request.get(API + "/applications/mine", {
        headers: { Authorization: "Bearer " + cand.accessToken },
      });
      expect(mine.ok()).toBeTruthy();
      const rows = (await mine.json()) as {
        offerId?: number;
        jobOfferId?: number;
        id?: number;
      }[];
      const hit = (Array.isArray(rows) ? rows : []).find(
        (r) => r.jobOfferId === offerId || r.offerId === offerId
      );
      appId = hit?.id ?? null;
      if (!appId && Array.isArray(rows) && rows[0]?.id) {
        appId = rows[0].id as number;
      }
      test.skip(
        !appId,
        "apply HTTP " + apply.status() + " et aucune candidature existante"
      );
    }

    const cvBuf = Buffer.from(
      "CV DEMO HIRELINK\nSara El Amrani\nJava Spring Boot MySQL\nExperience fictive Vague 7.\n",
      "utf8"
    );

    const cvRes = await request.post(API + `/applications/${appId}/cv`, {
      headers: {
        Authorization: "Bearer " + cand.accessToken,
        "X-HireLink-CSRF": "1",
      },
      multipart: {
        file: {
          name: "cv-demo.txt",
          mimeType: "text/plain",
          buffer: cvBuf,
        },
      },
    });
    const cvBody = await cvRes.text();
    expect(
      [200, 201],
      `attach CV → ${cvRes.status()} ${cvBody.slice(0, 200)}`
    ).toContain(cvRes.status());

    const other = await apiLogin(request, "a.tazi@gmail.com", "Candidat123!");
    const idor = await request.get(API + `/applications/${appId}/cv`, {
      headers: { Authorization: "Bearer " + other.accessToken },
    });
    expect([403, 404]).toContain(idor.status());

    const recruiter = await apiLogin(
      request,
      "n.bennani@atlasdigital.ma",
      "Recruteur123!"
    );
    const recCv = await request.get(API + `/applications/${appId}/cv`, {
      headers: { Authorization: "Bearer " + recruiter.accessToken },
    });
    expect([200, 403, 404]).toContain(recCv.status());
    if (recCv.status() === 200) {
      const buf = await recCv.body();
      expect(buf.byteLength).toBeGreaterThan(10);
      return;
    }

    const mineOffers = await request.get(API + "/offers/mine", {
      headers: { Authorization: "Bearer " + recruiter.accessToken },
    });
    expect(mineOffers.ok()).toBeTruthy();
    const atlas = (await mineOffers.json()) as { id?: number }[];
    const atlasId = atlas.find((o) => o.id)?.id;
    test.skip(!atlasId, "pas d'offre Atlas pour vérifier accès CV recruteur");

    const cand2 = await apiLogin(request, "a.tazi@gmail.com", "Candidat123!");
    const applyAtlas = await request.post(API + "/applications", {
      headers: {
        Authorization: "Bearer " + cand2.accessToken,
        "Content-Type": "application/json",
        "X-HireLink-CSRF": "1",
      },
      data: {
        jobOfferId: atlasId,
        coverLetter: "Candidature Atlas Vague 7 (fictive).",
      },
    });
    let atlasAppId: number | null = null;
    if (applyAtlas.status() === 200 || applyAtlas.status() === 201) {
      atlasAppId = (await applyAtlas.json()).id;
    } else {
      const mine2 = await request.get(API + "/applications/mine", {
        headers: { Authorization: "Bearer " + cand2.accessToken },
      });
      const rows2 = (await mine2.json()) as { jobOfferId?: number; id?: number }[];
      atlasAppId = rows2.find((r) => r.jobOfferId === atlasId)?.id ?? null;
    }
    expect(atlasAppId).toBeTruthy();

    const cv2 = await request.post(API + `/applications/${atlasAppId}/cv`, {
      headers: {
        Authorization: "Bearer " + cand2.accessToken,
        "X-HireLink-CSRF": "1",
      },
      multipart: {
        file: {
          name: "cv-atlas.txt",
          mimeType: "text/plain",
          buffer: Buffer.from(
            "CV DEMO ATLAS\nAmine Tazi\nExperience fictive Vague 7.\n",
            "utf8"
          ),
        },
      },
    });
    expect([200, 201]).toContain(cv2.status());

    const recOk = await request.get(API + `/applications/${atlasAppId}/cv`, {
      headers: { Authorization: "Bearer " + recruiter.accessToken },
    });
    expect(recOk.status()).toBe(200);
    expect((await recOk.body()).byteLength).toBeGreaterThan(10);
  });
});
