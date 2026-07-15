# NCS 직무능력표준 카탈로그 (C 영역)

NCS정보망DB 원본 xlsx를 세분류 단위 계층 JSON으로 정규화해 MySQL `ncs_classification` 테이블에
적재하고, 백엔드·프런트엔드의 직무·자격 카탈로그로 제공하는 데이터 파이프라인이다.

## 현재 구현 상태

- DB 테이블과 트랜잭션 기반 멱등 스냅샷 교체 로더는 구현되어 있다. 기존 행을 `DELETE`한 뒤 전체 입력을
  적재하고 건수·능력단위·요소 합계를 검증한 경우에만 commit하므로 과거 잘못된 키와 삭제된 분류가 남지 않는다.
- normalizer와 loader는 각 계층의 `code`에 해당 계층의 코드만 저장하고, loader가
  `대-중-소-세` 복합 `ncs_code`를 한 번만 조립하는 계약을 공유한다.
- 이전 artifact처럼 `sub.code`가 이미 복합 코드인 JSONL도 상위 prefix가 정확히 일치하면 leaf 코드로
  정규화한다. 상위 코드와 불일치하거나 이중 복합인 값은 DB 연결 전에 실패한다.
- 백엔드 `catalog` mapper/service가 `ncs_classification`, `certificate`, `certificate_exam_schedule`을
  조회하며, `/api/catalog/**` 공개 API와 프런트 `/catalog/**` 검색·상세 화면이 연결되어 있다.
- 현재 제공 범위는 사용자가 직접 조회하는 NCS·자격증 레퍼런스 카탈로그다. 공고→직무 자동 매핑과
  적합도 분석의 NCS RAG 자동 grounding은 별도 PoC/후속 범위이므로 카탈로그 연결 완료와 구분한다.
- 스키마 정본은 [20260713_c_ncs_classification.sql](../../backend/src/main/resources/db/patches/20260713_c_ncs_classification.sql)과
  [schema.sql](../../backend/src/main/resources/db/schema.sql)이다.
- 초기 이중 조합 키를 정정하는 기존 DB용 patch는
  [20260714_c_ncs_code_contract.sql](../../backend/src/main/resources/db/patches/20260714_c_ncs_code_contract.sql)이다.

## 런타임 카탈로그

| 구분 | 현재 경로 |
| --- | --- |
| NCS 검색·상세 API | `GET /api/catalog/ncs`, `GET /api/catalog/ncs/{id}` |
| 자격증 검색·상세 API | `GET /api/catalog/certificates`, `GET /api/catalog/certificates/{id}` |
| 사용자 화면 | `/catalog`, `/catalog/ncs`, `/catalog/certificates` |
| 백엔드 구현 | `backend/src/main/java/com/careertuner/catalog`, `backend/src/main/resources/mapper/catalog` |
| 프런트 구현 | `frontend/src/features/catalog` |

카탈로그는 비로그인 사용자도 참고할 수 있는 공개 레퍼런스다. NCS 검색은 세분류명·능력단위·기술
키워드를, 자격증 검색은 이름·설명과 자격 유형 필터를 지원한다. 상세에서는 NCS 능력단위 계층 또는
자격 설명·국가 시험일정을 반환한다. 데이터가 적재되지 않은 환경에서는 빈 검색 결과를 반환하며,
운영 적재 여부를 기능 구현 여부와 혼동하지 않는다.

## 데이터 구조

```text
세분류(대-중-소-세)
└─ 능력단위(수준)
   └─ 능력단위요소
      ├─ 수행준거[]
      ├─ 지식[]
      ├─ 기술[]
      └─ 태도[]
```

2026-02 원본을 기준으로 정규화한 당시 집계는 세분류 1,109, 능력단위 13,442, 요소 47,650,
수행준거 196,761, 지식 223,390, 기술 188,056, 태도 163,157건이다. 이 값은 적재 실행 증거이며
현재 DB 행 수를 자동 보증하지 않는다.

## 정규화와 dry-run 검증

정규화에는 `openpyxl`, 실제 DB 적재에는 `pymysql`이 필요하다. `--dry-run`은 `pymysql`을 import하지 않는다.

```powershell
cd ml/ncs-catalog
python -m pip install openpyxl pymysql

python scripts/ncs_normalize.py `
  --input ".tmp\NCS정보망DB.xlsx" `
  --sheet all `
  --output .tmp\ncs_all.jsonl

