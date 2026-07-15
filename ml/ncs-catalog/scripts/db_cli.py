"""DB loader 공통 CLI/environment 설정. import 시 연결하거나 driver를 로드하지 않는다."""

from __future__ import annotations

import argparse
import os
from typing import Any


DB_SSL_MODES = ("DISABLED", "REQUIRED", "VERIFY_CA", "VERIFY_IDENTITY")


def validate_snapshot_replace(
    *,
    actual_count: int,
    expected_count: int | None,
    minimum_count: int,
    label: str,
) -> None:
    """파괴적 전체 교체 전에 명시한 예상 건수와 안전 하한을 강제한다."""
    if expected_count is None:
        raise ValueError(f"{label} 전체 교체에는 예상 건수가 필요합니다")
    if actual_count != expected_count:
        raise ValueError(
            f"{label} 예상 건수 불일치: expected={expected_count}, actual={actual_count}"
        )
    if actual_count < minimum_count:
        raise ValueError(
            f"{label} 안전 하한 미달: minimum={minimum_count}, actual={actual_count}"
        )


def add_db_arguments(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--db-host", default=os.getenv("DB_HOST"))
    parser.add_argument("--db-port", type=int, default=int(os.getenv("DB_PORT", "3306")))
    parser.add_argument("--db-name", default=os.getenv("DB_NAME"))
    parser.add_argument("--db-user", default=os.getenv("DB_USERNAME"))
    parser.add_argument("--db-password", default=os.getenv("DB_PASSWORD"))
    parser.add_argument(
        "--db-ssl-mode",
        choices=DB_SSL_MODES,
        default=os.getenv("DB_SSL_MODE", "REQUIRED").upper(),
        help="MySQL TLS 정책(원격 기본 REQUIRED, 로컬에서만 DISABLED)",
    )
    parser.add_argument(
        "--db-ssl-ca",
        default=os.getenv("DB_SSL_CA"),
        help="VERIFY_CA/VERIFY_IDENTITY에서 사용할 CA PEM 경로",
    )


def resolve_db_config(args: argparse.Namespace, parser: argparse.ArgumentParser) -> dict[str, Any]:
    missing = [
        name
        for name, value in (
            ("DB_HOST/--db-host", args.db_host),
            ("DB_NAME/--db-name", args.db_name),
            ("DB_USERNAME/--db-user", args.db_user),
            ("DB_PASSWORD/--db-password", args.db_password),
        )
        if not value
    ]
    if missing:
        parser.error("DB 적재에는 다음 설정이 필요합니다: " + ", ".join(missing))
    config: dict[str, Any] = {
        "host": args.db_host,
        "port": args.db_port,
        "user": args.db_user,
        "password": args.db_password,
        "database": args.db_name,
        "charset": "utf8mb4",
    }
    if args.db_ssl_mode == "DISABLED":
        config["ssl_disabled"] = True
    elif args.db_ssl_mode == "REQUIRED":
        # truthy ssl mapping은 PyMySQL의 preferred fallback이 아니라 TLS 협상을 강제한다.
        config["ssl"] = {"check_hostname": False, "verify_mode": False}
    else:
        if not args.db_ssl_ca:
            parser.error(f"{args.db_ssl_mode}에는 DB_SSL_CA/--db-ssl-ca가 필요합니다")
        config["ssl"] = {
            "ca": args.db_ssl_ca,
            "check_hostname": args.db_ssl_mode == "VERIFY_IDENTITY",
            "verify_mode": True,
        }
    return config
