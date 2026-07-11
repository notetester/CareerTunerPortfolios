import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import QtMultimedia
import CareerTuner

// 하단 입력바 — CC Desktop 의 프롬프트 입력창 포지션.
// mode: "answer"(스레드 답변) | "intake"(홈 autoprep 한 줄 요청)
Item {
    id: root
    property string mode: "answer"
    signal submitted(string text)

    // 영상 답변 상태 — 패널 열림 / 녹화 완료된 임시 mp4 경로
    property bool videoPanelOpen: false
    property string recordedVideoPath: ""

    implicitHeight: bar.implicitHeight + 26

    function fill(text) { input.text = text; input.forceActiveFocus() }
    function clear() { input.text = "" }

    function closeVideoPanel() {
        cameraRecorder.stopPreview()
        if (root.recordedVideoPath !== "")
            cameraRecorder.discard(root.recordedVideoPath)
        root.recordedVideoPath = ""
        consentBox.checked = false
        root.videoPanelOpen = false
    }

    onVideoPanelOpenChanged: {
        if (videoPanelOpen) {
            cameraRecorder.videoSink = videoOut.videoSink
            cameraRecorder.startPreview()
        }
    }

    Connections {
        target: session
        function onTranscribed(text) {
            if (root.mode === "answer") { input.text = text; input.forceActiveFocus() }
        }
        function onAnswerSubmissionStarted() {
            if (root.mode === "answer") input.text = ""
        }
        function onAnswerSubmissionFailed(text) {
            if (root.mode === "answer" && input.text.trim() === "") {
                input.text = text
                input.forceActiveFocus()
            }
        }
        function onVideoAnswerSubmitted() { root.closeVideoPanel() }
    }
    Connections {
        target: recorder
        function onRecorded(filePath) { session.transcribeAudio(filePath) }
        function onErrorOccurred(message) { win.showToast("녹음 오류", message) }
    }
    Connections {
        target: cameraRecorder
        function onRecorded(filePath) {
            if (root.recordedVideoPath !== "" && root.recordedVideoPath !== filePath)
                cameraRecorder.discard(root.recordedVideoPath)
            root.recordedVideoPath = filePath
        }
        function onErrorOccurred(message) { win.showToast("카메라 오류", message) }
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

            // ── 영상 답변 패널: 카메라 프리뷰 + 녹화 + 동의 전송 ──
            ColumnLayout {
                visible: root.videoPanelOpen
                Layout.fillWidth: true
                spacing: 8

                RowLayout {
                    Layout.fillWidth: true
                    spacing: 8
                    Row {
                        spacing: 6
                        Icon { name: "video"; size: 13; color: Theme.text; anchors.verticalCenter: parent.verticalCenter }
                        Text { text: "영상 답변"; color: Theme.text; font.pixelSize: 12; font.bold: true; anchors.verticalCenter: parent.verticalCenter }
                    }
                    Text {
                        text: root.recordedVideoPath !== ""
                              ? "녹화 완료 — 원본 저장·분석 동의를 확인하세요"
                              : (cameraRecorder.recording ? "녹화 중" : "최대 3분 · 제출 원본은 답변 기록에 저장됩니다")
                        color: Theme.muted; font.pixelSize: 11
                    }
                    Item { Layout.fillWidth: true }
                    Rectangle {
                        width: 22; height: 22; radius: 6
                        color: vClose.containsMouse ? Theme.hover : "transparent"
                        Text { anchors.centerIn: parent; text: "✕"; color: Theme.muted; font.pixelSize: 11 }
                        MouseArea {
                            id: vClose
                            anchors.fill: parent; hoverEnabled: true
                            cursorShape: Qt.PointingHandCursor
                            onClicked: root.closeVideoPanel()
                        }
                    }
                }

                // 프리뷰
                Rectangle {
                    Layout.fillWidth: true
                    Layout.preferredHeight: 210
                    radius: Theme.radius
                    color: "black"
                    border.color: cameraRecorder.recording ? Theme.danger : Theme.border
                    clip: true

                    VideoOutput {
                        id: videoOut
                        anchors.fill: parent
                        fillMode: VideoOutput.PreserveAspectFit
                    }
                    // 남은 시간 배지 (녹화 중)
                    Rectangle {
                        visible: cameraRecorder.recording
                        x: 10; y: 10
                        width: remainLbl.implicitWidth + 18; height: 24; radius: 12
                        color: Qt.rgba(0, 0, 0, 0.55)
                        Text {
                            id: remainLbl
                            anchors.centerIn: parent
                            text: {
                                const left = Math.max(0, cameraRecorder.maxSeconds - cameraRecorder.seconds)
                                return "● 남은 " + Math.floor(left / 60) + ":" + String(left % 60).padStart(2, "0")
                            }
                            color: Theme.danger; font.pixelSize: 11; font.bold: true
                        }
                    }
                }

                // 컨트롤: 녹화 시작/중지 ↔ (완료 후) 동의 + 전송/다시 녹화
                RowLayout {
                    Layout.fillWidth: true
                    spacing: 8

                    // 녹화 시작/중지
                    Rectangle {
                        visible: root.recordedVideoPath === ""
                        width: recLbl.implicitWidth + 22; height: 28; radius: 8
                        color: cameraRecorder.recording ? Theme.accentSoft : Theme.raised
                        border.color: cameraRecorder.recording ? Theme.accent : Theme.border
                        Text {
                            id: recLbl; anchors.centerIn: parent
                            text: cameraRecorder.recording ? "■ 녹화 중지" : "● 녹화 시작"
                            color: cameraRecorder.recording ? Theme.accent : Theme.text
                            font.pixelSize: 11
                        }
                        MouseArea {
                            anchors.fill: parent; cursorShape: Qt.PointingHandCursor
                            onClicked: cameraRecorder.recording ? cameraRecorder.stop() : cameraRecorder.start()
                        }
                    }

                    // 동의 체크 (녹화 완료 후)
                    RowLayout {
                        visible: root.recordedVideoPath !== ""
                        spacing: 7
                        Rectangle {
                            id: consentBox
                            property bool checked: false
                            width: 16; height: 16; radius: 4
                            color: checked ? Theme.accent : Theme.raised
                            border.color: checked ? Theme.accent : Theme.border
                            Text {
                                anchors.centerIn: parent; visible: consentBox.checked
                                text: "✓"; color: "white"; font.pixelSize: 10; font.bold: true
                            }
                            MouseArea {
                                anchors.fill: parent; cursorShape: Qt.PointingHandCursor
                                onClicked: consentBox.checked = !consentBox.checked
                            }
                        }
                        Text {
                            text: "원본 저장 및 분석에 동의합니다 (삭제 후 재분석 불가)"
                            color: Theme.muted; font.pixelSize: 11
                            MouseArea {
                                anchors.fill: parent; cursorShape: Qt.PointingHandCursor
                                onClicked: consentBox.checked = !consentBox.checked
                            }
                        }
                    }

                    Item { Layout.fillWidth: true }

                    // 다시 녹화
                    Rectangle {
                        visible: root.recordedVideoPath !== ""
                        width: retryLbl.implicitWidth + 20; height: 28; radius: 8
                        color: Theme.raised; border.color: Theme.border
                        Text { id: retryLbl; anchors.centerIn: parent; text: "다시 녹화"; color: Theme.text; font.pixelSize: 11 }
                        MouseArea {
                            anchors.fill: parent; cursorShape: Qt.PointingHandCursor
                            onClicked: {
                                cameraRecorder.discard(root.recordedVideoPath)
                                root.recordedVideoPath = ""
                                consentBox.checked = false
                            }
                        }
                    }

                    // 전송
                    Rectangle {
                        visible: root.recordedVideoPath !== ""
                        width: sendVideoLbl.implicitWidth + 22; height: 28; radius: 8
                        opacity: consentBox.checked && !session.scoring ? 1 : 0.4
                        gradient: Gradient {
                            GradientStop { position: 0.0; color: Theme.accent2 }
                            GradientStop { position: 1.0; color: Theme.accent }
                        }
                        Text { id: sendVideoLbl; anchors.centerIn: parent; text: "↑ 영상 전송"; color: "white"; font.pixelSize: 11; font.bold: true }
                        MouseArea {
                            anchors.fill: parent; cursorShape: Qt.PointingHandCursor
                            enabled: consentBox.checked && !session.scoring
                            onClicked: {
                                session.submitVideoAnswer(root.recordedVideoPath, consentBox.checked)
                            }
                        }
                    }
                }
            }

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
                    width: stopRow.implicitWidth + 20; height: 26; radius: 7
                    color: Theme.raised; border.color: Theme.border
                    Row {
                        id: stopRow; anchors.centerIn: parent; spacing: 6
                        Rectangle { width: 6; height: 6; radius: 1; color: Theme.text; anchors.verticalCenter: parent.verticalCenter }
                        Text { text: "정지"; color: Theme.text; font.pixelSize: 11; anchors.verticalCenter: parent.verticalCenter }
                    }
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
                        ? "답변을 입력하세요 — 마이크 버튼으로 음성 답변도 가능 (Enter 전송 · Shift+Enter 줄바꿈)"
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
                    width: micRow.implicitWidth + 18; height: 26; radius: 7
                    color: recorder.recording ? Theme.accentSoft : "transparent"
                    border.color: recorder.recording ? Theme.accent : Theme.border
                    Row {
                        id: micRow; anchors.centerIn: parent; spacing: 6
                        Icon {
                            name: "mic"; size: 12
                            color: recorder.recording ? Theme.accent : Theme.muted
                            anchors.verticalCenter: parent.verticalCenter
                        }
                        Text {
                            text: "음성 답변"
                            color: recorder.recording ? Theme.accent : Theme.muted
                            font.pixelSize: 11
                            anchors.verticalCenter: parent.verticalCenter
                        }
                    }
                    MouseArea {
                        anchors.fill: parent; cursorShape: Qt.PointingHandCursor
                        enabled: session.currentQid >= 0 && !session.scoring && !session.transcribing
                        onClicked: recorder.recording ? recorder.stop() : recorder.start()
                    }
                }

                // 영상 답변 (답변 모드) — 카메라 없으면 폰 이어하기 안내로 대체
                Rectangle {
                    visible: root.mode === "answer"
                    width: camRow.implicitWidth + 18; height: 26; radius: 7
                    color: root.videoPanelOpen ? Theme.accentSoft : "transparent"
                    border.color: root.videoPanelOpen ? Theme.accent : Theme.border
                    Row {
                        id: camRow; anchors.centerIn: parent; spacing: 6
                        Icon {
                            name: cameraRecorder.cameraAvailable ? "video" : "smartphone"
                            size: 12
                            color: root.videoPanelOpen ? Theme.accent : Theme.muted
                            anchors.verticalCenter: parent.verticalCenter
                        }
                        Text {
                            text: cameraRecorder.cameraAvailable ? "영상 답변" : "카메라 없음 — 폰으로"
                            color: root.videoPanelOpen ? Theme.accent : Theme.muted
                            font.pixelSize: 11
                            anchors.verticalCenter: parent.verticalCenter
                        }
                    }
                    MouseArea {
                        anchors.fill: parent; cursorShape: Qt.PointingHandCursor
                        enabled: session.currentQid >= 0 && !session.scoring
                        onClicked: {
                            if (!cameraRecorder.cameraAvailable) {
                                // 카메라 없는 PC — 세션을 폰으로 보내 영상 면접을 이어한다
                                jobModel.dispatchToPhone(session.sessionId)
                                win.showToast("폰으로 이어하기", "폰 알림(최대 30초 내)을 탭하면 이 세션이 폰에서 이어집니다")
                                return
                            }
                            if (root.videoPanelOpen) root.closeVideoPanel()
                            else root.videoPanelOpen = true
                        }
                    }
                }

                // 컨텍스트 칩
                Rectangle {
                    height: 22; radius: 11
                    width: ctxRow.implicitWidth + 18
                    color: root.mode === "intake" ? Theme.accentSoft : Theme.raised
                    border.color: root.mode === "intake" ? "transparent" : Theme.border
                    Row {
                        id: ctxRow; anchors.centerIn: parent; spacing: 5
                        Icon {
                            visible: root.mode === "intake"
                            name: "zap"; size: 10; color: Theme.accent
                            anchors.verticalCenter: parent.verticalCenter
                        }
                        Text {
                            id: ctxLbl
                            text: root.mode === "intake"
                                  ? "AI 자동 준비 (인테이크)"
                                  : (session.currentQid >= 0
                                      ? session.mode + " · 답변 대기"
                                      : "질문 없음")
                            color: root.mode === "intake" ? Theme.accent : Theme.muted
                            font.pixelSize: 10
                            anchors.verticalCenter: parent.verticalCenter
                        }
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
        if (root.mode === "intake") input.text = ""
    }
}
