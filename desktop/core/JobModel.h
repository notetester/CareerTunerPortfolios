#pragma once
#include <QAbstractListModel>
#include <QVector>
#include <QString>
#include <QVariantList>
#include <QVariantMap>
#include <QHash>

class ApiClient;

// 작업(면접 세션) 한 건.
struct Job {
    qint64  id;
    qint64  caseId;      // applicationCaseId — 분석문서 내보내기 등에 사용
    QString title;       // "회사 · 직무" (케이스 라벨) — 없으면 "지원건 #n"
    QString mode;        // 표시용 한글 라벨
    QString modeCode;    // 원본 enum (BASIC/JOB/...)
    QString status;      // RUNNING / REPORTED / DONE
    int     progress;    // 0~100 (answered/total 기반)
};

// 세션 목록을 QML ListView(사이드바) 에 바인딩하기 위한 모델.
class JobModel : public QAbstractListModel
{
    Q_OBJECT
public:
    enum Roles {
        IdRole = Qt::UserRole + 1, CaseRole, TitleRole, ModeRole, ModeCodeRole,
        StatusRole, ProgressRole
    };

    explicit JobModel(QObject* parent = nullptr);

    void setApi(ApiClient* api) { m_api = api; }

    Q_PROPERTY(QVariantMap current READ current NOTIFY currentChanged)
    QVariantMap current() const { return m_current; }   // 최근 세션 요약 — 폰 패널/디스패치용

    Q_INVOKABLE void reload();                                       // 케이스 라벨 → 세션 목록
    Q_INVOKABLE void loadCases();                                    // 지원건 목록 → casesReady
    Q_INVOKABLE void createSession(int caseId, const QString& mode); // 생성 → sessionCreated
    Q_INVOKABLE void loadProgress(int sessionId);                    // 진행률 → progressReady
    Q_INVOKABLE QVariantMap sessionContext(qint64 sessionId) const;  // 알림에서 실제 세션 문맥 복원
    Q_INVOKABLE void markResumed(int sessionId);                     // 이어받기 시각 기록 → resumed
    Q_INVOKABLE void dispatchToPhone(int sessionId);                 // 폰/웹으로 알림 발송 → dispatched

signals:
    void casesReady(const QVariantList& cases);
    void sessionCreated(int sessionId, int caseId, const QString& modeLabel, const QString& title);
    void progressReady(const QVariantMap& progress);
    void resumed(int sessionId);
    void dispatched(int sessionId);
    void currentChanged();

public:
    int rowCount(const QModelIndex& parent = QModelIndex()) const override;
    QVariant data(const QModelIndex& index, int role) const override;
    QHash<int, QByteArray> roleNames() const override;

private:
    void loadSessions();
    QString caseLabel(qint64 caseId) const;
    int indexOf(qint64 id) const;

    QVector<Job> m_jobs;
    QHash<qint64, QString> m_caseLabels;   // caseId → "회사 · 직무"
    QVariantMap m_current;
    ApiClient* m_api = nullptr;
};
