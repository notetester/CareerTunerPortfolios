package com.careertuner.privacy.service;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 개인 차단/허용 정책의 표면(surface) 카탈로그 (docs/PERSONAL_BLOCK_POLICY.md §2).
 * 표면 키는 점(.) 표기 상속 — 값이 없으면 마지막 조각을 떼며 상위 키로 올라가 첫 non-null 을 쓴다.
 * 예: invite.GROUP.creator.anonymous → invite.GROUP.creator → invite.GROUP → invite → 기본값.
 */
public final class PrivacySurfaces {

    public static final String ALLOW = "allow";
    public static final String BLOCK = "block";

    /* 관계 클래스 — 각각 완전히 독립 열 */
    public static final String STRANGER = "stranger";
    public static final String FRIEND = "friend";
    public static final String COMPANY = "company";
    public static final String BLOCKED_ACCOUNT = "blockedAccount";
    public static final String BLOCKED_IP = "blockedIp";
    /** 운영자는 제재·공지 수단이라 개인 정책 대상이 아니다(항상 허용). */
    public static final String OPERATOR = "operator";

    public static final List<String> RELATIONS =
            List.of(STRANGER, FRIEND, COMPANY, BLOCKED_ACCOUNT, BLOCKED_IP);

    /* 다이렉트 */
    public static final String DM = "dm";
    public static final String NOTE = "note";
    public static final String FRIEND_REQUEST = "friendRequest";
    public static final String FILE_SHARE = "fileShare";
    public static final String POSTING_SHARE = "postingShare";
    /* 초대 (베이스 — 상세는 invite.{TYPE}.{creator|member}.anonymous) */
    public static final String INVITE = "invite";
    /* 콘텐츠 노출 */
    public static final String CONTENT_POST = "content.post";
    public static final String CONTENT_COMMENT = "content.comment";
    public static final String CONTENT_REPLY = "content.reply";
    public static final String CONTENT_ROOM_MESSAGE = "content.roomMessage";
    public static final String CONTENT_ALL_ANONYMOUS_ROOM_MESSAGES = "content.allAnonymousRoomMessages";
    /* 프로필 */
    public static final String PROFILE_VIEW_ME = "profile.viewMe";
    public static final String PROFILE_VISIBLE_TO_ME = "profile.visibleToMe";
    public static final String PROFILE_SEARCH_ME = "profile.searchMe";
    /* 내 활동 목록 공개 — 상대(관계별)가 내 프로필에서 볼 수 있는 항목 */
    public static final String ACTIVITY_POSTS = "activity.posts";
    public static final String ACTIVITY_COMMENTS = "activity.comments";
    public static final String ACTIVITY_REPLIES = "activity.replies";
    public static final String ACTIVITY_LIKES = "activity.likes";
    public static final String ACTIVITY_BOOKMARKS = "activity.bookmarks";
    public static final String ACTIVITY_SCRAPS = "activity.scraps";

    /** UI 매트릭스의 기본 행(베이스 키). 상세 키는 이 아래로 상속된다. */
    public static final List<String> BASE_SURFACES = List.of(
            DM, NOTE, FRIEND_REQUEST, FILE_SHARE, POSTING_SHARE,
            INVITE,
            CONTENT_POST, CONTENT_COMMENT, CONTENT_REPLY, CONTENT_ROOM_MESSAGE,
            CONTENT_ALL_ANONYMOUS_ROOM_MESSAGES,
            PROFILE_VIEW_ME, PROFILE_VISIBLE_TO_ME, PROFILE_SEARCH_ME,
            ACTIVITY_POSTS, ACTIVITY_COMMENTS, ACTIVITY_REPLIES,
            ACTIVITY_LIKES, ACTIVITY_BOOKMARKS, ACTIVITY_SCRAPS);

    /** 저장을 허용하는 표면 키 전체(상세 포함) 검증 패턴. */
    public static final Pattern SURFACE_KEY = Pattern.compile(
            "^(dm|note|friendRequest|fileShare|postingShare"
                    + "|invite(\\.(GROUP|PUBLIC|PRIVATE)(\\.(creator|member)(\\.anonymous)?)?)?"
                    + "|content\\.(post|comment|reply|roomMessage)(\\.anonymous)?"
                    + "|content\\.allAnonymousRoomMessages"
                    + "|profile\\.(viewMe|visibleToMe|searchMe)"
                    + "|activity\\.(posts|comments|replies|likes|bookmarks|scraps))$");

    /* 채팅방 차단 파생 플래그 (conversation_block.flags_json) */
    public static final String ROOM_INVITE_FROM_ROOM = "inviteFromRoom";
    public static final String ROOM_MEMBER_CREATED_INVITE = "memberCreatedRoomInvite";
    public static final String ROOM_MEMBER_JOINED_INVITE = "memberJoinedRoomInvite";
    public static final Pattern ROOM_FLAG_KEY = Pattern.compile(
            "^(inviteFromRoom|memberCreatedRoomInvite|memberJoinedRoomInvite)(\\.anonymous)?$");

    private PrivacySurfaces() {
    }

    public static boolean isBlockedRelation(String relation) {
        return BLOCKED_ACCOUNT.equals(relation) || BLOCKED_IP.equals(relation);
    }

    /** 관계별 시스템 기본값 — 차단 관계만 차단, 나머지는 허용. */
    public static String defaultValue(String relation) {
        return isBlockedRelation(relation) ? BLOCK : ALLOW;
    }

    /** 채팅방 차단 파생 플래그 기본값 — 그 방으로의 재초대만 기본 차단. */
    public static String roomFlagDefault(String flag) {
        return flag.startsWith(ROOM_INVITE_FROM_ROOM) ? BLOCK : ALLOW;
    }

    /**
     * 점 표기 상속 해석 — surface 키에서 시작해 상위로 올라가며 첫 allow/block 을 찾는다.
     * 전부 null 이면 null 반환(호출자가 기본값 적용).
     */
    public static String resolve(Map<String, String> values, String surface) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        String key = surface;
        while (key != null && !key.isEmpty()) {
            String value = values.get(key);
            if (ALLOW.equals(value) || BLOCK.equals(value)) {
                return value;
            }
            int lastDot = key.lastIndexOf('.');
            key = lastDot < 0 ? null : key.substring(0, lastDot);
        }
        return null;
    }

    /** 초대 표면 키 조립. */
    public static String inviteSurface(String roomType, boolean inviterIsCreator, boolean anonymous) {
        String key = INVITE + "." + roomType + "." + (inviterIsCreator ? "creator" : "member");
        return anonymous ? key + ".anonymous" : key;
    }
}
