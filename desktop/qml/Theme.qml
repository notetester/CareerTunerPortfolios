pragma Singleton
import QtQuick

// 앱 공통 톤앤매너 — 블랙 + 인디고(톤다운) 다크.
// docs/desktop-app-v2-mockup.html 팔레트와 1:1.
QtObject {
    // 배경 계열
    readonly property color bg:      "#08090c"   // 최심 배경
    readonly property color surface: "#0e1014"   // 사이드바·카드
    readonly property color raised:  "#15181d"   // 한 단 위 (칩·입력)
    readonly property color hover:   "#1b1f26"   // hover
    readonly property color border:  "#23272f"

    // 텍스트
    readonly property color text:  "#eceef2"
    readonly property color muted: "#8a919e"

    // 포인트 (인디고 톤다운)
    readonly property color accent:     "#5e6ad2"
    readonly property color accent2:    "#7d88de"  // 그라데이션 상단
    readonly property color accentSoft: Qt.rgba(94/255, 106/255, 210/255, 0.16)

    // 의미색
    readonly property color good:   "#4cc38a"
    readonly property color warn:   "#d6a24c"
    readonly property color info:   "#58a6ff"
    readonly property color danger: "#e46962"

    // 공통 치수
    readonly property int radius:  10
    readonly property int radiusL: 14
}
