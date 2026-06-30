import QtQuick
import QtQuick.Controls
import QtQuick.Layouts

// 면접 연습: 질문 카드 + 답변 입력/녹음 + 다음/꼬리질문.
// 현재는 정적 — 추후 C++(InterviewClient)로 질문/평가 연동.
Item {
    ColumnLayout {
        anchors.fill: parent
        anchors.margins: 24
        spacing: 16

        RowLayout {
            Layout.fillWidth: true
            ColumnLayout {
                spacing: 2
                Text { text: "면접 연습"; color: "#e6edf3"; font.pixelSize: 22; font.bold: true }
                Text { text: "삼성전자 · SW 개발직군 · 직무 면접 모드"; color: "#8b949e"; font.pixelSize: 13 }
            }
            Item { Layout.fillWidth: true }
            Text { text: "Q3 / 12"; color: "#8b949e"; font.pixelSize: 13 }
        }

        // 질문 카드
        Rectangle {
            Layout.fillWidth: true
            color: "#161b22"; border.color: "#30363d"; radius: 12
            implicitHeight: qcol.implicitHeight + 44
            ColumnLayout {
                id: qcol
                x: 24; y: 22; width: parent.width - 48
                spacing: 8
                Text { text: "질문 3"; color: "#2dd4bf"; font.pixelSize: 12; font.bold: true }
                Text {
                    text: "팀 프로젝트에서 기술적 의견 충돌이 있었을 때, 어떻게 풀어갔는지 구체적으로 말씀해 주세요."
                    color: "#e6edf3"; font.pixelSize: 18; wrapMode: Text.WordWrap; Layout.fillWidth: true
                }
            }
        }

        // 답변 입력
        Rectangle {
            Layout.fillWidth: true
            Layout.fillHeight: true
            color: "#161b22"; border.color: "#30363d"; radius: 12
            ColumnLayout {
                anchors.fill: parent
                anchors.margins: 18
                spacing: 14
                ScrollView {
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    TextArea {
                        placeholderText: "답변을 입력하거나, 마이크 버튼으로 음성 답변을 녹음하세요..."
                        color: "#e6edf3"; wrapMode: TextArea.Wrap
                        background: Rectangle { color: "#0d1117"; border.color: "#30363d"; radius: 10 }
                    }
                }
                RowLayout {
                    spacing: 12
                    Rectangle {
                        width: 46; height: 46; radius: 23
                        color: "transparent"; border.color: "#f85149"; border.width: 2
                        Rectangle { anchors.centerIn: parent; width: 18; height: 18; radius: 4; color: "#f85149" }
                        MouseArea { anchors.fill: parent } // TODO: 녹음 시작/중지
                    }
                    Text { text: "🎤 음성 답변 녹음"; color: "#8b949e"; font.pixelSize: 12 }
                    Item { Layout.fillWidth: true }
                    Button { text: "꼬리질문 받기" }
                    Button { text: "답변 제출 → 다음" }
                }
            }
        }
    }
}
