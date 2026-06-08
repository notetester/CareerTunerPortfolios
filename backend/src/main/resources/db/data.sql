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

-- C 담당 분석 화면 검증용 추가 샘플 -----------------------------------
-- B/D/E 담당 결과가 이미 생성됐다는 전제로 C 화면에서 참조하는 분석·면접·사용량 데이터를 채운다.
INSERT INTO application_case (user_id, company_name, job_title, posting_date, source_type, status, is_favorite)
 SELECT u.id, '토스', 'Frontend Developer', DATE_ADD(CURRENT_DATE, INTERVAL 18 DAY), 'TEXT', 'READY', 1
 FROM users u
 WHERE u.email = 'jiwon.kim@careertuner.dev'
   AND NOT EXISTS (
       SELECT 1 FROM application_case ac
       WHERE ac.user_id = u.id AND ac.company_name = '토스' AND ac.job_title = 'Frontend Developer'
   );
INSERT INTO application_case (user_id, company_name, job_title, posting_date, source_type, status, is_favorite)
 SELECT u.id, '라인플러스', 'Fullstack Developer', DATE_ADD(CURRENT_DATE, INTERVAL 28 DAY), 'TEXT', 'APPLIED', 0
 FROM users u
 WHERE u.email = 'jiwon.kim@careertuner.dev'
   AND NOT EXISTS (
       SELECT 1 FROM application_case ac
       WHERE ac.user_id = u.id AND ac.company_name = '라인플러스' AND ac.job_title = 'Fullstack Developer'
   );

UPDATE application_case ac
INNER JOIN users u ON u.id = ac.user_id
SET ac.status = CASE
        WHEN ac.company_name = '국민건강보험공단' THEN 'READY'
        WHEN ac.company_name = '현대자동차' THEN 'READY'
        ELSE ac.status
    END
WHERE u.email = 'jiwon.kim@careertuner.dev'
  AND ac.company_name IN ('국민건강보험공단', '현대자동차');

INSERT INTO job_posting (application_case_id, original_text, extracted_text, source_type)
 SELECT ac.id,
        'React, TypeScript 기반 금융 서비스 화면 개발과 A/B 테스트, 성능 최적화를 담당합니다. 테스트 자동화와 대규모 트래픽 경험을 우대합니다.',
        'React, TypeScript 기반 금융 서비스 화면 개발과 A/B 테스트, 성능 최적화를 담당합니다. 테스트 자동화와 대규모 트래픽 경험을 우대합니다.',
        'TEXT'
 FROM application_case ac JOIN users u ON ac.user_id = u.id
 WHERE u.email = 'jiwon.kim@careertuner.dev'
   AND ac.company_name = '토스'
   AND NOT EXISTS (SELECT 1 FROM job_posting jp WHERE jp.application_case_id = ac.id)
 LIMIT 1;
INSERT INTO job_posting (application_case_id, original_text, extracted_text, source_type)
 SELECT ac.id,
        'Spring Boot와 React를 활용한 사내 플랫폼 개발, CI/CD 운영, 장애 대응 경험을 요구합니다.',
        'Spring Boot와 React를 활용한 사내 플랫폼 개발, CI/CD 운영, 장애 대응 경험을 요구합니다.',
        'TEXT'
 FROM application_case ac JOIN users u ON ac.user_id = u.id
 WHERE u.email = 'jiwon.kim@careertuner.dev'
   AND ac.company_name = '라인플러스'
   AND NOT EXISTS (SELECT 1 FROM job_posting jp WHERE jp.application_case_id = ac.id)
 LIMIT 1;

