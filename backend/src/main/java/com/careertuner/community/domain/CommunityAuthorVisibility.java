package com.careertuner.community.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 한 커뮤니티 조회 스냅샷에서 개인정보 정책으로 숨겨야 할 작성자 집합.
 * 익명·비익명 표면은 독립 정책이므로 서로 다른 집합으로 유지한다.
 */
public record CommunityAuthorVisibility(
        Set<Long> blockedNamedAuthorIds,
        Set<Long> blockedAnonymousAuthorIds
) {
    /** 개인정보 매퍼와 커뮤니티 SQL의 단일 IN 절 최대 작성자 수. 전체 집합은 여러 배치로 전량 처리한다. */
    public static final int AUTHOR_BATCH_SIZE = 500;

    public CommunityAuthorVisibility {
        blockedNamedAuthorIds = blockedNamedAuthorIds == null ? Set.of() : Set.copyOf(blockedNamedAuthorIds);
        blockedAnonymousAuthorIds = blockedAnonymousAuthorIds == null
                ? Set.of()
                : Set.copyOf(blockedAnonymousAuthorIds);
    }

    public static CommunityAuthorVisibility visibleToAll() {
        return new CommunityAuthorVisibility(Set.of(), Set.of());
    }

    /** CommunityPostMapper에 고정 1개 파라미터로 전달할 정렬된 JSON 배열. 빈 집합은 필터 생략을 위해 null이다. */
    public String blockedNamedAuthorIdsJson() {
        return toJsonArray(blockedNamedAuthorIds);
    }

    /** 익명 표면 차단 집합의 고정 1개 SQL 파라미터 JSON 배열. */
    public String blockedAnonymousAuthorIdsJson() {
        return toJsonArray(blockedAnonymousAuthorIds);
    }

    /** 임의 cap 없이 모든 id를 고정 크기 이하 집합으로 분할한다. */
    public static List<Set<Long>> partition(Set<Long> authorIds) {
        if (authorIds == null || authorIds.isEmpty()) {
            return List.of();
        }
        List<Long> authors = new ArrayList<>(authorIds);
        List<Set<Long>> batches = new ArrayList<>((authors.size() + AUTHOR_BATCH_SIZE - 1) / AUTHOR_BATCH_SIZE);
        for (int from = 0; from < authors.size(); from += AUTHOR_BATCH_SIZE) {
            int to = Math.min(from + AUTHOR_BATCH_SIZE, authors.size());
            batches.add(Set.copyOf(authors.subList(from, to)));
        }
        return List.copyOf(batches);
    }

    private static String toJsonArray(Set<Long> authorIds) {
        if (authorIds == null || authorIds.isEmpty()) {
            return null;
        }
        List<Long> sorted = new ArrayList<>(authorIds);
        sorted.sort(Long::compareTo);
        StringBuilder json = new StringBuilder(sorted.size() * 8).append('[');
        for (int index = 0; index < sorted.size(); index++) {
            if (index > 0) {
                json.append(',');
            }
            json.append(sorted.get(index));
        }
        return json.append(']').toString();
    }
}
