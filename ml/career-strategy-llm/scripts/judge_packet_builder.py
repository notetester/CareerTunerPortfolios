"""semantic skill judge — stage 1.5: judge_packet 빌더.

평가 결과(`eval_fit_model.py` 의 {summary, results})에서 HALLUCINATED_SKILL 후보를 모아
stage 1 정규화기(`skill_normalizer`)로 명백한 오탐을 거른 뒤, **남은 모호 후보만**
AI judge 가 읽을 수 있는 packet 으로 만든다.

산출:
  judge_packet.jsonl        — judge 입력(후보당 1줄, 풀 스키마)
  judge_chatgpt_packet.md   — ChatGPT/사람이 읽는 형태(같은 후보, 루브릭 포함)
  normalization_stats.json  — raw/정규화/잔여 카운트(병렬 지표 근거)

원칙: 기존 raw hallucination 지표를 **대체하지 않는다**(병렬). stage 1 은 명백한 오탐만
내리고, valid_error 단정은 절대 하지 않는다(judge/사람 몫).

사용:
  python scripts/judge_packet_builder.py \
    --result careertuner-c-career-strategy-3b=<LoRA.json> \
    --result qwen2.5:3b-instruct=<base.json> \
    --cases eval/golden_fit_cases.jsonl \
    --out-dir out/judge/2026-06-23-golden-set-002-review
"""
import argparse
import json
import os
import sys

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, SCRIPT_DIR)
from skill_normalizer import classify_flagged_skill, JUDGE_STATUSES, RESOLVED_FP_STATUSES  # noqa: E402

DECISIONS = ["valid_error", "acceptable_gray", "harness_false_positive", "needs_policy"]


def load_cases(path):
    cases = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            c = json.loads(line)
            cases[c["id"]] = c
    return cases


def load_result(path):
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    model = (data.get("summary") or {}).get("model") or "unknown"
    return model, data.get("results", [])


def _excerpt_for(row, flagged):
    """flagged skill 이 나온 learningTaskReasons 항목(skill+why)을 raw excerpt 로."""
    parsed = row.get("parsed") or {}
    for item in parsed.get("learningTaskReasons", []) or []:
        if isinstance(item, dict) and str(item.get("skill", "")).strip() == str(flagged).strip():
            return {"skill": item.get("skill"), "why": item.get("why")}
    # parsed 없으면 raw_output 일부
    raw = row.get("raw_output") or ""
    idx = raw.find(str(flagged))
    if idx >= 0:
        return {"snippet": raw[max(0, idx - 40):idx + len(str(flagged)) + 80]}
    return {"skill": flagged, "why": None}


def _missing_skills(inp):
    return list(inp.get("missingRequiredSkills") or []) + list(inp.get("missingPreferredSkills") or [])


