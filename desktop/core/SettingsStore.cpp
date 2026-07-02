#include "SettingsStore.h"
#include <QStandardPaths>
#include <QFileDialog>
#include <QDir>

SettingsStore::SettingsStore(QObject* parent)
    : QObject(parent)
    , m_s(QStringLiteral("CareerTuner"), QStringLiteral("Desktop"))
{
}

QString SettingsStore::baseUrl() const
{
    // 기본은 팀 공용 원격 백엔드(Tailscale) — 기존 앱 기본값 유지
    return m_s.value("server/baseUrl", QStringLiteral("https://careertuner-dev.example.invalid")).toString();
}
void SettingsStore::setBaseUrl(const QString& v)
{
    if (v == baseUrl()) return;
    m_s.setValue("server/baseUrl", v);
    emit changed();
}

QString SettingsStore::saveDir() const
{
    const QString def = QStandardPaths::writableLocation(QStandardPaths::DocumentsLocation)
                        + QStringLiteral("/CareerTuner");
    return m_s.value("local/saveDir", def).toString();
}
void SettingsStore::setSaveDir(const QString& v)
{
    if (v == saveDir()) return;
    m_s.setValue("local/saveDir", v);
    emit changed();
}

bool SettingsStore::autoSave() const { return m_s.value("local/autoSave", true).toBool(); }
void SettingsStore::setAutoSave(bool v)
{
    if (v == autoSave()) return;
    m_s.setValue("local/autoSave", v);
    emit changed();
}

bool SettingsStore::autoLogin() const { return m_s.value("auth/autoLogin", true).toBool(); }
void SettingsStore::setAutoLogin(bool v)
{
    if (v == autoLogin()) return;
    m_s.setValue("auth/autoLogin", v);
    if (!v) clearTokens(); // 자동 로그인 끄면 보관 토큰도 제거
    emit changed();
}

bool SettingsStore::trayNotify() const { return m_s.value("notify/tray", true).toBool(); }
void SettingsStore::setTrayNotify(bool v)
{
    if (v == trayNotify()) return;
    m_s.setValue("notify/tray", v);
    emit changed();
}

QString SettingsStore::accessToken() const  { return m_s.value("auth/accessToken").toString(); }
QString SettingsStore::refreshToken() const { return m_s.value("auth/refreshToken").toString(); }

void SettingsStore::setTokens(const QString& access, const QString& refresh)
{
    m_s.setValue("auth/accessToken", access);
    if (!refresh.isEmpty())
        m_s.setValue("auth/refreshToken", refresh);
}

void SettingsStore::clearTokens()
{
    m_s.remove("auth/accessToken");
    m_s.remove("auth/refreshToken");
}

QString SettingsStore::pickSaveDir()
{
    const QString dir = QFileDialog::getExistingDirectory(
        nullptr, QStringLiteral("저장 폴더 선택"), saveDir());
    if (!dir.isEmpty()) {
        QDir().mkpath(dir);
        setSaveDir(dir);
    }
    return saveDir();
}
