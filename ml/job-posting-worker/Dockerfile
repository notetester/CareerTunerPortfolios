FROM python:3.14-slim

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

WORKDIR /app

COPY requirements.txt .
COPY requirements-ocr.txt .
ARG INSTALL_OCR=false
RUN pip install --no-cache-dir -r requirements.txt \
    && if [ "$INSTALL_OCR" = "true" ]; then pip install --no-cache-dir -r requirements-ocr.txt; fi \
    && useradd --create-home appuser

COPY scripts ./scripts

USER appuser

EXPOSE 8091

CMD ["python", "scripts/15_job_posting_worker_api.py", "--host", "0.0.0.0", "--port", "8091"]
