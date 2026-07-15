-- =====================================================================
--  F 담당(챗봇 에이전트) 스키마 마이그레이션 — 2026-06-23
--  커뮤니티 글(community_post)의 bge-m3 임베딩을 저장할 별도 테이블 추가.
--
--  배경:
--   챗봇을 LangChain4j 툴호출 에이전트로 전환하면서, searchCommunityPosts 툴이
--   "1단계 SQL 후보 좁히기 → 2단계 코사인" 방식으로 커뮤니티 글을 검색한다.
--   각 글의 임베딩(bge-m3, 1024차원 float)을 저장한다.
--
--   ⚠️ community_post 에 컬럼을 추가하지 않고 별도 테이블로 분리한 이유:
--     기존 목록/상세/인기글 쿼리가 SELECT cp.* 라, 임베딩 컬럼을 추가하면
--     임베딩 적재 후 모든 조회에 1024차원 JSON(행당 ~15KB+)이 딸려오는 회귀가 생긴다.
--     별도 테이블이면 검색 시에만 JOIN 으로 가져오고 기존 쿼리는 무영향.
--
--  ⚠️ 공유 DB 변경 → 팀 합의 후 적용.
--     IF NOT EXISTS 패턴으로 이미 있으면 스킵 — 재실행 안전.
--  실행: mysql -h <host> -u <user> -p <db> < 20260623_f_community_post_embedding.sql
--
--  ☐ 적용 이력: (미적용) 운영 공유 DB(team1_db @ 54.116.80.214) 적용 후 날짜 기록할 것.
--     로컬/개인 DB 는 이 패치를 직접 실행할 것.
-- =====================================================================

CREATE TABLE IF NOT EXISTS community_post_embedding (
    post_id    BIGINT   NOT NULL COMMENT 'community_post.id',
    embedding  JSON     NOT NULL COMMENT 'bge-m3 임베딩 벡터 (1024차원 float 배열)',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (post_id),
    CONSTRAINT fk_cpe_post FOREIGN KEY (post_id) REFERENCES community_post (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ── 검증 (적용 후 확인용) ────────────────────────────────────────────
-- SHOW CREATE TABLE community_post_embedding;
-- SELECT COUNT(*) FROM community_post_embedding;
