#include "ApiClient.h"

#include <QHttpMultiPart>
#include <QJsonDocument>
#include <QNetworkReply>
#include <QTimer>
#include <QUrl>

namespace {
constexpr auto kAuthHeader = "Authorization";

QString cancelledForServerChange()
{
    return QStringLiteral("서버가 변경되어 요청이 취소되었습니다.");
}

QString expiredMessage(const QString& message = {})
{
    return message.isEmpty()
        ? QStringLiteral("로그인 세션이 만료되었습니다. 다시 로그인해 주세요.")
        : message;
}

bool isAuthenticationBootstrap(const QString& path)
{
    const QString endpoint = path.section(QLatin1Char('?'), 0, 0);
    return endpoint == QStringLiteral("/api/auth/login")
        || endpoint == QStringLiteral("/api/auth/refresh")
        || endpoint == QStringLiteral("/api/auth/logout")
        || endpoint.startsWith(QStringLiteral("/api/auth/mfa/login/"))
        || endpoint.startsWith(QStringLiteral("/api/auth/native-handoff"))
        || endpoint.startsWith(QStringLiteral("/api/auth/oauth/"));
}
} // namespace

ApiClient::ApiClient(QObject* parent) : QObject(parent) {}

void ApiClient::setBaseUrl(const QString& url)
{
    if (url == m_baseUrl) return;

    // 이전 호스트에서 진행 중인 인증/데이터 응답이 새 호스트 세션을 되살리지 않게 중단한다.
    cancelTokenRefresh();
    ++m_generation;
    ++m_authGeneration;
    m_token.clear();
    m_baseUrl = url;
    const auto replies = m_nam.findChildren<QNetworkReply*>();
    for (QNetworkReply* reply : replies)
        reply->abort();
    emit authenticationIdentityChanged();
}

void ApiClient::setToken(const QString& token)
{
    if (token == m_token) return;

    // 로그인 주체 교체와 access-token 회전은 다르다. 이 공개 진입점은 전자에만 사용한다.
    // refresh 성공은 beginTokenRefresh()가 m_token만 바꿔 동일 사용자의 모델/요청을 유지한다.
    cancelTokenRefresh();
    ++m_authGeneration;
    m_token = token;
    emit authenticationIdentityChanged();
}

void ApiClient::configureTokenRefresh(RefreshTokenProvider provider,
                                      TokenResponseConsumer consumer)
{
    m_refreshTokenProvider = std::move(provider);
    m_tokenResponseConsumer = std::move(consumer);
}

void ApiClient::clearTokenRefreshConfiguration()
{
    cancelTokenRefresh();
    m_refreshTokenProvider = {};
    m_tokenResponseConsumer = {};
}

QNetworkRequest ApiClient::makeRequest(const QString& path) const
{
    QNetworkRequest req{QUrl(m_baseUrl + path)};
    req.setHeader(QNetworkRequest::ContentTypeHeader, "application/json");
    if (!m_token.isEmpty())
        req.setRawHeader(kAuthHeader, QByteArray("Bearer ") + m_token.toUtf8());
    return req;
}

void ApiClient::applyHeaders(QNetworkRequest& request, const Headers& headers)
{
    for (const auto& header : headers) {
        const QByteArray name = header.first.trimmed();
        const QByteArray value = header.second;
        const QByteArray lower = name.toLower();
        // 인증·전송 경계 헤더는 호출자가 덮어쓸 수 없게 하고, 줄바꿈 삽입도 거부한다.
        if (name.isEmpty() || name.contains('\r') || name.contains('\n')
            || value.contains('\r') || value.contains('\n')
            || lower == QByteArrayLiteral("authorization")
            || lower == QByteArrayLiteral("host")
            || lower == QByteArrayLiteral("content-length")
            || lower == QByteArrayLiteral("cookie")) {
            continue;
        }
        request.setRawHeader(name, value);
    }
}

