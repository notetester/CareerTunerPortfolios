#include "ApiClient.h"
#include <QNetworkReply>
#include <QJsonDocument>
#include <QHttpMultiPart>
#include <QUrl>

ApiClient::ApiClient(QObject* parent) : QObject(parent) {}

void ApiClient::setBaseUrl(const QString& url)
{
    if (url == m_baseUrl) return;

    // 이전 호스트에서 진행 중인 인증/데이터 응답이 새 호스트 세션을 되살리지 않게 중단한다.
    ++m_generation;
    m_token.clear();
    m_baseUrl = url;
    const auto replies = m_nam.findChildren<QNetworkReply*>();
    for (QNetworkReply* reply : replies)
        reply->abort();
}

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

void ApiClient::deleteResource(const QString& path, JsonCallback cb)
{
    handle(m_nam.deleteResource(makeRequest(path)), std::move(cb));
}

void ApiClient::deleteResourceWithToken(const QString& path, const QString& bearerToken, JsonCallback cb)
{
    deleteResourceWithToken(path, m_baseUrl, bearerToken, std::move(cb));
}

void ApiClient::deleteResourceWithToken(const QString& path, const QString& baseUrl,
                                        const QString& bearerToken, JsonCallback cb)
{
    // origin/token을 값으로 고정한다. 일반 요청의 서버 전환/로그아웃과 독립적으로 끝낸다.
    const QUrl cleanupUrl(baseUrl + path);
    QNetworkRequest request{cleanupUrl};
    request.setHeader(QNetworkRequest::ContentTypeHeader, "application/json");
    if (!bearerToken.isEmpty())
        request.setRawHeader("Authorization", QByteArray("Bearer ") + bearerToken.toUtf8());
    handleIndependentCleanup(m_cleanupNam.deleteResource(request), std::move(cb));
}

void ApiClient::postMultipart(const QString& path,
                              const QList<QPair<QString, QString>>& fields,
                              const QList<FilePart>& files,
                              JsonCallback cb)
{
    auto* multi = new QHttpMultiPart(QHttpMultiPart::FormDataType);

    for (const auto& f : fields) {
        QHttpPart part;
        part.setHeader(QNetworkRequest::ContentDispositionHeader,
                       QStringLiteral("form-data; name=\"%1\"").arg(f.first));
        part.setBody(f.second.toUtf8());
        multi->append(part);
    }
    for (const auto& f : files) {
        QHttpPart part;
        part.setHeader(QNetworkRequest::ContentDispositionHeader,
                       QStringLiteral("form-data; name=\"%1\"; filename=\"%2\"")
                           .arg(f.fieldName, f.fileName));
        part.setHeader(QNetworkRequest::ContentTypeHeader, f.mimeType);
        part.setBody(f.data);
        multi->append(part);
    }

    // Content-Type 은 QHttpMultiPart 가 boundary 포함으로 직접 설정한다
    QNetworkRequest req{QUrl(m_baseUrl + path)};
    if (!m_token.isEmpty())
        req.setRawHeader("Authorization", QByteArray("Bearer ") + m_token.toUtf8());

    QNetworkReply* reply = m_nam.post(req, multi);
    multi->setParent(reply); // reply 수명에 묶음
    handle(reply, std::move(cb));
}

void ApiClient::download(const QString& path, BytesCallback cb)
{
    QNetworkRequest req{QUrl(m_baseUrl + path)};
    if (!m_token.isEmpty())
        req.setRawHeader("Authorization", QByteArray("Bearer ") + m_token.toUtf8());

    QNetworkReply* reply = m_nam.get(req);
    const quint64 generation = m_generation;
    connect(reply, &QNetworkReply::finished, this, [this, reply, generation, cb = std::move(cb)]() {
        const QByteArray bytes = reply->readAll();
        const QString ctype = reply->header(QNetworkRequest::ContentTypeHeader).toString();
        const bool ok = generation == m_generation && reply->error() == QNetworkReply::NoError;
        reply->deleteLater();
        if (cb) cb(ok, bytes, ctype);
    });
}

void ApiClient::handle(QNetworkReply* reply, JsonCallback cb)
{
    const quint64 generation = m_generation;
    connect(reply, &QNetworkReply::finished, this, [this, reply, generation, cb = std::move(cb)]() {
        const QByteArray raw = reply->readAll();
        reply->deleteLater();

        if (generation != m_generation) {
            if (cb) cb(false, QJsonValue(), QStringLiteral("서버가 변경되어 요청이 취소되었습니다."));
            return;
        }

        const QJsonObject root = QJsonDocument::fromJson(raw).object();
        // 공통 envelope: { success, code, message, data } — data 는 object 또는 array
        const bool ok        = root.value("success").toBool();
        const QJsonValue data = root.value("data");
        const QString message  = root.value("message").toString();
        if (cb) cb(ok, data, message);
    });
}

void ApiClient::handleIndependentCleanup(QNetworkReply* reply, JsonCallback cb)
{
    connect(reply, &QNetworkReply::finished, this, [reply, cb = std::move(cb)]() {
        const QByteArray raw = reply->readAll();
        reply->deleteLater();

        const QJsonObject root = QJsonDocument::fromJson(raw).object();
        const bool ok = root.value("success").toBool();
        const QJsonValue data = root.value("data");
        const QString message = root.value("message").toString();
        if (cb) cb(ok, data, message);
    });
}
