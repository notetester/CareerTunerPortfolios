import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import CareerTuner

// 홈 — autoprep 인테이크 + 실행 라이브 스텝 (CC 의 "한 줄 치면 작업이 돈다").
Item {
    id: root

    property string lastQuery: ""
    property var candidates: []
    property var modes: []
    property string askMessage: ""
    property int pendingCaseId: -1

    function startIntake(query) {
        root.lastQuery = query
        root.candidates = []
        root.modes = []
        root.askMessage = ""
        root.pendingCaseId = -1
        autoprep.intake(query)
    }

    Connections {
        target: autoprep
        function onIntakeReady(result) {
            if (result.ready === true) {
                root.askMessage = ""
                autoprep.run(root.lastQuery, root.pendingCaseId, "")
            } else {
                root.askMessage = result.message
                root.candidates = result.nextAsk === "CASE" ? result.candidates : []
                root.modes = result.nextAsk === "MODE" ? result.modes : []
            }
        }
    }

    Flickable {
        anchors.fill: parent
        contentHeight: content.implicitHeight + 60
        clip: true

        ColumnLayout {
            id: content
            width: Math.min(parent.width - 48, 680)
            anchors.horizontalCenter: parent.horizontalCenter
            spacing: 18

            Item { height: autoprep.running || autoprep.steps.length > 0 ? 24 : 110 }

            // 히어로 (실행 전)
            ColumnLayout {
                visible: !autoprep.running && autoprep.steps.length === 0
                Layout.fillWidth: true
                spacing: 10
                Text {
                    Layout.alignment: Qt.AlignHCenter
                    text: "오늘은 어떤 면접을 준비할까요?"
                    color: Theme.text; font.pixelSize: 25; font.bold: true
                }
                Text {
                    Layout.alignment: Qt.AlignHCenter
                    horizontalAlignment: Text.AlignHCenter
                    text: "한 줄로 요청하면 공고 분석부터 예상 질문 생성까지 알아서 준비합니다.\n아래 입력창에 바로 입력하거나, 왼쪽 세션에서 이어서 진행하세요."
                    color: Theme.muted; font.pixelSize: 13; lineHeight: 1.5
                }
                RowLayout {
                    Layout.alignment: Qt.AlignHCenter
                    Layout.topMargin: 12
                    spacing: 8
                    Repeater {
                        model: [
                            "이 공고로 압박 면접 5문항 만들어줘",
                            "지원건 분석부터 예상 질문까지 한 번에",
                            "인성 면접 준비해줘"
                        ]
                        delegate: Rectangle {
                            required property string modelData
                            height: 32; radius: 16
                            width: chipLbl.implicitWidth + 26
                            color: Theme.raised; border.color: chipHover.containsMouse ? Theme.accent : Theme.border
                            Text { id: chipLbl; anchors.centerIn: parent; text: modelData; color: Theme.text; font.pixelSize: 12 }
                            MouseArea {
                                id: chipHover
                                anchors.fill: parent
                                hoverEnabled: true
                                cursorShape: Qt.PointingHandCursor
                                onClicked: root.startIntake(modelData)
                            }
                        }
                    }
                }
            }

            // 되묻기 (CASE / MODE)
            Rectangle {
                visible: root.askMessage !== ""
                Layout.fillWidth: true
                radius: Theme.radius
                color: Theme.surface; border.color: Theme.border
                implicitHeight: askCol.implicitHeight + 28
                ColumnLayout {
                    id: askCol
                    x: 16; y: 14; width: parent.width - 32
                    spacing: 10
                    Text {
                        Layout.fillWidth: true
                        text: "🤖 " + root.askMessage
                        color: Theme.text; font.pixelSize: 13
                        wrapMode: Text.WordWrap
                    }
                    Flow {
                        Layout.fillWidth: true
                        spacing: 8
                        Repeater {
                            model: root.candidates
                            delegate: Rectangle {
                                required property var modelData
                                height: 30; radius: 8
                                width: candLbl.implicitWidth + 22
                                color: Theme.raised; border.color: Theme.border
                                Text { id: candLbl; anchors.centerIn: parent; text: modelData.label; color: Theme.text; font.pixelSize: 12 }
                                MouseArea {
                                    anchors.fill: parent; cursorShape: Qt.PointingHandCursor
                                    onClicked: {
                                        root.pendingCaseId = modelData.caseId
                                        root.candidates = []
                                        root.askMessage = ""
                                        autoprep.run(root.lastQuery, modelData.caseId, "")
                                    }
                                }
                            }
                        }
                        Repeater {
                            model: root.modes
                            delegate: Rectangle {
                                required property var modelData
                                height: 30; radius: 8
                                width: modeLbl2.implicitWidth + 22
                                color: Theme.raised; border.color: Theme.border
                                Text { id: modeLbl2; anchors.centerIn: parent; text: modelData.label; color: Theme.text; font.pixelSize: 12 }
                                MouseArea {
                                    anchors.fill: parent; cursorShape: Qt.PointingHandCursor
                                    onClicked: {
                                        root.modes = []
                                        root.askMessage = ""
                                        autoprep.run(root.lastQuery, root.pendingCaseId, modelData.code)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 실행 스텝 (라이브)
            Rectangle {
                visible: autoprep.steps.length > 0
                Layout.fillWidth: true
                radius: Theme.radius
                color: Theme.surface; border.color: Theme.border
                implicitHeight: runCol.implicitHeight + 8

                Column {
                    id: runCol
                    width: parent.width
                    padding: 0

                    RowLayout {
                        width: parent.width - 28
                        x: 14
                        height: 40
                        spacing: 10
                        Text {
                            text: autoprep.running ? "실행 중 — " + root.lastQuery : "실행 결과"
                            color: Theme.text; font.pixelSize: 13; font.bold: true
                            elide: Text.ElideRight
                            Layout.fillWidth: true
                        }
                        Rectangle {
                            visible: autoprep.running
                            width: cancelLbl.implicitWidth + 18; height: 24; radius: 7
                            color: Theme.raised; border.color: Theme.border
                            Text { id: cancelLbl; anchors.centerIn: parent; text: "중지"; color: Theme.muted; font.pixelSize: 11 }
                            MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: autoprep.cancel() }
                        }
                    }

                    Repeater {
                        model: autoprep.steps
                        delegate: Column {
                            required property var modelData
                            required property int index
                            width: runCol.width

                            Rectangle { width: parent.width; height: 1; color: Theme.border }
                            RowLayout {
                                width: parent.width - 28
                                x: 14
                                height: 38
                                spacing: 10
                                // 상태 아이콘
                                Item {
                                    width: 16; height: 16
                                    BusyIndicator {
                                        anchors.fill: parent
                                        running: modelData.status === "RUNNING"
                                        visible: modelData.status === "RUNNING"
                                    }
                                    Text {
                                        anchors.centerIn: parent
                                        visible: modelData.status !== "RUNNING"
                                        text: modelData.status === "DONE" ? "✓"
                                            : modelData.status === "FAILED" ? "✕"
                                            : modelData.status === "SKIPPED" ? "–" : "•"
                                        color: modelData.status === "DONE" ? Theme.good
                                             : modelData.status === "FAILED" ? Theme.danger : Theme.muted
                                        font.pixelSize: 12
                                    }
                                }
                                Text { text: modelData.label; color: Theme.text; font.pixelSize: 12; font.bold: true }
                                Text {
                                    Layout.fillWidth: true
                                    text: modelData.status === "RUNNING" && modelData.substep !== ""
                                          ? modelData.substep : modelData.summary
                                    color: Theme.muted; font.pixelSize: 11
                                    elide: Text.ElideRight
                                }
                                Text {
                                    visible: modelData.elapsedMs > 0
                                    text: (modelData.elapsedMs / 1000).toFixed(1) + "s"
                                    color: Theme.muted; font.pixelSize: 10
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
