// Uses the installed taste-skill CDP helper; no browser package is installed.
import { readFileSync, mkdirSync } from 'node:fs';
import { resolve, join } from 'node:path';
import { pathToFileURL } from 'node:url';
const count = Number(process.argv[2]);
if (![1000, 3000, 5000, 7000].includes(count)) throw new Error('Expected invoice count: 1000, 3000, 5000 or 7000');
const helper = process.argv[3] || join(process.env.USERPROFILE, '.codex/skills/taste-skill/scripts/lib/cdp.mjs');
const { Browser } = await import(pathToFileURL(helper));
const folder = resolve('docs/benchmark', String(count));
const result = JSON.parse(readFileSync(join(folder, 'result.json'), 'utf8'));
mkdirSync(folder, { recursive: true });
const browser = await Browser.launch({ width: 1440, height: 1100 });
try {
  const page = await browser.newPage({ width: 1440, height: 1100 });
  for (const [name, report] of Object.entries(result.reports)) {
    await page.goto('http://127.0.0.1:8048/profiles/' + report.representativeQueryId);
    await page.screenshot({ path: join(folder, name + '-profile-summary.png'), format: 'png' });
    await page.screenshot({ path: join(folder, name + '-profile.png'), format: 'png', fullPage: true });
    console.log('Saved actual Drill Profile screenshot:', count, name, report.representativeQueryId);
    const failure = report.runs.find(run => !run.warmup && run.success === false);
    if (failure) {
      await page.goto('http://127.0.0.1:8048/profiles/' + failure.queryId);
      await page.screenshot({ path: join(folder, name + '-failed-profile-summary.png'), format: 'png' });
    }
  }
} finally { await browser.close(); }
