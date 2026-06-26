"""skill_normalizer(stage 1 결정론 정규화) 단위 테스트.

실행: python scripts/test_skill_normalizer.py   (stdlib unittest, 모델/네트워크 불필요)
원칙 검증: ① 명백한 gray-zone 오탐만 내린다 ② 범위밖을 valid_error 로 자동 단정하지 않는다
(judge 후보로만 남긴다) ③ allowedSkills 가 비면 모두 unresolved.
"""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from skill_normalizer import classify_flagged_skill, JUDGE_STATUSES, RESOLVED_FP_STATUSES  # noqa: E402


def status(flagged, allowed):
    return classify_flagged_skill(flagged, allowed)["status"]


class ListAllMatchTest(unittest.TestCase):
    """콤마/및 나열의 모든 조각이 allowed 면 false_positive(나열 over-count)."""

    def test_comma_list_all_allowed(self):
        self.assertEqual("false_positive", status(
            "협업, 코드리뷰, 커뮤니케이션",
            ["협업", "코드리뷰", "커뮤니케이션", "Spring Boot"]))

    def test_and_conjunction_all_allowed(self):
        self.assertEqual("false_positive", status(
            "재무 설계 및 세무 지식", ["재무 설계", "세무 지식"]))

    def test_whitespace_diff_in_list(self):
        # '영업 관리' vs allowed '영업관리'(공백만 차이) + '실적 분석' 정확 → 둘 다 매칭
        self.assertEqual("false_positive", status(
            "영업 관리 및 실적 분석", ["영업관리", "실적 분석"]))

    def test_long_list_all_allowed(self):
        self.assertEqual("false_positive", status(
            "급여 정산, 4대보험 실무, 근태 관리, 협업, 문서화, 커뮤니케이션",
            ["급여 정산", "4대보험 실무", "근태 관리", "협업", "문서화", "커뮤니케이션"]))


class SuffixAndParenTest(unittest.TestCase):
    """단일 스킬 + wrapper 명사/괄호 → false_positive."""

    def test_suffix_usage(self):
        self.assertEqual("false_positive", status("Figma 사용법", ["Figma", "UX 리서치"]))

    def test_suffix_methodology(self):
        self.assertEqual("false_positive", status("UX 리서치 방법론", ["Figma", "UX 리서치"]))

    def test_paren_expansion(self):
        self.assertEqual("false_positive", status("MSA (Microservices Architecture)", ["Java", "MSA"]))

    def test_suffix_skill_word(self):
        self.assertEqual("false_positive", status("임직원 인터뷰 기술", ["임직원 인터뷰"]))

    def test_suffix_experience(self):
        self.assertEqual("false_positive", status("신규점 오픈 경험", ["신규점 오픈", "SV 경험"]))

    def test_iterative_suffix_strip(self):
        # '프레임워크' + '이해' 두 wrapper 반복 제거 후 '커뮤니케이션' 정확매칭
        self.assertEqual("false_positive", status("커뮤니케이션 프레임워크 이해", ["커뮤니케이션"]))


class SoftMatchTest(unittest.TestCase):
    """allowed 가 부분문자열로 들어있으나 여분 토큰(접미어 아님) 존재 → judge(soft_match)."""

    def test_prefix_context(self):
        self.assertEqual("soft_match", status("데이터 기반 수요예측", ["수요예측", "SCM 시스템 운영"]))

    def test_extra_skill_token(self):
        # '발주 최적화'는 접미어 목록에 없는 추가 개념 → 정확매칭 불가, substring 으로만 soft
        self.assertEqual("soft_match", status("수요예측 기반 발주 최적화", ["수요예측", "SAP WMS"]))

    def test_prefix_modifier_stays_soft(self):
        # 접두 수식('글로벌 공급망')은 제거 대상 아님 → substring soft
        self.assertEqual("soft_match", status("글로벌 공급망 수요예측", ["수요예측"]))


