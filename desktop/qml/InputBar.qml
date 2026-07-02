import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import CareerTuner

// 하단 입력바 — CC Desktop 의 프롬프트 입력창 포지션.
// mode: "answer"(스레드 답변) | "intake"(홈 autoprep 한 줄 요청)
Item {
    id: root
    property string mode: "answer"
    signal submitted(string text)

    implicitHeight: bar.implicitHeight + 26

    function fill(text) { input.text = text; input.forceActiveFocus() }
    function clear() { input.text = "" }

    Connections {
        target: session
        function onTranscribed(text) {
            if (root.mode === "answer") { input.text = text; input.forceActiveFocus() }
        }
    }
    Connections {
        target: recorder
        function onRecorded(filePath) { session.transcribeAudio(filePath) }
        function onErrorOccurred(message) { win.showToast("녹음 오류", message) }
    }

    Rectangle {
        id: bar
        anchors.horizontalCenter: parent.horizontalCenter
        anchors.bottom: parent.bottom
        anchors.bottomMargin: 14
        width: Math.min(parent.width - 48, 760)
        radius: Theme.radiusL
        color: Theme.surface
        border.color: input.activeFocus ? Theme.accent : Theme.border
        implicitHeight: barCol.implicitHeight + 20

        ColumnLayout {
            id: barCol
            x: 12; y: 10
            width: parent.width - 24
            spacing: 6

            // 녹음 중 상태줄
            RowLayout {
                visible: recorder.recording
                Layout.fillWidth: true
                spacing: 10
                Row {
                    spacing: 2
                    Repeater {
                        model: 5
                        delegate: Rectangle {
                            required property int index
                            width: 3; radius: 1.5
                            height: 6
                            anchors.verticalCenter: parent.verticalCenter
                            color: Theme.accent
                            SequentialAnimation on height {
                                running: recorder.recording
                                loops: Animation.Infinite
                                PauseAnimation { duration: index * 120 }
                                NumberAnimation { to: 18; duration: 380; easing.type: Easing.InOutSine }
                                NumberAnimation { to: 6;  duration: 380; easing.type: Easing.InOutSine }
                            }
                        }
                    }
                }
                Text {
                    text: Math.floor(recorder.seconds / 60) + ":" + String(recorder.seconds % 60).padStart(2, "0")
                    color: Theme.danger; font.pixelSize: 12; font.bold: true
                }
                Text { text: "녹음 중 — 답변을 말하세요"; color: Theme.muted; font.pixelSize: 11 }
                Item { Layout.fillWidth: true }
                Rectangle {
                    width: stopLbl.implicitWidth + 20; height: 26; radius: 7
                    color: Theme.raised; border.color: Theme.border
                    Text { id: stopLbl; anchors.centerIn: parent; text: "■ 정지"; color: Theme.text; font.pixelSize: 11 }
                    MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: recorder.stop() }
                }
            }

            // 전사 중
            RowLayout {
                visible: session.transcribing
                spacing: 8
                BusyIndicator { running: true; Layout.preferredWidth: 14; Layout.preferredHeight: 14 }
                Text { text: "전사 중…"; color: Theme.muted; font.pixelSize: 11 }
            }

            TextArea {
                id: input
                Layout.fillWidth: true
                placeholderText: root.mode === "intake"
                    ? "한 줄로 요청하세요 — 예) 이 공고로 압박 면접 5문항 만들어줘"
                    : (session.currentQid >= 0
                        ? "답변을 입력하세요 — 🎙 버튼으로 음성 답변도 가능 (Enter 전송 · Shift+Enter 줄바꿈)"
                        : "대기 중인 질문이 없습니다 — 꼬리질문을 받거나 리포트를 확인하세요")
                placeholderTextColor: Theme.muted
                color: Theme.text
                font.pixelSize: 13
                wrapMode: TextArea.Wrap
                background: null
                enabled: root.mode === "intake" || session.currentQid >= 0

                Keys.onReturnPressed: (event) => {
                    if (event.modifiers & Qt.ShiftModifier) {
                        event.accepted = false
                    } else {
                        event.accepted = true
                        root.trySend()
                    }
                }
            }

            RowLayout {
                Layout.fillWidth: true
                spacing: 8

                // 마이크 (답변 모드)
                Rectangle {
                    visible: root.mode === "answer"
                    width: micLbl.implicitWidth + 18; height: 26; radius: 7
                    color: recorder.recording ? Theme.accentSoft : "transparent"
                    border.color: recorder.recording ? Theme.accent : Theme.border
                    Text {
                        id: micLbl; anchors.centerIn: parent
                        text: "🎙 음성 답변"
                        color: recorder.recording ? Theme.accent : Theme.muted
                        font.pixelSize: 11
                    }
                    MouseArea {
                        anchors.fill: parent; cursorShape: Qt.PointingHandCursor
                        enabled: session.currentQid >= 0
                        onClicked: recorder.recording ? recorder.stop() : recorder.start()
                    }
                }

                // 컨텍스트 칩
                Rectangle {
                    height: 22; radius: 11
                    width: ctxLbl.implicitWidth + 18
                    color: root.mode === "intake" ? Theme.accentSoft : Theme.raised
                    border.color: root.mode === "intake" ? "transparent" : Theme.border
                    Text {
                        id: ctxLbl
                        anchors.centerIn: parent
                        text: root.mode === "intake"
                              ? "⚡ AI 자동 준비 (인테이크)"
                              : (session.currentQid >= 0
                                  ? session.mode + " · 답변 대기"
                                  : "질문 없음")
                        color: root.mode === "intake" ? Theme.accent : Theme.muted
                        font.pixelSize: 10
                    }
                }

                Item { Layout.fillWidth: true }

                // 전송
                Rectangle {
                    width: 32; height: 32; radius: 9
                    opacity: sendEnabled ? 1 : 0.35
                    property bool sendEnabled: input.text.trim() !== ""
                                               && !session.scoring
                                               && (root.mode === "intake" || session.currentQid >= 0)
                    gradient: Gradient {
                        GradientStop { position: 0.0; color: Theme.accent2 }
                        GradientStop { position: 1.0; color: Theme.accent }
                    }
                    Text { anchors.centerIn: parent; text: "↑"; color: "white"; font.pixelSize: 15; font.bold: true }
                    MouseArea {
                        anchors.fill: parent; cursorShape: Qt.PointingHandCursor
                        enabled: parent.sendEnabled
                        onClicked: root.trySend()
                    }
                }
            }
        }
    }

    function trySend() {
        const t = input.text.trim()
        if (t === "") return
        if (root.mode === "answer" && (session.currentQid < 0 || session.scoring)) return
        root.submitted(t)
        input.text = ""
    }
}
