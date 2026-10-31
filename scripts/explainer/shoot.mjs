// Screenshots of the explainer page for review: desktop and phone, light and dark, per section and per timeline step.
// Usage (from viewer/, inside the dev shell): node ../scripts/explainer/shoot.mjs PAGE.html OUTDIR [steps]
import { createRequire } from 'module';
const { chromium } = createRequire(process.cwd() + '/')('playwright');
import fs from 'fs';
import path from 'path';

const [PAGE, OUT, STEPS] = process.argv.slice(2);
fs.mkdirSync(OUT, { recursive: true });
// the artifact host wraps the page in a skeleton; do the same here
const wrapped = path.join(OUT, '_wrapped.html');
fs.writeFileSync(wrapped, '<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover"></head><body>' + fs.readFileSync(PAGE, 'utf8') + '</body></html>');
const browser = await chromium.launch();
const errors = [];
for (const [name, vp] of [['desk', { width: 1366, height: 900 }], ['phone', { width: 390, height: 844 }]]) {
  for (const scheme of ['light', 'dark']) {
    const ctx = await browser.newContext({ viewport: vp, colorScheme: scheme, deviceScaleFactor: 1 });
    const page = await ctx.newPage();
    page.on('console', (m) => { if (m.type() === 'error') errors.push(`${name}-${scheme}: ${m.text()}`); });
    page.on('pageerror', (e) => errors.push(`${name}-${scheme}: ${e.message}`));
    await page.goto('file://' + wrapped);
    await page.waitForTimeout(1200);
    const sw = await page.evaluate(() => [document.documentElement.scrollWidth, window.innerWidth]);
    if (sw[0] > sw[1]) errors.push(`${name}-${scheme}: horizontal overflow ${sw[0]} > ${sw[1]}`);
    for (const id of ['timeline', 'architecture', 'time', 'experiments', 'decisions']) {
      const el = await page.$('#' + id);
      await el.screenshot({ path: path.join(OUT, `${name}-${scheme}-${id}.png`) });
    }
    if (STEPS && scheme === 'light' || (STEPS === 'all')) {
      const n = await page.$$eval('.chip', (c) => c.length);
      for (let i = 0; i < n; i++) {
        await page.click(`.chip >> nth=${i}`);
        await page.waitForTimeout(150);
        const st = await page.$('#stage');
        await st.screenshot({ path: path.join(OUT, `${name}-${scheme}-step${String(i + 1).padStart(2, '0')}.png`) });
      }
    }
    await ctx.close();
  }
}
await browser.close();
console.log(errors.length ? 'ERRORS:\n' + errors.join('\n') : 'no console errors, no overflow');
