#include "JobModel.h"
#include "ApiClient.h"
#include <QJsonArray>
#include <QJsonObject>
#include <QVariantMap>

static QString modeLabel(const QString& m)
{
    if (m == "BASIC")       return QStringLiteral("기본 면접");
    if (m == "JOB")         return QStringLiteral("직무 면접");
    if (m == "COMPANY")     return QStringLiteral("기업 맞춤");
    if (m == "PERSONALITY") return QStringLiteral("인성 면접");
    if (m == "PRESSURE")    return QStringLiteral("압박 면접");
    if (m == "RESUME")      return QStringLiteral("자소서 면접");
    return m;
}

JobModel::JobModel(QObject* parent) : QAbstractListModel(parent)
{
    // 실데이터는 로그인 후 reload() 로 채운다.
}

void JobModel::reload()
{
    if (!m_api) return;
    m_api->get("/api/interview/sessions?page=0&size=20",
        [this](bool ok, const QJsonValue& data, const QString&) {
            beginResetModel();
            m_jobs.clear();
            if (ok) {
                const QJsonArray arr = data.toObject().value("sessions").toArray();
                for (const QJsonValue& v : arr) {
                    const QJsonObject s = v.toObject();
                    const QString mode = modeLabel(s.value("mode").toString());
                    const bool ended = !s.value("endedAt").isNull();
                    Job j;
                    j.id       = s.value("id").toInteger();
                    j.mode     = mode;
                    j.title    = mode + QStringLiteral("  ·  지원건 #") + QString::number(s.value("applicationCaseId").toInteger());
                    j.status   = ended ? QStringLiteral("DONE") : QStringLiteral("RUNNING");
                    j.progress = ended ? 100
                               : ((!s.value("avgScore").isNull() || !s.value("avgVoiceScore").isNull()) ? 60 : 20);
                    m_jobs.push_back(j);
                }
            }
            endResetModel();
        });
}

void JobModel::loadCases()
{
    if (!m_api) return;
    m_api->get("/api/application-cases?page=0&size=30",
        [this](bool ok, const QJsonValue& data, const QString&) {
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
            emit casesReady(out);
        });
}

void JobModel::createSession(int caseId, const QString& mode)
{
    if (!m_api) return;
    QJsonObject body;
    body["applicationCaseId"] = caseId;
    body["mode"] = mode;
    m_api->post("/api/interview/sessions", body,
        [this](bool ok, const QJsonValue&, const QString&) {
            if (ok) reload();   // 생성 성공 시 목록 새로고침
        });
}

void JobModel::loadQuestions(int sessionId)
{
    if (!m_api) return;
    m_api->get("/api/interview/sessions/" + QString::number(sessionId) + "/questions",
        [this](bool ok, const QJsonValue& data, const QString&) {
            QVariantList out;
            if (ok) {
                for (const QJsonValue& v : data.toArray()) {
                    const QJsonObject q = v.toObject();
                    QVariantMap m;
                    m["question"] = q.value("question").toString();
                    m["type"]     = q.value("questionType").toString();
                    out.push_back(m);
                }
            }
            emit questionsReady(out);
        });
}

void JobModel::loadProgress(int sessionId)
{
    if (!m_api) return;
    m_api->get("/api/interview/sessions/" + QString::number(sessionId) + "/progress",
        [this](bool ok, const QJsonValue& data, const QString&) {
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

void JobModel::markResumed(int sessionId)
{
    if (!m_api) return;
    m_api->post("/api/interview/sessions/" + QString::number(sessionId) + "/resume", QJsonObject(),
        [this, sessionId](bool ok, const QJsonValue&, const QString&) {
            if (ok) { emit resumed(sessionId); reload(); }   // 이어받은 시각 기록 → 목록 최신 정렬 반영
        });
}

void JobModel::dispatchToPhone(int sessionId)
{
    if (!m_api) return;
    m_api->post("/api/interview/sessions/" + QString::number(sessionId) + "/dispatch", QJsonObject(),
        [this, sessionId](bool ok, const QJsonValue&, const QString&) {
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
        case TitleRole:    return j.title;
        case ModeRole:     return j.mode;
        case StatusRole:   return j.status;
        case ProgressRole: return j.progress;
        default:           return QVariant();
    }
}

QHash<int, QByteArray> JobModel::roleNames() const
{
    return {
        {IdRole,       "jobId"},
        {TitleRole,    "title"},
        {ModeRole,     "mode"},
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

void JobModel::upsert(qint64 id, const QString& title, const QString& mode,
                      const QString& status, int progress)
{
    const int i = indexOf(id);
    if (i < 0) {
        beginInsertRows(QModelIndex(), 0, 0);
        m_jobs.prepend({id, title, mode, status, progress});
        endInsertRows();
    } else {
        m_jobs[i] = {id, title, mode, status, progress};
        emit dataChanged(index(i), index(i));
    }
}

void JobModel::setProgress(qint64 id, int progress, const QString& status)
{
    const int i = indexOf(id);
    if (i < 0) return;
    m_jobs[i].progress = progress;
    m_jobs[i].status   = status;
    emit dataChanged(index(i), index(i));
}
