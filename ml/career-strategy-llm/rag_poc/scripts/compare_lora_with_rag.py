"""R2 A/B 오프라인 비교 — 3B LoRA only vs 3B LoRA + retrievedContext.

같은 입력에서 retrievedContext 유무만 다르게(A/B) 모델을 호출하고, 기존 C 평가 지표(eval_fit_model.evaluate
재사용: contract success/json/CJK/E1 grounding/E2/bad_skills raw·residual)로 채점·집계 + RAG 전용 지표.

모드:
  --mock           : Ollama 없이 결정론 mock 출력으로 **하니스 end-to-end 검증**(오프라인). raw 는 out/(gitignore).
  --base-url/--model: 실제 Ollama 3B LoRA 호출(4090 등). raw outputs 는 --out-dir(CareerTunerAI results)에만.

semantic valid_error 는 하니스만으로 단정하지 않는다 — normalized residual>0 이면 judge packet 절차(별도). 이 스크립트는 raw/residual 까지만.
점수/applyDecision 은 입력값을 그대로 두며, 이 스크립트는 그 값을 생성·변경하지 않는다.
"""
import argparse
import json
import os
import sys
import time
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "..", "..", "scripts"))  # ml/.../scripts
from build_rag_eval_cases import build_cases, build_pairs  # noqa: E402
from eval_fit_model import evaluate  # noqa: E402  (기존 채점 로직 재사용)
from _io_utils import configure_stdout_utf8  # noqa: E402

VARIANTS = ["lora_only", "lora_with_retrieved_context"]


def _mock_output(case):
    """결정론 mock: allowed 스킬만 사용한 유효 계약 JSON(날조 없음) — 하니스 검증용."""
    allowed = (case.get("expected") or {}).get("allowedSkills") or ["역량"]
    missing = (case.get("input") or {}).get("missingSkills") or allowed[:1]
    return json.dumps({
        "fitSummary": "입력 근거로 적합도를 설명합니다.",
        "strengths": [f"{allowed[0]} 보유"],
        "risks": [f"{missing[0]} 보완 필요"] if missing else ["보완 필요"],
        "strategyActions": [f"{missing[0]} 학습" if missing else "유지"],
        "learningTaskReasons": [{"skill": missing[0] if missing else allowed[0], "why": "공고 요구 역량"}],
    }, ensure_ascii=False)


def _call_ollama(base_url, model, messages, timeout):
    body = json.dumps({"model": model, "messages": messages, "temperature": 0.2,
                       "stream": False}).encode("utf-8")
    req = urllib.request.Request(base_url.rstrip("/") + "/chat/completions", data=body,
                                 headers={"Content-Type": "application/json"})
    t0 = time.time()
    with urllib.request.urlopen(req, timeout=timeout) as r:
        data = json.loads(r.read().decode("utf-8"))
    dt = (time.time() - t0) * 1000.0
    return data["choices"][0]["message"]["content"], dt


def _ctx_support(content, retrieved_context):
    """RAG 전용 proxy: 출력이 retrievedContext 텍스트 토큰과 겹치는 정도(근거 활용 신호)."""
    import re
    tok = lambda s: set(re.findall(r"[A-Za-z0-9가-힣]+", str(s).lower()))
    ctok = set()
    for c in retrieved_context or []:
        ctok |= tok(c.get("text", ""))
    if not ctok:
        return {"contextProvided": False, "contextOverlap": 0.0}
    otok = tok(content)
    return {"contextProvided": True, "contextOverlap": round(len(ctok & otok) / max(1, len(ctok)), 4)}


def run_variant(pair, variant, *, mock, base_url, model, timeout, repeat):
    case = {"id": pair["caseId"], "input": pair["variants"][variant]["input"], "expected": pair["expected"]}
    ctx = pair["variants"][variant]["input"].get("retrievedContext") or []
    msgs = pair["variants"][variant]["messages"]
    rows = []
    for _ in range(repeat):
        if mock:
            content, dt = _mock_output(case), 0.0
        else:
            content, dt = _call_ollama(base_url, model, msgs, timeout)
        row = evaluate(case, content, None)
        row["latency_ms"] = dt
        row["rag"] = _ctx_support(content, ctx)
        rows.append(row)
    return rows


