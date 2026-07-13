# 공개 릴리스 검증 manifest

기준 일자: 2026-07-13

이 문서는 공개 후보를 조립할 때 확정된 provenance 수치만 기록합니다. SHA는 내용 정화로 재작성된 공개 이력과 원본 기준을 구분하기 위한 식별자이며, 아직 확정하지 않은 배포 결과를 통과로 표시하지 않습니다.

| 항목 | 확정 기준 |
| --- | --- |
| 원본 소스 baseline | `d00a57fc8d1e3499ba6c23acec498c47ac0d5d4c` |
| 정화된 `dev` baseline | `48f294d306e54d12d6f7085e08a417522c6f0c2e` |
| 정화 이력의 commit 수 | 1,830 |
| 게시 대상으로 보존한 ref 수 | 157 |
| 정화 이력의 도달 가능한 고유 blob 수 | 10,235 |
| 학습 문서 baseline | `eccc5e3f31042b8d09b23a067390299e243ff6b5` |
| 지식 projection baseline | `87b4986cbbeed88b057b706817050f4cf10c5cf6` |
| 공개 데모 projection baseline | `3784252bd5954e69a2e79d98384ef7501e2a40a4` |

## 게시 전 남은 게이트

- 현재 공개 overlay를 포함한 전체 ref secret scan
- 프런트엔드 typecheck, mock production build와 역할·인증·native 계약 테스트
- 기능 설명서 build와 dead-link 검사
- Pages 산출물의 루트, `/docs/`, `/Obsidian/` 경로 검사
- 게시 후 실제 Pages 응답과 주요 light/dark·반응형 화면 확인

각 게이트의 실행 결과는 실제 명령과 기준 SHA가 확정된 뒤에만 갱신합니다.
