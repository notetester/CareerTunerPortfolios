# AI Job Posting Extraction Pipeline Runbook

## Scope

This runbook covers the commercial job-posting intake path:

`upload or URL -> self-hosted document extraction worker -> quality gate -> user review if needed -> job/company/fit/interview pipeline`

OpenAI is not part of the default path. It is a backup only for explicitly allowlisted job-posting OCR stages.

## Default Policy

- Primary extraction: rules plus self-hosted Python worker.
- Default OpenAI fallback: disabled.
- Runtime fallback control: admin setting at `/admin/ai-settings`.
- Persisted setting key: `JOB_POSTING_OPENAI_FALLBACK` in `ai_runtime_setting`.
- Allowed fallback stages:
  - `JOB_POSTING_PDF_OCR`
  - `JOB_POSTING_IMAGE_OCR`

Both conditions must be true before OpenAI fallback can run:

1. Global fallback setting is enabled.
2. The failed stage is in the allowlist.

Quality failure alone must not trigger OpenAI.

## Python Worker

Start the internal worker:

```powershell
C:\Users\careertuner\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe ml\job-posting-worker\scripts\15_job_posting_worker_api.py --host 127.0.0.1 --port 8091
```

Health check:

```powershell
Invoke-RestMethod http://127.0.0.1:8091/health
```

Spring integration flags:

```properties
JOB_POSTING_AI_WORKER_ENABLED=true
JOB_POSTING_AI_WORKER_BASE_URL=http://127.0.0.1:8091
JOB_POSTING_AI_WORKER_TIMEOUT=30s
JOB_POSTING_AI_CACHE_DIR=/tmp/careertuner-job-posting-worker-cache
```

Docker Compose starts the worker as `job-posting-worker` and the backend uses:

```properties
JOB_POSTING_AI_WORKER_BASE_URL=http://job-posting-worker:8091
```

For production scanned PDF/image handling, build the worker with the local OCR engine:

```powershell
docker build --build-arg INSTALL_OCR=true -t careertuner-job-posting-worker:ocr ml\job-posting-worker
```

The worker uses existing OCR text first, then local PaddleOCR when `paddleocr`, `paddlepaddle`, and PDF support through `PyMuPDF` are installed. If the OCR engine is missing and no existing OCR text is available, OCR-candidate files fail closed with `ocr_not_executed`; OpenAI is not called automatically.

`JOB_POSTING_AI_CACHE_DIR` must point to a writable path. PaddleOCR stores downloaded models under this path; using a persistent volume avoids re-downloading models on every worker restart.

Worker endpoint:

```text
POST /extract/job-posting
```

Required response contract:

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

Spring validates `qualityStatus`. Unknown values are treated as `FAILED`.

## Quality Gates

Initial gates:

- `PASS`: `qualityScore >= 70`, text length >= 500, section keyword hits >= 2.
- `REVIEW_REQUIRED`: `40 <= qualityScore < 70`.
- `FAILED`: score < 40, text length < 200, blank extraction, or parse failure.

Backend behavior:

- `PASS`: save extraction metadata and continue automatic analysis.
- `REVIEW_REQUIRED`: save extracted revision and stop automatic analysis until user confirmation.
- `FAILED`: mark extraction failed, show retry path.

User review:

- `PATCH /api/application-cases/{id}/job-posting/extraction/review`
- Body: `{ "extractedText": "..." }`
- Saves a new manual job-posting revision.
- Marks quality as `PASS`, `qualityScore=100`, and clears fallback flags.
- Continues the same automatic self-AI analysis pipeline after confirmation.

## Automatic Self-AI Pipeline

When extraction quality is `PASS`, Spring continues the commercial service flow without calling OpenAI:

1. Save/update the extracted job-posting revision.
2. Update application-case metadata from local posting rules.
3. Generate `job_analysis` rows from local skill/section parsing.
4. Generate `company_analysis` rows from job-posting facts only.
5. Generate `fit_analysis`, strategy actions, condition matrix, and learning tasks through the local deterministic fit model.
6. Create a `JOB` interview session and seed interview-prep questions.

