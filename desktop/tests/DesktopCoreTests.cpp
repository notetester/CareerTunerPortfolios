#include "ApiClient.h"
#include "PlannerOverlayController.h"
#include "SettingsStore.h"

#include <QEventLoop>
#include <QSignalSpy>
#include <QTemporaryDir>
#include <QTimer>
#include <QtTest>

#include <memory>

class DesktopCoreTests : public QObject
{
    Q_OBJECT

private slots:
    void initTestCase()
    {
        m_settingsDir = std::make_unique<QTemporaryDir>();
        QVERIFY(m_settingsDir->isValid());
        QVERIFY(qputenv("CAREERTUNER_PORTABLE_DATA_DIR", m_settingsDir->path().toUtf8()));
    }

    void safeServerUrlsAreNormalized()
    {
        QCOMPARE(SettingsStore::normalizedBaseUrl(QStringLiteral(" https://Example.COM/ ")),
                 QStringLiteral("https://example.com"));
        QCOMPARE(SettingsStore::normalizedBaseUrl(QStringLiteral("http://localhost:8080/")),
                 QStringLiteral("http://localhost:8080"));
        QCOMPARE(SettingsStore::normalizedBaseUrl(QStringLiteral("http://127.0.0.1:8080")),
                 QStringLiteral("http://127.0.0.1:8080"));
        QVERIFY(!SettingsStore::normalizedBaseUrl(QStringLiteral("http://[::1]:8080")).isEmpty());
    }

    void unsafeServerUrlsAreRejected()
    {
        const QStringList rejected = {
            QStringLiteral("http://example.com"),
            QStringLiteral("example.com"),
            QStringLiteral("ftp://example.com"),
            QStringLiteral("https://user:password@example.com"),
            QStringLiteral("https://example.com/api"),
            QStringLiteral("https://example.com?token=value"),
            QStringLiteral("https://example.com/#fragment")
        };
        for (const QString& url : rejected)
            QVERIFY2(SettingsStore::normalizedBaseUrl(url).isEmpty(), qPrintable(url));
    }

    void changingServerClearsCredentials()
    {
        SettingsStore store;
        store.setTokens(QStringLiteral("access-token"), QStringLiteral("refresh-token"));
        QSignalSpy baseUrlSpy(&store, &SettingsStore::baseUrlChanged);

        QVERIFY(store.applyBaseUrl(QStringLiteral("https://api.example.test/")));
        QCOMPARE(store.baseUrl(), QStringLiteral("https://api.example.test"));
        QVERIFY(store.accessToken().isEmpty());
        QVERIFY(store.refreshToken().isEmpty());
        QCOMPARE(baseUrlSpy.count(), 1);
    }

    void apiClientDropsBearerWhenHostChanges()
    {
        ApiClient api;
        api.setToken(QStringLiteral("old-host-token"));
        api.setBaseUrl(QStringLiteral("https://api.example.test"));

        QVERIFY(api.token().isEmpty());
        QCOMPARE(api.baseUrl(), QStringLiteral("https://api.example.test"));
    }

    void staleReplyCannotReviveOldHostSession()
    {
        ApiClient api;
        api.setBaseUrl(QStringLiteral("http://127.0.0.1:1"));
        bool callbackCalled = false;
        bool callbackOk = true;
        QString callbackMessage;
        QEventLoop loop;

        api.get(QStringLiteral("/stale-request"),
                [&](bool ok, const QJsonValue&, const QString& message) {
                    callbackCalled = true;
                    callbackOk = ok;
                    callbackMessage = message;
                    loop.quit();
                });
        api.setBaseUrl(QStringLiteral("https://api.example.test"));

        if (!callbackCalled) {
            QTimer::singleShot(1000, &loop, &QEventLoop::quit);
            loop.exec();
        }
        QVERIFY(callbackCalled);
        QVERIFY(!callbackOk);
        QCOMPARE(callbackMessage, QStringLiteral("서버가 변경되어 요청이 취소되었습니다."));
        QVERIFY(api.token().isEmpty());
    }

    void unchangedOrRejectedServerKeepsCredentials()
    {
        SettingsStore store;
        QVERIFY(store.applyBaseUrl(QStringLiteral("https://api.example.test")));
        store.setTokens(QStringLiteral("access-token"), QStringLiteral("refresh-token"));

        QVERIFY(store.applyBaseUrl(QStringLiteral("https://api.example.test/")));
        QCOMPARE(store.accessToken(), QStringLiteral("access-token"));
        QCOMPARE(store.refreshToken(), QStringLiteral("refresh-token"));

        QVERIFY(!store.applyBaseUrl(QStringLiteral("http://attacker.example")));
        QCOMPARE(store.baseUrl(), QStringLiteral("https://api.example.test"));
        QCOMPARE(store.accessToken(), QStringLiteral("access-token"));
        QCOMPARE(store.refreshToken(), QStringLiteral("refresh-token"));
    }

    void unsafeStoredServerIsMigratedWithoutCredentials()
    {
        auto rawSettings = SettingsStore::createSettings();
        rawSettings->setValue(QStringLiteral("server/baseUrl"), QStringLiteral("http://attacker.example"));
        rawSettings->setValue(QStringLiteral("auth/accessToken"), QStringLiteral("access-token"));
        rawSettings->setValue(QStringLiteral("auth/refreshToken"), QStringLiteral("refresh-token"));
        rawSettings->sync();
        rawSettings.reset();

        SettingsStore store;
        QCOMPARE(store.baseUrl(), SettingsStore::defaultBaseUrl());
        QVERIFY(store.accessToken().isEmpty());
        QVERIFY(store.refreshToken().isEmpty());
    }

    void plannerOverlayDefaultsAreNonIntrusive()
    {
        PlannerOverlayController controller;
        QVERIFY(!controller.enabled());
        QVERIFY(!controller.alwaysOnTop());
    }

private:
    std::unique_ptr<QTemporaryDir> m_settingsDir;
};

QTEST_GUILESS_MAIN(DesktopCoreTests)

#include "DesktopCoreTests.moc"
