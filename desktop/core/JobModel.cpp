#include "JobModel.h"
#include "ApiClient.h"
#include <QJsonArray>
#include <QJsonObject>
#include <QtGlobal>
#include <QVariantMap>

static QString modeLabel(const QString& m)
{
    if (m == "BASIC")       return QStringLiteral("기본 면접");
    if (m == "JOB")         return QStringLiteral("직무 면접");
    if (m == "COMPANY")     return QStringLiteral("기업 맞춤");
    if (m == "PERSONALITY") return QStringLiteral("인성 면접");
    if (m == "PRESSURE")    return QStringLiteral("압박 면접");
    if (m == "RESUME")      return QStringLiteral("자소서 면접");
    if (m == "PORTFOLIO")   return QStringLiteral("포트폴리오 면접");
    if (m == "REAL")        return QStringLiteral("실전 종합 면접");
    return m;
}

JobModel::JobModel(QObject* parent) : QAbstractListModel(parent)
{
    // 실데이터는 로그인 후 reload() 로 채운다.
}

void JobModel::clear()
{
    ++m_reloadGeneration;
    ++m_accountGeneration;
    ++m_casesRequestGeneration;
    beginResetModel();
    m_jobs.clear();
    m_caseLabels.clear();
    endResetModel();
    m_current.clear();
    if (m_creatingSession) {
        m_creatingSession = false;
        emit creatingSessionChanged();
    }
    if (m_casesLoading || !m_casesError.isEmpty()) {
        m_casesLoading = false;
        m_casesError.clear();
        emit casesStateChanged();
    }
    emit currentChanged();
}

QString JobModel::caseLabel(qint64 caseId) const
{
    return m_caseLabels.value(caseId,
        QStringLiteral("지원건 #%1").arg(caseId));
}

void JobModel::reload()
{
    if (!m_api) return;
    const quint64 reloadGeneration = ++m_reloadGeneration;
    const quint64 accountGeneration = m_accountGeneration;
    // 1) 케이스 라벨 맵 (회사 · 직무) → 2) 세션 목록
    m_api->get("/api/application-cases?page=0&size=50",
        [this, reloadGeneration, accountGeneration](bool ok, const QJsonValue& data, const QString&) {
            if (reloadGeneration != m_reloadGeneration || accountGeneration != m_accountGeneration) return;
            if (ok) {
                m_caseLabels.clear();
                for (const QJsonValue& v : data.toArray()) {
                    const QJsonObject c = v.toObject();
                    m_caseLabels.insert(c.value("id").toInteger(),
                        c.value("companyName").toString()
                        + QStringLiteral(" · ") + c.value("jobTitle").toString());
                }
            }
            loadSessions(reloadGeneration);
        });
}

void JobModel::loadSessions(quint64 reloadGeneration)
{
    const quint64 accountGeneration = m_accountGeneration;
    m_api->get("/api/interview/sessions?page=0&size=30",
        [this, reloadGeneration, accountGeneration](bool ok, const QJsonValue& data, const QString&) {
            if (reloadGeneration != m_reloadGeneration || accountGeneration != m_accountGeneration) return;
            beginResetModel();
            m_jobs.clear();
            if (ok) {
                const QJsonArray arr = data.toObject().value("sessions").toArray();
                for (const QJsonValue& v : arr) {
                    const QJsonObject s = v.toObject();
                    const QString code = s.value("mode").toString();
                    const bool ended = s.contains("endedAt") && !s.value("endedAt").isNull();
                    const int total = qMax(0, s.value("totalQuestions").toInt());
                    const int answered = qBound(0, s.value("answeredQuestions").toInt(), total);
                    const bool finished = s.value("finished").toBool();
                    Job j;
                    j.id       = s.value("id").toInteger();
                    j.caseId   = s.value("applicationCaseId").toInteger();
                    j.modeCode = code;
                    j.mode     = modeLabel(code);
                    j.title    = caseLabel(j.caseId);
                    j.status   = finished
                        ? QStringLiteral("DONE")
                        : ended ? QStringLiteral("REPORTED") : QStringLiteral("RUNNING");
                    j.progress = finished ? 100
                        : total > 0 ? (answered * 100 + total / 2) / total : 0;
                    m_jobs.push_back(j);
                }
            }
            endResetModel();
            if (!m_jobs.isEmpty()) {
                const Job& j = m_jobs.first();
                m_current = QVariantMap{
                    {"id", static_cast<qint64>(j.id)},
                    {"caseId", static_cast<qint64>(j.caseId)},
                    {"title", j.title}, {"mode", j.mode},
                    {"status", j.status}, {"progress", j.progress}
                };
            } else {
                m_current.clear();
            }
            emit currentChanged();
        });
}

