import QtQuick
import QtQuick.Controls
import QtQuick.Dialogs
import QtQuick.Layouts
import CareerTuner

Item {
    id: root
    property string selectedKind: "CHAT"
    property string selectedRoomType: "GROUP"
    property string selectedShareMode: "TEMPORARY"

    function firstLetter(name) {
        const value = String(name || "?")
        return value.length > 0 ? value.charAt(0) : "?"
    }

    function relationLabel(status) {
        if (status === "FRIEND") return "친구"
        if (status === "REQUESTED") return "요청됨"
        if (status === "PENDING_INCOMING") return "수락"
        return "추가"
    }

    function sizeText(bytes) {
        const n = Number(bytes || 0)
        if (n > 1024 * 1024) return (n / 1024 / 1024).toFixed(1) + " MB"
        if (n > 1024) return Math.round(n / 1024) + " KB"
        return n + " B"
    }

    function roomTypeLabel(type) {
        if (type === "PUBLIC") return "공개"
        if (type === "PRIVATE") return "비공개"
        if (type === "DIRECT") return "1:1"
        return "그룹"
    }

    function shareModeLabel(mode) {
        if (mode === "CLOUD") return "클라우드"
        if (mode === "LOCAL") return "로컬"
        return "임시"
    }

    function availabilityLabel(value) {
        if (value === "EXPIRED") return "만료"
        if (value === "PLAN_INACTIVE") return "플랜 중지"
        if (value === "LOCAL_ONLY") return "로컬"
        return "저장"
    }

    Component.onCompleted: collaboration.refresh()

    Connections {
        target: collaboration
        function onMessageSent(conversationId, content, postingIdsText) {
            if (collaboration.currentConversationId !== conversationId) return
            if (messageInput.text === content) messageInput.clear()
            if (postingIdsInput.text === postingIdsText) postingIdsInput.clear()
        }
    }

    FileDialog {
        id: attachDialog
        title: "첨부 파일 선택"
        onAccepted: collaboration.uploadAttachment(selectedFile)
    }

    RowLayout {
        anchors.fill: parent
        spacing: 0

        Rectangle {
            Layout.preferredWidth: 310
            Layout.fillHeight: true
            color: Theme.surface
            border.color: Theme.border

            ColumnLayout {
                anchors.fill: parent
                anchors.margins: 12
                spacing: 10

                RowLayout {
                    Layout.fillWidth: true
                    Text { text: "친구"; color: Theme.text; font.pixelSize: 14; font.bold: true }
                    Item { Layout.fillWidth: true }
                    Rectangle {
                        width: 30; height: 30; radius: 8
                        color: Theme.raised; border.color: Theme.border
                        Text { anchors.centerIn: parent; text: "↻"; color: Theme.text; font.pixelSize: 14 }
                        MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: collaboration.refresh() }
                    }
                }

                TextField {
                    id: searchBox
                    Layout.fillWidth: true
                    height: 36
                    placeholderText: "이름 또는 이메일"
                    color: Theme.text
                    placeholderTextColor: Theme.muted
                    background: Rectangle { radius: 8; color: Theme.raised; border.color: Theme.border }
                    onTextChanged: searchDelay.restart()
                    onAccepted: collaboration.searchUsers(text)
                }

                Timer {
                    id: searchDelay
                    interval: 350
                    repeat: false
                    onTriggered: collaboration.searchUsers(searchBox.text)
                }

                ScrollView {
                    Layout.fillWidth: true
                    Layout.preferredHeight: Math.min(180, Math.max(76, searchResultsCol.implicitHeight + 8))
                    clip: true
                    ColumnLayout {
                        id: searchResultsCol
                        width: parent.width
                        spacing: 6
                        Repeater {
                            model: collaboration.searchResults
                            delegate: Rectangle {
                                property var user: modelData
                                Layout.fillWidth: true
                                height: 48
                                radius: 8
                                color: Theme.raised
                                border.color: Theme.border
                                RowLayout {
                                    anchors.fill: parent
                                    anchors.leftMargin: 10; anchors.rightMargin: 8
                                    spacing: 8
                                    Rectangle {
                                        width: 28; height: 28; radius: 14
                                        color: Theme.accentSoft
                                        Text { anchors.centerIn: parent; text: firstLetter(user["name"]); color: Theme.text; font.pixelSize: 11; font.bold: true }
                                    }
                                    ColumnLayout {
                                        Layout.fillWidth: true
                                        spacing: 1
                                        Text { text: user["name"]; color: Theme.text; font.pixelSize: 12; font.bold: true; elide: Text.ElideRight; Layout.fillWidth: true }
                                        Text { text: user["email"]; color: Theme.muted; font.pixelSize: 10; elide: Text.ElideRight; Layout.fillWidth: true }
                                    }
                                    Rectangle {
                                        width: actionText.implicitWidth + 16
                                        height: 26
                                        radius: 7
                                        color: user["relationStatus"] === "FRIEND" || user["relationStatus"] === "REQUESTED" ? Theme.hover : Theme.accent
                                        opacity: user["relationStatus"] === "FRIEND" || user["relationStatus"] === "REQUESTED" ? 0.65 : 1
                                        Text { id: actionText; anchors.centerIn: parent; text: relationLabel(user["relationStatus"]); color: "white"; font.pixelSize: 10; font.bold: true }
                                        MouseArea {
                                            anchors.fill: parent
                                            cursorShape: Qt.PointingHandCursor
                                            enabled: user["relationStatus"] === "NONE" || user["relationStatus"] === "PENDING_INCOMING"
                                            onClicked: collaboration.sendFriendRequest(user["id"])
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Text { text: "받은 요청"; color: Theme.muted; font.pixelSize: 10; font.bold: true; Layout.leftMargin: 4 }
                ScrollView {
                    Layout.fillWidth: true
                    Layout.preferredHeight: Math.min(130, Math.max(42, incomingCol.implicitHeight + 8))
                    clip: true
                    ColumnLayout {
                        id: incomingCol
                        width: parent.width
                        spacing: 6
                        Repeater {
                            model: collaboration.incomingRequests
                            delegate: Rectangle {
                                property var req: modelData
                                property var user: req["user"]
                                Layout.fillWidth: true
                                height: 44
                                radius: 8
                                color: Theme.raised
                                RowLayout {
                                    anchors.fill: parent
                                    anchors.leftMargin: 10; anchors.rightMargin: 8
                                    Text { text: firstLetter(user["name"]); color: Theme.text; font.bold: true; font.pixelSize: 12 }
                                    Text { text: user["name"]; color: Theme.text; font.pixelSize: 12; Layout.fillWidth: true; elide: Text.ElideRight }
                                    Rectangle {
                                        width: 42; height: 24; radius: 7; color: Theme.good
                                        Text { anchors.centerIn: parent; text: "수락"; color: "white"; font.pixelSize: 10; font.bold: true }
                                        MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: collaboration.acceptRequest(req["id"]) }
                                    }
                                    Rectangle {
                                        width: 42; height: 24; radius: 7; color: Theme.hover; border.color: Theme.border
                                        Text { anchors.centerIn: parent; text: "거절"; color: Theme.muted; font.pixelSize: 10 }
                                        MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: collaboration.declineRequest(req["id"]) }
                                    }
                                }
                            }
                        }
                    }
                }

                Text { text: "친구 목록"; color: Theme.muted; font.pixelSize: 10; font.bold: true; Layout.leftMargin: 4 }
                ScrollView {
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    clip: true
                    ColumnLayout {
                        width: parent.width
                        spacing: 6
                        Repeater {
                            model: collaboration.friends
                            delegate: Rectangle {
                                property var user: modelData
                                Layout.fillWidth: true
                                height: 50
                                radius: 8
                                color: friendHover.containsMouse ? Theme.hover : "transparent"
                                border.color: Theme.border
                                RowLayout {
                                    anchors.fill: parent
                                    anchors.leftMargin: 10; anchors.rightMargin: 8
                                    spacing: 8
                                    Rectangle {
                                        width: 30; height: 30; radius: 15; color: Theme.accentSoft
                                        Text { anchors.centerIn: parent; text: firstLetter(user["name"]); color: Theme.text; font.pixelSize: 12; font.bold: true }
                                    }
                                    ColumnLayout {
                                        Layout.fillWidth: true
                                        spacing: 1
                                        Text { text: user["name"]; color: Theme.text; font.pixelSize: 12; font.bold: true; elide: Text.ElideRight; Layout.fillWidth: true }
                                        Text { text: user["email"]; color: Theme.muted; font.pixelSize: 10; elide: Text.ElideRight; Layout.fillWidth: true }
                                    }
                                    Rectangle {
                                        visible: collaboration.currentConversationId > 0 && collaboration.currentConversationType !== "DIRECT"
                                        width: 42; height: 24; radius: 7
                                        color: Theme.accent
                                        Text { anchors.centerIn: parent; text: "초대"; color: "white"; font.pixelSize: 10; font.bold: true }
                                    }
                                    Text { text: "›"; color: Theme.muted; font.pixelSize: 18 }
                                }
                                MouseArea {
                                    id: friendHover
                                    anchors.fill: parent
                                    hoverEnabled: true
                                    cursorShape: Qt.PointingHandCursor
                                    onClicked: function(mouse) {
                                        if (collaboration.currentConversationId > 0
                                                && collaboration.currentConversationType !== "DIRECT"
                                                && mouse.x > width - 80) {
                                            collaboration.inviteFriendToCurrentRoom(user["id"])
                                        } else {
                                            collaboration.openConversation(user["id"], user["name"])
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Rectangle {
            Layout.preferredWidth: 300
            Layout.fillHeight: true
            color: Theme.bg
            border.color: Theme.border

            ColumnLayout {
                anchors.fill: parent
                anchors.margins: 12
                spacing: 8
                Text { text: "대화방"; color: Theme.text; font.pixelSize: 14; font.bold: true }

                Row {
                    spacing: 4
                    Repeater {
                        model: [
                            { label: "그룹", type: "GROUP" },
                            { label: "공개", type: "PUBLIC" },
                            { label: "비공개", type: "PRIVATE" }
                        ]
                        delegate: Rectangle {
                            required property var modelData
                            width: 54; height: 28; radius: 7
                            color: root.selectedRoomType === modelData.type ? Theme.accent : Theme.surface
                            border.color: root.selectedRoomType === modelData.type ? Theme.accent : Theme.border
                            Text { anchors.centerIn: parent; text: modelData.label; color: root.selectedRoomType === modelData.type ? "white" : Theme.text; font.pixelSize: 10; font.bold: true }
                            MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: root.selectedRoomType = modelData.type }
                        }
                    }
                }

                TextField {
                    id: roomTitleInput
                    Layout.fillWidth: true
                    height: 32
                    placeholderText: "새 채팅방 이름"
                    color: Theme.text
                    placeholderTextColor: Theme.muted
                    background: Rectangle { radius: 8; color: Theme.surface; border.color: Theme.border }
                }

                RowLayout {
                    Layout.fillWidth: true
                    spacing: 6
                    TextField {
                        id: roomPasswordInput
                        Layout.fillWidth: true
                        height: 30
                        visible: root.selectedRoomType === "PRIVATE"
                        echoMode: TextInput.Password
                        placeholderText: "비밀번호"
                        color: Theme.text
                        placeholderTextColor: Theme.muted
                        background: Rectangle { radius: 8; color: Theme.surface; border.color: Theme.border }
                    }
                    Rectangle {
                        width: 58; height: 30; radius: 8
                        color: Theme.accent
                        Text { anchors.centerIn: parent; text: "개설"; color: "white"; font.pixelSize: 11; font.bold: true }
                        MouseArea {
                            anchors.fill: parent
                            cursorShape: Qt.PointingHandCursor
                            onClicked: {
                                collaboration.createRoom(root.selectedRoomType, roomTitleInput.text, roomPasswordInput.text)
                                roomTitleInput.clear()
                                roomPasswordInput.clear()
                            }
                        }
                    }
                }

                TextField {
                    id: roomSearchInput
                    Layout.fillWidth: true
                    height: 30
                    placeholderText: "공개/비공개 방 찾기"
                    color: Theme.text
                    placeholderTextColor: Theme.muted
                    background: Rectangle { radius: 8; color: Theme.surface; border.color: Theme.border }
                    onTextChanged: roomSearchDelay.restart()
                    onAccepted: collaboration.discoverRooms(text)
                }

                Timer {
                    id: roomSearchDelay
                    interval: 350
                    repeat: false
                    onTriggered: collaboration.discoverRooms(roomSearchInput.text)
                }

                ScrollView {
                    Layout.fillWidth: true
                    Layout.preferredHeight: Math.min(150, Math.max(48, discoverCol.implicitHeight + 8))
                    clip: true
                    ColumnLayout {
                        id: discoverCol
                        width: parent.width
                        spacing: 6
                        Repeater {
                            model: collaboration.discoverableRooms
                            delegate: Rectangle {
                                property var room: modelData
                                Layout.fillWidth: true
                                height: 46
                                radius: 8
                                color: Theme.surface
                                border.color: Theme.border
                                RowLayout {
                                    anchors.fill: parent
                                    anchors.leftMargin: 10; anchors.rightMargin: 8
                                    spacing: 6
                                    Text { text: room["locked"] ? "잠금" : roomTypeLabel(room["type"]); color: Theme.muted; font.pixelSize: 10; font.bold: true }
                                    ColumnLayout {
                                        Layout.fillWidth: true
                                        spacing: 1
                                        Text { text: room["displayName"]; color: Theme.text; font.pixelSize: 11; font.bold: true; elide: Text.ElideRight; Layout.fillWidth: true }
                                        Text { text: room["memberCount"] + "명"; color: Theme.muted; font.pixelSize: 9 }
                                    }
                                    Rectangle {
                                        width: 42; height: 24; radius: 7
                                        color: Theme.accent
                                        Text { anchors.centerIn: parent; text: "참가"; color: "white"; font.pixelSize: 10; font.bold: true }
                                        MouseArea {
                                            anchors.fill: parent
                                            cursorShape: Qt.PointingHandCursor
                                            onClicked: collaboration.joinRoom(room["id"], room["locked"] ? roomJoinPassword.text : "")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                TextField {
                    id: roomJoinPassword
                    Layout.fillWidth: true
                    height: 28
                    echoMode: TextInput.Password
                    placeholderText: "비공개 방 참가 비밀번호"
                    color: Theme.text
                    placeholderTextColor: Theme.muted
                    background: Rectangle { radius: 8; color: Theme.surface; border.color: Theme.border }
                }

                Text { text: "참여 중"; color: Theme.muted; font.pixelSize: 10; font.bold: true; Layout.leftMargin: 4 }
                ScrollView {
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    clip: true
                    ColumnLayout {
                        width: parent.width
                        spacing: 6
                        Repeater {
                            model: collaboration.conversations
                            delegate: Rectangle {
                                property var convo: modelData
                                Layout.fillWidth: true
                                height: 62
                                radius: 8
                                color: collaboration.currentConversationId === convo["id"] ? Theme.accentSoft : convoHover.containsMouse ? Theme.hover : Theme.surface
                                border.color: Theme.border
                                RowLayout {
                                    anchors.fill: parent
                                    anchors.leftMargin: 10; anchors.rightMargin: 10
                                    spacing: 8
                                    Rectangle {
                                        width: 34; height: 34; radius: 17
                                        color: Theme.raised; border.color: Theme.border
                                        Text { anchors.centerIn: parent; text: firstLetter(convo["peerName"]); color: Theme.text; font.pixelSize: 13; font.bold: true }
                                    }
                                    ColumnLayout {
                                        Layout.fillWidth: true
                                        spacing: 2
                                        RowLayout {
                                            Layout.fillWidth: true
                                            Text { text: convo["peerName"]; color: Theme.text; font.pixelSize: 12; font.bold: true; elide: Text.ElideRight; Layout.fillWidth: true }
                                            Icon {
                                                visible: convo["muted"] === true
                                                name: "bell-off"; size: 10; color: Theme.muted
                                                Layout.preferredWidth: 10; Layout.preferredHeight: 10
                                            }
                                            Text { text: roomTypeLabel(convo["type"]); color: Theme.muted; font.pixelSize: 9 }
                                            Rectangle {
                                                visible: Number(convo["unreadCount"]) > 0
                                                width: unreadText.implicitWidth + 12; height: 18; radius: 9
                                                color: Theme.accent
                                                Text { id: unreadText; anchors.centerIn: parent; text: convo["unreadCount"]; color: "white"; font.pixelSize: 10; font.bold: true }
                                            }
                                        }
                                        Text {
                                            text: convo["latestPreview"] || "대화를 시작하세요"
                                            color: Theme.muted; font.pixelSize: 10
                                            elide: Text.ElideRight; Layout.fillWidth: true
                                        }
                                    }
                                }
                                MouseArea {
                                    id: convoHover
                                    anchors.fill: parent
                                    hoverEnabled: true
                                    cursorShape: Qt.PointingHandCursor
                                    onClicked: collaboration.openConversationById(
                                                   convo["id"], convo["peerName"], convo["type"],
                                                   convo["muted"] === true)
                                }
                            }
                        }
                    }
                }
            }
        }

        Rectangle {
            Layout.fillWidth: true
            Layout.fillHeight: true
            color: Theme.bg

            ColumnLayout {
                anchors.fill: parent
                spacing: 0

                Rectangle {
                    Layout.fillWidth: true
                    height: 54
                    color: Theme.surface
                    border.color: Theme.border
                    RowLayout {
                        anchors.fill: parent
                        anchors.leftMargin: 16; anchors.rightMargin: 16
                        spacing: 10
                        Rectangle {
                            width: 32; height: 32; radius: 16
                            color: Theme.accentSoft
                            Text { anchors.centerIn: parent; text: firstLetter(collaboration.currentPeerName); color: Theme.text; font.pixelSize: 12; font.bold: true }
                        }
                        ColumnLayout {
                            Layout.fillWidth: true
                            spacing: 0
                            Text {
                                text: collaboration.currentConversationId > 0 ? collaboration.currentPeerName : "대화를 선택하세요"
                                color: Theme.text; font.pixelSize: 13; font.bold: true
                            }
                            Text {
                                text: collaboration.currentConversationId > 0 ? roomTypeLabel(collaboration.currentConversationType) + " · 채팅 · 쪽지 · 파일 · 공고" : "친구 목록이나 대화방을 선택하면 thread가 열립니다"
                                color: Theme.muted; font.pixelSize: 10
                            }
                        }
                        // 음소거 토글 — 음소거 방은 키워드/이름 언급 시에만 알림이 온다
                        Rectangle {
                            visible: collaboration.currentConversationId > 0
                            width: 30; height: 30; radius: 8
                            color: collaboration.currentConversationMuted ? Theme.accentSoft : Theme.raised
                            border.color: collaboration.currentConversationMuted ? Theme.accent : Theme.border
                            Icon {
                                anchors.centerIn: parent
                                name: collaboration.currentConversationMuted ? "bell-off" : "bell"
                                size: 14
                                color: collaboration.currentConversationMuted ? Theme.accent : Theme.text
                            }
                            MouseArea {
                                id: muteHover
                                anchors.fill: parent
                                hoverEnabled: true
                                cursorShape: Qt.PointingHandCursor
                                onClicked: collaboration.setConversationMuted(
                                               collaboration.currentConversationId,
                                               !collaboration.currentConversationMuted)
                            }
                            ToolTip.visible: muteHover.containsMouse
                            ToolTip.text: collaboration.currentConversationMuted
                                          ? "음소거 해제"
                                          : "음소거 — 키워드/이름 언급 시에만 알림"
                        }
                        Rectangle {
                            visible: collaboration.currentConversationId > 0
                            width: 30; height: 30; radius: 8
                            color: Theme.raised; border.color: Theme.border
                            Text { anchors.centerIn: parent; text: "↻"; color: Theme.text; font.pixelSize: 14 }
                            MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: collaboration.loadMessages(collaboration.currentConversationId) }
                        }
                    }
                }

                ListView {
                    id: messageList
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    clip: true
                    spacing: 4
                    model: collaboration.messages
                    onCountChanged: Qt.callLater(function() { messageList.positionViewAtEnd() })
                    delegate: Item {
                        property var msg: modelData
                        width: ListView.view.width
                        height: bubble.height + 12
                        RowLayout {
                            anchors.left: parent.left
                            anchors.right: parent.right
                            anchors.top: parent.top
                            anchors.margins: 10
                            layoutDirection: msg["mine"] ? Qt.RightToLeft : Qt.LeftToRight
                            Item { Layout.fillWidth: true }
                            Rectangle {
                                id: bubble
                                property real maxBubbleWidth: Math.min(messageList.width * 0.72, 540)
                                Layout.maximumWidth: maxBubbleWidth
                                width: Math.min(maxBubbleWidth, Math.max(170, bubbleCol.implicitWidth + 24))
                                height: bubbleCol.implicitHeight + 18
                                radius: 10
                                color: msg["mine"] ? Theme.accentSoft : Theme.surface
                                border.color: msg["mine"] ? Theme.accent : Theme.border
                                ColumnLayout {
                                    id: bubbleCol
                                    x: 12; y: 9
                                    width: parent.width - 24
                                    spacing: 6
                                    RowLayout {
                                        Layout.fillWidth: true
                                        Text {
                                            text: msg["kind"] === "NOTE" ? "쪽지" : "채팅"
                                            color: msg["kind"] === "NOTE" ? Theme.warn : Theme.info
                                            font.pixelSize: 10; font.bold: true
                                        }
                                        Text {
                                            text: msg["mine"] ? "나" : msg["sender"]["name"]
                                            color: Theme.muted; font.pixelSize: 10
                                            elide: Text.ElideRight; Layout.fillWidth: true
                                        }
                                    }
                                    Text {
                                        visible: String(msg["content"] || "").length > 0
                                        text: msg["content"]
                                        color: Theme.text
                                        font.pixelSize: 12
                                        wrapMode: Text.WordWrap
                                        Layout.fillWidth: true
                                    }
                                    Repeater {
                                        model: msg["attachments"]
                                        delegate: Rectangle {
                                            property var file: modelData
                                            Layout.fillWidth: true
                                            height: 30
                                            radius: 7
                                            color: Theme.raised
                                            border.color: Theme.border
                                            RowLayout {
                                                anchors.fill: parent
                                                anchors.leftMargin: 8; anchors.rightMargin: 6
                                                spacing: 6
                                                Text { text: "첨부"; color: Theme.muted; font.pixelSize: 9; font.bold: true }
                                                Text { text: file["originalName"]; color: Theme.text; font.pixelSize: 10; elide: Text.ElideRight; Layout.fillWidth: true }
                                                Text { text: shareModeLabel(file["shareMode"]); color: Theme.muted; font.pixelSize: 9 }
                                                Text { text: sizeText(file["sizeBytes"]); color: Theme.muted; font.pixelSize: 9 }
                                                Rectangle {
                                                    width: 38; height: 22; radius: 6
                                                    color: file["availability"] === "AVAILABLE" ? Theme.hover : Theme.surface
                                                    border.color: Theme.border
                                                    opacity: file["availability"] === "AVAILABLE" ? 1 : 0.65
                                                    Text { anchors.centerIn: parent; text: availabilityLabel(file["availability"]); color: Theme.text; font.pixelSize: 9 }
                                                    MouseArea {
                                                        anchors.fill: parent
                                                        enabled: file["availability"] === "AVAILABLE"
                                                        cursorShape: Qt.PointingHandCursor
                                                        onClicked: collaboration.downloadAttachment(file["fileId"], file["originalName"])
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Repeater {
                                        model: msg["sharedPostings"]
                                        delegate: Rectangle {
                                            property var posting: modelData
                                            Layout.fillWidth: true
                                            height: 42
                                            radius: 7
                                            color: Theme.raised
                                            border.color: Theme.border
                                            ColumnLayout {
                                                anchors.fill: parent
                                                anchors.leftMargin: 8; anchors.rightMargin: 8
                                                spacing: 1
                                                Text { text: "공고 #" + posting["applicationCaseId"] + " · " + posting["companyName"]; color: Theme.muted; font.pixelSize: 9; elide: Text.ElideRight; Layout.fillWidth: true }
                                                Text { text: posting["jobTitle"]; color: Theme.text; font.pixelSize: 11; font.bold: true; elide: Text.ElideRight; Layout.fillWidth: true }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Rectangle {
                    Layout.fillWidth: true
                    height: inputCol.implicitHeight + 18
                    color: Theme.surface
                    border.color: Theme.border
                    opacity: collaboration.currentConversationId > 0 ? 1 : 0.45
                    ColumnLayout {
                        id: inputCol
                        x: 12; y: 9
                        width: parent.width - 24
                        spacing: 8
                        RowLayout {
                            visible: collaboration.pendingAttachments.length > 0
                            Layout.fillWidth: true
                            spacing: 6
                            Repeater {
                                model: collaboration.pendingAttachments
                                delegate: Rectangle {
                                    property var file: modelData
                                    height: 26
                                    width: Math.min(220, pendingName.implicitWidth + 42)
                                    radius: 7
                                    color: Theme.raised
                                    border.color: Theme.border
                                    Icon {
                                        x: 8; anchors.verticalCenter: parent.verticalCenter
                                        name: "paperclip"; size: 10; color: Theme.muted
                                    }
                                    Text {
                                        id: pendingName
                                        x: 22; anchors.verticalCenter: parent.verticalCenter
                                        width: parent.width - 44
                                        text: file["name"]
                                        color: Theme.text; font.pixelSize: 10
                                        elide: Text.ElideRight
                                    }
                                    Text { x: parent.width - 20; anchors.verticalCenter: parent.verticalCenter; text: "×"; color: Theme.muted; font.pixelSize: 13 }
                                    MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: collaboration.removePendingAttachment(index) }
                                }
                            }
                            Rectangle {
                                visible: collaboration.pendingAttachments.length > 1
                                height: 26
                                width: 68
                                radius: 7
                                color: Theme.raised
                                border.color: Theme.border
                                Text { anchors.centerIn: parent; text: "모두 취소"; color: Theme.danger; font.pixelSize: 9 }
                                MouseArea {
                                    anchors.fill: parent
                                    cursorShape: Qt.PointingHandCursor
                                    onClicked: collaboration.clearPendingAttachments()
                                }
                            }
                        }
                        RowLayout {
                            Layout.fillWidth: true
                            spacing: 6
                            Row {
                                spacing: 4
                                Repeater {
                                    model: [
                                        { label: "임시", mode: "TEMPORARY", hint: "" },
                                        { label: "클라우드", mode: "CLOUD", hint: "" },
                                        // LOCAL 은 데스크톱 전용 공유 모드 — 웹/모바일 선택지에는 없다
                                        { label: "로컬", mode: "LOCAL", hint: "데스크톱 전용 (웹/모바일에는 안 보임)" }
                                    ]
                                    delegate: Rectangle {
                                        required property var modelData
                                        width: modelData.mode === "CLOUD" ? 62 : 48
                                        height: 28
                                        radius: 7
                                        color: root.selectedShareMode === modelData.mode ? Theme.accent : Theme.raised
                                        border.color: root.selectedShareMode === modelData.mode ? Theme.accent : Theme.border
                                        Text { anchors.centerIn: parent; text: modelData.label; color: root.selectedShareMode === modelData.mode ? "white" : Theme.text; font.pixelSize: 10; font.bold: true }
                                        MouseArea {
                                            id: shareModeHover
                                            anchors.fill: parent
                                            hoverEnabled: true
                                            cursorShape: Qt.PointingHandCursor
                                            onClicked: root.selectedShareMode = modelData.mode
                                        }
                                        ToolTip.visible: shareModeHover.containsMouse && modelData.hint.length > 0
                                        ToolTip.text: modelData.hint
                                    }
                                }
                            }
                            Text {
                                visible: root.selectedShareMode === "LOCAL"
                                text: "웹/모바일에는 안 보임"
                                color: Theme.muted; font.pixelSize: 9
                            }
                            TextField {
                                id: temporaryHoursInput
                                visible: root.selectedShareMode === "TEMPORARY"
                                width: 72
                                height: 28
                                text: "72"
                                validator: IntValidator { bottom: 1; top: 720 }
                                color: Theme.text
                                horizontalAlignment: TextInput.AlignHCenter
                                background: Rectangle { radius: 7; color: Theme.raised; border.color: Theme.border }
                            }
                            TextField {
                                id: postingIdsInput
                                Layout.fillWidth: true
                                height: 28
                                placeholderText: "공유할 공고 ID"
                                color: Theme.text
                                placeholderTextColor: Theme.muted
                                background: Rectangle { radius: 7; color: Theme.raised; border.color: Theme.border }
                            }
                        }
                        RowLayout {
                            Layout.fillWidth: true
                            spacing: 8
                            Row {
                                spacing: 4
                                Repeater {
                                    model: [
                                        { label: "채팅", kind: "CHAT" },
                                        { label: "쪽지", kind: "NOTE" }
                                    ]
                                    delegate: Rectangle {
                                        required property var modelData
                                        width: 46; height: 34; radius: 8
                                        color: root.selectedKind === modelData.kind ? Theme.accent : Theme.raised
                                        border.color: root.selectedKind === modelData.kind ? Theme.accent : Theme.border
                                        Text { anchors.centerIn: parent; text: modelData.label; color: "white"; font.pixelSize: 11; font.bold: root.selectedKind === modelData.kind }
                                        MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: root.selectedKind = modelData.kind }
                                    }
                                }
                            }
                            Rectangle {
                                width: 38; height: 34; radius: 8
                                color: Theme.raised; border.color: Theme.border
                                Icon { anchors.centerIn: parent; name: "paperclip"; size: 15; color: Theme.muted }
                                MouseArea {
                                    anchors.fill: parent
                                    enabled: collaboration.currentConversationId > 0
                                    cursorShape: Qt.PointingHandCursor
                                    onClicked: attachDialog.open()
                                }
                            }
                            TextArea {
                                id: messageInput
                                Layout.fillWidth: true
                                Layout.preferredHeight: 54
                                wrapMode: TextArea.Wrap
                                placeholderText: root.selectedKind === "NOTE" ? "쪽지 내용" : "메시지 입력"
                                color: Theme.text
                                placeholderTextColor: Theme.muted
                                background: Rectangle { radius: 8; color: Theme.raised; border.color: Theme.border }
                                enabled: collaboration.currentConversationId > 0
                            }
                            Rectangle {
                                width: 68; height: 54; radius: 9
                                color: Theme.accent
                                opacity: collaboration.currentConversationId > 0 && !collaboration.sendingMessage ? 1 : 0.4
                                Text {
                                    anchors.centerIn: parent
                                    text: collaboration.sendingMessage ? "전송 중…" : "보내기"
                                    color: "white"; font.pixelSize: 12; font.bold: true
                                }
                                MouseArea {
                                    anchors.fill: parent
                                    enabled: collaboration.currentConversationId > 0 && !collaboration.sendingMessage
                                    cursorShape: Qt.PointingHandCursor
                                    onClicked: {
                                        collaboration.sendMessage(
                                            root.selectedKind,
                                            messageInput.text,
                                            root.selectedShareMode,
                                            Number(temporaryHoursInput.text || 72),
                                            postingIdsInput.text
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
