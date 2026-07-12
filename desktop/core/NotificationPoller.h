#pragma once
#include <QObject>
#include <QTimer>
#include <QString>
#include <QHash>
#include <QJsonObject>
#include <QVariant>

class ApiClient;
class DesktopCoreTests;

// 알림 폴링 (웹과 동일한 30초 주기).
// 첫 폴링은 기준선만 잡고(토스트 X), 이후 새로 생긴 안읽음 알림을 시그널로 쏜다.
// → main.cpp 에서 트레이 토스트 + QML 알림칩에 연결.
// 최근 20개 목록은 items 로 노출 → NotificationCenter.qml 이 그대로 그린다.
class NotificationPoller : public QObject
{
    Q_OBJECT
    Q_PROPERTY(int unread READ unread NOTIFY unreadChanged)
    Q_PROPERTY(QVariantList items READ items NOTIFY itemsChanged)
public:
    explicit NotificationPoller(ApiClient* api, QObject* parent = nullptr);

    int unread() const { return m_unread; }
    QVariantList items() const { return m_items; }

    Q_INVOKABLE void start();
    Q_INVOKABLE void stop();
    Q_INVOKABLE void pollNow();
    Q_INVOKABLE void markAsRead(qint64 id);
    Q_INVOKABLE void markAllRead();

signals:
    void unreadChanged();
    void itemsChanged();
    // 새 알림 도착 (폴링 간격 내 신규 + 미읽음)
    void notificationArrived(const QString& type, const QString& title,
                             const QString& message, const QString& link,
                             const QString& targetType, qint64 targetId,
                             bool desktopToast,
                             bool desktopTaskbar);

private:
    friend class DesktopCoreTests;
    void pollNotifications(quint64 pollGeneration);
    bool updatePreferences(const QJsonObject& data);
    bool channelEnabled(const QString& type, const QString& channel) const;
    bool senderEnabled(const QString& type, const QString& relation) const;
    static QString categoryForType(const QString& type);
    bool globalDeliveryEnabled(const QString& category) const;
    bool withinQuietHours() const;

    ApiClient* m_api;
    QTimer m_timer;
    qint64 m_lastMaxId = -1;  // -1 = 아직 기준선 없음
    int m_unread = 0;
    // 최근 20개 알림 (각 항목: id/type/title/message/link/targetType/targetId/isRead/createdAt/senderRelation/actorName)
    QVariantList m_items;
    QHash<QString, bool> m_desktopToastByType;
    QHash<QString, bool> m_desktopTaskbarByType;
    // type → (발신자 관계 → 수신 여부). 확인되지 않은/누락된 값은 fail-closed 한다.
    QHash<QString, QHash<QString, bool>> m_sendersByType;
    bool m_pushEnabled = false;
    bool m_preferencesConfirmed = false;
    QHash<QString, bool> m_categories;
    QString m_quietHoursStart;
    QString m_quietHoursEnd;
    quint64 m_pollGeneration = 0;
    quint64 m_mutationGeneration = 0;
};
