// storyboard-static.html 을 서버 없이 file:// 로 열어 자체완결 동작을 검증.
import { chromium } from "playwright-core";
import { fileURLToPath, pathToFileURL } from "node:url";
import path from "node:path";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const OUT = path.resolve(__dirname, "../output");
const FILE = path.join(OUT, "storyboard-static.html");

const browser = await chromium.launch({ channel: process.env.BROWSER_CHANNEL || "chrome", headless: true });
const ctx = await browser.newContext({ viewport: { width: 1280, height: 1000 }, deviceScaleFactor: 2 });
const page = await ctx.newPage();
await page.goto(pathToFileURL(FILE).href, { waitUntil: "load", timeout: 30000 });
await page.waitForTimeout(3500);

// iframe 안에 실제 DOM + CSS 주입됐는지 점검
const diag = await page.evaluate(() => {
  const f = document.querySelectorAll("iframe.screen");
  const out = [];
  for (let i = 0; i < Math.min(f.length, 4); i++) {
    const d = f[i].contentDocument;
    const injected = !!(d && d.getElementById("__appcss__"));
    const nodes = d ? d.body.querySelectorAll("*").length : 0;
    out.push({ i, injected, nodes });
  }
  return { total: f.length, sample: out };
});
console.log("iframe 진단:", JSON.stringify(diag));

for (const [sel, file] of [[".frame >> nth=0", "qa-static-01.png"], [".frame >> nth=4", "qa-static-05.png"]]) {
  try { await page.locator(sel).first().screenshot({ path: path.join(OUT, file) }); console.log("shot:", file); }
  catch (e) { console.log("FAIL", file, e.message.split("\n")[0]); }
}
await browser.close();
