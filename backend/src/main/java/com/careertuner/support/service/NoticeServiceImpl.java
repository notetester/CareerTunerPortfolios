package com.careertuner.support.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.support.domain.Notice;
import com.careertuner.support.dto.NoticeDetailResponse;
import com.careertuner.support.dto.NoticeListResponse;
import com.careertuner.support.mapper.NoticeMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NoticeServiceImpl implements NoticeService {

    private final NoticeMapper noticeMapper;

    private static final Map<String, String> CATEGORY_TAG = Map.of(
            "GENERAL", "안내",
            "UPDATE", "업데이트",
            "MAINTENANCE", "점검",
            "EVENT", "이벤트",
            "POLICY", "정책"
    );

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private String toTag(String category) {
        if (category == null) return "안내";
        return CATEGORY_TAG.getOrDefault(category, category);
    }

    private String fmtDate(LocalDateTime dt) {
        return dt != null ? dt.format(DATE_FMT) : "";
    }

    @Override
    public List<NoticeListResponse> getNotices() {
        return noticeMapper.findAllPublished().stream()
                .map(n -> new NoticeListResponse(
                        n.getId(), n.getTitle(), toTag(n.getCategory()),
                        n.isPinned(), n.getViewCount(), fmtDate(n.getCreatedAt())))
                .toList();
    }

    @Override
    @Transactional
    public NoticeDetailResponse getNoticeDetail(Long id) {
        Notice n = noticeMapper.findById(id);
        if (n == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "공지사항을 찾을 수 없습니다.");
        }
        noticeMapper.incrementViewCount(id);
        return new NoticeDetailResponse(
                n.getId(), n.getTitle(), n.getContent(), toTag(n.getCategory()),
                n.isPinned(), n.getViewCount() + 1, fmtDate(n.getCreatedAt()));
    }
}
