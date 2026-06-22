# Real Validation Dataset

Place job-posting regression files here when the team has confirmed data ownership and retention rules.

Expected layout:

```text
data/real_validation/
  raw_ocr_inputs_selected_20/
  ocr_postings_selected_20/
```

The current local 20-file baseline lives under `personal/experiments/b_hybrid_ai/data/real_validation/` and is intentionally not committed.

Run against a local dataset:

```powershell
python scripts\16_run_stabilization_check.py --input-dir data\real_validation\raw_ocr_inputs_selected_20 --existing-ocr-dir data\real_validation\ocr_postings_selected_20 --min-files 20
```

Use `--min-files 43` for the production release regression gate.

Prepare a reproducible ignored working set from the local `personal/` source data:

```powershell
python scripts\25_prepare_real_regression_set.py --target-count 43 --output ..\..\.tmp\job_posting_real_regression_set\manifest.json
```

The manifest includes the selected files, copied raw/OCR output directories, and the stabilization command to run against that exact set.
