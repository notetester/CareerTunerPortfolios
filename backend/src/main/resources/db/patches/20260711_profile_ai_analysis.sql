-- A영역 프로필 AI 분석 산출물 영속(사용자 조회 + C 적합도 입력 창구). feature_type 별 최신 1행 upsert.
-- 기존에는 evaluate 결과가 응답 후 휘발되어 새로고침 시 사라지고 C가 참고할 수 없었다 — 이 테이블이 그 저장소.
CREATE TABLE IF NOT EXISTS profile_ai_analysis (
    id                 BIGINT NOT NULL AUTO_INCREMENT,
    user_id            BIGINT NOT NULL,
    feature_type       VARCHAR(40) NOT NULL,   -- PROFILE_SUMMARY / PROFILE_SKILL_EXTRACT / PROFILE_COMPLETENESS
    summary            MEDIUMTEXT NULL,
    strengths          JSON NULL,              -- ["..."]
    gaps               JSON NULL,              -- ["..."]
    recommendations    JSON NULL,              -- ["..."]
    extracted_skills   JSON NULL,              -- ["..."]
    criteria           JSON NULL,              -- [{criterion,rawScore,weight,weightedScore,evidence,improvement}]
    job_family         VARCHAR(60) NULL,
    completeness_score INT NULL,
    ai_score           INT NULL,
    quality_warnings   JSON NULL,              -- ["..."]
    model              VARCHAR(120) NULL,
    status             VARCHAR(20) NULL,
    created_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_profile_ai_user_feature (user_id, feature_type),
    CONSTRAINT fk_profile_ai_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
