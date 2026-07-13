# 공개 데모 · 로컬 앱 빌드 가이드

CareerTuner 공개 복제본은 GitHub Pages에서 웹 데모, 기능 설명서와 지식 지도를 하나의 정적 산출물로 제공합니다. 웹 데모는 `VITE_USE_MOCK=true`인 명시적 mock 모드이므로 운영 백엔드와 자격증명 없이 동작합니다.

| 채널 | 실행 방식 | 산출물 |
| --- | --- | --- |
| 웹 데모 | `dev` push 또는 수동 workflow 실행 | <https://notetester.github.io/CareerTunerPortfolios/> |
| 기능 설명서 | 웹 데모와 함께 build | <https://notetester.github.io/CareerTunerPortfolios/docs/> |
| 지식 지도 | 공개 검토본을 정적 복사 | <https://notetester.github.io/CareerTunerPortfolios/Obsidian/> |
| Android/iOS | 로컬 명령으로 mock 또는 backend 연동 build | `frontend/`의 네이티브 프로젝트 산출물 |

## 1. GitHub Pages

`.github/workflows/pages.yml`은 다음 순서로 공개 산출물을 만듭니다.

1. 프런트엔드 typecheck와 역할·인증·모바일 계약 테스트 실행
2. `VITE_PUBLIC_BASE=/CareerTunerPortfolios/`인 네트워크 독립 mock build
3. VitePress 기능 설명서 audit와 build
4. `/docs/`와 `/Obsidian/`을 같은 Pages artifact에 조립
5. 필수 route와 자격증명·내부 네트워크 패턴 검사
6. pull request에서는 build만 검증하고, `dev` push 또는 수동 실행에서만 배포

로컬에서 동일한 웹 산출물을 확인하려면:

```bash
cd frontend
npm ci
npm run typecheck
$env:VITE_DEMO_MODE="true"
$env:VITE_USE_MOCK="true"
$env:VITE_PUBLIC_BASE="/CareerTunerPortfolios/"
npm run build
```

기능 설명서는 별도로 검증합니다.

```bash
cd portfolio-docs
npm ci
npm audit --audit-level=high
npm run docs:build
```

## 2. Android mock APK

JDK 21과 Android SDK가 준비된 환경에서 다음 명령으로 백엔드가 필요 없는 debug APK를 만듭니다.

```bash
cd frontend
npm ci
npm run demo:apk
```

산출물은 `frontend/dist-apk/CareerTuner-demo.apk`입니다. debug 서명이므로 테스트와 포트폴리오 시연용이며 스토어 배포용이 아닙니다. 세부 설정은 [`frontend/MOBILE_BUILD.md`](../frontend/MOBILE_BUILD.md)를 참고하세요.

## 3. 실제 백엔드 연동 앱

번들 앱은 build 시점에 `VITE_API_BASE_URL=https://<reachable-host>/api`를 주입합니다. callback URL, CORS, OAuth app link/universal link와 인증서 서명은 해당 배포 환경에서 별도로 설정해야 하며 값을 저장소에 커밋하지 않습니다.

mock은 포트폴리오와 장애 독립 시연을 위한 별도 모드입니다. 운영 서비스가 정상일 때 mock endpoint가 실제 API보다 우선하지 않도록 `VITE_USE_MOCK`을 명시적으로 켠 build에서만 활성화합니다.

## 4. 자주 겪는 문제

| 증상 | 확인할 항목 |
| --- | --- |
| Pages 화면이 흰 화면 | `VITE_PUBLIC_BASE=/CareerTunerPortfolios/`와 asset URL 확인 |
| pull request에서 deploy가 생략됨 | 정상 동작. pull request는 build와 검증만 수행 |
| APK build가 SDK 단계에서 멈춤 | JDK 21, `ANDROID_HOME`, platform/build-tools 설치 확인 |
| backend 연동 앱에서 API 호출 실패 | HTTPS, CORS, `VITE_API_BASE_URL`과 네이티브 네트워크 정책 확인 |
