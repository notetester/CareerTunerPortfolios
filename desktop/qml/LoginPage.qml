import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import CareerTuner

// 로그인: POST /api/auth/login (AuthService). 성공 시 토큰 영속화 → 다음부터 자동 로그인.
Item {
    id: login

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
            Text { text: "면접 준비 컨트롤 센터에 로그인"; color: Theme.muted; font.pixelSize: 13 }

            TextField {
                id: emailField
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

            RowLayout {
                spacing: 8
                Rectangle {
                    width: 16; height: 16; radius: 4
                    color: appSettings.autoLogin ? Theme.accent : "transparent"
                    border.color: appSettings.autoLogin ? Theme.accent : Theme.border
                    Text {
                        anchors.centerIn: parent; visible: appSettings.autoLogin
                        text: "✓"; color: "white"; font.pixelSize: 10; font.bold: true
                    }
                    MouseArea { anchors.fill: parent; onClicked: appSettings.autoLogin = !appSettings.autoLogin }
                }
                Text { text: "자동 로그인 (이 컴퓨터에 로그인 유지)"; color: Theme.muted; font.pixelSize: 12 }
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
                    auth.login(emailField.text, pwField.text)
                }
                Text { anchors.centerIn: parent; text: "로그인"; color: "white"; font.pixelSize: 13; font.bold: true }
                MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: loginBtn.doLogin() }
            }

            Text {
                id: errMsg
                text: ""; color: Theme.danger; font.pixelSize: 12
                visible: text !== ""; Layout.fillWidth: true; wrapMode: Text.WordWrap
            }
        }

        Connections {
            target: auth
            function onLoginFailed(message) { errMsg.text = "✕ " + message }
        }
    }
}
