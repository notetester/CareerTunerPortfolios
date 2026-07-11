#pragma once

#include "ApiClient.h"

#include <QObject>
#include <QString>

#include <functional>

/**
 * 데스크톱 AI 호출의 과금 사전 계약.
 * preview -> 환불정책 고지/확인 기록 -> 실제 AI 요청 헤더 전달 순서를 한 곳에서 보장한다.
 */
class AiChargeCoordinator : public QObject
{
    Q_OBJECT
public:
    using Operation = std::function<void(const ApiClient::Headers& headers)>;
    using Failure = std::function<void(const QString& message)>;

    explicit AiChargeCoordinator(ApiClient* api, QObject* parent = nullptr);

    void run(const QString& featureType, Operation operation, Failure failure = {});
    void runWithActionKey(const QString& featureType, const QString& actionKey,
                          Operation operation, Failure failure = {});
    static QString createActionKey();
    /** 로그아웃/계정 전환 즉시 진행 중 preview/ack가 실제 유료 작업을 시작하지 못하게 한다. */
    void invalidate();

signals:
    void notice(const QString& message);
    void errorOccurred(const QString& message);

private:
    void fail(const QString& message, Failure failure);
    static QString noticeText(const QJsonObject& preview);

    ApiClient* m_api;
    quint64 m_generation = 0;
};
