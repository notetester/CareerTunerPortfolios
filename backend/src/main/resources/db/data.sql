-- =====================================================================
--  CareerTuner — 개발/demo 전용 샘플 데이터
--  ※ 최초 1회(빈 DB) 적용 가정. 회원/소셜/프로필은 INSERT IGNORE 로 재실행 안전,
--    도메인 샘플(지원 건 등)은 재실행 시 중복될 수 있다.
--  ※ 비밀번호 계정 공통 비번: "Career1234!"  (BCrypt 해시는 spring-security-crypto 로 생성)
--  ※ 알려진 비밀번호의 SUPER_ADMIN이 포함되므로 운영 DB에는 절대 적용하지 않는다.
--     운영 bootstrap은 별도 one-time 계정 생성/승격 절차와 운영 비밀값을 사용한다.
-- =====================================================================

-- 회원 ---------------------------------------------------------------
INSERT IGNORE INTO users (email, password, password_enabled, name, email_verified, user_type, role, status, plan, credit) VALUES
 ('admin@careertuner.dev',       '$2a$10$Po9I2ItGfYMIYNBOB/FvuONHDtGqhRrLzFYu1B5TDzSbyjDvQfzja', 1, '관리자',  1, 'JOB_SEEKER',     'SUPER_ADMIN', 'ACTIVE', 'PRO',   999),
 ('jiwon.kim@careertuner.dev',   '$2a$10$Po9I2ItGfYMIYNBOB/FvuONHDtGqhRrLzFYu1B5TDzSbyjDvQfzja', 1, '김지원',  1, 'JOB_SEEKER',     'USER',  'ACTIVE', 'FREE',  10),
 ('seoyeon.lee@careertuner.dev', '$2a$10$Po9I2ItGfYMIYNBOB/FvuONHDtGqhRrLzFYu1B5TDzSbyjDvQfzja', 1, '이서연',  1, 'CAREER_CHANGER', 'USER',  'ACTIVE', 'BASIC', 30),
 ('minsu.park@careertuner.dev',  NULL,                                                           0, '박민수',  1, 'JOB_SEEKER',     'USER',  'ACTIVE', 'FREE',  5),
 ('pending@careertuner.dev',     '$2a$10$Po9I2ItGfYMIYNBOB/FvuONHDtGqhRrLzFYu1B5TDzSbyjDvQfzja', 1, '최유진',  0, 'JOB_SEEKER',     'USER',  'ACTIVE', 'FREE',  3);

-- 관리자 CRUD 권한 카탈로그 -----------------------------------------
-- 로컬/demo의 SUPER_ADMIN은 역할로 전체 접근한다. 아래 exact catalog와 runtime 그룹 템플릿은
-- 권한 관리 UI 검증용이며 공유·운영 DB에는 data.sql 대신 patch를 적용한다.
INSERT INTO admin_permission_menu_group (
    menu_group_code, display_name, description, display_order, active
)
VALUES
    ('MEMBER', '회원 운영', '회원 계정과 프로필 운영 권한', 10, 1),
    ('AI', 'AI 운영', 'AI 분석과 모델 운영 권한', 20, 1),
    ('BILLING', '결제 운영', '결제와 구독 운영 권한', 30, 1),
    ('CONTENT', '콘텐츠 운영', '콘텐츠와 고객지원 운영 권한', 40, 1),
    ('AUDIT', '보안·감사 운영', '보안 설정과 감사 로그 운영 권한', 50, 1),
    ('POLICY', '정책·권한 운영', '운영 정책과 관리자 권한 운영', 60, 1)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    description = VALUES(description),
    display_order = VALUES(display_order),
    active = VALUES(active);

