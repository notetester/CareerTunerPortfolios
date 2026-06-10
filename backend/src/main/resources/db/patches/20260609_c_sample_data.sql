-- =====================================================================
--  C 영역 개발용 샘플 데이터 보강 (적합도 상세 / 학습 로드맵 / 장기 경향 실행 이력)
--  ※ 공통 data.sql 은 건드리지 않고 C 소유 테이블만 채우는 추가 패치.
--  ※ data.sql(기본 회원·지원 건·fit_analysis)이 먼저 적용된 빈 DB 기준.
--  ※ 모든 문장은 NOT EXISTS / 조건부 UPDATE 가드로 재실행 안전(멱등)을 지향한다.
--  ※ 대상 테이블: fit_analysis(신규 컬럼 백필), fit_analysis_learning_task,
--    career_analysis_run — 모두 C 담당. 타 담당 원본은 읽기 전용 참조만.
-- =====================================================================

-- ── 1) fit_analysis 신규 컬럼 백필 ──────────────────────────────────
--  data.sql 의 fit_analysis 샘플은 기본 컬럼만 채워 score_basis/strategy_actions/model
--  이 비어 있다. 점수 산정 근거와 지원 액션이 사용자/관리자 화면에 보이도록 보강한다.
--  ※ 형태 주의: 프런트는 score_basis·strategy_actions 를 "문자열 배열"로 파싱한다
--    (parseJsonList). 객체 배열 형태인 gap_recommendations·certificate_recommendations
--    는 형태가 다르므로 이 시드에서 채우지 않고, 사용자가 실제 적합도 재실행(POST)할 때
--    mock/실 AI 가 정식 구조로 생성하도록 둔다. NULL 이면 화면은 안전하게 폴백 표기한다.
UPDATE fit_analysis fa
INNER JOIN application_case ac ON ac.id = fa.application_case_id
INNER JOIN users u ON u.id = ac.user_id
SET
    fa.model = COALESCE(fa.model, 'mock-seed'),
    fa.score_basis = COALESCE(fa.score_basis, JSON_ARRAY(
        CONCAT('매칭 역량 ', JSON_LENGTH(fa.matched_skills), '개 / 부족 역량 ', JSON_LENGTH(fa.missing_skills), '개'),
        '매칭 기술과 프로젝트 경험을 가산, 미충족 필수 조건을 감점',
        CONCAT('현재 적합도 ', fa.fit_score, '점은 필수 대비 충족 비중으로 산정'))),
    fa.strategy_actions = COALESCE(fa.strategy_actions, JSON_ARRAY(
        '부족 필수 역량 1개를 2주 안에 보완 프로젝트로 착수',
        '강점 기술은 정량 성과 문장으로 지원서에 전면 배치',
        '보완 계획을 면접 예상 답변과 연결해 준비'))
WHERE u.email IN ('jiwon.kim@careertuner.dev', 'seoyeon.lee@careertuner.dev')
  AND (fa.score_basis IS NULL OR fa.strategy_actions IS NULL OR fa.model IS NULL);

-- ── 2) 학습 로드맵 체크리스트 (fit_analysis_learning_task) ───────────
--  토스(78점) 지원 건의 적합도 결과에 학습 로드맵 3단계를 연결한다.
INSERT INTO fit_analysis_learning_task
    (fit_analysis_id, skill, title, practice_task, expected_duration, priority, sort_order, completed)
SELECT fa.id, 'TypeScript', 'TypeScript 실무 적용', '기존 React 프로젝트 1개를 TS로 점진 마이그레이션', '2주', 'HIGH', 1, 1
FROM fit_analysis fa
INNER JOIN application_case ac ON ac.id = fa.application_case_id
INNER JOIN users u ON u.id = ac.user_id
WHERE u.email = 'jiwon.kim@careertuner.dev' AND ac.company_name = '토스'
  AND NOT EXISTS (SELECT 1 FROM fit_analysis_learning_task t WHERE t.fit_analysis_id = fa.id AND t.skill = 'TypeScript')
LIMIT 1;

