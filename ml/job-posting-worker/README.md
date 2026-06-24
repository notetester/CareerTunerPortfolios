# Job Posting Worker

Self-hosted document extraction worker for the CareerTuner job-posting intake pipeline.

The worker exposes `POST /extract/job-posting` and returns extracted text plus the stable quality-gate metadata contract used by Spring. It does not call OpenAI.

## Run Locally

```powershell
python -m pip install -r requirements.txt
python scripts\15_job_posting_worker_api.py --host 127.0.0.1 --port 8091
```

Install the optional self-hosted OCR engine when the worker must process scanned PDFs or images without pre-generated OCR text:

```powershell
python -m pip install -r requirements-ocr.txt
```

This installs PaddleOCR/PaddlePaddle plus PyMuPDF for image-based PDF OCR.

Health check:

```powershell
Invoke-RestMethod http://127.0.0.1:8091/health
```

Spring integration:

```properties
JOB_POSTING_AI_WORKER_ENABLED=true
JOB_POSTING_AI_WORKER_BASE_URL=http://127.0.0.1:8091
JOB_POSTING_AI_WORKER_TIMEOUT=30s
JOB_POSTING_AI_CACHE_DIR=/tmp/careertuner-job-posting-worker-cache
```

## Docker

```powershell
docker build -t careertuner-job-posting-worker:latest .
docker run --rm -p 8091:8091 careertuner-job-posting-worker:latest
```

Build the production OCR-capable image:

```powershell
docker build --build-arg INSTALL_OCR=true -t careertuner-job-posting-worker:ocr .
```

In `docker-compose.yml`, the worker is internal-only and shares the backend upload volume read-only at `/app/.uploads` so file paths sent by Spring remain readable by the worker.

## Contract

```json
{
  "text": "...",
  "meta": {
    "strategy": "TEXT_DIRECT | PDF_TEXT | IMAGE_PDF_OCR | LONG_IMAGE_TILING | IMAGE_OCR | HTML_TEXT | WORKER_ERROR",
    "qualityScore": 0,
    "qualityStatus": "PASS | REVIEW_REQUIRED | FAILED",
    "metrics": {},
    "warnings": [],
    "sectionHints": [],
    "modelVersions": {},
    "fallbackEligible": false,
    "generatedAt": "..."
  }
}
```

## Verification

```powershell
python -m unittest discover -s tests
```

Run local operational drills:

```powershell
python scripts\17_run_worker_drills.py --output ..\..\.tmp\job_posting_worker_drills.json
```

Run a repo-owned synthetic 43-file stabilization drill:

```powershell
python scripts\19_run_synthetic_stabilization_fixture.py --count 43 --output-dir ..\..\.tmp\job_posting_synthetic_stabilization --report ..\..\.tmp\job_posting_synthetic_stabilization\document_pipeline_stabilization.md
```

Check release readiness evidence:

```powershell
python scripts\18_check_release_readiness.py --include-artifacts --min-files 20 --output ..\..\.tmp\job_posting_release_readiness.json
```

Collect all production-readiness evidence in one pass:

```powershell
python scripts\26_run_release_evidence.py --output ..\..\.tmp\job_posting_release_evidence.json
```

Pass staging DB connection details through the one-pass collector when they differ from local defaults:

```powershell
$env:DB_PASSWORD = "..."
python scripts\26_run_release_evidence.py --db-host 127.0.0.1 --db-name team1_db --db-user root --mysql-bin mysql --output ..\..\.tmp\job_posting_release_evidence.json
```

Generate a Markdown report from release evidence:

```powershell
python scripts\27_summarize_release_evidence.py --input ..\..\.tmp\job_posting_release_evidence.json --output ..\..\.tmp\job_posting_release_evidence.md
```

Audit final production readiness evidence:

```powershell
python scripts\21_audit_production_readiness.py --output ..\..\.tmp\job_posting_production_readiness.json
```

Import additional real postings and OCR backfill:

```powershell
python scripts\28_import_real_regression_candidates.py --source-dir "D:\path\to\new_real_postings" --output ..\..\.tmp\job_posting_real_import.json
```

The importer is for real postings only. It excludes synthetic fixtures and derived OCR/analysis outputs during recursive discovery.
For PDF/image candidates, OCR quality is verified before the raw file is copied; failed OCR candidates are rejected without changing the raw regression inventory.
Inventory and regression-set preparation use normalized OCR/text fingerprints to reject exact duplicate ready postings from the production count.

Prepare a reproducible real regression set from ready audited files:

```powershell
python scripts\25_prepare_real_regression_set.py --target-count 43 --output ..\..\.tmp\job_posting_real_regression_set\manifest.json
```

Generate worker Docker runtime smoke evidence:

```powershell
python scripts\23_run_worker_docker_smoke.py --output ..\..\.tmp\job_posting_worker_docker_smoke.json
```

Generate OCR runtime smoke evidence after installing `requirements-ocr.txt`:

```powershell
python scripts\24_run_ocr_runtime_smoke.py --output ..\..\.tmp\job_posting_worker_ocr_runtime.json
```

Verify an applied MySQL schema and write staging DB evidence:

```powershell
$env:DB_PASSWORD = "..."
python scripts\22_verify_mysql_pipeline_schema.py --host 127.0.0.1 --database team1_db --user root --output ..\..\.tmp\application_case_pipeline_db_migration.json
```

Run the current local 20-file baseline from the repository root when the ignored validation dataset is present:

```powershell
python ml\job-posting-worker\scripts\16_run_stabilization_check.py --input-dir personal\experiments\b_hybrid_ai\data\real_validation\raw_ocr_inputs_selected_20 --existing-ocr-dir personal\experiments\b_hybrid_ai\data\real_validation\ocr_postings_selected_20 --output-dir .tmp\job_posting_stabilization --report .tmp\job_posting_stabilization\document_pipeline_stabilization.md --min-files 20
```

## Production Notes

- OpenAI fallback is controlled by Spring admin settings and remains disabled by default.
- Scanned PDF/image extraction uses existing OCR text first, then local PaddleOCR/PyMuPDF when installed.
- Set `JOB_POSTING_AI_CACHE_DIR` to a writable persistent path for PaddleOCR model/cache files.
- The worker should run on a private network; do not publish it publicly.
- Store regression datasets outside `personal/` for team CI once licensing and data ownership are confirmed.
