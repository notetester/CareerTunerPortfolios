#pragma once
#include <QAbstractListModel>
#include <QVector>
#include <QString>

// 작업(Job) 한 건.
struct Job {
    qint64  id;
    QString title;
    QString mode;
    QString status;   // QUEUED / RUNNING / DONE / FAILED / CANCELED
    int     progress; // 0~100
};

// 작업 목록을 QML ListView 에 바인딩하기 위한 모델.
// roleNames() 의 키(jobId/title/mode/status/progress)를 QML delegate 에서 그대로 쓴다.
class JobModel : public QAbstractListModel
{
    Q_OBJECT
public:
    enum Roles { IdRole = Qt::UserRole + 1, TitleRole, ModeRole, StatusRole, ProgressRole };

    explicit JobModel(QObject* parent = nullptr);

    int rowCount(const QModelIndex& parent = QModelIndex()) const override;
    QVariant data(const QModelIndex& index, int role) const override;
    QHash<int, QByteArray> roleNames() const override;

    // 작업 추가 또는 갱신(있으면 갱신, 없으면 맨 앞에 추가)
    Q_INVOKABLE void upsert(qint64 id, const QString& title, const QString& mode,
                            const QString& status, int progress);
    // 진행률/상태만 갱신
    Q_INVOKABLE void setProgress(qint64 id, int progress, const QString& status);

private:
    int indexOf(qint64 id) const;
    QVector<Job> m_jobs;
};
