// storyboard-static.html(DOM 앵커로 박스가 정확히 얹힌 정적 렌더)에서 각 프레임의
// 웹/앱 화면(.scr)을 박스 포함 PNG 로 캡처 → output/annotated/{id}-{web|app}.png.
// MD·slides·PPTX 가 모두 이 annotated 이미지를 쓰므로, 좌표식 합성(build.py annotate) 대신
// "실측 정렬된" 화면을 그대로 이미지화해 전 산출물 박스 정렬을 통일한다.
import { chromium } from "playwright-core";
import { readFile, mkdir } from "node:fs/promises";
import { fileURLToPath, pathToFileURL } from "node:url";
import path from "node:path";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const OUT = path.resolve(__dirname, "../output");
const SPEC = path.resolve(__dirname, "../spec/c-flow.spec.json");
const FILE = path.join(OUT, "storyboard-static.html");
const DIR = path.join(OUT, "annotated");
await mkdir(DIR, { recursive: true });

const spec = JSON.parse(await readFile(SPEC, "utf8"));
const ids = (spec.frames || []).map((f) => f.id);

const browser = await chromium.launch({ channel: process.env.BROWSER_CHANNEL || "chrome", headless: true });
const ctx = await browser.newContext({ viewport: { width: 1280, height: 1500 }, deviceScaleFactor: 2 });
const page = await ctx.newPage();
await page.goto(pathToFileURL(FILE).href, { waitUntil: "load", timeout: 30000 });
await page.waitForTimeout(6000);

// 번호 배지(.cb)는 박스 좌상단 바깥(-11,-11)이라 .scr overflow:hidden 에 잘릴 수 있다 → 캡처 동안 visible.
// iframe 요소 높이는 고정이라 overflow:visible 여도 화면 내용은 더 흘러나오지 않는다(배지만 노출).
await page.evaluate(() => {
  document.querySelectorAll(".scr").forEach((s) => (s.style.overflow = "visible"));
  document.querySelectorAll(".lab").forEach((l) => (l.style.display = "none")); // 화면 위 "웹·1440" 라벨 제거(슬라이드가 자체 라벨 부여)
});

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
async function styled(fr) {
  return await fr.evaluate((el) => {
    const ifs = el.querySelectorAll("iframe.screen");
    for (const f of ifs) { const d = f.contentDocument; if (!d || !d.body) return false;
      let flex = 0; const all = d.body.querySelectorAll("*");
      for (let k = 0; k < all.length; k++) { const dsp = getComputedStyle(all[k]).display; if (dsp === "flex" || dsp === "grid") { if (++flex >= 3) break; } }
      if (flex < 3) return false; }
    return true;
  });
}

const frames = page.locator(".frame");
const n = await frames.count();
for (let i = 0; i < n; i++) {
  const fr = frames.nth(i);
  await fr.evaluate((el) => el.scrollIntoView({ block: "start" }));
  for (let t = 0; t < 60 && !(await forceInject(fr)); t++) await page.waitForTimeout(250);
  for (let t = 0; t < 20 && !(await styled(fr)); t++) await page.waitForTimeout(150);
  await page.waitForTimeout(300);
  for (const kind of ["web", "app"]) {
    const scr = fr.locator(`.scr.${kind}`);
    if (!(await scr.count())) continue;
    const r = await scr.evaluate((el) => { const b = el.getBoundingClientRect(); return { x: b.x, y: b.y, w: b.width, h: b.height }; });
    if (r.w < 2 || r.h < 2) continue;
    const PAD = 13; // 좌상단 배지 + 약간의 여백 포함
    const x = Math.max(0, Math.floor(r.x - PAD)), y = Math.max(0, Math.floor(r.y - PAD));
    const width = Math.min(1280 - x, Math.ceil(r.w + PAD * 2));
    const height = Math.min(1500 - y, Math.ceil(r.h + PAD * 2));
    await page.screenshot({ path: path.join(DIR, `${ids[i]}-${kind}.png`), clip: { x, y, width, height } });
    console.log("screen:", ids[i], kind, `${Math.round(r.w)}x${Math.round(r.h)}`);
  }
}
await browser.close();
