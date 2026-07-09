// 데모/테스트용 Android APK 원클릭 빌드.
//   사용: npm run demo:apk          (frontend/ 에서 실행)
//   동작: 웹을 mock(데모) 모드로 빌드 → 커밋된 Capacitor android 플랫폼 동기화
//         → 디버그 APK 조립 → frontend/dist-apk/CareerTuner-demo.apk 로 복사.
//
// 목적: 웹 GitHub Pages 데모(https://notetester.github.io/CareerTunerDemo/)와 "같은 mock 빌드"를
//       그대로 설치형 Android 앱으로 패키징한다. 백엔드가 없어도 mock 레지스트리(VITE_USE_MOCK=true)
//       로 동작하므로, 사이드로드 후 모든 데모 화면을 데이터가 있는 것처럼 클릭하며 시연·테스트할 수 있다.
//       (로그인은 아무 이메일/비밀번호나 입력하면 데모 계정으로 통과)
//
// 범위: 이 한 줄짜리 명령이 build → sync → assemble 까지 묶어 한 번에 처리한다.
//
// 옵션:
//   --skip-web   웹 재빌드 생략(dist 가 이미 최신일 때)
//   --clean      Gradle 빌드 산출물만 청소(android/ 플랫폼 파일은 유지)
//   --open       APK 조립 대신 Android Studio 로 프로젝트 열기(SDK 자동 설치 경로)
import { spawnSync } from "node:child_process";
import { existsSync, mkdirSync, copyFileSync, readFileSync, rmSync } from "node:fs";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), ".."); // frontend/
const androidDir = resolve(root, "android");
const isWin = process.platform === "win32";
const flags = new Set(process.argv.slice(2));

const KNOWN_FLAGS = new Set(["--skip-web", "--clean", "--open"]);
for (const f of flags) if (!KNOWN_FLAGS.has(f)) console.warn(`⚠ 알 수 없는 옵션 무시: ${f}`);

function section(title) {
  console.log(`\n${"=".repeat(62)}\n▶ ${title}\n${"=".repeat(62)}`);
}

/** 명령을 현재 셸에서 실행하고, 실패하면 즉시 종료한다. */
function run(cmd, opts = {}) {
  console.log(`\n$ ${cmd}${opts.cwd && opts.cwd !== root ? `   (cwd: ${opts.cwd})` : ""}`);
  const res = spawnSync(cmd, { stdio: "inherit", shell: true, cwd: opts.cwd ?? root, env: opts.env ?? process.env });
  if (res.status !== 0) {
    console.error(`\n✗ 명령 실패 (exit ${res.status ?? "?"}): ${cmd}`);
    process.exit(res.status ?? 1);
  }
}

/** 디렉터리가 단순 존재가 아니라 실제 SDK(마커 하위폴더 보유)인지. 빈/잔존 폴더 false positive 방지. */
function isSdk(dir) {
  return !!dir && existsSync(dir) && ["platform-tools", "build-tools", "platforms"].some((m) => existsSync(resolve(dir, m)));
}

/** Android SDK 가 사용 가능한지(=gradle assembleDebug 가능한지) 추정한다. */
function androidSdkAvailable() {
  // 환경변수/기본 경로: 존재만으로는 부족, 실제 SDK 마커가 있어야 인정한다.
  if (isSdk(process.env.ANDROID_HOME) || isSdk(process.env.ANDROID_SDK_ROOT)) return true;
  // cap 가 SDK 를 찾았을 때만 기록하는 local.properties(sdk.dir) → 신뢰(gradle 이 이 경로를 사용).
  const lp = resolve(androidDir, "local.properties");
  if (existsSync(lp) && /^\s*sdk\.dir=/m.test(readFileSync(lp, "utf8"))) return true;
  // OS 기본 설치 경로
  const candidates = [];
  if (isWin && process.env.LOCALAPPDATA) candidates.push(resolve(process.env.LOCALAPPDATA, "Android", "Sdk"));
  const home = process.env.HOME || process.env.USERPROFILE;
  if (home) candidates.push(resolve(home, "Library/Android/sdk"), resolve(home, "Android/Sdk"));
  return candidates.some(isSdk);
}

// ── 1) 웹 빌드 (mock 데모 모드) ────────────────────────────────────────────
// --mode mock → .env.mock(VITE_USE_MOCK=true) 로드. VITE_USE_MOCK 은 환경변수로도 명시(belt & suspenders).
// --base / → 네이티브 WebView 는 앱 루트에서 서빙되므로 항상 루트 베이스로 빌드한다
//            (웹 데모의 /CareerTunerDemo/ 서브패스 설정이 환경에 새어들어와도 무시).
section("1/4 · 웹 빌드 (mock 데모 모드)");
if (flags.has("--skip-web")) {
  console.log("⏭  --skip-web: 기존 dist 사용");
} else {
  run("npx vite build --mode mock --base /", { env: { ...process.env, VITE_USE_MOCK: "true", VITE_PUBLIC_BASE: "/" } });
}

