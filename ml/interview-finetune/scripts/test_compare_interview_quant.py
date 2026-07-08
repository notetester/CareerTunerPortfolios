"""compare_interview_quant + eval_interview_model 검증 테스트(pytest).

precedent: ml/career-strategy-llm/scripts/test_eval_robustness.py

실행: python -m pytest ml/interview-finetune/scripts/test_compare_interview_quant.py -q

라이브 모델 없이(GPU 게이트 다운) 전부 오프라인으로 돈다:
  - compare_interview_quant 의 순수 math core 를 합성 paired 로 직접 검증
  - eval_interview_model.py 를 --mock 서브프로세스로 돌려 well-formed 결과 파일 생성 검증
"""
import json
import os
import subprocess
import sys

import pytest

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, SCRIPT_DIR)

import compare_interview_quant as C          # noqa: E402
import eval_interview_model as E             # noqa: E402

REPO_ROOT = os.path.abspath(os.path.join(SCRIPT_DIR, "..", "..", ".."))
GOLDEN = os.path.join(SCRIPT_DIR, "..", "eval", "interview_golden_cases.jsonl")


def _pair(cid, tag, expected, q4, f16, q4_ok=True, f16_ok=True, q4_cjk=False, f16_cjk=False):
    return {"id": cid, "tag": tag, "expected": expected,
            "q4_score": q4, "f16_score": f16,
            "q4_json_ok": q4_ok, "f16_json_ok": f16_ok,
            "q4_cjk": q4_cjk, "f16_cjk": f16_cjk}


# ── compute_verdict / math core ──────────────────────────────────────────────

def test_identical_scores_pass():
    """q4==f16 전 케이스 → delta 0, agreement 1.0, PASS."""
    paired = [_pair(f"c{i}", "TECH", 70, 70, 70) for i in range(10)]
    v = C.compute_verdict(paired)
    assert v["mean_abs_diff"] == 0.0
    assert v["agreement_at_10"] == 1.0
    assert all(d["delta"] == 0 for d in v["per_case_deltas"])
    assert v["verdict"] == "PASS"


def test_systematic_plus8_shift_fails_on_mean_diff():
    """q4 가 전부 +8 이동 → mean|q4-f16|=8 > 5 이므로 c1 FAIL, verdict FAIL.

    (agreement@10 은 8<=10 이라 여전히 1.0 이지만, mean-diff 기준에서 걸려야 한다.)"""
    paired = [_pair(f"c{i}", "TECH", 60, 68, 60) for i in range(10)]
    v = C.compute_verdict(paired)
    assert v["mean_abs_diff"] == 8.0
    assert v["agreement_at_10"] == 1.0
    assert v["criteria"]["c1_mean_abs_diff_le_5"]["pass"] is False
    assert v["verdict"] == "FAIL"


def test_agreement_drops_below_threshold_fails():
    """일부 케이스가 10 초과로 벌어지면 agreement@10 < 0.90 → c2 FAIL."""
    paired = [_pair(f"c{i}", "TECH", 70, 70, 70) for i in range(8)]
    paired += [_pair("big1", "TECH", 70, 90, 70), _pair("big2", "TECH", 70, 88, 70)]
    v = C.compute_verdict(paired)
    assert v["agreement_at_10"] == 0.8
    assert v["criteria"]["c2_agreement_at_10_ge_0.90"]["pass"] is False
    assert v["verdict"] == "FAIL"


def test_q4_worse_parse_rate_fails():
    """q4 json_parse_rate < f16 → c3 FAIL (계약 회귀)."""
    paired = [_pair(f"c{i}", "TECH", 70, 70, 70) for i in range(9)]
    paired.append(_pair("bad", "TECH", 70, None, 70, q4_ok=False, f16_ok=True))
    v = C.compute_verdict(paired)
    assert v["q4_json_parse_rate"] < v["f16_json_parse_rate"]
    assert v["criteria"]["c3_q4_parse_ge_f16_parse"]["pass"] is False
    assert v["verdict"] == "FAIL"


def test_q4_mae_much_worse_fails():
    """q4 가 골든에서 크게 벗어나 f16 MAE+3 초과 → c4 FAIL."""
    # f16 은 골든과 정확히 일치(MAE 0), q4 는 전부 +10 (MAE 10) → 10 > 0+3.
    paired = [_pair(f"c{i}", "TECH", 60, 70, 60) for i in range(10)]
    v = C.compute_verdict(paired)
    assert v["f16_mae_vs_golden"] == 0.0
    assert v["q4_mae_vs_golden"] == 10.0
    assert v["criteria"]["c4_q4_mae_not_worse_than_f16_plus_3"]["pass"] is False
    assert v["verdict"] == "FAIL"


