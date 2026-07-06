-- 사용권이 남아 있으면 먼저 차감하고, 소진된 뒤에는 기능별 사용량 크레딧 정책으로 전환한다.
UPDATE subscription_benefit_policy
   SET overage_policy = 'CREDIT',
       credit_cost = CASE benefit_code
           WHEN 'APPLICATION_ANALYSIS' THEN 2
           WHEN 'MOCK_INTERVIEW' THEN 2
           WHEN 'VOICE_INTERVIEW' THEN 3
           ELSE credit_cost
       END
 WHERE quantity > 0
   AND benefit_code IN ('APPLICATION_ANALYSIS', 'MOCK_INTERVIEW', 'VOICE_INTERVIEW');
