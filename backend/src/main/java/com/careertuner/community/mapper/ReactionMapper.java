package com.careertuner.community.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.careertuner.community.domain.CommentReaction;
import com.careertuner.community.domain.PostReaction;

@Mapper
public interface ReactionMapper {

    PostReaction findPostReaction(@Param("userId") Long userId,
                                  @Param("postId") Long postId,
                                  @Param("reactionType") String reactionType);

    void insertPostReaction(PostReaction reaction);

    void deletePostReaction(@Param("userId") Long userId,
                            @Param("postId") Long postId,
                            @Param("reactionType") String reactionType);

    CommentReaction findCommentReaction(@Param("userId") Long userId,
                                        @Param("commentId") Long commentId,
                                        @Param("reactionType") String reactionType);

    void insertCommentReaction(CommentReaction reaction);

    void deleteCommentReaction(@Param("userId") Long userId,
                               @Param("commentId") Long commentId,
                               @Param("reactionType") String reactionType);
}
