import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import CareerTuner

// 메인 셸 — Claude Code Desktop 문법.
// 사이드바 = 세션 리스트 · 중앙 = 선택 세션의 대화 스레드 · 하단 = 입력바 · 우측 = 접이식 폰 패널.
ApplicationWindow {
    id: win
    visible: true
    width: 1280
    height: 820
    title: "CareerTuner — 면접 준비 컨트롤 센터"
    color: Theme.bg

    // ── 앱 상태 ──
    property bool loggedIn: false
    property bool autoLoginPending: true
    property string view: "home"      // home | thread | report | devices | settings
    property bool phoneOpen: false

    function openSession(jobId, title, mode, caseId) {
        session.open(jobId, title, mode, caseId)
        win.view = "thread"
    }

    function showToast(title, body) { toasts.push(title, body) }

    Connections {
        target: auth
        function onLoggedIn(token) {
            win.loggedIn = true
            win.autoLoginPending = false
            jobModel.reload()
        }
        function onAutoLoginFailed() { win.autoLoginPending = false }
        function onLoggedOut() {
            win.loggedIn = false
            win.view = "home"
        }
    }

    Connections {
        target: jobModel
        function onSessionCreated(sessionId, caseId, modeLabel, title) {
            win.openSession(sessionId, title, modeLabel, caseId)
            session.generateQuestions()   // 새 세션은 질문부터 생성
            win.showToast("세션 생성됨", title + " · " + modeLabel + " — 질문을 생성합니다")
        }
        function onDispatched(sessionId) {
            win.showToast("폰으로 보냈습니다", "폰·웹 알림 벨에 곧 표시됩니다 (최대 30초)")
        }
    }

    Connections {
        target: session
        function onAnswerScored(score) { win.showToast("채점 완료 — " + score + "점", "피드백이 스레드에 추가되었습니다") }
        function onVoiceScored(score) { win.showToast("전달력 채점 — " + score + "점", "음성 전달력 평가가 반영되었습니다") }
        function onExported(path, what) { win.showToast(what + " 저장됨", path) }
        function onErrorOccurred(message) { win.showToast("오류", message) }
        function onSessionFinished() { win.showToast("세션 완료 🎉", "모든 질문에 답변했습니다 — 리포트를 확인하세요") }
    }

    Connections {
        target: notifications
        function onNotificationArrived(type, title, message, link, targetId) {
            win.showToast(title, message)
        }
    }

    Connections {
        target: autoprep
        function onFinished(message) {
            win.showToast("자동 준비 완료", message !== "" ? message : "요청한 작업이 끝났습니다")
            jobModel.reload()
        }
        function onErrorOccurred(message) { win.showToast("자동 준비 오류", message) }
    }

    NewJobDialog { id: newJobDialog }

    // ── 자동 로그인 스플래시 ──
    Rectangle {
        visible: win.autoLoginPending
        anchors.fill: parent
        color: Theme.bg
        z: 10
        ColumnLayout {
            anchors.centerIn: parent
            spacing: 14
            Rectangle {
                Layout.alignment: Qt.AlignHCenter
                width: 52; height: 52; radius: 14; color: Theme.accent
                Text { anchors.centerIn: parent; text: "C"; color: "white"; font.bold: true; font.pixelSize: 26 }
            }
            Text { text: "자동 로그인 중…"; color: Theme.muted; font.pixelSize: 13; Layout.alignment: Qt.AlignHCenter }
        }
    }

    // ── 로그인 게이트 ──
    LoginPage {
        visible: !win.loggedIn && !win.autoLoginPending
        anchors.fill: parent
    }

    // ── 본체 ──
    RowLayout {
        visible: win.loggedIn
        anchors.fill: parent
        spacing: 0

        // ══ 사이드바: 세션 리스트 ══
        Rectangle {
            Layout.preferredWidth: 256
            Layout.fillHeight: true
            color: Theme.surface
            border.color: Theme.border

            ColumnLayout {
                anchors.fill: parent
                anchors.margins: 10
                spacing: 8

                // 로고 + 새 면접 준비
                RowLayout {
                    Layout.margins: 4
                    spacing: 8
                    Rectangle {
                        width: 24; height: 24; radius: 7; color: Theme.accent
                        Text { anchors.centerIn: parent; text: "C"; color: "white"; font.bold: true; font.pixelSize: 12 }
                    }
                    Text { text: "CareerTuner"; color: Theme.text; font.bold: true; font.pixelSize: 14 }
                    Item { Layout.fillWidth: true }
                    // 알림 뱃지
                    Rectangle {
                        visible: notifications.unread > 0
                        width: badgeText.implicitWidth + 12; height: 18; radius: 9
                        color: Theme.accentSoft
                        Text {
                            id: badgeText
                            anchors.centerIn: parent
                            text: "🔔 " + notifications.unread
                            color: Theme.accent; font.pixelSize: 10; font.bold: true
                        }
                        MouseArea { anchors.fill: parent; onClicked: notifications.markAllRead() }
                    }
                }

                Rectangle {
                    Layout.fillWidth: true
                    height: 36; radius: 9
                    gradient: Gradient {
                        GradientStop { position: 0.0; color: Theme.accent2 }
                        GradientStop { position: 1.0; color: Theme.accent }
                    }
                    Text { anchors.centerIn: parent; text: "＋ 새 면접 준비"; color: "white"; font.bold: true; font.pixelSize: 13 }
                    MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: newJobDialog.open() }
                }

                // 홈(자동 준비) 진입
                Rectangle {
                    Layout.fillWidth: true
                    height: 34; radius: 8
                    color: win.view === "home" ? Theme.accentSoft : "transparent"
                    Text {
                        x: 12; anchors.verticalCenter: parent.verticalCenter
                        text: "⚡ AI 자동 준비"
                        color: win.view === "home" ? Theme.text : Theme.muted
                        font.pixelSize: 13
                    }
                    MouseArea { anchors.fill: parent; onClicked: win.view = "home" }
                }

                Text {
                    text: "세션"
                    color: Theme.muted; font.pixelSize: 10; font.bold: true
                    Layout.leftMargin: 6; Layout.topMargin: 4
                }

                // 세션 리스트
                ListView {
                    id: sessList
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    clip: true
                    spacing: 2
                    model: jobModel
                    delegate: Rectangle {
                        required property int index
                        required property var jobId
                        required property var caseId
                        required property string title
                        required property string mode
                        required property string status
                        width: ListView.view.width
                        height: 52
                        radius: 8
                        color: (win.view === "thread" || win.view === "report") && session.sessionId === jobId
                               ? Theme.accentSoft
                               : hoverArea.containsMouse ? Theme.hover : "transparent"

                        ColumnLayout {
                            anchors.fill: parent
                            anchors.leftMargin: 10; anchors.rightMargin: 10
                            anchors.topMargin: 8; anchors.bottomMargin: 8
                            spacing: 3
                            Text {
                                Layout.fillWidth: true
                                text: title
                                color: Theme.text; font.pixelSize: 12; font.bold: true
                                elide: Text.ElideRight
                            }
                            RowLayout {
                                spacing: 6
                                Rectangle {
                                    width: 7; height: 7; radius: 3.5
                                    color: status === "DONE" ? Theme.good : Theme.warn
                                }
                                Text {
                                    text: mode + (status === "DONE" ? " · 완료" : " · 진행 중")
                                    color: Theme.muted; font.pixelSize: 11
                                }
                            }
                        }
                        MouseArea {
                            id: hoverArea
                            anchors.fill: parent
                            hoverEnabled: true
                            cursorShape: Qt.PointingHandCursor
                            onClicked: win.openSession(jobId, title, mode, caseId)
                        }
                    }
                }

                // 하단: 사용자 + 아이콘
                Rectangle { Layout.fillWidth: true; height: 1; color: Theme.border }
                RowLayout {
                    Layout.margins: 4
                    spacing: 9
                    Rectangle {
                        width: 30; height: 30; radius: 15; color: Theme.raised
                        border.color: Theme.border
                        Text {
                            anchors.centerIn: parent
                            text: auth.userName.length > 0 ? auth.userName.charAt(0) : "?"
                            color: Theme.text; font.pixelSize: 12
                        }
                    }
                    ColumnLayout {
                        spacing: 0
                        Text { text: auth.userName; color: Theme.text; font.pixelSize: 12; font.bold: true }
                        Text {
                            text: (auth.userPlan !== "" ? auth.userPlan + " 플랜" : "")
                                  + (appSettings.autoLogin ? " · 자동 로그인" : "")
                            color: Theme.muted; font.pixelSize: 10
                        }
                    }
                }
                RowLayout {
                    Layout.fillWidth: true
                    spacing: 4
                    Repeater {
                        model: [
                            { icon: "🖥️", key: "devices",  tip: "연결된 기기" },
                            { icon: "📱", key: "phone",    tip: "폰 연동 패널" },
                            { icon: "⚙️", key: "settings", tip: "설정" }
                        ]
                        delegate: Rectangle {
                            required property var modelData
                            Layout.fillWidth: true
                            height: 32; radius: 8
                            color: (modelData.key === "phone" && win.phoneOpen)
                                   || win.view === modelData.key ? Theme.accentSoft : "transparent"
                            Text { anchors.centerIn: parent; text: modelData.icon; font.pixelSize: 14 }
                            MouseArea {
                                anchors.fill: parent
                                cursorShape: Qt.PointingHandCursor
                                onClicked: {
                                    if (modelData.key === "phone") win.phoneOpen = !win.phoneOpen
                                    else win.view = modelData.key
                                }
                            }
                            ToolTip.visible: iconHover.containsMouse
                            ToolTip.text: modelData.tip
                            MouseArea { id: iconHover; anchors.fill: parent; hoverEnabled: true; acceptedButtons: Qt.NoButton }
                        }
                    }
                }
            }
        }

        // ══ 중앙 ══
        ColumnLayout {
            Layout.fillWidth: true
            Layout.fillHeight: true
            spacing: 0

            // 상단바
            Rectangle {
                Layout.fillWidth: true
                height: 52
                color: "transparent"
                border.color: Theme.border
                visible: win.view !== "home"

                RowLayout {
                    anchors.fill: parent
                    anchors.leftMargin: 18; anchors.rightMargin: 18
                    spacing: 10

                    Text {
                        text: win.view === "devices" ? "연결된 기기"
                            : win.view === "settings" ? "설정"
                            : session.title
                        color: Theme.text; font.pixelSize: 14; font.bold: true
                        elide: Text.ElideRight
                        Layout.maximumWidth: 340
                    }
                    // 상태 칩
                    Rectangle {
                        visible: win.view === "thread" || win.view === "report"
                        height: 22; radius: 11
                        width: statusChip.implicitWidth + 18
                        color: Theme.raised; border.color: Theme.border
                        Text {
                            id: statusChip
                            anchors.centerIn: parent
                            text: session.progress.finished === true
                                  ? "● 완료"
                                  : "● " + (session.progress.answered || 0) + "/" + (session.progress.total || 0) + " 진행 중"
                            color: session.progress.finished === true ? Theme.good : Theme.warn
                            font.pixelSize: 11
                        }
                    }
                    Rectangle {
                        visible: win.view === "thread" || win.view === "report"
                        height: 22; radius: 11
                        width: modeChip.implicitWidth + 18
                        color: Theme.raised; border.color: Theme.border
                        Text { id: modeChip; anchors.centerIn: parent; text: session.mode; color: Theme.muted; font.pixelSize: 11 }
                    }
                    Item { Layout.fillWidth: true }

                    // 액션들 (세션 화면에서만)
                    Row {
                        visible: win.view === "thread" || win.view === "report"
                        spacing: 8
                        Rectangle {
                            width: dispatchLbl.implicitWidth + 22; height: 30; radius: 8
                            color: Theme.raised; border.color: Theme.border
                            Text { id: dispatchLbl; anchors.centerIn: parent; text: "📲 폰으로 보내기"; color: Theme.text; font.pixelSize: 12 }
                            MouseArea {
                                anchors.fill: parent; cursorShape: Qt.PointingHandCursor
                                onClicked: { jobModel.dispatchToPhone(session.sessionId); win.phoneOpen = true }
                            }
                        }
                        Rectangle {
                            width: saveLbl.implicitWidth + 22; height: 30; radius: 8
                            color: Theme.raised; border.color: Theme.border
                            Text { id: saveLbl; anchors.centerIn: parent; text: "💾 자료 저장"; color: Theme.text; font.pixelSize: 12 }
                            MouseArea {
                                anchors.fill: parent; cursorShape: Qt.PointingHandCursor
                                onClicked: session.exportAll()
                            }
                        }
                        Rectangle {
                            visible: win.view === "thread"
                            width: repLbl.implicitWidth + 22; height: 30; radius: 8
                            gradient: Gradient {
                                GradientStop { position: 0.0; color: Theme.accent2 }
                                GradientStop { position: 1.0; color: Theme.accent }
                            }
                            Text { id: repLbl; anchors.centerIn: parent; text: "리포트"; color: "white"; font.pixelSize: 12; font.bold: true }
                            MouseArea {
                                anchors.fill: parent; cursorShape: Qt.PointingHandCursor
                                onClicked: { session.loadReport(); win.view = "report" }
                            }
                        }
                        Rectangle {
                            visible: win.view === "report"
                            width: backLbl.implicitWidth + 22; height: 30; radius: 8
                            color: Theme.raised; border.color: Theme.border
                            Text { id: backLbl; anchors.centerIn: parent; text: "← 스레드"; color: Theme.text; font.pixelSize: 12 }
                            MouseArea {
                                anchors.fill: parent; cursorShape: Qt.PointingHandCursor
                                onClicked: win.view = "thread"
                            }
                        }
                    }
                }
            }

            // 화면 스택
            StackLayout {
                Layout.fillWidth: true
                Layout.fillHeight: true
                currentIndex: win.view === "home" ? 0
                            : win.view === "thread" ? 1
                            : win.view === "report" ? 2
                            : win.view === "devices" ? 3 : 4

                HomeView { id: homeView }                            // 0
                SessionThread {}                                     // 1
                ReportView {}                                        // 2
                DevicesPage {}                                       // 3
                SettingsPage {}                                      // 4
            }

            // 하단 입력바 (홈=인테이크 · 스레드=답변)
            InputBar {
                Layout.fillWidth: true
                visible: win.view === "home" || win.view === "thread"
                mode: win.view === "home" ? "intake" : "answer"
                onSubmitted: (text) => {
                    if (win.view === "home") homeView.startIntake(text)
                    else session.submitAnswer(text)
                }
            }
        }

        // ══ 우측 폰 패널 (접이식) ══
        PhonePanel {
            Layout.fillHeight: true
            Layout.preferredWidth: win.phoneOpen ? 296 : 0
            visible: Layout.preferredWidth > 0
            Behavior on Layout.preferredWidth { NumberAnimation { duration: 200; easing.type: Easing.OutCubic } }
            onCloseRequested: win.phoneOpen = false
        }
    }

    // ── 토스트 ──
    Column {
        id: toasts
        anchors.right: parent.right
        anchors.bottom: parent.bottom
        anchors.margins: 22
        spacing: 8
        z: 100

        function push(title, body) {
            toastComp.createObject(toasts, { title: title, body: body })
        }

        Component {
            id: toastComp
            Rectangle {
                id: toastItem
                property string title: ""
                property string body: ""
                width: 320
                height: tcol.implicitHeight + 22
                radius: 10
                color: Theme.surface
                border.color: Theme.border
                Rectangle { width: 3; height: parent.height - 16; y: 8; x: 0; radius: 2; color: Theme.accent }
                ColumnLayout {
                    id: tcol
                    x: 14; y: 11; width: parent.width - 28
                    spacing: 3
                    Text { text: toastItem.title; color: Theme.text; font.pixelSize: 12; font.bold: true; Layout.fillWidth: true; elide: Text.ElideRight }
                    Text { text: toastItem.body; color: Theme.muted; font.pixelSize: 11; Layout.fillWidth: true; wrapMode: Text.WordWrap; maximumLineCount: 3; elide: Text.ElideRight }
                }
                Timer { interval: 4500; running: true; onTriggered: toastItem.destroy() }
                opacity: 0
                Component.onCompleted: opacity = 1
                Behavior on opacity { NumberAnimation { duration: 200 } }
            }
        }
    }
}
