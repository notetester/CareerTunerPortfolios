-- A 관리자/직원 등급·급여 관리: 사내 관리자 계정의 조직 등급(연차/티어/밴드/등급/호봉)과
-- 기본급을 체계적으로 관리하는 콘솔용 테이블. 기존 권한 거버넌스(admin_permission_*)를
-- 보완하는 등급 속성 레이어. 급여 금액은 민감정보로 SUPER_ADMIN 전용 접근.
-- 변경은 admin_staff_grade_history 에 old/new 스냅샷으로 감사.

CREATE TABLE IF NOT EXISTS admin_staff_grade (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL COMMENT '관리자/직원 사용자 ID',
    department     VARCHAR(60)  NULL COMMENT '부서',
    seniority      VARCHAR(30)  NULL COMMENT '연차 구분. JUNIOR/MID/SENIOR/LEAD/PRINCIPAL',
    job_tier       VARCHAR(30)  NULL COMMENT '직군 티어',
    pay_band       VARCHAR(30)  NULL COMMENT '급여 밴드',
    job_grade      VARCHAR(30)  NULL COMMENT '직급/등급',
    pay_step       VARCHAR(30)  NULL COMMENT '호봉/스텝',
    base_salary    INT          NULL COMMENT '기본 연봉(원). 민감정보',
    currency       VARCHAR(10)  NOT NULL DEFAULT 'KRW',
    effective_date DATE         NULL COMMENT '적용 시작일',
    memo           VARCHAR(255) NULL,
    updated_by     BIGINT       NULL,
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_admin_staff_grade_user (user_id),
    KEY idx_admin_staff_grade_dept (department),
    KEY idx_admin_staff_grade_updated_by (updated_by),
    CONSTRAINT fk_admin_staff_grade_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_admin_staff_grade_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '관리자/직원 조직 등급 및 급여';

CREATE TABLE IF NOT EXISTS admin_staff_grade_history (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    old_values_json JSON         NULL COMMENT '변경 전 스냅샷',
    new_values_json JSON         NULL COMMENT '변경 후 스냅샷',
    changed_by      BIGINT       NULL,
    source          VARCHAR(20)  NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL/EXCEL',
    memo            VARCHAR(255) NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_admin_staff_grade_history_user (user_id, created_at),
    KEY idx_admin_staff_grade_history_changed_by (changed_by),
    CONSTRAINT fk_staff_grade_hist_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_staff_grade_hist_changed_by FOREIGN KEY (changed_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '관리자/직원 등급·급여 변경 이력';
