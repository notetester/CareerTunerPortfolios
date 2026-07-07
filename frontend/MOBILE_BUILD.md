# 모바일 앱 빌드 가이드 (PWA · Android · iOS)

CareerTuner 프런트엔드를 PWA / Android(APK) / iOS 앱으로 빌드·테스트하는 방법.
전략 배경은 [docs/planning/모바일 고려.md](../docs/planning/모바일%20고려.md) 참고.
한 React 코드베이스(`src/`)와 같은 빌드 산출물(`dist/`)을 Android·iOS·PWA가 공유한다.

## 0. 모드 / 데이터

- **mock 데모 모드**(`VITE_USE_MOCK=true`, `.env.mock`): 백엔드 없이 mock 데이터로 동작. 자체완결 APK·웹 데모용. `npm run dev:mock` 으로 로컬 실행.
- **백엔드 연동**: `VITE_API_BASE_URL=http://<도달가능주소>:8080/api` 로 빌드(백엔드 CORS 허용 필요). 미지정 시 상대경로 `/api`.

## 1. PWA (모든 플랫폼, 빌드 불필요)

`npm run build` → 정적 호스팅. 브라우저에서 "홈 화면에 추가"로 설치형 실행.
- iOS Safari: 공유 → **홈 화면에 추가** (별도 빌드/서명/계정 전혀 불필요).
- Android Chrome: 설치 배너 또는 메뉴 → 앱 설치.

## 2. Android (APK/AAB) — Windows/macOS/Linux 모두 가능

사전: JDK 21, Android SDK(platform 36, build-tools 36, platform-tools). `ANDROID_HOME`/`JAVA_HOME` 설정.

`android/` 는 이제 repo에 포함되는 정식 네이티브 프로젝트다. 푸시 알림, 파일·카메라·마이크 권한,
딥링크, 앱 아이콘/스플래시, `AndroidManifest.xml`, 릴리즈 서명 설정을 여기서 직접 관리한다.

### 2.0 원클릭 데모 APK (권장)

웹 데모(`https://notetester.github.io/CareerTunerDemo/`)와 **같은 mock 빌드를 설치형 앱으로** 한 번에 만든다.
build → sync → assemble 을 한 명령으로 묶고, 산출물을 알기 쉬운 경로로 복사한다.

```bash
npm run demo:apk
# = scripts/build-demo-apk.mjs : vite build --mode mock --base / → cap sync android → assembleDebug
# 산출물: frontend/dist-apk/CareerTuner-demo.apk
```

- 옵션: `npm run demo:apk -- --open`(Android Studio 로 열기, SDK 자동 설치 경로) · `--clean`(Gradle 산출물 청소) · `--skip-web`(웹 재빌드 생략).
- **Android SDK 가 없으면** 웹·네이티브 프로젝트만 준비하고 멈추며, (A) `--open` (B) `ANDROID_HOME` 설정 (C) CI 태그 빌드 중 하나를 안내한다.
- SDK 없이 받기만 하려면 §2.3 의 CI 릴리즈를 쓴다.

### 2.1 수동 단계 (세밀한 제어가 필요할 때)

```bash
# 빌드(웹 mock 빌드 + 동기화 + 디버그 APK)
npm run mobile:sync     # = vite build --mode mock && cap sync android
npm run mobile:apk      # = cd android && gradlew assembleDebug
# 산출물: android/app/build/outputs/apk/debug/app-debug.apk

# 릴리즈 산출물(서명 설정 필요)
npm run mobile:apk:release
npm run mobile:aab
```

테스트: BlueStacks 창에 APK 드래그&드롭 → 실행. (디버그 서명 자동, 사이드로드 OK)

### 2.2 푸시/서명 로컬 파일

- FCM 푸시: Firebase 콘솔에서 Android 앱(`com.careertuner.app`)을 만든 뒤 `google-services.json` 을
  `frontend/android/app/google-services.json` 으로 둔다. 실제 파일은 gitignore 대상이며,
  커밋 가능한 형식 예시는 `frontend/android/app/google-services.example.json` 이다.
- 릴리즈 서명: `frontend/android/app/release-signing.example.properties` 를
  `frontend/android/app/release-signing.properties` 로 복사해 실제 keystore 값을 채운다.
  또는 `CAREERTUNER_ANDROID_STOREFILE`, `CAREERTUNER_ANDROID_STOREPASSWORD`,
  `CAREERTUNER_ANDROID_KEYALIAS`, `CAREERTUNER_ANDROID_KEYPASSWORD` 환경변수를 쓴다.
- 릴리즈 서명이 없으면 `assembleRelease`/`bundleRelease` 는 명시적으로 실패한다. 디버그 APK는 기존처럼 자동 디버그 서명을 사용한다.

### 2.3 로컬 SDK 없이 받기 — CI 릴리즈

`demo-*` 또는 `v*` 태그를 푸시하면 `.github/workflows/android-release.yml` 가 mock 데모 APK 를 빌드해
GitHub Release 에 첨부한다. 팀원은 클론/빌드 없이 Releases 페이지에서 받아 BlueStacks 에 드롭하면 된다.

