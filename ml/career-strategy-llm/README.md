# career-strategy-llm — C 담당 자체 모델

CareerTuner **C 영역(홈/스펙비교/취업분석/대시보드 + 커리어 전략 추천)** 의 자체 파인튜닝 LLM
`careertuner-c-career-strategy` 학습·변환·서빙 파이프라인.

> 인프라(스크립트/학습 설정/서빙)는 D의 `ml/interview-finetune/`(2026-06-20 검증 완료)에서 가져왔다.
> **데이터·task·출력계약만 C 도메인으로 교체**했다. D 파일은 직접 수정하지 않고 복사 후 변경.

## 목표

* C의 AI(적합도 설명/지원전략/학습로드맵/대시보드 요약)를 외부 API 대신 **직접 파인튜닝한 소형 모델**로 수행.
* 목적은 성능 1등이 아니라 **"직접 학습해 붙였다"는 증거 + C다운 설계**.

## 직군 범위 정책 (★IT 전용 아님)

> CareerTuner C 자체 LLM은 특정 직군 전용 모델이 아니라, 공고의 필수/우대 조건과 사용자 프로필의
> 역량·자격·경력을 비교해 설명하는 **범용 커리어 전략 설명 모델**이다.
>
> 다만 Phase 1 MVP 는 팀 프로젝트 특성과 시연 우선순위를 고려해 **IT/SW 직군을 중심으로 검증**한다.
> 동시에 마케팅·영업·디자인·회계·인사·물류·고객서비스 등 **비IT 직군 샘플을 포함**해 데이터 구조와
> 프롬프트가 IT 전용으로 굳지 않도록 한다.
>
> 비IT 직군의 정밀한 자격증/RAG/점수정책은 **Phase 2 이후 확장** 대상으로 둔다.

데이터셋 구분: `*.it_mvp.*`(IT/SW baseline 297) + `*.nonit.*`(비IT 120) → `*.mixed.*`(통합). `domainGroup`(IT_SOFTWARE/DATA_AI/MARKETING/SALES/DESIGN/FINANCE_ACCOUNTING/HR_ADMIN/MANUFACTURING_LOGISTICS/SERVICE_CS) 필드로 직군을 표기한다.

## C와 D의 차이 (★뉴로-심볼릭)

| | D(면접) | C(커리어 전략) |
| --- | --- | --- |
| LLM 역할 | 질문 생성·답변 **채점(점수 생성)** | **설명만 생성**(점수는 안 만듦) |
| 점수 위치 | 출력 | **입력**(서버 규칙엔진이 계산) |
| 시드 | 직군×면접모드×회사 | **프로필×공고** + 규칙엔진 사전계산값 |
| 출력 | 질문/점수 JSON | 설명·강점·위험·전략·학습사유 JSON |

→ `fitScore`, `applyDecision`, `matchedSkills`, `missing*` 은 **규칙엔진(코드)** 이 결정론적으로 계산해
모델에 **입력**으로 준다. 모델은 점수를 새로 만들거나 바꾸지 않고 **한국어 설명만** 쓴다.
자격증/NCS/직무 사실은 모델이 상상하지 않고 가능한 경우 RAG/공개데이터로 grounding(Phase 2).

## task

* **`C_FIT_EXPLAIN`** — MVP 1순위. fitScore·matched/missing 을 받아 적합도 설명·강점·위험·지원판단 근거 생성.
* `C_STRATEGY` / `C_LEARNING_ROADMAP` / `C_TREND_SUMMARY` — Phase 2~3 확장(데이터 확보 후).

우선 `C_FIT_EXPLAIN` 만 end-to-end 로 관통한다.

> **Phase 1 기본 학습 데이터 = mixed**(범용 직군): `data/train.mixed.jsonl` / `data/val.mixed.jsonl`.
> `*.it_mvp.*`(IT baseline)·`*.nonit.*`(비IT)는 비교/보존용. 4090 학습 입력은 mixed 로 고정.

## 모델 정책 (3B 우선 / 7B 비교)

* **Phase 1 기본 개발 모델: `Qwen/Qwen2.5-3B-Instruct`** — D 검증 경로, 가볍고 파이프라인/모델 문제 분리 쉬움.
* **7B 비교 후보: `Qwen/Qwen2.5-7B-Instruct`** — 한국어 유창성·JSON 안정성·설명 품질이 더 좋을 수 있음.
* **최종 서빙 모델은 지금 확정하지 않는다.** C/D가 메인 시연 기능일 수 있어 7B 메인 가능성도 열어둔다.
  Phase 2~3에서 **같은 데이터셋으로 3B vs 7B 비교**(`reports/01_model_comparison_plan.md`) 후 결정.
