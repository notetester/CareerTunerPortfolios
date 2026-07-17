"""
careertuner-mod vs 스톡 gemma4 정확도 비교 검증
- eval.jsonl (held-out 1,500건)을 두 모델에 돌려 채점
- 지표: 정확도 / abuse 재현율·정밀도 / JSON 파싱 실패율 / 혼동행렬
- Ollama API (localhost:11434) 사용. Ollama 켜져 있어야 함.

사용법:
    python evaluate.py            # 전체 1500건, 세 모델 (2시간+)
    python evaluate.py --n 300    # 300건 샘플만 (빠른 경향 확인)
    python evaluate.py --model careertuner-mod   # 한 모델만
"""
import json, os, re, sys, time, argparse, urllib.request

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
EVAL_PATH = os.path.join(BASE, "data", "processed", "eval.jsonl")
OLLAMA_URL = "http://localhost:11434/api/chat"
MODELS = ["gemma4", "careertuner-mod", "careertuner-mod-full"]  # 비교 대상

# 학습 때 쓴 시스템 프롬프트 (careertuner-mod는 Modelfile SYSTEM에 이미 있지만
# gemma4 스톡에는 없으므로, 공평하게 둘 다 요청에 명시해 동일 조건으로 맞춤)
SYSTEM_PROMPT = """너는 CareerTuner 커뮤니티의 게시글 검열기다.
입력으로 게시글의 제목과 본문이 주어진다. 아래 기준으로 분류하여 JSON으로만 응답한다.

[카테고리]
- abuse : 특정 대상(사람·집단)을 향한 욕설·인신공격·비하·혐오 표현·성희롱
- spam  : 도배, 반복 문자열, 의미 없는 내용
- ad    : 상업적 광고·홍보, 도박·대출·불법거래 유도, 외부 연락처로 유인
- normal: 위에 해당하지 않는 모든 글

[판정 원칙]
1. 부정적·비판적 의견 자체는 normal이다. 특정 대상을 향한 욕설·인신공격이 있을 때만 abuse다.
2. 비속어(예: 미친, 존나, 개-)가 섞여 있어도, 사람·집단을 겨냥하지 않은
   감탄·강조·자기표현이면 normal이다. 판별 기준은 "대상이 있느냐"다.
   - 대상 있음(누군가를 까는 욕) → abuse
   - 대상 없음(놀람·흥분·강조의 감탄사) → normal
3. 채용 공고, 스터디·프로젝트 팀원 모집, 강의 후기 등 취업준비 커뮤니티 맥락의
   정보성 글은 ad가 아니다. 영리 목적의 반복 홍보만 ad다.
4. toxic은 abuse, spam, ad 중 하나면 true, normal이면 false다.
5. confidence는 판정 확신도다(0.0~1.0). 애매하면 낮게 매긴다.
6. 제목과 본문 중 한 곳이라도 위반이 있으면 해당 카테고리로 분류한다."""


def ask(model: str, user_input: str) -> str:
    body = json.dumps({
        "model": model,
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": user_input},
        ],
        "stream": False,
        "think": False,                       # gemma-4 thinking 차단
        "options": {"temperature": 0.1, "num_predict": 64},
    }).encode("utf-8")
    req = urllib.request.Request(OLLAMA_URL, data=body,
                                 headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=120) as r:
        return json.loads(r.read())["message"]["content"]


def parse_pred(text: str):
    """응답에서 JSON 판정 추출. category(abuse/normal) 반환, 실패 시 None."""
    m = re.search(r"\{[^{}]*\}", text, re.DOTALL)
    if not m:
        return None
    try:
        d = json.loads(m.group())
    except json.JSONDecodeError:
        return None
    cat = str(d.get("category", "")).strip().lower()
    if cat in ("abuse", "normal", "spam", "ad"):
        # 학습 스코프는 abuse/normal 이진. spam/ad 예측은 abuse가 아니므로
        # 이진 채점에선 normal 쪽(=not abuse)으로 집계하되 따로 카운트.
        return cat
    # category 없으면 toxic 불리언으로 폴백
    if isinstance(d.get("toxic"), bool):
        return "abuse" if d["toxic"] else "normal"
    return None


def evaluate(model: str, rows: list) -> dict:
    n = len(rows)
    stat = {"model": model, "n": n, "correct": 0, "parse_fail": 0,
            "tp": 0, "fp": 0, "fn": 0, "tn": 0, "nonbinary_pred": 0}
    t0 = time.time()
    for i, r in enumerate(rows, 1):
        gold_abuse = '"abuse"' in r["output"]
        try:
            resp = ask(model, r["input"])
        except Exception as e:
            stat["parse_fail"] += 1
            continue
        cat = parse_pred(resp)
        if cat is None:
            stat["parse_fail"] += 1
            continue
        if cat in ("spam", "ad"):
            stat["nonbinary_pred"] += 1
        pred_abuse = (cat == "abuse")
        if pred_abuse and gold_abuse:   stat["tp"] += 1
        elif pred_abuse:                stat["fp"] += 1
        elif gold_abuse:                stat["fn"] += 1
        else:                           stat["tn"] += 1
        if pred_abuse == gold_abuse:
            stat["correct"] += 1
        if i % 50 == 0:
            el = time.time() - t0
            print(f"  [{model}] {i}/{n}  경과 {el/60:.1f}분  "
                  f"현재 정확도 {stat['correct']/i:.1%}  파싱실패 {stat['parse_fail']}")
    stat["elapsed_min"] = (time.time() - t0) / 60
    return stat


def report(stat: dict):
    n, c = stat["n"], stat["correct"]
    tp, fp, fn, tn = stat["tp"], stat["fp"], stat["fn"], stat["tn"]
    prec = tp / (tp + fp) if tp + fp else 0.0
    rec  = tp / (tp + fn) if tp + fn else 0.0
    print(f"\n===== {stat['model']} =====")
    print(f"  정확도       : {c}/{n} = {c/n:.1%}")
    print(f"  abuse 재현율 : {rec:.1%}  (실제 abuse 중 잡아낸 비율 - 검열 누락 지표)")
    print(f"  abuse 정밀도 : {prec:.1%}  (abuse 판정 중 진짜 비율 - 오검열 지표)")
    print(f"  파싱 실패    : {stat['parse_fail']}건 ({stat['parse_fail']/n:.1%})")
    if stat["nonbinary_pred"]:
        print(f"  spam/ad 예측 : {stat['nonbinary_pred']}건 (이진 외 카테고리)")
    print(f"  혼동행렬     : TP={tp} FP={fp} FN={fn} TN={tn}")
    print(f"  소요         : {stat['elapsed_min']:.1f}분")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--n", type=int, default=0, help="샘플 수 (0=전체)")
    ap.add_argument("--model", type=str, default="", help="한 모델만 평가")
    args = ap.parse_args()

    rows = [json.loads(l) for l in open(EVAL_PATH, encoding="utf-8")]
    if args.n:
        rows = rows[:args.n]
    print(f"eval 데이터 {len(rows)}건 로드")

    models = [args.model] if args.model else MODELS
    results = []
    for m in models:
        print(f"\n>>> {m} 평가 시작 ({len(rows)}건)")
        results.append(evaluate(m, rows))

    print("\n" + "=" * 50)
    for s in results:
        report(s)
    if len(results) == 2:
        a, b = results
        d_acc = a["correct"]/a["n"] - b["correct"]/b["n"]
        print(f"\n>>> 정확도 차이 ({a['model']} - {b['model']}): {d_acc:+.1%}p")


if __name__ == "__main__":
    main()
