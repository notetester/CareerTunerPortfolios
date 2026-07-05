package com.careertuner.nickname.dto;

/**
 * 표시명 벌크 해석 입력 키 — (계정 id, 선택한 닉네임 프로필 id).
 *
 * <p>다른 도메인(community/collaboration)이 작성자/발신자 목록의 표시명을 한 번에 해석할 때 쓴다.
 * profileId 가 null 이면 계정 기본 프로필/계정명으로 폴백한다(단건 resolveDisplayName 과 동일 규칙).</p>
 */
public record DisplayNameQuery(Long accountId, Long profileId) {
}
