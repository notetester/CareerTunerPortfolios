import QtQuick
import QtQuick.Controls
import QtQuick.Layouts

// 새 면접 준비 위저드: 자연어 입력 → 지원건(CASE) → 모드(MODE) → 생성.
// 프로토타입(desktop-app-prototype.html)의 되묻기 흐름을 QML로.
Dialog {
    id: dlg
    modal: true
    anchors.centerIn: parent
    width: 520
    padding: 0

    property int step: 0
    property string chosenCase: ""
    signal jobCreated(string company, string mode)

    function resetWizard() { step = 0; chosenCase = ""; queryField.text = "" }
    onAboutToShow: resetWizard()

    background: Rectangle { color: "#161b22"; border.color: "#30363d"; radius: 16 }

    contentItem: ColumnLayout {
        spacing: 0

        // 단계 표시 점
        RowLayout {
            Layout.fillWidth: true
            Layout.margins: 22
            spacing: 6
            Repeater {
                model: 3
                delegate: Rectangle {
                    required property int index
                    Layout.fillWidth: true
                    height: 4; radius: 2
                    color: index <= dlg.step ? "#7c5cff" : "#30363d"
                }
            }
        }

        StackLayout {
            currentIndex: dlg.step
            Layout.fillWidth: true
            Layout.leftMargin: 26
            Layout.rightMargin: 26
            Layout.bottomMargin: 24

            // 0: 자연어 입력
            ColumnLayout {
                spacing: 14
                Text { text: "새 면접 준비"; color: "#e6edf3"; font.pixelSize: 20; font.bold: true }
                Text {
                    text: "무엇을 준비할지 입력하세요. AI 오케스트레이터가 단계를 짭니다."
                    color: "#8b949e"; font.pixelSize: 13; wrapMode: Text.WordWrap; Layout.fillWidth: true
                }
                TextField {
                    id: queryField
                    Layout.fillWidth: true
                    placeholderText: "예: 삼성전자 SW 개발직군 면접 준비해줘"
                    color: "#e6edf3"
                    background: Rectangle { color: "#0d1117"; border.color: "#30363d"; radius: 10 }
                }
                RowLayout {
                    Layout.fillWidth: true
                    Item { Layout.fillWidth: true }
                    Button { text: "취소"; onClicked: dlg.close() }
                    Button { text: "다음 →"; onClicked: dlg.step = 1 }
                }
            }

            // 1: 지원 건(CASE) 되묻기
            ColumnLayout {
                spacing: 10
                Text { text: "🤖  어느 지원 건으로 준비할까요?"; color: "#e6edf3"; font.pixelSize: 16; font.bold: true }
                Repeater {
                    model: ["삼성전자 · SW 개발직군", "카카오 · 프론트엔드", "네이버 · 백엔드 인턴"]
                    delegate: Button {
                        required property string modelData
                        Layout.fillWidth: true
                        text: modelData
                        onClicked: { dlg.chosenCase = modelData; dlg.step = 2 }
                    }
                }
            }

            // 2: 면접 모드(MODE) 되묻기
            ColumnLayout {
                spacing: 10
                Text { text: "🤖  면접 모드는 어떤 걸로 할까요?"; color: "#e6edf3"; font.pixelSize: 16; font.bold: true }
                Repeater {
                    model: ["직무 면접", "인성 면접", "압박 면접"]
                    delegate: Button {
                        required property string modelData
                        Layout.fillWidth: true
                        text: modelData
                        onClicked: {
                            dlg.jobCreated(dlg.chosenCase, modelData)
                            dlg.close()
                        }
                    }
                }
            }
        }
    }
}
