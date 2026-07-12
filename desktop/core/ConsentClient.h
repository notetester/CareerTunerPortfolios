#pragma once

#include <QObject>
#include <QString>

class ApiClient;
class DesktopCoreTests;

// 현재 로그인 계정의 동의 상태를 서버 정본(/api/consents/me)에서 읽는다.
// 브라우저 handoff에는 자격증명을 붙이지 않고, 웹에서 다시 로그인한 뒤 동의를 관리한다.
class ConsentClient : public QObject
{
    Q_OBJECT
    Q_PROPERTY(bool loading READ loading NOTIFY changed)
    Q_PROPERTY(bool loaded READ loaded NOTIFY changed)
    Q_PROPERTY(bool requiredConsentsMissing READ requiredConsentsMissing NOTIFY changed)
    Q_PROPERTY(bool termsAgreed READ termsAgreed NOTIFY changed)
    Q_PROPERTY(bool privacyAgreed READ privacyAgreed NOTIFY changed)
    Q_PROPERTY(bool aiDataAgreed READ aiDataAgreed NOTIFY changed)
    Q_PROPERTY(bool resumeAnalysisAgreed READ resumeAnalysisAgreed NOTIFY changed)
    Q_PROPERTY(bool marketingAgreed READ marketingAgreed NOTIFY changed)
    Q_PROPERTY(QString errorText READ errorText NOTIFY changed)
public:
    explicit ConsentClient(ApiClient* api, QObject* parent = nullptr);

    bool loading() const { return m_loading; }
    bool loaded() const { return m_loaded; }
    bool requiredConsentsMissing() const { return m_requiredConsentsMissing; }
    bool termsAgreed() const { return m_termsAgreed; }
    bool privacyAgreed() const { return m_privacyAgreed; }
    bool aiDataAgreed() const { return m_aiDataAgreed; }
    bool resumeAnalysisAgreed() const { return m_resumeAnalysisAgreed; }
    bool marketingAgreed() const { return m_marketingAgreed; }
    QString errorText() const { return m_errorText; }

    Q_INVOKABLE void refresh();
    Q_INVOKABLE void clear();

signals:
    void changed();

private:
    friend class DesktopCoreTests;
    ApiClient* m_api;
    quint64 m_generation = 0;
    bool m_loading = false;
    bool m_loaded = false;
    bool m_requiredConsentsMissing = false;
    bool m_termsAgreed = false;
    bool m_privacyAgreed = false;
    bool m_aiDataAgreed = false;
    bool m_resumeAnalysisAgreed = false;
    bool m_marketingAgreed = false;
    QString m_errorText;
};
