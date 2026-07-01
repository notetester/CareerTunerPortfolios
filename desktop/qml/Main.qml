import QtQuick
import QtQuick.Controls
import QtQuick.Layouts

// 메인 셸: 좌측 사이드바 + 우측 화면 스택.
// 다크 팔레트는 프로토타입(docs/desktop-app-prototype.html)과 동일.
ApplicationWindow {
    id: win
    visible: true
    width: 1280
    height: 800
    title: "CareerTuner — 면접 준비 컨트롤 센터"
    color: "#0d1117"

    readonly property color cBg2:    "#161b22"
    readonly property color cBorder: "#30363d"
    readonly property color cAccent: "#7c5cff"
    readonly property color cMuted:  "#8b949e"
    readonly property color cText:   "#e6edf3"

    NewJobDialog {
        id: newJobDialog
        onJobCreated: (caseId, mode) => jobModel.createSession(caseId, mode)
    }

    // ── 로그인 게이트 ──
    property bool loggedIn: false
    Connections {
        target: auth
        function onLoggedIn(token) { win.loggedIn = true; jobModel.reload() }
    }
    LoginPage {
        visible: !win.loggedIn
        anchors.fill: parent
    }

    RowLayout {
        visible: win.loggedIn
        anchors.fill: parent
        spacing: 0

        // ── 사이드바 ──
        Rectangle {
            Layout.preferredWidth: 210
            Layout.fillHeight: true
            color: win.cBg2
            border.color: win.cBorder

            ColumnLayout {
                anchors.fill: parent
                anchors.margins: 10
                spacing: 4

                RowLayout {
                    Layout.margins: 6
                    spacing: 8
                    Rectangle {
                        width: 26; height: 26; radius: 7; color: win.cAccent
                        Text { anchors.centerIn: parent; text: "C"; color: "white"; font.bold: true }
                    }
                    Text { text: "CareerTuner"; color: win.cText; font.bold: true; font.pixelSize: 15 }
                }

                Repeater {
                    model: ["작업 대시보드", "면접 연습", "면접 리포트", "연결된 기기", "설정"]
                    delegate: Rectangle {
                        Layout.fillWidth: true
                        height: 40
                        radius: 9
                        color: index === stack.currentIndex ? Qt.rgba(0.49, 0.36, 1, 0.16) : "transparent"
                        Text {
                            anchors.verticalCenter: parent.verticalCenter
                            x: 12
                            text: modelData
                            color: index === stack.currentIndex ? win.cText : win.cMuted
                            font.pixelSize: 14
                        }
                        MouseArea { anchors.fill: parent; onClicked: stack.currentIndex = index }
                    }
                }

                Item { Layout.fillHeight: true } // 아래 여백

                // 사용자 정보(자리표시)
                RowLayout {
                    Layout.margins: 6
                    spacing: 10
                    Rectangle {
                        width: 30; height: 30; radius: 15; color: "#222b38"
                        Text { anchors.centerIn: parent; text: "정"; color: win.cText; font.pixelSize: 13 }
                    }
                    ColumnLayout {
                        spacing: 0
                        Text { text: "정원일"; color: win.cText; font.pixelSize: 13; font.bold: true }
                        Text { text: "PRO 플랜"; color: win.cMuted; font.pixelSize: 11 }
                    }
                }
            }
        }

        // ── 화면 스택 ──
        StackLayout {
            id: stack
            Layout.fillWidth: true
            Layout.fillHeight: true
            currentIndex: 0

            DashboardPage {                        // 0 작업 대시보드
                onOpenDetail: (jobId, title, mode) => {
                    detailPage.jobId = jobId
                    detailPage.jobTitle = title
                    detailPage.jobMode = mode
                    detailPage.questionList = []
                    detailPage.progress = ({})
                    detailPage.resumeMsg = ""
                    stack.currentIndex = 5
                    jobModel.loadQuestions(jobId)
                    jobModel.loadProgress(jobId)
                }
                onRequestNewJob: newJobDialog.open()
            }
            PracticePage {}                        // 1 면접 연습
            ReportPage {}                          // 2 면접 리포트
            DevicesPage {}                         // 3 연결된 기기
            SettingsPage {}                        // 4 설정
            JobDetailPage {                        // 5 작업 상세 (카드 클릭 진입)
                id: detailPage
                onBack: stack.currentIndex = 0
            }
        }

        // ── 우측 폰 미러 (모바일 동기화 시각화) ──
        PhoneMirror {
            Layout.preferredWidth: 300
            Layout.fillHeight: true
        }
    }

    // 간단한 자리표시 컴포넌트
    component Placeholder: Item {
        property string label: ""
        Text {
            anchors.centerIn: parent
            text: parent.label
            color: "#8b949e"
            font.pixelSize: 16
        }
    }
}
