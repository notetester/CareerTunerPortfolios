#pragma once
#include <QObject>
#include <QNetworkAccessManager>
#include <QNetworkRequest>
#include <QJsonObject>
#include <QJsonValue>
#include <QString>
#include <functional>

class QNetworkReply;

// 서버 REST 호출 래퍼.
// - 모든 응답은 공통 envelope { success, code, message, data } 로 가정하고 파싱한다.
// - JWT 토큰이 있으면 Authorization: Bearer 를 자동 첨부한다.
class ApiClient : public QObject
{
    Q_OBJECT
public:
    explicit ApiClient(QObject* parent = nullptr);

    Q_INVOKABLE void setBaseUrl(const QString& url) { m_baseUrl = url; }
    void setToken(const QString& token) { m_token = token; }
    QString baseUrl() const { return m_baseUrl; }
    QString token() const { return m_token; }

    // ok = envelope.success, data = envelope.data, message = envelope.message
    using JsonCallback = std::function<void(bool ok, const QJsonValue& data, const QString& message)>;

    void get(const QString& path, JsonCallback cb);
    void post(const QString& path, const QJsonObject& body, JsonCallback cb);

private:
    QNetworkRequest makeRequest(const QString& path) const;
    void handle(QNetworkReply* reply, JsonCallback cb);

    QNetworkAccessManager m_nam;
    QString m_baseUrl = "http://localhost:8080";
    QString m_token;
};
