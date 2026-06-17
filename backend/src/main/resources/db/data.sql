-- =====================================================================
--  CareerTuner — 개발용 샘플 데이터
--  ※ 최초 1회(빈 DB) 적용 가정. 회원/소셜/프로필은 INSERT IGNORE 로 재실행 안전,
--    도메인 샘플(지원 건 등)은 재실행 시 중복될 수 있다.
--  ※ 비밀번호 계정 공통 비번: "Career1234!"  (BCrypt 해시는 spring-security-crypto 로 생성)
-- =====================================================================

-- 회원 ---------------------------------------------------------------
INSERT IGNORE INTO users (email, password, password_enabled, name, email_verified, user_type, role, status, plan, credit) VALUES
 ('admin@careertuner.dev',       '$2a$10$Po9I2ItGfYMIYNBOB/FvuONHDtGqhRrLzFYu1B5TDzSbyjDvQfzja', 1, '관리자',  1, 'JOB_SEEKER',     'ADMIN', 'ACTIVE', 'PRO',   999),
 ('jiwon.kim@careertuner.dev',   '$2a$10$Po9I2ItGfYMIYNBOB/FvuONHDtGqhRrLzFYu1B5TDzSbyjDvQfzja', 1, '김지원',  1, 'JOB_SEEKER',     'USER',  'ACTIVE', 'FREE',  10),
 ('seoyeon.lee@careertuner.dev', '$2a$10$Po9I2ItGfYMIYNBOB/FvuONHDtGqhRrLzFYu1B5TDzSbyjDvQfzja', 1, '이서연',  1, 'CAREER_CHANGER', 'USER',  'ACTIVE', 'BASIC', 30),
 ('minsu.park@careertuner.dev',  NULL,                                                           0, '박민수',  1, 'JOB_SEEKER',     'USER',  'ACTIVE', 'FREE',  5),
 ('pending@careertuner.dev',     '$2a$10$Po9I2ItGfYMIYNBOB/FvuONHDtGqhRrLzFYu1B5TDzSbyjDvQfzja', 1, '최유진',  0, 'JOB_SEEKER',     'USER',  'ACTIVE', 'FREE',  3);

-- 소셜 연동 (박민수 = 카카오 전용 계정) -------------------------------
INSERT IGNORE INTO user_social (user_id, provider, provider_user_id)
 SELECT id, 'KAKAO', 'kakao_1001' FROM users WHERE email = 'minsu.park@careertuner.dev';

-- 프로필 -------------------------------------------------------------
INSERT IGNORE INTO user_profile (user_id, desired_job, desired_industry, skills, certificates, portfolio_links, self_intro)
 SELECT id, '프론트엔드 개발자', 'IT/웹', '["React","TypeScript","JavaScript","Git"]', '["정보처리기사"]', '["https://github.com/jiwon"]', 'React 기반 웹 프로젝트 경험을 쌓아온 신입 개발자입니다.'
 FROM users WHERE email = 'jiwon.kim@careertuner.dev';
INSERT IGNORE INTO user_profile (user_id, desired_job, desired_industry, skills, certificates, portfolio_links, self_intro)
 SELECT id, '마케팅 기획', '커머스', '["GA4","SQL","콘텐츠기획"]', '["GAIQ"]', '[]', '데이터 기반 퍼포먼스 마케팅 경험을 바탕으로 기획 직무로 전환을 준비 중입니다.'
 FROM users WHERE email = 'seoyeon.lee@careertuner.dev';

-- 지원 건 (여러 직군으로 구성) ---------------------------------------
INSERT INTO application_case (user_id, company_name, job_title, posting_date, source_type, status, is_favorite)
 SELECT u.id, '카카오페이', '프론트엔드 개발자', '2026-07-01', 'TEXT', 'READY', 1
 FROM users u
 WHERE u.email = 'jiwon.kim@careertuner.dev'
   AND NOT EXISTS (
       SELECT 1 FROM application_case ac
       WHERE ac.user_id = u.id AND ac.company_name = '카카오페이' AND ac.job_title = '프론트엔드 개발자'
   );
INSERT INTO application_case (user_id, company_name, job_title, posting_date, source_type, status, is_favorite)
 SELECT u.id, '국민건강보험공단', '전산직', '2026-07-10', 'MANUAL', 'ANALYZING', 0
 FROM users u
 WHERE u.email = 'jiwon.kim@careertuner.dev'
   AND NOT EXISTS (
       SELECT 1 FROM application_case ac
       WHERE ac.user_id = u.id AND ac.company_name = '국민건강보험공단' AND ac.job_title = '전산직'
   );
