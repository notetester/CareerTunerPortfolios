-- 온보딩 구버전이 kind=PORTFOLIO 로만 올리고 ref_id 를 남기지 않은 파일을 사용자 프로필에 입양한다.
-- user_profile 이 없는 사용자도 파일 소유자 기준으로 빈 프로필을 먼저 만든 뒤 연결한다.
INSERT IGNORE INTO user_profile (user_id)
SELECT DISTINCT owner_user_id
  FROM file_asset
 WHERE kind = 'PORTFOLIO'
   AND ref_id IS NULL;

-- 이미 연결된 개수를 포함해 사용자당 합계 10개까지만 오래된 orphan을 입양한다.
-- 초과 orphan은 자동으로 버리지 않고 운영자가 확인할 수 있게 미연결 상태로 남긴다.
WITH linked_counts AS (
    SELECT p.user_id, COUNT(f.id) AS linked_count
      FROM user_profile p
      LEFT JOIN file_asset f
        ON f.owner_user_id = p.user_id
       AND f.kind = 'PORTFOLIO'
       AND f.ref_type = 'USER_PROFILE_PORTFOLIO'
       AND f.ref_id = p.id
     GROUP BY p.user_id
), ranked_orphans AS (
    SELECT f.id,
           p.id AS profile_id,
           ROW_NUMBER() OVER (PARTITION BY f.owner_user_id ORDER BY f.id) AS orphan_rank,
           lc.linked_count
      FROM file_asset f
      JOIN user_profile p ON p.user_id = f.owner_user_id
      JOIN linked_counts lc ON lc.user_id = f.owner_user_id
     WHERE f.kind = 'PORTFOLIO'
       AND f.ref_type IS NULL
       AND f.ref_id IS NULL
)
UPDATE file_asset f
JOIN ranked_orphans r ON r.id = f.id
   SET f.ref_type = 'USER_PROFILE_PORTFOLIO',
       f.ref_id = r.profile_id
 WHERE r.orphan_rank <= GREATEST(0, 10 - r.linked_count);
