package com.careertuner.privacy.dto;

import java.util.List;
import java.util.Map;

/**
 * 관계 정책 문서 응답.
 * overrides = 저장된 값(표면키→allow|block, 상세 키 포함), effective = 베이스 표면의 상속 해석 결과.
 * 상세 키의 해석은 프런트가 같은 상속 규칙(점 표기 상위 탐색)으로 수행한다.
 */
public record PrivacyPolicyResponse(
        List<String> relations,
        List<String> baseSurfaces,
        Map<String, Map<String, String>> overrides,
        Map<String, Map<String, String>> effective
) {}
