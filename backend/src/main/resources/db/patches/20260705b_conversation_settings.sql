-- 채팅방 개설자 설정 전면 + 방 관리자(권한별) 위임 (W5)
-- collaboration_conversation 에 방 메타/공개 정책 컬럼을 추가하고,
-- 세부 권한 위임·재입장불가 강퇴(ban)·초대 허용 멤버·활동 로그 테이블을 신설한다.
-- schema.sql 은 수정하지 않으며, 이 패치 파일에만 신규 스키마를 둔다.

-- ── 방 메타/공개 정책 컬럼 (collaboration_conversation 확장) ──
-- image_file_id  : 방 프로필 사진(file_asset 재사용, 삭제 시 NULL)
-- notice         : 방 공지
-- invite_policy  : 초대 권한 정책 (OWNER_ONLY / MANAGERS / SPECIFIC_MEMBERS / ALL_MEMBERS)
-- allow_anonymous: 익명 참가 허용 여부
-- anonymous_only : 익명만 참가 가능(실명 참가 불가)

ALTER TABLE collaboration_conversation
    ADD COLUMN IF NOT EXISTS image_file_id   BIGINT       NULL AFTER description,
    ADD COLUMN IF NOT EXISTS notice          VARCHAR(1000) NULL AFTER image_file_id,
    ADD COLUMN IF NOT EXISTS invite_policy   VARCHAR(20)  NOT NULL DEFAULT 'ALL_MEMBERS' AFTER notice,
    ADD COLUMN IF NOT EXISTS allow_anonymous TINYINT(1)   NOT NULL DEFAULT 0 AFTER invite_policy,
    ADD COLUMN IF NOT EXISTS anonymous_only  TINYINT(1)   NOT NULL DEFAULT 0 AFTER allow_anonymous;

-- 초대 정책 검증 (기존 CHECK 는 그대로 두고 추가 제약만 이름 붙여 삽입)
SET @exists_invite_policy_chk := (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'collaboration_conversation'
       AND CONSTRAINT_NAME = 'chk_collab_conversation_invite_policy'
);
SET @sql_invite_policy_chk := IF(@exists_invite_policy_chk = 0,
    'ALTER TABLE collaboration_conversation ADD CONSTRAINT chk_collab_conversation_invite_policy CHECK (invite_policy IN (''OWNER_ONLY'', ''MANAGERS'', ''SPECIFIC_MEMBERS'', ''ALL_MEMBERS''))',
    'SELECT 1');
PREPARE stmt_invite_policy_chk FROM @sql_invite_policy_chk;
EXECUTE stmt_invite_policy_chk;
DEALLOCATE PREPARE stmt_invite_policy_chk;

-- 방 프로필 사진 FK (file_asset 삭제 시 방 이미지 참조만 NULL)
SET @exists_image_fk := (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'collaboration_conversation'
       AND CONSTRAINT_NAME = 'fk_collab_conversation_image'
);
SET @sql_image_fk := IF(@exists_image_fk = 0,
    'ALTER TABLE collaboration_conversation ADD CONSTRAINT fk_collab_conversation_image FOREIGN KEY (image_file_id) REFERENCES file_asset (id) ON DELETE SET NULL',
    'SELECT 1');
PREPARE stmt_image_fk FROM @sql_image_fk;
EXECUTE stmt_image_fk;
DEALLOCATE PREPARE stmt_image_fk;

