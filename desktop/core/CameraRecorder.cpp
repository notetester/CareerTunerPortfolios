#include "CameraRecorder.h"
#include <QStandardPaths>
#include <QDateTime>
#include <QDir>
#include <QFile>
#include <QFileInfo>
#include <QMediaFormat>
#include <QUrl>
#include <QVideoSink>

CameraRecorder::CameraRecorder(QObject* parent) : QObject(parent)
{
    m_session.setCamera(&m_camera);
    m_session.setAudioInput(&m_audioInput);
    m_session.setRecorder(&m_recorder);

    // 서버(avatar-score → serve)가 ffmpeg/MediaPipe 로 읽는 일반 포맷 — H.264 + AAC mp4
    QMediaFormat fmt;
    fmt.setFileFormat(QMediaFormat::MPEG4);
    fmt.setVideoCodec(QMediaFormat::VideoCodec::H264);
    fmt.setAudioCodec(QMediaFormat::AudioCodec::AAC);
    m_recorder.setMediaFormat(fmt);
    m_recorder.setQuality(QMediaRecorder::NormalQuality); // base64 전송 크기 고려

    connect(&m_recorder, &QMediaRecorder::durationChanged, this, [this](qint64 ms) {
        const int s = static_cast<int>(ms / 1000);
        if (s != m_seconds) { m_seconds = s; emit secondsChanged(); }
        if (m_recording && s >= kMaxSeconds)
            m_recorder.stop(); // 최대 3분 — 자동 종료 후 recorded() 발행
    });
    connect(&m_recorder, &QMediaRecorder::errorOccurred, this,
        [this](QMediaRecorder::Error, const QString& msg) {
            m_recording = false;
            emit recordingChanged();
            emit errorOccurred(msg);
        });
    connect(&m_recorder, &QMediaRecorder::recorderStateChanged, this,
        [this](QMediaRecorder::RecorderState st) {
            if (st == QMediaRecorder::StoppedState) finishRecording();
        });
    connect(&m_camera, &QCamera::errorOccurred, this,
        [this](QCamera::Error err, const QString& msg) {
            if (err == QCamera::NoError) return;
            emit errorOccurred(msg.isEmpty() ? QStringLiteral("카메라를 열 수 없습니다") : msg);
        });

    // 카메라/마이크 연결·해제 → 버튼/기기 카드 상태 갱신
    connect(&m_devices, &QMediaDevices::videoInputsChanged, this, &CameraRecorder::devicesChanged);
    connect(&m_devices, &QMediaDevices::audioInputsChanged, this, &CameraRecorder::devicesChanged);
}

void CameraRecorder::finishRecording()
{
    if (!m_recording) return;

    const QString completedPath = m_outPath;
    const bool cancelled = m_cancelled;
    m_recording = false;
    emit recordingChanged();
    if (cancelled)
        discard(completedPath); // 취소된 녹화는 즉시 폐기하고 recorder 소유권도 비운다.
    else
        emit recorded(completedPath);
}

QString CameraRecorder::recordingDir()
{
    return QDir::cleanPath(QStandardPaths::writableLocation(QStandardPaths::TempLocation)
                           + QStringLiteral("/careertuner"));
}

bool CameraRecorder::cameraAvailable() const
{
    return !QMediaDevices::videoInputs().isEmpty();
}

bool CameraRecorder::microphoneAvailable() const
{
    return !QMediaDevices::audioInputs().isEmpty();
}

void CameraRecorder::setVideoSink(QVideoSink* sink)
{
    if (m_videoSink == sink) return;
    m_videoSink = sink;
    m_session.setVideoSink(sink);
    emit videoSinkChanged();
}

void CameraRecorder::startPreview()
{
    if (m_previewing) return;
    if (!cameraAvailable()) {
        emit errorOccurred(QStringLiteral("카메라를 찾을 수 없습니다 — 폰으로 이어하기를 이용하세요"));
        return;
    }
    m_camera.setCameraDevice(QMediaDevices::defaultVideoInput());
    m_camera.start();
    m_previewing = true;
    emit previewingChanged();
}

void CameraRecorder::stopPreview()
{
    if (m_recording) cancel();
    if (!m_previewing) return;
    m_camera.stop();
    m_previewing = false;
    emit previewingChanged();
}

void CameraRecorder::start()
{
    if (m_recording) return;
    if (!m_previewing) startPreview();
    if (!m_previewing) return; // 카메라 없음 — startPreview 가 이미 에러 발행

    if (!m_outPath.isEmpty()) discard(m_outPath);
    const QString dir = recordingDir();
    QDir().mkpath(dir);
    m_outPath = dir + QStringLiteral("/video-answer-%1.mp4")
                          .arg(QDateTime::currentDateTime().toString("yyyyMMdd-HHmmss"));

    m_cancelled = false;
    m_seconds = 0;
    emit secondsChanged();

    m_recorder.setOutputLocation(QUrl::fromLocalFile(m_outPath));
    m_recorder.record();
    m_recording = true;
    emit recordingChanged();
}

void CameraRecorder::stop()
{
    if (!m_recording) return;
    m_recorder.stop(); // StoppedState 시그널에서 recorded() 발행
    if (m_recorder.recorderState() == QMediaRecorder::StoppedState)
        finishRecording();
}

void CameraRecorder::cancel()
{
    if (!m_recording) return;
    m_cancelled = true;
    m_recorder.stop();
    if (m_recorder.recorderState() == QMediaRecorder::StoppedState)
        finishRecording();
}

bool CameraRecorder::discard(const QString& filePath)
{
    if (filePath.isEmpty()) return true;

    const QFileInfo fileInfo(filePath);
    const QString expectedDir = QDir::cleanPath(recordingDir());
    const Qt::CaseSensitivity pathCase =
#ifdef Q_OS_WIN
        Qt::CaseInsensitive;
#else
        Qt::CaseSensitive;
#endif
    if (QDir::cleanPath(fileInfo.absolutePath()).compare(expectedDir, pathCase) != 0
        || !fileInfo.fileName().startsWith(QStringLiteral("video-answer-"))
        || fileInfo.suffix().compare(QStringLiteral("mp4"), Qt::CaseInsensitive) != 0) {
        return false;
    }

    const QString absolutePath = QDir::cleanPath(fileInfo.absoluteFilePath());
    const bool removed = !QFile::exists(absolutePath) || QFile::remove(absolutePath);
    if (removed && QDir::cleanPath(m_outPath).compare(absolutePath, pathCase) == 0)
        m_outPath.clear();
    return removed;
}

bool CameraRecorder::release(const QString& filePath)
{
    if (filePath.isEmpty() || m_outPath.isEmpty()) return false;

    const Qt::CaseSensitivity pathCase =
#ifdef Q_OS_WIN
        Qt::CaseInsensitive;
#else
        Qt::CaseSensitive;
#endif
    const QString requested = QDir::cleanPath(QFileInfo(filePath).absoluteFilePath());
    const QString active = QDir::cleanPath(QFileInfo(m_outPath).absoluteFilePath());
    if (requested.compare(active, pathCase) != 0) return false;

    m_outPath.clear();
    return true;
}