INSERT INTO admin_permission_policy (
    permission_code, display_name, description, menu_group_code, display_order, active
)
VALUES
    ('USER_READ', '회원 조회', '회원 데이터를 조회하는 권한.', 'MEMBER', 100, 1),
    ('USER_CREATE', '회원 생성', '회원 데이터를 생성하는 권한.', 'MEMBER', 101, 1),
    ('USER_UPDATE', '회원 수정', '회원 데이터를 수정하는 권한.', 'MEMBER', 102, 1),
    ('USER_DELETE', '회원 삭제', '회원 데이터를 삭제하는 권한. 삭제 표시는 소프트 삭제로 처리한다.', 'MEMBER', 103, 1),
    ('SECURITY_READ', '보안 조회', '보안 데이터를 조회하는 권한.', 'AUDIT', 200, 1),
    ('SECURITY_CREATE', '보안 생성', '보안 데이터를 생성하는 권한.', 'AUDIT', 201, 1),
    ('SECURITY_UPDATE', '보안 수정', '보안 데이터를 수정하는 권한.', 'AUDIT', 202, 1),
    ('SECURITY_DELETE', '보안 삭제', '보안 데이터를 삭제하는 권한. 삭제 표시는 소프트 삭제로 처리한다.', 'AUDIT', 203, 1),
    ('BILLING_READ', '결제 조회', '결제 데이터를 조회하는 권한.', 'BILLING', 300, 1),
    ('BILLING_CREATE', '결제 생성', '결제 데이터를 생성하는 권한.', 'BILLING', 301, 1),
    ('BILLING_UPDATE', '결제 수정', '결제 데이터를 수정하는 권한.', 'BILLING', 302, 1),
    ('BILLING_DELETE', '결제 삭제', '결제 데이터를 삭제하는 권한. 삭제 표시는 소프트 삭제로 처리한다.', 'BILLING', 303, 1),
    ('CONTENT_READ', '콘텐츠 조회', '콘텐츠 데이터를 조회하는 권한.', 'CONTENT', 400, 1),
    ('CONTENT_CREATE', '콘텐츠 생성', '콘텐츠 데이터를 생성하는 권한.', 'CONTENT', 401, 1),
    ('CONTENT_UPDATE', '콘텐츠 수정', '콘텐츠 데이터를 수정하는 권한.', 'CONTENT', 402, 1),
    ('CONTENT_DELETE', '콘텐츠 삭제', '콘텐츠 데이터를 삭제하는 권한. 삭제 표시는 소프트 삭제로 처리한다.', 'CONTENT', 403, 1),
    ('AI_READ', 'AI 운영 조회', 'AI 운영 데이터를 조회하는 권한.', 'AI', 500, 1),
    ('AI_CREATE', 'AI 운영 생성', 'AI 운영 데이터를 생성하는 권한.', 'AI', 501, 1),
    ('AI_UPDATE', 'AI 운영 수정', 'AI 운영 데이터를 수정하는 권한.', 'AI', 502, 1),
    ('AI_DELETE', 'AI 운영 삭제', 'AI 운영 데이터를 삭제하는 권한. 삭제 표시는 소프트 삭제로 처리한다.', 'AI', 503, 1),
    ('POLICY_READ', '정책 조회', '정책 데이터를 조회하는 권한.', 'POLICY', 600, 1),
    ('POLICY_CREATE', '정책 생성', '정책 데이터를 생성하는 권한.', 'POLICY', 601, 1),
    ('POLICY_UPDATE', '정책 수정', '정책 데이터를 수정하는 권한.', 'POLICY', 602, 1),
    ('POLICY_DELETE', '정책 삭제', '정책 데이터를 삭제하는 권한. 삭제 표시는 소프트 삭제로 처리한다.', 'POLICY', 603, 1),
    ('ADMIN_PERMISSION_READ', '관리자 권한 조회', '관리자 권한 데이터를 조회하는 권한.', 'POLICY', 700, 1),
    ('ADMIN_PERMISSION_CREATE', '관리자 권한 생성', '관리자 권한 데이터를 생성하는 권한.', 'POLICY', 701, 1),
    ('ADMIN_PERMISSION_UPDATE', '관리자 권한 수정', '관리자 권한 데이터를 수정하는 권한.', 'POLICY', 702, 1),
    ('ADMIN_PERMISSION_DELETE', '관리자 권한 삭제', '관리자 권한 데이터를 삭제하는 권한. 삭제 표시는 소프트 삭제로 처리한다.', 'POLICY', 703, 1),
    ('AUDIT_READ', '감사 로그 조회', '관리자 및 보안 감사 로그를 조회하는 권한.', 'AUDIT', 800, 1)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    description = VALUES(description),
    menu_group_code = VALUES(menu_group_code),
    display_order = VALUES(display_order),
    active = VALUES(active);