-- ── 멤버별 세부 권한 (OWNER 는 전권, MANAGER 는 부여된 플래그만) ──
-- role 은 collaboration_conversation_member.role 을 유지하고, 세부 권한만 여기서 관리한다.
CREATE TABLE IF NOT EXISTS collaboration_conversation_permission (
    conversation_id     BIGINT     NOT NULL,
    user_id             BIGINT     NOT NULL,
    can_kick            TINYINT(1) NOT NULL DEFAULT 0,
    can_ban             TINYINT(1) NOT NULL DEFAULT 0,
    can_set_password    TINYINT(1) NOT NULL DEFAULT 0,
    can_invite          TINYINT(1) NOT NULL DEFAULT 0,
    can_edit_room       TINYINT(1) NOT NULL DEFAULT 0,
    can_manage_members  TINYINT(1) NOT NULL DEFAULT 0,
    granted_by          BIGINT     NULL,
    created_at          DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (conversation_id, user_id),
    KEY idx_collab_conv_permission_user (user_id),
    CONSTRAINT fk_collab_conv_permission_conversation FOREIGN KEY (conversation_id) REFERENCES collaboration_conversation (id) ON DELETE CASCADE,
    CONSTRAINT fk_collab_conv_permission_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_collab_conv_permission_granted_by FOREIGN KEY (granted_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '방 관리자(MANAGER) 세부 권한 플래그';

-- ── 재입장불가 강퇴(ban) — ban 된 유저는 재입장·재초대 불가 ──
CREATE TABLE IF NOT EXISTS collaboration_conversation_ban (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT       NOT NULL,
    user_id         BIGINT       NOT NULL,
    banned_by       BIGINT       NULL,
    reason          VARCHAR(500) NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_collab_conv_ban (conversation_id, user_id),
    KEY idx_collab_conv_ban_user (user_id),
    CONSTRAINT fk_collab_conv_ban_conversation FOREIGN KEY (conversation_id) REFERENCES collaboration_conversation (id) ON DELETE CASCADE,
    CONSTRAINT fk_collab_conv_ban_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_collab_conv_ban_banned_by FOREIGN KEY (banned_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '재입장불가 강퇴(ban) 명단 — 재입장·재초대 차단';

-- ── SPECIFIC_MEMBERS 초대 정책의 허용 멤버 목록 ──
CREATE TABLE IF NOT EXISTS collaboration_conversation_invite_allow (
    conversation_id BIGINT   NOT NULL,
    user_id         BIGINT   NOT NULL,
    granted_by      BIGINT   NULL,
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (conversation_id, user_id),
    CONSTRAINT fk_collab_conv_invite_allow_conversation FOREIGN KEY (conversation_id) REFERENCES collaboration_conversation (id) ON DELETE CASCADE,
    CONSTRAINT fk_collab_conv_invite_allow_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_collab_conv_invite_allow_granted_by FOREIGN KEY (granted_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'SPECIFIC_MEMBERS 초대 정책에서 초대 가능한 멤버 목록';

-- ── 방 활동 로그 (누가 무엇을) ──
CREATE TABLE IF NOT EXISTS collaboration_conversation_audit (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT       NOT NULL,
    actor_id        BIGINT       NULL,
    target_user_id  BIGINT       NULL,
    action          VARCHAR(50)  NOT NULL,
    detail          VARCHAR(1000) NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_collab_conv_audit_conversation (conversation_id, id DESC),
    CONSTRAINT fk_collab_conv_audit_conversation FOREIGN KEY (conversation_id) REFERENCES collaboration_conversation (id) ON DELETE CASCADE,
    CONSTRAINT fk_collab_conv_audit_actor FOREIGN KEY (actor_id) REFERENCES users (id) ON DELETE SET NULL,
    CONSTRAINT fk_collab_conv_audit_target FOREIGN KEY (target_user_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '방 설정 변경·강퇴·밴·권한 위임 등 방 활동 로그';

-- ── 익명 참가자의 방 전용 프로필/닉네임(W6 방 전용 프로필 연동 인터페이스) ──
-- 없으면 임시 익명 라벨을 쓰되, 방 전용 닉네임/설명/사진을 멤버 행 옆에 보관한다.
ALTER TABLE collaboration_conversation_member
    ADD COLUMN IF NOT EXISTS anonymous       TINYINT(1)   NOT NULL DEFAULT 0 AFTER muted,
    ADD COLUMN IF NOT EXISTS room_nickname   VARCHAR(60)  NULL AFTER anonymous,
    ADD COLUMN IF NOT EXISTS room_profile_file_id BIGINT  NULL AFTER room_nickname;

SET @exists_member_profile_fk := (
    SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
     WHERE TABLE_SCHEMA = DATABASE()
       AND TABLE_NAME = 'collaboration_conversation_member'
       AND CONSTRAINT_NAME = 'fk_collab_conv_member_room_profile'
);
SET @sql_member_profile_fk := IF(@exists_member_profile_fk = 0,
    'ALTER TABLE collaboration_conversation_member ADD CONSTRAINT fk_collab_conv_member_room_profile FOREIGN KEY (room_profile_file_id) REFERENCES file_asset (id) ON DELETE SET NULL',
    'SELECT 1');
PREPARE stmt_member_profile_fk FROM @sql_member_profile_fk;
EXECUTE stmt_member_profile_fk;
DEALLOCATE PREPARE stmt_member_profile_fk;
