#include "NotificationPoller.h"
#include "ApiClient.h"
#include <QJsonArray>
#include <QJsonObject>
#include <QDateTime>
#include <QTime>
#include <QSet>

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
    ++m_pollGeneration;
    ++m_mutationGeneration;
    m_lastMaxId = -1;
    m_unread = 0;
    m_items.clear();
    m_desktopToastByType.clear();
    m_desktopTaskbarByType.clear();
    m_sendersByType.clear();
    m_pushEnabled = false;
    m_preferencesConfirmed = false;
    m_categories.clear();
    m_quietHoursStart.clear();
    m_quietHoursEnd.clear();
    emit unreadChanged();
    emit itemsChanged();
}

void NotificationPoller::pollNow()
{
    const quint64 pollGeneration = ++m_pollGeneration;
    // LOCAL 파일 공유 게이트용 데스크톱 presence heartbeat — 폴링 틱마다 best-effort 로
    // 남긴다(실패 무시). 폴러는 로그인 시 start(), 로그아웃 시 stop() 되므로 로그인 상태에서만 돈다.
    m_api->post(QStringLiteral("/api/collaboration/desktop-presence"), QJsonObject(),
        [](bool, const QJsonValue&, const QString&) {});
    m_api->get(QStringLiteral("/api/notifications/preferences"),
        [this, pollGeneration](bool ok, const QJsonValue& data, const QString&) {
            if (pollGeneration != m_pollGeneration) return;
            if (ok) updatePreferences(data.toObject());
            pollNotifications(pollGeneration);
        });
}

