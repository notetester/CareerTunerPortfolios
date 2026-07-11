-- 시연용 대량 데이터 시드(멱등). 92만번대 고정 ID + ON DUPLICATE KEY 무시로 재실행 안전, 기존 행 무손상.
-- 대상: 이서연(user_id=3) 지원건 8건 풀그래프(공고문·공고분석·기업분석·적합도·학습과제·면접·플래너·알림)
--       + 김지원(user_id=2) 대표 케이스에 자격증 근거 snapshot 주입(NULL 인 최신 행만) + 플래너.
-- 자격증 근거/기업맥락 JSON 은 백엔드 실제 응답 형태(CertificateEvidenceSnapshot / company_analysis)와 동일.

-- ── 1) 지원 건 8 (이서연) ──
INSERT INTO application_case (id, user_id, company_name, job_title, posting_date, deadline_date, source_type, status, is_favorite, created_at, updated_at) VALUES
 (920001, 3, '삼성SDS', '백엔드 개발자', DATE_SUB(CURDATE(), INTERVAL 12 DAY), DATE_ADD(CURDATE(), INTERVAL 9 DAY), 'TEXT', 'APPLIED', 1, DATE_SUB(NOW(), INTERVAL 12 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
 (920002, 3, '쿠팡', '서버 개발자', DATE_SUB(CURDATE(), INTERVAL 9 DAY), DATE_ADD(CURDATE(), INTERVAL 5 DAY), 'TEXT', 'READY', 1, DATE_SUB(NOW(), INTERVAL 9 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY)),
 (920003, 3, '우아한형제들', '백엔드 개발자', DATE_SUB(CURDATE(), INTERVAL 8 DAY), DATE_ADD(CURDATE(), INTERVAL 14 DAY), 'TEXT', 'APPLIED', 0, DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY)),
 (920004, 3, 'LG CNS', '클라우드 엔지니어', DATE_SUB(CURDATE(), INTERVAL 7 DAY), DATE_ADD(CURDATE(), INTERVAL 11 DAY), 'TEXT', 'READY', 0, DATE_SUB(NOW(), INTERVAL 7 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY)),
 (920005, 3, '당근', '백엔드 개발자', DATE_SUB(CURDATE(), INTERVAL 5 DAY), DATE_ADD(CURDATE(), INTERVAL 20 DAY), 'TEXT', 'DRAFT', 0, DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY)),
 (920006, 3, 'KB국민은행', 'IT 개발직', DATE_SUB(CURDATE(), INTERVAL 15 DAY), DATE_ADD(CURDATE(), INTERVAL 3 DAY), 'TEXT', 'APPLIED', 0, DATE_SUB(NOW(), INTERVAL 15 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY)),
 (920007, 3, '라인플러스', '서버 개발자', DATE_SUB(CURDATE(), INTERVAL 4 DAY), DATE_ADD(CURDATE(), INTERVAL 16 DAY), 'TEXT', 'READY', 1, DATE_SUB(NOW(), INTERVAL 4 DAY), NOW()),
 (920008, 3, '현대오토에버', 'SW 엔지니어', DATE_SUB(CURDATE(), INTERVAL 30 DAY), DATE_SUB(CURDATE(), INTERVAL 2 DAY), 'TEXT', 'CLOSED', 0, DATE_SUB(NOW(), INTERVAL 30 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY))
ON DUPLICATE KEY UPDATE id = id;

-- ── 2) 공고문 ──
INSERT INTO job_posting (id, application_case_id, revision, original_text, source_type, created_at) VALUES
 (920001, 920001, 1, '[삼성SDS] 백엔드 개발자 채용\n담당업무: 클라우드 기반 엔터프라이즈 서비스 백엔드 개발\n자격요건: Java, Spring Boot, JPA/MyBatis, MySQL 실무 활용 가능자\n우대사항: Kubernetes, Kafka, MSA 설계 경험\n근무지: 잠실 / 정규직 / 신입·경력', 'TEXT', DATE_SUB(NOW(), INTERVAL 12 DAY)),
 (920002, 920002, 1, '[쿠팡] Server Developer\n담당업무: 대규모 트래픽 커머스 백엔드 개발\n자격요건: Java 또는 Kotlin, Spring, RDBMS\n우대사항: AWS, Redis, 대용량 트래픽 처리 경험\n정규직 / 송파', 'TEXT', DATE_SUB(NOW(), INTERVAL 9 DAY)),
 (920003, 920003, 1, '[우아한형제들] 백엔드 개발자\n담당업무: 배달 플랫폼 주문/정산 시스템 개발\n자격요건: Java, Spring Boot, JPA\n우대사항: MSA, Kafka, 결제 도메인 경험', 'TEXT', DATE_SUB(NOW(), INTERVAL 8 DAY)),
 (920004, 920004, 1, '[LG CNS] 클라우드 엔지니어\n담당업무: 대외 클라우드 전환 프로젝트 인프라 설계·구축\n자격요건: AWS 또는 Azure, Linux, 네트워크 기초\n우대사항: Terraform, Kubernetes, AWS SAA 자격증', 'TEXT', DATE_SUB(NOW(), INTERVAL 7 DAY)),
 (920005, 920005, 1, '[당근] 백엔드 개발자\n담당업무: 지역 커뮤니티 서비스 백엔드\n자격요건: Kotlin 또는 Java, Spring\n우대사항: Go, gRPC, 대용량 트래픽', 'TEXT', DATE_SUB(NOW(), INTERVAL 5 DAY)),
 (920006, 920006, 1, '[KB국민은행] IT 개발직 신입\n담당업무: 뱅킹 서비스 개발·운영\n자격요건: 전산 관련 전공 또는 동등 역량, Java\n우대사항: 정보처리기사, SQLD, 금융권 프로젝트 경험', 'TEXT', DATE_SUB(NOW(), INTERVAL 15 DAY)),
 (920007, 920007, 1, '[라인플러스] Server Developer\n담당업무: 글로벌 메신저 플랫폼 서버 개발\n자격요건: Java/Kotlin, Spring, RDB\n우대사항: 대규모 분산 시스템, Redis, gRPC', 'TEXT', DATE_SUB(NOW(), INTERVAL 4 DAY)),
 (920008, 920008, 1, '[현대오토에버] SW 엔지니어\n담당업무: 차량 SW 플랫폼 개발\n자격요건: C++ 또는 Java\n우대사항: 임베디드, AUTOSAR', 'TEXT', DATE_SUB(NOW(), INTERVAL 30 DAY))
