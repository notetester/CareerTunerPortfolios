#pragma once
#include <QObject>
#include <QNetworkAccessManager>
#include <QString>
#include <QByteArray>
#include <QTimer>

class QNetworkReply;

// 서버의 실시간 진행 스트림(SSE)을 구독한다.
// 핵심: 연결이 끊기면 "마지막으로 받은 이벤트 번호(seq)"를 기억했다가
//       재연결 시 Last-Event-ID 헤더로 알려 → 서버가 그 다음부터 다시 보내준다.
//       (= 넷플릭스 이어보기. 끊겨도 진행을 놓치지 않음)
class SseClient : public QObject
{
    Q_OBJECT
public:
    explicit SseClient(QObject* parent = nullptr);

    void setToken(const QString& token) { m_token = token; }

    Q_INVOKABLE void subscribe(const QString& url); // 작업 스트림 구독 시작
    Q_INVOKABLE void stop();                        // 구독 중지(재연결 안 함)

signals:
    void eventReceived(qint64 seq, const QString& type, const QString& data);
    void streamOpened();
    void streamClosed();

private slots:
    void onReadyRead();
    void onFinished();

private:
    void connectStream();
    void scheduleReconnect();
    void processBuffer();

    QNetworkAccessManager m_nam;
    QNetworkReply* m_reply = nullptr;
    QString  m_url;
    QString  m_token;
    qint64   m_lastSeq = 0;   // 마지막으로 받은 이벤트 번호(책갈피)
    QByteArray m_buffer;      // 줄 단위 누적 버퍼
    QTimer   m_reconnectTimer;
    bool     m_stopped = false;
};
