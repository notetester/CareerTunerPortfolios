package com.careertuner.notification.push;

import java.util.List;

/**
 * 알림 발신자(actor)와 수신자 사이의 관계 구분.
 * 댓글·답글·쪽지·채팅 알림을 "모르는 사람/친구/기업 계정/운영자" 단위로 따로 켜고 끌 수 있게 한다.
 * COMPANY 는 기업 계정 role 도입 전에도 설정 스키마에 미리 포함해 두는 예약값이다(현재 발신자 판정에서는 나오지 않는다).
 */
public final class SenderRelation {

    public static final String STRANGER = "stranger";
    public static final String FRIEND = "friend";
    public static final String COMPANY = "company";
    public static final String OPERATOR = "operator";

    /** 설정 UI/직렬화에 노출되는 순서 고정 목록. */
    public static final List<String> ALL = List.of(STRANGER, FRIEND, COMPANY, OPERATOR);

    private SenderRelation() {
    }

    public static boolean isValid(String relation) {
        return relation != null && ALL.contains(relation);
    }
}
