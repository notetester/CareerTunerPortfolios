#include "PlannerOverlayController.h"
#include "SettingsStore.h"

#include <QApplication>
#include <QSettings>
#include <QWindow>

#ifdef Q_OS_WIN
#include <windows.h>
#endif

PlannerOverlayController::PlannerOverlayController(QObject* parent)
    : QObject(parent)
{
    auto settings = SettingsStore::createSettings();
    m_enabled = settings->value(QStringLiteral("plannerOverlay/enabled"), false).toBool();
    m_alwaysOnTop = settings->value(QStringLiteral("plannerOverlay/alwaysOnTop"), false).toBool();
    m_clickThrough = settings->value(QStringLiteral("plannerOverlay/clickThrough"), false).toBool();
    m_overlayOpacity = settings->value(QStringLiteral("plannerOverlay/opacity"), 0.92).toDouble();
}

void PlannerOverlayController::setEnabled(bool enabled)
{
    if (m_enabled == enabled) return;
    m_enabled = enabled;
    persist();
    emit enabledChanged();
}

void PlannerOverlayController::setAlwaysOnTop(bool alwaysOnTop)
{
    if (m_alwaysOnTop == alwaysOnTop) return;
    m_alwaysOnTop = alwaysOnTop;
    persist();
    applyWindowFlags();
    emit alwaysOnTopChanged();
}

void PlannerOverlayController::setClickThrough(bool clickThrough)
{
    if (m_clickThrough == clickThrough) return;
    m_clickThrough = clickThrough;
    persist();
    applyNativeWindowOptions();
    emit clickThroughChanged();
}

void PlannerOverlayController::setOverlayOpacity(double overlayOpacity)
{
    const double bounded = qBound(0.25, overlayOpacity, 1.0);
    if (qFuzzyCompare(m_overlayOpacity, bounded)) return;
    m_overlayOpacity = bounded;
    persist();
    if (m_window) m_window->setOpacity(m_overlayOpacity);
    applyNativeWindowOptions();
    emit overlayOpacityChanged();
}

void PlannerOverlayController::attach(QObject* windowObject)
{
    auto* window = qobject_cast<QWindow*>(windowObject);
    if (!window) return;
    m_window = window;
    applyWindowFlags();
    if (m_window) m_window->setOpacity(m_overlayOpacity);
    applyNativeWindowOptions();
}

void PlannerOverlayController::playReminderSound()
{
    QApplication::beep();
}

void PlannerOverlayController::flashTaskbar()
{
    if (m_window) m_window->alert(6000);
}

void PlannerOverlayController::persist() const
{
    auto settings = SettingsStore::createSettings();
    settings->setValue(QStringLiteral("plannerOverlay/enabled"), m_enabled);
    settings->setValue(QStringLiteral("plannerOverlay/alwaysOnTop"), m_alwaysOnTop);
    settings->setValue(QStringLiteral("plannerOverlay/clickThrough"), m_clickThrough);
    settings->setValue(QStringLiteral("plannerOverlay/opacity"), m_overlayOpacity);
}

void PlannerOverlayController::applyWindowFlags()
{
    if (!m_window) return;
    const bool wasVisible = m_window->isVisible();
    Qt::WindowFlags flags = Qt::Tool | Qt::FramelessWindowHint;
    if (m_alwaysOnTop) {
        flags |= Qt::WindowStaysOnTopHint;
    }
    m_window->setFlags(flags);
    if (wasVisible) m_window->show();
}

void PlannerOverlayController::applyNativeWindowOptions()
{
    if (!m_window) return;
#ifdef Q_OS_WIN
    HWND hwnd = reinterpret_cast<HWND>(m_window->winId());
    if (!hwnd) return;
    LONG_PTR exStyle = GetWindowLongPtr(hwnd, GWL_EXSTYLE);
    exStyle |= WS_EX_LAYERED | WS_EX_TOOLWINDOW;
    if (m_clickThrough) {
        exStyle |= WS_EX_TRANSPARENT;
    } else {
        exStyle &= ~WS_EX_TRANSPARENT;
    }
    SetWindowLongPtr(hwnd, GWL_EXSTYLE, exStyle);
    const BYTE alpha = static_cast<BYTE>(qBound(0, static_cast<int>(m_overlayOpacity * 255.0), 255));
    SetLayeredWindowAttributes(hwnd, 0, alpha, LWA_ALPHA);
#endif
}
