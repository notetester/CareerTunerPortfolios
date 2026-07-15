# career-strategy-llm — C 담당 자체 모델

> **현재성 기준:** 2026-07-14 `origin/dev` `23bb4d22`. 7B LoRA 비교, NCS 생성 A/B,
> 동일 schema base/LoRA 통제 비교와 블라인드 전략 이중판정까지 반영했다.
> 현재 판단은 [CURRENT_STATE.md](CURRENT_STATE.md), 수치와 한계는
> [AI 종합 기술 보고서](../../docs/ai-reports/areas/shared-ai/portfolio/careertuner-self-ai-model-deep-dive.md)를 우선한다.

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
자격증/NCS/직무 사실은 모델 기억에 맡기지 않는다. 현재 백엔드는 NCS 카탈로그와 자격 시험 공개데이터를
서버 측 근거 서비스로 조회하고, 키가 없거나 업스트림이 실패하면 날짜를 만들지 않고 degrade한다. 모델은
규칙엔진이 확정한 점수·판단과 허용된 근거를 설명할 뿐 사실의 권위 소스가 아니다.

## task

* **`C_FIT_EXPLAIN`** — MVP 1순위. fitScore·matched/missing 을 받아 적합도 설명·강점·위험·지원판단 근거 생성.
* `C_STRATEGY` / `C_LEARNING_ROADMAP` / `C_TREND_SUMMARY` — Phase 2~3 확장(데이터 확보 후).

우선 `C_FIT_EXPLAIN` 만 end-to-end 로 관통한다.

> **Phase 1 기본 학습 데이터 = mixed**(범용 직군): `data/train.mixed.jsonl` / `data/val.mixed.jsonl`.
> `*.it_mvp.*`(IT baseline)·`*.nonit.*`(비IT)는 비교/보존용. 4090 학습 입력은 mixed 로 고정.

## 모델 정책 (3B 유지 / 7B 비교 완료)

* **현재 서빙 기준 모델: `Qwen/Qwen2.5-3B-Instruct` LoRA** — 4090에서 학습·GGUF·Ollama·백엔드 연결을 완주했다.
* **7B LoRA 비교 완료(2026-07-14):** 같은 train375/val41, r16/α32, 3 epoch, golden60 조건에서
  7B와 3B의 contract·E1 차이는 ±2건 이내로 구분되지 않았고, 7B는 지연 약 1.8배, 평가 VRAM 약
  2.1배, 학습시간 약 1.68배가 필요했다. 이는 7B의 능력이 열등하다는 결론이 아니라 현재 배포 제약에서
  비용을 정당화할 우위가 없다는 `KEEP_3B` 결정이다.
* **재평가 조건:** 멀티시드·확대 golden set·모델별 하이퍼파라미터 튜닝에서 비용을 감수할 일관된 우위가
  확인되면 7B 전환을 다시 검토한다.
* **LoRA·schema·gate 역할 분리(2026-07-14):** 동일 schema 통제에서 raw contract는 LoRA 56/60,
  base 53/60, gate 후 accepted는 LoRA 46/60, base 49/60, E1은 LoRA 11/60, base 5/60이었다.
  schema는 구조, E1+R3는 안전, LoRA는 전략 품질을 담당하는 계층으로 유지한다.
* **NCS 생성 자동 주입 비활성:** 무주입 16회·주입 16회 A/B에서 주입이 raw grounding을
  악화시켰고 gate가 unsafe 출력을 흡수했다. NCS는 결정론 요구·로드맵 근거로 사용한다.
* 모델명: 3B=`careertuner-c-career-strategy-3b`, 7B=`careertuner-c-career-strategy-7b`,
  프로덕션/데모 alias=`careertuner-c-career-strategy`.

`Qwen2.5-3B-Instruct`는 Apache 2.0이 아니라 **Qwen Research License**다. 연구·평가 목적 범위를 벗어난
상용 사용 전에는 별도 허가 또는 상업 친화 베이스로의 재학습·회귀검증이 필요하다. 비교한 7B는 Apache 2.0으로
모델 크기별 라이선스가 다르므로 이름별 LICENSE를 확인한다.

## 작업 토폴로지

```
[노트북] 데이터 생성·조립·코딩 (GPU 불필요, Claude Code 워크플로우로 합성)
   seed_profiles.py → (합성 워크플로우) → assemble_dataset.py → prepare_data.py → data/{train,val}.jsonl
        │  data/ 폴더를 공유 4090 PC로 전송(USB/OneDrive/scp)
[공유 4090] 학습·변환·서빙 ([00_runbook_4090](../../docs/ai-reports/areas/c-career-strategy/reports/00_runbook_4090.md)의 복붙 런북)
   finetune_lora.py → merge_and_export.py → llama.cpp GGUF → Ollama create
[백엔드 적합도] provider=oss + endpoint 설정 시 OSS → Claude → OpenAI → 결정적 Mock
                  기본 provider=openai에서는 자체 모델을 건너뛰며, 명시적 CAREERTUNER 선택은 endpoint가 있으면 OSS부터 시도
```