INSERT INTO application_case (user_id, company_name, job_title, posting_date, source_type, status, is_favorite)
 SELECT u.id, '현대자동차', '생산관리', '2026-06-20', 'TEXT', 'DRAFT', 0
 FROM users u
 WHERE u.email = 'jiwon.kim@careertuner.dev'
   AND NOT EXISTS (
       SELECT 1 FROM application_case ac
       WHERE ac.user_id = u.id AND ac.company_name = '현대자동차' AND ac.job_title = '생산관리'
   );
INSERT INTO application_case (user_id, company_name, job_title, posting_date, source_type, status, is_favorite)
 SELECT u.id, 'CJ올리브영', '마케팅 기획', '2026-07-05', 'TEXT', 'READY', 1
 FROM users u
 WHERE u.email = 'seoyeon.lee@careertuner.dev'
   AND NOT EXISTS (
       SELECT 1 FROM application_case ac
       WHERE ac.user_id = u.id AND ac.company_name = 'CJ올리브영' AND ac.job_title = '마케팅 기획'
   );
INSERT INTO application_case (user_id, company_name, job_title, posting_date, source_type, status, is_favorite)
 SELECT u.id, '스타트업 A', '서비스 운영 매니저', NULL, 'MANUAL', 'DRAFT', 0
 FROM users u
 WHERE u.email = 'seoyeon.lee@careertuner.dev'
   AND NOT EXISTS (
       SELECT 1 FROM application_case ac
       WHERE ac.user_id = u.id AND ac.company_name = '스타트업 A' AND ac.job_title = '서비스 운영 매니저'
   );

-- 공고문 샘플 ---------------------------------------------------------
INSERT INTO job_posting (application_case_id, original_text, extracted_text, source_type)
 SELECT ac.id,
        'React 기반 웹 서비스 개발, REST API 연동, 사용자 경험 개선을 담당합니다. TypeScript와 AWS 경험을 우대합니다.',
        'React 기반 웹 서비스 개발, REST API 연동, 사용자 경험 개선을 담당합니다. TypeScript와 AWS 경험을 우대합니다.',
        'TEXT'
 FROM application_case ac JOIN users u ON ac.user_id = u.id
 WHERE u.email = 'jiwon.kim@careertuner.dev'
   AND ac.company_name = '카카오페이'
   AND NOT EXISTS (SELECT 1 FROM job_posting jp WHERE jp.application_case_id = ac.id)
 LIMIT 1;

-- 분석 예시 (카카오페이 / 프론트엔드) --------------------------------
INSERT INTO job_analysis (application_case_id, employment_type, experience_level, required_skills, preferred_skills, duties, difficulty, summary)
 SELECT ac.id, '정규직', '신입~경력 3년', '["React","JavaScript","REST API"]', '["TypeScript","Next.js","AWS"]', '웹 서비스 개발 및 유지보수', 'NORMAL', '프론트엔드 핵심 역량(React, API 연동) 중심 공고'
 FROM application_case ac JOIN users u ON ac.user_id = u.id
 WHERE u.email = 'jiwon.kim@careertuner.dev'
   AND ac.company_name = '카카오페이'
   AND NOT EXISTS (SELECT 1 FROM job_analysis ja WHERE ja.application_case_id = ac.id)
 LIMIT 1;
INSERT INTO fit_analysis (application_case_id, fit_score, matched_skills, missing_skills, recommended_study, recommended_certificates, strategy)
 SELECT ac.id, 72, '["React","REST API","Git"]', '["TypeScript","AWS"]', '["TypeScript 기본 문법 학습","AWS 배포 토이 프로젝트"]', '[]', '강점인 React 경험을 수치로 정리하고 TypeScript/AWS 보완 후 지원 권장'
 FROM application_case ac JOIN users u ON ac.user_id = u.id
 WHERE u.email = 'jiwon.kim@careertuner.dev'
   AND ac.company_name = '카카오페이'
   AND NOT EXISTS (SELECT 1 FROM fit_analysis fa WHERE fa.application_case_id = ac.id)
 LIMIT 1;

