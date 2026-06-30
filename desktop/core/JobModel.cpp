#include "JobModel.h"

JobModel::JobModel(QObject* parent) : QAbstractListModel(parent)
{
    // 서버 연결 전 화면 확인용 시드 데이터(추후 ApiClient 결과로 대체).
    m_jobs.push_back({128, QStringLiteral("삼성전자 · SW 개발직군"), QStringLiteral("직무 면접"), QStringLiteral("RUNNING"), 65});
    m_jobs.push_back({126, QStringLiteral("네이버 · 백엔드 인턴"),   QStringLiteral("인성 면접"), QStringLiteral("DONE"),    100});
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
