package com.careertuner.privacy.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.careertuner.privacy.mapper.PrivacyMapper;

import tools.jackson.databind.ObjectMapper;

/**
 * 개인정책 평가 실패(fail) 방향 고정 테스트 — DB/Spring 없이 모든 매퍼 호출이
 * 예외를 던지는 프록시를 주입해, 표면별 fail-open/closed 분기를 검증한다.
 *
 * 방향 규칙:
 * - 수신 표면(dm/note/friendRequest/fileShare/postingShare/invite.*) = fail-closed
 *   (평가 불가 시 차단한 상대의 접촉·전달·초대가 도달하면 안 된다)
 * - 열람측(content.*, profile.*) = fail-open (평가 불가로 콘텐츠가 사라지면 안 된다)
 */
class PrivacyPolicyFailDirectionTest {

    private PrivacyPolicyServiceImpl service;

    @BeforeEach
    void setUp() {
        PrivacyMapper throwingMapper = (PrivacyMapper) Proxy.newProxyInstance(
                PrivacyMapper.class.getClassLoader(),
                new Class<?>[]{PrivacyMapper.class},
                (proxy, method, args) -> {
                    throw new IllegalStateException("DB down (테스트 주입): " + method.getName());
                });
        service = new PrivacyPolicyServiceImpl(throwingMapper, new ObjectMapper(), "test-salt");
    }

    @Test
    void 수신표면은_평가실패시_닫힌다() {
        for (String surface : Set.of(
                PrivacySurfaces.DM,
                PrivacySurfaces.NOTE,
                PrivacySurfaces.FRIEND_REQUEST,
                PrivacySurfaces.FILE_SHARE,
                PrivacySurfaces.POSTING_SHARE,
                PrivacySurfaces.INVITE,
                "invite.GROUP.creator",
                "invite.PRIVATE.member.anonymous")) {
            assertFalse(service.allows(1L, 2L, surface), "fail-closed 여야 함: " + surface);
        }
    }

    @Test
    void 열람표면은_평가실패시_열린다() {
        for (String surface : Set.of(
                PrivacySurfaces.CONTENT_POST,
                PrivacySurfaces.CONTENT_POST + ".anonymous",
                PrivacySurfaces.CONTENT_COMMENT,
                PrivacySurfaces.CONTENT_ROOM_MESSAGE,
                PrivacySurfaces.PROFILE_VIEW_ME,
                PrivacySurfaces.PROFILE_SEARCH_ME)) {
            assertTrue(service.allows(1L, 2L, surface), "fail-open 이어야 함: " + surface);
        }
    }

    @Test
    void 본인_미상은_실패분기와_무관하게_허용() {
        assertTrue(service.allows(1L, 1L, PrivacySurfaces.DM));
        assertTrue(service.allows(null, 2L, PrivacySurfaces.DM));
        assertTrue(service.allows(1L, null, PrivacySurfaces.DM));
    }

    @Test
    void 초대판정은_파생규칙과_관계정책이_모두_실패하면_닫힌다() {
        assertFalse(service.allowsInvite(1L, 2L, 5L, "GROUP", true, false));
    }

    @Test
    void 발신자차단_알림억제는_평가실패시_차단으로_간주한다() {
        assertTrue(service.isBlockedSender(1L, 2L));
        assertFalse(service.isBlockedSender(1L, 1L)); // 본인 예외 유지
    }

    @Test
    void 표시용_관계조회는_평가실패시_무관계로_연다() {
        assertEquals(PrivacySurfaces.STRANGER, service.relationOf(1L, 2L));
    }

    @Test
    void 열람측_억제조회는_평가실패시_숨기지_않는다() {
        assertFalse(service.isConversationBlocked(1L, 10L));
        assertTrue(service.blockedAuthorsAmong(1L, Set.of(2L, 3L), PrivacySurfaces.CONTENT_POST).isEmpty());
    }
}
