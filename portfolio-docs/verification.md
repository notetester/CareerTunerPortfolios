# 검증 기준선

이 설명서는 최신화 시점을 숨기지 않습니다.

| 항목 | 기준 |
| --- | --- |
| 비공개 원본 소스 baseline | `d00a57fc8d1e3499ba6c23acec498c47ac0d5d4c` |
| 전 영역 실행 증거 baseline | `30a5511a` (PR #395) |
| 최신 표적 delta | PR #408 관리자 AI 상담 문구, PR #409 커뮤니티 데스크톱 폭 |
| 기준 일자 | 2026-07-13 |
| 프런트엔드 | typecheck, mock production build, 관리자·A~F demo readiness, 인증·native OAuth·Capacitor·deep-link 계약 |
| 백엔드 | 단위·통합 테스트와 DB patch 검증 |
| 공개 이력 | 모든 공개 branch/tag/보존 ref의 old→new commit 매핑, author/committer allowlist, 알려진 값 exact scan, 패턴 scan |
| 문서 | VitePress build와 dead-link 검사 |
| 배포 후 | Pages root, `/docs/`, `/Obsidian/`, SPA deep link와 주요 역할별 화면 |

검증 원칙은 "한 번 한 검사를 계속 반복"하는 것이 아니라 기준 SHA와 영향을 받은 경로를 기록하고, 새 변경이 생기면 관련 영역만 재검증하는 것입니다. 다만 이력 정화, 인증·권한, 공통 DB·라우팅처럼 전파 범위가 큰 변경은 전체 게이트를 다시 통과합니다.

외부 자격증명이나 실제 provider 상태가 필요한 항목은 코드 테스트 통과와 라이브 검증 완료를 구분해 표시합니다. SMS 발급 대기나 OAuth 운영 콘솔 설정을 코드 미구현으로도, 완료로도 과장하지 않습니다.
