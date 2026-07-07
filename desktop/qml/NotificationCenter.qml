import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import CareerTuner

// 벨 클릭 시 뜨는 알림 센터 앵커 팝업.
// NotificationPoller.items(최근 20개)를 그대로 그린다 — 항목 클릭 = 읽음 처리 + 링크 라우팅.
// 카드 스타일(좌측 액센트 바·팔레트)은 Main.qml 토스트 카드와 통일 (Theme 싱글톤).
Popup {
    id: root

    // 클릭한 알림의 link — Main.qml 의 routeNotificationLink 로 연결
    signal linkActivated(string link)

    width: 340
    padding: 0
    height: 47 + Math.min(380, Math.max(84, notifList.contentHeight + 10))
    // 벨(부모) 클릭은 토글로 처리하므로 팝업이 먼저 닫히지 않게 부모 밖 클릭만 닫는다
    closePolicy: Popup.CloseOnEscape | Popup.CloseOnPressOutsideParent
    background: Rectangle {
        color: Theme.surface
        border.color: Theme.border
        radius: Theme.radiusL
    }

    onOpened: notifications.pollNow()   // 열 때 최신 상태로 갱신

    // type → Icon.qml 아이콘 이름 (웹 TYPE_META 아이콘의 데스크톱 스트로크 대응)
    function typeIcon(type) {
        switch (type) {
        case "NOTE_MESSAGE": return "mail"
        case "ROOM_MESSAGE":
        case "ROOM_MENTION":
        case "ROOM_INVITE": return "message"
        case "FRIEND_REQUEST":
        case "FRIEND_ACCEPTED": return "users"
        case "INTERVIEW_DISPATCH": return "smartphone"
        case "QUESTIONS_GENERATED":
        case "INTERVIEW_REPORT_READY": return "mic"
        case "COMMENT":
        case "COMMENT_REPLY": return "message"
        case "LIKE": return "heart"
        case "CORRECTION_COMPLETE": return "pencil"
        case "RECOMMENDED_JOB":
        case "RECOMMENDED_POST": return "pin"
        case "NOTICE":
        case "MARKETING_AD": return "megaphone"
        case "CREDIT_LOW":
        case "CREDIT_RECHARGED":
        case "PAYMENT_COMPLETE":
        case "PAYMENT_SCHEDULED":
        case "SUBSCRIPTION_CANCELED":
        case "REFUND_RESULT": return "box"
        default:
            // AI 분석 계열(…_ANALYZED / …_ANALYSIS_… / 추출·트렌드)은 spark, 나머지는 기본 벨
            return (String(type).indexOf("ANALY") >= 0
                    || String(type).indexOf("EXTRACTION") >= 0
                    || String(type).indexOf("TREND") >= 0) ? "spark" : "bell"
        }
    }

    // ISO 8601 → 상대시간 (웹 relTime 과 동일 규칙)
    function relTime(ts) {
        const t = new Date(ts).getTime()
        if (isNaN(t)) return ""
        const m = Math.floor(Math.max(0, Date.now() - t) / 60000)
        if (m < 1) return "방금"
        if (m < 60) return m + "분 전"
        const h = Math.floor(m / 60)
        if (h < 24) return h + "시간 전"
        const d = Math.floor(h / 24)
        if (d === 1) return "어제"
        if (d < 7) return d + "일 전"
        return Qt.formatDate(new Date(t), "M월 d일")
    }

    contentItem: ColumnLayout {
        spacing: 0

        // ── 헤더: 제목 + 안읽음 칩 + 모두 읽음 ──
        RowLayout {
            Layout.fillWidth: true
            Layout.preferredHeight: 46
            Layout.leftMargin: 14
            Layout.rightMargin: 10
            spacing: 8
            Text { text: "알림"; color: Theme.text; font.pixelSize: 13; font.bold: true }
            Rectangle {
                visible: notifications.unread > 0
                width: unreadCountText.implicitWidth + 12; height: 17; radius: 8.5
                color: Theme.accentSoft
                Text {
                    id: unreadCountText
                    anchors.centerIn: parent
                    text: notifications.unread > 99 ? "99+" : notifications.unread
                    color: Theme.accent; font.pixelSize: 9; font.bold: true
                }
            }
            Item { Layout.fillWidth: true }
            Rectangle {
                width: readAllLbl.implicitWidth + 16; height: 24; radius: 7
                color: readAllHover.containsMouse ? Theme.hover : Theme.raised
                border.color: Theme.border
                opacity: notifications.unread > 0 ? 1 : 0.5
                Text { id: readAllLbl; anchors.centerIn: parent; text: "모두 읽음"; color: Theme.text; font.pixelSize: 10 }
                MouseArea {
                    id: readAllHover
                    anchors.fill: parent
                    hoverEnabled: true
                    enabled: notifications.unread > 0
                    cursorShape: Qt.PointingHandCursor
                    onClicked: notifications.markAllRead()
                }
            }
        }
        Rectangle { Layout.fillWidth: true; height: 1; color: Theme.border }

        // ── 알림 리스트 ──
        Item {
            Layout.fillWidth: true
            Layout.fillHeight: true

            // 빈 상태 (ListView 밖에 두어야 contentItem 높이 0 에도 가운데에 뜬다)
            Text {
                visible: notifList.count === 0
                anchors.centerIn: parent
                text: "새 알림이 없습니다"
                color: Theme.muted; font.pixelSize: 11
            }

            ListView {
                id: notifList
                anchors.fill: parent
                clip: true
                spacing: 0
                model: notifications.items
                ScrollBar.vertical: ScrollBar {}

                delegate: Rectangle {
                    id: noteItem
                    required property var modelData
                    property var note: modelData
                    width: ListView.view.width
                    height: Math.max(noteCol.implicitHeight + 20, 50)
                    color: noteHover.containsMouse ? Theme.hover : "transparent"

                    // 안읽음 = 좌측 액센트 바 (토스트 카드와 동일 문법)
                    Rectangle {
                        visible: noteItem.note.isRead !== true
                        width: 3; height: parent.height - 16; y: 8; x: 0; radius: 2
                        color: Theme.accent
                    }

                    RowLayout {
                        anchors.left: parent.left
                        anchors.right: parent.right
                        anchors.top: parent.top
                        anchors.leftMargin: 12; anchors.rightMargin: 12; anchors.topMargin: 10
                        spacing: 10

                        // 타입 아이콘
                        Rectangle {
                            Layout.alignment: Qt.AlignTop
                            width: 30; height: 30; radius: 15
                            color: Theme.raised; border.color: Theme.border
                            Icon { anchors.centerIn: parent; name: root.typeIcon(String(noteItem.note.type)); size: 14; color: Theme.accentText }
                        }

                        ColumnLayout {
                            id: noteCol
                            Layout.fillWidth: true
                            spacing: 2
                            RowLayout {
                                Layout.fillWidth: true
                                spacing: 6
                                Text {
                                    text: noteItem.note.title
                                    color: noteItem.note.isRead === true ? Theme.muted : Theme.text
                                    font.pixelSize: 12; font.bold: noteItem.note.isRead !== true
                                    elide: Text.ElideRight; Layout.fillWidth: true
                                }
                                Text {
                                    text: root.relTime(String(noteItem.note.createdAt))
                                    color: Theme.muted; font.pixelSize: 9
                                }
                            }
                            Text {
                                visible: String(noteItem.note.message || "").length > 0
                                text: noteItem.note.message
                                color: Theme.muted; font.pixelSize: 11
                                wrapMode: Text.WordWrap; maximumLineCount: 2; elide: Text.ElideRight
                                Layout.fillWidth: true
                            }
                            Row {
                                visible: String(noteItem.note.actorName || "").length > 0
                                spacing: 4
                                Icon { name: "user"; size: 9; color: Theme.muted; anchors.verticalCenter: parent.verticalCenter }
                                Text {
                                    text: noteItem.note.actorName
                                    color: Theme.muted; font.pixelSize: 9
                                    anchors.verticalCenter: parent.verticalCenter
                                }
                            }
                        }
                    }

                    // 항목 사이 구분선
                    Rectangle {
                        anchors.bottom: parent.bottom
                        width: parent.width; height: 1
                        color: Theme.border; opacity: 0.5
                    }

                    MouseArea {
                        id: noteHover
                        anchors.fill: parent
                        hoverEnabled: true
                        cursorShape: Qt.PointingHandCursor
                        onClicked: {
                            if (noteItem.note.isRead !== true)
                                notifications.markAsRead(noteItem.note.id)
                            const link = String(noteItem.note.link || "")
                            root.close()
                            if (link.length > 0)
                                root.linkActivated(link)
                        }
                    }
                }
            }
        }
    }
}
