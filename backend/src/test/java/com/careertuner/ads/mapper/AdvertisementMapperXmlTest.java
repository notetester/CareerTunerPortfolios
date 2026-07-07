package com.careertuner.ads.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** 노출 쿼리의 핵심 불변식(활성·기간·플랫폼·우선순위)을 XML 텍스트로 고정한다. */
class AdvertisementMapperXmlTest {

    private String servableQuery() throws Exception {
        String xml = Files.readString(Path.of("src/main/resources/mapper/ads/AdvertisementMapper.xml"));
        int start = xml.indexOf("<select id=\"findServable\"");
        int end = xml.indexOf("</select>", start);
        assertThat(start).isGreaterThanOrEqualTo(0);
        assertThat(end).isGreaterThan(start);
        return xml.substring(start, end);
    }

    @Test
    void servableQueryFiltersActivePeriodAndPlatform() throws Exception {
        String query = servableQuery();

        assertThat(query).contains("active = 1");
        assertThat(query).contains("placement = #{placement}");
        // 기간: start<=now, end>now (NULL 은 제한 없음)
        assertThat(query).contains("start_at IS NULL");
        assertThat(query).contains("end_at IS NULL");
        // 플랫폼: ALL 광고이거나 요청 플랫폼 매치
        assertThat(query).contains("target_platform = 'ALL'");
        assertThat(query).contains("target_platform = #{platform}");
    }

    @Test
    void servableQueryOrdersByPriorityDesc() throws Exception {
        assertThat(servableQuery()).contains("ORDER BY priority DESC");
    }
}