INSERT INTO job_analysis (application_case_id, employment_type, experience_level, required_skills, preferred_skills, duties, qualifications, difficulty, summary)
 SELECT ac.id, '정규직', '신입~경력 3년', '["Java","Spring Boot","SQL","정보처리기사"]', '["공공 SI","보안","MyBatis"]',
        '공공 서비스 전산 시스템 개발과 운영, SQL 기반 데이터 처리, 장애 대응',
        'Java 웹 개발 기초와 관계형 DB 활용 경험, 공공기관 정보보안 기준 이해',
        'NORMAL',
        'Java/Spring 기반 공공 전산 운영 역량과 SQL 정확도가 중요한 공고'
 FROM application_case ac JOIN users u ON ac.user_id = u.id
 WHERE u.email = 'jiwon.kim@careertuner.dev'
   AND ac.company_name = '국민건강보험공단'
   AND NOT EXISTS (SELECT 1 FROM job_analysis ja WHERE ja.application_case_id = ac.id)
 LIMIT 1;
INSERT INTO job_analysis (application_case_id, employment_type, experience_level, required_skills, preferred_skills, duties, qualifications, difficulty, summary)
 SELECT ac.id, '정규직', '신입', '["Excel","데이터 분석","생산관리","협업"]', '["Python","제조 도메인","품질관리"]',
        '생산 계획 수립, 공정 지표 관리, 현업 부서와 일정 조율',
        '제조 공정 흐름 이해와 데이터 기반 문제 해결 경험',
        'HARD',
        '개발 역량보다 제조 도메인 이해와 운영 지표 관리 경험이 중요한 공고'
 FROM application_case ac JOIN users u ON ac.user_id = u.id
 WHERE u.email = 'jiwon.kim@careertuner.dev'
   AND ac.company_name = '현대자동차'
   AND NOT EXISTS (SELECT 1 FROM job_analysis ja WHERE ja.application_case_id = ac.id)
 LIMIT 1;
INSERT INTO job_analysis (application_case_id, employment_type, experience_level, required_skills, preferred_skills, duties, qualifications, difficulty, summary)
 SELECT ac.id, '정규직', '경력 1~4년', '["React","TypeScript","성능 최적화","실험 설계"]', '["Playwright","대규모 트래픽","금융 도메인"]',
        '금융 서비스 프런트엔드 개발, 사용자 실험, Core Web Vitals 개선',
        'React/TypeScript 실무 경험과 데이터 기반 UI 개선 경험',
        'HARD',
        '프런트엔드 기본기 위에 실험 설계와 성능 개선 사례를 요구하는 공고'
 FROM application_case ac JOIN users u ON ac.user_id = u.id
 WHERE u.email = 'jiwon.kim@careertuner.dev'
   AND ac.company_name = '토스'
   AND NOT EXISTS (SELECT 1 FROM job_analysis ja WHERE ja.application_case_id = ac.id)
 LIMIT 1;
INSERT INTO job_analysis (application_case_id, employment_type, experience_level, required_skills, preferred_skills, duties, qualifications, difficulty, summary)
 SELECT ac.id, '정규직', '신입~경력 3년', '["React","Spring Boot","REST API","SQL"]', '["CI/CD","Node.js","장애 대응"]',
        '풀스택 서비스 개발, API 연동, 배포 자동화, 서비스 운영',
        '프런트와 백엔드를 함께 이해하고 운영 품질을 개선한 경험',
        'NORMAL',
        'React와 Spring Boot를 함께 쓰는 서비스 운영형 풀스택 공고'
 FROM application_case ac JOIN users u ON ac.user_id = u.id
 WHERE u.email = 'jiwon.kim@careertuner.dev'
   AND ac.company_name = '라인플러스'
   AND NOT EXISTS (SELECT 1 FROM job_analysis ja WHERE ja.application_case_id = ac.id)
 LIMIT 1;
INSERT INTO job_analysis (application_case_id, employment_type, experience_level, required_skills, preferred_skills, duties, qualifications, difficulty, summary)
 SELECT ac.id, '정규직', '신입~경력 2년', '["GA4","SQL","캠페인 기획","콘텐츠 기획"]', '["CRM","뷰티 커머스","A/B 테스트"]',
        '마케팅 캠페인 기획, 고객 세그먼트 분석, 성과 리포트 작성',
        '데이터 기반 캠페인 개선 경험과 커머스 고객 이해',
        'NORMAL',
        '퍼포먼스 데이터와 콘텐츠 기획을 함께 보는 커머스 마케팅 공고'
 FROM application_case ac JOIN users u ON ac.user_id = u.id
 WHERE u.email = 'seoyeon.lee@careertuner.dev'
   AND ac.company_name = 'CJ올리브영'
   AND NOT EXISTS (SELECT 1 FROM job_analysis ja WHERE ja.application_case_id = ac.id)
 LIMIT 1;
