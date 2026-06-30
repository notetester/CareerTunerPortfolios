#include "JobModel.h"
#include "ApiClient.h"
#include <QJsonArray>
#include <QJsonObject>

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
        [this](bool ok, const QJsonObject& data, const QString&) {
            beginResetModel();
            m_jobs.clear();
            if (ok) {
                const QJsonArray arr = data.value("sessions").toArray();
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