ApiClient::RequestSpec ApiClient::makeSpec(const QString& path, const QByteArray& method,
                                           const QByteArray& payload,
                                           const Headers& headers) const
{
    RequestSpec spec;
    spec.method = method;
    spec.request = makeRequest(path);
    applyHeaders(spec.request, headers);
    spec.payload = payload;
    spec.bearerAtSend = m_token;
    spec.serverGeneration = m_generation;
    spec.authGeneration = m_authGeneration;
    spec.authenticated = !m_token.isEmpty();
    // 인증 자체의 요청은 refresh 재귀나 로그인 도중의 기존 세션 연장을 허용하지 않는다.
    spec.allowRefresh = spec.authenticated && !m_logoutRevocationPending
        && !isAuthenticationBootstrap(path);
    return spec;
}

QNetworkReply* ApiClient::issue(const RequestSpec& spec)
{
    QNetworkRequest request = spec.request;
    if (spec.authenticated && !spec.bearerAtSend.isEmpty()) {
        request.setRawHeader(kAuthHeader,
                             QByteArray("Bearer ") + spec.bearerAtSend.toUtf8());
    } else {
        request.setRawHeader(kAuthHeader, QByteArray());
    }

    if (spec.multipart) {
        auto* multi = new QHttpMultiPart(QHttpMultiPart::FormDataType);
        for (const auto& field : spec.fields) {
            QHttpPart part;
            part.setHeader(QNetworkRequest::ContentDispositionHeader,
                           QStringLiteral("form-data; name=\"%1\"").arg(field.first));
            part.setBody(field.second.toUtf8());
            multi->append(part);
        }
        for (const auto& file : spec.files) {
            QHttpPart part;
            part.setHeader(QNetworkRequest::ContentDispositionHeader,
                           QStringLiteral("form-data; name=\"%1\"; filename=\"%2\"")
                               .arg(file.fieldName, file.fileName));
            part.setHeader(QNetworkRequest::ContentTypeHeader, file.mimeType);
            part.setBody(file.data);
            multi->append(part);
        }
        QNetworkReply* reply = m_nam.post(request, multi);
        multi->setParent(reply);
        return reply;
    }

    if (spec.method == QByteArrayLiteral("GET"))
        return m_nam.get(request);
    if (spec.method == QByteArrayLiteral("POST"))
        return m_nam.post(request, spec.payload);
    if (spec.method == QByteArrayLiteral("DELETE"))
        return m_nam.deleteResource(request);
    return m_nam.sendCustomRequest(request, spec.method, spec.payload);
}

void ApiClient::get(const QString& path, JsonCallback cb)
{
    get(path, {}, std::move(cb));
}

void ApiClient::get(const QString& path, const Headers& headers, JsonCallback cb)
{
    send(makeSpec(path, QByteArrayLiteral("GET"), {}, headers), std::move(cb));
}

void ApiClient::post(const QString& path, const QJsonObject& body, JsonCallback cb)
{
    post(path, body, {}, std::move(cb));
}

void ApiClient::post(const QString& path, const QJsonObject& body,
                     const Headers& headers, JsonCallback cb)
{
    send(makeSpec(path, QByteArrayLiteral("POST"),
                  QJsonDocument(body).toJson(QJsonDocument::Compact), headers),
         std::move(cb));
}

void ApiClient::postDetailed(const QString& path, const QJsonObject& body,
                             DetailedJsonCallback cb)
{
    postDetailed(path, body, {}, std::move(cb));
}

void ApiClient::postDetailed(const QString& path, const QJsonObject& body,
                             const Headers& headers, DetailedJsonCallback cb)
{
    sendDetailed(makeSpec(path, QByteArrayLiteral("POST"),
                          QJsonDocument(body).toJson(QJsonDocument::Compact), headers),
                 std::move(cb));
}

void ApiClient::patch(const QString& path, const QJsonObject& body, JsonCallback cb)
{
    patch(path, body, {}, std::move(cb));
}

void ApiClient::patch(const QString& path, const QJsonObject& body,
                      const Headers& headers, JsonCallback cb)
{
    send(makeSpec(path, QByteArrayLiteral("PATCH"),
                  QJsonDocument(body).toJson(QJsonDocument::Compact), headers),
         std::move(cb));
}

void ApiClient::deleteResource(const QString& path, JsonCallback cb)
{
    deleteResource(path, {}, std::move(cb));
}

