"""
워크플로우 raw 결과 -> 학습 JSONL 조립.

워크플로우(gen-interview-dataset)가 생성한 raw(가짜 분석 + 질문 + 채점)를 받아,
briefing.py 로 QGEN user(브리핑)를 조립하고 task별 {"messages":[system,user,assistant]} 로 만든다.
출력은 prepare_data.py 호환.

raw 형식(워크플로우 return): [{ seed, analysis_q:{company_analysis, job_analysis, questions}, eval:{items} }]

사용:
    python assemble_dataset.py --input raw_generated.json --out dataset.jsonl
"""
import argparse
import json
from collections import Counter

from briefing import build_briefing
from synth_prompts import QGEN_SYS, MA_SYS, EVAL_SYS, PROBE_SYS


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
        aq = item.get("analysis_q") or {}
        ev = item.get("eval") or {}
        questions = aq.get("questions") or []
        if not questions:
            continue

        # QGEN: 가짜 분석 -> 브리핑(briefing.py) -> 질문6
        briefing = build_briefing(seed, company=aq.get("company_analysis"), job=aq.get("job_analysis"))
        rows.append(_row("QGEN", QGEN_SYS, briefing, json.dumps(questions, ensure_ascii=False)))

        # MODEL_ANSWER + EVAL: 질문별 모범답안 + 답변 3종 채점
        for it in ev.get("items") or []:
            qi = it.get("question_index", -1)
            if qi < 0 or qi >= len(questions):
                continue
            q = questions[qi]["question"]
            ma = (it.get("model_answer") or "").strip()
            if not ma:
                continue
            rows.append(_row("MODEL_ANSWER", MA_SYS,
                             f"회사명: {seed.get('company_name', '')}\n직무명: {seed.get('job_title', '')}\n질문:\n{q}",
                             ma))
            for c in it.get("cases") or []:
                user = (f"질문:\n{q}\n\n기준 모범답안(만점 기준):\n{ma}\n\n"
                        f"지원자 답변:\n{c.get('answer', '')}")
                asst = json.dumps({"score": c.get("score", 0), "feedback": c.get("feedback", "")},
                                  ensure_ascii=False)
                rows.append(_row("EVAL", EVAL_SYS, user, asst))

        # PROBE: 압박 모드 — 질문+답변(quality 매칭)별 반박 꼬리질문
        ev_by_qi = {it.get("question_index"): it for it in (ev.get("items") or [])}
        for pit in (item.get("probe") or {}).get("items") or []:
            qi = pit.get("question_index", -1)
            if qi < 0 or qi >= len(questions) or qi not in ev_by_qi:
                continue
            cases = ev_by_qi[qi].get("cases") or []
            ans = next((c.get("answer") for c in cases if c.get("quality") == pit.get("quality")), None)
            probe = (pit.get("probe") or "").strip()
            if not ans or not probe:
                continue
            rows.append(_row("PROBE", PROBE_SYS,
                             f"질문:\n{questions[qi]['question']}\n\n지원자 답변:\n{ans}",
                             probe))
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
