import QtQuick
import QtQuick.Controls
import QtQuick.Layouts

// 면접 리포트: 종합 점수(도넛) + 질문별 평가.
Item {
    ColumnLayout {
        anchors.fill: parent
        anchors.margins: 24
        spacing: 16

        ColumnLayout {
            spacing: 2
            Text { text: "면접 리포트"; color: "#e6edf3"; font.pixelSize: 22; font.bold: true }
            Text { text: "네이버 · 백엔드 인턴 · 종합 평가"; color: "#8b949e"; font.pixelSize: 13 }
        }

        RowLayout {
            Layout.fillWidth: true
            Layout.fillHeight: true
            spacing: 20

            // 점수 카드
            Rectangle {
                Layout.preferredWidth: 240
                Layout.fillHeight: true
                color: "#161b22"; border.color: "#30363d"; radius: 12
                ColumnLayout {
                    anchors.centerIn: parent
                    spacing: 12
                    Canvas {
                        Layout.alignment: Qt.AlignHCenter
                        width: 150; height: 150
                        onPaint: {
                            var ctx = getContext("2d");
                            var cx = 75, cy = 75, r = 62;
                            ctx.lineWidth = 12;
                            ctx.strokeStyle = "#0a0d12";
                            ctx.beginPath(); ctx.arc(cx, cy, r, 0, 2 * Math.PI); ctx.stroke();
                            ctx.strokeStyle = "#2dd4bf";
                            ctx.beginPath();
                            ctx.arc(cx, cy, r, -Math.PI / 2, -Math.PI / 2 + 2 * Math.PI * 0.82);
                            ctx.stroke();
                        }
                    }
                    Text { text: "82점"; color: "#e6edf3"; font.pixelSize: 26; font.bold: true; Layout.alignment: Qt.AlignHCenter }
                    Text { text: "종합: 우수"; color: "#8b949e"; font.pixelSize: 13; Layout.alignment: Qt.AlignHCenter }
                }
            }

            // 질문별 평가
            ListView {
                Layout.fillWidth: true
                Layout.fillHeight: true
                clip: true
                spacing: 11
                model: [
                    { q: "Q1. 자기소개",      p: 88, f: "핵심 역량을 30초 안에 명확히 전달. 직무 연관성 뚜렷." },
                    { q: "Q2. 트러블슈팅",    p: 85, f: "문제→가설→검증 흐름 좋음. 결과 수치 보강 권장." },
                    { q: "Q3. 협업 갈등 해결", p: 76, f: "상황 설명은 충분하나 본인 행동(Action) 비중을 키울 것." },
                    { q: "Q4. 지원 동기",      p: 79, f: "회사 분석 양호. 본인 경험과 연결 고리 하나 추가." }
                ]
                delegate: Rectangle {
                    required property var modelData
                    width: ListView.view.width
                    color: "#161b22"; border.color: "#30363d"; radius: 11
                    implicitHeight: rcol.implicitHeight + 26
                    ColumnLayout {
                        id: rcol
                        x: 16; y: 13; width: parent.width - 32
                        spacing: 5
                        RowLayout {
                            Layout.fillWidth: true
                            Text { text: rcol.parent.modelData.q; color: "#e6edf3"; font.pixelSize: 14; font.bold: true }
                            Item { Layout.fillWidth: true }
                            Text { text: rcol.parent.modelData.p; color: "#2dd4bf"; font.pixelSize: 15; font.bold: true }
                        }
                        Text {
                            text: rcol.parent.modelData.f; color: "#8b949e"; font.pixelSize: 13
                            wrapMode: Text.WordWrap; Layout.fillWidth: true
                        }
                    }
                }
            }
        }
    }
}
