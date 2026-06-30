#include "SseClient.h"
#include <QNetworkReply>
#include <QNetworkRequest>
#include <QUrl>
#include <QList>

SseClient::SseClient(QObject* parent) : QObject(parent)
{
    m_reconnectTimer.setSingleShot(true);
    connect(&m_reconnectTimer, &QTimer::timeout, this, [this]() { connectStream(); });
}

void SseClient::subscribe(const QString& url)
{
    m_url = url;
    m_stopped = false;
    m_lastSeq = 0;
    connectStream();
}

void SseClient::stop()
{
    m_stopped = true;
    m_reconnectTimer.stop();
    if (m_reply) {
        m_reply->abort();
        m_reply->deleteLater();
        m_reply = nullptr;
    }
}

void SseClient::connectStream()
{
    if (m_stopped) return;

    QNetworkRequest req{QUrl(m_url)};
    req.setRawHeader("Accept", "text/event-stream");
    if (!m_token.isEmpty())
        req.setRawHeader("Authorization", QByteArray("Bearer ") + m_token.toUtf8());
    // 끊겼다 다시 붙는 경우: 마지막 본 번호 이후부터 받기
    if (m_lastSeq > 0)
        req.setRawHeader("Last-Event-ID", QByteArray::number(m_lastSeq));

    m_buffer.clear();
    m_reply = m_nam.get(req);
    connect(m_reply, &QNetworkReply::readyRead, this, &SseClient::onReadyRead);
    connect(m_reply, &QNetworkReply::finished,  this, &SseClient::onFinished);
    emit streamOpened();
}

void SseClient::onReadyRead()
{
    if (!m_reply) return;
    m_buffer += m_reply->readAll();
    processBuffer();
}

void SseClient::processBuffer()
{
    // SSE 이벤트는 빈 줄(\n\n)으로 구분된다.
    int idx;
    while ((idx = m_buffer.indexOf("\n\n")) != -1) {
        const QByteArray chunk = m_buffer.left(idx);
        m_buffer.remove(0, idx + 2);

        qint64 seq = -1;
        QString type, data;
        const QList<QByteArray> lines = chunk.split('\n');
        for (const QByteArray& lineRaw : lines) {
            const QByteArray line = lineRaw.trimmed();
            if (line.startsWith("id:"))
                seq = line.mid(3).trimmed().toLongLong();
            else if (line.startsWith("event:"))
                type = QString::fromUtf8(line.mid(6).trimmed());
            else if (line.startsWith("data:"))
                data += QString::fromUtf8(line.mid(5).trimmed());
        }
        if (seq >= 0) m_lastSeq = seq;            // 책갈피 갱신
        if (!type.isEmpty() || !data.isEmpty())
            emit eventReceived(seq, type, data);
    }
}

void SseClient::onFinished()
{
    if (m_reply) {
        m_reply->deleteLater();
        m_reply = nullptr;
    }
    emit streamClosed();
    if (!m_stopped)
        scheduleReconnect();                       // 끊기면 자동 재연결
}

void SseClient::scheduleReconnect()
{
    m_reconnectTimer.start(2000);                  // 2초 후 재시도(Last-Event-ID로 이어받음)
}
