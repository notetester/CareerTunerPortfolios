package com.careertuner.applicationcase.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.applicationcase.dto.ModelOptionsResponse;
import com.careertuner.applicationcase.service.ModelOptionsService;
import com.careertuner.common.security.AuthUser;
import com.careertuner.common.web.ApiResponse;

import lombok.RequiredArgsConstructor;

/**
 * 등록·재실행 화면의 단계별 모델 선택지 조회. {@code sourceType} 이 PDF/IMAGE 면 OCR 선택지를 포함하고,
 * 텍스트·URL·수동 입력(또는 미지정)이면 OCR 은 null(미적용)로 응답한다.
 */
@RestController
@RequestMapping("/api/application-cases")
@RequiredArgsConstructor
public class ModelOptionsController {

    private final ModelOptionsService modelOptionsService;

    @GetMapping("/model-options")
    public ApiResponse<ModelOptionsResponse> getModelOptions(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) String sourceType) {
        return ApiResponse.ok(modelOptionsService.modelOptions(sourceType));
    }
}