* 공유 4090 PC에는 **Claude Code를 설치하지 않는다.** 파일 전송 + 명령어 실행 방식.
* **공유 GPU 주의:** 원격 Ollama 주소는 `AI_OLLAMA_BASE_URL` 등 배포 환경변수로만 주입한다. 저장소의
  주소 스냅샷은 장비 가용성을 보장하지 않으므로 학습·서빙 전 health 확인과 로컬 폴백 경로를 함께 점검한다.

## 데이터·산출물 규칙

* 합성 데이터는 **운영 DB에 넣지 않고** 이 폴더의 JSONL 로만 관리(`data/`, git 추적 제외).
* LoRA 어댑터·merged·GGUF 등 **모델 산출물은 git 추적 금지**(`.gitignore`).
* 자격증/직무 사실은 공개데이터(NCS·Q-net·워크넷)로 grounding, 라이선스(공공누리 유형) 확인.

## 문서·artifact 경계

AI 산출물은 성격별로 분리한다.

* `scripts/` — 재현 가능한 validator, runner, deterministic helper. 제품/평가 재현에 필요한 최소 스크립트만 둔다.
* `CURRENT_STATE.md` — C 자체 LLM 의 현재 판단과 핵심 출처 링크를 모은 최신 정리본.
* `reports/README.md` — 과거 `reports/NN` 장문 보고서의 archive index. 장문 본문은 보관하지 않는다.
* `../../docs/ai-reports/areas/c-career-strategy/reports/` — `CareerTunerAIDocs` submodule. C 장문 실험 보고서와 누적 해석 문서를 둔다.
* `../../docs/ai-artifacts/` — `CareerTunerAI` submodule. A~F 공통 raw output, generated requests/results, benchmark manifest, aggregate summary 를 둔다.
* main repo 에는 짧은 checklist/index 와 artifact path/commit SHA 만 남긴다.

## 폴더

```
ml/career-strategy-llm/
  README.md              이 문서
  model-card.md          모델 카드(운영안 13장)
  CURRENT_STATE.md       현재 판단과 핵심 출처 링크
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
    README.md                      CareerTunerAIDocs archive index
  data/                  합성 데이터(추적 제외)
```

## 데이터 생성 재현 (로컬 개발 환경)

```bash
cd ml/career-strategy-llm/scripts
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
선생 = `sonnet`(워크플로우 `model:'sonnet'`). 상세 결과는 [02_dataset_quality_report](../../docs/ai-reports/areas/c-career-strategy/reports/02_dataset_quality_report.md)를 본다.

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
mixed 품질: [03_dataset_quality_report.mixed](../../docs/ai-reports/areas/c-career-strategy/reports/03_dataset_quality_report.mixed.md). **4090 학습 입력 = `train.mixed.jsonl` / `val.mixed.jsonl`.**
스모크는 `--n 10 --balance --domains nonit` 로 비IT 10건 먼저 점검.

## 현재 백엔드 연결

적합도 설명용 자체 모델 연결은 구현되어 있다.

* `analysis/ai/provider/CareerAnalysisOssClient.java` — Ollama의 OpenAI 호환 endpoint 호출, JSON 추출,
  재시도·시간예산·공유 GPU permit 적용
* `fitanalysis/ai/OssFitAnalysisAiService.java` — 규칙엔진 skeleton에 모델의 설명 필드만 병합
* `fitanalysis/ai/FallbackFitAnalysisAiService.java` — 활성 진입점. 자체 모델을 쓸 수 있으면
  `OSS → Claude(Haiku) → OpenAI → 결정적 Mock` 순으로 degrade
* `application.yaml`의 `careertuner.analysis.ai.*` — provider와 OSS endpoint/model/timeout 설정. 기본값은
  `openai`이고 base URL은 비워 두므로 환경변수 없이 자체 모델이 임의 호출되지 않는다.

```dotenv
CAREERTUNER_ANALYSIS_AI_PROVIDER=oss
CAREERTUNER_ANALYSIS_AI_OSS_BASE_URL=http://<approved-ollama-host>:11434/v1
CAREERTUNER_ANALYSIS_AI_OSS_MODEL=careertuner-c-career-strategy-3b
# endpoint 인증이 구성된 경우에만 설정
CAREERTUNER_ANALYSIS_AI_OSS_API_KEY=<secret>
```

커리어 트렌드와 대시보드 요약은 현재 이 LoRA 모델의 task가 아니다. 각각
`FallbackCareerTrendAiService`, `FallbackDashboardInsightAiService`의 `Claude → OpenAI → Mock` 체인을
사용한다. 연결 회귀는 `CareerAnalysisOssClientTest`, `CareerAnalysisOssClientGateBudgetIntegrationTest`,
`OssFitAnalysisAiServiceTest`, `FallbackFitAnalysisAiServiceTest`에서 확인한다.
