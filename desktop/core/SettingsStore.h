#pragma once
#include <QObject>
#include <QSettings>
#include <QString>

// 앱 로컬 영속 설정(QSettings 래퍼).
// - 토큰(자동로그인) · 서버 주소 · 저장 폴더 · 자동 저장 · 트레이 알림을 재시작 후에도 유지한다.
// - Windows 에서는 HKCU\Software\CareerTuner\Desktop 에 저장된다.
class SettingsStore : public QObject
{
    Q_OBJECT
    Q_PROPERTY(QString baseUrl    READ baseUrl    WRITE setBaseUrl    NOTIFY changed)
    Q_PROPERTY(QString saveDir    READ saveDir    WRITE setSaveDir    NOTIFY changed)
    Q_PROPERTY(bool    autoSave   READ autoSave   WRITE setAutoSave   NOTIFY changed)
    Q_PROPERTY(bool    autoLogin  READ autoLogin  WRITE setAutoLogin  NOTIFY changed)
    Q_PROPERTY(bool    trayNotify READ trayNotify WRITE setTrayNotify NOTIFY changed)
    // 서버 주소 프리셋 (설정 화면 콤보박스용 — 값은 아래 static 상수의 단일 소스)
    Q_PROPERTY(QString localServerUrl     READ localServerUrl     CONSTANT)
    Q_PROPERTY(QString tailscaleServerUrl READ tailscaleServerUrl CONSTANT)
public:
    explicit SettingsStore(QObject* parent = nullptr);

    // 기본 서버 주소의 단일 소스 — ApiClient 기본값·설정 화면 프리셋이 전부 여기를 쓴다
    static QString defaultBaseUrl() { return QStringLiteral("https://careertuner-dev.example.invalid"); }  // 팀 공용(Tailscale)
    static QString localBaseUrl()   { return QStringLiteral("http://localhost:8080"); }                 // 로컬 시연용

    QString localServerUrl() const     { return localBaseUrl(); }
    QString tailscaleServerUrl() const { return defaultBaseUrl(); }

    QString baseUrl() const;
    void setBaseUrl(const QString& v);

    QString saveDir() const;          // 기본: 문서\CareerTuner
    void setSaveDir(const QString& v);

    bool autoSave() const;
    void setAutoSave(bool v);

    bool autoLogin() const;
    void setAutoLogin(bool v);

    bool trayNotify() const;
    void setTrayNotify(bool v);

    // 토큰은 QML 에 노출하지 않고 C++ (AuthService) 만 접근
    QString accessToken() const;
    QString refreshToken() const;
    void setTokens(const QString& access, const QString& refresh);
    void clearTokens();

    Q_INVOKABLE QString pickSaveDir(); // 폴더 선택 다이얼로그 → 선택 시 저장까지

signals:
    void changed();

private:
    QSettings m_s;
};