void ApiClient::deleteResource(const QString& path, const Headers& headers, JsonCallback cb)
{
    send(makeSpec(path, QByteArrayLiteral("DELETE"), {}, headers), std::move(cb));
}

void ApiClient::deleteResourceWithToken(const QString& path, const QString& bearerToken,
                                        JsonCallback cb)
{
    deleteResourceWithToken(path, m_baseUrl, bearerToken, std::move(cb));
}

void ApiClient::deleteResourceWithToken(const QString& path, const QString& baseUrl,
                                        const QString& bearerToken, JsonCallback cb)
{
    // origin/token을 값으로 고정한다. 일반 요청의 서버 전환/로그아웃과 독립적으로 끝낸다.
    QNetworkRequest request{QUrl(baseUrl + path)};
    request.setHeader(QNetworkRequest::ContentTypeHeader, "application/json");
    if (!bearerToken.isEmpty())
        request.setRawHeader(kAuthHeader, QByteArray("Bearer ") + bearerToken.toUtf8());
    handleIndependentCleanup(m_cleanupNam.deleteResource(request), std::move(cb));
}

void ApiClient::revokeSessionAtLogout(const QString& baseUrl, const QString& refreshToken,
                                      JsonCallback cb)
{
    if (refreshToken.isEmpty()) {
        if (cb) cb(true, QJsonValue(), QString());
        return;
    }
    m_logoutRevocationPending = true;
    m_logoutBaseUrl = baseUrl;
    m_logoutRefreshToken = refreshToken;
    m_logoutCallback = std::move(cb);
    const quint64 logoutAttempt = ++m_logoutRevocationAttempt;
    // 이미 시작된 401 refresh가 있으면 응답에서 회전된 refresh token을 확보한 뒤 폐기한다.
    // 기능 요청 replay는 명시적 로그아웃과 경쟁하지 않도록 버린다.
    m_refreshWaiters.clear();
    if (!m_refreshInFlight) {
        startPendingLogoutRevocation();
    } else {
        // 이미 wire에 올라간 refresh가 멈춘 경우에도 명시 로그아웃이 무기한 대기하지 않는다.
        QTimer::singleShot(3000, this, [this, logoutAttempt]() {
            if (!m_logoutRevocationPending || logoutAttempt != m_logoutRevocationAttempt
                || !m_refreshInFlight) return;
            const auto replies = m_refreshNam.findChildren<QNetworkReply*>();
            for (QNetworkReply* reply : replies) reply->abort();
        });
    }
}

void ApiClient::startPendingLogoutRevocation(const QString& refreshToken)
{
    if (!m_logoutRevocationPending) return;
    if (!refreshToken.isEmpty()) m_logoutRefreshToken = refreshToken;

    QNetworkRequest request{QUrl(m_logoutBaseUrl + QStringLiteral("/api/auth/logout"))};
    request.setHeader(QNetworkRequest::ContentTypeHeader, "application/json");
    const QByteArray payload = QJsonDocument(QJsonObject{
        {QStringLiteral("refreshToken"), m_logoutRefreshToken}
    }).toJson(QJsonDocument::Compact);

    QNetworkReply* reply = m_cleanupNam.post(request, payload);
    // 명시적 로그아웃은 로컬 화면 전환을 무기한 붙들 수 없다. old-origin 전송은 일반
    // 요청과 분리해 끝까지 시도하되, 네트워크 장애 시 3초 뒤 abort하여 callback을 보장한다.
    QTimer::singleShot(3000, reply, [reply]() {
        if (reply->isRunning()) reply->abort();
    });
    connect(reply, &QNetworkReply::finished, this,
            [this, reply]() mutable {
        const int status = reply->attribute(QNetworkRequest::HttpStatusCodeAttribute).toInt();
        const QByteArray raw = reply->readAll();
        const QString networkError = reply->errorString();
        reply->deleteLater();

        const QJsonObject root = QJsonDocument::fromJson(raw).object();
        const bool ok = status >= 200 && status < 300
            && root.value(QStringLiteral("success")).toBool();
        QString message = root.value(QStringLiteral("message")).toString();
        if (message.isEmpty() && !ok) message = networkError;
        JsonCallback cb = std::move(m_logoutCallback);
        m_logoutCallback = {};
        ++m_logoutRevocationAttempt;
        m_logoutRevocationPending = false;
        m_logoutBaseUrl.clear();
        m_logoutRefreshToken.clear();
        if (cb) cb(ok, root.value(QStringLiteral("data")), message);
    });
}

