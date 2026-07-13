# 공개 릴리스 검증 manifest

기준 일자: 2026-07-13

이 문서는 공개 후보를 다시 검증할 때 이미 확인한 범위를 반복하지 않도록 provenance, 자동 검증, 수동 QA와 남은 외부 게이트를 함께 기록합니다. SHA는 내용 정화로 재작성된 공개 이력과 원본 기준을 구분하기 위한 식별자입니다.

## 기준선과 provenance

| 항목 | 확정 기준 |
| --- | --- |
| 원본 소스 baseline | `d00a57fc8d1e3499ba6c23acec498c47ac0d5d4c` |
| 정화된 `dev` baseline | `48f294d306e54d12d6f7085e08a417522c6f0c2e` |
| 제품·플랫폼 검증 baseline | `e20cb1287512ba3476cbdc0306c0e69000a623be` |
| 정화 이력의 commit 수 | 1,830 |
| 게시 대상으로 보존한 ref 수 | 157 |
| 정화 이력의 도달 가능한 고유 blob 수 | 10,235 |
| 학습 문서 baseline | `eccc5e3f31042b8d09b23a067390299e243ff6b5` |
| 지식 projection baseline | `87b4986cbbeed88b057b706817050f4cf10c5cf6` |
| 공개 데모 projection baseline | `3784252bd5954e69a2e79d98384ef7501e2a40a4` |
| 최종 이력 검증 보고서 SHA-256 | `513fd6738ba9d61d86cb23c63e7ea1158b945dd71b7eedad7b5f4fc377ee360c` |
| 원본→공개 commit map SHA-256 | `079b3a1424cc6e20c320bc503164d6337faa291507cdd85befaa5c48f76c89fc` |

정화 baseline은 1,830개 commit, 157개 ref(일반 head 115개, archive head 21개, tag 21개), 10,235개 고유 blob을 대상으로 원본 commit의 1:1 매핑과 parent topology를 검증했습니다. 알려진 민감값 29개, commit/tag metadata, blob 경계 패턴, 금지 경로, 비공개 네트워크 식별자와 허용되지 않은 작성자 identity는 모두 0건이었습니다. 공식 Gitleaks 8.30.1 검사도 누출 0건으로 통과했습니다.

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
- 조립 산출물: 213개 파일, 7,862,050 bytes
- 로컬 `href`/`src` 링크 856개 검사, 누락 0건
- 산출물의 알려진 민감값 29개 0건, Gitleaks 누출 0건, 배포 secret pattern 0건
- 루트 데모와 `/docs/`, `/Obsidian/`, `/SecondBrain/`, `/Wiki/` 진입 파일 확인

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
- `/docs/`, `/Obsidian/`, `/SecondBrain/`, `/Wiki/`의 desktop/mobile 표시 확인

## 아직 남은 외부 게이트

- 기존 공개 저장소를 안전한 rollback용 private legacy로 보존하고, 숨은 `refs/pull/*`이 없는 새 공개 저장소로 전환
- 정화된 allowlist branch/tag/archive ref와 overlay branch 게시, PR 생성·검토·`dev` 병합
- 새 원격을 fresh clone하여 모든 도달 가능 ref의 이력·secret·identity·네트워크 검사를 다시 통과
- 실제 GitHub Pages 배포 SHA와 루트, `/docs/`, `/Obsidian/`, `/SecondBrain/`, `/Wiki/` 응답 확인

위 외부 게이트는 원격 전환과 배포가 실제로 완료된 뒤에만 통과로 갱신합니다. 이후 새 PR은 변경된 기능과 그 의존 경계만 이 기준선에 추가 검증합니다.
