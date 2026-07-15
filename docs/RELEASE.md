# 데모·릴리즈 가이드

> 현재 동작 기준: 2026-07-14 `dev` 런타임과 `.github/workflows/`.

CareerTuner는 운영 웹, 백엔드 없이 동작하는 mock 데모, Android APK, iOS 시뮬레이터 빌드, Windows 데스크톱 ZIP을 서로 다른 채널로 제공한다. mock과 live 산출물을 혼동하지 않는 것이 가장 중요하다.

| 채널 | 데이터 모드 | 트리거 | 산출물 |
| --- | --- | --- | --- |
| 운영 웹 | live | `dev`의 `frontend/**` 변경 | <https://careertuner.example.com> |
| 공개 웹 데모 | mock | `dev`의 `frontend/**` 변경 | <https://notetester.github.io/CareerTunerDemo/> |
| Android `demo-*`/`v*` | mock | 태그 push 또는 수동 실행 | GitHub Release의 APK·웹 ZIP |
| Android `live-*` | live | 태그 push 또는 수동 실행 | GitHub Release의 APK·웹 ZIP |
| iOS 시뮬레이터 | mock | 수동 실행 | Actions `.app` artifact |
| Windows 데스크톱 | live 기본 | `desktop-v*` 태그 push 또는 `desktop-release.yml` 수동 실행 | GitHub Release의 Windows x64 ZIP |

환경 주소와 전환 방식은 [환경 프로파일](ENVIRONMENTS.md), 실제 백엔드 연결 점검은 [실데이터 구동 런북](operations/real-data-runbook.md)을 따른다.

## 운영 웹과 공개 mock 데모

`frontend/**`가 `dev`에 반영되면 두 self-hosted 배포가 독립적으로 실행된다.

- `.github/workflows/deploy-web.yml`: 기본 Vite 빌드와 App Links 메타데이터를 생성해 EC2 nginx 정적 루트를 원자적으로 교체한다. 같은 출처의 `/api`가 운영 백엔드로 연결된다.
- `.github/workflows/deploy-demo.yml`: `VITE_USE_MOCK=true`, `VITE_PUBLIC_BASE=/CareerTunerDemo/`로 빌드한 뒤 비밀값 패턴을 검사하고 공개 `CareerTunerDemo` 저장소에 산출물만 보낸다.
- 공개 데모 배포의 typecheck는 현재 경고만 남기고 배포를 계속한다. PR의 정식 품질 판정은 `frontend-ci.yml`과 로컬 `npm run typecheck` 결과를 사용한다.
- mock 레지스트리는 `frontend/src/app/lib/mock/domains/`의 A~F 사용자·관리자 기능을 폭넓게 제공한다. 등록되지 않은 요청은 운영 데이터로 조용히 우회하지 않고 데모 미제공 오류를 표시한다.

배포에 필요한 저장소 secret은 실제 값을 문서나 로그에 기록하지 않는다. 공개 데모 push에는 `DEMO_REPO_TOKEN`, 운영 웹 배포에는 EC2 접속 secret과 App Links용 공개 repository variable이 필요하다.

## Android APK

### 태그로 만들기

```bash
# 백엔드 없는 자체완결 데모
git tag demo-2026-07-14
git push origin demo-2026-07-14

# AWS 운영 백엔드 연결
git tag live-2026-07-14
git push origin live-2026-07-14
```

`v*`와 `demo-*`는 mock, `live-*`는 `frontend/.env.aws`를 사용하는 live 빌드다. 수동 실행에서는 `mode` 입력으로 같은 선택을 한다.

워크플로 `.github/workflows/android-release.yml`은 self-hosted Linux runner에서 다음을 수행한다.

1. Node 22, JDK 21, Android platform 36과 build-tools 35.0.0 준비
2. 의존성 설치와 mock/live 웹 빌드
3. Capacitor Android 동기화
4. 시연용 release key로 APK 서명
5. 인증서 지문, App Link, network security, 앱 권한 정책 검증
6. GitHub prerelease 생성과 APK·웹 ZIP 첨부

산출물 이름은 `CareerTuner-<mode>-<tag>.apk`, `CareerTuner-web-<mode>-<tag>.zip` 형식이다. 이 release key는 사이드로드와 App Link 시연을 위한 키이며 Play Store 최종 서명 키가 아니다.

필수 서명 secret이나 `ANDROID_APP_LINK_SHA256_CERT_FINGERPRINTS`가 없거나 배포된 `assetlinks.json`과 지문이 맞지 않으면 빌드는 실패해야 한다. 값을 임시로 우회해 APK를 배포하지 않는다.

> 수동 실행 버튼은 워크플로가 GitHub의 기본 브랜치에 존재할 때 표시된다. 현재 기본 브랜치는 `dev`다. 태그 실행은 태그가 가리키는 커밋에 워크플로가 있으면 동작한다.

### 설치 후 서버 전환

live APK의 초기 주소는 `https://careertuner.example.com/api`다. 개발·진단 빌드에서는 설정 → 계정 설정 → 서버 주소에서 허용된 프리셋으로 바꿀 수 있다. arbitrary URL은 개발 옵션과 명시적 허용 없이는 받지 않는다.

## iOS 시뮬레이터

Actions에서 **Build iOS demo (unsigned simulator)**를 수동 실행한다. macOS runner가 mock 웹을 빌드하고 iOS 프로젝트를 재생성한 뒤 Associated Domains와 권한 문구를 확인하고 무서명 시뮬레이터 `.app`을 만든다.

- Apple Developer 서명은 필요 없지만 산출물은 Mac의 iOS Simulator 전용이다.
- 실기기·TestFlight·App Store 배포에는 별도 서명과 Apple 설정이 필요하다.
- 비공개 저장소의 macOS runner 비용 때문에 자동 push 트리거를 두지 않는다.
- 상세 로컬 절차는 [모바일 빌드 가이드](../frontend/MOBILE_BUILD.md)를 따른다.

## 릴리즈 전 확인

```bash
cd frontend
npm ci
npm run typecheck
npm run build:mock
npm run test:native-config
npm run test:deep-link-runtime
```

- mock 산출물에 실제 DB 주소, 운영 API key, client secret, 사용자 데이터가 없는지 확인한다.
- live 산출물은 `/api/health`, 로그인, OAuth/deep link, 파일 업로드, AI provider 폴백을 실제 환경에서 확인한다.
- 태그가 의도한 커밋을 가리키는지 `git show --no-patch <tag>`로 먼저 확인한다.
- 잘못 만든 릴리즈는 GitHub Release와 원격 태그를 모두 정리한다. 이미 배포된 서명 키나 비밀값이 노출됐다면 삭제만 하지 말고 즉시 회전한다.

## 자주 겪는 문제

| 증상 | 확인할 것 |
| --- | --- |
| `Capacitor CLI requires NodeJS >=22` | setup-node와 로컬 Node가 22 이상인지 확인 |
| APK 서명 단계 실패 | 4개 서명 secret과 인증서 alias/password 조합 확인 |
| App Link 검증 실패 | 배포된 `/.well-known/assetlinks.json`의 package/fingerprint와 release key 대조 |
| 웹 데모가 흰 화면 | `VITE_PUBLIC_BASE=/CareerTunerDemo/`와 SPA `404.html` 생성 확인 |
| live APK가 mock처럼 보임 | 태그가 `live-*`인지, 빌드 로그의 resolved mode가 `live`인지 확인 |
| 공개 데모에 기능이 없음 | 해당 endpoint가 `frontend/src/app/lib/mock/domains/`에 등록됐는지 확인 |
