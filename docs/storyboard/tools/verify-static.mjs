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
// 무거운 iframe 22개 로드+CSS주입+측정 완료까지 충분히 대기(타이밍 플레이크 방지).
await page.waitForTimeout(7000);

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
// 프레임의 web/app iframe 이 실제로 스타일 적용됐는지(flex/grid 요소 존재) 확인. 22개 무거운
// iframe 로드가 끝나는 시점이 들쭉날쭉하므로 고정 대기 대신 "스타일될 때까지" 폴링한 뒤 캡처한다.
async function styled(fr) {
  return await fr.evaluate((el) => {
    const ifs = el.querySelectorAll("iframe.screen");
    for (const f of ifs) {
      const d = f.contentDocument; if (!d || !d.body) return false;
      let flex = 0; const all = d.body.querySelectorAll("*");
      for (let k = 0; k < all.length; k++) { const dsp = getComputedStyle(all[k]).display; if (dsp === "flex" || dsp === "grid") { flex++; if (flex >= 3) break; } }
      if (flex < 3) return false;
    }
    return true;
  });
}
// 캡처 직전 대상 프레임 iframe 에 CSS 를 강제 주입(로드 지연/유실과 무관하게 스타일 보장).
async function forceInject(fr) {
  return await fr.evaluate((el) => {
    const css = document.getElementById("appcss").textContent;
    const ifs = el.querySelectorAll("iframe.screen"); let ready = 0;
    ifs.forEach((f) => { const d = f.contentDocument; if (!d || !d.body || !d.body.children.length) return;
      let s = d.getElementById("__appcss__"); if (!s) { s = d.createElement("style"); s.id = "__appcss__"; d.head.appendChild(s); }
      if (s.textContent !== css) s.textContent = css; ready++; });
    return ready === ifs.length;
  });
}
const n = await page.locator(".frame").count();
for (let i = 0; i < n; i++) {
  const fr = page.locator(".frame").nth(i);
  await fr.evaluate((el) => el.scrollIntoView({ block: "start" }));
  for (let t = 0; t < 60 && !(await forceInject(fr)); t++) await page.waitForTimeout(250); // iframe body 준비될 때까지(최대 15s)
  for (let t = 0; t < 20 && !(await styled(fr)); t++) await page.waitForTimeout(150); // 스타일 반영 대기
  await page.waitForTimeout(250);
  const box = await fr.evaluate((el) => { const r = el.getBoundingClientRect(); return { top: r.top, h: r.height }; });
  const y = Math.max(0, Math.floor(box.top));
  const h = Math.min(1360 - y, Math.ceil(box.h) + 8);
  await page.screenshot({ path: path.join(OUT, `qa-static-${String(i + 1).padStart(2, "0")}.png`), clip: { x: 0, y, width: 1280, height: h } });
  console.log("shot:", i + 1, "h", h);
}
await browser.close();
