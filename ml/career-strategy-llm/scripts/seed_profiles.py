"""
CareerTuner C(커리어 전략) 자체모델 — C_FIT_EXPLAIN 합성 학습용 시드 생성기 (범용 직군).

★ 직군 범위 정책: C 자체 LLM은 특정 직군 전용 모델이 아니다.
  공고의 필수/우대 조건과 사용자 프로필의 역량·자격·경험을 비교해 설명하는 **범용 커리어 전략 설명 모델**이다.
  Phase 1 MVP 는 IT/SW 중심으로 검증하되, 비IT 직군(마케팅·영업·디자인·회계·인사·물류·CS) 샘플을 포함해
  데이터/프롬프트가 IT 전용으로 굳지 않게 한다. 비IT 정밀 자격증/RAG/점수정책은 Phase 2 확장.

★ 뉴로-심볼릭: fitScore/applyDecision/matchedSkills/missing* 은 **여기(코드)** 에서 결정론 계산.
  LLM은 입력으로만 받아 설명을 쓰고 점수/판단을 만들지 않는다. compute_fit/norm 은 validate_dataset.py 의 오라클.
  ⚠️ 이 규칙엔진은 MVP 임시 미러링 — 백엔드 점수정책과 정합은 통합 단계에서 확정.

필드명(requiredSkills/preferredSkills/profileSkills)은 기존 백엔드 DTO 정합을 위해 유지하되,
값의 의미를 '프로그래밍 기술'에 한정하지 않고 **직무 역량/도구/자격/경험 조건**으로 확장한다.

사용:
    # IT/SW MVP(이미 생성된 baseline 재현): IT 도메인만 균형
    python seed_profiles.py --n 300 --balance --domains it   --out ../data/seeds.fit_explain.it_mvp.300.jsonl
    # 비IT 120건(직군·판단 쿼터 고정)
    python seed_profiles.py --preset nonit120                 --out ../data/seeds.fit_explain.nonit.120.jsonl
    # 비IT 10건 스모크
    python seed_profiles.py --n 10 --balance --domains nonit  --out ../data/seeds.fit_explain.nonit.smoke.jsonl
"""
import argparse
import json
import random
from collections import Counter

