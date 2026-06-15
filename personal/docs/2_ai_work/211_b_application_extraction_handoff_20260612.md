# 211 B 공고문 추출 안정화 인계 메모

## 기본 정보

- 날짜: 2026-06-12
- 브랜치: `SHIN-SANG-HOON`
- 작업 범위: B 파트 지원건, 공고문 추출, 추출 상태, 알림, 목록/상세 표시
- 현재 목적: 집 PC에서 같은 브랜치를 받아 이어서 QA와 후속 수정을 진행한다.

## 오늘 반영된 핵심 변경

- 공고문 추출을 즉시 처리 흐름에서 비동기 작업 흐름으로 전환했다.
- `application_case_extraction` 테이블을 추가해서 추출 작업 상태를 별도로 관리한다.
- 추출 상태는 `QUEUED`, `RUNNING`, `SUCCEEDED`, `FAILED`를 사용한다.
- 추출 실패 시 지원건은 삭제하지 않고 남긴다.
- 실패 작업은 사용자가 수동으로 재시도한다.
- 완료/실패 결과는 알림과 토스트로 확인할 수 있게 했다.
- 목록/상세 화면에서 `추출 중`, `추출 실패`, `추출 완료` 상태를 볼 수 있게 했다.
- `/applications/new`는 추출 시작 후 지원건을 먼저 만들고, 추출 작업을 백그라운드로 진행하는 흐름이다.
- OpenAI 공고문 추출 기본 타임아웃을 90초에서 300초로 늘렸다.

## DB 적용 상태

로컬 `team1_db`에는 아래 패치를 적용했다.

```text
backend/src/main/resources/db/patches/20260612_application_case_extraction_active_guard.sql
```

확인된 상태:

- `application_case_extraction` 테이블 존재
- `active_status_marker` 생성 컬럼 존재
- `uk_case_extraction_active` unique index 존재
- `uk_case_extraction_active` 구성은 `application_case_id`, `active_status_marker`

이 unique index는 같은 지원건에 진행 중 작업(`QUEUED`, `RUNNING`)이 1개만 생기도록 막는다.
완료/실패 작업은 `active_status_marker`가 `NULL`이므로 여러 이력이 남을 수 있다.

집 PC나 다른 DB에서 이어갈 때는 위 SQL 패치를 다시 적용해야 한다.
적용 후 확인 SQL:

```sql
SHOW TABLES LIKE 'application_case_extraction';

SHOW COLUMNS FROM application_case_extraction
LIKE 'active_status_marker';

SHOW INDEX FROM application_case_extraction
WHERE Key_name = 'uk_case_extraction_active';
```

## 오늘 겪은 DB 이슈와 결론

처음 패치 파일은 `application_case_extraction` 테이블이 이미 있다고 가정해서 기존 DB에서 실패했다.
그래서 패치 파일에 `CREATE TABLE IF NOT EXISTS application_case_extraction`을 추가했다.

이후 외래키 오류가 있었고, 최종적으로 진행 중 작업 중복 방지용 생성 컬럼을 아래 방식으로 정리했다.

```sql
active_status_marker TINYINT GENERATED ALWAYS AS (
    CASE WHEN status IN ('QUEUED', 'RUNNING') THEN 1 ELSE NULL END
) STORED
```

기존의 `active_application_case_id` 방식은 사용하지 않는다.

## 검증한 것

직접 통과 확인:

```powershell
cd backend
.\gradlew.bat test --tests com.careertuner.applicationcase.service.OpenAiPropertiesTest
```

결과:

```text
BUILD SUCCESSFUL
```

이 테스트는 OpenAI 공고문 추출 기본 타임아웃이 300초인지 확인한다.

이전 검증에서 프론트 build/typecheck는 통과했지만, Vite 경고가 남아 있었다.

- 큰 chunk 경고
- `toast.tsx` static/dynamic import 혼용 경고

둘 다 빌드 실패는 아니고, 기존 구조에서 발생하던 경고일 가능성이 높다.

## 집에서 이어갈 순서

1. 브랜치 받기

```powershell
git fetch origin
git switch SHIN-SANG-HOON
git pull origin SHIN-SANG-HOON
```

2. DB 패치 적용

```text
backend/src/main/resources/db/patches/20260612_application_case_extraction_active_guard.sql
```

3. 백엔드 실행

```powershell
cd backend
.\gradlew.bat bootRun
```

4. 프론트 실행

```powershell
cd frontend
npm run dev
```

5. QA 시작

```text
/applications/new
```

## 우선 QA 체크리스트

- `/applications/new`에서 공고문 추출 시작 시 지원건이 즉시 생성되는지 확인한다.
- 추출 중 다른 페이지로 이동하거나 새로고침해도 상태가 복구되는지 확인한다.
- 추출 성공 후 목록/상세에 `추출 완료`가 표시되는지 확인한다.
- 추출 실패 시 지원건이 삭제되지 않고 `추출 실패`로 남는지 확인한다.
- 실패 상태에서 재시도 버튼이 정상 동작하는지 확인한다.
- 완료/실패 알림이 토스트와 알림함 양쪽에 남는지 확인한다.
- 즐겨찾기/보관 필터가 기존처럼 동작하는지 확인한다.
- 기존 공고 분석/기업 분석 진입이 깨지지 않았는지 확인한다.

## 추가로 결정한 방향

공고일은 실제 공고에서 명확하지 않거나 `접수기간 시작일`과 의미가 섞일 수 있다.
제품 관점에서는 아래 방향이 더 낫다.

- 공고일 입력/표시는 제거한다.
- 대신 지원건 등록일은 기존 `application_case.created_at`을 사용해서 보여준다.
- AI는 마감일만 추출해서 `deadline_date`에 적용한다.
- `posting_date` 컬럼은 당장 삭제하지 않고, 우선 화면에서 사용하지 않는 방향으로 줄인다.

아직 이 변경은 구현하지 않았다.
다음 작업으로 별도 적용하면 된다.

## 남은 개선 후보

- 마감일 추출 프롬프트에 `접수기간/방법`, `지원기간`, `제출기한`, `마감일`의 종료일을 `deadlineDate`로 추출하라고 명시한다.
- 목록 화면의 추출 상태 조회는 현재 케이스 수에 비례해 개별 조회한다. 데이터가 많아지면 bulk status API로 개선한다.
- Vite 큰 chunk 경고와 `toast.tsx` import 경고는 별도 정리한다.
- 전체 검증으로 백엔드 전체 테스트, 프론트 typecheck, 프론트 production build를 다시 돌린다.

## 현재 주의사항

- 백엔드 서버는 타임아웃 300초 변경을 반영하려면 재시작해야 한다.
- 집 PC DB에는 오늘 적용한 SQL이 자동 반영되지 않을 수 있다.
- `application_case_extraction` 패치 실행 시 DBeaver에서는 전체 스크립트 실행을 사용한다.
- DBeaver의 `DROP TEMPORARY TABLE` 경고는 임시 테이블 삭제 확인이며, 적용 실패의 핵심 원인이 아니다.
- `application_case_extraction doesn't exist` 오류는 보통 앞의 `CREATE TABLE`이 실패한 뒤 뒤쪽 쿼리를 계속 실행해서 생기는 후속 오류다.
