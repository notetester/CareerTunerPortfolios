#pragma once

#include <QObject>
#include <QString>

#include "ApiClient.h"

class AdClient : public QObject
{
    Q_OBJECT
    Q_PROPERTY(bool visible READ visible NOTIFY changed)
    Q_PROPERTY(QString title READ title NOTIFY changed)
    Q_PROPERTY(QString body READ body NOTIFY changed)
    Q_PROPERTY(QString targetUrl READ targetUrl NOTIFY changed)

public:
    explicit AdClient(ApiClient* api, QObject* parent = nullptr);

    bool visible() const { return m_visible; }
    QString title() const { return m_title; }
    QString body() const { return m_body; }
    QString targetUrl() const { return m_targetUrl; }

    Q_INVOKABLE void refresh();
    Q_INVOKABLE void clear();
    Q_INVOKABLE void openTarget();

signals:
    void changed();

private:
    void recordEvent(const QString& eventType);
    void applyEmpty();

    ApiClient* m_api;
    qint64 m_campaignId = 0;
    bool m_visible = false;
    QString m_title;
    QString m_body;
    QString m_targetUrl;
};