-- 커뮤니티 -----------------------------------------------------------
INSERT INTO community_post (user_id, category, title, content, company_name, job_title, interview_type, difficulty, is_anonymous)
 SELECT u.id, 'INTERVIEW_REVIEW', '카카오페이 프론트엔드 1차 면접 후기', 'React 프로젝트의 문제 해결 경험을 깊게 물어봤습니다. 수치로 답하니 반응이 좋았어요.', '카카오페이', '프론트엔드 개발자', 'FIRST', 'NORMAL', 1
 FROM users u
 WHERE u.email = 'jiwon.kim@careertuner.dev'
   AND NOT EXISTS (
       SELECT 1 FROM community_post cp
       WHERE cp.user_id = u.id AND cp.title = '카카오페이 프론트엔드 1차 면접 후기'
   );
INSERT INTO community_post (user_id, category, title, content, interview_type, difficulty, is_anonymous)
 SELECT u.id, 'QNA', '마케팅 직무 자기소개 어떻게 시작하면 좋을까요', '퍼포먼스 마케팅에서 기획으로 전환하는 스토리를 어떻게 풀어야 할지 고민입니다.', NULL, 'EASY', 0
 FROM users u
 WHERE u.email = 'seoyeon.lee@careertuner.dev'
   AND NOT EXISTS (
       SELECT 1 FROM community_post cp
       WHERE cp.user_id = u.id AND cp.title = '마케팅 직무 자기소개 어떻게 시작하면 좋을까요'
   );

-- 고객센터 FAQ/공지 ----------------------------------------------------
INSERT INTO faq (category, question, answer, sort_order, is_published, admin_id)
 SELECT 'general', 'CareerTuner는 어떤 서비스인가요?', '지원 건별로 공고, 스펙, 면접 준비 흐름을 한곳에서 관리하는 취업 전략 플랫폼입니다.', 1, 1, a.id
 FROM users a
 WHERE a.email = 'admin@careertuner.dev'
   AND NOT EXISTS (SELECT 1 FROM faq f WHERE f.question = 'CareerTuner는 어떤 서비스인가요?');
INSERT INTO faq (category, question, answer, sort_order, is_published, admin_id)
 SELECT 'account', '비밀번호를 잊어버리면 어떻게 하나요?', '로그인 화면의 비밀번호 찾기를 통해 재설정 메일을 받을 수 있습니다.', 2, 1, a.id
 FROM users a
 WHERE a.email = 'admin@careertuner.dev'
   AND NOT EXISTS (SELECT 1 FROM faq f WHERE f.question = '비밀번호를 잊어버리면 어떻게 하나요?');
INSERT INTO faq (category, question, answer, sort_order, is_published, admin_id)
 SELECT 'payment', '크레딧은 언제 차감되나요?', '사용자가 명시적으로 AI 재분석이나 생성 작업을 실행할 때 기능별 정책에 따라 차감됩니다.', 3, 1, a.id
 FROM users a
 WHERE a.email = 'admin@careertuner.dev'
   AND NOT EXISTS (SELECT 1 FROM faq f WHERE f.question = '크레딧은 언제 차감되나요?');
INSERT INTO faq (category, question, answer, sort_order, is_published, admin_id)
 SELECT 'interview', '면접 질문은 지원 건과 연결되나요?', '면접 세션은 지원 건을 선택해 생성하며, 해당 공고와 분석 결과를 기반으로 질문을 준비합니다.', 4, 1, a.id
 FROM users a
 WHERE a.email = 'admin@careertuner.dev'
   AND NOT EXISTS (SELECT 1 FROM faq f WHERE f.question = '면접 질문은 지원 건과 연결되나요?');

INSERT INTO notice (title, content, category, status, is_pinned, admin_id, published_at)
 SELECT 'CareerTuner 통합 테스트 데이터 안내', '개발용 임시 DB에 고객센터, 커뮤니티, 알림 샘플 데이터가 추가되었습니다.', 'SERVICE', 'PUBLISHED', 1, a.id, NOW()
 FROM users a
 WHERE a.email = 'admin@careertuner.dev'
   AND NOT EXISTS (SELECT 1 FROM notice n WHERE n.title = 'CareerTuner 통합 테스트 데이터 안내');