```bash
git tag demo-apk-<설명> && git push origin demo-apk-<설명>
# 또는: Actions 탭 → "Release Android demo APK" → Run workflow
```

### 2.4 푸시·딥링크 테스트

- **푸시(FCM)**: §2.2 의 **실제 `google-services.json`** 이 있어야 기기 토큰이 발급된다(example 파일로는 등록 불가).
  앱에서 설정 → 알림 → 푸시를 켜면 FCM 토큰이 백엔드에 등록되고, 발송 시 알림 채널
  (`ct_alerts`/`ct_alerts_sound`/`ct_alerts_vibrate`/`ct_alerts_silent`)이 수신자의 소리/진동 설정을 반영한다.
  알림을 탭하면 FCM `data.url` 경로로 앱 내 이동한다.
- **딥링크**: 에뮬레이터/실기기에 adb 로 VIEW 인텐트를 보내 확인한다. 커스텀 스킴·App Link 둘 다 같은 앱 내 경로로 이동해야 한다.

```bash
adb shell am start -a android.intent.action.VIEW -d "careertuner://applications"
adb shell am start -a android.intent.action.VIEW -d "https://careertuner.kr/community"
```

## 3. iOS — **macOS + Xcode 필수** (Windows 빌드 불가)

iOS 빌드/실행은 Apple 정책상 macOS에서만 가능하다. 이 저장소에는 의존성(`@capacitor/ios`)과
CI 워크플로가 준비되어 있어, **Mac 또는 macOS CI만 확보하면 한 번에 빌드**된다.

### 3.1 서명/계정 필요 여부

| 목적 | 서명 | Apple 계정 |
| --- | --- | --- |
| **iOS 시뮬레이터** 테스트 | 불필요 | 불필요 (무료) |
| **내 아이폰 실기기** 설치 | 필요(개발 서명) | **무료 Apple ID** 가능 (프로필 7일마다 만료 → 재설치) |
| TestFlight / 앱스토어 배포, 푸시 등 | 필요 | **Apple Developer $99/년** |

→ 개발/테스트는 **무서명(시뮬레이터)** 또는 **무료 Apple ID(실기기)** 로 충분. $99는 배포부터.

### 3.2 Mac에서 로컬 빌드 (시뮬레이터, 무서명)

```bash
cd frontend
npm ci
npm run ios:sync          # = vite build --mode mock && cap sync ios
npx cap add ios           # 최초 1회 (ios/ 는 gitignore라 재생성), pod install 포함
npx cap open ios          # Xcode 열기 → 시뮬레이터 선택 → ▶ 실행 (서명 0)
# 또는 CLI:
# xcodebuild -workspace ios/App/App.xcworkspace -scheme App \
#   -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
#   CODE_SIGNING_ALLOWED=NO build
```

### 3.3 무Mac: GitHub Actions macOS 러너로 빌드 검증

`.github/workflows/ios-build.yml` (수동 실행: Actions 탭 → "Build iOS demo (unsigned simulator)" → Run workflow).
- macOS 러너에서 `cap add ios` → `cap sync ios` → **무서명 시뮬레이터 빌드** 후 `.app` 아티팩트 업로드.
- Apple 계정 불필요. iOS 빌드가 깨지지 않는지 지속 검증용.
- ⚠ 비공개 저장소에서 macOS 러너는 분당 과금 10배 → 수동 실행으로만 둠.
- ⚠ 업로드된 `.app` 은 **Mac 시뮬레이터에서만 실행** 가능(실기기는 서명 필요, Windows 실행 불가).

### 3.4 실기기/배포가 필요해지면

- 실기기 테스트: Xcode에 무료 Apple ID 로그인 → 자동 서명(Personal Team) → 케이블 연결 후 실행(7일 유효).
- 배포: Apple Developer($99) 가입 → CI에 서명 인증서/프로비저닝 추가(예: fastlane match, Codemagic, Ionic Appflow).

## 4. 공통 메모

- 네이티브 프로젝트 중 `android/` 는 커밋 대상, `ios/` 는 아직 재생성 대상이다.
- 앱 아이콘: `npm run gen:icons` (`scripts/generate-icons.mjs`, `public/icons/*` 생성).
- 데이터: mock 데모는 **사용자 앱 전 도메인 + 관리자 콘솔 전 도메인**을 채운다(백엔드 없이 모든 화면이 데이터가 있는 듯 동작). 공통 계약은 `src/app/lib/mock/registry.ts`, 도메인별 핸들러는 `src/app/lib/mock/domains/*`(+`domains/admin/*`), 핵심 흐름은 `index.ts`. 새 엔드포인트는 해당 도메인 모듈 routes 에 `{ method, pattern, handler }` 한 줄을 추가한다(additive). 등록 안 된 엔드포인트만 `api()` 가 "데모 미제공"으로 처리한다(로그인은 아무 값이나 통과; 관리자 화면은 /admin 경로로 접근).
