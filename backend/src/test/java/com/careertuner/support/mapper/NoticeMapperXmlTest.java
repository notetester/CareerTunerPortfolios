package com.careertuner.support.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * 공지 예약(C) — 조회 시점 판정 게이트가 SQL 에 정확히 들어갔는지 정적 검증.
 * 공개 조회(목록/상세)는 PUBLISHED + (시각 지난 SCHEDULED)만 노출하고,
 * 관리자 조회는 예약 포함 전체를 그대로 봐야 한다(게이트 없음).
 */
class NoticeMapperXmlTest {

    private static final String PUBLIC_XML = "src/main/resources/mapper/support/NoticeMapper.xml";
    private static final String ADMIN_XML = "src/main/resources/mapper/admin/notice/AdminNoticeMapper.xml";
    // 약관 LegalMapper 와 동일한 검증된 KST 비교 표현. XML 원문이라 '<' 는 &lt; 로 이스케이프돼 있다.
    private static final String KST_GATE = "scheduled_at &lt;= (UTC_TIMESTAMP() + INTERVAL 9 HOUR)";

    private String selectBody(String file, String selectId) throws Exception {
        String xml = Files.readString(Path.of(file));
        int start = xml.indexOf("<select id=\"" + selectId + "\"");
        assertThat(start).as(selectId + " 존재").isGreaterThanOrEqualTo(0);
        return xml.substring(start, xml.indexOf("</select>", start));
    }

    // ── 공개 목록: PUBLISHED 유지(무영향) + 시각 지난 SCHEDULED 포함 ──
    @Test
    void publicList_publishedUntouched_andScheduledTimeGated() throws Exception {
        String q = selectBody(PUBLIC_XML, "findAllPublished");
        assertThat(q).contains("status = 'PUBLISHED'");          // 즉시발행 무영향
        assertThat(q).contains("status = 'SCHEDULED'");          // 예약 분기 추가
        assertThat(q).contains(KST_GATE);                        // 시각 게이트(KST)
        // 정렬은 발행시각 — SCHEDULED 는 published_at 없으니 scheduled_at 로 폴백.
        assertThat(q).contains("COALESCE(published_at, scheduled_at)");
    }

    // ── 공개 상세: 목록과 동일한 게이트(시각 전엔 단건도 안 보임) ──
    @Test
    void publicDetail_hasSameTimeGate() throws Exception {
        String q = selectBody(PUBLIC_XML, "findById");
        assertThat(q).contains("status = 'PUBLISHED'");
        assertThat(q).contains("status = 'SCHEDULED'");
        assertThat(q).contains(KST_GATE);
        assertThat(q).contains("id = #{id}");
    }

    // ── 관리자 목록: 게이트 없음(예약/임시 포함 전체 노출) ──
    @Test
    void adminList_isNotTimeGated() throws Exception {
        String q = selectBody(ADMIN_XML, "findAll");
        assertThat(q).doesNotContain("UTC_TIMESTAMP");
        assertThat(q).doesNotContain("status = 'SCHEDULED'");
        assertThat(q).contains("scheduled_at"); // 컬럼은 노출(뱃지용)하되 필터는 없음
    }

    // ── 관리자 상세: 게이트 없음 ──
    @Test
    void adminDetail_isNotTimeGated() throws Exception {
        String q = selectBody(ADMIN_XML, "findById");
        assertThat(q).doesNotContain("UTC_TIMESTAMP");
        assertThat(q).contains("WHERE id = #{id}");
    }
}
