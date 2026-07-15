-- AutoPrep 텍스트 공고 인테이크의 응답 유실·동시 재시도 중복 지원 건 생성을 방지한다.
-- file_id는 성공 응답 후 pending file_asset이 삭제되어도 매핑을 유지해야 하므로 의도적으로 FK를 두지 않는다.
CREATE TABLE IF NOT EXISTS auto_prep_case_dedupe (
    user_id             BIGINT NOT NULL,
    file_id             BIGINT NOT NULL,
    application_case_id BIGINT NULL,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, file_id),
    KEY idx_auto_prep_case_dedupe_case (application_case_id),
    CONSTRAINT fk_auto_prep_case_dedupe_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_auto_prep_case_dedupe_case
        FOREIGN KEY (application_case_id) REFERENCES application_case (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