class HeadVerbSuffixTest(unittest.TestCase):
    """헤드 동사형 접미(운영/관리/협상/기초) 제거 후 정확매칭 → false_positive(reports/43 비준).

    안전: 제거 후 allowedSkill 과 정확매칭될 때만 resolve. 매칭 안 되면 unresolved 유지.
    """

    def test_suffix_operation(self):
        self.assertEqual("false_positive", status("SAP WMS 운영", ["SAP WMS", "수요예측"]))

    def test_suffix_management(self):
        self.assertEqual("false_positive", status("위험물 운송 관리", ["위험물 운송", "재고 운영"]))

    def test_suffix_negotiation(self):
        self.assertEqual("false_positive", status("무역 영어 협상", ["무역 영어"]))

    def test_suffix_basics(self):
        self.assertEqual("false_positive", status("SCM 기초", ["SCM", "재고 운영"]))


class ConditionalClauseTest(unittest.TestCase):
    """공고 조건/우대 절이 skill 필드에 새어든 형태 → 절 제거 후 정확매칭 → false_positive."""

    def test_experience_condition(self):
        self.assertEqual("false_positive", status("SV 경험이 있는 경우", ["SV 경험", "점포 운영관리"]))

    def test_if_present_condition(self):
        self.assertEqual("false_positive", status("Python 경험이 있으면", ["Python", "SQL"]))


class UnresolvedTest(unittest.TestCase):
    """결정론으로 못 풂 → judge 후보(절대 valid_error 단정 X)."""

    def test_no_match_out_of_scope_candidate(self):
        r = classify_flagged_skill("헬프데스크 솔루션 이해", ["고객 상담", "VOC 관리", "클레임 응대"])
        self.assertEqual("unresolved", r["status"])
        self.assertEqual("no_match", r["method"])

    def test_partial_list(self):
        # 'LMS 솔루션 선택' 미매칭 + '사용법' filler → 미매칭 잔존 → unresolved
        self.assertEqual("unresolved", status(
            "LMS 솔루션 선택 및 사용법", ["교육과정 기획", "교육 운영", "커뮤니케이션"]))

    def test_system_ops_no_clean_match(self):
        self.assertEqual("unresolved", status(
            "사내 안전관리 시스템 운영", ["산업안전 관리", "위험성 평가"]))


class ConservatismTest(unittest.TestCase):
    """레이어 안전 불변식."""

    def test_never_auto_valid_error(self):
        # 어떤 입력도 'valid_error' 를 결정론으로 내지 않는다(judge/사람 몫)
        for flagged, allowed in [
            ("완전 무관 스킬 XYZ", ["Java", "SQL"]),
            ("코드리뷰", ["Spring Boot"]),
            ("", ["A"]),
        ]:
            self.assertIn(classify_flagged_skill(flagged, allowed)["status"],
                          {"exact", "false_positive", "soft_match", "unresolved"})

    def test_empty_allowed_all_unresolved(self):
        self.assertEqual("unresolved", status("아무 스킬", []))

    def test_out_of_scope_is_unresolved_not_fp(self):
        # 범위밖이 명백해도 결정론은 unresolved 로만(오탐 아님을 단정하지 않음)
        r = classify_flagged_skill("코드리뷰", ["고객 상담", "VOC 관리"])
        self.assertEqual("unresolved", r["status"])
        self.assertNotIn(r["status"], RESOLVED_FP_STATUSES)

    def test_status_partitions(self):
        self.assertEqual(set(), JUDGE_STATUSES & RESOLVED_FP_STATUSES)

    def test_product_code_not_resolved_by_suffix_strip(self):
        # 입력 밖 구체 제품/코드명은 접미(운영) 제거해도 매칭 대상이 없어 unresolved 유지 → valid_error 후보 보호
        r = classify_flagged_skill("CRM465 운영", ["고객 상담", "VOC 관리"])
        self.assertEqual("unresolved", r["status"])
        self.assertNotIn(r["status"], RESOLVED_FP_STATUSES)

    def test_product_code_not_resolved_by_conditional_strip(self):
        r = classify_flagged_skill("CRMOne 도입 경험이 있는 경우", ["VOC 관리", "고객 상담"])
        self.assertEqual("unresolved", r["status"])
        self.assertNotIn(r["status"], RESOLVED_FP_STATUSES)

    def test_duties_only_system_stays_gray(self):
        # allowedSkills 에 없는 일반 시스템/도구 표현은 접미 제거해도 unresolved(자동 fp 금지)
        self.assertEqual("unresolved", status(
            "사내 안전관리 시스템 운영", ["산업안전 관리", "위험성 평가", "안전보건 법규"]))


if __name__ == "__main__":
    unittest.main(verbosity=2)
