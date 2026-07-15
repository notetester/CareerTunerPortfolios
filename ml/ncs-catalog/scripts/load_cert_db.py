"""국가·민간 자격증 카탈로그와 국가전문자격 일정을 DB에 적재한다.

개인 파일 경로는 ``--private-cert-csv`` 또는 ``NCS_PRIVATE_CERT_CSV``에서 받고,
DB 설정은 표준 ``DB_*`` 환경변수/CLI 인자에서 받는다. ``--dry-run``은 입력 파일만
검증하고 DB driver를 import하거나 연결하지 않는다. 선택적인 국가기술자격 상세 CSV는
설명과 검색 텍스트를 개요·수행직무·진로·취득방법·변천과정으로 보강한다.
"""

from __future__ import annotations

import argparse
import csv
import json
import os
import re
import sys
from collections import defaultdict
from pathlib import Path
from typing import Any, Mapping

from db_cli import add_db_arguments, resolve_db_config, validate_snapshot_replace

SUFFIX = re.compile(r"(기술사|기능장|산업기사|기사|기능사|기술자|관리사|상담사|지도사|평가사)$")
CERT_INSERT_SQL = """INSERT INTO certificate
 (cert_type,name,grade,authority,issuer_org,series,jm_cd,reg_no,official,status,description,ncs_sub_name,has_schedule,search_text)
 VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)"""
SCHEDULE_INSERT_SQL = """INSERT INTO certificate_exam_schedule
 (cert_name,year,round_name,doc_reg_start,doc_reg_end,doc_exam,doc_pass,prac_exam_start,prac_exam_end,prac_pass)
 VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)"""
MIN_CERTIFICATES = 600
MIN_SCHEDULES = 60

def default_resources_dir() -> Path:
    return Path(__file__).resolve().parents[3] / "backend" / "src" / "main" / "resources" / "cert"


def default_national_detail_path() -> Path:
    return (
        Path(__file__).resolve().parents[3]
        / "docs"
        / "ai-artifacts"
        / "archive"
        / "public-cert-20251231"
        / "national-tech-detail-20251231.csv"
    )


NATIONAL_DETAIL = os.getenv("NCS_NATIONAL_DETAIL_CSV", str(default_national_detail_path()))


class _EnvironmentDbConfig(Mapping[str, Any]):
    """기존 보강 스크립트의 ``DB`` import를 표준 환경변수 계약으로 연결한다."""

    def __init__(self) -> None:
        self._resolved: dict[str, Any] | None = None

    def _config(self) -> dict[str, Any]:
        if self._resolved is None:
            parser = argparse.ArgumentParser(add_help=False)
            add_db_arguments(parser)
            self._resolved = resolve_db_config(parser.parse_args([]), parser)
        return self._resolved

    def __getitem__(self, key: str) -> Any:
        return self._config()[key]

    def __iter__(self):
        return iter(self._config())

    def __len__(self) -> int:
        return len(self._config())


# ``enrich_national_cert_detail.py`` 호환용. 값은 import 시 고정하지 않고 실제 사용 시 검증한다.
DB: Mapping[str, Any] = _EnvironmentDbConfig()


# 설명에 넣을 상세 섹션(항목명, 표시 헤더) — 순서대로.
# '법령상 우대현황'은 ▣/□ 법령 인용 수백 건의 미가공 장문 벽(원본 PDF)이라 제외 —
# 사용자 요청(개요·수행직무·진로·취득방법)에 없고 가독성을 크게 해쳐 설명에서 뺀다.
DETAIL_SECTIONS = [
    ("개요", "개요"),
    ("수행직무", "수행직무"),
    ("진로 및 전망", "진로 및 전망"),
    ("취득방법", "취득방법"),
    ("변천과정", "변천과정"),
]
_WS = re.compile(r"\s+")
_CIRCLED = re.compile(r"\s*([①-⑳])")


def _clean(value: Any) -> str:
    """PDF 추출 잔재(연속 공백/개행/nbsp)를 단일 공백으로 정리."""
    return _WS.sub(" ", str(value or "").replace(" ", " ")).strip()


def _sectionize(value: str) -> str:
    """원문자 번호(①②…) 앞에 줄바꿈을 넣어 목록으로 보이게 한다(취득방법 등).
    원본은 '한국산업인력공단② 관련학과'처럼 마커가 앞 글자에 붙어 목록으로 안 읽힌다."""
    return _CIRCLED.sub(r"\n\1 ", value).strip()