The default pipeline model label is `self-rules-v1`. Usage logs are written with `credit_used=0` for these local stages.
Optional Qwen/Gemma analysis through the B-owned Ollama adapter is disabled by default. Enable it only after a local model is installed by setting `B_ANALYSIS_LOCAL_LLM_ENABLED=true`, `B_ANALYSIS_OLLAMA_BASE_URL`, and `B_ANALYSIS_OLLAMA_MODEL`; invalid JSON, timeout, or model absence falls back to `self-rules-v1`.

`REVIEW_REQUIRED` stops before step 3. The user review endpoint resumes steps 3-6 after the edited text is confirmed.

## Stabilization Check

Run the 20-file baseline:

```powershell
C:\Users\careertuner\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe ml\job-posting-worker\scripts\16_run_stabilization_check.py --input-dir personal\experiments\b_hybrid_ai\data\real_validation\raw_ocr_inputs_selected_20 --existing-ocr-dir personal\experiments\b_hybrid_ai\data\real_validation\ocr_postings_selected_20 --output-dir .tmp\job_posting_stabilization --report .tmp\job_posting_stabilization\document_pipeline_stabilization.md --min-files 20
```

Current release gate:

- `PASS + REVIEW_REQUIRED >= 90%`
- `FAILED <= 10%`
- `PASS` results with missing core sections <= 5%
- Max processing time per file <= 180 seconds

Artifacts:

- Summary JSON: `.tmp/job_posting_stabilization/stabilization_summary.json`
- Report: `.tmp/job_posting_stabilization/document_pipeline_stabilization.md`

Before production release, expand the regression set to at least 43 files and compare against this 20-file baseline.
Use `--min-files 43` for the production release gate.

Audit the currently available real-file inventory:

```powershell
C:\Users\careertuner\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe ml\job-posting-worker\scripts\20_audit_real_regression_inventory.py --output .tmp\job_posting_real_regression_inventory.json
```

The inventory separates actual job-posting candidates from company-info or tip/reference documents and lists OCR backfill targets. A file counts toward the 43-file production gate only when it is a real job-posting candidate and has usable OCR/text input for the document pipeline.

Import additional real job-posting files and backfill OCR text with the self-hosted OCR pipeline:

```powershell
C:\Users\careertuner\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe ml\job-posting-worker\scripts\28_import_real_regression_candidates.py --source-dir "D:\path\to\new_real_postings" --output .tmp\job_posting_real_import.json
```

The importer skips non-job reference documents, duplicate file content, and filename conflicts. PDF/image files are copied into the raw regression directory only when OCR can produce `PASS` or `REVIEW_REQUIRED` text; OpenAI is not called.
Recursive discovery also excludes synthetic fixtures and derived OCR/analysis outputs, so generated experiment artifacts cannot satisfy the real-file release gate.
If OCR quality fails, the source file is rejected before it is copied to the raw regression inventory.
The inventory audit fingerprints normalized OCR/text output and the regression-set manifest skips exact duplicate ready postings, so the 43-file gate is based on unique ready postings.

Prepare the reproducible real regression input set from the ready audited files:

```powershell
C:\Users\careertuner\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe ml\job-posting-worker\scripts\25_prepare_real_regression_set.py --target-count 43 --output .tmp\job_posting_real_regression_set\manifest.json
```

The manifest writes copied raw/OCR directories under `.tmp/job_posting_real_regression_set/` and includes the exact `16_run_stabilization_check.py` command for that set.

## Verification Commands