def build_candidates(rows_by_model, cases):
    """결과(모델별 rows) + 골든셋에서 후보 추출 + stage1 분류.

    반환: (candidates, stats)
      candidates — judge 로 보낼 (soft_match/unresolved) 후보 dict 리스트(케이스·flagged 단위 dedup)
      stats — raw/정규화/잔여 카운트 + 상태별 분포 + 결정론 해소(FP) 목록
    """
    by_key = {}              # (caseId, flaggedKey) -> candidate
    status_counts = {}
    raw_flag_items = 0
    resolved_fp = []         # 결정론으로 내린 오탐(감사용)

    for model, rows in rows_by_model.items():
        for row in rows:
            cid = row.get("id")
            bad = (row.get("detail") or {}).get("bad_skills") or []
            case = cases.get(cid) or {}
            inp = case.get("input") or {}
            exp = case.get("expected") or {}
            allowed = exp.get("allowedSkills") or []
            for flagged in bad:
                raw_flag_items += 1
                cl = classify_flagged_skill(flagged, allowed)
                st = cl["status"]
                status_counts[st] = status_counts.get(st, 0) + 1
                occ = {"model": model, "run": row.get("run")}
                if st in RESOLVED_FP_STATUSES:
                    resolved_fp.append({"caseId": cid, "flaggedText": flagged,
                                        "method": cl["method"], "matchedAllowed": cl["matchedAllowed"],
                                        **occ})
                    continue
                if st not in JUDGE_STATUSES:
                    continue
                key = (cid, "".join(str(flagged).split()).lower())
                if key not in by_key:
                    by_key[key] = {
                        "candidateId": f"{cid}::{key[1]}",
                        "caseId": cid,
                        "flagType": "HALLUCINATED_SKILL",
                        "field": "learningTaskReasons.skill",
                        "flaggedText": flagged,
                        "expectedDecision": case.get("expectedDecision"),
                        "jobTitle": inp.get("jobTitle"),
                        "experienceLevel": inp.get("experienceLevel"),
                        "jobRequirements": {
                            "requiredSkills": inp.get("requiredSkills") or [],
                            "preferredSkills": inp.get("preferredSkills") or [],
                            "duties": inp.get("duties"),
                        },
                        "allowedSkills": allowed,
                        "matchedSkills": inp.get("matchedSkills") or [],
                        "missingSkills": _missing_skills(inp),
                        "profileSkills": inp.get("profileSkills") or [],
                        "profileCertificates": inp.get("profileCertificates") or [],
                        "normalizer": {"status": st, "method": cl["method"],
                                       "matchedAllowed": cl["matchedAllowed"],
                                       "unmatchedParts": cl["unmatchedParts"],
                                       "softHits": cl["softHits"]},
                        "rawExcerpt": _excerpt_for(row, flagged),
                        "occurrences": [],
                    }
                by_key[key]["occurrences"].append(occ)

    candidates = sorted(by_key.values(), key=lambda c: (c["caseId"], c["flaggedText"]))
    stats = {
        "raw_hallucination_flag_items": raw_flag_items,
        "stage1_resolved_false_positive": sum(status_counts.get(s, 0) for s in RESOLVED_FP_STATUSES),
        "stage1_residual_to_judge": sum(status_counts.get(s, 0) for s in JUDGE_STATUSES),
        "unique_judge_candidates": len(candidates),
        "status_counts": status_counts,
        "resolved_false_positives": resolved_fp,
    }
    return candidates, stats


CHATGPT_HEADER = """# Semantic Skill Judge — 후보 판정 패킷 (ChatGPT/사람용)

자동 하니스(`eval_fit_model.py`)가 HALLUCINATED_SKILL 로 잡았으나, **결정론 정규화(stage 1)로
풀리지 않은 모호 후보**만 모았습니다. 각 후보를 아래 네 가지 중 하나로 판정해 주세요.
이 판정은 **1차 판정**이며 최종 정책은 사람이 확정합니다(기존 raw 지표는 그대로 유지).

## 판정 라벨
- `valid_error`: 실제 오류. 입력(공고/프로필/allowedSkills)에 **없는** 스킬·제품·플랫폼을 보유 또는 핵심 전략처럼 서술.
- `acceptable_gray`: 표현은 느슨하지만 입력에 있는 스킬/요구사항과 **의미상 연결**됨(같은 스킬을 풀어 쓴 정도).
- `harness_false_positive`: 문자열/공백/콤마/괄호/접미어 문제로 하니스가 잘못 잡음(의미상 allowed 와 동일).
- `needs_policy`: AI 도 판단은 가능하나 **팀 기준이 필요한** 애매한 케이스.

## 응답 형식 (후보당 JSON 한 줄)
```
{"candidateId": "...", "judgeId": "<당신의 식별자>", "decision": "acceptable_gray", "confidence": 0.0~1.0, "rationale": "한 줄 근거", "needsHumanReview": false}
```
- **★ `judgeId` 는 반드시 본인 식별자로 채우세요** — 예: `gpt-5.5`, `gemini-2.0`, `claude-opus-4`, 또는 본인 이름.
  `chatgpt` 같은 placeholder 를 그대로 두지 마세요(출처 자기검증이 안 됩니다 — reports/43 caveat).
  과거 호환을 위해 `judge` 필드명을 써도 되지만, 가능하면 `judgeId` 로 본인을 명시하세요.
- 판단이 갈리거나 confidence 가 낮으면 `needsHumanReview: true` 로 표시하세요.

---
"""


