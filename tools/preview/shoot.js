const { chromium } = require('playwright');
(async () => {
  const shots = process.argv.slice(2);
  const browser = await chromium.launch({ args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'] });
  const page = await browser.newPage({ viewport: { width: 2400, height: 1110 } });
  page.on('console', m => { if (m.type() === 'error') console.log('console:', m.text()); });
  for (const s of shots) {
    const [name, shift, shifty] = s.split(':');
    const t0 = Date.now();
    await page.goto(`http://127.0.0.1:8765/render.html?shot=${name}&w=2400&h=1110&shift=${shift || 0}&shifty=${shifty || 0}${process.env.EXTRA || ""}`);
    await page.waitForFunction(() => window.DONE || window.ERR, null, { timeout: 600000, polling: 500 });
    const err = await page.evaluate(() => window.ERR);
    if (err) { console.log(name, 'ERR', err); continue; }
    await page.locator('canvas').screenshot({ path: `out_${s.replace(/:/g, '_')}.png` });
    console.log(name, 'ok', (Date.now() - t0) / 1000, 's');
  }
  await browser.close();
})();
