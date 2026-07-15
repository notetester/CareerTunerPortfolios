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
python ml\job-posting-worker\scripts\15_job_posting_worker_api.py --host 127.0.0.1 --port 8091
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
docker build --build-arg PYTHON_VERSION=3.12 --build-arg INSTALL_OCR=true -t careertuner-job-posting-worker:ocr ml\job-posting-worker
```

The worker uses existing OCR text first, then local PaddleOCR when `paddleocr`, `paddlepaddle`, and PDF support through `PyMuPDF` are installed. If the OCR engine is missing and no existing OCR text is available, OCR-candidate files fail closed with `ocr_not_executed`; OpenAI is not called automatically.
The default non-OCR Dockerfile build and CI unit-test runtime use Python 3.14. OCR-capable images support Python 3.12 and 3.13 because `paddlepaddle` does not publish Python 3.14 wheels yet. Docker Compose and production deploys pin `PYTHON_VERSION=3.12` to avoid the Python 3.13 native installation crashes observed on the WSL2 self-hosted runner.

The HTTP health endpoint starts before OCR model warm-up, so the worker remains observable while models initialize in the background. `JOB_POSTING_AI_CACHE_DIR` must point to a writable path. PaddleOCR and PaddleX store downloaded models under this path; Docker Compose mounts the persistent `job_posting_ai_cache` volume there to avoid re-downloading models on every worker restart.

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

## Extraction Processing Timing and Status Polling

### Overview

`createFromJobPosting()` is **asynchronous**: it inserts an application case with default metadata and queues an extraction job, then returns immediately. The actual parsing and metadata extraction happen in the background via a scheduled polling worker.

### Timing Breakdown

#### 1. Case Creation (Synchronous)
- **File**: `ApplicationCaseServiceImpl.java:99-119`
- **Action**: Insert `application_case` with `companyName = "기업명 확인 필요"` (default)
- **Returned immediately to client**
- **Extraction job status**: `QUEUED`

#### 2. Background Worker Scheduling
- **Trigger**: `@Scheduled` polling (NOT @Async or event-driven)
- **File**: `ApplicationCaseExtractionWorker.java:68-77`
- **Schedule**:
  - Initial delay: 5000ms (5 seconds, default)
  - Fixed delay: 5000ms (5 seconds, default)
  - Configurable via `careertuner.extraction.worker.initial-delay-ms` and `careertuner.extraction.worker.fixed-delay-ms`
- **Batch size**: 5 jobs per poll cycle

#### 3. Job Claim Mechanism
- **File**: `ApplicationCaseExtractionMapper.xml:122-138`
- **Action**: `QUEUED → RUNNING` state transition
- **SQL**: Atomic update with `WHERE status = 'QUEUED'` to prevent race conditions
- **Set**: `started_at = NOW()` to record processing start time

#### 4. Extraction and Metadata Parsing
- **File**: `ApplicationCaseExtractionWorker.java:123-160`
- **Actions**:
  - Extract posting text based on source type (TEXT, URL, PDF, IMAGE)
  - Parse company name and job title via regex and labeled-line detection
  - Run quality gate evaluation
- **Output**: `ExtractionResult` with parsed metadata and quality status

#### 5. Completion and Application Case Update
- **File**: `ApplicationCaseExtractionWorker.java:162-226`
- **Condition**: Only when `status == SUCCEEDED && qualityStatus == PASS`
- **File**: `ApplicationCaseExtractionMapper.xml:140-155`
- **Actions**:
  - Set `status = SUCCEEDED`, `finished_at = NOW()`
  - **Update `application_case.companyName` and `application_case.jobTitle`** only in this phase
  - Continue automatic analysis pipeline

### Status Polling for Onboarding Chatbot

#### Extraction Status Values
- **QUEUED**: Waiting to be processed by worker
- **RUNNING**: Currently being processed (includes `startedAt` timestamp)
- **SUCCEEDED**: Processing complete; check `qualityStatus`
- **FAILED**: Processing failed; see `errorMessage`

#### Quality Status Values (upon SUCCEEDED)
- **PASS**: Extraction successful, automatic analysis will proceed, metadata ready
- **REVIEW_REQUIRED**: Extraction successful but quality issues detected; user review required before automatic analysis
- **FAILED**: Internal state (extraction marked as FAILED if quality gate fails)

#### Polling Strategy for Chatbot
1. Call createFromJobPosting() → returns extraction with status='QUEUED'
2. Poll getLatestJobPostingExtraction(userId, caseId) in a loop:
   - If status == 'QUEUED' || status == 'RUNNING' → continue polling
   - If status == 'FAILED' → show error, allow retry
   - If status == 'SUCCEEDED':
     - If qualityStatus == 'PASS' → read companyName/jobTitle from ApplicationCase (metadata is now populated)
     - If qualityStatus == 'REVIEW_REQUIRED' → prompt user to review; do NOT read companyName yet
3. After user submits review (POST /api/application-cases/{id}/job-posting/extraction/review):
   - qualityStatus transitions to 'PASS'
   - companyName/jobTitle become available

#### Response Fields Available for Polling
- **File**: `ApplicationCaseExtractionResponse.java:7-26`
- **Key fields**:
  - `status`: Current extraction state (QUEUED, RUNNING, SUCCEEDED, FAILED)
  - `qualityStatus`: Quality assessment result (PASS, REVIEW_REQUIRED, null until completion)
  - `startedAt`: Timestamp when worker claimed the job
  - `finishedAt`: Timestamp when extraction completed
  - `errorMessage`: Populated if status == FAILED
  - `qualityReportJson`: Detailed quality metrics

### Guaranteed Timing Properties

- **No synchronous guarantee**: Metadata is not available immediately after case creation
- **Eventual consistency**: Within ~5 seconds (initial delay) + processing time
- **Worst-case**: ~5 seconds initial wait + ~5 seconds polling interval + extraction duration
- **Best-case**: Worker picks up job within 5 seconds of creation, metadata ready within 10 seconds total

### When companyName/jobTitle Become Available

**Only after**:
1. `extraction.status == SUCCEEDED`
2. `extraction.qualityStatus == PASS`
3. `ApplicationCaseExtractionWorker.completeSucceeded()` has executed the `applicationCaseMapper.updateApplicationCase()` call

**NOT available**:
- Immediately after `createFromJobPosting()` returns
- When `qualityStatus == REVIEW_REQUIRED` (user review required first)
- When extraction is still QUEUED or RUNNING

### Implications for Chatbot Onboarding

1. After creating a case, **always poll** `getLatestJobPostingExtraction()` to confirm metadata is ready
2. **Do not read** `application_case.companyName` until extraction completes with `PASS`
3. If user sees prompt "기업명 확인 필요" in UI, it means extraction has not yet completed
4. For REVIEW_REQUIRED results, guide user through the extraction review flow before proceeding


## Automatic Self-AI Pipeline

When extraction quality is `PASS`, Spring continues the commercial service flow without calling OpenAI:

1. Save/update the extracted job-posting revision.
2. Update application-case metadata from local posting rules.
3. Generate `job_analysis` rows from local skill/section parsing.
4. Generate `company_analysis` rows from job-posting facts only.
5. Generate `fit_analysis`, strategy actions, condition matrix, and learning tasks through the local deterministic fit model.
6. Create a `JOB` interview session and seed interview-prep questions.

Analysis through the B-owned Ollama adapter is **enabled by default** (`B_ANALYSIS_LOCAL_LLM_ENABLED=true`) and serves the fine-tuned `careertuner-b-jobposting-r1` model. Schema/grounding validation failures, invalid JSON, timeout, or model absence fall back to the `self-rules-v1` rule path after one retry; usage logs are written with `credit_used=0` for these local stages. Set `B_ANALYSIS_LOCAL_LLM_ENABLED=false` to skip the model and use `self-rules-v1` directly. Override the endpoint, model, and read timeout with `B_ANALYSIS_OLLAMA_BASE_URL`, `B_ANALYSIS_OLLAMA_MODEL`, and `B_ANALYSIS_OLLAMA_READ_TIMEOUT` (default 480s).

`REVIEW_REQUIRED` stops before step 3. The user review endpoint resumes steps 3-6 after the edited text is confirmed.

## Stabilization Check

The repository tracks the validators and the expected dataset layout, but it does **not** track the real
20-file/43-file job-posting datasets or any `.tmp/` evidence artifacts. A fresh clone therefore cannot claim
the real-data release gate. Obtain an approved, locally retained dataset, then point the commands at it with
`JOB_POSTING_REAL_RAW_DIR` and `JOB_POSTING_REAL_OCR_DIR`. The repo-owned synthetic 43-file drill validates
the pipeline mechanics only; it is not a substitute for the 43 unique real postings required by the release gate.

Run the 20-file baseline from the repository root after setting those two environment variables:

```powershell
$env:JOB_POSTING_REAL_RAW_DIR = Read-Host "Approved raw job-posting directory"
$env:JOB_POSTING_REAL_OCR_DIR = Read-Host "Approved OCR text directory"
python ml\job-posting-worker\scripts\16_run_stabilization_check.py --input-dir $env:JOB_POSTING_REAL_RAW_DIR --existing-ocr-dir $env:JOB_POSTING_REAL_OCR_DIR --output-dir .tmp\job_posting_stabilization --report .tmp\job_posting_stabilization\document_pipeline_stabilization.md --min-files 20
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
python ml\job-posting-worker\scripts\20_audit_real_regression_inventory.py --raw-dir $env:JOB_POSTING_REAL_RAW_DIR --ocr-dir $env:JOB_POSTING_REAL_OCR_DIR --output .tmp\job_posting_real_regression_inventory.json
```

The inventory separates actual job-posting candidates from company-info or tip/reference documents and lists OCR backfill targets. A file counts toward the 43-file production gate only when it is a real job-posting candidate and has usable OCR/text input for the document pipeline.

Import additional real job-posting files and backfill OCR text with the self-hosted OCR pipeline:

```powershell
$env:JOB_POSTING_IMPORT_SOURCE_DIR = Read-Host "Directory containing approved new postings"
python ml\job-posting-worker\scripts\28_import_real_regression_candidates.py --source-dir $env:JOB_POSTING_IMPORT_SOURCE_DIR --raw-dir $env:JOB_POSTING_REAL_RAW_DIR --ocr-dir $env:JOB_POSTING_REAL_OCR_DIR --output .tmp\job_posting_real_import.json
```

The importer skips non-job reference documents, duplicate file content, and filename conflicts. PDF/image files are copied into the raw regression directory only when OCR can produce `PASS` or `REVIEW_REQUIRED` text; OpenAI is not called.
Recursive discovery also excludes synthetic fixtures and derived OCR/analysis outputs, so generated experiment artifacts cannot satisfy the real-file release gate.
If OCR quality fails, the source file is rejected before it is copied to the raw regression inventory.
The inventory audit fingerprints normalized OCR/text output and the regression-set manifest skips exact duplicate ready postings, so the 43-file gate is based on unique ready postings.

Prepare the reproducible real regression input set from the ready audited files:

```powershell
python ml\job-posting-worker\scripts\25_prepare_real_regression_set.py --raw-dir $env:JOB_POSTING_REAL_RAW_DIR --ocr-dir $env:JOB_POSTING_REAL_OCR_DIR --target-count 43 --output .tmp\job_posting_real_regression_set\manifest.json
```

The manifest writes copied raw/OCR directories under `.tmp/job_posting_real_regression_set/` and includes the exact `16_run_stabilization_check.py` command for that set.

## Verification Commands

```powershell
# Python contracts and stabilization helpers
python -m unittest discover -s ml\job-posting-worker\tests

