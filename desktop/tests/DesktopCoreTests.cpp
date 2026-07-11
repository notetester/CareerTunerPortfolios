#include "ApiClient.h"
#include "CameraRecorder.h"
#include "CollaborationClient.h"
#include "InterviewSession.h"
#include "JobModel.h"
#include "PlannerOverlayController.h"
#include "SettingsStore.h"
#include "VoiceRecorder.h"

#include <QDir>
#include <QEventLoop>
#include <QFile>
#include <QHostAddress>
#include <QSignalSpy>
#include <QTemporaryDir>
#include <QTcpServer>
#include <QTcpSocket>
#include <QTimer>
#include <QStandardPaths>
#include <QtTest>
#include <QUuid>

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
