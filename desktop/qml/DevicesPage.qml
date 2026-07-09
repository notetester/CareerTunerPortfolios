import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import CareerTuner

// 연결된 기기 — 알림 수신 채널 현황 + 테스트 디스패치.
Item {
    id: root

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

            // 이 데스크탑
            Rectangle {
                Layout.fillWidth: true
                radius: Theme.radius
                color: Theme.surface; border.color: Theme.border
                height: 66
                RowLayout {
                    anchors.fill: parent
                    anchors.leftMargin: 16; anchors.rightMargin: 16
                    spacing: 12
                    Icon { name: "monitor"; size: 20; color: Theme.muted; Layout.preferredWidth: 20; Layout.preferredHeight: 20 }
                    ColumnLayout {
                        Layout.fillWidth: true
                        spacing: 3
                        Text { text: "이 데스크탑"; color: Theme.text; font.pixelSize: 13; font.bold: true }
                        Text {
                            // 실제 기기 감지(CameraRecorder) — 카메라 없는 PC 는 영상 면접을 폰으로 이어한다
                            text: "현재 기기 · 알림 폴링 30초 · 트레이 상주 · "
                                  + (cameraRecorder.cameraAvailable ? "카메라 있음" : "카메라 없음(영상 면접은 폰으로 이어하기)")
                                  + " · " + (cameraRecorder.microphoneAvailable ? "마이크 있음" : "마이크 없음")
                            color: Theme.muted; font.pixelSize: 11
                        }
                    }
                    Rectangle {
                        height: 22; radius: 11
                        width: onLbl.implicitWidth + 18
                        color: Theme.raised; border.color: Theme.border
                        Text { id: onLbl; anchors.centerIn: parent; text: "● 온라인"; color: Theme.good; font.pixelSize: 11 }
                    }
                }
            }

            // 폰/웹 (계정 공용 채널)
            Rectangle {
                Layout.fillWidth: true
                radius: Theme.radius
                color: Theme.surface; border.color: Theme.border
                height: 66
                RowLayout {
                    anchors.fill: parent
                    anchors.leftMargin: 16; anchors.rightMargin: 16
                    spacing: 12
                    Icon { name: "smartphone"; size: 20; color: Theme.muted; Layout.preferredWidth: 20; Layout.preferredHeight: 20 }
                    ColumnLayout {
                        Layout.fillWidth: true
                        spacing: 3
                        Text { text: "폰 · 웹 (CareerTuner 앱)"; color: Theme.text; font.pixelSize: 13; font.bold: true }
                        Text {
                            text: "인앱 알림(30초 폴링) + 웹 푸시 — 디스패치 수신 대상"
                            color: Theme.muted; font.pixelSize: 11
                        }
                    }
                    Rectangle {
                        height: 28; radius: 8
                        width: testLbl.implicitWidth + 20
                        color: Theme.raised; border.color: Theme.border
                        opacity: session.sessionId > 0 ? 1 : 0.4
                        Text { id: testLbl; anchors.centerIn: parent; text: "테스트 알림"; color: Theme.text; font.pixelSize: 11 }
                        MouseArea {
                            anchors.fill: parent; cursorShape: Qt.PointingHandCursor
                            enabled: session.sessionId > 0
                            onClicked: jobModel.dispatchToPhone(session.sessionId)
                        }
                    }
                }
            }

            Text {
                Layout.fillWidth: true
                Layout.topMargin: 8
                text: "디스패치 알림은 이 계정으로 로그인된 모든 기기(폰·웹·데스크탑)에 전송됩니다.\n테스트 알림은 현재 선택된 세션을 폰으로 보내는 것과 동일하게 동작합니다."
                color: Theme.muted; font.pixelSize: 11
                lineHeight: 1.5
                wrapMode: Text.WordWrap
            }
        }
    }
}
