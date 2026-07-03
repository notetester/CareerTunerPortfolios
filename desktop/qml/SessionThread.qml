import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import CareerTuner

// 세션 대화 스레드 — CC Desktop 의 대화 타임라인 문법.
// session.thread(질문→답변→채점카드→꼬리질문) + agent-steps 접힌 행을 그린다.
Item {
    id: root

    ListView {
        id: list
        anchors.fill: parent
        anchors.leftMargin: Math.max(24, (parent.width - 760) / 2)
        anchors.rightMargin: Math.max(24, (parent.width - 760) / 2)
        clip: true
        spacing: 0
        model: session.thread

        // 새 아이템 추가되면 맨 아래로
        onCountChanged: Qt.callLater(() => list.positionViewAtEnd())

        header: Column {
            width: list.width
            spacing: 10
            topPadding: 20
            bottomPadding: 8

            // 세션 시스템 라인
            RowLayout {
                width: parent.width
                spacing: 8
                Rectangle { Layout.fillWidth: true; height: 1; color: Theme.border }
                Text {
                    text: session.title + " · " + session.mode
                    color: Theme.muted; font.pixelSize: 11
                }
                Rectangle { Layout.fillWidth: true; height: 1; color: Theme.border }
            }

            // agent-steps (있을 때만)
            Rectangle {
                visible: session.agentSteps.length > 0
                width: parent.width
                radius: Theme.radius
                color: Theme.surface
                border.color: Theme.border
                height: stepsCol.implicitHeight

                Column {
                    id: stepsCol
                    width: parent.width
                    Repeater {
                        model: session.agentSteps
                        delegate: Column {
                            id: stepItem
                            required property var modelData
                            required property int index
                            property bool expanded: false
                            width: stepsCol.width

                            Rectangle {
                                width: parent.width
                                height: 34
                                color: stepHover.containsMouse ? Theme.hover : "transparent"
                                RowLayout {
                                    anchors.fill: parent
                                    anchors.leftMargin: 14; anchors.rightMargin: 14
                                    spacing: 9
                                    Icon {
                                        name: "chevron"; size: 9; color: Theme.muted
                                        Layout.preferredWidth: 9; Layout.preferredHeight: 9
                                        rotation: stepItem.expanded ? 90 : 0
                                        Behavior on rotation { NumberAnimation { duration: 120 } }
                                    }
                                    Item {
                                        Layout.preferredWidth: 12; Layout.preferredHeight: 12
                                        property bool ok: stepItem.modelData.status === "DONE" || stepItem.modelData.status === "SUCCESS"
                                        Icon { visible: parent.ok; anchors.centerIn: parent; name: "check"; size: 12; color: Theme.good }
                                        Text { visible: !parent.ok; anchors.centerIn: parent; text: "•"; color: Theme.good; font.pixelSize: 12 }
                                    }
                                    Text {
                                        text: stepItem.modelData.action !== "" ? stepItem.modelData.action : stepItem.modelData.agent
                                        color: Theme.text; font.pixelSize: 12; font.bold: true
                                    }
                                    Text {
                                        Layout.fillWidth: true
                                        text: stepItem.modelData.summary
                                        color: Theme.muted; font.pixelSize: 11
                                        elide: Text.ElideRight
                                    }
                                    Text {
                                        visible: stepItem.modelData.elapsedMs > 0
                                        text: (stepItem.modelData.elapsedMs / 1000).toFixed(1) + "s"
                                        color: Theme.muted; font.pixelSize: 10
                                    }
                                }
                                MouseArea {
                                    id: stepHover
                                    anchors.fill: parent
                                    hoverEnabled: true
                                    cursorShape: Qt.PointingHandCursor
                                    onClicked: stepItem.expanded = !stepItem.expanded
                                }
                            }
                            Rectangle {
                                visible: stepItem.expanded && stepItem.modelData.detail !== ""
                                width: parent.width
                                height: detailText.implicitHeight + 18
                                color: Theme.bg
                                Text {
                                    id: detailText
                                    x: 38; y: 9; width: parent.width - 52
                                    text: stepItem.modelData.detail
                                    color: Theme.muted; font.pixelSize: 11
                                    wrapMode: Text.WordWrap
                                }
                            }
                            Rectangle {
                                visible: stepItem.index < session.agentSteps.length - 1
                                width: parent.width; height: 1; color: Theme.border
                            }
                        }
                    }
                }
            }

            // 로딩/빈 상태
            RowLayout {
                visible: session.loading
                width: parent.width
                spacing: 10
                BusyIndicator { running: true; Layout.preferredWidth: 22; Layout.preferredHeight: 22 }
                Text { text: "세션을 불러오는 중…"; color: Theme.muted; font.pixelSize: 12 }
            }
            Rectangle {
                visible: !session.loading && session.thread.length === 0
                width: parent.width
                radius: Theme.radius
                color: Theme.surface
                border.color: Theme.border
                height: emptyCol.implicitHeight + 36
                ColumnLayout {
                    id: emptyCol
                    anchors.centerIn: parent
                    width: parent.width - 48
                    spacing: 10
                    Text {
                        text: "아직 질문이 없습니다"
                        color: Theme.text; font.pixelSize: 14; font.bold: true
                        Layout.alignment: Qt.AlignHCenter
                    }
                    Text {
                        text: "공고·지원건 분석을 반영해 예상 질문을 생성합니다."
                        color: Theme.muted; font.pixelSize: 12
                        Layout.alignment: Qt.AlignHCenter
                    }
                    Rectangle {
                        Layout.alignment: Qt.AlignHCenter
                        width: genLbl.implicitWidth + 28; height: 32; radius: 8
                        gradient: Gradient {
                            GradientStop { position: 0.0; color: Theme.accent2 }
                            GradientStop { position: 1.0; color: Theme.accent }
                        }
                        Text { id: genLbl; anchors.centerIn: parent; text: "예상 질문 생성"; color: "white"; font.pixelSize: 12; font.bold: true }
                        MouseArea {
                            anchors.fill: parent; cursorShape: Qt.PointingHandCursor
                            onClicked: session.generateQuestions()
                        }
                    }
                }
            }
        }

        footer: Item { width: 1; height: 24 }

        delegate: Loader {
            id: itemLoader
            required property var modelData
            width: list.width
            sourceComponent: modelData.kind === "question" ? questionComp
                           : modelData.kind === "answer"   ? answerComp
                           : modelData.kind === "score"    ? scoreComp
                           : scoringComp
            onLoaded: item.data_ = modelData
        }
    }

    // ── 질문 버블 ──
    Component {
        id: questionComp
        Item {
            property var data_: ({})
            width: list.width
            height: qRow.implicitHeight + 22
            RowLayout {
                id: qRow
                width: parent.width
                y: 14
                spacing: 12
                Rectangle {
                    Layout.alignment: Qt.AlignTop
                    width: 30; height: 30; radius: 8
                    color: Theme.accentSoft
                    Icon { anchors.centerIn: parent; name: "spark"; size: 13; color: Theme.accentText }
                }
                ColumnLayout {
                    Layout.fillWidth: true
                    spacing: 5
                    RowLayout {
                        spacing: 8
                        Text { text: "AI 면접관"; color: Theme.muted; font.pixelSize: 11 }
                        Rectangle {
                            height: 18; radius: 9
                            width: qTypeLbl.implicitWidth + 14
                            color: data_.followUp === true ? Theme.accentSoft : Theme.raised
                            border.color: data_.followUp === true ? "transparent" : Theme.border
                            Text {
                                id: qTypeLbl
                                anchors.centerIn: parent
                                text: data_.followUp === true ? "꼬리질문" : (data_.qtype || "질문")
                                color: data_.followUp === true ? Theme.accent : Theme.muted
                                font.pixelSize: 10
                            }
                        }
                    }
                    Text {
                        Layout.fillWidth: true
                        text: data_.text || ""
                        color: Theme.text; font.pixelSize: 13
                        wrapMode: Text.WordWrap
                        lineHeight: 1.4
                    }
                }
            }
        }
    }

    // ── 내 답변 ──
    Component {
        id: answerComp
        Item {
            property var data_: ({})
            width: list.width
            height: aRow.implicitHeight + 18
            RowLayout {
                id: aRow
                width: parent.width
                y: 10
                spacing: 12
                Rectangle {
                    Layout.alignment: Qt.AlignTop
                    width: 30; height: 30; radius: 8
                    color: Theme.raised; border.color: Theme.border
                    Text {
                        anchors.centerIn: parent
                        text: auth.userName.length > 0 ? auth.userName.charAt(0) : "나"
                        color: Theme.text; font.pixelSize: 12
                    }
                }
                ColumnLayout {
                    Layout.fillWidth: true
                    spacing: 5
                    RowLayout {
                        spacing: 8
                        Text { text: "내 답변"; color: Theme.muted; font.pixelSize: 11 }
                        Row {
                            visible: data_.hasAudio === true
                            spacing: 5
                            Icon { name: "mic"; size: 10; color: Theme.muted; anchors.verticalCenter: parent.verticalCenter }
                            Text { text: "음성"; color: Theme.muted; font.pixelSize: 10; anchors.verticalCenter: parent.verticalCenter }
                        }
                    }
                    Rectangle {
                        Layout.fillWidth: true
                        radius: Theme.radius
                        color: Theme.surface; border.color: Theme.border
                        height: ansText.implicitHeight + 24
                        Text {
                            id: ansText
                            x: 14; y: 12; width: parent.width - 28
                            text: data_.text || ""
                            color: Theme.text; font.pixelSize: 13
                            wrapMode: Text.WordWrap
                            lineHeight: 1.45
                        }
                    }
                }
            }
        }
    }

    // ── 채점 카드 ──
    Component {
        id: scoreComp
        Item {
            id: scoreRoot
            property var data_: ({})
            property bool showModel: false
            property bool showImproved: false
            width: list.width
            height: card.implicitHeight + 16

            Rectangle {
                id: card
                width: parent.width
                y: 8
                radius: 12
                color: Theme.surface; border.color: Theme.border
                implicitHeight: cardCol.implicitHeight + 4

                ColumnLayout {
                    id: cardCol
                    width: parent.width
                    spacing: 0

                    // 헤더: 점수 원 + 축
                    RowLayout {
                        Layout.fillWidth: true
                        Layout.margins: 14
                        spacing: 14
                        Rectangle {
                            width: 46; height: 46; radius: 23
                            color: "transparent"
                            border.color: Theme.accent; border.width: 3
                            Text {
                                anchors.centerIn: parent
                                text: scoreRoot.data_.score >= 0 ? scoreRoot.data_.score : "—"
                                color: Theme.accent; font.pixelSize: 15; font.bold: true
                            }
                        }
                        ColumnLayout {
                            spacing: 6
                            Text { text: "답변 평가"; color: Theme.text; font.pixelSize: 13; font.bold: true }
                            RowLayout {
                                spacing: 6
                                Rectangle {
                                    visible: scoreRoot.data_.voiceScore >= 0
                                    height: 20; radius: 10
                                    width: vsRow.implicitWidth + 16
                                    color: Theme.accentSoft
                                    Row {
                                        id: vsRow; anchors.centerIn: parent
                                        spacing: 5
                                        Icon { name: "mic"; size: 10; color: Theme.accent; anchors.verticalCenter: parent.verticalCenter }
                                        Text {
                                            text: "전달력 " + scoreRoot.data_.voiceScore
                                            color: Theme.accent; font.pixelSize: 10
                                            anchors.verticalCenter: parent.verticalCenter
                                        }
                                    }
                                }
                            }
                        }
                        Item { Layout.fillWidth: true }
                    }

                    Rectangle { Layout.fillWidth: true; height: 1; color: Theme.border }

                    // 피드백
                    Text {
                        visible: (scoreRoot.data_.feedback || "") !== ""
                        Layout.fillWidth: true
                        Layout.margins: 14
                        text: scoreRoot.data_.feedback || ""
                        color: Theme.text; font.pixelSize: 12
                        wrapMode: Text.WordWrap
                        lineHeight: 1.5
                    }

                    // 개선 답변 (접기)
                    Rectangle {
                        visible: scoreRoot.showImproved && (scoreRoot.data_.improvedAnswer || "") !== ""
                        Layout.fillWidth: true
                        Layout.leftMargin: 14; Layout.rightMargin: 14; Layout.bottomMargin: 6
                        radius: 8
                        color: Theme.bg
                        height: impText.implicitHeight + 40
                        ColumnLayout {
                            x: 12; y: 10; width: parent.width - 24
                            spacing: 6
                            Row {
                                spacing: 6
                                Icon { name: "zap"; size: 11; color: Theme.good; anchors.verticalCenter: parent.verticalCenter }
                                Text { text: "개선 답변 제안"; color: Theme.good; font.pixelSize: 11; font.bold: true; anchors.verticalCenter: parent.verticalCenter }
                            }
                            Text {
                                id: impText
                                Layout.fillWidth: true
                                text: scoreRoot.data_.improvedAnswer || ""
                                color: Theme.muted; font.pixelSize: 12
                                wrapMode: Text.WordWrap; lineHeight: 1.5
                            }
                        }
                    }

                    // 모범답안 (접기)
                    Rectangle {
                        visible: scoreRoot.showModel && (scoreRoot.data_.modelAnswer || "") !== ""
                        Layout.fillWidth: true
                        Layout.leftMargin: 14; Layout.rightMargin: 14; Layout.bottomMargin: 6
                        radius: 8
                        color: Theme.bg
                        height: modelText.implicitHeight + 40
                        ColumnLayout {
                            x: 12; y: 10; width: parent.width - 24
                            spacing: 6
                            Row {
                                spacing: 6
                                Icon { name: "file"; size: 11; color: Theme.info; anchors.verticalCenter: parent.verticalCenter }
                                Text { text: "모범답안"; color: Theme.info; font.pixelSize: 11; font.bold: true; anchors.verticalCenter: parent.verticalCenter }
                            }
                            Text {
                                id: modelText
                                Layout.fillWidth: true
                                text: scoreRoot.data_.modelAnswer || ""
                                color: Theme.muted; font.pixelSize: 12
                                wrapMode: Text.WordWrap; lineHeight: 1.5
                            }
                        }
                    }

                    // 액션
                    RowLayout {
                        Layout.margins: 12
                        Layout.topMargin: 6
                        spacing: 8
                        Rectangle {
                            width: modelBtnLbl.implicitWidth + 20; height: 28; radius: 7
                            color: Theme.raised; border.color: Theme.border
                            Text {
                                id: modelBtnLbl; anchors.centerIn: parent
                                text: scoreRoot.showModel ? "모범답안 접기" : "모범답안 보기"
                                color: Theme.text; font.pixelSize: 11
                            }
                            MouseArea {
                                anchors.fill: parent; cursorShape: Qt.PointingHandCursor
                                onClicked: {
                                    if ((scoreRoot.data_.modelAnswer || "") === "")
                                        session.requestModelAnswer(scoreRoot.data_.qid)
                                    scoreRoot.showModel = !scoreRoot.showModel
                                }
                            }
                        }
                        Rectangle {
                            visible: (scoreRoot.data_.improvedAnswer || "") !== ""
                            width: impBtnLbl.implicitWidth + 20; height: 28; radius: 7
                            color: Theme.raised; border.color: Theme.border
                            Text {
                                id: impBtnLbl; anchors.centerIn: parent
                                text: scoreRoot.showImproved ? "개선 제안 접기" : "개선 제안 보기"
                                color: Theme.text; font.pixelSize: 11
                            }
                            MouseArea {
                                anchors.fill: parent; cursorShape: Qt.PointingHandCursor
                                onClicked: scoreRoot.showImproved = !scoreRoot.showImproved
                            }
                        }
                        Rectangle {
                            width: fuBtnLbl.implicitWidth + 20; height: 28; radius: 7
                            color: Theme.raised; border.color: Theme.border
                            Text { id: fuBtnLbl; anchors.centerIn: parent; text: "꼬리질문 받기"; color: Theme.text; font.pixelSize: 11 }
                            MouseArea {
                                anchors.fill: parent; cursorShape: Qt.PointingHandCursor
                                onClicked: session.requestFollowUp()
                            }
                        }
                    }
                }
            }
        }
    }

    // ── 채점 중 ──
    Component {
        id: scoringComp
        Item {
            property var data_: ({})
            width: list.width
            height: 46
            Rectangle {
                width: parent.width; y: 8; height: 34; radius: Theme.radius
                color: Theme.surface; border.color: Theme.border
                RowLayout {
                    anchors.fill: parent
                    anchors.leftMargin: 14
                    spacing: 10
                    BusyIndicator { running: true; Layout.preferredWidth: 16; Layout.preferredHeight: 16 }
                    Text { text: "답변 채점 중 — 내용 · 논리 · 직무적합 평가"; color: Theme.muted; font.pixelSize: 12 }
                }
            }
        }
    }
}
