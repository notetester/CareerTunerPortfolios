#pragma once

#include <QObject>
#include <QPointer>

class QWindow;

class PlannerOverlayController : public QObject
{
    Q_OBJECT
    Q_PROPERTY(bool enabled READ enabled WRITE setEnabled NOTIFY enabledChanged)
    Q_PROPERTY(bool alwaysOnTop READ alwaysOnTop WRITE setAlwaysOnTop NOTIFY alwaysOnTopChanged)
    Q_PROPERTY(bool clickThrough READ clickThrough WRITE setClickThrough NOTIFY clickThroughChanged)
    Q_PROPERTY(double overlayOpacity READ overlayOpacity WRITE setOverlayOpacity NOTIFY overlayOpacityChanged)
public:
    explicit PlannerOverlayController(QObject* parent = nullptr);

    bool enabled() const { return m_enabled; }
    void setEnabled(bool enabled);

    bool alwaysOnTop() const { return m_alwaysOnTop; }
    void setAlwaysOnTop(bool alwaysOnTop);

    bool clickThrough() const { return m_clickThrough; }
    void setClickThrough(bool clickThrough);

    double overlayOpacity() const { return m_overlayOpacity; }
    void setOverlayOpacity(double overlayOpacity);

    Q_INVOKABLE void attach(QObject* windowObject);
    Q_INVOKABLE void playReminderSound();
    Q_INVOKABLE void flashTaskbar();

signals:
    void enabledChanged();
    void alwaysOnTopChanged();
    void clickThroughChanged();
    void overlayOpacityChanged();

private:
    void persist() const;
    void applyWindowFlags();
    void applyNativeWindowOptions();

    bool m_enabled = true;
    bool m_alwaysOnTop = true;
    bool m_clickThrough = false;
    double m_overlayOpacity = 0.92;
    QPointer<QWindow> m_window;
};
