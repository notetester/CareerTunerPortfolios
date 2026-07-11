#pragma once
#include <QObject>
#include <QString>
#include <QMediaCaptureSession>
#include <QMediaRecorder>
#include <QAudioInput>

// 마이크 녹음기 (Qt Multimedia).
// start() → 임시 파일에 녹음 → stop() → recorded(파일경로) 시그널.
// 업로드는 InterviewSession(ApiClient::postMultipart) 쪽에서 담당한다.
class DesktopCoreTests;

class VoiceRecorder : public QObject
{
    Q_OBJECT
    Q_PROPERTY(bool recording READ recording NOTIFY recordingChanged)
    Q_PROPERTY(int  seconds   READ seconds   NOTIFY secondsChanged)
public:
    explicit VoiceRecorder(QObject* parent = nullptr);

    bool recording() const { return m_recording; }
    int  seconds() const   { return m_seconds; }

    Q_INVOKABLE void start();
    Q_INVOKABLE void stop();
    Q_INVOKABLE void cancel();

signals:
    void recordingChanged();
    void secondsChanged();
    void recorded(const QString& filePath); // 정상 종료 시 결과 파일
    void errorOccurred(const QString& message);

private:
    friend class DesktopCoreTests;

    static QString recordingDir();
    static bool discardManagedRecording(const QString& filePath);
    void finishRecording();

    QMediaCaptureSession m_session;
    QAudioInput          m_audioInput;
    QMediaRecorder       m_recorder;
    bool m_recording = false;
    bool m_cancelled = false;
    int  m_seconds = 0;
    QString m_outPath;
};
