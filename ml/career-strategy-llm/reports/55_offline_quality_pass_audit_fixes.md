# C 영역 적대 감사 + 오프라인 품질 패스 — 41 findings 처리 (2026-06-27)

> 4090 PC offline(Tailscale `chanssick` 회색, sleep/off 추정 — 인증 문제 아님) 동안 **GPU 실측 없이 할 수 있는
> 코드 품질 작업**으로, C 영역(자체 LLM 평가 하니스·RAG PoC·semantic judge)과 4090 ops 스크립트를
> **까다로운 비평자 기준으로 적대 감사**하고 confirmed 버그를 고쳤다. 멀티에이전트 감사가 41건(critical 0 ·
> high 8 · medium 17 · low 16)을 냈고, 코드/PowerShell 직접 재현으로 확정된 항목을 이번 PR 에서 처리한다.
> **점수/applyDecision·E1/E2·scope·격리 불변식은 유지**(채점 하니스·fixture 한정, backend/runtime/model 무변경).

## 1. 처리한 핵심(HIGH) — 안전불변식 위반·실버그

| # | 파일 | 문제(직접 재현) | 수정 |
| --- | --- | --- | --- |
| H1 | `scripts/skill_normalizer.py` | `_strip_suffix_nouns` 가 경계 없이 `관리/운영/협상/기초` 등을 잘라 `품질관리`+allowed`품질`→`false_positive` 로 **환각 후보 침묵 whitewash**(안전불변식 위반) | 접미 제거를 **공백 경계(' '+suf)** 있을 때만. `_substring_hit` 도 **토큰 경계** 강제(자바스크립트≠자바). 회귀 4건(품질/영업/재고관리·자바스크립트→unresolved) |
| H2 | `rag_poc/scripts/rag_prompt_builder.py` | `sanitize_context` FORBIDDEN_KEYS 가드가 재구성된 3키만 봐 **절대 발화 못 하는 dead code** | 가드 제거 → **화이트리스트가 1차 방어** 명시 + **text 값-수준 점수/판단 누수 스캔**(`fitScore:`/`applyDecision:`) 도입 |
| H3 | `scripts/ops/4090/Install-4090OpenSshSelfHeal.ps1` | 공개키 값을 self-heal 소스 `$keys=@(__KEY_LINES__)` 에 보간 → 매부팅 SYSTEM 재파싱 **코드주입 경로** | 키를 `OpsDir\authorized_keys.txt` 에 **데이터로 분리**, self-heal 은 `Copy-Item` 만(코드/데이터 분리) + 따옴표/백틱/개행 거부 |
| H4 | `scripts/ops/4090/Install-4090OpenSshSelfHeal.ps1` | `-Once` 트리거에 `$t2.Repetition.Interval=` 대입 → `.Repetition` 이 `$null` 이라 예외, `$ErrorActionPreference=Stop` 로 **self-heal 작업 미등록**(과거 RepetitionDuration 수정이 만든 회귀) | `-RepetitionInterval (New-TimeSpan -Hours 1)` 로 생성 + 등록 후 트리거 2개 검증. **PowerShell 직접 재현으로 옛 패턴 throw 확인** |
| H5 | `scripts/eval_fit_model.py` | `collect_text` 가 `learningTaskReasons` 의 **문자열 항목을 드롭** → 7B 스키마 일탈 시 CJK/금지문구/금지언급/E2 채점 **침묵 비활성화**(bad_skills 경로와 비대칭) | dict/str 모두 본문 포함(`_learning_task_text`). 회귀: 문자열 항목의 CJK·금지문구 채점 확인 |
| H6 | `scripts/test_golden_case_tools.py` | matched/missing 가 rule engine 재계산과 일치하는지 검증하는 **안전검증의 실패경로 미테스트**(헬퍼가 늘 정확히 채움) | 손-조작 불일치 케이스 4건 추가(matched/missing required·preferred 위조 + mustMention 범위밖) |

## 2. 처리한 MEDIUM(정확성·견고성·테스트공백)

- `skill_normalizer._substring_hit` 토큰경계(H1 과 통합).
- `rag_poc/build_retrieved_context._assert_no_score_keys`: 출력 키 화이트리스트 + 값-수준 누수 스캔으로 **회귀 가드 활성화**(과거 dead). `FORBIDDEN_KEYS` 에 `vectorDistance/rank/distance` 보강.
- `eval_fit_model`: must*/mustNot/forbiddenClaims **라틴 토큰 단어경계**(`SQL`⊄`MySQL`). 한국어는 조사/활용 가변성 때문에 기존 부분문자열 유지(negation 미해결은 한계로 명시).
- `judge_consensus`: 동률 시 `conf=0.0` 으로 **확신 손실** → 동률 라벨 평균으로 보존 + agreement `tie 1-1`. 같은 judge **중복투표 dedup**(candidateId,judge).
- `semantic_skill_judge.mock_judge`: 모든 mock verdict 에 `needsHumanReview=True` + `synthetic=True` → **whitewash 합의단계 전파 차단**. `judge_consensus` 가 synthetic 혼입을 `is_synthetic`/경고로 표식(실측 오인 방지).
- `embedding_retriever.retrieve`: `min_score` 기본 `None`→`0.0`(음수/직교 cosine 제외, lexical 과 의미 일치).
- `compare_lora_with_rag_hard_cases.per_case_delta`: hallucination **raw+normalized 이중계산 금지**(verdict 는 normalized residual + E1 + E2 + contract_fail).
- `compare_lora_with_rag.aggregate`: mock 집계에 `mock=true`/`rag_metrics_valid=false`/`_note` → mock 을 RAG 효과 실측으로 오인 못 하게. top-level `mock` 강제.
- `Test-4090OpenSshHealth.ps1`: ACL bad-principal 판정을 icacls 문자열(백슬래시) → **Get-Acl 구조적 ACE/소유자 + 허용 SID 화이트리스트**(`Everyone:(F)`/`Users:(RX)` 도 탐지). PowerShell 로 `Everyone(S-1-1-0)` 탐지 재현.
- `test_judge_consensus.py`: `validate_verdict`/`canon_decision`/`normalize_verdict` 경계 게이트 직접 테스트 7건 + 동률 confidence 보존.

