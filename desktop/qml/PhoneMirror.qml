import QtQuick
import QtQuick.Layouts

// 우측 폰 미러: 데스크탑 작업을 폰에서도 보는 모습(실시간 동기화 시각화).
Rectangle {
    color: "#161b22"

    ColumnLayout {
        anchors.fill: parent
        anchors.margins: 16
        spacing: 12

        Text { text: "모바일 미러 (실시간 동기화)"; color: "#8b949e"; font.pixelSize: 11; font.bold: true }

        // 폰 프레임
        Rectangle {
            Layout.fillWidth: true
            Layout.fillHeight: true
            radius: 28
            color: "#0d1117"
            border.color: "#20242c"; border.width: 7

            ColumnLayout {
                anchors.fill: parent
                anchors.margins: 16
                spacing: 10

                RowLayout {
                    Layout.fillWidth: true
                    Text { text: "9:41"; color: "#8b949e"; font.pixelSize: 11 }
                    Item { Layout.fillWidth: true }
                    Text { text: "📶 🔋"; color: "#8b949e"; font.pixelSize: 11 }
                }
                Text { text: "C  CareerTuner"; color: "#2dd4bf"; font.pixelSize: 15; font.bold: true }
                Text {
                    text: "데스크탑과 같은 작업을 폰에서도 봅니다"
                    color: "#8b949e"; font.pixelSize: 11; wrapMode: Text.WordWrap; Layout.fillWidth: true
                }

                // 동기화된 작업 카드
                Rectangle {
                    Layout.fillWidth: true
                    color: "#161b22"; border.color: "#30363d"; radius: 12
                    implicitHeight: pcol.implicitHeight + 24
                    ColumnLayout {
                        id: pcol
                        x: 12; y: 12; width: parent.width - 24
                        spacing: 6
                        Text { text: "삼성전자 · SW 개발직군"; color: "#e6edf3"; font.pixelSize: 13; font.bold: true }
                        Text { text: "직무 면접 · #128"; color: "#8b949e"; font.pixelSize: 11 }
                        Rectangle {
                            Layout.fillWidth: true; height: 6; radius: 3; color: "#0a0d12"
                            Rectangle {
                                width: parent.width * 0.65; height: 6; radius: 3
                                gradient: Gradient {
                                    orientation: Gradient.Horizontal
                                    GradientStop { position: 0.0; color: "#7c5cff" }
                                    GradientStop { position: 1.0; color: "#2dd4bf" }
                                }
                            }
                        }
                        Text { text: "⟳ 모의면접 · 65%"; color: "#2dd4bf"; font.pixelSize: 11 }
                    }
                }

                Item { Layout.fillHeight: true }
            }
        }

        Text {
            text: "같은 작업 번호로 접속 → 동시에 같은 진행"
            color: "#8b949e"; font.pixelSize: 10; wrapMode: Text.WordWrap
            Layout.fillWidth: true; horizontalAlignment: Text.AlignHCenter
        }
    }
}
