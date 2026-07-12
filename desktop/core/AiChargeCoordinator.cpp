#include "AiChargeCoordinator.h"

#include <QJsonObject>
#include <QPointer>
#include <QUuid>

AiChargeCoordinator::AiChargeCoordinator(ApiClient* api, QObject* parent)
    : QObject(parent), m_api(api)
{
}

void AiChargeCoordinator::invalidate()
{
    ++m_generation;
}

void AiChargeCoordinator::run(const QString& featureType, Operation operation, Failure failure)
{
    runWithActionKey(featureType, createActionKey(), std::move(operation), std::move(failure));
}

QString AiChargeCoordinator::createActionKey()
{
    return QStringLiteral("AI_USAGE:")
        + QUuid::createUuid().toString(QUuid::WithoutBraces);
}

void AiChargeCoordinator::runWithActionKey(const QString& featureType, const QString& actionKey,
                                           Operation operation, Failure failure)
{
    if (!m_api || featureType.trimmed().isEmpty() || actionKey.trimmed().isEmpty() || !operation) {
        fail(QStringLiteral("AI 과금 요청을 시작할 수 없습니다."), std::move(failure));
        return;
    }

    const quint64 generation = m_generation;
    const QJsonObject body{
        {QStringLiteral("featureType"), featureType},
        {QStringLiteral("actionKey"), actionKey}
    };
    const QPointer<AiChargeCoordinator> self(this);
    m_api->post(QStringLiteral("/api/billing/charge-preview"), body,
        [self, generation, featureType, actionKey, operation = std::move(operation),
         failure = std::move(failure)](bool ok, const QJsonValue& data,
                                       const QString& message) mutable {
            if (!self || generation != self->m_generation) return;
            if (!ok) {
                self->fail(message.isEmpty()
                    ? QStringLiteral("AI 사용 비용을 확인하지 못했습니다.") : message,
                    std::move(failure));
                return;
            }

            const QJsonObject preview = data.toObject();
            if (preview.value(QStringLiteral("featureType")).toString() != featureType) {
                self->fail(QStringLiteral("AI 과금 기능 확인 결과가 요청과 일치하지 않습니다."),
                           std::move(failure));
                return;
            }
            const QString chargeType = preview.value(QStringLiteral("chargeType")).toString();
            const bool sufficient = preview.value(QStringLiteral("sufficient")).toBool();
            if (chargeType == QStringLiteral("BLOCKED")) {
                self->fail(QStringLiteral("사용 가능한 이용권이 없고 크레딧 대체 차감도 허용되지 않습니다."),
                           std::move(failure));
                return;
            }
            if (!sufficient) {
                self->fail(QStringLiteral("AI 기능을 실행할 크레딧이 부족합니다."),
                           std::move(failure));
                return;
            }
            if (preview.value(QStringLiteral("actionKey")).toString() != actionKey) {
                self->fail(QStringLiteral("AI 과금 확인 키가 요청과 일치하지 않습니다."),
                           std::move(failure));
                return;
            }

            emit self->notice(AiChargeCoordinator::noticeText(preview));
            const ApiClient::Headers headers{
                {QByteArrayLiteral("X-AI-Charge-Acknowledgement"), actionKey.toUtf8()},
                {QByteArrayLiteral("X-AI-Charge-Feature"), featureType.toUtf8()}
            };
            const QString triggerType = preview.value(QStringLiteral("triggerType")).toString();
            if (triggerType.isEmpty()) {
                if (generation != self->m_generation) return;
                operation(headers);
                return;
            }

            const qint64 policyId = preview.value(QStringLiteral("refundPolicyId")).toInteger();
            if (policyId <= 0) {
                self->fail(QStringLiteral("환불정책 확인 정보가 올바르지 않습니다."),
                           std::move(failure));
                return;
            }
            const QJsonObject acknowledgement{
                {QStringLiteral("policyId"), policyId},
                {QStringLiteral("triggerType"), triggerType},
                {QStringLiteral("actionKey"), actionKey}
            };
            self->m_api->post(
                QStringLiteral("/api/billing/refund-policy/acknowledgements"),
                acknowledgement,
                [self, generation, headers, operation = std::move(operation),
                 failure = std::move(failure)](bool acknowledged, const QJsonValue&,
                                                const QString& acknowledgementMessage) mutable {
                    if (!self || generation != self->m_generation) return;
                    if (!acknowledged) {
                        self->fail(acknowledgementMessage.isEmpty()
                            ? QStringLiteral("환불정책 확인을 기록하지 못했습니다.")
                            : acknowledgementMessage, std::move(failure));
                        return;
                    }
                    if (generation == self->m_generation) operation(headers);
                });
        });
}

void AiChargeCoordinator::fail(const QString& message, Failure failure)
{
    emit errorOccurred(message);
    if (failure) failure(message);
}

QString AiChargeCoordinator::noticeText(const QJsonObject& preview)
{
    const QString chargeType = preview.value(QStringLiteral("chargeType")).toString();
    QString charge;
    if (chargeType == QStringLiteral("TICKET")) {
        const int remaining = preview.value(QStringLiteral("remainingTicket")).toInt();
        const int minimum = preview.value(QStringLiteral("minimumCreditCost")).toInt();
        const int maximum = preview.value(QStringLiteral("maximumCreditCost")).toInt();
        charge = maximum > 0
            ? QStringLiteral("이용권 1회가 우선 차감됩니다. 차감 전 잔여 %1회 · 이용권 소진 시 최소 %2크레딧, 실제 사용량에 따라 최대 %3크레딧이 차감됩니다.")
                  .arg(remaining).arg(minimum).arg(maximum)
            : QStringLiteral("이용권 1회가 우선 차감됩니다. 차감 전 잔여 %1회")
                  .arg(remaining);
    } else if (chargeType == QStringLiteral("CREDIT")) {
        const int minimum = preview.value(QStringLiteral("minimumCreditCost")).toInt();
        const int maximum = preview.value(QStringLiteral("maximumCreditCost")).toInt();
        charge = preview.value(QStringLiteral("usageBased")).toBool()
            ? QStringLiteral("최소 %1크레딧이 차감되며 실제 사용량에 따라 최대 %2크레딧까지 사용될 수 있습니다. 현재 보유 %3크레딧")
                  .arg(minimum).arg(maximum)
                  .arg(preview.value(QStringLiteral("currentCredit")).toInt())
            : QStringLiteral("%1크레딧이 차감됩니다. 현재 보유 %2크레딧")
                  .arg(preview.value(QStringLiteral("chargeAmount")).toInt())
                  .arg(preview.value(QStringLiteral("currentCredit")).toInt());
    } else {
        charge = QStringLiteral("무료 이용으로 차감되지 않습니다.");
    }
    const int version = preview.value(QStringLiteral("refundPolicyVersion")).toInt();
    const QString summary = preview.value(QStringLiteral("refundPolicySummary")).toString().trimmed();
    const QString title = preview.value(QStringLiteral("refundPolicyTitle")).toString().trimmed();
    const QString variableChargeNotice = chargeType == QStringLiteral("TICKET")
            || chargeType == QStringLiteral("CREDIT")
        ? QStringLiteral(" · 사용권이 우선 사용되며, 기능별 실제 사용량에 따라 차감되는 크레딧은 달라질 수 있습니다.")
        : QString();
    return QStringLiteral("%1%2 · 환불정책 v%3: %4")
        .arg(charge, variableChargeNotice).arg(version)
        .arg(summary.isEmpty() ? title : summary);
}