void ApiClient::postMultipart(const QString& path,
                              const QList<QPair<QString, QString>>& fields,
                              const QList<FilePart>& files,
                              JsonCallback cb)
{
    postMultipart(path, fields, files, {}, std::move(cb));
}

void ApiClient::postMultipart(const QString& path,
                              const QList<QPair<QString, QString>>& fields,
                              const QList<FilePart>& files,
                              const Headers& headers,
                              JsonCallback cb)
{
    RequestSpec spec = makeSpec(path, QByteArrayLiteral("POST"), {}, headers);
    // QHttpMultiPart가 boundary를 포함한 Content-Type을 설정한다.
    spec.request.setHeader(QNetworkRequest::ContentTypeHeader, QVariant());
    spec.multipart = true;
    spec.fields = fields;
    spec.files = files;
    send(std::move(spec), std::move(cb));
}

void ApiClient::download(const QString& path, BytesCallback cb)
{
    download(path, {}, std::move(cb));
}

void ApiClient::download(const QString& path, const Headers& headers, BytesCallback cb)
{
    sendBytes(makeSpec(path, QByteArrayLiteral("GET"), {}, headers), std::move(cb));
}

void ApiClient::send(RequestSpec spec, JsonCallback cb)
{
    QNetworkReply* reply = issue(spec);
    handle(reply, std::move(spec), std::move(cb));
}

void ApiClient::sendDetailed(RequestSpec spec, DetailedJsonCallback cb)
{
    QNetworkReply* reply = issue(spec);
    handleDetailed(reply, std::move(spec), std::move(cb));
}

void ApiClient::sendBytes(RequestSpec spec, BytesCallback cb)
{
    QNetworkReply* reply = issue(spec);
    handleBytes(reply, std::move(spec), std::move(cb));
}

void ApiClient::handle(QNetworkReply* reply, RequestSpec spec, JsonCallback cb)
{
    connect(reply, &QNetworkReply::finished, this,
            [this, reply, spec = std::move(spec), cb = std::move(cb)]() mutable {
        const int status = reply->attribute(QNetworkRequest::HttpStatusCodeAttribute).toInt();
        const QByteArray raw = reply->readAll();
        const QString networkError = reply->errorString();
        reply->deleteLater();

        // 서버 변경은 인증 주체도 폐기한다. 두 세대를 모두 먼저 확인해야 이전 서버의
        // 비인증(login 등) 응답이 새 서버/계정 UI에 실패 콜백으로도 섞이지 않는다.
        if (spec.authGeneration != m_authGeneration)
            return;
        if (spec.serverGeneration != m_generation) {
            if (cb) cb(false, QJsonValue(), cancelledForServerChange());
            return;
        }

        if (status == 401 && spec.allowRefresh) {
            retryAfterUnauthorized(std::move(spec), std::move(cb));
            return;
        }

        const QJsonObject root = QJsonDocument::fromJson(raw).object();
        const bool ok = root.value(QStringLiteral("success")).toBool();
        const QJsonValue data = root.value(QStringLiteral("data"));
        QString message = root.value(QStringLiteral("message")).toString();
        if (message.isEmpty() && !ok && status == 0)
            message = networkError;
        if (cb) cb(ok, data, message);
    });
}

void ApiClient::handleDetailed(QNetworkReply* reply, RequestSpec spec,
                               DetailedJsonCallback cb)
{
    connect(reply, &QNetworkReply::finished, this,
            [this, reply, spec = std::move(spec), cb = std::move(cb)]() mutable {
        const int status = reply->attribute(QNetworkRequest::HttpStatusCodeAttribute).toInt();
        const QByteArray raw = reply->readAll();
        const QString networkError = reply->errorString();
        reply->deleteLater();

        if (spec.authGeneration != m_authGeneration) return;
        if (spec.serverGeneration != m_generation) return;
        if (status == 401 && spec.allowRefresh) {
            retryDetailedAfterUnauthorized(std::move(spec), std::move(cb));
            return;
        }

        const QJsonObject root = QJsonDocument::fromJson(raw).object();
        const bool ok = root.value(QStringLiteral("success")).toBool();
        const QJsonValue data = root.value(QStringLiteral("data"));
        QString message = root.value(QStringLiteral("message")).toString();
        if (message.isEmpty() && !ok && status == 0) message = networkError;
        if (cb) cb(ok, data, message, status);
    });
}

