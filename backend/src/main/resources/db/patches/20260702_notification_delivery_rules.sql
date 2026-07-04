-- 알림 이벤트별 플랫폼/채널 수신 설정.
-- 기존 categories_json 은 상위 카테고리 호환용으로 유지하고, rules_json 이 세부 설정을 담당한다.
ALTER TABLE notification_preference
    ADD COLUMN rules_json JSON NULL AFTER categories_json;
