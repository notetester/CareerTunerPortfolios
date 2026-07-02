-- Desktop messenger and small community rooms: friends, group/public/private rooms, notes, postings, and attachment policies.

CREATE TABLE IF NOT EXISTS collaboration_friend_request (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    requester_id   BIGINT      NOT NULL,
    receiver_id    BIGINT      NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    pending_marker TINYINT GENERATED ALWAYS AS (
        CASE WHEN status = 'PENDING' THEN 1 ELSE NULL END
    ) STORED,
    responded_at   DATETIME    NULL,
    created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_collab_friend_request_pending (requester_id, receiver_id, pending_marker),
    KEY idx_collab_friend_request_receiver (receiver_id, status, created_at DESC),
    KEY idx_collab_friend_request_requester (requester_id, status, created_at DESC),
    CONSTRAINT chk_collab_friend_request_status CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'CANCELED')),
    CONSTRAINT fk_collab_friend_request_requester FOREIGN KEY (requester_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_collab_friend_request_receiver FOREIGN KEY (receiver_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '사용자 간 친구 요청';

CREATE TABLE IF NOT EXISTS collaboration_friendship (
    id             BIGINT   NOT NULL AUTO_INCREMENT,
    user_id        BIGINT   NOT NULL,
    friend_user_id BIGINT   NOT NULL,
    created_by     BIGINT   NULL,
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_collab_friendship_pair (user_id, friend_user_id),
    KEY idx_collab_friendship_friend (friend_user_id),
    CONSTRAINT fk_collab_friendship_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_collab_friendship_friend FOREIGN KEY (friend_user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_collab_friendship_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '수락된 친구 관계. 조회 단순화를 위해 양방향 행을 저장한다';

CREATE TABLE IF NOT EXISTS collaboration_conversation (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    type          VARCHAR(20)  NOT NULL DEFAULT 'DIRECT',
    user_low_id   BIGINT       NULL,
    user_high_id  BIGINT       NULL,
    title         VARCHAR(120) NULL,
    description   VARCHAR(500) NULL,
    password_hash VARCHAR(255) NULL,
    max_members   INT          NOT NULL DEFAULT 100,
    created_by    BIGINT       NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_collab_direct_pair (type, user_low_id, user_high_id),
    KEY idx_collab_conversation_type_updated (type, updated_at DESC),
    KEY idx_collab_conversation_created_by (created_by, updated_at DESC),
    CONSTRAINT chk_collab_conversation_type CHECK (type IN ('DIRECT', 'GROUP', 'PUBLIC', 'PRIVATE')),
    CONSTRAINT fk_collab_conversation_low FOREIGN KEY (user_low_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_collab_conversation_high FOREIGN KEY (user_high_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_collab_conversation_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '1:1, 그룹, 공개, 비공개 메신저 대화방';

CREATE TABLE IF NOT EXISTS collaboration_conversation_member (
    conversation_id      BIGINT      NOT NULL,
    user_id              BIGINT      NOT NULL,
    role                 VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    status               VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    invited_by           BIGINT      NULL,
    last_read_message_id BIGINT      NULL,
    muted                TINYINT(1)  NOT NULL DEFAULT 0,
    joined_at            DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_read_at         DATETIME    NULL,
    left_at              DATETIME    NULL,
    PRIMARY KEY (conversation_id, user_id),
    KEY idx_collab_conversation_member_user (user_id, status, joined_at DESC),
    KEY idx_collab_conversation_member_inviter (invited_by),
    CONSTRAINT chk_collab_conversation_member_role CHECK (role IN ('OWNER', 'MANAGER', 'MEMBER')),
    CONSTRAINT chk_collab_conversation_member_status CHECK (status IN ('ACTIVE', 'LEFT', 'REMOVED')),
    CONSTRAINT fk_collab_conversation_member_conversation FOREIGN KEY (conversation_id) REFERENCES collaboration_conversation (id) ON DELETE CASCADE,
    CONSTRAINT fk_collab_conversation_member_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_collab_conversation_member_inviter FOREIGN KEY (invited_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '대화방 참여자와 읽음 상태';

CREATE TABLE IF NOT EXISTS collaboration_conversation_invite (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT      NOT NULL,
    inviter_id      BIGINT      NULL,
    invitee_id      BIGINT      NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    pending_marker  TINYINT GENERATED ALWAYS AS (
        CASE WHEN status = 'PENDING' THEN 1 ELSE NULL END
    ) STORED,
    responded_at    DATETIME    NULL,
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_collab_conversation_invite_pending (conversation_id, invitee_id, pending_marker),
    KEY idx_collab_conversation_invite_invitee (invitee_id, status, created_at DESC),
    CONSTRAINT chk_collab_conversation_invite_status CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'CANCELED')),
    CONSTRAINT fk_collab_conversation_invite_conversation FOREIGN KEY (conversation_id) REFERENCES collaboration_conversation (id) ON DELETE CASCADE,
    CONSTRAINT fk_collab_conversation_invite_inviter FOREIGN KEY (inviter_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_collab_conversation_invite_invitee FOREIGN KEY (invitee_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '그룹/비공개 채팅방 참가 초대';

CREATE TABLE IF NOT EXISTS collaboration_message (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT      NOT NULL,
    sender_id       BIGINT      NOT NULL,
    kind            VARCHAR(20) NOT NULL DEFAULT 'CHAT',
    content         MEDIUMTEXT  NULL,
    deleted_at      DATETIME    NULL,
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_collab_message_thread (conversation_id, id),
    KEY idx_collab_message_sender (sender_id, created_at DESC),
    CONSTRAINT chk_collab_message_kind CHECK (kind IN ('CHAT', 'NOTE')),
    CONSTRAINT fk_collab_message_conversation FOREIGN KEY (conversation_id) REFERENCES collaboration_conversation (id) ON DELETE CASCADE,
    CONSTRAINT fk_collab_message_sender FOREIGN KEY (sender_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '대화방 메시지. CHAT과 NOTE를 kind로 구분한다';

CREATE TABLE IF NOT EXISTS collaboration_message_attachment (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    message_id    BIGINT      NOT NULL,
    file_asset_id BIGINT      NOT NULL,
    share_mode    VARCHAR(20) NOT NULL DEFAULT 'TEMPORARY',
    expires_at    DATETIME    NULL,
    created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_collab_message_attachment (message_id, file_asset_id),
    KEY idx_collab_message_attachment_file (file_asset_id),
    KEY idx_collab_message_attachment_expiry (share_mode, expires_at),
    CONSTRAINT chk_collab_message_attachment_mode CHECK (share_mode IN ('TEMPORARY', 'CLOUD', 'LOCAL')),
    CONSTRAINT fk_collab_message_attachment_message FOREIGN KEY (message_id) REFERENCES collaboration_message (id) ON DELETE CASCADE,
    CONSTRAINT fk_collab_message_attachment_file FOREIGN KEY (file_asset_id) REFERENCES file_asset (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '대화 메시지 첨부 파일과 공유 정책';

CREATE TABLE IF NOT EXISTS collaboration_message_posting (
    id                  BIGINT   NOT NULL AUTO_INCREMENT,
    message_id          BIGINT   NOT NULL,
    application_case_id BIGINT   NOT NULL,
    created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_collab_message_posting (message_id, application_case_id),
    KEY idx_collab_message_posting_case (application_case_id),
    CONSTRAINT fk_collab_message_posting_message FOREIGN KEY (message_id) REFERENCES collaboration_message (id) ON DELETE CASCADE,
    CONSTRAINT fk_collab_message_posting_case FOREIGN KEY (application_case_id) REFERENCES application_case (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '채팅방에 공유된 사용자 공고';
