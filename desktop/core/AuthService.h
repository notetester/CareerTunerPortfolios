#pragma once
#include <QObject>
#include <QString>

class ApiClient;
class SettingsStore;
class QJsonObject;

// 인증 흐름.
// - login:        POST /api/auth/login → LoginResponse.token 보관 또는 MFA challenge 전환
// - tryAutoLogin: 보관된 refreshToken 으로 POST /api/auth/refresh → 재로그인 없이 진입
// - 토큰은 SettingsStore(QSettings)에 영속화되어 앱 재시작 후에도 유지된다.
class AuthService : public QObject
{
    Q_OBJECT
    Q_PROPERTY(QString userName  READ userName  NOTIFY profileChanged)
    Q_PROPERTY(QString userEmail READ userEmail NOTIFY profileChanged)
    Q_PROPERTY(QString userPlan  READ userPlan  NOTIFY profileChanged)
    Q_PROPERTY(bool mfaChallengeActive READ mfaChallengeActive NOTIFY mfaChallengeChanged)
    Q_PROPERTY(QString mfaChallengeMethod READ mfaChallengeMethod NOTIFY mfaChallengeChanged)
    Q_PROPERTY(QString mfaStatusText READ mfaStatusText NOTIFY mfaStatusChanged)
public:
    explicit AuthService(ApiClient* api, SettingsStore* store, QObject* parent = nullptr);

    Q_INVOKABLE void login(const QString& email, const QString& password);
    Q_INVOKABLE void verifyMfa(const QString& value, bool useBackupCode);
    Q_INVOKABLE void checkMfaStatus();
    Q_INVOKABLE void cancelMfa();
    Q_INVOKABLE void tryAutoLogin();
    Q_INVOKABLE void logout();

    QString token() const { return m_token; }
    QString userName() const  { return m_userName; }
    QString userEmail() const { return m_userEmail; }
    QString userPlan() const  { return m_userPlan; }
    bool mfaChallengeActive() const { return !m_mfaChallengeToken.isEmpty(); }
    QString mfaChallengeMethod() const { return m_mfaChallengeMethod; }
    QString mfaStatusText() const { return m_mfaStatusText; }

signals:
    void loggedIn(const QString& token);
    void loginFailed(const QString& message);
    void autoLoginFailed();          // 저장 토큰 없음/만료 → 로그인 화면으로
    void aboutToLogout();            // access token 제거 전, 작성 중 원격 자산 정리 기회
    void loggedOut();
    void profileChanged();
    void mfaChallengeChanged();
    void mfaStatusChanged();

private:
    void applyTokenResponse(const QJsonObject& data); // TokenResponse 파싱 + 영속화
    bool completeLoginResponse(const QJsonObject& data);
    void beginMfaChallenge(const QJsonObject& data);
    void clearMfaChallenge();
    void setMfaStatusText(const QString& text);

    ApiClient*     m_api;
    SettingsStore* m_store;
    QString m_token;
    QString m_userName;
    QString m_userEmail;
    QString m_userPlan;
    QString m_mfaChallengeToken;
    QString m_mfaChallengeMethod;
    QString m_mfaStatusText;
};
