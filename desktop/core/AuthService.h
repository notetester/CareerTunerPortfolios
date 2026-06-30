#pragma once
#include <QObject>
#include <QString>

class ApiClient;

// 로그인 흐름. POST /api/auth/login → 응답 data.accessToken 을 보관하고
// ApiClient 에 토큰을 심어 이후 모든 요청에 Bearer 로 첨부되게 한다.
class AuthService : public QObject
{
    Q_OBJECT
public:
    explicit AuthService(ApiClient* api, QObject* parent = nullptr);

    Q_INVOKABLE void login(const QString& email, const QString& password);
    QString token() const { return m_token; }

signals:
    void loggedIn(const QString& token);
    void loginFailed(const QString& message);

private:
    ApiClient* m_api;
    QString    m_token;
};
