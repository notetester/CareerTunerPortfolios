-- C 자격증 근거 snapshot 저장 컬럼 추가.
-- 적합도 분석 생성 시 1회 수집한 자격증 근거(공식 출처·조회상태·솔직한 메시지)를 저장하고, 읽기 경로(조회)에서는
-- 이 컬럼만 읽는다(외부 API 미호출 → Q-Net 장애가 화면 조회 성능에 영향 없음).
-- nullable·무파괴 추가. 기존 판단값(fit_score/matched_skills/apply_decision 등)과 A/B/D 원본 테이블은 수정하지 않는다.
-- 롤백: ALTER TABLE fit_analysis DROP COLUMN certificate_evidence;

SET @ddl = (
    SELECT IF(COUNT(*) = 0,
        'ALTER TABLE fit_analysis ADD COLUMN certificate_evidence JSON NULL AFTER apply_decision',
        'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'fit_analysis' AND column_name = 'certificate_evidence'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
