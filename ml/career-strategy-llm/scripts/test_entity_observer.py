"""E2 관측형 named-entity observer 단위 테스트 — CRM465류 포착 + 오탐 방지.

실행: python scripts/test_entity_observer.py   (stdlib unittest, 모델/네트워크 불필요)
관측은 '측정 전용'이라 success 에 영향을 주지 않아야 한다(reject/fallback 아님)는 점도 검증.
"""
import json
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from eval_fit_model import evaluate, scan_named_entities, supported_terms  # noqa: E402

# 입력에 Spring Boot/SQL/정보처리기사가 있는 전형적 케이스
CASE = {
    "id": "case-test-001", "domainGroup": "IT_SOFTWARE", "expectedDecision": "HOLD",
    "input": {
        "companyName": "테크노바", "jobTitle": "백엔드 개발자", "desiredJob": "백엔드 개발자",
        "duties": "백엔드 API 개발", "requiredSkills": ["Java", "Spring Boot", "SQL"],
        "preferredSkills": ["Docker"], "profileSkills": ["Java", "Git"],
        "profileCertificates": ["정보처리기사"], "matchedSkills": ["Java"],
        "missingRequiredSkills": ["Spring Boot", "SQL"], "missingPreferredSkills": ["Docker"],
        "fitScore": 30, "applyDecision": "HOLD",
    },
    "expected": {"allowedSkills": ["Java", "Spring Boot", "SQL", "Docker"],
                 "forbiddenClaims": ["합격 보장"]},
}


class SupportedTermsTest(unittest.TestCase):
    def test_input_terms_are_supported(self):
        terms = supported_terms(CASE)
        for t in ("java", "spring", "boot", "sql", "docker", "git"):
            self.assertIn(t, terms, f"{t} 는 입력 기반 supported 여야 함")


class ScanNamedEntitiesTest(unittest.TestCase):
    def setUp(self):
        self.supported = supported_terms(CASE)

    def _high(self, text):
        return scan_named_entities(text, self.supported)["high"]

    def _review(self, text):
        return scan_named_entities(text, self.supported)["review"]

    def test_flags_alphanumeric_product_codes(self):
        # 입력에 없는 알파벳+숫자 제품코드 → high
        self.assertIn("CRM465", self._high("CRM465 운영 경험이 강점입니다."))
        self.assertIn("ERP900", self._high("ERP900 도입 경험 보유."))
        self.assertIn("ToolX12", self._high("ToolX12 를 활용했습니다."))

    def test_no_flag_generic_tech(self):
        # 일반 기술명은 절대 flag 안 함(오탐 방지 핵심)
        self.assertEqual([], self._high("Java, React, SQL 을 사용했습니다."))
        self.assertEqual([], self._review("Java, React, SQL 을 사용했습니다."))

    def test_no_flag_version_suffix_of_generic(self):
        # Java21/Python3 같은 버전 표기는 제품코드 오탐 아님
        self.assertEqual([], self._high("Java21 과 Python3 환경에서 개발."))

    def test_no_flag_terms_present_in_input(self):
        # 입력에 있는 명칭(Spring Boot, Docker)은 보유 서술해도 관측 대상 아님
        self.assertEqual([], self._high("Spring Boot 와 Docker 경험 보유."))
        self.assertEqual([], self._review("Spring Boot 와 Docker 경험 보유."))

    def test_no_flag_allowed_examples(self):
        # SAP/QuickBooks 처럼 허용 예시는 제외
        self.assertEqual([], self._high("SAP 와 QuickBooks 를 다뤘습니다."))
        self.assertEqual([], self._review("SAP 와 QuickBooks 를 다뤘습니다."))

    def test_no_flag_bare_category_terms(self):
        # 단독 범주어(CRM/ERP/API)는 고유명사 날조가 아님 → review 제외
        self.assertEqual([], self._review("CRM 과 ERP, API 설계 경험."))

    def test_review_tier_catches_unsupported_propernoun(self):
        # 입력 밖 대문자 고유명사 → review(낮은 신뢰도, 사람 검토용)
        self.assertIn("Mythos", self._review("Mythos 라는 사내 시스템을 운영."))

    def test_korean_only_text_no_false_positive(self):
        # 한글만 있는 정상 설명은 아무것도 flag 안 함
        self.assertEqual({"high": [], "review": []},
                         scan_named_entities("필수 역량이 부족해 보완이 필요합니다.", self.supported))


class ObservationDoesNotAffectSuccessTest(unittest.TestCase):
    def test_crm465_observed_but_run_still_success(self):
        # 계약 위반이 없으면, CRM465 가 관측돼도 success 는 유지(관측은 reject/fallback 아님)
        content = json.dumps({
            "fitSummary": "필수 일부 보유, 보완 권장",
            "strengths": ["CRM465 운영 경험"],  # ← 입력 밖 제품코드(관측 대상)
            "risks": ["Spring Boot 미보유"],
            "strategyActions": ["Spring Boot 학습 후 재분석"],
            "learningTaskReasons": [{"skill": "Spring Boot", "why": "필수 역량"}],
        }, ensure_ascii=False)
        row = evaluate(CASE, content, None)
        self.assertTrue(row["success"], "계약 위반이 없으면 관측과 무관하게 success")
        self.assertIn("CRM465", row["named_entities"]["high"])

    def test_clean_output_no_entities(self):
        content = json.dumps({
            "fitSummary": "필수 역량 보완이 필요합니다.",
            "strengths": ["Java 보유"], "risks": ["SQL 미보유"],
            "strategyActions": ["SQL 학습"], "learningTaskReasons": [],
        }, ensure_ascii=False)
        row = evaluate(CASE, content, None)
        self.assertEqual([], row["named_entities"]["high"])


if __name__ == "__main__":
    unittest.main(verbosity=2)
