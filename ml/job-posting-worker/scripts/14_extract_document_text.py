"""Extract job posting text with a self-AI-first document input contract.

This script is the local experiment contract for the production pipeline. It
does not call OpenAI. Heavy OCR uses existing self-hosted OCR output first and
then an optional locally installed PaddleOCR engine; the script classifies
inputs, scores quality, and writes stable txt/meta outputs.
"""

from __future__ import annotations

import argparse
import html
import json
import os
import re
import tempfile
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


TEXT_SUFFIXES = {".txt", ".md"}
HTML_SUFFIXES = {".html", ".htm"}
PDF_SUFFIXES = {".pdf"}
IMAGE_SUFFIXES = {".png", ".jpg", ".jpeg", ".webp", ".bmp", ".tif", ".tiff"}
SUPPORTED_SUFFIXES = TEXT_SUFFIXES | HTML_SUFFIXES | PDF_SUFFIXES | IMAGE_SUFFIXES
MIN_USABLE_TEXT_LENGTH = 200
MIN_PASS_TEXT_LENGTH = 500

SECTION_KEYWORDS = [
    "Company",
    "Role",
    "Position",
    "Responsibilities",
    "Duties",
    "Qualifications",
    "Requirements",
    "Skills",
    "Employment",
    "Benefits",
    "Apply",
    "Deadline",
    "모집부문",
    "담당업무",
    "주요업무",
    "업무내용",
    "자격요건",
    "지원자격",
    "우대사항",
    "기술스택",
    "근무조건",
    "복리후생",
    "전형절차",
    "접수방법",
    "회사소개",
    "지원방법",
    "홈페이지 지원",
    "근무지역",
    "근무형태",
    "공개채용",
    "경력채용",
    "함께할업무",
    "함께할 업무",
    "업무예요",
    "입사지원",
    "이력서",
    "포지션",
]

NOISE_KEYWORDS = [
    "로그인",
    "회원가입",
    "추천공고",
    "추천 채용",
    "공유하기",
    "인쇄하기",
    "지도",
    "주변",
    "AI 서류",
    "채용정보 메뉴",
    "스크랩",
    "관심기업",
    "앱 다운로드",
]


@dataclass(frozen=True)
class ExtractionOptions:
    long_image_ratio: float = 3.0
    long_image_min_height: int = 4000
    enable_paddle_ocr: bool = True
    paddle_ocr_lang: str = "korean"


_PADDLE_OCR_CACHE: dict[str, Any] = {}


def configure_ocr_cache_env(cache_root: Path | None = None) -> Path:
    os.environ["FLAGS_use_mkldnn"] = "0"
    root = cache_root or Path(os.environ.get(
        "JOB_POSTING_AI_CACHE_DIR",
        str(Path(tempfile.gettempdir()) / "careertuner-job-posting-worker-cache"),
    ))
    root.mkdir(parents=True, exist_ok=True)
    os.environ.setdefault("XDG_CACHE_HOME", str(root / ".cache"))
    os.environ.setdefault("PADDLE_OCR_BASE_DIR", str(root / ".paddleocr"))
    return root


def experiment_root() -> Path:
    return Path(__file__).resolve().parents[1]


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def discover_input_files(input_path: Path) -> list[Path]:
    if input_path.is_file():
        return [input_path] if input_path.suffix.lower() in SUPPORTED_SUFFIXES else []
    if not input_path.exists():
        return []
    return sorted(
        path
        for path in input_path.rglob("*")
        if path.is_file() and path.suffix.lower() in SUPPORTED_SUFFIXES
    )


def output_stem(input_path: Path) -> str:
    return input_path.stem


def output_paths(input_path: Path, output_dir: Path) -> tuple[Path, Path]:
    stem = output_stem(input_path)
    return output_dir / f"{stem}.txt", output_dir / f"{stem}.meta.json"


