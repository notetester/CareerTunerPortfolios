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
    m_items.clear();
    emit unreadChanged();
    emit itemsChanged();
}

void NotificationPoller::pollNow()
{
    // LOCAL 파일 공유 게이트용 데스크톱 presence heartbeat — 폴링 틱마다 best-effort 로
    // 남긴다(실패 무시). 폴러는 로그인 시 start(), 로그아웃 시 stop() 되므로 로그인 상태에서만 돈다.
    m_api->post(QStringLiteral("/api/collaboration/desktop-presence"), QJsonObject(),
        [](bool, const QJsonValue&, const QString&) {});
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
    m_api->get(QStringLiteral("/api/notifications?page=0&size=20&platform=DESKTOP"),
        [this](bool ok, const QJsonValue& data, const QString&) {
            if (!ok) return;
            const QJsonObject o = data.toObject();
            const QJsonArray arr = o.value("notifications").toArray();

            int unread = 0;
            qint64 maxId = m_lastMaxId;
            const bool baseline = (m_lastMaxId < 0); // 첫 폴링: 토스트 없이 기준선만
            QVariantList items;

            for (const QJsonValue& v : arr) {
                const QJsonObject n = v.toObject();
                const qint64 id = n.value("id").toInteger();
                const bool read = n.value("read").toBool();
                const QString type = n.value("type").toString();
                const QString relation = n.value("senderRelation").toString();
                if (!read) ++unread;
                if (id > maxId) maxId = id;

                // 알림 센터(QML) 표시용 목록 — 억제 여부와 무관하게 전부 담는다
                items.push_back(QVariantMap{
                    {"id", id},
                    {"type", type},
                    {"title", n.value("title").toString()},
                    {"message", n.value("message").toString()},
                    {"link", n.value("link").toString()},
                    {"targetType", n.value("targetType").toString()},
                    {"targetId", n.value("targetId").toInteger()},
                    {"isRead", read},
                    {"createdAt", n.value("createdAt").toString()},
                    {"senderRelation", relation},
                    {"actorName", n.value("actor").toObject().value("name").toString()}
                });

                if (!baseline && id > m_lastMaxId && !read) {
                    const bool desktopToast = channelEnabled(type, QStringLiteral("desktopToast"));
                    const bool desktopTaskbar = channelEnabled(type, QStringLiteral("desktopTaskbar"));
                    // 채널이 전부 꺼졌거나, 발신자 관계(모르는 사람/친구/기업/운영자)가 꺼진
                    // 알림은 토스트/작업표시줄 없이 목록에만 남긴다
                    if ((!desktopToast && !desktopTaskbar) || !senderEnabled(type, relation)) {
                        continue;
                    }
                    emit notificationArrived(
                        type,
                        n.value("title").toString(),
                        n.value("message").toString(),
                        n.value("link").toString(),
                        n.value("targetType").toString(),
                        n.value("targetId").toInteger(),
                        desktopToast,
                        desktopTaskbar);
                }
            }
            m_lastMaxId = maxId;
            if (items != m_items) {
                m_items = items;
                emit itemsChanged();
            }
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
    QHash<QString, QHash<QString, bool>> sendersByType;
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
        // 발신자 관계별 수신 설정 — 관계 기반 알림(댓글·답글·쪽지·채팅 등)에만 내려온다
        const QJsonObject senders = rule.value(QStringLiteral("senders")).toObject();
        if (!senders.isEmpty()) {
            QHash<QString, bool> byRelation;
            for (auto sit = senders.begin(); sit != senders.end(); ++sit) {
                byRelation.insert(sit.key(), sit.value().toBool(true));
            }
            sendersByType.insert(type, byRelation);
        }
    }
    m_desktopToastByType = desktopToastByType;
    m_desktopTaskbarByType = desktopTaskbarByType;
    m_sendersByType = sendersByType;
}

bool NotificationPoller::channelEnabled(const QString& type, const QString& channel) const
{
    if (channel == QStringLiteral("desktopTaskbar")) {
        return m_desktopTaskbarByType.value(type, true);
    }
    return m_desktopToastByType.value(type, true);
}

bool NotificationPoller::senderEnabled(const QString& type, const QString& relation) const
{
    // 관계 미상(빈 값)은 필터하지 않고 통과 — 서버 senderEnabled 와 동일 규칙
    if (relation.isEmpty()) {
        return true;
    }
    const auto it = m_sendersByType.constFind(type);
    if (it == m_sendersByType.constEnd()) {
        return true;
    }
    return it->value(relation, true);
}

void NotificationPoller::markAsRead(qint64 id)
{
    m_api->patch(QStringLiteral("/api/notifications/%1/read").arg(id), QJsonObject(),
        [this, id](bool ok, const QJsonValue&, const QString&) {
            if (!ok) return;
            // 서버 반영 성공 → 다음 폴링을 기다리지 않고 로컬 목록/카운트를 즉시 갱신
            bool changed = false;
            for (QVariant& item : m_items) {
                QVariantMap map = item.toMap();
                if (map.value("id").toLongLong() == id && !map.value("isRead").toBool()) {
                    map.insert("isRead", true);
                    item = map;
                    changed = true;
                }
            }
            if (changed) {
                emit itemsChanged();
                if (m_unread > 0) {
                    --m_unread;
                    emit unreadChanged();
                }
            }
        });
}

void NotificationPoller::markAllRead()
{
    m_api->post(QStringLiteral("/api/notifications/read-all"), QJsonObject(),
        [this](bool ok, const QJsonValue&, const QString&) {
            if (!ok) return;
            bool changed = false;
            for (QVariant& item : m_items) {
                QVariantMap map = item.toMap();
                if (!map.value("isRead").toBool()) {
                    map.insert("isRead", true);
                    item = map;
                    changed = true;
                }
            }
            if (changed) emit itemsChanged();
            if (m_unread != 0) {
                m_unread = 0;
                emit unreadChanged();
            }
        });
}
