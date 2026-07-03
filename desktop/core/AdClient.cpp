#include "AdClient.h"

#include <QDesktopServices>
#include <QJsonArray>
#include <QJsonObject>
#include <QUrl>

AdClient::AdClient(ApiClient* api, QObject* parent)
    : QObject(parent), m_api(api)
{
}

void AdClient::refresh()
{
    if (!m_api) return;
    m_api->get(QStringLiteral("/api/ads?surface=DESKTOP"), [this](bool ok, const QJsonValue& data, const QString&) {
        if (!ok || !data.isArray() || data.toArray().isEmpty()) {
            applyEmpty();
            return;
        }

        const QJsonObject item = data.toArray().first().toObject();
        m_campaignId = item.value(QStringLiteral("id")).toVariant().toLongLong();
        m_title = item.value(QStringLiteral("title")).toString();
        m_body = item.value(QStringLiteral("body")).toString();
        m_targetUrl = item.value(QStringLiteral("targetUrl")).toString();
        m_visible = !m_title.isEmpty();
        emit changed();
        recordEvent(QStringLiteral("IMPRESSION"));
    });
}

void AdClient::clear()
{
    applyEmpty();
}

void AdClient::openTarget()
{
    if (!m_visible || m_targetUrl.isEmpty()) return;
    recordEvent(QStringLiteral("CLICK"));
    QDesktopServices::openUrl(QUrl(m_targetUrl));
}

void AdClient::recordEvent(const QString& eventType)
{
    if (!m_api || m_campaignId <= 0) return;
    QJsonObject body;
    body.insert(QStringLiteral("surface"), QStringLiteral("DESKTOP"));
    body.insert(QStringLiteral("eventType"), eventType);
    m_api->post(QStringLiteral("/api/ads/%1/events").arg(m_campaignId), body, [](bool, const QJsonValue&, const QString&) {});
}

void AdClient::applyEmpty()
{
    m_campaignId = 0;
    m_visible = false;
    m_title.clear();
    m_body.clear();
    m_targetUrl.clear();
    emit changed();
}
