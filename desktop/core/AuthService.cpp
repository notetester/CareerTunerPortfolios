#include "AuthService.h"
#include "ApiClient.h"
#include "SettingsStore.h"
#include <QJsonObject>
#include <QUrl>

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

bool AuthService::completeLoginResponse(const QJsonObject& data)
{
    // /login 과 /mfa/login/verify 는 LoginResponse 로 감싸고,
    // /refresh 와 /mfa/login/status 는 TokenResponse 를 직접 반환한다.
    const QJsonObject nestedToken = data.value(QStringLiteral("token")).toObject();
    const QJsonObject token = nestedToken.isEmpty() ? data : nestedToken;
    if (!token.contains(QStringLiteral("accessToken"))
        || token.value(QStringLiteral("accessToken")).toString().isEmpty()) {
        return false;
    }

    applyTokenResponse(token);
    clearMfaChallenge();
    emit loggedIn(m_token);
    return true;
}

void AuthService::beginMfaChallenge(const QJsonObject& data)
{
    m_mfaChallengeToken = data.value(QStringLiteral("challengeToken")).toString();
    m_mfaChallengeMethod = data.value(QStringLiteral("challengeMethod")).toString();
    setMfaStatusText(m_mfaChallengeMethod.contains(QStringLiteral("PUSH"))
        ? QStringLiteral("인증 앱 코드를 입력하거나 휴대폰에서 승인한 뒤 확인하세요.")
        : QStringLiteral("인증 앱에 표시된 6자리 코드를 입력하세요."));
    emit mfaChallengeChanged();
}

void AuthService::clearMfaChallenge()
{
    const bool changed = !m_mfaChallengeToken.isEmpty() || !m_mfaChallengeMethod.isEmpty();
    m_mfaChallengeToken.clear();
    m_mfaChallengeMethod.clear();
    setMfaStatusText(QString());
    if (changed) emit mfaChallengeChanged();
}

void AuthService::setMfaStatusText(const QString& text)
{
    if (text == m_mfaStatusText) return;
    m_mfaStatusText = text;
    emit mfaStatusChanged();
}

void AuthService::login(const QString& email, const QString& password)
{
    clearMfaChallenge();
    QJsonObject body;
    body["email"]    = email;
    body["password"] = password;

    m_api->post("/api/auth/login", body,
        [this](bool ok, const QJsonValue& data, const QString& message) {
            const QJsonObject o = data.toObject();
            if (!ok) {
                emit loginFailed(message.isEmpty() ? QStringLiteral("로그인 실패") : message);
                return;
            }

            if (o.value(QStringLiteral("mfaRequired")).toBool()) {
                if (o.value(QStringLiteral("challengeToken")).toString().isEmpty()) {
                    emit loginFailed(QStringLiteral("2단계 인증 요청을 시작하지 못했습니다."));
                    return;
                }
                beginMfaChallenge(o);
                return;
            }

            if (!completeLoginResponse(o))
                emit loginFailed(QStringLiteral("로그인 응답에 인증 토큰이 없습니다."));
        });
}

void AuthService::verifyMfa(const QString& value, bool useBackupCode)
{
    if (!mfaChallengeActive()) {
        emit loginFailed(QStringLiteral("2단계 인증 요청이 만료되었습니다. 다시 로그인해 주세요."));
        return;
    }

    const QString normalized = value.trimmed();
    if (normalized.isEmpty()) {
        emit loginFailed(useBackupCode
            ? QStringLiteral("백업 코드를 입력해 주세요.")
            : QStringLiteral("인증 코드를 입력해 주세요."));
        return;
    }

    setMfaStatusText(QStringLiteral("2단계 인증을 확인하고 있습니다…"));
    const QString challengeToken = m_mfaChallengeToken;
    QJsonObject body;
    body[QStringLiteral("challengeToken")] = challengeToken;
    body[useBackupCode ? QStringLiteral("backupCode") : QStringLiteral("code")] = normalized;
    body[QStringLiteral("useApprovedChallenge")] = false;
    m_api->post(QStringLiteral("/api/auth/mfa/login/verify"), body,
        [this, challengeToken](bool ok, const QJsonValue& data, const QString& message) {
            // 사용자가 취소하거나 새 challenge 로 넘어간 뒤 도착한 이전 응답은 무시한다.
            if (challengeToken != m_mfaChallengeToken) return;
            if (ok && completeLoginResponse(data.toObject())) return;
            setMfaStatusText(QStringLiteral("코드를 다시 확인해 주세요."));
            emit loginFailed(message.isEmpty()
                ? QStringLiteral("2단계 인증에 실패했습니다.")
                : message);
        });
}

