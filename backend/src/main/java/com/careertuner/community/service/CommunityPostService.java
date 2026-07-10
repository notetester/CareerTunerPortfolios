package com.careertuner.community.service;

import java.util.List;

import com.careertuner.community.dto.CreatePostRequest;
import com.careertuner.community.dto.HotPostResponse;
import com.careertuner.community.dto.PostDetailResponse;
import com.careertuner.community.dto.PostPageResponse;
import com.careertuner.community.dto.UpdatePostRequest;

public interface CommunityPostService {

    PostPageResponse getPosts(String category, String keyword, String sort, int page, int size, Long viewerId);

    /** id 목록으로 조회(챗봇 추천 모아보기). 입력 순서 보존, status·뷰어 차단 조건은 목록과 동일. 최대 20건. */
    PostPageResponse getPostsByIds(List<Long> ids, Long viewerId);

    PostDetailResponse getPostDetail(Long postId, Long currentUserId);

    Long createPost(CreatePostRequest request, Long userId);

    void updatePost(Long postId, UpdatePostRequest request, Long userId);

    void deletePost(Long postId, Long userId);

    /** viewerId(nullable): 뷰어가 차단한 작성자의 인기글 제외 — 비로그인은 무필터. */
    List<HotPostResponse> getHotPosts(Long viewerId);
}
