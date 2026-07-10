package com.careertuner.ai.autoprep.handler;

import java.util.List;

import org.springframework.stereotype.Component;

import com.careertuner.ai.autoprep.PrepProgress;
import com.careertuner.ai.autoprep.PrepStepContext;
import com.careertuner.ai.autoprep.PrepStepHandler;
import com.careertuner.ai.autoprep.PrepStepResult;
import com.careertuner.community.dto.HotPostResponse;
import com.careertuner.community.service.CommunityPostService;

import lombok.RequiredArgsConstructor;

/**
 * ⑥ 커뮤니티 (F). 면접 준비 맥락에서 참고할 커뮤니티 글을 큐레이션해 보여준다.
 * 슬롯에 직무가 있으면 직무 키워드 검색(제목·본문·회사·직무 LIKE, 인기순 상위 5)을 우선하고,
 * 매칭이 없거나 직무 미상이면 인기 글로 폴백한다(읽기 전용).
 */
@Component
@RequiredArgsConstructor
public class CommunityPrepHandler implements PrepStepHandler {

    private static final int JOB_MATCH_LIMIT = 5;

    private final CommunityPostService communityPostService;

    @Override
    public String key() {
        return "COMMUNITY";
    }

    @Override
    public PrepStepResult handle(PrepStepContext context, PrepProgress progress) {
        long start = System.nanoTime();
        String jobTitle = context.slots() == null ? null : normalize(context.slots().jobTitle());
        List<HotPostResponse> posts = List.of();
        if (jobTitle != null) {
            progress.substep("관련 글 검색", "직무 키워드 \"" + jobTitle + "\" 매칭");
            try {
                // getPosts 는 차단 작성자 필터를 이미 태운다. 신고 누적 블러 글은 추천 표면에서 제외.
                posts = communityPostService
                        .getPosts(null, jobTitle, "popular", 0, JOB_MATCH_LIMIT, context.userId())
                        .posts().stream()
                        .filter(p -> !p.blurred())
                        .map(p -> new HotPostResponse(p.id(), p.title(),
                                p.stats().commentCount(), p.stats().viewCount()))
                        .toList();
            } catch (RuntimeException ex) {
                // 검색 실패는 추천 품질 문제일 뿐 — 인기 글 폴백으로 완주한다.
            }
        }
        boolean jobMatched = !posts.isEmpty();
        if (!jobMatched) {
            progress.substep("인기순 정렬", "후기·팁 큐레이션");
            // 준비 콘텐츠도 사용자에게 노출되는 표면 — 본인이 차단한 작성자의 인기글은 제외한다.
            posts = communityPostService.getHotPosts(context.userId());
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        String summary = jobMatched
                ? "\"" + jobTitle + "\" 관련 커뮤니티 글 " + posts.size() + "개"
                : "참고할 커뮤니티 인기 글 " + posts.size() + "개";
        return PrepStepResult.done("COMMUNITY", summary, posts, ms);
    }

    private static String normalize(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