```powershell
# Python contracts and stabilization helpers
C:\Users\careertuner\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe -m unittest discover -s ml\job-posting-worker\tests

# Real baseline gate
C:\Users\careertuner\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe ml\job-posting-worker\scripts\16_run_stabilization_check.py --input-dir personal\experiments\b_hybrid_ai\data\real_validation\raw_ocr_inputs_selected_20 --existing-ocr-dir personal\experiments\b_hybrid_ai\data\real_validation\ocr_postings_selected_20 --output-dir .tmp\job_posting_stabilization --report .tmp\job_posting_stabilization\document_pipeline_stabilization.md --min-files 20

# Worker operational drills
C:\Users\careertuner\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe ml\job-posting-worker\scripts\17_run_worker_drills.py --output .tmp\job_posting_worker_drills.json

# Repo-owned synthetic 43-file stabilization drill
C:\Users\careertuner\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe ml\job-posting-worker\scripts\19_run_synthetic_stabilization_fixture.py --count 43 --output-dir .tmp\job_posting_synthetic_stabilization --report .tmp\job_posting_synthetic_stabilization\document_pipeline_stabilization.md

# Release readiness evidence
C:\Users\careertuner\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe ml\job-posting-worker\scripts\18_check_release_readiness.py --include-artifacts --min-files 20 --output .tmp\job_posting_release_readiness.json

# Collect all production-readiness evidence in one pass
C:\Users\careertuner\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe ml\job-posting-worker\scripts\26_run_release_evidence.py --output .tmp\job_posting_release_evidence.json

# Collect release evidence against a non-default staging DB/client
$env:DB_PASSWORD = "..."
C:\Users\careertuner\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe ml\job-posting-worker\scripts\26_run_release_evidence.py --db-host 127.0.0.1 --db-name team1_db --db-user root --mysql-bin mysql --output .tmp\job_posting_release_evidence.json

# Release evidence Markdown report
C:\Users\careertuner\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe ml\job-posting-worker\scripts\27_summarize_release_evidence.py --input .tmp\job_posting_release_evidence.json --output .tmp\job_posting_release_evidence.md

# Real regression inventory audit
C:\Users\careertuner\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe ml\job-posting-worker\scripts\20_audit_real_regression_inventory.py --output .tmp\job_posting_real_regression_inventory.json

# Import additional real postings and OCR backfill
C:\Users\careertuner\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe ml\job-posting-worker\scripts\28_import_real_regression_candidates.py --source-dir "D:\path\to\new_real_postings" --output .tmp\job_posting_real_import.json

# Prepare 43-file real regression set
C:\Users\careertuner\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe ml\job-posting-worker\scripts\25_prepare_real_regression_set.py --target-count 43 --output .tmp\job_posting_real_regression_set\manifest.json

# Full production readiness audit
C:\Users\careertuner\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe ml\job-posting-worker\scripts\21_audit_production_readiness.py --output .tmp\job_posting_production_readiness.json

# Worker Docker runtime smoke evidence
C:\Users\careertuner\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe ml\job-posting-worker\scripts\23_run_worker_docker_smoke.py --output .tmp\job_posting_worker_docker_smoke.json

# OCR runtime smoke evidence
C:\Users\careertuner\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe ml\job-posting-worker\scripts\24_run_ocr_runtime_smoke.py --output .tmp\job_posting_worker_ocr_runtime.json

# Staging DB migration evidence
$env:DB_PASSWORD = "..."
C:\Users\careertuner\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe ml\job-posting-worker\scripts\22_verify_mysql_pipeline_schema.py --host 127.0.0.1 --database team1_db --user root --output .tmp\application_case_pipeline_db_migration.json

# Backend
cd backend
.\gradlew.bat test

# Frontend
cd frontend
npm.cmd run typecheck
```

## CI Gates

`.github/workflows/service-pipeline-ci.yml` runs on backend, worker, compose, and workflow changes:

- Backend: `./gradlew test`
- Backend migration drift checks: schema and patch assertions in `ApplicationCaseExtractionMapperXmlTest`
- Backend/Python quality gate parity: shared fixture assertions in `ApplicationCaseExtractionQualityGateTest` and worker tests
- Backend/frontend extraction DTO contract drift: `ApplicationCaseExtractionContractTest`; frontend CI also runs on extraction API contract changes
- Worker: `python -m unittest discover -s tests`
- Worker drills: `python scripts/17_run_worker_drills.py`
- Synthetic 43-file stabilization drill: `python scripts/19_run_synthetic_stabilization_fixture.py --count 43`
- Release readiness static check: `python scripts/18_check_release_readiness.py`
- Compose: `docker compose config`
- Worker image runtime smoke evidence: `python ml/job-posting-worker/scripts/23_run_worker_docker_smoke.py`

