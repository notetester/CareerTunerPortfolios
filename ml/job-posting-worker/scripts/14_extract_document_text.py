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
import threading
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
    "What you will do",
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
    "직무내용",
    "모집직무",
    "합류하게 되면 이런 일을 하게 됩니다",
    "이런동료를기다립니다",
    "이런 분이라면 더욱좋습니다",
    "이런 분을 찾습니다",
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
    # 레이아웃 인식 OCR(PPStructureV3): 표·2단 공고의 읽기 순서 복원. 실패 시 기본 line-OCR 폴백.
    enable_ppstructure: bool = True
    paddle_ocr_lang: str = "korean"


_PADDLE_OCR_CACHE: dict[str, Any] = {}
_PPSTRUCTURE_CACHE: dict[str, Any] = {}
_OCR_ENGINE_LOCK = threading.Lock()


def configure_ocr_cache_env(cache_root: Path | None = None) -> Path:
    os.environ["FLAGS_use_mkldnn"] = "0"
    root = cache_root or Path(os.environ.get(
        "JOB_POSTING_AI_CACHE_DIR",
        str(Path(tempfile.gettempdir()) / "careertuner-job-posting-worker-cache"),
    ))
    root.mkdir(parents=True, exist_ok=True)
    os.environ.setdefault("XDG_CACHE_HOME", str(root / ".cache"))
    os.environ.setdefault("PADDLE_OCR_BASE_DIR", str(root / ".paddleocr"))
    os.environ.setdefault("PADDLE_PDX_CACHE_HOME", str(root / ".paddlex"))
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


# \uc0ac\uc774\ud2b8/\ud478\ud130 \uc7a1\uc74c \uc81c\uac70\uc6a9(\ubd84\uc11d\uc5d0 \ub4e4\uc5b4\uac08 OCR \ud14d\uc2a4\ud2b8\ub9cc \uc815\ub9ac. \uba85\ubc31\ud55c \uc7a1\uc74c \uc904\ub9cc \ubcf4\uc218\uc801\uc73c\ub85c \uc81c\uac70).
_NOISE_URL = re.compile(r"https?://\S+|www\.\S+|[\w.+-]+@[\w-]+\.[\w.-]+|\b[\w-]+\.(?:go\.kr|or\.kr|co\.kr|com|net|kr)\b", re.I)
_NOISE_COPYRIGHT = re.compile(r"all rights reserved|\u00a9|\u24d2|\(c\)\s*\uce74\uce74\uc624|\(c\)\s*\d", re.I)
_NOISE_PRINTDATE = re.compile(r"^\ucd9c\ub825\uc77c\uc790\s*[:\uff1a]", re.I)
_NOISE_FOOTER_EXACT = {
    "contact", "\uacf5\uc720\ud558\uae30", "\uc2a4\ud06c\ub7a9", "\uc778\uc1c4\ud558\uae30", "\ucd94\ucc9c\uacf5\uace0", "\uc9c0\ub3c4", "\uad00\uc2ec\uae30\uc5c5", "\uc571\ub2e4\uc6b4\ub85c\ub4dc", "\ubaa9\ub85d",
}


def _line_meaningful_chars(text: str) -> int:
    return sum(1 for ch in text if ch.isalnum() or 0xAC00 <= ord(ch) <= 0xD7A3)


