#pragma once
#include <QObject>
#include <QTimer>
#include <QString>

class ApiClient;

// 알림 폴링 (웹과 동일한 30초 주기).
// 첫 폴링은 기준선만 잡고(토스트 X), 이후 새로 생긴 안읽음 알림을 시그널로 쏜다.
// → main.cpp 에서 트레이 토스트 + QML 알림칩에 연결.
class NotificationPoller : public QObject
{
    Q_OBJECT
    Q_PROPERTY(int unread READ unread NOTIFY unreadChanged)
public:
    explicit NotificationPoller(ApiClient* api, QObject* parent = nullptr);

    int unread() const { return m_unread; }

    Q_INVOKABLE void start();
    Q_INVOKABLE void stop();
    Q_INVOKABLE void pollNow();
    Q_INVOKABLE void markAllRead();

signals:
    void unreadChanged();
    // 새 알림 도착 (폴링 간격 내 신규 + 미읽음)
    void notificationArrived(const QString& type, const QString& title,
                             const QString& message, const QString& link,
                             qint64 targetId);

private:
    ApiClient* m_api;
    QTimer m_timer;
    qint64 m_lastMaxId = -1;  // -1 = 아직 기준선 없음
    int m_unread = 0;
};