INSERT INTO admin_permission_group (
    group_code, display_name, description, role_scope, display_order, active
)
VALUES
    ('MEMBER_ADMIN', '회원 운영 권한 템플릿', '회원 데이터 조회·생성·수정 기본 권한', 'ADMIN', 10, 1),
    ('SECURITY_OPERATOR', '보안 운영 권한 템플릿', '보안 데이터 조회·생성·수정 기본 권한', 'ADMIN', 20, 1),
    ('BILLING_ADMIN', '결제 운영 권한 템플릿', '결제 데이터 조회·생성·수정 기본 권한', 'ADMIN', 30, 1),
    ('CONTENT_ADMIN', '콘텐츠 운영 권한 템플릿', '콘텐츠 데이터 조회·생성·수정 기본 권한', 'ADMIN', 40, 1),
    ('AI_ADMIN', 'AI 운영 권한 템플릿', 'AI 운영 데이터 조회·생성·수정 기본 권한', 'ADMIN', 50, 1),
    ('AUDIT_ADMIN', '감사 조회 권한 템플릿', '감사 로그 조회 전용 권한', 'ADMIN', 60, 1),
    ('POLICY_ADMIN', '정책·권한 운영 템플릿', '정책과 관리자 권한 전체 CRUD를 수행하는 슈퍼 관리자 전용 권한', 'SUPER_ADMIN', 70, 1),
    ('SUPER_ADMIN_GROUP', '슈퍼 관리자 그룹', '모든 관리자 CRUD와 감사 조회 권한', 'SUPER_ADMIN', 100, 1)
ON DUPLICATE KEY UPDATE
    display_name = VALUES(display_name),
    description = VALUES(description),
    role_scope = VALUES(role_scope),
    display_order = VALUES(display_order),
    active = VALUES(active);

