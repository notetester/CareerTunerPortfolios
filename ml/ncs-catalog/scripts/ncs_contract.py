"""NCS 정규화 JSONL과 DB loader가 공유하는 분류 코드 계약."""

from __future__ import annotations

from typing import Any, Mapping


def code_text(value: Any, field: str) -> str:
    """Excel 숫자형을 안정적으로 문자열 코드로 바꾸고 빈 코드를 거부한다."""
    if value is None or isinstance(value, bool):
        raise ValueError(f"{field} is required")
    if isinstance(value, float) and value.is_integer():
        value = int(value)
    text = str(value).strip()
    if not text:
        raise ValueError(f"{field} is required")
    return text


def bounded_text(value: Any, field: str, max_length: int) -> str:
    """필수 문자열을 정규화하고 DB 컬럼 길이 계약을 적용한다."""
    text = code_text(value, field)
    if len(text) > max_length:
        raise ValueError(f"{field} exceeds {max_length} characters")
    return text


def classification_codes(
    major_code: Any,
    middle_code: Any,
    minor_code: Any,
    sub_code: Any,
) -> tuple[str, str]:
    """복합 ``ncs_code``와 레벨-local ``sub_code``를 반환한다.

    정본 계약은 ``sub.code``에 세분류 자체 코드만 두는 것이다. 2026-07-14 이전
    artifact는 ``sub.code``에 이미 ``대-중-소-세`` 복합 코드를 넣었으므로, 정확한
    상위 prefix와 일치하는 경우에만 leaf 코드로 복원한다. 다른 하이픈 형식은 조용히
    이중 조립하지 않고 거부한다.
    """
    major = bounded_text(major_code, "major.code", 20)
    middle = bounded_text(middle_code, "middle.code", 20)
    minor = bounded_text(minor_code, "minor.code", 20)
    sub = bounded_text(sub_code, "sub.code", 60)

    legacy_prefix = f"{major}-{middle}-{minor}-"
    if "-" in sub:
        if not sub.startswith(legacy_prefix):
            raise ValueError(
                f"sub.code legacy composite does not match parent codes: {sub!r}"
            )
        sub = sub[len(legacy_prefix):]
        if not sub or "-" in sub:
            raise ValueError(f"sub.code has an invalid legacy composite: {sub_code!r}")

    sub = bounded_text(sub, "sub.code", 20)
    ncs_code = f"{major}-{middle}-{minor}-{sub}"
    if len(ncs_code) > 60:
        raise ValueError("ncsCode exceeds 60 characters")
    return ncs_code, sub


def classification_codes_from_record(record: Mapping[str, Any]) -> tuple[str, str]:
    """정규화 record에서 검증된 ``(ncs_code, sub_code)``를 구한다."""
    try:
        ncs_code, sub_code = classification_codes(
            record["major"]["code"],
            record["middle"]["code"],
            record["minor"]["code"],
            record["sub"]["code"],
        )
    except (KeyError, TypeError) as exc:
        raise ValueError(f"invalid NCS classification record: missing {exc}") from exc

    declared = record.get("ncsCode", record.get("ncs_code"))
    if declared is not None and code_text(declared, "ncsCode") != ncs_code:
        raise ValueError(
            f"declared ncsCode does not match component codes: {declared!r} != {ncs_code!r}"
        )
    return ncs_code, sub_code
