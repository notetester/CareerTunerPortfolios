"""NCS 근거 RAG PoC 불변식 테스트 (stage R4).

실행: python rag_poc/tests/test_ncs_rag.py  (stdlib unittest, 네트워크·모델 불필요)
핵심 불변식:
  - NCS chunk 은 전부 global(개인정보 scope 격리, fail-closed 불변).
  - NCS 는 evidence bucket 에서 jobRequirements 로만(userEvidence 로 절대 아님).
  - 모델이 NCS 요구 역량을 '보유'로 서술하면 gate(requirement_as_owned)가 잡는다.
  - NCS 요구/로드맵은 전부 '요구/학습' 라벨 — 보유 주장이 아니다.
  - 직무→능력단위 매핑은 결정론(무관 직무는 매핑 없음 = false positive 억제).
"""
import json
import os
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "scripts"))
sys.path.insert(0, os.path.join(HERE, "..", "..", "scripts"))

import build_ncs_chunks as ncs_chunks  # noqa: E402
import ncs_skill_taxonomy as tax  # noqa: E402
from offline_retriever import retrieve  # noqa: E402
from build_rag_evidence_buckets import BUCKET_MAP  # noqa: E402
from compare_lora_with_evidence_gated_rag import evidence_audit  # noqa: E402
from run_ncs_rag_poc import demo_case  # noqa: E402

UNITS = ncs_chunks.unit_records()
CHUNKS = ncs_chunks.build_chunks()


class NcsScopeTest(unittest.TestCase):
    def test_1_all_ncs_chunks_global(self):
        self.assertTrue(CHUNKS)
        self.assertTrue(all(c["visibility"] == "global" for c in CHUNKS))

    def test_2_ncs_retrievable_without_user_id(self):
        r = retrieve(CHUNKS, "데이터 아키텍처 설계 데이터 모델", top_k=3)
        self.assertTrue(r, "global NCS 는 user_id 없이도 검색돼야 함")

    def test_3_user_private_still_fail_closed(self):
        priv = [{"chunkId": "p1", "sourceType": "user_profile_summary",
                 "visibility": "user_private", "userId": "u1", "text": "데이터 아키텍처"}]
        self.assertEqual(retrieve(priv, "데이터 아키텍처"), [])  # user_id 없음 → 0


class NcsBucketTest(unittest.TestCase):
    def test_4_ncs_maps_to_job_requirements(self):
        self.assertEqual(BUCKET_MAP.get("ncs_unit"), "jobRequirements")
        self.assertEqual(BUCKET_MAP.get("ncs_element"), "jobRequirements")

    def test_5_ncs_never_user_evidence(self):
        for st, bucket in BUCKET_MAP.items():
            if st.startswith("ncs_"):
                self.assertNotEqual(bucket, "userEvidence", f"{st} 가 userEvidence 로 매핑됨")


class NcsTaxonomyTest(unittest.TestCase):
    def test_6_relevant_job_maps_to_units(self):
        req = tax.required_units_for("빅데이터 수집 정제 통계 분석 시각화 모델링", UNITS, top_k=3)
        self.assertTrue(req)
        self.assertTrue(any("빅데이터" in u["subName"] for u in req))

    def test_7_unrelated_job_no_false_positive(self):
        # NCS fixture(IT 데이터 계열)와 무관한 직무 → 매핑 없음(정밀도)
        req = tax.required_units_for("바리스타 채용. 에스프레소 추출과 라떼아트, 고객 응대.", UNITS, top_k=5)
        self.assertEqual(req, [])

    def test_8_required_units_deterministic(self):
        job = "데이터 아키텍처 요구사항 분석 데이터 모델 검증"
        self.assertEqual(tax.required_units_for(job, UNITS), tax.required_units_for(job, UNITS))

    def test_9_roadmap_items_are_required_not_owned(self):
        req = tax.required_units_for("데이터 모델 검증 데이터 표준", UNITS, top_k=3)
        for item in tax.roadmap_from_units(req):
            self.assertEqual(item["status"], "REQUIRED_STANDARD")
            self.assertNotIn("보유", item["label"])


class NcsGateTest(unittest.TestCase):
    def setUp(self):
        _, self.required, self.buckets, self.case, self.req_names = demo_case(UNITS)

    def test_10_gate_catches_ncs_conflation(self):
        out = json.dumps({"fitSummary": f"지원자는 {self.req_names[0]} 능력을 보유하고 있습니다."},
                         ensure_ascii=False)
        audit = evidence_audit(out, self.case, self.buckets)
        self.assertGreaterEqual(audit["requirement_as_owned_count"], 1)
        self.assertGreaterEqual(audit["evidence_gate_violation_count"], 1)

    def test_11_safe_output_passes(self):
        out = json.dumps({"fitSummary": f"지원자는 Python 경험이 있습니다. "
                                        f"{self.req_names[0]}은(는) 학습이 필요합니다."}, ensure_ascii=False)
        audit = evidence_audit(out, self.case, self.buckets)
        self.assertEqual(audit["evidence_gate_violation_count"], 0)

    def test_12_ncs_in_job_requirements_bucket(self):
        jr = " ".join(i["text"] for i in self.buckets["jobRequirements"])
        self.assertIn("NCS", jr)
        self.assertEqual(self.buckets["userEvidence"] and self.buckets["userEvidence"][0]["sourceType"],
                         "user_profile_summary")


if __name__ == "__main__":
    unittest.main(verbosity=2)
