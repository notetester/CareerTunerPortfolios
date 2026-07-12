-- 시연용 이번 달 AI 사용 로그(멱등). '사용량 기반 요금제 추천'(USAGE_PLAN_RECOMMENDATION)이 이서연(user3)의
-- 실사용을 근거로 UPGRADE_PLAN 을 내도록, 이번 달(현재 월) ai_usage_log 를 16건 심는다. created_at 은 이달 1일
-- 기준 오프셋이라 항상 현재 월에 들어간다(집계 기준: monthlyUsage WHERE created_at >= 이달 1일).
-- ID 9205xxx 고정 + ON DUPLICATE KEY UPDATE 무시로 재실행 안전.
INSERT INTO ai_usage_log (id, user_id, application_case_id, feature_type, status, model, credit_used, created_at) VALUES
 (9205001, 3, 920001, 'FIT_ANALYSIS',        'SUCCESS', 'careertuner-c-3b', 2, DATE_ADD(DATE_FORMAT(CURDATE(),'%Y-%m-01'), INTERVAL 1 DAY)),
 (9205002, 3, 920002, 'FIT_ANALYSIS',        'SUCCESS', 'careertuner-c-3b', 2, DATE_ADD(DATE_FORMAT(CURDATE(),'%Y-%m-01'), INTERVAL 2 DAY)),
 (9205003, 3, 920003, 'FIT_ANALYSIS',        'SUCCESS', 'careertuner-c-3b', 2, DATE_ADD(DATE_FORMAT(CURDATE(),'%Y-%m-01'), INTERVAL 3 DAY)),
 (9205004, 3, 920004, 'FIT_ANALYSIS',        'SUCCESS', 'careertuner-c-3b', 2, DATE_ADD(DATE_FORMAT(CURDATE(),'%Y-%m-01'), INTERVAL 4 DAY)),
 (9205005, 3, 920006, 'FIT_ANALYSIS',        'SUCCESS', 'careertuner-c-3b', 2, DATE_ADD(DATE_FORMAT(CURDATE(),'%Y-%m-01'), INTERVAL 5 DAY)),
 (9205006, 3, 920001, 'JOB_ANALYSIS',        'SUCCESS', 'gpt-5',            1, DATE_ADD(DATE_FORMAT(CURDATE(),'%Y-%m-01'), INTERVAL 1 DAY)),
 (9205007, 3, 920002, 'JOB_ANALYSIS',        'SUCCESS', 'gpt-5',            1, DATE_ADD(DATE_FORMAT(CURDATE(),'%Y-%m-01'), INTERVAL 2 DAY)),
 (9205008, 3, 920004, 'JOB_ANALYSIS',        'SUCCESS', 'gpt-5',            1, DATE_ADD(DATE_FORMAT(CURDATE(),'%Y-%m-01'), INTERVAL 4 DAY)),
 (9205009, 3, 920001, 'INTERVIEW_QUESTION',  'SUCCESS', 'claude-haiku',     1, DATE_ADD(DATE_FORMAT(CURDATE(),'%Y-%m-01'), INTERVAL 5 DAY)),
 (9205010, 3, 920002, 'INTERVIEW_QUESTION',  'SUCCESS', 'claude-haiku',     1, DATE_ADD(DATE_FORMAT(CURDATE(),'%Y-%m-01'), INTERVAL 6 DAY)),
 (9205011, 3, 920006, 'INTERVIEW_QUESTION',  'SUCCESS', 'claude-haiku',     1, DATE_ADD(DATE_FORMAT(CURDATE(),'%Y-%m-01'), INTERVAL 7 DAY)),
 (9205012, 3, 920001, 'CORRECTION_SELF_INTRO','SUCCESS', 'gpt-5',           2, DATE_ADD(DATE_FORMAT(CURDATE(),'%Y-%m-01'), INTERVAL 6 DAY)),
 (9205013, 3, 920003, 'CORRECTION_SELF_INTRO','SUCCESS', 'gpt-5',           2, DATE_ADD(DATE_FORMAT(CURDATE(),'%Y-%m-01'), INTERVAL 8 DAY)),
 (9205014, 3, 920001, 'COMPANY_RESEARCH',    'SUCCESS', 'gpt-5',            1, DATE_ADD(DATE_FORMAT(CURDATE(),'%Y-%m-01'), INTERVAL 3 DAY)),
 (9205015, 3, 920004, 'COMPANY_RESEARCH',    'SUCCESS', 'gpt-5',            1, DATE_ADD(DATE_FORMAT(CURDATE(),'%Y-%m-01'), INTERVAL 9 DAY)),
 (9205016, 3, NULL,   'CAREER_TREND',        'SUCCESS', 'careertuner-c-3b', 1, DATE_ADD(DATE_FORMAT(CURDATE(),'%Y-%m-01'), INTERVAL 10 DAY))
ON DUPLICATE KEY UPDATE id = id;
