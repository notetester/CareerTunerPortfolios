#include "AdClient.h"
#include "SettingsStore.h"

#include <QDesktopServices>
#include <QJsonArray>
#include <QJsonObject>
#include <QUrl>

namespace {

bool hasUnsafeNavigationCharacter(const QString& value)
{
    for (const QChar character : value) {
        if (character == QLatin1Char('\\')
            || character.isSpace()
            || character.category() == QChar::Other_Control) {
            return true;
        }
    }
    return false;
}

QUrl safeAdTarget(const QString& rawValue, const QString& baseUrl)
{
    const QString value = rawValue.trimmed();
    if (value.isEmpty() || hasUnsafeNavigationCharacter(value)) {
        return {};
    }

    // 서비스 내부 경로는 반드시 하나의 '/'로 시작해야 한다. '//host' 형태의 외부 이동은 거부한다.
    if (value.startsWith(QLatin1Char('/'))) {
        if (value.startsWith(QStringLiteral("//"))) {
            return {};
        }
        const QUrl base(baseUrl, QUrl::StrictMode);
        const QUrl relative(value, QUrl::StrictMode);
        if (!base.isValid() || base.host().isEmpty()
            || (base.scheme().compare(QStringLiteral("http"), Qt::CaseInsensitive) != 0
                && base.scheme().compare(QStringLiteral("https"), Qt::CaseInsensitive) != 0)
            || !relative.isValid()) {
            return {};
        }
        return base.resolved(relative);
    }

    const QUrl target(value, QUrl::StrictMode);
    if (!target.isValid() || target.host().isEmpty()) {
        return {};
    }
    const QString scheme = target.scheme().toLower();
    return scheme == QStringLiteral("http") || scheme == QStringLiteral("https")
        ? target
        : QUrl();
}

} // namespace

AdClient::AdClient(ApiClient* api, SettingsStore* settings, QObject* parent)
    : QObject(parent), m_api(api), m_settings(settings)
{
}

void AdClient::refresh()
{
    if (!m_api) return;
    const quint64 refreshGeneration = ++m_refreshGeneration;
    const quint64 lifecycleGeneration = m_lifecycleGeneration;
    // 서빙: placement 는 필수. 데스크톱 홈 배너 1건만 요청.
    m_api->get(QStringLiteral("/api/ads?placement=HOME_BANNER&platform=DESKTOP&limit=1"),
               [this, refreshGeneration, lifecycleGeneration](bool ok, const QJsonValue& data, const QString&) {
        if (refreshGeneration != m_refreshGeneration
            || lifecycleGeneration != m_lifecycleGeneration) return;
        if (!ok || !data.isArray() || data.toArray().isEmpty()) {
            applyEmpty();
            return;
        }

        const QJsonObject item = data.toArray().first().toObject();
        m_adId = item.value(QStringLiteral("id")).toVariant().toLongLong();
        m_title = item.value(QStringLiteral("title")).toString();
        // linkUrl 이 이동 URL. body/targetUrl 필드는 백엔드에 없다.
        m_targetUrl = item.value(QStringLiteral("linkUrl")).toString();
        // imageUrl 은 nullable — 없으면 빈 문자열(텍스트 배너).
        m_imageUrl = item.value(QStringLiteral("imageUrl")).toString();
        m_body.clear(); // 계약에 없음 — Main.qml 이 title-only 배너를 그린다.
        m_visible = m_adId > 0 && !m_title.isEmpty();
        emit changed();

        if (m_visible) {
            recordImpression();
        }
    });
}

void AdClient::clear()
{
    ++m_lifecycleGeneration;
    ++m_refreshGeneration;
    applyEmpty();
}

void AdClient::openTarget()
{
    if (!m_api || !m_visible || m_adId <= 0) return;
    const qint64 adId = m_adId;
    const QString fallbackTargetUrl = m_targetUrl;
    const QString webOrigin = m_settings ? m_settings->webAppUrl() : m_api->baseUrl();
    const quint64 lifecycleGeneration = m_lifecycleGeneration;
    // 클릭 집계 후 서버가 돌려주는 linkUrl 로 외부 브라우저를 연다.
    // (SPA 302 대신 URL 반환 계약 — 응답 linkUrl 을 우선하고, 없으면 캐시된 m_targetUrl 로 폴백.)
    m_api->post(QStringLiteral("/api/ads/%1/click").arg(adId), QJsonObject(),
                [this, fallbackTargetUrl, webOrigin, lifecycleGeneration](bool ok, const QJsonValue& data, const QString&) {
        if (lifecycleGeneration != m_lifecycleGeneration) return;
        QString url;
        if (ok && data.isObject()) {
            url = data.toObject().value(QStringLiteral("linkUrl")).toString();
        }
        if (url.isEmpty()) {
            url = fallbackTargetUrl;
        }
        const QUrl target = safeAdTarget(url, webOrigin);
        if (target.isValid() && !target.isEmpty()) {
            emit targetOpened(target);
            QDesktopServices::openUrl(target);
        }
    });
}

void AdClient::recordImpression()
{
    if (!m_api || m_adId <= 0) return;
    const qint64 adId = m_adId;
    // 본문 없는 POST. best-effort — 결과는 무시.
    m_api->post(QStringLiteral("/api/ads/%1/impression").arg(adId), QJsonObject(),
                [](bool, const QJsonValue&, const QString&) {});
}

void AdClient::applyEmpty()
{
    m_adId = 0;
    m_visible = false;
    m_title.clear();
    m_body.clear();
    m_targetUrl.clear();
    m_imageUrl.clear();
    emit changed();
}
