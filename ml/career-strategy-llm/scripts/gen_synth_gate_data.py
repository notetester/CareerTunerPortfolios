"""공유 dev DB gate 통계 실규모 검증용 합성 데이터 생성(GPU 불필요).
식별가능한 ID 범위(8_000_000+)에 users→application_case→fit_analysis→gate_result 체인을
현실적 분포로 대량 생성. 별도 cleanup.sql 로 언제든 제거 가능."""
import io, json, random

random.seed(20260707)
BASE = 8_000_000
N_USERS = 500
N = 5000  # cases/analyses/gate results

SKILLS = ["Java","Spring Boot","React","TypeScript","Kafka","Spark","AWS","Kubernetes","Python",
          "MySQL","Redis","GraphQL","Node.js","Docker","Terraform","Kotlin","Go","GA4","정보처리기사",
          "SQLD","리눅스마스터","AWS SAA","Figma","Airflow","PyTorch","Elasticsearch"]
COMPANIES = ["카카오","네이버","토스","라인","쿠팡","배달의민족","당근","야놀자","무신사","크래프톤",
             "엔씨소프트","넥슨","현대오토에버","삼성SDS","LG CNS","SK텔레콤","KT","우아한형제들"]
JOBS = ["백엔드 개발자","프론트엔드 개발자","데이터 엔지니어","서버 개발자","ML 엔지니어","DevOps 엔지니어",
        "안드로이드 개발자","풀스택 개발자","플랫폼 개발자","데이터 분석가"]
RTYPE = ["requirement_as_owned","matched_skill_without_user_evidence"]

def reasons_for(status):
    if status == "PASSED":
        return [], None, 0
    k = random.choice([1,1,2,2,3])
    rs = []
    has_crit = False
    for _ in range(k):
        sev = random.choices(["warning","critical"], weights=[7,3])[0]
        has_crit = has_crit or sev == "critical"
        rs.append({"type": random.choice(RTYPE), "claim": random.choice(SKILLS),
                   "reason": "사용자 원본 근거 없이 보유로 단정" if sev=="critical" else "우대 역량을 보유로 서술",
                   "severity": sev})
    return rs, ("critical" if has_crit else "warning"), len(rs)

def esc(s): return s.replace("'", "''")

def batched_insert(f, table, cols, rows, per=500):
    for i in range(0, len(rows), per):
        chunk = rows[i:i+per]
        f.write(f"INSERT INTO {table} ({cols}) VALUES\n")
        f.write(",\n".join(chunk))
        f.write(";\n")

out = io.open("gate_synth.sql","w",encoding="utf-8",newline="\n")
out.write("-- 합성 gate 통계 검증 데이터(ID>=8000000, cleanup 가능). 자동 생성.\n")
out.write("SET FOREIGN_KEY_CHECKS=1;\n")

users = [f"({BASE+u}, 'synthgate+{u}@careertuner.dev', '합성회원{u}')" for u in range(N_USERS)]
batched_insert(out, "users", "id, email, name", users)

cases, analyses, gates = [], [], []
status_choices = random.choices(["PASSED","REVIEW_REQUIRED","REJECTED"], weights=[55,40,5], k=N)
for i in range(N):
    cid = BASE + i; uid = BASE + (i % N_USERS)
    comp = random.choice(COMPANIES); job = random.choice(JOBS)
    cases.append(f"({cid}, {uid}, '{esc(comp)}', '{esc(job)}', 'APPLIED', 0)")
    score = random.randint(30, 95)
    analyses.append(f"({cid}, {cid}, {score}, 'SUCCESS')")
    st = status_choices[i]
    rs, maxsev, cnt = reasons_for(st)
    needs = 0 if st == "PASSED" else 1
    # review_status 분포
    if st == "PASSED":
        rv = "PENDING"
    else:
        rv = random.choices(["PENDING","RESOLVED","REANALYSIS_REQUESTED"], weights=[70,20,10])[0]
    # 2% 깨진 JSON(방어 파싱 실규모 검증)
    if st != "PASSED" and random.random() < 0.02:
        rj = "'{broken-json'"
    elif st == "PASSED":
        rj = "'[]'"
    else:
        rj = "'" + esc(json.dumps(rs, ensure_ascii=False)) + "'"
    sev = "NULL" if maxsev is None else f"'{maxsev}'"
    gates.append(f"({cid}, {cid}, '{st}', {needs}, {cnt}, {sev}, {rj}, 'r3-review-first', '{rv}')")

batched_insert(out, "application_case", "id, user_id, company_name, job_title, status, is_favorite", cases)
batched_insert(out, "fit_analysis", "id, application_case_id, fit_score, status", analyses)
batched_insert(out, "fit_analysis_gate_result",
               "id, fit_analysis_id, gate_status, needs_human_review, reason_count, max_severity, gate_reasons_json, evidence_gate_version, review_status", gates)
out.close()

cu = io.open("gate_synth_cleanup.sql","w",encoding="utf-8",newline="\n")
cu.write("-- 합성 데이터 제거(FK 순서). \n")
cu.write("DELETE FROM fit_analysis_gate_result WHERE id >= 8000000;\n")
cu.write("DELETE FROM fit_analysis WHERE id >= 8000000;\n")
cu.write("DELETE FROM application_case WHERE id >= 8000000;\n")
cu.write("DELETE FROM users WHERE id >= 8000000;\n")
cu.close()

# 기대 분포 요약(검증 대조군)
from collections import Counter
gs = Counter(status_choices)
print("expected gate_status:", dict(gs))
print("files: gate_synth.sql, gate_synth_cleanup.sql")
