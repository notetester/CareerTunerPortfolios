# B 인계 — 공고 추출 워커 트랜잭션 분리 요청 (F 실측 보고)

> **보관 문서:** 2026-07-02 인계 당시의 실측 기록이다. 근본 수정은 이후 반영됐으며 현재 결함 목록으로 사용하지 않는다.

작성: F (2026-07-02) · 대상: B (`applicationcase` 도메인) · 당시 상태: 요청

F는 이 변경의 **소비자**입니다(챗봇 온보딩이 추출 상태를 폴링). 아래는 실측 사실과 F 관점의 영향이고,
**구현 방식 판단은 B 몫**입니다. 궁금한 점은 F에게.

## 증상 (실측)

공고 추출이 실제로는 1초 내 끝나는데, **다른 커넥션(챗봇 폴링·목록 조회 등)에는 SUCCEEDED가
~2분 늦게 보입니다.** 그동안 폴링은 RUNNING만 보고, `ANALYZING` 상태 전이는 아예 안 보입니다
(커밋 순간 READY로 점프).

| 근거 | 값 |
| --- | --- |
| case 65 (2026-07-02 밤, 실사용) | `markExtractionSucceeded` 문장시각 21:28:34 vs 커밋 21:29:52 (`application_case.updated_at`) — **78초 불가시** |
| case 66 (재현, zrepro 계정) | `finished_at` 22:40:30 (시작 1초 뒤)인데 타 커넥션 가시화는 **~110~125초 후** (15초 간격 폴링 실측) |

사용자 영향: 챗봇 온보딩이 `추출대기`("잠시 후 다시 보내주세요")를 2분간 반복 → 프론트 자동 넛지
소진 → 스피너/락아웃처럼 보이는 UX. (F는 넛지 백오프 + 안내로 stopgap 적용했으나 근본은 이것.)

## 원인

`ApplicationCaseExtractionWorker.completeSucceeded()` (`ApplicationCaseExtractionWorker.java:160-225`)가
**단일 `transactionTemplate.execute`** 안에서 다음을 전부 수행:

1. `markExtractionSucceeded` (추출 row SUCCEEDED)
2. case 메타데이터 update
3. `autoPipelineService.runAfterExtractionPass(...)` — **직무분석 LLM + 기업분석 LLM + FIT + 면접프렙**
   (`ApplicationCaseAutoPipelineService.java:81-109`, 내부에서 ANALYZING→READY 전이 포함)
4. 성공 알림

LLM 호출 80~120초 동안 트랜잭션이 열려 있어 1·2·3의 모든 상태가 커밋까지 불가시.
(READ COMMITTED에서 당연한 결과. 부수 효과로 DB 커넥션도 그 시간만큼 점유.)

## 제안

- `markExtractionSucceeded`(+case 메타)를 **먼저 커밋** → LLM 파이프라인(`runAfterExtractionPass`)은
  **별도 트랜잭션(들)** 로.
- 분리 시 주의: 지금은 파이프라인 예외가 전체 롤백으로 "SUCCEEDED 마킹까지" 되돌리지만, 분리 후엔
  **파이프라인 실패의 상태 전이를 따로 처리**해야 합니다(예: ANALYZING→FAILED 또는 READY-미분석 등 —
  판단은 B 몫). 오토파이프라인 내부의 `markAnalyzingIfRunnable`/`markReadyAfterAnalysis` 전이가
  비로소 외부에 실시간 노출된다는 점도 함께.

## 효과

- 추출 성공이 초 단위로 가시화 → 챗봇 온보딩 좀비 대기 소멸
- ANALYZING이 실제로 보이므로 진행 UI가 정직해짐
- F의 넛지 stopgap(백오프 ~2.7분) 제거/축소 가능

## 재현 방법

zrepro 계정(아무 신규 register 계정) → 챗봇 "면접해줘" → 직무/기술 답변 → URL로
`POST /api/application-cases/from-job-posting` (`sourceType=URL`) → `selectedCaseId` 실어
"공고 링크로 올렸어요" → "진행 상황 알려줘" 반복하며
`SELECT status, finished_at FROM application_case_extraction WHERE application_case_id=?` 를
별도 커넥션에서 관찰. `finished_at`이 찍힌 뒤에도 status 가시화가 파이프라인 종료까지 지연됨.