def aggregate(rows, *, mock=False):
    n = len(rows) or 1
    def rate(pred): return round(sum(1 for r in rows if pred(r)) / n, 3)
    raw = sum(len((r.get("detail") or {}).get("bad_skills") or []) for r in rows)
    resid = sum(len((r.get("detail") or {}).get("bad_skills_residual") or []) for r in rows)
    lat = [r.get("latency_ms", 0.0) for r in rows]
    agg = {
        "n": len(rows),
        "success_rate": rate(lambda r: r.get("success")),
        "json_parse_rate": rate(lambda r: r.get("json_ok")),
        "cjk_leak_rate": rate(lambda r: r.get("cjk_leak")),
        "grounding_violation_count": sum(1 for r in rows if r.get("grounding_violation")),
        "e2_high_count": sum(len((r.get("named_entities") or {}).get("high") or []) for r in rows),
        "hallucinated_skill_raw": raw,
        "hallucinated_skill_normalized_residual": resid,
        "avg_latency_ms": round(sum(lat) / n, 1),
        "context_used_avg_overlap": round(sum(r["rag"]["contextOverlap"] for r in rows) / n, 4),
    }
    if mock:
        # mock 은 retrievedContext 를 안 봐서 A==B → overlap 이 B 에서만 비제로인 건 '동어반복 아티팩트'다.
        # RAG 효과 지표를 실측으로 오인하지 않게 명시 표식(reports/55). mock 은 배선검증 전용.
        agg["mock"] = True
        agg["rag_metrics_valid"] = False
        agg["_note"] = "mock=retrievedContext 무시 → A==B; context_used_avg_overlap 은 tautological(실측 아님)"
    return agg


def main(argv=None):
    configure_stdout_utf8()
    ap = argparse.ArgumentParser()
    ap.add_argument("--mock", action="store_true")
    ap.add_argument("--base-url", default="http://localhost:11434/v1")
    ap.add_argument("--model", default="careertuner-c-career-strategy-3b")
    ap.add_argument("--timeout", type=int, default=240)
    ap.add_argument("--repeat", type=int, default=2)
    ap.add_argument("--out-dir", help="raw per-run 저장(미지정 시 저장 안 함). CareerTunerAI results 권장.")
    a = ap.parse_args(argv)

    pairs = build_pairs(build_cases())
    by_variant = {v: [] for v in VARIANTS}
    raw_records = []
    for p in pairs:
        for v in VARIANTS:
            rows = run_variant(p, v, mock=a.mock, base_url=a.base_url, model=a.model,
                               timeout=a.timeout, repeat=a.repeat)
            by_variant[v] += rows
            raw_records.append({"caseId": p["caseId"], "variant": v,
                                "n": len(rows), "agg": aggregate(rows, mock=a.mock)})

    print(f"=== R2 A/B 비교 (mock={a.mock} model={a.model} repeat={a.repeat} cases={len(pairs)}) ===")
    if a.mock:
        print("  ⚠ mock 모드 — retrievedContext 무시(A==B). RAG 효과 지표는 실측 아님(배선검증 전용).")
    for v in VARIANTS:
        print(f"[{v}] {aggregate(by_variant[v], mock=a.mock)}")

    if a.out_dir:
        os.makedirs(a.out_dir, exist_ok=True)
        with open(os.path.join(a.out_dir, "rag_r2_ab_raw.json"), "w", encoding="utf-8") as f:
            json.dump({"mock": bool(a.mock), "model": a.model, "repeat": a.repeat, "cases": len(pairs),
                       "rag_metrics_valid": (not a.mock),
                       "perCaseVariant": raw_records,
                       "summary": {v: aggregate(by_variant[v], mock=a.mock) for v in VARIANTS}},
                      f, ensure_ascii=False, indent=2)
        print(f"  raw → {a.out_dir}/rag_r2_ab_raw.json")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