// ── 2) Capacitor Android 플랫폼 준비 ──────────────────────────────────────
// android/ 는 커밋된 네이티브 프로젝트다. 권한/딥링크/서명/아이콘을 보존해야 하므로 자동 생성하지 않는다.
section("2/4 · Capacitor Android 플랫폼 동기화");
if (!existsSync(androidDir) || !existsSync(resolve(androidDir, "settings.gradle"))) {
  console.error(
    "\n✗ frontend/android 네이티브 프로젝트가 없습니다.\n" +
      "   이제 Android 플랫폼은 자동 생성 대상이 아니라 repo에 커밋되는 대상입니다.\n" +
      "   최신 dev를 받았는지 확인한 뒤 다시 실행하세요.\n",
  );
  process.exit(1);
}
if (flags.has("--clean")) {
  console.log("🧹 --clean: Gradle 산출물 청소");
  rmSync(resolve(androidDir, ".gradle"), { recursive: true, force: true });
  rmSync(resolve(androidDir, "build"), { recursive: true, force: true });
  rmSync(resolve(androidDir, "app", "build"), { recursive: true, force: true });
}
run("npx cap sync android"); // 최신 dist + 플러그인 반영

// --open: SDK 없이도 Android Studio 에서 빌드/설치하도록 프로젝트만 열고 종료.
if (flags.has("--open")) {
  section("Android Studio 열기 (--open)");
  run("npx cap open android");
  console.log("\nℹ Android Studio 에서 ▶(Run) 또는 Build > Build APK 로 빌드하세요.");
  process.exit(0);
}

// ── 3) Android SDK 확인 ───────────────────────────────────────────────────
section("3/4 · Android SDK 확인");
if (!androidSdkAvailable()) {
  console.error(
    `\n⚠  Android SDK 를 찾지 못해 APK 조립(gradle assembleDebug)을 진행할 수 없습니다.\n` +
      `   웹 빌드와 네이티브 프로젝트(frontend/android)는 준비됐습니다. 아래 중 하나로 마무리하세요.\n\n` +
      `   (A) Android Studio 로 열어 빌드 — SDK 가 없으면 자동 설치 안내가 뜹니다:\n` +
      `         npm run demo:apk -- --open        (또는  npx cap open android)\n\n` +
      `   (B) SDK 설치 후 ANDROID_HOME 지정하고 재실행 (새 터미널):\n` +
      (isWin
        ? `         setx ANDROID_HOME "%LOCALAPPDATA%\\Android\\Sdk"\n`
        : `         export ANDROID_HOME="$HOME/Android/Sdk"\n`) +
      `         npm run demo:apk\n\n` +
      `   (C) 로컬 SDK 없이 CI 로 빌드 — 'demo-*' 태그를 푸시하면 GitHub Actions(android-release.yml)가\n` +
      `       APK 를 만들어 Releases 에 첨부합니다:\n` +
      `         git tag demo-apk-<설명> && git push origin demo-apk-<설명>\n` +
      `         (또는 Actions 탭 → "Release Android demo APK" → Run workflow)\n`,
  );
  process.exit(1);
}
console.log("✓ Android SDK 감지됨");

// ── 4) 디버그 APK 조립 ────────────────────────────────────────────────────
section("4/4 · 디버그 APK 조립");
// win32: shell:true 는 cmd.exe /c 로 실행되며, cmd 는 cwd 의 bare 배치파일을 PATH 에서 못 찾는다.
// 반드시 `.\\` 접두사로 현재 폴더의 gradlew.bat 를 명시한다(없으면 'not recognized'로 실패).
run(isWin ? ".\\gradlew.bat assembleDebug --warning-mode none" : "./gradlew assembleDebug --warning-mode none", {
  cwd: androidDir,
});

const apkSrc = resolve(androidDir, "app/build/outputs/apk/debug/app-debug.apk");
if (!existsSync(apkSrc)) {
  console.error(`\n✗ APK 산출물을 찾지 못했습니다: ${apkSrc}`);
  process.exit(1);
}
const outDir = resolve(root, "dist-apk");
mkdirSync(outDir, { recursive: true });
const apkOut = resolve(outDir, "CareerTuner-demo.apk");
copyFileSync(apkSrc, apkOut);

section("완료");
console.log(`✅ 데모 APK 생성: ${apkOut}`);
console.log(`   설치(테스트): BlueStacks 창에 위 .apk 를 드래그&드롭 → 실행`);
console.log(`   로그인: 아무 이메일/비밀번호나 입력 → 데모 계정으로 진입 (백엔드 불필요)`);
