-- =====================================================================
--  F 담당(챗봇) 스키마 마이그레이션 — 2026-06-24
--  운영 패널 3단계-1: 답 못한 질문 의미 기반 군집화용 컬럼 보강.
--
--  배경:
--   1단계는 question_norm 정확매칭(공백·대소문자만 동일)으로 묶어, 표현이
--   조금만 달라도("환불 어떻게" vs "환불 방법") 다른 그룹이 된다. 의미 기반
--   군집화를 하려면 질문 임베딩 벡터가 필요한데, 매 조회마다 전부 재임베딩하면
--   Ollama 호출이 폭증한다. 그래서 수집 시점에 임베딩을 1회 저장해 둔다.
--   (수집 훅이 미스 판정 때 이미 질문을 임베딩하므로 그 벡터를 재사용 → 추가 비용 0.)
--
--   - embedding   : 질문 임베딩(bge-m3) JSON 배열. 군집화 코사인 계산용.
--   - best_faq_id : 미스 당시 가장 가까웠던 FAQ id. 운영 화면의 "가장 가까웠던 FAQ"
--                   표시·카테고리 추정 근거(top_similarity 와 짝).
--
--  ⚠️ 공유 DB 변경 → 팀 합의 후 적용. 로컬 dev DB 는 먼저 적용해 검증.
--  ⚠️ 선행: 20260623_f_chatbot_unanswered.sql(테이블 생성)이 적용돼 있어야 한다.
--  ⚠️ 이 패치 적용 전까지: 수집 INSERT 가 새 컬럼을 쓰므로 적재가 (best-effort 라
--     예외를 삼켜) 멈춘다. 챗봇 응답은 정상이나 수집이 안 되니 적용을 미루지 말 것.
--  ⚠️ MySQL 8 은 ADD COLUMN IF NOT EXISTS 미지원 → 재실행 시 "Duplicate column"
--     이면 이미 적용된 것(무시).
--  실행: mysql -h <host> -u <user> -p <db> < 20260624_f_chatbot_unanswered_cluster.sql
--
--  ☐ 적용 이력: (미적용) 로컬 적용일 ____  / 운영 공유 DB(team1_db) 적용일 ____
-- =====================================================================

ALTER TABLE chatbot_unanswered_question
    ADD COLUMN embedding   MEDIUMTEXT NULL COMMENT '질문 임베딩(bge-m3) JSON 배열 — 군집화용. 임베딩 불가 시 NULL' AFTER top_similarity,
    ADD COLUMN best_faq_id BIGINT     NULL COMMENT '미스 당시 가장 가까웠던 FAQ id(있으면) — best 표시·카테고리 근거' AFTER embedding;

-- ── 검증 (적용 후 확인용) ────────────────────────────────────────────
-- SHOW CREATE TABLE chatbot_unanswered_question;
-- SELECT id, question, top_similarity, best_faq_id,
--        CASE WHEN embedding IS NULL THEN 'NULL' ELSE CONCAT(LENGTH(embedding), 'B') END AS emb
--   FROM chatbot_unanswered_question ORDER BY id DESC LIMIT 5;
