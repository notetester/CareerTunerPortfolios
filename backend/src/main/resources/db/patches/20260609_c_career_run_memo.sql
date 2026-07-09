-- C 장기 경향/대시보드 요약 실행 이력의 운영 메모 테이블.
-- 적합도(admin_fit_analysis_memo)와 동일 패턴. 멱등(CREATE TABLE IF NOT EXISTS).
-- 관리자 완료 기준의 "분석 결과 운영 메모"를 장기 경향/대시보드 요약 실행 이력 단위로 남긴다.

CREATE TABLE IF NOT EXISTS admin_career_run_memo (
    id                    BIGINT NOT NULL AUTO_INCREMENT,
    career_analysis_run_id BIGINT NOT NULL,
    admin_user_id         BIGINT NOT NULL,
    memo_type             VARCHAR(30) NOT NULL DEFAULT 'GENERAL', -- GENERAL/QUALITY/USER_INQUIRY/REANALYSIS
    content               MEDIUMTEXT NOT NULL,
    created_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_admin_career_memo_run (career_analysis_run_id),
    KEY idx_admin_career_memo_admin_user (admin_user_id),
    CONSTRAINT fk_admin_career_memo_run FOREIGN KEY (career_analysis_run_id) REFERENCES career_analysis_run (id) ON DELETE CASCADE,
    CONSTRAINT fk_admin_career_memo_admin_user FOREIGN KEY (admin_user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
