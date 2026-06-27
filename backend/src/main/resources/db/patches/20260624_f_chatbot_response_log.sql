-- =====================================================================
--  F 담당(챗봇) 스키마 마이그레이션 — 2026-06-24
--  관리자 AI 상담 운영 패널 3단계-2: 챗봇 턴 응답 로그 테이블.
--
--  배경:
--   챗봇이 응답할 때마다 1행(답함/못함·유사도·인용 FAQ·전환)을 best-effort로
--   적재하기 위한 테이블이다. 운영 콘솔 메트릭 밴드(자동 해결률·FAQ 참조 응답
--   수·상담사 전환율)·임계값 슬라이더 미리보기·참조 대화 표(답한 대화 로그)의
--   단일 소스가 된다. 미스만 모으는 chatbot_unanswered_question 과 달리,
--   여기에는 답한 턴까지 전부 들어가 "분모(전 턴)"를 만든다.
--   - response_path  : NAV_FAST / FAQ_FAST / AGENT (어느 경로로 응답했는지)
--   - faq_referenced : FAQ 근거로 답했는가(=자동 해결로 카운트)
--   - top_similarity : FAQ 최고 유사도(슬라이더 미리보기 분포 소스)
--   - matched_faq_id : 인용한 최상위 FAQ id(참조 대화 표시·faq join)
--   - handoff        : 상담사/문의로 전환되었는가(전환율 분자)
--   적재 훅은 수집 훅과 동일하게 best-effort(예외 삼킴)라 챗봇 응답을 깨지 않는다.
--
--  ⚠️ 공유 DB 변경 → 팀 합의 후 적용. IF NOT EXISTS 로 재실행 안전.
--     로컬 dev DB 는 먼저 적용해 검증.
--  ⚠️ 이 패치 미적용 시: 적재 훅은 best-effort(예외 삼킴)라 챗봇 응답은 정상
--     동작하지만 적재만 안 된다. 메트릭/슬라이더/참조 조회 API 는 테이블 부재로 실패한다.
--  ⚠️ matched_faq_id 는 faq(id) 로의 논리 참조이며 FK 제약은 두지 않는다
--     (faq 미시드/삭제여도 적재가 깨지지 않게 — unanswered.best_faq_id 와 동일 설계).
--  실행: mysql -h <host> -u <user> -p <db> < 20260624_f_chatbot_response_log.sql
--
--  ☐ 적용 이력: (미적용) 로컬 적용일 ____  / 운영 공유 DB(team1_db) 적용일 ____
-- =====================================================================

CREATE TABLE IF NOT EXISTS chatbot_response_log (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT        NULL     COMMENT '발생 대화 id(있으면) — chatbot_conversation_memory 논리 참조(FK 없음)',
    user_id         BIGINT        NULL     COMMENT '로그인 유저 id, 비로그인 NULL',
    question        VARCHAR(1000) NOT NULL COMMENT '사용자 질문(verbatim)',
    response_path   VARCHAR(16)   NOT NULL COMMENT '응답 경로: NAV_FAST/FAQ_FAST/AGENT',
    faq_referenced  TINYINT(1)    NOT NULL DEFAULT 0 COMMENT 'FAQ 근거로 답함(=자동 해결)',
    top_similarity  DOUBLE        NULL     COMMENT 'FAQ 최고 유사도(슬라이더 미리보기 소스; 계산 불가 시 NULL)',
    matched_faq_id  BIGINT        NULL     COMMENT '인용한 최상위 FAQ id(참조 대화 표시; 없으면 NULL)',
    handoff         TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '상담사/문의 전환 여부(전환율 분자)',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_crl_created (created_at),
    INDEX idx_crl_ref_created (faq_referenced, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ── 검증 (적용 후 확인용) ────────────────────────────────────────────
-- SHOW CREATE TABLE chatbot_response_log;
-- SELECT COUNT(*) AS turns,
--        SUM(faq_referenced) AS faq_ref,
--        ROUND(SUM(faq_referenced)/COUNT(*), 4) AS auto_resolve_rate,
--        ROUND(SUM(handoff)/COUNT(*), 4)        AS handoff_rate
--   FROM chatbot_response_log;
