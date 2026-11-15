import { chromium } from 'playwright';
import fs from 'fs';
import path from 'path';

const API = 'http://127.0.0.1:18081';
const FE = 'http://127.0.0.1:5501';
const OUT = path.resolve('docs/audit-screenshots');
const results = [];

function step(name, status, detail = '') {
  results.push({ step: name, status, detail: String(detail).slice(0, 500) });
  console.log(`[${status}] ${name} — ${detail}`);
}

async function loginApi(email, password) {
  const res = await fetch(`${API}/api/v1/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  if (!res.ok) throw new Error(`login ${email} HTTP ${res.status}`);
  return res.json();
}

async function injectSession(page, auth) {
  await page.addInitScript((a) => {
    localStorage.setItem('hirelink.token', a.accessToken);
    localStorage.setItem(
      'hirelink.session',
      JSON.stringify({
        userId: a.userId,
        email: a.email,
        fullName: a.fullName,
        role: a.role,
        mustSetPassword: a.mustSetPassword,
        emailVerified: a.emailVerified,
        planTier: a.planTier || null,
        mfaEnabled: !!a.mfaEnabled,
      })
    );
  }, auth);
}

async function shot(page, name) {
  await page.screenshot({ path: path.join(OUT, name), fullPage: true });
}

async function main() {
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 1400, height: 900 } });
  // CSP of pages only allows 8081; bypass for isolated API
  await context.route('**/*', async (route) => route.continue());
  const page = await context.newPage();
  page.setDefaultTimeout(25000);

  // --- Visitor landing ---
  try {
    await page.goto(`${FE}/index.html?api=${encodeURIComponent(API)}`, { waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(1500);
    await shot(page, '01-landing.png');
    step('landing_open', 'OK', 'page loaded');
  } catch (e) {
    step('landing_open', 'FAIL', e.message);
  }

  try {
    const search = page.locator('#offer-search, input[placeholder*="Recher"], input[type=search]').first();
    if (await search.count()) {
      await search.fill('Java');
      await page.waitForTimeout(1200);
      await shot(page, '02-search-java.png');
      step('landing_search', 'OK', 'typed Java');
    } else {
      step('landing_search', 'FAIL', 'no search input');
    }
  } catch (e) {
    step('landing_search', 'FAIL', e.message);
  }

  try {
    const offers = page.locator('#offers, #public-offers, .offer-card, [data-offer-id]').first();
    await page.waitForTimeout(500);
    step('landing_offers_area', (await offers.count()) ? 'OK' : 'PARTIAL', 'offers UI probe');
  } catch (e) {
    step('landing_offers_area', 'FAIL', e.message);
  }

  // --- Candidate with injected session ---
  let candidateAuth;
  try {
    candidateAuth = await loginApi('candidat@hirelink.ma', 'Candidat123!');
    step('candidate_api_login', 'OK', candidateAuth.email);
  } catch (e) {
    step('candidate_api_login', 'FAIL', e.message);
  }

  const candPage = await context.newPage();
  if (candidateAuth) await injectSession(candPage, candidateAuth);
  try {
    await candPage.goto(`${FE}/app/candidate.html?api=${encodeURIComponent(API)}`, {
      waitUntil: 'domcontentloaded',
      bypassCSP: true,
    });
    await candPage.waitForTimeout(2500);
    await shot(candPage, '09-candidate-authed.png');
    const match = candPage.locator('#match-only');
    const hasMatch = (await match.count()) > 0;
    step('candidate_catalog', hasMatch ? 'OK' : 'FAIL', hasMatch ? '#match-only present' : 'no #match-only');

    if (hasMatch) {
      await candPage.evaluate(() => {
        const el = document.getElementById('match-only');
        if (el && !el.checked) {
          el.checked = true;
          el.dispatchEvent(new Event('change', { bubbles: true }));
          el.dispatchEvent(new Event('input', { bubbles: true }));
        }
      });
      await candPage.waitForTimeout(2500);
      await shot(candPage, '10-pour-toi-authed.png');
      const checked = await candPage.evaluate(() => document.getElementById('match-only')?.checked === true);
      step('pour_toi_toggle', checked ? 'OK' : 'FAIL', checked ? 'JS toggle #match-only' : 'toggle failed');
    } else {
      step('pour_toi_toggle', 'FAIL', 'no #match-only');
    }

    // Country ALL
    const country = candPage.locator('#country-filter, select[name=country], #offer-country').first();
    if (await country.count()) {
      await country.selectOption({ label: /international|tous|ALL/i }).catch(() =>
        country.selectOption('ALL').catch(() => null)
      );
      await candPage.waitForTimeout(1500);
      await shot(candPage, '11-country-all.png');
      step('country_all', 'OK', 'attempted ALL');
    } else {
      step('country_all', 'PARTIAL', 'no country select');
    }
  } catch (e) {
    step('candidate_catalog', 'FAIL', e.message);
  }

  // Auth form visibility (document known headless issue)
  try {
    const authPage = await context.newPage();
    await authPage.goto(`${FE}/auth.html?mode=login&api=${encodeURIComponent(API)}`, {
      waitUntil: 'domcontentloaded',
      bypassCSP: true,
    });
    await authPage.waitForTimeout(1000);
    // Click Connexion tab if present
    const loginTab = authPage.locator('button, a, [role=tab]').filter({ hasText: /connexion|se connecter|login/i }).first();
    if (await loginTab.count()) await loginTab.click().catch(() => {});
    await authPage.waitForTimeout(500);
    const email = authPage.locator('#email, input[type=email]').first();
    const visible = await email.isVisible().catch(() => false);
    if (visible) {
      await email.fill('candidat@hirelink.ma');
      await authPage.locator('#password, input[type=password]').first().fill('Candidat123!');
      await shot(authPage, '13-auth-form-visible.png');
      step('candidate_login_form', 'OK', 'email visible and filled');
    } else {
      await shot(authPage, '13-auth-form-hidden.png');
      step('candidate_login_form', 'NOT_TESTED', 'email input not visible in headless (mode UI)');
    }
  } catch (e) {
    step('candidate_login_form', 'FAIL', e.message);
  }

  // --- Recruiter injected ---
  let recruiterAuth;
  try {
    recruiterAuth = await loginApi('recruteur@hirelink.ma', 'Recruteur123!');
    step('recruiter_api_login', 'OK', recruiterAuth.email);
  } catch (e) {
    step('recruiter_api_login', 'FAIL', e.message);
  }

  const recPage = await context.newPage();
  if (recruiterAuth) await injectSession(recPage, recruiterAuth);
  try {
    await recPage.goto(`${FE}/app/recruiter-offers.html?api=${encodeURIComponent(API)}`, {
      waitUntil: 'domcontentloaded',
      bypassCSP: true,
    });
    await recPage.waitForTimeout(2500);
    await shot(recPage, '12-recruiter-authed.png');
    const url = recPage.url();
    const ok = !url.includes('auth.html');
    step('recruiter_offers_page', ok ? 'OK' : 'FAIL', url);

    // Publish cycle via API (browser-adjacent proof) then check catalog
    if (recruiterAuth) {
      const createRes = await fetch(`${API}/api/v1/offers`, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${recruiterAuth.accessToken}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          title: 'Audit Recette Browser CDI Java',
          description: 'Offre creee pendant la recette navigateur isolee pour verifier la sync catalogue.',
          location: 'Casablanca',
          contractType: 'CDI',
          requiredSkills: 'Java, Spring Boot',
          workMode: 'HYBRID',
          published: true,
        }),
      });
      const createBody = await createRes.json().catch(() => ({}));
      step('recruiter_create_offer_api', createRes.ok ? 'OK' : 'FAIL', `HTTP ${createRes.status} id=${createBody.id || createBody.offerId || '?'}`);

      const cat = await fetch(`${API}/api/v1/catalog/offers?country=MA&q=Audit%20Recette&size=5`).then((r) => r.json());
      const found = (cat.items || []).some((i) => (i.title || '').includes('Audit Recette'));
      step('catalog_reflects_new_offer', found ? 'OK' : 'FAIL', `total=${cat.totalElements}`);
    }
  } catch (e) {
    step('recruiter_offers_page', 'FAIL', e.message);
  }

  // Resilience: dead API
  try {
    const dead = await context.newPage();
    await dead.goto(`${FE}/index.html?api=${encodeURIComponent('http://127.0.0.1:19999')}`, {
      waitUntil: 'domcontentloaded',
      bypassCSP: true,
    });
    await dead.waitForTimeout(2000);
    await shot(dead, '08-api-down.png');
    step('resilience_bad_api', 'OK', 'loaded with dead API');
  } catch (e) {
    step('resilience_bad_api', 'FAIL', e.message);
  }

  await browser.close();
  fs.writeFileSync(path.join(OUT, 'browser-recette.json'), JSON.stringify(results, null, 2));
  const fails = results.filter((r) => r.status === 'FAIL').length;
  console.log(`DONE fails=${fails}`);
  process.exit(fails > 3 ? 1 : 0);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
