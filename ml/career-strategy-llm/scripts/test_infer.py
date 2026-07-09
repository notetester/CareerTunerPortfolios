"""학습한 C 모델 생성 sanity 테스트 — C_FIT_EXPLAIN (IT/비IT 4건 + 자동검사).
(D의 ml/interview-finetune/test_infer.py 패턴을 C 도메인으로 확장)

어댑터(out/career-strategy-lora-3b) 또는 merged(out/career-strategy-merged-3b) 둘 다 테스트 가능.
cwd 와 무관하게 동작한다(경로는 이 스크립트 위치 기준으로 해석, import 는 sys.path 보강).

사용:
    python test_infer.py --help
    python test_infer.py                                   # 기본: ../out/career-strategy-lora-3b (어댑터)
    python test_infer.py --model ../out/career-strategy-merged-3b --merged
    python test_infer.py --model ../out/career-strategy-lora-3b --max-new 512

성공(합격) 기준(각 샘플):
  - JSON parse 가능 / 필수 키 존재(fitSummary,strengths,risks,strategyActions,learningTaskReasons)
  - 금지 키 없음(fitScore,score,applyDecision,decision)
  - 중국어/일본어 토큰 누출 없음
  - 입력에 없는 회사/역량/자격증 생성 없음, 점수/판단 변경 없음(육안 확인)
"""
import argparse
import json
import os
import re
import sys

# import 와 기본 모델 경로를 cwd 와 무관하게 만든다(런북은 scripts/ 에서 실행).
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, SCRIPT_DIR)
DEFAULT_MODEL = os.path.normpath(os.path.join(SCRIPT_DIR, "..", "out", "career-strategy-lora-3b"))

# torch/transformers/peft 는 무겁고 4090에서만 설치되므로 함수 안에서 import 한다.
# (→ `python test_infer.py --help` 는 학습 deps 설치 전에도 동작)
from assemble_dataset import build_fit_user  # noqa: E402
from synth_prompts import FIT_EXPLAIN_SYS  # noqa: E402

REQUIRED_KEYS = ["fitSummary", "strengths", "risks", "strategyActions", "learningTaskReasons"]
FORBIDDEN_KEYS = {"fitscore", "score", "applydecision", "decision", "fit_score", "apply_decision"}
CJ_ONLY = re.compile(r"[一-鿿぀-ヿㇰ-ㇿ]")  # 중국어/일본어 토큰 누출 검사(한글 제외)

# IT 2건 + 비IT 2건 (규칙엔진 사전계산값 포함). APPLY/COMPLEMENT/HOLD 고르게.
SAMPLES = [
    {"id": "it_complement", "domainGroup": "IT_SOFTWARE",
     "companyName": "토스", "jobTitle": "백엔드 개발자", "desiredJob": "백엔드 개발자", "experienceLevel": "신입",
     "requiredSkills": ["Java", "Spring", "MySQL", "REST API"], "preferredSkills": ["Kafka", "AWS", "Docker"],
     "duties": "결제·정산 백엔드 API 설계·개발 및 운영, 성능 개선",
     "profileSkills": ["Java", "Spring Boot", "MySQL", "Git"], "profileCertificates": ["정보처리기사"],
     "matchedSkills": ["Java", "Spring", "MySQL"], "missingRequiredSkills": ["REST API"],
     "missingPreferredSkills": ["Kafka", "AWS", "Docker"], "fitScore": 72, "applyDecision": "COMPLEMENT_BEFORE_APPLY"},
    {"id": "it_hold", "domainGroup": "DATA_AI",
     "companyName": "노바데이터", "jobTitle": "데이터 엔지니어", "desiredJob": "데이터 엔지니어", "experienceLevel": "주니어(1~3년)",
     "requiredSkills": ["Airflow", "SQL", "Spark", "dbt"], "preferredSkills": ["Python", "BigQuery"],
     "duties": "데이터 파이프라인 구축, 지표·대시보드 작성",
     "profileSkills": ["Pandas", "통계", "데이터시각화"], "profileCertificates": ["ADsP"],
     "matchedSkills": [], "missingRequiredSkills": ["Airflow", "SQL", "Spark", "dbt"],
     "missingPreferredSkills": ["Python", "BigQuery"], "fitScore": 9, "applyDecision": "HOLD"},
    {"id": "nonit_apply", "domainGroup": "MARKETING",
     "companyName": "그린커머스", "jobTitle": "퍼포먼스 마케터", "desiredJob": "퍼포먼스 마케터", "experienceLevel": "미들(4~7년)",
     "requiredSkills": ["GA4", "퍼포먼스 광고 운영", "메타광고"], "preferredSkills": ["구글애즈", "SQL"],
     "duties": "디지털 광고 운영, 콘텐츠 기획, 퍼널·성과 분석 및 개선",
     "profileSkills": ["GA4", "퍼포먼스 광고 운영", "메타광고", "구글애즈", "카피라이팅"],
     "profileCertificates": ["검색광고마케터 1급"],
     "matchedSkills": ["GA4", "퍼포먼스 광고 운영", "메타광고", "구글애즈"], "missingRequiredSkills": [],
     "missingPreferredSkills": ["SQL"], "fitScore": 88, "applyDecision": "APPLY"},
    {"id": "nonit_hold", "domainGroup": "DESIGN",
     "companyName": "루미랩스", "jobTitle": "UX/UI 디자이너", "desiredJob": "UX/UI 디자이너", "experienceLevel": "신입",
     "requiredSkills": ["Figma", "프로토타이핑", "와이어프레임"], "preferredSkills": ["UX 리서치", "디자인 시스템"],
     "duties": "UI/그래픽 디자인, 사용자 리서치, 브랜드·시각 자산 제작",
     "profileSkills": ["포토샵", "타이포그래피", "프로토타이핑"], "profileCertificates": ["GTQ"],
     "matchedSkills": ["프로토타이핑"], "missingRequiredSkills": ["Figma", "와이어프레임"],
     "missingPreferredSkills": ["UX 리서치", "디자인 시스템"], "fitScore": 29, "applyDecision": "HOLD"},
]


