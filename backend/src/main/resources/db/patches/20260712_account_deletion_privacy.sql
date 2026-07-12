-- 기존 DELETED 회원도 신규 탈퇴 처리와 같은 비식별·인증 폐기 상태로 맞춘다.
-- 사용자/게시글/댓글/메시지/감사 FK 행은 삭제하지 않는다.

UPDATE users
   SET email = NULL,
       login_id = NULL,
       phone = NULL,
       phone_verified = 0,
       password = NULL,
       password_enabled = 0,
       name = '탈퇴한 사용자',
       email_verified = 0,
       deleted_at = COALESCE(deleted_at, NOW()),
       status_changed_at = COALESCE(status_changed_at, NOW()),
       status_changed_by = NULL,
       blocked_reason = NULL,
       blocked_until = NULL
 WHERE status = 'DELETED'
   AND (
       email IS NOT NULL OR login_id IS NOT NULL OR phone IS NOT NULL OR phone_verified <> 0
       OR password IS NOT NULL OR password_enabled <> 0 OR name <> '탈퇴한 사용자'
       OR email_verified <> 0 OR deleted_at IS NULL OR status_changed_by IS NOT NULL
       OR blocked_reason IS NOT NULL OR blocked_until IS NOT NULL
   );

DELETE us
  FROM user_social us
  JOIN users u ON u.id = us.user_id
 WHERE u.status = 'DELETED';

DELETE ps
  FROM push_subscription ps
  JOIN users u ON u.id = ps.user_id
 WHERE u.status = 'DELETED';

UPDATE refresh_token rt
JOIN users u ON u.id = rt.user_id
   SET rt.revoked = 1,
       rt.revoked_at = COALESCE(rt.revoked_at, NOW())
 WHERE u.status = 'DELETED'
   AND rt.revoked = 0;

UPDATE email_verification ev
JOIN users u ON u.id = ev.user_id
   SET ev.email = CONCAT('deleted-', ev.id, '@invalid.local'),
       ev.used = 1,
       ev.used_at = COALESCE(ev.used_at, NOW()),
       ev.expired_at = LEAST(ev.expired_at, NOW())
 WHERE u.status = 'DELETED'
   AND (
       ev.email <> CONCAT('deleted-', ev.id, '@invalid.local')
       OR ev.used = 0 OR ev.used_at IS NULL OR ev.expired_at > NOW()
   );

DELETE otp
  FROM sms_otp_code otp
  JOIN users u ON u.id = otp.user_id
 WHERE u.status = 'DELETED';

UPDATE user_nickname_profile np
JOIN users u ON u.id = np.user_id
   SET np.nickname = CONCAT(
           'deleted-',
           LEFT(SHA2(CONCAT('careertuner-deleted-profile:', np.id), 256), 22)
       ),
       np.avatar_file_id = NULL,
       np.bio = NULL,
       np.is_default = 0,
       np.status = 'HIDDEN'
 WHERE u.status = 'DELETED'
   AND (
       np.nickname <> CONCAT(
           'deleted-',
           LEFT(SHA2(CONCAT('careertuner-deleted-profile:', np.id), 256), 22)
       )
       OR np.avatar_file_id IS NOT NULL OR np.bio IS NOT NULL OR np.is_default <> 0
       OR np.status <> 'HIDDEN'
   );

UPDATE user_chat_profile cp
JOIN users u ON u.id = cp.user_id
   SET cp.nickname = '탈퇴한 사용자',
       cp.avatar_url = NULL,
       cp.description = NULL,
       cp.is_default = 0
 WHERE u.status = 'DELETED'
   AND (
       cp.nickname <> '탈퇴한 사용자' OR cp.avatar_url IS NOT NULL
       OR cp.description IS NOT NULL OR cp.is_default <> 0
   );

UPDATE conversation_member_profile cmp
JOIN users u ON u.id = cmp.user_id
   SET cmp.nickname_profile_id = NULL,
       cmp.anonymous = 0,
       cmp.updated_at = NOW()
 WHERE u.status = 'DELETED'
   AND (cmp.nickname_profile_id IS NOT NULL OR cmp.anonymous <> 0);

UPDATE collaboration_conversation_member cm
JOIN users u ON u.id = cm.user_id
   SET cm.status = 'LEFT',
       cm.room_nickname = NULL,
       cm.room_profile_file_id = NULL,
       cm.muted = 1,
       cm.left_at = COALESCE(cm.left_at, NOW())
 WHERE u.status = 'DELETED'
   AND cm.status = 'ACTIVE';

UPDATE collaboration_friend_request fr
JOIN users requester ON requester.id = fr.requester_id
JOIN users receiver ON receiver.id = fr.receiver_id
   SET fr.status = 'CANCELED',
       fr.responded_at = COALESCE(fr.responded_at, NOW()),
       fr.updated_at = NOW()
 WHERE fr.status = 'PENDING'
   AND (requester.status = 'DELETED' OR receiver.status = 'DELETED');

UPDATE collaboration_conversation_invite ci
LEFT JOIN users inviter ON inviter.id = ci.inviter_id
JOIN users invitee ON invitee.id = ci.invitee_id
   SET ci.status = 'CANCELED',
       ci.responded_at = COALESCE(ci.responded_at, NOW()),
       ci.updated_at = NOW()
 WHERE ci.status = 'PENDING'
   AND (inviter.status = 'DELETED' OR invitee.status = 'DELETED');

DELETE udp
  FROM user_desktop_presence udp
  JOIN users u ON u.id = udp.user_id
 WHERE u.status = 'DELETED';
