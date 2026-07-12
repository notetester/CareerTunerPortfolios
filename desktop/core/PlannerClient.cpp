#include "PlannerClient.h"
#include "ApiClient.h"
#include "SettingsStore.h"

#include <QDateTime>
#include <QCryptographicHash>
#include <QJsonArray>
#include <QJsonObject>
#include <QSettings>
#include <QUrl>
#include <QUrlQuery>

namespace {
QString dashboardRangePath()
{
    const QDateTime now = QDateTime::currentDateTime();
    const QString from = now.addDays(-1).toString(QStringLiteral("yyyy-MM-ddTHH:mm:ss"));
    const QString to = now.addDays(30).toString(QStringLiteral("yyyy-MM-ddTHH:mm:ss"));
    QUrlQuery query;
    query.addQueryItem(QStringLiteral("from"), from);
    query.addQueryItem(QStringLiteral("to"), to);
    return QStringLiteral("/api/planner?%1").arg(query.toString(QUrl::FullyEncoded));
}
}

PlannerClient::PlannerClient(ApiClient* api, QObject* parent)
    : QObject(parent)
    , m_api(api)
{
    m_timer.setInterval(60 * 1000);
    connect(&m_timer, &QTimer::timeout, this, &PlannerClient::refreshNow);
}

void PlannerClient::setAccountScope(const QString& accountScope)
{
    const QString normalized = accountScope.trimmed().toCaseFolded();
    if (normalized == m_accountScope) return;
    if (!m_accountScope.isEmpty()) storeFiredReminderIds();
    ++m_refreshGeneration;
    m_accountScope = normalized;
    m_firedReminderIds.clear();
    loadFiredReminderIds();
}

void PlannerClient::start()
{
    if (m_active) return;
    setActive(true);
    refreshNow();
    m_timer.start();
}

void PlannerClient::stop()
{
    ++m_refreshGeneration;
    m_timer.stop();
    setActive(false);
    m_items.clear();
    setStatusText(QString());
    emit itemsChanged();
}

void PlannerClient::refreshNow()
{
    if (!m_api || !m_active) return;
    const quint64 generation = ++m_refreshGeneration;
    const QString accountScope = m_accountScope;
    m_api->get(dashboardRangePath(), [this, generation, accountScope](bool ok, const QJsonValue& data, const QString& message) {
        if (!m_active || generation != m_refreshGeneration || accountScope != m_accountScope) return;
        if (!ok) {
            setStatusText(message.isEmpty() ? QStringLiteral("플래너를 불러오지 못했습니다") : message);
            return;
        }
        applyDashboard(data.toObject());
    });
}

void PlannerClient::setActive(bool active)
{
    if (m_active == active) return;
    m_active = active;
    emit activeChanged();
}

void PlannerClient::setStatusText(const QString& text)
{
    if (m_statusText == text) return;
    m_statusText = text;
    emit statusTextChanged();
}

void PlannerClient::loadFiredReminderIds()
{
    if (m_accountScope.isEmpty()) return;
    auto settings = SettingsStore::createSettings();
    const QStringList raw = settings->value(firedReminderSettingsKey()).toStringList();
    for (const QString& value : raw) {
        bool ok = false;
        const qint64 id = value.toLongLong(&ok);
        if (ok) m_firedReminderIds.insert(id);
    }
}

void PlannerClient::storeFiredReminderIds() const
{
    if (m_accountScope.isEmpty()) return;
    QStringList raw;
    int count = 0;
    for (qint64 id : m_firedReminderIds) {
        raw.append(QString::number(id));
        if (++count >= 200) break;
    }
    auto settings = SettingsStore::createSettings();
    settings->setValue(firedReminderSettingsKey(), raw);
}

QString PlannerClient::firedReminderSettingsKey() const
{
    const QByteArray digest = QCryptographicHash::hash(
        m_accountScope.toUtf8(), QCryptographicHash::Sha256).toHex().left(24);
    return QStringLiteral("planner/accounts/%1/firedReminderIds")
        .arg(QString::fromLatin1(digest));
}

void PlannerClient::applyDashboard(const QJsonObject& dashboard)
{
    QVariantList nextItems;
    const QJsonArray schedules = dashboard.value(QStringLiteral("scheduleItems")).toArray();
    const QJsonArray memos = dashboard.value(QStringLiteral("memos")).toArray();

    for (const QJsonValue& value : schedules) {
        const QJsonObject item = value.toObject();
        if (!item.value(QStringLiteral("overlayVisible")).toBool()) continue;
        if (item.value(QStringLiteral("status")).toString() == QStringLiteral("CANCELED")) continue;
        nextItems.push_back(scheduleItemToOverlay(item));
    }
    for (const QJsonValue& value : memos) {
        const QJsonObject memo = value.toObject();
        if (!memo.value(QStringLiteral("overlayVisible")).toBool()) continue;
        nextItems.push_back(memoToOverlay(memo));
    }

    fireDueReminders(schedules);
    if (nextItems != m_items) {
        m_items = nextItems;
        emit itemsChanged();
    }
    setStatusText(nextItems.isEmpty()
        ? QStringLiteral("표시 중인 메모나 일정이 없습니다")
        : QStringLiteral("표시 중 %1개").arg(nextItems.size()));
}

