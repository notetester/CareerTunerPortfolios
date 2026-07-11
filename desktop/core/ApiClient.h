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
    // PATCH — 부분 갱신 (알림 읽음 처리·대화방 음소거 등). Qt 에 전용 API 가 없어 sendCustomRequest 사용.
    void patch(const QString& path, const QJsonObject& body, JsonCallback cb)
    {
        const QByteArray payload = QJsonDocument(body).toJson(QJsonDocument::Compact);
        handle(m_nam.sendCustomRequest(makeRequest(path), QByteArrayLiteral("PATCH"), payload), std::move(cb));
    }
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
    // 기본은 팀 공용 원격 백엔드(Tailscale) — 상수는 SettingsStore 한 곳에서 관리.
    // 실제 기동 시 SettingsStore 에 저장된 값으로 덮어쓴다.
    QString m_baseUrl = SettingsStore::defaultBaseUrl();
    QString m_token;
    quint64 m_generation = 0;
};
