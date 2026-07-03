import QtQuick
import QtQuick.Shapes

// Lucide 스타일 스트로크 아이콘 (이모지 대체).
// 24x24 뷰박스 SVG 패스를 Qt Quick Shapes 로 그린다 — 바이너리 리소스 없이 텍스트만으로 관리.
// 사용: Icon { name: "mic"; size: 16; color: Theme.muted }
Item {
    id: root
    property string name: ""
    property color color: "#8A8F98"
    property real size: 16
    property real strokeWidth: 1.8

    width: size
    height: size

    // 아이콘별 패스 (여러 서브패스는 한 문자열에 M 으로 연결)
    readonly property var icons: ({
        "monitor":    "M4 3 H20 A2 2 0 0 1 22 5 V15 A2 2 0 0 1 20 17 H4 A2 2 0 0 1 2 15 V5 A2 2 0 0 1 4 3 M8 21 H16 M12 17 V21",
        "smartphone": "M7 2 H17 A2 2 0 0 1 19 4 V20 A2 2 0 0 1 17 22 H7 A2 2 0 0 1 5 20 V4 A2 2 0 0 1 7 2 M11 18 H13",
        "gear":       "M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z M9 12 a3 3 0 1 0 6 0 a3 3 0 1 0 -6 0",
        "bell":       "M18 8 A6 6 0 0 0 6 8 C6 15 3 17 3 17 H21 C21 17 18 15 18 8 M10.3 21 A1.94 1.94 0 0 0 13.7 21",
        "mic":        "M12 2 A3 3 0 0 1 15 5 V11 A3 3 0 0 1 9 11 V5 A3 3 0 0 1 12 2 M5 10 V11 A7 7 0 0 0 19 11 V10 M12 18 V22",
        "zap":        "M13 2 L3 14 H12 L11 22 L21 10 H12 Z",
        "spark":      "M12 3 V6 M12 18 V21 M5.6 5.6 L7.7 7.7 M16.3 16.3 L18.4 18.4 M3 12 H6 M18 12 H21 M5.6 18.4 L7.7 16.3 M16.3 7.7 L18.4 5.6",
        "check":      "M20 6 L9 17 L4 12",
        "x":          "M18 6 L6 18 M6 6 L18 18",
        "download":   "M21 15 V19 A2 2 0 0 1 19 21 H5 A2 2 0 0 1 3 19 V15 M7 10 L12 15 L17 10 M12 15 V3",
        "box":        "M21 8 L12 3 L3 8 V16 L12 21 L21 16 Z M3 8 L12 13 L21 8 M12 13 V21",
        "file":       "M15 2 H6 A2 2 0 0 0 4 4 V20 A2 2 0 0 0 6 22 H18 A2 2 0 0 0 20 20 V7 Z M14 2 V7 H20",
        "chevron":    "M9 18 L15 12 L9 6",
        "message":    "M7.9 20 A9 9 0 1 0 4 16.1 L2 22 Z",
        "list":       "M8 6 H21 M8 12 H21 M8 18 H21 M3 6 H3.01 M3 12 H3.01 M3 18 H3.01",
        "paperclip":  "M21.44 11.05 L12.25 20.24 A6 6 0 0 1 3.76 11.75 L12.33 3.18 A4 4 0 1 1 18 8.84 L9.41 17.41 A2 2 0 0 1 6.58 14.58 L15.07 6.1",
        "globe":      "M12 2 A10 10 0 1 0 12 22 A10 10 0 1 0 12 2 M2 12 H22 M12 2 A15 15 0 0 1 12 22 A15 15 0 0 1 12 2",
        "video":      "M16 13 L21.22 16.48 A0.5 0.5 0 0 0 22 16.07 V7.93 A0.5 0.5 0 0 0 21.25 7.5 L16 10.5 M4 6 H14 A2 2 0 0 1 16 8 V16 A2 2 0 0 1 14 18 H4 A2 2 0 0 1 2 16 V8 A2 2 0 0 1 4 6",
        "bell-off":   "M8.7 3 A6 6 0 0 1 18 8 A21.3 21.3 0 0 0 18.6 13 M17 17 H3 C3 17 6 15 6 8 A4.67 4.67 0 0 1 6.3 6.3 M10.3 21 A1.94 1.94 0 0 0 13.7 21 M2 2 L22 22",
        "heart":      "M19 14 C20.49 12.54 22 10.79 22 8.5 A5.5 5.5 0 0 0 16.5 3 C14.74 3 13.5 3.5 12 5 C10.5 3.5 9.26 3 7.5 3 A5.5 5.5 0 0 0 2 8.5 C2 10.79 3.51 12.54 5 14 L12 21 Z",
        "eye":        "M2 12 C4.5 7.3 8 5 12 5 C16 5 19.5 7.3 22 12 C19.5 16.7 16 19 12 19 C8 19 4.5 16.7 2 12 Z M9 12 a3 3 0 1 0 6 0 a3 3 0 1 0 -6 0",
        "pencil":     "M17 3 A2.85 2.83 0 1 1 21 7 L7.5 20.5 L2 22 L3.5 16.5 Z M15 5 L19 9",
        "user":       "M19 21 V19 A4 4 0 0 0 15 15 H9 A4 4 0 0 0 5 19 V21 M8 7 a4 4 0 1 0 8 0 a4 4 0 1 0 -8 0",
        "users":      "M16 21 V19 A4 4 0 0 0 12 15 H6 A4 4 0 0 0 2 19 V21 M5 7 a4 4 0 1 0 8 0 a4 4 0 1 0 -8 0 M22 21 V19 A4 4 0 0 0 19 15.13 M16 3.13 A4 4 0 0 1 16 10.88",
        "mail":       "M4 4 H20 A2 2 0 0 1 22 6 V18 A2 2 0 0 1 20 20 H4 A2 2 0 0 1 2 18 V6 A2 2 0 0 1 4 4 M22 7 L13.03 12.7 A1.94 1.94 0 0 1 10.97 12.7 L2 7",
        "pin":        "M12 17 V22 M9 10.76 A2 2 0 0 1 7.89 12.55 L6.11 13.45 A2 2 0 0 0 5 15.24 V16 A1 1 0 0 0 6 17 H18 A1 1 0 0 0 19 16 V15.24 A2 2 0 0 0 17.89 13.45 L16.11 12.55 A2 2 0 0 1 15 10.76 V7 H16 A2 2 0 0 0 16 3 H8 A2 2 0 0 0 8 7 H9 Z",
        "megaphone":  "M3 11 L21 6 V18 L3 14 Z M11.6 16.8 A3 3 0 1 1 5.8 15.2"
    })

    Shape {
        width: 24
        height: 24
        preferredRendererType: Shape.CurveRenderer
        transform: Scale { xScale: root.size / 24; yScale: root.size / 24 }

        ShapePath {
            strokeColor: root.color
            strokeWidth: root.strokeWidth
            fillColor: "transparent"
            capStyle: ShapePath.RoundCap
            joinStyle: ShapePath.RoundJoin
            PathSvg { path: root.icons[root.name] ?? "" }
        }
    }
}
