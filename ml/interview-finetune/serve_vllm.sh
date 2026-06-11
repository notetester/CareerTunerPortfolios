#!/usr/bin/env bash
# 파인튜닝한 LoRA 어댑터를 vLLM 으로 서빙한다(OpenAI 호환 /v1/chat/completions).
# 백엔드는 careertuner.interview.eval 설정으로 이 엔드포인트에 연결한다.
#
# 사용:
#   bash serve_vllm.sh out/interview-lora
#
# 그 다음 백엔드 환경변수:
#   INTERVIEW_EVAL_PROVIDER=oss
#   INTERVIEW_EVAL_BASE_URL=http://<gpu-host>:8000/v1
#   INTERVIEW_EVAL_MODEL=careertuner-interview
set -euo pipefail

ADAPTER_DIR="${1:-out/interview-lora}"
BASE_MODEL="${BASE_MODEL:-Qwen/Qwen2.5-7B-Instruct}"
SERVED_NAME="${SERVED_NAME:-careertuner-interview}"
PORT="${PORT:-8000}"

# pip install vllm 후 실행. --enable-lora 로 베이스 모델 + LoRA 어댑터를 함께 서빙한다.
exec vllm serve "${BASE_MODEL}" \
  --enable-lora \
  --lora-modules "${SERVED_NAME}=${ADAPTER_DIR}" \
  --max-lora-rank 16 \
  --port "${PORT}" \
  --dtype bfloat16
