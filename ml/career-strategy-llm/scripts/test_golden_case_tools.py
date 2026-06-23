"""golden_case_tools 단위 테스트 — 결정론 필드 계산 + 검증 규칙.

실행: python scripts/test_golden_case_tools.py
추가로 전체 골든셋(eval/golden_fit_cases.jsonl)이 0오류로 검증되는지도 확인.
"""
import json
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from golden_case_tools import (  # noqa: E402
    compute_matched_missing, decision_band_ok, validate_case, STD_REQUIRED_KEYS, STD_FORBIDDEN_KEYS,
)


def make_case(**over):
    req = over.get("required", ["Java", "Spring", "SQL"])
    pref = over.get("preferred", ["AWS"])
    prof = over.get("profile", ["Java"])
    dec = over.get("decision", "HOLD")
    fs = over.get("fitScore", 30)
    matched, mreq, mpref = compute_matched_missing(req, pref, prof)
    return {
        "id": over.get("id", "case-test-x"), "domainGroup": "IT_SOFTWARE", "expectedDecision": dec,
        "input": {"companyName": "C", "jobTitle": "J", "desiredJob": "J", "experienceLevel": "신입",
                  "requiredSkills": req, "preferredSkills": pref, "duties": "d",
                  "profileSkills": prof, "profileCertificates": over.get("certs", []),
                  "matchedSkills": matched, "missingRequiredSkills": mreq, "missingPreferredSkills": mpref,
                  "fitScore": fs, "applyDecision": dec},
        "expected": {"requiredKeys": STD_REQUIRED_KEYS, "forbiddenKeys": STD_FORBIDDEN_KEYS,
                     "mustMention": over.get("mustMention", []), "mustNotMention": over.get("mustNotMention", []),
                     "allowedSkills": req + pref, "forbiddenClaims": over.get("forbiddenClaims", ["합격 보장", "합격률"])},
    }


class ComputeTest(unittest.TestCase):
    def test_exact_match_case_insensitive_cert_excluded(self):
        # profileSkills 정확일치만 matched, cert 는 매칭 제외
        matched, mreq, mpref = compute_matched_missing(
            ["Java", "Spring", "정보처리기사"], ["AWS"], ["java", "Git"])
        self.assertEqual(["Java"], matched)             # java==Java(대소문자), Spring/정보처리기사 미보유
        self.assertEqual(["Spring", "정보처리기사"], mreq)
        self.assertEqual(["AWS"], mpref)

    def test_band(self):
        self.assertTrue(decision_band_ok("APPLY", 80, 0))
        self.assertFalse(decision_band_ok("APPLY", 80, 1))   # 필수 미충족이면 APPLY 아님
        self.assertTrue(decision_band_ok("COMPLEMENT_BEFORE_APPLY", 60, 2))
        self.assertTrue(decision_band_ok("HOLD", 55, 3))
        self.assertFalse(decision_band_ok("HOLD", 70, 0))


class ValidateTest(unittest.TestCase):
    def test_clean_case_ok(self):
        self.assertEqual([], validate_case(make_case(fitScore=30, decision="HOLD")))

    def test_detects_wrong_matched_via_band(self):
        c = make_case(fitScore=30, decision="HOLD")
        c["input"]["fitScore"] = 95   # HOLD 밴드 밖
        self.assertTrue(any("밴드" in e for e in validate_case(c)))

    def test_detects_mustnotmention_in_allowed(self):
        c = make_case(required=["Java", "Prometheus"], mustNotMention=["Prometheus"])
        self.assertTrue(any("mustNotMention" in e for e in validate_case(c)))

    def test_detects_bare_forbidden_claim(self):
        c = make_case(forbiddenClaims=["합격 보장", "즉시 지원"])
        self.assertTrue(any("bare" in e for e in validate_case(c)))


class FullGoldenSetTest(unittest.TestCase):
    def test_repo_golden_set_validates_clean(self):
        path = os.path.join(os.path.dirname(__file__), "..", "eval", "golden_fit_cases.jsonl")
        errs, ids = [], set()
        for line in open(path, encoding="utf-8"):
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            c = json.loads(line)
            self.assertNotIn(c["id"], ids, f"중복 id {c['id']}")
            ids.add(c["id"])
            errs += validate_case(c)
        self.assertEqual([], errs, f"골든셋 검증 오류: {errs}")
        self.assertGreaterEqual(len(ids), 36, "골든셋 36건 이상")


if __name__ == "__main__":
    unittest.main(verbosity=2)