# ── 직군 카탈로그 (domainGroup 별 family) ──────────────────────────────────
# 각 family: {domainGroup, titles, skills(=역량/도구), duties, certs}
DOMAIN_FAMILIES = {
    # IT/SW (baseline 재현성 위해 기존 스킬 풀 유지)
    "백엔드": {"domainGroup": "IT_SOFTWARE",
              "titles": ["백엔드 개발자", "서버 개발자", "Java 백엔드 개발자"],
              "skills": ["Java", "Spring Boot", "MySQL", "Redis", "JPA", "REST API", "Kafka", "Git", "JUnit"],
              "duties": "서비스 백엔드 API 설계·개발 및 운영, 데이터 모델링, 성능 개선",
              "certs": ["정보처리기사", "SQLD", "리눅스마스터"]},
    "프론트엔드": {"domainGroup": "IT_SOFTWARE",
                "titles": ["프론트엔드 개발자", "웹 퍼블리셔 겸 FE", "React 개발자"],
                "skills": ["JavaScript", "TypeScript", "React", "Next.js", "HTML/CSS", "Redux", "Vite", "GraphQL", "Git"],
                "duties": "웹 프론트엔드 기능 설계·개발, 상태관리, API 연동, 성능·접근성 개선",
                "certs": ["정보처리기사", "웹디자인기능사", "컴퓨터활용능력"]},
    "풀스택": {"domainGroup": "IT_SOFTWARE",
              "titles": ["풀스택 개발자", "웹 개발자"],
              "skills": ["JavaScript", "TypeScript", "React", "Node.js", "Spring Boot", "MySQL", "REST API", "Docker", "Git"],
              "duties": "프론트·백엔드 기능 설계·개발 및 운영, API 연동, 배포",
              "certs": ["정보처리기사", "SQLD", "컴퓨터활용능력"]},
    "인프라": {"domainGroup": "IT_SOFTWARE",
              "titles": ["DevOps 엔지니어", "클라우드 엔지니어", "SRE"],
              "skills": ["Linux", "AWS", "Docker", "Kubernetes", "Terraform", "CI/CD", "Prometheus", "Nginx", "Bash"],
              "duties": "인프라 구축·운영, 배포 자동화, 모니터링, 비용·안정성 개선",
              "certs": ["AWS 솔루션스 아키텍트 어소시에이트", "리눅스마스터", "정보처리기사"]},
    "데이터": {"domainGroup": "DATA_AI",
              "titles": ["데이터 분석가", "데이터 엔지니어", "BI 분석가"],
              "skills": ["Python", "SQL", "Pandas", "Spark", "Airflow", "BigQuery", "통계", "데이터시각화", "dbt"],
              "duties": "데이터 수집·전처리·분석, 파이프라인 구축, 지표·대시보드 작성",
              "certs": ["ADsP", "ADP", "SQLD", "빅데이터분석기사"]},
    "AI": {"domainGroup": "DATA_AI",
           "titles": ["AI 엔지니어", "머신러닝 엔지니어", "MLOps 엔지니어"],
           "skills": ["Python", "PyTorch", "TensorFlow", "scikit-learn", "딥러닝", "MLflow", "Docker", "CUDA", "LLM"],
           "duties": "모델 학습·평가·배포, 데이터 파이프라인, 서빙·실험 관리",
           "certs": ["빅데이터분석기사", "정보처리기사", "ADsP"]},
    # 비IT
    "마케팅": {"domainGroup": "MARKETING",
              "titles": ["퍼포먼스 마케터", "콘텐츠 마케터", "브랜드 마케터", "그로스 마케터", "마케팅 AE"],
              "skills": ["콘텐츠 기획", "SNS 채널 운영", "GA4", "퍼포먼스 광고 운영", "구글애즈", "메타광고",
                         "카피라이팅", "포토샵", "SEO", "이메일 마케팅", "CRM", "데이터 분석"],
              "duties": "콘텐츠 기획·채널 운영, 광고 집행·성과 분석, 캠페인 기획",
              "certs": ["검색광고마케터 1급", "GAIQ", "사회조사분석사", "컴퓨터활용능력"]},
    "영업": {"domainGroup": "SALES",
            "titles": ["B2B 영업", "기술영업", "해외영업", "영업관리", "법인영업"],
            "skills": ["고객 발굴", "제안서 작성", "계약 협상", "고객 관계관리", "시장 조사",
                       "영업 파이프라인 관리", "견적 산출", "CRM", "프레젠테이션", "외국어 커뮤니케이션"],
            "duties": "고객 발굴·관리, 제안·견적·계약, 매출 목표 관리",
            "certs": ["무역영어", "컴퓨터활용능력", "유통관리사"]},
    "디자인": {"domainGroup": "DESIGN",
              "titles": ["UX/UI 디자이너", "그래픽 디자이너", "브랜드 디자이너", "영상 편집 디자이너", "제품 디자이너"],
              "skills": ["Figma", "포토샵", "일러스트레이터", "UX 리서치", "와이어프레임", "프로토타이핑",
                         "타이포그래피", "브랜딩", "영상편집", "디자인 시스템"],
              "duties": "UI/그래픽 디자인, 사용자 리서치, 브랜드·시각 자산 제작",
              "certs": ["GTQ", "컬러리스트기사", "웹디자인기능사", "시각디자인산업기사"]},
    "회계/재무": {"domainGroup": "FINANCE_ACCOUNTING",
                "titles": ["회계 담당", "재무 담당", "세무 담당", "자금 담당", "결산 담당"],
                "skills": ["전표 처리", "결산", "세무 신고", "자금 관리", "재무제표 작성", "원가 관리",
                           "ERP 운영", "엑셀", "예산 관리", "부가세 신고"],
                "duties": "전표·결산·세무 신고, 자금·예산 관리, 재무 보고",
                "certs": ["전산회계 1급", "전산세무 2급", "재경관리사", "컴퓨터활용능력"]},
    "인사/총무": {"domainGroup": "HR_ADMIN",
                "titles": ["인사 담당", "채용 담당", "총무 담당", "노무 담당", "HRD 담당"],
                "skills": ["채용 관리", "급여 정산", "4대보험", "근태 관리", "인사 평가", "교육 기획",
                           "노무 관리", "문서 작성", "사내 행사 운영", "ERP 운영"],
                "duties": "채용·급여·근태 관리, 인사 평가·교육, 총무 운영",
                "certs": ["공인노무사", "컴퓨터활용능력", "ERP정보관리사(인사)", "사회조사분석사"]},
    "물류/생산관리": {"domainGroup": "MANUFACTURING_LOGISTICS",
                  "titles": ["생산관리 담당", "품질관리(QC)", "자재/구매 담당", "물류 운영 담당", "공정 엔지니어"],
                  "skills": ["생산 계획", "재고 관리", "품질 검사", "공정 관리", "SCM", "ERP 운영",
                             "6시그마", "안전 관리", "입출고 관리", "데이터 분석"],
                  "duties": "생산·재고·품질 관리, 공정 개선, 입출고·물류 운영",
                  "certs": ["품질경영기사", "물류관리사", "산업안전기사", "지게차운전기능사"]},
    "고객상담/서비스": {"domainGroup": "SERVICE_CS",
                   "titles": ["고객상담 매니저", "CS 운영 담당", "VOC 분석 담당", "서비스 기획 담당", "콜센터 QA"],
                   "skills": ["고객 응대", "VOC 분석", "상담 매뉴얼 운영", "CS 지표 관리", "컴플레인 처리",
                              "CRM", "데이터 분석", "상담 품질 관리", "채팅 상담", "프로세스 개선"],
                   "duties": "고객 응대·상담, VOC 분석·개선, CS 지표·품질 관리",
                   "certs": ["CS Leaders", "컴퓨터활용능력", "텔레마케팅관리사", "사회조사분석사"]},
}