INSERT INTO notice (title, content, category, status, is_pinned, admin_id, published_at)
 SELECT '커뮤니티 운영 가이드라인 적용', '커뮤니티 신고와 모더레이션 기능 점검을 위한 기본 가이드라인이 적용되었습니다.', 'COMMUNITY', 'PUBLISHED', 0, a.id, NOW()
 FROM users a
 WHERE a.email = 'admin@careertuner.dev'
   AND NOT EXISTS (SELECT 1 FROM notice n WHERE n.title = '커뮤니티 운영 가이드라인 적용');

INSERT INTO support_ticket (user_id, subject, category, status, priority)
 SELECT u.id, '크레딧 표시가 실제 사용량과 맞는지 확인하고 싶어요', 'AI기능', 'RECEIVED', 'NORMAL'
 FROM users u
 WHERE u.email = 'jiwon.kim@careertuner.dev'
   AND NOT EXISTS (
       SELECT 1 FROM support_ticket t
       WHERE t.user_id = u.id AND t.subject = '크레딧 표시가 실제 사용량과 맞는지 확인하고 싶어요'
   );
INSERT INTO support_ticket_message (ticket_id, sender_type, sender_id, content, is_internal)
 SELECT t.id, 'USER', t.user_id, '대시보드와 결제 화면의 크레딧 숫자가 같은 기준인지 궁금합니다.', 0
 FROM support_ticket t
 JOIN users u ON u.id = t.user_id
 WHERE u.email = 'jiwon.kim@careertuner.dev'
   AND t.subject = '크레딧 표시가 실제 사용량과 맞는지 확인하고 싶어요'
   AND NOT EXISTS (
       SELECT 1 FROM support_ticket_message m
       WHERE m.ticket_id = t.id AND m.sender_type = 'USER'
   );
-- 관리자 답변 + 답변완료 상태: 사용자 문의 내역 화면(내 문의 내역)에서 답변 스레드를 보여주기 위한 시드
INSERT INTO support_ticket_message (ticket_id, sender_type, sender_id, content, is_internal)
 SELECT t.id, 'ADMIN', a.id,
        '크레딧 숫자는 대시보드와 결제 화면 모두 ai_usage_log 차감 기준으로 동일합니다. 자동 캐시 재생성은 차감되지 않고, 명시적 재분석만 1크레딧 차감됩니다.', 0
 FROM support_ticket t
 JOIN users u ON u.id = t.user_id
 JOIN users a ON a.email = 'admin@careertuner.dev'
 WHERE u.email = 'jiwon.kim@careertuner.dev'
   AND t.subject = '크레딧 표시가 실제 사용량과 맞는지 확인하고 싶어요'
   AND NOT EXISTS (
       SELECT 1 FROM support_ticket_message m
       WHERE m.ticket_id = t.id AND m.sender_type = 'ADMIN'
   );
UPDATE support_ticket t
 JOIN users u ON u.id = t.user_id
   SET t.status = 'ANSWERED'
 WHERE u.email = 'jiwon.kim@careertuner.dev'
   AND t.subject = '크레딧 표시가 실제 사용량과 맞는지 확인하고 싶어요'
   AND t.status <> 'ANSWERED';

-- 커뮤니티 댓글/모더레이션 샘플 ---------------------------------------
INSERT INTO community_comment (post_id, user_id, content, is_anonymous, status)
 SELECT p.id, u.id, '후기 감사합니다. React 프로젝트 수치화 예시가 특히 도움이 됐어요.', 0, 'PUBLISHED'
 FROM community_post p
 JOIN users u ON u.email = 'seoyeon.lee@careertuner.dev'
 WHERE p.title = '카카오페이 프론트엔드 1차 면접 후기'
   AND NOT EXISTS (
       SELECT 1 FROM community_comment c
       WHERE c.post_id = p.id AND c.user_id = u.id AND c.content = '후기 감사합니다. React 프로젝트 수치화 예시가 특히 도움이 됐어요.'
   );

UPDATE community_post p
   SET comment_count = (SELECT COUNT(*) FROM community_comment c WHERE c.post_id = p.id AND c.status = 'PUBLISHED')
 WHERE p.title IN ('카카오페이 프론트엔드 1차 면접 후기', '마케팅 직무 자기소개 어떻게 시작하면 좋을까요');

INSERT INTO post_ai_result (post_id, task_type, status, result_json, model, attempt_count, completed_at)
 SELECT p.id, 'MODERATION', 'COMPLETED',
        '{"toxic":false,"category":"normal","confidence":0.96,"reasons":["개발용 정상 후기 샘플"]}',
        'sample-rule', 1, NOW()
 FROM community_post p
 WHERE p.title = '카카오페이 프론트엔드 1차 면접 후기'
   AND NOT EXISTS (SELECT 1 FROM post_ai_result r WHERE r.post_id = p.id AND r.task_type = 'MODERATION');