def render_chatgpt_packet(candidates):
    lines = [CHATGPT_HEADER]
    for i, c in enumerate(candidates, 1):
        ex = c["rawExcerpt"]
        why = ex.get("why") or ex.get("snippet") or ""
        lines.append(f"### {i}. `{c['candidateId']}`")
        lines.append(f"- **flaggedText**: `{c['flaggedText']}`  (field: {c['field']})")
        lines.append(f"- **직무**: {c.get('jobTitle')} / {c.get('experienceLevel')} · 기대결정 {c.get('expectedDecision')}")
        lines.append(f"- **allowedSkills**: {c['allowedSkills']}")
        lines.append(f"- **missingSkills**: {c['missingSkills']}")
        lines.append(f"- **요구사항(duties)**: {c['jobRequirements'].get('duties')}")
        lines.append(f"- **normalizer**: status={c['normalizer']['status']} method={c['normalizer']['method']} "
                     f"softHits={c['normalizer']['softHits']} unmatched={c['normalizer']['unmatchedParts']}")
        lines.append(f"- **모델 출력 근거(why)**: {why}")
        lines.append(f"- **occurrences**: {c['occurrences']}")
        lines.append("")
        lines.append(f'응답: `{{"candidateId": "{c["candidateId"]}", "judgeId": "<당신의 식별자>", "decision": "", "confidence": 0.0, "rationale": "", "needsHumanReview": false}}`')
        lines.append("\n---")
    return "\n".join(lines)


def write_outputs(candidates, stats, out_dir):
    os.makedirs(out_dir, exist_ok=True)
    packet_path = os.path.join(out_dir, "judge_packet.jsonl")
    with open(packet_path, "w", encoding="utf-8") as f:
        for c in candidates:
            f.write(json.dumps(c, ensure_ascii=False) + "\n")
    md_path = os.path.join(out_dir, "judge_chatgpt_packet.md")
    with open(md_path, "w", encoding="utf-8") as f:
        f.write(render_chatgpt_packet(candidates))
    stats_path = os.path.join(out_dir, "normalization_stats.json")
    with open(stats_path, "w", encoding="utf-8") as f:
        json.dump(stats, f, ensure_ascii=False, indent=2)
    return packet_path, md_path, stats_path


def main():
    ap = argparse.ArgumentParser(description="semantic skill judge — judge_packet 빌더(stage 1 정규화 후 잔여 후보)")
    ap.add_argument("--result", action="append", default=[],
                    help="model=path 형식 평가 결과(JSON). 여러 번 지정 가능.")
    ap.add_argument("--cases", required=True, help="골든셋 JSONL(eval/golden_fit_cases.jsonl)")
    ap.add_argument("--out-dir", required=True)
    args = ap.parse_args()

    cases = load_cases(args.cases)
    rows_by_model = {}
    for spec in args.result:
        if "=" in spec:
            label, path = spec.split("=", 1)
        else:
            label, path = None, spec
        model, rows = load_result(path)
        rows_by_model[label or model] = rows

    candidates, stats = build_candidates(rows_by_model, cases)
    packet_path, md_path, stats_path = write_outputs(candidates, stats, args.out_dir)

    print(f"[judge_packet] raw flag items={stats['raw_hallucination_flag_items']} "
          f"→ stage1 오탐 해소={stats['stage1_resolved_false_positive']} "
          f"잔여(judge)={stats['stage1_residual_to_judge']} "
          f"unique 후보={stats['unique_judge_candidates']}")
    print(f"  status_counts={stats['status_counts']}")
    print(f"  → {packet_path}\n  → {md_path}\n  → {stats_path}")


if __name__ == "__main__":
    main()