FAMILY_GROUP = {f: v["domainGroup"] for f, v in DOMAIN_FAMILIES.items()}
GROUP_FAMILIES = {}
for _f, _g in FAMILY_GROUP.items():
    GROUP_FAMILIES.setdefault(_g, []).append(_f)
IT_GROUPS = {"IT_SOFTWARE", "DATA_AI"}


def domain_group_of(family):
    return FAMILY_GROUP.get(family)


def is_it_group(group):
    return group in IT_GROUPS


INDUSTRIES = ["IT/SaaS", "핀테크", "이커머스", "제조", "바이오/제약", "게임", "물류", "금융", "공공", "미디어",
              "유통", "광고/미디어", "교육", "F&B", "패션/뷰티"]
COMPANY_TYPES = ["스타트업", "중견기업", "대기업", "공공기관", "외국계"]
EXPERIENCE = ["신입", "주니어(1~3년)", "미들(4~7년)"]
FIT_BANDS = ["HIGH", "MID", "LOW"]

KO_PREFIX = ["넥스트", "그린", "스마트", "블루", "하이퍼", "코어", "링크", "노바", "오픈", "페어", "제트", "루미", "온", "해치"]
KO_SUFFIX = ["페이", "랩스", "소프트", "테크", "바이오", "웍스", "클라우드", "모빌리티", "커머스", "데이터", "파트너스", "그룹"]
EN_PREFIX = ["Next", "Green", "Smart", "Blue", "Hyper", "Core", "Link", "Nova", "Open", "Lumi"]
EN_SUFFIX = ["Pay", "Labs", "Soft", "Tech", "Bio", "Works", "Cloud", "Mobility", "Commerce", "Data"]

# 스킬명 정규화(동의어) — IT 매칭용. 비IT 값은 그대로 소문자화만 적용된다.
ALIAS = {
    "spring": "spring boot", "springboot": "spring boot",
    "react.js": "react", "reactjs": "react",
    "node": "node.js", "nodejs": "node.js",
    "rest": "rest api", "restful api": "rest api", "restful": "rest api",
    "k8s": "kubernetes", "ts": "typescript", "js": "javascript",
}


def norm(s):
    t = s.strip().lower()
    return ALIAS.get(t, t)


def _company_name(rng, lang):
    if lang == "en":
        return rng.choice(EN_PREFIX) + rng.choice(EN_SUFFIX)
    return rng.choice(KO_PREFIX) + rng.choice(KO_SUFFIX)


def _dedup(skills):
    seen, out = set(), []
    for s in skills:
        k = norm(s)
        if k not in seen:
            seen.add(k)
            out.append(s)
    return out


def build_profile_skills(rng, required, preferred, band, pool):
    """fit 밴드로 필수 커버리지 통제 + 우대 일부 + 같은 직군 내 노이즈 스킬."""
    cover = {"HIGH": (0.85, 1.0), "MID": (0.45, 0.7), "LOW": (0.1, 0.4)}[band]
    k_req = max(0, round(len(required) * rng.uniform(*cover)))
    chosen = rng.sample(required, min(k_req, len(required)))
    pref_p = {"HIGH": 0.8, "MID": 0.5, "LOW": 0.3}[band]
    if preferred and rng.random() < pref_p:
        chosen += rng.sample(preferred, rng.randint(1, min(2, len(preferred))))
    used_norm = {norm(x) for x in required} | {norm(x) for x in preferred}
    noise_bag = [s for s in pool if norm(s) not in used_norm]   # 직군 내부 노이즈(도메인 일관)
    if noise_bag:
        chosen += rng.sample(noise_bag, min(rng.randint(1, 3), len(noise_bag)))
    return _dedup(chosen)