void AuthService::checkMfaStatus()
{
    if (!mfaChallengeActive()) {
        emit loginFailed(QStringLiteral("2단계 인증 요청이 만료되었습니다. 다시 로그인해 주세요."));
        return;
    }

    setMfaStatusText(QStringLiteral("휴대폰 승인 상태를 확인하고 있습니다…"));
    const QString challengeToken = m_mfaChallengeToken;
    const QString encoded = QString::fromUtf8(QUrl::toPercentEncoding(challengeToken));
    m_api->get(QStringLiteral("/api/auth/mfa/login/status?challengeToken=%1").arg(encoded),
        [this, challengeToken](bool ok, const QJsonValue& data, const QString& message) {
            if (challengeToken != m_mfaChallengeToken) return;
            if (!ok) {
                setMfaStatusText(QStringLiteral("승인 상태를 확인하지 못했습니다."));
                emit loginFailed(message.isEmpty()
                    ? QStringLiteral("휴대폰 승인 상태 확인에 실패했습니다.")
                    : message);
                return;
            }

            const QJsonObject o = data.toObject();
            const QString status = o.value(QStringLiteral("status")).toString();
            if (status == QStringLiteral("VERIFIED")) {
                if (completeLoginResponse(o)) return;
                clearMfaChallenge();
                emit loginFailed(QStringLiteral("승인 응답에 인증 토큰이 없습니다. 다시 로그인해 주세요."));
                return;
            }
            if (status == QStringLiteral("EXPIRED") || status == QStringLiteral("NOT_FOUND")) {
                clearMfaChallenge();
                emit loginFailed(QStringLiteral("2단계 인증 요청이 만료되었습니다. 다시 로그인해 주세요."));
                return;
            }
            if (status == QStringLiteral("DENIED")) {
                clearMfaChallenge();
                emit loginFailed(QStringLiteral("휴대폰에서 로그인 요청을 거절했습니다."));
                return;
            }
            setMfaStatusText(QStringLiteral("아직 승인되지 않았습니다. 휴대폰에서 승인한 뒤 다시 확인하세요."));
        });
}

void AuthService::cancelMfa()
{
    clearMfaChallenge();
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
            if (!ok || !completeLoginResponse(o)) {
                // 만료/폐기된 토큰 — 지우고 로그인 화면으로
                if (m_store) m_store->clearTokens();
                emit autoLoginFailed();
            }
        });
}

void AuthService::logout()
{
    clearMfaChallenge();
    const QString refresh = m_store ? m_store->refreshToken() : QString();
    if (!refresh.isEmpty()) {
        QJsonObject body;
        body["refreshToken"] = refresh;
        m_api->post("/api/auth/logout", body, nullptr); // best-effort
    }
    // direct connection 슬롯이 현재 access token으로 작성 중 첨부 삭제 요청을 시작할 수 있게 한다.
    emit aboutToLogout();
    if (m_store) m_store->clearTokens();
    m_token.clear();
    m_api->setToken(QString());
    m_userName.clear(); m_userEmail.clear(); m_userPlan.clear();
    emit profileChanged();
    emit loggedOut();
}