def test_new_cjk_leak_flagged_and_fails():
    """q4 에서만 새로 CJK 누출 → c5 FAIL, new_cjk_leak_ids 에 표시."""
    paired = [_pair(f"c{i}", "TECH", 70, 70, 70) for i in range(9)]
    paired.append(_pair("leak", "TECH", 70, 70, 70, q4_cjk=True, f16_cjk=False))
    v = C.compute_verdict(paired)
    assert v["new_cjk_leak_ids"] == ["leak"]
    assert v["criteria"]["c5_no_new_cjk_leaks"]["pass"] is False
    assert v["verdict"] == "FAIL"


def test_preexisting_cjk_not_counted_as_new():
    """f16 에도 이미 있던 CJK 는 'new' 아님 → c5 통과."""
    paired = [_pair(f"c{i}", "TECH", 70, 70, 70) for i in range(9)]
    paired.append(_pair("leak", "TECH", 70, 70, 70, q4_cjk=True, f16_cjk=True))
    v = C.compute_verdict(paired)
    assert v["new_cjk_leak_ids"] == []
    assert v["criteria"]["c5_no_new_cjk_leaks"]["pass"] is True


def test_none_scores_excluded_from_delta():
    """점수 None 케이스는 delta/agreement 분모에서 제외(정의 불가)."""
    paired = [_pair("ok", "TECH", 70, 72, 70),
              _pair("bad", "TECH", 70, None, 70, q4_ok=False)]
    deltas = C.per_case_deltas(paired)
    assert [d["id"] for d in deltas] == ["ok"]
    assert C.agreement_at(paired) == 1.0  # ok 하나만 분모, 2<=10


def test_by_tag_splits_metrics():
    """유형별 MAE/parse/cjk 분해."""
    paired = [_pair("t1", "TECH", 60, 62, 60), _pair("t2", "TECH", 60, 60, 60),
              _pair("p1", "PERSONALITY", 80, 85, 80)]
    tags = C.by_tag(paired, "q4")
    assert set(tags) == {"TECH", "PERSONALITY"}
    assert tags["TECH"]["n"] == 2
    assert tags["PERSONALITY"]["mae_vs_golden"] == 5.0  # |85-80|


# ── eval_interview_model parse core (extractJsonSpan + clampScore 미러) ────────

def test_parse_well_formed_json():
    score, ok = E.parse_score('{"score": 85, "feedback": "좋음", "improvedAnswer": ""}')
    assert ok is True and score == 85


def test_parse_json_with_fence_and_prose():
    """```json 펜스 + 앞뒤 잡설이 있어도 extractJsonSpan 이 span 을 뽑는다."""
    content = '설명입니다.\n```json\n{"score": 73, "feedback": "x"}\n```\n끝.'
    score, ok = E.parse_score(content)
    assert ok is True and score == 73


def test_parse_clamps_out_of_range():
    """0..100 밖 점수는 clamp."""
    assert E.parse_score('{"score": 250, "feedback": "x"}') == (100, True)
    assert E.parse_score('{"score": -30, "feedback": "x"}') == (0, True)


def test_malformed_json_is_not_ok():
    score, ok = E.parse_score('{"score": 80, "feedback": ')  # 끊긴 JSON
    assert ok is False and score is None


def test_empty_output_is_not_ok():
    assert E.parse_score("") == (None, False)
    assert E.parse_score("   ") == (None, False)


def test_missing_score_key_is_not_ok():
    score, ok = E.parse_score('{"feedback": "score 없음"}')
    assert ok is False and score is None


def test_non_numeric_score_is_not_ok():
    score, ok = E.parse_score('{"score": "높음", "feedback": "x"}')
    assert ok is False and score is None


def test_bool_score_rejected():
    """bool 은 int 하위형이지만 계약 위반으로 본다."""
    score, ok = E.parse_score('{"score": true, "feedback": "x"}')
    assert ok is False and score is None


def test_cjk_leak_detection():
    """일본어 가나 + 중국어 한자는 잡고, 순수 한국어는 안 잡는다."""
    assert bool(E.CJK_RE.search("机器学习 채점"))    # 중국어 한자
    assert bool(E.CJK_RE.search("これは 답변"))       # 일본어 가나
    assert not E.CJK_RE.search("스프링 의존성 주입입니다")  # 순수 한글


