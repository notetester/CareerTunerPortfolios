-- =====================================================================
--  D 담당(가상 면접) 스키마 마이그레이션 — 2026-06-12
--  음성 모의면접 / 아바타 화상 면접의 온디바이스 분석 결과 저장 테이블 추가.
--
--  배경:
--   ADR-002 — 원본 음성·영상은 서버에 저장하지 않고(온디바이스 분석),
--   트랜스크립트와 점수(JSON)만 보관한다. 음성 모의면접(VOICE)과
--   아바타 화상 면접(AVATAR)이 같은 테이블을 공유한다.
--
--  ⚠️ 공유 DB 변경 → 팀 합의 후 적용. CREATE TABLE IF NOT EXISTS 라 재실행 안전.
--  실행: mysql -h <host> -u <user> -p <db> < 20260612_d_interview_media_analysis.sql
--
--  ✅ 적용 이력: 2026-06-12 운영 공유 DB(team1_db @ localhost) 적용 완료
--     로컬/개인 DB 는 이 패치를 직접 실행할 것.
-- =====================================================================

CREATE TABLE IF NOT EXISTS interview_media_analysis (
    id                   BIGINT NOT NULL AUTO_INCREMENT,
    interview_session_id BIGINT NOT NULL,
    kind                 VARCHAR(20) NOT NULL,                       -- VOICE(음성 모의면접) / AVATAR(아바타 화상 면접)
    transcript           JSON NULL,                                  -- [{"role":"ai|user","text":"..."}]
    metrics              JSON NULL,                                  -- 측정 지표 원본 (말속도·침묵·필러·피치, 표정/자세, Inworld 프로필)
    score                INT NULL,                                   -- 종합 점수 0~100
    score_detail         JSON NULL,                                  -- 항목별 점수 {"pace":80,"fluency":70,...}
    created_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_media_analysis_session (interview_session_id),
    CONSTRAINT fk_media_analysis_session FOREIGN KEY (interview_session_id)
        REFERENCES interview_session (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- ── 검증 (적용 후 확인용) ────────────────────────────────────────────
-- SHOW TABLES LIKE 'interview_media_analysis';
-- SHOW COLUMNS FROM interview_media_analysis;
