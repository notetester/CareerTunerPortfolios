#include "AuthService.h"
#include "ApiClient.h"
#include "SettingsStore.h"
#include <QJsonObject>

AuthService::AuthService(ApiClient* api, SettingsStore* store, QObject* parent)
    : QObject(parent), m_api(api), m_store(store) {}

void AuthService::applyTokenResponse(const QJsonObject& o)
{
    // TokenResponse: { accessToken, refreshToken, tokenType, expiresIn, user{ name, email, plan, ... } }
    m_token = o.value("accessToken").toString();
    m_api->setToken(m_token);

    if (m_store && m_store->autoLogin())
        m_store->setTokens(m_token, o.value("refreshToken").toString());

    const QJsonObject u = o.value("user").toObject();
    m_userName  = u.value("name").toString();
    m_userEmail = u.value("email").toString();
    m_userPlan  = u.value("plan").toString();
    emit profileChanged();
}

void AuthService::login(const QString& email, const QString& password)
{
    QJsonObject body;
    body["email"]    = email;
    body["password"] = password;

    m_api->post("/api/auth/login", body,
        [this](bool ok, const QJsonValue& data, const QString& message) {
            const QJsonObject o = data.toObject();
            if (ok && o.contains("accessToken")) {
                applyTokenResponse(o);
                emit loggedIn(m_token);
            } else {
                emit loginFailed(message.isEmpty() ? QStringLiteral("로그인 실패") : message);
            }
        });
}

void AuthService::tryAutoLogin()
{
    const QString refresh = m_store ? m_store->refreshToken() : QString();
    if (refresh.isEmpty() || !m_store->autoLogin()) {
        emit autoLoginFailed();
        return;
    }

    QJsonObject body;
    body["refreshToken"] = refresh;
    m_api->post("/api/auth/refresh", body,
        [this](bool ok, const QJsonValue& data, const QString&) {
            const QJsonObject o = data.toObject();
            if (ok && o.contains("accessToken")) {
                applyTokenResponse(o);
                emit loggedIn(m_token);
            } else {
                // 만료/폐기된 토큰 — 지우고 로그인 화면으로
                if (m_store) m_store->clearTokens();
                emit autoLoginFailed();
            }
        });
}

void AuthService::logout()
{
    const QString refresh = m_store ? m_store->refreshToken() : QString();
    if (!refresh.isEmpty()) {
        QJsonObject body;
        body["refreshToken"] = refresh;
        m_api->post("/api/auth/logout", body, nullptr); // best-effort
    }
    if (m_store) m_store->clearTokens();
    m_token.clear();
    m_api->setToken(QString());
    m_userName.clear(); m_userEmail.clear(); m_userPlan.clear();
    emit profileChanged();
    emit loggedOut();
}
