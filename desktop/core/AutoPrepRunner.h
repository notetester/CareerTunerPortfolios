#pragma once
#include <QObject>
#include <QVariantList>
#include <QVariantMap>
#include <QString>
#include <QByteArray>
#include <QNetworkAccessManager>

class ApiClient;
class QNetworkReply;
class QJsonObject;
class DesktopCoreTests;

// AI 오케스트레이터(autoprep) 인테이크 + 실행 스트림.
// - intake(): POST /api/auto-prep/intake — 되묻기(CASE/MODE) 판별
// - run():    POST /api/auto-prep/run/stream — POST 기반 SSE 를 직접 파싱해
//             plan / part-start / substep / part-done / done 이벤트를 스텝 리스트로 반영
// ⚠ POST-SSE 는 재연결하면 작업이 다시 실행되므로 자동 재연결하지 않는다 (SseClient 와 다른 점).
class AutoPrepRunner : public QObject
{
    Q_OBJECT
    Q_PROPERTY(bool         running READ running NOTIFY runningChanged)
    Q_PROPERTY(QVariantList steps   READ steps   NOTIFY stepsChanged)
public:
    explicit AutoPrepRunner(ApiClient* api, QObject* parent = nullptr);

    bool running() const { return m_running; }
    QVariantList steps() const { return m_steps; }

    Q_INVOKABLE void intake(const QString& query);
    Q_INVOKABLE void run(const QString& query, int caseId, const QString& mode);
    Q_INVOKABLE void cancel();
    Q_INVOKABLE void clear();

signals:
    void runningChanged();
    void stepsChanged();
    // intake 응답: { ready, message, nextAsk, candidates:[{caseId,label}], modes:[{code,label}] }
    void intakeReady(const QVariantMap& result);
    void finished(const QString& message);
    void errorOccurred(const QString& message);
    void cleared();

private:
    friend class DesktopCoreTests;
    void processBuffer();
    void handleEvent(const QString& type, const QString& data);
    void startStream(const QJsonObject& body, quint64 generation);
    static QString partLabel(const QString& key);
    int stepIndex(const QString& key) const;

    ApiClient* m_api;
    QNetworkAccessManager m_nam;
    QNetworkReply* m_reply = nullptr;
    QByteArray m_buffer;
    QVariantList m_steps;
    bool m_running = false;
    bool m_authPreflightPending = false;
    quint64 m_intakeGeneration = 0;
    quint64 m_runGeneration = 0;
};
