# 공개 릴리스 검증 manifest

기준 일자: 2026-07-13

이 문서는 공개 후보를 다시 검증할 때 이미 확인한 범위를 반복하지 않도록 provenance, 자동 검증, 수동 QA와 남은 외부 게이트를 함께 기록합니다. SHA는 내용 정화로 재작성된 공개 이력과 원본 기준을 구분하기 위한 식별자입니다.

## 기준선과 provenance

| 항목 | 확정 기준 |
| --- | --- |
| 공개 대상 저장소 | `notetester/CareerTunerPortfolios` |
| 원본 소스 baseline | `d00a57fc8d1e3499ba6c23acec498c47ac0d5d4c` |
| 정화된 `dev` baseline | `48f294d306e54d12d6f7085e08a417522c6f0c2e` |
| 제품·플랫폼 검증 baseline | `e20cb1287512ba3476cbdc0306c0e69000a623be` |
| 정화 이력의 commit 수 | 1,830 |
| 원본 이력에서 보존한 ref 수 | 157(원본 head 136개 + tag 21개) |
| 정화 이력의 도달 가능한 고유 blob 수 | 10,235 |
| 학습 문서 baseline | `eccc5e3f31042b8d09b23a067390299e243ff6b5` |
| 지식 projection baseline | `87b4986cbbeed88b057b706817050f4cf10c5cf6` |
| 공개 데모 projection baseline | `3784252bd5954e69a2e79d98384ef7501e2a40a4` |
| 새 원격 fresh-clone 검증 baseline | `74fb51dd9121d3d471539367b1534204f7ac70a3` |
| 최종 이력 검증 보고서 SHA-256 | `513fd6738ba9d61d86cb23c63e7ea1158b945dd71b7eedad7b5f4fc377ee360c` |
| 원본→공개 commit map SHA-256 | `079b3a1424cc6e20c320bc503164d6337faa291507cdd85befaa5c48f76c89fc` |
| 새 원격 fresh-clone 검증 보고서 SHA-256 | `2f051a16fc5bbfcb2f0bf7f80857bc5c970ba8b9c2059533724503e6b227b98b` |

정화 baseline은 1,830개 commit, 157개 ref(일반 head 115개, archive head 21개, tag 21개), 10,235개 고유 blob을 대상으로 원본 commit의 1:1 매핑과 parent topology를 검증했습니다. 원본 보호 브랜치 `dev`, `main`, `master`, `live`는 공개 포트폴리오의 운영 브랜치와 충돌하지 않도록 각각 `archive/source-dev`, `archive/source-main`, `archive/source-master`, `archive/source-live`로 보존했습니다. 알려진 민감값 29개, commit/tag metadata, blob 경계 패턴, 금지 경로, 비공개 네트워크 식별자와 허용되지 않은 작성자 identity는 모두 0건이었습니다. 공식 Gitleaks 8.30.1 검사도 누출 0건으로 통과했습니다.

## 자동 검증 결과

### 프런트엔드와 공개 데모

- `npm ci`: 795개 package 설치, `npm audit` 취약점 0건
- TypeScript typecheck와 18개 계약 테스트: 총 19개 gate 통과
  - 관리자 권한, 지원 건, OAuth/native 설정, deep link, 모바일 플랫폼
  - 면접 모델 재시도, A~F 데모 준비 상태, 협업·자동 준비 pending 파일
  - 챗봇 계정 범위, 인증 세션 경계, 공개 역할 로그인, 장애 시 Sites fallback
- mock production build: 3,130개 module, PWA precache 111개(약 5.3 MiB) 생성 성공
- PWA navigation fallback에서 `/docs/`와 `/Obsidian/`을 분리하는 계약: 6/6 통과

### 백엔드

- MySQL 8 격리 DB 전체 테스트: 287개 suite, 1,711개 test, 실패 0, 오류 0, 의도적 skip 9
- 무캐시 clean 실행: 66.4초
- 잘못된 DB 자격증명 실패 시간: 80.8초 → 23.9초(테스트용 Hikari 연결 대기 3초)
- 검증에 사용한 격리 DB는 완료 후 삭제

### 기능 설명서와 Pages 산출물

- VitePress build 통과, 설명서 dependency `npm audit` 취약점 0건
- 복수형 경로 로컬 조립 산출물: 213개 파일, 7,863,179 bytes
- 실제 Pages 산출물: 212개 파일, 7,856,171 bytes, SHA-256 `84f265b9dd64301810e15eeaeb3ae7d857fe24938163b07eada4ff1d3d13f427`
- 로컬 `href`/`src` 링크 856개 검사, 누락 0건
- 산출물의 알려진 민감값 29개 0건, Gitleaks 누출 0건, 배포 secret pattern 0건
- 루트 데모와 `/docs/`, `/Obsidian/`, `/Obsidian/SecondBrain/`, `/Obsidian/Wiki/` 진입 파일 확인

### 새 공개 원격 fresh-clone 검증

