// 렌더된 HTML 을 QA 캡처 / PDF / 16:9 슬라이드 PNG 로 출력.
// usage: node export.mjs [qa|pdf|slides|both]
import { chromium } from "playwright-core";
import { fileURLToPath, pathToFileURL } from "node:url";
import { mkdir } from "node:fs/promises";
import path from "node:path";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const OUT = path.resolve(__dirname, "../output");
const HTML = path.resolve(OUT, "storyboard.html");
const SLIDES_HTML = path.resolve(OUT, "slides.html");
const mode = process.argv[2] || "both";
const CHANNEL = process.env.BROWSER_CHANNEL || "chrome";

const browser = await chromium.launch({ channel: CHANNEL, headless: true });

async function openPage(file, vp) {
  const ctx = await browser.newContext({ viewport: vp, deviceScaleFactor: 2 });
  const page = await ctx.newPage();
  await page.goto(pathToFileURL(file).href, { waitUntil: "networkidle", timeout: 30000 });
  await page.waitForTimeout(3500); // mermaid(CDN) 렌더 대기
  return page;
}

if (mode === "qa" || mode === "both" || mode === "pdf") {
  const page = await openPage(HTML, { width: 1280, height: 1000 });
  if (mode === "qa" || mode === "both") {
    const targets = [
      { sel: ".flowwrap", file: "qa-flow.png" },
      { sel: ".frame >> nth=0", file: "qa-frame-01.png" },
      { sel: ".frame >> nth=4", file: "qa-frame-05.png" },
      { sel: ".frame >> nth=7", file: "qa-frame-08.png" },
    ];
    for (const t of targets) {
      try { await page.locator(t.sel).first().screenshot({ path: path.join(OUT, t.file) }); console.log("qa:", t.file); }
      catch (e) { console.log("qa FAIL", t.file, e.message.split("\n")[0]); }
    }
  }
  if (mode === "pdf" || mode === "both") {
    await page.emulateMedia({ media: "print" });
    await page.pdf({ path: path.join(OUT, "storyboard.pdf"), printBackground: true, width: "1240px", height: "1750px", margin: { top: "10px", bottom: "10px", left: "10px", right: "10px" } });
    console.log("pdf: storyboard.pdf");
  }
}

if (mode === "slides" || mode === "both") {
  const dir = path.join(OUT, "slides");
  await mkdir(dir, { recursive: true });
  const page = await openPage(SLIDES_HTML, { width: 1280, height: 720 });
  const slides = page.locator(".slide");
  const n = await slides.count();
  for (let i = 0; i < n; i++) {
    const f = `slide-${String(i + 1).padStart(2, "0")}.png`;
    await slides.nth(i).screenshot({ path: path.join(dir, f) });
    console.log("slide:", f);
  }
  console.log(`slides total: ${n}`);
}

await browser.close();
