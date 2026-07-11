#pragma once

#include <QObject>
#include <QSet>
#include <QTimer>
#include <QVariant>

class ApiClient;
class QDateTime;
class QJsonArray;
class QJsonObject;
class DesktopCoreTests;

class PlannerClient : public QObject
{
    Q_OBJECT
    Q_PROPERTY(QVariantList items READ items NOTIFY itemsChanged)
    Q_PROPERTY(QString statusText READ statusText NOTIFY statusTextChanged)
    Q_PROPERTY(bool active READ active NOTIFY activeChanged)
public:
    explicit PlannerClient(ApiClient* api, QObject* parent = nullptr);

    QVariantList items() const { return m_items; }
    QString statusText() const { return m_statusText; }
    bool active() const { return m_active; }

    Q_INVOKABLE void start();
    Q_INVOKABLE void stop();
    Q_INVOKABLE void refreshNow();
    void setAccountScope(const QString& accountScope);

signals:
    void itemsChanged();
    void statusTextChanged();
    void activeChanged();
    void reminderArrived(const QString& title, const QString& message,
                         bool desktopToast, bool desktopTaskbar, bool desktopSound);

private:
    friend class DesktopCoreTests;
    void setActive(bool active);
    void setStatusText(const QString& text);
    void loadFiredReminderIds();
    void storeFiredReminderIds() const;
    QString firedReminderSettingsKey() const;
    void applyDashboard(const QJsonObject& dashboard);
    QVariantMap scheduleItemToOverlay(const QJsonObject& item) const;
    QVariantMap memoToOverlay(const QJsonObject& memo) const;
    void fireDueReminders(const QJsonArray& schedules);
    QString scheduleTimeLabel(const QJsonObject& item) const;
    QDateTime parseDateTime(const QString& value) const;
    bool channelsContain(const QJsonArray& channels, const QString& channel) const;

    ApiClient* m_api;
    QTimer m_timer;
    QVariantList m_items;
    QString m_statusText;
    bool m_active = false;
    QSet<qint64> m_firedReminderIds;
    QString m_accountScope;
    quint64 m_refreshGeneration = 0;
};