* 모델명: 3B=`careertuner-c-career-strategy-3b`, 7B=`careertuner-c-career-strategy-7b`,
  프로덕션/데모 alias=`careertuner-c-career-strategy`.

## 작업 토폴로지

```
[노트북] 데이터 생성·조립·코딩 (GPU 불필요, Claude Code 워크플로우로 합성)
   seed_profiles.py → (합성 워크플로우) → assemble_dataset.py → prepare_data.py → data/{train,val}.jsonl
        │  data/ 폴더를 공유 4090 PC로 전송(USB/OneDrive/scp)
[공유 4090] 학습·변환·서빙 (reports/00_runbook_4090.md 의 복붙 런북)
   finetune_lora.py → merge_and_export.py → llama.cpp GGUF → Ollama create
[백엔드] provider=oss 로 Ollama 호출 + 폴백 (Phase 1 후반, 원격 호출 방식 미확정)
```

* 공유 4090 PC에는 **Claude Code를 설치하지 않는다.** 파일 전송 + 명령어 실행 방식.
* **Tailscale 주의:** 백엔드 설정에 `localhost:11434` 정황은 있으나, 현재 TeamViewer로 접속한 공유
  4090 PC에는 **Tailscale 미설치 확인**. 해당 100.x 주소가 실제 공유 4090인지 불명확. **원격 Ollama
  호출 방식은 미확정**이며, Phase 1 은 수동 런북/로컬 테스트 우선.

## 데이터·산출물 규칙

* 합성 데이터는 **운영 DB에 넣지 않고** 이 폴더의 JSONL 로만 관리(`data/`, git 추적 제외).
* LoRA 어댑터·merged·GGUF 등 **모델 산출물은 git 추적 금지**(`.gitignore`).
* 자격증/직무 사실은 공개데이터(NCS·Q-net·워크넷)로 grounding, 라이선스(공공누리 유형) 확인.

## 문서·artifact 경계

AI 산출물은 성격별로 분리한다.

* `scripts/` — 재현 가능한 validator, runner, deterministic helper. 제품/평가 재현에 필요한 최소 스크립트만 둔다.
* `reports/` — 기존 `reports/NN` 링크를 깨지 않기 위한 transitional mirror. 새 장문 보고서는 추가하지 않는다.
* `../../docs/ai-reports/career-strategy-llm/reports/` — `CareerTunerAIDocs` submodule. C 장문 실험 보고서와 누적 해석 문서를 둔다.
* `../../docs/ai-artifacts/` — `CareerTunerAI` submodule. A~F 공통 raw output, generated requests/results, benchmark manifest, aggregate summary 를 둔다.
* main repo 에는 짧은 checklist/index 와 artifact path/commit SHA 만 남긴다.

## 폴더

```
ml/career-strategy-llm/
  README.md              이 문서
  model-card.md          모델 카드(운영안 13장)
  requirements.txt       학습 의존성(D와 동일 스택)
  scripts/
    seed_profiles.py             ★C 시드 + 규칙엔진 사전계산(--balance 균형 분포)
    synth_prompts.py             C task system 프롬프트(서빙 프롬프트와 정합)
    generate_dataset.workflow.js ★sonnet 선생 distillation 워크플로우(시드 슬라이스 배치)
    join_raw.py                  워크플로우 출력 + 시드 → raw.json 조인
    validate_dataset.py          자동 검증(규칙엔진 오라클 재검증, 금지키/환각/모순)
    filter_dataset.py            실패/중복 제거 → clean raw
    assemble_dataset.py          raw → messages JSONL (C용)
    prepare_data.py              train/val 분할 (D와 동일 로직)
    finetune_lora.py             QLoRA 학습 (D 설정 + C 기본값)
    merge_and_export.py          LoRA 병합 → GGUF 준비 (D + C 경로)
    test_infer.py                C_FIT_EXPLAIN 빠른 검증
  reports/
    00_runbook_4090.md             공유 4090 복붙 런북
    01_model_comparison_plan.md    3B vs 7B 비교 계획
    02_dataset_quality_report.md   데이터 품질 리포트(Phase 1 300건)
    README.md                      보고서 submodule 전환 안내
  data/                  합성 데이터(추적 제외)
```

## 데이터 생성 실행 (Phase 1, 로컬 노트북)

