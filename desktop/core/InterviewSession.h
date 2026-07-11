#pragma once
#include <QObject>
#include <QVariantList>
#include <QVariantMap>
#include <QString>
#include <QStringList>
#include <QHash>
#include <QJsonObject>

class ApiClient;
class SettingsStore;

// 선택된 면접 세션 한 건의 "대화 스레드" 상태 + 연습 흐름 + 산출물 내보내기.
//
// thread 프로퍼티 (QVariantList of QVariantMap) 아이템 종류:
//   {kind:"question", qid, text, qtype, followUp}
//   {kind:"answer",   qid, text, hasAudio, hasVideo, pending}
//   {kind:"score",    qid, score, feedback, improvedAnswer, modelAnswer,
//                     voiceScore[, visualScore, videoScore]}
//   {kind:"scoring",  qid}                  — 채점 중 스피너 행
// QML 은 이 리스트를 그대로 그린다 (CC Desktop 의 대화 타임라인 문법).
class InterviewSession : public QObject
{
    Q_OBJECT
    Q_PROPERTY(int          sessionId   READ sessionId   NOTIFY sessionChanged)
    Q_PROPERTY(QString      title       READ title       NOTIFY sessionChanged)
    Q_PROPERTY(QString      mode        READ mode        NOTIFY sessionChanged)
    Q_PROPERTY(QVariantList thread      READ thread      NOTIFY threadChanged)
    Q_PROPERTY(QVariantList agentSteps  READ agentSteps  NOTIFY agentStepsChanged)
    Q_PROPERTY(QVariantMap  progress    READ progress    NOTIFY progressChanged)
    Q_PROPERTY(QVariantMap  report      READ report      NOTIFY reportChanged)
    Q_PROPERTY(bool         loading     READ loading     NOTIFY busyChanged)
    Q_PROPERTY(bool         scoring     READ scoring     NOTIFY busyChanged)
    Q_PROPERTY(bool         transcribing READ transcribing NOTIFY busyChanged)
    Q_PROPERTY(qint64       currentQid  READ currentQid  NOTIFY progressChanged)
    Q_PROPERTY(QString      currentQuestionText READ currentQuestionText NOTIFY progressChanged)
public:
    explicit InterviewSession(ApiClient* api, SettingsStore* store, QObject* parent = nullptr);

    int sessionId() const { return m_sessionId; }
    QString title() const { return m_title; }
    QString mode() const { return m_mode; }
    QVariantList thread() const { return m_thread; }
    QVariantList agentSteps() const { return m_agentSteps; }
    QVariantMap progress() const { return m_progress; }
    QVariantMap report() const { return m_report; }
    bool loading() const { return m_loading; }
    bool scoring() const { return m_scoring; }
    bool transcribing() const { return m_transcribing; }
    qint64 currentQid() const { return m_currentQid; }
    QString currentQuestionText() const { return m_currentQText; }

    // ── 세션 열기/연습 흐름 ──
    Q_INVOKABLE void open(int sessionId, const QString& title, const QString& mode, int caseId);
    Q_INVOKABLE void generateQuestions();            // 질문이 아직 없을 때
    Q_INVOKABLE void submitAnswer(const QString& text);
    Q_INVOKABLE void requestFollowUp();              // 마지막 답변 질문에 꼬리질문 1개
    Q_INVOKABLE void requestModelAnswer(qint64 questionId);

    // ── 음성 ──
    Q_INVOKABLE void transcribeAudio(const QString& filePath); // base64 → voice-transcribe

    // ── 영상 답변 (카메라 면접) ──
    // 녹화 mp4 를 저장한 뒤 avatar-score + 표준 answers API 로 전송한다.
    // consented(동의 플래그)가 false 면 전송하지 않는다. 제출 원본은 답변 기록에 보관하고,
    // 실패한 업로드만 정리한다. 사용자가 원본을 삭제하면 원본 기반 재분석은 제공하지 않는다.
    Q_INVOKABLE void submitVideoAnswer(const QString& filePath, bool consented);
    Q_INVOKABLE void deleteAnswerMedia(qint64 answerId, const QString& kind);

