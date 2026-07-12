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
public class CommunityPost {

    private Long id;
    private Long userId;
    private String category;
    private String title;
    private String content;
    private String companyName;
    private String jobTitle;
    private String interviewType;
    private String difficulty;
    private String status;
    private String tagsJson;
    private boolean anonymous;
    /** 작성 시 선택한 표시용 닉네임 프로필(user_nickname_profile.id). NULL 이면 계정 기본 프로필/계정명으로 폴백. */
    private Long nicknameProfileId;
    private int viewCount;
    private int commentCount;
    private int likeCount;
    private int dislikeCount;
    private Integer reportCount;
    private int recommendCount;
    private int disrecommendCount;
    private int bookmarkCount;
    private int scrapCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // JOIN으로 가져오는 작성자 정보
    private String userName;
    private String userStatus;
}
