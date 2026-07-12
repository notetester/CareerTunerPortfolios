-- 시연용 면접 그래프 보강(멱등). 20260710_demo_seed_bulk.sql 이 이서연(user3) 면접 세션 920001~920004 를
-- total_score 만 넣어 '대시보드/분석 지표용'으로 두었더니, 그 세션의 '면접 리포트' 탭을 열면
-- getReport 가 답변 부재로 400("평가된 면접 답변이 없습니다")을 던졌다(A~F 검증에서 D 유일 실오류).
-- 여기서 각 세션에 질문 3 + 답변 3(채점/피드백) + interview_session.report JSON 을 채워, 리포트가
-- 캐시 경로(session.report 반환)로 LLM 호출 없이 즉시 렌더되고 관리자 상세도 질문/답변이 보이게 한다.
-- ID 는 9203xxx(질문)·9204xxx(답변) 고정 + ON DUPLICATE KEY UPDATE 무시로 재실행 안전.

-- ── 1) 질문(세션별 3) ──
INSERT INTO interview_question (id, interview_session_id, parent_question_id, question, model_answer, question_type, sort_order) VALUES
 -- 920001 삼성SDS 백엔드 · BASIC
 (9203011, 920001, NULL, 'HashMap 과 TreeMap 의 차이와 각각을 언제 쓰는지 설명해 주세요.', 'HashMap 은 평균 O(1) 조회로 순서가 필요 없을 때, TreeMap 은 정렬·범위 조회가 필요할 때 O(log n) 으로 사용합니다.', 'TECH', 1),
 (9203012, 920001, NULL, 'DB 트랜잭션 격리수준을 설명하고 각 수준이 막는 문제를 말해 주세요.', 'READ COMMITTED·REPEATABLE READ·SERIALIZABLE 순으로 팬텀/논리 리드 등을 차단하며, 격리 강화와 동시성 사이의 트레이드오프를 설명합니다.', 'TECH', 2),
 (9203013, 920001, NULL, '대량 트래픽에서 응답 지연이 발생하면 어떻게 원인을 찾겠습니까?', 'APM·슬로우쿼리·리소스 지표로 병목을 격리하고, 캐시·인덱스·커넥션 풀 순으로 좁혀 갑니다.', 'SITUATION', 3),
 -- 920002 삼성SDS 백엔드 · JOB
 (9203021, 920002, NULL, 'Spring Boot 에서 MyBatis 를 선택한 이유와 JPA 대비 장단점을 설명해 주세요.', '복잡 쿼리 가시성·튜닝 용이성 때문에 MyBatis 를 쓰되, 단순 CRUD 생산성은 JPA 가 낫다는 트레이드오프를 설명합니다.', 'EXPECTED', 1),
 (9203022, 920002, NULL, 'Kafka 이벤트 파이프라인 설계 경험을 설명해 주세요.', '토픽·파티셔닝·컨슈머 그룹 설계와 재처리·순서 보장 전략을 근거와 함께 제시합니다.', 'TECH', 2),
 (9203023, 920002, NULL, '클라우드 전환 프로젝트에서 무중단 배포를 어떻게 보장하겠습니까?', '블루-그린/카나리 배포와 헬스체크·롤백 기준을 정의해 무중단을 보장합니다.', 'SITUATION', 3),
 -- 920003 쿠팡 서버 · JOB
 (9203031, 920003, NULL, '대규모 트래픽 커머스에서 성능 병목을 찾고 개선한 경험을 STAR 로 설명해 주세요.', '상황·과제·행동·결과 구조로 병목 원인과 개선 수치를 제시합니다.', 'EXPECTED', 1),
 (9203032, 920003, NULL, 'Redis 를 캐시로 쓸 때 캐시 무효화 전략을 설명해 주세요.', 'TTL·write-through·이벤트 기반 무효화의 트레이드오프와 스탬피드 방지책을 설명합니다.', 'TECH', 2),
 (9203033, 920003, NULL, '주문 급증 시 재고 정합성을 어떻게 보장하겠습니까?', '낙관/비관 락, 원자적 감소 연산, 큐잉으로 오버셀을 방지하는 방안을 제시합니다.', 'SITUATION', 3),
 -- 920004 KB국민은행 IT개발직 · PERSONALITY
 (9203041, 920004, NULL, '협업 중 의견 충돌이 있었을 때 어떻게 해결했는지 말해 주세요.', '근거·데이터 기반 합의와 역할 조정 사례를 구체적으로 제시합니다.', 'PERSONALITY', 1),
 (9203042, 920004, NULL, '금융권 개발자로서 가장 중요하다고 생각하는 가치는 무엇입니까?', '정확성·보안·감사 추적 등 금융 도메인 가치와 이를 코드/운영에 반영한 경험을 연결합니다.', 'PERSONALITY', 2),
 (9203043, 920004, NULL, '운영 중 장애가 발생하면 커뮤니케이션을 어떻게 하겠습니까?', '영향 범위 공유·상태 업데이트 주기·사후 회고까지의 커뮤니케이션 원칙을 제시합니다.', 'SITUATION', 3)
