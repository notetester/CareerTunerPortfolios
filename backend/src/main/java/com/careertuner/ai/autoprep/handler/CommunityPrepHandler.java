package com.careertuner.ai.autoprep.handler;

import java.util.List;

import org.springframework.stereotype.Component;

import com.careertuner.ai.autoprep.PrepStepContext;
import com.careertuner.ai.autoprep.PrepStepHandler;
import com.careertuner.ai.autoprep.PrepStepResult;
import com.careertuner.community.dto.HotPostResponse;
import com.careertuner.community.service.CommunityPostService;

import lombok.RequiredArgsConstructor;

/**
 * ⑥ 커뮤니티 (F). 면접 준비 맥락에서 참고할 인기 글(면접 후기·취업 팁 등)을 큐레이션해 보여준다.
 * 별도 추천 엔진이 없어 현재는 인기 글을 모아 제공한다(읽기 전용).
 */
@Component
@RequiredArgsConstructor
public class CommunityPrepHandler implements PrepStepHandler {

    private final CommunityPostService communityPostService;

    @Override
    public String key() {
        return "COMMUNITY";
    }

    @Override
    public PrepStepResult handle(PrepStepContext context) {
        long start = System.nanoTime();
        List<HotPostResponse> hotPosts = communityPostService.getHotPosts();
        long ms = (System.nanoTime() - start) / 1_000_000;
        return PrepStepResult.done("COMMUNITY",
                "참고할 커뮤니티 인기 글 " + hotPosts.size() + "개", hotPosts, ms);
    }
}
