# AI repository boundaries

CareerTuner 의 AI 관련 자료는 성격별로 저장소를 나눈다. AI 도구는 작업 시작 시 이 경계를 먼저 확인한다.

## 저장소 역할

| 저장소 또는 경로 | 역할 | 커밋 대상 |
| --- | --- | --- |
| `D:/dev/CareerTuner` | 제품 코드, 재현용 최소 fixture/runner, 짧은 상태 인덱스 | backend/frontend/runtime code, validator, 작은 synthetic fixture, checklist, artifact path/SHA |
| `D:/dev/CareerTuner/docs/ai-reports` | `CareerTunerAIDocs` submodule | 장문 실험 보고서, 누적 해석 문서, 사람이 읽는 분석 |
| `D:/dev/CareerTuner/docs/ai-artifacts` | `CareerTunerAI` submodule | generated requests, raw model outputs, result JSON, manifests, aggregate summaries |
| `D:/dev/CareerTuner/docs/storyboard` | `CareerTunerDocs` submodule | A-F/TOTAL storyboard deliverables |

## C career-strategy-llm 기준

- `ml/career-strategy-llm/scripts/` 는 제품/평가 재현에 필요한 validator, runner, deterministic helper 만 둔다.
- 반복 실행 artifact, raw output, result JSON 은 A~F 공통 artifact 경로인 `docs/ai-artifacts/` submodule 에 둔다.
- 긴 실험 보고서와 누적 분석은 `docs/ai-reports/areas/<area-slug>/reports/` 에 둔다. C 영역은 `docs/ai-reports/areas/c-career-strategy/reports/` 를 사용한다.
- `ml/career-strategy-llm/reports/` 는 링크 전환 전까지 transitional mirror 로 유지한다. 새 장문 보고서를 계속 여기에 쌓지 않는다.
- CareerTuner main repo 에 raw output 이나 `reports/generated/` 결과를 커밋하지 않는다.

## submodule 작업 순서

```bash
git submodule update --init docs/ai-reports
git submodule update --init docs/ai-artifacts
git submodule update --init docs/storyboard
```

submodule 안에서 파일을 수정한 경우:

```bash
cd <submodule-path>
git add <files>
git commit -m "<message>"
git push origin main

cd D:/dev/CareerTuner
git add <submodule-path>
git commit -m "<message>"
```

메인 repo PR 에는 submodule pointer 변경과 짧은 링크/상태 문서만 포함한다.
