# 데모 · 릴리즈 가이드

CareerTuner 의 데모 산출물(웹 데모 / Android APK / iOS)을 만들고 배포하는 방법.
세 채널 모두 **mock 데모 모드**(`VITE_USE_MOCK=true`) 빌드를 사용하므로 백엔드 없이 동작한다
(로그인에 아무 이메일/비밀번호나 입력하면 데모 계정 "김데모"로 진입).

| 채널 | 트리거 | 산출물 위치 |
| --- | --- | --- |
| 웹 데모 (GitHub Pages) | `dev` 에 push (자동) | <https://notetester.github.io/CareerTunerDemo/> |
| Android APK | **`v*` 또는 `demo-*` 태그 push** (자동) | [GitHub Releases](https://github.com/notetester/CareerTuner/releases) |
| iOS 시뮬레이터 빌드 | Actions 수동 실행 | Actions 아티팩트 (`.app`) |

---

## 1. Android APK 릴리즈 — 태그만 푸시하면 끝

```bash
git tag demo-apk-3            # 또는 v0.2.0 등 (v* / demo-* 패턴)
git push origin demo-apk-3
```

약 3분 뒤 [Releases](https://github.com/notetester/CareerTuner/releases) 에 자동으로 올라온다:

- `CareerTuner-demo-<태그>.apk` — 데모 APK. **BlueStacks 창에 드래그&드롭**하면 설치된다.
- `CareerTuner-web-demo-<태그>.zip` — 같은 빌드의 웹 번들(정적 호스팅용).

동작 방식: `.github/workflows/android-release.yml` 이 ubuntu 러너에서
`build:mock → cap add android → cap sync → gradlew assembleDebug` 를 수행하고
`softprops/action-gh-release` 로 Release 를 생성·첨부한다(prerelease 표시).

주의사항:

- **디버그 서명**이므로 사이드로드(테스트) 전용이다. 스토어 배포용 아님.
- 태그가 가리키는 커밋에 워크플로 파일이 있어야 한다(2026-06 이후 커밋이면 모두 해당).
- Actions 탭의 수동 실행(workflow_dispatch) 버튼은 워크플로가 **main 에 머지된 뒤** 노출된다.
- 잘못 만든 태그/릴리즈는 Releases 페이지에서 삭제 후 `git push origin :refs/tags/<태그>` 로 태그도 지운다.

## 2. 웹 데모 (GitHub Pages) — dev 머지만 하면 자동

`frontend/**` 변경이 `dev` 에 머지되면 `.github/workflows/deploy-demo.yml` 이
typecheck → mock 빌드 → 시크릿 스캔 후, 공개 저장소
[CareerTunerDemo](https://github.com/notetester/CareerTunerDemo) 에 빌드 결과를 push 한다.
→ 데모 주소: **<https://notetester.github.io/CareerTunerDemo/>**

- 본 저장소(CareerTuner)는 키/비밀번호가 포함돼 비공개이므로, **빌드 산출물만** 공개 저장소로 내보낸다.
- 내보내기 전에 dist 에서 DB 주소·시크릿 패턴을 grep 으로 검사하며, 발견 시 배포를 중단한다.
- 인증: 저장소 시크릿 `DEMO_REPO_TOKEN` (CareerTunerDemo 에 Contents R/W 권한의 fine-grained PAT).
- typecheck 가 깨지면 배포가 중단된다 → PR 전에 `npm run typecheck` 확인.

## 3. iOS — 시뮬레이터 무서명 빌드

Actions 탭 → **Build iOS demo (unsigned simulator)** → Run workflow (main 머지 후 버튼 노출).
macOS 러너가 무서명(`CODE_SIGNING_ALLOWED=NO`) 시뮬레이터 빌드 후 `.app` 아티팩트를 업로드한다.

- Apple 계정/서명 불필요. 단, `.app` 실행은 Mac 의 iOS 시뮬레이터에서만 가능.
- 비공개 저장소에서 macOS 러너는 분당 과금 10배라 **수동 실행으로만** 둔다.
- 실기기 설치/스토어 배포 등 자세한 분기는 [frontend/MOBILE_BUILD.md](../frontend/MOBILE_BUILD.md) 참고.

## 4. 데모 데이터(mock) 범위

mock 레지스트리는 `frontend/src/app/lib/mock/` 에 있다. 현재 **인증 + C 영역
(홈/대시보드/취업 분석/적합도)** 이 채워져 있고, 미등록 엔드포인트는 "데모 미제공" 안내가 뜬다.
자기 도메인 화면을 데모에 포함하려면 `mock/index.ts` 의 `routes` 배열에 핸들러를 추가하면 된다.

## 5. 자주 겪는 문제

| 증상 | 원인/해결 |
| --- | --- |
| CI 에서 `The Capacitor CLI requires NodeJS >=22` | 워크플로 Node 버전이 22 미만. setup-node `node-version: 22` 확인 |
| 데모 배포가 typecheck 에서 실패 | `cd frontend && npm run typecheck` 로 에러 파일 확인 후 해당 담당자가 수정 |
| 릴리즈에 APK 가 안 보임 | Actions 탭에서 해당 태그 run 의 실패 스텝 확인. 태그 커밋에 워크플로 존재 여부 확인 |
| 웹 데모 화면이 흰 화면 | base 경로 문제. `VITE_PUBLIC_BASE=/CareerTunerDemo/` 로 빌드됐는지 확인 |
