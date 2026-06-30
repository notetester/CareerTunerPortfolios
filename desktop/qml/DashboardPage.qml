import QtQuick
import QtQuick.Controls
import QtQuick.Layouts

// 작업 대시보드: jobModel(C++ JobModel)을 ListView 로 바인딩.
Item {
    ColumnLayout {
        anchors.fill: parent
        anchors.margins: 24
        spacing: 16

        // 헤더
        RowLayout {
            Layout.fillWidth: true
            ColumnLayout {
                spacing: 2
                Text { text: "작업 대시보드"; color: "#e6edf3"; font.pixelSize: 22; font.bold: true }
                Text { text: "진행 중인 면접 준비 작업을 한눈에 모니터링합니다."; color: "#8b949e"; font.pixelSize: 13 }
            }
            Item { Layout.fillWidth: true }
            Button {
                text: "＋ 새 면접 준비"
                // TODO: 새 작업 위저드(되묻기 CASE/MODE) 연결
            }
        }

        // 작업 카드 목록
        ListView {
            id: list
            Layout.fillWidth: true
            Layout.fillHeight: true
            clip: true
            spacing: 14
            model: jobModel

            delegate: Rectangle {
                width: ListView.view.width
                height: 112
                radius: 12
                color: "#161b22"
                border.color: "#30363d"

                ColumnLayout {
                    anchors.fill: parent
                    anchors.margins: 16
                    spacing: 10

                    RowLayout {
                        Layout.fillWidth: true
                        Text { text: title; color: "#e6edf3"; font.pixelSize: 15; font.bold: true }
                        Item { Layout.fillWidth: true }
                        Text {
                            text: status === "DONE" ? "✓ 완료"
                                : status === "RUNNING" ? ("진행 중 " + progress + "%")
                                : "대기열"
                            color: status === "DONE" ? "#3fb950"
                                 : status === "RUNNING" ? "#58a6ff"
                                 : "#d29922"
                            font.pixelSize: 12
                            font.bold: true
                        }
                    }

                    Text { text: mode + " · 지원 건 #" + jobId; color: "#8b949e"; font.pixelSize: 12 }

                    // 진행률 바
                    Rectangle {
                        Layout.fillWidth: true
                        height: 7
                        radius: 4
                        color: "#0a0d12"
                        Rectangle {
                            width: parent.width * progress / 100
                            height: parent.height
                            radius: 4
                            gradient: Gradient {
                                orientation: Gradient.Horizontal
                                GradientStop { position: 0.0; color: "#7c5cff" }
                                GradientStop { position: 1.0; color: "#2dd4bf" }
                            }
                        }
                    }
                }

                MouseArea {
                    anchors.fill: parent
                    // TODO: 클릭 시 작업 상세(JobDetailPage)로 전환
                }
            }
        }
    }
}