void JobModel::loadCases()
{
    if (!m_api) return;
    const quint64 accountGeneration = m_accountGeneration;
    const quint64 casesGeneration = ++m_casesRequestGeneration;
    m_casesLoading = true;
    m_casesError.clear();
    emit casesStateChanged();
    m_api->get("/api/application-cases?page=0&size=30",
        [this, accountGeneration, casesGeneration](bool ok, const QJsonValue& data, const QString& message) {
            if (accountGeneration != m_accountGeneration
                || casesGeneration != m_casesRequestGeneration) return;
            m_casesLoading = false;
            m_casesError = ok ? QString() : (message.isEmpty()
                ? QStringLiteral("지원 건을 불러오지 못했습니다.") : message);
            emit casesStateChanged();
            QVariantList out;
            if (ok) {
                for (const QJsonValue& v : data.toArray()) {
                    const QJsonObject c = v.toObject();
                    QVariantMap m;
                    m["caseId"] = static_cast<qint64>(c.value("id").toInteger());
                    m["label"]  = c.value("companyName").toString() + QStringLiteral(" · ") + c.value("jobTitle").toString();
                    out.push_back(m);
                }
            }
            if (ok) emit casesReady(out);
        });
}

void JobModel::createSession(int caseId, const QString& mode)
{
    if (!m_api || m_creatingSession) return;
    m_creatingSession = true;
    emit creatingSessionChanged();
    const quint64 accountGeneration = m_accountGeneration;
    QJsonObject body;
    body["applicationCaseId"] = caseId;
    body["mode"] = mode;
    m_api->post("/api/interview/sessions", body,
        [this, caseId, mode, accountGeneration](bool ok, const QJsonValue& data, const QString& message) {
            if (accountGeneration != m_accountGeneration) return;
            m_creatingSession = false;
            emit creatingSessionChanged();
            if (!ok) {
                emit sessionCreateFailed(message.isEmpty()
                    ? QStringLiteral("면접 세션을 만들지 못했습니다") : message);
                return;
            }
            const QJsonObject s = data.toObject();
            const int sid = static_cast<int>(s.value("id").toInteger());
            emit sessionCreated(sid, caseId, modeLabel(mode), caseLabel(caseId));
            reload();   // 사이드바 목록 갱신
        });
}

void JobModel::loadProgress(int sessionId)
{
    if (!m_api) return;
    const quint64 accountGeneration = m_accountGeneration;
    m_api->get("/api/interview/sessions/" + QString::number(sessionId) + "/progress",
        [this, accountGeneration](bool ok, const QJsonValue& data, const QString&) {
            if (accountGeneration != m_accountGeneration) return;
            QVariantMap m;
            if (ok) {
                const QJsonObject o = data.toObject();
                m["total"]    = o.value("totalQuestions").toInt();
                m["answered"] = o.value("answeredQuestions").toInt();
                m["finished"] = o.value("finished").toBool();
            }
            emit progressReady(m);
        });
}

QVariantMap JobModel::sessionContext(qint64 sessionId) const
{
    const int row = indexOf(sessionId);
    if (row < 0) return {};

    const Job& job = m_jobs.at(row);
    return QVariantMap{
        {"id", job.id},
        {"caseId", job.caseId},
        {"title", job.title},
        {"mode", job.mode},
        {"modeCode", job.modeCode},
        {"status", job.status},
        {"progress", job.progress}
    };
}

void JobModel::markResumed(int sessionId)
{
    if (!m_api) return;
    const quint64 accountGeneration = m_accountGeneration;
    m_api->post("/api/interview/sessions/" + QString::number(sessionId) + "/resume", QJsonObject(),
        [this, sessionId, accountGeneration](bool ok, const QJsonValue&, const QString&) {
            if (accountGeneration != m_accountGeneration) return;
            if (ok) { emit resumed(sessionId); reload(); }   // 이어받은 시각 기록 → 목록 최신 정렬 반영
        });
}

void JobModel::dispatchToPhone(int sessionId)
{
    if (!m_api) return;
    const quint64 accountGeneration = m_accountGeneration;
    QJsonObject body;
    body["target"] = QStringLiteral("MOBILE");
    m_api->post("/api/interview/sessions/" + QString::number(sessionId) + "/dispatch", body,
        [this, sessionId, accountGeneration](bool ok, const QJsonValue&, const QString&) {
            if (accountGeneration != m_accountGeneration) return;
            if (ok) emit dispatched(sessionId);   // 서버가 알림 저장+푸시 발송 → 폰/웹 알림 벨에 뜸
        });
}

int JobModel::rowCount(const QModelIndex& parent) const
{
    if (parent.isValid()) return 0;
    return static_cast<int>(m_jobs.size());
}

QVariant JobModel::data(const QModelIndex& index, int role) const
{
    if (!index.isValid() || index.row() < 0 || index.row() >= m_jobs.size())
        return QVariant();

    const Job& j = m_jobs.at(index.row());
    switch (role) {
        case IdRole:       return j.id;
        case CaseRole:     return j.caseId;
        case TitleRole:    return j.title;
        case ModeRole:     return j.mode;
        case ModeCodeRole: return j.modeCode;
        case StatusRole:   return j.status;
        case ProgressRole: return j.progress;
        default:           return QVariant();
    }
}

QHash<int, QByteArray> JobModel::roleNames() const
{
    return {
        {IdRole,       "jobId"},
        {CaseRole,     "caseId"},
        {TitleRole,    "title"},
        {ModeRole,     "mode"},
        {ModeCodeRole, "modeCode"},
        {StatusRole,   "status"},
        {ProgressRole, "progress"},
    };
}

int JobModel::indexOf(qint64 id) const
{
    for (int i = 0; i < m_jobs.size(); ++i)
        if (m_jobs[i].id == id) return i;
    return -1;
}
