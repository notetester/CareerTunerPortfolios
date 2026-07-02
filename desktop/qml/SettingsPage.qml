import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import CareerTuner

// 설정 — SettingsStore(QSettings) 실바인딩. 전부 재시작 후에도 유지된다.
Item {
    id: root

    component SettingCard: Rectangle {
        property alias title: titleText.text
        property alias desc: descText.text
        default property alias content: slot.data
        Layout.fillWidth: true
        radius: Theme.radius
        color: Theme.surface; border.color: Theme.border
        implicitHeight: Math.max(rowLay.implicitHeight + 26, 58)
        RowLayout {
            id: rowLay
            anchors.verticalCenter: parent.verticalCenter
            x: 16; width: parent.width - 32
            spacing: 12
            ColumnLayout {
                Layout.fillWidth: true
                spacing: 3
                Text { id: titleText; color: Theme.text; font.pixelSize: 13; font.bold: true }
                Text {
                    id: descText; color: Theme.muted; font.pixelSize: 11
                    Layout.fillWidth: true; wrapMode: Text.WordWrap
                }
            }
            Item {
                id: slot
                implicitWidth: childrenRect.width
                implicitHeight: childrenRect.height
            }
        }
    }

    component Toggle: Rectangle {
        property bool checked: false
        signal toggled(bool value)
        width: 38; height: 21; radius: 10.5
        color: checked ? Theme.accent : Theme.raised
        border.color: checked ? Theme.accent : Theme.border
        Rectangle {
            width: 15; height: 15; radius: 7.5
            y: 3; x: parent.checked ? 20 : 3
            color: parent.checked ? "white" : Theme.muted
            Behavior on x { NumberAnimation { duration: 130 } }
        }
        MouseArea {
            anchors.fill: parent
            cursorShape: Qt.PointingHandCursor
            onClicked: parent.toggled(!parent.checked)
        }
    }

    Flickable {
        anchors.fill: parent
        contentHeight: content.implicitHeight + 60
        clip: true

        ColumnLayout {
            id: content
            width: Math.min(parent.width - 48, 640)
            anchors.horizontalCenter: parent.horizontalCenter
            spacing: 10

            Item { height: 10 }

            Text { text: "로컬 저장"; color: Theme.muted; font.pixelSize: 11; font.bold: true; Layout.leftMargin: 2 }

            SettingCard {
                title: "저장 폴더"
                desc: appSettings.saveDir + " — 세션별 하위 폴더로 정리"
                Rectangle {
                    width: pickLbl.implicitWidth + 22; height: 30; radius: 8
                    color: Theme.raised; border.color: Theme.border
                    Text { id: pickLbl; anchors.centerIn: parent; text: "변경…"; color: Theme.text; font.pixelSize: 12 }
                    MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: appSettings.pickSaveDir() }
                }
            }

            SettingCard {
                title: "완료 시 자동 저장"
                desc: "세션의 모든 답변이 끝나면 리포트를 저장 폴더에 자동 저장"
                Toggle {
                    checked: appSettings.autoSave
                    onToggled: (v) => appSettings.autoSave = v
                }
            }

            Text { text: "계정 · 연결"; color: Theme.muted; font.pixelSize: 11; font.bold: true; Layout.leftMargin: 2; Layout.topMargin: 10 }

            SettingCard {
                title: "자동 로그인"
                desc: "토큰을 이 컴퓨터에 보관하고 시작 시 자동 갱신 (끄면 보관 토큰 삭제)"
                Toggle {
                    checked: appSettings.autoLogin
                    onToggled: (v) => appSettings.autoLogin = v
                }
            }

            SettingCard {
                title: "서버 주소"
                desc: "기본: 팀 공용 원격 백엔드 — 로컬 시연 시 http://localhost:8080"
                RowLayout {
                    spacing: 8
                    TextField {
                        id: urlField
                        text: appSettings.baseUrl
                        color: Theme.text
                        font.pixelSize: 11
                        implicitWidth: 230
                        background: Rectangle {
                            color: Theme.bg; radius: 7
                            border.color: urlField.activeFocus ? Theme.accent : Theme.border
                        }
                    }
                    Rectangle {
                        width: applyLbl.implicitWidth + 18; height: 30; radius: 8
                        color: Theme.raised; border.color: Theme.border
                        Text { id: applyLbl; anchors.centerIn: parent; text: "적용"; color: Theme.text; font.pixelSize: 12 }
                        MouseArea {
                            anchors.fill: parent; cursorShape: Qt.PointingHandCursor
                            onClicked: {
                                appSettings.baseUrl = urlField.text.trim()
                                win.showToast("서버 주소 변경됨", appSettings.baseUrl)
                            }
                        }
                    }
                }
            }

            SettingCard {
                title: "로그아웃"
                desc: auth.userEmail !== "" ? auth.userEmail + " 계정에서 로그아웃" : "현재 계정에서 로그아웃"
                Rectangle {
                    width: outLbl.implicitWidth + 22; height: 30; radius: 8
                    color: Theme.raised; border.color: Theme.border
                    Text { id: outLbl; anchors.centerIn: parent; text: "로그아웃"; color: Theme.danger; font.pixelSize: 12 }
                    MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: auth.logout() }
                }
            }

            Text { text: "알림 · 화면"; color: Theme.muted; font.pixelSize: 11; font.bold: true; Layout.leftMargin: 2; Layout.topMargin: 10 }

            SettingCard {
                title: "트레이 알림"
                desc: "폰 답변 도착·채점 완료 등을 Windows 토스트로 표시 (30초 폴링)"
                Toggle {
                    checked: appSettings.trayNotify
                    onToggled: (v) => appSettings.trayNotify = v
                }
            }

            SettingCard {
                title: "테마"
                desc: "블랙 + 인디고 다크 (기본 고정)"
            }
        }
    }
}
