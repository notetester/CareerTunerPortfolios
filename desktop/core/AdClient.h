#pragma once

#include <QObject>
#include <QString>
#include <QUrl>

#include "ApiClient.h"

class SettingsStore;
class DesktopCoreTests;

// 데스크톱 홈 배너 광고 클라이언트.
// 살아있는 백엔드 ads API 계약에 맞춘다:
//   서빙 GET /api/ads?placement=HOME_BANNER&platform=DESKTOP&limit=1
//        → ApiResponse<List<AdResponse{ id, title, imageUrl, linkUrl, placement, targetPlatform }>>
//   임프레션 POST /api/ads/{id}/impression (본문 없음)
//   클릭     POST /api/ads/{id}/click → AdClickResponse{ id, linkUrl }
// 백엔드에는 body/targetUrl 필드가 없다. 이동 URL 은 linkUrl 이며 m_targetUrl 에 담는다.
// body 는 계약에 없으므로 항상 빈 문자열 — Main.qml 이 title-only 배너를 그린다.
// 데스크톱은 QtWebEngine 불가 — 순수 QML Item/Image/Text 만 사용한다.
class AdClient : public QObject
{
    Q_OBJECT
    Q_PROPERTY(bool visible READ visible NOTIFY changed)
    Q_PROPERTY(QString title READ title NOTIFY changed)
    // body 는 백엔드에 없어 항상 빈 문자열이지만, Main.qml 이 참조하므로 시그니처는 유지한다.
    Q_PROPERTY(QString body READ body NOTIFY changed)
    // 이동 URL(백엔드 linkUrl). Main.qml 은 이 값이 있을 때만 "자세히"/클릭 커서를 표시한다.
    Q_PROPERTY(QString targetUrl READ targetUrl NOTIFY changed)
    // 이미지 경로(/api/ads/{id}/image). 없으면 빈 문자열 → 텍스트 배너.
    // Main.qml 에서 appSettings.baseUrl + imageUrl 로 Image source 구성에 사용할 수 있다.
    Q_PROPERTY(QString imageUrl READ imageUrl NOTIFY changed)

public:
    explicit AdClient(ApiClient* api, SettingsStore* settings = nullptr, QObject* parent = nullptr);

    bool visible() const { return m_visible; }
    QString title() const { return m_title; }
    QString body() const { return m_body; }
    QString targetUrl() const { return m_targetUrl; }
    QString imageUrl() const { return m_imageUrl; }

    Q_INVOKABLE void refresh();
    Q_INVOKABLE void clear();
    Q_INVOKABLE void openTarget();

signals:
    void changed();
    void targetOpened(const QUrl& url);

private:
    friend class DesktopCoreTests;
    void recordImpression();
    void applyEmpty();

    ApiClient* m_api;
    SettingsStore* m_settings;
    quint64 m_refreshGeneration = 0;
    quint64 m_lifecycleGeneration = 0;
    qint64 m_adId = 0;
    bool m_visible = false;
    QString m_title;
    QString m_body;      // 백엔드에 없음 — 항상 빈 값 유지
    QString m_targetUrl; // = AdResponse.linkUrl
    QString m_imageUrl;  // = AdResponse.imageUrl (nullable)
};
