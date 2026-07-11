# 관리자 권한 DB 운영 절차

이 폴더의 SQL은 자동 migration이 아니라 승인된 운영자가 MySQL 8에서 한 번씩 실행하는
fail-closed 유지보수 절차다. `data.sql`의 알려진 seed 계정을 공유·운영 관리자로 승격하지 않는다.

## 안전 기준

- `SUPER_ADMIN`은 강한 개별 비밀번호의 A~F 전용 계정을 만들거나, 이미 `ACTIVE`이고 이메일 인증을 마친 개인 개발자 계정을 승격한다.
- 대상과 실행자는 고정 seed 5개가 아니어야 하며, 비밀번호 로그인이 활성화된 개별 BCrypt 자격 증명을 사용해야 한다. 공용 비밀번호 hash나 로그인 불가능 계정은 발급·quorum에서 제외한다.
- 발급·회수는 실행자와 대상 이메일, 변경 티켓, 10자 이상의 사유를 명시한다.
- 회수 후에도 안전한 `ACTIVE SUPER_ADMIN`이 최소 3명 남아야 한다.
- 각 작업은 별도 mysql 세션에서 실행한다. 실패한 세션은 종료해 transaction과 named lock을 해제한다.
- 실행 전 DB snapshot을 확보하고 애플리케이션의 관리자 변경 작업을 잠시 중지한다.

## 최초 준비 순서

기존 공유 DB에서는 다음 순서로 준비한다.

1. `patches/20260711_admin_active_assignment_unique.sql`
2. `patches/20260711_admin_soft_delete_columns.sql`
3. `patches/20260711_admin_permission_crud_catalog.sql`
4. 계정이 없으면 A~F별 `create_verified_developer_superadmin.sql`, 기존 계정이면 `grant_verified_developer_superadmin.sql`
5. `verify_superadmin_quorum.sql`이 `PASS`인지 확인
6. 필요한 경우 `patches/20260711_admin_seed_role_reconciliation.sql`

고정 seed 정합 패치는 안전한 비seed `ACTIVE SUPER_ADMIN`이 3명 미만이면 실패하며,
조건을 만족하는 비seed 계정은 변경하지 않는다.

## 개발자별 발급

이미 가입한 개인 개발자는 `grant_verified_developer_superadmin.sql`로 승격한다.
팀 계정이 아직 없으면 A~F 전용 이메일과 서로 다른 강한 비밀번호를 준비하고, plaintext가 아닌
cost 10~14 BCrypt hash만 `create_verified_developer_superadmin.sql`에 전달한다. hash와 실제 비밀번호는
저장소, PR, 이슈, 채팅에 남기지 않는다. 명령줄 인자 기록도 피하도록 mysql 대화형 세션에서 실행한다.
경로는 현재 clone의 절대 경로로 바꾼다.

```sql
SET @ct_operator_email = 'existing-superadmin@example.com';
SET @ct_target_email = 'dev-admin-a@careertuner.dev';
SET @ct_target_name = 'A 영역 개발 관리자';
SET @ct_password_hash = '$2b$12$...로컬에서 생성한 전체 BCrypt hash...';
SET @ct_change_ref = 'OPS-1200';
SET @ct_reason = 'A 영역 관리자 통합 테스트 전용 계정 발급';
SOURCE C:/path/to/CareerTuner/backend/src/main/resources/db/maintenance/create_verified_developer_superadmin.sql;
```

기본 허용 이메일은 `dev-admin-a@careertuner.dev`부터 `dev-admin-f@careertuner.dev`까지다.
그 밖의 개인 이메일은 승인 후 `SET @ct_allow_external_email = 1;`을 같은 세션에 명시한다.
기존 계정과 충돌하면 실패하며, 이 절차로 만든 동일 이메일·이름·hash·역할의 재실행만 `created=0` no-op이다.

이미 가입한 개인 개발자 승격은 다음과 같다.

```sql
SET @ct_operator_email = 'existing-superadmin@example.com';
SET @ct_target_email = 'verified-developer@example.com';
SET @ct_change_ref = 'OPS-1234';
SET @ct_reason = '시연 및 관리자 기능 통합 검증 담당자 발급';
SOURCE C:/path/to/CareerTuner/backend/src/main/resources/db/maintenance/grant_verified_developer_superadmin.sql;
SOURCE C:/path/to/CareerTuner/backend/src/main/resources/db/maintenance/verify_superadmin_quorum.sql;
```

동일 입력을 재실행하면 `changed=0`이며 역할 이력과 감사 로그가 추가되지 않는다.
발급 직후 기존 refresh token은 회수되므로 대상 개발자는 다시 로그인해야 한다.

## 개발자별 회수

회수 SQL은 대상을 `USER`로 복구하고 직접 권한, 그룹, refresh token을 모두 회수한다.
대상을 제외한 안전한 quorum이 3명 미만이면 아무 변경도 하지 않고 실패한다.

```sql
SET @ct_operator_email = 'existing-superadmin@example.com';
SET @ct_target_email = 'departing-developer@example.com';
SET @ct_change_ref = 'OPS-5678';
SET @ct_reason = '관리자 통합 검증 담당 종료에 따른 권한 회수';
SOURCE C:/path/to/CareerTuner/backend/src/main/resources/db/maintenance/revoke_verified_developer_superadmin.sql;
SOURCE C:/path/to/CareerTuner/backend/src/main/resources/db/maintenance/verify_superadmin_quorum.sql;
```

## 감사 확인

발급·회수 결과는 다음 세 곳에 남는다.

- `user_role_change_history`: 역할 전환 전후와 실행자
- `admin_permission_audit`: `SUPER_ADMIN_GRANTED` 또는 `SUPER_ADMIN_REVOKED`
- `admin_action_log`: DB 운영 절차, 티켓, refresh token 회수 여부

권한 기본값은 별도 중복 테이블을 만들지 않고 runtime이 실제로 읽는 `admin_permission_group_item`으로 일원화한다.
일반 ADMIN 그룹에는 담당 영역 READ/CREATE/UPDATE만 포함한다. DELETE는 자동 포함하지 않지만
슈퍼 관리자가 일반 ADMIN에게 개별 부여할 수 있으며, `SUPER_ADMIN_GROUP`은 exact catalog 전체를 가진다.
