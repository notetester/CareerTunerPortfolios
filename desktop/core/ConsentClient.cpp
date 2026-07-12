#include "ConsentClient.h"
#include "ApiClient.h"

#include <QJsonObject>

ConsentClient::ConsentClient(ApiClient* api, QObject* parent)
    : QObject(parent), m_api(api)
{
}

void ConsentClient::refresh()
{
    if (!m_api || m_loading) return;
    const quint64 generation = ++m_generation;
    m_loading = true;
    m_errorText.clear();
    emit changed();
    m_api->get(QStringLiteral("/api/consents/me"),
        [this, generation](bool ok, const QJsonValue& data, const QString& message) {
            if (generation != m_generation) return;
            m_loading = false;
            if (!ok || !data.isObject()) {
                m_errorText = message.isEmpty()
                    ? QStringLiteral("동의 상태를 불러오지 못했습니다.") : message;
                emit changed();
                return;
            }
            const QJsonObject status = data.toObject();
            m_termsAgreed = status.value(QStringLiteral("termsAgreed")).toBool(false);
            m_privacyAgreed = status.value(QStringLiteral("privacyAgreed")).toBool(false);
            m_aiDataAgreed = status.value(QStringLiteral("aiDataAgreed")).toBool(false);
            m_resumeAnalysisAgreed = status.value(QStringLiteral("resumeAnalysisAgreed")).toBool(false);
            m_marketingAgreed = status.value(QStringLiteral("marketingAgreed")).toBool(false);
            m_requiredConsentsMissing = status.value(QStringLiteral("requiredConsentsMissing"))
                .toBool(!m_termsAgreed || !m_privacyAgreed);
            m_loaded = true;
            m_errorText.clear();
            emit changed();
        });
}

void ConsentClient::clear()
{
    ++m_generation;
    m_loading = false;
    m_loaded = false;
    m_requiredConsentsMissing = false;
    m_termsAgreed = false;
    m_privacyAgreed = false;
    m_aiDataAgreed = false;
    m_resumeAnalysisAgreed = false;
    m_marketingAgreed = false;
    m_errorText.clear();
    emit changed();
}
