"""E2 관측형 named-entity observer 단위 테스트 — CRM465/CRMONE 포착 + 오탐 방지 + 필드 스코프.

실행: python scripts/test_entity_observer.py   (stdlib unittest, 모델/네트워크 불필요)
관측은 '측정 전용'이라 success 에 영향을 주지 않아야 한다(reject/fallback 아님)는 점도 검증.
보정(적대적 검증 반영): review 는 보유 문맥(fitSummary+strengths)만, high 는 전 필드(코드+coinage),
버전표기(Python3/C++17) 양 티어 제외, coinage 는 {crm} 만.
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
SUP = supported_terms(CASE)


def high_of(full_text):
    return scan_named_entities(full_text, "", SUP)["high"]


def review_of(possession_text):
    # high 는 비우고(full_text=""), 보유 문맥만 review 로 스캔
    return scan_named_entities("", possession_text, SUP)["review"]


class SupportedTermsTest(unittest.TestCase):
    def test_input_terms_are_supported(self):
        for t in ("java", "spring", "boot", "sql", "docker", "git"):
            self.assertIn(t, SUP, f"{t} 는 입력 기반 supported 여야 함")


class HighTierTest(unittest.TestCase):
    def test_alphanumeric_product_codes(self):
        self.assertIn("CRM465", high_of("CRM465 운영 경험이 강점입니다."))
        self.assertIn("ERP900", high_of("ERP900 도입 경험 보유."))
        self.assertIn("ToolX12", high_of("ToolX12 를 활용했습니다."))

    def test_crmone_coinage(self):
        # 핵심: digit 없는 가짜 CRM 제품명(CRMONE) → high(coinage). 1차 관측에서 review 로 샜던 케이스.
        self.assertIn("CRMONE", high_of("CRM 도구(CRMONE, HubSpot 등) 기초 실습을 진행하세요."))

    def test_coinage_only_crm_not_erp_ml_db(self):
        # erp/ml/db/api 는 실제 제품(ERPNext/MLflow/DBeaver/Apigee) 충돌로 coinage 제외
        for t in ("ERPNext", "MLflow", "DBeaver", "Apigee", "Airflow"):
            self.assertEqual([], high_of(f"{t} 를 학습하세요."), f"{t} 는 high 면 안 됨")

    def test_version_suffix_of_generic_not_high(self):
        self.assertEqual([], high_of("Java21 과 Python3 환경에서 개발."))


class ReviewTierTest(unittest.TestCase):
    def test_unsupported_propernoun_in_possession(self):
        # 보유 문맥의 입력 밖 대문자 고유명사 → review(낮은 신뢰도)
        self.assertIn("Mythos", review_of("Mythos 라는 사내 시스템 운영 경험 보유."))

    def test_no_flag_generic_tech(self):
        self.assertEqual([], review_of("Java, React, SQL 보유."))

    def test_no_flag_version_suffix(self):
        # Python3/C++17 양 티어 버전 가드(Python3 는 기존 review 누출 버그였음)
        self.assertEqual([], review_of("Python3 와 C++17 보유."))

    def test_no_flag_terms_in_input(self):
        self.assertEqual([], review_of("Spring Boot 와 Docker 경험 보유."))

    def test_no_flag_allowed_examples(self):
        self.assertEqual([], review_of("SAP 와 QuickBooks 를 다뤘습니다."))

    def test_no_flag_category_terms(self):
        self.assertEqual([], review_of("CRM 과 ERP, API 경험."))

    def test_korean_only_no_false_positive(self):
        self.assertEqual({"high": [], "review": []},
                         scan_named_entities("필수 역량이 부족합니다.", "필수 역량이 부족합니다.", SUP))


class FieldScopeTest(unittest.TestCase):
    def test_review_only_possession_not_learning(self):
        # 같은 고유명사라도 학습추천(strategyActions 등=full_text 만, possession 아님)이면 review 아님
        full_only = scan_named_entities("Mythos 를 학습하세요.", "", SUP)
        self.assertEqual([], full_only["review"], "학습추천 문맥의 고유명사는 review 아님")
        self.assertEqual([], full_only["high"], "Mythos 는 코드/coinage 아니라 high 도 아님")

    def test_high_catches_coinage_in_learning_context(self):
        # 가짜 제품(CRMONE)은 학습추천(full_text)에 있어도 high
        r = scan_named_entities("CRMONE 학습 추천", "", SUP)
        self.assertIn("CRMONE", r["high"])


class ObservationDoesNotAffectSuccessTest(unittest.TestCase):
    def test_crm465_observed_but_run_still_success(self):
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

    def test_crmone_in_strategyactions_caught_as_high(self):
        content = json.dumps({
            "fitSummary": "보완 권장", "strengths": ["Java 보유"], "risks": [],
            "strategyActions": ["CRM 도구(CRMONE 등) 기초 실습"],  # 가짜 제품 in 학습추천
            "learningTaskReasons": [],
        }, ensure_ascii=False)
        row = evaluate(CASE, content, None)
        self.assertIn("CRMONE", row["named_entities"]["high"])
        self.assertEqual([], row["named_entities"]["review"])  # 학습추천이라 review 아님

    def test_clean_output_no_entities(self):
        content = json.dumps({
            "fitSummary": "필수 역량 보완이 필요합니다.",
            "strengths": ["Java 보유"], "risks": ["SQL 미보유"],
            "strategyActions": ["SQL 학습"], "learningTaskReasons": [],
        }, ensure_ascii=False)
        row = evaluate(CASE, content, None)
        self.assertEqual([], row["named_entities"]["high"])
        self.assertEqual([], row["named_entities"]["review"])


if __name__ == "__main__":
    unittest.main(verbosity=2)
