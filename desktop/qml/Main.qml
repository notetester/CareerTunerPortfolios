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
    minimumWidth: phoneOpen ? 1120 : 960
    minimumHeight: 640
    title: "CareerTuner — 면접 준비 컨트롤 센터"
    color: Theme.bg

    // QSettings에 저장된 테마를 싱글턴 토큰에 바인딩해 모든 화면에 즉시 반영한다.
    Binding {
        target: Theme
        property: "darkMode"
        value: appSettings.darkTheme
    }

    // ── 앱 상태 ──
    property bool loggedIn: false
    property bool autoLoginPending: true
    property string view: "home"      // home | thread | report | collaboration | board | devices | settings
    property bool phoneOpen: false
    readonly property bool sessionAnswerDraftPending: sessionInputBar.hasDraftActivity
    readonly property bool sessionAnswerMediaPending: sessionInputBar.hasPendingMediaActivity

    function startQuestionRegeneration() {
        if (sessionAnswerMediaPending) {
            win.showToast("미제출 원본 정리 필요", "녹음·영상 작업을 취소하거나 제출한 뒤 질문을 다시 생성해 주세요")
            return
        }
        session.generateQuestions()
    }

    function openSession(jobId, title, mode, caseId) {
        const targetSessionId = Number(jobId)
        if (session.sessionId >= 0 && session.sessionId !== targetSessionId)
            sessionInputBar.cancelSessionMedia()
        session.open(targetSessionId, title, mode, caseId)
        win.view = "thread"
    }

    function showToast(title, body, type, link, targetType, targetId) {
        toasts.push(title, body, String(type || ""), String(link || ""),
                    String(targetType || ""), Number(targetId || 0))
    }

    function notificationSessionId(link, targetId) {
        const explicitId = Number(targetId || 0)
        if (isFinite(explicitId) && explicitId > 0)
            return Math.floor(explicitId)

        const url = String(link || "")
        const queryMatch = url.match(/[?&]session=(\d+)/)
        if (queryMatch)
            return Number(queryMatch[1])
        const mobileMatch = url.match(/^\/m\/session\/(\d+)/)
        return mobileMatch ? Number(mobileMatch[1]) : 0
    }

    function openInterviewNotification(type, link, targetId) {
        const sessionId = notificationSessionId(link, targetId)
        if (sessionId <= 0) {
            routeNotificationLink(link)
            return
        }

        const context = jobModel.sessionContext(sessionId)
        const hasContext = Number(context.id || 0) === sessionId
        const title = hasContext && String(context.title || "").length > 0
                    ? String(context.title) : "면접 세션 #" + sessionId
        const mode = hasContext && String(context.mode || "").length > 0
                   ? String(context.mode) : "이어하기"
        const caseId = hasContext ? Number(context.caseId || 0) : 0

        win.openSession(sessionId, title, mode, caseId)
        jobModel.markResumed(sessionId)
        if (String(type || "") === "INTERVIEW_REPORT_READY") {
            session.loadReport()
            win.view = "report"
        }
    }

    // 트레이·인앱 토스트·알림 센터의 단일 알림 활성화 진입점.
    function activateNotification(type, link, targetType, targetId) {
        const url = String(link || "")
        const target = String(targetType || "")
        if (target === "INTERVIEW_SESSION"
                || ((url.indexOf("/interview") === 0 || url.indexOf("/m/session/") === 0)
                    && notificationSessionId(url, targetId) > 0)) {
            openInterviewNotification(type, url, targetId)
            return
        }
        routeNotificationLink(url)
    }

    // 알림 link → 데스크톱 화면 라우팅.
    // 구조화된 대상 처리는 activateNotification 에서 끝낸 뒤 나머지 링크만 이곳으로 온다.
    // 데스크톱에 대응 화면이 없는 링크는 공개 웹 앱으로 이어서 고아 링크를 만들지 않는다.
    function routeNotificationLink(link) {
        const url = String(link || "")
        if (url.length === 0) return
        if (url.indexOf("/messenger") === 0 || url.indexOf("/collaboration") === 0) {
            win.view = "collaboration"
            collaboration.refresh()
            return
        }
        if (url === "/community" || url.indexOf("/community/posts/") === 0
                || /[?&]post=\d+/.test(url)) {
            // '/community/posts/{id}' (추천 글 알림 등) 이면 해당 글 상세까지 연다
            win.view = "board"
            const pm = url.match(/\/community\/posts\/(\d+)/)
            const qm = url.match(/[?&]post=(\d+)/)
            if (pm) community.openPost(Number(pm[1]))
            else if (qm) community.openPost(Number(qm[1]))
            return
        }
        if (url.indexOf("/planner") === 0) {
            plannerOverlayController.enabled = true
            plannerClient.refreshNow()
            return
        }
        if (url.indexOf("/settings") === 0) {
            win.view = "settings"
            return
        }

        // 웹 전용 화면(지원 건, 결제, 고객센터 등)과 세션 ID 없는 면접 링크.
        // 알림 링크는 서버가 생성한 내부 경로 또는 http(s) URL만 허용한다.
        if (url.indexOf("/") === 0) {
            Qt.openUrlExternally(appSettings.webAppUrl + url)
        } else if (/^https?:\/\//i.test(url)) {
            Qt.openUrlExternally(url)
        }
    }

    Connections {
        target: auth
        function onAboutToLogout() {
            sessionInputBar.cancelSessionMedia()
            newJobDialog.close()
            newJobDialog.resetWizard()
            win.loggedIn = false
            win.view = "home"
        }
        function onLoggedIn(token) {
            win.loggedIn = true
            win.autoLoginPending = false
            jobModel.reload()
        }
        function onAutoLoginFailed() { win.autoLoginPending = false }
        function onAuthenticationExpired(message) {
            win.autoLoginPending = false
            win.showToast("로그인 만료", message)
        }
        function onLoggedOut() {
            sessionInputBar.cancelSessionMedia()
            win.loggedIn = false
            win.view = "home"
        }
    }

    Connections {
        target: jobModel
        function onSessionCreated(sessionId, caseId, modeLabel, title) {
            if (!win.loggedIn) return
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
        function onAnswerMediaDeleted(kind) {
            win.showToast("원본 삭제됨", kind === "VIDEO" ? "영상 원본을 삭제했습니다" : "음성 원본을 삭제했습니다")
        }
        function onQuestionsRegenerated() {
            sessionInputBar.clear()
            sessionInputBar.cancelSessionMedia()
            win.showToast("질문 교체 완료", "선택한 모델로 미답변 질문을 다시 만들었습니다")
        }
        function onExported(path, what) { win.showToast(what + " 저장됨", path) }
        function onErrorOccurred(message) { win.showToast("오류", message) }
        function onSessionFinished() {
            win.showToast("세션 완료", "모든 질문에 답변했습니다 — 리포트 버튼을 누르면 이용 안내 후 생성합니다")
        }
    }

    Connections {
        target: notifications
        function onNotificationArrived(type, title, message, link, targetType, targetId,
                                       desktopToast, desktopTaskbar) {
            if (desktopToast)
                win.showToast(title, message, type, link, targetType, targetId)
            if (type === "ROOM_MESSAGE" || type === "ROOM_MENTION" || type === "ROOM_INVITE"
                || type === "FRIEND_REQUEST" || type === "FRIEND_ACCEPTED")
                collaboration.refresh()
        }
    }

    Connections {
        target: collaboration
        function onErrorOccurred(message) { win.showToast("협업 오류", message) }
        function onInfo(title, message) { win.showToast(title, message) }
        function onAttachmentDownloaded(path) { win.showToast("첨부 저장됨", path) }
    }

    Connections {
        target: community
        function onErrorOccurred(message) { win.showToast("게시판 오류", message) }
        function onInfo(title, message) { win.showToast(title, message) }
    }

    Connections {
        target: autoprep
        function onFinished(message) {
            win.showToast("자동 준비 완료", message !== "" ? message : "요청한 작업이 끝났습니다")
            jobModel.reload()
        }
        function onErrorOccurred(message) { win.showToast("자동 준비 오류", message) }
    }

    Connections {
        target: aiCharge
        function onNotice(message) { win.showToast("AI 이용 안내", message) }
        function onErrorOccurred(message) { win.showToast("AI 실행 불가", message) }
    }

    NewJobDialog { id: newJobDialog }

    Rectangle {
        visible: win.loggedIn && consentStatus.loaded && consentStatus.requiredConsentsMissing
        z: 90
        anchors.top: parent.top
        anchors.horizontalCenter: parent.horizontalCenter
        anchors.topMargin: 12
        width: Math.min(parent.width - 32, 720)
        height: consentBannerRow.implicitHeight + 20
        radius: 10
        color: Theme.surface; border.color: Theme.warn
        RowLayout {
            id: consentBannerRow
            x: 14; y: 10; width: parent.width - 28
            Text {
                Layout.fillWidth: true
                text: "필수 이용약관 또는 개인정보 동의가 없어 기능이 제한됩니다. 웹에서 동의를 복구해 주세요."
                color: Theme.text; font.pixelSize: 12; wrapMode: Text.WordWrap
            }
            Button {
                text: "동의 관리 ↗"
                Accessible.name: "웹에서 필수 동의 복구"
                onClicked: Qt.openUrlExternally(appSettings.webAppUrl + "/settings?tab=privacy")
            }
            Button {
                text: consentStatus.loading ? "확인 중…" : "다시 확인"
                enabled: !consentStatus.loading
                Accessible.name: "필수 동의 상태 다시 확인"
                onClicked: consentStatus.refresh()
            }
        }
    }

    // ── 앰비언트 라이트 (상단 인디고 워시 — Linear Modern 레이어드 배경) ──
    Canvas {
        anchors.fill: parent
        z: 0
        opacity: 0.8
        onPaint: {
            const ctx = getContext("2d")
            ctx.clearRect(0, 0, width, height)
            const g = ctx.createRadialGradient(width / 2, -80, 0, width / 2, -80, 420)
            g.addColorStop(0, "rgba(94,106,210,0.10)")
            g.addColorStop(1, "rgba(94,106,210,0)")
            ctx.fillStyle = g
            ctx.fillRect(0, 0, width, height)
        }
        onWidthChanged: requestPaint()
        onHeightChanged: requestPaint()
    }

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
                    // 안읽음 카운트 칩
                    Rectangle {
                        visible: notifications.unread > 0
                        width: badgeRow.implicitWidth + 16; height: 18; radius: 9
                        color: Theme.accentSoft
                        Row {
                            id: badgeRow
                            anchors.centerIn: parent
                            spacing: 4
                            Icon { name: "bell"; size: 10; color: Theme.accentText; anchors.verticalCenter: parent.verticalCenter }
                            Text {
                                text: notifications.unread > 99 ? "99+" : notifications.unread
                                color: Theme.accentText; font.pixelSize: 10; font.bold: true
                                anchors.verticalCenter: parent.verticalCenter
                            }
                        }
                    }
                    // 알림 벨 — 클릭 시 알림 센터 팝업 (빨간 점 = 안읽음 존재)
                    Rectangle {
                        id: bellButton
                        width: 26; height: 22; radius: 7
                        color: bellHover.containsMouse || notificationCenter.opened ? Theme.hover : "transparent"
                        border.color: activeFocus ? Theme.accent : "transparent"
                        activeFocusOnTab: true
                        Accessible.role: Accessible.Button
                        Accessible.name: notifications.unread > 0
                                         ? "알림 센터, 읽지 않은 알림 " + notifications.unread + "개"
                                         : "알림 센터"
                        function toggleNotificationCenter() {
                            notificationCenter.opened ? notificationCenter.close() : notificationCenter.open()
                        }
                        Keys.onPressed: (event) => {
                            if (event.key === Qt.Key_Return || event.key === Qt.Key_Enter || event.key === Qt.Key_Space) {
                                event.accepted = true
                                toggleNotificationCenter()
                            }
                        }
                        Icon { anchors.centerIn: parent; name: "bell"; size: 12; color: Theme.text }
                        Rectangle {
                            visible: notifications.unread > 0
                            width: 7; height: 7; radius: 3.5
                            x: parent.width - 8; y: 1
                            color: Theme.danger
                        }
                        MouseArea {
                            id: bellHover
                            anchors.fill: parent
                            hoverEnabled: true
                            cursorShape: Qt.PointingHandCursor
                            onClicked: parent.toggleNotificationCenter()
                        }
                        NotificationCenter {
                            id: notificationCenter
                            x: -100
                            y: parent.height + 8
                            onNotificationActivated: (type, link, targetType, targetId) =>
                                win.activateNotification(type, link, targetType, targetId)
                        }
                    }
                }

                Rectangle {
                    Layout.fillWidth: true
                    height: 36; radius: 9
                    activeFocusOnTab: true
                    Accessible.role: Accessible.Button
                    Accessible.name: "새 면접 준비"
                    Keys.onPressed: (event) => {
                        if (event.key === Qt.Key_Return || event.key === Qt.Key_Enter || event.key === Qt.Key_Space) {
                            event.accepted = true
                            newJobDialog.open()
                        }
                    }
                    border.color: activeFocus ? Theme.accentText : "transparent"
                    border.width: activeFocus ? 2 : 0
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
                    border.color: activeFocus ? Theme.accent : "transparent"
                    activeFocusOnTab: true
                    Accessible.role: Accessible.Button
                    Accessible.name: "AI 자동 준비 홈"
                    Keys.onPressed: (event) => {
                        if (event.key === Qt.Key_Return || event.key === Qt.Key_Enter || event.key === Qt.Key_Space) {
                            event.accepted = true
                            win.view = "home"
                        }
                    }
                    Row {
                        x: 12; anchors.verticalCenter: parent.verticalCenter
                        spacing: 7
                        Icon {
                            name: "zap"; size: 13
                            color: win.view === "home" ? Theme.accentText : Theme.muted
                            anchors.verticalCenter: parent.verticalCenter
                        }
                        Text {
                            text: "AI 자동 준비"
                            color: win.view === "home" ? Theme.text : Theme.muted
                            font.pixelSize: 13
                            anchors.verticalCenter: parent.verticalCenter
                        }
                    }
                    MouseArea { anchors.fill: parent; onClicked: win.view = "home" }
                }

                Text {
                    text: "SESSIONS"
                    color: Theme.muted; font.pixelSize: 9; font.bold: true
                    font.family: Theme.monoFont; font.letterSpacing: 1.6
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
                        required property int progress
                        readonly property bool currentSessionBusy: session.sessionId === jobId
                            && (session.loading || session.scoring || session.transcribing || session.reportLoading
                                || session.followUpPendingQuestionIds.length > 0
                                || session.modelAnswerPendingQuestionIds.length > 0)
                        width: ListView.view.width
                        height: 52
                        radius: 8
                        color: (win.view === "thread" || win.view === "report") && session.sessionId === jobId
                               ? Theme.accentSoft
                               : hoverArea.containsMouse ? Theme.hover : "transparent"
                        border.color: activeFocus ? Theme.accent : "transparent"
                        activeFocusOnTab: true
                        Accessible.role: Accessible.Button
                        Accessible.name: "면접 세션 열기: " + title + ", " + mode
                        Accessible.description: currentSessionBusy ? "현재 세션 작업 처리 중" : ""
                        opacity: currentSessionBusy ? 0.65 : 1
                        function openThisSession() {
                            if (!currentSessionBusy) win.openSession(jobId, title, mode, caseId)
                        }
                        Keys.onPressed: (event) => {
                            if (event.key === Qt.Key_Return || event.key === Qt.Key_Enter || event.key === Qt.Key_Space) {
                                event.accepted = true
                                openThisSession()
                            }
                        }

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
                                Layout.fillWidth: true
                                spacing: 6
                                Rectangle {
                                    width: 7; height: 7; radius: 3.5
                                    color: status === "DONE" ? Theme.good
                                         : status === "REPORTED" ? Theme.info : Theme.warn
                                }
                                Text {
                                    Layout.fillWidth: true
                                    text: status === "DONE" ? mode + " · 완료"
                                        : status === "REPORTED" ? mode + " · 리포트 · " + progress + "% 답변"
                                        : mode + (progress > 0 ? " · " + progress + "% 진행 중" : " · 진행 중")
                                    color: Theme.muted; font.pixelSize: 11
                                    elide: Text.ElideRight
                                }
                            }
                        }
                        MouseArea {
                            id: hoverArea
                            anchors.fill: parent
                            hoverEnabled: true
                            enabled: !parent.currentSessionBusy
                            cursorShape: enabled ? Qt.PointingHandCursor : Qt.ArrowCursor
                            onClicked: parent.openThisSession()
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
                            { icon: "message",    key: "collaboration", tip: "친구와 대화" },
                            { icon: "list",       key: "board",    tip: "커뮤니티 게시판" },
                            { icon: "pin",        key: "plannerOverlay", tip: "플래너 오버레이" },
                            { icon: "monitor",    key: "devices",  tip: "연결된 기기" },
                            { icon: "smartphone", key: "phone",    tip: "폰 연동 패널" },
                            { icon: "gear",       key: "settings", tip: "설정" }
                        ]
                        delegate: Rectangle {
                            required property var modelData
                            Layout.fillWidth: true
                            height: 32; radius: 8
                            color: (modelData.key === "phone" && win.phoneOpen)
                                   || win.view === modelData.key ? Theme.accentSoft : "transparent"
                            border.color: activeFocus ? Theme.accent : "transparent"
                            activeFocusOnTab: true
                            Accessible.role: Accessible.Button
                            Accessible.name: modelData.tip
                            function activateSidebarItem() {
                                if (modelData.key === "phone") win.phoneOpen = !win.phoneOpen
                                else if (modelData.key === "plannerOverlay") plannerOverlayController.enabled = !plannerOverlayController.enabled
                                else win.view = modelData.key
                            }
                            Keys.onPressed: (event) => {
                                if (event.key === Qt.Key_Return || event.key === Qt.Key_Enter || event.key === Qt.Key_Space) {
                                    event.accepted = true
                                    activateSidebarItem()
                                }
                            }
                            Icon {
                                anchors.centerIn: parent
                                name: modelData.icon; size: 15
                                color: (modelData.key === "phone" && win.phoneOpen)
                                       || win.view === modelData.key ? Theme.accentText : Theme.muted
                            }
                            MouseArea {
                                anchors.fill: parent
                                cursorShape: Qt.PointingHandCursor
                                onClicked: parent.activateSidebarItem()
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
                        text: win.view === "collaboration" ? "친구와 대화"
                            : win.view === "board" ? "커뮤니티 게시판"
                            : win.view === "devices" ? "연결된 기기"
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
                            width: dispatchRow.implicitWidth + 24; height: 30; radius: 8
                            color: Theme.raised; border.color: activeFocus ? Theme.accent : Theme.border
                            activeFocusOnTab: true
                            Accessible.role: Accessible.Button
                            Accessible.name: "현재 면접 세션을 폰으로 보내기"
                            function dispatchSession() { jobModel.dispatchToPhone(session.sessionId); win.phoneOpen = true }
                            Keys.onPressed: (event) => {
                                if (event.key === Qt.Key_Return || event.key === Qt.Key_Enter || event.key === Qt.Key_Space) {
                                    event.accepted = true
                                    dispatchSession()
                                }
                            }
                            Row {
                                id: dispatchRow
                                anchors.centerIn: parent
                                spacing: 6
                                Icon { name: "smartphone"; size: 13; color: Theme.muted; anchors.verticalCenter: parent.verticalCenter }
                                Text { text: "폰으로 보내기"; color: Theme.text; font.pixelSize: 12; anchors.verticalCenter: parent.verticalCenter }
                            }
                            MouseArea {
                                anchors.fill: parent; cursorShape: Qt.PointingHandCursor
                                onClicked: parent.dispatchSession()
                            }
                        }
                        Rectangle {
                            width: saveRow.implicitWidth + 24; height: 30; radius: 8
                            color: Theme.raised; border.color: activeFocus ? Theme.accent : Theme.border
                            activeFocusOnTab: true
                            Accessible.role: Accessible.Button
                            Accessible.name: "현재 면접 자료 모두 저장"
                            Keys.onPressed: (event) => {
                                if (event.key === Qt.Key_Return || event.key === Qt.Key_Enter || event.key === Qt.Key_Space) {
                                    event.accepted = true
                                    session.exportAll()
                                }
                            }
                            Row {
                                id: saveRow
                                anchors.centerIn: parent
                                spacing: 6
                                Icon { name: "download"; size: 13; color: Theme.muted; anchors.verticalCenter: parent.verticalCenter }
                                Text { text: "자료 저장"; color: Theme.text; font.pixelSize: 12; anchors.verticalCenter: parent.verticalCenter }
                            }
                            MouseArea {
                                anchors.fill: parent; cursorShape: Qt.PointingHandCursor
                                onClicked: session.exportAll()
                            }
                        }
                        Rectangle {
                            visible: win.view === "thread"
                            width: repLbl.implicitWidth + 22; height: 30; radius: 8
                            border.color: activeFocus ? Theme.accentText : "transparent"
                            activeFocusOnTab: visible
                            Accessible.role: Accessible.Button
                            Accessible.name: "현재 면접 리포트 열기"
                            Accessible.description: session.reportLoading ? "리포트 생성 중" : "이용 안내 후 리포트 생성"
                            opacity: session.reportLoading ? 0.65 : 1
                            function openReport() {
                                if (session.reportLoading) return
                                session.loadReport()
                                win.view = "report"
                            }
                            Keys.onPressed: (event) => {
                                if (event.key === Qt.Key_Return || event.key === Qt.Key_Enter || event.key === Qt.Key_Space) {
                                    event.accepted = true
                                    openReport()
                                }
                            }
                            gradient: Gradient {
                                GradientStop { position: 0.0; color: Theme.accent2 }
                                GradientStop { position: 1.0; color: Theme.accent }
                            }
                            Text { id: repLbl; anchors.centerIn: parent; text: "리포트"; color: "white"; font.pixelSize: 12; font.bold: true }
                            MouseArea {
                                anchors.fill: parent; cursorShape: Qt.PointingHandCursor
                                enabled: !session.reportLoading
                                onClicked: parent.openReport()
                            }
                        }
                        Rectangle {
                            visible: win.view === "report"
                            width: backLbl.implicitWidth + 22; height: 30; radius: 8
                            color: Theme.raised; border.color: activeFocus ? Theme.accent : Theme.border
                            activeFocusOnTab: visible
                            Accessible.role: Accessible.Button
                            Accessible.name: "면접 스레드로 돌아가기"
                            Keys.onPressed: (event) => {
                                if (event.key === Qt.Key_Return || event.key === Qt.Key_Enter || event.key === Qt.Key_Space) {
                                    event.accepted = true
                                    win.view = "thread"
                                }
                            }
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
            Rectangle {
                Layout.fillWidth: true
                height: desktopAds.visible && win.loggedIn ? (desktopAds.body.length > 0 ? 62 : 46) : 0
                visible: height > 0
                color: Theme.surface
                clip: true
                activeFocusOnTab: desktopAds.targetUrl.length > 0
                Accessible.role: Accessible.Button
                Accessible.name: desktopAds.title.length > 0 ? "광고 열기: " + desktopAds.title : "광고 열기"
                Accessible.description: desktopAds.body
                border.color: activeFocus ? Theme.accent : Theme.border
                Keys.onPressed: (event) => {
                    if (event.key === Qt.Key_Return || event.key === Qt.Key_Enter || event.key === Qt.Key_Space) {
                        event.accepted = true
                        if (desktopAds.targetUrl.length > 0) desktopAds.openTarget()
                    }
                }

                RowLayout {
                    anchors.fill: parent
                    anchors.leftMargin: 18
                    anchors.rightMargin: 18
                    spacing: 12
                    Rectangle {
                        width: 8
                        Layout.fillHeight: true
                        color: Theme.warn
                        opacity: 0.85
                    }
                    ColumnLayout {
                        Layout.fillWidth: true
                        spacing: 2
                        Text {
                            Layout.fillWidth: true
                            text: desktopAds.title
                            color: Theme.text
                            font.bold: true
                            font.pixelSize: 12
                            elide: Text.ElideRight
                        }
                        Text {
                            Layout.fillWidth: true
                            visible: desktopAds.body.length > 0
                            text: desktopAds.body
                            color: Theme.muted
                            font.pixelSize: 11
                            elide: Text.ElideRight
                        }
                    }
                    Text {
                        visible: desktopAds.targetUrl.length > 0
                        text: "자세히"
                        color: Theme.accentText
                        font.pixelSize: 11
                        font.bold: true
                    }
                }

                MouseArea {
                    anchors.fill: parent
                    cursorShape: desktopAds.targetUrl.length > 0 ? Qt.PointingHandCursor : Qt.ArrowCursor
                    onClicked: desktopAds.openTarget()
                }
            }

            StackLayout {
                Layout.fillWidth: true
                Layout.fillHeight: true
                currentIndex: win.view === "home" ? 0
                            : win.view === "thread" ? 1
                            : win.view === "report" ? 2
                            : win.view === "collaboration" ? 3
                            : win.view === "board" ? 4
                            : win.view === "devices" ? 5 : 6

                HomeView { id: homeView }                            // 0
                SessionThread {}                                     // 1
                ReportView {}                                        // 2
                CollaborationPage {}                                 // 3
                BoardPage {}                                         // 4
                DevicesPage {}                                       // 5
                SettingsPage {}                                      // 6
            }

            // 하단 입력바 (홈=인테이크 · 스레드=답변)
            InputBar {
                id: sessionInputBar
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

    PlannerOverlay {}

    // ── 토스트 ──
    Column {
        id: toasts
        anchors.right: parent.right
        anchors.bottom: parent.bottom
        anchors.margins: 22
        spacing: 8
        z: 100

        function push(title, body, type, link, targetType, targetId) {
            toastComp.createObject(toasts, {
                title: title,
                body: body,
                notificationType: type,
                notificationLink: link,
                notificationTargetType: targetType,
                notificationTargetId: targetId
            })
        }

        Component {
            id: toastComp
            Rectangle {
                id: toastItem
                property string title: ""
                property string body: ""
                property string notificationType: ""
                property string notificationLink: ""
                property string notificationTargetType: ""
                property var notificationTargetId: 0
                readonly property bool actionable: notificationLink.length > 0
                                                   || notificationTargetType.length > 0
                width: 320
                height: tcol.implicitHeight + 22
                radius: 10
                color: actionable && toastHover.containsMouse ? Theme.hover : Theme.surface
                border.color: actionable && toastHover.containsMouse ? Theme.accent : Theme.border
                Rectangle { width: 3; height: parent.height - 16; y: 8; x: 0; radius: 2; color: Theme.accent }
                ColumnLayout {
                    id: tcol
                    x: 14; y: 11; width: parent.width - 28
                    spacing: 3
                    Text { text: toastItem.title; color: Theme.text; font.pixelSize: 12; font.bold: true; Layout.fillWidth: true; elide: Text.ElideRight }
                    Text { text: toastItem.body; color: Theme.muted; font.pixelSize: 11; Layout.fillWidth: true; wrapMode: Text.WordWrap; maximumLineCount: 3; elide: Text.ElideRight }
                    Text {
                        visible: toastItem.actionable
                        text: "클릭하여 열기  →"
                        color: Theme.accentText
                        font.pixelSize: 10
                        font.bold: true
                        Layout.topMargin: 2
                    }
                }
                MouseArea {
                    id: toastHover
                    anchors.fill: parent
                    enabled: toastItem.actionable
                    hoverEnabled: true
                    cursorShape: Qt.PointingHandCursor
                    onClicked: {
                        win.activateNotification(toastItem.notificationType,
                                                 toastItem.notificationLink,
                                                 toastItem.notificationTargetType,
                                                 toastItem.notificationTargetId)
                        toastItem.destroy()
                    }
                }
                Timer { interval: 4500; running: true; onTriggered: toastItem.destroy() }
                opacity: 0
                Component.onCompleted: opacity = 1
                Behavior on opacity { NumberAnimation { duration: 200 } }
            }
        }
    }
}
