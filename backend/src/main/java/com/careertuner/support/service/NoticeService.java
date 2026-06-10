package com.careertuner.support.service;

import java.util.List;

import com.careertuner.support.dto.NoticeDetailResponse;
import com.careertuner.support.dto.NoticeListResponse;

public interface NoticeService {

    List<NoticeListResponse> getNotices();

    NoticeDetailResponse getNoticeDetail(Long id);
}
