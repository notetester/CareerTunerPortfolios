# AI 저장소 경계와 서브모듈 작업 절차

> 최종 대조 기준: `origin/dev` `23bb4d22` (2026-07-14)

CareerTuner의 AI 관련 자료는 제품 코드, 사람이 읽는 보고서, 반복 실행 artifact, 장기 맥락을 분리한다. 모든 경로는 clone 위치와 무관한 **저장소 상대경로**로 표기한다.

## 저장소 역할

| 현재 repo 경로 | 연결 저장소/역할 | 포함하는 것 | 포함하지 않는 것 |
| --- | --- | --- | --- |
| repo root, `backend/`, `frontend/`, `desktop/`, `ml/` | `CareerTuner` 제품 repo | 런타임 코드, 재현용 validator/runner, 작은 synthetic fixture, 짧은 상태 문서와 artifact commit 링크 | 반복 raw output, 대용량 generated 결과, 비밀값 |
| `docs/ai-reports/` | `CareerTunerAIDocs` submodule | 장문 실험 보고서, 누적 해석, 사람이 읽는 AI 모델 분석 | raw model response와 대용량 benchmark 결과 |
| `docs/ai-artifacts/` | `CareerTunerAI` submodule | generated request/response, result JSON, manifest, aggregate summary, 4090 운영 문서·스크립트 | 제품 런타임 코드 |
| `docs/storyboard/` | `CareerTunerDocs` submodule | A~F/TOTAL 스토리보드와 대표 산출물 | 현재 API·런타임 상태의 정본 |
| `docs/obsidian-vault/` | `CareerTunerObsidian` submodule | Obsidian overlay, 결정 로그, LLM Wiki 원천/합성 지식, Graphify report, 공개용 sanitized graph | 제품 비밀값과 비공개 raw 개인정보 |

서브모듈 연결과 추적 브랜치는 [`.gitmodules`](../.gitmodules)가 기준이다. 로컬 절대 경로나 특정 개발자 장비 경로를 문서·스크립트·보고서에 넣지 않는다.

## 제품 repo에 남는 AI 코드

다음은 artifact가 아니라 빌드·배포·재현에 필요한 제품 자산이므로 본체에 둔다.

- `backend/src/main/java/**`: AI 오케스트레이션, 공급자 adapter, 폴백, API와 영속 경계
- `frontend/src/**`, `desktop/**`: AI 기능을 호출하고 결과를 표시하는 클라이언트
- `ml/job-posting-worker/**`: Compose에서 별도 서비스로 배포되는 공고 추출/OCR 런타임
- `ml/*/scripts/**`: 제품·평가 재현에 필요한 validator, runner, deterministic helper
- 작고 검토 가능한 synthetic fixture와 계약 테스트
- 현재 상태를 설명하는 짧은 README/model card/checklist

Qdrant·Ollama·외부 모델의 실행 데이터나 모델 weight를 본체에 커밋하지 않는다. DB dump, 실사용 입력, 개인 식별정보, 자격증명도 어느 저장소에도 올리지 않는다.

## C career-strategy-llm 기준

- `ml/career-strategy-llm/scripts/`에는 제품/평가 재현에 필요한 validator, runner, deterministic helper만 둔다.
- 반복 실행 artifact, raw output, generated result JSON은 `docs/ai-artifacts/`에 둔다.
- 4090/Tailscale/OpenSSH/GitHub Actions/MCP/Ollama 운영 문서와 스크립트는 `docs/ai-artifacts/docs/ops/`, `docs/ai-artifacts/scripts/ops/`에 둔다.
- 장문 실험 보고서와 누적 분석은 `docs/ai-reports/areas/<area-slug>/reports/`에 둔다. C 영역은 `docs/ai-reports/areas/c-career-strategy/reports/`를 사용한다.
- `ml/career-strategy-llm/reports/`는 짧은 archive index/호환 안내만 유지한다.
- 본체에 `reports/generated/`, raw response, 반복 benchmark 산출물을 커밋하지 않는다.

다른 영역도 같은 원칙을 적용한다. 영역별 정본 위치는 해당 submodule의 `areas/<area-slug>/README.md`에서 안내한다.

## 자체모델 최종 백업과 재현성 (2026-07-14)

자체 파인튜닝 4모델(C career-strategy, E correction, D interview, B jobposting)의 최종 백업은
`docs/ai-artifacts`(=`CareerTunerAI`) submodule의 `results/2026-07-14-final-model-backup/`에 착지한다.
재료 백업은 2026-07-14 17:44, E 어댑터 실물은 18:03에 기록했다. 본체(main repo)에는 재현용 스크립트·validator·
짧은 상태 문서와 artifact commit 링크만 남기고, 대용량 가중치와 raw 백업은 이 submodule에 둔다.

merged `model.safetensors`(모델당 약 6GB)와 GGUF(Ollama blob)는 용량상 어느 git 저장소에도 백업하지 않으며
4090 운영기 전용이다. submodule 백업에 실제 들어 있는 재현 재료는 다음과 같다.

