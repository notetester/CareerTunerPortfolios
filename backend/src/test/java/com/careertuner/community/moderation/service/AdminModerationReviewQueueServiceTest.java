package com.careertuner.community.moderation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.careertuner.common.exception.BusinessException;
import com.careertuner.common.exception.ErrorCode;
import com.careertuner.community.domain.CommunityPost;
import com.careertuner.community.mapper.CommunityCommentMapper;
import com.careertuner.community.mapper.CommunityPostMapper;
import com.careertuner.community.moderation.domain.ModerationReviewQueueView;
import com.careertuner.community.moderation.mapper.CommentAiResultMapper;
import com.careertuner.community.moderation.mapper.PostAiResultMapper;

import tools.jackson.databind.ObjectMapper;

class AdminModerationReviewQueueServiceTest {

    private final PostAiResultMapper aiResultMapper = Mockito.mock(PostAiResultMapper.class);
    private final CommentAiResultMapper commentAiResultMapper = Mockito.mock(CommentAiResultMapper.class);
    private final CommunityPostMapper postMapper = Mockito.mock(CommunityPostMapper.class);
    private final CommunityCommentMapper commentMapper = Mockito.mock(CommunityCommentMapper.class);
    private final PostModerationService moderationService = Mockito.mock(PostModerationService.class);
    private final ModerationSettingService settingService = Mockito.mock(ModerationSettingService.class);
    private final AdminModerationService service = new AdminModerationService(
            aiResultMapper, commentAiResultMapper, postMapper, commentMapper,
            moderationService, settingService, new ObjectMapper());

    @Test
    void queueUsesCurrentThresholdAndStripsHtmlFromPreview() {
        when(settingService.getHideThreshold()).thenReturn(0.8);
        ModerationReviewQueueView view = new ModerationReviewQueueView();
        view.setPostId(10L);
        view.setTitle("경계 판정 글");
        view.setContent("<p>본문 <strong>미리보기</strong></p>");
        view.setAuthorName("작성자");
        view.setPostCategory("FREE");
        view.setAiCategory("abuse");
        view.setConfidence(0.72);
        when(aiResultMapper.findReviewQueue(0.8, 0, 10)).thenReturn(List.of(view));
        when(aiResultMapper.countReviewQueue(0.8)).thenReturn(1);

        var result = service.getReviewQueue(1, 10);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.items()).singleElement()
                .satisfies(item -> {
                    assertThat(item.postId()).isEqualTo(10L);
                    assertThat(item.contentPreview()).isEqualTo("본문 미리보기");
                    assertThat(item.confidence()).isEqualTo(0.72);
                });
    }

    @Test
    void hideDecisionTransitionsPublishedPostAndNotifiesOnce() {
        when(settingService.getHideThreshold()).thenReturn(0.8);
        when(aiResultMapper.recordReviewAction(10L, "HIDE", 99L, 0.8)).thenReturn(1);
        CommunityPost post = CommunityPost.builder().id(10L).userId(20L).title("경계 판정 글").build();
        when(postMapper.findById(10L)).thenReturn(post);
        when(postMapper.hideIfPublished(10L)).thenReturn(1);

        service.decideReviewQueue(99L, 10L, "HIDE");

        verify(postMapper).hideIfPublished(10L);
        verify(moderationService).sendReviewHiddenNotification(post);
    }

    @Test
    void sameHideDecisionRetryIsIdempotent() {
        when(settingService.getHideThreshold()).thenReturn(0.8);
        when(aiResultMapper.recordReviewAction(10L, "HIDE", 99L, 0.8)).thenReturn(0);
        when(aiResultMapper.findReviewAction(10L)).thenReturn("HIDE");

        service.decideReviewQueue(99L, 10L, "hide");

        verify(postMapper, never()).hideIfPublished(10L);
        verify(moderationService, never()).sendReviewHiddenNotification(Mockito.any());
    }

    @Test
    void successfulHideFollowedByRetryNotifiesExactlyOnce() {
        when(settingService.getHideThreshold()).thenReturn(0.8);
        when(aiResultMapper.recordReviewAction(10L, "HIDE", 99L, 0.8)).thenReturn(1, 0);
        when(aiResultMapper.findReviewAction(10L)).thenReturn("HIDE");
        CommunityPost post = CommunityPost.builder().id(10L).userId(20L).title("경계 판정 글").build();
        when(postMapper.findById(10L)).thenReturn(post);
        when(postMapper.hideIfPublished(10L)).thenReturn(1);

        service.decideReviewQueue(99L, 10L, "HIDE");
        service.decideReviewQueue(99L, 10L, "HIDE");

        verify(postMapper, times(1)).hideIfPublished(10L);
        verify(moderationService, times(1)).sendReviewHiddenNotification(post);
    }

    @Test
    void keepDecisionDoesNotChangePostOrNotify() {
        when(settingService.getHideThreshold()).thenReturn(0.8);
        when(aiResultMapper.recordReviewAction(10L, "KEEP", 99L, 0.8)).thenReturn(1);

        service.decideReviewQueue(99L, 10L, "KEEP");

        verify(postMapper, never()).hideIfPublished(10L);
        verify(moderationService, never()).sendReviewHiddenNotification(Mockito.any());
    }

    @Test
    void conflictingDecisionIsRejected() {
        when(settingService.getHideThreshold()).thenReturn(0.8);
        when(aiResultMapper.recordReviewAction(10L, "KEEP", 99L, 0.8)).thenReturn(0);
        when(aiResultMapper.findReviewAction(10L)).thenReturn("HIDE");

        assertThatThrownBy(() -> service.decideReviewQueue(99L, 10L, "KEEP"))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getErrorCode())
                        .isEqualTo(ErrorCode.CONFLICT));
    }
}
