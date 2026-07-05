-- 알림 발신자 관계(모르는 사람/친구/기업/운영자)별 세부 수신 설정 + 언급 감지 키워드.
-- notification.sender_relation: 관계 기반 알림(댓글·답글·쪽지·채팅)에 생성 시점 관계를 기록.
-- notification_preference.keywords_json: 알림 해제 채팅방에서도 언급으로 간주할 사용자 키워드 목록.
ALTER TABLE notification
    ADD COLUMN sender_relation VARCHAR(12) NULL AFTER target_id;

ALTER TABLE notification_preference
    ADD COLUMN keywords_json JSON NULL AFTER rules_json;
