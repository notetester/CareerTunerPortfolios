# 검증 문서 안내

이 폴더는 “검증했다”는 선언이 아니라 어떤 코드 기준으로 무엇을 실행했고 어떤 조건에서 다시 확인해야
하는지를 기록한다. 테스트 raw output과 대형 결과물은 메인 저장소에 누적하지 않는다.

## 현재 문서와 데이터

| 항목 | 현재 위치 | 책임 |
| --- | --- | --- |
| 전 영역 시연 준비도 | [DEMO_READINESS_LEDGER.md](DEMO_READINESS_LEDGER.md) | A~F와 웹·모바일·데스크톱 기준점 |
| 기계 판독 재검증 범위 | [demo-readiness-checks.json](demo-readiness-checks.json) | 변경 경로와 재실행할 검사 연결 |
| C 영역 기능 검증 | [c-feature-verification.md](c-feature-verification.md) | 기능별 증분 검증 원장 |

## 검증 원장 필수 항목

- 기준 branch와 전체 commit SHA
- 실행 날짜와 환경
- 실행한 명령 또는 수동 시나리오
- 실제 결과와 실패·skip 사유
- 증거 파일 또는 workflow URL
- 관련 기능이 바뀌었을 때의 재검증 조건

새 PR이 기존 검증 범위와 무관하면 전체 검사를 반복하지 않는다. 변경 파일과 의존 관계를
`demo-readiness-checks.json`에 대조하고 영향받은 항목만 재검증한 뒤 기준 SHA를 갱신한다.

## 기록 경계

- 작은 체크리스트와 결과 요약: 이 폴더
- raw AI output과 benchmark artifact: `docs/ai-artifacts/` 서브모듈
- 사람이 읽는 장문 AI 분석: `docs/ai-reports/` 서브모듈
- CI 로그: workflow URL과 run ID만 기록
- 로컬 임시 결과: `.tmp/` 등 비추적 경로

파일명은 소문자 ASCII `kebab-case`를 사용한다. 이미지나 JSON에는 같은 이름의 원장 또는 README에서
생성자, 기준 SHA, 재생성 방법을 연결한다.

생성 환경·기준 SHA·재현 절차를 확인할 수 없는 독립 스크린샷은 검증 증거로 보존하지 않는다. 화면 증거가
필요하면 먼저 원장에 수동 시나리오와 기준점을 기록하고, 그 기록에서 이미지의 생성 조건을 직접 연결한다.
