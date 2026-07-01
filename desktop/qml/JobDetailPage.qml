import QtQuick
import QtQuick.Controls
import QtQuick.Layouts

// 작업 상세: AI 오케스트레이터 실행 그래프(의존 레인) + 단계별 결과.
// 현재는 샘플 레이아웃 — 추후 C++(JobModel/이벤트)에서 실제 데이터 주입.
Item {
    id: detail
    property int jobId: 128
    property string jobTitle: "삼성전자 · SW 개발직군"
    property string jobMode: "직무 면접"
    property var questionList: []
    signal back()

    Connections {
        target: jobModel
        function onQuestionsReady(questions) { detail.questionList = questions }
    }

    property var lanes: [
        { label: "1차",    nodes: [{n:"공고 분석", s:"done"}] },
        { label: "병렬",   nodes: [{n:"프로필 정리", s:"done"}, {n:"자소서 분석", s:"done"}, {n:"적합도 분석", s:"done"}] },
        { label: "질문",   nodes: [{n:"예상질문 생성", s:"done"}] },
        { label: "면접",   nodes: [{n:"모의면접", s:"run"}] },
        { label: "평가",   nodes: [{n:"답변 평가", s:"wait"}] },
        { label: "리포트", nodes: [{n:"리포트 생성", s:"wait"}] }
    ]
    function nodeColor(s) { return s === "done" ? "#3fb950" : s === "run" ? "#58a6ff" : "#30363d" }

    ColumnLayout {
        anchors.fill: parent
        anchors.margins: 24
        spacing: 16

        RowLayout {
            Layout.fillWidth: true
            ColumnLayout {
                spacing: 2
                Text { text: detail.jobTitle; color: "#e6edf3"; font.pixelSize: 22; font.bold: true }
                Text { text: detail.jobMode + " · 지원 건 #" + detail.jobId; color: "#8b949e"; font.pixelSize: 13 }
            }
            Item { Layout.fillWidth: true }
            Button { text: "← 대시보드"; onClicked: detail.back() }
        }

        Text { text: "AI 오케스트레이터 — 실행 그래프 (의존 관계)"; color: "#8b949e"; font.pixelSize: 12; font.bold: true }

        // 의존 그래프
        Rectangle {
            Layout.fillWidth: true
            color: "#161b22"; border.color: "#30363d"; radius: 12
            implicitHeight: graphCol.implicitHeight + 36
            ColumnLayout {
                id: graphCol
                x: 18; y: 18; width: parent.width - 36
                spacing: 14
                Repeater {
                    model: lanes
                    delegate: RowLayout {
                        required property var modelData
                        Layout.fillWidth: true
                        spacing: 14
                        Text {
                            text: modelData.label; color: "#8b949e"; font.pixelSize: 12
                            Layout.preferredWidth: 48; horizontalAlignment: Text.AlignRight
                        }
                        Flow {
                            Layout.fillWidth: true
                            spacing: 10
                            Repeater {
                                model: modelData.nodes
                                delegate: Rectangle {
                                    required property var modelData
                                    width: 150; height: 38; radius: 9
                                    color: "#0d1117"; border.color: nodeColor(modelData.s)
                                    RowLayout {
                                        anchors.fill: parent; anchors.margins: 10; spacing: 8
                                        Rectangle { width: 9; height: 9; radius: 4.5; color: nodeColor(modelData.s) }
                                        Text { text: modelData.n; color: "#e6edf3"; font.pixelSize: 12 }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Text { text: "생성된 질문 (" + detail.questionList.length + "개)"; color: "#8b949e"; font.pixelSize: 12; font.bold: true }

        ListView {
            Layout.fillWidth: true
            Layout.fillHeight: true
            clip: true
            spacing: 11
            model: detail.questionList
            delegate: Rectangle {
                required property var modelData
                width: ListView.view.width
                color: "#161b22"; border.color: "#30363d"; radius: 11
                implicitHeight: rcol.implicitHeight + 28
                ColumnLayout {
                    id: rcol
                    x: 16; y: 14; width: parent.width - 32
                    spacing: 5
                    Text { text: modelData.type; color: "#2dd4bf"; font.pixelSize: 11; font.bold: true }
                    Text {
                        text: modelData.question; color: "#e6edf3"; font.pixelSize: 13
                        wrapMode: Text.WordWrap; Layout.fillWidth: true
                    }
                }
            }
        }
    }
}
