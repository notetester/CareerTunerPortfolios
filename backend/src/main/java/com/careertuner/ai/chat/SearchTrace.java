package com.careertuner.ai.chat;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.careertuner.ai.chat.ChatResponse.SiteLink;
import com.careertuner.community.search.PostHit;

/**
 * 한 요청 동안 툴이 실제로 돌려준 출처(커뮤니티 글 PostHit, FAQ 링크)를 기록한다.
 * <p>에이전트는 String 을 반환하므로(구조화 출력+툴 충돌 회피), links 는 모델 JSON 이 아니라
 * <b>실제 툴 출력</b>에서 접지한다. 에이전트 호출은 요청 스레드에서 동기 실행되므로 ThreadLocal 로 안전.
 * 컨트롤러가 요청 시작/끝에 clear 한다.
 */
@Component
public class SearchTrace {

    private final ThreadLocal<List<PostHit>> hits = ThreadLocal.withInitial(ArrayList::new);
    private final ThreadLocal<List<SiteLink>> faqLinks = ThreadLocal.withInitial(ArrayList::new);

    public void add(List<PostHit> found) {
        if (found != null && !found.isEmpty()) {
            hits.get().addAll(found);
        }
    }

    /** searchFaq 툴이 돌려준 FAQ 중 링크 있는 것 기록(DB 출처라 신뢰). */
    public void addFaqLinks(List<SiteLink> links) {
        if (links != null && !links.isEmpty()) {
            faqLinks.get().addAll(links);
        }
    }

    /** 이번 요청에서 검색 툴이 돌려준 글들(중복 postId 제거, 순서 보존). */
    public List<PostHit> snapshot() {
        List<PostHit> out = new ArrayList<>();
        List<Long> seen = new ArrayList<>();
        for (PostHit h : hits.get()) {
            if (!seen.contains(h.postId())) {
                seen.add(h.postId());
                out.add(h);
            }
        }
        return out;
    }

    /** 이번 요청에서 searchFaq 가 돌려준 FAQ 링크들(중복 url 제거, 순서 보존). */
    public List<SiteLink> faqLinks() {
        List<SiteLink> out = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (SiteLink l : faqLinks.get()) {
            if (l.url() != null && !seen.contains(l.url())) {
                seen.add(l.url());
                out.add(l);
            }
        }
        return out;
    }

    public void clear() {
        hits.remove();
        faqLinks.remove();
    }
}
