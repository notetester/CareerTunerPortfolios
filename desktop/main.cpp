#include <QApplication>
#include <QQmlApplicationEngine>
#include <QQmlContext>
#include <QSystemTrayIcon>
#include <QMenu>

#include "core/ApiClient.h"
#include "core/AuthService.h"
#include "core/SseClient.h"
#include "core/JobModel.h"

// CareerTuner 데스크탑 진입점.
// - C++ 코어 객체(api/auth/sse/jobModel)를 QML 화면에 노출한다.
// - 창을 닫아도 트레이에 상주해 작업(SSE)이 계속 돌게 한다.
int main(int argc, char* argv[])
{
    // 트레이(QSystemTrayIcon)를 쓰므로 QGuiApplication 이 아니라 QApplication.
    QApplication app(argc, argv);
    app.setApplicationName("CareerTuner Desktop");
    app.setOrganizationName("CareerTuner");
    app.setQuitOnLastWindowClosed(false); // 창 닫아도 종료 안 함 → 트레이 상주

    // ── 엔진(C++ 코어) ──
    ApiClient   api;
    AuthService auth(&api);
    SseClient   sse;
    JobModel    jobs;

    // ── QML 화면에 코어 노출 ──
    QQmlApplicationEngine engine;
    QQmlContext* ctx = engine.rootContext();
    ctx->setContextProperty("api", &api);
    ctx->setContextProperty("auth", &auth);
    ctx->setContextProperty("sse", &sse);
    ctx->setContextProperty("jobModel", &jobs);

    engine.loadFromModule("CareerTuner", "Main");
    if (engine.rootObjects().isEmpty())
        return -1;

    // ── 트레이 (창 닫아도 작업 계속) ──
    // TODO: 아이콘 리소스(:/icon.png) 추가 — 현재는 시스템 기본.
    QSystemTrayIcon tray;
    tray.setToolTip("CareerTuner — 면접 준비 컨트롤 센터");
    QMenu trayMenu;
    QAction* showAct = trayMenu.addAction("열기");
    QAction* quitAct = trayMenu.addAction("종료");
    QObject::connect(showAct, &QAction::triggered, [&engine]() {
        if (!engine.rootObjects().isEmpty())
            engine.rootObjects().first()->setProperty("visible", true);
    });
    QObject::connect(quitAct, &QAction::triggered, &app, &QApplication::quit);
    tray.setContextMenu(&trayMenu);
    tray.show();

    return app.exec();
}
