-- 개인 차단/허용 정책 (docs/PERSONAL_BLOCK_POLICY.md)
-- 사용자 개인이 계정/IP/채팅방을 차단하고, 관계별(모르는 사람/친구/기업/차단 계정/차단 IP)
-- 수신·노출 정책을 표면(surface) 단위로 설정한다.

-- 계정 차단. flags_json 의 non-null 표면 값이 관계 정책보다 우선한다.
CREATE TABLE IF NOT EXISTS user_block (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL COMMENT '차단을 설정한 사용자',
    blocked_user_id BIGINT       NOT NULL COMMENT '차단 대상 계정',
    flags_json      JSON         NULL COMMENT '표면별 명시 설정. null 항목은 blockedAccount 관계 정책을 따름',
    block_ip        TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '이 계정의 접속 IP 도 차단(user_ip_block 파생)',
    memo            VARCHAR(200) NULL COMMENT '개인 메모(차단 사유 등)',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_user_block_pair (user_id, blocked_user_id),
    KEY idx_user_block_blocked (blocked_user_id),
    CONSTRAINT fk_user_block_user    FOREIGN KEY (user_id)         REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_block_blocked FOREIGN KEY (blocked_user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '개인 계정 차단(표면별 세부 설정 포함)';

-- IP 차단. 원본 IP 는 저장·노출하지 않고 해시만 둔다(계정 차단에서 파생 생성).
CREATE TABLE IF NOT EXISTS user_ip_block (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL COMMENT '차단을 설정한 사용자',
    ip_hash        VARCHAR(64)  NOT NULL COMMENT 'SHA-256(서버솔트+IP). 원본 IP 비저장',
    source_user_id BIGINT       NULL COMMENT '어느 계정 차단에서 파생됐는지',
    label          VARCHAR(100) NULL COMMENT '목록 표기용 라벨',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_user_ip_block (user_id, ip_hash),
    CONSTRAINT fk_user_ip_block_user   FOREIGN KEY (user_id)        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_ip_block_source FOREIGN KEY (source_user_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '개인 IP 차단(해시만 저장)';

-- 채팅방 차단. 방 숨김 + 재초대/구성원 경유 초대 차단(연속 초대 테러 방지).
CREATE TABLE IF NOT EXISTS conversation_block (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    user_id         BIGINT      NOT NULL,
    conversation_id BIGINT      NOT NULL,
    flags_json      JSON        NULL COMMENT 'inviteFromRoom/memberCreatedRoomInvite/memberJoinedRoomInvite(+익명 변형)',
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_conversation_block (user_id, conversation_id),
    CONSTRAINT fk_conversation_block_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_conversation_block_conv FOREIGN KEY (conversation_id) REFERENCES collaboration_conversation (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '개인 채팅방 차단';

-- 관계별 정책 문서(사용자별 1행). 표면 키는 점 표기 상속(docs 참고), null=상위 따름.
CREATE TABLE IF NOT EXISTS user_privacy_policy (
    id          BIGINT   NOT NULL AUTO_INCREMENT,
    user_id     BIGINT   NOT NULL,
    policy_json JSON     NULL,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_user_privacy_policy_user (user_id),
    CONSTRAINT fk_user_privacy_policy_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '개인 차단/허용 관계별 정책';

-- 익명 초대 슬롯: 초대자가 익명으로 표시되는 초대(정책 표면 invite.*.anonymous 대상).
ALTER TABLE collaboration_conversation_invite
    ADD COLUMN anonymous TINYINT(1) NOT NULL DEFAULT 0 COMMENT '익명 초대 여부' AFTER invitee_id;
