import QtQuick
import QtQuick.Controls
import QtQuick.Layouts

// 설정: 백엔드 서버 주소 + 동작 토글.
// backendUrl 은 추후 ApiClient.baseUrl 에 바인딩(실서버 연동 단계).
Item {
    id: settings
    property alias backendUrl: urlField.text

    ColumnLayout {
        anchors.fill: parent
        anchors.margins: 24
        spacing: 16

        ColumnLayout {
            spacing: 2
            Text { text: "설정"; color: "#e6edf3"; font.pixelSize: 22; font.bold: true }
            Text { text: "데스크탑 앱 동작 설정"; color: "#8b949e"; font.pixelSize: 13 }
        }

        // 백엔드 주소
        Rectangle {
            Layout.fillWidth: true
            implicitHeight: srvCol.implicitHeight + 32
            color: "#161b22"; border.color: "#30363d"; radius: 12
            ColumnLayout {
                id: srvCol
                x: 18; y: 16; width: parent.width - 36
                spacing: 8
                Text { text: "백엔드 서버 주소"; color: "#e6edf3"; font.pixelSize: 14; font.bold: true }
                RowLayout {
                    Layout.fillWidth: true
                    spacing: 10
                    TextField {
                        id: urlField
                        Layout.fillWidth: true
                        text: api.baseUrl()
                        color: "#e6edf3"
                        background: Rectangle { color: "#0d1117"; border.color: "#30363d"; radius: 8 }
                    }
                    Button { text: "적용"; onClicked: { api.setBaseUrl(urlField.text); srvMsg.text = "✓ 적용됨: " + urlField.text } }
                }
                Text { text: "원격(Tailscale) 백엔드면 100.x 주소로 변경"; color: "#8b949e"; font.pixelSize: 11 }
                Text { id: srvMsg; text: ""; color: "#2dd4bf"; font.pixelSize: 12; visible: text !== "" }
            }
        }

        // 동작 토글
        Rectangle {
            Layout.fillWidth: true
            implicitHeight: optCol.implicitHeight + 32
            color: "#161b22"; border.color: "#30363d"; radius: 12
            ColumnLayout {
                id: optCol
                x: 18; y: 16; width: parent.width - 36
                spacing: 10
                Repeater {
                    model: [
                        { t: "창을 닫아도 트레이에서 작업 계속 실행", on: true },
                        { t: "작업 완료 시 Windows 알림 표시", on: true },
                        { t: "폰으로 진행 상황 푸시 전송", on: true },
                        { t: "서버 연결 끊기면 자동 재구독 (Last-Event-ID)", on: true },
                        { t: "시작 시 자동 실행", on: false }
                    ]
                    delegate: RowLayout {
                        required property var modelData
                        Layout.fillWidth: true
                        spacing: 10
                        CheckBox { checked: modelData.on }
                        Text { text: modelData.t; color: "#e6edf3"; font.pixelSize: 13; Layout.fillWidth: true; verticalAlignment: Text.AlignVCenter }
                    }
                }
            }
        }

        Item { Layout.fillHeight: true }
    }
}