def strip_site_noise(text: str) -> str:
    """OCR \uacb0\uacfc\uc5d0\uc11c \uc0ac\uc774\ud2b8/\ud478\ud130 \uc7a1\uc74c \uc904(\uc800\uc791\uad8c\u00b7\ucd9c\ub825\uc77c\uc790\u00b7standalone URL/email\u00b7footer \ud0a4\uc6cc\ub4dc)\uc744 \ubcf4\uc218\uc801\uc73c\ub85c \uc81c\uac70\ud55c\ub2e4.
    \ubcf8\ubb38 \uc190\uc2e4\uc744 \ub9c9\uae30 \uc704\ud574, URL/email \uc740 \uadf8 \uc904\uc5d0\uc11c \uc81c\uac70 \ud6c4 \uc758\ubbf8 \uae00\uc790\uac00 \uac70\uc758 \uc5c6\uc744 \ub54c\ub9cc \uc904 \uc804\uccb4\ub97c \ubc84\ub9b0\ub2e4."""
    kept = []
    for line in text.split("\n"):
        stripped = line.strip()
        if not stripped:
            continue
        if _NOISE_COPYRIGHT.search(stripped) or _NOISE_PRINTDATE.match(stripped):
            continue
        if re.sub(r"\s+", "", stripped).lower() in _NOISE_FOOTER_EXACT:
            continue
        if _NOISE_URL.search(stripped) and _line_meaningful_chars(_NOISE_URL.sub("", stripped)) <= 3:
            continue
        kept.append(line)
    return "\n".join(kept)


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
    with _OCR_ENGINE_LOCK:
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
    return strip_site_noise(normalize_text("\n".join(unique_preserving_order(lines))))


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


def load_ppstructure_class():
    configure_ocr_cache_env()
    try:
        import paddle

        paddle.set_flags({"FLAGS_use_mkldnn": False})
    except Exception:
        pass
    try:
        from paddleocr import PPStructureV3
    except ImportError as exc:
        raise RuntimeError("paddleocr (PPStructureV3) is required for layout-aware OCR.") from exc
    return PPStructureV3


def create_ppstructure(lang: str):
    with _OCR_ENGINE_LOCK:
        if lang in _PPSTRUCTURE_CACHE:
            return _PPSTRUCTURE_CACHE[lang]
        ppstructure_class = load_ppstructure_class()
        # enable_mkldnn=False 는 PaddleX 공통 kwargs 로 하위 모델 전체에 전파돼 oneDNN 런타임 크래시를 막는다.
        constructor_options = [
            {"lang": lang, "enable_mkldnn": False},
            {"lang": lang},
            {"enable_mkldnn": False},
            {},
        ]
        last_error: Exception | None = None
        for options in constructor_options:
            try:
                engine = ppstructure_class(**options)
                _PPSTRUCTURE_CACHE[lang] = engine
                return engine
            except (TypeError, ValueError, RuntimeError) as exc:
                last_error = exc
        raise RuntimeError("Unable to create PPStructureV3 with supported options.") from last_error


def ppstructure_text(input_path: Path, lang: str) -> str:
    engine = create_ppstructure(lang)
    # 공고 추출에 불필요한 무거운 모듈은 끈다(수식/도장/차트). 레이아웃+표+텍스트만 사용.
    results = list(engine.predict(
        str(input_path),
        use_formula_recognition=False,
        use_seal_recognition=False,
        use_chart_recognition=False,
    ))
    lines: list[str] = []
    for res in results:
        blocks = res.get("parsing_res_list") if hasattr(res, "get") else None
        if not blocks:
            # 레이아웃 블록이 없으면 원시 OCR 결과로라도 텍스트를 건진다.
            ocr_res = res.get("overall_ocr_res") if hasattr(res, "get") else None
            if ocr_res is not None:
                lines.extend(collect_ocr_text(ocr_res))
            continue
        for block in blocks:
            content = _ppstructure_block_content(block)
            if content:
                lines.append(content)
    return strip_site_noise(normalize_text("\n".join(lines)))


def _ppstructure_block_content(block: Any) -> str:
    label = str(_ppstructure_block_attr(block, "label") or "").lower()
    content = str(_ppstructure_block_attr(block, "content") or "").strip()
    if not content:
        return ""
    # 표 블록은 HTML 로 오는 경우가 있어 셀 텍스트만 남긴다.
    if "table" in label and "<" in content and ">" in content:
        content = strip_html_text(content)
    return content


def _ppstructure_block_attr(block: Any, name: str) -> Any:
    if isinstance(block, dict):
        return block.get(name)
    return getattr(block, name, None)