def test_user_prompt_matches_backend_shape():
    """build_user_prompt 가 OssAnswerEvaluator user 프롬프트 뼈대와 동일한 라벨/모범답안 블록을 포함."""
    case = {"companyName": "토스", "jobTitle": "백엔드 개발자",
            "question": "DI란?", "answer": "외부 주입",
            "referenceModelAnswer": "의존성 주입은 ..."}
    u = E.build_user_prompt(case)
    assert "회사명: 토스" in u
    assert "직무명: 백엔드 개발자" in u
    assert "기준 모범답안(이 답안을 만점 기준으로 삼는다):" in u
    assert "질문:\nDI란?" in u
    assert "지원자 답변:\n외부 주입" in u
    assert '반드시 {"score": 0~100 정수' in u


def test_user_prompt_omits_model_answer_block_when_absent():
    case = {"companyName": "X", "jobTitle": "Y", "question": "q", "answer": "a"}
    u = E.build_user_prompt(case)
    assert "기준 모범답안" not in u


# ── --mock 엔드투엔드(라이브 모델 없음) ───────────────────────────────────────

def _run_eval_mock(model, out_path):
    cmd = [sys.executable, os.path.join(SCRIPT_DIR, "eval_interview_model.py"),
           "--cases", GOLDEN, "--mock", "--model", model, "--out", out_path, "--save-raw"]
    r = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", cwd=REPO_ROOT)
    assert r.returncode == 0, f"eval_interview_model --mock 실패:\n{r.stderr}"
    with open(out_path, encoding="utf-8") as f:
        return json.load(f)


def test_mock_end_to_end_produces_wellformed_result(tmp_path):
    """--mock 러너가 라이브 모델 없이 well-formed 결과 파일을 만든다.

    골든 20건, 전부 파싱 성공, CJK 누출 0, score 는 0..100 정수."""
    out = str(tmp_path / "mock-q4.json")
    data = _run_eval_mock("interview-3b:q4", out)
    s = data["summary"]
    assert s["mock"] is True
    assert s["model"] == "interview-3b:q4"
    assert s["cases"] == 20
    assert s["json_parse_rate"] == 1.0
    assert s["cjk_leak_rate"] == 0.0
    rows = data["results"]
    assert len(rows) == 20
    for r in rows:
        assert r["json_ok"] is True
        assert isinstance(r["parsed_score"], int) and 0 <= r["parsed_score"] <= 100
        assert r["cjk_leak"] is False
        assert r["raw"]  # save-raw


def test_mock_full_chain_to_verdict(tmp_path):
    """골든 -> q4 mock -> f16 mock -> compare -> verdict. 라이브 모델 전무.

    mock 은 q4/f16 를 골든 기준 ±2 로 흔들므로 mean-diff·MAE 가 작아 PASS 가 나와야 한다."""
    q4_out = str(tmp_path / "q4.json")
    f16_out = str(tmp_path / "f16.json")
    q4_data = _run_eval_mock("interview-3b:q4", q4_out)
    f16_data = _run_eval_mock("interview-3b", f16_out)

    golden = C.load_golden(GOLDEN)
    paired = C.build_paired(q4_data, f16_data, golden)
    assert len(paired) == 20
    v = C.compute_verdict(paired)
    # ±2 지터라 최악 delta 4 <= 10, mean-diff 작음 → PASS.
    assert v["mean_abs_diff"] <= 5.0
    assert v["agreement_at_10"] >= 0.90
    assert v["verdict"] == "PASS"
    # 유형 3종이 모두 표에 있어야(TECH/PERSONALITY/SITUATION).
    assert set(v["by_tag_q4"]) == {"TECH", "PERSONALITY", "SITUATION"}


def test_compare_cli_writes_verdict(tmp_path):
    """compare_interview_quant.py CLI 가 verdict JSON 파일을 쓴다(엔드투엔드 I/O 경계)."""
    q4_out = str(tmp_path / "q4.json")
    f16_out = str(tmp_path / "f16.json")
    _run_eval_mock("interview-3b:q4", q4_out)
    _run_eval_mock("interview-3b", f16_out)
    verdict_out = str(tmp_path / "verdict.json")
    cmd = [sys.executable, os.path.join(SCRIPT_DIR, "compare_interview_quant.py"),
           "--q4", q4_out, "--f16", f16_out, "--cases", GOLDEN, "--out", verdict_out]
    r = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", cwd=REPO_ROOT)
    assert r.returncode == 0, f"compare 실패:\n{r.stderr}"
    with open(verdict_out, encoding="utf-8") as f:
        v = json.load(f)
    assert v["verdict"] in ("PASS", "FAIL")
    assert v["paired_case_count"] == 20
    assert "criteria" in v


if __name__ == "__main__":
    sys.exit(pytest.main([__file__, "-q"]))