ON DUPLICATE KEY UPDATE id = id;

-- ── 3) 공고 분석 ──
INSERT INTO job_analysis (id, application_case_id, job_posting_id, job_posting_revision, employment_type, experience_level, required_skills, preferred_skills, duties, qualifications, difficulty, summary, created_at) VALUES
 (920001, 920001, 920001, 1, '정규직', '신입·경력', '["Java","Spring Boot","MySQL"]', '["Kubernetes","Kafka","MSA"]', '클라우드 기반 엔터프라이즈 서비스 백엔드 개발', '["Java/Spring 실무 활용","JPA 또는 MyBatis"]', 'MEDIUM', '대기업 SI 계열 백엔드. 기본기 중심 + 클라우드 우대.', DATE_SUB(NOW(), INTERVAL 12 DAY)),
 (920002, 920002, 920002, 1, '정규직', '경력무관', '["Java","Spring","RDBMS"]', '["AWS","Redis","대용량 트래픽"]', '대규모 트래픽 커머스 백엔드 개발', '["Java 또는 Kotlin"]', 'HIGH', '트래픽 규모가 커 성능 경험을 중시.', DATE_SUB(NOW(), INTERVAL 9 DAY)),
 (920003, 920003, 920003, 1, '정규직', '신입·경력', '["Java","Spring Boot","JPA"]', '["MSA","Kafka","결제 도메인"]', '주문/정산 시스템 개발', '["Spring Boot 실무"]', 'MEDIUM', '결제·정산 도메인 이해가 가점.', DATE_SUB(NOW(), INTERVAL 8 DAY)),
 (920004, 920004, 920004, 1, '정규직', '신입·경력', '["AWS","Linux","네트워크"]', '["Terraform","Kubernetes","AWS SAA"]', '클라우드 전환 인프라 설계·구축', '["AWS 또는 Azure 사용 경험"]', 'MEDIUM', '자격증(AWS SAA) 우대가 명시된 인프라 포지션.', DATE_SUB(NOW(), INTERVAL 7 DAY)),
 (920005, 920005, 920005, 1, '정규직', '경력무관', '["Kotlin","Spring"]', '["Go","gRPC"]', '지역 커뮤니티 서비스 백엔드', '["Kotlin 또는 Java"]', 'MEDIUM', 'Kotlin 전환 추세, 신입은 Java 가능.', DATE_SUB(NOW(), INTERVAL 5 DAY)),
 (920006, 920006, 920006, 1, '정규직', '신입', '["Java","전산 기초"]', '["정보처리기사","SQLD","금융 프로젝트"]', '뱅킹 서비스 개발·운영', '["관련 전공 또는 동등 역량"]', 'LOW', '금융권 신입 공채 — 자격증이 공고에 명시됨.', DATE_SUB(NOW(), INTERVAL 15 DAY)),
 (920007, 920007, 920007, 1, '정규직', '경력', '["Java","Spring","RDB"]', '["분산 시스템","Redis","gRPC"]', '글로벌 메신저 서버 개발', '["Java/Kotlin"]', 'HIGH', '글로벌 스케일 분산 시스템 경험 중시.', DATE_SUB(NOW(), INTERVAL 4 DAY)),
 (920008, 920008, 920008, 1, '정규직', '신입·경력', '["C++"]', '["임베디드","AUTOSAR"]', '차량 SW 플랫폼 개발', '["C++ 또는 Java"]', 'MEDIUM', '차량 SW — 임베디드 성향.', DATE_SUB(NOW(), INTERVAL 30 DAY))
ON DUPLICATE KEY UPDATE id = id;

