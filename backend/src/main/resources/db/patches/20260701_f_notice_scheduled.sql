-- =====================================================================
--  F 담당(공지) 스키마 마이그레이션 — 2026-07-01
--  공지(notice) 예약 발행 — scheduled_at 컬럼 추가 (방식 a: 조회 시점 판정).
--
--  배경:
--   AdminNotices/NoticeCompose(관리자 공지 작성)에 "예약" UI 가 이미 있으나
--   백엔드가 예약 시각을 받지도 저장하지도 않아 예약 공지가 영원히 노출되지 않았다.
--   약관(legal effective_date)·가이드라인(community_guideline scheduled_at)과 동일하게
--   "읽는 시점에 scheduled_at<=현재(KST) 면 공개"로 처리한다(별도 스케줄러 없이 예약 발행).
--
--  설계 결정(DB 체크리스트):
--   - 라이프사이클: status = DRAFT | PUBLISHED | SCHEDULED.
--       SCHEDULED 는 상태를 승격하지 않는다(조회 시점 판정). 시각 도달 시 공개 쿼리가
--       자동 포함하고, status 자체는 SCHEDULED 로 유지한다(약관/가이드라인과 동일).
--   - scheduled_at : 예약 발행 시각. status=SCHEDULED 일 때만 채운다. 그 외 NULL.
--   - 비교 기준은 KST(+09:00): 공개 쿼리에서 scheduled_at <= (UTC_TIMESTAMP() + INTERVAL 9 HOUR).
--       UTC_TIMESTAMP() 는 세션 타임존과 무관하게 항상 UTC → +9h=KST (LegalMapper 검증 표현 재사용).
--   - 관리자 조회는 status 무관 전체를 그대로 본다(예약/임시 포함). 공개 조회만 시각 게이트.
--
--  ⚠️ MySQL 8 은 ADD COLUMN 에 IF NOT EXISTS 미지원 → 이 ALTER 는 한 번만 적용한다(재실행 시 1060 에러).
--  ⚠️ 기존 행은 scheduled_at 이 NULL 로 채워진다 — PUBLISHED/DRAFT 는 영향 없음(공개 OR 분기는 SCHEDULED 한정).
--  ⚠️ 공유 DB 변경 → 팀 합의 후 AWS(team1_db) 적용. 로컬 dev DB 는 먼저 적용해 검증.
--  실행: mysql -h <host> -u <user> -p <db> < 20260701_f_notice_scheduled.sql
--
--  ☐ 적용 이력: (미적용) 로컬 적용일 ____  / 운영 공유 DB(team1_db) 적용일 ____
-- =====================================================================

ALTER TABLE notice
    ADD COLUMN scheduled_at DATETIME NULL
        COMMENT '예약 발행 시각(KST). status=SCHEDULED 일 때만 채움. 조회 시점 판정으로 노출'
        AFTER status;

-- ── 검증 (적용 후 확인용) ────────────────────────────────────────────
-- SHOW CREATE TABLE notice;
-- SELECT id, title, status, scheduled_at, published_at FROM notice ORDER BY id DESC LIMIT 5;
