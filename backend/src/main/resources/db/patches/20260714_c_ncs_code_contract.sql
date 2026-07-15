-- 2026-07-14 C NCS 코드 계약 정정
--
-- 초기 적재기는 sub_code에 이미 대-중-소-세 복합 코드를 저장한 뒤 ncs_code를 다시 조합했다.
-- sub_code VARCHAR(20)는 표준 23자 복합 코드를 non-strict MySQL에서 잘랐을 수 있으므로, 복원 기준은
-- 온전히 남는 ncs_code의 정확한 double-prefix 패턴이다. canonical row가 이미 있으면 legacy row를 제거하고,
-- 아니면 leaf sub_code로 재키잉한다. 적용 뒤 같은 double-prefix가 남으면 CHECK로 patch를 실패시킨다.

START TRANSACTION;

DELETE legacy
FROM ncs_classification legacy
JOIN ncs_classification canonical
 ON canonical.id <> legacy.id
 AND canonical.ncs_code = CONCAT(
       legacy.major_code, '-', legacy.middle_code, '-', legacy.minor_code, '-',
       SUBSTRING_INDEX(legacy.ncs_code, '-', -1)
     )
WHERE legacy.ncs_code = CONCAT(
        legacy.major_code, '-', legacy.middle_code, '-', legacy.minor_code, '-',
        legacy.major_code, '-', legacy.middle_code, '-', legacy.minor_code, '-',
        SUBSTRING_INDEX(legacy.ncs_code, '-', -1)
      );

UPDATE ncs_classification
SET sub_code = SUBSTRING_INDEX(ncs_code, '-', -1),
    ncs_code = CONCAT(
      major_code, '-', middle_code, '-', minor_code, '-', SUBSTRING_INDEX(ncs_code, '-', -1)
    )
WHERE ncs_code = CONCAT(
        major_code, '-', middle_code, '-', minor_code, '-',
        major_code, '-', middle_code, '-', minor_code, '-',
        SUBSTRING_INDEX(ncs_code, '-', -1)
      );

-- canonical ncs_code를 먼저 수동 보정했지만 sub_code가 잘린 composite로 남은 행도 leaf로 통일한다.
UPDATE ncs_classification
SET sub_code = SUBSTRING_INDEX(ncs_code, '-', -1)
WHERE ncs_code = CONCAT(
        major_code, '-', middle_code, '-', minor_code, '-',
        SUBSTRING_INDEX(ncs_code, '-', -1)
      )
  AND sub_code <> SUBSTRING_INDEX(ncs_code, '-', -1);

DROP TEMPORARY TABLE IF EXISTS _ncs_code_contract_assert;
CREATE TEMPORARY TABLE _ncs_code_contract_assert (
    remaining_double_prefix BIGINT NOT NULL,
    invalid_composite BIGINT NOT NULL,
    CONSTRAINT chk_ncs_code_contract_zero CHECK (
      remaining_double_prefix = 0 AND invalid_composite = 0
    )
);
INSERT INTO _ncs_code_contract_assert (remaining_double_prefix, invalid_composite)
SELECT
  COALESCE(SUM(ncs_code = CONCAT(
        major_code, '-', middle_code, '-', minor_code, '-',
        major_code, '-', middle_code, '-', minor_code, '-',
        SUBSTRING_INDEX(ncs_code, '-', -1)
      )), 0),
  COALESCE(SUM(ncs_code <> CONCAT(major_code, '-', middle_code, '-', minor_code, '-', sub_code)), 0)
FROM ncs_classification
;
DROP TEMPORARY TABLE _ncs_code_contract_assert;

COMMIT;
