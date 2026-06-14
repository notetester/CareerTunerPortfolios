package com.careertuner.community.service;

import java.util.List;

import com.careertuner.community.dto.CreatePostRequest;
import com.careertuner.community.dto.HotPostResponse;
import com.careertuner.community.dto.PostDetailResponse;
import com.careertuner.community.dto.PostPageResponse;
import com.careertuner.community.dto.UpdatePostRequest;

public interface CommunityPostService {

    PostPageResponse getPosts(String category, String sort, int page, int size);

    PostDetailResponse getPostDetail(Long postId, Long currentUserId);

    Long createPost(CreatePostRequest request, Long userId);

    void updatePost(Long postId, UpdatePostRequest request, Long userId);

    void deletePost(Long postId, Long userId);

    List<HotPostResponse> getHotPosts();
}
