#include "ApiClient.h"
#include "AdClient.h"
#include "AiChargeCoordinator.h"
#include "AuthService.h"
#include "AutoPrepRunner.h"
#include "CameraRecorder.h"
#include "CollaborationClient.h"
#include "CommunityClient.h"
#include "ConsentClient.h"
#include "InterviewSession.h"
#include "JobModel.h"
#include "NotificationPoller.h"
#include "PlannerOverlayController.h"
#include "PlannerClient.h"
#include "SettingsStore.h"
#include "VoiceRecorder.h"

#include <QDir>
#include <QDateTime>
#include <QEventLoop>
#include <QFile>
#include <QHostAddress>
#include <QPointer>
#include <QSignalSpy>
#include <QTemporaryDir>
#include <QTcpServer>
#include <QTcpSocket>
#include <QTimer>
#include <QStandardPaths>
#include <QtTest>
#include <QUuid>

#include <memory>

namespace {
bool completeHttpRequest(const QByteArray& request)
{
    const int headerEnd = request.indexOf("\r\n\r\n");
    if (headerEnd < 0) return false;

    int contentLength = 0;
    for (const QByteArray& line : request.left(headerEnd).split('\n')) {
        const QByteArray trimmed = line.trimmed();
        if (trimmed.toLower().startsWith("content-length:")) {
            contentLength = trimmed.mid(trimmed.indexOf(':') + 1).trimmed().toInt();
            break;
        }
    }
    return request.size() >= headerEnd + 4 + contentLength;
}

QByteArray httpBody(const QByteArray& request)
{
    const int headerEnd = request.indexOf("\r\n\r\n");
    return headerEnd < 0 ? QByteArray() : request.mid(headerEnd + 4);
}

void writeHttpResponse(QTcpSocket* socket, int status, const QByteArray& body)
{
    const QByteArray reason = status == 200 ? QByteArrayLiteral("OK")
        : status == 401 ? QByteArrayLiteral("Unauthorized") : QByteArrayLiteral("Error");
    socket->write("HTTP/1.1 " + QByteArray::number(status) + " " + reason
        + "\r\nContent-Type: application/json\r\nContent-Length: "
        + QByteArray::number(body.size()) + "\r\nConnection: close\r\n\r\n" + body);
    socket->flush();
    socket->disconnectFromHost();
}
} // namespace

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

    void staleReplyCannotReachCallbackAfterHostAndAuthGenerationChange()
    {
        ApiClient api;
        api.setBaseUrl(QStringLiteral("http://127.0.0.1:1"));
        bool callbackCalled = false;
        api.get(QStringLiteral("/stale-request"),
                [&](bool, const QJsonValue&, const QString&) {
                    callbackCalled = true;
                });
        api.setBaseUrl(QStringLiteral("https://api.example.test"));
        QTest::qWait(100);

        // setBaseUrl은 server/auth generation을 함께 바꾼다. 이전 서버의 callback은
        // 실패 callback으로도 새 로그인 화면에 도달하면 안 된다.
        QVERIFY(!callbackCalled);
        QVERIFY(api.token().isEmpty());
    }

    void concurrentUnauthorizedRequestsShareRefreshAndReplayExactRequestOnce()
    {
        QTcpServer server;
        QVERIFY(server.listen(QHostAddress::LocalHost, 0));
        int refreshRequests = 0;
        int initialProtectedRequests = 0;
        int replayedProtectedRequests = 0;
        QList<QByteArray> protectedPostBodies;
        QList<QByteArray> protectedPostRequests;

        connect(&server, &QTcpServer::newConnection, this, [&]() {
            while (server.hasPendingConnections()) {
                QTcpSocket* socket = server.nextPendingConnection();
                QVERIFY(socket != nullptr);
                connect(socket, &QTcpSocket::readyRead, socket,
                        [&, socket, request = QByteArray(), handled = false]() mutable {
                    request += socket->readAll();
                    if (handled || !completeHttpRequest(request)) return;
                    handled = true;

                    if (request.startsWith("POST /api/auth/refresh ")) {
                        ++refreshRequests;
                        QCOMPARE(httpBody(request), QByteArray(R"({"refreshToken":"refresh-old"})"));
                        QVERIFY(!request.contains("Authorization:"));
                        QTimer::singleShot(80, socket, [socket]() {
                            writeHttpResponse(socket, 200,
                                R"({"success":true,"data":{"accessToken":"access-new","refreshToken":"refresh-new","user":{"name":"테스트","email":"test@example.com","plan":"FREE"}}})");
                        });
                        return;
                    }

                    const bool isPost = request.startsWith("POST /api/protected-post ");
                    const bool isGet = request.startsWith("GET /api/protected-get ");
                    QVERIFY(isPost || isGet);
                    if (isPost) {
                        protectedPostBodies.push_back(httpBody(request));
                        protectedPostRequests.push_back(request);
                    }

                    if (request.contains("Authorization: Bearer access-old\r\n")) {
                        ++initialProtectedRequests;
                        writeHttpResponse(socket, 401,
                            R"({"success":false,"message":"expired","data":null})");
                        return;
                    }

                    QVERIFY(request.contains("Authorization: Bearer access-new\r\n"));
                    ++replayedProtectedRequests;
                    writeHttpResponse(socket, 200,
                        isPost
                            ? QByteArray(R"({"success":true,"data":{"kind":"post"}})")
                            : QByteArray(R"({"success":true,"data":{"kind":"get"}})"));
                });
            }
        });

        ApiClient api;
        api.setBaseUrl(QStringLiteral("http://127.0.0.1:%1").arg(server.serverPort()));
        api.setToken(QStringLiteral("access-old"));
        QSignalSpy identitySpy(&api, &ApiClient::authenticationIdentityChanged);
        QSignalSpy expiredSpy(&api, &ApiClient::authenticationExpired);
        int tokenResponses = 0;
        QString rotatedRefresh;
        api.configureTokenRefresh(
            []() { return QStringLiteral("refresh-old"); },
            [&](const QJsonObject& data) {
                ++tokenResponses;
                rotatedRefresh = data.value(QStringLiteral("refreshToken")).toString();
            });

        int callbacks = 0;
        QStringList kinds;
        const ApiClient::Headers headers{
            {QByteArrayLiteral("X-Client-Submission-Id"), QByteArrayLiteral("desktop-123")}
        };
        api.post(QStringLiteral("/api/protected-post"),
                 QJsonObject{{QStringLiteral("answerText"), QStringLiteral("동일 본문")}},
                 headers,
                 [&](bool ok, const QJsonValue& data, const QString&) {
                     QVERIFY(ok);
                     ++callbacks;
                     kinds.push_back(data.toObject().value(QStringLiteral("kind")).toString());
                 });
        api.get(QStringLiteral("/api/protected-get"),
                [&](bool ok, const QJsonValue& data, const QString&) {
                    QVERIFY(ok);
                    ++callbacks;
                    kinds.push_back(data.toObject().value(QStringLiteral("kind")).toString());
                });

        QTRY_COMPARE_WITH_TIMEOUT(callbacks, 2, 4000);
        QCOMPARE(refreshRequests, 1);
        QCOMPARE(initialProtectedRequests, 2);
        QCOMPARE(replayedProtectedRequests, 2);
        QCOMPARE(tokenResponses, 1);
        QCOMPARE(rotatedRefresh, QStringLiteral("refresh-new"));
        QCOMPARE(api.token(), QStringLiteral("access-new"));
        QCOMPARE(identitySpy.count(), 0); // 동일 사용자의 token rotation은 모델을 비우지 않는다.
        QCOMPARE(expiredSpy.count(), 0);
        QVERIFY(kinds.contains(QStringLiteral("post")));
        QVERIFY(kinds.contains(QStringLiteral("get")));
        QCOMPARE(protectedPostBodies.size(), 2);
        QCOMPARE(protectedPostBodies.at(0), protectedPostBodies.at(1));
        QCOMPARE(protectedPostRequests.size(), 2);
        for (const QByteArray& request : protectedPostRequests)
            QVERIFY(request.contains("X-Client-Submission-Id: desktop-123\r\n"));
    }

    void authServiceRotatesInMemoryRefreshTokenWhenAutoLoginIsDisabled()
    {
        QTcpServer server;
        QVERIFY(server.listen(QHostAddress::LocalHost, 0));
        int refreshRequests = 0;
        int protectedRequests = 0;

        connect(&server, &QTcpServer::newConnection, this, [&]() {
            while (server.hasPendingConnections()) {
                QTcpSocket* socket = server.nextPendingConnection();
                QVERIFY(socket != nullptr);
                connect(socket, &QTcpSocket::readyRead, socket,
                        [&, socket, request = QByteArray(), handled = false]() mutable {
                    request += socket->readAll();
                    if (handled || !completeHttpRequest(request)) return;
                    handled = true;
                    if (request.startsWith("POST /api/auth/login ")) {
                        writeHttpResponse(socket, 200,
                            R"({"success":true,"data":{"mfaRequired":false,"token":{"accessToken":"login-access","refreshToken":"login-refresh","user":{"name":"로그인 사용자","email":"login@example.com","plan":"FREE"}}}})");
                    } else if (request.startsWith("POST /api/auth/refresh ")) {
                        ++refreshRequests;
                        QCOMPARE(httpBody(request), QByteArray(R"({"refreshToken":"login-refresh"})"));
                        writeHttpResponse(socket, 200,
                            R"({"success":true,"data":{"accessToken":"rotated-access","refreshToken":"rotated-refresh","user":{"name":"갱신 사용자","email":"login@example.com","plan":"PRO"}}})");
                    } else {
                        ++protectedRequests;
                        if (request.contains("Authorization: Bearer login-access\r\n")) {
                            writeHttpResponse(socket, 401,
                                R"({"success":false,"message":"expired","data":null})");
                        } else {
                            QVERIFY(request.contains("Authorization: Bearer rotated-access\r\n"));
                            writeHttpResponse(socket, 200,
                                R"({"success":true,"data":{"ok":true}})");
                        }
                    }
                });
            }
        });

        SettingsStore store;
        store.clearTokens();
        store.setAutoLogin(false);
        ApiClient api;
        api.setBaseUrl(QStringLiteral("http://127.0.0.1:%1").arg(server.serverPort()));
        AuthService auth(&api, &store);
        QSignalSpy loggedInSpy(&auth, &AuthService::loggedIn);
        QSignalSpy identitySpy(&api, &ApiClient::authenticationIdentityChanged);

        auth.login(QStringLiteral("login@example.com"), QStringLiteral("password"));
        QTRY_COMPARE_WITH_TIMEOUT(loggedInSpy.count(), 1, 3000);
        QCOMPARE(auth.token(), QStringLiteral("login-access"));
        QCOMPARE(api.token(), QStringLiteral("login-access"));
        QVERIFY(store.accessToken().isEmpty());
        QVERIFY(store.refreshToken().isEmpty());

        bool protectedOk = false;
        api.get(QStringLiteral("/api/protected"),
                [&](bool ok, const QJsonValue&, const QString&) { protectedOk = ok; });
        QTRY_VERIFY_WITH_TIMEOUT(protectedOk, 3000);
        QCOMPARE(refreshRequests, 1);
        QCOMPARE(protectedRequests, 2);
        QCOMPARE(api.token(), QStringLiteral("rotated-access"));
        QCOMPARE(auth.token(), QStringLiteral("rotated-access"));
        QCOMPARE(auth.userName(), QStringLiteral("갱신 사용자"));
        QCOMPARE(auth.userPlan(), QStringLiteral("PRO"));
        QCOMPARE(identitySpy.count(), 1); // 최초 로그인만 주체 변경, refresh 회전은 모델 유지
        QVERIFY(store.accessToken().isEmpty());
        QVERIFY(store.refreshToken().isEmpty());
        store.setAutoLogin(true);
        QCOMPARE(store.accessToken(), QStringLiteral("rotated-access"));
        QCOMPARE(store.refreshToken(), QStringLiteral("rotated-refresh"));
    }

    void latestLoginRequestWinsWhenResponsesArriveOutOfOrder()
    {
        QTcpServer server;
        QVERIFY(server.listen(QHostAddress::LocalHost, 0));
        int loginRequests = 0;

        connect(&server, &QTcpServer::newConnection, this, [&]() {
            while (server.hasPendingConnections()) {
                QTcpSocket* socket = server.nextPendingConnection();
                QVERIFY(socket != nullptr);
                connect(socket, &QTcpSocket::readyRead, socket,
                        [&, socket, request = QByteArray(), handled = false]() mutable {
                    request += socket->readAll();
                    if (handled || !completeHttpRequest(request)) return;
                    handled = true;
                    ++loginRequests;
                    const bool first = httpBody(request).contains("first@example.com");
                    const QByteArray body = first
                        ? QByteArray(R"({"success":true,"data":{"mfaRequired":false,"token":{"accessToken":"first-access","refreshToken":"first-refresh","user":{"name":"첫 계정","email":"first@example.com","plan":"FREE"}}}})")
                        : QByteArray(R"({"success":true,"data":{"mfaRequired":false,"token":{"accessToken":"second-access","refreshToken":"second-refresh","user":{"name":"둘째 계정","email":"second@example.com","plan":"PRO"}}}})");
                    QTimer::singleShot(first ? 160 : 10, socket,
                        [socket, body]() { writeHttpResponse(socket, 200, body); });
                });
            }
        });

        SettingsStore store;
        store.clearTokens();
        ApiClient api;
        api.setBaseUrl(QStringLiteral("http://127.0.0.1:%1").arg(server.serverPort()));
        AuthService auth(&api, &store);
        QSignalSpy loggedInSpy(&auth, &AuthService::loggedIn);

        auth.login(QStringLiteral("first@example.com"), QStringLiteral("pw1"));
        QVERIFY(auth.busy());
        auth.login(QStringLiteral("second@example.com"), QStringLiteral("pw2"));
        QVERIFY(auth.busy());

        QTRY_COMPARE_WITH_TIMEOUT(loggedInSpy.count(), 1, 3000);
        QTest::qWait(220);
        QCOMPARE(loginRequests, 2);
        QCOMPARE(loggedInSpy.count(), 1);
        QCOMPARE(auth.userEmail(), QStringLiteral("second@example.com"));
        QCOMPARE(auth.token(), QStringLiteral("second-access"));
        QCOMPARE(api.token(), QStringLiteral("second-access"));
        QVERIFY(!auth.busy());

        QFile qml(QStringLiteral(CAREERTUNER_DESKTOP_SOURCE_DIR "/qml/LoginPage.qml"));
        QVERIFY(qml.open(QIODevice::ReadOnly));
        const QByteArray source = qml.readAll();
        QVERIFY(source.contains("if (auth.busy) return"));
        QVERIFY(source.contains("enabled: !auth.busy"));
    }

    void logoutRevocationCompletesUnauthenticatedOnCapturedOriginBeforeLocalClear()
    {
        QTcpServer oldServer;
        QTcpServer newServer;
        QVERIFY(oldServer.listen(QHostAddress::LocalHost, 0));
        QVERIFY(newServer.listen(QHostAddress::LocalHost, 0));
        int loginRequests = 0;
        int logoutRequests = 0;
        int newServerConnections = 0;

        connect(&newServer, &QTcpServer::newConnection, this, [&]() {
            ++newServerConnections;
            while (newServer.hasPendingConnections())
                newServer.nextPendingConnection()->deleteLater();
        });
        connect(&oldServer, &QTcpServer::newConnection, this, [&]() {
            while (oldServer.hasPendingConnections()) {
                QTcpSocket* socket = oldServer.nextPendingConnection();
                QVERIFY(socket != nullptr);
                connect(socket, &QTcpSocket::readyRead, socket,
                        [&, socket, request = QByteArray(), handled = false]() mutable {
                    request += socket->readAll();
                    if (handled || !completeHttpRequest(request)) return;
                    handled = true;
                    if (request.startsWith("POST /api/auth/login ")) {
                        ++loginRequests;
                        writeHttpResponse(socket, 200,
                            R"({"success":true,"data":{"mfaRequired":false,"token":{"accessToken":"expired-access","refreshToken":"logout-refresh","user":{"name":"로그아웃","email":"logout@example.com","plan":"FREE"}}}})");
                    } else if (request.startsWith("POST /api/auth/logout ")) {
                        ++logoutRequests;
                        QVERIFY(!request.contains("Authorization:"));
                        QCOMPARE(httpBody(request), QByteArray(R"({"refreshToken":"logout-refresh"})"));
                        // 로컬 자격증명이 서버 revoke 완료 전까지 유지되는지 관찰할 여유를 둔다.
                        QTimer::singleShot(120, socket, [socket]() {
                            writeHttpResponse(socket, 200,
                                R"({"success":true,"message":"","data":null})");
                        });
                    } else {
                        writeHttpResponse(socket, 500,
                            R"({"success":false,"message":"unexpected","data":null})");
                    }
                });
            }
        });

        SettingsStore store;
        store.clearTokens();
        ApiClient api;
        api.setBaseUrl(QStringLiteral("http://127.0.0.1:%1").arg(oldServer.serverPort()));
        AuthService auth(&api, &store);
        QSignalSpy loggedInSpy(&auth, &AuthService::loggedIn);
        QSignalSpy loggedOutSpy(&auth, &AuthService::loggedOut);
        auth.login(QStringLiteral("logout@example.com"), QStringLiteral("pw"));
        QTRY_COMPARE_WITH_TIMEOUT(loggedInSpy.count(), 1, 3000);

        auth.logout();
        api.setBaseUrl(QStringLiteral("http://127.0.0.1:%1").arg(newServer.serverPort()));
        QTRY_COMPARE_WITH_TIMEOUT(logoutRequests, 1, 2000);
        QCOMPARE(loggedOutSpy.count(), 0);
        QCOMPARE(store.refreshToken(), QStringLiteral("logout-refresh"));
        QTRY_COMPARE_WITH_TIMEOUT(loggedOutSpy.count(), 1, 3000);
        QCOMPARE(newServerConnections, 0);
        QVERIFY(api.token().isEmpty());
        QVERIFY(store.refreshToken().isEmpty());
    }

    void logoutRevokesTokenRotatedByAlreadyInFlightRefresh()
    {
        QTcpServer oldServer;
        QTcpServer newServer;
        QVERIFY(oldServer.listen(QHostAddress::LocalHost, 0));
        QVERIFY(newServer.listen(QHostAddress::LocalHost, 0));
        int refreshRequests = 0;
        int logoutRequests = 0;
        int newServerConnections = 0;
        QPointer<QTcpSocket> heldRefreshSocket;

        connect(&newServer, &QTcpServer::newConnection, this, [&]() {
            ++newServerConnections;
            while (newServer.hasPendingConnections())
                newServer.nextPendingConnection()->deleteLater();
        });
        connect(&oldServer, &QTcpServer::newConnection, this, [&]() {
            while (oldServer.hasPendingConnections()) {
                QTcpSocket* socket = oldServer.nextPendingConnection();
                QVERIFY(socket != nullptr);
                connect(socket, &QTcpSocket::readyRead, socket,
                        [&, socket, request = QByteArray(), handled = false]() mutable {
                    request += socket->readAll();
                    if (handled || !completeHttpRequest(request)) return;
                    handled = true;
                    if (request.startsWith("POST /api/auth/login ")) {
                        writeHttpResponse(socket, 200,
                            R"({"success":true,"data":{"mfaRequired":false,"token":{"accessToken":"old-access","refreshToken":"old-refresh","user":{"name":"동시성","email":"race@example.com","plan":"FREE"}}}})");
                    } else if (request.startsWith("GET /api/protected ")) {
                        writeHttpResponse(socket, 401,
                            R"({"success":false,"message":"expired","data":null})");
                    } else if (request.startsWith("POST /api/auth/refresh ")) {
                        ++refreshRequests;
                        heldRefreshSocket = socket;
                    } else if (request.startsWith("POST /api/auth/logout ")) {
                        ++logoutRequests;
                        QVERIFY(!request.contains("Authorization:"));
                        QCOMPARE(httpBody(request), QByteArray(R"({"refreshToken":"rotated-refresh"})"));
                        writeHttpResponse(socket, 200,
                            R"({"success":true,"data":null})");
                    } else {
                        writeHttpResponse(socket, 500,
                            R"({"success":false,"message":"unexpected","data":null})");
                    }
                });
            }
        });

        SettingsStore store;
        store.clearTokens();
        ApiClient api;
        api.setBaseUrl(QStringLiteral("http://127.0.0.1:%1").arg(oldServer.serverPort()));
        AuthService auth(&api, &store);
        QSignalSpy loggedInSpy(&auth, &AuthService::loggedIn);
        QSignalSpy loggedOutSpy(&auth, &AuthService::loggedOut);
        auth.login(QStringLiteral("race@example.com"), QStringLiteral("pw"));
        QTRY_COMPARE_WITH_TIMEOUT(loggedInSpy.count(), 1, 3000);

        bool protectedCallbackCalled = false;
        api.get(QStringLiteral("/api/protected"),
                [&](bool, const QJsonValue&, const QString&) { protectedCallbackCalled = true; });
        QTRY_COMPARE_WITH_TIMEOUT(refreshRequests, 1, 3000);
        QTRY_VERIFY_WITH_TIMEOUT(!heldRefreshSocket.isNull(), 3000);

        auth.logout();
        api.setBaseUrl(QStringLiteral("http://127.0.0.1:%1").arg(newServer.serverPort()));
        QCOMPARE(loggedOutSpy.count(), 0);
        writeHttpResponse(heldRefreshSocket, 200,
            R"({"success":true,"data":{"accessToken":"rotated-access","refreshToken":"rotated-refresh","user":{"name":"동시성","email":"race@example.com","plan":"FREE"}}})");

        QTRY_COMPARE_WITH_TIMEOUT(logoutRequests, 1, 3000);
        QTRY_COMPARE_WITH_TIMEOUT(loggedOutSpy.count(), 1, 3000);
        QVERIFY(!protectedCallbackCalled);
        QCOMPARE(newServerConnections, 0);
        QVERIFY(store.refreshToken().isEmpty());
        QVERIFY(api.token().isEmpty());
    }

    void failedRefreshExpiresAuthenticationWithoutReplayingRequest()
    {
        QTcpServer server;
        QVERIFY(server.listen(QHostAddress::LocalHost, 0));
        int protectedRequests = 0;
        int refreshRequests = 0;

        connect(&server, &QTcpServer::newConnection, this, [&]() {
            while (server.hasPendingConnections()) {
                QTcpSocket* socket = server.nextPendingConnection();
                QVERIFY(socket != nullptr);
                connect(socket, &QTcpSocket::readyRead, socket,
                        [&, socket, request = QByteArray(), handled = false]() mutable {
                    request += socket->readAll();
                    if (handled || !completeHttpRequest(request)) return;
                    handled = true;
                    if (request.startsWith("POST /api/auth/refresh ")) {
                        ++refreshRequests;
                        writeHttpResponse(socket, 401,
                            R"({"success":false,"message":"refresh rejected","data":null})");
                    } else {
                        ++protectedRequests;
                        writeHttpResponse(socket, 401,
                            R"({"success":false,"message":"expired","data":null})");
                    }
                });
            }
        });

        ApiClient api;
        api.setBaseUrl(QStringLiteral("http://127.0.0.1:%1").arg(server.serverPort()));
        api.setToken(QStringLiteral("access-old"));
        api.configureTokenRefresh([]() { return QStringLiteral("refresh-old"); }, {});
        QSignalSpy identitySpy(&api, &ApiClient::authenticationIdentityChanged);
        QSignalSpy expiredSpy(&api, &ApiClient::authenticationExpired);
        bool featureCallbackCalled = false;

        api.get(QStringLiteral("/api/protected"),
                [&](bool, const QJsonValue&, const QString&) { featureCallbackCalled = true; });

        QTRY_COMPARE_WITH_TIMEOUT(expiredSpy.count(), 1, 3000);
        QTest::qWait(50);
        QCOMPARE(protectedRequests, 1);
        QCOMPARE(refreshRequests, 1);
        QCOMPARE(identitySpy.count(), 1);
        QVERIFY(!featureCallbackCalled);
        QVERIFY(api.token().isEmpty());
        QCOMPARE(expiredSpy.at(0).at(0).toString(), QStringLiteral("refresh rejected"));
    }

    void tokenIdentityChangeDropsStaleCallbackAndClearsAccountModels()
    {
        QTcpServer server;
        QVERIFY(server.listen(QHostAddress::LocalHost, 0));
        bool slowRequestReceived = false;

        connect(&server, &QTcpServer::newConnection, this, [&]() {
            while (server.hasPendingConnections()) {
                QTcpSocket* socket = server.nextPendingConnection();
                QVERIFY(socket != nullptr);
                connect(socket, &QTcpSocket::readyRead, socket,
                        [&, socket, request = QByteArray(), handled = false]() mutable {
                    request += socket->readAll();
                    if (handled || !completeHttpRequest(request)) return;
                    handled = true;
                    if (request.startsWith("GET /api/application-cases?")) {
                        writeHttpResponse(socket, 200,
                            R"({"success":true,"data":[{"id":7,"companyName":"Old","jobTitle":"Account"}]})");
                    } else if (request.startsWith("GET /api/interview/sessions?")) {
                        writeHttpResponse(socket, 200,
                            R"({"success":true,"data":{"sessions":[{"id":77,"applicationCaseId":7,"mode":"BASIC","totalQuestions":2,"answeredQuestions":1,"finished":false}]}})");
                    } else {
                        slowRequestReceived = true;
                        QTimer::singleShot(120, socket, [socket]() {
                            writeHttpResponse(socket, 200,
                                R"({"success":true,"data":{"secret":"old-account"}})");
                        });
                    }
                });
            }
        });

        ApiClient api;
        api.setBaseUrl(QStringLiteral("http://127.0.0.1:%1").arg(server.serverPort()));
        api.setToken(QStringLiteral("old-account-token"));
        JobModel jobs;
        jobs.setApi(&api);
        jobs.reload();
        QTRY_COMPARE_WITH_TIMEOUT(jobs.rowCount(), 1, 3000);
        QVERIFY(!jobs.current().isEmpty());

        SettingsStore store;
        InterviewSession session(&api, &store);
        session.m_sessionId = 77;
        session.m_title = QStringLiteral("Old account session");
        session.m_thread = {QVariantMap{{QStringLiteral("kind"), QStringLiteral("question")}}};
        session.m_progress = QVariantMap{{QStringLiteral("answered"), 1}};
        session.m_report = QVariantMap{{QStringLiteral("totalScore"), 80}};

        connect(&api, &ApiClient::authenticationIdentityChanged, &jobs, &JobModel::clear);
        connect(&api, &ApiClient::authenticationIdentityChanged, &session, &InterviewSession::clear);
        bool staleCallbackCalled = false;
        api.get(QStringLiteral("/api/slow-old-account"),
                [&](bool, const QJsonValue&, const QString&) { staleCallbackCalled = true; });
        QTRY_VERIFY_WITH_TIMEOUT(slowRequestReceived, 1000);

        api.setToken(QStringLiteral("new-account-token"));
        QCOMPARE(jobs.rowCount(), 0);
        QVERIFY(jobs.current().isEmpty());
        QCOMPARE(session.sessionId(), -1);
        QVERIFY(session.thread().isEmpty());
        QVERIFY(session.progress().isEmpty());
        QVERIFY(session.report().isEmpty());

        QTest::qWait(200);
        QVERIFY(!staleCallbackCalled);
    }

    void logoutHandlerCancelsActiveSessionRecording()
    {
        QFile file(QStringLiteral(CAREERTUNER_DESKTOP_SOURCE_DIR "/qml/Main.qml"));
        QVERIFY(file.open(QIODevice::ReadOnly));
        const QByteArray qml = file.readAll();
        const qsizetype handler = qml.indexOf("function onAboutToLogout()");
        QVERIFY(handler >= 0);
        const qsizetype handlerEnd = qml.indexOf('}', handler);
        QVERIFY(handlerEnd > handler);
        const qsizetype cancel = qml.indexOf("sessionInputBar.cancelSessionMedia()", handler);
        QVERIFY(cancel > handler);
        QVERIFY(cancel < handlerEnd);
        const qsizetype closeDialog = qml.indexOf("newJobDialog.close()", handler);
        const qsizetype resetDialog = qml.indexOf("newJobDialog.resetWizard()", handler);
        QVERIFY(closeDialog > handler && closeDialog < handlerEnd);
        QVERIFY(resetDialog > closeDialog && resetDialog < handlerEnd);

        QFile dialog(QStringLiteral(CAREERTUNER_DESKTOP_SOURCE_DIR "/qml/NewJobDialog.qml"));
        QVERIFY(dialog.open(QIODevice::ReadOnly));
        const QByteArray dialogQml = dialog.readAll();
        const qsizetype resetFunction = dialogQml.indexOf("function resetWizard()");
        QVERIFY(resetFunction >= 0);
        QVERIFY(dialogQml.indexOf("caseList = []", resetFunction) > resetFunction);
        QVERIFY(dialogQml.indexOf("chosenCaseId = -1", resetFunction) > resetFunction);
    }

    void interviewPersistenceRequestsSidebarStatusRefresh()
    {
        QTcpServer server;
        QVERIFY(server.listen(QHostAddress::LocalHost, 0));

        connect(&server, &QTcpServer::newConnection, this, [&]() {
            while (server.hasPendingConnections()) {
                QTcpSocket* socket = server.nextPendingConnection();
                QVERIFY(socket != nullptr);
                connect(socket, &QTcpSocket::readyRead, socket,
                        [socket, request = QByteArray(), handled = false]() mutable {
                    request += socket->readAll();
                    if (handled || !completeHttpRequest(request)) return;
                    handled = true;
                    if (request.startsWith("POST /api/interview/questions/11/answers ")) {
                        writeHttpResponse(socket, 200,
                            R"({"success":true,"data":{"id":501,"score":84,"feedback":"good"}})");
                    } else if (request.startsWith("GET /api/interview/sessions/7/progress ")) {
                        writeHttpResponse(socket, 200,
                            R"({"success":true,"data":{"totalQuestions":2,"answeredQuestions":1,"finished":false}})");
                    } else if (request.startsWith("GET /api/interview/sessions/7/report ")) {
                        writeHttpResponse(socket, 200,
                            R"({"success":true,"data":{"totalScore":84}})");
                    } else {
                        writeHttpResponse(socket, 500,
                            R"({"success":false,"message":"unexpected request"})");
                    }
                });
            }
        });

        ApiClient api;
        api.setBaseUrl(QStringLiteral("http://127.0.0.1:%1").arg(server.serverPort()));
        SettingsStore store;
        InterviewSession session(&api, &store);
        session.m_sessionId = 7;
        session.m_currentQid = 11;
        session.m_currentQText = QStringLiteral("첫 질문");
        session.m_scoring = true;
        session.m_thread = {
            QVariantMap{{QStringLiteral("kind"), QStringLiteral("question")},
                        {QStringLiteral("qid"), 11LL}, {QStringLiteral("text"), QStringLiteral("첫 질문")}},
            QVariantMap{{QStringLiteral("kind"), QStringLiteral("answer")},
                        {QStringLiteral("qid"), 11LL}, {QStringLiteral("pending"), true}},
            QVariantMap{{QStringLiteral("kind"), QStringLiteral("scoring")},
                        {QStringLiteral("qid"), 11LL}},
            QVariantMap{{QStringLiteral("kind"), QStringLiteral("question")},
                        {QStringLiteral("qid"), 12LL}, {QStringLiteral("text"), QStringLiteral("다음 질문")}}
        };
        QSignalSpy refreshSpy(&session, &InterviewSession::sidebarRefreshRequested);

        session.submitStoredAnswer(11, 7, QStringLiteral("답변"), {}, {}, {}, 0, false);
        QTRY_COMPARE_WITH_TIMEOUT(refreshSpy.count(), 1, 3000);
        session.loadReport();
        QTRY_COMPARE_WITH_TIMEOUT(refreshSpy.count(), 2, 3000);
        QCOMPARE(session.report().value(QStringLiteral("totalScore")).toInt(), 84);
    }

    void answerRetryReusesUuidAfterAmbiguousFailureAndRotatesAfterExplicitClientError()
    {
        QTcpServer server;
        QVERIFY(server.listen(QHostAddress::LocalHost, 0));
        QStringList submissionIds;
        int answerRequests = 0;

        connect(&server, &QTcpServer::newConnection, this, [&]() {
            while (server.hasPendingConnections()) {
                QTcpSocket* socket = server.nextPendingConnection();
                QVERIFY(socket != nullptr);
                connect(socket, &QTcpSocket::readyRead, socket,
                        [&, socket, request = QByteArray(), handled = false]() mutable {
                    request += socket->readAll();
                    if (handled || !completeHttpRequest(request)) return;
                    handled = true;
                    if (request.startsWith("POST /api/interview/questions/11/answers ")) {
                        ++answerRequests;
                        const QJsonObject submitted = QJsonDocument::fromJson(httpBody(request)).object();
                        submissionIds.push_back(
                            submitted.value(QStringLiteral("clientSubmissionId")).toString());
                        QCOMPARE(submitted.value(QStringLiteral("answerText")).toString(),
                                 QStringLiteral("같은 답변"));
                        if (answerRequests == 1) {
                            writeHttpResponse(socket, 500,
                                R"({"success":false,"message":"response lost","data":null})");
                        } else if (answerRequests == 2) {
                            writeHttpResponse(socket, 400,
                                R"({"success":false,"message":"invalid request","data":null})");
                        } else {
                            const QByteArray body = QStringLiteral(
                                R"({"success":true,"data":{"id":501,"questionId":11,"clientSubmissionId":"%1","submissionStatus":"COMPLETED","answerText":"같은 답변","audioUrl":null,"videoUrl":null,"score":84,"feedback":"good","improvedAnswer":"better"}})")
                                .arg(submissionIds.last()).toUtf8();
                            writeHttpResponse(socket, 200, body);
                        }
                    } else if (request.startsWith("GET /api/interview/sessions/7/progress ")) {
                        writeHttpResponse(socket, 200,
                            R"({"success":true,"data":{"totalQuestions":2,"answeredQuestions":1,"finished":false}})");
                    } else {
                        writeHttpResponse(socket, 500,
                            R"({"success":false,"message":"unexpected request","data":null})");
                    }
                });
            }
        });

        ApiClient api;
        api.setBaseUrl(QStringLiteral("http://127.0.0.1:%1").arg(server.serverPort()));
        SettingsStore store;
        InterviewSession session(&api, &store);
        session.m_sessionId = 7;
        session.m_currentQid = 11;
        session.m_currentQText = QStringLiteral("첫 질문");
        session.m_thread = {
            QVariantMap{{QStringLiteral("kind"), QStringLiteral("question")},
                        {QStringLiteral("qid"), 11LL}, {QStringLiteral("text"), QStringLiteral("첫 질문")}},
            QVariantMap{{QStringLiteral("kind"), QStringLiteral("question")},
                        {QStringLiteral("qid"), 12LL}, {QStringLiteral("text"), QStringLiteral("다음 질문")}}
        };
        QSignalSpy errorSpy(&session, &InterviewSession::errorOccurred);

        session.submitAnswer(QStringLiteral("같은 답변"));
        QTRY_COMPARE_WITH_TIMEOUT(answerRequests, 1, 3000);
        QTRY_COMPARE_WITH_TIMEOUT(errorSpy.count(), 1, 3000);
        QVERIFY(!session.m_pendingClientSubmissionId.isEmpty());

        session.submitAnswer(QStringLiteral("같은 답변"));
        QTRY_COMPARE_WITH_TIMEOUT(answerRequests, 2, 3000);
        QTRY_COMPARE_WITH_TIMEOUT(errorSpy.count(), 2, 3000);
        QVERIFY(session.m_pendingClientSubmissionId.isEmpty());

        session.submitAnswer(QStringLiteral("같은 답변"));
        QTRY_COMPARE_WITH_TIMEOUT(answerRequests, 3, 3000);
        QTRY_VERIFY_WITH_TIMEOUT(!session.scoring(), 3000);

        QCOMPARE(submissionIds.size(), 3);
        QVERIFY(!QUuid(submissionIds.at(0)).isNull());
        QCOMPARE(submissionIds.at(0), submissionIds.at(1));
        QVERIFY(submissionIds.at(2) != submissionIds.at(1));
        QVERIFY(session.m_pendingClientSubmissionId.isEmpty());
    }

    void answerSubmissionPersistsAvailableScoresAndLinksLateMediaResults()
    {
        QTcpServer server;
        QVERIFY(server.listen(QHostAddress::LocalHost, 0));
        QList<QJsonObject> answerBodies;
        QList<QJsonObject> mediaBodies;

        connect(&server, &QTcpServer::newConnection, this, [&]() {
            while (server.hasPendingConnections()) {
                QTcpSocket* socket = server.nextPendingConnection();
                QVERIFY(socket != nullptr);
                connect(socket, &QTcpSocket::readyRead, socket,
                        [&, socket, request = QByteArray(), handled = false]() mutable {
                    request += socket->readAll();
                    if (handled || !completeHttpRequest(request)) return;
                    handled = true;
                    if (request.startsWith("POST /api/interview/questions/11/answers ")) {
                        answerBodies.push_back(QJsonDocument::fromJson(httpBody(request)).object());
                        writeHttpResponse(socket, 200,
                            R"({"success":true,"data":{"id":501,"questionId":11,"answerText":"음성 답변","score":82,"feedback":"good","improvedAnswer":"better","audioUrl":null,"videoUrl":null}})");
                    } else if (request.startsWith("POST /api/interview/questions/12/answers ")) {
                        answerBodies.push_back(QJsonDocument::fromJson(httpBody(request)).object());
                        writeHttpResponse(socket, 200,
                            R"({"success":true,"data":{"id":502,"questionId":12,"answerText":"영상 답변","score":88,"feedback":"good","improvedAnswer":"better","audioUrl":null,"videoUrl":"/api/file/12/content"}})");
                    } else if (request.startsWith(
                                   "POST /api/interview/sessions/7/media-results ")) {
                        mediaBodies.push_back(QJsonDocument::fromJson(httpBody(request)).object());
                        writeHttpResponse(socket, 200,
                            R"({"success":true,"data":{"id":901}})");
                    } else if (request.startsWith(
                                   "GET /api/interview/sessions/7/progress ")) {
                        writeHttpResponse(socket, 200,
                            R"({"success":true,"data":{"totalQuestions":3,"answeredQuestions":1,"finished":false}})");
                    } else {
                        writeHttpResponse(socket, 500,
                            R"({"success":false,"message":"unexpected","data":null})");
                    }
                });
            }
        });

        ApiClient api;
        api.setBaseUrl(QStringLiteral("http://127.0.0.1:%1").arg(server.serverPort()));
        SettingsStore store;
        InterviewSession session(&api, &store);
        session.m_sessionId = 7;
        session.m_currentQid = 11;
        session.m_scoring = true;
        session.m_voiceScoreByQuestion.insert(11, 73);
        session.m_thread = {
            QVariantMap{{"kind", "answer"}, {"qid", 11LL}, {"pending", true}},
            QVariantMap{{"kind", "scoring"}, {"qid", 11LL}},
            QVariantMap{{"kind", "question"}, {"qid", 99LL}, {"text", "다음 질문"}}
        };

        session.submitStoredAnswer(11, 7, QStringLiteral("음성 답변"), {}, {}, {}, 0, false);
        QTRY_COMPARE_WITH_TIMEOUT(answerBodies.size(), 1, 3000);
        QTRY_VERIFY_WITH_TIMEOUT(!session.scoring(), 3000);
        QCOMPARE(answerBodies.at(0).value("voiceScore").toInt(), 73);
        QVERIFY(!answerBodies.at(0).contains("visualScore"));

        session.m_currentQid = 12;
        session.m_scoring = true;
        session.m_thread = {
            QVariantMap{{"kind", "answer"}, {"qid", 12LL}, {"pending", true}},
            QVariantMap{{"kind", "scoring"}, {"qid", 12LL}},
            QVariantMap{{"kind", "question"}, {"qid", 99LL}, {"text", "다음 질문"}}
        };
        const QJsonObject avatar{
            {"combined", 86},
            {"voice", QJsonObject{{"score", 81}, {"metrics", QJsonObject{{"pace", 4}}}}},
            {"visual", QJsonObject{{"score", 91}, {"metrics", QJsonObject{{"posture", 5}}}}}
        };
        session.submitStoredAnswer(12, 7, QStringLiteral("영상 답변"), {},
                                   QStringLiteral("/api/file/12/content"), {}, 0, true,
                                   81, 91, 86, avatar);
        QTRY_COMPARE_WITH_TIMEOUT(answerBodies.size(), 2, 3000);
        QTRY_VERIFY_WITH_TIMEOUT(!session.scoring(), 3000);
        QCOMPARE(answerBodies.at(1).value("voiceScore").toInt(), 81);
        QCOMPARE(answerBodies.at(1).value("visualScore").toInt(), 91);
        QTRY_VERIFY_WITH_TIMEOUT(!mediaBodies.isEmpty(), 3000);
        const QJsonObject videoMedia = mediaBodies.at(0);
        QCOMPARE(videoMedia.value("kind").toString(), QStringLiteral("AVATAR"));
        QCOMPARE(videoMedia.value("questionId").toInteger(), 12LL);
        QCOMPARE(videoMedia.value("answerId").toInteger(), 502LL);
        QCOMPARE(videoMedia.value("metrics").toObject()
                     .value("visual").toObject().value("posture").toInt(), 5);

        session.m_thread.push_back(QVariantMap{
            {"kind", "score"}, {"qid", 13LL}, {"answerId", 503LL}, {"voiceScore", -1}});
        session.recordVoiceScore(13, 77);
        QTRY_COMPARE_WITH_TIMEOUT(mediaBodies.size(), 2, 3000);
        const QJsonObject lateVoice = mediaBodies.at(1);
        QCOMPARE(lateVoice.value("kind").toString(), QStringLiteral("VOICE"));
        QCOMPARE(lateVoice.value("questionId").toInteger(), 13LL);
        QCOMPARE(lateVoice.value("answerId").toInteger(), 503LL);
        QCOMPARE(lateVoice.value("scoreDetail").toObject().value("voiceScore").toInt(), 77);
    }

    void reloadThreadRestoresMediaScoresAndFailsClosedOnReviewError()
    {
        QTcpServer server;
        QVERIFY(server.listen(QHostAddress::LocalHost, 0));
        bool failReview = false;

        connect(&server, &QTcpServer::newConnection, this, [&]() {
            while (server.hasPendingConnections()) {
                QTcpSocket* socket = server.nextPendingConnection();
                connect(socket, &QTcpSocket::readyRead, socket,
                        [&, socket, request = QByteArray(), handled = false]() mutable {
                    request += socket->readAll();
                    if (handled || !completeHttpRequest(request)) return;
                    handled = true;
                    if (request.startsWith("GET /api/interview/sessions/7/questions ")) {
                        writeHttpResponse(socket, 200,
                            R"({"success":true,"data":[{"id":11,"question":"질문","questionType":"JOB","parentQuestionId":null}]})");
                    } else if (request.startsWith("GET /api/interview/sessions/7/review ")) {
                        if (failReview) {
                            writeHttpResponse(socket, 500,
                                R"({"success":false,"message":"review unavailable","data":null})");
                        } else {
                            writeHttpResponse(socket, 200,
                                R"({"success":true,"data":{"sessionId":7,"mode":"PRESSURE","items":[{"questionId":11,"answerId":501,"answerText":"답변","audioUrl":"/a","videoUrl":"/v","score":84,"feedback":"good","improvedAnswer":"better","modelAnswer":"model","voiceScore":72,"visualScore":89}]}})");
                        }
                    } else {
                        writeHttpResponse(socket, 500,
                            R"({"success":false,"message":"unexpected","data":null})");
                    }
                });
            }
        });

        ApiClient api;
        api.setBaseUrl(QStringLiteral("http://127.0.0.1:%1").arg(server.serverPort()));
        SettingsStore store;
        InterviewSession session(&api, &store);
        session.m_sessionId = 7;
        session.m_loading = true;
        session.reloadThread();
        QTRY_VERIFY_WITH_TIMEOUT(!session.loading(), 3000);
        QVERIFY(!session.threadLoadFailed());
        QCOMPARE(session.thread().size(), 3);
        const QVariantMap restoredScore = session.thread().at(2).toMap();
        QCOMPARE(restoredScore.value("kind").toString(), QStringLiteral("score"));
        QCOMPARE(restoredScore.value("voiceScore").toInt(), 72);
        QCOMPARE(restoredScore.value("visualScore").toInt(), 89);

        const QVariantList stableThread = session.thread();
        session.m_currentQid = 11;
        session.m_loading = true;
        failReview = true;
        QSignalSpy errorSpy(&session, &InterviewSession::errorOccurred);
        session.reloadThread();
        QTRY_COMPARE_WITH_TIMEOUT(errorSpy.count(), 1, 3000);
        QVERIFY(session.threadLoadFailed());
        QVERIFY(!session.loading());
        QCOMPARE(session.currentQid(), -1LL);
        QCOMPARE(session.thread(), stableThread);

        failReview = false;
        session.retryLoadThread();
        QTRY_VERIFY_WITH_TIMEOUT(!session.loading(), 3000);
        QVERIFY(!session.threadLoadFailed());
        QCOMPARE(session.thread().at(2).toMap().value("voiceScore").toInt(), 72);
    }

    void cardActionsUseTheirQuestionIdAndDeduplicateInFlightAiCalls()
    {
        QTcpServer server;
        QVERIFY(server.listen(QHostAddress::LocalHost, 0));
        QPointer<QTcpSocket> followSocket;
        QPointer<QTcpSocket> modelSocket;
        int followRequests = 0;
        int modelRequests = 0;

        connect(&server, &QTcpServer::newConnection, this, [&]() {
            while (server.hasPendingConnections()) {
                QTcpSocket* socket = server.nextPendingConnection();
                connect(socket, &QTcpSocket::readyRead, socket,
                        [&, socket, request = QByteArray(), handled = false]() mutable {
                    request += socket->readAll();
                    if (handled || !completeHttpRequest(request)) return;
                    handled = true;
                    if (request.startsWith("POST /api/interview/questions/11/follow-ups ")) {
                        ++followRequests;
                        followSocket = socket;
                    } else if (request.startsWith(
                                   "POST /api/interview/questions/12/model-answer ")) {
                        ++modelRequests;
                        modelSocket = socket;
                    } else if (request.startsWith(
                                   "GET /api/interview/sessions/7/progress ")) {
                        writeHttpResponse(socket, 200,
                            R"({"success":true,"data":{"totalQuestions":3,"answeredQuestions":2,"finished":false}})");
                    } else {
                        writeHttpResponse(socket, 500,
                            R"({"success":false,"message":"wrong question id","data":null})");
                    }
                });
            }
        });

        ApiClient api;
        api.setBaseUrl(QStringLiteral("http://127.0.0.1:%1").arg(server.serverPort()));
        SettingsStore store;
        InterviewSession session(&api, &store);
        session.m_sessionId = 7;
        session.m_mode = QStringLiteral("압박 면접"); // JobModel이 실제로 전달하는 표시 라벨
        session.m_thread = {
            QVariantMap{{"kind", "score"}, {"qid", 11LL}, {"answerId", 501LL}, {"modelAnswer", ""}},
            QVariantMap{{"kind", "score"}, {"qid", 12LL}, {"answerId", 502LL}, {"modelAnswer", ""}}
        };

        session.requestFollowUp(11);
        session.requestFollowUp(11);
        QTRY_COMPARE_WITH_TIMEOUT(followRequests, 1, 3000);
        QVERIFY(session.followUpPending(11));
        QVERIFY(!followSocket.isNull());
        writeHttpResponse(followSocket, 200,
            R"({"success":true,"data":[{"id":99,"question":"11번의 꼬리질문","questionType":"FOLLOW_UP","parentQuestionId":11}]})");
        QTRY_VERIFY_WITH_TIMEOUT(!session.followUpPending(11), 3000);
        QCOMPARE(session.thread().last().toMap().value("qid").toLongLong(), 99LL);
        QCOMPARE(session.currentQid(), 99LL);

        session.m_mode = QStringLiteral("BASIC");
        session.requestFollowUp(12);
        QTest::qWait(100);
        QCOMPARE(followRequests, 1);
        QVERIFY(!session.followUpPending(12));

        session.requestModelAnswer(12);
        session.requestModelAnswer(12);
        QTRY_COMPARE_WITH_TIMEOUT(modelRequests, 1, 3000);
        QVERIFY(session.modelAnswerPending(12));
        QVERIFY(!modelSocket.isNull());
        writeHttpResponse(modelSocket, 200,
            R"({"success":true,"data":{"modelAnswer":"12번 모범답안"}})");
        QTRY_VERIFY_WITH_TIMEOUT(!session.modelAnswerPending(12), 3000);
        QCOMPARE(modelRequests, 1);
        bool updated = false;
        for (const QVariant& value : session.thread()) {
            const QVariantMap item = value.toMap();
            if (item.value("kind") == QStringLiteral("score")
                && item.value("qid").toLongLong() == 12) {
                QCOMPARE(item.value("modelAnswer").toString(), QStringLiteral("12번 모범답안"));
                updated = true;
            }
        }
        QVERIFY(updated);

        QFile qmlFile(QStringLiteral(CAREERTUNER_DESKTOP_SOURCE_DIR "/qml/SessionThread.qml"));
        QVERIFY(qmlFile.open(QIODevice::ReadOnly));
        const QByteArray qml = qmlFile.readAll();
        QVERIFY(qml.contains("visible: session.mode === \"PRESSURE\" || session.mode === \"압박 면접\""));
        QVERIFY(qml.contains("session.requestFollowUp(scoreRoot.data_.qid)"));
        QVERIFY(qml.contains("session.followUpPendingQuestionIds.indexOf(data_.qid)"));
        QVERIFY(qml.contains("session.modelAnswerPendingQuestionIds.indexOf(data_.qid)"));
    }

    void aiChargeCoordinatorPreviewsAcknowledgesAndSuppliesBoundHeaders()
    {
        QTcpServer server;
        QVERIFY(server.listen(QHostAddress::LocalHost, 0));
        QString actionKey;
        QStringList sequence;
        QByteArray acknowledgementBody;

        connect(&server, &QTcpServer::newConnection, this, [&]() {
            while (server.hasPendingConnections()) {
                QTcpSocket* socket = server.nextPendingConnection();
                QVERIFY(socket != nullptr);
                connect(socket, &QTcpSocket::readyRead, socket,
                        [&, socket, request = QByteArray(), handled = false]() mutable {
                    request += socket->readAll();
                    if (handled || !completeHttpRequest(request)) return;
                    handled = true;
                    if (request.startsWith("POST /api/billing/charge-preview ")) {
                        sequence.push_back(QStringLiteral("preview"));
                        const QJsonObject previewRequest =
                            QJsonDocument::fromJson(httpBody(request)).object();
                        actionKey = previewRequest.value(QStringLiteral("actionKey")).toString();
                        QCOMPARE(previewRequest.value(QStringLiteral("featureType")).toString(),
                                 QStringLiteral("INTERVIEW_ANSWER_EVAL"));
                        QVERIFY(actionKey.startsWith(QStringLiteral("AI_USAGE:")));
                        const QByteArray body = QStringLiteral(
                            R"({"success":true,"data":{"featureType":"INTERVIEW_ANSWER_EVAL","chargeType":"CREDIT","chargeAmount":2,"minimumCreditCost":1,"maximumCreditCost":4,"usageBased":true,"remainingTicket":0,"currentCredit":30,"sufficient":true,"triggerType":"CREDIT_USE","actionKey":"%1","refundPolicyId":7,"refundPolicyVersion":3,"refundPolicyTitle":"환불정책","refundPolicySummary":"AI 사용 전 안내"}})")
                            .arg(actionKey).toUtf8();
                        writeHttpResponse(socket, 200, body);
                    } else if (request.startsWith(
                                   "POST /api/billing/refund-policy/acknowledgements ")) {
                        sequence.push_back(QStringLiteral("ack"));
                        acknowledgementBody = httpBody(request);
                        writeHttpResponse(socket, 200,
                            R"({"success":true,"data":{}})");
                    } else {
                        writeHttpResponse(socket, 500,
                            R"({"success":false,"message":"unexpected","data":null})");
                    }
                });
            }
        });

        ApiClient api;
        api.setBaseUrl(QStringLiteral("http://127.0.0.1:%1").arg(server.serverPort()));
        AiChargeCoordinator charge(&api);
        QSignalSpy noticeSpy(&charge, &AiChargeCoordinator::notice);
        QSignalSpy errorSpy(&charge, &AiChargeCoordinator::errorOccurred);
        bool operationCalled = false;

        charge.run(QStringLiteral("INTERVIEW_ANSWER_EVAL"),
            [&](const ApiClient::Headers& headers) {
                sequence.push_back(QStringLiteral("operation"));
                operationCalled = true;
                QHash<QByteArray, QByteArray> byName;
                for (const auto& header : headers) byName.insert(header.first, header.second);
                QCOMPARE(byName.value("X-AI-Charge-Feature"),
                         QByteArray("INTERVIEW_ANSWER_EVAL"));
                QCOMPARE(QString::fromUtf8(byName.value("X-AI-Charge-Acknowledgement")),
                         actionKey);
            });

        QTRY_VERIFY_WITH_TIMEOUT(operationCalled, 3000);
        QCOMPARE(sequence, QStringList({QStringLiteral("preview"), QStringLiteral("ack"),
                                        QStringLiteral("operation")}));
        QCOMPARE(noticeSpy.count(), 1);
        QCOMPARE(errorSpy.count(), 0);
        const QJsonObject acknowledgement = QJsonDocument::fromJson(acknowledgementBody).object();
        QCOMPARE(acknowledgement.value(QStringLiteral("policyId")).toInt(), 7);
        QCOMPARE(acknowledgement.value(QStringLiteral("triggerType")).toString(),
                 QStringLiteral("CREDIT_USE"));
        QCOMPARE(acknowledgement.value(QStringLiteral("actionKey")).toString(), actionKey);
    }

    void aiChargeCoordinatorBlocksWithoutCallingActualOperation()
    {
        QTcpServer server;
        QVERIFY(server.listen(QHostAddress::LocalHost, 0));
        int actualCalls = 0;

        connect(&server, &QTcpServer::newConnection, this, [&]() {
            while (server.hasPendingConnections()) {
                QTcpSocket* socket = server.nextPendingConnection();
                connect(socket, &QTcpSocket::readyRead, socket,
                        [socket, request = QByteArray(), handled = false]() mutable {
                    request += socket->readAll();
                    if (handled || !completeHttpRequest(request)) return;
                    handled = true;
                    const QString actionKey = QJsonDocument::fromJson(httpBody(request)).object()
                        .value(QStringLiteral("actionKey")).toString();
                    const QByteArray body = QStringLiteral(
                        R"({"success":true,"data":{"featureType":"INTERVIEW_REPORT","chargeType":"BLOCKED","sufficient":false,"actionKey":"%1"}})")
                        .arg(actionKey).toUtf8();
                    writeHttpResponse(socket, 200, body);
                });
            }
        });

        ApiClient api;
        api.setBaseUrl(QStringLiteral("http://127.0.0.1:%1").arg(server.serverPort()));
        AiChargeCoordinator charge(&api);
        QSignalSpy errorSpy(&charge, &AiChargeCoordinator::errorOccurred);
        charge.run(QStringLiteral("INTERVIEW_REPORT"),
                   [&](const ApiClient::Headers&) { ++actualCalls; });
        QTRY_COMPARE_WITH_TIMEOUT(errorSpy.count(), 1, 3000);
        QCOMPARE(actualCalls, 0);
    }

    void aiChargeTicketNoticeIncludesUsageBasedCreditFallback()
    {
        QTcpServer server;
        QVERIFY(server.listen(QHostAddress::LocalHost, 0));
        connect(&server, &QTcpServer::newConnection, this, [&]() {
            while (server.hasPendingConnections()) {
                QTcpSocket* socket = server.nextPendingConnection();
                connect(socket, &QTcpSocket::readyRead, socket,
                        [socket, request = QByteArray(), handled = false]() mutable {
                    request += socket->readAll();
                    if (handled || !completeHttpRequest(request)) return;
                    handled = true;
                    const QString actionKey = QJsonDocument::fromJson(httpBody(request)).object()
                        .value(QStringLiteral("actionKey")).toString();
                    const QByteArray body = QStringLiteral(
                        R"({"success":true,"data":{"featureType":"INTERVIEW_REPORT","chargeType":"TICKET","chargeAmount":1,"minimumCreditCost":2,"maximumCreditCost":7,"usageBased":true,"remainingTicket":3,"currentCredit":20,"sufficient":true,"triggerType":null,"actionKey":"%1","refundPolicyId":7,"refundPolicyVersion":3,"refundPolicyTitle":"환불정책","refundPolicySummary":"사용 전 안내"}})")
                        .arg(actionKey).toUtf8();
                    writeHttpResponse(socket, 200, body);
                });
            }
        });

        ApiClient api;
        api.setBaseUrl(QStringLiteral("http://127.0.0.1:%1").arg(server.serverPort()));
        AiChargeCoordinator charge(&api);
        QSignalSpy noticeSpy(&charge, &AiChargeCoordinator::notice);
        int operationCalls = 0;
        charge.run(QStringLiteral("INTERVIEW_REPORT"),
                   [&](const ApiClient::Headers&) { ++operationCalls; });
        QTRY_COMPARE_WITH_TIMEOUT(operationCalls, 1, 3000);
        QCOMPARE(noticeSpy.count(), 1);
        const QString notice = noticeSpy.at(0).at(0).toString();
        QVERIFY(notice.contains(QStringLiteral("차감 전 잔여 3회")));
        QVERIFY(notice.contains(QStringLiteral("소진 시 최소 2크레딧")));
        QVERIFY(notice.contains(QStringLiteral("최대 7크레딧")));
        QVERIFY(notice.contains(QStringLiteral("실제 사용량")));
    }

    void interviewAiEndpointsUseChargeCoordinatorFeatureMappings()
    {
        QFile source(QStringLiteral(CAREERTUNER_DESKTOP_SOURCE_DIR
                                    "/core/InterviewSession.cpp"));
        QVERIFY(source.open(QIODevice::ReadOnly));
        const QByteArray cpp = source.readAll();
        for (const QByteArray& feature : {
                 QByteArray("INTERVIEW_QUESTION_GEN"),
                 QByteArray("INTERVIEW_ANSWER_EVAL"),
                 QByteArray("INTERVIEW_FOLLOWUP_GEN"),
                 QByteArray("INTERVIEW_MODEL_ANSWER"),
                 QByteArray("INTERVIEW_REPORT"),
                 QByteArray("INTERVIEW_VOICE_SCORING"),
                 QByteArray("INTERVIEW_VIDEO_ANALYSIS")}) {
            const bool mapped = cpp.contains("m_aiCharge->run(QStringLiteral(\"" + feature + "\")")
                || cpp.contains("m_aiCharge->runWithActionKey(QStringLiteral(\"" + feature + "\")");
            QVERIFY2(mapped,
                     feature.constData());
        }
        QVERIFY(cpp.contains("postDetailed(QStringLiteral(\"/api/interview/questions/%1/answers\")"));
        QVERIFY(cpp.contains("/voice-score"));
        QVERIFY(cpp.contains("/avatar-score"));
    }

    void answerSubmissionFingerprintIncludesQuestionTextAndMedia()
    {
        ApiClient api;
        SettingsStore store;
        InterviewSession session(&api, &store);

        const QString first = session.ensureClientSubmissionId(
            11, QStringLiteral("답변"), QStringLiteral("TEXT"));
        QCOMPARE(session.ensureClientSubmissionId(
                     11, QStringLiteral(" 답변 "), QStringLiteral("TEXT")), first);
        const QString changedMedia = session.ensureClientSubmissionId(
            11, QStringLiteral("답변"), QStringLiteral("AUDIO:C:/tmp/a.m4a"));
        QVERIFY(changedMedia != first);
        const QString changedQuestion = session.ensureClientSubmissionId(
            12, QStringLiteral("답변"), QStringLiteral("AUDIO:C:/tmp/a.m4a"));
        QVERIFY(changedQuestion != changedMedia);

        QVERIFY(InterviewSession::shouldPreserveClientSubmissionId(0));
        QVERIFY(InterviewSession::shouldPreserveClientSubmissionId(409));
        QVERIFY(InterviewSession::shouldPreserveClientSubmissionId(503));
        QVERIFY(!InterviewSession::shouldPreserveClientSubmissionId(400));
        QVERIFY(!InterviewSession::shouldPreserveClientSubmissionId(404));
    }

    void sessionListUsesFinishedInsteadOfEndedAtForCompletion()
    {
        QTcpServer server;
        QVERIFY(server.listen(QHostAddress::LocalHost, 0));

        connect(&server, &QTcpServer::newConnection, this, [&]() {
            while (server.hasPendingConnections()) {
                QTcpSocket* socket = server.nextPendingConnection();
                QVERIFY(socket != nullptr);
                connect(socket, &QTcpSocket::readyRead, socket,
                        [socket, request = QByteArray()]() mutable {
                    request += socket->readAll();
                    if (!request.contains("\r\n\r\n")) return;

                    QByteArray body;
                    if (request.startsWith("GET /api/application-cases?")) {
                        body = R"({"success":true,"data":[{"id":101,"companyName":"Acme","jobTitle":"Engineer"}]})";
                    } else if (request.startsWith("GET /api/interview/sessions?")) {
                        body = R"JSON({"success":true,"data":{"sessions":[{"id":1,"applicationCaseId":101,"mode":"BASIC","endedAt":"2026-07-12T10:00:00","totalQuestions":6,"answeredQuestions":6,"finished":true},{"id":2,"applicationCaseId":101,"mode":"JOB","endedAt":"2026-07-12T10:00:00","totalQuestions":6,"answeredQuestions":1,"finished":false},{"id":3,"applicationCaseId":101,"mode":"COMPANY","endedAt":null,"totalQuestions":6,"answeredQuestions":2,"finished":false}]}})JSON";
                    } else {
                        body = R"({"success":false,"message":"unexpected request"})";
                    }

                    socket->write(
                        "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: "
                        + QByteArray::number(body.size()) + "\r\nConnection: close\r\n\r\n" + body);
                    socket->flush();
                    socket->disconnectFromHost();
                });
            }
        });

        ApiClient api;
        api.setBaseUrl(QStringLiteral("http://127.0.0.1:%1").arg(server.serverPort()));
        JobModel jobs;
        jobs.setApi(&api);
        jobs.reload();

        QTRY_COMPARE_WITH_TIMEOUT(jobs.rowCount(), 3, 3000);
        QCOMPARE(jobs.data(jobs.index(0, 0), JobModel::StatusRole).toString(), QStringLiteral("DONE"));
        QCOMPARE(jobs.data(jobs.index(0, 0), JobModel::ProgressRole).toInt(), 100);
        QCOMPARE(jobs.data(jobs.index(1, 0), JobModel::StatusRole).toString(), QStringLiteral("REPORTED"));
        QCOMPARE(jobs.data(jobs.index(1, 0), JobModel::ProgressRole).toInt(), 17);
        QCOMPARE(jobs.data(jobs.index(2, 0), JobModel::StatusRole).toString(), QStringLiteral("RUNNING"));
        QCOMPARE(jobs.data(jobs.index(2, 0), JobModel::ProgressRole).toInt(), 33);
    }

    void createSessionReportsFailureAndClosesDialogOnlyAfterSuccess()
    {
        QTcpServer server;
        QVERIFY(server.listen(QHostAddress::LocalHost, 0));
        int createRequests = 0;

        connect(&server, &QTcpServer::newConnection, this, [&]() {
            while (server.hasPendingConnections()) {
                QTcpSocket* socket = server.nextPendingConnection();
                QVERIFY(socket != nullptr);
                connect(socket, &QTcpSocket::readyRead, socket,
                        [&, socket, request = QByteArray(), handled = false]() mutable {
                    request += socket->readAll();
                    if (handled || !completeHttpRequest(request)) return;
                    handled = true;
                    if (request.startsWith("POST /api/interview/sessions ")) {
                        ++createRequests;
                        if (createRequests == 1) {
                            writeHttpResponse(socket, 500,
                                R"({"success":false,"message":"세션 생성 실패","data":null})");
                        } else {
                            writeHttpResponse(socket, 200,
                                R"({"success":true,"data":{"id":77}})");
                        }
                    } else if (request.startsWith("GET /api/application-cases?")) {
                        writeHttpResponse(socket, 200,
                            R"({"success":true,"data":[{"id":7,"companyName":"Acme","jobTitle":"Engineer"}]})");
                    } else if (request.startsWith("GET /api/interview/sessions?")) {
                        writeHttpResponse(socket, 200,
                            R"({"success":true,"data":{"sessions":[]}})");
                    } else {
                        writeHttpResponse(socket, 500,
                            R"({"success":false,"message":"unexpected","data":null})");
                    }
                });
            }
        });

        ApiClient api;
        api.setBaseUrl(QStringLiteral("http://127.0.0.1:%1").arg(server.serverPort()));
        JobModel jobs;
        jobs.setApi(&api);
        QSignalSpy failedSpy(&jobs, &JobModel::sessionCreateFailed);
        QSignalSpy createdSpy(&jobs, &JobModel::sessionCreated);

        jobs.createSession(7, QStringLiteral("BASIC"));
        QVERIFY(jobs.creatingSession());
        QTRY_COMPARE_WITH_TIMEOUT(failedSpy.count(), 1, 3000);
        QVERIFY(!jobs.creatingSession());
        QCOMPARE(createdSpy.count(), 0);
        QCOMPARE(failedSpy.at(0).at(0).toString(), QStringLiteral("세션 생성 실패"));

        jobs.createSession(7, QStringLiteral("BASIC"));
        QTRY_COMPARE_WITH_TIMEOUT(createdSpy.count(), 1, 3000);
        QVERIFY(!jobs.creatingSession());

        QFile dialog(QStringLiteral(CAREERTUNER_DESKTOP_SOURCE_DIR "/qml/NewJobDialog.qml"));
        QVERIFY(dialog.open(QIODevice::ReadOnly));
        const QByteArray qml = dialog.readAll();
        QVERIFY(qml.contains("function onSessionCreated() { if (dlg.visible) dlg.close() }"));
        QVERIFY(qml.contains("function onSessionCreateFailed(message)"));
        QVERIFY(qml.contains("jobModel.creatingSession"));
        QVERIFY(!qml.contains("jobModel.createSession(dlg.chosenCaseId, modelData.value)\n                                dlg.close()"));
    }

    void desktopQmlDocumentsThemeShareSemanticsAndCoreAccessibility()
    {
        const auto readQml = [](const QString& name) {
            QFile file(QStringLiteral(CAREERTUNER_DESKTOP_SOURCE_DIR "/qml/") + name);
            if (!file.open(QIODevice::ReadOnly)) return QByteArray();
            return file.readAll();
        };

        const QByteArray planner = readQml(QStringLiteral("PlannerOverlay.qml"));
        QVERIFY(planner.contains("color: Theme.darkMode"));
        QVERIFY(planner.contains("Qt.rgba(0.985, 0.985, 0.99, 0.96)"));

        const QByteArray collaboration = readQml(QStringLiteral("CollaborationPage.qml"));
        QVERIFY(collaboration.contains("온라인 제한 서버 공유"));
        QVERIFY(collaboration.contains("서버를 통해 전달되며 소유자 데스크톱이 온라인일 때만"));
        QVERIFY(!collaboration.contains("{ label: \"로컬\", mode: \"LOCAL\""));

        for (const QString& name : {QStringLiteral("LoginPage.qml"),
                                    QStringLiteral("HomeView.qml"),
                                    QStringLiteral("InputBar.qml"),
                                    QStringLiteral("NewJobDialog.qml"),
                                    QStringLiteral("Main.qml")}) {
            const QByteArray qml = readQml(name);
            QVERIFY2(qml.contains("Accessible.role: Accessible.Button"), qPrintable(name));
            QVERIFY2(qml.contains("Accessible.name:"), qPrintable(name));
            QVERIFY2(qml.contains("Keys.onPressed:"), qPrintable(name));
        }

        const QByteArray main = readQml(QStringLiteral("Main.qml"));
        for (const QByteArray& contract : {
                 QByteArray("Accessible.name: notifications.unread > 0"),
                 QByteArray("Accessible.name: \"면접 세션 열기: \" + title + \", \" + mode"),
                 QByteArray("Accessible.name: \"현재 면접 세션을 폰으로 보내기\""),
                 QByteArray("Accessible.name: \"현재 면접 자료 모두 저장\""),
                 QByteArray("Accessible.name: \"현재 면접 리포트 열기\""),
                 QByteArray("Accessible.name: \"면접 스레드로 돌아가기\""),
                 QByteArray("Accessible.name: desktopAds.title.length > 0")}) {
            QVERIFY2(main.contains(contract), contract.constData());
        }

        const QByteArray input = readQml(QStringLiteral("InputBar.qml"));
        for (const QByteArray& contract : {
                 QByteArray("Accessible.name: \"영상 답변 패널 닫기\""),
                 QByteArray("Accessible.name: cameraRecorder.recording ? \"영상 녹화 중지\" : \"영상 녹화 시작\""),
                 QByteArray("Accessible.role: Accessible.CheckBox"),
                 QByteArray("Accessible.name: \"영상 원본 저장 및 분석 동의\""),
                 QByteArray("Accessible.name: \"영상 다시 녹화\""),
                 QByteArray("Accessible.name: \"영상 답변 전송\"")}) {
            QVERIFY2(input.contains(contract), contract.constData());
        }

        const QByteArray settings = readQml(QStringLiteral("SettingsPage.qml"));
        QVERIFY(settings.contains("root.openWebSettings(\"privacy\")"));
        QVERIFY(settings.contains("root.openWebSettings(\"account\")"));
        QVERIFY(settings.contains("전체 끄기·카테고리·방해금지 시간·유형별 설정"));
    }

    void communityPostSearchRejectsLateResponseFromDifferentFilter()
    {
        QTcpServer server;
        QVERIFY(server.listen(QHostAddress::LocalHost, 0));
        QPointer<QTcpSocket> oldSocket;
        QPointer<QTcpSocket> newSocket;

        connect(&server, &QTcpServer::newConnection, this, [&]() {
            while (server.hasPendingConnections()) {
                QTcpSocket* socket = server.nextPendingConnection();
                connect(socket, &QTcpSocket::readyRead, socket,
                        [&, socket, request = QByteArray(), handled = false]() mutable {
                    request += socket->readAll();
                    if (handled || !completeHttpRequest(request)) return;
                    handled = true;
                    if (request.contains("keyword=old")) oldSocket = socket;
                    else if (request.contains("keyword=new")) newSocket = socket;
                    else writeHttpResponse(socket, 500,
                        R"({"success":false,"message":"unexpected","data":null})");
                });
            }
        });

        ApiClient api;
        api.setBaseUrl(QStringLiteral("http://127.0.0.1:%1").arg(server.serverPort()));
        CommunityClient community(&api);
        community.loadPosts(QStringLiteral("CAREER"), QStringLiteral("old"), 0);
        QTRY_VERIFY_WITH_TIMEOUT(!oldSocket.isNull(), 3000);
        community.loadPosts(QStringLiteral("FREE"), QStringLiteral("new"), 0);
        QTRY_VERIFY_WITH_TIMEOUT(!newSocket.isNull(), 3000);

        writeHttpResponse(oldSocket, 200,
            R"({"success":true,"data":{"posts":[{"id":1,"category":"CAREER","title":"과거 검색"}],"total":1}})");
        QTest::qWait(120);
        QVERIFY(community.loading());
        QVERIFY(community.postsModel().isEmpty());

        writeHttpResponse(newSocket, 200,
            R"({"success":true,"data":{"posts":[{"id":2,"category":"FREE","title":"현재 검색"}],"total":1}})");
        QTRY_VERIFY_WITH_TIMEOUT(!community.loading(), 3000);
        QCOMPARE(community.category(), QStringLiteral("FREE"));
        QCOMPARE(community.keyword(), QStringLiteral("new"));
        QCOMPARE(community.postsModel().size(), 1);
        QCOMPARE(community.postsModel().first().toMap().value("id").toLongLong(), 2LL);
        QCOMPARE(community.postsModel().first().toMap().value("title").toString(),
                 QStringLiteral("현재 검색"));
    }

    void notificationPollerUsesPlatformScopedExactUnreadCountAndBulkMutation()
    {
        QTcpServer server;
        QVERIFY(server.listen(QHostAddress::LocalHost, 0));
        QList<QByteArray> requests;

        connect(&server, &QTcpServer::newConnection, this, [&]() {
            while (server.hasPendingConnections()) {
                QTcpSocket* socket = server.nextPendingConnection();
                QVERIFY(socket != nullptr);
                connect(socket, &QTcpSocket::readyRead, socket,
                        [&, socket, request = QByteArray(), handled = false]() mutable {
                    request += socket->readAll();
                    if (handled || !request.contains("\r\n\r\n")) return;
                    handled = true;
                    requests.push_back(request.left(request.indexOf("\r\n")));

                    QByteArray body;
                    if (request.startsWith("GET /api/notifications/preferences ")) {
                        body = R"({"success":true,"data":{"rules":{}}})";
                    } else if (request.startsWith(
                                   "GET /api/notifications/unread-count?platform=DESKTOP ")) {
                        body = R"({"success":true,"data":37})";
                    } else if (request.startsWith(
                                   "GET /api/notifications?page=0&size=20&platform=DESKTOP ")) {
                        // 첫 페이지의 미읽음 수(1)가 전체 미읽음 수(37)를 덮어쓰면 안 된다.
                        body = R"({"success":true,"data":{"notifications":[{"id":9,"type":"NOTICE","title":"공지","read":false}],"total":37,"page":0,"size":20,"hasNext":true}})";
                    } else if (request.startsWith("POST /api/collaboration/desktop-presence ")
                               || request.startsWith(
                                   "POST /api/notifications/read-all?platform=DESKTOP ")) {
                        body = R"({"success":true,"data":null})";
                    } else {
                        body = R"({"success":false,"message":"unexpected request"})";
                    }

                    socket->write(
                        "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: "
                        + QByteArray::number(body.size()) + "\r\nConnection: close\r\n\r\n" + body);
                    socket->flush();
                    socket->disconnectFromHost();
                });
            }
        });

        ApiClient api;
        api.setBaseUrl(QStringLiteral("http://127.0.0.1:%1").arg(server.serverPort()));
        NotificationPoller poller(&api);
        poller.pollNow();

        QTRY_COMPARE_WITH_TIMEOUT(poller.items().size(), 1, 3000);
        QTRY_COMPARE_WITH_TIMEOUT(poller.unread(), 37, 3000);
        QVERIFY(requests.contains(
            QByteArray("GET /api/notifications/unread-count?platform=DESKTOP HTTP/1.1")));

        poller.markAllRead();
        QTRY_VERIFY_WITH_TIMEOUT(requests.contains(
            QByteArray("POST /api/notifications/read-all?platform=DESKTOP HTTP/1.1")), 3000);
        QTRY_COMPARE_WITH_TIMEOUT(poller.unread(), 0, 3000);
    }

    void notificationGlobalPreferencesSuppressDesktopDeliveryConsistently()
    {
        ApiClient api;
        NotificationPoller poller(&api);

        poller.updatePreferences(QJsonObject{
            {"pushEnabled", false},
            {"categories", QJsonObject{{"community", true}}},
            {"rules", QJsonObject()}
        });
        QVERIFY(!poller.globalDeliveryEnabled(QStringLiteral("community")));

        poller.updatePreferences(QJsonObject{
            {"pushEnabled", true},
            {"categories", QJsonObject{{"community", false}, {"notice", true}}},
            {"rules", QJsonObject()}
        });
        QCOMPARE(NotificationPoller::categoryForType(QStringLiteral("COMMENT")),
                 QStringLiteral("community"));
        QCOMPARE(NotificationPoller::categoryForType(QStringLiteral("PAYMENT_COMPLETE")),
                 QStringLiteral("billing"));
        QCOMPARE(NotificationPoller::categoryForType(QStringLiteral("UNKNOWN_TYPE")),
                 QStringLiteral("notice"));
        QVERIFY(!poller.globalDeliveryEnabled(QStringLiteral("community")));
        QVERIFY(poller.globalDeliveryEnabled(QStringLiteral("notice")));

        const QTime nowKst = QDateTime::currentDateTimeUtc().addSecs(9 * 60 * 60).time();
        poller.updatePreferences(QJsonObject{
            {"pushEnabled", true},
            {"categories", QJsonObject{{"notice", true}}},
            {"quietHoursStart", nowKst.addSecs(-60).toString(QStringLiteral("HH:mm"))},
            {"quietHoursEnd", nowKst.addSecs(60).toString(QStringLiteral("HH:mm"))},
            {"rules", QJsonObject()}
        });
        QVERIFY(poller.withinQuietHours());
        QVERIFY(!poller.globalDeliveryEnabled(QStringLiteral("notice")));

        poller.stop();
        QVERIFY(!poller.globalDeliveryEnabled(QStringLiteral("community")));
    }

    void staleUnreadCountCannotOverwriteCompletedReadMutation()
    {
        QTcpServer server;
        QVERIFY(server.listen(QHostAddress::LocalHost, 0));
        QPointer<QTcpSocket> heldUnreadSocket;
        int readRequests = 0;

        connect(&server, &QTcpServer::newConnection, this, [&]() {
            while (server.hasPendingConnections()) {
                QTcpSocket* socket = server.nextPendingConnection();
                QVERIFY(socket != nullptr);
                connect(socket, &QTcpSocket::readyRead, socket,
                        [&, socket, request = QByteArray(), handled = false]() mutable {
                    request += socket->readAll();
                    if (handled || !completeHttpRequest(request)) return;
                    handled = true;
                    if (request.startsWith("GET /api/notifications/preferences ")) {
                        writeHttpResponse(socket, 200,
                            R"({"success":true,"data":{"rules":{}}})");
                    } else if (request.startsWith(
                                   "GET /api/notifications/unread-count?platform=DESKTOP ")) {
                        heldUnreadSocket = socket; // read 성공 뒤 과거 count를 늦게 보낸다.
                    } else if (request.startsWith(
                                   "GET /api/notifications?page=0&size=20&platform=DESKTOP ")) {
                        writeHttpResponse(socket, 200,
                            R"({"success":true,"data":{"notifications":[{"id":9,"type":"NOTICE","title":"공지","read":false}],"total":1,"page":0,"size":20,"hasNext":false}})");
                    } else if (request.startsWith("PATCH /api/notifications/9/read ")) {
                        ++readRequests;
                        writeHttpResponse(socket, 200,
                            R"({"success":true,"data":null})");
                    } else if (request.startsWith("POST /api/collaboration/desktop-presence ")) {
                        writeHttpResponse(socket, 200,
                            R"({"success":true,"data":null})");
                    } else {
                        writeHttpResponse(socket, 500,
                            R"({"success":false,"message":"unexpected","data":null})");
                    }
                });
            }
        });

        ApiClient api;
        api.setBaseUrl(QStringLiteral("http://127.0.0.1:%1").arg(server.serverPort()));
        NotificationPoller poller(&api);
        poller.pollNow();
        QTRY_COMPARE_WITH_TIMEOUT(poller.items().size(), 1, 3000);
        QTRY_VERIFY_WITH_TIMEOUT(!heldUnreadSocket.isNull(), 3000);

        poller.markAsRead(9);
        QTRY_COMPARE_WITH_TIMEOUT(readRequests, 1, 3000);
        QTRY_VERIFY_WITH_TIMEOUT(poller.items().first().toMap().value("isRead").toBool(), 3000);
        QCOMPARE(poller.unread(), 0);

        writeHttpResponse(heldUnreadSocket, 200, R"({"success":true,"data":37})");
        QTest::qWait(120);
        QCOMPARE(poller.unread(), 0);
    }

    void cleanupRequestKeepsCapturedOldOriginAndBearerAcrossServerChange()
    {
        QTcpServer oldServer;
        QTcpServer newServer;
        QVERIFY(oldServer.listen(QHostAddress::LocalHost, 0));
        QVERIFY(newServer.listen(QHostAddress::LocalHost, 0));

        QByteArray oldRequest;
        int newServerConnections = 0;
        bool responseSent = false;
        bool callbackCalled = false;
        bool callbackOk = false;
        QEventLoop loop;

        connect(&newServer, &QTcpServer::newConnection, this, [&]() {
            ++newServerConnections;
            while (newServer.hasPendingConnections())
                newServer.nextPendingConnection()->deleteLater();
        });
        connect(&oldServer, &QTcpServer::newConnection, this, [&]() {
            QTcpSocket* socket = oldServer.nextPendingConnection();
            QVERIFY(socket != nullptr);
            const auto consume = [&, socket]() {
                oldRequest += socket->readAll();
                if (responseSent || !oldRequest.contains("\r\n\r\n")) return;
                responseSent = true;
                const QByteArray body = R"({"success":true,"message":"","data":null})";
                socket->write(
                    "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: "
                    + QByteArray::number(body.size()) + "\r\nConnection: close\r\n\r\n" + body);
                socket->flush();
                socket->disconnectFromHost();
            };
            connect(socket, &QTcpSocket::readyRead, this, consume);
            consume();
        });

        ApiClient api;
        const QString oldBase = QStringLiteral("http://127.0.0.1:%1").arg(oldServer.serverPort());
        const QString newBase = QStringLiteral("http://127.0.0.1:%1").arg(newServer.serverPort());
        api.setBaseUrl(oldBase);
        api.setToken(QStringLiteral("general-old-token"));
        const QString capturedBaseUrl = api.baseUrl();

        // 실제 정리 시점이 서버 전환 뒤여도 send/upload 시작 때 캡처한 origin을 사용해야 한다.
        api.setBaseUrl(newBase);
        api.setToken(QStringLiteral("new-host-token"));
        api.deleteResourceWithToken(
            QStringLiteral("/api/file/42"), capturedBaseUrl,
            QStringLiteral("old-cleanup-token"),
            [&](bool ok, const QJsonValue&, const QString&) {
                callbackCalled = true;
                callbackOk = ok;
                loop.quit();
            });

        if (!callbackCalled) {
            QTimer::singleShot(3000, &loop, &QEventLoop::quit);
            loop.exec();
        }

        QVERIFY(callbackCalled);
        QVERIFY(callbackOk);
        QVERIFY(responseSent);
        QCOMPARE(newServerConnections, 0);
        QCOMPARE(api.baseUrl(), newBase);
        QCOMPARE(api.token(), QStringLiteral("new-host-token"));
        QVERIFY(oldRequest.startsWith("DELETE /api/file/42 HTTP/1.1\r\n"));
        QVERIFY(oldRequest.contains("Authorization: Bearer old-cleanup-token\r\n"));
        QVERIFY(!oldRequest.contains("general-old-token"));
        QVERIFY(!oldRequest.contains("new-host-token"));
        QVERIFY(oldRequest.contains(
            QByteArray("Host: 127.0.0.1:") + QByteArray::number(oldServer.serverPort()) + "\r\n"));
    }

    void accountChangeAbortsAndClearsAutoPrepStreamAndHomeState()
    {
        QTcpServer server;
        QVERIFY(server.listen(QHostAddress::LocalHost, 0));
        QPointer<QTcpSocket> streamSocket;

        connect(&server, &QTcpServer::newConnection, this, [&]() {
            while (server.hasPendingConnections()) {
                QTcpSocket* socket = server.nextPendingConnection();
                QVERIFY(socket != nullptr);
                connect(socket, &QTcpSocket::readyRead, socket,
                        [&, socket, request = QByteArray(), handled = false]() mutable {
                    request += socket->readAll();
                    if (handled || !completeHttpRequest(request)) return;
                    handled = true;
                    if (request.startsWith("GET /api/auth/me ")) {
                        writeHttpResponse(socket, 200,
                            R"({"success":true,"data":{"email":"test@example.com"}})");
                    } else if (request.startsWith("POST /api/auto-prep/run/stream ")) {
                        streamSocket = socket;
                        socket->write(
                            "HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\n"
                            "Connection: keep-alive\r\n\r\n"
                            "event: plan\n"
                            "data: {\"steps\":[\"JOB\"]}\n\n");
                        socket->flush();
                    } else {
                        writeHttpResponse(socket, 500,
                            R"({"success":false,"message":"unexpected","data":null})");
                    }
                });
            }
        });

        ApiClient api;
        api.setBaseUrl(QStringLiteral("http://127.0.0.1:%1").arg(server.serverPort()));
        api.setToken(QStringLiteral("old-account"));
        AutoPrepRunner runner(&api);
        QSignalSpy clearedSpy(&runner, &AutoPrepRunner::cleared);
        QSignalSpy errorSpy(&runner, &AutoPrepRunner::errorOccurred);

        runner.run(QStringLiteral("준비해줘"), 7, QStringLiteral("BASIC"));
        QTRY_COMPARE_WITH_TIMEOUT(runner.steps().size(), 1, 3000);
        QVERIFY(runner.running());
        QVERIFY(!streamSocket.isNull());

        api.setToken(QStringLiteral("new-account"));
        QTRY_COMPARE_WITH_TIMEOUT(clearedSpy.count(), 1, 1000);
        QVERIFY(!runner.running());
        QVERIFY(runner.steps().isEmpty());
        QTRY_VERIFY_WITH_TIMEOUT(streamSocket.isNull()
                                 || streamSocket->state() == QAbstractSocket::UnconnectedState,
                                 3000);
        QCOMPARE(errorSpy.count(), 0);

        QFile home(QStringLiteral(CAREERTUNER_DESKTOP_SOURCE_DIR "/qml/HomeView.qml"));
        QVERIFY(home.open(QIODevice::ReadOnly));
        const QByteArray qml = home.readAll();
        QVERIFY(qml.contains("function onCleared() { root.resetAccountState() }"));
        QVERIFY(qml.contains("root.candidates = []"));
        QVERIFY(qml.contains("root.modes = []"));
        QVERIFY(qml.contains("root.askMessage = \"\""));
    }

    void autoPrepRefreshesAuthenticationBeforeOpeningPostSseExactlyOnce()
    {
        QTcpServer server;
        QVERIFY(server.listen(QHostAddress::LocalHost, 0));
        int preflightRequests = 0;
        int refreshRequests = 0;
        int streamRequests = 0;
        QByteArray streamRequest;

        connect(&server, &QTcpServer::newConnection, this, [&]() {
            while (server.hasPendingConnections()) {
                QTcpSocket* socket = server.nextPendingConnection();
                connect(socket, &QTcpSocket::readyRead, socket,
                        [&, socket, request = QByteArray(), handled = false]() mutable {
                    request += socket->readAll();
                    if (handled || !completeHttpRequest(request)) return;
                    handled = true;
                    if (request.startsWith("GET /api/auth/me ")) {
                        ++preflightRequests;
                        if (request.contains("Authorization: Bearer expired-access\r\n")) {
                            writeHttpResponse(socket, 401,
                                R"({"success":false,"message":"expired","data":null})");
                        } else {
                            QVERIFY(request.contains(
                                "Authorization: Bearer refreshed-access\r\n"));
                            writeHttpResponse(socket, 200,
                                R"({"success":true,"data":{"email":"user@example.com"}})");
                        }
                    } else if (request.startsWith("POST /api/auth/refresh ")) {
                        ++refreshRequests;
                        QCOMPARE(httpBody(request), QByteArray(R"({"refreshToken":"refresh-old"})"));
                        writeHttpResponse(socket, 200,
                            R"({"success":true,"data":{"accessToken":"refreshed-access","refreshToken":"refresh-new","user":{"name":"사용자","email":"user@example.com","plan":"FREE"}}})");
                    } else if (request.startsWith("POST /api/auto-prep/run/stream ")) {
                        ++streamRequests;
                        streamRequest = request;
                        const QByteArray events =
                            "event: plan\n"
                            "data: {\"steps\":[\"JOB\"]}\n\n"
                            "event: done\n"
                            "data: {\"message\":\"완료\"}\n\n";
                        socket->write(
                            "HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nContent-Length: "
                            + QByteArray::number(events.size())
                            + "\r\nConnection: close\r\n\r\n" + events);
                        socket->flush();
                        socket->disconnectFromHost();
                    } else {
                        writeHttpResponse(socket, 500,
                            R"({"success":false,"message":"unexpected","data":null})");
                    }
                });
            }
        });

        ApiClient api;
        api.setBaseUrl(QStringLiteral("http://127.0.0.1:%1").arg(server.serverPort()));
        api.setToken(QStringLiteral("expired-access"));
        api.configureTokenRefresh([]() { return QStringLiteral("refresh-old"); }, {});
        AutoPrepRunner runner(&api);
        QSignalSpy finishedSpy(&runner, &AutoPrepRunner::finished);
        QSignalSpy errorSpy(&runner, &AutoPrepRunner::errorOccurred);

        runner.run(QStringLiteral("자동 준비"), 7, QStringLiteral("BASIC"));
        QTRY_COMPARE_WITH_TIMEOUT(finishedSpy.count(), 1, 4000);
        QCOMPARE(preflightRequests, 2);
        QCOMPARE(refreshRequests, 1);
        QCOMPARE(streamRequests, 1);
        QCOMPARE(api.token(), QStringLiteral("refreshed-access"));
        QVERIFY(streamRequest.contains("Authorization: Bearer refreshed-access\r\n"));
        QVERIFY(!streamRequest.contains("expired-access"));
        QCOMPARE(errorSpy.count(), 0);
    }

    void autoPrepPreflightFailureDoesNotStartStreamAndRequestsRelogin()
    {
        QTcpServer server;
        QVERIFY(server.listen(QHostAddress::LocalHost, 0));
        int streamRequests = 0;
        connect(&server, &QTcpServer::newConnection, this, [&]() {
            while (server.hasPendingConnections()) {
                QTcpSocket* socket = server.nextPendingConnection();
                connect(socket, &QTcpSocket::readyRead, socket,
                        [&, socket, request = QByteArray(), handled = false]() mutable {
                    request += socket->readAll();
                    if (handled || !completeHttpRequest(request)) return;
                    handled = true;
                    if (request.startsWith("GET /api/auth/me ")) {
                        writeHttpResponse(socket, 401,
                            R"({"success":false,"message":"expired","data":null})");
                    } else if (request.startsWith("POST /api/auth/refresh ")) {
                        writeHttpResponse(socket, 401,
                            R"({"success":false,"message":"refresh expired","data":null})");
                    } else if (request.startsWith("POST /api/auto-prep/run/stream ")) {
                        ++streamRequests;
                        writeHttpResponse(socket, 500,
                            R"({"success":false,"message":"must not run","data":null})");
                    }
                });
            }
        });

        ApiClient api;
        api.setBaseUrl(QStringLiteral("http://127.0.0.1:%1").arg(server.serverPort()));
        api.setToken(QStringLiteral("expired-access"));
        api.configureTokenRefresh([]() { return QStringLiteral("expired-refresh"); }, {});
        AutoPrepRunner runner(&api);
        QSignalSpy errorSpy(&runner, &AutoPrepRunner::errorOccurred);
        runner.run(QStringLiteral("자동 준비"), 7, QStringLiteral("BASIC"));

        QTRY_COMPARE_WITH_TIMEOUT(errorSpy.count(), 1, 4000);
        QVERIFY(errorSpy.at(0).at(0).toString().contains(QStringLiteral("다시 로그인")));
        QCOMPARE(streamRequests, 0);
        QVERIFY(!runner.running());
    }

    void existingReportIsSavedImmediatelyWhenAutoSaveIsRequested()
    {
        QTemporaryDir saveDir;
        QVERIFY(saveDir.isValid());
        SettingsStore store;
        store.setAutoSave(true);
        store.setSaveDir(saveDir.path());
        ApiClient api;
        InterviewSession session(&api, &store);
        session.m_sessionId = 77;
        session.m_title = QStringLiteral("이전 계정 세션");
        session.m_report = QVariantMap{{QStringLiteral("totalScore"), 91}};
        QSignalSpy exportedSpy(&session, &InterviewSession::exported);

        session.maybeAutoSave();
        QCoreApplication::processEvents();
        QCOMPARE(exportedSpy.count(), 1);
        QVERIFY(!QDir(saveDir.path()).entryList(QDir::Dirs | QDir::NoDotAndDotDot).isEmpty());
    }

    void sentAttachmentSnapshotDoesNotRemoveLaterUploads()
    {
        const QVariantList pending{
            QVariantMap{{QStringLiteral("id"), 11LL}, {QStringLiteral("name"), QStringLiteral("sent-a")}},
            QVariantMap{{QStringLiteral("id"), 12LL}, {QStringLiteral("name"), QStringLiteral("sent-b")}},
            QVariantMap{{QStringLiteral("id"), 21LL}, {QStringLiteral("name"), QStringLiteral("uploaded-later")}}
        };

        const QVariantList remaining = CollaborationClient::withoutAttachmentIds(
            pending, QSet<qint64>{11LL, 12LL});

        QCOMPARE(remaining.size(), 1);
        QCOMPARE(remaining.first().toMap().value(QStringLiteral("id")).toLongLong(), 21LL);
        QCOMPARE(remaining.first().toMap().value(QStringLiteral("name")).toString(),
                 QStringLiteral("uploaded-later"));
    }

    void inFlightAttachmentsAreDeferredUntilSendResult()
    {
        const QVariantList pending{
            QVariantMap{{QStringLiteral("id"), 11LL}},
            QVariantMap{{QStringLiteral("id"), 12LL}},
            QVariantMap{{QStringLiteral("id"), 21LL}}
        };
        const QSet<qint64> inFlight{11LL, 12LL};

        QVERIFY(CollaborationClient::cleanupAttachmentIds(pending, inFlight)
                == QSet<qint64>{21LL});
        QVERIFY(CollaborationClient::missingAttachmentIds(inFlight, pending).isEmpty());

        const QVariantList onlyOneStillVisible{
            QVariantMap{{QStringLiteral("id"), 11LL}},
            QVariantMap{{QStringLiteral("id"), 21LL}}
        };
        QVERIFY(CollaborationClient::missingAttachmentIds(inFlight, onlyOneStillVisible)
                == QSet<qint64>{12LL});
        QVERIFY(CollaborationClient::missingAttachmentIds(inFlight, {}).contains(11LL));
        QVERIFY(CollaborationClient::missingAttachmentIds(inFlight, {}).contains(12LL));
    }

    void releasingSubmittedVideoPreservesFileForSessionExport()
    {
        QTemporaryDir dir;
        QVERIFY(dir.isValid());
        const QString path = dir.filePath(QStringLiteral("video-answer.mp4"));
        QFile file(path);
        QVERIFY(file.open(QIODevice::WriteOnly));
        QCOMPARE(file.write("video"), 5);
        file.close();

        CameraRecorder recorder;
        recorder.m_outPath = path;

        QVERIFY(recorder.release(path));
        QVERIFY(QFile::exists(path));
        QVERIFY(recorder.m_outPath.isEmpty());
    }

    void cancelledCameraRecordingDeletesManagedTempWithoutEmittingRecorded()
    {
        const QString managedDir = CameraRecorder::recordingDir();
        QVERIFY(QDir().mkpath(managedDir));
        const QString path = managedDir + QStringLiteral("/video-answer-cancelled-%1.mp4")
            .arg(QUuid::createUuid().toString(QUuid::WithoutBraces));
        QFile file(path);
        QVERIFY(file.open(QIODevice::WriteOnly));
        QCOMPARE(file.write("video"), 5);
        file.close();

        CameraRecorder recorder;
        QSignalSpy recordedSpy(&recorder, &CameraRecorder::recorded);
        recorder.m_outPath = path;
        recorder.m_recording = true;
        recorder.m_cancelled = true;

        recorder.finishRecording();

        QVERIFY(!QFile::exists(path));
        QVERIFY(recorder.m_outPath.isEmpty());
        QVERIFY(!recorder.recording());
        QCOMPARE(recordedSpy.count(), 0);
    }

    void cameraErrorAndLogoutResetDeleteRecorderOwnedTempFiles()
    {
        const QString managedDir = CameraRecorder::recordingDir();
        QVERIFY(QDir().mkpath(managedDir));
        const auto createManaged = [&](const QString& label) {
            const QString path = managedDir + QStringLiteral("/video-answer-%1-%2.mp4")
                .arg(label, QUuid::createUuid().toString(QUuid::WithoutBraces));
            QFile file(path);
            if (!file.open(QIODevice::WriteOnly)) return QString();
            if (file.write("partial") != 7) return QString();
            file.close();
            return path;
        };

        CameraRecorder recorder;
        QSignalSpy errorSpy(&recorder, &CameraRecorder::errorOccurred);
        const QString failedPath = createManaged(QStringLiteral("failed"));
        QVERIFY(!failedPath.isEmpty());
        recorder.m_outPath = failedPath;
        recorder.m_recording = true;
        recorder.handleRecorderError(QStringLiteral("encoder failed"));
        QVERIFY(!QFile::exists(failedPath));
        QVERIFY(recorder.m_outPath.isEmpty());
        QVERIFY(!recorder.recording());
        QCOMPARE(errorSpy.count(), 1);

        const QString logoutPath = createManaged(QStringLiteral("logout"));
        QVERIFY(!logoutPath.isEmpty());
        recorder.m_outPath = logoutPath;
        recorder.m_recording = false; // recorder error 뒤처럼 QML stopPreview만으로는 찾지 못하던 상태
        recorder.reset();
        QVERIFY(!QFile::exists(logoutPath));
        QVERIFY(recorder.m_outPath.isEmpty());

        QFile inputBar(QStringLiteral(CAREERTUNER_DESKTOP_SOURCE_DIR "/qml/InputBar.qml"));
        QVERIFY(inputBar.open(QIODevice::ReadOnly));
        const QByteArray qml = inputBar.readAll();
        const qsizetype cancelFunction = qml.indexOf("function cancelSessionMedia()");
        QVERIFY(cancelFunction >= 0);
        QVERIFY(qml.indexOf("cameraRecorder.reset()", cancelFunction) > cancelFunction);
    }

    void cancelledVoiceRecordingDeletesManagedTempWithoutEmittingRecorded()
    {
        const QString managedDir = VoiceRecorder::recordingDir();
        QVERIFY(QDir().mkpath(managedDir));
        const QString suffix = QUuid::createUuid().toString(QUuid::WithoutBraces);
        const QString cancelledPath = managedDir
            + QStringLiteral("/answer-cancelled-%1.m4a").arg(suffix);
        QFile cancelledFile(cancelledPath);
        QVERIFY(cancelledFile.open(QIODevice::WriteOnly));
        QCOMPARE(cancelledFile.write("audio"), 5);
        cancelledFile.close();

        VoiceRecorder recorder;
        QSignalSpy recordedSpy(&recorder, &VoiceRecorder::recorded);
        QSignalSpy recordingChangedSpy(&recorder, &VoiceRecorder::recordingChanged);
        recorder.m_outPath = cancelledPath;
        recorder.m_recording = true;
        recorder.m_cancelled = true;

        recorder.finishRecording();

        QVERIFY(!QFile::exists(cancelledPath));
        QVERIFY(recorder.m_outPath.isEmpty());
        QVERIFY(!recorder.recording());
        QCOMPARE(recordedSpy.count(), 0);
        QCOMPARE(recordingChangedSpy.count(), 1);

        QTemporaryDir externalDir;
        QVERIFY(externalDir.isValid());
        const QString externalPath = externalDir.filePath(QStringLiteral("answer-external.m4a"));
        QFile externalFile(externalPath);
        QVERIFY(externalFile.open(QIODevice::WriteOnly));
        QCOMPARE(externalFile.write("audio"), 5);
        externalFile.close();

        recorder.m_outPath = externalPath;
        recorder.m_recording = true;
        recorder.m_cancelled = true;
        recorder.finishRecording();
        QVERIFY(QFile::exists(externalPath));
        QCOMPARE(recordedSpy.count(), 0);
    }

    void completedVoiceRecordingTransfersManagedFileToInterviewSession()
    {
        const QString managedDir = VoiceRecorder::recordingDir();
        QVERIFY(QDir().mkpath(managedDir));
        const QString path = managedDir + QStringLiteral("/answer-completed-%1.m4a")
            .arg(QUuid::createUuid().toString(QUuid::WithoutBraces));
        QFile file(path);
        QVERIFY(file.open(QIODevice::WriteOnly));
        QCOMPARE(file.write("audio"), 5);
        file.close();

        VoiceRecorder recorder;
        QSignalSpy recordedSpy(&recorder, &VoiceRecorder::recorded);
        recorder.m_outPath = path;
        recorder.m_recording = true;
        recorder.m_cancelled = false;

        recorder.finishRecording();

        QVERIFY(QFile::exists(path));
        QVERIFY(recorder.m_outPath.isEmpty());
        QCOMPARE(recordedSpy.count(), 1);
        QCOMPARE(recordedSpy.at(0).at(0).toString(), path);
        QVERIFY(QFile::remove(path));
    }

    void interviewSessionCleansManagedMediaAtSessionBoundaryAndDestruction()
    {
        const QString managedDir = QDir::cleanPath(
            QStandardPaths::writableLocation(QStandardPaths::TempLocation)
            + QStringLiteral("/careertuner"));
        QVERIFY(QDir().mkpath(managedDir));
        const QString suffix = QUuid::createUuid().toString(QUuid::WithoutBraces);
        const QString audioPath = managedDir + QStringLiteral("/answer-test-%1.m4a").arg(suffix);
        const QString videoPath = managedDir + QStringLiteral("/video-answer-test-%1.mp4").arg(suffix);
        const QString destructionPath = managedDir
            + QStringLiteral("/video-answer-destruction-%1.mp4").arg(suffix);

        const auto createFile = [](const QString& path) {
            QFile file(path);
            if (!file.open(QIODevice::WriteOnly)) return false;
            return file.write("media") == 5;
        };
        QVERIFY(createFile(audioPath));
        QVERIFY(createFile(videoPath));
        QVERIFY(createFile(destructionPath));

        QTemporaryDir externalDir;
        QVERIFY(externalDir.isValid());
        const QString externalPath = externalDir.filePath(QStringLiteral("answer-external.m4a"));
        QVERIFY(createFile(externalPath));

        ApiClient api;
        SettingsStore store;
        {
            InterviewSession session(&api, &store);
            session.m_audioFiles = {audioPath, externalPath};
            session.m_videoFiles = {videoPath, videoPath};
            session.m_localMediaPathByAnswerKind.insert(QStringLiteral("1:AUDIO"), audioPath);
            session.m_localMediaPathByAnswerKind.insert(QStringLiteral("2:VIDEO"), videoPath);
            session.m_pendingAudioPath = audioPath;
            session.m_pendingAudioQuestionId = 3;

            session.cleanupLocalMediaFiles();

            QVERIFY(!QFile::exists(audioPath));
            QVERIFY(!QFile::exists(videoPath));
            QVERIFY(QFile::exists(externalPath));
            QVERIFY(session.m_audioFiles.isEmpty());
            QVERIFY(session.m_videoFiles.isEmpty());
            QVERIFY(session.m_localMediaPathByAnswerKind.isEmpty());
            QVERIFY(session.m_pendingAudioPath.isEmpty());
            QCOMPARE(session.m_pendingAudioQuestionId, -1LL);

            session.m_videoFiles = {destructionPath};
        }
        QVERIFY(!QFile::exists(destructionPath));
        QVERIFY(QFile::exists(externalPath));
    }

    void sameSessionOpenWhileAiWorkIsPendingPreservesIdempotencyState()
    {
        ApiClient api;
        SettingsStore store;
        InterviewSession session(&api, &store);
        session.m_sessionId = 42;
        session.m_caseId = 7;
        session.m_title = QStringLiteral("원래 제목");
        session.m_sessionGeneration = 9;
        session.m_scoring = true;
        session.m_pendingSubmissionQuestionId = 3;
        session.m_pendingClientSubmissionId = QStringLiteral("stable-submission-id");
        session.m_followUpPendingQuestions.insert(3);

        session.open(42, QStringLiteral("덮어쓴 제목"), QStringLiteral("JOB"), 8);

        QCOMPARE(session.m_sessionGeneration, 9ULL);
        QCOMPARE(session.title(), QStringLiteral("원래 제목"));
        QCOMPARE(session.m_pendingClientSubmissionId, QStringLiteral("stable-submission-id"));
        QVERIFY(session.m_scoring);
        QVERIFY(session.m_followUpPendingQuestions.contains(3));
    }

    void questionGenerationWaitsForInitialReadAndRunsSingleFlight()
    {
        QTcpServer server;
        QVERIFY(server.listen(QHostAddress::LocalHost, 0));
        int questionReads = 0;
        int previews = 0;
        int generations = 0;
        connect(&server, &QTcpServer::newConnection, this, [&]() {
            while (server.hasPendingConnections()) {
                QTcpSocket* socket = server.nextPendingConnection();
                connect(socket, &QTcpSocket::readyRead, socket,
                    [&, socket, request = QByteArray(), handled = false]() mutable {
                        request += socket->readAll();
                        if (handled || !completeHttpRequest(request)) return;
                        handled = true;
                        if (request.startsWith("GET /api/interview/sessions/12/questions ")) {
                            ++questionReads;
                            const QByteArray data = questionReads == 1 ? QByteArrayLiteral("[]")
                                : QByteArrayLiteral(R"([{"id":91,"question":"첫 질문","questionType":"BASIC"}])");
                            writeHttpResponse(socket, 200, "{\"success\":true,\"data\":" + data + "}");
                        } else if (request.startsWith("GET /api/interview/sessions/12/review ")) {
                            writeHttpResponse(socket, 200, R"({"success":true,"data":{"items":[]}})");
                        } else if (request.startsWith("GET /api/interview/sessions/12/progress ")) {
                            writeHttpResponse(socket, 200, R"({"success":true,"data":{"totalQuestions":1,"answeredQuestions":0,"finished":false}})");
                        } else if (request.startsWith("GET /api/interview/sessions/12/agent-steps ")) {
                            writeHttpResponse(socket, 200, R"({"success":true,"data":[]})");
                        } else if (request.startsWith("POST /api/billing/charge-preview ")) {
                            ++previews;
                            const QJsonObject requestBody = QJsonDocument::fromJson(httpBody(request)).object();
                            const QJsonObject preview{
                                {"featureType", "INTERVIEW_QUESTION_GEN"}, {"actionKey", requestBody.value("actionKey")},
                                {"chargeType", "FREE"}, {"sufficient", true}, {"refundPolicyVersion", 1}
                            };
                            writeHttpResponse(socket, 200, QJsonDocument(QJsonObject{
                                {"success", true}, {"data", preview}}).toJson(QJsonDocument::Compact));
                        } else if (request.startsWith("POST /api/interview/sessions/12/generate-questions ")) {
                            ++generations;
                            writeHttpResponse(socket, 200, R"({"success":true,"data":null})");
                        } else {
                            writeHttpResponse(socket, 500, R"({"success":false,"message":"unexpected"})");
                        }
                    });
            }
        });

        ApiClient api;
        api.setBaseUrl(QStringLiteral("http://127.0.0.1:%1").arg(server.serverPort()));
        SettingsStore store;
        AiChargeCoordinator charge(&api);
        InterviewSession session(&api, &store, &charge);
        session.open(12, QStringLiteral("테스트"), QStringLiteral("BASIC"), 1);
        session.generateQuestions();
        session.generateQuestions();

        QTRY_COMPARE_WITH_TIMEOUT(generations, 1, 4000);
        QTRY_COMPARE_WITH_TIMEOUT(session.currentQid(), 91LL, 4000);
        QCOMPARE(previews, 1);
        QCOMPARE(questionReads, 2);
        QVERIFY(!session.loading());
    }

    void invalidatingChargeCoordinatorPreventsLatePaidOperation()
    {
        QTcpServer server;
        QVERIFY(server.listen(QHostAddress::LocalHost, 0));
        QPointer<QTcpSocket> heldPreview;
        QJsonObject previewRequest;
        connect(&server, &QTcpServer::newConnection, this, [&]() {
            while (server.hasPendingConnections()) {
                QTcpSocket* socket = server.nextPendingConnection();
                connect(socket, &QTcpSocket::readyRead, socket,
                    [&, socket, request = QByteArray(), handled = false]() mutable {
                        request += socket->readAll();
                        if (handled || !completeHttpRequest(request)) return;
                        handled = true;
                        heldPreview = socket;
                        previewRequest = QJsonDocument::fromJson(httpBody(request)).object();
                    });
            }
        });
        ApiClient api;
        api.setBaseUrl(QStringLiteral("http://127.0.0.1:%1").arg(server.serverPort()));
        AiChargeCoordinator charge(&api);
        bool operationCalled = false;
        charge.run(QStringLiteral("INTERVIEW_REPORT"),
                   [&](const ApiClient::Headers&) { operationCalled = true; });
        QTRY_VERIFY_WITH_TIMEOUT(!heldPreview.isNull(), 3000);
        charge.invalidate();
        const QJsonObject preview{{"featureType", "INTERVIEW_REPORT"},
            {"actionKey", previewRequest.value("actionKey")}, {"chargeType", "FREE"},
            {"sufficient", true}, {"refundPolicyVersion", 1}};
        writeHttpResponse(heldPreview, 200, QJsonDocument(QJsonObject{
            {"success", true}, {"data", preview}}).toJson(QJsonDocument::Compact));
        QTest::qWait(100);
        QVERIFY(!operationCalled);
    }

    void ambiguousQuestionGenerationReusesActionKeyAfterBoundedReconcile()
    {
        QTcpServer server;
        QVERIFY(server.listen(QHostAddress::LocalHost, 0));
        int generations = 0;
        QList<QString> actionKeys;
        connect(&server, &QTcpServer::newConnection, this, [&]() {
            while (server.hasPendingConnections()) {
                QTcpSocket* socket = server.nextPendingConnection();
                connect(socket, &QTcpSocket::readyRead, socket,
                    [&, socket, request = QByteArray(), handled = false]() mutable {
                        request += socket->readAll();
                        if (handled || !completeHttpRequest(request)) return;
                        handled = true;
                        if (request.startsWith("POST /api/billing/charge-preview ")) {
                            const QJsonObject body = QJsonDocument::fromJson(httpBody(request)).object();
                            actionKeys.push_back(body.value("actionKey").toString());
                            const QJsonObject preview{{"featureType", "INTERVIEW_QUESTION_GEN"},
                                {"actionKey", body.value("actionKey")}, {"chargeType", "FREE"},
                                {"sufficient", true}, {"refundPolicyVersion", 1}};
                            writeHttpResponse(socket, 200, QJsonDocument(QJsonObject{
                                {"success", true}, {"data", preview}}).toJson(QJsonDocument::Compact));
                        } else if (request.startsWith("POST /api/interview/sessions/33/generate-questions ")) {
                            ++generations;
                            if (generations == 1)
                                writeHttpResponse(socket, 500, R"({"success":false,"message":"timeout","data":null})");
                            else
                                writeHttpResponse(socket, 200, R"({"success":true,"data":null})");
                        } else if (request.startsWith("GET /api/interview/sessions/33/questions ")) {
                            const QByteArray data = generations < 2 ? QByteArrayLiteral("[]")
                                : QByteArrayLiteral(R"([{"id":330,"question":"복구 질문","questionType":"BASIC"}])");
                            writeHttpResponse(socket, 200, "{\"success\":true,\"data\":" + data + "}");
                        } else if (request.startsWith("GET /api/interview/sessions/33/review ")) {
                            writeHttpResponse(socket, 200, R"({"success":true,"data":{"items":[]}})");
                        } else if (request.startsWith("GET /api/interview/sessions/33/progress ")) {
                            writeHttpResponse(socket, 200, R"({"success":true,"data":{"totalQuestions":1,"answeredQuestions":0}})");
                        } else if (request.startsWith("GET /api/interview/sessions/33/agent-steps ")) {
                            writeHttpResponse(socket, 200, R"({"success":true,"data":[]})");
                        } else {
                            writeHttpResponse(socket, 500, R"({"success":false,"message":"unexpected"})");
                        }
                    });
            }
        });
        ApiClient api;
        api.setBaseUrl(QStringLiteral("http://127.0.0.1:%1").arg(server.serverPort()));
        SettingsStore store;
        AiChargeCoordinator charge(&api);
        InterviewSession session(&api, &store, &charge);
        session.m_sessionId = 33;
        session.m_sessionGeneration = 1;
        session.generateQuestions();
        QTRY_VERIFY_WITH_TIMEOUT(!session.loading(), 4000);
        QCOMPARE(generations, 1);
        QVERIFY(!session.m_questionGenerationActionKey.isEmpty());
        session.generateQuestions();
        QTRY_COMPARE_WITH_TIMEOUT(generations, 2, 4000);
        QTRY_COMPARE_WITH_TIMEOUT(session.currentQid(), 330LL, 4000);
        QCOMPARE(actionKeys.size(), 2);
        QCOMPARE(actionKeys.at(0), actionKeys.at(1));
        QVERIFY(session.m_questionGenerationActionKey.isEmpty());
    }

    void plannerStopRejectsLateDashboardAndScopesReminderIdsByEnvironment()
    {
        QTcpServer server;
        QVERIFY(server.listen(QHostAddress::LocalHost, 0));
        QPointer<QTcpSocket> heldDashboard;
        connect(&server, &QTcpServer::newConnection, this, [&]() {
            while (server.hasPendingConnections()) {
                QTcpSocket* socket = server.nextPendingConnection();
                connect(socket, &QTcpSocket::readyRead, socket,
                    [&, socket, request = QByteArray(), handled = false]() mutable {
                        request += socket->readAll();
                        if (handled || !completeHttpRequest(request)) return;
                        handled = true;
                        heldDashboard = socket;
                    });
            }
        });
        ApiClient api;
        api.setBaseUrl(QStringLiteral("http://127.0.0.1:%1").arg(server.serverPort()));
        PlannerClient planner(&api);
        planner.setAccountScope(QStringLiteral("https://one.example|user@example.com"));
        const QString firstKey = planner.firedReminderSettingsKey();
        planner.start();
        QTRY_VERIFY_WITH_TIMEOUT(!heldDashboard.isNull(), 3000);
        planner.stop();
        writeHttpResponse(heldDashboard, 200,
            R"({"success":true,"data":{"scheduleItems":[],"memos":[{"id":1,"title":"old","content":"old","overlayVisible":true}]}})");
        QTest::qWait(100);
        QVERIFY(planner.items().isEmpty());
        QVERIFY(!planner.active());
        planner.setAccountScope(QStringLiteral("https://two.example|user@example.com"));
        QVERIFY(firstKey != planner.firedReminderSettingsKey());
    }

    void notificationPollEmitsOnlyNewestDeliverableAndFailsClosedWithoutPreferences()
    {
        QTcpServer server;
        QVERIFY(server.listen(QHostAddress::LocalHost, 0));
        connect(&server, &QTcpServer::newConnection, this, [&]() {
            while (server.hasPendingConnections()) {
                QTcpSocket* socket = server.nextPendingConnection();
                connect(socket, &QTcpSocket::readyRead, socket,
                    [socket, request = QByteArray(), handled = false]() mutable {
                        request += socket->readAll();
                        if (handled || !completeHttpRequest(request)) return;
                        handled = true;
                        if (request.startsWith("GET /api/notifications/unread-count")) {
                            writeHttpResponse(socket, 200, R"({"success":true,"data":2})");
                        } else {
                            writeHttpResponse(socket, 200,
                                R"({"success":true,"data":{"notifications":[{"id":2,"type":"NOTICE","title":"old","read":false},{"id":3,"type":"NOTICE","title":"new","read":false}]}})");
                        }
                    });
            }
        });
        ApiClient api;
        api.setBaseUrl(QStringLiteral("http://127.0.0.1:%1").arg(server.serverPort()));
        NotificationPoller poller(&api);
        QVERIFY(!poller.globalDeliveryEnabled(QStringLiteral("notice")));
        QVERIFY(poller.updatePreferences(QJsonObject{{"pushEnabled", true},
            {"categories", QJsonObject{{"notice", true}}},
            {"rules", QJsonObject{{"NOTICE", QJsonObject{{"enabled", true},
                {"channels", QJsonObject{{"desktopToast", true}, {"desktopTaskbar", true}}}}}}}}));
        poller.m_lastMaxId = 1;
        poller.m_pollGeneration = 4;
        QSignalSpy arrived(&poller, &NotificationPoller::notificationArrived);
        poller.pollNotifications(4);
        QTRY_COMPARE_WITH_TIMEOUT(arrived.count(), 1, 3000);
        QCOMPARE(arrived.first().at(1).toString(), QStringLiteral("new"));
        QCOMPARE(poller.items().size(), 2);
        poller.stop();
        QVERIFY(!poller.globalDeliveryEnabled(QStringLiteral("notice")));
    }

    void consentClearRejectsLateStatusAndRefreshReadsServerTruth()
    {
        QTcpServer server;
        QVERIFY(server.listen(QHostAddress::LocalHost, 0));
        QPointer<QTcpSocket> firstSocket;
        int requests = 0;
        connect(&server, &QTcpServer::newConnection, this, [&]() {
            while (server.hasPendingConnections()) {
                QTcpSocket* socket = server.nextPendingConnection();
                connect(socket, &QTcpSocket::readyRead, socket,
                    [&, socket, request = QByteArray(), handled = false]() mutable {
                        request += socket->readAll();
                        if (handled || !completeHttpRequest(request)) return;
                        handled = true;
                        if (++requests == 1) firstSocket = socket;
                        else writeHttpResponse(socket, 200,
                            R"({"success":true,"data":{"termsAgreed":true,"privacyAgreed":true,"aiDataAgreed":false,"resumeAnalysisAgreed":true,"marketingAgreed":false,"requiredConsentsMissing":false}})");
                    });
            }
        });
        ApiClient api;
        api.setBaseUrl(QStringLiteral("http://127.0.0.1:%1").arg(server.serverPort()));
        ConsentClient consent(&api);
        consent.refresh();
        QTRY_VERIFY_WITH_TIMEOUT(!firstSocket.isNull(), 3000);
        consent.clear();
        writeHttpResponse(firstSocket, 200,
            R"({"success":true,"data":{"termsAgreed":false,"privacyAgreed":false,"requiredConsentsMissing":true}})");
        QTest::qWait(100);
        QVERIFY(!consent.loaded());
        consent.refresh();
        QTRY_VERIFY_WITH_TIMEOUT(consent.loaded(), 3000);
        QVERIFY(!consent.requiredConsentsMissing());
        QVERIFY(consent.resumeAnalysisAgreed());
    }

    void accountClearRejectsLateSessionCreationAndResetsCaseLoading()
    {
        QTcpServer server;
        QVERIFY(server.listen(QHostAddress::LocalHost, 0));
        QPointer<QTcpSocket> heldCreate;
        connect(&server, &QTcpServer::newConnection, this, [&]() {
            while (server.hasPendingConnections()) {
                QTcpSocket* socket = server.nextPendingConnection();
                connect(socket, &QTcpSocket::readyRead, socket,
                    [&, socket, request = QByteArray(), handled = false]() mutable {
                        request += socket->readAll();
                        if (handled || !completeHttpRequest(request)) return;
                        handled = true;
                        heldCreate = socket;
                    });
            }
        });
        ApiClient api;
        api.setBaseUrl(QStringLiteral("http://127.0.0.1:%1").arg(server.serverPort()));
        JobModel jobs;
        jobs.setApi(&api);
        QSignalSpy created(&jobs, &JobModel::sessionCreated);
        jobs.createSession(1, QStringLiteral("BASIC"));
        QTRY_VERIFY_WITH_TIMEOUT(!heldCreate.isNull(), 3000);
        jobs.clear();
        writeHttpResponse(heldCreate, 200, R"({"success":true,"data":{"id":99}})");
        QTest::qWait(100);
        QCOMPARE(created.count(), 0);
        QVERIFY(!jobs.creatingSession());
        QVERIFY(!jobs.casesLoading());
        QVERIFY(jobs.casesError().isEmpty());
    }

    void casePickerDistinguishesFailureFromSuccessfulEmptyStateAndCanRetry()
    {
        QTcpServer server;
        QVERIFY(server.listen(QHostAddress::LocalHost, 0));
        int requests = 0;
        connect(&server, &QTcpServer::newConnection, this, [&]() {
            while (server.hasPendingConnections()) {
                QTcpSocket* socket = server.nextPendingConnection();
                connect(socket, &QTcpSocket::readyRead, socket,
                    [&, socket, request = QByteArray(), handled = false]() mutable {
                        request += socket->readAll();
                        if (handled || !completeHttpRequest(request)) return;
                        handled = true;
                        if (++requests == 1)
                            writeHttpResponse(socket, 500, R"({"success":false,"message":"case failure"})");
                        else
                            writeHttpResponse(socket, 200, R"({"success":true,"data":[]})");
                    });
            }
        });
        ApiClient api;
        api.setBaseUrl(QStringLiteral("http://127.0.0.1:%1").arg(server.serverPort()));
        JobModel jobs;
        jobs.setApi(&api);
        QSignalSpy ready(&jobs, &JobModel::casesReady);
        jobs.loadCases();
        QTRY_VERIFY_WITH_TIMEOUT(!jobs.casesLoading(), 3000);
        QVERIFY(!jobs.casesError().isEmpty());
        QCOMPARE(ready.count(), 0);
        jobs.loadCases();
        QTRY_COMPARE_WITH_TIMEOUT(ready.count(), 1, 3000);
        QVERIFY(!jobs.casesLoading());
        QVERIFY(jobs.casesError().isEmpty());
        QVERIFY(ready.first().first().toList().isEmpty());
    }

    void collaborationClearImmediatelyReleasesInFlightMessageAndAttachments()
    {
        ApiClient api;
        api.setBaseUrl(QStringLiteral("http://127.0.0.1:1"));
        api.setToken(QStringLiteral("old-token"));
        CollaborationClient client(&api);
        client.m_pendingAttachments = {QVariantMap{{"id", 11LL}, {"name", "pending"}}};
        client.m_inFlightMessage.requestId = 5;
        client.m_inFlightMessage.attachmentIds = {11LL};
        client.m_loading = true;
        client.m_activeLoadingRequests = 2;
        client.clear();
        QVERIFY(!client.sendingMessage());
        QVERIFY(client.pendingAttachments().isEmpty());
        QVERIFY(!client.loading());
        QCOMPARE(client.m_activeLoadingRequests, 0);
    }

    void collaborationSearchRejectsLateResultFromPreviousTarget()
    {
        QTcpServer server;
        QVERIFY(server.listen(QHostAddress::LocalHost, 0));
        QPointer<QTcpSocket> oldSearch;
        connect(&server, &QTcpServer::newConnection, this, [&]() {
            while (server.hasPendingConnections()) {
                QTcpSocket* socket = server.nextPendingConnection();
                connect(socket, &QTcpSocket::readyRead, socket,
                    [&, socket, request = QByteArray(), handled = false]() mutable {
                        request += socket->readAll();
                        if (handled || !completeHttpRequest(request)) return;
                        handled = true;
                        if (request.contains("keyword=old")) oldSearch = socket;
                        else writeHttpResponse(socket, 200,
                            R"({"success":true,"data":[{"id":2,"name":"new user"}]})");
                    });
            }
        });
        ApiClient api;
        api.setBaseUrl(QStringLiteral("http://127.0.0.1:%1").arg(server.serverPort()));
        CollaborationClient client(&api);
        client.searchUsers(QStringLiteral("old"));
        QTRY_VERIFY_WITH_TIMEOUT(!oldSearch.isNull(), 3000);
        client.searchUsers(QStringLiteral("new"));
        QTRY_COMPARE_WITH_TIMEOUT(client.searchResults().size(), 1, 3000);
        QCOMPARE(client.searchResults().first().toMap().value("name").toString(), QStringLiteral("new user"));
        writeHttpResponse(oldSearch, 200,
            R"({"success":true,"data":[{"id":1,"name":"old user"}]})");
        QTRY_VERIFY_WITH_TIMEOUT(!client.loading(), 3000);
        QCOMPARE(client.searchResults().first().toMap().value("name").toString(), QStringLiteral("new user"));
    }

    void adClickUsesClickedSnapshotAndConfiguredWebOrigin()
    {
        QVERIFY(qputenv("CAREERTUNER_WEB_APP_URL", QByteArray("https://web.example.test")));
        QTcpServer server;
        QVERIFY(server.listen(QHostAddress::LocalHost, 0));
        QPointer<QTcpSocket> heldClick;
        connect(&server, &QTcpServer::newConnection, this, [&]() {
            while (server.hasPendingConnections()) {
                QTcpSocket* socket = server.nextPendingConnection();
                connect(socket, &QTcpSocket::readyRead, socket,
                    [&, socket, request = QByteArray(), handled = false]() mutable {
                        request += socket->readAll();
                        if (handled || !completeHttpRequest(request)) return;
                        handled = true;
                        heldClick = socket;
                    });
            }
        });
        ApiClient api;
        api.setBaseUrl(QStringLiteral("http://127.0.0.1:%1").arg(server.serverPort()));
        SettingsStore store;
        AdClient ads(&api, &store);
        ads.m_adId = 1;
        ads.m_visible = true;
        ads.m_targetUrl = QStringLiteral("/clicked-a");
        QSignalSpy opened(&ads, &AdClient::targetOpened);
        ads.openTarget();
        QTRY_VERIFY_WITH_TIMEOUT(!heldClick.isNull(), 3000);
        ads.m_adId = 2;
        ads.m_targetUrl = QStringLiteral("/new-b");
        writeHttpResponse(heldClick, 200, R"({"success":true,"data":{}})");
        QTRY_COMPARE_WITH_TIMEOUT(opened.count(), 1, 3000);
        QCOMPARE(opened.first().first().toUrl(), QUrl(QStringLiteral("https://web.example.test/clicked-a")));
        qunsetenv("CAREERTUNER_WEB_APP_URL");
    }

    void communityDetailLoadingWaitsForMatchingPostAndComments()
    {
        QTcpServer server;
        QVERIFY(server.listen(QHostAddress::LocalHost, 0));
        QPointer<QTcpSocket> oldDetail;
        QPointer<QTcpSocket> newComments;
        connect(&server, &QTcpServer::newConnection, this, [&]() {
            while (server.hasPendingConnections()) {
                QTcpSocket* socket = server.nextPendingConnection();
                connect(socket, &QTcpSocket::readyRead, socket,
                    [&, socket, request = QByteArray(), handled = false]() mutable {
                        request += socket->readAll();
                        if (handled || !completeHttpRequest(request)) return;
                        handled = true;
                        if (request.startsWith("GET /api/community/posts/1 HTTP")) oldDetail = socket;
                        else if (request.startsWith("GET /api/community/posts/2/comments")) newComments = socket;
                        else if (request.startsWith("GET /api/community/posts/2 HTTP"))
                            writeHttpResponse(socket, 200, R"({"success":true,"data":{"id":2,"title":"new"}})");
                        else writeHttpResponse(socket, 200, R"({"success":true,"data":[]})");
                    });
            }
        });
        ApiClient api;
        api.setBaseUrl(QStringLiteral("http://127.0.0.1:%1").arg(server.serverPort()));
        CommunityClient community(&api);
        community.openPost(1);
        QTRY_VERIFY_WITH_TIMEOUT(!oldDetail.isNull(), 3000);
        community.openPost(2);
        QTRY_COMPARE_WITH_TIMEOUT(community.currentPost().value("id").toLongLong(), 2LL, 3000);
        QTRY_VERIFY_WITH_TIMEOUT(!newComments.isNull(), 3000);
        QVERIFY(community.loading());
        writeHttpResponse(oldDetail, 200, R"({"success":true,"data":{"id":1,"title":"old"}})");
        QTest::qWait(80);
        QVERIFY(community.loading());
        writeHttpResponse(newComments, 200, R"({"success":true,"data":[]})");
        QTRY_VERIFY_WITH_TIMEOUT(!community.loading(), 3000);
        QCOMPARE(community.currentPost().value("id").toLongLong(), 2LL);
    }

    void exportAllDownloadsAuthenticatedServerMediaFromReview()
    {
        QTcpServer server;
        QVERIFY(server.listen(QHostAddress::LocalHost, 0));
        connect(&server, &QTcpServer::newConnection, this, [&]() {
            while (server.hasPendingConnections()) {
                QTcpSocket* socket = server.nextPendingConnection();
                connect(socket, &QTcpSocket::readyRead, socket,
                    [socket, request = QByteArray(), handled = false]() mutable {
                        request += socket->readAll();
                        if (handled || !completeHttpRequest(request)) return;
                        handled = true;
                        writeHttpResponse(socket, 200, QByteArrayLiteral("server-audio"));
                    });
            }
        });
        QTemporaryDir dir;
        QVERIFY(dir.isValid());
        SettingsStore store;
        store.setSaveDir(dir.path());
        ApiClient api;
        api.setBaseUrl(QStringLiteral("http://127.0.0.1:%1").arg(server.serverPort()));
        api.setToken(QStringLiteral("token"));
        InterviewSession session(&api, &store);
        session.m_sessionId = 5;
        session.m_title = QStringLiteral("서버 원본");
        session.m_review = QVariantMap{{"items", QVariantList{QVariantMap{
            {"answerId", 55LL}, {"audioUrl", "/api/file/55/content"}}}}};
        session.exportAll();
        const QString expected = session.sessionFolder() + QStringLiteral("/답변-55-음성.bin");
        QTRY_VERIFY_WITH_TIMEOUT(QFile::exists(expected), 3000);
        QFile file(expected);
        QVERIFY(file.open(QIODevice::ReadOnly));
        QCOMPARE(file.readAll(), QByteArrayLiteral("server-audio"));
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
