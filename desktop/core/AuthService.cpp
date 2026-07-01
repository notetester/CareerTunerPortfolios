#include "AuthService.h"
#include "ApiClient.h"
#include <QJsonObject>

AuthService::AuthService(ApiClient* api, QObject* parent)
    : QObject(parent), m_api(api) {}

void AuthService::login(const QString& email, const QString& password)
{
    QJsonObject body;
    body["email"]    = email;
    body["password"] = password;

    m_api->post("/api/auth/login", body,
        [this](bool ok, const QJsonValue& data, const QString& message) {
            const QJsonObject o = data.toObject();
            if (ok && o.contains("accessToken")) {
                m_token = o.value("accessToken").toString();
                m_api->setToken(m_token);
                emit loggedIn(m_token);
            } else {
                emit loginFailed(message.isEmpty() ? QStringLiteral("로그인 실패") : message);
            }
        });
}
