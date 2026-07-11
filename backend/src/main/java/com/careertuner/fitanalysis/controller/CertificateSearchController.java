package com.careertuner.fitanalysis.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.common.web.ApiResponse;
import com.careertuner.fitanalysis.certificate.CertificateSearchService;
import com.careertuner.fitanalysis.dto.CertificateSearchResponse;

import lombok.RequiredArgsConstructor;

/**
 * 자격증 통합 검색 — 국가자격(오프라인 스냅샷) + 민간자격(등록정보 라이브). 검색은 조회이며 추천이 아니다.
 * 인증 사용자 대상(민간 조회가 외부 API 라 비로그인 무제한 노출은 쿼터 낭비).
 */
@RestController
@RequestMapping("/api/certificates")
@RequiredArgsConstructor
public class CertificateSearchController {

    private final CertificateSearchService certificateSearchService;

    @GetMapping("/search")
    public ApiResponse<CertificateSearchResponse> search(@RequestParam String q) {
        return ApiResponse.ok(certificateSearchService.search(q));
    }
}