def section_hints(text: str) -> list[str]:
    # 헤더 띄어쓰기/대소문자 차이로 섹션을 놓치지 않도록 공백 제거 + casefold 후 매칭한다.
    # 예: 본문 "담당 업무"가 키워드 "담당업무"와 매칭된다. 정규화 형태로 중복(예: "함께할업무"/"함께할 업무") 제거.
    collapsed = re.sub(r"\s+", "", text).casefold()
    hints: list[str] = []
    seen: set[str] = set()
    for keyword in SECTION_KEYWORDS:
        norm = re.sub(r"\s+", "", keyword).casefold()
        if norm and norm in collapsed and norm not in seen:
            seen.add(norm)
            hints.append(keyword)
    return hints


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


# 핵심 업무 섹션(담당업무/주요업무/responsibilities) 헤더로 인식할 키워드.
CRITICAL_SECTION_HEADERS = [
    "담당업무", "주요업무", "업무내용", "함께할업무", "함께할 업무",
    "직무내용", "합류하게 되면 이런 일을 하게 됩니다",
    "responsibilities", "duties", "what you will do",
]
# 업무 본문에 실제 업무/기술 내용이 있는지 판별하는 토큰(하나라도 있으면 useful line).
USEFUL_WORK_TOKENS = [
    "개발", "운영", "설계", "구축", "개선", "관리", "분석", "협업", "구현", "담당", "수행", "기획",
    "build", "operate", "develop", "design", "manage", "implement", "maintain",
    "api", "react", "vue", "typescript", "javascript", "spring", "node", "python",
    "java", "kotlin", "aws", "sql", "kubernetes", "docker", "mysql",
]
_ISOLATED_JAMO = re.compile(r"[㄰-㆏]")


# 헤더 줄 앞뒤에 흔히 붙는 불릿/구두점(예: "자격요건:", "·우대사항", "주요업무 -").
_HEADER_TRIM_CHARS = " \t·•◦▪‣▶▷●○■□*-–—:：.|"
_INLINE_HEADER_SEPARATORS = (":", "：", "-", "–", "—")


def _normalize_header_token(value: str) -> str:
    """앞뒤 불릿·구두점·공백을 제거하고 casefold 한 헤더 매칭용 토큰을 만든다."""
    return re.sub(r"\s+", "", value.strip(_HEADER_TRIM_CHARS)).casefold()


def _is_header_like_line(line: str) -> bool:
    """한 줄이 섹션 헤더처럼 보이는지(불릿·구두점 정규화 후 섹션 키워드와 정확히 일치 + 짧음) 판정한다."""
    norm = _normalize_header_token(line)
    if not norm or len(norm) > 16:
        return False
    return any(_normalize_header_token(keyword) == norm for keyword in SECTION_KEYWORDS)


def _split_inline_section_header(line: str) -> list[str]:
    """Split "Responsibilities: body" into logical header/body lines for quality metrics only."""
    stripped = line.strip()
    if not stripped:
        return []
    section_norms = {_normalize_header_token(keyword) for keyword in SECTION_KEYWORDS}
    for separator in _INLINE_HEADER_SEPARATORS:
        separator_index = stripped.find(separator)
        if separator_index <= 0:
            continue
        prefix = stripped[:separator_index]
        prefix_norm = _normalize_header_token(prefix)
        if not prefix_norm or len(prefix_norm) > 16 or prefix_norm not in section_norms:
            continue
        body = stripped[separator_index + len(separator):].strip()
        logical_lines = [prefix.strip()]
        if body:
            logical_lines.append(body)
        return logical_lines
    return [stripped]


def _is_useful_work_line(line: str) -> bool:
    lower = line.casefold()
    return any(token in lower for token in USEFUL_WORK_TOKENS)


def _meaningful_char_count(text: str) -> int:
    return sum(1 for ch in text if ch.isalnum() or 0xAC00 <= ord(ch) <= 0xD7A3)


def _is_suspect_line(line: str) -> bool:
    """OCR 파편으로 의심되는 줄: 고립 자모/희귀 유니코드/짧은 토큰 위주/의미 글자 거의 없음."""
    stripped = line.strip()
    if not stripped:
        return False
    if _ISOLATED_JAMO.search(stripped):
        return True
    for ch in stripped:
        if ch.isspace() or ch.isalnum() or 0xAC00 <= ord(ch) <= 0xD7A3:
            continue
        if ch in "·/,.()[]{}%~:;-+–—'\"!?&•…":
            continue
        return True  # 흔치 않은 기호/유니코드 포함
    tokens = stripped.split()
    if len(tokens) >= 3 and sum(1 for t in tokens if len(t) <= 2) / len(tokens) > 0.7:
        return True
    return _meaningful_char_count(stripped) <= 2