QVariantMap PlannerClient::scheduleItemToOverlay(const QJsonObject& item) const
{
    const QString company = item.value(QStringLiteral("applicationCompanyName")).toString();
    const QString jobTitle = item.value(QStringLiteral("applicationJobTitle")).toString();
    QString body = item.value(QStringLiteral("description")).toString();
    if (!company.isEmpty()) {
        const QString appLabel = jobTitle.isEmpty() ? company : QStringLiteral("%1 · %2").arg(company, jobTitle);
        body = body.isEmpty() ? appLabel : QStringLiteral("%1\n%2").arg(appLabel, body);
    }
    return QVariantMap{
        {QStringLiteral("type"), QStringLiteral("schedule")},
        {QStringLiteral("title"), item.value(QStringLiteral("title")).toString()},
        {QStringLiteral("body"), body},
        {QStringLiteral("meta"), scheduleTimeLabel(item)},
        {QStringLiteral("opacity"), item.value(QStringLiteral("opacity")).toDouble(0.92)},
        {QStringLiteral("pinned"), item.value(QStringLiteral("pinned")).toBool()},
        {QStringLiteral("clickThrough"), item.value(QStringLiteral("clickThrough")).toBool()},
        {QStringLiteral("color"), QStringLiteral("indigo")}
    };
}

QVariantMap PlannerClient::memoToOverlay(const QJsonObject& memo) const
{
    return QVariantMap{
        {QStringLiteral("type"), QStringLiteral("memo")},
        {QStringLiteral("title"), memo.value(QStringLiteral("title")).toString(QStringLiteral("메모"))},
        {QStringLiteral("body"), memo.value(QStringLiteral("content")).toString()},
        {QStringLiteral("meta"), QStringLiteral("메모")},
        {QStringLiteral("opacity"), memo.value(QStringLiteral("opacity")).toDouble(0.92)},
        {QStringLiteral("pinned"), memo.value(QStringLiteral("pinned")).toBool()},
        {QStringLiteral("clickThrough"), false},
        {QStringLiteral("color"), memo.value(QStringLiteral("color")).toString(QStringLiteral("yellow"))}
    };
}

void PlannerClient::fireDueReminders(const QJsonArray& schedules)
{
    const QDateTime now = QDateTime::currentDateTime();
    bool changed = false;
    for (const QJsonValue& scheduleValue : schedules) {
        const QJsonObject item = scheduleValue.toObject();
        const QJsonArray reminders = item.value(QStringLiteral("reminders")).toArray();
        for (const QJsonValue& reminderValue : reminders) {
            const QJsonObject reminder = reminderValue.toObject();
            const qint64 reminderId = reminder.value(QStringLiteral("id")).toInteger();
            if (m_firedReminderIds.contains(reminderId)) continue;
            if (reminder.value(QStringLiteral("status")).toString() != QStringLiteral("PENDING")) continue;
            const QDateTime remindAt = parseDateTime(reminder.value(QStringLiteral("remindAt")).toString());
            if (!remindAt.isValid() || remindAt > now) continue;

            const QJsonArray channels = reminder.value(QStringLiteral("channels")).toArray();
            const bool desktopToast = channelsContain(channels, QStringLiteral("DESKTOP_TOAST"));
            const bool desktopTaskbar = channelsContain(channels, QStringLiteral("DESKTOP_TASKBAR"));
            const bool desktopSound = reminder.value(QStringLiteral("soundEnabled")).toBool()
                || channelsContain(channels, QStringLiteral("DESKTOP_SOUND"));
            if (desktopToast || desktopTaskbar || desktopSound) {
                emit reminderArrived(item.value(QStringLiteral("title")).toString(),
                                     scheduleTimeLabel(item),
                                     desktopToast,
                                     desktopTaskbar,
                                     desktopSound);
            }
            m_firedReminderIds.insert(reminderId);
            changed = true;
        }
    }
    if (changed) storeFiredReminderIds();
}

QString PlannerClient::scheduleTimeLabel(const QJsonObject& item) const
{
    if (item.value(QStringLiteral("allDay")).toBool()
        || item.value(QStringLiteral("timingPrecision")).toString() == QStringLiteral("DAY")) {
        return QStringLiteral("하루종일");
    }
    const QDateTime start = parseDateTime(item.value(QStringLiteral("startAt")).toString());
    if (!start.isValid()) return QStringLiteral("시간 미정");
    const QString startText = start.toString(QStringLiteral("M월 d일 HH:mm"));
    const QDateTime end = parseDateTime(item.value(QStringLiteral("endAt")).toString());
    if (!end.isValid()) return startText;
    return QStringLiteral("%1-%2").arg(startText, end.toString(QStringLiteral("HH:mm")));
}

QDateTime PlannerClient::parseDateTime(const QString& value) const
{
    if (value.isEmpty()) return QDateTime();
    QDateTime parsed = QDateTime::fromString(value, Qt::ISODate);
    if (!parsed.isValid()) {
        parsed = QDateTime::fromString(value.left(19), QStringLiteral("yyyy-MM-ddTHH:mm:ss"));
    }
    return parsed;
}

bool PlannerClient::channelsContain(const QJsonArray& channels, const QString& channel) const
{
    for (const QJsonValue& value : channels) {
        if (value.toString() == channel) return true;
    }
    return false;
}
