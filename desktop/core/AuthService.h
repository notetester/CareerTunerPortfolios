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
    Q_PROPERTY(bool busy READ busy NOTIFY busyChanged)
public:
    explicit AuthService(ApiClient* api, SettingsStore* store, QObject* parent = nullptr);
    ~AuthService() override;

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
    bool busy() const { return m_busy; }

signals:
    void loggedIn(const QString& token);
    void loginFailed(const QString& message);
    void autoLoginFailed();          // 저장 토큰 없음/만료 → 로그인 화면으로
    void aboutToLogout();            // access token 제거 전, 작성 중 원격 자산 정리 기회
    void loggedOut();
    void profileChanged();
    void mfaChallengeChanged();
    void mfaStatusChanged();
    void busyChanged();
    void authenticationExpired(const QString& message);

private:
    void applyTokenResponse(const QJsonObject& data, bool identityChange); // TokenResponse 파싱 + 영속화
    bool completeLoginResponse(const QJsonObject& data);
    void handleAuthenticationExpired(const QString& message);
    void beginMfaChallenge(const QJsonObject& data);
    void clearMfaChallenge();
    void setMfaStatusText(const QString& text);
    quint64 beginAuthenticationRequest();
    bool isCurrentAuthenticationRequest(quint64 generation) const;
    void invalidateAuthenticationRequests();
    void setBusy(bool busy);
    void persistCurrentSessionIfRequested();
    void finishLocalLogout();

    ApiClient*     m_api;
    SettingsStore* m_store;
    QString m_token;
    QString m_refreshToken; // 자동 로그인 설정과 무관하게 현재 실행 중 access token 갱신에 사용
    QString m_userName;
    QString m_userEmail;
    QString m_userPlan;
    QString m_mfaChallengeToken;
    QString m_mfaChallengeMethod;
    QString m_mfaStatusText;
    quint64 m_authRequestGeneration = 0;
    bool m_busy = false;
    bool m_logoutInProgress = false;
};
