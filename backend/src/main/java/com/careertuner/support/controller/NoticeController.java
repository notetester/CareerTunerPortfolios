package com.careertuner.support.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careertuner.common.web.ApiResponse;
import com.careertuner.support.dto.NoticeDetailResponse;
import com.careertuner.support.dto.NoticeListResponse;
import com.careertuner.support.service.NoticeService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/support/notices")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;

    @GetMapping
    public ApiResponse<List<NoticeListResponse>> getNotices() {
        return ApiResponse.ok(noticeService.getNotices());
    }

    @GetMapping("/{id}")
    public ApiResponse<NoticeDetailResponse> getNoticeDetail(@PathVariable Long id) {
        return ApiResponse.ok(noticeService.getNoticeDetail(id));
    }
}