-- ── 4) 기업 분석(4곳 — 기업맥락 소스) ──
INSERT INTO company_analysis (id, application_case_id, job_posting_id, job_posting_revision, company_summary, recent_issues, industry, competitors, interview_points, sources, verified_facts, ai_inferences, source_type, checked_at, created_at) VALUES
 (920001, 920001, 920001, 1, '국내 최대 IT 서비스 기업. 클라우드·물류 IT 로 사업 전환 중이며 그룹사 디지털 전환 프로젝트를 다수 수행.', '클라우드 사업 매출 비중 확대 발표, 생성형 AI 사업부 신설', 'IT 서비스', '["LG CNS","SK C&C"]', '기본기(자료구조·DB)와 협업 태도를 깊게 검증. 프로젝트에서 맡은 역할을 구체적 수치로 설명 요구.', '["회사 IR 자료","채용 공고"]', '["클라우드 전환 사업 확대 중"]', '["신입에게는 기본기와 성장 가능성을 중점 평가할 것으로 추정"]', 'WEB', NOW(), DATE_SUB(NOW(), INTERVAL 11 DAY)),
 (920002, 920002, 920002, 1, '국내 1위 이커머스. 로켓배송 물류망과 자체 클라우드 인프라를 운영하며 트래픽 규모가 국내 최상위.', '실적 흑자 전환, 대만 등 해외 확장', '이커머스', '["네이버쇼핑","11번가"]', '대용량 트래픽·장애 대응 시나리오 질문 빈번. STAR 구조로 문제 해결 경험 정리 필요.', '["실적 발표","기술 블로그"]', '["자체 물류·인프라 보유"]', '["성능 최적화 경험을 강하게 볼 것으로 추정"]', 'WEB', NOW(), DATE_SUB(NOW(), INTERVAL 8 DAY)),
 (920004, 920004, 920004, 1, 'LG 그룹 IT 서비스사. 공공·금융 클라우드 전환 프로젝트 다수, AM/CM 조직 운영.', '공공 클라우드 전환 사업 수주 확대', 'IT 서비스', '["삼성SDS","SK C&C"]', '자격증(AWS SAA)과 인프라 기초(네트워크·리눅스) 확인 질문. 프로젝트 아키텍처 그림 설명 요구.', '["채용 공고","보도자료"]', '["AWS SAA 우대 명시"]', '["자격증 보유 시 서류 가점 가능성"]', 'WEB', NOW(), DATE_SUB(NOW(), INTERVAL 6 DAY)),
 (920007, 920007, 920007, 1, '글로벌 메신저 LINE 운영사. 일본·동남아 중심 대규모 분산 시스템을 운영.', '글로벌 조직 개편, AI 서비스 확대', '플랫폼', '["카카오"]', '분산 시스템 설계 화이트보드 면접. 장애 케이스를 어떻게 격리했는지 질문.', '["기술 블로그"]', '["글로벌 리전 분산 운영"]', '["영어 협업 경험이 가점일 것으로 추정"]', 'WEB', NOW(), DATE_SUB(NOW(), INTERVAL 3 DAY))
ON DUPLICATE KEY UPDATE id = id;

