"""NCS 근거 RAG PoC 드라이버 (stage R4) — 오프라인·결정론, GPU/모델 불필요.

증명하려는 것(R2 시리즈 결론 위에서):
  1. NCS 능력단위 = global 검색 대상(개인정보 scope 와 격리, fail-closed 불변).
  2. NCS 는 evidence bucket 에서 **jobRequirements** 로만 들어간다(userEvidence 아님).
  3. 모델이 NCS 요구 역량을 '보유'로 서술하면(conflation) 결정론 gate(requirement_as_owned)가 잡는다.
  4. NCS 의 안전한 값 = 결정론 계층(직무→요구 능력단위, 학습 로드맵). 생성 주입에 의존하지 않는다.

실제 3B 생성 A/B(주입 유무)는 GPU(campaign 종료 후)에서 compare_lora_with_evidence_gated_rag 로.
여기서는 conflation 출력을 합성해 gate 동작을 관통 검증한다. 개인정보 없음.
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "..", "..", "scripts"))

import build_ncs_chunks as ncs_chunks
import ncs_skill_taxonomy as tax
import offline_retriever as retr
from build_rag_evidence_buckets import BUCKET_MAP
from compare_lora_with_evidence_gated_rag import evidence_audit


def build_buckets(user_evidence_text, required_units):
    """NCS 요구 능력단위 → jobRequirements 버킷. 사용자 근거는 userEvidence."""
    return {
        "userEvidence": [{"sourceType": "user_profile_summary", "sourceId": "profile",
                          "text": user_evidence_text}],
        "jobRequirements": [{"sourceType": "ncs_unit", "sourceId": u.get("unitName"), "text": t}
                            for u, t in zip(required_units, tax.as_job_requirement_texts(required_units))],
        "catalogFacts": [],
        "companyContext": [],
    }


def demo_case(units):
    job = ("데이터 아키텍트 채용. 전사 데이터 표준·모델 설계, 데이터 아키텍처 요구사항 분석과 "
           "데이터 모델 검증 역량을 요구합니다. 대용량 처리 경험 우대.")
    required = tax.required_units_for(job, units, top_k=3)
    user_profile = "지원자는 Python 과 SQL 로 데이터 처리 스크립트를 작성한 경험이 있다."
    buckets = build_buckets(user_profile, required)
    # 스킬 universe: NCS 요구 능력단위명을 requiredSkills 로(=직무 표준 요구)
    req_skill_names = [u["unitName"] for u in required]
    case = {
        "input": {
            "profileSkills": ["Python", "SQL"],
            "matchedSkills": ["Python", "SQL"],
            "requiredSkills": req_skill_names,
            "missingRequiredSkills": req_skill_names,
        },
        "expected": {"allowedSkills": ["Python", "SQL"] + req_skill_names},
    }
    return job, required, buckets, case, req_skill_names


def main():
    units = ncs_chunks.unit_records()
    assert BUCKET_MAP.get("ncs_unit") == "jobRequirements", "NCS 는 jobRequirements 버킷이어야 함"

    # scope 안전: NCS chunk 는 global → user_id 없이 검색됨
    chunks = ncs_chunks.build_chunks()
    assert all(c["visibility"] == "global" for c in chunks), "NCS chunk 은 전부 global"

    job, required, buckets, case, req_names = demo_case(units)

    # 두 가지 합성 모델 출력: (A) conflation, (B) 안전(요구/학습으로 표현)
    conflation = json.dumps({
        "fitSummary": f"지원자는 {req_names[0]} 능력과 {req_names[1]} 역량을 보유하고 있습니다.",
        "strengths": [f"{req_names[0]}에 능숙합니다."],
    }, ensure_ascii=False)
    safe = json.dumps({
        "fitSummary": f"지원자는 Python·SQL 경험이 있습니다. {req_names[0]}은(는) 직무가 요구하는 역량으로 학습이 필요합니다.",
        "strengths": ["Python 데이터 처리 경험"],
    }, ensure_ascii=False)

    audit_conf = evidence_audit(conflation, case, buckets)
    audit_safe = evidence_audit(safe, case, buckets)

    result = {
        "job_to_required_ncs_units": [
            {"subName": u["subName"], "unitName": u["unitName"], "level": u["level"],
             "relevance": u["relevance"]} for u in required],
        "learning_roadmap": tax.roadmap_from_units(required),
        "gate_on_conflation_output": audit_conf,
        "gate_on_safe_output": audit_safe,
        "assertions": {
            "ncs_is_jobRequirements_not_userEvidence": True,
            "ncs_chunks_all_global": True,
            "gate_catches_ncs_conflation": audit_conf["requirement_as_owned_count"] >= 1,
            "safe_output_passes_gate": audit_safe["evidence_gate_violation_count"] == 0,
        },
    }
    print(json.dumps(result, ensure_ascii=False, indent=2))
    # 핵심 불변식 하드 체크
    assert result["assertions"]["gate_catches_ncs_conflation"], "gate 가 NCS conflation 을 못 잡음"
    assert result["assertions"]["safe_output_passes_gate"], "안전 출력이 잘못 걸림"
    print("\nOK: NCS→jobRequirements + gate 가 conflation 차단, 안전출력 통과.")


if __name__ == "__main__":
    main()
