"""
CareerTuner 모더레이션 파인튜닝 데이터 준비 (v2, 실측 기반 개정)

v1 대비 변경점:
  1. KMHAS_CAP 제거 -> 전체 풀(dedup 후 ~120k, abuse 50.2%/normal 49.8%)에서
     클래스 균형 유지 subsample. 실측 결과 원본이 이미 balanced라 균형 샘플링은
     비율 보존용 안전장치.
  2. TARGET_N개로 subsample (기본 15k). 이유: 시스템 프롬프트가 ~850토큰으로
     시퀀스의 90%를 차지 -> 모델이 같은 규칙을 반복 학습. 형식 정렬 SFT엔
     10~15k면 충분. 52k*3epoch는 시간 20h+ & 과학습.
  3. train / eval 자동 분리 (eval은 held-out, 텍스트 기준 disjoint).
     실측 eval이 있어야 loss 말고 실제 정확도로 튜닝 판단 가능.
  4. 길이 통계 출력 -> seq_length 근거. (실측: 전체 시퀀스 p99 ~1100토큰,
     seq_length=1024 유지가 맞음. 512로 낮추면 시스템 프롬프트가 잘림.)

시스템 프롬프트 / 서빙 포맷 / confidence(고정 0.9)는 v1 그대로 유지.
(train/serve 토큰 일치가 문구 매끄러움보다 중요. confidence는 비정보 필드로
 인지하고 유지 - 프로덕션 스키마 안정성 목적, 임계값으로 쓰지 말 것.)

출력: data/processed/train.jsonl , data/processed/eval.jsonl
"""
import csv, json, os, random, subprocess

TARGET_N   = 15000   # train 목표 건수 (15k*1epoch/32 ≈ 469 step ≈ ~2h @14.6s/it)
EVAL_N     = 1500    # held-out eval 건수
SEED       = 42
random.seed(SEED)

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
6. 제목과 본문 중 한 곳이라도 위반이 있으면 해당 카테고리로 분류한다.