INSERT INTO job_analysis (application_case_id, employment_type, experience_level, required_skills, preferred_skills, duties, qualifications, difficulty, summary)
 SELECT ac.id, '계약직', '경력 1~3년', '["고객 응대","운영 프로세스","VOC 분석","문서화"]', '["SQL","Notion","CS 자동화"]',
        '서비스 운영 정책 정리, VOC 분석, 고객 문의 흐름 개선',
        '고객 접점 경험과 반복 업무를 구조화한 사례',
        'EASY',
        '분석 역량보다 운영 안정화와 문서화 습관이 중요한 서비스 운영 공고'
 FROM application_case ac JOIN users u ON ac.user_id = u.id
 WHERE u.email = 'seoyeon.lee@careertuner.dev'
   AND ac.company_name = '스타트업 A'
   AND NOT EXISTS (SELECT 1 FROM job_analysis ja WHERE ja.application_case_id = ac.id)
 LIMIT 1;

INSERT INTO fit_analysis (application_case_id, fit_score, matched_skills, missing_skills, recommended_study, recommended_certificates, strategy)
 SELECT ac.id, 61, '["Java","정보처리기사","Git"]', '["Spring Boot","공공 SI 경험","SQL 튜닝"]',
        '["Spring Boot CRUD 프로젝트 1개 완성","MyBatis 동적 SQL과 인덱스 기초 정리","공공기관 보안 가이드 요약"]',
        '["SQLD"]',
        '정보처리기사와 Java 기초를 강점으로 두되, Spring Boot와 SQL 튜닝 보완 계획을 지원서 전면에 배치'
 FROM application_case ac JOIN users u ON ac.user_id = u.id
 WHERE u.email = 'jiwon.kim@careertuner.dev'
   AND ac.company_name = '국민건강보험공단'
   AND NOT EXISTS (SELECT 1 FROM fit_analysis fa WHERE fa.application_case_id = ac.id)
 LIMIT 1;
INSERT INTO fit_analysis (application_case_id, fit_score, matched_skills, missing_skills, recommended_study, recommended_certificates, strategy)
 SELECT ac.id, 48, '["Excel","협업","Git"]', '["생산관리 경험","데이터 분석 사례","제조 도메인 이해"]',
        '["생산관리 KPI와 공정 용어 정리","Excel 피벗/파워쿼리로 공정 데이터 샘플 분석","제조 현장 개선 사례 3개 요약"]',
        '["컴퓨터활용능력 1급"]',
        '직무 전환 지원으로 보고, 개발 프로젝트의 일정·품질 관리 경험을 생산관리 언어로 번역해서 제시'
 FROM application_case ac JOIN users u ON ac.user_id = u.id
 WHERE u.email = 'jiwon.kim@careertuner.dev'
   AND ac.company_name = '현대자동차'
   AND NOT EXISTS (SELECT 1 FROM fit_analysis fa WHERE fa.application_case_id = ac.id)
 LIMIT 1;
INSERT INTO fit_analysis (application_case_id, fit_score, matched_skills, missing_skills, recommended_study, recommended_certificates, strategy)
 SELECT ac.id, 78, '["React","TypeScript","REST API","Git"]', '["대규모 트래픽 최적화","테스트 자동화","실험 설계"]',
        '["Core Web Vitals 개선 리포트 작성","Playwright E2E 테스트 도입","A/B 테스트 지표 설계 연습"]',
        '[]',
        'React와 TypeScript 경험은 강점으로 충분하므로, 성능 개선 수치와 테스트 자동화 경험을 포트폴리오에 추가'
 FROM application_case ac JOIN users u ON ac.user_id = u.id
 WHERE u.email = 'jiwon.kim@careertuner.dev'
   AND ac.company_name = '토스'
   AND NOT EXISTS (SELECT 1 FROM fit_analysis fa WHERE fa.application_case_id = ac.id)
 LIMIT 1;