- 검증 baseline 당시 공개 원격 `dev`와 `main`: 정본 작성자 `박성호 <hwangseongho52@gmail.com>`가 기록된 동일 SHA `74fb51dd9121d3d471539367b1534204f7ac70a3`
- 해당 baseline의 전체 163개 ref(일반 head 141개, tag 21개, PR head 1개), 1,844개 commit, 21,034개 tree, 10,349개 blob, annotated tag 8개 확인
- `git fsck` 오류와 도달 불가 객체 0건
- 알려진 민감값 29개, metadata 패턴, blob 경계, 금지 이력 경로, 잘못된 실명, 비정규 identity, 비공개 네트워크 식별자, Gitleaks 누출 모두 0건
- 실제 Pages 8개 경로의 HTTP 200과 배포 산출물 hash 일치 확인
- GitHub가 자동 생성했던 초기 commit과 교정 전 merge commit은 공개 ref 및 fresh clone에서 도달하지 않습니다. exact SHA를 아는 경우 GitHub commits API가 아직 객체를 반환하지만, 두 객체 모두 정화된 공개 내용이며 기존 단수형 저장소의 민감 SHA는 API에서도 반환되지 않음을 확인했습니다.

### Android·iOS·데스크톱

- Capacitor Android sync 통과: release-safe 네트워크 정책과 plugin 8개 확인
- Android `lintDebug testDebugUnitTest assembleDebug`: 512 tasks를 49.98초에 성공, unit 1/1 통과, lint 오류 0건(기존 warning 13건)
- debug APK: 12,012,162 bytes, APK Signature Scheme v2 확인, SHA-256 `AF9619B91BF6810D3B4295B9FFFC8EAA576BE7C2FC8D483D3BC400EBA3D57239`
- 데스크톱 Qt 6.11.1 / MinGW 13.1 / CMake 4.4 Release configure·build 성공, CTest 1/1 통과
- iOS는 Windows에서 실제 Xcode compile을 실행할 수 없어 native 설정·deep link·Associated Domains와 비실행 workflow 계약까지만 정적 검증

## 수동 브라우저 QA

- 일반 사용자와 관리자 원클릭 로그인, dashboard 진입과 mock 데이터 로딩 확인
- 비로그인 `/admin/policies` 접근은 로그인으로 복귀하고, 일반 사용자는 403 화면으로 차단되는 fail-closed 동작 확인
- 관리자 모바일 메뉴 drawer, 사용자 모바일 bottom navigation, desktop과 390×844 mobile에서 수평 overflow 없음 확인
- 사용자·관리자 화면의 light/dark theme 확인
- PWA 방문 뒤에도 `/docs/`와 `/Obsidian/`이 SPA fallback에 가로채이지 않음을 확인
- `/docs/`, `/Obsidian/`, `/Obsidian/SecondBrain/`, `/Obsidian/Wiki/`의 desktop/mobile 표시 확인

## 원격 전환 최종 결과

- 완료: `notetester/CareerTunerPortfolios`에 정화된 allowlist branch/tag/archive ref와 공개 overlay 게시
- 완료: 공개 후보 PR #1 검토·병합 후 정본 identity merge로 `dev`와 `main` 정렬
- 완료: 새 원격 fresh clone에서 모든 도달 가능 ref의 이력·secret·identity·네트워크·Gitleaks 재검증
- 완료: GitHub Pages 루트, manifest, service worker, `/docs/`, `/docs/ai-integration`, `/Obsidian/`, `/Obsidian/SecondBrain/`, `/Obsidian/Wiki/` 응답과 artifact hash 확인
- 완료: 정본 실명 identity와 이 manifest가 포함된 SHA `fda689b00fd5aaf68ea942902fb0c9b7ad164da0`를 Pages run `29254689829`에서 다시 build·deploy하고 두 job의 성공 확인
- 완료: 최신 정본 `dev` SHA `0d81d013fbbd5bf4835db30016a151604ccaece2`를 Pages run `29261204607`에서 build·deploy하고 라이브 8개 경로의 HTTP 200 확인
- 완료: `github-pages` 환경의 deployment branch 정책을 `dev`만 허용하도록 정리
- 완료: 기존 `notetester/CareerTunerPortfolio`를 private rollback 저장소로 전환하고 새 `notetester/CareerTunerPortfolios`가 public임을 확인

최초 성공 배포 run `29252450813`의 head는 교정 전 merge SHA였지만 tree가 검증 baseline과 동일했고, 후속 run `29254689829`와 `29261204607`에서 정본 SHA 및 `dev` 배포 기록까지 일치시켰습니다. 이 문단을 추가하는 후속 commit은 Pages 조립 대상이 아닌 검증 manifest만 바꾸므로 배포 산출물에는 영향을 주지 않습니다. 공개 포트폴리오 전환 게이트는 모두 완료됐으며, 이후 새 PR은 변경된 기능과 그 의존 경계만 이 기준선에 추가 검증합니다.
