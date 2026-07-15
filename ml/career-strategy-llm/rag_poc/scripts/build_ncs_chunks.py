"""NCS 세분류 fixture → 검색용 chunk (stage R4, NCS 근거 RAG).

NCS(국가직무능력표준)는 **직무가 요구하는** 능력단위·기술의 권위 있는 분류다. 지원자가 보유한 것이 아니다.
따라서 chunk 은 전부 **visibility=global**(공개 레퍼런스) + sourceType=`ncs_unit` 로 만들고, evidence bucket 은
`jobRequirements`(요구사항)로 매핑한다(build_rag_evidence_buckets 참조). userEvidence 로 들어가면 안 된다 —
그러면 R2 시리즈에서 확인한 conflation(모델이 요구사항을 보유로 착각)을 NCS 가 오히려 증폭시킨다.

fixture(ncs_fixture.jsonl) 한 줄 = 세분류 {ncsCode, majorName, subName, units:[{unitName, level, skills[]}]}.
능력단위 하나가 chunk 하나. **synthetic/공개 데이터 전용 — 개인정보 없음.**
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_FIXTURE = os.path.join(HERE, "..", "fixtures", "ncs_fixture.jsonl")

# 대분류명 → 기존 fixture 의 domainGroup 규약에 맞춘 그룹(대략). 미지정은 GENERAL.
DOMAIN_GROUP = {
    "정보통신": "IT_SOFTWARE",
    "경영·회계·사무": "BUSINESS",
    "전기·전자": "ELECTRONICS",
}


def load_ncs_fixture(path=DEFAULT_FIXTURE):
    return [json.loads(l) for l in open(path, encoding="utf-8") if l.strip()]


def unit_records(fixture=None):
    """세분류 fixture → 능력단위 평면 목록 {subName, ncsCode, unitName, level, skills, domainGroup}."""
    fixture = fixture if fixture is not None else load_ncs_fixture()
    out = []
    for sub in fixture:
        group = DOMAIN_GROUP.get(sub.get("majorName", ""), "GENERAL")
        for u in sub.get("units", []):
            out.append({
                "subName": sub["subName"],
                "ncsCode": sub["ncsCode"],
                "unitName": u.get("unitName") or "",
                "level": u.get("level"),
                "skills": [s for s in (u.get("skills") or []) if s],
                "domainGroup": group,
            })
    return out


def build_chunks(fixture=None):
    """능력단위 → 검색 chunk(global). text 는 '이 직무가 요구하는' 프레이밍으로 고정."""
    chunks = []
    for i, u in enumerate(unit_records(fixture)):
        skills = ", ".join(u["skills"]) if u["skills"] else "(세부 기술 미수록)"
        text = (f"{u['subName']} 직무의 NCS 능력단위 '{u['unitName']}'"
                f"(직무수준 {u['level']}) 이 능력단위가 요구하는 기술: {skills}.")
        chunks.append({
            "chunkId": f"ncs-{u['ncsCode']}-u{i:02d}",
            "sourceType": "ncs_unit",
            "sourceId": f"{u['subName']}::{u['unitName']}",
            "visibility": "global",
            "domainGroup": u["domainGroup"],
            "ncsSubName": u["subName"],
            "level": u["level"],
            "skills": u["skills"],
            "text": text,
        })
    return chunks


def main():
    import sys
    chunks = build_chunks()
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, "..", "fixtures", "ncs_chunks.jsonl")
    with open(out, "w", encoding="utf-8", newline="") as f:
        for c in chunks:
            f.write(json.dumps(c, ensure_ascii=False) + "\n")
    print(f"wrote {len(chunks)} NCS chunks (all visibility=global) -> {out}")


if __name__ == "__main__":
    main()
