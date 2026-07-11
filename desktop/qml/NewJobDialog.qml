import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import CareerTuner

// 새 면접 준비 위저드: 지원 건 선택(실데이터) → 모드 → 세션 생성.
// 생성 결과는 jobModel.sessionCreated 시그널로 Main 이 받아 스레드를 연다.
Dialog {
    id: dlg
    modal: true
    anchors.centerIn: parent
    width: 520
    padding: 0

    property int step: 0
    property int chosenCaseId: -1
    property var caseList: []

    function resetWizard() { step = 0; chosenCaseId = -1 }
    onAboutToShow: { resetWizard(); jobModel.loadCases() }

    Connections {
        target: jobModel
        function onCasesReady(cases) { dlg.caseList = cases }
    }

    background: Rectangle { color: Theme.surface; border.color: Theme.border; radius: 16 }

    contentItem: ColumnLayout {
        spacing: 0

        // 진행 바
        RowLayout {
            Layout.fillWidth: true
            Layout.margins: 22
            spacing: 6
            Repeater {
                model: 2
                delegate: Rectangle {
                    required property int index
                    Layout.fillWidth: true
                    height: 4; radius: 2
                    color: index <= dlg.step ? Theme.accent : Theme.border
                }
            }
        }

        StackLayout {
            currentIndex: dlg.step
            Layout.fillWidth: true
            Layout.leftMargin: 26
            Layout.rightMargin: 26
            Layout.bottomMargin: 24

            // 0: 지원 건(실데이터)
            ColumnLayout {
                spacing: 10
                Text { text: "어느 지원 건으로 준비할까요?"; color: Theme.text; font.pixelSize: 16; font.bold: true }
                Text {
                    visible: dlg.caseList.length === 0
                    text: "지원 건 불러오는 중…"; color: Theme.muted; font.pixelSize: 12
                }
                ListView {
                    Layout.fillWidth: true
                    Layout.preferredHeight: 240
                    clip: true
                    spacing: 6
                    model: dlg.caseList
                    delegate: Rectangle {
                        required property var modelData
                        width: ListView.view.width
                        height: 44; radius: 9
                        color: caseHover.containsMouse ? Theme.hover : Theme.raised
                        border.color: Theme.border
                        Text {
                            x: 14; anchors.verticalCenter: parent.verticalCenter
                            text: modelData.label; color: Theme.text; font.pixelSize: 13
                        }
                        MouseArea {
                            id: caseHover
                            anchors.fill: parent; hoverEnabled: true
                            cursorShape: Qt.PointingHandCursor
                            onClicked: { dlg.chosenCaseId = modelData.caseId; dlg.step = 1 }
                        }
                    }
                }
                RowLayout {
                    Layout.fillWidth: true
                    Item { Layout.fillWidth: true }
                    Rectangle {
                        width: cancelLbl.implicitWidth + 24; height: 32; radius: 8
                        color: Theme.raised; border.color: Theme.border
                        Text { id: cancelLbl; anchors.centerIn: parent; text: "취소"; color: Theme.muted; font.pixelSize: 12 }
                        MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: dlg.close() }
                    }
                }
            }

            // 1: 모드(실제 enum)
            ColumnLayout {
                spacing: 8
                Text { text: "면접 모드는 어떤 걸로 할까요?"; color: Theme.text; font.pixelSize: 16; font.bold: true }
                Repeater {
                    model: [
                        { label: "기본 면접", desc: "지원 직무 기준 일반 문항", value: "BASIC" },
                        { label: "직무 면접", desc: "기술·직무 역량 검증 중심", value: "JOB" },
                        { label: "인성 면접", desc: "가치관·협업·조직 적합성", value: "PERSONALITY" },
                        { label: "압박 면접", desc: "꼬리질문·반박 대응 훈련", value: "PRESSURE" },
                        { label: "자소서 기반 면접", desc: "이력서·자기소개서 경험 검증", value: "RESUME" },
                        { label: "기업 맞춤 면접", desc: "기업·산업 맥락과 지원동기 중심", value: "COMPANY" }
                    ]
                    delegate: Rectangle {
                        required property var modelData
                        Layout.fillWidth: true
                        height: 52; radius: 10
                        color: modeHover.containsMouse ? Theme.hover : Theme.raised
                        border.color: Theme.border
                        ColumnLayout {
                            x: 14; anchors.verticalCenter: parent.verticalCenter
                            spacing: 2
                            Text { text: modelData.label; color: Theme.text; font.pixelSize: 13; font.bold: true }
                            Text { text: modelData.desc; color: Theme.muted; font.pixelSize: 11 }
                        }
                        MouseArea {
                            id: modeHover
                            anchors.fill: parent; hoverEnabled: true
                            cursorShape: Qt.PointingHandCursor
                            onClicked: {
                                jobModel.createSession(dlg.chosenCaseId, modelData.value)
                                dlg.close()
                            }
                        }
                    }
                }
                RowLayout {
                    Layout.fillWidth: true
                    Rectangle {
                        width: backLbl2.implicitWidth + 24; height: 32; radius: 8
                        color: Theme.raised; border.color: Theme.border
                        Text { id: backLbl2; anchors.centerIn: parent; text: "← 이전"; color: Theme.muted; font.pixelSize: 12 }
                        MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: dlg.step = 0 }
                    }
                    Item { Layout.fillWidth: true }
                }
            }
        }
    }
}