INSERT INTO fit_analysis (application_case_id, fit_score, matched_skills, missing_skills, recommended_study, recommended_certificates, strategy)
 SELECT ac.id, 66, '["React","REST API","SQL","Git"]', '["Spring Boot 운영 경험","CI/CD","장애 대응"]',
        '["Spring Boot 게시판 API를 배포까지 진행","GitHub Actions 배포 파이프라인 구성","장애 회고 템플릿 작성"]',
        '["SQLD"]',
        '프런트 강점과 SQL 이해를 살리고, 백엔드 운영 경험은 작은 배포 프로젝트로 보완해 풀스택 성장 가능성을 강조'
 FROM application_case ac JOIN users u ON ac.user_id = u.id
 WHERE u.email = 'jiwon.kim@careertuner.dev'
   AND ac.company_name = '라인플러스'
   AND NOT EXISTS (SELECT 1 FROM fit_analysis fa WHERE fa.application_case_id = ac.id)
 LIMIT 1;
INSERT INTO fit_analysis (application_case_id, fit_score, matched_skills, missing_skills, recommended_study, recommended_certificates, strategy)
 SELECT ac.id, 69, '["GA4","SQL","콘텐츠기획"]', '["CRM 캠페인","뷰티 커머스 지표","A/B 테스트 설계"]',
        '["CRM 세그먼트 캠페인 샘플 기획","뷰티 커머스 KPI 용어 정리","A/B 테스트 결과 리포트 작성"]',
        '["검색광고마케터 1급"]',
        '데이터 기반 캠페인 개선 경험을 핵심 근거로 삼고, 커머스 도메인 이해를 빠르게 보완'
 FROM application_case ac JOIN users u ON ac.user_id = u.id
 WHERE u.email = 'seoyeon.lee@careertuner.dev'
   AND ac.company_name = 'CJ올리브영'
   AND NOT EXISTS (SELECT 1 FROM fit_analysis fa WHERE fa.application_case_id = ac.id)
 LIMIT 1;
INSERT INTO fit_analysis (application_case_id, fit_score, matched_skills, missing_skills, recommended_study, recommended_certificates, strategy)
 SELECT ac.id, 55, '["콘텐츠기획","고객 응대","문서화"]', '["VOC 분석","운영 자동화","SQL"]',
        '["VOC 분류 기준표 작성","SQL 기초 SELECT/ GROUP BY 연습","운영 프로세스 개선안 1페이지 작성"]',
        '[]',
        '운영 직무 전환 가능성은 있으나, 반복 문의를 데이터로 정리한 사례를 보강해야 설득력이 올라감'
 FROM application_case ac JOIN users u ON ac.user_id = u.id
 WHERE u.email = 'seoyeon.lee@careertuner.dev'
   AND ac.company_name = '스타트업 A'
   AND NOT EXISTS (SELECT 1 FROM fit_analysis fa WHERE fa.application_case_id = ac.id)
 LIMIT 1;

UPDATE fit_analysis fa
INNER JOIN application_case ac ON ac.id = fa.application_case_id
INNER JOIN users u ON u.id = ac.user_id
SET fa.created_at = CASE
        WHEN ac.company_name = '카카오페이' THEN DATE_SUB(NOW(), INTERVAL 9 DAY)
        WHEN ac.company_name = '국민건강보험공단' THEN DATE_SUB(NOW(), INTERVAL 5 DAY)
        WHEN ac.company_name = '현대자동차' THEN DATE_SUB(NOW(), INTERVAL 4 DAY)
        WHEN ac.company_name = '토스' THEN DATE_SUB(NOW(), INTERVAL 3 DAY)
        WHEN ac.company_name = '라인플러스' THEN DATE_SUB(NOW(), INTERVAL 2 DAY)
        WHEN ac.company_name = 'CJ올리브영' THEN DATE_SUB(NOW(), INTERVAL 1 DAY)
        WHEN ac.company_name = '스타트업 A' THEN DATE_SUB(NOW(), INTERVAL 12 HOUR)
        ELSE fa.created_at
    END