def load(model_path, merged):
    import torch
    from transformers import AutoTokenizer, AutoModelForCausalLM
    print(f"모델 로딩 중... ({'merged HF' if merged else 'base+LoRA 어댑터'}) {model_path}")
    tok = AutoTokenizer.from_pretrained(model_path)
    if merged:
        model = AutoModelForCausalLM.from_pretrained(model_path, dtype=torch.bfloat16, device_map="auto")
    else:
        from peft import AutoPeftModelForCausalLM
        model = AutoPeftModelForCausalLM.from_pretrained(model_path, dtype=torch.bfloat16, device_map="auto")
    model.eval()
    print("로딩 완료.\n")
    return tok, model


def run(tok, model, system, user, max_new):
    import torch
    msgs = [{"role": "system", "content": system}, {"role": "user", "content": user}]
    inputs = tok.apply_chat_template(msgs, add_generation_prompt=True,
                                     return_tensors="pt", return_dict=True).to(model.device)
    with torch.no_grad():
        out = model.generate(**inputs, max_new_tokens=max_new, do_sample=False,
                             pad_token_id=tok.eos_token_id)
    return tok.decode(out[0][inputs["input_ids"].shape[1]:], skip_special_tokens=True)


def extract_json(t):
    s = t.find("{")
    e = t.rfind("}")
    return t[s:e + 1] if s >= 0 and e > s else t


def check(out, seed):
    """(problems, parsed). 자동 점검(육안 항목은 별도)."""
    problems = []
    try:
        d = json.loads(extract_json(out))
    except Exception:
        return ["JSON_PARSE_FAIL"], None
    if not isinstance(d, dict):
        return ["NOT_OBJECT"], None
    for k in REQUIRED_KEYS:
        if k not in d:
            problems.append(f"missing:{k}")
    for k in d:
        if k.lower() in FORBIDDEN_KEYS:
            problems.append(f"forbidden:{k}")
    if CJ_ONLY.search(out):
        problems.append("CJK_LEAK")
    return problems, d


def main():
    ap = argparse.ArgumentParser(description="C_FIT_EXPLAIN 생성 sanity 테스트 (IT/비IT 4건 + 자동검사)")
    ap.add_argument("--model", default=DEFAULT_MODEL,
                    help="어댑터 또는 merged 모델 경로 (기본: ../out/career-strategy-lora-3b)")
    ap.add_argument("--merged", action="store_true", help="merged HF 모델이면 지정(미지정=LoRA 어댑터)")
    ap.add_argument("--max-new", type=int, default=512)
    args = ap.parse_args()

    tok, model = load(args.model, args.merged)
    passed = 0
    for s in SAMPLES:
        print("=" * 70)
        print(f"[{s['id']}] {s['domainGroup']} · {s['jobTitle']} · fitScore={s['fitScore']} · {s['applyDecision']}")
        print("=" * 70)
        out = run(tok, model, FIT_EXPLAIN_SYS, build_fit_user(s), args.max_new)
        problems, d = check(out, s)
        if d is not None:
            print(json.dumps(d, ensure_ascii=False, indent=2))
        else:
            print(out)
        verdict = "PASS" if not problems else "FAIL: " + ", ".join(problems)
        print(f"\n>>> 자동검사: {verdict}\n")
        if not problems:
            passed += 1

    print("=" * 70)
    print(f"자동검사 통과 {passed}/{len(SAMPLES)} (육안 확인 필요: 환각/모순/HOLD권장오류/IT표현누출)")


if __name__ == "__main__":
    main()
