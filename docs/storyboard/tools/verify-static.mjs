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
  const frames = Array.from(document.querySelectorAll(".frame")).map((fr, i) => {
    const web = fr.querySelector(".scr.web"), app = fr.querySelector(".scr.app"), cap = fr.querySelector(".capcol");
    const appIf = app && app.querySelector("iframe");
    return `f${i + 1}:frame${Math.round(fr.getBoundingClientRect().height)} web${web?Math.round(web.getBoundingClientRect().height):0} app${app?Math.round(app.getBoundingClientRect().height):0}(if${appIf?Math.round(appIf.getBoundingClientRect().height):0}) cap${cap?Math.round(cap.getBoundingClientRect().height):0}`;
  });
  return { totalCo: co.length, anchored, frames };
});
console.log("anchored", diag.anchored, "/", diag.totalCo);
diag.frames.forEach((f) => console.log(f));

// 프레임별 캡처 — 뷰포트 스크린샷(page.screenshot)은 컴포지터 경로라 CSS zoom 을 정상 반영한다
// (locator.screenshot / fullPage 는 zoom 무시 버그). 각 프레임을 상단에 스크롤시키고 clip 캡처.
await page.setViewportSize({ width: 1280, height: 1360 });
await page.waitForTimeout(300);
const n = await page.locator(".frame").count();
for (let i = 0; i < n; i++) {
  const fr = page.locator(".frame").nth(i);
  await fr.evaluate((el) => el.scrollIntoView({ block: "start" }));
  await page.waitForTimeout(150);
  const box = await fr.evaluate((el) => { const r = el.getBoundingClientRect(); return { top: r.top, h: r.height }; });
  const y = Math.max(0, Math.floor(box.top));
  const h = Math.min(1360 - y, Math.ceil(box.h) + 8);
  await page.screenshot({ path: path.join(OUT, `qa-static-${String(i + 1).padStart(2, "0")}.png`), clip: { x: 0, y, width: 1280, height: h } });
  console.log("shot:", i + 1, "h", h);
}
await browser.close();