## 3. LOW(관측·통계 정확도)

- `eval_fit_model`: grounding substring 1~2자 라틴 토큰 경계, connect 단계 timeout(`URLError`)을 `ERROR_Timeout` 으로 통일해 `timeout_count` 과소집계 해소.

## 4. 추가한 오프라인 도구(4090 없이 사용)

- `rag_poc/scripts/validate_rag_hard_cases.py` — **R2b preflight 게이트**(종료코드): PII·scope·금지키/값누수·negative_control·MSSQL·score 불변·hardType 8종. 실모델 A/B 전 단계에서 한 줄 차단용.
- `rag_poc/scripts/summarize_r2b_results.py` — **결과 요약기 + mock-not-real 가드**: `mock`/`rag_metrics_valid` 보고 **mock 이면 RAG 효과 판정 생략 + 종료코드 2**(자동 파이프라인이 '실측 완료'로 처리 못 하게).
- `.github/workflows/4090-job-trigger.yml` — **dryRun 입력(기본 true) + 4090 SSH 도달성 probe**(forced-command 미발동 TCP probe). 기본 dryRun=true 라 실수 dispatch 도 job 미트리거. 실제 트리거(dryRun=false)는 미도달 시 헛 트리거 전에 중단. **수동 dispatch 전용 유지**(자동 트리거 없음).
- `docs/ops/4090_POWER_AND_AVAILABILITY_RUNBOOK.md` · `scripts/ops/4090/Set-4090PowerPolicy.ps1` — 전원·가용성 진단/복구 + 절전 방지 템플릿(점검 기본, `-Apply` 는 관리자·소유자 동의 전제, 키 없음).

## 5. 테스트

순수 파이썬 회귀 전부 통과: skill_normalizer selfcheck 26/26, test_skill_normalizer 29, test_golden_case_tools **11**(+4), test_judge_consensus **17**(+8), test_eval_robustness **7**(+2), test_entity_observer 24, test_judge_packet_builder 7, rag_poc 7종(context_builder **6**·rag_prompt_builder **7** 포함, value-누수/회귀가드 추가). PowerShell 3종 parse OK + Repetition/ACL 동작 재현 검증.

## 6. 남은 것(4090/외부 의존 — 이번 범위 밖)

- **R2b 실측 A/B**: 4090 복구 시 `validate_rag_hard_cases.py` 통과 → `compare_lora_with_rag_hard_cases.py --base-url ...` → `summarize_r2b_results.py` 순. raw 는 CareerTunerAI results(메인 미커밋).
- **CareerTunerAI `install_ssh_persistence_task.ps1`**: H4 와 동일한 `.Repetition` null 위험 가능 — 4090 도달 후 같은 `-RepetitionInterval` 패턴으로 점검·수정 필요(별도 repo).
- **reports/49 MSSQL valid_error occurrence vs unique**: 산출물 `results/2026-06-26-7b-smoke-002-judge/consensus` 로 occurrence 분해 재확인(본문은 unique 1 로 표·합의와 일치시켜 정정).

## 7. 영역 경계(중요)

TRIPTOGETHER 운영자 페이지 기능(실시간 검색·필터, 컬럼 정렬, 회원/IP 차단, 이메일 인증, 쪽지, 권한 설정)은
**C 영역이 아닌 admin/공통 도메인**이라 [AGENTS.md](../../../AGENTS.md) 작업범위 규칙상 **팀 합의 후** 진행 대상이다.
이번 PR 은 C 영역(자체 LLM 평가/RAG/judge)과 4090 ops 한정이며, admin 기능은 포함하지 않는다(별도 제안 필요).

관련: [reports/49](49_7b_smoke_benchmark_result.md) · [reports/54](54_rag_r2b_hardcase_eval.md) · [reports/43](43_external_6judge_integration_and_audit.md) · [reports/42](42_needs_policy_label_decision_brief.md) · [docs/ops/4090_POWER_AND_AVAILABILITY_RUNBOOK.md](../../../docs/ops/4090_POWER_AND_AVAILABILITY_RUNBOOK.md)
