package com.careertuner.privacy.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

/** 표면 키의 점(.) 표기 상속 해석 검증 — 구체 키가 없으면 상위 키로 올라간다. */
class PrivacySurfacesTest {

    @Test
    void 구체키가_있으면_그_값을_쓴다() {
        Map<String, String> values = Map.of(
                "invite", "allow",
                "invite.GROUP.creator.anonymous", "block");
        assertThat(PrivacySurfaces.resolve(values, "invite.GROUP.creator.anonymous")).isEqualTo("block");
    }

    @Test
    void 없으면_상위로_올라간다() {
        Map<String, String> values = Map.of("invite.GROUP", "block");
        assertThat(PrivacySurfaces.resolve(values, "invite.GROUP.creator.anonymous")).isEqualTo("block");
        assertThat(PrivacySurfaces.resolve(values, "invite.PUBLIC.member")).isNull();
    }

    @Test
    void 베이스까지_없으면_null() {
        assertThat(PrivacySurfaces.resolve(Map.of(), "dm")).isNull();
        assertThat(PrivacySurfaces.resolve(null, "dm")).isNull();
    }

    @Test
    void 기본값은_차단관계만_차단() {
        assertThat(PrivacySurfaces.defaultValue(PrivacySurfaces.STRANGER)).isEqualTo("allow");
        assertThat(PrivacySurfaces.defaultValue(PrivacySurfaces.FRIEND)).isEqualTo("allow");
        assertThat(PrivacySurfaces.defaultValue(PrivacySurfaces.BLOCKED_ACCOUNT)).isEqualTo("block");
        assertThat(PrivacySurfaces.defaultValue(PrivacySurfaces.BLOCKED_IP)).isEqualTo("block");
    }

    @Test
    void 표면키_검증패턴() {
        assertThat(PrivacySurfaces.SURFACE_KEY.matcher("dm").matches()).isTrue();
        assertThat(PrivacySurfaces.SURFACE_KEY.matcher("invite.GROUP.creator.anonymous").matches()).isTrue();
        assertThat(PrivacySurfaces.SURFACE_KEY.matcher("content.post.anonymous").matches()).isTrue();
        assertThat(PrivacySurfaces.SURFACE_KEY.matcher("profile.searchMe").matches()).isTrue();
        assertThat(PrivacySurfaces.SURFACE_KEY.matcher("invite.DM").matches()).isFalse();
        assertThat(PrivacySurfaces.SURFACE_KEY.matcher("이상한키").matches()).isFalse();
    }

    @Test
    void 초대_표면키_조립() {
        assertThat(PrivacySurfaces.inviteSurface("GROUP", true, false)).isEqualTo("invite.GROUP.creator");
        assertThat(PrivacySurfaces.inviteSurface("PRIVATE", false, true)).isEqualTo("invite.PRIVATE.member.anonymous");
    }
}