-- ── 5) 적합도 분석(8건, 3건은 자격증 근거 snapshot 포함) ──
INSERT INTO fit_analysis (id, application_case_id, fit_score, matched_skills, missing_skills, recommended_study, recommended_certificates, strategy, source_snapshot, score_basis, gap_recommendations, certificate_recommendations, strategy_actions, condition_matrix, analysis_confidence, apply_decision, certificate_evidence, model, prompt_version, status, created_at) VALUES
 (920001, 920001, 76,
  '["Java","Spring Boot","MySQL"]', '["Kubernetes","Kafka"]', '["Kubernetes 기초 실습","Kafka 이벤트 파이프라인 학습"]', '["정보처리기사"]',
  '삼성SDS 백엔드 적합도는 76점입니다. 필수 역량(Java/Spring Boot/MySQL)을 모두 충족해 서류 경쟁력이 있습니다. 우대인 Kubernetes·Kafka 는 학습 로드맵으로 보완하면서, 면접에서는 기본기(DB 설계·트랜잭션)를 수치 기반 사례로 설명하세요. 이 회사는 클라우드 전환 사업을 확대 중이니 컨테이너 학습 계획을 지원 전략으로 언급하는 접근이 좋습니다.',
  '{"jobAnalysisId":920001,"jobPostingRevision":1,"requiredSkills":["Java","Spring Boot","MySQL"],"profileSkills":["Java","Spring Boot","MySQL","Git"]}',
  '["필수 역량 3개 중 3개 충족","우대 2개 미충족","프로필 기반 산정"]',
  '[{"skill":"Kubernetes","category":"PREFERRED_GAP","priority":"HIGH","reason":"클라우드 전환 사업 확대 중인 회사라 우대 가중치가 큼"},{"skill":"Kafka","category":"PREFERRED_GAP","priority":"MEDIUM","reason":"MSA 이벤트 처리 기본기"}]',
  '[{"name":"정보처리기사","priority":"MEDIUM","reason":"대기업 SI 서류 기본 신뢰도"}]',
  '["지원서에 Spring Boot 프로젝트 성능 개선 수치를 명시","Kubernetes 학습 계획을 자기소개서에 한 줄 언급","면접 전 트랜잭션 격리수준 복습"]',
  '[{"condition":"Java","conditionType":"REQUIRED","matchStatus":"MET","evidence":"프로필 보유 기술에서 확인"},{"condition":"Spring Boot","conditionType":"REQUIRED","matchStatus":"MET","evidence":"프로필 보유 기술에서 확인"},{"condition":"MySQL","conditionType":"REQUIRED","matchStatus":"MET","evidence":"프로필 보유 기술에서 확인"},{"condition":"Kubernetes","conditionType":"PREFERRED","matchStatus":"UNMET","evidence":"프로필에서 확인되지 않음"},{"condition":"Kafka","conditionType":"PREFERRED","matchStatus":"UNMET","evidence":"프로필에서 확인되지 않음"}]',
  '{"level":"HIGH","score":88,"reasons":[]}',
  '{"decision":"APPLY","reasons":["필수 요건 전부 충족(76점)","마감 전 지원 가능"],"actions":["지원서 제출 후 면접 기본기 준비"]}',
  '{"generatedAt":"2026-07-10T18:00:00","strategyStatus":"RECOMMENDED","triggeredSignals":["GAP_CERTIFIABLE"],"items":[{"certName":"정보처리기사","kind":"NATIONAL_TECHNICAL","scheduleStatus":"VERIFIED_CURRENT","registrationStatus":null,"message":"Q-Net 공식 확인 기준 시험일정입니다. 시험 일정은 변경될 수 있으니 접수 전 공식 페이지 재확인이 필요합니다.","sourceName":"한국산업인력공단 큐넷(Q-Net) 국가기술자격 시험정보","sourceUrl":"https://www.q-net.or.kr/","scheduleRounds":[{"round":"기사(2026년도 제3회)","docRegStart":"20260713","docRegEnd":"20260716","docExam":"20260809","docPass":"20260903","pracExamStart":"20261011","pracExamEnd":"20261024","pracPass":"20261120"}]}]}',
  'careertuner-c-career-strategy-3b', 'v0.2', 'SUCCESS', DATE_SUB(NOW(), INTERVAL 11 DAY)),
 (920002, 920002, 64,
  '["Java","Spring"]', '["RDBMS 심화","AWS","Redis"]', '["인덱스·쿼리 튜닝 실습","Redis 캐시 패턴 학습"]', '["SQLD"]',
  '쿠팡 서버 개발 적합도는 64점입니다. Java/Spring 기본은 충족하나 이 회사가 중시하는 대용량 트래픽·성능 경험 근거가 부족합니다. 개인 프로젝트에 부하 테스트(k6 등)를 추가해 수치를 만들고, Redis 캐시 적용 사례를 준비하면 면접 경쟁력이 올라갑니다.',
  '{"jobAnalysisId":920002,"jobPostingRevision":1,"requiredSkills":["Java","Spring","RDBMS"],"profileSkills":["Java","Spring Boot","MySQL","Git"]}',
  '["필수 3개 중 2개 충족","성능 관련 근거 부족"]',
  '[{"skill":"Redis","category":"PREFERRED_GAP","priority":"HIGH","reason":"캐시 경험이 면접 단골 주제"},{"skill":"AWS","category":"PREFERRED_GAP","priority":"MEDIUM","reason":"배포 경험 증빙"}]',
  '[{"name":"SQLD","priority":"MEDIUM","reason":"RDBMS 심화 역량의 객관적 증빙"}]',
  '["개인 프로젝트에 부하 테스트 수치 추가","Redis 캐시 적용 사례 1건 제작"]',
  '[{"condition":"Java","conditionType":"REQUIRED","matchStatus":"MET","evidence":"프로필 보유 기술에서 확인"},{"condition":"Spring","conditionType":"REQUIRED","matchStatus":"MET","evidence":"프로필 보유 기술에서 확인"},{"condition":"RDBMS","conditionType":"REQUIRED","matchStatus":"PARTIAL","evidence":"MySQL 보유, 심화 근거 부족"},{"condition":"Redis","conditionType":"PREFERRED","matchStatus":"UNMET","evidence":"프로필에서 확인되지 않음"}]',
  '{"level":"MEDIUM","score":72,"reasons":["프로필에 성능 관련 정량 근거가 없어 일반 기준으로 평가"]}',
  '{"decision":"COMPLEMENT","reasons":["핵심 우대(성능) 근거 부족(64점)"],"actions":["부하 테스트 수치 확보 후 지원 권장"]}',
  '{"generatedAt":"2026-07-10T18:00:00","strategyStatus":"RECOMMENDED","triggeredSignals":["GAP_CERTIFIABLE"],"items":[{"certName":"SQLD","kind":"PRIVATE_OR_OTHER","scheduleStatus":"MANUAL_REQUIRED","registrationStatus":"REGISTERED_ACTIVE","message":"민간자격 등록이 확인됐습니다(신청기관: 한국데이터산업진흥원). 다만 시험일정은 중앙 공공데이터에 없어 주관기관 공식 페이지 확인이 필요합니다.","sourceName":"한국직업능력연구원 민간자격등록정보(민간자격정보서비스)","sourceUrl":"https://www.pqi.or.kr/","scheduleRounds":[]}]}',
  'careertuner-c-career-strategy-3b', 'v0.2', 'SUCCESS', DATE_SUB(NOW(), INTERVAL 7 DAY)),
 (920003, 920003, 71,
  '["Java","Spring Boot"]', '["JPA","Kafka"]', '["JPA 연관관계 심화","Kafka 기초"]', '[]',
  '우아한형제들 적합도는 71점입니다. Java/Spring Boot 충족, JPA 실무 근거가 약합니다. 주문·정산 도메인 특성상 트랜잭션 정합성 사례를 준비하세요.',
  '{"jobAnalysisId":920003,"jobPostingRevision":1}',
  '["필수 3개 중 2개 충족"]',
  '[{"skill":"JPA","category":"REQUIRED_GAP","priority":"HIGH","reason":"필수 요건"}]',
  '[]',
  '["JPA 미니 프로젝트 1건","정산 도메인 글 정리"]',
  '[{"condition":"Java","conditionType":"REQUIRED","matchStatus":"MET","evidence":"프로필 보유 기술에서 확인"},{"condition":"Spring Boot","conditionType":"REQUIRED","matchStatus":"MET","evidence":"프로필 보유 기술에서 확인"},{"condition":"JPA","conditionType":"REQUIRED","matchStatus":"UNMET","evidence":"프로필에서 확인되지 않음"}]',
  '{"level":"HIGH","score":85,"reasons":[]}',
  '{"decision":"COMPLEMENT","reasons":["필수 JPA 미충족(71점)"],"actions":["JPA 보완 후 지원"]}',
  NULL, 'careertuner-c-career-strategy-3b', 'v0.2', 'SUCCESS', DATE_SUB(NOW(), INTERVAL 6 DAY)),
 (920004, 920004, 69,
  '["AWS","Linux"]', '["네트워크 심화","Terraform","Kubernetes"]', '["Terraform 으로 VPC 구성 실습","CKA 커리큘럼 훑기"]', '["AWS Solutions Architect Associate"]',
  'LG CNS 클라우드 적합도는 69점입니다. AWS·Linux 기반은 충족하고, 공고에 AWS SAA 자격증 우대가 명시돼 있어 자격증 전략의 효용이 높은 케이스입니다. Terraform 실습 결과물을 GitHub 에 정리해 지원서에 링크하세요.',
  '{"jobAnalysisId":920004,"jobPostingRevision":1}',
  '["필수 3개 중 2개 충족","공고에 자격증 우대 명시"]',
  '[{"skill":"Terraform","category":"PREFERRED_GAP","priority":"HIGH","reason":"IaC 필수 추세"}]',
  '[{"name":"AWS Solutions Architect Associate","priority":"HIGH","reason":"공고 우대 명시 — 서류 가점 직접 연결"}]',
  '["Terraform VPC 실습 리포 공개","AWS SAA 접수 일정 확인"]',
  '[{"condition":"AWS","conditionType":"REQUIRED","matchStatus":"MET","evidence":"프로필 보유 기술에서 확인"},{"condition":"Linux","conditionType":"REQUIRED","matchStatus":"MET","evidence":"프로필 보유 기술에서 확인"},{"condition":"네트워크","conditionType":"REQUIRED","matchStatus":"PARTIAL","evidence":"기초 수준 확인"},{"condition":"AWS SAA","conditionType":"PREFERRED","matchStatus":"UNMET","evidence":"자격증 미보유"}]',
  '{"level":"HIGH","score":90,"reasons":[]}',
  '{"decision":"COMPLEMENT","reasons":["자격증 우대 미충족(69점)"],"actions":["AWS SAA 일정 확인 후 지원 시점 판단"]}',
  '{"generatedAt":"2026-07-10T18:00:00","strategyStatus":"REQUIRED_OR_STRONGLY_PREFERRED","triggeredSignals":["POSTING_NAMES_CERTIFICATE"],"items":[{"certName":"AWS Solutions Architect Associate","kind":"PRIVATE_OR_OTHER","scheduleStatus":"MANUAL_REQUIRED","registrationStatus":"NOT_FOUND","message":"공식 민간자격 등록정보에서 확인되지 않았습니다 — 해외 벤더 자격증입니다. 시험일정은 주관기관(AWS) 공식 페이지 확인이 필요합니다.","sourceName":"한국직업능력연구원 민간자격등록정보(민간자격정보서비스)","sourceUrl":"https://www.pqi.or.kr/","scheduleRounds":[]}]}',
  'careertuner-c-career-strategy-3b', 'v0.2', 'SUCCESS', DATE_SUB(NOW(), INTERVAL 5 DAY)),
 (920005, 920005, 58,
  '["Spring"]', '["Kotlin"]', '["Kotlin 문법 전환 학습"]', '[]',
  '당근 적합도는 58점입니다. Kotlin 전환이 핵심 과제입니다. Java 프로젝트 하나를 Kotlin 으로 포팅해 학습 근거를 만드세요.',
  '{"jobAnalysisId":920005,"jobPostingRevision":1}', '["필수 2개 중 1개 충족"]',
  '[{"skill":"Kotlin","category":"REQUIRED_GAP","priority":"HIGH","reason":"필수 언어"}]', '[]',
  '["Kotlin 포팅 프로젝트 1건"]',
  '[{"condition":"Kotlin","conditionType":"REQUIRED","matchStatus":"UNMET","evidence":"프로필에서 확인되지 않음"},{"condition":"Spring","conditionType":"REQUIRED","matchStatus":"MET","evidence":"프로필 보유 기술에서 확인"}]',
  '{"level":"MEDIUM","score":70,"reasons":["언어 전환 가능성은 프로필로 판단 불가"]}',
  '{"decision":"COMPLEMENT","reasons":["필수 Kotlin 미충족(58점)"],"actions":["Kotlin 학습 후 재분석"]}',
  NULL, 'careertuner-c-career-strategy-3b', 'v0.2', 'SUCCESS', DATE_SUB(NOW(), INTERVAL 4 DAY)),
 (920006, 920006, 81,
  '["Java","전산 기초","정보처리기사"]', '["SQLD"]', '["SQLD 핵심 정리"]', '["SQLD"]',
  'KB국민은행 IT 적합도는 81점입니다. 공고 명시 자격증(정보처리기사)을 이미 보유해 강점입니다. SQLD 까지 더하면 우대 요건을 모두 충족합니다. 금융권 특성상 안정성·꼼꼼함 사례를 준비하세요.',
  '{"jobAnalysisId":920006,"jobPostingRevision":1}',
  '["필수 전부 충족","보유 자격증이 공고 우대와 일치"]',
  '[{"skill":"SQLD","category":"PREFERRED_GAP","priority":"MEDIUM","reason":"우대 자격증"}]',
  '[{"name":"SQLD","priority":"MEDIUM","reason":"우대 요건 완성"}]',
  '["지원서에 정보처리기사 명시","금융 도메인 용어 정리"]',
  '[{"condition":"Java","conditionType":"REQUIRED","matchStatus":"MET","evidence":"프로필 보유 기술에서 확인"},{"condition":"정보처리기사","conditionType":"PREFERRED","matchStatus":"MET","evidence":"보유 자격증에서 확인"},{"condition":"SQLD","conditionType":"PREFERRED","matchStatus":"UNMET","evidence":"자격증 미보유"}]',
  '{"level":"HIGH","score":92,"reasons":[]}',
  '{"decision":"APPLY","reasons":["필수 충족 + 자격증 강점(81점)"],"actions":["마감(3일) 전 즉시 지원"]}',
  '{"generatedAt":"2026-07-10T18:00:00","strategyStatus":"USE_EXISTING_AS_STRENGTH","triggeredSignals":["POSTING_NAMES_CERTIFICATE","HELD_CERT_RELEVANT"],"items":[{"certName":"정보처리기사","kind":"NATIONAL_TECHNICAL","scheduleStatus":"VERIFIED_CURRENT","registrationStatus":null,"message":"Q-Net 공식 확인 기준 시험일정입니다. 이미 보유한 자격증으로, 공고 우대 요건과 일치해 지원서에 명시하는 것이 좋습니다.","sourceName":"한국산업인력공단 큐넷(Q-Net) 국가기술자격 시험정보","sourceUrl":"https://www.q-net.or.kr/","scheduleRounds":[]},{"certName":"SQLD","kind":"PRIVATE_OR_OTHER","scheduleStatus":"MANUAL_REQUIRED","registrationStatus":"REGISTERED_ACTIVE","message":"민간자격 등록이 확인됐습니다(신청기관: 한국데이터산업진흥원). 시험일정은 주관기관 공식 페이지 확인이 필요합니다.","sourceName":"한국직업능력연구원 민간자격등록정보(민간자격정보서비스)","sourceUrl":"https://www.pqi.or.kr/","scheduleRounds":[]}]}',
  'careertuner-c-career-strategy-3b', 'v0.2', 'SUCCESS', DATE_SUB(NOW(), INTERVAL 10 DAY)),
 (920007, 920007, 62,
  '["Java","Spring"]', '["분산 시스템","Redis"]', '["분산 시스템 기초(CAP·합의) 학습","Redis 실습"]', '[]',
  '라인플러스 적합도는 62점입니다. 기본기는 충족하나 이 회사 면접의 핵심인 분산 시스템 설계 근거가 부족합니다. 화이트보드 설계 연습과 장애 격리 사례 준비가 우선입니다.',
  '{"jobAnalysisId":920007,"jobPostingRevision":1}', '["필수 3개 중 2개 충족"]',
  '[{"skill":"분산 시스템","category":"PREFERRED_GAP","priority":"HIGH","reason":"면접 핵심 주제"}]', '[]',
  '["시스템 설계 연습 주 2회","장애 격리 사례 정리"]',
  '[{"condition":"Java","conditionType":"REQUIRED","matchStatus":"MET","evidence":"프로필 보유 기술에서 확인"},{"condition":"Spring","conditionType":"REQUIRED","matchStatus":"MET","evidence":"프로필 보유 기술에서 확인"},{"condition":"RDB","conditionType":"REQUIRED","matchStatus":"MET","evidence":"MySQL 보유"},{"condition":"분산 시스템","conditionType":"PREFERRED","matchStatus":"UNMET","evidence":"프로필에서 확인되지 않음"}]',
  '{"level":"HIGH","score":86,"reasons":[]}',
  '{"decision":"COMPLEMENT","reasons":["핵심 우대 미충족(62점)"],"actions":["설계 연습 후 지원"]}',
  NULL, 'careertuner-c-career-strategy-3b', 'v0.2', 'SUCCESS', DATE_SUB(NOW(), INTERVAL 3 DAY)),
 (920008, 920008, 45,
  '["Java"]', '["C++","임베디드"]', '["C++ 기초"]', '[]',
  '현대오토에버 적합도는 45점입니다. 주력 언어(C++)가 달라 이번 공고는 마감됐고, 유사 포지션 재공고 시 C++ 기초를 갖춘 뒤 지원을 권장합니다.',
  '{"jobAnalysisId":920008,"jobPostingRevision":1}', '["필수 1개 중 0개(주력) 충족"]',
  '[{"skill":"C++","category":"REQUIRED_GAP","priority":"HIGH","reason":"주력 언어 불일치"}]', '[]',
  '["C++ 기초 강의 수강"]',
  '[{"condition":"C++","conditionType":"REQUIRED","matchStatus":"UNMET","evidence":"프로필에서 확인되지 않음"}]',
  '{"level":"HIGH","score":84,"reasons":[]}',
  '{"decision":"HOLD","reasons":["주력 언어 불일치(45점)","공고 마감"],"actions":["재공고 시 재평가"]}',
  NULL, 'careertuner-c-career-strategy-3b', 'v0.2', 'SUCCESS', DATE_SUB(NOW(), INTERVAL 25 DAY))
