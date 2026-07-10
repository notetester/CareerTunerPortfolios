# syntax=docker/dockerfile:1
ARG PYTHON_VERSION=3.14
FROM python:${PYTHON_VERSION}-slim

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1
ENV JOB_POSTING_AI_CACHE_DIR=/app/.cache/job-posting-ai

ARG PYTHON_VERSION=3.14
ARG INSTALL_OCR=false
RUN if [ "$INSTALL_OCR" = "true" ]; then \
        apt-get update \
        && apt-get install -y --no-install-recommends libgl1 libglib2.0-0t64 libgomp1 \
        && rm -rf /var/lib/apt/lists/*; \
    fi

WORKDIR /app

COPY requirements.txt .
COPY requirements-ocr.txt .
# WSL2 overlayfs 에서 pip 26 캐시·bytecode 생성을 함께 사용하면 대규모 OCR
# 의존성 설치 중 SIGSEGV 또는 손상된 pyc가 발생할 수 있어 이미지 빌드에서 우회한다.
RUN --mount=type=cache,target=/root/.cache/pip \
    pip install --no-compile -r requirements.txt \
    && if [ "$INSTALL_OCR" = "true" ] && ! printf '%s' "$PYTHON_VERSION" | grep -Eq '^3\.(12|13)([.-]|$)'; then \
        echo "INSTALL_OCR=true requires PYTHON_VERSION=3.12 or 3.13 because paddlepaddle does not publish Python 3.14 wheels yet."; \
        exit 1; \
    fi \
    && if [ "$INSTALL_OCR" = "true" ]; then pip install --no-cache-dir --no-compile -r requirements-ocr.txt; fi \
    && useradd --create-home appuser \
    && install -d -o appuser -g appuser "$JOB_POSTING_AI_CACHE_DIR"

COPY scripts ./scripts

USER appuser

EXPOSE 8091

CMD ["python", "scripts/15_job_posting_worker_api.py", "--host", "0.0.0.0", "--port", "8091"]
