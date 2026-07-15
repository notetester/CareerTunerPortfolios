# 모바일 앱 빌드 가이드 (PWA · Android · iOS)

CareerTuner 프런트엔드를 PWA / Android(APK) / iOS 앱으로 빌드·테스트하는 방법.
전략 배경은 [docs/planning/모바일 고려.md](../docs/planning/모바일%20고려.md) 참고.
한 React 코드베이스(`src/`)와 같은 빌드 산출물(`dist/`)을 Android·iOS·PWA가 공유한다.

## 0. 모드 / 데이터

- **mock 데모 모드**(`VITE_USE_MOCK=true`, `.env.mock`): 백엔드 없이 mock 데이터로 동작. 자체완결 APK·웹 데모용. `npm run dev:mock` 으로 로컬 실행.
- **백엔드 연동**: 번들 앱은 `VITE_API_BASE_URL=https://<도달가능주소>/api` 로 빌드한다(백엔드 CORS 허용 필요). 미지정 시 상대경로 `/api`.
  HTTPS 문서에서 HTTP API를 호출하는 mixed content는 허용하지 않는다. 로컬 HTTP가 꼭 필요하면 아래의 명시적 live-reload debug 절차를 사용한다.

## 1. PWA (모든 플랫폼, 네이티브 빌드 불필요)

`npm run build` → 정적 호스팅. 브라우저에서 "홈 화면에 추가"로 설치형 실행.
- iOS Safari: 공유 → **홈 화면에 추가** (별도 빌드/서명/계정 전혀 불필요).
- Android Chrome: 설치 배너 또는 메뉴 → 앱 설치.

## 2. Android (APK/AAB) — Windows/macOS/Linux 모두 가능

사전: JDK 21, Android SDK(platform 36, build-tools 35.0.0, platform-tools). `ANDROID_HOME`/`JAVA_HOME` 설정.

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
npm run mobile:sync     # = vite build --mode mock && secure-default cap sync android
npm run mobile:apk      # = cd android && gradlew assembleDebug
# 산출물: android/app/build/outputs/apk/debug/app-debug.apk

# 릴리즈 산출물(서명 설정 필요)
npm run mobile:apk:release
npm run mobile:aab
```

테스트: BlueStacks 창에 APK 드래그&드롭 → 실행. (디버그 서명 자동, 사이드로드 OK)

#### 네트워크 프로필

`npm run native:sync -- android`의 기본값은 release-safe 프로필이다. 생성되는 WebView origin은 HTTPS이고,
mixed content와 외부 live-reload `server.url`이 없다. `assembleRelease`/`bundleRelease`는 Gradle에서 생성 설정,
메인 manifest, network security config를 다시 검사하며 하나라도 느슨하면 서명 전에 실패한다.

실기기 LAN live reload만 예외다. `run-app-livereload.bat`가 아래 세 값을 함께 지정하고 **debug APK**를 만든다.
공인 HTTP 호스트는 debug에서도 거부한다. 일반 번들·CI·release 동기화에서는 이 값을 설정하지 않는다.

```powershell
$env:CAP_SYNC_MODE = "debug"
$env:CAP_SERVER_URL = "http://192.168.0.20:5173"
$env:CAP_ALLOW_CLEARTEXT = "true"
npm run native:sync -- android
cd android
.\gradlew.bat assembleDebug
```

Android main/release manifest와 network security config는 cleartext를 거부한다. `src/debug` source set만
로컬 live reload를 위해 OS cleartext를 열며, WebView mixed content는 debug에서도 계속 차단한다.

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

`.github/workflows/android-release.yml`은 태그와 수동 입력에 따라 두 빌드 모드를 구분한다.

| 트리거 | 빌드 명령 | 연결 대상 |
| --- | --- | --- |
| `demo-*`, `v*` 태그 | `npm run build:mock` | 백엔드 없이 동작하는 자체완결 mock 데이터 |
| `live-*` 태그 | `npm run build:aws` | `frontend/.env.aws`의 `https://careertuner.example.com/api` 운영 백엔드 |
| 수동 실행 | `mode=mock` 또는 `mode=live` 선택 | 선택한 모드와 동일 |

두 모드 모두 팀 self-hosted Linux runner에서 release APK와 같은 웹 번들 ZIP을 빌드해 GitHub Release에
prerelease로 첨부한다. 팀원은 클론이나 로컬 Android SDK 없이 APK를 받아 폰 또는 BlueStacks에 설치할 수
있다. `live` APK는 실제 계정으로 로그인하고 AWS 웹 서비스와 같은 데이터를 사용하므로 장애 독립 데모가
필요한 경우에는 반드시 `mock` APK를 선택한다.

APK는 `ANDROID_DEMO_*` GitHub secrets의 **시연 전용 release 키**로 서명한다. 이 키는 verified App Link
검증과 팀 시연 설치를 위한 것이며 Play Store 최종 서명 키가 아니다. secrets가 없는 fork PR에서는 이
릴리즈 워크플로를 실행하지 않고, `frontend-ci.yml`이 비밀값 없이 manifest·workflow·지문 검증 로직만 테스트한다.
`live` 모드는 빌드 전에 시연 키의 SHA-256 지문이 Repository variable과 배포된
`https://careertuner.example.com/.well-known/assetlinks.json`에 모두 존재하는지도 확인하며, 불일치하면 릴리즈를
중단한다.

