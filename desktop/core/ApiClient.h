#pragma once
#include <QObject>
#include <QNetworkAccessManager>
#include <QNetworkRequest>
#include <QJsonDocument>
#include <QJsonObject>
#include <QJsonValue>
#include <QString>
#include <QList>
#include <QPair>
#include <QVector>
#include <QtTypes>
#include <functional>

#include "SettingsStore.h"

class QNetworkReply;

// 서버 REST 호출 래퍼.
// - 모든 응답은 공통 envelope { success, code, message, data } 로 가정하고 파싱한다.
// - JWT 토큰이 있으면 Authorization: Bearer 를 자동 첨부한다.
class ApiClient : public QObject
{
    Q_OBJECT
public:
    explicit ApiClient(QObject* parent = nullptr);

    Q_INVOKABLE void setBaseUrl(const QString& url);
    /** 새 로그인/로그아웃처럼 사용자 정체성이 바뀌는 토큰 교체. 이전 인증 응답은 폐기한다. */
    void setToken(const QString& token);
    Q_INVOKABLE QString baseUrl() const { return m_baseUrl; }
    QString token() const { return m_token; }

    // ok = envelope.success, data = envelope.data, message = envelope.message
    using JsonCallback = std::function<void(bool ok, const QJsonValue& data, const QString& message)>;
    using DetailedJsonCallback = std::function<void(bool ok, const QJsonValue& data,
                                                    const QString& message, int httpStatus)>;
    // raw 응답(파일 다운로드 등 envelope 아님)
    using BytesCallback = std::function<void(bool ok, const QByteArray& bytes, const QString& contentType)>;
    using Headers = QList<QPair<QByteArray, QByteArray>>;
    using RefreshTokenProvider = std::function<QString()>;
    using TokenResponseConsumer = std::function<void(const QJsonObject&)>;

    // multipart 파일 파트 한 개
    struct FilePart {
        QString    fieldName; // form 파트 이름 (예: "file")
        QString    fileName;  // 서버에 전달할 파일명
        QString    mimeType;  // 예: "audio/webm"
        QByteArray data;
    };

    /** AuthService가 메모리/보관 refresh token과 회전된 TokenResponse 처리기를 연결한다. */
    void configureTokenRefresh(RefreshTokenProvider provider, TokenResponseConsumer consumer);
    void clearTokenRefreshConfiguration();

    void get(const QString& path, JsonCallback cb);
    void get(const QString& path, const Headers& headers, JsonCallback cb);
    void post(const QString& path, const QJsonObject& body, JsonCallback cb);
    void post(const QString& path, const QJsonObject& body,
              const Headers& headers, JsonCallback cb);
    /** 멱등 요청처럼 네트워크/5xx와 확정 4xx를 구분해야 하는 호출용. */
    void postDetailed(const QString& path, const QJsonObject& body, DetailedJsonCallback cb);
    void postDetailed(const QString& path, const QJsonObject& body,
                      const Headers& headers, DetailedJsonCallback cb);
    // PATCH — 부분 갱신 (알림 읽음 처리·대화방 음소거 등). Qt 에 전용 API 가 없어 sendCustomRequest 사용.
    void patch(const QString& path, const QJsonObject& body, JsonCallback cb);
    void patch(const QString& path, const QJsonObject& body,
               const Headers& headers, JsonCallback cb);
    void deleteResource(const QString& path, JsonCallback cb);
    void deleteResource(const QString& path, const Headers& headers, JsonCallback cb);
    /** 로그아웃/서버 전환과 독립적으로 old URL+bearer를 캡처해 끝내는 1회성 업로드 정리 요청. */
    void deleteResourceWithToken(const QString& path, const QString& bearerToken, JsonCallback cb);
    /** 비동기 작업 시작 시 캡처한 origin+bearer로 지연된 정리를 안전하게 보낸다. */
    void deleteResourceWithToken(const QString& path, const QString& baseUrl,
                                 const QString& bearerToken, JsonCallback cb);
    /**
     * 로그아웃용 refresh token을 캡처한 origin에서 폐기한다.
     * 일반 요청/서버 전환과 분리하고 access token 없이 refresh-token body 계약으로 호출한다.
     * 완료 또는 제한 시간 종료 시 callback을 반드시 호출한다.
     */
    void revokeSessionAtLogout(const QString& baseUrl, const QString& refreshToken,
                               JsonCallback cb = {});
    // multipart/form-data POST — 음성 업로드 등
    void postMultipart(const QString& path,
                       const QList<QPair<QString, QString>>& fields,
                       const QList<FilePart>& files,
                       JsonCallback cb);
    void postMultipart(const QString& path,
                       const QList<QPair<QString, QString>>& fields,
                       const QList<FilePart>& files,
                       const Headers& headers,
                       JsonCallback cb);
    // envelope 파싱 없이 바이트 그대로 — GET /api/file/{id}/content
    void download(const QString& path, BytesCallback cb);
    void download(const QString& path, const Headers& headers, BytesCallback cb);

signals:
    /** 새 로그인/로그아웃으로 인증 주체가 바뀌어 화면 모델을 즉시 비워야 한다. */
    void authenticationIdentityChanged();
    /** 401 뒤 refresh token 회전까지 실패해 명시적 재로그인이 필요하다. */
    void authenticationExpired(const QString& message);

private:
    struct RequestSpec {
        QByteArray method;
        QNetworkRequest request;
        QByteArray payload;
        QList<QPair<QString, QString>> fields;
        QList<FilePart> files;
        QString bearerAtSend;
        quint64 serverGeneration = 0;
        quint64 authGeneration = 0;
        bool multipart = false;
        bool authenticated = false;
        bool allowRefresh = false;
        bool retried = false;
    };

