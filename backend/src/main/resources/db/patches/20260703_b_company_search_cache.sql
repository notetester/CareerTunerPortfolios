-- 기업분석 웹검색 재설계(235 §4·§6, 팀장 승인 2026-07-03) — 검색 결과 캐시 테이블.
-- 같은 회사 7일 내 재검색 방지(비용↓) + 재조회/신선도 판정 근거(235 §11: 캐시 7일).
-- 멱등: IF NOT EXISTS. schema.sql 반영과 동일 정의.
CREATE TABLE IF NOT EXISTS company_search_cache (
    id         BIGINT NOT NULL AUTO_INCREMENT,
    query_key  VARCHAR(255) NOT NULL,
    results    JSON NULL,
    fetched_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_company_search_cache_query (query_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