```bash
git tag demo-apk-<설명> && git push origin demo-apk-<설명>
# 실서버 연동 APK
git tag live-apk-<설명> && git push origin live-apk-<설명>
# 또는: Actions 탭 → "Release Android demo APK" → Run workflow
# mode에서 mock 또는 live 선택
```

### 2.4 푸시·딥링크·verified App Link 테스트

- **푸시(FCM)**: §2.2 의 **실제 `google-services.json`** 이 있어야 기기 토큰이 발급된다(example 파일로는 등록 불가).
  앱에서 설정 → 알림 → 푸시를 켜면 FCM 토큰이 백엔드에 등록되고, 발송 시 알림 채널
  (`ct_alerts`/`ct_alerts_sound`/`ct_alerts_vibrate`/`ct_alerts_silent`)이 수신자의 소리/진동 설정을 반영한다.
  알림을 탭하면 FCM `data.url` 경로로 앱 내 이동한다.
- **일반 딥링크**: 앱 내부 화면 이동은 `careertuner://` 커스텀 스킴을 사용한다.
- **네이티브 OAuth callback**: `https://careertuner.example.com/auth/callback` verified App Link만 사용한다.
  `careertuner://auth/callback`은 다른 앱도 같은 스킴을 등록할 수 있으므로 파서에서 거부한다.
- **네이티브 소셜 계정 연결 결과**: `https://careertuner.example.com/profile/detail` verified App Link만 사용한다.
  성공은 `socialLinked=KAKAO|NAVER|GOOGLE`(mock은 `socialMock=1` 추가), 실패는
  `socialLinkError=social_login_cancelled|social_login_failed`만 허용한다. 이외의 query, 중복 값,
  두 결과의 혼합, 하위 경로 및 같은 query를 붙인 커스텀 스킴은 파서에서 거부한다.

```bash
adb shell am start -a android.intent.action.VIEW -d "careertuner://applications"

# 디버그 라우팅 단위 확인: 컴포넌트를 명시해 OS 도메인 검증만 우회한다.
# 이 명령의 성공은 verified App Link 배포 성공을 뜻하지 않는다.
adb shell am start -W -n com.careertuner.app/.MainActivity \
  -a android.intent.action.VIEW \
  -d "https://careertuner.example.com/auth/callback?error=social_login_cancelled"

adb shell am start -W -n com.careertuner.app/.MainActivity \
  -a android.intent.action.VIEW \
  -d "https://careertuner.example.com/profile/detail?socialLinked=KAKAO"
```

운영 verified App Link의 필수 전제는 다음과 같다.

1. 실제 release keystore로 `cd android && ./gradlew signingReport`를 실행해 `SHA-256` 지문을 확인한다.
2. GitHub Repository variable `ANDROID_APP_LINK_SHA256_CERT_FINGERPRINTS`에 지문을
   `AA:BB:...` 형식으로 등록한다. 인증서 회전 중이면 쉼표로 여러 지문을 등록할 수 있다.
3. `deploy-web.yml`이 `npm run build:verified-app-links`로
   `dist/.well-known/assetlinks.json`을 생성·배포한다. 지문이 없거나 잘못되면 배포는 실패한다.
4. 배포 뒤 실제 release 서명 앱에서 검증 상태를 확인한다.

```bash
curl -fsS https://careertuner.example.com/.well-known/assetlinks.json
adb shell pm verify-app-links --re-verify com.careertuner.app
adb shell pm get-app-links com.careertuner.app
```