ON DUPLICATE KEY UPDATE id = id;

-- ── 6) 학습 과제(대표 4건 케이스 × 3) ──
INSERT INTO fit_analysis_learning_task (id, fit_analysis_id, skill, title, practice_task, expected_duration, priority, sort_order, completed, created_at) VALUES
 (9200011, 920001, 'Kubernetes', 'Kubernetes 1단계 · 핵심 개념 정리', 'Pod/Deployment/Service 개념 노트', '1주', 'HIGH', 1, 1, DATE_SUB(NOW(), INTERVAL 11 DAY)),
 (9200012, 920001, 'Kubernetes', 'Kubernetes 2단계 · 적용 실습', '미니 프로젝트를 minikube 에 배포', '2주', 'HIGH', 2, 0, DATE_SUB(NOW(), INTERVAL 11 DAY)),
 (9200013, 920001, 'Kafka', 'Kafka 1단계 · 핵심 개념 정리', '토픽/파티션/컨슈머그룹 정리', '1주', 'MEDIUM', 3, 0, DATE_SUB(NOW(), INTERVAL 11 DAY)),
 (9200021, 920002, 'Redis', 'Redis 1단계 · 캐시 패턴', 'look-aside 캐시 적용 실습', '1주', 'HIGH', 1, 0, DATE_SUB(NOW(), INTERVAL 7 DAY)),
 (9200022, 920002, 'RDBMS', '쿼리 튜닝 실습', '실행계획 분석 3건 정리', '1주', 'HIGH', 2, 1, DATE_SUB(NOW(), INTERVAL 7 DAY)),
 (9200023, 920002, 'AWS', 'AWS 배포 기초', 'EC2+RDS 로 프로젝트 배포', '2주', 'MEDIUM', 3, 0, DATE_SUB(NOW(), INTERVAL 7 DAY)),
 (9200041, 920004, 'Terraform', 'Terraform 1단계 · VPC 실습', 'VPC/서브넷 IaC 구성', '2주', 'HIGH', 1, 0, DATE_SUB(NOW(), INTERVAL 5 DAY)),
 (9200042, 920004, 'Kubernetes', 'K8s 기초', 'kubectl 핵심 명령 실습', '1주', 'MEDIUM', 2, 0, DATE_SUB(NOW(), INTERVAL 5 DAY)),
 (9200061, 920006, 'SQLD', 'SQLD 핵심 정리', '기출 2회분 풀이', '2주', 'MEDIUM', 1, 0, DATE_SUB(NOW(), INTERVAL 10 DAY))
