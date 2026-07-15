from __future__ import annotations

import argparse
import copy
import csv
import io
import json
import tempfile
import unittest
from collections import OrderedDict
from contextlib import redirect_stdout
from pathlib import Path
from unittest.mock import patch

import load_cert_db
import load_ncs_db
import ncs_normalize
from db_cli import add_db_arguments, resolve_db_config, validate_snapshot_replace


class SnapshotCursor:
    def __init__(self, connection):
        self.connection = connection
        self.result = None

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, traceback):
        return False

    def execute(self, sql):
        compact = " ".join(sql.split()).upper()
        if compact == "DELETE FROM NCS_CLASSIFICATION":
            self.connection.ncs_rows = []
        elif compact == "DELETE FROM CERTIFICATE_EXAM_SCHEDULE":
            self.connection.schedule_rows = []
        elif compact == "DELETE FROM CERTIFICATE":
            self.connection.certificate_rows = []
        elif compact.startswith("UPDATE CERTIFICATE"):
            return
        elif "FROM NCS_CLASSIFICATION" in compact and compact.startswith("SELECT COUNT"):
            self.result = (
                len(self.connection.ncs_rows),
                sum(int(row[9]) for row in self.connection.ncs_rows),
                sum(int(row[10]) for row in self.connection.ncs_rows),
            )
        elif compact == "SELECT COUNT(*) FROM CERTIFICATE":
            self.result = (len(self.connection.certificate_rows),)
        elif compact == "SELECT COUNT(*) FROM CERTIFICATE_EXAM_SCHEDULE":
            self.result = (len(self.connection.schedule_rows),)
        else:
            raise AssertionError(f"unexpected SQL: {compact}")

    def executemany(self, sql, rows):
        compact = " ".join(sql.split()).upper()
        if compact.startswith("INSERT INTO NCS_CLASSIFICATION"):
            self.connection.ncs_rows.extend(rows)
        elif compact.startswith("INSERT INTO CERTIFICATE_EXAM_SCHEDULE"):
            self.connection.schedule_rows.extend(rows)
        elif compact.startswith("INSERT INTO CERTIFICATE"):
            self.connection.certificate_rows.extend(rows)
        else:
            raise AssertionError(f"unexpected batch SQL: {compact}")

    def fetchone(self):
        if self.result is None:
            raise AssertionError("fetchone called without a SELECT result")
        return self.result


class SnapshotConnection:
    def __init__(self):
        self.ncs_rows = [("legacy-double-composite",) + (None,) * 14]
        self.certificate_rows = [("legacy-certificate",)]
        self.schedule_rows = [("legacy-schedule",)]

    def cursor(self):
        return SnapshotCursor(self)


class FakeWorksheet:
    def __init__(self, rows):
        self._rows = rows

    def iter_rows(self, values_only=True):
        if not values_only:
            raise AssertionError("fixture supports values_only only")
        return iter(self._rows)


def sample_record() -> dict:
    header = tuple(f"column-{index}" for index in range(20))
    knowledge_row = (
        "01", "사업관리", "0101", "사업관리", "010101", "프로젝트관리",
        "01010101", "프로젝트관리", "0101010101", "프로젝트전략기획", 5,
        "010101010101", "전략수립", 5, "1", "계획을 수립한다", "K1", "지식", "1", "프로젝트 지식",
    )
    skill_row = list(knowledge_row)
    skill_row[17] = "기술"
    skill_row[18] = "2"
    skill_row[19] = "일정 분석"
    accumulator = OrderedDict()
    ncs_normalize.norm_sheet(FakeWorksheet([header, knowledge_row, tuple(skill_row)]), accumulator)
    return ncs_normalize.to_record(next(iter(accumulator.values())))


