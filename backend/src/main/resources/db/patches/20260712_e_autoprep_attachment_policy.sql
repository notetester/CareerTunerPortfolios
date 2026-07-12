-- AutoPrep 첨부 한도를 코드 분기 대신 E billing policy에서 관리한다.
INSERT INTO benefit_catalog (code, name, description, active, sort_order)
VALUES ('AUTOPREP_ATTACHMENT', 'AutoPrep 첨부 한도', 'AutoPrep 한 요청에서 읽을 수 있는 첨부 파일 수', 1, 55)
ON DUPLICATE KEY UPDATE
    name = VALUES(name), description = VALUES(description), active = VALUES(active), sort_order = VALUES(sort_order);

INSERT INTO subscription_benefit_policy
    (plan_code, benefit_code, benefit_name, benefit_type, quantity, reset_cycle, overage_policy, credit_cost, active, sort_order)
VALUES
    ('FREE', 'AUTOPREP_ATTACHMENT', 'AutoPrep 첨부 한도', 'LIMIT', 1, 'NONE', 'BLOCK', 0, 1, 55),
    ('BASIC', 'AUTOPREP_ATTACHMENT', 'AutoPrep 첨부 한도', 'LIMIT', 1, 'NONE', 'BLOCK', 0, 1, 55),
    ('PRO', 'AUTOPREP_ATTACHMENT', 'AutoPrep 첨부 한도', 'LIMIT', 5, 'NONE', 'BLOCK', 0, 1, 55),
    ('PREMIUM', 'AUTOPREP_ATTACHMENT', 'AutoPrep 첨부 한도', 'LIMIT', 5, 'NONE', 'BLOCK', 0, 1, 55)
ON DUPLICATE KEY UPDATE
    benefit_name = VALUES(benefit_name), benefit_type = VALUES(benefit_type), quantity = VALUES(quantity),
    reset_cycle = VALUES(reset_cycle), overage_policy = VALUES(overage_policy), credit_cost = VALUES(credit_cost),
    active = VALUES(active), sort_order = VALUES(sort_order);
