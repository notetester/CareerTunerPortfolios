-- 기업분석 검색 캐시 매퍼 통합테스트용 최소 스키마(H2 호환, D-4a).
-- 운영 schema.sql 은 MySQL JSON 타입/구문을 쓰므로, 매퍼가 문자열로 다루는 results 는 CLOB 로 둔다.
-- 컬럼명·의미·UNIQUE(query_key) 는 운영 스키마와 동일하게 유지한다.
-- upsert(ON DUPLICATE KEY UPDATE)는 테스트 datasource H2 를 MySQL 모드로 띄워 검증한다.
CREATE TABLE IF NOT EXISTS company_search_cache (
    id         BIGINT PRIMARY KEY AUTO_INCREMENT,
    query_key  VARCHAR(255) NOT NULL,
    results    CLOB NULL,
    fetched_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_company_search_cache_query UNIQUE (query_key)
);
