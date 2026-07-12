import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import CareerTuner

// 설정 — SettingsStore(QSettings) 실바인딩. 전부 재시작 후에도 유지된다.
Item {
    id: root

    function openWebSettings(tab) {
        // 브라우저 세션은 웹에서 별도로 인증한다. 토큰은 URL에 절대 넣지 않는다.
        Qt.openUrlExternally(appSettings.webAppUrl + "/settings?tab=" + encodeURIComponent(tab))
    }

    component SettingCard: Rectangle {
        property alias title: titleText.text
        property alias desc: descText.text
        default property alias content: slot.data
        Layout.fillWidth: true
        radius: Theme.radius
        color: Theme.surface; border.color: Theme.border
        implicitHeight: Math.max(rowLay.implicitHeight + 26, 58)
        RowLayout {
            id: rowLay
            anchors.verticalCenter: parent.verticalCenter
            x: 16; width: parent.width - 32
            spacing: 12
            ColumnLayout {
                Layout.fillWidth: true
                spacing: 3
                Text { id: titleText; color: Theme.text; font.pixelSize: 13; font.bold: true }
                Text {
                    id: descText; color: Theme.muted; font.pixelSize: 11
                    Layout.fillWidth: true; wrapMode: Text.WordWrap
                }
            }
            Item {
                id: slot
                implicitWidth: childrenRect.width
                implicitHeight: childrenRect.height
            }
        }
    }

    component Toggle: Rectangle {
        property bool checked: false
        property string accessibleName: "설정 전환"
        signal toggled(bool value)
        activeFocusOnTab: true
        Accessible.role: Accessible.CheckBox
        Accessible.name: accessibleName
        Accessible.checked: checked
        width: 38; height: 21; radius: 10.5
        color: checked ? Theme.accent : Theme.raised
        border.color: activeFocus ? Theme.accentText : (checked ? Theme.accent : Theme.border)
        border.width: activeFocus ? 2 : 1
        Keys.onPressed: (event) => {
            if (event.key === Qt.Key_Return || event.key === Qt.Key_Enter || event.key === Qt.Key_Space) {
                event.accepted = true
                toggled(!checked)
            }
        }
        Rectangle {
            width: 15; height: 15; radius: 7.5
            y: 3; x: parent.checked ? 20 : 3
            color: parent.checked ? "white" : Theme.muted
            Behavior on x { NumberAnimation { duration: 130 } }
        }
        MouseArea {
            anchors.fill: parent
            cursorShape: Qt.PointingHandCursor
            onClicked: parent.toggled(!parent.checked)
        }
    }

    Flickable {
        anchors.fill: parent
        contentHeight: content.implicitHeight + 60
        clip: true

        ColumnLayout {
            id: content
            width: Math.min(parent.width - 48, 640)
            anchors.horizontalCenter: parent.horizontalCenter
            spacing: 10

            Item { height: 10 }

            Text { text: "로컬 저장"; color: Theme.muted; font.pixelSize: 11; font.bold: true; Layout.leftMargin: 2 }

            SettingCard {
                title: "저장 폴더"
                desc: appSettings.saveDir + " — 세션별 하위 폴더로 정리"
                Rectangle {
                    width: pickLbl.implicitWidth + 22; height: 30; radius: 8
                    color: Theme.raised; border.color: Theme.border
                    Text { id: pickLbl; anchors.centerIn: parent; text: "변경…"; color: Theme.text; font.pixelSize: 12 }
                    MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: appSettings.pickSaveDir() }
                }
            }

            SettingCard {
                title: "완료 시 자동 저장"
                desc: "세션 완료 후 사용자가 리포트를 생성하면 저장 폴더에 자동 저장"
                Toggle {
                    accessibleName: "완료 시 자동 저장"
                    checked: appSettings.autoSave
                    onToggled: (v) => appSettings.autoSave = v
                }
            }

            Text { text: "계정 · 연결"; color: Theme.muted; font.pixelSize: 11; font.bold: true; Layout.leftMargin: 2; Layout.topMargin: 10 }

            SettingCard {
                title: "자동 로그인"
                desc: "토큰을 이 컴퓨터에 보관하고 시작 시 자동 갱신 (끄면 보관 토큰 삭제)"
                Toggle {
                    accessibleName: "자동 로그인"
                    checked: appSettings.autoLogin
                    onToggled: (v) => appSettings.autoLogin = v
                }
            }

            SettingCard {
                title: "서버 주소"
                desc: "프리셋(로컬/AWS/Tailscale)은 선택 즉시 저장 — 커스텀은 직접 입력 후 적용"
                ColumnLayout {
                    spacing: 6

                    // 프리셋 콤보박스 — 로컬 / AWS 공개 서버 / Tailscale(팀 개발용) / 커스텀
                    ComboBox {
                        id: serverPreset
                        implicitWidth: 296
                        implicitHeight: 30
                        model: [
                            "로컬 (http://localhost:8080)",
                            "AWS 공개 서버",
                            "Tailscale (팀 개발용)",
                            "커스텀"
                        ]

                        // 저장된 주소가 프리셋과 일치하면 해당 항목, 아니면 커스텀으로 시작
                        Component.onCompleted: {
                            if (appSettings.baseUrl === appSettings.localServerUrl) currentIndex = 0
                            else if (appSettings.baseUrl === appSettings.awsServerUrl) currentIndex = 1
                            else if (appSettings.baseUrl === appSettings.tailscaleServerUrl) currentIndex = 2
                            else currentIndex = 3
                        }
                        onActivated: (index) => {
                            if (index <= 2) {
                                const url = index === 0 ? appSettings.localServerUrl
                                    : index === 1 ? appSettings.awsServerUrl
                                    : appSettings.tailscaleServerUrl
                                const previous = appSettings.baseUrl
                                if (appSettings.applyBaseUrl(url)) {
                                    urlField.text = appSettings.baseUrl
                                    win.showToast("서버 주소 변경됨",
                                                  previous === appSettings.baseUrl
                                                  ? appSettings.baseUrl
                                                  : appSettings.baseUrl + " — 다시 로그인해 주세요")
                                }
                            } else {
                                urlField.forceActiveFocus()
                            }
                        }

                        font.pixelSize: 11
                        background: Rectangle {
                            radius: 7; color: Theme.raised
                            border.color: serverPreset.activeFocus ? Theme.accent : Theme.border
                        }
                        contentItem: Text {
                            leftPadding: 10; rightPadding: 24
                            text: serverPreset.displayText
                            color: Theme.text; font.pixelSize: 11
                            verticalAlignment: Text.AlignVCenter
                            elide: Text.ElideRight
                        }
                        indicator: Text {
                            x: serverPreset.width - 18
                            anchors.verticalCenter: parent.verticalCenter
                            text: "▾"; color: Theme.muted; font.pixelSize: 10
                        }
                        delegate: ItemDelegate {
                            required property var modelData
                            required property int index
                            width: serverPreset.width
                            height: 28
                            highlighted: serverPreset.highlightedIndex === index
                            contentItem: Text {
                                text: modelData
                                color: Theme.text; font.pixelSize: 11
                                verticalAlignment: Text.AlignVCenter
                                elide: Text.ElideRight
                            }
                            background: Rectangle { color: highlighted ? Theme.hover : "transparent"; radius: 6 }
                        }
                        popup: Popup {
                            y: serverPreset.height + 4
                            width: serverPreset.width
                            padding: 4
                            implicitHeight: contentItem.implicitHeight + 8
                            contentItem: ListView {
                                clip: true
                                implicitHeight: contentHeight
                                model: serverPreset.popup.visible ? serverPreset.delegateModel : null
                                currentIndex: serverPreset.highlightedIndex
                            }
                            background: Rectangle { color: Theme.surface; border.color: Theme.border; radius: 8 }
                        }
                    }

                    RowLayout {
                        spacing: 8
                        TextField {
                            id: urlField
                            text: appSettings.baseUrl
                            color: Theme.text
                            font.pixelSize: 11
                            implicitWidth: 230
                            // 직접 입력은 커스텀 프리셋에서만 활성
                            enabled: serverPreset.currentIndex === 3
                            opacity: enabled ? 1 : 0.5
                            background: Rectangle {
                                color: Theme.bg; radius: 7
                                border.color: urlField.activeFocus ? Theme.accent : Theme.border
                            }
                        }
                        Rectangle {
                            width: applyLbl.implicitWidth + 18; height: 30; radius: 8
                            color: Theme.raised; border.color: Theme.border
                            opacity: serverPreset.currentIndex === 3 ? 1 : 0.5
                            Text { id: applyLbl; anchors.centerIn: parent; text: "적용"; color: Theme.text; font.pixelSize: 12 }
                            MouseArea {
                                anchors.fill: parent; cursorShape: Qt.PointingHandCursor
                                enabled: serverPreset.currentIndex === 3
                                onClicked: {
                                    const previous = appSettings.baseUrl
                                    if (appSettings.applyBaseUrl(urlField.text.trim())) {
                                        urlField.text = appSettings.baseUrl
                                        win.showToast("서버 주소 변경됨",
                                                      previous === appSettings.baseUrl
                                                      ? appSettings.baseUrl
                                                      : appSettings.baseUrl + " — 다시 로그인해 주세요")
                                    } else {
                                        urlField.text = appSettings.baseUrl
                                        win.showToast("서버 주소를 적용하지 못했습니다",
                                                      "HTTPS 주소 또는 localhost/loopback HTTP 주소만 사용할 수 있습니다")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            SettingCard {
                title: "개인정보 동의 복구"
                desc: consentStatus.loading ? "서버에서 현재 계정의 동의 상태를 확인하고 있습니다"
                    : consentStatus.errorText.length > 0 ? consentStatus.errorText
                    : !consentStatus.loaded ? "동의 상태를 아직 확인하지 못했습니다"
                    : consentStatus.requiredConsentsMissing
                        ? "이용약관 또는 개인정보 필수 동의가 없어 일부 기능이 제한됩니다"
                        : "필수 동의 정상 · AI 데이터 " + (consentStatus.aiDataAgreed ? "동의" : "미동의")
                          + " · 이력서 분석 " + (consentStatus.resumeAnalysisAgreed ? "동의" : "미동의")
                Row {
                    spacing: 6
                    Rectangle {
                        width: recheckConsentLabel.implicitWidth + 22; height: 30; radius: 8
                        color: Theme.raised; border.color: activeFocus ? Theme.accent : Theme.border
                        activeFocusOnTab: true
                        Accessible.role: Accessible.Button
                        Accessible.name: "개인정보 동의 상태 다시 확인"
                        function refreshConsent() { consentStatus.refresh() }
                        Keys.onPressed: (event) => {
                            if (event.key === Qt.Key_Return || event.key === Qt.Key_Enter || event.key === Qt.Key_Space) {
                                event.accepted = true
                                refreshConsent()
                            }
                        }
                        Text { id: recheckConsentLabel; anchors.centerIn: parent; text: consentStatus.loading ? "확인 중…" : "다시 확인"; color: Theme.text; font.pixelSize: 12 }
                        MouseArea { anchors.fill: parent; enabled: !consentStatus.loading; cursorShape: Qt.PointingHandCursor; onClicked: parent.refreshConsent() }
                    }
                    Rectangle {
                        width: privacyWebLabel.implicitWidth + 22; height: 30; radius: 8
                        color: Theme.raised
                        border.color: activeFocus ? Theme.accent : Theme.border
                        activeFocusOnTab: true
                        Accessible.role: Accessible.Button
                        Accessible.name: "웹에서 개인정보 동의 관리 열기"
                        Keys.onPressed: (event) => {
                            if (event.key === Qt.Key_Return || event.key === Qt.Key_Enter || event.key === Qt.Key_Space) {
                                event.accepted = true
                                root.openWebSettings("privacy")
                            }
                        }
                        Text { id: privacyWebLabel; anchors.centerIn: parent; text: "웹에서 동의 관리 ↗"; color: Theme.accentText; font.pixelSize: 12 }
                        MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: root.openWebSettings("privacy") }
                    }
                }
            }

            SettingCard {
                title: "계정 삭제"
                desc: "본인 확인과 삭제 범위 안내가 필요한 작업입니다. 웹 계정 설정에서 안전하게 진행하세요"
                Rectangle {
                    width: deleteWebLabel.implicitWidth + 22; height: 30; radius: 8
                    color: Theme.raised
                    border.color: activeFocus ? Theme.accent : Theme.border
                    activeFocusOnTab: true
                    Accessible.role: Accessible.Button
                    Accessible.name: "웹 계정 삭제 설정 열기"
                    Keys.onPressed: (event) => {
                        if (event.key === Qt.Key_Return || event.key === Qt.Key_Enter || event.key === Qt.Key_Space) {
                            event.accepted = true
                            root.openWebSettings("account")
                        }
                    }
                    Text { id: deleteWebLabel; anchors.centerIn: parent; text: "웹 계정 설정 ↗"; color: Theme.danger; font.pixelSize: 12 }
                    MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: root.openWebSettings("account") }
                }
            }

            SettingCard {
                title: "로그아웃"
                desc: auth.userEmail !== "" ? auth.userEmail + " 계정에서 로그아웃" : "현재 계정에서 로그아웃"
                Rectangle {
                    width: outLbl.implicitWidth + 22; height: 30; radius: 8
                    color: Theme.raised; border.color: Theme.border
                    Text { id: outLbl; anchors.centerIn: parent; text: "로그아웃"; color: Theme.danger; font.pixelSize: 12 }
                    MouseArea { anchors.fill: parent; cursorShape: Qt.PointingHandCursor; onClicked: auth.logout() }
                }
            }

            Text { text: "알림 · 화면"; color: Theme.muted; font.pixelSize: 11; font.bold: true; Layout.leftMargin: 2; Layout.topMargin: 10 }

            SettingCard {
                title: "Windows 토스트"
                desc: "새 알림을 Windows 토스트로 표시합니다. 웹 계정의 전체 끄기·카테고리·방해금지 시간·유형별 설정도 함께 따릅니다"
                Toggle {
                    accessibleName: "Windows 토스트"
                    checked: appSettings.trayNotify
                    onToggled: (v) => appSettings.trayNotify = v
                }
            }

            SettingCard {
                title: "테마"
                desc: appSettings.darkTheme
                      ? "다크 · 딥 블랙 + 인디고"
                      : "라이트 · 오프화이트 + 인디고"
                Toggle {
                    accessibleName: "다크 테마"
                    checked: appSettings.darkTheme
                    onToggled: (v) => appSettings.darkTheme = v
                }
            }
        }
    }
}
