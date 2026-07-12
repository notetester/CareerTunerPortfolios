import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import CareerTuner

// 면접 리포트 — GET /sessions/{id}/report 실데이터 + 로컬 저장.
Item {
    id: root

    Flickable {
        anchors.fill: parent
        contentHeight: content.implicitHeight + 60
        clip: true

        ColumnLayout {
            id: content
            width: Math.min(parent.width - 48, 760)
            anchors.horizontalCenter: parent.horizontalCenter
            spacing: 18

            Item { height: 8 }

            // 빈 상태
            Text {
                visible: session.reportError.length === 0
                    && !session.report.totalScore && session.report.totalScore !== 0
                text: "리포트가 아직 없습니다 — 답변을 제출하면 채점 결과가 쌓입니다."
                color: Theme.muted; font.pixelSize: 13
                Layout.topMargin: 30
                Layout.alignment: Qt.AlignHCenter
            }

            Rectangle {
                visible: session.reportError.length > 0
                Layout.fillWidth: true
                radius: Theme.radius
                color: Theme.surface; border.color: Theme.danger
                implicitHeight: reportErrorRow.implicitHeight + 24
                RowLayout {
                    id: reportErrorRow
                    x: 14; y: 12; width: parent.width - 28
                    Text {
                        Layout.fillWidth: true
                        text: session.reportError
                        color: Theme.danger; font.pixelSize: 12; wrapMode: Text.WordWrap
                    }
                    Button {
                        text: "다시 시도"
                        enabled: !session.reportLoading
                        Accessible.name: "면접 리포트 다시 생성"
                        onClicked: session.loadReport()
                    }
                }
            }

            // 헤더: 종합 점수
            RowLayout {
                visible: session.report.totalScore !== undefined
                Layout.fillWidth: true
                spacing: 22

                Rectangle {
                    width: 104; height: 104; radius: 52
                    color: "transparent"
                    border.color: Theme.accent; border.width: 5
                    ColumnLayout {
                        anchors.centerIn: parent
                        spacing: 0
                        Text {
                            Layout.alignment: Qt.AlignHCenter
                            text: session.report.totalScore !== undefined ? session.report.totalScore : "—"
                            color: Theme.text; font.pixelSize: 25; font.bold: true
                        }
                        Text { Layout.alignment: Qt.AlignHCenter; text: "종합 점수"; color: Theme.muted; font.pixelSize: 10 }
                    }
                }

                ColumnLayout {
                    Layout.fillWidth: true
                    spacing: 8
                    Text {
                        text: session.title + " 리포트"
                        color: Theme.text; font.pixelSize: 16; font.bold: true
                    }
                    Text {
                        text: (session.report.questionCount !== undefined ? session.report.questionCount + "문항" : "")
                              + (session.report.durationLabel ? " · " + session.report.durationLabel : "")
                              + (session.report.previousScore ? " · 직전 " + session.report.previousScore + "점" : "")
                        color: Theme.muted; font.pixelSize: 12
                    }
                    Flow {
                        Layout.fillWidth: true
                        spacing: 6
                        Repeater {
                            model: session.report.categories || []
                            delegate: Rectangle {
                                required property var modelData
                                height: 22; radius: 11
                                width: catLbl.implicitWidth + 18
                                color: Theme.raised; border.color: Theme.border
                                Text {
                                    id: catLbl; anchors.centerIn: parent
                                    text: modelData.label + " " + modelData.score
                                    color: modelData.score >= 80 ? Theme.good : Theme.muted
                                    font.pixelSize: 11
                                }
                            }
                        }
                    }
                }
            }

            // 총평
            Rectangle {
                visible: (session.report.summaryFeedback || []).length > 0
                Layout.fillWidth: true
                radius: Theme.radius
                color: Theme.surface; border.color: Theme.border
                implicitHeight: fbCol.implicitHeight + 28
                ColumnLayout {
                    id: fbCol
                    x: 16; y: 14; width: parent.width - 32
                    spacing: 7
                    Text { text: "총평"; color: Theme.text; font.pixelSize: 12; font.bold: true }
                    Repeater {
                        model: session.report.summaryFeedback || []
                        delegate: Text {
                            required property string modelData
                            Layout.fillWidth: true
                            text: "· " + modelData
                            color: Theme.muted; font.pixelSize: 12
                            wrapMode: Text.WordWrap; lineHeight: 1.4
                        }
                    }
                }
            }

            // 질문별 평가
            Rectangle {
                visible: (session.report.questionScores || []).length > 0
                Layout.fillWidth: true
                radius: Theme.radius
                color: Theme.surface; border.color: Theme.border
                implicitHeight: qsCol.implicitHeight + 8

                Column {
                    id: qsCol
                    width: parent.width

                    RowLayout {
                        width: parent.width - 32; x: 16; height: 38
                        Text { text: "질문별 평가"; color: Theme.text; font.pixelSize: 12; font.bold: true }
                    }
                    Repeater {
                        model: session.report.questionScores || []
                        delegate: Column {
                            required property var modelData
                            width: qsCol.width
                            Rectangle { width: parent.width; height: 1; color: Theme.border }
                            RowLayout {
                                width: parent.width - 32; x: 16
                                height: Math.max(44, qsQ.implicitHeight + 20)
                                spacing: 12
                                Text {
                                    text: modelData.score !== null && modelData.score !== undefined ? modelData.score : "—"
                                    color: modelData.score >= 80 ? Theme.good
                                         : modelData.score >= 60 ? Theme.text : Theme.warn
                                    font.pixelSize: 14; font.bold: true
                                    Layout.preferredWidth: 30
                                }
                                ColumnLayout {
                                    Layout.fillWidth: true
                                    spacing: 2
                                    Text {
                                        id: qsQ
                                        Layout.fillWidth: true
                                        text: "Q" + modelData.order + ". " + modelData.question
                                        color: Theme.text; font.pixelSize: 12
                                        wrapMode: Text.WordWrap
                                    }
                                    Text {
                                        visible: (modelData.feedback || "") !== ""
                                        Layout.fillWidth: true
                                        text: modelData.feedback || ""
                                        color: Theme.muted; font.pixelSize: 11
                                        wrapMode: Text.WordWrap
                                        maximumLineCount: 2; elide: Text.ElideRight
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 저장 스트립
            Rectangle {
                Layout.fillWidth: true
                radius: Theme.radiusL
                color: "transparent"
                border.color: Theme.border
                implicitHeight: saveCol.implicitHeight + 30

                ColumnLayout {
                    id: saveCol
                    x: 16; y: 15; width: parent.width - 32
                    spacing: 10

                    RowLayout {
                        spacing: 8
                        Rectangle {
                            width: mdRow.implicitWidth + 24; height: 32; radius: 8
                            gradient: Gradient {
                                GradientStop { position: 0.0; color: Theme.accent2 }
                                GradientStop { position: 1.0; color: Theme.accent }
                            }
                            Row {
                                id: mdRow; anchors.centerIn: parent; spacing: 6
                                Icon { name: "download"; size: 12; color: "white"; anchors.verticalCenter: parent.verticalCenter }
                                Text { text: "리포트 저장 (.md)"; color: "white"; font.pixelSize: 12; font.bold: true; anchors.verticalCenter: parent.verticalCenter }
                            }
                            MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: session.exportReport("md") }
                        }
                        Rectangle {
                            width: htmlLbl.implicitWidth + 24; height: 32; radius: 8
                            color: Theme.raised; border.color: Theme.border
                            Text { id: htmlLbl; anchors.centerIn: parent; text: "HTML로 저장"; color: Theme.text; font.pixelSize: 12 }
                            MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: session.exportReport("html") }
                        }
                        Rectangle {
                            width: allRow.implicitWidth + 24; height: 32; radius: 8
                            color: Theme.raised; border.color: Theme.border
                            Row {
                                id: allRow; anchors.centerIn: parent; spacing: 6
                                Icon { name: "box"; size: 12; color: Theme.text; anchors.verticalCenter: parent.verticalCenter }
                                Text { text: "세션 자료 전부 내보내기"; color: Theme.text; font.pixelSize: 12; anchors.verticalCenter: parent.verticalCenter }
                            }
                            MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: session.exportAll() }
                        }
                        Rectangle {
                            visible: appSettings.autoSave
                            height: 22; radius: 11
                            width: autoLbl.implicitWidth + 16
                            color: Theme.accentSoft
                            Text { id: autoLbl; anchors.centerIn: parent; text: "자동 저장 켜짐"; color: Theme.good; font.pixelSize: 10 }
                        }
                    }
                    Text {
                        Layout.fillWidth: true
                        text: "저장 위치: " + appSettings.saveDir + " — 리포트 · 녹음 · 회사/직무분석 문서가 세션 폴더로 정리됩니다"
                        color: Theme.muted; font.pixelSize: 11
                        wrapMode: Text.WordWrap
                    }
                }
            }
        }
    }
}
