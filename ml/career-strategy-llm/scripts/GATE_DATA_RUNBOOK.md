# Gate 데이터 파이프라인 RUNBOOK (C 적합도 evidence gate)

C 영역 fit-analysis evidence gate(`fit_analysis_gate_result`)의 **진짜(real-LLM) gate 데이터**를 만드는 절차.
합성 생성기(`gen_synth_gate_data.py`)의 분포는 **추정치**이고, 이 런북은 그 추정을 실측으로 교정하기 위한 것이다.

- **OFFLINE 파트(GPU 불필요)**: 픽스처 작성·검증, seed SQL 생성, 이 문서. → 지금 커밋 가능.
- **GPU-gated 파트(Ollama UP 필요)**: 실제 생성 run + 저장 rows 판독 + 실측 vs 추정 비교. → Ollama DOWN 이면 실행 금지.

현재 상태: **Ollama DOWN → GPU-gated 파트 미실행.** 아래 §3~§6 는 GPU 확보 후 실행하는 절차 기술이다.

---

## 관련 파일

| 파일 | 역할 |
| --- | --- |
| `data/evidence_attribution_baseline/gate_adversarial_v1.jsonl` | **이번 산출물.** gate 결과를 의도적으로 세 밴드(matched_without_evidence / requirement_as_owned / clean_pass)로 벌린 24 케이스 적대적 픽스처. |
| `data/evidence_attribution_baseline/a_only_baseline_v1.jsonl` | 기존 60 케이스 A-only baseline(E1 focus). 스키마 원본. |
| `scripts/validate_gate_adversarial_fixture.py` | 위 픽스처 preflight 게이트(PII/스키마/caseId 유일/forbiddenOwned 재계산/overlap). A-only validator 의 per-row 로직 재사용, A-only 전용 카테고리 커버리지·total=60 단정만 제외. |
| `scripts/validate_a_only_baseline_fixture.py` | A-only 전용 validator(카테고리 12/8×6, total=60 강제). gate 픽스처엔 직접 안 맞음 — per-row 로직만 공유. |
| `scripts/run_e2e_production_baseline.py` | **유일한 genuine-gate-row 드라이버.** `seed-sql`(정적 SQL 출력)·`run`(로그인→실제 `POST /api/fit-analyses`→규칙엔진→OSS→E1 guard→R3 gate→저장). |
| `scripts/gen_synth_gate_data.py` | 순수 합성 대규모 픽스처. `gate_status` 55/40/5, severity 70/30 은 **추정** — 이 런북이 교정 대상으로 삼는 값. |

---

## §0. 밴드 설계(왜 이 픽스처가 gate 를 벌리는가)

R3 gate(`EvidenceGateService.evaluate`)는 순수 결정론 후처리다. reason 은 **사용자 원본 근거(profileSkills+certificates)** 대비:

- `matched_skill_without_user_evidence`: AI 가 낸 `matchedSkills` 중 사용자 원본 근거에 없는 항목. 그 항목이 **required** 면 `critical`, 아니면 `warning`.
- `requirement_as_owned`: 사용자 노출 텍스트가 공고 요구 역량을 '보유'로 단정했으나 사용자 원본 근거 없음. **required** 면 `critical`, 우대면 `warning`.
- reason 하나라도 있으면 `REVIEW_REQUIRED`(review-first — critical 도 폐기 안 하고 검토 라우팅), 없으면 `PASSED`.

픽스처는 `profile.skills` 대 `job.requiredSkills/preferredSkills` 관계로 이 결과를 **유도**한다(실제 reason 은 런타임 모델+gate 가 생성):

| 밴드(`gateBand`) | 셋업 | 노리는 결과 |
| --- | --- | --- |
| `matched_skill_without_user_evidence` (9) | 필수/우대 스킬이 프로필에 없음 → 모델이 matched 로 승격하기 쉬움 | reason type=matched_..., 필수 미보유는 critical / 우대만이면 warning |
| `requirement_as_owned` (9) | duties/요구 스택을 보유로 단정하기 쉬운 셋업, 근거 없음 | reason type=requirement_as_owned, required→critical / preferred→warning |
| `clean_pass` (6) | 필수+우대를 프로필이 전부 보유 → `forbiddenOwned=[]` | reason 0 → PASSED |

> gate 결과는 **모델 출력에 의존**하므로 픽스처의 `expectedReasonTypes`/`expectedMaxSeverity` 는 **목표(hint)**이지 보장이 아니다. 실측 대조는 §6 에서 한다.

---

## §1. (OFFLINE) 픽스처 검증 — GPU 없이 지금 실행

```bash
python ml/career-strategy-llm/scripts/validate_gate_adversarial_fixture.py
# 기대: exit 0, "OK PII 없음 · 스키마 · caseId 유일 · forbiddenOwned 재계산 일치 · allowed/forbidden 비중첩"
```

이 게이트가 통과해야 seed·run 으로 넘어간다. 픽스처 행 스키마는 `a_only_baseline_v1.jsonl` 과 동일
(`caseId/category/intent/profile{skills,certificates,desiredJob}/job{companyName,jobTitle,requiredSkills,preferredSkills,duties}/expected{allowedOwned,forbiddenOwned,expectedGateStatusForUnsafeClaim}`).
gate 전용 주석 키(`gateBand`, `expectedReasonTypes`, `expectedMaxSeverity`, `expectedGateStatusHint`)는 validator 가 무시한다.

## §2. (OFFLINE) seed SQL 생성 — GPU 없이 지금 실행