# Real baseline gate (requires locally supplied, approved data)
python ml\job-posting-worker\scripts\16_run_stabilization_check.py --input-dir $env:JOB_POSTING_REAL_RAW_DIR --existing-ocr-dir $env:JOB_POSTING_REAL_OCR_DIR --output-dir .tmp\job_posting_stabilization --report .tmp\job_posting_stabilization\document_pipeline_stabilization.md --min-files 20

# Worker operational drills
python ml\job-posting-worker\scripts\17_run_worker_drills.py --output .tmp\job_posting_worker_drills.json

# Repo-owned synthetic 43-file stabilization drill
python ml\job-posting-worker\scripts\19_run_synthetic_stabilization_fixture.py --count 43 --output-dir .tmp\job_posting_synthetic_stabilization --report .tmp\job_posting_synthetic_stabilization\document_pipeline_stabilization.md

# Release readiness evidence for the local 20-file baseline
python ml\job-posting-worker\scripts\18_check_release_readiness.py --include-artifacts --min-files 20 --output .tmp\job_posting_release_readiness.json

# Collect all production-readiness evidence in one pass (real input paths are explicit)
python ml\job-posting-worker\scripts\26_run_release_evidence.py --raw-dir $env:JOB_POSTING_REAL_RAW_DIR --ocr-dir $env:JOB_POSTING_REAL_OCR_DIR --target-count 43 --output .tmp\job_posting_release_evidence.json