void NotificationPoller::pollNotifications(quint64 pollGeneration)
{
    const quint64 mutationGeneration = m_mutationGeneration;
    m_api->get(QStringLiteral("/api/notifications/unread-count?platform=DESKTOP"),
        [this, pollGeneration, mutationGeneration](bool ok, const QJsonValue& data,
                                                   const QString&) {
            if (!ok || pollGeneration != m_pollGeneration
                || mutationGeneration != m_mutationGeneration) return;
            const int unread = data.toInt();
            if (unread != m_unread) {
                m_unread = unread;
                emit unreadChanged();
            }
        });
    m_api->get(QStringLiteral("/api/notifications?page=0&size=20&platform=DESKTOP"),
        [this, pollGeneration, mutationGeneration](bool ok, const QJsonValue& data,
                                                   const QString&) {
            if (!ok || pollGeneration != m_pollGeneration
                || mutationGeneration != m_mutationGeneration) return;
            const QJsonObject o = data.toObject();
            const QJsonArray arr = o.value("notifications").toArray();

            qint64 maxId = m_lastMaxId;
            const bool baseline = (m_lastMaxId < 0); // 첫 폴링: 토스트 없이 기준선만
            QVariantList items;
            QJsonObject newestDeliverable;
            bool newestDesktopToast = false;
            bool newestDesktopTaskbar = false;

            for (const QJsonValue& v : arr) {
                const QJsonObject n = v.toObject();
                const qint64 id = n.value("id").toInteger();
                const bool read = n.value("read").toBool();
                const QString type = n.value("type").toString();
                // API 응답에는 category가 없으므로 웹 TYPE_TO_CATEGORY와 같은 매핑을 적용한다.
                const QString category = categoryForType(type);
                const QString relation = n.value("senderRelation").toString();
                if (id > maxId) maxId = id;

                // 알림 센터(QML) 표시용 목록 — 억제 여부와 무관하게 전부 담는다
                items.push_back(QVariantMap{
                    {"id", id},
                    {"type", type},
                    {"category", category},
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
                    if (!globalDeliveryEnabled(category)) continue;
                    const bool desktopToast = channelEnabled(type, QStringLiteral("desktopToast"));
                    const bool desktopTaskbar = channelEnabled(type, QStringLiteral("desktopTaskbar"));
                    // 채널이 전부 꺼졌거나, 발신자 관계(모르는 사람/친구/기업/운영자)가 꺼진
                    // 알림은 토스트/작업표시줄 없이 목록에만 남긴다
                    if ((!desktopToast && !desktopTaskbar) || !senderEnabled(type, relation)) {
                        continue;
                    }
                    // QSystemTrayIcon은 동시에 여러 알림의 클릭 identity를 보존하지 못한다.
                    // 한 poll에서는 가장 최신 한 건만 외부/인앱 toast로 보내고, 나머지는
                    // 알림 센터와 unread count에 그대로 유지한다.
                    if (newestDeliverable.isEmpty()
                        || id > newestDeliverable.value(QStringLiteral("id")).toInteger()) {
                        newestDeliverable = n;
                        newestDesktopToast = desktopToast;
                        newestDesktopTaskbar = desktopTaskbar;
                    }
                }
            }
            if (!newestDeliverable.isEmpty()) {
                emit notificationArrived(
                    newestDeliverable.value("type").toString(),
                    newestDeliverable.value("title").toString(),
                    newestDeliverable.value("message").toString(),
                    newestDeliverable.value("link").toString(),
                    newestDeliverable.value("targetType").toString(),
                    newestDeliverable.value("targetId").toInteger(),
                    newestDesktopToast,
                    newestDesktopTaskbar);
            }
            m_lastMaxId = maxId;
            if (items != m_items) {
                m_items = items;
                emit itemsChanged();
            }
        });
}

bool NotificationPoller::updatePreferences(const QJsonObject& data)
{
    // 토스트는 개인정보/방해금지 설정을 확실히 읽은 경우에만 허용한다. 빈 envelope나
    // 스키마가 깨진 응답을 "모두 켜짐"으로 해석하지 않는다.
    if (!data.contains(QStringLiteral("pushEnabled"))
        || !data.value(QStringLiteral("pushEnabled")).isBool()
        || !data.value(QStringLiteral("categories")).isObject()
        || !data.value(QStringLiteral("rules")).isObject()) {
        return false;
    }
    QHash<QString, bool> desktopToastByType;
    QHash<QString, bool> desktopTaskbarByType;
    QHash<QString, QHash<QString, bool>> sendersByType;
    QHash<QString, bool> categories;
    const QJsonObject categoryObject = data.value(QStringLiteral("categories")).toObject();
    for (auto it = categoryObject.begin(); it != categoryObject.end(); ++it)
        categories.insert(it.key(), it.value().toBool(false));
    const QJsonObject rules = data.value(QStringLiteral("rules")).toObject();
    for (auto it = rules.begin(); it != rules.end(); ++it) {
        const QString type = it.key();
        const QJsonObject rule = it.value().toObject();
        const bool enabled = rule.value(QStringLiteral("enabled")).toBool(false);
        const QJsonObject channels = rule.value(QStringLiteral("channels")).toObject();
        desktopToastByType.insert(
            type,
            enabled && channels.value(QStringLiteral("desktopToast")).toBool(false));
        desktopTaskbarByType.insert(
            type,
            enabled && channels.value(QStringLiteral("desktopTaskbar")).toBool(false));
        // 발신자 관계별 수신 설정 — 관계 기반 알림(댓글·답글·쪽지·채팅 등)에만 내려온다
        const QJsonObject senders = rule.value(QStringLiteral("senders")).toObject();
        if (!senders.isEmpty()) {
            QHash<QString, bool> byRelation;
            for (auto sit = senders.begin(); sit != senders.end(); ++sit) {
                byRelation.insert(sit.key(), sit.value().toBool(false));
            }
            sendersByType.insert(type, byRelation);
        }
    }
    m_desktopToastByType = desktopToastByType;
    m_desktopTaskbarByType = desktopTaskbarByType;
    m_sendersByType = sendersByType;
    m_pushEnabled = data.value(QStringLiteral("pushEnabled")).toBool(false);
    m_categories = categories;
    m_quietHoursStart = data.value(QStringLiteral("quietHoursStart")).toString();
    m_quietHoursEnd = data.value(QStringLiteral("quietHoursEnd")).toString();
    m_preferencesConfirmed = true;
    return true;
}

QString NotificationPoller::categoryForType(const QString& type)
{
    static const QSet<QString> aiAnalysis{
        QStringLiteral("PROFILE_ANALYZED"), QStringLiteral("JOB_ANALYSIS_COMPLETE"),
        QStringLiteral("COMPANY_ANALYSIS_COMPLETE"), QStringLiteral("FIT_ANALYSIS_COMPLETE"),
        QStringLiteral("CAREER_TREND_COMPLETE"),
        QStringLiteral("JOB_POSTING_EXTRACTION_SUCCEEDED"),
        QStringLiteral("JOB_POSTING_EXTRACTION_REVIEW_REQUIRED"),
        QStringLiteral("JOB_POSTING_EXTRACTION_FAILED")
    };
    static const QSet<QString> interview{
        QStringLiteral("QUESTIONS_GENERATED"), QStringLiteral("INTERVIEW_REPORT_READY"),
        QStringLiteral("INTERVIEW_DISPATCH")
    };
    static const QSet<QString> community{
        QStringLiteral("COMMENT"), QStringLiteral("COMMENT_REPLY"),
        QStringLiteral("COMMENT_HIDDEN"), QStringLiteral("COMMENT_RESTORED"),
        QStringLiteral("COMMENT_REMOVED"), QStringLiteral("LIKE"), QStringLiteral("LIKE_ANON"),
        QStringLiteral("POST_DISLIKE"), QStringLiteral("POST_DISLIKE_ANON"),
        QStringLiteral("POST_RECOMMEND"), QStringLiteral("POST_RECOMMEND_ANON"),
        QStringLiteral("POST_DISRECOMMEND"), QStringLiteral("POST_DISRECOMMEND_ANON"),
        QStringLiteral("COMMENT_LIKE"), QStringLiteral("COMMENT_LIKE_ANON"),
        QStringLiteral("COMMENT_DISLIKE"), QStringLiteral("COMMENT_DISLIKE_ANON"),
        QStringLiteral("COMMENT_RECOMMEND"), QStringLiteral("COMMENT_RECOMMEND_ANON"),
        QStringLiteral("COMMENT_DISRECOMMEND"), QStringLiteral("COMMENT_DISRECOMMEND_ANON"),
        QStringLiteral("POST_BOOKMARK"), QStringLiteral("POST_BOOKMARK_ANON"),
        QStringLiteral("POST_SCRAP"), QStringLiteral("POST_SCRAP_ANON"),
        QStringLiteral("POST_WATCH_COMMENT"), QStringLiteral("COMMENT_WATCH_REPLY"),
        QStringLiteral("POST_HIDDEN"), QStringLiteral("POST_IMAGE_BLURRED"),
        QStringLiteral("COMMUNITY_STRIKE_WARNING"), QStringLiteral("POST_REMOVED"),
        QStringLiteral("POST_RESTORED"), QStringLiteral("POST_SUMMARY_READY")
    };
    static const QSet<QString> messenger{
        QStringLiteral("FRIEND_REQUEST"), QStringLiteral("FRIEND_ACCEPTED"),
        QStringLiteral("ROOM_INVITE"), QStringLiteral("ROOM_MESSAGE"),
        QStringLiteral("NOTE_MESSAGE"), QStringLiteral("ROOM_MENTION")
    };
    static const QSet<QString> billing{
        QStringLiteral("CREDIT_LOW"), QStringLiteral("PAYMENT_COMPLETE"),
        QStringLiteral("PAYMENT_SCHEDULED"), QStringLiteral("SUBSCRIPTION_CANCELED"),
        QStringLiteral("CREDIT_RECHARGED"), QStringLiteral("REFUND_RESULT")
    };
    static const QSet<QString> admin{
        QStringLiteral("NEW_REPORT"), QStringLiteral("NEW_TICKET"), QStringLiteral("NEW_USER"),
        QStringLiteral("NEW_COMPANY_APPLICATION"), QStringLiteral("NEW_JOB_POSTING_REVIEW"),
        QStringLiteral("LOW_CONFIDENCE_REPORT"), QStringLiteral("TICKET_DRAFT_READY")
    };
    if (aiAnalysis.contains(type)) return QStringLiteral("ai_analysis");
    if (interview.contains(type)) return QStringLiteral("interview");
    if (type == QStringLiteral("CORRECTION_COMPLETE")) return QStringLiteral("correction");
    if (community.contains(type)) return QStringLiteral("community");
    if (messenger.contains(type)) return QStringLiteral("messenger");
    if (type == QStringLiteral("RECOMMENDED_JOB") || type == QStringLiteral("RECOMMENDED_POST"))
        return QStringLiteral("recommendation");
    if (type == QStringLiteral("MARKETING_AD")) return QStringLiteral("marketing");
    if (billing.contains(type)) return QStringLiteral("billing");
    if (admin.contains(type)) return QStringLiteral("admin");
    return QStringLiteral("notice");
}

bool NotificationPoller::globalDeliveryEnabled(const QString& category) const
{
    if (!m_preferencesConfirmed || !m_pushEnabled || withinQuietHours()) return false;
    return category.isEmpty() || m_categories.value(category, false);
}

bool NotificationPoller::withinQuietHours() const
{
    const auto parse = [](const QString& value) {
        QTime parsed = QTime::fromString(value.trimmed(), QStringLiteral("HH:mm:ss"));
        if (!parsed.isValid())
            parsed = QTime::fromString(value.trimmed(), QStringLiteral("HH:mm"));
        return parsed;
    };
    const QTime start = parse(m_quietHoursStart);
    const QTime end = parse(m_quietHoursEnd);
    if (!start.isValid() || !end.isValid() || start == end) return false;
    const QTime nowKst = QDateTime::currentDateTimeUtc().addSecs(9 * 60 * 60).time();
    if (start < end) return nowKst >= start && nowKst < end;
    return nowKst >= start || nowKst < end;
}

bool NotificationPoller::channelEnabled(const QString& type, const QString& channel) const
{
    if (!m_preferencesConfirmed) return false;
    if (channel == QStringLiteral("desktopTaskbar")) {
        return m_desktopTaskbarByType.value(type, false);
    }
    return m_desktopToastByType.value(type, false);
}

bool NotificationPoller::senderEnabled(const QString& type, const QString& relation) const
{
    if (!m_preferencesConfirmed) return false;
    // 관계 미상(빈 값)은 필터하지 않고 통과 — 서버 senderEnabled 와 동일 규칙
    if (relation.isEmpty()) {
        return true;
    }
    const auto it = m_sendersByType.constFind(type);
    if (it == m_sendersByType.constEnd()) {
        return true;
    }
    return it->value(relation, false);
}

void NotificationPoller::markAsRead(qint64 id)
{
    m_api->patch(QStringLiteral("/api/notifications/%1/read").arg(id), QJsonObject(),
        [this, id](bool ok, const QJsonValue&, const QString&) {
            if (!ok) return;
            ++m_mutationGeneration;
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
    m_api->post(QStringLiteral("/api/notifications/read-all?platform=DESKTOP"), QJsonObject(),
        [this](bool ok, const QJsonValue&, const QString&) {
            if (!ok) return;
            ++m_mutationGeneration;
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