`run_e2e_production_baseline.py seed-sql` 은 `a_only_baseline_v1.jsonl` 을 하드코딩해 읽는다(`FIXTURE` 상수).
gate 픽스처로 seed 하려면 실행 전 그 상수를 `gate_adversarial_v1.jsonl` 로 바꾸거나, 아래처럼 env 오버라이드 없이 임시 심볼릭/복사 대신 **드라이버의 FIXTURE 경로만 바꾼 뒤** 실행한다.

```bash
# 정적 seed SQL 출력(모델 호출 없음, DB 접속 없음 — 순수 파일 생성)
python ml/career-strategy-llm/scripts/run_e2e_production_baseline.py seed-sql \
  --out ml/career-strategy-llm/scripts/gate_e2e_seed.sql
# 생성물: users(id 911001~) + user_profile + application_case(id 910001~) + job_analysis. INSERT IGNORE 멱등.
```

DB 적용은 dev 에 mysql CLI 가 없으므로 `backend/tools/ApplySqlPatch.java`(JDBC) 로 적용한다.
**seed SQL·raw output 은 커밋하지 않는다**(§7).

---

## §3. (GPU) Ollama + 3B 기동 — Ollama UP 필요

```bash
# 4090 호스트에서 3B 서빙(모델 태그는 운영 문서 기준)
ollama serve                      # 데몬
ollama pull <3B-tag>              # 예: qwen2.5:3b 계열 — 운영 문서의 확정 태그 사용
ollama run  <3B-tag> ""           # warmup(첫 로드 지연 제거)
```

## §4. (GPU) 백엔드 OSS provider 로 :8081 기동

```bash
# provider=oss + oss base-url 을 Ollama 로 지정. :8080 은 BlueStacks 점유 → :8081.
# (프로퍼티 키는 backend 설정 기준. 예시)
export CT_AI_PROVIDER=oss
export CT_AI_OSS_BASE_URL=http://<ollama-host>:11434
cd backend && ./gradlew bootRun --args='--server.port=8081'
```

## §5. (GPU) 서빙 확인 → E2E 생성 run

```bash
# 1) 동시성/서빙 확인(3B 가 실제로 응답하고 폴백으로 안 새는지)
python ml/career-strategy-llm/scripts/run_concurrency_load_test.py --base http://localhost:8081
#    → 응답률·latency·model 태그 확인. 3B 미응답이면 여기서 멈춤(폴백 rows 는 genuine 아님).

# 2) 실제 gate row 생성(픽스처 전 케이스를 production 경로로 관통)
python ml/career-strategy-llm/scripts/run_e2e_production_baseline.py run \
  --base http://localhost:8081 \
  --out <CareerTunerAI submodule 경로>/gate_e2e_raw \
  --timeout-seconds 150
#    → per-case: model / gateStatus / maxSeverity / gateReasonCount / evidenceGateVersion 수집.
#    → summary 에 gateDistribution·modelDistribution 포함.
```

## §6. (GPU) genuine 판정 → 저장 rows 판독 → 실측 vs 추정 비교

1. **modelDistribution 이 3B 만인지 확인.** 폴백 rows(3B 태그가 아닌 것)는 **genuine gate 아님** → 집계에서 제외.
   폴백이 섞였으면 그 케이스만 재실행하거나 결과에서 필터한 뒤 분포를 다시 낸다.
2. **저장된 rows 판독**: `fit_analysis_gate_result`(gate_status, max_severity, reason_count, gate_reasons_json, evidence_gate_version)를
   `ApplySqlPatch`/read 쿼리로 되읽어 run summary 와 일치하는지 교차확인(응답에만 있고 저장 안 된 케이스 색출).
3. **실측 vs 추정 대조**:
   - 실측 `gate_status` 분포(PASSED/REVIEW_REQUIRED/REJECTED) ↔ `gen_synth_gate_data.py` 의 **55/40/5** 추정.
   - 실측 `max_severity` 분포(warning/critical) ↔ 합성 생성기 reason 의 **70/30**(warning:critical) 추정.
   - 실측 reason `type` 분포(requirement_as_owned vs matched_skill_without_user_evidence) ↔ 합성의 균등 `random.choice`.
   - 밴드별 유도 결과가 §0 표대로 나왔는지(matched 밴드→matched type 우세, req 밴드→requirement type 우세, clean→PASSED).
4. **교정 산출물**: 실측 분포 요약 표 1장(밴드×gate_status×severity×type 카운트)을 만들어,
   합성 생성기의 가중치를 실측값으로 갱신할지 결정한다. 이 **요약만 커밋**한다.

---

## §7. 커밋 경계(엄수)

- **커밋 O**: `gate_adversarial_v1.jsonl`(픽스처), `validate_gate_adversarial_fixture.py`(validator), 이 런북, **실측 분포 요약**(집계 표/짧은 md).
- **커밋 X(절대)**: raw 모델 출력, per-case 원본 응답 JSON, seed SQL, 대량 result JSON → **`CareerTunerAI` submodule(`docs/ai-artifacts/`)** 로만.
  `run` 의 `--out` 은 submodule 경로로 지정한다. 메인 repo(`CareerTuner`)엔 raw/generated 를 커밋하지 않는다(AGENTS.md 규칙).
- backend/ · frontend/ 는 이 작업에서 수정하지 않는다(C 영역 경계). provider/포트 변경은 런타임 env·설정으로만.

## §8. 실행 순서 요약

```
[OFFLINE 지금]  §1 validate → §2 seed-sql 생성
[GPU 확보 후]   §3 ollama+3B → §4 backend oss:8081 → §5 load-test→run → §6 genuine 판정·판독·대조 → 요약 커밋
```