- C·E 학습 datasets
- Ollama modelfiles(`ollama show` 17종)
- 실행 환경 스냅샷(pip freeze, nvidia-smi)
- adapter manifest
- E delivery-s 어댑터 실물(`adapters/e-correction-delivery-s/adapter_model.safetensors`, 유일한 실물 어댑터)

### 모델별 재현성 원장

| 모델 | 재현 가능성 | 재현 재료 위치 | 비고 |
| --- | --- | --- | --- |
| C career-strategy (`careertuner-c-career-strategy-3b`) | 재현 가능(기능적) | submodule datasets + 본체 `ml/career-strategy-llm/scripts` | QLoRA 재학습→merge→GGUF 재현. 랜덤성으로 byte-identical은 아니며, 재학습 eval loss가 프로덕션과 근접해 기능적 재현을 확인 |
| E correction (`careertuner-e-correction-3b:latest` = delivery-s) | 정확 복원 가능 | submodule datasets + delivery-s 어댑터 실물 | base + 백업 어댑터로 배포 정본을 정확 복원 |
| D interview | 재현 불가 | 학습 데이터 소실 | GGUF blob·Modelfile만 잔존, 스크립트·골든셋은 git에 있음 |
| B jobposting | 재현 불가 | 학습 데이터 소실 | 원 학습 JSONL 미백업 |

base Qwen2.5-3B의 공식 라이선스는 연구용(qwen-research, 비상업)이므로, 상용 배포는 상업 허가 base 재학습 또는
외부 provider(Claude/OpenAI) 경로가 필요하다(상용화 P0 제약). 이 표는 재현 재료의 **저장소 경계**만 요약하며,
실험 결론·모델카드 상세는 `docs/ai-reports`의 C 영역 deep-dive와 각 모델 카드를 정본으로 한다. raw output과
benchmark 결과 자체는 수정하지 않는다.

## 필요한 서브모듈만 받기

일반 개발은 모든 서브모듈을 내려받을 필요가 없다.

```bash
git submodule update --init docs/ai-reports
git submodule update --init docs/ai-artifacts
git submodule update --init docs/storyboard
git submodule update --init docs/obsidian-vault
```

전체가 필요한 문서/포트폴리오 작업만 다음을 사용한다.

```bash
git submodule update --init --recursive
```

메인 repo가 가리키는 정확한 commit을 재현하려면 임의로 submodule 최신 branch를 pull하지 않고 먼저 `git submodule update --init <path>`를 실행한다.

## 서브모듈 수정: branch와 PR 필수

서브모듈의 `main`에 직접 commit/push하지 않는다. 작업 순서는 다음과 같다.

### 1. 메인 repo 개인 브랜치 준비

```bash
git switch dev
git pull --ff-only origin dev
git switch -c <personal-branch>
git submodule update --init <submodule-path>
```

### 2. 서브모듈 개인 브랜치에서 수정

```bash
cd <submodule-path>
git switch -c <submodule-branch>
git add <files>
git commit -m "docs: <한국어 변경 요약>"
git push -u origin <submodule-branch>
```

해당 submodule 저장소의 `main`을 대상으로 PR을 만들고 검증·리뷰 후 merge한다. 보고서와 artifact PR에도 비밀값, 개인정보, raw 불필요 파일이 없는지 확인한다.

### 3. merge된 commit으로 본체 pointer 갱신

```bash
cd <submodule-path>
git fetch origin
git switch main
git pull --ff-only origin main

cd ../..  # 실제 깊이에 맞게 CareerTuner repo root로 이동
git add <submodule-path>
git commit -m "docs: <서브모듈 변경 요약>"
git push -u origin <personal-branch>
```

마지막으로 CareerTuner repo에서 `dev` 대상 PR을 만든다. 본체 PR에는 다음을 적는다.

- 바뀐 submodule과 고정 commit SHA
- submodule PR 링크
- 본체에서 함께 바꾼 짧은 인덱스/상태 링크
- 재현 또는 검증한 명령

즉, 쓰기 흐름은 **submodule 개인 branch → submodule PR/merge → 본체 pointer commit → 본체 `dev` PR/merge**다. 어느 단계에서도 보호 브랜치에 직접 push하지 않는다.

## 이동·삭제 체크리스트

- [ ] 문서가 장문 해석인지 raw artifact인지 제품 재현 코드인지 분류했다.
- [ ] 메인 repo와 서브모듈 안의 상대 링크를 모두 갱신했다.
- [ ] raw output과 generated 결과가 본체 git index에 남지 않았다.
- [ ] 이메일/API key/token/내부 장비 경로/개인 데이터 패턴을 검사했다.
- [ ] submodule PR이 merge된 commit을 본체가 가리킨다.
- [ ] 본체 변경은 개인 브랜치에서 `dev` 대상 PR로 올렸다.
- [ ] `git diff --submodule=log`로 pointer 변경 범위를 확인했다.
