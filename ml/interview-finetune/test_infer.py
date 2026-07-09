"""학습한 LoRA 어댑터 빠른 테스트 — QGEN(질문생성) + EVAL(채점)."""
import torch
from peft import AutoPeftModelForCausalLM
from transformers import AutoTokenizer

ADAPTER = "out/interview-lora"

print("모델 로딩 중... (베이스 Qwen2.5-3B + LoRA 어댑터, 처음이면 좀 걸림)")
tok = AutoTokenizer.from_pretrained(ADAPTER)
model = AutoPeftModelForCausalLM.from_pretrained(
    ADAPTER, dtype=torch.bfloat16, device_map="auto")
model.eval()
print("로딩 완료.\n")


def run(system, user, max_new=512):
    msgs = [{"role": "system", "content": system},
            {"role": "user", "content": user}]
    inputs = tok.apply_chat_template(msgs, add_generation_prompt=True,
                                     return_tensors="pt", return_dict=True).to(model.device)
    with torch.no_grad():
        out = model.generate(**inputs, max_new_tokens=max_new, do_sample=False,
                             pad_token_id=tok.eos_token_id)
    return tok.decode(out[0][inputs["input_ids"].shape[1]:], skip_special_tokens=True)


QGEN_SYS = ("너는 IT 직무 모의면접 면접관이다. 아래 면접 브리핑의 재료(회사·직무·공고)를 활용해 "
            "실제 면접에서 나올 질문을 만든다. 자료 밖 내용은 지어내지 않는다. "
            "면접 모드의 초점에 맞춰 질문 성격을 맞추되, 난이도는 국비 초급 주니어 수준으로 의도적으로 쉽게 한다. "
            "각 질문은 한국어 한 문장, question_type 은 TECH/EXPECTED/PERSONALITY/SITUATION 중 하나. "
            "JSON 배열만 반환한다.")

EVAL_SYS = ("너는 모의면접 답변을 평가하는 면접관이다. 주어진 기준 모범답안을 만점(100) 기준으로 삼아 "
            "지원자 답변을 직접 비교 채점한다. 핵심이 빠진 만큼만 감점하고, 표현 차이로는 깎지 않는다. "
            "score(0~100)와 feedback(한국어 2~3문장)만 JSON 으로 반환한다.")

print("=" * 64)
print("[1] QGEN — 브리핑을 주면 면접 질문 6개를 만드는가?")
print("=" * 64)
qgen_user = """# 면접 브리핑
회사명: 토스
직무명: 백엔드 개발자
면접 모드: 직무 — 필수 스킬별 기술 질문 위주, 국비 주니어 수준
질문 수: 6

## 직무 정보
- 필수 스킬: Java, Spring Boot, MySQL, Redis
- 주요 업무: 결제·정산 API 개발 및 운영
- 난이도: NORMAL"""
print(run(QGEN_SYS, qgen_user))

print("\n" + "=" * 64)
print("[2] EVAL — 틀린 답변을 제대로 낮게 채점하는가?")
print("=" * 64)
eval_user = """질문:
Spring Boot에서 의존성 주입(DI)이 무엇인지 설명해 주세요.

기준 모범답안(만점 기준):
의존성 주입은 객체가 필요로 하는 다른 객체를 외부에서 넣어주는 방식입니다. 직접 생성하지 않아 결합도가 낮아지고 테스트와 유지보수가 쉬워집니다. 스프링은 @Autowired 등으로 이를 자동 처리합니다.

지원자 답변:
그냥 객체를 내부에서 new 로 직접 만드는 겁니다."""
print(run(EVAL_SYS, eval_user))

print("\n완료. (위 [1] 질문 6개 / [2] 낮은 점수가 나오면 학습 성공)")
