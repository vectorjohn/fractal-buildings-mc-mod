// Re-encode PNGs as JPEG (quality 0.82) with headless Chromium's canvas: node scripts/png-to-jpg.mjs <src.png> <dst.jpg> [...pairs]
import { chromium } from '@playwright/test';
import { readFileSync, writeFileSync } from 'node:fs';
const args = process.argv.slice(2);
const browser = await chromium.launch();
const page = await browser.newPage();
for (let i = 0; i + 1 < args.length; i += 2) {
  const b64 = readFileSync(args[i]).toString('base64');
  const out = await page.evaluate(async (src) => {
    const img = new Image(); img.src = 'data:image/png;base64,' + src; await img.decode();
    const c = document.createElement('canvas'); c.width = img.width; c.height = img.height;
    c.getContext('2d').drawImage(img, 0, 0);
    return c.toDataURL('image/jpeg', 0.82).split(',')[1];
  }, b64);
  writeFileSync(args[i + 1], Buffer.from(out, 'base64'));
}
await browser.close();
