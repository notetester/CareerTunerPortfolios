import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import CareerTuner

// 접이식 폰 연동 패널 — 디스패치 · 이어받기 안내.
Rectangle {
    id: root
    color: Theme.surface
    border.color: Theme.border
    clip: true

    signal closeRequested()

    property string lastDispatch: ""

    Connections {
        target: jobModel
        function onDispatched(sessionId) {
            root.lastDispatch = Qt.formatTime(new Date(), "hh:mm")
        }
    }

    ColumnLayout {
        width: 296
        anchors.top: parent.top
        anchors.margins: 14
        x: 14
        spacing: 12

        RowLayout {
            Layout.fillWidth: true
            Layout.topMargin: 14
            spacing: 8
            Text { text: "📱 폰 연동"; color: Theme.text; font.pixelSize: 13; font.bold: true }
            Item { Layout.fillWidth: true }
            Rectangle {
                width: 24; height: 24; radius: 6
                color: closeHover.containsMouse ? Theme.hover : "transparent"
                Text { anchors.centerIn: parent; text: "✕"; color: Theme.muted; font.pixelSize: 11 }
                MouseArea {
                    id: closeHover
                    anchors.fill: parent; hoverEnabled: true
                    cursorShape: Qt.PointingHandCursor
                    onClicked: root.closeRequested()
                }
            }
        }

        // 폰 프레임 (현재 세션 미러)
        Rectangle {
            Layout.alignment: Qt.AlignHCenter
            width: 210
            radius: 24
            color: Theme.bg
            border.color: Theme.border; border.width: 2
            implicitHeight: phoneCol.implicitHeight + 34

            ColumnLayout {
                id: phoneCol
                x: 12; y: 22; width: parent.width - 24
                spacing: 8

                Rectangle {
                    Layout.alignment: Qt.AlignHCenter
                    width: 52; height: 5; radius: 2.5; color: Theme.border
                    Layout.topMargin: -12
                }
                Text {
                    text: "CareerTuner · " + (session.sessionId > 0 ? session.mode : "대기")
                    color: Theme.muted; font.pixelSize: 10
                }
                Rectangle {
                    Layout.fillWidth: true
                    radius: 8
                    color: Theme.surface; border.color: Theme.border
                    implicitHeight: phoneQ.implicitHeight + 18
                    Text {
                        id: phoneQ
                        x: 9; y: 9; width: parent.width - 18
                        text: session.currentQuestionText !== ""
                              ? session.currentQuestionText
                              : (session.sessionId > 0 ? "모든 질문에 답변 완료" : "세션을 선택하세요")
                        color: Theme.text; font.pixelSize: 11
                        wrapMode: Text.WordWrap
                        maximumLineCount: 4; elide: Text.ElideRight
                        lineHeight: 1.4
                    }
                }
                Rectangle {
                    visible: root.lastDispatch !== ""
                    Layout.fillWidth: true
                    radius: 8
                    color: Theme.accentSoft
                    implicitHeight: notiTxt.implicitHeight + 16
                    Text {
                        id: notiTxt
                        x: 9; y: 8; width: parent.width - 18
                        text: "🔔 알림 발송됨 — 폰에서 탭하면 이 세션으로 이어집니다"
                        color: Theme.text; font.pixelSize: 10
                        wrapMode: Text.WordWrap
                    }
                }
            }
        }

        // 디스패치 버튼
        Rectangle {
            Layout.fillWidth: true
            height: 34; radius: 9
            opacity: session.sessionId > 0 ? 1 : 0.4
            gradient: Gradient {
                GradientStop { position: 0.0; color: Theme.accent2 }
                GradientStop { position: 1.0; color: Theme.accent }
            }
            Text { anchors.centerIn: parent; text: "📲 이 세션 폰으로 보내기"; color: "white"; font.pixelSize: 12; font.bold: true }
            MouseArea {
                anchors.fill: parent
                cursorShape: Qt.PointingHandCursor
                enabled: session.sessionId > 0
                onClicked: jobModel.dispatchToPhone(session.sessionId)
            }
        }

        Text {
            Layout.fillWidth: true
            text: (root.lastDispatch !== ""
                    ? "마지막 디스패치: " + root.lastDispatch + "\n"
                    : "")
                  + "폰·웹 앱 알림 벨에 최대 30초 내 표시됩니다.\n알림을 탭하면 세션으로 바로 이동하고,\n진행 위치(다음 질문)부터 이어집니다."
            color: Theme.muted; font.pixelSize: 11
            lineHeight: 1.5
            wrapMode: Text.WordWrap
        }
    }
}
