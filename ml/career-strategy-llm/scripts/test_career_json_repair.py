"""career_json_repair(백엔드 truncation 수리 미러) 회귀 테스트.

실행: python scripts/test_career_json_repair.py  (또는 pytest)
백엔드 CareerAnalysisOssClientTest.repairTruncatedJson_* 와 동일한 불변식을 파이썬 포팅에도 건다."""

from career_json_repair import is_repairable, parse_content, repair_truncated_json


def test_truncation_repaired():
    # post-R3 재벤치마크 실측 형태(닫힘 중괄호 누락)
    trunc = (
        '{\n  "gateResult": {\n    "gateStatus": "REVIEW_REQUIRED",\n'
        '    "reasons": [\n      {"skillName": "WMS", "severity": "WARNING"}\n    ]\n  }'
    )
    assert is_repairable(trunc)
    assert repair_truncated_json('{"a": [1, 2,') == '{"a": [1, 2]}'


def test_non_truncation_not_repaired():
    assert repair_truncated_json('{"a": "cut mid strin') is None   # 문자열 중간 절단
    assert repair_truncated_json('{"a": 1]') is None                # 괄호 불일치
    assert repair_truncated_json('{"a": 1}') is None                # 이미 균형(다른 원인)
    assert repair_truncated_json('plain text') is None


def test_parse_content_paths():
    assert parse_content('{"a": 1}') == {"a": 1}
    assert parse_content('```json\n{"a": 1}\n```') == {"a": 1}      # fence 제거
    assert parse_content('설명: {"a": 1} 끝') == {"a": 1}            # 잡설 제거(extract span)
    assert parse_content('') is None
    assert parse_content('{"a": "cut mid strin') is None            # 수리 불가


def _run() -> int:
    fns = [v for k, v in sorted(globals().items()) if k.startswith("test_") and callable(v)]
    for fn in fns:
        fn()
        print(f"PASS {fn.__name__}")
    print(f"OK {len(fns)} tests")
    return 0


if __name__ == "__main__":
    raise SystemExit(_run())