표준 debug keystore 지문은 누구나 재현할 수 있으므로 운영 `assetlinks.json`에 절대 게시하지 않는다.
`android-release.yml`은 keystore에서 계산한 실제 SHA-256 지문이 Repository variable에 포함되는지 확인한 뒤
공개 `assetlinks.json`에도 같은 지문이 실제 배포됐는지 확인하고 `assembleRelease`를 실행한다.
어느 한쪽이라도 불일치하면 APK를 만들지 않는다. 그래도 코드·빌드 성공만으로 OS 검증 완료를
주장할 수는 없다. 최종 완료 조건은 시연 release APK 설치 후 `pm get-app-links`의 verified 상태 확인이다.
Play Store 최종 키로 전환할 때는 해당 지문을 Repository variable과 배포된 `assetlinks.json`에 함께 반영한다.

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
npx cap add ios           # 최초 1회 (ios/ 는 gitignore라 재생성), pod install 포함
npm run ios:sync          # = vite build --mode mock && secure-default cap sync ios
npm run ios:configure-links # App.entitlements 생성 + App target Debug/Release에 자동 연결
npx cap open ios          # Xcode 열기 → 시뮬레이터 선택 → ▶ 실행 (서명 0)
# 또는 CLI:
# xcodebuild -project ios/App/App.xcodeproj -scheme App \
#   -sdk iphonesimulator -destination 'generic/platform=iOS Simulator' \
#   CODE_SIGNING_ALLOWED=NO build
```

### 3.3 무Mac: GitHub Actions macOS 러너로 빌드 검증

`.github/workflows/ios-build.yml` (수동 실행: Actions 탭 → "Build iOS demo (unsigned simulator)" → Run workflow).
- macOS 러너에서 `cap add ios` → `cap sync ios` → Associated Domains 자동 적용 →
  **무서명 시뮬레이터 빌드** 후 `.app` 아티팩트 업로드.
- Apple 계정 불필요. iOS 빌드가 깨지지 않는지 지속 검증용.
- ⚠ 비공개 저장소에서 macOS 러너는 분당 과금 10배 → 수동 실행으로만 둠.
- ⚠ 업로드된 `.app` 은 **Mac 시뮬레이터에서만 실행** 가능(실기기는 서명 필요, Windows 실행 불가).

### 3.4 실기기/배포가 필요해지면

- 실기기 테스트: Xcode에 무료 Apple ID 로그인 → 자동 서명(Personal Team) → 케이블 연결 후 실행(7일 유효).
- 배포: Apple Developer($99) 가입 → CI에 서명 인증서/프로비저닝 추가(예: fastlane match, Codemagic, Ionic Appflow).

### 3.5 Universal Link 등록·검증

iOS도 Android와 같은 두 HTTPS 반환 경로(`/auth/callback`, `/profile/detail`)만 앱으로 연다.
`ios/`를 매번 재생성하므로 `npm run ios:configure-links`가 다음을 멱등 적용한다.

- `App/App.entitlements`: `applinks:careertuner.example.com`
- App target Debug/Release: `CODE_SIGN_ENTITLEMENTS = App/App.entitlements`
- `Info.plist`: 일반 화면 딥링크용 `CFBundleURLTypes/CFBundleURLSchemes = careertuner`

Apple Developer의 10자리 Team ID는 비밀값이 아니며 GitHub Repository variable
`IOS_APP_LINK_TEAM_IDS`에 등록한다. 여러 팀 서명을 전환하는 기간에는 쉼표로 구분한다.
웹 배포는 이 값과 고정 bundle ID `com.careertuner.app`으로
`/.well-known/apple-app-site-association`을 생성한다. Team ID가 아직 없으면 Android 배포를
막지 않되 `details: []`인 deny-all AASA를 게시하므로, **iOS OAuth 반환은 준비 미완료 상태**다.

```bash
gh variable set IOS_APP_LINK_TEAM_IDS --body ABCDE12345
curl -i https://careertuner.example.com/.well-known/apple-app-site-association
codesign -d --entitlements :- /path/to/CareerTuner.app
```

AASA 응답은 HTTPS 200, 무리다이렉트, `Content-Type: application/json`이어야 한다. 배포 워크플로가
생성 파일과 공개 응답의 바이트 일치까지 검사한다. 최종 완료 조건은 Team ID가 포함된 AASA 배포,
Associated Domains가 포함된 실제 개발/배포 서명 앱 설치, 실기기에서 두 URL이 Safari가 아니라 앱으로
열리고 잘못된 query는 앱 파서에서 거부되는지 확인하는 것이다. 무서명 시뮬레이터 빌드 성공만으로는
Universal Link가 검증됐다고 판단하지 않는다.

확장자가 없는 AASA는 표준 Nginx에서 `application/octet-stream`으로 응답할 수 있다. 웹 배포는 전체
`dist` 교체 전에 AASA 한 파일만 시험 설치해 공개 MIME과 내용을 검사하고, 실패하면 기존 AASA를
즉시 원복한 뒤 중단한다. 이때 `careertuner.example.com`의 단일 TLS server 블록을 확인한 뒤 AASA exact
`location`을 멱등 설치하고 `nginx -t`가 통과할 때만 reload한다. server 블록이 없거나 둘 이상이라
안전하게 결정할 수 없거나 설정 검증에 실패하면 원래 Nginx 파일을 복원하고 웹 배포를 중단한다.

## 4. 공통 메모

- 네이티브 프로젝트 중 `android/` 는 커밋 대상, `ios/` 는 아직 재생성 대상이다.
- 앱 아이콘: `npm run gen:icons` (`scripts/generate-icons.mjs`, `public/icons/*` 생성).
- 데이터: mock 데모는 **사용자 앱 전 도메인 + 관리자 콘솔 전 도메인**을 채운다(백엔드 없이 모든 화면이 데이터가 있는 듯 동작). 공통 계약은 `src/app/lib/mock/registry.ts`, 도메인별 핸들러는 `src/app/lib/mock/domains/*`(+`domains/admin/*`), 핵심 흐름은 `index.ts`. 새 엔드포인트는 해당 도메인 모듈 routes 에 `{ method, pattern, handler }` 한 줄을 추가한다(additive). 등록 안 된 엔드포인트만 `api()` 가 "데모 미제공"으로 처리한다(로그인은 아무 값이나 통과; 관리자 화면은 /admin 경로로 접근).
