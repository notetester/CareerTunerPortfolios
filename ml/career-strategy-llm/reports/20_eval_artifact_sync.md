# 평가 결과 동기화 — 별도 private artifact repo (서브모듈 아님)

> TeamViewer 로 매번 zip 옮기는 비효율 제거. 평가 결과/로그는 **메인 repo 밖** 별도 private repo 로 주고받는다.
> ★ 핵심: 메인 repo 의 **gitignore 된 `out/` 안**에 별도 repo 를 clone 한다. **git submodule 아님** — `.gitmodules`/gitlink 가 생기면 안 된다.

## 1. 원칙
```text
- 메인 CareerTuner repo 에는 대용량 raw 결과/로그/캡처를 커밋하지 않는다.
- ml/career-strategy-llm/out/eval-sync/ 아래에 별도 private repo 를 clone 해 push/pull 한다.
- git submodule 로 등록하지 않는다(.gitmodules·gitlink 금지).
- 메인 repo 에는 '요약 리포트'만 들어간다.
```

## 2. .gitignore 충돌 없음 (확인됨)
`ml/career-strategy-llm/.gitignore` 의 `out/` 규칙으로 `out/` 전체가 무시된다.
```bash
git check-ignore ml/career-strategy-llm/out/eval-sync/c-fit-3b-eval-v2.json   # → 무시됨
git check-ignore ml/career-strategy-llm/out/eval-sync/.git/config             # → 무시됨
```
→ `out/eval-sync/` 에 repo 를 clone 해도(내부 `.git/` 포함) 메인 repo 는 그 무엇도 추적하지 않는다. 서브모듈/gitlink 가 생기지 않는다.

## 3. 목표 구조
```text
CareerTuner/
└─ ml/career-strategy-llm/
   ├─ eval/      # 골든셋·작은 입력  → 메인 repo
   ├─ scripts/   # 하니스 코드        → 메인 repo
   ├─ reports/   # 요약 리포트        → 메인 repo
   └─ out/       # gitignore
      └─ eval-sync/   # ← 별도 private artifact repo (여기서만 push/pull)
         ├─ c-fit-3b-eval-v2.json
         ├─ c-fit-3b-base-eval-v2.json
         ├─ c-fit-3b-pairwise-input.json
         ├─ logs/
         └─ README.md
```

## 4. 1회 셋업 (★사람 또는 4090 Codex 가 수행 — Claude Code 는 하지 않음)
```powershell
# (1) GitHub 등에서 private repo 생성: 예) careertuner-eval-artifacts  (개인/팀 private)
# (2) 메인 repo 안의 gitignored 위치에 clone. ★ git submodule add 쓰지 말 것!
cd ml/career-strategy-llm/out
git clone https://github.com/<owner>/careertuner-eval-artifacts.git eval-sync
```
- 토큰/자격증명 입력은 사람이 직접. Claude Code 는 토큰을 다루지 않는다.

## 5. 4090 Codex — 결과 push
```powershell
# 평가(reports/19) 후 결과를 artifact repo 로 복사
Copy-Item ml/career-strategy-llm/out/eval/c-fit-3b-*-v2.json ml/career-strategy-llm/out/eval-sync/ -Force
Copy-Item ml/career-strategy-llm/out/eval/c-fit-3b-pairwise-input.json ml/career-strategy-llm/out/eval-sync/ -Force
cd ml/career-strategy-llm/out/eval-sync
git add c-fit-3b-eval-v2.json c-fit-3b-base-eval-v2.json c-fit-3b-pairwise-input.json
git commit -m "eval v2 결과 업로드"
git push
```

## 6. 노트북(Claude Code) — pull 후 분석
```powershell
cd ml/career-strategy-llm/out/eval-sync
git pull
# → Claude 가 pairwise-input.json 등을 읽어 6축 판정(reports/18) → 요약만 메인 repo reports/ 에 반영
```

## 7. 금지
```text
- git submodule add 금지 · .gitmodules 생성 금지 · 메인 repo gitlink 금지
- 메인 repo 에 eval 결과 JSON 커밋 금지
- gguf/safetensors/merged model 업로드 금지
- 개인정보/API key/token 업로드 금지
- F/D 모델·설정 수정 금지
```

## 8. 안전 점검(주기적)
```bash
# 메인 repo status 에 out/eval-sync 관련 항목이 '절대' 보이면 안 된다
git -C <CareerTuner> status --short | grep eval-sync   # 출력 없어야 정상
# .gitmodules 가 생기지 않았는지
test -f <CareerTuner>/.gitmodules && echo "WARNING: .gitmodules 존재" || echo "OK: 서브모듈 없음"
```
- Claude Code 역할: artifact repo 를 pull 해 **분석**하고, **요약만** 메인 repo `reports/` 에 커밋한다. 실제 private repo 생성·토큰 입력·서브모듈 등록은 하지 않는다.
