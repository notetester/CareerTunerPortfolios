// C 영역 스토리보드 자동 캡처.
// 실행 중인 데모 mock 서버(VITE_USE_MOCK=true, 기본 http://localhost:5173)를
// 헤드리스 Chrome 으로 순회하며 웹/앱 두 뷰포트로 PNG 를 저장한다.
//   - 인증: localStorage 'careertuner.auth' 에 데모 토큰을 addInitScript 로 주입(앱 코드 실행 전).
//   - 산출: ../assets/<id>-<viewport>.png (뷰포트 컷) + <id>-<viewport>-full.png (전체 스크롤).
// GUI 조작 없음. 사용자 브라우저와 분리된 독립 컨텍스트.
import { chromium } from "playwright-core";
import { mkdir } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import path from "node:path";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const OUT = path.resolve(__dirname, "../assets");
const BASE = process.env.BASE_URL || "http://localhost:5173";
const CHANNEL = process.env.BROWSER_CHANNEL || "chrome"; // chrome | msedge
const TOKEN = JSON.stringify({ accessToken: "demo-access", refreshToken: "demo-refresh" });

const VIEWPORTS = {
  web: { width: 1440, height: 1024, deviceScaleFactor: 2, isMobile: false },
  app: { width: 390, height: 844, deviceScaleFactor: 2, isMobile: true, hasTouch: true },
};

// 캡처 매니페스트. section 은 지원건 상세 서브탭(C 적합도 패널은 'fit').
const FRAMES = [
  // ── 사용자 여정 ──
  { id: "01-home", route: "/", group: "user", title: "홈 대시보드" },
  { id: "02-dashboard", route: "/dashboard", group: "user", title: "취업분석 대시보드" },
  { id: "03-analysis", route: "/analysis", group: "user", title: "취업분석·장기경향" },
  { id: "04-detail-overview", route: "/applications/1", group: "user", title: "지원건 상세(진입)" },
  { id: "05-fit", route: "/applications/1/fit", group: "user", title: "적합도 분석 + 전략 + 학습추천" },
  // ── 관리자(C 소유) ──
  { id: "06-admin-home", route: "/admin/home", group: "admin", title: "관리자 홈" },
  { id: "07-admin-analytics", route: "/admin/analytics", group: "admin", title: "분석 통계" },
  { id: "08-admin-fit", route: "/admin/fit-analysis", group: "admin", title: "적합도 분석 관리" },
  { id: "09-admin-prompts-fit", route: "/admin/prompts/fit-analysis", group: "admin", title: "적합도 프롬프트 운영" },
  { id: "10-admin-prompts-analytics", route: "/admin/prompts/analytics", group: "admin", title: "장기분석 프롬프트 운영" },
  // ── 상태 분기 ──
  { id: "11-login-required", route: "/applications/1/fit", group: "state", title: "로그인 필요 상태", noAuth: true },
];

async function capture() {
  await mkdir(OUT, { recursive: true });
  const browser = await chromium.launch({ channel: CHANNEL, headless: true });
  const results = [];
  for (const frame of FRAMES) {
    for (const [vp, vpCfg] of Object.entries(VIEWPORTS)) {
      const ctx = await browser.newContext({
        viewport: { width: vpCfg.width, height: vpCfg.height },
        deviceScaleFactor: vpCfg.deviceScaleFactor,
        isMobile: vpCfg.isMobile,
        hasTouch: vpCfg.hasTouch || false,
        colorScheme: "light",
      });
      if (!frame.noAuth) {
        await ctx.addInitScript((t) => localStorage.setItem("careertuner.auth", t), TOKEN);
      }
      const page = await ctx.newPage();
      let status = "ok";
      try {
        await page.goto(BASE + frame.route, { waitUntil: "networkidle", timeout: 20000 });
        await page.waitForTimeout(1100); // mock 220ms 지연 + 차트/애니메이션 settle
        await page.screenshot({ path: path.join(OUT, `${frame.id}-${vp}.png`) });
        await page.screenshot({ path: path.join(OUT, `${frame.id}-${vp}-full.png`), fullPage: true });
      } catch (err) {
        status = `ERR: ${err.message.split("\n")[0]}`;
      }
      results.push(`${frame.id}/${vp}: ${status}`);
      console.log(`  ${frame.id}/${vp}: ${status}`);
      await ctx.close();
    }
  }
  await browser.close();
  console.log("\nDONE\n" + results.join("\n"));
}

capture().catch((e) => {
  console.error("CAPTURE FAILED:", e);
  process.exit(1);
});