void ApiClient::handleBytes(QNetworkReply* reply, RequestSpec spec, BytesCallback cb)
{
    connect(reply, &QNetworkReply::finished, this,
            [this, reply, spec = std::move(spec), cb = std::move(cb)]() mutable {
        const int status = reply->attribute(QNetworkRequest::HttpStatusCodeAttribute).toInt();
        const QByteArray bytes = reply->readAll();
        const QString ctype = reply->header(QNetworkRequest::ContentTypeHeader).toString();
        const bool networkOk = reply->error() == QNetworkReply::NoError;
        reply->deleteLater();

        if (spec.authGeneration != m_authGeneration)
            return;
        if (spec.serverGeneration != m_generation) {
            if (cb) cb(false, {}, {});
            return;
        }
        if (status == 401 && spec.allowRefresh) {
            retryBytesAfterUnauthorized(std::move(spec), std::move(cb));
            return;
        }
        if (cb) cb(networkOk, bytes, ctype);
    });
}

void ApiClient::retryAfterUnauthorized(RequestSpec spec, JsonCallback cb)
{
    if (spec.retried) {
        failTokenRefresh(QStringLiteral("갱신된 로그인 토큰이 거부되었습니다. 다시 로그인해 주세요."));
        return;
    }

    spec.retried = true;
    if (!m_token.isEmpty() && spec.bearerAtSend != m_token) {
        spec.bearerAtSend = m_token;
        send(std::move(spec), std::move(cb));
        return;
    }

    enqueueTokenRefresh([this, spec = std::move(spec), cb = std::move(cb)]() mutable {
        spec.bearerAtSend = m_token;
        send(std::move(spec), std::move(cb));
    });
}

void ApiClient::retryDetailedAfterUnauthorized(RequestSpec spec, DetailedJsonCallback cb)
{
    if (spec.retried) {
        failTokenRefresh(QStringLiteral("갱신된 로그인 토큰이 거부되었습니다. 다시 로그인해 주세요."));
        return;
    }

    spec.retried = true;
    if (!m_token.isEmpty() && spec.bearerAtSend != m_token) {
        spec.bearerAtSend = m_token;
        sendDetailed(std::move(spec), std::move(cb));
        return;
    }

    enqueueTokenRefresh([this, spec = std::move(spec), cb = std::move(cb)]() mutable {
        spec.bearerAtSend = m_token;
        sendDetailed(std::move(spec), std::move(cb));
    });
}

void ApiClient::retryBytesAfterUnauthorized(RequestSpec spec, BytesCallback cb)
{
    if (spec.retried) {
        failTokenRefresh(QStringLiteral("갱신된 로그인 토큰이 거부되었습니다. 다시 로그인해 주세요."));
        return;
    }

    spec.retried = true;
    if (!m_token.isEmpty() && spec.bearerAtSend != m_token) {
        spec.bearerAtSend = m_token;
        sendBytes(std::move(spec), std::move(cb));
        return;
    }

    enqueueTokenRefresh([this, spec = std::move(spec), cb = std::move(cb)]() mutable {
        spec.bearerAtSend = m_token;
        sendBytes(std::move(spec), std::move(cb));
    });
}

void ApiClient::enqueueTokenRefresh(std::function<void()> retry)
{
    m_refreshWaiters.push_back(std::move(retry));
    if (!m_refreshInFlight)
        beginTokenRefresh();
}

