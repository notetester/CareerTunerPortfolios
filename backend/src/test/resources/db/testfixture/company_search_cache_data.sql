-- created_at 보존 검증용 시드 1건. fetched_at·created_at 을 과거 고정값으로 박아
-- upsert 갱신 후 created_at 이 최초값 그대로인지(갱신 금지) 결정적으로 확인한다.
INSERT INTO company_search_cache (id, query_key, results, fetched_at, created_at)
VALUES (100, 'seed-company', '[{"url":"https://old.example.com"}]',
        TIMESTAMP '2026-06-01 09:00:00', TIMESTAMP '2026-06-01 09:00:00');
