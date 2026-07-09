-- 관리자 fit-analysis DB-fixture 데이터(합성, PII 0). 매 테스트 전 실행 — DELETE 후 INSERT 로 멱등.
-- 시나리오:
--   c101 카카오: f1(legacy — gate row 없음) 이 최신
--   c102 네이버: f2(구, REVIEW_REQUIRED/RESOLVED) → f3(최신, REVIEW_REQUIRED/PENDING, 정상 reasons 2건)
--   c103 토스  : f4(구, REVIEW_REQUIRED/PENDING) → f6(최신, PASSED, 빈 배열 reasons)
--   ('최신' 판정은 운영과 동일하게 MAX(id) — auto-increment 전제라 id 를 시간순으로 부여
--   c104 라인  : f7(최신, REVIEW_REQUIRED/PENDING, 깨진 JSON reasons)
-- 검토대기(최신+PENDING) 기대값 = f3 + f7 = 2

DELETE FROM fit_analysis_learning_task;
DELETE FROM admin_fit_analysis_memo;
DELETE FROM fit_analysis_gate_result;
DELETE FROM fit_analysis;
DELETE FROM application_case;
DELETE FROM users;

INSERT INTO users (id, email, name) VALUES
 (9001, 'fixture-user@careertuner.dev', '김픽스처'),
 (9101, 'fixture-admin@careertuner.dev', '한검토');

INSERT INTO application_case (id, user_id, company_name, job_title, status, is_favorite) VALUES
 (101, 9001, '카카오', '프론트엔드 개발자', 'READY', 1),
 (102, 9001, '네이버', '백엔드 개발자', 'APPLIED', 0),
 (103, 9001, '토스', '데이터 엔지니어', 'READY', 0),
 (104, 9001, '라인', '서버 개발자', 'DRAFT', 0);

INSERT INTO fit_analysis (id, application_case_id, fit_score, matched_skills, missing_skills, strategy, status, created_at) VALUES
 (1, 101, 72, '["React"]', '["TypeScript"]', 'legacy 분석(R3 이전)', 'SUCCESS', TIMESTAMP '2026-06-20 10:00:00'),
 (2, 102, 55, '["Java"]', '["Kafka"]', '구 분석', 'SUCCESS', TIMESTAMP '2026-06-25 10:00:00'),
 (3, 102, 61, '["Java"]', '["Kafka"]', '최신 분석', 'SUCCESS', TIMESTAMP '2026-07-01 10:00:00'),
 (4, 103, 40, '["SQL"]', '["Spark"]', '구 분석', 'SUCCESS', TIMESTAMP '2026-06-26 10:00:00'),
 (6, 103, 66, '["SQL","Spark"]', '[]', '최신 분석', 'SUCCESS', TIMESTAMP '2026-07-01 11:00:00'),
 (7, 104, 48, '["Kotlin"]', '["Spring Boot"]', '최신 분석', 'SUCCESS', TIMESTAMP '2026-07-01 12:00:00');

INSERT INTO fit_analysis_gate_result
 (id, fit_analysis_id, gate_status, needs_human_review, reason_count, max_severity, gate_reasons_json, evidence_gate_version, review_status, reviewed_by, reviewed_at) VALUES
 (12, 2, 'REVIEW_REQUIRED', 1, 1, 'warning',
  '[{"type":"requirement_as_owned","claim":"Kafka","reason":"공고 요구 역량을 보유로 단정","severity":"warning"}]',
  'r3-review-first', 'RESOLVED', 9101, TIMESTAMP '2026-06-26 09:00:00'),
 (13, 3, 'REVIEW_REQUIRED', 1, 2, 'critical',
  '[{"type":"requirement_as_owned","claim":"Kafka","reason":"필수 요구 역량을 보유로 단정했으나 사용자 원본 근거 없음","severity":"critical"},{"type":"matched_skill_without_user_evidence","claim":"Redis","reason":"AI 매칭 역량이 사용자 원본 근거에 없음","severity":"warning"}]',
  'r3-review-first', 'PENDING', NULL, NULL),
 (16, 4, 'REVIEW_REQUIRED', 1, 1, 'warning',
  '[{"type":"requirement_as_owned","claim":"Spark","reason":"공고 요구 역량을 보유로 단정","severity":"warning"}]',
  'r3-review-first', 'PENDING', NULL, NULL),
 (14, 6, 'PASSED', 0, 0, NULL, '[]', 'r3-review-first', 'PENDING', NULL, NULL),
 (17, 7, 'REVIEW_REQUIRED', 1, 1, 'warning', '{broken-json', 'r3-review-first', 'PENDING', NULL, NULL);

INSERT INTO admin_fit_analysis_memo (fit_analysis_id, admin_user_id, memo_type, content) VALUES
 (3, 9101, 'REANALYSIS', '재분석 필요 — Kafka 단정 검토'),
 (3, 9101, 'GENERAL', '일반 메모');

INSERT INTO fit_analysis_learning_task (fit_analysis_id, skill, title, priority, sort_order) VALUES
 (3, 'Kafka', 'Kafka 기초 학습', 'HIGH', 1);
