import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import CareerTuner

// 세션 대화 스레드 — CC Desktop 의 대화 타임라인 문법.
// session.thread(질문→답변→채점카드→꼬리질문) + agent-steps 접힌 행을 그린다.
Item {
    id: root

    Dialog {
        id: regenerateQuestionsDialog
        x: Math.round((root.width - width) / 2)
        y: Math.round((root.height - height) / 2)
        width: Math.min(440, root.width - 48)
        modal: true
        focus: true
        title: "예상 질문 다시 생성"
        standardButtons: Dialog.Ok | Dialog.Cancel
        onAccepted: win.startQuestionRegeneration()
        palette.window: Theme.surface
        palette.windowText: Theme.text
        palette.base: Theme.surface
        palette.text: Theme.text
        palette.button: Theme.raised
        palette.buttonText: Theme.text
        background: Rectangle {
            radius: Theme.radius
            color: Theme.surface
            border.color: Theme.border
        }
        contentItem: Text {
            text: "선택한 모델로 기존 미답변 질문을 교체합니다. "
                  + (win.sessionAnswerDraftPending
                     ? "작성 중인 텍스트 답변은 생성 성공 후 초기화됩니다. " : "")
                  + "새 AI 사용으로 별도 차감될 수 있습니다. 계속할까요?"
            color: Theme.text
            font.pixelSize: 12
            wrapMode: Text.WordWrap
        }
    }

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
                visible: !session.loading && !session.threadLoadFailed && session.thread.length === 0
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
                    RowLayout {
                        Layout.alignment: Qt.AlignHCenter
                        spacing: 8
                        Text {
                            text: "AI 모델"
                            color: Theme.muted
                            font.pixelSize: 11
                        }
                        ComboBox {
                            id: generationModelPicker
                            implicitWidth: 184
                            implicitHeight: 30
                            enabled: !session.loading
                            Accessible.name: "질문 생성 AI 모델"
                            property var modelValues: ["AUTO", "CAREERTUNER", "CLAUDE", "OPENAI"]
                            model: ["자동 (추천)", "CareerTuner 자체 모델", "Claude Haiku", "OpenAI GPT"]
                            function syncFromSession() {
                                currentIndex = Math.max(0, modelValues.indexOf(session.questionGenerationModel))
                            }
                            Component.onCompleted: syncFromSession()
                            onActivated: (index) => session.questionGenerationModel = modelValues[index]
                            Connections {
                                target: session
                                function onQuestionGenerationModelChanged() {
                                    generationModelPicker.syncFromSession()
                                }
                            }
                        }
                    }
                    Rectangle {
                        Layout.alignment: Qt.AlignHCenter
                        width: genLbl.implicitWidth + 28; height: 32; radius: 8
                        activeFocusOnTab: visible
                        Accessible.role: Accessible.Button
                        Accessible.name: "예상 질문 생성"
                        border.color: activeFocus ? Theme.accentText : "transparent"
                        border.width: activeFocus ? 2 : 0
                        function generateExpectedQuestions() { session.generateQuestions() }
                        Keys.onPressed: (event) => {
                            if (event.key === Qt.Key_Return || event.key === Qt.Key_Enter || event.key === Qt.Key_Space) {
                                event.accepted = true
                                generateExpectedQuestions()
                            }
                        }
                        gradient: Gradient {
                            GradientStop { position: 0.0; color: Theme.accent2 }
                            GradientStop { position: 1.0; color: Theme.accent }
                        }
                        Text { id: genLbl; anchors.centerIn: parent; text: "예상 질문 생성"; color: "white"; font.pixelSize: 12; font.bold: true }
                        MouseArea {
                            anchors.fill: parent; cursorShape: Qt.PointingHandCursor
                            onClicked: parent.generateExpectedQuestions()
                        }
                    }
                }
            }
            Rectangle {
                visible: !session.loading && !session.threadLoadFailed && session.canRegenerateQuestions
                width: parent.width
                radius: Theme.radius
                color: Theme.surface
                border.color: Theme.border
                height: regenerateCol.implicitHeight + 28
                ColumnLayout {
                    id: regenerateCol
                    anchors.centerIn: parent
                    width: parent.width - 40
                    spacing: 8
                    Text {
                        text: "질문을 다른 모델로 다시 만들어 볼까요?"
                        color: Theme.text; font.pixelSize: 13; font.bold: true
                    }
                    Text {
                        Layout.fillWidth: true
                        text: "답변 전에는 기존 선택을 유지하거나 다른 모델을 골라 미답변 질문을 교체할 수 있습니다."
                        color: Theme.muted; font.pixelSize: 11
                        wrapMode: Text.WordWrap
                    }
                    RowLayout {
                        Layout.fillWidth: true
                        spacing: 8
                        Text { text: "AI 모델"; color: Theme.muted; font.pixelSize: 11 }
                        ComboBox {
                            id: regenerationModelPicker
                            Layout.preferredWidth: 184
                            implicitHeight: 30
                            enabled: !session.loading
                            Accessible.name: "질문 재생성 AI 모델"
                            property var modelValues: ["AUTO", "CAREERTUNER", "CLAUDE", "OPENAI"]
                            model: ["자동 (추천)", "CareerTuner 자체 모델", "Claude Haiku", "OpenAI GPT"]
                            function syncFromSession() {
                                currentIndex = Math.max(0, modelValues.indexOf(session.questionGenerationModel))
                            }
                            Component.onCompleted: syncFromSession()
                            onActivated: (index) => session.questionGenerationModel = modelValues[index]
                            Connections {
                                target: session
                                function onQuestionGenerationModelChanged() {
                                    regenerationModelPicker.syncFromSession()
                                }
                            }
                        }
                        Item { Layout.fillWidth: true }
                        Rectangle {
                            width: regenerateLabel.implicitWidth + 22; height: 32; radius: 8
                            enabled: !win.sessionAnswerMediaPending
                            color: Theme.accentSoft
                            border.color: activeFocus ? Theme.accentText : Theme.accent
                            activeFocusOnTab: visible && enabled
                            Accessible.role: Accessible.Button
                            Accessible.name: "선택 모델로 질문 재생성"
                            Accessible.description: enabled ? "" : "미제출 녹음·영상 작업을 먼저 정리하세요"
                            opacity: enabled ? 1 : 0.45
                            function confirmRegeneration() { regenerateQuestionsDialog.open() }
                            Keys.onPressed: (event) => {
                                if (event.key === Qt.Key_Return || event.key === Qt.Key_Enter || event.key === Qt.Key_Space) {
                                    event.accepted = true
                                    confirmRegeneration()
                                }
                            }
                            Text {
                                id: regenerateLabel
                                anchors.centerIn: parent
                                text: "선택 모델로 질문 재생성"
                                color: Theme.accentText; font.pixelSize: 11; font.bold: true
                            }
                            MouseArea {
                                anchors.fill: parent; cursorShape: Qt.PointingHandCursor
                                enabled: parent.enabled
                                onClicked: parent.confirmRegeneration()
                            }
                        }
                    }
                }
            }
            Rectangle {
                visible: !session.loading && session.threadLoadFailed
                width: parent.width
                radius: Theme.radius
                color: Theme.surface
                border.color: Theme.danger
                height: reloadFailureCol.implicitHeight + 36
                ColumnLayout {
                    id: reloadFailureCol
                    anchors.centerIn: parent
                    width: parent.width - 48
                    spacing: 10
                    Text {
                        text: "세션 질문과 복기 정보를 불러오지 못했습니다"
                        color: Theme.text; font.pixelSize: 14; font.bold: true
                        Layout.alignment: Qt.AlignHCenter
                    }
                    Text {
                        text: "중복 답변을 막기 위해 입력을 잠갔습니다. 다시 불러온 뒤 계속하세요."
                        color: Theme.muted; font.pixelSize: 12
                        Layout.alignment: Qt.AlignHCenter
                    }
                    Rectangle {
                        Layout.alignment: Qt.AlignHCenter
                        width: retryLoadLabel.implicitWidth + 28; height: 32; radius: 8
                        color: Theme.raised
                        border.color: activeFocus ? Theme.accent : Theme.border
                        activeFocusOnTab: true
                        Accessible.role: Accessible.Button
                        Accessible.name: "면접 세션 다시 불러오기"
                        Keys.onPressed: (event) => {
                            if (event.key === Qt.Key_Return || event.key === Qt.Key_Enter || event.key === Qt.Key_Space) {
                                event.accepted = true
                                session.retryLoadThread()
                            }
                        }
                        Text { id: retryLoadLabel; anchors.centerIn: parent; text: "다시 불러오기"; color: Theme.text; font.pixelSize: 12; font.bold: true }
                        MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: session.retryLoadThread() }
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
                        Row {
                            visible: data_.hasVideo === true
                            spacing: 5
                            Icon { name: "video"; size: 10; color: Theme.muted; anchors.verticalCenter: parent.verticalCenter }
                            Text { text: "영상"; color: Theme.muted; font.pixelSize: 10; anchors.verticalCenter: parent.verticalCenter }
                        }
                        Text {
                            visible: data_.pending === true
                            text: "저장 중…"
                            color: Theme.accentText
                            font.pixelSize: 10
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
            property bool modelAnswerPending_: session.modelAnswerPendingQuestionIds.indexOf(data_.qid) >= 0
            property bool followUpPending_: session.followUpPendingQuestionIds.indexOf(data_.qid) >= 0
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
                                Rectangle {
                                    visible: scoreRoot.data_.visualScore >= 0
                                    height: 20; radius: 10
                                    width: visualRow.implicitWidth + 16
                                    color: Theme.accentSoft
                                    Row {
                                        id: visualRow; anchors.centerIn: parent
                                        spacing: 5
                                        Icon { name: "video"; size: 10; color: Theme.accent; anchors.verticalCenter: parent.verticalCenter }
                                        Text {
                                            text: "비언어 " + scoreRoot.data_.visualScore
                                            color: Theme.accent; font.pixelSize: 10
                                            anchors.verticalCenter: parent.verticalCenter
                                        }
                                    }
                                }
                                Rectangle {
                                    visible: scoreRoot.data_.videoScore >= 0
                                    height: 20; radius: 10
                                    width: videoTotalRow.implicitWidth + 16
                                    color: Theme.raised
                                    Row {
                                        id: videoTotalRow; anchors.centerIn: parent
                                        spacing: 5
                                        Text {
                                            text: "영상 종합 " + scoreRoot.data_.videoScore
                                            color: Theme.text; font.pixelSize: 10
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
                            color: Theme.raised
                            border.color: activeFocus ? Theme.accent : Theme.border
                            activeFocusOnTab: !scoreRoot.modelAnswerPending_
                            Accessible.role: Accessible.Button
                            Accessible.name: scoreRoot.modelAnswerPending_
                                             ? "모범답안 생성 중" : "모범답안 보기"
                            opacity: scoreRoot.modelAnswerPending_ ? 0.55 : 1
                            function activateModelAnswer() {
                                if (scoreRoot.modelAnswerPending_) return
                                if ((scoreRoot.data_.modelAnswer || "") === "")
                                    session.requestModelAnswer(scoreRoot.data_.qid)
                                scoreRoot.showModel = !scoreRoot.showModel
                            }
                            Keys.onPressed: (event) => {
                                if (event.key === Qt.Key_Return || event.key === Qt.Key_Enter || event.key === Qt.Key_Space) {
                                    event.accepted = true
                                    activateModelAnswer()
                                }
                            }
                            Text {
                                id: modelBtnLbl; anchors.centerIn: parent
                                text: scoreRoot.modelAnswerPending_
                                      ? "생성 중…"
                                      : (scoreRoot.showModel ? "모범답안 접기" : "모범답안 보기")
                                color: Theme.text; font.pixelSize: 11
                            }
                            MouseArea {
                                anchors.fill: parent; cursorShape: Qt.PointingHandCursor
                                enabled: !scoreRoot.modelAnswerPending_
                                onClicked: parent.activateModelAnswer()
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
                            visible: session.mode === "PRESSURE" || session.mode === "압박 면접"
                            width: fuBtnLbl.implicitWidth + 20; height: 28; radius: 7
                            color: Theme.raised
                            border.color: activeFocus ? Theme.accent : Theme.border
                            activeFocusOnTab: visible && !scoreRoot.followUpPending_
                            Accessible.role: Accessible.Button
                            Accessible.name: scoreRoot.followUpPending_
                                             ? "꼬리질문 생성 중" : "이 답변의 꼬리질문 받기"
                            opacity: scoreRoot.followUpPending_ ? 0.55 : 1
                            function activateFollowUp() {
                                if (!scoreRoot.followUpPending_)
                                    session.requestFollowUp(scoreRoot.data_.qid)
                            }
                            Keys.onPressed: (event) => {
                                if (event.key === Qt.Key_Return || event.key === Qt.Key_Enter || event.key === Qt.Key_Space) {
                                    event.accepted = true
                                    activateFollowUp()
                                }
                            }
                            Text {
                                id: fuBtnLbl; anchors.centerIn: parent
                                text: scoreRoot.followUpPending_ ? "생성 중…" : "꼬리질문 받기"
                                color: Theme.text; font.pixelSize: 11
                            }
                            MouseArea {
                                anchors.fill: parent; cursorShape: Qt.PointingHandCursor
                                enabled: !scoreRoot.followUpPending_
                                onClicked: parent.activateFollowUp()
                            }
                        }
                        Rectangle {
                            visible: scoreRoot.data_.answerId > 0
                                     && scoreRoot.data_.hasAudioOriginal === true
                            width: audioDeleteLbl.implicitWidth + 20; height: 28; radius: 7
                            color: Theme.raised; border.color: Theme.border
                            Text {
                                id: audioDeleteLbl; anchors.centerIn: parent
                                text: "음성 원본 삭제"; color: Theme.danger; font.pixelSize: 11
                            }
                            MouseArea {
                                anchors.fill: parent; cursorShape: Qt.PointingHandCursor
                                onClicked: session.deleteAnswerMedia(scoreRoot.data_.answerId, "AUDIO")
                            }
                        }
                        Rectangle {
                            visible: scoreRoot.data_.answerId > 0
                                     && scoreRoot.data_.hasVideoOriginal === true
                            width: videoDeleteLbl.implicitWidth + 20; height: 28; radius: 7
                            color: Theme.raised; border.color: Theme.border
                            Text {
                                id: videoDeleteLbl; anchors.centerIn: parent
                                text: "영상 원본 삭제"; color: Theme.danger; font.pixelSize: 11
                            }
                            MouseArea {
                                anchors.fill: parent; cursorShape: Qt.PointingHandCursor
                                onClicked: session.deleteAnswerMedia(scoreRoot.data_.answerId, "VIDEO")
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
