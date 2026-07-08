"""백엔드 CareerAnalysisOssClient 의 parseContent + repairTruncatedJson 을 Python 으로 미러.

목적: 벤치마크 평가기의 엄격 PARSE_FAIL(raw) 지표는 그대로 두고, "production 경로(truncation
수리)라면 살아났을 케이스"를 병렬 지표로 정량화한다. 로직은 backend 와 동일해야 한다
(fence 제거 → extract_json_span → json.loads → 실패 시 truncation 한정 수리 → 재파싱).
truncation(닫힘 괄호 잘림)만 수리하고, 괄호 불일치·문자열 중간 절단은 수리하지 않는다."""

from __future__ import annotations

import json
import re
from typing import Any

_FENCE_RE = re.compile(r"^```(?:json)?\s*", re.IGNORECASE)


def extract_json_span(text: str) -> str:
    """첫 {/[ 부터 마지막 }/] 까지만 취한다(소형 모델 잡설 제거) — backend extractJsonSpan 동일."""
    obj_start = text.find("{")
    arr_start = text.find("[")
    if obj_start < 0:
        start = arr_start
    elif arr_start < 0:
        start = obj_start
    else:
        start = min(obj_start, arr_start)
    end = max(text.rfind("}"), text.rfind("]"))
    return text[start:end + 1] if 0 <= start < end else text


def repair_truncated_json(text: str) -> str | None:
    """닫힘 괄호가 잘린 truncation 만 수리 — backend repairTruncatedJson 동일 알고리즘.

    문자열/이스케이프 상태를 추적하며 {}/[] 스택을 센다. 끝에 열린 괄호만 남았으면 닫아 준다.
    괄호 불일치(스택 오염)나 문자열 중간 절단(닫히지 않은 따옴표)은 None(수리 대상 아님)."""
    stack: list[str] = []
    in_string = False
    escaped = False
    for ch in text:
        if escaped:
            escaped = False
            continue
        if in_string:
            if ch == "\\":
                escaped = True
            elif ch == '"':
                in_string = False
            continue
        if ch == '"':
            in_string = True
        elif ch == "{":
            stack.append("}")
        elif ch == "[":
            stack.append("]")
        elif ch in "}]":
            if not stack or stack[-1] != ch:
                return None  # 괄호 불일치 — truncation 아님
            stack.pop()
    if in_string or not stack:
        return None  # 문자열 중간 절단이거나 이미 균형(다른 원인)
    repaired = text.rstrip()
    if repaired.endswith(","):  # 잘린 꼬리의 trailing comma 제거
        repaired = repaired[:-1]
    while stack:
        repaired += stack.pop()
    return repaired


def parse_content(content: str) -> Any | None:
    """backend parseContent 미러 — 정상 파싱 결과(dict/list) 또는 None(빈응답/수리불가)."""
    text = (content or "").strip()
    if text.startswith("```"):
        text = _FENCE_RE.sub("", text)
        text = re.sub(r"\s*```$", "", text).strip()
    text = extract_json_span(text)
    if not text.strip():
        return None
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        pass
    repaired = repair_truncated_json(text)
    if repaired is not None:
        try:
            return json.loads(repaired)
        except json.JSONDecodeError:
            return None
    return None


def is_repairable(raw_text: str) -> bool:
    """raw 파싱은 실패했지만 truncation 수리로 살아나는가 — production 수리 효과 판정."""
    return parse_content(raw_text) is not None