WHERE u.email IN ('jiwon.kim@careertuner.dev', 'seoyeon.lee@careertuner.dev')
  AND ac.company_name IN ('카카오페이', '국민건강보험공단', '현대자동차', '토스', '라인플러스', 'CJ올리브영', '스타트업 A');

INSERT INTO interview_session (application_case_id, mode, started_at, ended_at, total_score, report, created_at)
 SELECT ac.id, 'JOB', DATE_SUB(NOW(), INTERVAL 3 HOUR), DATE_SUB(NOW(), INTERVAL 2 HOUR), 74,
        '{"summary":"React 경험 설명은 좋았지만 TypeScript 전환 경험 보완 필요","strengths":["프로젝트 구조 설명","API 연동 경험"],"weaknesses":["정량 성과 부족"]}',
        DATE_SUB(NOW(), INTERVAL 2 HOUR)
 FROM application_case ac JOIN users u ON ac.user_id = u.id
 WHERE u.email = 'jiwon.kim@careertuner.dev'
   AND ac.company_name = '카카오페이'
   AND NOT EXISTS (
       SELECT 1 FROM interview_session interview
       WHERE interview.application_case_id = ac.id
         AND interview.mode = 'JOB'
         AND DATE(interview.created_at) = CURRENT_DATE
   )
 LIMIT 1;
INSERT INTO interview_session (application_case_id, mode, started_at, ended_at, total_score, report, created_at)
 SELECT ac.id, 'TECH', DATE_SUB(NOW(), INTERVAL 90 MINUTE), DATE_SUB(NOW(), INTERVAL 45 MINUTE), 81,
        '{"summary":"성능 개선 방향과 React 상태 관리 답변이 안정적","strengths":["문제 분해","프런트엔드 기본기"],"weaknesses":["실험 지표 설계"]}',
        DATE_SUB(NOW(), INTERVAL 45 MINUTE)
 FROM application_case ac JOIN users u ON ac.user_id = u.id
 WHERE u.email = 'jiwon.kim@careertuner.dev'
   AND ac.company_name = '토스'
   AND NOT EXISTS (
       SELECT 1 FROM interview_session interview
       WHERE interview.application_case_id = ac.id
         AND interview.mode = 'TECH'
         AND DATE(interview.created_at) = CURRENT_DATE
   )
 LIMIT 1;
INSERT INTO interview_session (application_case_id, mode, started_at, ended_at, total_score, report, created_at)
 SELECT ac.id, 'BASIC', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY) + INTERVAL 40 MINUTE, 63,
        '{"summary":"공공기관 지원 동기는 무난하지만 Spring 경험 질문에서 구체성이 부족","strengths":["성실성","자격증 기반"],"weaknesses":["백엔드 프로젝트 깊이"]}',
        DATE_SUB(NOW(), INTERVAL 3 DAY)
 FROM application_case ac JOIN users u ON ac.user_id = u.id
 WHERE u.email = 'jiwon.kim@careertuner.dev'
   AND ac.company_name = '국민건강보험공단'
   AND NOT EXISTS (
       SELECT 1 FROM interview_session interview
       WHERE interview.application_case_id = ac.id
         AND interview.mode = 'BASIC'
         AND DATE(interview.created_at) = DATE(DATE_SUB(NOW(), INTERVAL 3 DAY))
   )
 LIMIT 1;
INSERT INTO interview_session (application_case_id, mode, started_at, ended_at, total_score, report, created_at)
 SELECT ac.id, 'JOB', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY) + INTERVAL 35 MINUTE, 70,
        '{"summary":"GA4 기반 성과 분석 답변은 좋으나 CRM 캠페인 경험 보완 필요","strengths":["데이터 해석","콘텐츠 기획"],"weaknesses":["커머스 도메인 용어"]}',
        DATE_SUB(NOW(), INTERVAL 1 DAY)
 FROM application_case ac JOIN users u ON ac.user_id = u.id
 WHERE u.email = 'seoyeon.lee@careertuner.dev'
   AND ac.company_name = 'CJ올리브영'
   AND NOT EXISTS (
       SELECT 1 FROM interview_session interview
       WHERE interview.application_case_id = ac.id
         AND interview.mode = 'JOB'
         AND DATE(interview.created_at) = DATE(DATE_SUB(NOW(), INTERVAL 1 DAY))
   )
 LIMIT 1;

