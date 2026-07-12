pragma Singleton
import QtQuick

// 앱 공통 톤앤매너 — Linear Modern (딥 스페이스 + 인디고 앰비언트).
// docs/mobile-app-v2-mockup.html · 웹 모바일 v2 와 동일 토큰. 순흑(#000)·순백(#fff) 금지.
QtObject {
    // 사용자 설정에 따라 라이트/다크를 즉시 반영한다. 초기값은 기존 UX와 동일한 다크다.
    property bool darkMode: true

    // 배경 계열
    readonly property color bgDeep:  darkMode ? "#020203" : "#ECEEF3"
    readonly property color bg:      darkMode ? "#050506" : "#F5F6F8"
    readonly property color surface: darkMode ? "#0a0a0c" : "#FCFCFD"
    readonly property color raised:  darkMode ? Qt.rgba(1, 1, 1, 0.05) : Qt.rgba(0, 0, 0, 0.045)
    readonly property color hover:   darkMode ? Qt.rgba(1, 1, 1, 0.08) : Qt.rgba(0, 0, 0, 0.07)

    // 보더 (화이트 6~10% — harsh 금지)
    readonly property color border:       darkMode ? Qt.rgba(1, 1, 1, 0.06) : Qt.rgba(0, 0, 0, 0.10)
    readonly property color borderHover:  darkMode ? Qt.rgba(1, 1, 1, 0.10) : Qt.rgba(0, 0, 0, 0.16)
    readonly property color borderAccent: Qt.rgba(94/255, 106/255, 210/255, darkMode ? 0.30 : 0.38)

    // 텍스트 (#EDEDEF — 순백 금지)
    readonly property color text:   darkMode ? "#EDEDEF" : "#202124"
    readonly property color muted:  darkMode ? "#8A8F98" : "#667085"
    readonly property color subtle: darkMode ? Qt.rgba(1, 1, 1, 0.60) : Qt.rgba(0, 0, 0, 0.60)

    // 포인트 (인디고)
    readonly property color accent:     "#5E6AD2"
    readonly property color accent2:    "#6872D9"  // hover/그라데이션 상단
    readonly property color accentSoft: Qt.rgba(94/255, 106/255, 210/255, 0.14)
    readonly property color accentGlow: Qt.rgba(94/255, 106/255, 210/255, 0.30)
    readonly property color accentText: darkMode ? "#aab2ef" : "#4F5AC7"  // 액센트 칩 위 글자

    // 의미색
    readonly property color good:   darkMode ? "#4cc38a" : "#16845B"
    readonly property color warn:   darkMode ? "#d6a24c" : "#9C6700"
    readonly property color info:   darkMode ? "#58a6ff" : "#1769AA"
    readonly property color danger: darkMode ? "#e46962" : "#C2413A"

    // 공통 치수
    readonly property int radius:  10
    readonly property int radiusL: 14

    // 모노 대문자 라벨 (Linear 시그니처) — Text 에 스프레드해서 사용
    readonly property string monoFont: "Consolas"
}
