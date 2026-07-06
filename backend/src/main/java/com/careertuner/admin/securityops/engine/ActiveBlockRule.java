package com.careertuner.admin.securityops.engine;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 런타임 차단 캐시에 실리는 활성 규칙 스냅샷.
 *
 * <p>파일 캐시로 직렬화/역직렬화되므로 기본 생성자를 가진 가변 POJO 로 둔다.
 * 값은 {@code ruleType} 에 따라 {@code ruleValue} 문자열을 해석한다:
 * IP=단일 IP, CIDR="a.b.c.d/n", IP_RANGE="start~end"(또는 '-'), COUNTRY=ISO2, ASN=숫자.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActiveBlockRule {
    private Long id;
    private String ruleType;
    private String ruleValue;
    private String actionType;
    private String scope;
    private int priority;
    private String reason;
    private String category;
}