INSERT INTO ai_usage_log (user_id, application_case_id, feature_type, token_usage, credit_used, created_at)
 SELECT u.id, ac.id, 'FIT_ANALYSIS', 2360, 2, DATE_SUB(NOW(), INTERVAL 5 DAY)
 FROM users u JOIN application_case ac ON ac.user_id = u.id
 WHERE u.email = 'jiwon.kim@careertuner.dev' AND ac.company_name = '국민건강보험공단'
   AND NOT EXISTS (
       SELECT 1 FROM ai_usage_log log
       WHERE log.user_id = u.id AND log.application_case_id = ac.id AND log.feature_type = 'FIT_ANALYSIS'
         AND DATE(log.created_at) = DATE(DATE_SUB(NOW(), INTERVAL 5 DAY))
   )
 LIMIT 1;
INSERT INTO ai_usage_log (user_id, application_case_id, feature_type, token_usage, credit_used, created_at)
 SELECT u.id, ac.id, 'FIT_ANALYSIS', 2480, 2, DATE_SUB(NOW(), INTERVAL 4 DAY)
 FROM users u JOIN application_case ac ON ac.user_id = u.id
 WHERE u.email = 'jiwon.kim@careertuner.dev' AND ac.company_name = '현대자동차'
   AND NOT EXISTS (
       SELECT 1 FROM ai_usage_log log
       WHERE log.user_id = u.id AND log.application_case_id = ac.id AND log.feature_type = 'FIT_ANALYSIS'
         AND DATE(log.created_at) = DATE(DATE_SUB(NOW(), INTERVAL 4 DAY))
   )
 LIMIT 1;
INSERT INTO ai_usage_log (user_id, application_case_id, feature_type, token_usage, credit_used, created_at)
 SELECT u.id, ac.id, 'JOB_ANALYSIS', 1880, 1, DATE_SUB(NOW(), INTERVAL 3 DAY)
 FROM users u JOIN application_case ac ON ac.user_id = u.id
 WHERE u.email = 'jiwon.kim@careertuner.dev' AND ac.company_name = '토스'
   AND NOT EXISTS (
       SELECT 1 FROM ai_usage_log log
       WHERE log.user_id = u.id AND log.application_case_id = ac.id AND log.feature_type = 'JOB_ANALYSIS'
         AND DATE(log.created_at) = DATE(DATE_SUB(NOW(), INTERVAL 3 DAY))
   )
 LIMIT 1;
INSERT INTO ai_usage_log (user_id, application_case_id, feature_type, token_usage, credit_used, created_at)
 SELECT u.id, ac.id, 'FIT_ANALYSIS', 2610, 2, DATE_SUB(NOW(), INTERVAL 2 DAY)
 FROM users u JOIN application_case ac ON ac.user_id = u.id
 WHERE u.email = 'jiwon.kim@careertuner.dev' AND ac.company_name = '토스'
   AND NOT EXISTS (
       SELECT 1 FROM ai_usage_log log
       WHERE log.user_id = u.id AND log.application_case_id = ac.id AND log.feature_type = 'FIT_ANALYSIS'
         AND DATE(log.created_at) = DATE(DATE_SUB(NOW(), INTERVAL 2 DAY))
   )
 LIMIT 1;
INSERT INTO ai_usage_log (user_id, application_case_id, feature_type, token_usage, credit_used, created_at)
 SELECT u.id, ac.id, 'LONG_TERM_ANALYSIS', 3120, 3, DATE_SUB(NOW(), INTERVAL 1 DAY)
 FROM users u JOIN application_case ac ON ac.user_id = u.id
 WHERE u.email = 'jiwon.kim@careertuner.dev' AND ac.company_name = '라인플러스'
   AND NOT EXISTS (
       SELECT 1 FROM ai_usage_log log
       WHERE log.user_id = u.id AND log.application_case_id = ac.id AND log.feature_type = 'LONG_TERM_ANALYSIS'
         AND DATE(log.created_at) = DATE(DATE_SUB(NOW(), INTERVAL 1 DAY))
   )
 LIMIT 1;
