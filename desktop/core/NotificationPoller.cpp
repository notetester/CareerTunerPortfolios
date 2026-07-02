#include "NotificationPoller.h"
#include "ApiClient.h"
#include <QJsonArray>
#include <QJsonObject>

NotificationPoller::NotificationPoller(ApiClient* api, QObject* parent)
    : QObject(parent), m_api(api)
{
    m_timer.setInterval(30 * 1000); // 웹 NotificationBell 과 동일 주기
    connect(&m_timer, &QTimer::timeout, this, &NotificationPoller::pollNow);
}

void NotificationPoller::start()
{
    pollNow();
    m_timer.start();
}

void NotificationPoller::stop()
{
    m_timer.stop();
    m_lastMaxId = -1;
    m_unread = 0;
    emit unreadChanged();
}

void NotificationPoller::pollNow()
{
    m_api->get(QStringLiteral("/api/notifications/preferences"),
        [this](bool ok, const QJsonValue& data, const QString&) {
            if (ok) {
                updatePreferences(data.toObject());
            }
            pollNotifications();
        });
}

void NotificationPoller::pollNotifications()
{
    m_api->get(QStringLiteral("/api/notifications?page=0&size=20"),
        [this](bool ok, const QJsonValue& data, const QString&) {
            if (!ok) return;
            const QJsonObject o = data.toObject();
            const QJsonArray arr = o.value("notifications").toArray();

            int unread = 0;
            qint64 maxId = m_lastMaxId;
            const bool baseline = (m_lastMaxId < 0); // 첫 폴링: 토스트 없이 기준선만

            for (const QJsonValue& v : arr) {
                const QJsonObject n = v.toObject();
                const qint64 id = n.value("id").toInteger();
                if (!n.value("read").toBool()) ++unread;
                if (id > maxId) maxId = id;

                if (!baseline && id > m_lastMaxId && !n.value("read").toBool()) {
                    const QString type = n.value("type").toString();
                    const bool desktopToast = channelEnabled(type, QStringLiteral("desktopToast"));
                    const bool desktopTaskbar = channelEnabled(type, QStringLiteral("desktopTaskbar"));
                    if (!desktopToast && !desktopTaskbar) {
                        continue;
                    }
                    emit notificationArrived(
                        type,
                        n.value("title").toString(),
                        n.value("message").toString(),
                        n.value("link").toString(),
                        n.value("targetId").toInteger(),
                        desktopToast,
                        desktopTaskbar);
                }
            }
            m_lastMaxId = maxId;
            if (unread != m_unread) {
                m_unread = unread;
                emit unreadChanged();
            }
        });
}

void NotificationPoller::updatePreferences(const QJsonObject& data)
{
    QHash<QString, bool> desktopToastByType;
    QHash<QString, bool> desktopTaskbarByType;
    const QJsonObject rules = data.value(QStringLiteral("rules")).toObject();
    for (auto it = rules.begin(); it != rules.end(); ++it) {
        const QString type = it.key();
        const QJsonObject rule = it.value().toObject();
        const bool enabled = rule.value(QStringLiteral("enabled")).toBool(true);
        const QJsonObject channels = rule.value(QStringLiteral("channels")).toObject();
        desktopToastByType.insert(
            type,
            enabled && channels.value(QStringLiteral("desktopToast")).toBool(true));
        desktopTaskbarByType.insert(
            type,
            enabled && channels.value(QStringLiteral("desktopTaskbar")).toBool(true));
    }
    m_desktopToastByType = desktopToastByType;
    m_desktopTaskbarByType = desktopTaskbarByType;
}

bool NotificationPoller::channelEnabled(const QString& type, const QString& channel) const
{
    if (channel == QStringLiteral("desktopTaskbar")) {
        return m_desktopTaskbarByType.value(type, true);
    }
    return m_desktopToastByType.value(type, true);
}

void NotificationPoller::markAllRead()
{
    m_api->post(QStringLiteral("/api/notifications/read-all"), QJsonObject(),
        [this](bool ok, const QJsonValue&, const QString&) {
            if (!ok) return;
            m_unread = 0;
            emit unreadChanged();
        });
}
