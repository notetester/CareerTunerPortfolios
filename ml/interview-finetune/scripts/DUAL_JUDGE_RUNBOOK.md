# 이중 LLM 판정단 런북 (Claude Opus 4.8 + Codex GPT‑5.5)

면접 답변 채점(및 게이트 사유 판정)의 골드 기준을 **자체 3B(F16) 자기참조 대신 최상위 LLM 2인의
독립 합의**로 만든다. F16의 오차를 기준 삼아 부정확을 증폭하는 문제를 막는다.

## 판정자
- **Claude Opus 4.8** — 메인 에이전트(또는 병렬 서브에이전트)가 루브릭으로 직접 채점.
- **Codex GPT‑5.5** — 공유 4090에 설치된 Codex CLI 를 SSH 로 호출(AI↔AI 채널).

## 프로토콜 (파일 기반 — 따옴표 중첩 회피)
1. **블라인드 입력 준비**: 케이스에서 점수를 제거({id, questionType, question, referenceModelAnswer, answer}),
   공통 루브릭 `eval/judge_rubric.md`.
2. **Codex 채점**(4090):
   - 전송: `scp -i ~/.ssh/careertuner_4090_full_ed25519 judge_rubric.md cases.jsonl hsy82@localhost:ct_judge/`
   - 실행: `ssh -n -i <key> hsy82@localhost 'codex exec --sandbox danger-full-access --skip-git-repo-check "In folder ct_judge read judge_rubric.md and cases.jsonl, score each candidate answer per the rubric, write one JSON line per case {id,score,reason} to ct_judge/scores.jsonl, print DONE"'`
   - 회수: `scp -i <key> hsy82@localhost:ct_judge/scores.jsonl .`
3. **Claude 채점**: 동일 블라인드 케이스를 동일 루브릭으로 채점(대량이면 서브에이전트 병렬, 청크 분할).
4. **판정단 골드**: `panel = round((claude + codex) / 2)`. `|claude − codex| > 10` 케이스는 격리·재검토.

## 함정 (실측으로 확인)
- 원격 셸은 **Windows cmd.exe** — 작은따옴표가 그룹핑이 아니다. **bash 바깥 작은따옴표 + cmd 안쪽 큰따옴표**,
  프롬프트에 중첩 따옴표를 넣지 말고 **파일 기반**으로. (JSON 인용은 파일에만.)
- `codex exec` 는 stdin 대기 → **`ssh -n`**(stdin=/dev/null). git repo 밖이면 **`--skip-git-repo-check`**.
- 키/호스트: `careertuner_4090_full_ed25519` → `hsy82@localhost`(Tailscale). Ollama 는 로컬 터널 `127.0.0.1:11435`.
- Codex 출력 형식: 헤더 뒤 `codex\n<응답>\ntokens used` — 파일로 쓰게 하면 파싱 불필요.

## 관측된 신뢰도 (2026-07-07, 60케이스)
- Claude ↔ Codex: 평균차 **5.07점**, 10점내 일치 **0.967**. 두 최상위 LLM 이 이 정도로 일치 → 골드 신뢰 가능.
- 이 방법이 F16 자기참조가 숨긴 사실을 드러냄: 3B(F16·Q4)는 중간 밴드(40~84) 채점 MAE 13~17로 약하다.

## 산출물
- 판정단 점수: `eval/panel_scores.jsonl`(id, panel, claude, codex). 골든셋 expectedScore = panel.
- raw(개별 응답 원문)은 본체 미커밋 → CareerTunerAI submodule.