def critical_section_metrics(normalized_text: str) -> dict[str, Any]:
    """핵심 업무 섹션 헤더~다음 헤더 사이 본문의 품질 지표를 측정한다."""
    physical_lines = [line.strip() for line in normalized_text.splitlines() if line.strip()]
    lines = [logical for line in physical_lines for logical in _split_inline_section_header(line)]
    header_norms = {_normalize_header_token(keyword) for keyword in CRITICAL_SECTION_HEADERS}
    body: list[str] | None = None
    for index, line in enumerate(lines):
        norm = _normalize_header_token(line)
        if norm in header_norms and len(norm) <= 16:
            body = []
            for following in lines[index + 1:]:
                if _is_header_like_line(following):
                    break
                body.append(following)
            break
    if body is None:
        return {"exists": False, "usefulLineCount": 0, "meaningfulCharCount": 0, "suspectLineRatio": 0.0}
    useful = sum(1 for line in body if _is_useful_work_line(line))
    suspect = sum(1 for line in body if _is_suspect_line(line))
    meaningful = sum(_meaningful_char_count(line) for line in body)
    return {
        "exists": True,
        "usefulLineCount": useful,
        "meaningfulCharCount": meaningful,
        "suspectLineRatio": round(suspect / len(body), 4) if body else 0.0,
    }


def analyze_quality(text: str, seed_warnings: list[str] | None = None) -> dict[str, Any]:
    normalized = normalize_text(text)
    hints = section_hints(normalized)
    critical = critical_section_metrics(normalized)
    metrics = {
        "textLength": len(normalized),
        "sectionKeywordHitCount": len(hints),
        "sectionKeywords": hints,
        "noiseKeywordHitCount": count_noise_hits(normalized),
        "criticalSectionExists": critical["exists"],
        "criticalSectionUsefulLineCount": critical["usefulLineCount"],
        "criticalSectionMeaningfulCharCount": critical["meaningfulCharCount"],
        "criticalSectionSuspectLineRatio": critical["suspectLineRatio"],
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
    # 핵심 업무 섹션 본문에 useful line 이 0 이고(짧거나 파편화) → PASS 를 REVIEW_REQUIRED 로 강등한다.
    # "짧음" 단독이 아니라 "유용한 업무 내용 부재 + (너무 짧거나 suspect 지배)" 로 정상 짧은 섹션을 보호한다.
    if (critical["exists"]
            and critical["usefulLineCount"] == 0
            and (critical["meaningfulCharCount"] < 30 or critical["suspectLineRatio"] >= 0.5)):
        warnings.append("critical_section_content_insufficient")
        if status == "PASS":
            status = "REVIEW_REQUIRED"
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
        return strip_site_noise(text), "PDF_TEXT", next_warnings
    if suffix in PDF_SUFFIXES | IMAGE_SUFFIXES:
        text = existing_ocr_text(input_path, existing_ocr_dir)
        if text:
            return strip_site_noise(normalize_text(text)), "EXISTING_OCR", warnings
        next_warnings = list(warnings)
        # 1순위: 레이아웃 인식 OCR(PPStructureV3) — 표·2단 공고의 읽기 순서를 복원한다.
        if options.enable_ppstructure:
            try:
                text = ppstructure_text(input_path, options.paddle_ocr_lang)
                if text:
                    return text, "PPSTRUCTURE", next_warnings
                next_warnings.append("ppstructure_empty_result")
            except Exception as exc:
                next_warnings.append("ppstructure_failed")
                next_warnings.append(f"ppstructure_error:{exc.__class__.__name__}")
        # 2순위(폴백): 기본 line-OCR. 레이아웃 인식 실패/빈 결과 시 사용.
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
        "ocr": "existing_output_or_ppstructurev3",
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
