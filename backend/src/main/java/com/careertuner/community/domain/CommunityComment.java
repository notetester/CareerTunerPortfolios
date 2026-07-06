package com.careertuner.community.domain;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommunityComment {

    private Long id;
    private Long postId;
    private Long userId;
    private Long parentId;
    private Long mentionUserId;   // 답글이 가리키는 대상 사용자(불변 참조). 표시명은 읽을 때 동적 렌더.
    private String content;
    private boolean anonymous;
    /** 작성 시 선택한 표시용 닉네임 프로필(user_nickname_profile.id). NULL 이면 계정 기본 프로필/계정명으로 폴백. */
    private Long nicknameProfileId;
    private String status;
    private int likeCount;
    private int dislikeCount;
    private int recommendCount;
    private int disrecommendCount;
    private int reportCount;   // 누적 신고 수(임계 이상 시 비작성자에게 블러)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // JOIN으로 가져오는 작성자 정보
    private String userName;
}