    // ── 리포트/내보내기 ──
    Q_INVOKABLE void loadReport();
    Q_INVOKABLE void exportReport(const QString& format);  // "md" | "html"
    Q_INVOKABLE void exportAll();                          // 리포트+분석문서+녹음 일괄

signals:
    void sessionChanged();
    void threadChanged();
    void agentStepsChanged();
    void progressChanged();
    void reportChanged();
    void busyChanged();
    void answerScored(int score);                 // 토스트용
    void answerSubmissionStarted();               // 입력창은 실제 제출 시작 뒤에만 비운다
    void answerSubmissionFailed(const QString& text); // 실패한 텍스트를 입력창에 복원
    void transcribed(const QString& text);        // 입력창 채우기
    void voiceScored(int score);                  // 전달력 점수 도착
    void videoScored(int score);                  // 영상 답변 결합 점수 도착
    void videoAnswerSubmitted();                  // 영상 원본+표준 답변 저장 완료
    void answerMediaDeleted(const QString& kind); // 연결 원본 물리 삭제 완료
    void exported(const QString& path, const QString& what);
    void errorOccurred(const QString& message);
    void sessionFinished();                       // 마지막 답변 완료

private:
    void reloadThread();                          // questions+review 병합 → thread 구성
    void refreshProgress();
    void loadAgentSteps();
    void appendQuestionItem(const QJsonObject& q);
    void setCurrentQuestion(qint64 qid, const QString& text);
    void startAnswerSubmission(qint64 qid, const QString& displayText,
                               bool hasAudio, bool hasVideo);
    void failAnswerSubmission(qint64 qid, const QString& retryText,
                              const QString& message, qint64 uploadedFileId,
                              bool restoreText);
    void submitStoredAnswer(qint64 qid, int sessionId, const QString& answerText,
                            const QString& audioUrl, const QString& videoUrl,
                            const QString& localMediaPath, qint64 uploadedFileId,
                            bool videoAnswer, int voiceScore = -1,
                            int visualScore = -1, int videoCombined = -1,
                            const QJsonObject& avatarResult = QJsonObject(),
                            const QString& nonverbalWarning = QString());
    void advanceAfterAnswer();
    void finishCompletedSession();
    void recordVoiceScore(qint64 questionId, int score);
    void saveVideoAnalysis(const QString& transcript, const QJsonObject& avatarResult);
    void deleteUnlinkedUpload(qint64 fileId);
    QString sessionFolder() const;                // 저장 폴더\<세션 제목-id>
    QString reportMarkdown() const;
    void writeFile(const QString& path, const QByteArray& bytes, const QString& what);
    void maybeAutoSave();

    ApiClient*     m_api;
    SettingsStore* m_store;

    int     m_sessionId = -1;
    int     m_caseId = -1;
    QString m_title;
    QString m_mode;

    QVariantList m_thread;
    QVariantList m_agentSteps;
    QVariantMap  m_progress;
    QVariantMap  m_report;
    QVariantMap  m_review;      // 원본 review 응답 (내보내기용)

    bool m_loading = false;
    bool m_scoring = false;
    bool m_transcribing = false;

    qint64  m_currentQid = -1;
    QString m_currentQText;
    QString m_pendingAudioPath;   // 현재 질문의 마지막 녹음 — 표준 답변 저장 성공까지 유지
    qint64 m_pendingAudioQuestionId = -1;
    QHash<qint64, QString> m_audioSourceByQuestion; // 재녹음 시 이전 비동기 점수 무시
    QHash<qint64, int> m_voiceScoreByQuestion; // 답변 카드보다 먼저 도착한 전달력 점수
    QStringList m_audioFiles;     // 이 세션에서 만든 녹음들 (일괄 내보내기 대상)
    QStringList m_videoFiles;     // 저장 성공한 영상들 (일괄 내보내기 대상)
    QHash<QString, QString> m_localMediaPathByAnswerKind; // "answerId:KIND" -> 로컬 원본
};
