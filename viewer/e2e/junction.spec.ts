import { expect, test } from '@playwright/test';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

// JUNCTION review pictures: a fly-camera view of a street junction in a district scene written by `fractal-cli city --scenes ID`.
// FB_JUNCTION_SHOTS = `name=cityDir:districtId:x,y,z,yaw,pitch;...` (absolute city dirs); each shot goes to out/junction/<name>.png.
// Without the variable the spec does nothing (it is a review tool, not part of the suite).
test.use({ viewport: { width: 1200, height: 800 } });

const shots = (process.env.FB_JUNCTION_SHOTS ?? '').split(';').filter((s) => s.includes('='));
if (shots.length > 0) mkdirSync('out/junction', { recursive: true });

for (const s of shots) {
  const [name, rest] = s.split('=') as [string, string];
  const [dir, id, stop] = rest.split(':') as [string, string, string];
  test(`junction ${name}`, async ({ page }) => {
    test.setTimeout(180_000);
    const file = join(dir, 'scenes', `${id}.json`);
    expect(existsSync(file), file).toBe(true);
    const body = readFileSync(file, 'utf8');
    await page.route((u) => u.pathname === `/data/${id}.json`, (r) => r.fulfill({ body, contentType: 'application/json' }));
    await page.goto(`/index.html?ir=/data/${id}.json&camera=fly&exterior=1&stops=${stop}`);
    await page.waitForFunction(() => window.__viewerReady !== undefined, undefined, { timeout: 150_000 });
    const ready = await page.evaluate(() => window.__viewerReady!);
    expect(ready.ok, String((ready as { error?: string }).error)).toBe(true);
    await page.waitForTimeout(1000);
    writeFileSync(`out/junction/${name}.png`, await page.screenshot());
  });
}