INSERT INTO ai_moderation_setting (id) VALUES (1)
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO community_guideline (version_label, summary, lede, oks_json, nos_json, rules_json, params_json, status, enforce_type, published_at)
SELECT 'v1.0', '최초 제정 — 선 게시·후 검토 원칙',
       'CareerTuner 커뮤니티는 면접·취업 경험을 솔직하게 나누는 곳입니다. 실제 피해를 주는 행동만 좁고 명확하게 금지합니다.',
       '["회사·전형에 대한 경험 공유","면접 질문과 과정 복기","직무 준비 팁 공유"]',
       '["특정인을 알아볼 수 있게 쓰는 것","인신공격·혐오 표현","허위 후기와 광고"]',
       '[{"t":"개인 특정·신상 노출","s":0,"b":"실명, 연락처, 부서·직급·시기 조합으로 특정인을 알아볼 수 있는 서술을 금지합니다."},{"t":"인신공격·혐오 표현","s":0,"b":"사람이나 집단을 겨냥한 모욕·위협·비하 표현을 금지합니다."},{"t":"허위 사실·조작된 후기","s":0,"b":"경험하지 않은 전형 후기나 의도적인 평판 조작을 금지합니다."}]',
       '{"blind":3,"sla":24,"expire":90,"s1":7,"s2":30,"appeal":30}',
       'PUBLISHED', 'IMMEDIATE', NOW()
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM community_guideline g WHERE g.version_label = 'v1.0');

-- 알림 샘플 -----------------------------------------------------------
INSERT INTO notification (user_id, actor_id, type, target_type, target_id, title, message, link, is_read)
 SELECT u.id, a.id, 'NOTICE', 'NOTICE', n.id, '새 공지사항이 등록됐어요', n.title, CONCAT('/support/notices/', n.id), 0
 FROM users u
 JOIN users a ON a.email = 'admin@careertuner.dev'
 JOIN notice n ON n.title = 'CareerTuner 통합 테스트 데이터 안내'
 WHERE u.email = 'jiwon.kim@careertuner.dev'
   AND NOT EXISTS (
       SELECT 1 FROM notification x
       WHERE x.user_id = u.id AND x.type = 'NOTICE' AND x.target_type = 'NOTICE' AND x.target_id = n.id
   );
INSERT INTO notification (user_id, actor_id, type, target_type, target_id, title, message, link, is_read)
 SELECT u.id, c.user_id, 'COMMENT', 'POST', p.id, '게시글에 댓글이 달렸어요', '카카오페이 면접 후기 글에 새 댓글이 있습니다.', CONCAT('/community/post/', p.id), 0
 FROM users u
 JOIN community_post p ON p.title = '카카오페이 프론트엔드 1차 면접 후기'
 JOIN community_comment c ON c.post_id = p.id
 WHERE u.email = 'jiwon.kim@careertuner.dev'
   AND NOT EXISTS (
       SELECT 1 FROM notification x
       WHERE x.user_id = u.id AND x.type = 'COMMENT' AND x.target_type = 'POST' AND x.target_id = p.id
   )
 LIMIT 1;

-- 면접 RAG 지식베이스 샘플 (관리자 지식 관리 화면이 비어 보이지 않도록) -----
INSERT INTO interview_knowledge (kind, title, content, source, indexed)
 SELECT 'RUBRIC', 'STAR 기법 평가 기준',
        '면접 답변은 Situation-Task-Action-Result 구조를 갖추었는지, 구체적 수치/성과가 드러나는지로 평가한다. 두루뭉술한 답변은 감점.',
        '내부 평가 가이드', 0
 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM interview_knowledge WHERE title = 'STAR 기법 평가 기준');
INSERT INTO interview_knowledge (kind, title, content, source, indexed)
 SELECT 'QUESTION_BANK', '백엔드 공통 질문',
        '트랜잭션 격리수준, 인덱스 동작, N+1 문제와 해결, REST 설계 원칙은 백엔드 직무 단골 질문이다.',
        '직무 질문 은행', 0
 FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM interview_knowledge WHERE title = '백엔드 공통 질문');
