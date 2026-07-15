#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
patch_path="$repo_root/backend/src/main/resources/db/patches/20260714_c_ncs_code_contract.sql"
database="${NCS_PATCH_TEST_DB:-careertuner_ncs_patch_ci}"

if [[ ! "$database" =~ ^[A-Za-z0-9_]+$ ]]; then
  echo "Unsafe test database name: $database" >&2
  exit 2
fi

mysql_host="${DB_HOST:-127.0.0.1}"
mysql_port="${DB_PORT:-3306}"
mysql_user="${DB_USERNAME:-root}"
mysql_ssl_mode="${DB_SSL_MODE:-DISABLED}"
export MYSQL_PWD="${DB_PASSWORD:-root}"
mysql_base=(mysql --protocol=TCP --ssl-mode="$mysql_ssl_mode" --default-character-set=utf8mb4 --host="$mysql_host" --port="$mysql_port" --user="$mysql_user")

cleanup() {
  "${mysql_base[@]}" --execute="DROP DATABASE IF EXISTS \`$database\`;" >/dev/null 2>&1 || true
}
trap cleanup EXIT

"${mysql_base[@]}" --execute="
  DROP DATABASE IF EXISTS \`$database\`;
  CREATE DATABASE \`$database\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
  CREATE TABLE \`$database\`.ncs_classification (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ncs_code VARCHAR(60) NOT NULL,
    major_code VARCHAR(20) NOT NULL,
    major_name VARCHAR(100) NOT NULL,
    middle_code VARCHAR(20) NOT NULL,
    middle_name VARCHAR(100) NOT NULL,
    minor_code VARCHAR(20) NOT NULL,
    minor_name VARCHAR(100) NOT NULL,
    sub_code VARCHAR(20) NOT NULL,
    sub_name VARCHAR(200) NOT NULL,
    unit_count INT NOT NULL DEFAULT 0,
    element_count INT NOT NULL DEFAULT 0,
    min_level INT NULL,
    max_level INT NULL,
    search_text MEDIUMTEXT NULL,
    detail_json MEDIUMTEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ncs_code (ncs_code)
  ) ENGINE=InnoDB;
  INSERT INTO \`$database\`.ncs_classification
    (ncs_code, major_code, major_name, middle_code, middle_name, minor_code, minor_name,
     sub_code, sub_name, detail_json)
  VALUES
    ('01-0101-010101-01-0101-010101-01010101', '01', '대1', '0101', '중1', '010101', '소1',
     '01-0101-010101-0101', '잘린 legacy', '[]'),
    ('02-0202-020202-02020202', '02', '대2', '0202', '중2', '020202', '소2',
     '02020202', 'canonical', '[]'),
    ('02-0202-020202-02-0202-020202-02020202', '02', '대2', '0202', '중2', '020202', '소2',
     '02-0202-020202-0202', '중복 legacy', '[]');
"

apply_patch() {
  "${mysql_base[@]}" "$database" < "$patch_path"
}

snapshot() {
  "${mysql_base[@]}" --batch --skip-column-names "$database" --execute="
    SELECT CONCAT(
      COUNT(*), '|',
      SUM(ncs_code <> CONCAT(major_code, '-', middle_code, '-', minor_code, '-', sub_code)), '|',
      GROUP_CONCAT(CONCAT(ncs_code, ':', sub_code) ORDER BY ncs_code SEPARATOR ',')
    )
    FROM ncs_classification;
  " | tr -d '\r'
}

apply_patch
first="$(snapshot)"
if [[ "$first" != "2|0|01-0101-010101-01010101:01010101,02-0202-020202-02020202:02020202" ]]; then
  echo "Unexpected first migration snapshot: $first" >&2
  exit 1
fi

apply_patch
second="$(snapshot)"
if [[ "$second" != "$first" ]]; then
  echo "NCS patch is not idempotent: first=$first second=$second" >&2
  exit 1
fi

echo "PASS: NCS code patch repairs truncated legacy rows and is idempotent"
