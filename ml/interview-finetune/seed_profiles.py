"""
가상 면접 시드 프로필 생성기 (조합 샘플러).

generate_synthetic.py 의 입력. 각 시드는 "씨앗"만 담는다(회사명/업종/직무/경력/모드).
풍부한 company_analysis/job_analysis 는 generate_synthetic 의 stage0 에서 Haiku 가 생성한다(옵션 A).

사용:
    python seed_profiles.py --n 300 --out seeds.json
"""
import argparse
import json
import random
from collections import Counter

# 직군 -> 대표 직무 + 스킬 힌트(Haiku 분석 생성 가이드용)
JOB_FAMILIES = {
    "개발": {
        "titles": ["백엔드 개발자", "프론트엔드 개발자", "풀스택 개발자", "안드로이드 개발자",
                   "데이터 엔지니어", "DevOps 엔지니어", "AI 엔지니어", "QA 엔지니어"],
        "skills": ["Java", "Spring", "Python", "React", "TypeScript", "MySQL", "Redis",
                   "Kafka", "AWS", "Docker", "Kubernetes", "Node.js"],
    },
    "마케팅": {
        "titles": ["퍼포먼스 마케터", "콘텐츠 마케터", "브랜드 마케터", "그로스 마케터"],
        "skills": ["GA4", "메타광고", "SQL", "퍼널분석", "CRM", "A/B테스트", "SEO", "콘텐츠기획"],
    },
    "생산관리": {
        "titles": ["생산관리 담당", "품질관리(QC)", "공정 엔지니어", "설비 엔지니어"],
        "skills": ["공정관리", "품질시스템", "SPC", "GMP", "ERP", "6시그마", "설비보전"],
    },
    "공공": {
        "titles": ["행정직", "정책연구원", "사업운영 담당"],
        "skills": ["정책분석", "보고서작성", "예산관리", "이해관계자 조율", "공공데이터"],
    },
    "서비스운영": {
        "titles": ["CS 운영 매니저", "서비스 기획자", "물류 운영 담당", "커뮤니티 매니저"],
        "skills": ["CS운영", "서비스기획", "데이터분석", "SLA관리", "프로세스개선", "VOC분석"],
    },
    "영업": {
        "titles": ["B2B 영업", "해외영업", "기술영업", "영업관리"],
        "skills": ["B2B영업", "계약협상", "고객관리", "시장분석", "제안서작성", "해외영업"],
    },
}

INDUSTRIES = ["IT/SaaS", "핀테크", "이커머스", "제조", "바이오/제약", "게임", "물류", "금융", "공공", "미디어"]
COMPANY_TYPES = ["스타트업", "중견기업", "대기업", "공공기관", "외국계"]
SENIORITY = ["신입", "주니어(1~3년)", "시니어(5년+)"]
MODES = ["BASIC", "JOB", "PERSONALITY", "PRESSURE", "RESUME", "COMPANY"]

# 회사명 조합 (가상)
KO_PREFIX = ["넥스트", "그린", "스마트", "블루", "하이퍼", "코어", "링크", "노바", "오픈", "페어", "제트", "루미", "온", "해치"]
KO_SUFFIX = ["페이", "랩스", "소프트", "테크", "바이오", "웍스", "클라우드", "모빌리티", "커머스", "데이터"]
EN_PREFIX = ["Next", "Green", "Smart", "Blue", "Hyper", "Core", "Link", "Nova", "Open", "Lumi"]
EN_SUFFIX = ["Pay", "Labs", "Soft", "Tech", "Bio", "Works", "Cloud", "Mobility", "Commerce", "Data"]


def _company_name(rng, lang):
    if lang == "en":
        return rng.choice(EN_PREFIX) + rng.choice(EN_SUFFIX)
    return rng.choice(KO_PREFIX) + rng.choice(KO_SUFFIX)


def make_seeds(n, seed=42, en_ratio=0.15):
    """직군·모드 균형 + 중복 회사명 회피로 시드 n개 생성."""
    rng = random.Random(seed)
    families = list(JOB_FAMILIES.keys())
    seeds, used = [], set()
    guard = 0
    while len(seeds) < n and guard < n * 50:
        guard += 1
        family = families[len(seeds) % len(families)]   # 직군 균형
        fam = JOB_FAMILIES[family]
        lang = "en" if rng.random() < en_ratio else "ko"
        name = _company_name(rng, lang)
        title = rng.choice(fam["titles"])
        key = (name, title)
        if key in used:
            continue
        used.add(key)
        hints = rng.sample(fam["skills"], k=min(4, len(fam["skills"])))
        seeds.append({
            "id": f"seed_{len(seeds) + 1:04d}",
            "company_name": name,
            "industry": rng.choice(INDUSTRIES),
            "company_type": rng.choice(COMPANY_TYPES),
            "job_title": title,
            "job_family": family,
            "seniority": rng.choice(SENIORITY),
            "mode": MODES[len(seeds) % len(MODES)],   # 모드 균형
            "lang": lang,
            "skill_hints": hints,
        })
    return seeds


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--n", type=int, default=300)
    ap.add_argument("--out", default="seeds.json")
    ap.add_argument("--seed", type=int, default=42)
    ap.add_argument("--en-ratio", type=float, default=0.15)
    args = ap.parse_args()

    seeds = make_seeds(args.n, args.seed, args.en_ratio)
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(seeds, f, ensure_ascii=False, indent=2)

    print(f"{len(seeds)}개 생성 -> {args.out}")
    print("직군:", dict(Counter(s["job_family"] for s in seeds)))
    print("모드:", dict(Counter(s["mode"] for s in seeds)))
    print("언어:", dict(Counter(s["lang"] for s in seeds)))


if __name__ == "__main__":
    main()
