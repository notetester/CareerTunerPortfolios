-- =====================================================================
--  F 담당(법적문서) 스키마 패치 — 2026-06-23
--  legal 감사추적 강화: version_label 유일성 + doc_type별 DRAFT 1건 유일성.
--
--  배경(F_EDGECASE_REVIEW.md 2-8):
--   - version_label 유니크 제약·검증 없어 동일 라벨 중복 게시 가능 → 감사추적 모호.
--   - doc_type별 DRAFT 1건 제약이 앱(read-then-write)에만 있어 동시 createDraft에서 깨짐.
--   둘 다 DB 레벨 제약으로 못박아 경합·중복을 원천 차단한다.
--
--  ⚠️ 공유 DB 변경 → 팀장 합의 후 AWS(team1_db) 적용. 로컬은 이 패치를 직접 실행.
--  ☐ 적용 이력: (미적용) AWS team1_db 적용 후 날짜 기록할 것.
--  실행: mysql -h <host> -u <user> -p <db> < 20260623_f_legal_uniqueness.sql
--
--  ※ 이 패치는 ALTER TABLE 이라 재실행 안전하지 않다(중복 키 에러). 1회만 적용.
--    적용 전 아래 [선행: 중복 탐지]를 반드시 실행해 0건임을 확인할 것.
-- =====================================================================


-- ── [선행: 중복 탐지] 적용 전 실행. 결과가 1건이라도 나오면 ALTER 중단하고 보고 ──
--   임의 삭제 금지 — 중복이 있으면 어느 행을 남길지 팀과 결정 후 진행.
--
--   (a) 동일 (doc_type, version_label) 중복:
--   SELECT doc_type, version_label, COUNT(*) AS cnt, GROUP_CONCAT(id) AS ids
--     FROM legal_document_version
--    GROUP BY doc_type, version_label
--   HAVING cnt > 1;
--
--   (b) doc_type별 DRAFT 2건 이상:
--   SELECT doc_type, COUNT(*) AS cnt, GROUP_CONCAT(id) AS ids
--     FROM legal_document_version
--    WHERE status = 'DRAFT'
--    GROUP BY doc_type
--   HAVING cnt > 1;


-- ── S2-1. (doc_type, version_label) 유일성 ────────────────────────────
--   같은 문서 타입에서 동일 버전 라벨(예: 'v2.4')을 두 번 게시하지 못하게 한다.
ALTER TABLE legal_document_version
    ADD UNIQUE KEY uk_legal_doctype_version (doc_type, version_label);


-- ── S2-2. doc_type별 DRAFT 1건 유일성 (부분 유니크 대체) ───────────────
--   MySQL 8 은 부분(조건부) 유니크 인덱스를 직접 지원하지 않으므로,
--   생성열(VIRTUAL)을 두어 status='DRAFT'일 때만 doc_type 값을 갖고
--   그 외(PUBLISHED 등)는 NULL 이 되게 한다. UNIQUE 는 NULL 중복을 허용하므로
--   PUBLISHED 는 여러 건 가능하고, DRAFT 는 doc_type 당 1건으로 제한된다.
--   (VIRTUAL: 저장 공간 없이 인덱스만 — MySQL 8.0.13+ 지원)
ALTER TABLE legal_document_version
    ADD COLUMN draft_doc_type VARCHAR(20)
        GENERATED ALWAYS AS (CASE WHEN status = 'DRAFT' THEN doc_type END) VIRTUAL
        COMMENT 'DRAFT 유일성 제약용 파생 컬럼 (DRAFT면 doc_type, 그 외 NULL)',
    ADD UNIQUE KEY uk_legal_draft_one (draft_doc_type);


-- ── 검증 (적용 후 확인용) ─────────────────────────────────────────────
-- SHOW INDEX FROM legal_document_version WHERE Key_name IN ('uk_legal_doctype_version','uk_legal_draft_one');
-- -- 동일 라벨 재게시 거부 확인:
-- INSERT INTO legal_document_version (doc_type, version_label, status) VALUES ('TERMS','dup-test','PUBLISHED');
-- INSERT INTO legal_document_version (doc_type, version_label, status) VALUES ('TERMS','dup-test','PUBLISHED'); -- 1062 Duplicate 예상
-- -- DRAFT 2건 거부 확인:
-- INSERT INTO legal_document_version (doc_type, version_label, status) VALUES ('TERMS','draft-a','DRAFT');
-- INSERT INTO legal_document_version (doc_type, version_label, status) VALUES ('TERMS','draft-b','DRAFT'); -- 1062 Duplicate 예상


-- =====================================================================
--  참고(S1, 별도 적용 불필요): 리액션 멱등(ReactionServiceImpl DuplicateKeyException
--  흡수)이 의존하는 UNIQUE 는 schema.sql 에 이미 정의돼 있다.
--    post_reaction    : uk_post_reaction    (user_id, post_id, reaction_type)
--    comment_reaction : uk_comment_reaction (user_id, comment_id, reaction_type)
--  운영 AWS DB 에 실제로 존재하는지만 아래로 확인할 것(없으면 별도 보고):
--    SHOW INDEX FROM post_reaction    WHERE Key_name = 'uk_post_reaction';
--    SHOW INDEX FROM comment_reaction WHERE Key_name = 'uk_comment_reaction';
-- =====================================================================
