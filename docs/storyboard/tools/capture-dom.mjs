// 화면을 PNG 가 아니라 "정적 DOM 스냅샷"으로 캡처(래스터 이미지 0 — 전부 DOM/SVG).
// CSS 는 한 번만(_app.css), 각 화면은 DOM 만(dom/<id>-<vp>.html, script 제거)로 저장 →
// render-dom.mjs 가 CSS 1벌 + DOM 22개로 자체완결 단일 HTML 을 만든다(React/서버/이미지 불필요).
import { chromium } from "playwright-core";
import { mkdir, writeFile, rm } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const OUT = path.resolve(__dirname, "../snapshots");
const DOMDIR = path.join(OUT, "dom");
const BASE = process.env.BASE_URL || "http://localhost:5173";
const CHANNEL = process.env.BROWSER_CHANNEL || "chrome";
const TOKEN = JSON.stringify({ accessToken: "demo-access", refreshToken: "demo-refresh" });

const VIEWPORTS = { web: { width: 1440, height: 1024 }, app: { width: 390, height: 844, isMobile: true } };
const FRAMES = [
  { id: "01-home", route: "/" }, { id: "02-dashboard", route: "/dashboard" },
  { id: "03-analysis", route: "/analysis" }, { id: "04-detail-overview", route: "/applications/1" },
  { id: "05-fit", route: "/applications/1/fit" }, { id: "06-admin-home", route: "/admin/home" },
  { id: "07-admin-analytics", route: "/admin/analytics" }, { id: "08-admin-fit", route: "/admin/fit-analysis" },
  { id: "09-admin-prompts-fit", route: "/admin/prompts/fit-analysis" }, { id: "10-admin-prompts-analytics", route: "/admin/prompts/analytics" },
  { id: "11-login-required", route: "/applications/1/fit", noAuth: true },
];

const extract = () => {
  // 1) 내용을 가리는 전역 플로팅 오버레이 제거(채팅 버블·오프라인 배너). 텍스트로 찾아 fixed 루트를 제거.
  //    공고문 추출 토스트·모바일 하단탭은 callout 이 문서화하므로 유지한다.
  const killByText = (re) => {
    const all = Array.from(document.querySelectorAll("body *"));
    for (const el of all) {
      if (re.test(el.textContent || "")) {
        let p = el, root = null;
        while (p) { try { if (getComputedStyle(p).position === "fixed") root = p; } catch { /* */ } p = p.parentElement; }
        if (root) { root.remove(); return; }
      }
    }
  };
  try { killByText(/무엇이든 물어보세요|궁금한 점|고객센터 챗봇/); } catch { /* */ }
  try { killByText(/오프라인|인터넷 연결/); } catch { /* */ }
  // 2) CSS 수집
  let css = "";
  for (const sheet of Array.from(document.styleSheets)) {
    try { for (const rule of Array.from(sheet.cssRules)) css += rule.cssText + "\n"; } catch { /* skip */ }
  }
  // 3) body 복제 후 script/link 제거
  const body = document.body.cloneNode(true);
  body.querySelectorAll("script,link,noscript").forEach((n) => n.remove());
  return { css, bodyHtml: body.outerHTML, htmlClass: document.documentElement.className, lang: document.documentElement.lang || "ko" };
};

await rm(OUT, { recursive: true, force: true });
await mkdir(DOMDIR, { recursive: true });
const browser = await chromium.launch({ channel: CHANNEL, headless: true });
let savedCss = false;
const report = [];
for (const f of FRAMES) {
  for (const [vp, cfg] of Object.entries(VIEWPORTS)) {
    const ctx = await browser.newContext({ viewport: { width: cfg.width, height: cfg.height }, isMobile: !!cfg.isMobile, deviceScaleFactor: 1, colorScheme: "light" });
    if (!f.noAuth) await ctx.addInitScript((t) => localStorage.setItem("careertuner.auth", t), TOKEN);
    const page = await ctx.newPage();
    await page.goto(BASE + f.route, { waitUntil: "networkidle", timeout: 20000 });
    await page.waitForTimeout(1200);
    const snap = await page.evaluate(extract);
    if (!savedCss) { await writeFile(path.join(OUT, "_app.css"), snap.css, "utf8"); savedCss = true; }
    // DOM 만(css 없음). render-dom 이 런타임에 CSS 주입.
    const dom = `<!doctype html><html class="${snap.htmlClass}" lang="${snap.lang}"><head><meta charset="utf-8"></head>${snap.bodyHtml}</html>`;
    await writeFile(path.join(DOMDIR, `${f.id}-${vp}.html`), dom, "utf8");
    report.push(`${f.id}/${vp}: dom ${(dom.length / 1024).toFixed(0)}KB`);
    await ctx.close();
  }
}
await browser.close();
console.log(report.join("\n"));