ON DUPLICATE KEY UPDATE id = id;

-- ── 7) 면접 세션(이서연 4건 — 대시보드/분석 지표용) ──
INSERT INTO interview_session (id, application_case_id, mode, started_at, ended_at, total_score, created_at) VALUES
 (920001, 920001, 'BASIC', DATE_SUB(NOW(), INTERVAL 9 DAY), DATE_SUB(NOW(), INTERVAL 9 DAY), 74, DATE_SUB(NOW(), INTERVAL 9 DAY)),
 (920002, 920001, 'JOB', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), 81, DATE_SUB(NOW(), INTERVAL 2 DAY)),
 (920003, 920002, 'JOB', DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY), 77, DATE_SUB(NOW(), INTERVAL 5 DAY)),
 (920004, 920006, 'PERSONALITY', DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_SUB(NOW(), INTERVAL 8 DAY), 69, DATE_SUB(NOW(), INTERVAL 8 DAY))
ON DUPLICATE KEY UPDATE id = id;

-- ── 8) 플래너(방금 생성된 테이블 — 두 시드 사용자 모두) ──
INSERT INTO planner_memo (id, user_id, title, content, color, pinned, overlay_visible, opacity, application_case_id, fit_analysis_id) VALUES
 (920001, 3, '삼성SDS 면접 준비', 'DB 트랜잭션 격리수준 + 프로젝트 수치 정리', 'yellow', 1, 1, 0.95, 920001, 920001),
 (920002, 3, NULL, 'AWS SAA 접수 일정 공식 페이지 확인하기', 'blue', 0, 0, 0.95, 920004, 920004),
 (920003, 2, '카카오페이 후속', '재분석 후 점수 변화 확인', 'green', 1, 1, 0.95, 1, NULL),
 (920004, 2, NULL, 'TypeScript 리팩토링 리포 README 보강', 'slate', 0, 0, 0.95, NULL, NULL)
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO planner_schedule_item (id, user_id, title, description, kind, status, all_day, timing_precision, start_at, end_at, timezone, application_case_id, fit_analysis_id, source_type, overlay_visible, opacity, pinned, click_through) VALUES
 (920001, 3, '쿠팡 서류 마감', '부하 테스트 수치 포함해 제출', 'DEADLINE', 'PLANNED', 1, 'DATE', DATE_ADD(CURDATE(), INTERVAL 5 DAY), NULL, 'Asia/Seoul', 920002, 920002, 'MANUAL', 1, 0.95, 1, 0),
 (920002, 3, 'KB국민은행 서류 마감', NULL, 'DEADLINE', 'PLANNED', 1, 'DATE', DATE_ADD(CURDATE(), INTERVAL 3 DAY), NULL, 'Asia/Seoul', 920006, 920006, 'MANUAL', 1, 0.95, 1, 0),
 (920003, 3, 'Kubernetes 실습 세션', 'minikube 배포 실습', 'TASK', 'PLANNED', 0, 'DATETIME', DATE_ADD(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 1 DAY), 'Asia/Seoul', 920001, 920001, 'FIT_STRATEGY', 0, 0.95, 0, 0),
 (920004, 2, '카카오페이 재분석', '학습 80% 달성 후 실행', 'TASK', 'PLANNED', 1, 'DATE', DATE_ADD(CURDATE(), INTERVAL 2 DAY), NULL, 'Asia/Seoul', 1, NULL, 'MANUAL', 1, 0.95, 0, 0),
 (920005, 2, '네이버 면접 준비', 'STAR 답변 3세트 정리', 'EVENT', 'PLANNED', 0, 'DATETIME', DATE_ADD(NOW(), INTERVAL 3 DAY), DATE_ADD(NOW(), INTERVAL 3 DAY), 'Asia/Seoul', 14, NULL, 'MANUAL', 0, 0.95, 0, 0)