INSERT INTO fit_analysis_learning_task
    (fit_analysis_id, skill, title, practice_task, expected_duration, priority, sort_order, completed)
SELECT fa.id, '성능 최적화', 'Core Web Vitals 개선', 'LCP/CLS 측정 후 개선 리포트 작성', '1주', 'MEDIUM', 2, 0
FROM fit_analysis fa
INNER JOIN application_case ac ON ac.id = fa.application_case_id
INNER JOIN users u ON u.id = ac.user_id
WHERE u.email = 'jiwon.kim@careertuner.dev' AND ac.company_name = '토스'
  AND NOT EXISTS (SELECT 1 FROM fit_analysis_learning_task t WHERE t.fit_analysis_id = fa.id AND t.skill = '성능 최적화')
LIMIT 1;

INSERT INTO fit_analysis_learning_task
    (fit_analysis_id, skill, title, practice_task, expected_duration, priority, sort_order, completed)
SELECT fa.id, '테스트 자동화', 'E2E 테스트 도입', 'Playwright로 핵심 플로우 3개 자동화', '2주', 'MEDIUM', 3, 0
FROM fit_analysis fa
INNER JOIN application_case ac ON ac.id = fa.application_case_id
INNER JOIN users u ON u.id = ac.user_id
WHERE u.email = 'jiwon.kim@careertuner.dev' AND ac.company_name = '토스'
  AND NOT EXISTS (SELECT 1 FROM fit_analysis_learning_task t WHERE t.fit_analysis_id = fa.id AND t.skill = '테스트 자동화')
LIMIT 1;

-- ── 3) 장기 경향 / 대시보드 요약 AI 실행 이력 (career_analysis_run) ──
--  운영자 분석 통계의 실행 이력과 사용자 취업 분석 배너에 과거 실행 흔적을 남긴다.
INSERT INTO career_analysis_run
    (user_id, analysis_type, status, input_snapshot, result, model, input_tokens, output_tokens, token_usage, retryable, created_at)
SELECT u.id, 'CAREER_TREND', 'SUCCESS',
    JSON_OBJECT('analyzedApplications', 5, 'averageFitScore', 64, 'topSkillGap', 'TypeScript'),
    JSON_OBJECT(
        'trendSummary', '최근 프런트엔드 지원에서 TypeScript와 성능 최적화가 반복 부족 역량으로 나타납니다. 강점인 React 경험을 정량 성과로 보강하면 70점대 진입이 가능합니다.',
        'recommendedDirections', JSON_ARRAY('TypeScript 마이그레이션 프로젝트 1개 추가', '성능 개선 수치를 포트폴리오에 명시', '토스·라인 등 프런트 직무 우선 재지원')),
    'mock-seed', 0, 0, 0, 0, DATE_SUB(NOW(), INTERVAL 2 DAY)
FROM users u
WHERE u.email = 'jiwon.kim@careertuner.dev'
  AND NOT EXISTS (
      SELECT 1 FROM career_analysis_run r
      WHERE r.user_id = u.id AND r.analysis_type = 'CAREER_TREND' AND r.model = 'mock-seed')
LIMIT 1;

INSERT INTO career_analysis_run
    (user_id, analysis_type, status, input_snapshot, result, model, input_tokens, output_tokens, token_usage, retryable, created_at)
SELECT u.id, 'DASHBOARD_SUMMARY', 'SUCCESS',
    JSON_OBJECT('activeApplications', 5, 'averageFitScore', 64, 'topGap', 'TypeScript'),
    JSON_OBJECT('summary', '진행 중인 지원 건 5개 중 토스가 78점으로 가장 높습니다. TypeScript 보완 학습을 시작하고 토스 지원 서류를 우선 점검하세요.'),
    'mock-seed', 0, 0, 0, 0, DATE_SUB(NOW(), INTERVAL 6 HOUR)
FROM users u
WHERE u.email = 'jiwon.kim@careertuner.dev'
  AND NOT EXISTS (
      SELECT 1 FROM career_analysis_run r
      WHERE r.user_id = u.id AND r.analysis_type = 'DASHBOARD_SUMMARY' AND r.model = 'mock-seed')
LIMIT 1;
