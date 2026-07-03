import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import CareerTuner

// 커뮤니티 게시판 — 좌측 글 목록(카테고리 칩 + 검색 + 더보기) · 우측 글 상세(본문·좋아요·댓글 스레드).
// 데이터는 전부 CommunityClient(community 컨텍스트)가 공급한다.
Item {
    id: root
    property string selectedCategory: ""   // 빈 문자열 = 전체
    property var replyTarget: null          // { id, name } — 답글 대상 댓글
    property bool commentAnonymous: true

    readonly property var categories: [
        { label: "전체", value: "" },
        { label: "취업후기", value: "JOB_REVIEW" },
        { label: "면접후기", value: "INTERVIEW_REVIEW" },
        { label: "직무질문", value: "JOB_QUESTION" },
        { label: "합격전략", value: "SUCCESS_STRATEGY" },
        { label: "포트폴리오", value: "PORTFOLIO_FEEDBACK" },
        { label: "자격증후기", value: "CERTIFICATE_REVIEW" },
        { label: "자유게시판", value: "FREE" }
    ]

    function reload() { community.loadPosts(root.selectedCategory, boardSearch.text, 0) }

    function dateText(iso) {
        const s = String(iso || "")
        return s.length >= 16 ? s.substring(0, 16).replace("T", " ") : s
    }

    function hasPost() {
        return Number(community.currentPost["id"] || 0) > 0
    }

    // 로그인 전 선요청을 피하려고 화면이 실제로 보일 때 목록을 새로 고친다
    onVisibleChanged: if (visible) root.reload()

    RowLayout {
        anchors.fill: parent
        spacing: 0

        // ══ 좌측: 글 목록 ══
        Rectangle {
            Layout.preferredWidth: 360
            Layout.fillHeight: true
            color: Theme.surface
            border.color: Theme.border

            ColumnLayout {
                anchors.fill: parent
                anchors.margins: 12
                spacing: 10

                RowLayout {
                    Layout.fillWidth: true
                    Text { text: "커뮤니티"; color: Theme.text; font.pixelSize: 14; font.bold: true }
                    Text { text: community.totalCount + "건"; color: Theme.muted; font.pixelSize: 10 }
                    Item { Layout.fillWidth: true }
                    Rectangle {
                        width: 30; height: 30; radius: 8
                        color: Theme.raised; border.color: Theme.border
                        Text { anchors.centerIn: parent; text: "↻"; color: Theme.text; font.pixelSize: 14 }
                        MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: root.reload() }
                    }
                    Rectangle {
                        width: writeRow.implicitWidth + 20; height: 30; radius: 8
                        color: Theme.accent
                        Row {
                            id: writeRow; anchors.centerIn: parent; spacing: 5
                            Icon { name: "pencil"; size: 11; color: "white"; anchors.verticalCenter: parent.verticalCenter }
                            Text { text: "글쓰기"; color: "white"; font.pixelSize: 11; font.bold: true; anchors.verticalCenter: parent.verticalCenter }
                        }
                        MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: writeDialog.open() }
                    }
                }

                // 카테고리 칩 필터
                Flow {
                    Layout.fillWidth: true
                    spacing: 4
                    Repeater {
                        model: root.categories
                        delegate: Rectangle {
                            required property var modelData
                            width: chipLbl.implicitWidth + 16
                            height: 24; radius: 12
                            color: root.selectedCategory === modelData.value ? Theme.accent : Theme.raised
                            border.color: root.selectedCategory === modelData.value ? Theme.accent : Theme.border
                            Text {
                                id: chipLbl
                                anchors.centerIn: parent
                                text: modelData.label
                                color: root.selectedCategory === modelData.value ? "white" : Theme.muted
                                font.pixelSize: 10
                                font.bold: root.selectedCategory === modelData.value
                            }
                            MouseArea {
                                anchors.fill: parent
                                cursorShape: Qt.PointingHandCursor
                                onClicked: {
                                    root.selectedCategory = modelData.value
                                    root.reload()
                                }
                            }
                        }
                    }
                }

                TextField {
                    id: boardSearch
                    Layout.fillWidth: true
                    height: 34
                    placeholderText: "제목·본문·회사명 검색"
                    color: Theme.text
                    placeholderTextColor: Theme.muted
                    background: Rectangle { radius: 8; color: Theme.raised; border.color: Theme.border }
                    onTextChanged: boardSearchDelay.restart()
                    onAccepted: root.reload()
                }

                Timer {
                    id: boardSearchDelay
                    interval: 350
                    repeat: false
                    onTriggered: root.reload()
                }

                // 글 목록 + 더보기
                ListView {
                    id: postList
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    clip: true
                    spacing: 6
                    model: community.postsModel
                    delegate: Rectangle {
                        property var post: modelData
                        width: ListView.view.width
                        height: 76
                        radius: 9
                        color: community.currentPostId === post["id"] ? Theme.accentSoft
                             : postHover.containsMouse ? Theme.hover : Theme.raised
                        border.color: Theme.border
                        ColumnLayout {
                            anchors.fill: parent
                            anchors.leftMargin: 12; anchors.rightMargin: 12
                            anchors.topMargin: 9; anchors.bottomMargin: 9
                            spacing: 4
                            RowLayout {
                                Layout.fillWidth: true
                                spacing: 6
                                Rectangle {
                                    width: catLbl.implicitWidth + 12; height: 17; radius: 8
                                    color: Theme.accentSoft
                                    Text { id: catLbl; anchors.centerIn: parent; text: post["categoryLabel"]; color: Theme.accent2; font.pixelSize: 9; font.bold: true }
                                }
                                Text {
                                    text: post["title"]
                                    color: Theme.text; font.pixelSize: 12; font.bold: true
                                    elide: Text.ElideRight; Layout.fillWidth: true
                                }
                            }
                            Text {
                                text: post["content"]
                                color: Theme.muted; font.pixelSize: 10
                                elide: Text.ElideRight; Layout.fillWidth: true
                                maximumLineCount: 1
                            }
                            RowLayout {
                                Layout.fillWidth: true
                                spacing: 8
                                Text { text: post["authorName"]; color: Theme.muted; font.pixelSize: 9; font.bold: true }
                                Text { text: root.dateText(post["createdAt"]); color: Theme.muted; font.pixelSize: 9 }
                                Item { Layout.fillWidth: true }
                                Row {
                                    spacing: 3
                                    Icon { name: "eye"; size: 10; color: Theme.muted; anchors.verticalCenter: parent.verticalCenter }
                                    Text { text: post["viewCount"]; color: Theme.muted; font.pixelSize: 9; anchors.verticalCenter: parent.verticalCenter }
                                }
                                Row {
                                    spacing: 3
                                    Icon { name: "message"; size: 10; color: Theme.muted; anchors.verticalCenter: parent.verticalCenter }
                                    Text { text: post["commentCount"]; color: Theme.muted; font.pixelSize: 9; anchors.verticalCenter: parent.verticalCenter }
                                }
                                Row {
                                    spacing: 3
                                    Icon { name: "heart"; size: 10; color: post["liked"] ? Theme.danger : Theme.muted; anchors.verticalCenter: parent.verticalCenter }
                                    Text { text: post["likeCount"]; color: post["liked"] ? Theme.danger : Theme.muted; font.pixelSize: 9; anchors.verticalCenter: parent.verticalCenter }
                                }
                            }
                        }
                        MouseArea {
                            id: postHover
                            anchors.fill: parent
                            hoverEnabled: true
                            cursorShape: Qt.PointingHandCursor
                            onClicked: {
                                root.replyTarget = null
                                community.openPost(post["id"])
                            }
                        }
                    }
                    footer: Item {
                        width: postList.width
                        height: community.hasMore ? 42 : 0
                        Rectangle {
                            visible: community.hasMore
                            anchors.centerIn: parent
                            width: parent.width - 4; height: 32; radius: 8
                            color: moreHover.containsMouse ? Theme.hover : Theme.raised
                            border.color: Theme.border
                            Text {
                                anchors.centerIn: parent
                                text: community.loading ? "불러오는 중…" : "더보기"
                                color: Theme.muted; font.pixelSize: 11; font.bold: true
                            }
                            MouseArea {
                                id: moreHover
                                anchors.fill: parent
                                hoverEnabled: true
                                cursorShape: Qt.PointingHandCursor
                                onClicked: community.loadMore()
                            }
                        }
                    }
                }
            }
        }

        // ══ 우측: 글 상세 ══
        Rectangle {
            Layout.fillWidth: true
            Layout.fillHeight: true
            color: Theme.bg

            // 선택 전 안내
            ColumnLayout {
                visible: !root.hasPost()
                anchors.centerIn: parent
                spacing: 8
                Icon {
                    name: "list"; size: 32; color: Theme.muted
                    Layout.alignment: Qt.AlignHCenter
                    Layout.preferredWidth: 32; Layout.preferredHeight: 32
                }
                Text {
                    text: community.loading ? "불러오는 중…" : "왼쪽 목록에서 글을 선택하세요"
                    color: Theme.muted; font.pixelSize: 13
                    Layout.alignment: Qt.AlignHCenter
                }
            }

            ColumnLayout {
                visible: root.hasPost()
                anchors.fill: parent
                spacing: 0

                // 상세 헤더
                Rectangle {
                    Layout.fillWidth: true
                    height: headerCol.implicitHeight + 22
                    color: Theme.surface
                    border.color: Theme.border
                    ColumnLayout {
                        id: headerCol
                        x: 18; y: 11
                        width: parent.width - 36
                        spacing: 5
                        RowLayout {
                            Layout.fillWidth: true
                            spacing: 8
                            Rectangle {
                                width: detailCatLbl.implicitWidth + 14; height: 20; radius: 10
                                color: Theme.accentSoft
                                Text { id: detailCatLbl; anchors.centerIn: parent; text: community.currentPost["categoryLabel"] || ""; color: Theme.accent2; font.pixelSize: 10; font.bold: true }
                            }
                            Text {
                                text: community.currentPost["title"] || ""
                                color: Theme.text; font.pixelSize: 15; font.bold: true
                                elide: Text.ElideRight; Layout.fillWidth: true
                            }
                        }
                        RowLayout {
                            Layout.fillWidth: true
                            spacing: 10
                            Text { text: community.currentPost["authorName"] || ""; color: Theme.text; font.pixelSize: 11; font.bold: true }
                            Text { text: root.dateText(community.currentPost["createdAt"]); color: Theme.muted; font.pixelSize: 10 }
                            Text { text: "조회 " + (community.currentPost["viewCount"] || 0); color: Theme.muted; font.pixelSize: 10 }
                            Item { Layout.fillWidth: true }
                            // 좋아요 토글
                            Rectangle {
                                width: likeRow.implicitWidth + 22; height: 28; radius: 8
                                color: community.currentPost["liked"] ? Theme.accentSoft : Theme.raised
                                border.color: community.currentPost["liked"] ? Theme.accent : Theme.border
                                Row {
                                    id: likeRow; anchors.centerIn: parent; spacing: 5
                                    Icon {
                                        name: "heart"; size: 12
                                        color: community.currentPost["liked"] ? Theme.accent2 : Theme.text
                                        anchors.verticalCenter: parent.verticalCenter
                                    }
                                    Text {
                                        text: community.currentPost["likeCount"] || 0
                                        color: community.currentPost["liked"] ? Theme.accent2 : Theme.text
                                        font.pixelSize: 12; font.bold: true
                                        anchors.verticalCenter: parent.verticalCenter
                                    }
                                }
                                MouseArea {
                                    anchors.fill: parent
                                    cursorShape: Qt.PointingHandCursor
                                    onClicked: community.toggleLike(community.currentPostId)
                                }
                            }
                        }
                    }
                }

                // 본문 + 댓글 스레드
                ScrollView {
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    clip: true
                    contentWidth: availableWidth

                    ColumnLayout {
                        width: parent.width
                        spacing: 12

                        Text {
                            Layout.fillWidth: true
                            Layout.margins: 18
                            Layout.bottomMargin: 0
                            text: community.currentPost["content"] || ""
                            color: Theme.text; font.pixelSize: 13
                            lineHeight: 1.35
                            wrapMode: Text.WordWrap
                        }

                        // 태그
                        Flow {
                            Layout.fillWidth: true
                            Layout.leftMargin: 18; Layout.rightMargin: 18
                            spacing: 4
                            visible: (community.currentPost["tags"] || []).length > 0
                            Repeater {
                                model: community.currentPost["tags"] || []
                                delegate: Rectangle {
                                    required property var modelData
                                    width: tagLbl.implicitWidth + 14; height: 20; radius: 10
                                    color: Theme.raised; border.color: Theme.border
                                    Text { id: tagLbl; anchors.centerIn: parent; text: "#" + modelData; color: Theme.muted; font.pixelSize: 10 }
                                }
                            }
                        }

                        // 면접후기 부가 정보 (면접후기 카테고리 상세에만 내려옴)
                        Rectangle {
                            visible: !!community.currentPost["interviewReview"]
                            Layout.fillWidth: true
                            Layout.leftMargin: 18; Layout.rightMargin: 18
                            height: reviewCol.implicitHeight + 20
                            radius: 9
                            color: Theme.surface; border.color: Theme.border
                            ColumnLayout {
                                id: reviewCol
                                x: 12; y: 10
                                width: parent.width - 24
                                spacing: 4
                                property var review: community.currentPost["interviewReview"] || ({})
                                Text { text: "면접 정보"; color: Theme.accent2; font.pixelSize: 10; font.bold: true }
                                Text {
                                    text: (reviewCol.review["companyName"] || "") + " · " + (reviewCol.review["jobRole"] || "")
                                          + (reviewCol.review["interviewType"] ? " · " + reviewCol.review["interviewType"] : "")
                                          + (reviewCol.review["difficulty"] ? " · 난이도 " + reviewCol.review["difficulty"] + "/5" : "")
                                    color: Theme.text; font.pixelSize: 11
                                    wrapMode: Text.WordWrap; Layout.fillWidth: true
                                }
                                Repeater {
                                    model: reviewCol.review["questions"] || []
                                    delegate: Text {
                                        required property var modelData
                                        text: "Q. " + modelData
                                        color: Theme.muted; font.pixelSize: 11
                                        wrapMode: Text.WordWrap; Layout.fillWidth: true
                                    }
                                }
                            }
                        }

                        Rectangle { Layout.fillWidth: true; Layout.leftMargin: 18; Layout.rightMargin: 18; height: 1; color: Theme.border }

                        Text {
                            Layout.leftMargin: 18
                            text: "댓글 " + community.comments.length
                            color: Theme.text; font.pixelSize: 12; font.bold: true
                        }

                        // 댓글 스레드 — 답글(parentId>0)은 들여쓰기
                        Repeater {
                            model: community.comments
                            delegate: Rectangle {
                                property var cmt: modelData
                                Layout.fillWidth: true
                                Layout.leftMargin: 18 + (Number(cmt["parentId"]) > 0 ? 26 : 0)
                                Layout.rightMargin: 18
                                height: cmtCol.implicitHeight + 18
                                radius: 9
                                color: Theme.surface
                                border.color: cmt["isAuthor"] ? Theme.accent : Theme.border
                                ColumnLayout {
                                    id: cmtCol
                                    x: 12; y: 9
                                    width: parent.width - 24
                                    spacing: 4
                                    RowLayout {
                                        Layout.fillWidth: true
                                        spacing: 6
                                        Text { text: cmt["isDeleted"] ? "-" : cmt["authorName"]; color: Theme.text; font.pixelSize: 11; font.bold: true }
                                        Rectangle {
                                            visible: cmt["isAuthor"] === true && !cmt["isDeleted"]
                                            width: opLbl.implicitWidth + 10; height: 15; radius: 7
                                            color: Theme.accentSoft
                                            Text { id: opLbl; anchors.centerIn: parent; text: "작성자"; color: Theme.accent2; font.pixelSize: 8; font.bold: true }
                                        }
                                        Text { text: root.dateText(cmt["createdAt"]); color: Theme.muted; font.pixelSize: 9 }
                                        Item { Layout.fillWidth: true }
                                        Row {
                                            visible: Number(cmt["likeCount"]) > 0
                                            spacing: 3
                                            Icon { name: "heart"; size: 9; color: Theme.muted; anchors.verticalCenter: parent.verticalCenter }
                                            Text { text: cmt["likeCount"]; color: Theme.muted; font.pixelSize: 9; anchors.verticalCenter: parent.verticalCenter }
                                        }
                                        // 답글 대상 지정
                                        Rectangle {
                                            visible: !cmt["isDeleted"]
                                            width: replyLbl.implicitWidth + 12; height: 20; radius: 6
                                            color: replyHover.containsMouse ? Theme.hover : "transparent"
                                            border.color: Theme.border
                                            Text { id: replyLbl; anchors.centerIn: parent; text: "답글"; color: Theme.muted; font.pixelSize: 9 }
                                            MouseArea {
                                                id: replyHover
                                                anchors.fill: parent
                                                hoverEnabled: true
                                                cursorShape: Qt.PointingHandCursor
                                                onClicked: root.replyTarget = { id: cmt["id"], name: cmt["authorName"] }
                                            }
                                        }
                                    }
                                    Text {
                                        Layout.fillWidth: true
                                        text: cmt["isDeleted"]
                                              ? "삭제된 댓글입니다"
                                              : (cmt["mentionLabel"] ? "@" + cmt["mentionLabel"] + " " : "") + cmt["content"]
                                        color: cmt["isDeleted"] ? Theme.muted : Theme.text
                                        font.pixelSize: 12
                                        font.italic: cmt["isDeleted"] === true
                                        wrapMode: Text.WordWrap
                                    }
                                }
                            }
                        }

                        Item { height: 8 }
                    }
                }

                // 댓글 입력
                Rectangle {
                    Layout.fillWidth: true
                    height: commentInputCol.implicitHeight + 18
                    color: Theme.surface
                    border.color: Theme.border
                    ColumnLayout {
                        id: commentInputCol
                        x: 12; y: 9
                        width: parent.width - 24
                        spacing: 6
                        // 답글 대상 칩
                        RowLayout {
                            visible: root.replyTarget !== null
                            spacing: 6
                            Rectangle {
                                width: replyTargetLbl.implicitWidth + 26; height: 22; radius: 7
                                color: Theme.accentSoft; border.color: Theme.accent
                                Text {
                                    id: replyTargetLbl
                                    x: 8; anchors.verticalCenter: parent.verticalCenter
                                    text: "답글 → " + (root.replyTarget ? root.replyTarget.name : "")
                                    color: Theme.accent2; font.pixelSize: 10; font.bold: true
                                }
                                Text { x: parent.width - 15; anchors.verticalCenter: parent.verticalCenter; text: "×"; color: Theme.muted; font.pixelSize: 12 }
                                MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: root.replyTarget = null }
                            }
                        }
                        RowLayout {
                            Layout.fillWidth: true
                            spacing: 6
                            // 익명 토글
                            Rectangle {
                                width: anonLbl.implicitWidth + 16; height: 32; radius: 8
                                color: root.commentAnonymous ? Theme.accent : Theme.raised
                                border.color: root.commentAnonymous ? Theme.accent : Theme.border
                                Text { id: anonLbl; anchors.centerIn: parent; text: "익명"; color: root.commentAnonymous ? "white" : Theme.muted; font.pixelSize: 10; font.bold: true }
                                MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: root.commentAnonymous = !root.commentAnonymous }
                            }
                            TextField {
                                id: commentInput
                                Layout.fillWidth: true
                                height: 32
                                placeholderText: root.replyTarget !== null ? "답글 입력" : "댓글 입력"
                                color: Theme.text
                                placeholderTextColor: Theme.muted
                                background: Rectangle { radius: 8; color: Theme.raised; border.color: Theme.border }
                                onAccepted: sendCommentBtn.submit()
                            }
                            Rectangle {
                                id: sendCommentBtn
                                width: 52; height: 32; radius: 8
                                color: Theme.accent
                                opacity: commentInput.text.trim().length > 0 ? 1 : 0.4
                                function submit() {
                                    if (commentInput.text.trim().length === 0) return
                                    community.addComment(
                                        community.currentPostId,
                                        commentInput.text,
                                        root.replyTarget !== null ? root.replyTarget.id : 0,
                                        root.commentAnonymous)
                                    commentInput.clear()
                                    root.replyTarget = null
                                }
                                Text { anchors.centerIn: parent; text: "등록"; color: "white"; font.pixelSize: 11; font.bold: true }
                                MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: sendCommentBtn.submit() }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── 글 작성 다이얼로그 (제목 + 본문 + 카테고리 + 익명) ──
    Dialog {
        id: writeDialog
        modal: true
        anchors.centerIn: parent
        width: 520
        padding: 0

        property string writeCategory: "FREE"
        property bool writeAnonymous: true

        onAboutToShow: {
            writeCategory = root.selectedCategory !== "" ? root.selectedCategory : "FREE"
            writeAnonymous = true
            writeTitle.clear()
            writeContent.clear()
        }

        background: Rectangle { color: Theme.surface; border.color: Theme.border; radius: 16 }

        contentItem: ColumnLayout {
            spacing: 10

            Text {
                Layout.topMargin: 22; Layout.leftMargin: 26
                text: "새 글 쓰기"; color: Theme.text; font.pixelSize: 16; font.bold: true
            }

            // 카테고리 선택 (전체 제외)
            Flow {
                Layout.fillWidth: true
                Layout.leftMargin: 26; Layout.rightMargin: 26
                spacing: 4
                Repeater {
                    model: root.categories.filter(c => c.value !== "")
                    delegate: Rectangle {
                        required property var modelData
                        width: wCatLbl.implicitWidth + 16; height: 26; radius: 13
                        color: writeDialog.writeCategory === modelData.value ? Theme.accent : Theme.raised
                        border.color: writeDialog.writeCategory === modelData.value ? Theme.accent : Theme.border
                        Text {
                            id: wCatLbl
                            anchors.centerIn: parent
                            text: modelData.label
                            color: writeDialog.writeCategory === modelData.value ? "white" : Theme.muted
                            font.pixelSize: 10; font.bold: writeDialog.writeCategory === modelData.value
                        }
                        MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: writeDialog.writeCategory = modelData.value }
                    }
                }
            }

            TextField {
                id: writeTitle
                Layout.fillWidth: true
                Layout.leftMargin: 26; Layout.rightMargin: 26
                height: 36
                placeholderText: "제목"
                color: Theme.text
                placeholderTextColor: Theme.muted
                background: Rectangle { radius: 8; color: Theme.raised; border.color: Theme.border }
            }

            ScrollView {
                Layout.fillWidth: true
                Layout.leftMargin: 26; Layout.rightMargin: 26
                Layout.preferredHeight: 180
                TextArea {
                    id: writeContent
                    wrapMode: TextArea.Wrap
                    placeholderText: "본문 내용"
                    color: Theme.text
                    placeholderTextColor: Theme.muted
                    background: Rectangle { radius: 8; color: Theme.raised; border.color: Theme.border }
                }
            }

            RowLayout {
                Layout.fillWidth: true
                Layout.leftMargin: 26; Layout.rightMargin: 26
                Layout.bottomMargin: 22
                spacing: 8
                Rectangle {
                    width: wAnonLbl.implicitWidth + 16; height: 30; radius: 8
                    color: writeDialog.writeAnonymous ? Theme.accent : Theme.raised
                    border.color: writeDialog.writeAnonymous ? Theme.accent : Theme.border
                    Text { id: wAnonLbl; anchors.centerIn: parent; text: "익명"; color: writeDialog.writeAnonymous ? "white" : Theme.muted; font.pixelSize: 11; font.bold: true }
                    MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: writeDialog.writeAnonymous = !writeDialog.writeAnonymous }
                }
                Item { Layout.fillWidth: true }
                Rectangle {
                    width: wCancelLbl.implicitWidth + 24; height: 32; radius: 8
                    color: Theme.raised; border.color: Theme.border
                    Text { id: wCancelLbl; anchors.centerIn: parent; text: "취소"; color: Theme.muted; font.pixelSize: 12 }
                    MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: writeDialog.close() }
                }
                Rectangle {
                    width: wSubmitLbl.implicitWidth + 26; height: 32; radius: 8
                    color: Theme.accent
                    opacity: writeTitle.text.trim().length > 0 && writeContent.text.trim().length > 0 ? 1 : 0.4
                    Text { id: wSubmitLbl; anchors.centerIn: parent; text: "등록"; color: "white"; font.pixelSize: 12; font.bold: true }
                    MouseArea {
                        anchors.fill: parent
                        cursorShape: Qt.PointingHandCursor
                        enabled: writeTitle.text.trim().length > 0 && writeContent.text.trim().length > 0
                        onClicked: {
                            community.createPost(writeDialog.writeCategory, writeTitle.text, writeContent.text, writeDialog.writeAnonymous)
                            writeDialog.close()
                        }
                    }
                }
            }
        }
    }
}