def read_text_file(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace").strip()


def strip_html_text(raw: str) -> str:
    text = re.sub(r"(?is)<(script|style|noscript|svg).*?</\1>", " ", raw)
    text = re.sub(r"(?s)<[^>]+>", "\n", text)
    text = html.unescape(text)
    return normalize_text(text)


def normalize_text(text: str) -> str:
    lines = []
    for line in text.replace("\r", "\n").split("\n"):
        normalized = re.sub(r"[ \t]+", " ", line.replace("\u00a0", " ")).strip()
        if normalized:
            lines.append(normalized)
    return "\n".join(lines).strip()


def optional_pdf_text(path: Path) -> tuple[str, str | None]:
    try:
        from pypdf import PdfReader
    except ImportError:
        return "", "pypdf_not_installed"
    try:
        reader = PdfReader(str(path))
        text = "\n".join((page.extract_text() or "") for page in reader.pages)
        return normalize_text(text), None
    except Exception as exc:  # noqa: BLE001 - this is a diagnostic experiment script.
        return "", f"pdf_text_extract_failed:{exc.__class__.__name__}"


def image_size(path: Path) -> tuple[int, int] | None:
    try:
        from PIL import Image
    except ImportError:
        return None
    try:
        with Image.open(path) as image:
            return image.size
    except Exception:
        return None


def is_long_image(width: int, height: int, options: ExtractionOptions) -> bool:
    if width <= 0:
        return False
    return height >= options.long_image_min_height or (height / width) >= options.long_image_ratio


def classify_strategy(input_path: Path, options: ExtractionOptions) -> tuple[str, list[str]]:
    suffix = input_path.suffix.lower()
    warnings: list[str] = []
    if suffix in TEXT_SUFFIXES:
        return "TEXT_DIRECT", warnings
    if suffix in HTML_SUFFIXES:
        return "HTML_TEXT", warnings
    if suffix in PDF_SUFFIXES:
        text, warning = optional_pdf_text(input_path)
        if warning:
            warnings.append(warning)
        if text:
            return "PDF_TEXT", warnings
        return "IMAGE_PDF_OCR", warnings
    if suffix in IMAGE_SUFFIXES:
        size = image_size(input_path)
        if size is None:
            warnings.append("image_size_unavailable")
            return "IMAGE_OCR", warnings
        width, height = size
        if is_long_image(width, height, options):
            return "LONG_IMAGE_TILING", warnings
        return "IMAGE_OCR", warnings
    return "UNSUPPORTED", ["unsupported_file_type"]


def existing_ocr_text(input_path: Path, existing_ocr_dir: Path | None) -> str:
    if existing_ocr_dir is None:
        return ""
    candidates = [
        existing_ocr_dir / f"{input_path.stem}.txt",
        existing_ocr_dir / input_path.with_suffix(".txt").name,
    ]
    for candidate in candidates:
        if candidate.exists():
            return read_text_file(candidate)
    return ""


def load_paddleocr_class():
    configure_ocr_cache_env()
    try:
        import paddle
        paddle.set_flags({"FLAGS_use_mkldnn": False})
    except Exception:
        pass
    try:
        from paddleocr import PaddleOCR
    except ImportError as exc:
        raise RuntimeError("paddleocr and paddlepaddle are required for local OCR execution.") from exc
    return PaddleOCR


def is_constructor_argument_error(exc: Exception) -> bool:
    return isinstance(exc, TypeError) or "Unknown argument:" in str(exc)


def create_paddle_ocr(lang: str):
    if lang in _PADDLE_OCR_CACHE:
        return _PADDLE_OCR_CACHE[lang]
    paddle_ocr_class = load_paddleocr_class()
    constructor_options = [
        {"lang": lang, "enable_mkldnn": False},
        {"lang": lang},
        {"enable_mkldnn": False},
        {},
    ]
    last_error: Exception | None = None
    for options in constructor_options:
        try:
            ocr = paddle_ocr_class(**options)
            _PADDLE_OCR_CACHE[lang] = ocr
            return ocr
        except (TypeError, ValueError, RuntimeError) as exc:
            if isinstance(exc, RuntimeError) and "Unknown argument" not in str(exc):
                raise
            if isinstance(exc, (TypeError, ValueError)) and not is_constructor_argument_error(exc):
                raise
            last_error = exc
    raise RuntimeError("Unable to create PaddleOCR with supported constructor options.") from last_error


def collect_ocr_text(value: Any) -> list[str]:
    lines: list[str] = []
    if isinstance(value, dict):
        for key in ("rec_texts", "texts", "rec_text"):
            field = value.get(key)
            if isinstance(field, str) and field.strip():
                lines.append(field.strip())
            elif isinstance(field, list):
                lines.extend(str(item).strip() for item in field if str(item).strip())
        for field in value.values():
            if isinstance(field, (dict, list, tuple)):
                lines.extend(collect_ocr_text(field))
        return lines
    if isinstance(value, (list, tuple)):
        if (
            len(value) >= 2
            and isinstance(value[1], (list, tuple))
            and value[1]
            and isinstance(value[1][0], str)
        ):
            text = value[1][0].strip()
            return [text] if text else []
        for item in value:
            lines.extend(collect_ocr_text(item))
    return lines


def unique_preserving_order(lines: list[str]) -> list[str]:
    seen: set[str] = set()
    unique: list[str] = []
    for line in lines:
        normalized = line.strip()
        if not normalized or normalized in seen:
            continue
        seen.add(normalized)
        unique.append(normalized)
    return unique


def paddle_ocr_text(input_path: Path, lang: str) -> str:
    ocr = create_paddle_ocr(lang)
    result = _run_paddle_ocr(ocr, input_path)
    if isinstance(result, list) and result and isinstance(result[0], str):
        lines = result
    else:
        lines = collect_ocr_text(result)
    return normalize_text("\n".join(unique_preserving_order(lines)))


def _run_paddle_ocr(ocr: Any, input_path: Path) -> Any:
    path_str = str(input_path)
    if hasattr(ocr, "predict"):
        try:
            results = list(ocr.predict(path_str))
            lines: list[str] = []
            for res in results:
                texts = None
                if hasattr(res, "get"):
                    texts = res.get("rec_texts")
                elif hasattr(res, "rec_texts"):
                    texts = res.rec_texts
                if texts:
                    lines.extend(str(t).strip() for t in texts if str(t).strip())
                else:
                    lines.extend(collect_ocr_text(res))
            return lines if lines else results
        except Exception:
            pass
    try:
        return ocr.ocr(path_str, cls=True)
    except TypeError:
        return ocr.ocr(path_str)


def section_hints(text: str) -> list[str]:
    return [keyword for keyword in SECTION_KEYWORDS if keyword in text]


def count_noise_hits(text: str) -> int:
    return sum(text.count(keyword) for keyword in NOISE_KEYWORDS)


def line_metrics(text: str) -> dict[str, Any]:
    lines = [line.strip() for line in text.splitlines() if line.strip()]
    if not lines:
        return {
            "nonEmptyLineCount": 0,
            "singleCharLineRatio": 0.0,
            "repeatedLineRatio": 0.0,
        }
    single_char = sum(1 for line in lines if len(line) == 1)
    repeats = len(lines) - len(set(lines))
    return {
        "nonEmptyLineCount": len(lines),
        "singleCharLineRatio": round(single_char / len(lines), 4),
        "repeatedLineRatio": round(repeats / len(lines), 4),
    }


def quality_score(metrics: dict[str, Any]) -> int:
    text_length = int(metrics["textLength"])
    section_hits = int(metrics["sectionKeywordHitCount"])
    noise_hits = int(metrics["noiseKeywordHitCount"])
    single_ratio = float(metrics["singleCharLineRatio"])
    repeat_ratio = float(metrics["repeatedLineRatio"])

    if text_length <= 0:
        return 0

    if text_length >= 1000:
        length_score = 35
    elif text_length >= 500:
        length_score = 25
    elif text_length >= 200:
        length_score = 10
    else:
        length_score = 0

    section_score = min(35, section_hits * 12)
    structure_score = max(0, 25 - int(round(single_ratio * 30)) - int(round(repeat_ratio * 20)))
    noise_penalty = min(25, noise_hits * 3)
    return max(0, min(100, length_score + section_score + structure_score - noise_penalty))


def quality_status(score: int, metrics: dict[str, Any]) -> str:
    text_length = int(metrics["textLength"])
    if text_length < MIN_USABLE_TEXT_LENGTH:
        return "FAILED"
    if score >= 70 and text_length >= MIN_PASS_TEXT_LENGTH and int(metrics["sectionKeywordHitCount"]) >= 2:
        return "PASS"
    if score >= 40:
        return "REVIEW_REQUIRED"
    return "FAILED"


def analyze_quality(text: str, seed_warnings: list[str] | None = None) -> dict[str, Any]:
    normalized = normalize_text(text)
    hints = section_hints(normalized)
    metrics = {
        "textLength": len(normalized),
        "sectionKeywordHitCount": len(hints),
        "sectionKeywords": hints,
        "noiseKeywordHitCount": count_noise_hits(normalized),
        **line_metrics(normalized),
    }
    score = quality_score(metrics)
    warnings = list(seed_warnings or [])
    if metrics["textLength"] < MIN_USABLE_TEXT_LENGTH:
        warnings.append("text_too_short")
    if metrics["textLength"] < MIN_PASS_TEXT_LENGTH:
        warnings.append("text_short_for_auto_analysis")
    if metrics["sectionKeywordHitCount"] == 0:
        warnings.append("section_keywords_missing")
    if metrics["noiseKeywordHitCount"] > 0:
        warnings.append("site_noise_detected")
    if metrics["singleCharLineRatio"] >= 0.25:
        warnings.append("too_many_single_char_lines")
    status = quality_status(score, metrics)
    return {
        "qualityScore": score,
        "qualityStatus": status,
        "metrics": metrics,
        "warnings": sorted(set(warnings)),
        "sectionHints": hints,
    }


def fallback_eligible(strategy: str) -> bool:
    return strategy in {"IMAGE_PDF_OCR", "LONG_IMAGE_TILING", "IMAGE_OCR"}


def extract_text_for_strategy(
    input_path: Path,
    strategy: str,
    existing_ocr_dir: Path | None,
    warnings: list[str],
    options: ExtractionOptions,
) -> tuple[str, str, list[str]]:
    suffix = input_path.suffix.lower()
    if strategy == "TEXT_DIRECT":
        return normalize_text(read_text_file(input_path)), "INPUT_TEXT", warnings
    if strategy == "HTML_TEXT":
        return strip_html_text(read_text_file(input_path)), "HTML_TEXT", warnings
    if strategy == "PDF_TEXT":
        text, warning = optional_pdf_text(input_path)
        next_warnings = list(warnings)
        if warning:
            next_warnings.append(warning)
        return text, "PDF_TEXT", next_warnings
    if suffix in PDF_SUFFIXES | IMAGE_SUFFIXES:
        text = existing_ocr_text(input_path, existing_ocr_dir)
        if text:
            return normalize_text(text), "EXISTING_OCR", warnings
        next_warnings = list(warnings)
        if options.enable_paddle_ocr:
            try:
                text = paddle_ocr_text(input_path, options.paddle_ocr_lang)
                if text:
                    return text, "PADDLE_OCR", next_warnings
                next_warnings.append("paddleocr_empty_result")
            except RuntimeError as exc:
                message = str(exc).lower()
                next_warnings.append("paddleocr_unavailable" if "required" in message else "paddleocr_failed")
                next_warnings.append(f"paddleocr_error:{exc.__class__.__name__}")
            except Exception as exc:
                next_warnings.append("paddleocr_failed")
                next_warnings.append(f"paddleocr_error:{exc.__class__.__name__}")
        return "", "NOT_EXECUTED", [*next_warnings, "ocr_not_executed"]
    return "", "UNSUPPORTED", [*warnings, "unsupported_file_type"]


def model_versions() -> dict[str, str]:
    return {
        "documentExtractionContract": "self_ai_v1",
        "qualityGate": "heuristic_20260617",
        "ocr": "existing_output_or_paddleocr",
    }


def extract_document(
    input_path: Path,
    output_dir: Path,
    existing_ocr_dir: Path | None = None,
    options: ExtractionOptions | None = None,
) -> dict[str, Any]:
    options = options or ExtractionOptions()
    output_dir.mkdir(parents=True, exist_ok=True)
    strategy, strategy_warnings = classify_strategy(input_path, options)
    text, text_source, text_warnings = extract_text_for_strategy(
        input_path=input_path,
        strategy=strategy,
        existing_ocr_dir=existing_ocr_dir,
        warnings=strategy_warnings,
        options=options,
    )
    quality = analyze_quality(text, text_warnings)
    result = {
        "inputFile": input_path.name,
        "inputSuffix": input_path.suffix.lower(),
        "strategy": strategy,
        "textSource": text_source,
        "qualityScore": quality["qualityScore"],
        "qualityStatus": quality["qualityStatus"],
        "metrics": quality["metrics"],
        "warnings": quality["warnings"],
        "sectionHints": quality["sectionHints"],
        "modelVersions": model_versions(),
        "fallbackEligible": fallback_eligible(strategy),
        "generatedAt": now_iso(),
    }
    text_path, meta_path = output_paths(input_path, output_dir)
    text_path.write_text(text + ("\n" if text else ""), encoding="utf-8")
    meta_path.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return result


def write_report(results: list[dict[str, Any]], report_path: Path) -> None:
    report_path.parent.mkdir(parents=True, exist_ok=True)
    status_counts: dict[str, int] = {}
    for result in results:
        status_counts[result["qualityStatus"]] = status_counts.get(result["qualityStatus"], 0) + 1
    lines = [
        "# Document Text Extraction Check",
        "",
        f"Generated at: {now_iso()}",
        "",
        "## Summary",
        "",
        "| Status | Count |",
        "|---|---:|",
    ]
    for status in ("PASS", "REVIEW_REQUIRED", "FAILED"):
        lines.append(f"| {status} | {status_counts.get(status, 0)} |")
    lines.extend(
        [
            "",
            "## Files",
            "",
            "| File | Strategy | Text source | Score | Status | Warnings |",
            "|---|---|---|---:|---|---|",
        ]
    )
    for result in sorted(results, key=lambda item: item["inputFile"]):
        warnings = ", ".join(result["warnings"])
        lines.append(
            "| {inputFile} | {strategy} | {textSource} | {qualityScore} | {qualityStatus} | {warnings} |".format(
                inputFile=result["inputFile"],
                strategy=result["strategy"],
                textSource=result["textSource"],
                qualityScore=result["qualityScore"],
                qualityStatus=result["qualityStatus"],
                warnings=warnings,
            )
        )
    report_path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def run(
    input_path: Path,
    output_dir: Path,
    existing_ocr_dir: Path | None,
    report_path: Path | None,
    limit: int | None,
    options: ExtractionOptions | None = None,
) -> int:
    files = discover_input_files(input_path)
    if limit is not None:
        files = files[: max(0, limit)]
    if not files:
        print(f"No supported files found in {input_path}")
        return 0
    results = [
        extract_document(path, output_dir, existing_ocr_dir=existing_ocr_dir, options=options)
        for path in files
    ]
    if report_path is not None:
        write_report(results, report_path)
    print(f"processed={len(results)}")
    print(f"output_dir={output_dir}")
    if report_path is not None:
        print(f"report={report_path}")
    return 0


def parse_args() -> argparse.Namespace:
    root = experiment_root()
    default_base = root / "data" / "real_validation"
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, default=default_base / "raw_ocr_inputs_selected_20")
    parser.add_argument("--output-dir", type=Path, default=default_base / "document_text_extraction")
    parser.add_argument("--existing-ocr-dir", type=Path, default=None)
    parser.add_argument("--report", type=Path, default=root / "reports" / "document_text_extraction_check.md")
    parser.add_argument("--limit", type=int, default=None)
    parser.add_argument("--disable-paddle-ocr", action="store_true")
    parser.add_argument("--paddle-ocr-lang", default="korean")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    raise SystemExit(
        run(
            input_path=args.input,
            output_dir=args.output_dir,
            existing_ocr_dir=args.existing_ocr_dir,
            report_path=args.report,
            limit=args.limit,
            options=ExtractionOptions(
                enable_paddle_ocr=not args.disable_paddle_ocr,
                paddle_ocr_lang=args.paddle_ocr_lang,
            ),
        )
    )


if __name__ == "__main__":
    main()