INSERT INTO admin_permission_group_item (group_code, permission_code, deleted_at)
VALUES
    ('MEMBER_ADMIN', 'USER_READ', NULL),
    ('MEMBER_ADMIN', 'USER_CREATE', NULL),
    ('MEMBER_ADMIN', 'USER_UPDATE', NULL),
    ('SECURITY_OPERATOR', 'SECURITY_READ', NULL),
    ('SECURITY_OPERATOR', 'SECURITY_CREATE', NULL),
    ('SECURITY_OPERATOR', 'SECURITY_UPDATE', NULL),
    ('BILLING_ADMIN', 'BILLING_READ', NULL),
    ('BILLING_ADMIN', 'BILLING_CREATE', NULL),
    ('BILLING_ADMIN', 'BILLING_UPDATE', NULL),
    ('CONTENT_ADMIN', 'CONTENT_READ', NULL),
    ('CONTENT_ADMIN', 'CONTENT_CREATE', NULL),
    ('CONTENT_ADMIN', 'CONTENT_UPDATE', NULL),
    ('AI_ADMIN', 'AI_READ', NULL),
    ('AI_ADMIN', 'AI_CREATE', NULL),
    ('AI_ADMIN', 'AI_UPDATE', NULL),
    ('AUDIT_ADMIN', 'AUDIT_READ', NULL),
    ('POLICY_ADMIN', 'POLICY_READ', NULL),
    ('POLICY_ADMIN', 'POLICY_CREATE', NULL),
    ('POLICY_ADMIN', 'POLICY_UPDATE', NULL),
    ('POLICY_ADMIN', 'POLICY_DELETE', NULL),
    ('POLICY_ADMIN', 'ADMIN_PERMISSION_READ', NULL),
    ('POLICY_ADMIN', 'ADMIN_PERMISSION_CREATE', NULL),
    ('POLICY_ADMIN', 'ADMIN_PERMISSION_UPDATE', NULL),
    ('POLICY_ADMIN', 'ADMIN_PERMISSION_DELETE', NULL),
    ('SUPER_ADMIN_GROUP', 'USER_READ', NULL),
    ('SUPER_ADMIN_GROUP', 'USER_CREATE', NULL),
    ('SUPER_ADMIN_GROUP', 'USER_UPDATE', NULL),
    ('SUPER_ADMIN_GROUP', 'USER_DELETE', NULL),
    ('SUPER_ADMIN_GROUP', 'SECURITY_READ', NULL),
    ('SUPER_ADMIN_GROUP', 'SECURITY_CREATE', NULL),
    ('SUPER_ADMIN_GROUP', 'SECURITY_UPDATE', NULL),
    ('SUPER_ADMIN_GROUP', 'SECURITY_DELETE', NULL),
    ('SUPER_ADMIN_GROUP', 'BILLING_READ', NULL),
    ('SUPER_ADMIN_GROUP', 'BILLING_CREATE', NULL),
    ('SUPER_ADMIN_GROUP', 'BILLING_UPDATE', NULL),
    ('SUPER_ADMIN_GROUP', 'BILLING_DELETE', NULL),
    ('SUPER_ADMIN_GROUP', 'CONTENT_READ', NULL),
    ('SUPER_ADMIN_GROUP', 'CONTENT_CREATE', NULL),
    ('SUPER_ADMIN_GROUP', 'CONTENT_UPDATE', NULL),
    ('SUPER_ADMIN_GROUP', 'CONTENT_DELETE', NULL),
    ('SUPER_ADMIN_GROUP', 'AI_READ', NULL),
    ('SUPER_ADMIN_GROUP', 'AI_CREATE', NULL),
    ('SUPER_ADMIN_GROUP', 'AI_UPDATE', NULL),
    ('SUPER_ADMIN_GROUP', 'AI_DELETE', NULL),
    ('SUPER_ADMIN_GROUP', 'POLICY_READ', NULL),
    ('SUPER_ADMIN_GROUP', 'POLICY_CREATE', NULL),
    ('SUPER_ADMIN_GROUP', 'POLICY_UPDATE', NULL),
    ('SUPER_ADMIN_GROUP', 'POLICY_DELETE', NULL),
    ('SUPER_ADMIN_GROUP', 'ADMIN_PERMISSION_READ', NULL),
    ('SUPER_ADMIN_GROUP', 'ADMIN_PERMISSION_CREATE', NULL),
    ('SUPER_ADMIN_GROUP', 'ADMIN_PERMISSION_UPDATE', NULL),
    ('SUPER_ADMIN_GROUP', 'ADMIN_PERMISSION_DELETE', NULL),
    ('SUPER_ADMIN_GROUP', 'AUDIT_READ', NULL)
ON DUPLICATE KEY UPDATE deleted_at = NULL;

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
 SELECT 'payment', 'AI 사용권과 크레딧은 언제 차감되나요?', 'AI 기능 사용 시 구독 사용권을 먼저 차감하고, 사용권이 부족하며 정책이 허용하는 경우에만 크레딧으로 차감됩니다.', 3, 1, a.id
 FROM users a
 WHERE a.email = 'admin@careertuner.dev'
   AND NOT EXISTS (SELECT 1 FROM faq f WHERE f.question = 'AI 사용권과 크레딧은 언제 차감되나요?');
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
