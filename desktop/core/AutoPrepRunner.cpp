#include "AutoPrepRunner.h"
#include "ApiClient.h"
#include <QNetworkAccessManager>
#include <QNetworkReply>
#include <QNetworkRequest>
#include <QJsonDocument>
#include <QJsonObject>
#include <QJsonArray>
#include <QUrl>

AutoPrepRunner::AutoPrepRunner(ApiClient* api, QObject* parent)
    : QObject(parent), m_api(api) {}

QString AutoPrepRunner::partLabel(const QString& key)
{
    if (key == "PROFILE")   return QStringLiteral("프로필 정리");
    if (key == "JOB")       return QStringLiteral("공고·직무 분석");
    if (key == "FIT")       return QStringLiteral("적합도 분석");
    if (key == "WRITE")     return QStringLiteral("자소서 초안");
    if (key == "INTERVIEW") return QStringLiteral("예상 질문 생성");
    if (key == "COMMUNITY") return QStringLiteral("커뮤니티 인사이트");
    return key;
}

void AutoPrepRunner::intake(const QString& query)
{
    QJsonObject body;
    body["query"] = query;
    m_api->post(QStringLiteral("/api/auto-prep/intake"), body,
        [this](bool ok, const QJsonValue& data, const QString& msg) {
            if (!ok) {
                emit errorOccurred(msg.isEmpty() ? QStringLiteral("요청 해석 실패") : msg);
                return;
            }
            const QJsonObject o = data.toObject();
            QVariantMap out;
            out["ready"]   = o.value("ready").toBool();
            out["message"] = o.value("message").toString();
            out["nextAsk"] = o.value("nextAsk").toString();

            QVariantList cands;
            for (const QJsonValue& v : o.value("candidates").toArray()) {
                const QJsonObject c = v.toObject();
                cands.push_back(QVariantMap{
                    {"caseId", c.value("id").toInteger()},
                    {"label", c.value("companyName").toString()
                              + QStringLiteral(" · ") + c.value("jobTitle").toString()}});
            }
            out["candidates"] = cands;

            QVariantList modes;
            for (const QJsonValue& v : o.value("modes").toArray()) {
                const QJsonObject m = v.toObject();
                modes.push_back(QVariantMap{
                    {"code", m.value("code").toString()},
                    {"label", m.value("label").toString()}});
            }
            out["modes"] = modes;

            const QJsonObject plan = o.value("plan").toObject();
            QVariantList planSteps;
            for (const QJsonValue& v : plan.value("steps").toArray())
                planSteps.push_back(partLabel(v.toString()));
            out["planSteps"] = planSteps;

            emit intakeReady(out);
        });
}

void AutoPrepRunner::run(const QString& query, int caseId, const QString& mode)
{
    if (m_running) return;

    m_steps.clear();
    emit stepsChanged();
    m_buffer.clear();
    m_running = true;
    emit runningChanged();

    QJsonObject body;
    body["query"] = query;
    if (caseId > 0) body["applicationCaseId"] = caseId;
    if (!mode.isEmpty()) body["mode"] = mode;

    QNetworkRequest req{QUrl(m_api->baseUrl() + QStringLiteral("/api/auto-prep/run/stream"))};
    req.setHeader(QNetworkRequest::ContentTypeHeader, "application/json");
    req.setRawHeader("Accept", "text/event-stream");
    if (!m_api->token().isEmpty())
        req.setRawHeader("Authorization", QByteArray("Bearer ") + m_api->token().toUtf8());

    // ApiClient 의 QNAM 을 빌리지 않고 자체 QNAM — 스트림 수명 관리 분리
    static QNetworkAccessManager nam;
    m_reply = nam.post(req, QJsonDocument(body).toJson(QJsonDocument::Compact));

    connect(m_reply, &QNetworkReply::readyRead, this, [this]() {
        if (!m_reply) return;
        m_buffer += m_reply->readAll();
        processBuffer();
    });
    connect(m_reply, &QNetworkReply::finished, this, [this]() {
        const bool aborted = !m_reply || m_reply->error() != QNetworkReply::NoError;
        if (m_reply) { m_reply->deleteLater(); m_reply = nullptr; }
        if (m_running) {
            m_running = false;
            emit runningChanged();
            if (aborted)
                emit errorOccurred(QStringLiteral("실행 스트림이 끊겼습니다"));
        }
    });
}

void AutoPrepRunner::cancel()
{
    if (m_reply) {
        m_reply->abort(); // finished 슬롯에서 정리
    }
}

void AutoPrepRunner::processBuffer()
{
    int idx;
    while ((idx = m_buffer.indexOf("\n\n")) != -1) {
        const QByteArray chunk = m_buffer.left(idx);
        m_buffer.remove(0, idx + 2);

        QString type, data;
        for (const QByteArray& lineRaw : chunk.split('\n')) {
            const QByteArray line = lineRaw.trimmed();
            if (line.startsWith("event:"))
                type = QString::fromUtf8(line.mid(6).trimmed());
            else if (line.startsWith("data:"))
                data += QString::fromUtf8(line.mid(5).trimmed());
        }
        if (!type.isEmpty())
            handleEvent(type, data);
    }
}

int AutoPrepRunner::stepIndex(const QString& key) const
{
    for (int i = 0; i < m_steps.size(); ++i)
        if (m_steps[i].toMap().value("key").toString() == key) return i;
    return -1;
}

void AutoPrepRunner::handleEvent(const QString& type, const QString& data)
{
    const QJsonObject o = QJsonDocument::fromJson(data.toUtf8()).object();

    if (type == QStringLiteral("plan")) {
        // 계획 수신 → 스텝 자리 미리 깔기
        m_steps.clear();
        for (const QJsonValue& v : o.value("steps").toArray()) {
            const QString key = v.toString();
            m_steps.push_back(QVariantMap{
                {"key", key}, {"label", partLabel(key)},
                {"status", "WAIT"}, {"summary", ""}, {"substep", ""}, {"elapsedMs", 0}});
        }
        emit stepsChanged();
    }
    else if (type == QStringLiteral("part-start")) {
        const int i = stepIndex(o.value("key").toString());
        if (i >= 0) {
            QVariantMap st = m_steps[i].toMap();
            st["status"] = "RUNNING";
            m_steps[i] = st;
            emit stepsChanged();
        }
    }
    else if (type == QStringLiteral("substep")) {
        const int i = stepIndex(o.value("key").toString());
        if (i >= 0) {
            QVariantMap st = m_steps[i].toMap();
            st["substep"] = o.value("name").toString();
            m_steps[i] = st;
            emit stepsChanged();
        }
    }
    else if (type == QStringLiteral("part-done")) {
        const int i = stepIndex(o.value("key").toString());
        if (i >= 0) {
            QVariantMap st = m_steps[i].toMap();
            st["status"]    = o.value("status").toString();  // DONE | SKIPPED | FAILED
            st["summary"]   = o.value("summary").toString();
            st["elapsedMs"] = o.value("elapsedMs").toInt();
            st["substep"]   = QString();
            m_steps[i] = st;
            emit stepsChanged();
        }
    }
    else if (type == QStringLiteral("done")) {
        m_running = false;
        emit runningChanged();
        emit finished(o.value("message").toString());
    }
}
