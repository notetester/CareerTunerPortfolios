import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import CareerTuner

// 로그인: LoginResponse.token 또는 MFA challenge 를 처리한다.
// 성공 시 토큰 영속화 → 다음부터 자동 로그인.
Item {
    id: login
    property bool backupCodeMode: false

    Rectangle {
        anchors.centerIn: parent
        width: 380
        implicitHeight: col.implicitHeight + 48
        color: Theme.surface
        border.color: Theme.border
        radius: 16

        ColumnLayout {
            id: col
            x: 28; y: 24; width: parent.width - 56
            spacing: 14

            RowLayout {
                spacing: 10
                Rectangle {
                    width: 32; height: 32; radius: 9; color: Theme.accent
                    Text { anchors.centerIn: parent; text: "C"; color: "white"; font.bold: true; font.pixelSize: 16 }
                }
                Text { text: "CareerTuner"; color: Theme.text; font.pixelSize: 18; font.bold: true }
            }
            Text {
                text: auth.mfaChallengeActive
                    ? "2단계 인증으로 로그인을 완료하세요"
                    : "면접 준비 컨트롤 센터에 로그인"
                color: Theme.muted; font.pixelSize: 13
            }

            Text {
                visible: auth.mfaChallengeActive
                Layout.fillWidth: true
                text: auth.mfaChallengeMethod.indexOf("PUSH") >= 0
                    ? "인증 앱의 6자리 코드 또는 백업 코드를 입력할 수 있습니다. 휴대폰 승인도 지원합니다."
                    : "인증 앱의 6자리 코드 또는 백업 코드를 입력할 수 있습니다."
                color: Theme.text; font.pixelSize: 12; wrapMode: Text.WordWrap
            }

            TextField {
                id: emailField
                visible: !auth.mfaChallengeActive
                Layout.fillWidth: true
                placeholderText: "이메일"
                placeholderTextColor: Theme.muted
                color: Theme.text
                background: Rectangle {
                    color: Theme.bg; radius: 8
                    border.color: emailField.activeFocus ? Theme.accent : Theme.border
                }
            }
            TextField {
                id: pwField
                visible: !auth.mfaChallengeActive
                Layout.fillWidth: true
                placeholderText: "비밀번호"
                placeholderTextColor: Theme.muted
                echoMode: TextInput.Password
                color: Theme.text
                background: Rectangle {
                    color: Theme.bg; radius: 8
                    border.color: pwField.activeFocus ? Theme.accent : Theme.border
                }
                onAccepted: loginBtn.doLogin()
            }

            TextField {
                id: mfaCodeField
                visible: auth.mfaChallengeActive
                Layout.fillWidth: true
                placeholderText: login.backupCodeMode ? "백업 코드" : "6자리 인증 코드"
                placeholderTextColor: Theme.muted
                color: Theme.text
                maximumLength: login.backupCodeMode ? 64 : 6
                inputMethodHints: login.backupCodeMode ? Qt.ImhNone : Qt.ImhDigitsOnly
                horizontalAlignment: login.backupCodeMode ? TextInput.AlignLeft : TextInput.AlignHCenter
                font.letterSpacing: login.backupCodeMode ? 0 : 4
                background: Rectangle {
                    color: Theme.bg; radius: 8
                    border.color: mfaCodeField.activeFocus ? Theme.accent : Theme.border
                }
                onAccepted: loginBtn.doLogin()
            }

            RowLayout {
                visible: !auth.mfaChallengeActive
                spacing: 8
                Rectangle {
                    width: 16; height: 16; radius: 4
                    color: appSettings.autoLogin ? Theme.accent : "transparent"
                    border.color: appSettings.autoLogin ? Theme.accent : Theme.border
                    Icon {
                        anchors.centerIn: parent; visible: appSettings.autoLogin
                        name: "check"; size: 10; color: "white"; strokeWidth: 3
                    }
                    MouseArea { anchors.fill: parent; onClicked: appSettings.autoLogin = !appSettings.autoLogin }
                }
                Text { text: "자동 로그인 (이 컴퓨터에 로그인 유지)"; color: Theme.muted; font.pixelSize: 12 }
            }

            RowLayout {
                visible: auth.mfaChallengeActive
                Layout.fillWidth: true
                spacing: 8
                Rectangle {
                    width: 16; height: 16; radius: 4
                    color: login.backupCodeMode ? Theme.accent : "transparent"
                    border.color: login.backupCodeMode ? Theme.accent : Theme.border
                    Icon {
                        anchors.centerIn: parent; visible: login.backupCodeMode
                        name: "check"; size: 10; color: "white"; strokeWidth: 3
                    }
                    MouseArea {
                        anchors.fill: parent
                        onClicked: {
                            login.backupCodeMode = !login.backupCodeMode
                            mfaCodeField.text = ""
                            mfaCodeField.forceActiveFocus()
                        }
                    }
                }
                Text { text: "백업 코드 사용"; color: Theme.muted; font.pixelSize: 12 }
                Item { Layout.fillWidth: true }
                Text {
                    visible: auth.mfaChallengeMethod.indexOf("PUSH") >= 0
                    text: "휴대폰 승인 확인"
                    color: Theme.accentText; font.pixelSize: 12; font.bold: true
                    MouseArea {
                        anchors.fill: parent
                        cursorShape: Qt.PointingHandCursor
                        onClicked: {
                            errMsg.text = ""
                            auth.checkMfaStatus()
                        }
                    }
                }
            }

            Text {
                visible: auth.mfaChallengeActive && auth.mfaStatusText.length > 0
                Layout.fillWidth: true
                text: auth.mfaStatusText
                color: Theme.muted; font.pixelSize: 11; wrapMode: Text.WordWrap
            }

            Rectangle {
                id: loginBtn
                Layout.fillWidth: true
                height: 38; radius: 9
                gradient: Gradient {
                    GradientStop { position: 0.0; color: Theme.accent2 }
                    GradientStop { position: 1.0; color: Theme.accent }
                }
                function doLogin() {
                    errMsg.text = ""
                    if (auth.mfaChallengeActive) {
                        auth.verifyMfa(mfaCodeField.text, login.backupCodeMode)
                    } else {
                        auth.login(emailField.text, pwField.text)
                    }
                }
                Text {
                    anchors.centerIn: parent
                    text: auth.mfaChallengeActive ? "인증하고 로그인" : "로그인"
                    color: "white"; font.pixelSize: 13; font.bold: true
                }
                MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: loginBtn.doLogin() }
            }

            Text {
                visible: auth.mfaChallengeActive
                Layout.alignment: Qt.AlignHCenter
                text: "다른 계정으로 로그인"
                color: Theme.muted; font.pixelSize: 12
                MouseArea {
                    anchors.fill: parent
                    cursorShape: Qt.PointingHandCursor
                    onClicked: {
                        auth.cancelMfa()
                        login.backupCodeMode = false
                        mfaCodeField.text = ""
                        errMsg.text = ""
                        emailField.forceActiveFocus()
                    }
                }
            }

            Text {
                id: errMsg
                text: ""; color: Theme.danger; font.pixelSize: 12
                visible: text !== ""; Layout.fillWidth: true; wrapMode: Text.WordWrap
            }
        }

        Connections {
            target: auth
            function onLoginFailed(message) { errMsg.text = message }
            function onMfaChallengeChanged() {
                if (auth.mfaChallengeActive) {
                    pwField.text = ""
                    login.backupCodeMode = false
                    mfaCodeField.text = ""
                    Qt.callLater(function() { mfaCodeField.forceActiveFocus() })
                }
            }
        }
    }
}
