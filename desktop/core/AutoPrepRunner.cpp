#include "AutoPrepRunner.h"
#include "ApiClient.h"
#include <QNetworkAccessManager>
#include <QNetworkReply>
#include <QNetworkRequest>
#include <QJsonDocument>
#include <QJsonObject>
#include <QJsonArray>
#include <QTimer>
#include <QUrl>

AutoPrepRunner::AutoPrepRunner(ApiClient* api, QObject* parent)
    : QObject(parent), m_api(api)
{
    // 로그아웃·계정/서버 전환은 독립 POST-SSE도 즉시 중단하고 화면 상태를 비운다.
    connect(m_api, &ApiClient::authenticationIdentityChanged, this, [this]() {
        const bool preflightPending = m_authPreflightPending;
        clear();
        // refresh 실패는 identityChanged 직후 authenticationExpired가 동기 발생한다.
        // 일반 계정 전환이라면 다음 이벤트 루프에서 이 임시 표식을 폐기한다.
        m_authPreflightPending = preflightPending;
        QTimer::singleShot(0, this, [this]() { m_authPreflightPending = false; });
    });
    connect(m_api, &ApiClient::authenticationExpired, this, [this](const QString&) {
        if (!m_authPreflightPending) return;
        m_authPreflightPending = false;
        emit errorOccurred(QStringLiteral(
            "로그인이 만료되었습니다. 다시 로그인한 뒤 AI 자동 준비를 실행해 주세요."));
    });
}

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
    const quint64 generation = ++m_intakeGeneration;
    QJsonObject body;
    body["query"] = query;
    m_api->post(QStringLiteral("/api/auto-prep/intake"), body,
        [this, generation](bool ok, const QJsonValue& data, const QString& msg) {
            if (generation != m_intakeGeneration) return;
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
    if (m_running || m_reply) return;

    const quint64 generation = ++m_runGeneration;

    m_steps.clear();
    emit stepsChanged();
    m_buffer.clear();
    m_running = true;
    m_authPreflightPending = true;
    emit runningChanged();

    QJsonObject body;
    body["query"] = query;
    if (caseId > 0) body["applicationCaseId"] = caseId;
    if (!mode.isEmpty()) body["mode"] = mode;

    // POST-SSE 자체를 401 뒤 재시도하면 동일 AI 작업이 중복 실행될 수 있다. 먼저 일반
    // ApiClient 요청으로 인증/refresh를 끝낸 다음, 회전된 최신 access token으로 스트림을 딱 한 번 연다.
    m_api->get(QStringLiteral("/api/auth/me"),
        [this, generation, body](bool ok, const QJsonValue&, const QString&) {
            if (generation != m_runGeneration) return;
            m_authPreflightPending = false;
            if (!ok || m_api->token().isEmpty()) {
                m_running = false;
                emit runningChanged();
                emit errorOccurred(QStringLiteral(
                    "로그인 상태를 확인하지 못했습니다. 다시 로그인한 뒤 AI 자동 준비를 실행해 주세요."));
                return;
            }
            startStream(body, generation);
        });
}

void AutoPrepRunner::startStream(const QJsonObject& body, quint64 generation)
{
    if (generation != m_runGeneration || !m_running || m_reply) return;
    m_authPreflightPending = false;

    QNetworkRequest req{QUrl(m_api->baseUrl() + QStringLiteral("/api/auto-prep/run/stream"))};
    req.setHeader(QNetworkRequest::ContentTypeHeader, "application/json");
    req.setRawHeader("Accept", "text/event-stream");
    if (!m_api->token().isEmpty())
        req.setRawHeader("Authorization", QByteArray("Bearer ") + m_api->token().toUtf8());

    // POST-SSE는 자동 재시도하면 AI 작업이 중복 실행될 수 있다. 자체 QNAM을 쓰되
    // ApiClient의 인증 주체 변경 시 clear()가 세대를 폐기하고 연결을 abort한다.
    QNetworkReply* reply = m_nam.post(req, QJsonDocument(body).toJson(QJsonDocument::Compact));
    m_reply = reply;

    connect(reply, &QNetworkReply::readyRead, this, [this, reply, generation]() {
        if (generation != m_runGeneration || reply != m_reply) return;
        m_buffer += reply->readAll();
        processBuffer();
    });
    connect(reply, &QNetworkReply::finished, this, [this, reply, generation]() {
        const bool failed = reply->error() != QNetworkReply::NoError;
        const int status = reply->attribute(QNetworkRequest::HttpStatusCodeAttribute).toInt();
        if (reply == m_reply) m_reply = nullptr;
        reply->deleteLater();
        if (generation != m_runGeneration) return;
        if (m_running) {
            m_running = false;
            emit runningChanged();
            if (failed) {
                emit errorOccurred(status == 401
                    ? QStringLiteral("로그인이 만료되었습니다. 다시 로그인해 주세요.")
                    : QStringLiteral("실행 스트림이 끊겼습니다"));
            }
        }
    });
}

void AutoPrepRunner::cancel()
{
    ++m_runGeneration;
    m_authPreflightPending = false;
    QNetworkReply* reply = m_reply;
    m_reply = nullptr;
    m_buffer.clear();
    if (reply) reply->abort(); // 폐기된 generation의 finished 슬롯은 오류 토스트를 내지 않는다.
    if (m_running) {
        m_running = false;
        emit runningChanged();
    }
}

void AutoPrepRunner::clear()
{
    ++m_intakeGeneration;
    cancel();
    if (!m_steps.isEmpty()) {
        m_steps.clear();
        emit stepsChanged();
    }
    emit cleared();
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
