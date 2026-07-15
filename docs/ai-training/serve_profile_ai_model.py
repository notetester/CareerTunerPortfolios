"""
학습된 프로필 AI 모델을 FastAPI 서버로 실행하는 예시입니다.

Spring Boot의 FineTunedProfileAiService가 ``/analyze-profile``로 요청하는 연결은 구현되어 있습니다.
다만 런타임 기본값은 ``PROFILE_AI_FINETUNED_ENABLED=false``이고, 사용하려면 활성화와
``PROFILE_AI_FINETUNED_BASE_URL`` 설정이 모두 필요합니다. 개발 기본 포트는 8000입니다.

실행 예:
uvicorn docs.ai-training.serve_profile_ai_model:app --host 0.0.0.0 --port 8000

주의:
위 명령은 하이픈(-)이 포함된 폴더명 때문에 Python 모듈 경로로 바로 실행하기 어렵습니다.
실제로는 아래처럼 실행하는 방식을 권장합니다.

python docs/ai-training/serve_profile_ai_model.py
"""

from __future__ import annotations

import json
import os
from pathlib import Path
from typing import Any

import torch
import uvicorn
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from peft import PeftModel
from transformers import AutoModelForCausalLM, AutoTokenizer, BitsAndBytesConfig


BASE_MODEL = os.getenv("PROFILE_AI_BASE_MODEL", "Qwen/Qwen3-4B-Instruct-2507")
ADAPTER_DIR = Path(os.getenv("PROFILE_AI_ADAPTER_DIR", "docs/ai-training/output/qwen3-profile-lora"))
MAX_NEW_TOKENS = int(os.getenv("PROFILE_AI_MAX_NEW_TOKENS", "1400"))
TEMPERATURE = float(os.getenv("PROFILE_AI_TEMPERATURE", "0"))


SYSTEM_PROMPT = (
    "너는 CareerTuner 프로필 평가 AI이다. "
    "응답은 반드시 summary, extractedSkills, strengths, gaps, recommendations, criterionScores를 포함한 JSON 객체 하나로만 작성한다. "
    "criterionScores에는 GOAL_CLARITY, EXPERIENCE_SPECIFICITY, ACHIEVEMENT_EVIDENCE, JOB_SKILL_ALIGNMENT, "
    "DOCUMENT_CONSISTENCY, IMPROVEMENT_READINESS 6개 기준을 모두 포함한다."
)


class ProfileAnalyzeRequest(BaseModel):
    featureType: str = Field(default="PROFILE_COMPLETENESS")
    jobFamily: str
    profile: dict[str, Any]


class ProfileAnalyzeResponse(BaseModel):
    model: str
    adapterDir: str
    result: dict[str, Any]


app = FastAPI(title="CareerTuner Profile AI Model Server")
tokenizer: AutoTokenizer | None = None
model: PeftModel | None = None


def extract_json(text: str) -> dict[str, Any]:
    start = text.find("{")
    end = text.rfind("}")
    if start < 0 or end < start:
        raise ValueError("model output does not contain a JSON object")
    return json.loads(text[start : end + 1])


@app.on_event("startup")
def load_model() -> None:
    global tokenizer, model

    if not ADAPTER_DIR.exists():
        raise RuntimeError(f"Adapter directory not found: {ADAPTER_DIR}")

    tokenizer = AutoTokenizer.from_pretrained(ADAPTER_DIR, trust_remote_code=True)
    if tokenizer.pad_token is None:
        tokenizer.pad_token = tokenizer.eos_token

    compute_dtype = torch.bfloat16 if torch.cuda.is_available() and torch.cuda.is_bf16_supported() else torch.float16
    quantization_config = BitsAndBytesConfig(
        load_in_4bit=True,
        bnb_4bit_quant_type="nf4",
        bnb_4bit_compute_dtype=compute_dtype,
        bnb_4bit_use_double_quant=True,
    )
    base_model = AutoModelForCausalLM.from_pretrained(
        BASE_MODEL,
        quantization_config=quantization_config,
        device_map="auto",
        trust_remote_code=True,
    )
    model = PeftModel.from_pretrained(base_model, ADAPTER_DIR)
    model.eval()


@app.get("/health")
def health() -> dict[str, str]:
    return {
        "status": "UP",
        "baseModel": BASE_MODEL,
        "adapterDir": str(ADAPTER_DIR),
    }


@app.post("/analyze-profile", response_model=ProfileAnalyzeResponse)
def analyze_profile(request: ProfileAnalyzeRequest) -> ProfileAnalyzeResponse:
    if tokenizer is None or model is None:
        raise HTTPException(status_code=503, detail="model is not loaded")

    user_payload = {
        "featureType": request.featureType,
        "jobFamily": request.jobFamily,
        "profile": request.profile,
    }
    messages = [
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": json.dumps(user_payload, ensure_ascii=False)},
    ]
    prompt = tokenizer.apply_chat_template(messages, tokenize=False, add_generation_prompt=True)
    inputs = tokenizer(prompt, return_tensors="pt").to(model.device)

    try:
        with torch.no_grad():
            output_ids = model.generate(
                **inputs,
                max_new_tokens=MAX_NEW_TOKENS,
                temperature=TEMPERATURE,
                do_sample=TEMPERATURE > 0,
                pad_token_id=tokenizer.eos_token_id,
            )
        generated_ids = output_ids[0][inputs["input_ids"].shape[-1] :]
        generated_text = tokenizer.decode(generated_ids, skip_special_tokens=True)
        result = extract_json(generated_text)
    except Exception as exc:
        raise HTTPException(status_code=500, detail=f"profile AI generation failed: {exc}") from exc

    return ProfileAnalyzeResponse(
        model=BASE_MODEL,
        adapterDir=str(ADAPTER_DIR),
        result=result,
    )


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=int(os.getenv("PORT", "8000")))
