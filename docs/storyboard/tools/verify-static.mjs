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

// 앵커가 측정 적용됐는지 진단(co[data-anchored])
const diag = await page.evaluate(() => {
  const co = document.querySelectorAll(".co");
  let anchored = 0;
  co.forEach((c) => { if (c.getAttribute("data-anchored") === "1") anchored++; });
  return { totalCo: co.length, anchored };
});
console.log("오버레이 진단:", JSON.stringify(diag), "(anchored = 실시간 측정 적용)");

// 11프레임 전부 캡처
const frames = await page.locator(".frame").count();
for (let i = 0; i < frames; i++) {
  const file = `qa-static-${String(i + 1).padStart(2, "0")}.png`;
  try { await page.locator(".frame").nth(i).screenshot({ path: path.join(OUT, file) }); console.log("shot:", file); }
  catch (e) { console.log("FAIL", file, e.message.split("\n")[0]); }
}
await browser.close();
