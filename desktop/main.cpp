#include <QApplication>
#include <QQmlApplicationEngine>
#include <QQmlContext>
#include <QSystemTrayIcon>
#include <QMenu>
#include <QQuickStyle>
#include <QPainter>
#include <QPixmap>
#include <QIcon>

#include "core/ApiClient.h"
#include "core/AuthService.h"
#include "core/SettingsStore.h"
#include "core/SseClient.h"
#include "core/JobModel.h"
#include "core/InterviewSession.h"
#include "core/VoiceRecorder.h"
#include "core/NotificationPoller.h"
#include "core/AutoPrepRunner.h"
#include "core/CollaborationClient.h"

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
    NotificationPoller poller(&api);
    AutoPrepRunner    autoprep(&api);
    CollaborationClient collaboration(&api);

    // 설정에서 서버 주소를 바꾸면 즉시 반영
    QObject::connect(&settings, &SettingsStore::changed, &api,
        [&api, &settings]() { api.setBaseUrl(settings.baseUrl()); });

    // 로그인 성공 → 알림 폴링 시작, 로그아웃 → 중지
    QObject::connect(&auth, &AuthService::loggedIn, &poller,
        [&poller](const QString&) { poller.start(); });
    QObject::connect(&auth, &AuthService::loggedIn, &collaboration,
        [&collaboration](const QString&) { collaboration.refresh(); });
    QObject::connect(&auth, &AuthService::loggedOut, &poller,
        [&poller]() { poller.stop(); });
    QObject::connect(&auth, &AuthService::loggedOut, &collaboration,
        [&collaboration]() { collaboration.clear(); });

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
    ctx->setContextProperty("notifications", &poller);
    ctx->setContextProperty("autoprep", &autoprep);
    ctx->setContextProperty("collaboration", &collaboration);

    engine.loadFromModule("CareerTuner", "Main");
    if (engine.rootObjects().isEmpty())
        return -1;

    // ── 트레이 (창 닫아도 알림·작업 계속) ──
    QSystemTrayIcon tray;
    tray.setIcon(makeAppIcon());
    tray.setToolTip("CareerTuner — 면접 준비 컨트롤 센터");
    QMenu trayMenu;
    QAction* showAct = trayMenu.addAction("열기");
    QAction* quitAct = trayMenu.addAction("종료");
    const auto showWindow = [&engine]() {
        if (!engine.rootObjects().isEmpty()) {
            QObject* win = engine.rootObjects().first();
            win->setProperty("visible", true);
            QMetaObject::invokeMethod(win, "requestActivate", Qt::DirectConnection);
        }
    };
    QObject::connect(showAct, &QAction::triggered, showWindow);
    QObject::connect(quitAct, &QAction::triggered, &app, &QApplication::quit);
    QObject::connect(&tray, &QSystemTrayIcon::activated, &app,
        [showWindow](QSystemTrayIcon::ActivationReason r) {
            if (r == QSystemTrayIcon::Trigger || r == QSystemTrayIcon::DoubleClick)
                showWindow();
        });
    QObject::connect(&tray, &QSystemTrayIcon::messageClicked, &app, showWindow);
    tray.setContextMenu(&trayMenu);
    tray.show();

    // 새 알림 → Windows 트레이 토스트 (설정에서 끌 수 있음)
    QObject::connect(&poller, &NotificationPoller::notificationArrived, &tray,
        [&tray, &settings](const QString&, const QString& title,
                           const QString& message, const QString&, qint64) {
            if (settings.trayNotify())
                tray.showMessage(title, message, QSystemTrayIcon::Information, 6000);
        });

    // 보관된 refresh 토큰으로 자동 로그인 시도 (실패 시 QML 이 로그인 화면 유지)
    auth.tryAutoLogin();

    return app.exec();
}
