package com.careertuner.community.guideline.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.community.guideline.dto.GuidelineResponse;
import com.careertuner.community.guideline.service.GuidelineService;
import com.careertuner.common.web.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/community/guidelines")
@RequiredArgsConstructor
public class GuidelineController {

    private final GuidelineService guidelineService;

    @GetMapping("/published")
    public ApiResponse<GuidelineResponse> getPublished() {
        return ApiResponse.ok(guidelineService.getPublishedGuideline());
    }
}
