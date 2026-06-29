"""RAG A/B 평가 케이스 빌더 — stage R2.

synthetic 케이스(실제 개인정보 없음). 각 케이스는 base input + retrievedContext(부가 근거)를 갖고,
build_pairs 가 변형 A(lora_only, ctx 없음)/B(lora_with_retrieved_context, ctx 있음)를 **같은 caseId**로,
**retrievedContext 유무만 다르게** 만든다. retrievedContext 텍스트는 R1/R1b fixture 와 정합한 synthetic.
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from rag_prompt_builder import build_rag_input, build_messages  # noqa: E402

# retrievedContext 조각(synthetic, fixture 정합)
CTX = {
    "sqld": {"sourceType": "certification_catalog", "sourceId": "cert-sqld",
             "text": "SQLD는 SQL 기본 이해와 데이터 모델링 역량을 검증하는 국가공인 자격입니다."},
    "info": {"sourceType": "certification_catalog", "sourceId": "cert-info",
             "text": "정보처리기사는 소프트웨어 개발 전반의 역량을 검증하는 국가기술자격입니다."},
    "springboot": {"sourceType": "skill_catalog", "sourceId": "skill-springboot",
                   "text": "Spring Boot는 Java 기반 백엔드 프레임워크로 REST API 개발과 의존성 관리에 쓰입니다."},
    "spark": {"sourceType": "skill_catalog", "sourceId": "skill-spark",
              "text": "Apache Spark는 대규모 분산 데이터 처리와 데이터 파이프라인에 쓰이는 엔진입니다."},
    "research": {"sourceType": "company_research_summary", "sourceId": "research-001",
                 "text": "회사 A는 이커머스 주문 결제 도메인을 운영하며 MSA 전환 중이고 코드리뷰와 기술 의사결정 문화를 강조합니다."},
}


def _case(case_id, *, job_title, allowed, matched, missing, fit, decision, ctx_keys, hint):
    return {
        "caseId": case_id,
        "input": {
            "profileSnapshot": {"matchedSkills": matched, "missingSkills": missing},
            "jobPostingSummary": {"jobTitle": job_title, "requiredSkills": allowed},
            "fitScore": fit, "applyDecision": decision,
            "matchedSkills": matched, "missingSkills": missing,
        },
        "expected": {"allowedSkills": allowed},
        "retrievedContext": [CTX[k] for k in ctx_keys],
        "ragHint": hint,
    }


def build_cases():
    return [
        _case("rag-sqld-001", job_title="백엔드 개발자", allowed=["Java", "Spring Boot", "SQL", "JPA"],
              matched=["SQL", "JPA"], missing=["Java", "Spring Boot"], fit=72, decision="COMPLEMENT_BEFORE_APPLY",
              ctx_keys=["sqld", "springboot"], hint="SQL/SQLD grounding — MSSQL 같은 미입력 제품 날조 억제 기대"),
        _case("rag-spring-002", job_title="이커머스 백엔드", allowed=["Java", "Spring Boot", "REST API", "SQL"],
              matched=["SQL"], missing=["Java", "Spring Boot", "REST API"], fit=68, decision="COMPLEMENT_BEFORE_APPLY",
              ctx_keys=["springboot"], hint="Spring Boot/REST API skill catalog 근거"),
        _case("rag-spark-003", job_title="데이터 엔지니어", allowed=["Python", "Spark", "SQL"],
              matched=["Python", "SQL"], missing=["Spark"], fit=70, decision="COMPLEMENT_BEFORE_APPLY",
              ctx_keys=["spark"], hint="Spark/데이터 파이프라인 근거"),
        _case("rag-cert-004", job_title="데이터 분석가", allowed=["SQL", "데이터 모델링"],
              matched=["SQL"], missing=["데이터 모델링"], fit=66, decision="COMPLEMENT_BEFORE_APPLY",
              ctx_keys=["sqld"], hint="자격증 추천(SQLD) 근거"),
        _case("rag-research-005", job_title="주문 결제 백엔드", allowed=["Java", "Spring Boot", "MSA"],
              matched=["Java"], missing=["Spring Boot", "MSA"], fit=64, decision="HOLD",
              ctx_keys=["research", "springboot"], hint="회사/직무 조사 context 활용"),
        _case("rag-multi-006", job_title="풀스택", allowed=["Java", "Spring Boot", "SQL", "REST API"],
              matched=["SQL"], missing=["Java", "Spring Boot", "REST API"], fit=60, decision="HOLD",
              ctx_keys=["springboot", "info"], hint="복수 catalog 근거"),
        _case("rag-apply-007", job_title="백엔드(주니어)", allowed=["Java", "Spring Boot", "SQL", "JPA"],
              matched=["Java", "Spring Boot", "SQL", "JPA"], missing=[], fit=84, decision="APPLY",
              ctx_keys=["springboot"], hint="APPLY-with-context: 점수/판단 불변 확인용"),
        _case("rag-negctrl-008", job_title="데이터 엔지니어", allowed=["Python", "Spark"],
              matched=["Python"], missing=["Spark"], fit=62, decision="HOLD",
              ctx_keys=[], hint="negative control — retrievedContext 비어있음(B==A 효과, builder 견고성)"),
    ]


def build_pairs(cases):
    """각 케이스 → [A(lora_only, ctx 없음), B(lora_with_retrieved_context, ctx 있음)] (같은 caseId)."""
    pairs = []
    for c in cases:
        a_input = build_rag_input(c["input"], retrieved_context=None)
        b_input = build_rag_input(c["input"], retrieved_context=c["retrievedContext"])
        pairs.append({
            "caseId": c["caseId"], "expected": c["expected"], "ragHint": c["ragHint"],
            "variants": {
                "lora_only": {"input": a_input,
                              "messages": build_messages(c["input"], with_context=False)},
                "lora_with_retrieved_context": {"input": b_input,
                              "messages": build_messages(c["input"], c["retrievedContext"], with_context=True)},
            },
        })
    return pairs


def main():
    cases = build_cases()
    pairs = build_pairs(cases)
    print(f"cases={len(cases)} pairs={len(pairs)} (각 pair=A/B 2변형)")
    for p in pairs:
        a, b = p["variants"]["lora_only"]["input"], p["variants"]["lora_with_retrieved_context"]["input"]
        print(f"  {p['caseId']}: A.ctx={'retrievedContext' in a} B.ctx={len(b.get('retrievedContext', []))} | {p['ragHint'][:40]}")


if __name__ == "__main__":
    main()
