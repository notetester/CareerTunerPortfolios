package com.careertuner.admin.pending.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 관리자 사이드바 미처리 큐 카운트/신호 집계 매퍼(읽기 전용).
 * <p>게시글 HIDDEN 카운트는 기존 {@code CommunityPostMapper.countAll} 을 재사용하므로 여기 없다.
 */
@Mapper
public interface AdminPendingCountMapper {

    /** 미처리 신고 수: post_report + comment_report status='PENDING'. */
    int countPendingReports();

    /** 미응답 티켓 수: support_ticket status='RECEIVED'. */
    int countReceivedTickets();

    /** 자동숨김 댓글 수: community_comment status='HIDDEN'. */
    int countHiddenComments();

    /** 방치 N일 이상 미처리 신고 존재 여부(post/comment). */
    boolean existsStaleReport(@Param("days") int days);

    /** 고신뢰 toxic(신뢰도≥threshold & toxic=true)로 판정된 미처리 신고 존재 여부. */
    boolean existsHighConfidenceToxicReport(@Param("threshold") double threshold);

    /** 방치 N일 이상 미응답 티켓 존재 여부. */
    boolean existsStaleReceivedTicket(@Param("days") int days);
}