class NcsContractTest(unittest.TestCase):
    def test_db_config_requires_tls_by_default_and_supports_explicit_local_disable(self):
        parser = argparse.ArgumentParser()
        add_db_arguments(parser)
        required = resolve_db_config(
            parser.parse_args(
                [
                    "--db-host", "db.example",
                    "--db-name", "careertuner",
                    "--db-user", "loader",
                    "--db-password", "secret",
                ]
            ),
            parser,
        )
        self.assertEqual(
            {"check_hostname": False, "verify_mode": False},
            required["ssl"],
        )

        disabled = resolve_db_config(
            parser.parse_args(
                [
                    "--db-host", "127.0.0.1",
                    "--db-name", "fixture",
                    "--db-user", "root",
                    "--db-password", "root",
                    "--db-ssl-mode", "DISABLED",
                ]
            ),
            parser,
        )
        self.assertTrue(disabled["ssl_disabled"])

    def test_verified_tls_requires_ca_and_identity_enables_hostname_check(self):
        parser = argparse.ArgumentParser()
        add_db_arguments(parser)
        common = [
            "--db-host", "db.example",
            "--db-name", "careertuner",
            "--db-user", "loader",
            "--db-password", "secret",
        ]
        with self.assertRaises(SystemExit):
            resolve_db_config(
                parser.parse_args([*common, "--db-ssl-mode", "VERIFY_IDENTITY"]),
                parser,
            )

        verified = resolve_db_config(
            parser.parse_args(
                [
                    *common,
                    "--db-ssl-mode", "VERIFY_IDENTITY",
                    "--db-ssl-ca", "ca.pem",
                ]
            ),
            parser,
        )
        self.assertEqual(
            {"ca": "ca.pem", "check_hostname": True, "verify_mode": True},
            verified["ssl"],
        )

    def test_normalizer_writes_leaf_sub_code_and_loader_composes_once(self):
        record = sample_record()
        self.assertEqual("01010101", record["sub"]["code"])

        row = load_ncs_db.build_row(record)
        self.assertEqual("01-0101-010101-01010101", row[0])
        self.assertEqual("01010101", row[7])
        self.assertEqual(1, row[9])
        self.assertEqual(1, row[10])
        self.assertIn("일정 분석", row[13])

    def test_loader_accepts_previous_composite_sub_code(self):
        record = sample_record()
        record["sub"]["code"] = "01-0101-010101-01010101"

        row = load_ncs_db.build_row(record)
        self.assertEqual("01-0101-010101-01010101", row[0])
        self.assertEqual("01010101", row[7])

    def test_loader_rejects_mismatched_legacy_composite(self):
        record = sample_record()
        record["sub"]["code"] = "99-9999-999999-01010101"
        with self.assertRaisesRegex(ValueError, "does not match parent codes"):
            load_ncs_db.build_row(record)

    def test_loader_dry_run_never_calls_db(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "ncs.jsonl"
            source.write_text(json.dumps(sample_record(), ensure_ascii=False) + "\n", encoding="utf-8")
            output = io.StringIO()
            with patch.object(load_ncs_db, "load_rows_to_db", side_effect=AssertionError("DB called")):
                with redirect_stdout(output):
                    result = load_ncs_db.main(["--input", str(source), "--dry-run"])
            self.assertEqual(0, result)
            summary = json.loads(output.getvalue())
            self.assertTrue(summary["dryRun"])
            self.assertEqual(1, summary["classifications"])
            self.assertEqual("01-0101-010101-01010101", summary["firstNcsCode"])

    def test_duplicate_ncs_code_is_rejected_before_db(self):
        record = sample_record()
        with self.assertRaisesRegex(ValueError, "duplicate ncs_code"):
            load_ncs_db.build_rows([record, copy.deepcopy(record)])

    def test_required_names_and_db_widths_fail_before_db(self):
        missing_name = sample_record()
        missing_name["major"]["name"] = None
        with self.assertRaisesRegex(ValueError, "major.name is required"):
            load_ncs_db.build_rows([missing_name])

        long_name = sample_record()
        long_name["sub"]["name"] = "가" * 201
        with self.assertRaisesRegex(ValueError, "sub.name exceeds 200"):
            load_ncs_db.build_rows([long_name])

    def test_snapshot_replace_removes_legacy_rows_and_is_rerun_safe(self):
        connection = SnapshotConnection()
        rows = load_ncs_db.build_rows([sample_record()])

        first = load_ncs_db.replace_ncs_snapshot(connection, rows, batch_size=20)
        second = load_ncs_db.replace_ncs_snapshot(connection, rows, batch_size=20)

        self.assertEqual(first, second)
        self.assertEqual([rows[0][0]], [row[0] for row in connection.ncs_rows])

    def test_destructive_replace_requires_expected_count_and_safe_minimum(self):
        with self.assertRaisesRegex(ValueError, "예상 건수가 필요"):
            validate_snapshot_replace(
                actual_count=1109,
                expected_count=None,
                minimum_count=1000,
                label="NCS 세분류",
            )
        with self.assertRaisesRegex(ValueError, "예상 건수 불일치"):
            validate_snapshot_replace(
                actual_count=1,
                expected_count=1109,
                minimum_count=1000,
                label="NCS 세분류",
            )
        with self.assertRaisesRegex(ValueError, "안전 하한 미달"):
            validate_snapshot_replace(
                actual_count=1,
                expected_count=1,
                minimum_count=1000,
                label="NCS 세분류",
            )

    def test_ncs_cli_rejects_unconfirmed_or_small_replace_before_db(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "ncs.jsonl"
            source.write_text(json.dumps(sample_record(), ensure_ascii=False) + "\n", encoding="utf-8")
            with patch.object(load_ncs_db, "load_rows_to_db", side_effect=AssertionError("DB called")):
                with self.assertRaises(SystemExit):
                    load_ncs_db.main(["--input", str(source)])
                with self.assertRaises(SystemExit):
                    load_ncs_db.main(
                        [
                            "--input", str(source),
                            "--confirm-replace-all",
                            "--expected-classifications", "1",
                        ]
                    )


class CertificateDryRunTest(unittest.TestCase):
    def test_certificate_dry_run_uses_cli_paths_without_db(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            resources = root / "cert"
            resources.mkdir()
            (resources / "national-tech-jmcd-20260711.csv").write_text(
                "종목명,jmCd\n정보처리기사,1320\n", encoding="utf-8-sig"
            )
            (resources / "national-qualification-catalog-20251231.csv").write_text(
                "자격구분코드,자격구분명,계열명,종목명\n"
                "T,국가기술자격,정보통신,정보처리기사\n",
                encoding="utf-8-sig",
            )
            (resources / "national-prof-schedule-2026-preannounced.json").write_text(
                json.dumps(
                    {
                        "year": 2026,
                        "byName": {"사회복지사": [{"round": "1회", "docExam": "2026-01-01"}]},
                    },
                    ensure_ascii=False,
                ),
                encoding="utf-8",
            )
            private_csv = root / "private.csv"
            with private_csv.open("w", encoding="cp949", newline="") as stream:
                writer = csv.writer(stream)
                writer.writerow(["번호", "상태", "부처", "등록번호", "자격명", "등급", "기관", "개요", "직무", "공인", "기타"])
                writer.writerow(["1", "등록완료", "고용노동부", "2026-1", "테스트자격", "1급", "테스트기관", "개요", "직무", "미공인", ""])

            output = io.StringIO()
            with patch.object(load_cert_db, "load_to_db", side_effect=AssertionError("DB called")):
                with redirect_stdout(output):
                    result = load_cert_db.main(
                        [
                            "--resources-dir", str(resources),
                            "--private-cert-csv", str(private_csv),
                            "--dry-run",
                        ]
                    )
            self.assertEqual(0, result)
            summary = json.loads(output.getvalue())
            self.assertTrue(summary["dryRun"])
            self.assertEqual(1, summary["national"])
            self.assertEqual(1, summary["private"])
            self.assertEqual(2, summary["certificates"])
            self.assertEqual(1, summary["schedules"])

    def test_certificate_snapshot_replace_is_rerun_safe(self):
        connection = SnapshotConnection()
        certificate = (
            "NATIONAL_TECH", "정보처리기사", None, "한국산업인력공단", "한국산업인력공단",
            "정보통신", "1320", None, None, "ACTIVE", "설명", None, 0, "검색",
        )
        schedule = ("정보처리기사", 2026, "1회", None, None, "20260101", None, None, None, None)

        first = load_cert_db.replace_certificate_snapshot(
            connection, [certificate], [schedule], batch_size=200
        )
        second = load_cert_db.replace_certificate_snapshot(
            connection, [certificate], [schedule], batch_size=200
        )

        self.assertEqual(first, second)
        self.assertEqual([certificate], connection.certificate_rows)
        self.assertEqual([schedule], connection.schedule_rows)

    def test_certificate_cli_requires_explicit_replace_counts_before_db(self):
        with tempfile.TemporaryDirectory() as directory:
            private_csv = Path(directory) / "private.csv"
            private_csv.write_text("", encoding="cp949")
            with patch.object(load_cert_db, "load_to_db", side_effect=AssertionError("DB called")):
                with self.assertRaises(SystemExit):
                    load_cert_db.main(["--private-cert-csv", str(private_csv)])
                with self.assertRaises(SystemExit):
                    load_cert_db.main(
                        [
                            "--private-cert-csv", str(private_csv),
                            "--confirm-replace-all",
                        ]
                    )


if __name__ == "__main__":
    unittest.main()