```bash
# 1) 균형 시드 생성 (APPLY/COMPLEMENT/HOLD ~동일)
python seed_profiles.py --n 300 --balance --out ../data/seeds.fit_explain.300.jsonl
# 2) 워크플로우(sonnet)로 fit_explain 생성 — Claude Code 의 Workflow 도구로 실행
#    Workflow({ scriptPath: ".../generate_dataset.workflow.js",
#               args: { seedsPath: "<절대경로 seeds.jsonl>", n: 300, batchSize: 15 } })
#    → 결과 .output 의 result.items 를 join_raw 로 시드와 조인
python join_raw.py --seeds ../data/seeds.fit_explain.300.jsonl --wf-output <.output 경로> --out ../data/raw.fit_explain.300.json
# 3) 검증 → 필터 → 조립 → 분할
python validate_dataset.py --raw ../data/raw.fit_explain.300.json --summary ../data/validate.summary.300.json
python filter_dataset.py   --raw ../data/raw.fit_explain.300.json --out ../data/raw.fit_explain.300.clean.json
python assemble_dataset.py --input ../data/raw.fit_explain.300.clean.json --out ../data/dataset.fit_explain.300.jsonl
python prepare_data.py     --input ../data/dataset.fit_explain.300.jsonl --out-dir ../data
```
확장: `--n 1000`(seed) + 워크플로우 `args.n: 1000` 으로 동일 파이프라인. 품질 검증 통과 필수.
선생 = `sonnet`(워크플로우 `model:'sonnet'`). 상세 결과는 `reports/02_dataset_quality_report.md`.

### 비IT + mixed (범용 직군)

```bash
# 비IT 120(직군·판단 쿼터 고정) → 워크플로우 → join → 검증 → 필터
python seed_profiles.py --preset nonit120 --out ../data/seeds.fit_explain.nonit.120.jsonl
#   Workflow(args:{ seedsPath:"<...nonit.120.jsonl>", n:120, batchSize:15 }) → join_raw → ...
python validate_dataset.py --raw ../data/raw.fit_explain.nonit.120.json --summary ../data/validate.summary.nonit.120.json
python filter_dataset.py   --raw ../data/raw.fit_explain.nonit.120.json --out ../data/raw.fit_explain.nonit.120.clean.json
# IT 297 + 비IT 119 병합 → mixed → split
python merge_raw.py    --inputs ../data/raw.fit_explain.it_mvp.300.clean.json ../data/raw.fit_explain.nonit.120.clean.json --out ../data/raw.fit_explain.mixed.clean.json --reid mix
python assemble_dataset.py --input ../data/raw.fit_explain.mixed.clean.json --out ../data/dataset.fit_explain.mixed.jsonl
python prepare_data.py     --input ../data/dataset.fit_explain.mixed.jsonl --out-dir ../data --tag mixed   # → train.mixed.jsonl / val.mixed.jsonl
python validate_dataset.py --raw ../data/raw.fit_explain.mixed.clean.json --summary ../data/validate.summary.mixed.json
```
mixed 품질: `reports/03_dataset_quality_report.mixed.md`. **4090 학습 입력 = `train.mixed.jsonl` / `val.mixed.jsonl`.**
스모크는 `--n 10 --balance --domains nonit` 로 비IT 10건 먼저 점검.

## 백엔드 연동 후보 파일 (Phase 1 후반에 작업, 지금은 기록만)

C 소유(팀장 승인 불필요):
* `backend/.../fitanalysis/ai/FitAnalysisAiService.java` (인터페이스)
* `backend/.../fitanalysis/ai/OpenAiFitAnalysisAiService.java`, `MockFitAnalysisAiService.java`
* `backend/.../analysis/ai/CareerTrendAiService.java`, `backend/.../dashboard/ai/DashboardInsightAiService.java`
* `backend/.../analysis/ai/provider/CareerAnalysisOpenAiClient.java` (기존 OpenAI 클라이언트)
* 신규 예정: `analysis/ai/provider/CareerAnalysisOssClient.java`, `FallbackCareerAnalysisClient.java`

미러링 참고(D 구현):
* `backend/.../interview/service/OssLlmGateway.java` (자체모델 호출 + `extractJsonSpan` JSON 보강)
* `backend/.../interview/service/FallbackInterviewLlmGateway.java` (OSS→Claude→OpenAI 폴백)

팀장 합의 필요(공통 영역): `application.yaml` 의 `careertuner.analysis.ai.*` 섹션, `ai/common`.