ON DUPLICATE KEY UPDATE id = id;

-- ── 9) 알림(이서연) ──
INSERT INTO notification (id, user_id, type, target_type, target_id, title, message, link, is_read, created_at) VALUES
 (920001, 3, 'FIT_ANALYSIS_COMPLETE', 'APPLICATION_CASE', 920001, '적합도 분석이 완료되었습니다', '삼성SDS · 백엔드 개발자 적합도 76점', '/applications/920001/fit', 0, DATE_SUB(NOW(), INTERVAL 11 DAY)),
 (920002, 3, 'FIT_ANALYSIS_COMPLETE', 'APPLICATION_CASE', 920006, '적합도 분석이 완료되었습니다', 'KB국민은행 · IT 개발직 적합도 81점', '/applications/920006/fit', 1, DATE_SUB(NOW(), INTERVAL 10 DAY)),
 (920003, 3, 'DEADLINE_REMINDER', 'APPLICATION_CASE', 920006, '마감 임박', 'KB국민은행 서류 마감이 3일 남았습니다', '/applications/920006/overview', 0, NOW())
ON DUPLICATE KEY UPDATE id = id;

-- ── 10) 김지원 대표 케이스에 자격증 근거 주입(NULL 인 최신 행만 — 무손상) ──
UPDATE fit_analysis fa
JOIN (SELECT MAX(id) mid FROM fit_analysis WHERE application_case_id = 14) t ON fa.id = t.mid
SET fa.certificate_evidence = '{"generatedAt":"2026-07-10T18:00:00","strategyStatus":"RECOMMENDED","triggeredSignals":["GAP_CERTIFIABLE"],"items":[{"certName":"정보처리기사","kind":"NATIONAL_TECHNICAL","scheduleStatus":"VERIFIED_CURRENT","registrationStatus":null,"message":"Q-Net 공식 확인 기준 시험일정입니다. 시험 일정은 변경될 수 있으니 접수 전 공식 페이지 재확인이 필요합니다.","sourceName":"한국산업인력공단 큐넷(Q-Net) 국가기술자격 시험정보","sourceUrl":"https://www.q-net.or.kr/","scheduleRounds":[{"round":"기사(2026년도 제3회)","docRegStart":"20260713","docRegEnd":"20260716","docExam":"20260809","docPass":"20260903","pracExamStart":"20261011","pracExamEnd":"20261024","pracPass":"20261120"}]}]}'
WHERE fa.certificate_evidence IS NULL;

