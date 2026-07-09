-- 아이디 기반 회원가입을 지원하기 위해 이메일을 선택 입력으로 전환한다.
-- UNIQUE KEY는 그대로 유지한다. MySQL UNIQUE 인덱스는 NULL 값을 여러 건 허용하므로
-- 이메일 미등록 계정이 여러 개 있어도 충돌하지 않고, 실제 이메일은 계속 중복 방지된다.
ALTER TABLE users
    MODIFY COLUMN email VARCHAR(255) NULL
        COMMENT 'Login and recovery email. ID-only signup users can register and verify it later.';