def load_national_details(path: str | Path | None = None) -> dict[str, dict[str, str]]:
    """tall CSV(종목명,항목,내용) → {종목명: {항목: 정리된 내용}}. 파일 없으면 빈 dict(선택 리소스)."""
    detail_path = Path(path or NATIONAL_DETAIL).expanduser()
    details: defaultdict[str, dict[str, str]] = defaultdict(dict)
    try:
        with detail_path.open(encoding="utf-8-sig", newline="") as stream:
            for row in csv.DictReader(stream):
                name = (row.get("종목명") or "").strip()
                item = (row.get("항목") or "").strip()
                value = _clean(row.get("내용"))
                if name and item and value:
                    details[name][item] = value
    except FileNotFoundError:
        print(f"[warn] 국가기술 상세 CSV 없음(설명 보강 생략): {detail_path}", file=sys.stderr)
    return dict(details)


def build_national_desc(
    category: str,
    series: str,
    ncs_name: str | None,
    details: Mapping[str, str],
) -> str:
    """국가자격 rich 설명 = 계열 + (개요·수행직무·진로·취득방법·변천과정·우대현황) + NCS 직무 + 시행기관.
    det 가 비면(CSV 미수록 종목) 기존 thin 설명과 동일하게 degrade."""
    parts = [f"[{category}] 계열: {series}."]
    for key, header in DETAIL_SECTIONS:
        value = details.get(key)
        if value:
            parts.append(f"■ {header}\n{_sectionize(value)}")
    if ncs_name:
        parts.append(f"관련 NCS 직무: {ncs_name}.")
    org = details.get("실시기관명")
    home = details.get("실시기관 홈페이지")
    if org:
        parts.append(f"시행: {org}" + (f" ({home})" if home else ""))
    return "\n\n".join(parts)


def national_search_text(
    name: str,
    series: str,
    category: str,
    ncs_name: str | None,
    details: Mapping[str, str],
) -> str:
    """검색 인덱스 — 개요·수행직무·진로 키워드까지 포함해 내용 기반 검색 지원."""
    extra = " ".join(filter(None, [
        details.get("개요", "")[:400],
        details.get("수행직무", "")[:400],
        details.get("진로 및 전망", "")[:300],
    ]))
    return f"{name} {series} {category} {ncs_name or ''} {extra}".strip()

def ncs_index(connection: Any) -> dict[str, str]:
    """NCS 세분류명/능력단위명 → sub_name 역색인(best-effort 매칭용)."""
    index: dict[str, str] = {}
    with connection.cursor() as cursor:
        cursor.execute("SELECT sub_name, detail_json FROM ncs_classification")
        for sub_name, detail in cursor.fetchall():
            index.setdefault(sub_name.replace(" ", ""), sub_name)
            try:
                for unit in json.loads(detail):
                    unit_name = (unit.get("unitName") or "").replace(" ", "")
                    if unit_name:
                        index.setdefault(unit_name, sub_name)
            except (TypeError, json.JSONDecodeError):
                pass
    return index

def match_ncs(name: str, index: Mapping[str, str]) -> str | None:
    """자격증명과 NCS 세분류명·능력단위명의 정규화된 정확 일치만 인정한다."""
    key = SUFFIX.sub("", name).replace(" ", "")
    if len(key) < 2:
        return None
    return index.get(key)


def required_file(path: Path, label: str) -> Path:
    if not path.is_file():
        raise ValueError(f"{label} 파일을 찾을 수 없습니다: {path}")
    return path