ON DUPLICATE KEY UPDATE id = id;

-- ── 2) 답변(질문별 1, 채점·피드백 포함) ──
INSERT INTO interview_answer (id, question_id, answer_text, score, feedback, created_at) VALUES
 (9204011, 9203011, 'HashMap 은 해시 기반이라 조회가 빠르고 순서가 없고, TreeMap 은 키가 정렬됩니다. 정렬이나 범위 조회가 필요하면 TreeMap 을 씁니다.', 76, '핵심은 정확합니다. 평균/최악 시간복잡도와 실제 사용 시나리오를 함께 제시하면 더 좋습니다.', DATE_SUB(NOW(), INTERVAL 9 DAY)),
 (9204012, 9203012, 'READ COMMITTED, REPEATABLE READ, SERIALIZABLE 이 있고 뒤로 갈수록 강하게 격리합니다. REPEATABLE READ 는 반복 조회 일관성을 보장합니다.', 72, '격리수준 명칭은 정확합니다. 팬텀 리드 같은 구체적 이상현상과 성능 트레이드오프를 덧붙이면 설득력이 올라갑니다.', DATE_SUB(NOW(), INTERVAL 9 DAY)),
 (9204013, 9203013, '모니터링 지표로 어디가 느린지 확인하고, 슬로우 쿼리와 커넥션 풀부터 점검하겠습니다.', 74, '원인 격리 절차가 좋습니다. APM·슬로우쿼리 등 실제 지표 사례를 들면 더 구체적입니다.', DATE_SUB(NOW(), INTERVAL 9 DAY)),
 (9204021, 9203021, '복잡한 통계 쿼리 튜닝이 잦아 SQL 가시성이 높은 MyBatis 를 선택했습니다. 단순 CRUD 는 JPA 가 더 생산적이라고 생각합니다.', 84, '선택 근거와 트레이드오프를 균형 있게 설명했습니다. 실제 튜닝으로 개선한 수치를 덧붙이면 최상입니다.', DATE_SUB(NOW(), INTERVAL 2 DAY)),
 (9204022, 9203022, '주문 이벤트를 토픽으로 발행하고 컨슈머 그룹으로 정산·알림을 분리했습니다.', 79, '설계 의도는 좋습니다. 파티셔닝 키와 재처리·순서 보장 전략을 구체화하면 좋습니다.', DATE_SUB(NOW(), INTERVAL 2 DAY)),
 (9204023, 9203023, '카나리 배포로 일부 트래픽을 먼저 보내고 헬스체크가 정상이면 확대, 이상 시 롤백하겠습니다.', 80, '무중단 전략이 명확합니다. 롤백 트리거 지표를 수치로 정의하면 더 견고합니다.', DATE_SUB(NOW(), INTERVAL 2 DAY)),
 (9204031, 9203031, '피크 시간 주문 API 지연을 APM 으로 추적해 N+1 쿼리를 발견하고 페치 조인·캐시로 응답을 320ms 에서 90ms 로 줄였습니다.', 78, 'STAR 구조와 수치가 좋습니다. 트래픽 규모와 개선 전후 처리량을 함께 제시하면 더 좋습니다.', DATE_SUB(NOW(), INTERVAL 5 DAY)),
 (9204032, 9203032, 'TTL 로 기본 만료를 두고, 데이터 변경 시 이벤트로 캐시를 무효화합니다.', 75, '전략은 적절합니다. 캐시 스탬피드 방지(뮤텍스/조기 재계산)를 덧붙이면 좋습니다.', DATE_SUB(NOW(), INTERVAL 5 DAY)),
 (9204033, 9203033, '재고 감소를 원자적 연산으로 처리하고, 초과 주문은 큐로 직렬화해 오버셀을 막겠습니다.', 78, '정합성 보장 방안이 구체적입니다. 락 경합 시 대기/실패 정책도 언급하면 완성도가 높습니다.', DATE_SUB(NOW(), INTERVAL 5 DAY)),
 (9204041, 9203041, '기술 스택 선택을 두고 충돌했을 때 각 안의 근거를 벤치마크 수치로 비교해 팀이 합의하도록 했습니다.', 70, '데이터 기반 합의 사례가 좋습니다. 본인의 역할과 갈등 완화 과정을 더 구체적으로 담으면 좋습니다.', DATE_SUB(NOW(), INTERVAL 8 DAY)),
 (9204042, 9203042, '금융은 작은 오류도 큰 영향을 주므로 정확성과 감사 추적을 가장 중요하게 생각합니다.', 68, '가치 선택은 적절합니다. 그 가치를 실제 코드·운영에 반영한 경험을 연결하면 설득력이 올라갑니다.', DATE_SUB(NOW(), INTERVAL 8 DAY)),
 (9204043, 9203043, '영향 범위를 먼저 공유하고 정해진 주기로 상태를 업데이트하며, 종료 후 회고로 재발을 막겠습니다.', 69, '커뮤니케이션 원칙이 명확합니다. 이해관계자별 채널·표현 차이를 언급하면 더 좋습니다.', DATE_SUB(NOW(), INTERVAL 8 DAY))
