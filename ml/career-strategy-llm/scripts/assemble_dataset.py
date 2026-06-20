"""
워크플로우 raw 결과 -> C 학습 JSONL 조립.
(D의 ml/interview-finetune/assemble_dataset.py 를 C 도메인으로 교체)

합성 데이터 생성 워크플로우(Phase 1에서 작성 예정, D의 generate_dataset.workflow.js 미러)가
seed별로 선생(Claude)의 설명 출력을 만든다. 이 스크립트는 그 raw 를 받아
task별 {"messages":[system,user,assistant]} JSONL 로 조립한다. 출력은 prepare_data.py 호환.

raw 형식(워크플로우 return):
  [{ "seed": {...seed_profiles.py 산출...}, "fit_explain": {...선생 출력 JSON...} }, ...]
  · seed 에는 규칙엔진 사전계산값(fitScore/applyDecision/matched/missing)이 포함돼 있다.
  · fit_explain 은 점수 없는 설명 JSON(fitSummary/strengths/risks/strategyActions/learningTaskReasons).

★ 뉴로-심볼릭: 점수·판단은 user(입력)에만 넣고, assistant(타깃)에는 설명만 둔다.

사용:
    python assemble_dataset.py --input raw_generated.json --out dataset.jsonl
"""
import argparse
import json
from collections import Counter

from synth_prompts import FIT_EXPLAIN_SYS


def _join(items):
    return ", ".join(items) if items else "(없음)"


def build_fit_user(seed):
    """seed(규칙엔진 사전계산값 포함) -> C_FIT_EXPLAIN user 메시지(구조화 입력 텍스트).

    런타임에 백엔드가 모델에 보낼 입력과 같은 형태여야 한다(train/serve skew 방지).
    통합 시 backend FitAnalysis 입력 빌더와 이 포맷을 정합시킨다.
    """
    lines = [
        "# 적합도 분석 입력",
        f"회사명: {seed.get('companyName', '')}",
        f"직무명: {seed.get('jobTitle', '')}",
        f"희망 직무: {seed.get('desiredJob', '')}",
        f"경력 수준: {seed.get('experienceLevel', '')}",
        "",
        "## 공고 요구",
        f"- 필수 스킬: {_join(seed.get('requiredSkills'))}",
        f"- 우대 스킬: {_join(seed.get('preferredSkills'))}",
        f"- 주요 업무: {seed.get('duties', '')}",
        "",
        "## 지원자 프로필",
        f"- 보유 스킬: {_join(seed.get('profileSkills'))}",
        f"- 보유 자격증: {_join(seed.get('profileCertificates'))}",
        "",
        "## 규칙엔진 사전계산 (서버 확정값 — 변경 금지)",
        f"- 적합도 점수(fitScore): {seed.get('fitScore')}",
        f"- 지원판단(applyDecision): {seed.get('applyDecision')}",
        f"- 매칭 역량: {_join(seed.get('matchedSkills'))}",
        f"- 부족 필수역량: {_join(seed.get('missingRequiredSkills'))}",
        f"- 부족 우대역량: {_join(seed.get('missingPreferredSkills'))}",
    ]
    return "\n".join(lines)


def _row(task, system, user, assistant):
    return {"task": task, "messages": [
        {"role": "system", "content": system},
        {"role": "user", "content": user},
        {"role": "assistant", "content": assistant},
    ]}


def assemble(raw):
    rows = []
    for item in raw:
        seed = item.get("seed") or {}
        fit = item.get("fit_explain")
        if not seed or not fit:
            continue
        # C_FIT_EXPLAIN: 구조화 입력(점수 포함) -> 설명 JSON(점수 제외)
        user = build_fit_user(seed)
        assistant = json.dumps(fit, ensure_ascii=False)
        rows.append(_row("C_FIT_EXPLAIN", FIT_EXPLAIN_SYS, user, assistant))
        # TODO(Phase 2~3): item["strategy"] / ["learning_roadmap"] / ["trend_summary"] 추가
    return rows


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--input", required=True, help="워크플로우 raw 결과 JSON")
    ap.add_argument("--out", default="dataset.jsonl")
    args = ap.parse_args()

    with open(args.input, "r", encoding="utf-8") as f:
        data = json.load(f)
    # 워크플로우 output({summary,...,result:[...]}) 또는 순수 리스트 둘 다 지원
    raw = data["result"] if isinstance(data, dict) and "result" in data else data
    rows = assemble(raw)
    with open(args.out, "w", encoding="utf-8") as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")

    print(f"{len(rows)}줄 -> {args.out}  (seed {len(raw)}개)")
    print("task별:", dict(Counter(r["task"] for r in rows)))


if __name__ == "__main__":
    main()