void ApiClient::beginTokenRefresh()
{
    const QString refreshToken = m_refreshTokenProvider ? m_refreshTokenProvider() : QString();
    if (refreshToken.isEmpty()) {
        failTokenRefresh({});
        return;
    }

    m_refreshInFlight = true;
    const quint64 attempt = ++m_refreshAttempt;
    const quint64 serverGeneration = m_generation;
    const quint64 authGeneration = m_authGeneration;

    QNetworkRequest request{QUrl(m_baseUrl + QStringLiteral("/api/auth/refresh"))};
    request.setHeader(QNetworkRequest::ContentTypeHeader, "application/json");
    const QByteArray payload = QJsonDocument(QJsonObject{
        {QStringLiteral("refreshToken"), refreshToken}
    }).toJson(QJsonDocument::Compact);
    QNetworkReply* reply = m_refreshNam.post(request, payload);
    connect(reply, &QNetworkReply::finished, this,
            [this, reply, attempt, serverGeneration, authGeneration]() {
        const int status = reply->attribute(QNetworkRequest::HttpStatusCodeAttribute).toInt();
        const QByteArray raw = reply->readAll();
        reply->deleteLater();

        if (attempt != m_refreshAttempt || !m_refreshInFlight) {
            return;
        }

        const QJsonObject root = QJsonDocument::fromJson(raw).object();
        const QJsonObject data = root.value(QStringLiteral("data")).toObject();
        const QString accessToken = data.value(QStringLiteral("accessToken")).toString();
        if (m_logoutRevocationPending) {
            // 서버/계정 전환 직전 refresh가 이미 wire에 올라갔다면 abort로 결과를 잃지 않는다.
            // 기능 세대에는 적용하지 않고 회전된 refresh token만 즉시 로그아웃한다.
            m_refreshInFlight = false;
            m_refreshWaiters.clear();
            const QString rotatedRefresh = data.value(QStringLiteral("refreshToken")).toString();
            const bool refreshed = status == 200
                && root.value(QStringLiteral("success")).toBool()
                && !accessToken.isEmpty() && !rotatedRefresh.isEmpty();
            startPendingLogoutRevocation(refreshed ? rotatedRefresh : m_logoutRefreshToken);
            return;
        }
        if (serverGeneration != m_generation || authGeneration != m_authGeneration) {
            return;
        }
        if (status != 200 || !root.value(QStringLiteral("success")).toBool()
            || accessToken.isEmpty()) {
            failTokenRefresh(root.value(QStringLiteral("message")).toString());
            return;
        }

        // 동일 사용자의 access/refresh token 회전이므로 authGeneration과 모델은 유지한다.
        m_refreshInFlight = false;
        m_token = accessToken;
        if (m_tokenResponseConsumer)
            m_tokenResponseConsumer(data);

        QVector<std::function<void()>> waiters = std::move(m_refreshWaiters);
        m_refreshWaiters.clear();
        for (auto& retry : waiters)
            if (retry) retry();
    });
}

void ApiClient::failTokenRefresh(const QString& message)
{
    ++m_refreshAttempt;
    m_refreshInFlight = false;
    m_refreshWaiters.clear();
    ++m_authGeneration;
    m_token.clear();
    emit authenticationIdentityChanged();
    emit authenticationExpired(expiredMessage(message));
}

void ApiClient::cancelTokenRefresh()
{
    if (m_logoutRevocationPending) {
        // 명시적 로그아웃 중에는 wire에 올라간 refresh 응답에서 회전 토큰을 회수해야 한다.
        // 기능 replay만 제거하고 old-origin refresh 자체는 완료시킨다.
        m_refreshWaiters.clear();
        return;
    }
    ++m_refreshAttempt;
    m_refreshInFlight = false;
    m_refreshWaiters.clear();
    const auto replies = m_refreshNam.findChildren<QNetworkReply*>();
    for (QNetworkReply* reply : replies)
        reply->abort();
}

void ApiClient::handleIndependentCleanup(QNetworkReply* reply, JsonCallback cb)
{
    connect(reply, &QNetworkReply::finished, this, [reply, cb = std::move(cb)]() {
        const QByteArray raw = reply->readAll();
        reply->deleteLater();

        const QJsonObject root = QJsonDocument::fromJson(raw).object();
        const bool ok = root.value(QStringLiteral("success")).toBool();
        const QJsonValue data = root.value(QStringLiteral("data"));
        const QString message = root.value(QStringLiteral("message")).toString();
        if (cb) cb(ok, data, message);
    });
}
