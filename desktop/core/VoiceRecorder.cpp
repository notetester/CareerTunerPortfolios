#include "VoiceRecorder.h"
#include <QStandardPaths>
#include <QDateTime>
#include <QDir>
#include <QTimer>
#include <QMediaFormat>
#include <QUrl>

VoiceRecorder::VoiceRecorder(QObject* parent) : QObject(parent)
{
    m_session.setAudioInput(&m_audioInput);
    m_session.setRecorder(&m_recorder);

    // 서버(voice-transcribe)가 받는 일반 포맷 — Opus/WebM 계열이 안 되면 AAC(m4a)로 폴백
    QMediaFormat fmt;
    fmt.setFileFormat(QMediaFormat::Mpeg4Audio);
    fmt.setAudioCodec(QMediaFormat::AudioCodec::AAC);
    m_recorder.setMediaFormat(fmt);
    m_recorder.setQuality(QMediaRecorder::HighQuality);

    connect(&m_recorder, &QMediaRecorder::durationChanged, this, [this](qint64 ms) {
        const int s = static_cast<int>(ms / 1000);
        if (s != m_seconds) { m_seconds = s; emit secondsChanged(); }
    });
    connect(&m_recorder, &QMediaRecorder::errorOccurred, this,
        [this](QMediaRecorder::Error, const QString& msg) {
            m_recording = false;
            emit recordingChanged();
            emit errorOccurred(msg);
        });
    connect(&m_recorder, &QMediaRecorder::recorderStateChanged, this,
        [this](QMediaRecorder::RecorderState st) {
            if (st == QMediaRecorder::StoppedState && m_recording) {
                m_recording = false;
                emit recordingChanged();
                if (!m_cancelled)
                    emit recorded(m_outPath);
            }
        });
}

void VoiceRecorder::start()
{
    if (m_recording) return;

    const QString dir = QStandardPaths::writableLocation(QStandardPaths::TempLocation)
                        + QStringLiteral("/careertuner");
    QDir().mkpath(dir);
    m_outPath = dir + QStringLiteral("/answer-%1.m4a")
                          .arg(QDateTime::currentDateTime().toString("yyyyMMdd-HHmmss"));

    m_cancelled = false;
    m_seconds = 0;
    emit secondsChanged();

    m_recorder.setOutputLocation(QUrl::fromLocalFile(m_outPath));
    m_recorder.record();
    m_recording = true;
    emit recordingChanged();
}

void VoiceRecorder::stop()
{
    if (!m_recording) return;
    m_recorder.stop(); // StoppedState 시그널에서 recorded() 발행
}

void VoiceRecorder::cancel()
{
    if (!m_recording) return;
    m_cancelled = true;
    m_recorder.stop();
}