def collect_certificates(
    resources_dir: Path,
    private_cert_csv: Path,
    index: Mapping[str, str],
    national_details: Mapping[str, Mapping[str, str]] | None = None,
) -> tuple[list[tuple[Any, ...]], dict[str, int]]:
    jmcd_path = required_file(resources_dir / "national-tech-jmcd-20260711.csv", "국가기술자격 종목코드")
    catalog_path = required_file(
        resources_dir / "national-qualification-catalog-20251231.csv", "국가자격 카탈로그"
    )
    required_file(private_cert_csv, "민간자격 등록정보")

    jmcd: dict[str, str] = {}
    with jmcd_path.open(encoding="utf-8-sig", newline="") as stream:
        for row in csv.DictReader(stream):
            jmcd[row["종목명"].strip()] = row["jmCd"].strip()

    certificates: list[tuple[Any, ...]] = []
    matched = 0
    enriched = 0
    national = 0
    detail_index = national_details or {}
    with catalog_path.open(encoding="utf-8-sig", newline="") as stream:
        for row in csv.DictReader(stream):
            name = row["종목명"].strip()
            series = row["계열명"].strip()
            category = row["자격구분명"].strip()
            cert_type = "NATIONAL_TECH" if row["자격구분코드"].strip() == "T" else "NATIONAL_PROF"
            ncs_name = match_ncs(name, index)
            if ncs_name:
                matched += 1
            details = detail_index.get(name, {})
            if details:
                enriched += 1
            description = build_national_desc(category, series, ncs_name, details)
            certificates.append(
                (
                    cert_type,
                    name,
                    None,
                    "한국산업인력공단",
                    "한국산업인력공단",
                    series,
                    jmcd.get(name),
                    None,
                    None,
                    "ACTIVE",
                    description,
                    ncs_name,
                    0,
                    national_search_text(name, series, category, ncs_name, details),
                )
            )
            national += 1

    private = 0
    with private_cert_csv.open(encoding="cp949", newline="") as stream:
        for row in csv.reader(stream):
            if len(row) < 11 or row[1] != "등록완료":
                continue
            _, status, ministry, reg_no, name, grade, org, overview, job_content, official, _ = row[:11]
            description = (overview or "").strip()
            if job_content:
                description += "\n\n[직무내용] " + job_content.strip()
            certificates.append(
                (
                    "PRIVATE",
                    name.strip(),
                    grade.strip() or None,
                    ministry.strip() or None,
                    org.strip() or None,
                    None,
                    None,
                    reg_no.strip() or None,
                    official.strip() or None,
                    status,
                    description or None,
                    None,
                    0,
                    f"{name} {grade} {ministry} {overview[:200]} {job_content[:200]}",
                )
            )
            private += 1

    return certificates, {
        "national": national,
        "private": private,
        "ncsMatched": matched,
        "nationalDetailEnriched": enriched,
    }


def collect_schedules(resources_dir: Path) -> list[tuple[Any, ...]]:
    schedule_path = required_file(
        resources_dir / "national-prof-schedule-2026-preannounced.json", "국가전문자격 일정"
    )
    with schedule_path.open(encoding="utf-8") as stream:
        schedule = json.load(stream)
    year = schedule["year"]
    rows = []
    for cert_name, rounds in schedule["byName"].items():
        for round_info in rounds:
            rows.append(
                (
                    cert_name,
                    year,
                    round_info.get("round"),
                    round_info.get("docRegStart"),
                    round_info.get("docRegEnd"),
                    round_info.get("docExam"),
                    round_info.get("docPass"),
                    round_info.get("pracExamStart"),
                    round_info.get("pracExamEnd"),
                    round_info.get("pracPass"),
                )
            )
    return rows


def replace_certificate_snapshot(
    connection: Any,
    certificates: list[tuple[Any, ...]],
    schedules: list[tuple[Any, ...]],
    batch_size: int,
) -> dict[str, int]:
    """자격증·일정 스냅샷을 한 트랜잭션에서 교체하고 최종 건수를 검증한다."""
    with connection.cursor() as cursor:
        # TRUNCATE는 암묵적 commit이므로 사용하지 않는다. DELETE + INSERT 전체가 rollback 가능하다.
        cursor.execute("DELETE FROM certificate_exam_schedule")
        cursor.execute("DELETE FROM certificate")
        for offset in range(0, len(certificates), batch_size):
            cursor.executemany(CERT_INSERT_SQL, certificates[offset:offset + batch_size])
        if schedules:
            cursor.executemany(SCHEDULE_INSERT_SQL, schedules)
        cursor.execute(
            """UPDATE certificate c SET has_schedule=1
               WHERE EXISTS (SELECT 1 FROM certificate_exam_schedule s WHERE s.cert_name=c.name)"""
        )
        cursor.execute("SELECT COUNT(*) FROM certificate")
        certificate_count = int(cursor.fetchone()[0])
        cursor.execute("SELECT COUNT(*) FROM certificate_exam_schedule")
        schedule_count = int(cursor.fetchone()[0])

    expected = (len(certificates), len(schedules))
    actual = (certificate_count, schedule_count)
    if actual != expected:
        raise RuntimeError(
            f"certificate snapshot verification failed: expected={expected}, actual={actual}"
        )
    return {"dbCertificates": actual[0], "dbSchedules": actual[1]}