## Production Readiness Evidence

The final release audit is:

```powershell
C:\Users\careertuner\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe ml\job-posting-worker\scripts\21_audit_production_readiness.py --output .tmp\job_posting_production_readiness.json
```

It must pass before this pipeline is considered production-ready. Required evidence:

- `.tmp/job_posting_release_readiness_43.json`: release readiness with `--min-files 43`.
- `.tmp/job_posting_real_regression_inventory.json`: at least 43 ready real job-posting files and zero OCR backfill gaps.
- `.tmp/job_posting_real_regression_set/manifest.json`: reproducible selected 43-file input set.
- `.tmp/job_posting_production_stabilization/stabilization_summary.json`: passing real stabilization summary with at least 43 files.
- `.tmp/job_posting_worker_drills.json`: worker operational drills passed.
- `.tmp/job_posting_worker_docker_smoke.json`: worker image runtime smoke passed.
- `.tmp/job_posting_worker_ocr_runtime.json`: OCR-capable worker smoke passed.
- `.tmp/application_case_pipeline_db_migration.json`: staging DB migration evidence passed.
- `.tmp/job_posting_release_evidence.json`: one-pass evidence collection summary.
- `.tmp/job_posting_release_evidence.md`: human-readable release evidence report.

Generate worker image evidence with `23_run_worker_docker_smoke.py`; it builds the worker image, starts the container, verifies `/health`, verifies `POST /extract/job-posting`, and writes JSON evidence.

Generate OCR evidence with `24_run_ocr_runtime_smoke.py` after installing `requirements-ocr.txt`; it verifies `paddleocr/paddlepaddle/PyMuPDF`, creates sample posting image and image-PDF inputs, runs the worker extraction contract without pre-generated OCR text, and writes JSON evidence.

Generate the reproducible real regression set with `25_prepare_real_regression_set.py`; after it reports `ok=true`, run the stabilization command from the manifest. The production stabilization command writes to `.tmp/job_posting_production_stabilization/` so it does not overwrite the 20-file baseline summary.

Use `26_run_release_evidence.py` as the release-candidate collection command. It runs the worker drills, real inventory, regression-set preparation, stabilization gate, release readiness, Docker/OCR smoke checks, DB schema verification, and final production audit while preserving each artifact.
Pass `--db-host`, `--db-port`, `--db-name`, `--db-user`, `--db-password-env`, and `--mysql-bin` when staging uses a non-default database endpoint or a MariaDB-compatible client.

Use `27_summarize_release_evidence.py` when the JSON evidence needs a compact release-review report with the current blockers, key counts, and artifact paths.

Generate the DB evidence with `22_verify_mysql_pipeline_schema.py` after applying the schema and patch files to staging. The verifier checks extraction quality metadata columns, the quality-status constraint, and the `ai_runtime_setting` table used by the OpenAI fallback allowlist.

## Operational Checks

- Admin setting remains disabled unless an operator explicitly saves an allowlist.
- `application_case_extraction.quality_status` is one of `PASS`, `REVIEW_REQUIRED`, `FAILED`, or null.
- `application_case_extraction.quality_report_json` stores extraction metrics and warnings.
- OpenAI fallback usage appears in B AI usage logs as `JOB_POSTING_OCR`.
- REVIEW_REQUIRED notifications are cleared by user action through the review screen.
- Worker timeout remains bounded by Spring HTTP timeout and the extraction worker stale-job timeout.

## Remaining Hardening

- Promote the Compose worker to the staging/production process manager and alerting stack.
- Keep at least 43 real postings in the regression set and expand it when more owned samples are available.
- Run worker unavailable, OCR model missing, malformed worker JSON, and long-file timeout drills in a staging environment.
- Add production dashboards for quality status distribution and fallback count.
