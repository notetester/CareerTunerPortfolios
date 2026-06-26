"""eval_fit_model.evaluate 견고성 테스트 — 모델 스키마 일탈에 크래시하지 않는다.

실행: python scripts/test_eval_robustness.py
배경: 7B base 가 learningTaskReasons 를 [{skill,why}] 가 아니라 ["스킬", ...] 문자열 리스트로
출력해 하니스가 AttributeError 로 죽었다(2026-06-26 golden60 smoke). 평가는 모델 출력 형식이
계약과 달라도 측정만 하고(실패로 기록) 죽지 않아야 한다.
"""
import json
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from eval_fit_model import evaluate  # noqa: E402

CASE = {"id": "t", "input": {}, "expected": {"allowedSkills": ["Spring Boot", "SQL"]}}


def _content(ltr):
    return json.dumps({
        "fitSummary": "보완 권장", "strengths": ["Java 보유"], "risks": ["SQL 미보유"],
        "strategyActions": ["SQL 학습"], "learningTaskReasons": ltr,
    }, ensure_ascii=False)


class EvalRobustnessTest(unittest.TestCase):
    def test_string_items_no_crash_and_checked(self):
        # 문자열 항목: 스킬명으로 취급. allowed 'SQL'은 통과, 범위밖 '블록체인 운영'은 bad_skill.
        row = evaluate(CASE, _content(["SQL", "블록체인 운영"]), None)
        self.assertIn("블록체인 운영", row["detail"]["bad_skills"])
        self.assertNotIn("SQL", row["detail"]["bad_skills"])
        # 범위밖은 정규화로도 안 풀려 judge 잔여로 남아야(whitewash 금지)
        self.assertIn("블록체인 운영", row["detail"]["bad_skills_residual"])

    def test_dict_items_still_work(self):
        row = evaluate(CASE, _content([{"skill": "코드리뷰", "why": "x"}]), None)
        self.assertIn("코드리뷰", row["detail"]["bad_skills"])

    def test_mixed_and_malformed_items_no_crash(self):
        # 문자열 + 객체 + None + 숫자 혼합 — 어떤 것도 크래시시키지 않는다.
        row = evaluate(CASE, _content(["SQL", {"skill": "Spring Boot"}, None, 42]), None)
        self.assertTrue(row["json_ok"])
        self.assertEqual([], row["detail"]["bad_skills"])  # SQL·Spring Boot 모두 allowed

    def test_wa_compound_string_not_whitewashed(self):
        # 7B 실제 패턴 '와/과' 복합 스킬구 — 크래시 없이 처리, 정규화로 자동 whitewash되지 않고 judge 잔여로 남김.
        case = {"id": "t", "input": {}, "expected": {"allowedSkills": ["Java", "Spring Boot"]}}
        c = json.dumps({"fitSummary": "보완", "strengths": [], "risks": [],
                        "strategyActions": [], "learningTaskReasons": ["Java와 Spring Boot"]}, ensure_ascii=False)
        row = evaluate(case, c, None)
        self.assertTrue(row["json_ok"])
        self.assertIn("Java와 Spring Boot", row["detail"]["bad_skills"])
        self.assertIn("Java와 Spring Boot", row["detail"]["bad_skills_residual"])  # judge로, whitewash 아님

    def test_product_code_string_not_whitewashed(self):
        # 입력 밖 제품코드가 문자열 항목으로 와도 normalizer 가 whitewash 하지 않고 valid_error 후보(잔여)로 남김.
        case = {"id": "t", "input": {}, "expected": {"allowedSkills": ["고객 상담", "VOC 관리"]}}
        c = json.dumps({"fitSummary": "보완", "strengths": [], "risks": [],
                        "strategyActions": [], "learningTaskReasons": ["CRM465 운영"]}, ensure_ascii=False)
        row = evaluate(case, c, None)
        self.assertIn("CRM465 운영", row["detail"]["bad_skills"])
        self.assertIn("CRM465 운영", row["detail"]["bad_skills_residual"])
        self.assertNotIn("CRM465 운영", row["detail"]["bad_skills_resolved_fp"])


if __name__ == "__main__":
    unittest.main(verbosity=2)
