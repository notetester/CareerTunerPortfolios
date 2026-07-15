package com.careertuner.catalog.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.catalog.dto.CatalogDtos.CertDetailResponse;
import com.careertuner.catalog.dto.CatalogDtos.NcsDetail;
import com.careertuner.catalog.service.CatalogSearchService;
import com.careertuner.common.web.ApiResponse;

import lombok.RequiredArgsConstructor;

/**
 * NCS 직무능력표준 · 자격증 카탈로그 공개 검색/조회.
 * 비로그인도 참고할 수 있는 레퍼런스 데이터라 permitAll(SecurityConfig).
 */
@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogSearchService catalogSearchService;

    /** NCS 세분류 검색(세분류명·능력단위·기술 키워드). */
    @GetMapping("/ncs")
    public ApiResponse<?> searchNcs(@RequestParam(required = false) String q,
                                    @RequestParam(defaultValue = "30") int limit) {
        return ApiResponse.ok(catalogSearchService.searchNcs(q, limit));
    }

    /** NCS 세분류 상세(능력단위→요소→수행준거/지식/기술/태도). */
    @GetMapping("/ncs/{id}")
    public ApiResponse<NcsDetail> ncsDetail(@PathVariable Long id) {
        NcsDetail detail = catalogSearchService.getNcsDetail(id);
        return detail == null
                ? ApiResponse.error("NOT_FOUND", "해당 NCS 세분류를 찾을 수 없습니다.")
                : ApiResponse.ok(detail);
    }

    /** 자격증 검색(이름·설명 키워드, type=NATIONAL_TECH/NATIONAL_PROF/PRIVATE 필터). */
    @GetMapping("/certificates")
    public ApiResponse<?> searchCertificates(@RequestParam(required = false) String q,
                                             @RequestParam(required = false) String type,
                                             @RequestParam(defaultValue = "30") int limit) {
        return ApiResponse.ok(catalogSearchService.searchCertificates(q, type, limit));
    }

    /** 자격증 상세(설명 + 국가 시험일정). */
    @GetMapping("/certificates/{id}")
    public ApiResponse<CertDetailResponse> certificateDetail(@PathVariable Long id) {
        CertDetailResponse detail = catalogSearchService.getCertificateDetail(id);
        return detail == null
                ? ApiResponse.error("NOT_FOUND", "해당 자격증을 찾을 수 없습니다.")
                : ApiResponse.ok(detail);
    }
}
