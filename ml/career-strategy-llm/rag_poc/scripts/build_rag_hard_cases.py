"""RAG R2b hard-case 빌더 — stage R2b (reports/54).

R2(reports/53)의 합성 8케이스는 너무 쉬워(A 도 success 1.0/E1 0/hallucination 0) RAG 개선 폭이 안 보였다.
여기서는 base(LoRA only)가 grounding/hallucination 에서 실제로 헷갈릴 hard-case(fixtures/hard_cases.jsonl)를 로드해
A/B pair 를 만든다. **중복 구현 금지** — pair 생성·prompt 조립은 기존
build_rag_eval_cases.build_pairs + rag_prompt_builder 를 그대로 재사용한다.

A = lora_only(retrievedContext 없음), B = lora_with_retrieved_context(있음). 같은 caseId,
차이는 retrievedContext 유무뿐(test_rag_hard_cases 로 보장). fitScore/applyDecision 은 서버 입력값 그대로(LLM 불변).
synthetic 전용(개인정보 없음).
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from build_rag_eval_cases import build_pairs  # noqa: E402  (pair 생성 재사용 — 중복 구현 금지)

FIXTURE = os.path.join(HERE, "..", "fixtures", "hard_cases.jsonl")

# hard_cases 의 input 키 중 build_pairs/rag_prompt_builder 가 prompt 에 렌더하는 base 키.
# (평가기 eval_fit_model.evaluate 가 스캔하는 requiredSkills/missingRequiredSkills/companyName 등 추가 키도
#  input 에 함께 보존해 grounding/E2 관측이 hard-case 에서 실제로 동작하게 한다.)


def load_hard_cases(path=FIXTURE):
    """fixtures/hard_cases.jsonl → build_pairs 가 기대하는 case dict 리스트.

    각 줄(주석 '#'·빈 줄 제외)은 {caseId, hardType, input, expected, retrievedContext, ragHint, ragGoal}.
    build_pairs 는 caseId/input/expected/retrievedContext/ragHint 를 사용한다(나머지는 메타로 보존만).
    """
    cases = []
    with open(path, encoding="utf-8") as f:
        for ln, line in enumerate(f, 1):
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            try:
                rec = json.loads(line)
            except json.JSONDecodeError as e:
                raise SystemExit(f"hard_cases {path}:{ln} JSON 오류: {e}")
            cases.append({
                "caseId": rec["caseId"],
                "hardType": rec.get("hardType"),
                "input": rec["input"],
                "expected": rec.get("expected") or {},
                "retrievedContext": rec.get("retrievedContext") or [],
                "ragHint": rec.get("ragHint", ""),
                "ragGoal": rec.get("ragGoal", ""),
            })
    return cases


def build_hard_pairs(path=FIXTURE):
    """hard_cases 로드 → 기존 build_pairs 로 A/B pair 생성(같은 caseId, ctx 유무만 차이).

    pair 에 hardType/ragGoal 메타를 부가(채점에는 영향 없음, 분석/리포트용).
    """
    cases = load_hard_cases(path)
    pairs = build_pairs(cases)
    meta = {c["caseId"]: c for c in cases}
    for p in pairs:
        c = meta[p["caseId"]]
        p["hardType"] = c.get("hardType")
        p["ragGoal"] = c.get("ragGoal")
    return pairs


def main():
    cases = load_hard_cases()
    pairs = build_hard_pairs()
    types = {}
    for c in cases:
        types[c["hardType"]] = types.get(c["hardType"], 0) + 1
    print(f"hard cases={len(cases)} pairs={len(pairs)} (각 pair=A/B 2변형)")
    print(f"  hardType 분포: {json.dumps(types, ensure_ascii=False)}")
    for p in pairs:
        a = p["variants"]["lora_only"]["input"]
        b = p["variants"]["lora_with_retrieved_context"]["input"]
        print(f"  {p['caseId']} [{p['hardType']}]: A.ctx={'retrievedContext' in a} "
              f"B.ctx={len(b.get('retrievedContext', []))} | {p['ragGoal']}")


if __name__ == "__main__":
    main()
