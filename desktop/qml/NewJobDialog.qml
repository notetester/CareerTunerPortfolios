import QtQuick
import QtQuick.Controls
import QtQuick.Layouts

// 새 면접 준비 위저드: (메모) → 지원 건 선택(실데이터) → 모드 → 실제 세션 생성.
Dialog {
    id: dlg
    modal: true
    anchors.centerIn: parent
    width: 520
    padding: 0

    property int step: 0
    property int chosenCaseId: -1
    property var caseList: []
    signal jobCreated(int caseId, string mode)   // 실제 세션 생성 요청

    function resetWizard() { step = 0; chosenCaseId = -1; queryField.text = "" }
    onAboutToShow: { resetWizard(); jobModel.loadCases() }

    Connections {
        target: jobModel
        function onCasesReady(cases) { dlg.caseList = cases }
    }

    background: Rectangle { color: "#161b22"; border.color: "#30363d"; radius: 16 }

    contentItem: ColumnLayout {
        spacing: 0

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

            // 0: 안내
            ColumnLayout {
                spacing: 14
                Text { text: "새 면접 준비"; color: "#e6edf3"; font.pixelSize: 20; font.bold: true }
                Text {
                    text: "지원 건을 고르면 그 공고 기준으로 면접 세션을 실제로 만듭니다."
                    color: "#8b949e"; font.pixelSize: 13; wrapMode: Text.WordWrap; Layout.fillWidth: true
                }
                TextField {
                    id: queryField
                    Layout.fillWidth: true
                    placeholderText: "메모(선택)"
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

            // 1: 지원 건(실데이터)
            ColumnLayout {
                spacing: 10
                Text { text: "🤖  어느 지원 건으로 준비할까요?"; color: "#e6edf3"; font.pixelSize: 16; font.bold: true }
                Text { visible: dlg.caseList.length === 0; text: "지원 건 불러오는 중..."; color: "#8b949e"; font.pixelSize: 12 }
                ListView {
                    Layout.fillWidth: true
                    Layout.preferredHeight: 240
                    clip: true
                    spacing: 8
                    model: dlg.caseList
                    delegate: Button {
                        required property var modelData
                        width: ListView.view.width
                        text: modelData.label
                        onClicked: { dlg.chosenCaseId = modelData.caseId; dlg.step = 2 }
                    }
                }
            }

            // 2: 모드(실제 enum)
            ColumnLayout {
                spacing: 10
                Text { text: "🤖  면접 모드는 어떤 걸로 할까요?"; color: "#e6edf3"; font.pixelSize: 16; font.bold: true }
                Repeater {
                    model: [
                        { label: "기본 면접", value: "BASIC" },
                        { label: "직무 면접", value: "JOB" },
                        { label: "인성 면접", value: "PERSONALITY" },
                        { label: "압박 면접", value: "PRESSURE" }
                    ]
                    delegate: Button {
                        required property var modelData
                        Layout.fillWidth: true
                        text: modelData.label
                        onClicked: { dlg.jobCreated(dlg.chosenCaseId, modelData.value); dlg.close() }
                    }
                }
            }
        }
    }
}
