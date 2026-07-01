import QtQuick
import QtQuick.Controls
import QtQuick.Layouts

// 연결된 기기: 같은 계정의 기기 목록 + 디스패치(폰으로 보내기/이어받기).
Item {
    id: devices

    Connections {
        target: jobModel
        function onDispatched(sid) { devices.showDispatch("✓ 폰으로 알림을 보냈어요 — 폰 CareerTuner 앱 알림 벨에 30초 안에 뜹니다.") }
    }

    ColumnLayout {
        anchors.fill: parent
        anchors.margins: 24
        spacing: 16

        ColumnLayout {
            spacing: 2
            Text { text: "연결된 기기"; color: "#e6edf3"; font.pixelSize: 22; font.bold: true }
            Text { text: "같은 계정에 로그인된 기기끼리 작업을 주고받습니다."; color: "#8b949e"; font.pixelSize: 13 }
        }

        // 기기 카드
        RowLayout {
            Layout.fillWidth: true
            spacing: 16
            Repeater {
                model: [
                    { icon: "🖥️", name: "정원일 데스크탑", meta: "Windows · CareerTuner Desktop v0.1", me: true },
                    { icon: "📱", name: "갤럭시 S20", meta: "Android · CareerTuner App", me: false }
                ]
                delegate: Rectangle {
                    required property var modelData
                    Layout.fillWidth: true
                    implicitHeight: 84
                    color: "#161b22"
                    border.color: modelData.me ? "#2dd4bf" : "#30363d"
                    radius: 12
                    RowLayout {
                        anchors.fill: parent; anchors.margins: 16; spacing: 14
                        Rectangle {
                            width: 48; height: 48; radius: 11; color: "#222b38"
                            Text { anchors.centerIn: parent; text: modelData.icon; font.pixelSize: 24 }
                        }
                        ColumnLayout {
                            spacing: 2
                            Text {
                                text: modelData.name + (modelData.me ? "  (이 기기)" : "")
                                color: "#e6edf3"; font.pixelSize: 15; font.bold: true
                            }
                            Text { text: modelData.meta; color: "#8b949e"; font.pixelSize: 12 }
                        }
                        Item { Layout.fillWidth: true }
                        RowLayout {
                            spacing: 6
                            Rectangle { width: 7; height: 7; radius: 3.5; color: "#3fb950" }
                            Text { text: "온라인"; color: "#3fb950"; font.pixelSize: 12; font.bold: true }
                        }
                    }
                    MouseArea {
                        anchors.fill: parent
                        onClicked: {
                            if (!modelData.me) {
                                if (jobModel.current.id !== undefined) jobModel.dispatchToPhone(jobModel.current.id)
                                else devices.showDispatch("진행 중인 작업이 없습니다.")
                            }
                        }
                    }
                }
            }
        }

        Text { text: "디스패치"; color: "#8b949e"; font.pixelSize: 12; font.bold: true }
        Rectangle {
            Layout.fillWidth: true
            implicitHeight: dispCol.implicitHeight + 36
            color: "#161b22"; border.color: "#30363d"; radius: 12
            ColumnLayout {
                id: dispCol
                x: 20; y: 18; width: parent.width - 40
                spacing: 14
                Text { text: "현재 진행 중인 작업을 다른 기기로 보내거나 이어받습니다."; color: "#8b949e"; font.pixelSize: 13 }
                RowLayout {
                    spacing: 10
                    Button {
                        text: "📲 현재 작업을 폰으로 보내기"
                        onClicked: {
                            if (jobModel.current.id !== undefined) jobModel.dispatchToPhone(jobModel.current.id)
                            else devices.showDispatch("진행 중인 작업이 없습니다. 대시보드에서 세션을 먼저 여세요.")
                        }
                    }
                    Button {
                        text: "🖥️ 폰에서 하던 세션 이어받기"
                        onClicked: {
                            if (jobModel.current.id !== undefined) { jobModel.markResumed(jobModel.current.id); devices.showDispatch("✓ '" + (jobModel.current.title || "") + "' 세션을 이어받았습니다.") }
                            else devices.showDispatch("이어받을 세션이 없습니다.")
                        }
                    }
                }
                Text { id: dispMsg; text: ""; color: "#2dd4bf"; font.pixelSize: 13; visible: text !== "" }
            }
        }

        Item { Layout.fillHeight: true }
    }

    function showDispatch(msg) { dispMsg.text = msg }
}
