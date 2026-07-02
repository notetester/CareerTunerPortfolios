pragma Singleton
import QtQuick

// 앱 공통 톤앤매너 — Linear Modern (딥 스페이스 + 인디고 앰비언트).
// docs/mobile-app-v2-mockup.html · 웹 모바일 v2 와 동일 토큰. 순흑(#000)·순백(#fff) 금지.
QtObject {
    // 배경 계열 (near-black 레이어)
    readonly property color bgDeep:  "#020203"   // 최심부
    readonly property color bg:      "#050506"   // 기본 캔버스
    readonly property color surface: "#0a0a0c"   // 엘리베이티드 (사이드바·입력독)
    readonly property color raised:  Qt.rgba(1, 1, 1, 0.05)   // 카드·칩
    readonly property color hover:   Qt.rgba(1, 1, 1, 0.08)

    // 보더 (화이트 6~10% — harsh 금지)
    readonly property color border:       Qt.rgba(1, 1, 1, 0.06)
    readonly property color borderHover:  Qt.rgba(1, 1, 1, 0.10)
    readonly property color borderAccent: Qt.rgba(94/255, 106/255, 210/255, 0.30)

    // 텍스트 (#EDEDEF — 순백 금지)
    readonly property color text:   "#EDEDEF"
    readonly property color muted:  "#8A8F98"
    readonly property color subtle: Qt.rgba(1, 1, 1, 0.60)

    // 포인트 (인디고)
    readonly property color accent:     "#5E6AD2"
    readonly property color accent2:    "#6872D9"  // hover/그라데이션 상단
    readonly property color accentSoft: Qt.rgba(94/255, 106/255, 210/255, 0.14)
    readonly property color accentGlow: Qt.rgba(94/255, 106/255, 210/255, 0.30)
    readonly property color accentText: "#aab2ef"  // 액센트 칩 위 글자

    // 의미색
    readonly property color good:   "#4cc38a"
    readonly property color warn:   "#d6a24c"
    readonly property color info:   "#58a6ff"
    readonly property color danger: "#e46962"

    // 공통 치수
    readonly property int radius:  10
    readonly property int radiusL: 14

    // 모노 대문자 라벨 (Linear 시그니처) — Text 에 스프레드해서 사용
    readonly property string monoFont: "Consolas"
}
