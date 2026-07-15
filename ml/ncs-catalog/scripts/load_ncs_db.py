"""정규화 NCS JSONL을 한 트랜잭션에서 ``ncs_classification`` 전체 스냅샷으로 교체한다.

``--dry-run``은 JSONL 계약과 중복을 검증할 뿐 DB driver를 import하거나 연결하지 않는다.
DB 설정은 ``DB_HOST``, ``DB_PORT``, ``DB_NAME``, ``DB_USERNAME``, ``DB_PASSWORD`` 또는
같은 이름의 CLI 인자에서 받는다.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any, Iterable, Mapping, Sequence

from db_cli import add_db_arguments, resolve_db_config, validate_snapshot_replace
from ncs_contract import bounded_text, classification_codes_from_record, code_text

SQL = """INSERT INTO ncs_classification
 (ncs_code,major_code,major_name,middle_code,middle_name,minor_code,minor_name,sub_code,sub_name,
  unit_count,element_count,min_level,max_level,search_text,detail_json)
 VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)"""
MIN_NCS_CLASSIFICATIONS = 1_000


def build_row(record: Mapping[str, Any]) -> tuple[Any, ...]:
    try:
        major = record["major"]
        middle = record["middle"]
        minor = record["minor"]
        sub = record["sub"]
        units = record["units"]
    except (KeyError, TypeError) as exc:
        raise ValueError(f"invalid NCS record: missing {exc}") from exc
    if not isinstance(units, list):
        raise ValueError("units must be a list")

    ncs_code, sub_code = classification_codes_from_record(record)
    major_code = code_text(major.get("code"), "major.code")
    middle_code = code_text(middle.get("code"), "middle.code")
    minor_code = code_text(minor.get("code"), "minor.code")
    major_name = bounded_text(major.get("name"), "major.name", 100)
    middle_name = bounded_text(middle.get("name"), "middle.name", 100)
    minor_name = bounded_text(minor.get("name"), "minor.name", 100)
    sub_name = bounded_text(sub.get("name"), "sub.name", 200)
    levels: list[int] = []
    element_count = 0
    search_parts = [str(sub.get("name") or "")]
    for unit_index, unit in enumerate(units, start=1):
        if not isinstance(unit, Mapping):
            raise ValueError(f"unit {unit_index} must be an object: {ncs_code}")
        level = unit.get("level")
        if level is not None:
            try:
                levels.append(int(level))
            except (ValueError, TypeError):
                pass
        search_parts.append(str(unit.get("unitName") or ""))
        elements = unit.get("elements") or []
        if not isinstance(elements, list):
            raise ValueError(f"elements must be a list: {ncs_code}")
        for element_index, element in enumerate(elements, start=1):
            if not isinstance(element, Mapping):
                raise ValueError(
                    f"unit {unit_index} element {element_index} must be an object: {ncs_code}"
                )
            element_count += 1
            search_parts.append(str(element.get("elementName") or ""))
            skills = element.get("skills") or []
            if not isinstance(skills, list):
                raise ValueError(
                    f"unit {unit_index} element {element_index} skills must be a list: {ncs_code}"
                )
            search_parts.extend(str(value) for value in skills)

    return (
        ncs_code,
        major_code,
        major_name,
        middle_code,
        middle_name,
        minor_code,
        minor_name,
        sub_code,
        sub_name,
        len(units),
        element_count,
        min(levels) if levels else None,
        max(levels) if levels else None,
        " ".join(part for part in search_parts if part),
        json.dumps(units, ensure_ascii=False),
    )


def read_records(path: Path) -> list[dict[str, Any]]:
    records = []
    with path.open(encoding="utf-8") as stream:
        for line_number, line in enumerate(stream, start=1):
            if not line.strip():
                continue
            try:
                value = json.loads(line)
            except json.JSONDecodeError as exc:
                raise ValueError(f"{path}:{line_number}: invalid JSON: {exc.msg}") from exc
            if not isinstance(value, dict):
                raise ValueError(f"{path}:{line_number}: record must be a JSON object")
            records.append(value)
    if not records:
        raise ValueError(f"no NCS records found: {path}")
    return records


def build_rows(records: Iterable[Mapping[str, Any]]) -> list[tuple[Any, ...]]:
    rows = []
    seen: set[str] = set()
    for index, record in enumerate(records, start=1):
        try:
            row = build_row(record)
        except ValueError as exc:
            raise ValueError(f"record {index}: {exc}") from exc
        ncs_code = row[0]
        if ncs_code in seen:
            raise ValueError(f"record {index}: duplicate ncs_code: {ncs_code}")
        seen.add(ncs_code)
        rows.append(row)
    return rows


def validation_summary(rows: Sequence[tuple[Any, ...]], dry_run: bool) -> dict[str, Any]:
    return {
        "dryRun": dry_run,
        "classifications": len(rows),
        "units": sum(int(row[9]) for row in rows),
        "elements": sum(int(row[10]) for row in rows),
        "firstNcsCode": rows[0][0] if rows else None,
        "lastNcsCode": rows[-1][0] if rows else None,
    }


def replace_ncs_snapshot(
    connection: Any,
    rows: Sequence[tuple[Any, ...]],
    batch_size: int,
) -> dict[str, int]:
    """현재 NCS 스냅샷을 한 트랜잭션에서 완전히 교체하고 건수를 검증한다."""
    with connection.cursor() as cursor:
        cursor.execute("DELETE FROM ncs_classification")
        for offset in range(0, len(rows), batch_size):
            cursor.executemany(SQL, rows[offset:offset + batch_size])
        cursor.execute(
            "SELECT COUNT(*), COALESCE(SUM(unit_count), 0), "
            "COALESCE(SUM(element_count), 0) FROM ncs_classification"
        )
        count, units, elements = cursor.fetchone()

    expected = (len(rows), sum(int(row[9]) for row in rows), sum(int(row[10]) for row in rows))
    actual = (int(count), int(units), int(elements))
    if actual != expected:
        raise RuntimeError(f"NCS snapshot verification failed: expected={expected}, actual={actual}")
    return {"dbClassifications": actual[0], "dbUnits": actual[1], "dbElements": actual[2]}


def load_rows_to_db(
    rows: Sequence[tuple[Any, ...]],
    config: Mapping[str, Any],
    batch_size: int,
    expected_classifications: int,
) -> None:
    validate_snapshot_replace(
        actual_count=len(rows),
        expected_count=expected_classifications,
        minimum_count=MIN_NCS_CLASSIFICATIONS,
        label="NCS 세분류",
    )
    try:
        import pymysql
    except ImportError as exc:
        raise RuntimeError("DB 적재에는 pymysql이 필요합니다") from exc

    connection = pymysql.connect(**dict(config))
    try:
        result = replace_ncs_snapshot(connection, rows, batch_size)
        connection.commit()
        print(json.dumps(result, ensure_ascii=False))
    except Exception:
        connection.rollback()
        raise
    finally:
        connection.close()


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("legacy_input", nargs="?", help=argparse.SUPPRESS)
    parser.add_argument("--input", help="정규화 JSONL 경로")
    parser.add_argument("--dry-run", action="store_true", help="DB 연결 없이 계약만 검증")
    parser.add_argument(
        "--confirm-replace-all",
        action="store_true",
        help="기존 NCS 카탈로그 전체 교체를 명시적으로 승인",
    )
    parser.add_argument(
        "--expected-classifications",
        type=int,
        help="직전 dry-run에서 확인한 예상 세분류 건수",
    )
    parser.add_argument("--batch-size", type=int, default=20)
    add_db_arguments(parser)
    return parser


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    input_value = args.input or args.legacy_input
    if not input_value:
        parser.error("--input이 필요합니다")
    if args.batch_size < 1:
        parser.error("--batch-size는 1 이상이어야 합니다")
    path = Path(input_value).expanduser()
    if not path.is_file():
        parser.error(f"JSONL을 찾을 수 없습니다: {path}")

    rows = build_rows(read_records(path))
    summary = validation_summary(rows, args.dry_run)
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    if args.dry_run:
        return 0

    if not args.confirm_replace_all:
        parser.error("실제 적재에는 --confirm-replace-all이 필요합니다")
    try:
        validate_snapshot_replace(
            actual_count=len(rows),
            expected_count=args.expected_classifications,
            minimum_count=MIN_NCS_CLASSIFICATIONS,
            label="NCS 세분류",
        )
    except ValueError as exc:
        parser.error(str(exc))

    load_rows_to_db(
        rows,
        resolve_db_config(args, parser),
        args.batch_size,
        args.expected_classifications,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
