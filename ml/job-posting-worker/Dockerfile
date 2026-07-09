# syntax=docker/dockerfile:1
ARG PYTHON_VERSION=3.14
FROM python:${PYTHON_VERSION}-slim

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

WORKDIR /app

COPY requirements.txt .
COPY requirements-ocr.txt .
ARG PYTHON_VERSION=3.14
ARG INSTALL_OCR=false
RUN --mount=type=cache,target=/root/.cache/pip \
    pip install -r requirements.txt \
    && if [ "$INSTALL_OCR" = "true" ] && ! printf '%s' "$PYTHON_VERSION" | grep -Eq '^3\.13([.-]|$)'; then \
        echo "INSTALL_OCR=true currently requires PYTHON_VERSION=3.13 because paddlepaddle does not publish Python 3.14 wheels yet."; \
        exit 1; \
    fi \
    && if [ "$INSTALL_OCR" = "true" ]; then pip install -r requirements-ocr.txt; fi \
    && useradd --create-home appuser

COPY scripts ./scripts

USER appuser

EXPOSE 8091

CMD ["python", "scripts/15_job_posting_worker_api.py", "--host", "0.0.0.0", "--port", "8091"]
