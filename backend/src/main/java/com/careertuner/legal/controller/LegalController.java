package com.careertuner.legal.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.common.web.ApiResponse;
import com.careertuner.legal.dto.LegalDocResponse;
import com.careertuner.legal.service.LegalService;

import lombok.RequiredArgsConstructor;

/**
 * 공개 법적 문서 조회 (permitAll). 지원하는 슬러그는 {@link com.careertuner.legal.domain.LegalDocType}에서 관리한다.
 */
@RestController
@RequestMapping("/api/legal")
@RequiredArgsConstructor
public class LegalController {

    private final LegalService legalService;

    @GetMapping("/{docType}")
    public ApiResponse<LegalDocResponse> getDoc(@PathVariable String docType) {
        return ApiResponse.ok(legalService.getPublicDoc(docType));
    }
}