UPDATE fit_analysis fa
JOIN (SELECT MAX(id) mid FROM fit_analysis WHERE application_case_id = 1) t ON fa.id = t.mid
SET fa.certificate_evidence = '{"generatedAt":"2026-07-10T18:00:00","strategyStatus":"OPTIONAL_LOW_PRIORITY","triggeredSignals":["USER_REQUESTED"],"items":[{"certName":"SQLD","kind":"PRIVATE_OR_OTHER","scheduleStatus":"MANUAL_REQUIRED","registrationStatus":"REGISTERED_ACTIVE","message":"민간자격 등록이 확인됐습니다(신청기관: 한국데이터산업진흥원). 다만 시험일정은 중앙 공공데이터에 없어 주관기관 공식 페이지 확인이 필요합니다.","sourceName":"한국직업능력연구원 민간자격등록정보(민간자격정보서비스)","sourceUrl":"https://www.pqi.or.kr/","scheduleRounds":[]}]}'
WHERE fa.certificate_evidence IS NULL;

UPDATE fit_analysis fa
JOIN (SELECT MAX(id) mid FROM fit_analysis WHERE application_case_id = 21) t ON fa.id = t.mid
SET fa.certificate_evidence = '{"generatedAt":"2026-07-10T18:00:00","strategyStatus":"NOT_NEEDED","triggeredSignals":[],"items":[]}'
WHERE fa.certificate_evidence IS NULL;