# Collect release evidence against a non-default staging DB/client
$env:DB_HOST = Read-Host "Staging DB host"
$env:DB_NAME = Read-Host "Staging DB name"
$env:DB_USERNAME = Read-Host "Staging DB user"
$env:DB_PASSWORD = "..."
python ml\job-posting-worker\scripts\26_run_release_evidence.py --raw-dir $env:JOB_POSTING_REAL_RAW_DIR --ocr-dir $env:JOB_POSTING_REAL_OCR_DIR --target-count 43 --db-host $env:DB_HOST --db-name $env:DB_NAME --db-user $env:DB_USERNAME --mysql-bin mysql --output .tmp\job_posting_release_evidence.json

# Release evidence Markdown report
python ml\job-posting-worker\scripts\27_summarize_release_evidence.py --input .tmp\job_posting_release_evidence.json --output .tmp\job_posting_release_evidence.md

# Real regression inventory audit
python ml\job-posting-worker\scripts\20_audit_real_regression_inventory.py --raw-dir $env:JOB_POSTING_REAL_RAW_DIR --ocr-dir $env:JOB_POSTING_REAL_OCR_DIR --output .tmp\job_posting_real_regression_inventory.json

# Import additional real postings and OCR backfill
python ml\job-posting-worker\scripts\28_import_real_regression_candidates.py --source-dir $env:JOB_POSTING_IMPORT_SOURCE_DIR --raw-dir $env:JOB_POSTING_REAL_RAW_DIR --ocr-dir $env:JOB_POSTING_REAL_OCR_DIR --output .tmp\job_posting_real_import.json

