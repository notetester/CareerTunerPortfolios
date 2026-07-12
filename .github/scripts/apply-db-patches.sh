#!/usr/bin/env bash
set -euo pipefail

patch_list="${1:-managed-db-patches.txt}"
compose_base="${2:-docker-compose.yml}"
compose_prod="${3:-docker-compose.prod.yml}"

if [[ ! -s "$patch_list" ]]; then
  echo "No database patches to apply."
  exit 0
fi

compose=(docker compose -f "$compose_base" -f "$compose_prod" --env-file .env)
backend_id="$("${compose[@]}" ps -q backend)"
if [[ -z "$backend_id" ]]; then
  echo "::error::기존 backend 컨테이너를 찾지 못해 DB 접속 정보를 안전하게 읽을 수 없습니다."
  exit 1
fi

container_env() {
  local key="$1"
  docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "$backend_id" \
    | awk -v key="$key" 'index($0, key "=") == 1 { sub("^[^=]*=", ""); print; exit }'
}

db_host="$(container_env DB_HOST)"
db_port="$(container_env DB_PORT)"
db_name="$(container_env DB_NAME)"
db_user="$(container_env DB_USERNAME)"
db_password="$(container_env DB_PASSWORD)"
db_port="${db_port:-3306}"

for required in db_host db_name db_user db_password; do
  if [[ -z "${!required}" ]]; then
    echo "::error::backend 컨테이너의 ${required} 값이 비어 있습니다."
    exit 1
  fi
done

mysql_client() {
  docker run --rm --network host \
    -e MYSQL_PWD="$db_password" \
    mysql:8.4 \
    mysql --protocol=TCP --connect-timeout=15 --default-character-set=utf8mb4 \
      --host="$db_host" --port="$db_port" --user="$db_user" "$db_name" "$@"
}

mysql_client --execute "
  CREATE TABLE IF NOT EXISTS schema_migration (
    migration_name VARCHAR(255) NOT NULL PRIMARY KEY,
    checksum CHAR(64) NOT NULL,
    applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
"

while IFS= read -r patch_path || [[ -n "$patch_path" ]]; do
  [[ -z "$patch_path" ]] && continue
  if [[ ! "$patch_path" =~ ^backend/src/main/resources/db/patches/[A-Za-z0-9._-]+\.sql$ ]]; then
    echo "::error::허용되지 않은 DB patch 경로: $patch_path"
    exit 1
  fi
  if [[ ! -f "$patch_path" ]]; then
    echo "::error::DB patch 파일을 찾을 수 없습니다: $patch_path"
    exit 1
  fi

  migration_name="$(basename "$patch_path")"
  checksum="$(sha256sum "$patch_path" | awk '{print $1}')"
  existing="$(mysql_client --batch --skip-column-names --execute \
    "SELECT checksum FROM schema_migration WHERE migration_name = '${migration_name}'" | tr -d '\r')"

  if [[ -n "$existing" ]]; then
    if [[ "$existing" != "$checksum" ]]; then
      echo "::error::이미 적용된 DB patch 내용이 변경됐습니다: $migration_name"
      exit 1
    fi
    echo "DB patch already applied: $migration_name"
    continue
  fi

  echo "Applying DB patch: $migration_name"
  mysql_client < "$patch_path"
  mysql_client --execute \
    "INSERT INTO schema_migration (migration_name, checksum) VALUES ('${migration_name}', '${checksum}')"
  echo "Applied DB patch: $migration_name"
done < "$patch_list"
