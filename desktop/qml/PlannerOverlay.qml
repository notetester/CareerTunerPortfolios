import QtQuick
import QtQuick.Window
import QtQuick.Controls
import QtQuick.Layouts
import CareerTuner

Window {
    id: overlayWindow
    width: 340
    height: Math.min(560, Math.max(220, 130 + Math.min(plannerClient.items.length, 5) * 84))
    x: 32
    y: 96
    visible: plannerOverlayController.enabled && plannerClient.items.length > 0
    color: "transparent"
    title: "CareerTuner 플래너"
    flags: Qt.Tool | Qt.FramelessWindowHint | (plannerOverlayController.alwaysOnTop ? Qt.WindowStaysOnTopHint : 0)
    opacity: plannerOverlayController.overlayOpacity

    Component.onCompleted: plannerOverlayController.attach(overlayWindow)
    onVisibleChanged: if (visible) Qt.callLater(function() { plannerOverlayController.attach(overlayWindow) })

    Connections {
        target: plannerOverlayController
        function onAlwaysOnTopChanged() { Qt.callLater(function() { plannerOverlayController.attach(overlayWindow) }) }
        function onClickThroughChanged() { Qt.callLater(function() { plannerOverlayController.attach(overlayWindow) }) }
        function onOverlayOpacityChanged() { overlayWindow.opacity = plannerOverlayController.overlayOpacity }
    }

    Rectangle {
        id: shell
        anchors.fill: parent
        radius: 10
        color: Qt.rgba(0.04, 0.04, 0.05, 0.92)
        border.color: Theme.borderHover
        clip: true

        ColumnLayout {
            anchors.fill: parent
            anchors.margins: 10
            spacing: 8

            Rectangle {
                id: dragBar
                Layout.fillWidth: true
                height: 34
                radius: 8
                color: Theme.raised

                property real dragX: 0
                property real dragY: 0

                MouseArea {
                    anchors.fill: parent
                    cursorShape: plannerOverlayController.clickThrough ? Qt.ArrowCursor : Qt.SizeAllCursor
                    enabled: !plannerOverlayController.clickThrough
                    onPressed: function(mouse) {
                        dragBar.dragX = mouse.x
                        dragBar.dragY = mouse.y
                    }
                    onPositionChanged: function(mouse) {
                        overlayWindow.x += mouse.x - dragBar.dragX
                        overlayWindow.y += mouse.y - dragBar.dragY
                    }
                }

                RowLayout {
                    anchors.fill: parent
                    anchors.leftMargin: 10
                    anchors.rightMargin: 8
                    spacing: 8

                    Text {
                        text: "플래너"
                        color: Theme.text
                        font.pixelSize: 13
                        font.bold: true
                    }
                    Text {
                        text: plannerClient.statusText
                        color: Theme.muted
                        font.pixelSize: 11
                        Layout.fillWidth: true
                        elide: Text.ElideRight
                    }
                    OverlayButton {
                        label: "핀"
                        active: plannerOverlayController.alwaysOnTop
                        onClicked: plannerOverlayController.alwaysOnTop = !plannerOverlayController.alwaysOnTop
                    }
                    OverlayButton {
                        label: "통과"
                        active: plannerOverlayController.clickThrough
                        onClicked: plannerOverlayController.clickThrough = !plannerOverlayController.clickThrough
                    }
                    OverlayButton {
                        label: "닫기"
                        active: false
                        onClicked: plannerOverlayController.enabled = false
                    }
                }
            }

            RowLayout {
                Layout.fillWidth: true
                spacing: 8
                Text { text: "투명도"; color: Theme.muted; font.pixelSize: 11 }
                Slider {
                    Layout.fillWidth: true
                    from: 0.35
                    to: 1.0
                    value: plannerOverlayController.overlayOpacity
                    enabled: !plannerOverlayController.clickThrough
                    onMoved: plannerOverlayController.overlayOpacity = value
                }
            }

            ListView {
                Layout.fillWidth: true
                Layout.fillHeight: true
                clip: true
                spacing: 8
                model: plannerClient.items
                delegate: Rectangle {
                    required property var modelData
                    width: ListView.view.width
                    height: Math.max(64, content.implicitHeight + 20)
                    radius: 8
                    color: modelData.type === "memo"
                        ? Qt.rgba(0.88, 0.70, 0.32, 0.16)
                        : Theme.raised
                    border.color: modelData.pinned ? Theme.borderAccent : Theme.border
                    opacity: Math.max(0.35, Math.min(1, modelData.opacity || 0.92))

                    ColumnLayout {
                        id: content
                        anchors.fill: parent
                        anchors.margins: 10
                        spacing: 4
                        RowLayout {
                            Layout.fillWidth: true
                            spacing: 6
                            Text {
                                text: modelData.meta
                                color: Theme.accentText
                                font.pixelSize: 10
                                font.bold: true
                                Layout.fillWidth: true
                                elide: Text.ElideRight
                            }
                            Text {
                                visible: modelData.pinned
                                text: "PIN"
                                color: Theme.accentText
                                font.pixelSize: 9
                                font.bold: true
                            }
                        }
                        Text {
                            Layout.fillWidth: true
                            text: modelData.title
                            color: Theme.text
                            font.pixelSize: 13
                            font.bold: true
                            elide: Text.ElideRight
                        }
                        Text {
                            Layout.fillWidth: true
                            visible: String(modelData.body || "").length > 0
                            text: modelData.body
                            color: Theme.muted
                            font.pixelSize: 11
                            wrapMode: Text.WordWrap
                            maximumLineCount: 3
                            elide: Text.ElideRight
                        }
                    }
                }
            }
        }
    }

    component OverlayButton: Rectangle {
        signal clicked()
        property string label: ""
        property bool active: false

        width: labelText.implicitWidth + 14
        height: 22
        radius: 7
        color: active ? Theme.accentSoft : Theme.hover
        border.color: active ? Theme.borderAccent : Theme.border
        Text {
            id: labelText
            anchors.centerIn: parent
            text: label
            color: active ? Theme.accentText : Theme.muted
            font.pixelSize: 10
            font.bold: active
        }
        MouseArea {
            anchors.fill: parent
            cursorShape: Qt.PointingHandCursor
            onClicked: parent.clicked()
        }
    }
}
