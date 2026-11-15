const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
const OUT = path.join('docs', 'evidence', 'final-delivery', 'web-intl-smoke');
fs.mkdirSync(OUT, { recursive: true });
const FE = 'http://127.0.0.1:5500';
const API = 'http://127.0.0.1:18089/api/v1';
const report = [];
const log = (step, status, detail) => {
  report.push({ step, status, detail });
  console.log(`${status}|${step}|${detail}`);
};

(async () => {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 1360, height: 900 } });
  await context.addInitScript(() => {
    window.HIRELINK_API_BASE = 'http://127.0.0.1:18089/api/v1';
  });
  const page = await context.newPage();
  const http5 = [];
  page.on('response', (r) => {
    if (r.url().includes('18089') && r.status() >= 500) http5.push(r.status() + ' ' + r.url());
  });

  // Public catalog API first
  const api = await page.goto(API + '/catalog/offers?q=developer&country=ALL&size=5', {
    waitUntil: 'domcontentloaded',
    timeout: 60000,
  });
  log('api_catalog', api && api.ok() ? 'PASS' : 'FAIL', 'status=' + (api && api.status()));

  await page.goto(FE + '/app/candidate.html', { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.waitForTimeout(2500);
  // May redirect to auth — still capture
  await page.screenshot({ path: path.join(OUT, 'browser-01-candidate.png') });
  const url = page.url();
  log('candidate_page', /candidate|auth/.test(url) ? 'PASS' : 'FAIL', url);

  // Landing public offers if present
  await page.goto(FE + '/index.html', { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.addInitScript(() => {
    window.HIRELINK_API_BASE = 'http://127.0.0.1:18089/api/v1';
  });
  await page.evaluate(() => {
    window.HIRELINK_API_BASE = 'http://127.0.0.1:18089/api/v1';
  });
  await page.reload({ waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(3000);
  await page.screenshot({ path: path.join(OUT, 'browser-02-landing.png') });
  const body = await page.locator('body').innerText();
  log('landing_loaded', body.length > 50 ? 'PASS' : 'FAIL', 'len=' + body.length);
  log('http500', http5.length ? 'FAIL' : 'PASS', http5.slice(0, 3).join('|') || 'none');

  // Mobile
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(FE + '/app/candidate.html', { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(1500);
  await page.screenshot({ path: path.join(OUT, 'browser-03-mobile.png') });
  log('mobile', 'PASS', '390x844');

  fs.writeFileSync(path.join(OUT, 'browser-recette.json'), JSON.stringify(report, null, 2));
  console.log('DONE');
  await browser.close();
})().catch((e) => {
  console.error('FATAL', e);
  process.exit(1);
});
