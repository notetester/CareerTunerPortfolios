import QtQuick
import QtQuick.Controls
import QtQuick.Layouts

// 로그인: POST /api/auth/login (AuthService). 성공 시 win.loggedIn = true.
Item {
    id: login

    Rectangle {
        anchors.centerIn: parent
        width: 380
        implicitHeight: col.implicitHeight + 48
        color: "#161b22"; border.color: "#30363d"; radius: 16

        ColumnLayout {
            id: col
            x: 28; y: 24; width: parent.width - 56
            spacing: 14

            RowLayout {
                spacing: 10
                Rectangle {
                    width: 32; height: 32; radius: 8; color: "#7c5cff"
                    Text { anchors.centerIn: parent; text: "C"; color: "white"; font.bold: true; font.pixelSize: 16 }
                }
                Text { text: "CareerTuner"; color: "#e6edf3"; font.pixelSize: 18; font.bold: true }
            }
            Text { text: "면접 준비 컨트롤 센터에 로그인"; color: "#8b949e"; font.pixelSize: 13 }

            TextField {
                id: emailField
                Layout.fillWidth: true
                placeholderText: "이메일"
                text: "jiwon.kim@careertuner.dev"
                color: "#e6edf3"
                background: Rectangle { color: "#0d1117"; border.color: "#30363d"; radius: 8 }
            }
            TextField {
                id: pwField
                Layout.fillWidth: true
                placeholderText: "비밀번호"
                echoMode: TextInput.Password
                text: "Career1234!"
                color: "#e6edf3"
                background: Rectangle { color: "#0d1117"; border.color: "#30363d"; radius: 8 }
                onAccepted: loginBtn.clicked()
            }
            Button {
                id: loginBtn
                Layout.fillWidth: true
                text: "로그인"
                onClicked: { errMsg.text = ""; auth.login(emailField.text, pwField.text) }
            }
            Text {
                id: errMsg
                text: ""; color: "#f85149"; font.pixelSize: 12
                visible: text !== ""; Layout.fillWidth: true; wrapMode: Text.WordWrap
            }
        }

        Connections {
            target: auth
            function onLoginFailed(message) { errMsg.text = "✕ " + message }
        }
    }
}
