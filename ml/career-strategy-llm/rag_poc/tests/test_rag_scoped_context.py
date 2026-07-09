"""R2c scoped retrievedContext 테스트 (reports/56).

실행: python rag_poc/tests/test_rag_scoped_context.py   (stdlib unittest, fresh checkout 실행 가능)
핵심: 역할/소유/주장정책 부여 · 직무요구/catalog 는 user_owned 아님 · user_evidence 만 보유 가능 ·
점수/판단 불변 · score/vectorDistance 미주입 · A/B/C 같은 base input · C 만 scoped/guard · UTF-8 guard.
"""
import os
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "scripts"))
from _io_utils import configure_stdout_utf8  # noqa: E402
from build_rag_scoped_context import (  # noqa: E402
    sanitize_scoped_context, build_scoped_input, build_scoped_messages, build_abc_pairs,
    role_for, SCOPED_KEYS, SCOPED_RAG_ADDENDUM, SCOPED_VARIANT,
)

CASE_INPUT = {
    "profileSnapshot": {"matchedSkills": ["SQL"]},
    "jobPostingSummary": {"jobTitle": "백엔드"},
    "fitScore": 72, "applyDecision": "COMPLEMENT_BEFORE_APPLY",
    "matchedSkills": ["SQL"], "missingSkills": ["Java", "Spring Boot"],
}
CTX = [
    {"sourceType": "job_posting", "sourceId": "job-1", "text": "Spark, ETL 역량이 요구됩니다.",
     "score": 0.9, "vectorDistance": 0.1, "fitScore": 99},  # 금지/메타 키 일부러 포함
    {"sourceType": "certification_catalog", "sourceId": "cert-sqld", "text": "SQLD는 SQL 모델링을 검증합니다."},
    {"sourceType": "user_profile_summary", "sourceId": "profile-a", "text": "지원자는 SQL 경험이 있습니다."},
]


class ScopedContextTest(unittest.TestCase):
    def test_1_items_have_role_ownership_policy(self):
        s = sanitize_scoped_context(CTX)
        for it in s:
            self.assertIn("contextRole", it)
            self.assertIn("ownership", it)
            self.assertIn("claimPolicy", it)

    def test_2_job_requirement_not_user_owned(self):
        role, own, policy = role_for("job_posting")
        self.assertEqual("job_requirement", role)
        self.assertNotEqual("user_owned", own)
        self.assertEqual("do_not_treat_as_user_owned", policy)

    def test_3_catalog_fact_not_user_owned(self):
        for st in ("skill_catalog", "certification_catalog"):
            role, own, policy = role_for(st)
            self.assertEqual("catalog_fact", role)
            self.assertNotEqual("user_owned", own)
            self.assertEqual("definition_only_not_user_owned", policy)

    def test_4_only_user_profile_is_user_owned(self):
        # user_profile_summary 만 ownership=user_owned
        self.assertEqual("user_owned", role_for("user_profile_summary")[1])
        for st in ("job_posting", "skill_catalog", "certification_catalog", "company_research_summary"):
            self.assertNotEqual("user_owned", role_for(st)[1])

    def test_5_does_not_change_score_or_decision(self):
        out = build_scoped_input(CASE_INPUT, CTX)
        self.assertEqual(72, out["fitScore"])
        self.assertEqual("COMPLEMENT_BEFORE_APPLY", out["applyDecision"])
        self.assertNotIn("retrievedContext", CASE_INPUT)  # 원본 비파괴

    def test_6_strips_score_and_vector_metadata(self):
        s = sanitize_scoped_context(CTX)
        for it in s:
            self.assertEqual(set(it.keys()), set(SCOPED_KEYS))
            self.assertNotIn("score", it)
            self.assertNotIn("vectorDistance", it)
            self.assertNotIn("fitScore", it)

    def test_7_abc_share_same_base_input(self):
        pairs = build_abc_pairs()
        self.assertGreaterEqual(len(pairs), 12)
        for p in pairs:
            a = dict(p["variants"]["lora_only"]["input"])
            b = dict(p["variants"]["lora_with_retrieved_context"]["input"])
            c = dict(p["variants"][SCOPED_VARIANT]["input"])
            for x in (a, b, c):
                x.pop("retrievedContext", None)
            self.assertEqual(a, b, f"{p['caseId']} A/B base 다름")
            self.assertEqual(a, c, f"{p['caseId']} A/C base 다름")

    def test_8_only_c_has_scoped_context_and_guard(self):
        pairs = build_abc_pairs()
        for p in pairs:
            a = p["variants"]["lora_only"]["input"]
            b = p["variants"]["lora_with_retrieved_context"]["input"]
            c = p["variants"][SCOPED_VARIANT]["input"]
            self.assertNotIn("retrievedContext", a)
            # B 의 ctx 항목엔 contextRole 없음, C 엔 있음
            for it in (b.get("retrievedContext") or []):
                self.assertNotIn("contextRole", it)
            for it in (c.get("retrievedContext") or []):
                self.assertIn("contextRole", it)
            # claim guard 는 C 시스템 메시지에만
            b_sys = p["variants"]["lora_with_retrieved_context"]["messages"][0]["content"]
            c_sys = p["variants"][SCOPED_VARIANT]["messages"][0]["content"]
            self.assertNotIn("RAG scoped 지침", b_sys)
            self.assertIn("RAG scoped 지침", c_sys)

    def test_9_guard_states_claim_policy(self):
        # 가드가 핵심 원칙(job_requirement/catalog_fact 를 보유로 보지 마라)을 명시
        self.assertIn("job_requirement", SCOPED_RAG_ADDENDUM)
        self.assertIn("catalog_fact", SCOPED_RAG_ADDENDUM)
        self.assertIn("user_evidence", SCOPED_RAG_ADDENDUM)

    def test_10_value_level_score_leak_blocked(self):
        leak = [{"sourceType": "skill_catalog", "sourceId": "x", "text": "회사 메모: fitScore: 99 / applyDecision: APPLY"}]
        with self.assertRaises(AssertionError):
            sanitize_scoped_context(leak)

    def test_11_utf8_guard_no_exception(self):
        try:
            configure_stdout_utf8()
        except Exception as e:  # noqa: BLE001
            self.fail(f"configure_stdout_utf8 raised {e!r}")


if __name__ == "__main__":
    unittest.main(verbosity=2)