# JSON 형식·코드 계약·중복·행 집계만 검사한다. DB 연결 없음.
python scripts/load_ncs_db.py --input .tmp\ncs_all.jsonl --dry-run
```

원본 경로는 `NCS_SOURCE_XLSX` 환경변수로도 줄 수 있다. 기존
`python scripts/ncs_normalize.py all .tmp/ncs_all.jsonl` 형식은 이 환경변수가 있을 때 호환한다.

## 검증된 JSONL을 DB에 적재

DB 값은 소스에 저장하지 않는다. 표준 환경변수를 주입한 뒤 dry-run과 같은 JSONL을 적재한다.

```powershell
$env:DB_HOST = "<mysql-host>"
$env:DB_PORT = "3306"
$env:DB_NAME = "<database>"
$env:DB_USERNAME = "<user>"
$env:DB_PASSWORD = "<password>"
$env:DB_SSL_MODE = "REQUIRED"

python scripts/load_ncs_db.py `
  --input .tmp\ncs_all.jsonl `
  --confirm-replace-all `
  --expected-classifications 1109
```

비밀번호 외 값은 `--db-host`, `--db-port`, `--db-name`, `--db-user` 인자로 덮어쓸 수도 있다.
비밀번호 CLI 인자도 지원하지만 프로세스 목록에 남을 수 있으므로 `DB_PASSWORD` 사용을 권장한다.
원격 적재는 기본 `DB_SSL_MODE=REQUIRED`로 TLS를 강제한다. CA와 호스트 이름까지 검증하려면
`DB_SSL_MODE=VERIFY_IDENTITY`, `DB_SSL_CA=<CA PEM 경로>`를 함께 사용한다. `DISABLED`는 로컬 fixture DB에만
명시적으로 사용한다.
`--expected-classifications`에는 바로 앞 dry-run의 `classifications` 값을 그대로 넣는다. 1,000건 미만
입력, 예상값 불일치, `--confirm-replace-all` 누락은 DB 연결 전에 거부한다. 공유 DB에서는 dry-run 결과와
샘플 `ncs_code`·`sub_code`를 검토한 뒤 적재한다. 실제 적재는
`ncs_classification` 전체 스냅샷을 한 트랜잭션에서 교체하므로 같은 입력을 재실행해도 행이 누적되지 않는다.

## 자격증 보조 loader

같은 폴더의 `load_cert_db.py`도 개인 경로와 DB 값을 소스에 두지 않는다. 백엔드 번들 자격증 파일은
repo-relative 기본 경로를 사용하고, 민간자격 CSV만 인자나 `NCS_PRIVATE_CERT_CSV`로 지정한다.

```powershell
python scripts/load_cert_db.py `
  --private-cert-csv ".tmp\민간자격등록정보.csv" `
  --dry-run
```

dry-run이 출력한 `certificates`와 `schedules`를 확인한 뒤 실제 적재에서 똑같이 명시한다.

```powershell
python scripts/load_cert_db.py `
  --private-cert-csv ".tmp\민간자격등록정보.csv" `
  --confirm-replace-all `
  --expected-certificates <dry-run-certificates> `
  --expected-schedules <dry-run-schedules>
```

자격증 600건 미만, 일정 60건 미만, 예상값 불일치는 전체 교체 전에 거부한다. `certificate`와
`certificate_exam_schedule`도 한 트랜잭션에서 전체 스냅샷을 교체하고 최종 건수를 검증하므로 재실행 시
중복 행이나 제거된 일정이 남지 않는다.

## 회귀 테스트

```powershell
python -m unittest discover -s scripts -p "test_*.py" -v
```

fixture 테스트는 canonical 코드, 기존 composite `sub.code` 호환, 잘못된 composite·중복·DB 제약 위반 거부,
NCS/자격증 dry-run의 DB 미연결, 축소 입력·미확인 전체 교체 거부, legacy seed 제거와 두 번 연속
스냅샷 교체의 멱등성을 검증한다.

세분류 코드는 대분류 안에서만 유일하므로 대-중-소-세 복합 코드를 유지한다. `detail_json`은 세분류별
계층 원문을 `MEDIUMTEXT`에, `search_text`는 능력단위명·요소명·기술 키워드를 검색용 텍스트로 보관한다.
원본 xlsx와 정규화 JSONL은 저장소에 커밋하지 않고 `docs/ai-artifacts/`에서 관리한다.
