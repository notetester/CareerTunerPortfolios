#pragma once
#include <QObject>
#include <QString>
#include <QMediaCaptureSession>
#include <QMediaRecorder>
#include <QMediaDevices>
#include <QAudioDevice>
#include <QAudioInput>
#include <QCamera>
#include <QVideoSink>

// 웹캠 영상+음성 녹화기 (Qt Multimedia) — 카메라 면접(영상 답변)용.
// startPreview() → QML VideoOutput 프리뷰 → start() → 임시 mp4 녹화(최대 3분)
// → stop() → recorded(파일경로) 시그널. VoiceRecorder 와 같은 문법.
// 전송(base64 → avatar-score)은 InterviewSession::submitVideoAnswer 쪽에서 담당한다.
class DesktopCoreTests;

class CameraRecorder : public QObject
{
    Q_OBJECT
    Q_PROPERTY(bool recording  READ recording  NOTIFY recordingChanged)
    Q_PROPERTY(bool previewing READ previewing NOTIFY previewingChanged)
    Q_PROPERTY(int  seconds    READ seconds    NOTIFY secondsChanged)
    Q_PROPERTY(int  maxSeconds READ maxSeconds CONSTANT)
    // 기기 감지 — 카메라 없는 PC 는 QML 이 "폰으로 이어하기" 로 대체한다
    Q_PROPERTY(bool cameraAvailable     READ cameraAvailable     NOTIFY devicesChanged)
    Q_PROPERTY(bool microphoneAvailable READ microphoneAvailable NOTIFY devicesChanged)
    // QML VideoOutput.videoSink 를 꽂으면 프리뷰가 그려진다
    Q_PROPERTY(QVideoSink* videoSink READ videoSink WRITE setVideoSink NOTIFY videoSinkChanged)
public:
    explicit CameraRecorder(QObject* parent = nullptr);

    bool recording() const  { return m_recording; }
    bool previewing() const { return m_previewing; }
    int  seconds() const    { return m_seconds; }
    int  maxSeconds() const { return kMaxSeconds; }
    bool cameraAvailable() const;
    bool microphoneAvailable() const;
    QVideoSink* videoSink() const { return m_videoSink; }
    void setVideoSink(QVideoSink* sink);

    Q_INVOKABLE void startPreview();  // 프리뷰 패널 열릴 때 — 카메라 켜기
    Q_INVOKABLE void stopPreview();   // 패널 닫힐 때 — 카메라 끄기 (녹화 중이면 취소)
    Q_INVOKABLE void start();
    Q_INVOKABLE void stop();
    Q_INVOKABLE void cancel();
    Q_INVOKABLE bool discard(const QString& filePath);
    /** 제출 완료 파일의 소유권을 InterviewSession으로 넘겨 다음 녹화가 지우지 않게 한다. */
    Q_INVOKABLE bool release(const QString& filePath);

signals:
    void recordingChanged();
    void previewingChanged();
    void secondsChanged();
    void devicesChanged();
    void videoSinkChanged();
    void recorded(const QString& filePath); // 정상 종료 시 결과 파일 (mp4)
    void errorOccurred(const QString& message);

private:
    friend class DesktopCoreTests;

    static constexpr int kMaxSeconds = 180; // 영상 답변 최대 3분
    static QString recordingDir();
    void finishRecording();

    QMediaCaptureSession m_session;
    QCamera              m_camera;
    QAudioInput          m_audioInput;
    QMediaRecorder       m_recorder;
    QMediaDevices        m_devices;     // 기기 연결/해제 감시
    QVideoSink*          m_videoSink = nullptr;
    bool m_recording  = false;
    bool m_previewing = false;
    bool m_cancelled  = false;
    int  m_seconds = 0;
    QString m_outPath;
};
