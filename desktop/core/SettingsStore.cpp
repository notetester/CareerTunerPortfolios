#include "SettingsStore.h"
#include <QCoreApplication>
#include <QStandardPaths>
#include <QFileDialog>
#include <QDir>
#include <QFile>

SettingsStore::SettingsStore(QObject* parent)
    : QObject(parent)
    , m_s(createSettings())
{
}

QString SettingsStore::portableDataDir()
{
    static const QString dir = []() {
        const QStringList args = QCoreApplication::arguments();
        for (int i = 1; i < args.size(); ++i) {
            const QString arg = args.at(i);
            if (arg == QStringLiteral("--portable-data-dir") && i + 1 < args.size()) {
                return QDir::fromNativeSeparators(args.at(i + 1));
            }
            if (arg.startsWith(QStringLiteral("--portable-data-dir="))) {
                return QDir::fromNativeSeparators(arg.mid(QStringLiteral("--portable-data-dir=").size()));
            }
        }

        const QString envDir = qEnvironmentVariable("CAREERTUNER_PORTABLE_DATA_DIR");
        if (!envDir.isEmpty()) {
            return QDir::fromNativeSeparators(envDir);
        }

        if (args.contains(QStringLiteral("--portable"))) {
            return QDir(QCoreApplication::applicationDirPath())
                .filePath(QStringLiteral("CareerTunerDesktopData"));
        }

        return QString();
    }();
    return dir;
}

QString SettingsStore::settingsPathForCurrentMode()
{
    const QString dataDir = portableDataDir();
    if (!dataDir.isEmpty()) {
        return QDir(dataDir).filePath(QStringLiteral("settings.ini"));
    }
#ifdef Q_OS_WIN
    return QStringLiteral("HKCU\\Software\\CareerTuner\\Desktop");
#else
    return QStringLiteral("CareerTuner/Desktop");
#endif
}

std::unique_ptr<QSettings> SettingsStore::createSettings()
{
    const QString dataDir = portableDataDir();
    if (!dataDir.isEmpty()) {
        QDir().mkpath(dataDir);
        const QString settingsPath = QDir(dataDir).filePath(QStringLiteral("settings.ini"));
        if (!QFile::exists(settingsPath)) {
            QFile file(settingsPath);
            if (file.open(QIODevice::WriteOnly | QIODevice::Text)) {
                file.write("[portable]\nenabled=true\n");
            }
        }
        return std::make_unique<QSettings>(settingsPath, QSettings::IniFormat);
    }
    return std::make_unique<QSettings>(QStringLiteral("CareerTuner"), QStringLiteral("Desktop"));
}

QString SettingsStore::baseUrl() const
{
    // 기본은 공개 AWS 통합 백엔드 — 기본값 상수는 defaultBaseUrl() 한 곳에서 관리
    return m_s->value("server/baseUrl", defaultBaseUrl()).toString();
}
void SettingsStore::setBaseUrl(const QString& v)
{
    if (v == baseUrl()) return;
    m_s->setValue("server/baseUrl", v);
    emit changed();
}

QString SettingsStore::saveDir() const
{
    const QString dataDir = portableDataDir();
    const QString def = dataDir.isEmpty()
        ? QStandardPaths::writableLocation(QStandardPaths::DocumentsLocation) + QStringLiteral("/CareerTuner")
        : QDir(dataDir).filePath(QStringLiteral("Documents"));
    return m_s->value("local/saveDir", def).toString();
}
void SettingsStore::setSaveDir(const QString& v)
{
    if (v == saveDir()) return;
    m_s->setValue("local/saveDir", v);
    emit changed();
}

bool SettingsStore::autoSave() const { return m_s->value("local/autoSave", true).toBool(); }
void SettingsStore::setAutoSave(bool v)
{
    if (v == autoSave()) return;
    m_s->setValue("local/autoSave", v);
    emit changed();
}

bool SettingsStore::autoLogin() const { return m_s->value("auth/autoLogin", true).toBool(); }
void SettingsStore::setAutoLogin(bool v)
{
    if (v == autoLogin()) return;
    m_s->setValue("auth/autoLogin", v);
    if (!v) clearTokens(); // 자동 로그인 끄면 보관 토큰도 제거
    emit changed();
}

bool SettingsStore::trayNotify() const { return m_s->value("notify/tray", true).toBool(); }
void SettingsStore::setTrayNotify(bool v)
{
    if (v == trayNotify()) return;
    m_s->setValue("notify/tray", v);
    emit changed();
}

bool SettingsStore::darkTheme() const { return m_s->value("display/darkTheme", true).toBool(); }
void SettingsStore::setDarkTheme(bool v)
{
    if (v == darkTheme()) return;
    m_s->setValue("display/darkTheme", v);
    emit changed();
}

QString SettingsStore::accessToken() const  { return m_s->value("auth/accessToken").toString(); }
QString SettingsStore::refreshToken() const { return m_s->value("auth/refreshToken").toString(); }

void SettingsStore::setTokens(const QString& access, const QString& refresh)
{
    m_s->setValue("auth/accessToken", access);
    if (!refresh.isEmpty())
        m_s->setValue("auth/refreshToken", refresh);
}

void SettingsStore::clearTokens()
{
    m_s->remove("auth/accessToken");
    m_s->remove("auth/refreshToken");
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
