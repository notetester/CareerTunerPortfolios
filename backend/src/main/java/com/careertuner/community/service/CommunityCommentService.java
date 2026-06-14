package com.careertuner.community.service;

import java.util.List;

import com.careertuner.community.dto.CommentResponse;
import com.careertuner.community.dto.CreateCommentRequest;

public interface CommunityCommentService {

    List<CommentResponse> getComments(Long postId, Long currentUserId);

    CommentResponse createComment(Long postId, CreateCommentRequest request, Long userId);

    void deleteComment(Long commentId, Long userId);
}
