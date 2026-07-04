-- 개인 차단 정책 확장 2종.
-- 1) 익명 콘텐츠 작성자 차단: 차단 목록에서 실명이 노출되면 익명성이 깨지므로,
--    콘텐츠 id 기반 차단 시 표시용 마스킹 라벨을 저장하고 응답에서 이름/이메일을 대체한다.
ALTER TABLE user_block
    ADD COLUMN masked_label VARCHAR(100) NULL COMMENT '익명 콘텐츠 기반 차단의 표시 라벨(비노출 익명성 유지)' AFTER memo;

-- 2) LOCAL 파일 공유 릴레이: 파일 소유자의 데스크톱 앱이 온라인일 때만 다운로드를 허용한다.
--    데스크톱이 폴링 주기마다 heartbeat 를 남긴다.
CREATE TABLE IF NOT EXISTS user_desktop_presence (
    user_id      BIGINT   NOT NULL,
    last_seen_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_user_desktop_presence_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '데스크톱 앱 접속 heartbeat(LOCAL 파일 공유 게이트)';
