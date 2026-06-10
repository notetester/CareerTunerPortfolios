-- Deletes only temporary auth test users created during A-part login/member-management verification.
-- Do not run this for project seed users such as admin@careertuner.dev.

START TRANSACTION;

-- Preview targets before delete.
SELECT id, email, status, created_at
FROM users
WHERE email LIKE 'codex-%-test-%@example.com'
ORDER BY id;

DELETE ulh
FROM user_login_history ulh
INNER JOIN users u ON u.id = ulh.user_id
WHERE u.email LIKE 'codex-%-test-%@example.com';

DELETE ev
FROM email_verification ev
INNER JOIN users u ON u.id = ev.user_id
WHERE u.email LIKE 'codex-%-test-%@example.com';

DELETE rt
FROM refresh_token rt
INNER JOIN users u ON u.id = rt.user_id
WHERE u.email LIKE 'codex-%-test-%@example.com';

DELETE uc
FROM user_consent uc
INNER JOIN users u ON u.id = uc.user_id
WHERE u.email LIKE 'codex-%-test-%@example.com';

DELETE ush
FROM user_status_history ush
INNER JOIN users u ON u.id = ush.user_id
WHERE u.email LIKE 'codex-%-test-%@example.com';

DELETE FROM users
WHERE email LIKE 'codex-%-test-%@example.com';

COMMIT;