INSERT INTO ai_usage_log (user_id, application_case_id, feature_type, token_usage, credit_used, created_at)
 SELECT u.id, ac.id, 'FIT_ANALYSIS', 2240, 2, DATE_SUB(NOW(), INTERVAL 1 DAY)
 FROM users u JOIN application_case ac ON ac.user_id = u.id
 WHERE u.email = 'seoyeon.lee@careertuner.dev' AND ac.company_name = 'CJ올리브영'
   AND NOT EXISTS (
       SELECT 1 FROM ai_usage_log log
       WHERE log.user_id = u.id AND log.application_case_id = ac.id AND log.feature_type = 'FIT_ANALYSIS'
         AND DATE(log.created_at) = DATE(DATE_SUB(NOW(), INTERVAL 1 DAY))
   )
 LIMIT 1;
INSERT INTO ai_usage_log (user_id, application_case_id, feature_type, token_usage, credit_used, created_at)
 SELECT u.id, ac.id, 'DASHBOARD_SUMMARY', 1450, 1, NOW()
 FROM users u JOIN application_case ac ON ac.user_id = u.id
 WHERE u.email = 'jiwon.kim@careertuner.dev' AND ac.company_name = '카카오페이'
   AND NOT EXISTS (
       SELECT 1 FROM ai_usage_log log
       WHERE log.user_id = u.id AND log.application_case_id = ac.id AND log.feature_type = 'DASHBOARD_SUMMARY'
         AND DATE(log.created_at) = CURRENT_DATE
   )
 LIMIT 1;

INSERT INTO admin_fit_analysis_memo (fit_analysis_id, admin_user_id, memo_type, content)
 SELECT fa.id, admin_user.id, 'QUALITY', '샘플 검수: 점수는 프로필 기술과 공고 필수 조건의 교집합을 기준으로 자연스럽게 분포함. 부족 기술은 사용자 화면과 관리자 통계 양쪽에서 반복 노출 확인 대상.'
 FROM fit_analysis fa
 INNER JOIN application_case ac ON ac.id = fa.application_case_id
 INNER JOIN users sample_user ON sample_user.id = ac.user_id
 INNER JOIN users admin_user ON admin_user.email = 'admin@careertuner.dev'
 WHERE sample_user.email = 'jiwon.kim@careertuner.dev'
   AND ac.company_name = '토스'
   AND NOT EXISTS (
       SELECT 1 FROM admin_fit_analysis_memo memo
       WHERE memo.fit_analysis_id = fa.id
         AND memo.memo_type = 'QUALITY'
         AND memo.content LIKE '샘플 검수:%'
   )
 LIMIT 1;
INSERT INTO admin_fit_analysis_memo (fit_analysis_id, admin_user_id, memo_type, content)
 SELECT fa.id, admin_user.id, 'REANALYSIS', 'Spring Boot 경험이 보완되면 재분석 시 65점 이상까지 상승할 가능성이 있어 학습 추천 노출 문구를 추적한다.'
 FROM fit_analysis fa
 INNER JOIN application_case ac ON ac.id = fa.application_case_id
 INNER JOIN users sample_user ON sample_user.id = ac.user_id
 INNER JOIN users admin_user ON admin_user.email = 'admin@careertuner.dev'
 WHERE sample_user.email = 'jiwon.kim@careertuner.dev'
   AND ac.company_name = '국민건강보험공단'
   AND NOT EXISTS (
       SELECT 1 FROM admin_fit_analysis_memo memo
       WHERE memo.fit_analysis_id = fa.id
         AND memo.memo_type = 'REANALYSIS'
         AND memo.content LIKE 'Spring Boot 경험이 보완되면%'
   )
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
