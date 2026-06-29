"""rag_poc 실행 스크립트 공용 IO 유틸 — Windows cp949 콘솔에서 UTF-8 출력 안정화.

배경(reports/56): 2026-06-27 4090 R2b 실측 때 Windows 콘솔이 cp949 라 '✓'/한글 print 가
UnicodeEncodeError 로 깨졌다(평가 자체는 정상, 출력만 크래시). 실행 스크립트 시작 시 이 함수를
호출해 stdout/stderr 를 UTF-8 로 재설정한다. 실패해도 예외 없이 통과 — 평가 동작/지표는 불변.
"""
import sys


def configure_stdout_utf8():
    """stdout/stderr 를 UTF-8 로 재설정(가능 시). 예외는 삼킨다(출력 인코딩 안정화 전용)."""
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding="utf-8")
        except Exception:  # noqa: BLE001 — 구형/비표준 스트림이면 조용히 무시
            pass