    QNetworkRequest makeRequest(const QString& path) const;
    RequestSpec makeSpec(const QString& path, const QByteArray& method,
                         const QByteArray& payload, const Headers& headers) const;
    static void applyHeaders(QNetworkRequest& request, const Headers& headers);
    QNetworkReply* issue(const RequestSpec& spec);
    void send(RequestSpec spec, JsonCallback cb);
    void sendDetailed(RequestSpec spec, DetailedJsonCallback cb);
    void sendBytes(RequestSpec spec, BytesCallback cb);
    void handle(QNetworkReply* reply, RequestSpec spec, JsonCallback cb);
    void handleDetailed(QNetworkReply* reply, RequestSpec spec, DetailedJsonCallback cb);
    void handleBytes(QNetworkReply* reply, RequestSpec spec, BytesCallback cb);
    void retryAfterUnauthorized(RequestSpec spec, JsonCallback cb);
    void retryDetailedAfterUnauthorized(RequestSpec spec, DetailedJsonCallback cb);
    void retryBytesAfterUnauthorized(RequestSpec spec, BytesCallback cb);
    void enqueueTokenRefresh(std::function<void()> retry);
    void beginTokenRefresh();
    void failTokenRefresh(const QString& message);
    void cancelTokenRefresh();
    void handleIndependentCleanup(QNetworkReply* reply, JsonCallback cb);
    void startPendingLogoutRevocation(const QString& refreshToken = {});

    QNetworkAccessManager m_nam;
    // 일반 요청과 분리해 refresh 요청 자체가 401 재시도 경로로 재귀 진입하지 않게 한다.
    QNetworkAccessManager m_refreshNam;
    // 서버 전환 시 m_nam의 일반 요청을 abort해도 이미 시작한 old-server 정리는 끝까지 보낸다.
    QNetworkAccessManager m_cleanupNam;
    // 기본은 팀 공용 원격 백엔드(Tailscale) — 상수는 SettingsStore 한 곳에서 관리.
    // 실제 기동 시 SettingsStore 에 저장된 값으로 덮어쓴다.
    QString m_baseUrl = SettingsStore::defaultBaseUrl();
    QString m_token;
    quint64 m_generation = 0;
    quint64 m_authGeneration = 0;
    quint64 m_refreshAttempt = 0;
    bool m_refreshInFlight = false;
    RefreshTokenProvider m_refreshTokenProvider;
    TokenResponseConsumer m_tokenResponseConsumer;
    QVector<std::function<void()>> m_refreshWaiters;
    bool m_logoutRevocationPending = false;
    QString m_logoutBaseUrl;
    QString m_logoutRefreshToken;
    JsonCallback m_logoutCallback;
    quint64 m_logoutRevocationAttempt = 0;
};