def load_to_db(
    config: Mapping[str, Any],
    resources_dir: Path,
    private_cert_csv: Path,
    batch_size: int,
    expected_certificates: int,
    expected_schedules: int,
    national_details: Mapping[str, Mapping[str, str]] | None = None,
) -> dict[str, Any]:
    try:
        import pymysql
    except ImportError as exc:
        raise RuntimeError("DB 적재에는 pymysql이 필요합니다") from exc

    connection = pymysql.connect(**dict(config))
    try:
        certificates, stats = collect_certificates(
            resources_dir,
            private_cert_csv,
            ncs_index(connection),
            national_details,
        )
        schedules = collect_schedules(resources_dir)
        validate_snapshot_replace(
            actual_count=len(certificates),
            expected_count=expected_certificates,
            minimum_count=MIN_CERTIFICATES,
            label="자격증",
        )
        validate_snapshot_replace(
            actual_count=len(schedules),
            expected_count=expected_schedules,
            minimum_count=MIN_SCHEDULES,
            label="자격증 일정",
        )
        db_counts = replace_certificate_snapshot(
            connection, certificates, schedules, batch_size
        )
        connection.commit()
        return {
            **stats,
            "certificates": len(certificates),
            "schedules": len(schedules),
            **db_counts,
        }
    except Exception:
        connection.rollback()
        raise
    finally:
        connection.close()


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--resources-dir",
        default=os.getenv("NCS_CERT_RESOURCE_DIR", str(default_resources_dir())),
        help="backend cert resource 디렉터리",
    )
    parser.add_argument(
        "--private-cert-csv",
        default=os.getenv("NCS_PRIVATE_CERT_CSV"),
        help="민간자격 등록정보 CSV(cp949)",
    )
    parser.add_argument(
        "--national-detail-csv",
        default=NATIONAL_DETAIL,
        help="국가기술자격 종목별 상세 tall CSV(없으면 설명 보강만 생략)",
    )
    parser.add_argument("--dry-run", action="store_true", help="DB 연결 없이 입력 파일만 검증")
    parser.add_argument(
        "--confirm-replace-all",
        action="store_true",
        help="기존 자격증·일정 카탈로그 전체 교체를 명시적으로 승인",
    )
    parser.add_argument(
        "--expected-certificates",
        type=int,
        help="직전 dry-run에서 확인한 예상 자격증 건수",
    )
    parser.add_argument(
        "--expected-schedules",
        type=int,
        help="직전 dry-run에서 확인한 예상 일정 건수",
    )
    parser.add_argument("--batch-size", type=int, default=200)
    add_db_arguments(parser)
    return parser


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    if not args.private_cert_csv:
        parser.error("--private-cert-csv 또는 NCS_PRIVATE_CERT_CSV가 필요합니다")
    if args.batch_size < 1:
        parser.error("--batch-size는 1 이상이어야 합니다")
    resources_dir = Path(args.resources_dir).expanduser()
    private_cert_csv = Path(args.private_cert_csv).expanduser()

    if args.dry_run:
        national_details = load_national_details(args.national_detail_csv)
        certificates, stats = collect_certificates(
            resources_dir,
            private_cert_csv,
            {},
            national_details,
        )
        schedules = collect_schedules(resources_dir)
        print(
            json.dumps(
                {
                    "dryRun": True,
                    **stats,
                    "certificates": len(certificates),
                    "schedules": len(schedules),
                },
                ensure_ascii=False,
                indent=2,
            )
        )
        return 0


    if not args.confirm_replace_all:
        parser.error("실제 적재에는 --confirm-replace-all이 필요합니다")
    if args.expected_certificates is None or args.expected_schedules is None:
        parser.error(
            "실제 적재에는 --expected-certificates와 --expected-schedules가 필요합니다"
        )

    national_details = load_national_details(args.national_detail_csv)
    result = load_to_db(
        resolve_db_config(args, parser),
        resources_dir,
        private_cert_csv,
        args.batch_size,
        args.expected_certificates,
        args.expected_schedules,
        national_details,
    )
    print(json.dumps({"dryRun": False, **result}, ensure_ascii=False, indent=2))
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