def compute_fit(required, preferred, profile_skills, certs, family_certs, experience):
    """★규칙엔진(임시 미러링): matched/missing/fitScore/applyDecision 결정론 계산(직군 무관)."""
    pn = {norm(s) for s in profile_skills}
    matched_req = [s for s in required if norm(s) in pn]
    matched_pref = [s for s in preferred if norm(s) in pn]
    matched = _dedup(matched_req + matched_pref)
    missing_req = [s for s in required if norm(s) not in pn]
    missing_pref = [s for s in preferred if norm(s) not in pn]

    req_ratio = len(matched_req) / len(required) if required else 0.0
    pref_ratio = len(matched_pref) / len(preferred) if preferred else 0.0
    fam_cert_norm = {norm(c) for c in family_certs}
    if {norm(c) for c in certs} & fam_cert_norm:
        cert_bonus = 6
    elif certs:
        cert_bonus = 2
    else:
        cert_bonus = 0
    exp_bonus = {"신입": 0, "주니어(1~3년)": 3, "미들(4~7년)": 5}[experience]

    score = round(70 * req_ratio + 15 * pref_ratio + cert_bonus + exp_bonus)
    score = max(0, min(100, score))
    if score >= 80:
        decision = "APPLY"
    elif score >= 60:
        decision = "COMPLEMENT_BEFORE_APPLY"
    else:
        decision = "HOLD"
    return matched, missing_req, missing_pref, score, decision


def build_one(rng, family, band, used):
    """시드 1개 생성(중복 (회사,직무) 회피). 실패 시 None."""
    fam = DOMAIN_FAMILIES[family]
    lang = "en" if rng.random() < 0.15 else "ko"
    name = _company_name(rng, lang)
    title = rng.choice(fam["titles"])
    key = (name, title)
    if key in used:
        return None
    used.add(key)

    pool = fam["skills"]
    required = rng.sample(pool, min(rng.randint(3, 5), len(pool)))
    rest = [s for s in pool if norm(s) not in {norm(x) for x in required}]
    preferred = rng.sample(rest, min(rng.randint(2, 4), len(rest))) if rest else []
    experience = rng.choice(EXPERIENCE)
    profile_skills = build_profile_skills(rng, required, preferred, band, pool)

    roll = rng.random()
    if roll < 0.60:
        certs = rng.sample(fam["certs"], min(rng.randint(1, 2), len(fam["certs"])))
    elif roll < 0.85:
        other = rng.choice([f for f in DOMAIN_FAMILIES if f != family])
        certs = rng.sample(DOMAIN_FAMILIES[other]["certs"], 1)
    else:
        certs = []

    matched, miss_req, miss_pref, score, decision = compute_fit(
        required, preferred, profile_skills, certs, fam["certs"], experience)
    desired = title if rng.random() < 0.75 else rng.choice(fam["titles"])

    return {
        "companyName": name,
        "industry": rng.choice(INDUSTRIES),
        "companyType": rng.choice(COMPANY_TYPES),
        "domainGroup": fam["domainGroup"],
        "jobTitle": title,
        "jobFamily": family,
        "desiredJob": desired,
        "experienceLevel": experience,
        "requiredSkills": required,
        "preferredSkills": preferred,
        "duties": fam["duties"],
        "profileSkills": profile_skills,
        "profileCertificates": certs,
        # ── 규칙엔진 사전계산값(서버 확정값 — 모델은 변경 금지) ──
        "matchedSkills": matched,
        "missingRequiredSkills": miss_req,
        "missingPreferredSkills": miss_pref,
        "fitScore": score,
        "applyDecision": decision,
        "fitBand": band,
        "lang": lang,
    }


def _assign_ids(seeds, prefix="cseed"):
    out = []
    for i, s in enumerate(seeds, 1):
        out.append({"id": f"{prefix}_{i:04d}", **{k: v for k, v in s.items() if k != "id"}})
    return out


