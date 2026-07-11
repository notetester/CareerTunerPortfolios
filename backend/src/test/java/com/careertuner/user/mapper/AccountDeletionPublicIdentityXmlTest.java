package com.careertuner.user.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class AccountDeletionPublicIdentityXmlTest {

    @Test
    void publicContentQueriesCarryAccountStatusForTombstoneMapping() throws Exception {
        String posts = read("community/CommunityPostMapper.xml");
        String comments = read("community/CommunityCommentMapper.xml");
        String reactions = read("community/ReactionMapper.xml");
        String collaboration = read("collaboration/CollaborationMapper.xml");
        String notifications = read("notification/NotificationMapper.xml");
        String nicknames = read("nickname/NicknameProfileMapper.xml");
        String privacy = read("privacy/PrivacyMapper.xml");

        assertThat(posts).contains("u.status AS user_status");
        assertThat(comments).contains("u.status AS user_status");
        assertThat(reactions).contains("u.status AS userStatus");
        assertThat(collaboration)
                .contains("u.status AS sender_status")
                .contains("peer_user.status AS peer_status")
                .contains("actor.status AS actor_status")
                .contains("target.status AS target_status")
                .contains("JOIN users u ON u.id = allow.user_id AND u.status = 'ACTIVE'");
        assertThat(notifications).contains("a.status AS actor_status");
        assertThat(nicknames)
                .contains("name AS name, status AS status")
                .contains("<select id=\"findAccountStatus\"");
        assertThat(privacy)
                .contains("u.status AS blocked_user_status")
                .contains("s.status AS source_user_status");
    }

    @Test
    void authenticationArtifactsCannotBeRecreatedForDeletedAccount() throws Exception {
        String auth = read("auth/AuthMapper.xml");
        String sms = read("sms/SmsOtpMapper.xml");
        String account = read("user/UserAccountMapper.xml");
        String users = read("user/UserMapper.xml");

        assertThat(auth)
                .contains("<insert id=\"insertSocial\"")
                .contains("<insert id=\"insertEmailVerification\"")
                .contains("<insert id=\"insertRefreshToken\"")
                .contains("u.status = 'ACTIVE'");
        assertThat(sms)
                .contains("<insert id=\"insert\"")
                .contains("u.status = 'ACTIVE'");
        assertThat(account)
                .contains("<update id=\"setLoginIdIfAbsent\"")
                .contains("<update id=\"updatePhone\"")
                .contains("status = 'ACTIVE'");
        assertThat(users)
                .contains("<update id=\"updatePassword\"")
                .contains("<update id=\"updateEmailAndMarkVerified\"")
                .contains("<update id=\"markPhoneVerified\"")
                .contains("status = 'ACTIVE'");
    }

    private static String read(String relative) throws Exception {
        return Files.readString(Path.of("src/main/resources/mapper", relative));
    }
}
