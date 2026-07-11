#include <QApplication>
#include <QQmlApplicationEngine>
#include <QQmlContext>
#include <QSystemTrayIcon>
#include <QMenu>
#include <QQuickStyle>
#include <QWindow>
#include <QPainter>
#include <QPixmap>
#include <QIcon>
#include <QVariant>

#include "core/ApiClient.h"
#include "core/AuthService.h"
#include "core/SettingsStore.h"
#include "core/SseClient.h"
#include "core/JobModel.h"
#include "core/InterviewSession.h"
#include "core/VoiceRecorder.h"
#include "core/NotificationPoller.h"
#include "core/PlannerClient.h"
#include "core/PlannerOverlayController.h"
#include "core/AutoPrepRunner.h"
#include "core/AdClient.h"
#include "core/CollaborationClient.h"
#include "core/CommunityClient.h"
#include "core/CameraRecorder.h"

// 앱 아이콘: 리소스 파일 없이 런타임에 그림 (인디고 라운드 + C)
static QIcon makeAppIcon()
{
    QPixmap pm(64, 64);
    pm.fill(Qt::transparent);
    QPainter p(&pm);
    p.setRenderHint(QPainter::Antialiasing);
    p.setBrush(QColor("#5e6ad2"));
    p.setPen(Qt::NoPen);
    p.drawRoundedRect(0, 0, 64, 64, 16, 16);
    p.setPen(Qt::white);
    QFont f = p.font();
    f.setBold(true);
    f.setPixelSize(38);
    p.setFont(f);
    p.drawText(pm.rect(), Qt::AlignCenter, "C");
    p.end();
    return QIcon(pm);
}

