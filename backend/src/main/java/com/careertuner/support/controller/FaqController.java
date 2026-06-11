package com.careertuner.support.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.common.web.ApiResponse;
import com.careertuner.support.dto.FaqResponse;
import com.careertuner.support.service.FaqService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/support/faq")
@RequiredArgsConstructor
public class FaqController {

    private final FaqService faqService;

    @GetMapping
    public ApiResponse<List<FaqResponse>> getFaqs(
            @RequestParam(required = false) String category) {
        return ApiResponse.ok(faqService.getFaqs(category));
    }
}