# Prepare 43-file real regression set
python ml\job-posting-worker\scripts\25_prepare_real_regression_set.py --raw-dir $env:JOB_POSTING_REAL_RAW_DIR --ocr-dir $env:JOB_POSTING_REAL_OCR_DIR --target-count 43 --output .tmp\job_posting_real_regression_set\manifest.json

# Full production readiness audit
python ml\job-posting-worker\scripts\21_audit_production_readiness.py --target-count 43 --output .tmp\job_posting_production_readiness.json

# Worker Docker runtime smoke evidence
python ml\job-posting-worker\scripts\23_run_worker_docker_smoke.py --output .tmp\job_posting_worker_docker_smoke.json

# OCR runtime smoke evidence
python ml\job-posting-worker\scripts\24_run_ocr_runtime_smoke.py --output .tmp\job_posting_worker_ocr_runtime.json

# Staging DB migration evidence
$env:DB_PASSWORD = "..."
python ml\job-posting-worker\scripts\22_verify_mysql_pipeline_schema.py --host $env:DB_HOST --database $env:DB_NAME --user $env:DB_USERNAME --output .tmp\application_case_pipeline_db_migration.json

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
python ml\job-posting-worker\scripts\21_audit_production_readiness.py --target-count 43 --output .tmp\job_posting_production_readiness.json
```

It must pass before this pipeline is considered production-ready. These generated files are ignored local/CI
artifacts and are intentionally absent from a fresh clone; a missing artifact is a blocker, not an implicit pass.
Required evidence:

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