[판정 예시]
- "미친 이걸 붙네 ㅋㅋ"      → 대상 없는 감탄        → {"toxic": false, "category": "normal", "confidence": 0.9}
- "와 존나 어렵네 이 코테"   → 강조·자기표현        → {"toxic": false, "category": "normal", "confidence": 0.9}
- "야 이 미친놈아 글 내려"    → 특정인 향한 욕설      → {"toxic": true,  "category": "abuse",  "confidence": 0.95}
- "ㅂㅅ같은 글 또 올라오네"   → 글/작성자 비하        → {"toxic": true,  "category": "abuse",  "confidence": 0.9}"""

def make_input(c):  return f"제목: \n본문: {c}"
def make_output(is_abuse):
    d = {"toxic": True, "category": "abuse", "confidence": 0.9} if is_abuse \
        else {"toxic": False, "category": "normal", "confidence": 0.9}
    return json.dumps(d, ensure_ascii=False)

# 모듈 루트(ml/moderation-llm) 기준 data/raw, data/processed
BASE=os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RAW=os.path.join(BASE,"data","raw"); PROC=os.path.join(BASE,"data","processed")
REPOS={"curse_detection":"https://github.com/2runo/Curse-detection-data.git",
       "unsmile":"https://github.com/smilegate-ai/korean_unsmile_dataset.git",
       "beep":"https://github.com/kocohub/korean-hate-speech.git",
       "kmhas":"https://github.com/adlnlp/K-MHaS.git"}

def clone():
    os.makedirs(RAW,exist_ok=True)
    for n,u in REPOS.items():
        t=os.path.join(RAW,n)
        if os.path.exists(t): print(f"[skip] {n}"); continue
        print(f"[clone] {n}"); subprocess.run(["git","clone","--depth","1",u,t],check=True)

def load_curse():
    rows=[]
    for line in open(f"{RAW}/curse_detection/dataset.txt",encoding="utf-8"):
        line=line.strip("\r\n")
        if not line or "|" not in line: continue
        t,l=line.rsplit("|",1); t=t.strip()
        if t: rows.append((t,l.strip()=="1"))
    return rows
def load_unsmile():
    cols=["여성/가족","남성","성소수자","인종/국적","연령","지역","종교","기타 혐오","악플/욕설"]; rows=[]
    for fn in ["unsmile_train_v1.0.tsv","unsmile_valid_v1.0.tsv"]:
        for r in csv.DictReader(open(f"{RAW}/unsmile/{fn}",encoding="utf-8"),delimiter="\t"):
            t=r.get("문장","").strip()
            if t: rows.append((t,any(r.get(c,"0").strip()=="1" for c in cols)))
    return rows
def load_beep():
    rows=[]
    for fn in ["train.tsv","dev.tsv"]:
        for r in csv.DictReader(open(f"{RAW}/beep/labeled/{fn}",encoding="utf-8"),delimiter="\t"):
            t=r.get("comments","").strip()
            if t: rows.append((t,r.get("hate","none").strip() in ("hate","offensive")))
    return rows
def load_kmhas():
    rows=[]
    for fn in ["kmhas_train.txt","kmhas_valid.txt"]:
        for r in csv.DictReader(open(f"{RAW}/kmhas/data/{fn}",encoding="utf-8"),delimiter="\t"):
            t=r.get("document","").strip(); lf=r.get("label","").strip()
            if t and lf: rows.append((t,{x.strip() for x in lf.split(",")}!={"8"}))
    return rows

def main():
    print("=== 1. clone ==="); clone()
    print("=== 2. load ===")
    allrows=load_curse()+load_unsmile()+load_beep()+load_kmhas()
    # dedup (텍스트 기준)
    seen=set(); ded=[]
    for t,ab in allrows:
        if t in seen: continue
        seen.add(t); ded.append((t,ab))
    print(f"   dedup 후 {len(ded)}건")
    abuse=[r for r in ded if r[1]]; normal=[r for r in ded if not r[1]]
    print(f"   abuse {len(abuse)} / normal {len(normal)}")
    random.shuffle(abuse); random.shuffle(normal)

    # 균형 subsample: train/eval 합쳐서 각 클래스 절반씩, 텍스트 disjoint 보장
    half_tr=TARGET_N//2; half_ev=EVAL_N//2
    need=half_tr+half_ev
    assert len(abuse)>=need and len(normal)>=need, "풀 부족"
    tr = abuse[:half_tr]+normal[:half_tr]
    ev = abuse[half_tr:half_tr+half_ev]+normal[half_tr:half_tr+half_ev]
    random.shuffle(tr); random.shuffle(ev)

    # 길이 통계 (seq_length 근거)
    def seqchars(c): return len(SYSTEM_PROMPT)+len(make_input(c))+len(make_output(False))
    L=sorted(seqchars(c) for c,_ in tr)
    p=lambda q:L[int(len(L)*q)]
    print(f"=== 3. 전체 시퀀스 char: p50={p(.5)} p95={p(.95)} p99={p(.99)} max={L[-1]}")
    print(f"        추정 토큰(≈char/1.3): p99≈{int(p(.99)/1.3)} -> seq_length=1024 적정, 낮추지 말 것")

    os.makedirs(PROC,exist_ok=True)
    def dump(rows,fn):
        with open(os.path.join(PROC,fn),"w",encoding="utf-8") as f:
            for c,ab in rows:
                f.write(json.dumps({"instruction":SYSTEM_PROMPT,"input":make_input(c),
                                    "output":make_output(ab)},ensure_ascii=False)+"\n")
    dump(tr,"train.jsonl"); dump(ev,"eval.jsonl")
    print(f"=== 완료: train {len(tr)}건, eval {len(ev)}건 (abuse/normal 각 50%) ===")
    print("   학습 config: epochs=1, seq_length=1024, rank=16, batch=32")
    print(f"   예상 step ≈ {len(tr)}//32 = {len(tr)//32}  (@14.6s/it ≈ {len(tr)//32*14.6/3600:.1f}h)")
    print("   ※ eval.jsonl은 in-distribution held-out. 데모 신뢰용으로 CareerTuner 실제")
    print("     댓글 200~300개 hard-case를 별도 수동 라벨링해 test_hard.jsonl 도 만들 것.")

if __name__=="__main__":
    main()