def make_balanced_seeds(n, seed=42, families=None, prefix="cseed"):
    """주어진 family 집합에서 APPLY/COMPLEMENT/HOLD 를 ~동일하게(거부 샘플링)."""
    rng = random.Random(seed)
    fams = list(families) if families else list(DOMAIN_FAMILIES.keys())
    targets = {"APPLY": n // 3, "COMPLEMENT_BEFORE_APPLY": n // 3, "HOLD": n - 2 * (n // 3)}
    buckets = {k: [] for k in targets}
    used, guard, fi = set(), 0, 0
    while sum(len(v) for v in buckets.values()) < n and guard < n * 400:
        guard += 1
        family = fams[fi % len(fams)]
        fi += 1
        s = build_one(rng, family, rng.choice(FIT_BANDS), used)
        if not s:
            continue
        d = s["applyDecision"]
        if len(buckets[d]) < targets[d]:
            buckets[d].append(s)
    seeds = buckets["APPLY"] + buckets["COMPLEMENT_BEFORE_APPLY"] + buckets["HOLD"]
    rng.shuffle(seeds)
    return _assign_ids(seeds, prefix), guard


def make_quota_seeds(family_quota, decision_quota, seed=42, prefix="cseed"):
    """family 쿼터 + decision 쿼터를 동시에 만족(joint 거부 샘플링)."""
    rng = random.Random(seed)
    fams = list(family_quota.keys())
    fam_left = dict(family_quota)
    dec_left = dict(decision_quota)
    total = sum(family_quota.values())
    seeds, used, guard, fi = [], set(), 0, 0
    while len(seeds) < total and guard < total * 800:
        guard += 1
        avail = [f for f in fams if fam_left[f] > 0]
        if not avail:
            break
        family = avail[fi % len(avail)]
        fi += 1
        s = build_one(rng, family, rng.choice(FIT_BANDS), used)
        if not s:
            continue
        d = s["applyDecision"]
        if fam_left[family] > 0 and dec_left.get(d, 0) > 0:
            fam_left[family] -= 1
            dec_left[d] -= 1
            seeds.append(s)
    rng.shuffle(seeds)
    return _assign_ids(seeds, prefix), guard


# 비IT 120 프리셋 (직군·판단 쿼터 고정)
NONIT120_FAMILY_QUOTA = {"마케팅": 18, "영업": 18, "디자인": 18, "회계/재무": 18,
                         "인사/총무": 16, "물류/생산관리": 16, "고객상담/서비스": 16}
NONIT120_DECISION_QUOTA = {"APPLY": 40, "COMPLEMENT_BEFORE_APPLY": 40, "HOLD": 40}


def _families_for_domains(spec):
    if not spec:
        return None
    spec = spec.strip().lower()
    if spec == "all":
        return None
    if spec == "it":
        groups = IT_GROUPS
    elif spec == "nonit":
        groups = set(GROUP_FAMILIES) - IT_GROUPS
    else:
        groups = {g.strip() for g in spec.split(",")}
    fams = [f for f, g in FAMILY_GROUP.items() if g in groups]
    if not fams:
        raise SystemExit(f"--domains 에 해당하는 직군이 없습니다: {spec}")
    return fams


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--n", type=int, default=300)
    ap.add_argument("--out", default="../data/seeds.jsonl")
    ap.add_argument("--seed", type=int, default=42)
    ap.add_argument("--balance", action="store_true", help="APPLY/COMPLEMENT/HOLD 균형(거부 샘플링)")
    ap.add_argument("--domains", default=None, help="it | nonit | all | <DOMAINGROUP,...>")
    ap.add_argument("--preset", default=None, choices=["nonit120"], help="고정 쿼터 프리셋")
    ap.add_argument("--id-prefix", default="cseed")
    ap.add_argument("--format", choices=["jsonl", "json"], default="jsonl")
    args = ap.parse_args()

    if args.preset == "nonit120":
        seeds, guard = make_quota_seeds(NONIT120_FAMILY_QUOTA, NONIT120_DECISION_QUOTA, args.seed, args.id_prefix)
        extra = f" (preset nonit120, 시도 {guard}회)"
    elif args.balance:
        fams = _families_for_domains(args.domains)
        seeds, guard = make_balanced_seeds(args.n, args.seed, fams, args.id_prefix)
        extra = f" (balance, 시도 {guard}회)"
    else:
        fams = _families_for_domains(args.domains)
        seeds, _ = make_balanced_seeds(args.n, args.seed, fams, args.id_prefix)  # 기본도 균형
        extra = ""

    with open(args.out, "w", encoding="utf-8") as f:
        if args.format == "json":
            json.dump(seeds, f, ensure_ascii=False, indent=2)
        else:
            for s in seeds:
                f.write(json.dumps(s, ensure_ascii=False) + "\n")

    print(f"{len(seeds)}개 생성 -> {args.out}{extra}")
    print("domainGroup:", dict(Counter(s["domainGroup"] for s in seeds)))
    print("직군:", dict(Counter(s["jobFamily"] for s in seeds)))
    print("지원판단:", dict(Counter(s["applyDecision"] for s in seeds)))
    if seeds:
        sc = [s["fitScore"] for s in seeds]
        print(f"fitScore 평균/최소/최대: {round(sum(sc)/len(sc),1)} / {min(sc)} / {max(sc)}")


if __name__ == "__main__":
    main()
