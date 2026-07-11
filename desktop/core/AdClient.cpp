#include "AdClient.h"

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

AdClient::AdClient(ApiClient* api, QObject* parent)
    : QObject(parent), m_api(api)
{
}

void AdClient::refresh()
{
    if (!m_api) return;
    // 서빙: placement 는 필수. 데스크톱 홈 배너 1건만 요청.
    m_api->get(QStringLiteral("/api/ads?placement=HOME_BANNER&platform=DESKTOP&limit=1"),
               [this](bool ok, const QJsonValue& data, const QString&) {
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
    applyEmpty();
}

void AdClient::openTarget()
{
    if (!m_api || !m_visible || m_adId <= 0) return;
    // 클릭 집계 후 서버가 돌려주는 linkUrl 로 외부 브라우저를 연다.
    // (SPA 302 대신 URL 반환 계약 — 응답 linkUrl 을 우선하고, 없으면 캐시된 m_targetUrl 로 폴백.)
    m_api->post(QStringLiteral("/api/ads/%1/click").arg(m_adId), QJsonObject(),
                [this](bool ok, const QJsonValue& data, const QString&) {
        QString url;
        if (ok && data.isObject()) {
            url = data.toObject().value(QStringLiteral("linkUrl")).toString();
        }
        if (url.isEmpty()) {
            url = m_targetUrl;
        }
        const QUrl target = safeAdTarget(url, m_api->baseUrl());
        if (target.isValid() && !target.isEmpty()) {
            QDesktopServices::openUrl(target);
        }
    });
}

void AdClient::recordImpression()
{
    if (!m_api || m_adId <= 0) return;
    // 본문 없는 POST. best-effort — 결과는 무시.
    m_api->post(QStringLiteral("/api/ads/%1/impression").arg(m_adId), QJsonObject(),
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