// CareerTuner 데스크탑 진입점.
// - C++ 코어 객체를 QML 화면에 노출한다.
// - 창을 닫아도 트레이에 상주해 알림 폴링·작업 스트림이 계속 돌게 한다.
int main(int argc, char* argv[])
{
    // 트레이(QSystemTrayIcon)를 쓰므로 QGuiApplication 이 아니라 QApplication.
    QApplication app(argc, argv);
    app.setApplicationName("CareerTuner Desktop");
    app.setOrganizationName("CareerTuner");
    app.setQuitOnLastWindowClosed(false); // 창 닫아도 종료 안 함 → 트레이 상주
    app.setWindowIcon(makeAppIcon());

    // 네이티브 스타일은 Controls 커스터마이징을 막음 → Fusion 으로 다크 테마 허용
    QQuickStyle::setStyle("Fusion");

    // ── 엔진(C++ 코어) ──
    SettingsStore     settings;
    ApiClient         api;
    api.setBaseUrl(settings.baseUrl());              // 영속화된 서버 주소 반영
    AuthService       auth(&api, &settings);
    SseClient         sse;
    JobModel          jobs;
    jobs.setApi(&api);
    InterviewSession  session(&api, &settings);
    VoiceRecorder     recorder;
    CameraRecorder    cameraRecorder;
    NotificationPoller poller(&api);
    PlannerClient     planner(&api);
    PlannerOverlayController plannerOverlayController;
    AutoPrepRunner    autoprep(&api);
    AdClient          ads(&api);
    CollaborationClient collaboration(&api);
    CommunityClient   community(&api);

    // 설정에서 서버 주소를 바꾸면 즉시 반영
    QObject::connect(&settings, &SettingsStore::changed, &api,
        [&api, &settings]() { api.setBaseUrl(settings.baseUrl()); });
    QObject::connect(&settings, &SettingsStore::changed, &ads,
        [&ads]() { ads.refresh(); });

    // 로그인 성공 → 알림 폴링 시작, 로그아웃 → 중지
    QObject::connect(&auth, &AuthService::loggedIn, &poller,
        [&poller](const QString&) { poller.start(); });
    QObject::connect(&auth, &AuthService::loggedIn, &planner,
        [&planner](const QString&) { planner.start(); });
    QObject::connect(&auth, &AuthService::loggedIn, &collaboration,
        [&collaboration](const QString&) { collaboration.refresh(); });
    QObject::connect(&auth, &AuthService::loggedIn, &ads,
        [&ads](const QString&) { ads.refresh(); });
    QObject::connect(&auth, &AuthService::aboutToLogout, &collaboration,
        [&collaboration]() { collaboration.clear(); });
    QObject::connect(&auth, &AuthService::loggedOut, &poller,
        [&poller]() { poller.stop(); });
    QObject::connect(&auth, &AuthService::loggedOut, &planner,
        [&planner]() { planner.stop(); });
    QObject::connect(&auth, &AuthService::loggedOut, &collaboration,
        [&collaboration]() { collaboration.clear(); });
    QObject::connect(&auth, &AuthService::loggedOut, &community,
        [&community]() { community.clear(); });
    QObject::connect(&auth, &AuthService::loggedOut, &ads,
        [&ads]() { ads.clear(); });

    // ── QML 화면에 코어 노출 ──
    QQmlApplicationEngine engine;
    QQmlContext* ctx = engine.rootContext();
    ctx->setContextProperty("api", &api);
    ctx->setContextProperty("auth", &auth);
    ctx->setContextProperty("appSettings", &settings);
    ctx->setContextProperty("sse", &sse);
    ctx->setContextProperty("jobModel", &jobs);
    ctx->setContextProperty("session", &session);
    ctx->setContextProperty("recorder", &recorder);
    ctx->setContextProperty("cameraRecorder", &cameraRecorder);
    ctx->setContextProperty("notifications", &poller);
    ctx->setContextProperty("plannerClient", &planner);
    ctx->setContextProperty("plannerOverlayController", &plannerOverlayController);
    ctx->setContextProperty("autoprep", &autoprep);
    ctx->setContextProperty("desktopAds", &ads);
    ctx->setContextProperty("collaboration", &collaboration);
    ctx->setContextProperty("community", &community);

    engine.loadFromModule("CareerTuner", "Main");
    if (engine.rootObjects().isEmpty())
        return -1;

    // ── 트레이 (창 닫아도 알림·작업 계속) ──
    QSystemTrayIcon tray;
    tray.setIcon(makeAppIcon());
    tray.setToolTip("CareerTuner — 면접 준비 컨트롤 센터");
    QMenu trayMenu;
    QAction* showAct = trayMenu.addAction("열기");
    QAction* plannerOverlayAct = trayMenu.addAction("플래너 오버레이 켜기");
    QAction* plannerClickAct = trayMenu.addAction("플래너 클릭 통과 해제");
    QAction* quitAct = trayMenu.addAction("종료");
    QString trayNotificationType;
    QString trayNotificationLink;
    QString trayNotificationTargetType;
    qint64 trayNotificationTargetId = 0;
    const auto showWindow = [&engine]() {
        if (!engine.rootObjects().isEmpty()) {
            QObject* win = engine.rootObjects().first();
            win->setProperty("visible", true);
            QMetaObject::invokeMethod(win, "requestActivate", Qt::DirectConnection);
        }
    };
    const auto activateNotification = [&engine, showWindow](
            const QString& type, const QString& link,
            const QString& targetType, qint64 targetId) {
        showWindow();
        if (engine.rootObjects().isEmpty()) return;
        QObject* win = engine.rootObjects().first();
        QMetaObject::invokeMethod(
            win, "activateNotification", Qt::QueuedConnection,
            Q_ARG(QVariant, QVariant(type)),
            Q_ARG(QVariant, QVariant(link)),
            Q_ARG(QVariant, QVariant(targetType)),
            Q_ARG(QVariant, QVariant::fromValue(targetId)));
    };
    QObject::connect(showAct, &QAction::triggered, showWindow);
    QObject::connect(plannerOverlayAct, &QAction::triggered, &plannerOverlayController,
        [&plannerOverlayController]() { plannerOverlayController.setEnabled(true); });
    QObject::connect(plannerClickAct, &QAction::triggered, &plannerOverlayController,
        [&plannerOverlayController]() { plannerOverlayController.setClickThrough(false); });
    QObject::connect(quitAct, &QAction::triggered, &app, &QApplication::quit);
    QObject::connect(&tray, &QSystemTrayIcon::activated, &app,
        [showWindow](QSystemTrayIcon::ActivationReason r) {
            if (r == QSystemTrayIcon::Trigger || r == QSystemTrayIcon::DoubleClick)
                showWindow();
        });
    QObject::connect(&tray, &QSystemTrayIcon::messageClicked, &app,
        [showWindow, activateNotification,
         &trayNotificationType, &trayNotificationLink,
         &trayNotificationTargetType, &trayNotificationTargetId]() {
            if (!trayNotificationLink.isEmpty() || !trayNotificationTargetType.isEmpty()) {
                activateNotification(trayNotificationType, trayNotificationLink,
                                     trayNotificationTargetType, trayNotificationTargetId);
            } else {
                showWindow();
            }
            trayNotificationType.clear();
            trayNotificationLink.clear();
            trayNotificationTargetType.clear();
            trayNotificationTargetId = 0;
        });
    tray.setContextMenu(&trayMenu);
    tray.show();

    // 새 알림 → Windows 트레이 토스트 / 작업표시줄 attention (설정에서 각각 끌 수 있음)
    QObject::connect(&poller, &NotificationPoller::notificationArrived, &tray,
        [&tray, &settings, &engine,
         &trayNotificationType, &trayNotificationLink,
         &trayNotificationTargetType, &trayNotificationTargetId](
                           const QString& type, const QString& title,
                           const QString& message, const QString& link,
                           const QString& targetType, qint64 targetId,
                           bool desktopToast, bool desktopTaskbar) {
            if (desktopToast && settings.trayNotify()) {
                trayNotificationType = type;
                trayNotificationLink = link;
                trayNotificationTargetType = targetType;
                trayNotificationTargetId = targetId;
                tray.showMessage(title, message, QSystemTrayIcon::Information, 6000);
            }
            if (desktopTaskbar && !engine.rootObjects().isEmpty()) {
                if (auto* window = qobject_cast<QWindow*>(engine.rootObjects().first())) {
                    window->alert(6000);
                }
            }
        });
    QObject::connect(&planner, &PlannerClient::reminderArrived, &tray,
        [&tray, &settings, &engine, &plannerOverlayController,
         &trayNotificationType, &trayNotificationLink,
         &trayNotificationTargetType, &trayNotificationTargetId](
            const QString& title, const QString& message,
            bool desktopToast, bool desktopTaskbar, bool desktopSound) {
            if (desktopToast && settings.trayNotify()) {
                // 일정 알림 클릭이 직전 일반 알림의 대상을 잘못 여는 일을 막는다.
                trayNotificationType.clear();
                trayNotificationLink.clear();
                trayNotificationTargetType.clear();
                trayNotificationTargetId = 0;
                tray.showMessage(QStringLiteral("일정 알림: %1").arg(title), message, QSystemTrayIcon::Information, 6000);
            }
            if (desktopTaskbar && !engine.rootObjects().isEmpty()) {
                if (auto* window = qobject_cast<QWindow*>(engine.rootObjects().first())) {
                    window->alert(6000);
                }
            }
            if (desktopSound)
                plannerOverlayController.playReminderSound();
        });

    // 보관된 refresh 토큰으로 자동 로그인 시도 (실패 시 QML 이 로그인 화면 유지)
    auth.tryAutoLogin();

    return app.exec();
}
