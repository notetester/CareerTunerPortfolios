#pragma once
#include <QObject>
#include <QNetworkAccessManager>
#include <QNetworkRequest>
#include <QJsonObject>
#include <QJsonValue>
#include <QString>
#include <QList>
#include <QPair>
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
    Q_INVOKABLE QString baseUrl() const { return m_baseUrl; }
    QString token() const { return m_token; }

    // ok = envelope.success, data = envelope.data, message = envelope.message
    using JsonCallback = std::function<void(bool ok, const QJsonValue& data, const QString& message)>;
    // raw 응답(파일 다운로드 등 envelope 아님)
    using BytesCallback = std::function<void(bool ok, const QByteArray& bytes, const QString& contentType)>;

    // multipart 파일 파트 한 개
    struct FilePart {
        QString    fieldName; // form 파트 이름 (예: "file")
        QString    fileName;  // 서버에 전달할 파일명
        QString    mimeType;  // 예: "audio/webm"
        QByteArray data;
    };

    void get(const QString& path, JsonCallback cb);
    void post(const QString& path, const QJsonObject& body, JsonCallback cb);
    void deleteResource(const QString& path, JsonCallback cb);
    // multipart/form-data POST — 음성 업로드 등
    void postMultipart(const QString& path,
                       const QList<QPair<QString, QString>>& fields,
                       const QList<FilePart>& files,
                       JsonCallback cb);
    // envelope 파싱 없이 바이트 그대로 — GET /api/file/{id}/content
    void download(const QString& path, BytesCallback cb);

private:
    QNetworkRequest makeRequest(const QString& path) const;
    void handle(QNetworkReply* reply, JsonCallback cb);

    QNetworkAccessManager m_nam;
    // 기본은 팀 공용 원격 백엔드(Tailscale). 실제 기동 시 SettingsStore 값으로 덮어쓴다.
    QString m_baseUrl = "https://careertuner-dev.example.invalid";
    QString m_token;
};