ON DUPLICATE KEY UPDATE id = id;

-- ── 3) 세션 리포트 JSON(InterviewReportResponse 형태). getReport 캐시 경로가 그대로 반환 → LLM 무호출. ──
UPDATE interview_session SET report = '{"totalScore":74,"previousScore":null,"questionCount":3,"durationLabel":"18분","categories":[{"label":"직무 전문성","score":76},{"label":"논리적 구성","score":72},{"label":"의사소통","score":74}],"summaryFeedback":["자료구조·트랜잭션 기본기를 정확히 설명했습니다.","답변에 실제 프로젝트 수치를 덧붙이면 설득력이 올라갑니다.","상황형 질문은 원인 격리 절차를 단계로 제시하면 좋습니다."],"questionScores":[]}' WHERE id = 920001;
UPDATE interview_session SET report = '{"totalScore":81,"previousScore":74,"questionCount":3,"durationLabel":"21분","categories":[{"label":"직무 전문성","score":83},{"label":"문제 해결","score":80},{"label":"의사소통","score":80}],"summaryFeedback":["기술 선택의 근거와 트레이드오프를 균형 있게 설명했습니다.","Kafka 설계는 파티셔닝·재처리 전략을 구체화하면 좋습니다.","무중단 배포의 롤백 기준을 수치로 정의하면 더 견고합니다."],"questionScores":[]}' WHERE id = 920002;
UPDATE interview_session SET report = '{"totalScore":77,"previousScore":null,"questionCount":3,"durationLabel":"19분","categories":[{"label":"직무 전문성","score":77},{"label":"문제 해결","score":78},{"label":"의사소통","score":76}],"summaryFeedback":["성능 개선 경험을 STAR 구조와 수치로 잘 제시했습니다.","캐시 무효화는 스탬피드 방지책을 덧붙이면 좋습니다.","재고 정합성 답변에 락 경합 정책을 추가하면 완성도가 높습니다."],"questionScores":[]}' WHERE id = 920003;
UPDATE interview_session SET report = '{"totalScore":69,"previousScore":null,"questionCount":3,"durationLabel":"16분","categories":[{"label":"인성·태도","score":70},{"label":"커뮤니케이션","score":69},{"label":"직무 이해","score":68}],"summaryFeedback":["데이터 기반 합의 사례가 좋습니다.","금융 도메인 가치를 실제 경험과 연결하면 설득력이 올라갑니다.","장애 커뮤니케이션은 이해관계자별 채널을 언급하면 더 좋습니다."],"questionScores":[]}' WHERE id = 920004;
