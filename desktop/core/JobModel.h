#pragma once
#include <QAbstractListModel>
#include <QVector>
#include <QString>
#include <QVariantList>
#include <QVariantMap>

class ApiClient;

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

    void setApi(ApiClient* api) { m_api = api; }

    Q_PROPERTY(QVariantMap current READ current NOTIFY currentChanged)
    QVariantMap current() const { return m_current; }   // 현재(최근) 세션 요약 — 폰미러/디스패치용

    Q_INVOKABLE void reload();                                       // 면접 세션 목록 로드
    Q_INVOKABLE void loadCases();                                    // 지원건 목록 → casesReady
    Q_INVOKABLE void createSession(int caseId, const QString& mode); // 세션 생성 후 reload
    Q_INVOKABLE void loadQuestions(int sessionId);                   // 질문 목록 → questionsReady
    Q_INVOKABLE void loadProgress(int sessionId);                    // 진행률 → progressReady
    Q_INVOKABLE void markResumed(int sessionId);                     // 이어받기 시각 기록 → resumed
    Q_INVOKABLE void dispatchToPhone(int sessionId);                 // 폰/웹으로 알림 발송 → dispatched

signals:
    void casesReady(const QVariantList& cases);
    void questionsReady(const QVariantList& questions);
    void progressReady(const QVariantMap& progress);
    void resumed(int sessionId);
    void dispatched(int sessionId);
    void currentChanged();

public:

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
    QVariantMap m_current;
    ApiClient* m_api = nullptr;
};
