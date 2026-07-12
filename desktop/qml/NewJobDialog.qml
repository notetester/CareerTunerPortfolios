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
    property string createError: ""

    function resetWizard() {
        step = 0
        chosenCaseId = -1
        caseList = []
        createError = ""
    }
    onAboutToShow: { resetWizard(); jobModel.loadCases() }

    Connections {
        target: jobModel
        function onCasesReady(cases) { dlg.caseList = cases }
        function onSessionCreated() { if (dlg.visible) dlg.close() }
        function onSessionCreateFailed(message) { dlg.createError = message }
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
                    visible: jobModel.casesLoading
                    text: "지원 건 불러오는 중…"; color: Theme.muted; font.pixelSize: 12
                }
                Text {
                    visible: !jobModel.casesLoading && jobModel.casesError.length > 0
                    Layout.fillWidth: true
                    text: jobModel.casesError
                    color: Theme.danger; font.pixelSize: 12; wrapMode: Text.WordWrap
                }
                Text {
                    visible: !jobModel.casesLoading && jobModel.casesError.length === 0
                        && dlg.caseList.length === 0
                    Layout.fillWidth: true
                    text: "등록된 지원 건이 없습니다. 웹 또는 모바일에서 지원 건을 추가한 뒤 다시 시도해 주세요."
                    color: Theme.muted; font.pixelSize: 12; wrapMode: Text.WordWrap
                }
                Rectangle {
                    visible: !jobModel.casesLoading
                        && (jobModel.casesError.length > 0 || dlg.caseList.length === 0)
                    width: retryCasesLabel.implicitWidth + 22; height: 30; radius: 8
                    color: Theme.raised; border.color: activeFocus ? Theme.accent : Theme.border
                    activeFocusOnTab: visible
                    Accessible.role: Accessible.Button
                    Accessible.name: "지원 건 다시 불러오기"
                    function retryCases() { jobModel.loadCases() }
                    Keys.onPressed: (event) => {
                        if (event.key === Qt.Key_Return || event.key === Qt.Key_Enter || event.key === Qt.Key_Space) {
                            event.accepted = true
                            retryCases()
                        }
                    }
                    Text { id: retryCasesLabel; anchors.centerIn: parent; text: "다시 불러오기"; color: Theme.accentText; font.pixelSize: 12 }
                    MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: parent.retryCases() }
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
                        border.color: activeFocus ? Theme.accent : Theme.border
                        activeFocusOnTab: true
                        Accessible.role: Accessible.Button
                        Accessible.name: "지원 건 선택: " + modelData.label
                        function chooseCase() { dlg.chosenCaseId = modelData.caseId; dlg.step = 1 }
                        Keys.onPressed: (event) => {
                            if (event.key === Qt.Key_Return || event.key === Qt.Key_Enter || event.key === Qt.Key_Space) {
                                event.accepted = true
                                chooseCase()
                            }
                        }
                        Text {
                            x: 14; anchors.verticalCenter: parent.verticalCenter
                            text: modelData.label; color: Theme.text; font.pixelSize: 13
                        }
                        MouseArea {
                            id: caseHover
                            anchors.fill: parent; hoverEnabled: true
                            cursorShape: Qt.PointingHandCursor
                            onClicked: parent.chooseCase()
                        }
                    }
                }
                RowLayout {
                    Layout.fillWidth: true
                    Item { Layout.fillWidth: true }
                    Rectangle {
                        width: cancelLbl.implicitWidth + 24; height: 32; radius: 8
                        color: Theme.raised; border.color: activeFocus ? Theme.accent : Theme.border
                        activeFocusOnTab: true
                        Accessible.role: Accessible.Button
                        Accessible.name: "새 면접 준비 취소"
                        Keys.onPressed: (event) => {
                            if (event.key === Qt.Key_Return || event.key === Qt.Key_Enter || event.key === Qt.Key_Space) {
                                event.accepted = true
                                dlg.close()
                            }
                        }
                        Text { id: cancelLbl; anchors.centerIn: parent; text: "취소"; color: Theme.muted; font.pixelSize: 12 }
                        MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: dlg.close() }
                    }
                }
            }

            // 1: 모드(실제 enum)
            ColumnLayout {
                spacing: 8
                Text { text: "면접 모드는 어떤 걸로 할까요?"; color: Theme.text; font.pixelSize: 16; font.bold: true }
                Text {
                    visible: dlg.createError !== ""
                    Layout.fillWidth: true
                    text: dlg.createError
                    color: Theme.danger; font.pixelSize: 12; wrapMode: Text.WordWrap
                }
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
                        border.color: activeFocus ? Theme.accent : Theme.border
                        activeFocusOnTab: true
                        Accessible.role: Accessible.Button
                        Accessible.name: "면접 모드 선택: " + modelData.label
                        Accessible.description: modelData.desc
                        function chooseMode() {
                            if (jobModel.creatingSession) return
                            dlg.createError = ""
                            jobModel.createSession(dlg.chosenCaseId, modelData.value)
                        }
                        Keys.onPressed: (event) => {
                            if (event.key === Qt.Key_Return || event.key === Qt.Key_Enter || event.key === Qt.Key_Space) {
                                event.accepted = true
                                chooseMode()
                            }
                        }
                        ColumnLayout {
                            x: 14; anchors.verticalCenter: parent.verticalCenter
                            spacing: 2
                            Text { text: modelData.label; color: Theme.text; font.pixelSize: 13; font.bold: true }
                            Text { text: modelData.desc; color: Theme.muted; font.pixelSize: 11 }
                        }
                        MouseArea {
                            id: modeHover
                            anchors.fill: parent; hoverEnabled: true
                            enabled: !jobModel.creatingSession
                            cursorShape: enabled ? Qt.PointingHandCursor : Qt.ArrowCursor
                            onClicked: parent.chooseMode()
                        }
                    }
                }
                RowLayout {
                    Layout.fillWidth: true
                    Rectangle {
                        width: backLbl2.implicitWidth + 24; height: 32; radius: 8
                        color: Theme.raised; border.color: activeFocus ? Theme.accent : Theme.border
                        activeFocusOnTab: true
                        Accessible.role: Accessible.Button
                        Accessible.name: "지원 건 선택으로 이전"
                        Keys.onPressed: (event) => {
                            if (event.key === Qt.Key_Return || event.key === Qt.Key_Enter || event.key === Qt.Key_Space) {
                                event.accepted = true
                                dlg.step = 0
                            }
                        }
                        Text { id: backLbl2; anchors.centerIn: parent; text: "← 이전"; color: Theme.muted; font.pixelSize: 12 }
                        MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: dlg.step = 0 }
                    }
                    Item { Layout.fillWidth: true }
                    BusyIndicator {
                        visible: jobModel.creatingSession
                        running: visible
                        implicitWidth: 24; implicitHeight: 24
                    }
                    Text {
                        visible: jobModel.creatingSession
                        text: "세션 만드는 중…"; color: Theme.muted; font.pixelSize: 11
                    }
                }
            }
        }
    }
}
