#include "ApiClient.h"
#include <QNetworkReply>
#include <QJsonDocument>
#include <QUrl>

ApiClient::ApiClient(QObject* parent) : QObject(parent) {}

QNetworkRequest ApiClient::makeRequest(const QString& path) const
{
    QNetworkRequest req{QUrl(m_baseUrl + path)};
    req.setHeader(QNetworkRequest::ContentTypeHeader, "application/json");
    if (!m_token.isEmpty())
        req.setRawHeader("Authorization", QByteArray("Bearer ") + m_token.toUtf8());
    return req;
}

void ApiClient::get(const QString& path, JsonCallback cb)
{
    handle(m_nam.get(makeRequest(path)), std::move(cb));
}

void ApiClient::post(const QString& path, const QJsonObject& body, JsonCallback cb)
{
    const QByteArray payload = QJsonDocument(body).toJson(QJsonDocument::Compact);
    handle(m_nam.post(makeRequest(path), payload), std::move(cb));
}

void ApiClient::handle(QNetworkReply* reply, JsonCallback cb)
{
    connect(reply, &QNetworkReply::finished, this, [reply, cb = std::move(cb)]() {
        const QByteArray raw = reply->readAll();
        reply->deleteLater();

        const QJsonObject root = QJsonDocument::fromJson(raw).object();
        // 공통 envelope: { success, code, message, data }
        const bool ok        = root.value("success").toBool();
        const QJsonObject data = root.value("data").toObject();
        const QString message  = root.value("message").toString();
        if (cb) cb(ok, data, message);
    });
}
