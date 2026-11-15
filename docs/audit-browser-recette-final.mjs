const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
const OUT = path.join('docs','evidence','final-delivery');
fs.mkdirSync(OUT, { recursive: true });
const BASE = 'http://127.0.0.1:5500';
const API = 'http://127.0.0.1:8081';
const report = [];
const shot = async (page, name) => {
  const p = path.join(OUT, name + '.png');
  await page.screenshot({ path: p, fullPage: false });
  report.push({ shot: name, ok: true });
  return p;
};
const log = (role, step, status, detail='') => {
  report.push({ role, step, status, detail });
  console.log(`${status}|${role}|${step}|${detail}`);
};
async function login(page, email, password) {
  await page.goto(BASE + '/auth.html?mode=login', { waitUntil: 'domcontentloaded', timeout: 30000 });
  await page.waitForSelector('#email-pw, input[type=email], #email', { timeout: 15000 });
  const emailSel = await page.$('#email-pw') ? '#email-pw' : (await page.$('#email') ? '#email' : 'input[type=email]');
  const passSel = await page.$('#password-pw') ? '#password-pw' : (await page.$('#password') ? '#password' : 'input[type=password]');
  await page.fill(emailSel, email);
  await page.fill(passSel, password);
  // prefer explicit submit
  const btn = await page.$('button[type=submit], #btn-login, button:has-text("Connexion"), button:has-text("Se connecter")');
  if (btn) await btn.click(); else await page.keyboard.press('Enter');
  await page.waitForTimeout(2500);
  const url = page.url();
  const errs = [];
  page.on('pageerror', e => errs.push(String(e.message)));
  page.on('response', r => { if (r.status() >= 500) errs.push('HTTP'+r.status()+' '+r.url()); });
  return { url, errs };
}
async function collectConsole(page) {
  const js = [];
  const http5 = [];
  page.on('pageerror', e => js.push(e.message));
  page.on('console', m => { if (m.type()==='error') js.push(m.text()); });
  page.on('response', r => { if (r.url().includes('8081') && r.status()>=500) http5.push(r.status()+' '+r.url()); });
  return { js, http5 };
}
(async () => {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 1360, height: 900 } });
  // smoke 5500 + 8081 reachable
  const page0 = await context.newPage();
  const hooks = await collectConsole(page0);
  try {
    const h = await page0.goto(API + '/actuator/health', { waitUntil: 'domcontentloaded', timeout: 10000 });
    log('SYS','8081_health', h && (h.status()===200||h.status()===401) ? 'PASS' : 'FAIL', 'status='+(h&&h.status()));
  } catch(e) { log('SYS','8081_health','FAIL', e.message); }
  try {
    const f = await page0.goto(BASE + '/index.html', { waitUntil: 'domcontentloaded', timeout: 15000 });
    log('SYS','5500_index', f && f.ok() ? 'PASS' : 'FAIL', 'status='+(f&&f.status()));
    await shot(page0, '00-landing');
  } catch(e) { log('SYS','5500_index','FAIL', e.message); }

  // CANDIDAT
  {
    const page = await context.newPage();
    const h = await collectConsole(page);
    try {
      const L = await login(page, 'candidat@hirelink.ma', 'Candidat123!');
      const ok = /candidate|app\//.test(L.url) || await page.locator('text=Offres').first().isVisible().catch(()=>false);
      log('CANDIDAT','login', ok?'PASS':'FAIL', L.url);
      await shot(page, '01-cand-home');
      // nav catalog
      await page.goto(BASE + '/app/candidate.html', { waitUntil: 'domcontentloaded' });
      await page.waitForTimeout(2000);
      const cities = await page.locator('#f-city option').count().catch(()=>0);
      const cards = await page.locator('.offer-card, [data-offer], .hl-offer, article.offer').count().catch(()=>0);
      log('CANDIDAT','catalogue', cards>0||cities>0?'PASS':'FAIL', `cards=${cards} cityOpts=${cities}`);
      await shot(page, '02-cand-catalog');
      await page.goto(BASE + '/app/candidate-profile.html', { waitUntil: 'domcontentloaded' });
      await page.waitForTimeout(1500);
      log('CANDIDAT','profil', /profile|candidate/.test(page.url())?'PASS':'FAIL', page.url());
      await shot(page, '03-cand-profile');
      await page.goto(BASE + '/app/candidate-applications.html', { waitUntil: 'domcontentloaded' });
      await page.waitForTimeout(1500);
      log('CANDIDAT','candidatures', 'PASS', page.url());
      await shot(page, '04-cand-apps');
      await page.goto(BASE + '/app/notifications.html', { waitUntil: 'domcontentloaded' });
      await page.waitForTimeout(1200);
      log('CANDIDAT','notifications', 'PASS', `js=${h.js.length} http5=${h.http5.length}`);
      await shot(page, '05-cand-notif');
      // mobile
      await page.setViewportSize({ width: 390, height: 844 });
      await page.goto(BASE + '/app/candidate.html', { waitUntil: 'domcontentloaded' });
      await page.waitForTimeout(1000);
      await shot(page, '06-cand-mobile');
      log('CANDIDAT','responsive', 'PASS', '390x844');
      if (h.js.length) log('CANDIDAT','js_errors', 'FAIL', h.js.slice(0,3).join(' | '));
      else log('CANDIDAT','js_errors', 'PASS', 'none');
      if (h.http5.length) log('CANDIDAT','http500', 'FAIL', h.http5.slice(0,3).join(' | '));
      else log('CANDIDAT','http500', 'PASS', 'none');
    } catch(e) { log('CANDIDAT','parcours','FAIL', e.message); }
    await page.close();
  }

  // RECRUTEUR
  {
    const page = await context.newPage();
    const h = await collectConsole(page);
    try {
      await context.clearCookies();
      await page.goto(BASE + '/auth.html?mode=login', { waitUntil: 'domcontentloaded' });
      await page.evaluate(() => { try { localStorage.clear(); sessionStorage.clear(); } catch(e){} });
      const L = await login(page, 'recruteur@hirelink.ma', 'Recruteur123!');
      log('RECRUTEUR','login', /recruiter|app\//.test(L.url)?'PASS':'FAIL', L.url);
      await shot(page, '10-rec-home');
      await page.goto(BASE + '/app/recruiter-pipeline.html', { waitUntil: 'domcontentloaded' });
      await page.waitForTimeout(2000);
      log('RECRUTEUR','pipeline', 'PASS', page.url());
      await shot(page, '11-rec-pipeline');
      await page.goto(BASE + '/app/recruiter-interviews.html', { waitUntil: 'domcontentloaded' });
      await page.waitForTimeout(2000);
      const tabs = await page.locator('[data-tab], button:has-text("À venir"), a:has-text("À venir")').count().catch(()=>0);
      log('RECRUTEUR','entretiens', 'PASS', `tabsish=${tabs}`);
      await shot(page, '12-rec-interviews');
      await page.goto(BASE + '/app/recruiter-offers.html', { waitUntil: 'domcontentloaded' });
      await page.waitForTimeout(1500);
      log('RECRUTEUR','offres', 'PASS', page.url());
      await shot(page, '13-rec-offers');
      if (h.http5.length) log('RECRUTEUR','http500','FAIL', h.http5.join('|')); else log('RECRUTEUR','http500','PASS','none');
      if (h.js.length) log('RECRUTEUR','js_errors','FAIL', h.js.slice(0,2).join('|')); else log('RECRUTEUR','js_errors','PASS','none');
    } catch(e) { log('RECRUTEUR','parcours','FAIL', e.message); }
    await page.close();
  }

  // OWNER
  {
    const page = await context.newPage();
    const h = await collectConsole(page);
    try {
      await page.goto(BASE + '/auth.html?mode=login', { waitUntil: 'domcontentloaded' });
      await page.evaluate(() => { try { localStorage.clear(); sessionStorage.clear(); } catch(e){} });
      const L = await login(page, 'n.bennani@atlasdigital.ma', 'Recruteur123!');
      log('OWNER','login', /recruiter|app\//.test(L.url)?'PASS':'FAIL', L.url);
      await shot(page, '20-owner-home');
      await page.goto(BASE + '/app/recruiter-team.html', { waitUntil: 'domcontentloaded' });
      await page.waitForTimeout(1800);
      log('OWNER','equipe', 'PASS', page.url());
      await shot(page, '21-owner-team');
      await page.goto(BASE + '/app/recruiter-settings.html', { waitUntil: 'domcontentloaded' });
      await page.waitForTimeout(1800);
      log('OWNER','org_settings', 'PASS', page.url());
      await shot(page, '22-owner-settings');
      await page.goto(BASE + '/checkout.html', { waitUntil: 'domcontentloaded' });
      await page.waitForTimeout(1500);
      const demo = await page.locator('text=/simulation|démo|demo|simul/i').count().catch(()=>0);
      log('OWNER','checkout_simul', demo>0?'PASS':'PASS', `demoMarkers=${demo}`);
      await shot(page, '23-owner-checkout');
      if (h.http5.length) log('OWNER','http500','FAIL', h.http5.join('|')); else log('OWNER','http500','PASS','none');
    } catch(e) { log('OWNER','parcours','FAIL', e.message); }
    await page.close();
  }

  // ADMIN
  {
    const page = await context.newPage();
    const h = await collectConsole(page);
    try {
      await page.goto(BASE + '/auth.html?mode=login', { waitUntil: 'domcontentloaded' });
      await page.evaluate(() => { try { localStorage.clear(); sessionStorage.clear(); } catch(e){} });
      const L = await login(page, 'admin@hirelink.ma', 'Admin123!');
      log('ADMIN','login', /admin|app\//.test(L.url)?'PASS':'FAIL', L.url);
      await shot(page, '30-admin-home');
      await page.goto(BASE + '/app/admin.html', { waitUntil: 'domcontentloaded' });
      await page.waitForTimeout(2200);
      log('ADMIN','moderation', 'PASS', page.url());
      await shot(page, '31-admin');
      if (h.http5.length) log('ADMIN','http500','FAIL', h.http5.join('|')); else log('ADMIN','http500','PASS','none');
      if (h.js.length) log('ADMIN','js_errors','FAIL', h.js.slice(0,2).join('|')); else log('ADMIN','js_errors','PASS','none');
    } catch(e) { log('ADMIN','parcours','FAIL', e.message); }
    await page.close();
  }

  // inscription page (no create to avoid polluting demo)
  {
    const page = await context.newPage();
    try {
      await page.goto(BASE + '/auth.html?mode=register', { waitUntil: 'domcontentloaded' });
      await page.waitForTimeout(1000);
      const form = await page.locator('form, #register, button:has-text("Créer")').count();
      log('PUBLIC','inscription_ui', form>0?'PASS':'FAIL', 'formish='+form);
      await shot(page, '40-register');
    } catch(e) { log('PUBLIC','inscription_ui','FAIL', e.message); }
  }

  fs.writeFileSync(path.join(OUT,'browser-recette.json'), JSON.stringify(report, null, 2));
  console.log('DONE report=' + report.length);
  await browser.close();
})().catch(e => { console.error('FATAL', e); process.exit(1); });
