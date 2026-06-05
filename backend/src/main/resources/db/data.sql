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
