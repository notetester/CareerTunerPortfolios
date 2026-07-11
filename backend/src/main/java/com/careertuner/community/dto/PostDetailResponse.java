package com.careertuner.community.dto;

import java.time.LocalDateTime;
import java.util.List;

public record PostDetailResponse(
        Long id,
        String category,
        String categoryLabel,
        String title,
        String content,
        List<String> tags,
        PostListResponse.AuthorDto author,
        PostListResponse.StatsDto stats,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String companyName,
        String jobRole,
        InterviewReviewDto interviewReview,
        boolean liked,
        boolean disliked,
        boolean recommended,
        boolean disrecommended,
        boolean bookmarked,
        boolean scrapped,
        boolean subscribed,
        /** 뷰어가 차단한 작성자의 게시글(content.post) 톰스톤 여부 — true 면 본문이 안내 문구로 교체됨. */
        boolean blocked,
        /** AI 이미지 검열에서 블러 대상으로 판정된 본문 이미지(+사유) — 프런트가 해당 이미지만 블러+사유+클릭하여 보기 처리. */
        List<BlurredImage> blurredImages,
        /** 현재 뷰어 본인 글인지 — 수정/삭제 버튼 게이팅용. 익명 글은 author.id 가 null 이라 이 플래그로만 판정 가능. */
        boolean mine
) {

    /** 블러 대상 이미지 URL + 사유 카테고리(ad/pii/gross/abuse/spam, 없으면 null). */
    public record BlurredImage(String url, String category) {}

    public record InterviewReviewDto(
            String companyName,
            String jobRole,
            String interviewType,
            Integer difficulty,
            String interviewDate,
            String resultStatus,
            List<String> questions
    ) {}
}
