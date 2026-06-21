# 전송 manifest — 공유 4090 PC (mixed 3B 학습)

> 노트북 `ml/career-strategy-llm/` → 공유 4090 PC 로 옮길 파일.
> ⚠️ **`data/` 는 `.gitignore` 처리** — **git clone/pull 만으로는 학습 데이터가 안 따라온다.**
> **데이터 파일(`data/train.mixed.jsonl`, `data/val.mixed.jsonl`)은 반드시 USB/OneDrive/scp 로 별도 전송**한다.
> 전송 후 `06_sha256_manifest.txt` 해시와 대조(런북 §0).

## 필수 (이게 없으면 학습/검증 불가)

| 파일 | 비고 |
| --- | --- |
| `data/train.mixed.jsonl` | **학습 입력. gitignore → 수동 전송 필수** |
| `data/val.mixed.jsonl` | **검증 입력. gitignore → 수동 전송 필수** |
| `requirements.txt` | 학습 의존성 |
| `scripts/finetune_lora.py` | QLoRA 학습 |
| `scripts/merge_and_export.py` | LoRA 병합 |
| `scripts/test_infer.py` | 학습 후 검증 |
| `scripts/synth_prompts.py` | `test_infer.py` 가 import(FIT_EXPLAIN_SYS) |
| `scripts/assemble_dataset.py` | `test_infer.py` 가 import(build_fit_user) |
| `reports/00_runbook_4090.md` | 복붙 런북 |

## 권장 (재현·점검용)

| 파일 | 비고 |
| --- | --- |
| `scripts/seed_profiles.py` `prepare_data.py` `validate_dataset.py` `filter_dataset.py` `join_raw.py` `merge_raw.py` `make_review_samples.py` | 4090에서 데이터 재생성/재검증 필요 시 |
| `model-card.md` `README.md` | 맥락 |
| `reports/06_sha256_manifest.txt` | 전송 무결성 검증 |
| `reports/03_dataset_quality_report.mixed.md` `04_human_review_samples.md` | 품질·검수 참고 |

## 전송하지 않아도 되는 것

- `data/*.it_mvp.*`, `data/*.nonit.*`, `data/raw.*`, `data/seeds.*`, `data/validate.summary.*` — mixed 학습엔 불필요(보존/비교용). IT baseline 단독 학습을 4090에서 하려면 `train.it_mvp.jsonl`/`val.it_mvp.jsonl` 만 추가 전송.
- `out/`, `*.gguf` — 4090에서 생성됨.

## 전송 방법 예시

```text
USB:       data\train.mixed.jsonl, data\val.mixed.jsonl + scripts\, requirements.txt, reports\00,06 복사
OneDrive:  같은 파일 업로드 → 4090에서 다운로드
scp:       scp -r ml/career-strategy-llm <4090>:C:/Users/<공유계정>/career-strategy-llm  (data 포함 확인)
```
git 으로 scripts/문서만 받고, **데이터 2개 파일만 별도 전송**하는 방식도 가능.
