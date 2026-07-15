package com.careertuner.catalog.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.careertuner.catalog.dto.CatalogDtos.CertDetail;
import com.careertuner.catalog.dto.CatalogDtos.CertDetailResponse;
import com.careertuner.catalog.dto.CatalogDtos.CertSearchItem;
import com.careertuner.catalog.dto.CatalogDtos.NcsDetail;
import com.careertuner.catalog.dto.CatalogDtos.NcsSearchItem;
import com.careertuner.catalog.mapper.CatalogMapper;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * NCS·자격증 카탈로그 검색/조회(읽기 전용). 판단·생성 없이 DB 조회만 한다.
 * NCS 상세의 units 는 detail_json(중첩 구조)을 파싱해 반환한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CatalogSearchService {

    private static final int MAX_LIMIT = 50;

    private final CatalogMapper catalogMapper;
    // Spring Boot 4 가 관리하는 Jackson 3(tools.jackson) ObjectMapper 빈을 주입한다.
    // (컨벤션: Jackson 2 타입·직접 인스턴스화 금지 — JacksonUsageConventionTests.)
    private final ObjectMapper objectMapper;

    public List<NcsSearchItem> searchNcs(String q, int limit) {
        return catalogMapper.searchNcs(safe(q), clamp(limit));
    }

    public NcsDetail getNcsDetail(Long id) {
        NcsSearchItem s = catalogMapper.findNcsSummary(id);
        if (s == null) {
            return null;
        }
        Object units = parseUnits(catalogMapper.findNcsDetailJson(id));
        return new NcsDetail(s.id(), s.ncsCode(), s.majorName(), s.middleName(), s.minorName(),
                s.subName(), s.unitCount(), s.elementCount(), s.minLevel(), s.maxLevel(), units);
    }

    public List<CertSearchItem> searchCertificates(String q, String type, int limit) {
        return catalogMapper.searchCertificates(safe(q), emptyToNull(type), clamp(limit));
    }

    public CertDetailResponse getCertificateDetail(Long id) {
        CertDetail c = catalogMapper.findCertificateById(id);
        if (c == null) {
            return null;
        }
        var schedules = (c.hasSchedule() != null && c.hasSchedule() == 1)
                ? catalogMapper.findExamSchedules(c.name())
                : List.<com.careertuner.catalog.dto.CatalogDtos.ExamSchedule>of();
        return new CertDetailResponse(c, schedules);
    }

    private Object parseUnits(String detailJson) {
        if (detailJson == null || detailJson.isBlank()) {
            return List.of();
        }
        try {
            // readTree(JsonNode) 를 그대로 응답에 넣으면 Spring 직렬화기가 JsonNode 를
            // POJO 로 보고 isArray()/nodeType 같은 게터를 뱉는다. 평문 List/Map 으로 읽어 반환한다.
            return objectMapper.readValue(detailJson, Object.class);
        } catch (Exception e) {
            log.warn("NCS detail_json 파싱 실패: {}", e.getMessage());
            return List.of();
        }
    }

    private static String safe(String q) {
        return q == null ? "" : q.trim();
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static int clamp(int limit) {
        return limit <= 0 ? 30 : Math.min(limit, MAX_LIMIT);
    }
}
