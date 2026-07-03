package com.careertuner.community.dto;

import java.time.LocalDateTime;

public record CommentResponse(
        Long id,
        Long postId,
        Long parentId,
        String mentionLabel,   // 답글 대상 표시명(@익명N). 서버가 현재 익명번호로 동적 산출. 없으면 null.
        PostListResponse.AuthorDto author,
        String content,
        int likeCount,
        boolean isAuthor,   // 이 댓글이 게시글 작성자(OP)의 것인지 — "작성자" 배지용
        boolean mine,       // 현재 사용자 본인 댓글인지 — 수정/삭제 버튼 게이팅용(익명이라 author.id null이어도 판정 가능)
        LocalDateTime createdAt,
        boolean liked,
        boolean isDeleted,  // 삭제/숨김 tombstone 여부. true면 본문·작성자·멘션은 비식별 처리되어 내려간다.
        boolean blocked     // 뷰어가 차단한 작성자의 댓글(content.comment/reply) tombstone 여부. 기본 false.
) {}
