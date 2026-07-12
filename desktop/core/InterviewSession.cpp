#include "InterviewSession.h"
#include "AiChargeCoordinator.h"
#include "ApiClient.h"
#include "SettingsStore.h"
#include <QJsonArray>
#include <QJsonObject>
#include <QJsonDocument>
#include <QFile>
#include <QFileInfo>
#include <QDir>
#include <QDateTime>
#include <QHash>
#include <QRegularExpression>
#include <QStandardPaths>
#include <QUrl>
#include <QUuid>
#include <QTimer>

InterviewSession::InterviewSession(ApiClient* api, SettingsStore* store,
                                   AiChargeCoordinator* aiCharge, QObject* parent)
    : QObject(parent), m_api(api), m_store(store), m_aiCharge(aiCharge) {}

InterviewSession::~InterviewSession()
{
    cleanupLocalMediaFiles();
}

void InterviewSession::clear()
{
    ++m_sessionGeneration;
    cleanupLocalMediaFiles();
    m_sessionId = -1;
    m_caseId = -1;
    m_title.clear();
    m_mode.clear();
    setQuestionGenerationModel(QStringLiteral("AUTO"));
    m_thread.clear();
    m_agentSteps.clear();
    m_progress.clear();
    m_report.clear();
    m_reportError.clear();
    m_review.clear();
    m_audioSourceByQuestion.clear();
    m_voiceScoreByQuestion.clear();
    const bool hadFollowUpPending = !m_followUpPendingQuestions.isEmpty();
    const bool hadModelAnswerPending = !m_modelAnswerPendingQuestions.isEmpty();
    m_followUpPendingQuestions.clear();
    m_modelAnswerPendingQuestions.clear();
    clearClientSubmissionId();
    m_currentQid = -1;
    m_currentQText.clear();
    m_loading = false;
    m_scoring = false;
    m_transcribing = false;
    m_threadLoadFailed = false;
    m_threadLoadInFlight = false;
    m_questionGenerationInFlight = false;
    m_questionGenerationQueued = false;
    m_reportLoadInFlight = false;
    m_questionGenerationActionKey.clear();
    m_questionGenerationActionModel.clear();
    m_followUpActionKeys.clear();

    emit sessionChanged();
    emit threadChanged();
    emit agentStepsChanged();
    emit progressChanged();
    emit reportChanged();
    emit busyChanged();
    if (hadFollowUpPending) emit followUpPendingChanged();
    if (hadModelAnswerPending) emit modelAnswerPendingChanged();
}

QString InterviewSession::managedLocalMediaPath(const QString& filePath)
{
    if (filePath.isEmpty()) return {};

    const QFileInfo info(filePath);
    const QString expectedDir = QDir::cleanPath(
        QStandardPaths::writableLocation(QStandardPaths::TempLocation)
        + QStringLiteral("/careertuner"));
    const Qt::CaseSensitivity pathCase =
#ifdef Q_OS_WIN
        Qt::CaseInsensitive;
#else
        Qt::CaseSensitive;
#endif
    const bool managedAudio = info.fileName().startsWith(QStringLiteral("answer-"))
        && info.suffix().compare(QStringLiteral("m4a"), Qt::CaseInsensitive) == 0;
    const bool managedVideo = info.fileName().startsWith(QStringLiteral("video-answer-"))
        && info.suffix().compare(QStringLiteral("mp4"), Qt::CaseInsensitive) == 0;
    if (QDir::cleanPath(info.absolutePath()).compare(expectedDir, pathCase) != 0
        || (!managedAudio && !managedVideo)) {
        return {};
    }
    return QDir::cleanPath(info.absoluteFilePath());
}

void InterviewSession::cleanupLocalMediaFiles()
{
    QHash<QString, QString> pathsByKey;
    const auto collect = [&pathsByKey](const QString& filePath) {
        const QString managedPath = managedLocalMediaPath(filePath);
        if (managedPath.isEmpty()) return;
#ifdef Q_OS_WIN
        pathsByKey.insert(managedPath.toCaseFolded(), managedPath);
#else
        pathsByKey.insert(managedPath, managedPath);
#endif
    };

    for (const QString& filePath : m_audioFiles) collect(filePath);
    for (const QString& filePath : m_videoFiles) collect(filePath);
    for (auto it = m_localMediaPathByAnswerKind.cbegin();
         it != m_localMediaPathByAnswerKind.cend(); ++it) {
        collect(it.value());
    }
    collect(m_pendingAudioPath);

    for (const QString& filePath : pathsByKey)
        QFile::remove(filePath);

    m_audioFiles.clear();
    m_videoFiles.clear();
    m_localMediaPathByAnswerKind.clear();
    m_pendingAudioPath.clear();
    m_pendingAudioQuestionId = -1;
}

// ─────────────────────────── 세션 열기 ───────────────────────────

void InterviewSession::open(int sessionId, const QString& title, const QString& mode, int caseId)
{
    // 같은 세션을 과금 확인/답변 평가/질문·리포트 조회 중 다시 누르면 진행 중 상태와
    // clientSubmissionId가 초기화되어 같은 작업이 중복 실행될 수 있다. 이 경우 화면 전환만
    // Main.qml에서 수행하고 core 상태는 그대로 유지한다.
    if (m_sessionId == sessionId && sessionId >= 0
        && (m_scoring || m_transcribing || m_threadLoadInFlight
            || m_questionGenerationInFlight || m_reportLoadInFlight
            || !m_pendingClientSubmissionId.isEmpty()
            || !m_followUpPendingQuestions.isEmpty()
            || !m_modelAnswerPendingQuestions.isEmpty()
            || !m_questionGenerationActionKey.isEmpty()
            || !m_followUpActionKeys.isEmpty())) {
        return;
    }
    ++m_sessionGeneration;
    // exportAll이 사용할 원본은 세션을 유지하는 동안 보관하고, 세션 경계에서만 폐기한다.
    if (m_sessionId >= 0 && m_sessionId != sessionId)
        cleanupLocalMediaFiles();
    m_sessionId = sessionId;
    m_caseId    = caseId;
    m_title     = title;
    m_mode      = mode;
    setQuestionGenerationModel(QStringLiteral("AUTO"));
    m_thread.clear();
    m_agentSteps.clear();
    m_report.clear();
    m_reportError.clear();
    m_review.clear();
    m_audioSourceByQuestion.clear();
    m_voiceScoreByQuestion.clear();
    const bool hadFollowUpPending = !m_followUpPendingQuestions.isEmpty();
    const bool hadModelAnswerPending = !m_modelAnswerPendingQuestions.isEmpty();
    m_followUpPendingQuestions.clear();
    m_modelAnswerPendingQuestions.clear();
    clearClientSubmissionId();
    m_currentQid = -1;
    m_currentQText.clear();
    m_scoring = false;
    m_transcribing = false;
    m_threadLoadFailed = false;
    m_threadLoadInFlight = false;
    m_questionGenerationInFlight = false;
    m_questionGenerationQueued = false;
    m_reportLoadInFlight = false;
    m_questionGenerationActionKey.clear();
    m_questionGenerationActionModel.clear();
    m_followUpActionKeys.clear();

    emit sessionChanged();
    emit threadChanged();
    emit reportChanged();
    if (hadFollowUpPending) emit followUpPendingChanged();
    if (hadModelAnswerPending) emit modelAnswerPendingChanged();

    reloadThread();
    refreshProgress();
    loadAgentSteps();
}

bool InterviewSession::isCurrentSession(int sessionId, quint64 generation) const
{
    return sessionId == m_sessionId && generation == m_sessionGeneration;
}

void InterviewSession::updateLoading()
{
    const bool loading = m_threadLoadInFlight || m_questionGenerationInFlight;
    if (m_loading == loading) return;
    m_loading = loading;
    emit busyChanged();
}

void InterviewSession::reloadThread(bool completesQuestionGeneration)
{
    const int sid = m_sessionId;
    if (sid < 0) return;
    const quint64 generation = m_sessionGeneration;
    m_threadLoadInFlight = true;
    updateLoading();
    m_threadLoadFailed = false;
    // 1) 질문 목록 (parentQuestionId → 꼬리질문 판별)
    m_api->get(QStringLiteral("/api/interview/sessions/%1/questions").arg(sid),
        [this, sid, generation, completesQuestionGeneration](bool okQ, const QJsonValue& qData, const QString& questionMessage) {
            if (!isCurrentSession(sid, generation)) return;
            if (!okQ) {
                m_threadLoadFailed = true;
                setCurrentQuestion(-1, QString());
                m_threadLoadInFlight = false;
                if (completesQuestionGeneration) m_questionGenerationInFlight = false;
                m_questionGenerationQueued = false;
                updateLoading();
                emit errorOccurred(questionMessage.isEmpty()
                    ? QStringLiteral("면접 질문을 불러오지 못했습니다. 다시 시도해 주세요.")
                    : questionMessage);
                return;
            }
            QHash<qint64, QJsonObject> qById;
            QList<qint64> order;
            for (const QJsonValue& v : qData.toArray()) {
                const QJsonObject q = v.toObject();
                const qint64 id = q.value("id").toInteger();
                qById.insert(id, q);
                order.push_back(id);
            }
            // 2) review (답변·채점 병합)
            m_api->get(QStringLiteral("/api/interview/sessions/%1/review").arg(sid),
                [this, sid, generation, completesQuestionGeneration, qById, order](bool okR, const QJsonValue& rData,
                                          const QString& reviewMessage) {
                    if (!isCurrentSession(sid, generation)) return;
                    if (!okR) {
                        m_threadLoadFailed = true;
                        setCurrentQuestion(-1, QString());
                        m_threadLoadInFlight = false;
                        if (completesQuestionGeneration) m_questionGenerationInFlight = false;
                        m_questionGenerationQueued = false;
                        updateLoading();
                        emit errorOccurred(reviewMessage.isEmpty()
                            ? QStringLiteral("면접 복기 정보를 불러오지 못했습니다. 다시 시도해 주세요.")
                            : reviewMessage);
                        return;
                    }
                    m_thread.clear();
                    QHash<qint64, QJsonObject> reviewByQid;
                    m_review = rData.toObject().toVariantMap();
                    for (const QJsonValue& v : rData.toObject().value("items").toArray()) {
                        const QJsonObject it = v.toObject();
                        reviewByQid.insert(it.value("questionId").toInteger(), it);
                    }
                    qint64 firstUnanswered = -1;
                    QString firstUnansweredText;
                    for (qint64 qid : order) {
                        const QJsonObject q = qById.value(qid);
                        appendQuestionItem(q);
                        const QJsonObject rv = reviewByQid.value(qid);
                        const QString answer = rv.value("answerText").toString();
                        if (!answer.isEmpty()) {
                            const qint64 answerId = rv.value("answerId").toInteger();
                            const bool hasAudio = !rv.value("audioUrl").toString().isEmpty();
                            const bool hasVideo = !rv.value("videoUrl").toString().isEmpty();
                            m_thread.push_back(QVariantMap{
                                {"kind", "answer"}, {"qid", qid}, {"answerId", answerId},
                                {"text", answer}, {"hasAudio", hasAudio}, {"hasVideo", hasVideo},
                                {"pending", false}});
                            m_thread.push_back(QVariantMap{
                                {"kind", "score"},
                                {"qid", qid},
                                {"answerId", answerId},
                                {"score", rv.value("score").toInt(-1)},
                                {"feedback", rv.value("feedback").toString()},
                                {"improvedAnswer", rv.value("improvedAnswer").toString()},
                                {"modelAnswer", rv.value("modelAnswer").toString()},
                                {"voiceScore", rv.value("voiceScore").toInt(-1)},
                                {"visualScore", rv.value("visualScore").toInt(-1)},
                                {"videoScore", -1},
                                {"hasAudioOriginal", hasAudio}, {"hasVideoOriginal", hasVideo}});
                        } else if (firstUnanswered < 0) {
                            firstUnanswered = qid;
                            firstUnansweredText = q.value("question").toString();
                        }
                    }
                    setCurrentQuestion(firstUnanswered, firstUnansweredText);
                    if (!order.isEmpty()) {
                        m_questionGenerationActionKey.clear();
                        m_questionGenerationActionModel.clear();
                    }
                    for (qint64 qid : order) {
                        const qint64 parentId = qById.value(qid).value(QStringLiteral("parentQuestionId")).toInteger();
                        if (parentId > 0) m_followUpActionKeys.remove(parentId);
                    }
                    m_threadLoadFailed = false;
                    const bool generateAfterLoad = m_questionGenerationQueued
                        && order.isEmpty() && !completesQuestionGeneration;
                    m_questionGenerationQueued = false;
                    m_threadLoadInFlight = false;
                    if (completesQuestionGeneration) m_questionGenerationInFlight = false;
                    updateLoading();
                    emit threadChanged();
                    if (generateAfterLoad) startQuestionGeneration();
                });
        });
}

void InterviewSession::retryLoadThread()
{
    if (m_sessionId < 0 || m_threadLoadInFlight || m_questionGenerationInFlight) return;
    m_threadLoadFailed = false;
    reloadThread();
}

void InterviewSession::appendQuestionItem(const QJsonObject& q)
{
    m_thread.push_back(QVariantMap{
        {"kind", "question"},
        {"qid", q.value("id").toInteger()},
        {"text", q.value("question").toString()},
        {"qtype", q.value("questionType").toString()},
        {"followUp", !q.value("parentQuestionId").isNull()}});
}

void InterviewSession::setCurrentQuestion(qint64 qid, const QString& text)
{
    m_currentQid = qid;
    m_currentQText = text;
    emit progressChanged();
}

void InterviewSession::refreshProgress()
{
    const int sid = m_sessionId;
    const quint64 generation = m_sessionGeneration;
    m_api->get(QStringLiteral("/api/interview/sessions/%1/progress").arg(sid),
        [this, sid, generation](bool ok, const QJsonValue& data, const QString&) {
            if (!isCurrentSession(sid, generation) || !ok) return;
            const QJsonObject o = data.toObject();
            m_progress = QVariantMap{
                {"total", o.value("totalQuestions").toInt()},
                {"answered", o.value("answeredQuestions").toInt()},
                {"finished", o.value("finished").toBool()}};
            emit progressChanged();
        });
}

void InterviewSession::loadAgentSteps()
{
    const int sid = m_sessionId;
    const quint64 generation = m_sessionGeneration;
    m_api->get(QStringLiteral("/api/interview/sessions/%1/agent-steps").arg(sid),
        [this, sid, generation](bool ok, const QJsonValue& data, const QString&) {
            if (!isCurrentSession(sid, generation)) return;
            m_agentSteps.clear();
            if (ok) {
                for (const QJsonValue& v : data.toArray()) {
                    const QJsonObject s = v.toObject();
                    m_agentSteps.push_back(QVariantMap{
                        {"agent", s.value("agent").toString()},
                        {"action", s.value("action").toString()},
                        {"status", s.value("status").toString()},
                        {"summary", s.value("summary").toString()},
                        {"detail", s.value("detail").toString()},
                        {"elapsedMs", s.value("elapsedMs").toInt()}});
                }
            }
            emit agentStepsChanged();
        });
}

// ─────────────────────────── 연습 흐름 ───────────────────────────

void InterviewSession::setQuestionGenerationModel(const QString& model)
{
    QString normalized = model.trimmed().toUpper();
    static const QSet<QString> allowed{
        QStringLiteral("AUTO"),
        QStringLiteral("CAREERTUNER"),
        QStringLiteral("CLAUDE"),
        QStringLiteral("OPENAI")
    };
    if (!allowed.contains(normalized)) normalized = QStringLiteral("AUTO");
    if (m_questionGenerationModel == normalized) return;

    m_questionGenerationModel = normalized;
    emit questionGenerationModelChanged();
}

bool InterviewSession::canRegenerateQuestions() const
{
    if (m_scoring || m_transcribing || !m_pendingClientSubmissionId.isEmpty()) return false;
    bool hasQuestion = false;
    for (const QVariant& item : m_thread) {
        const QString kind = item.toMap().value(QStringLiteral("kind")).toString();
        if (kind == QStringLiteral("question")) {
            hasQuestion = true;
        } else if (kind == QStringLiteral("answer")
                   || kind == QStringLiteral("score")
                   || kind == QStringLiteral("scoring")) {
            return false;
        }
    }
    return hasQuestion;
}

void InterviewSession::discardPendingAnswerMedia()
{
    const qint64 pendingQuestionId = m_pendingAudioQuestionId;
    const bool hadPendingMedia = !m_pendingAudioPath.isEmpty();
    const QString managedPath = managedLocalMediaPath(m_pendingAudioPath);
    if (!managedPath.isEmpty()) QFile::remove(managedPath);
    m_pendingAudioPath.clear();
    m_pendingAudioQuestionId = -1;
    if (pendingQuestionId >= 0) {
        m_audioSourceByQuestion.remove(pendingQuestionId);
        m_voiceScoreByQuestion.remove(pendingQuestionId);
    }
    if (hadPendingMedia) emit busyChanged();
}

void InterviewSession::generateQuestions()
{
    if (m_threadLoadFailed) {
        emit errorOccurred(QStringLiteral("세션 조회에 실패한 상태에서는 질문을 생성할 수 없습니다. 먼저 다시 불러와 주세요."));
        return;
    }
    if (m_sessionId < 0 || m_questionGenerationInFlight || m_questionGenerationQueued) return;
    if (m_threadLoadInFlight) {
        // 새 세션 open 직후의 최초 조회 결과를 먼저 확인한다. 이미 질문이 있으면 과금
        // preview/generate를 실행하지 않고, 비어 있을 때만 정확히 한 번 생성한다.
        m_questionGenerationQueued = true;
        return;
    }
    bool hasQuestion = false;
    for (const QVariant& item : m_thread) {
        if (item.toMap().value(QStringLiteral("kind")) == QStringLiteral("question")) {
            hasQuestion = true;
            break;
        }
    }
    if (hasQuestion && !canRegenerateQuestions()) {
        emit errorOccurred(QStringLiteral(
            "답변·원본·분석이 시작된 세션의 질문은 교체할 수 없습니다. 새 면접 세션을 만들어 주세요."));
        return;
    }
    startQuestionGeneration();
}

void InterviewSession::startQuestionGeneration()
{
    const int sid = m_sessionId;
    if (sid < 0 || m_questionGenerationInFlight || m_threadLoadFailed) return;
    const quint64 generation = m_sessionGeneration;
    // 선택만 바꾸고 확인을 취소한 경우에는 기존 복구 키를 보존한다. 실제 실행 시점에
    // 모델이 달라졌을 때만 사용자가 요청한 별도 생성 의도로 새 action key를 발급한다.
    if (!m_questionGenerationActionKey.isEmpty()
        && !m_questionGenerationActionModel.isEmpty()
        && m_questionGenerationActionModel != m_questionGenerationModel) {
        m_questionGenerationActionKey.clear();
        m_questionGenerationActionModel.clear();
    }
    if (m_questionGenerationActionKey.isEmpty()) {
        m_questionGenerationActionKey = AiChargeCoordinator::createActionKey();
        m_questionGenerationActionModel = m_questionGenerationModel;
    }
    if (m_questionGenerationActionModel.isEmpty())
        m_questionGenerationActionModel = m_questionGenerationModel;
    const QString actionKey = m_questionGenerationActionKey;
    const QString operationModel = m_questionGenerationActionModel;
    QStringList baselineQuestionIds;
    for (const QVariant& item : m_thread) {
        const QVariantMap row = item.toMap();
        if (row.value(QStringLiteral("kind")) == QStringLiteral("question"))
            baselineQuestionIds.push_back(QString::number(row.value(QStringLiteral("qid")).toLongLong()));
    }
    const QString baselineQuestionSignature = baselineQuestionIds.join(QLatin1Char(','));
    const bool replacingQuestions = !baselineQuestionSignature.isEmpty();
    QString endpoint = QStringLiteral("/api/interview/sessions/%1/generate-questions").arg(sid);
    if (operationModel != QStringLiteral("AUTO"))
        endpoint += QStringLiteral("?model=") + operationModel;
    m_questionGenerationInFlight = true;
    updateLoading();
    const auto operation = [this, sid, generation, actionKey, endpoint,
                            baselineQuestionSignature, replacingQuestions](const ApiClient::Headers& headers) {
        if (!isCurrentSession(sid, generation)) return;
        m_api->postDetailed(endpoint,
            QJsonObject(), headers,
            [this, sid, generation, actionKey, baselineQuestionSignature, replacingQuestions]
            (bool ok, const QJsonValue&, const QString& msg, int httpStatus) {
            if (!isCurrentSession(sid, generation)) return;
            if (!ok) {
                if (isAmbiguousOperationStatus(httpStatus)) {
                    reconcileQuestionGeneration(sid, generation,
                        msg.isEmpty() ? QStringLiteral("질문 생성 결과를 확인하지 못했습니다.") : msg,
                        baselineQuestionSignature, replacingQuestions);
                } else {
                    if (m_questionGenerationActionKey == actionKey) {
                        m_questionGenerationActionKey.clear();
                        m_questionGenerationActionModel.clear();
                    }
                    m_questionGenerationInFlight = false;
                    updateLoading();
                    emit errorOccurred(msg.isEmpty() ? QStringLiteral("질문 생성 실패") : msg);
                }
                return;
            }
            if (m_questionGenerationActionKey == actionKey) {
                m_questionGenerationActionKey.clear();
                m_questionGenerationActionModel.clear();
            }
            if (replacingQuestions) {
                discardPendingAnswerMedia();
                emit questionsRegenerated();
            }
            reloadThread(true);
            refreshProgress();
            loadAgentSteps();
        });
    };
    if (m_aiCharge) {
        m_aiCharge->runWithActionKey(QStringLiteral("INTERVIEW_QUESTION_GEN"), actionKey, operation,
            [this, sid, generation](const QString&) {
                if (!isCurrentSession(sid, generation)) return;
                m_questionGenerationInFlight = false;
                updateLoading();
            });
    } else {
        operation({});
    }
}

bool InterviewSession::isAmbiguousOperationStatus(int httpStatus)
{
    return httpStatus == 0 || httpStatus >= 500 || httpStatus == 408
        || httpStatus == 425 || httpStatus == 429;
}

void InterviewSession::reconcileQuestionGeneration(int sessionId, quint64 generation,
                                                   const QString& message,
                                                   const QString& baselineQuestionSignature,
                                                   bool replacingQuestions,
                                                   int attempt)
{
    if (!isCurrentSession(sessionId, generation)) return;
    m_api->get(QStringLiteral("/api/interview/sessions/%1/questions").arg(sessionId),
        [this, sessionId, generation, message, baselineQuestionSignature,
         replacingQuestions, attempt](bool ok, const QJsonValue& data, const QString&) {
            if (!isCurrentSession(sessionId, generation)) return;
            QStringList currentQuestionIds;
            for (const QJsonValue& question : data.toArray())
                currentQuestionIds.push_back(QString::number(question.toObject().value(QStringLiteral("id")).toInteger()));
            const QString currentSignature = currentQuestionIds.join(QLatin1Char(','));
            const bool mutationVisible = baselineQuestionSignature.isEmpty()
                ? !currentSignature.isEmpty()
                : currentSignature != baselineQuestionSignature;
            if (ok && mutationVisible) {
                m_questionGenerationActionKey.clear();
                m_questionGenerationActionModel.clear();
                if (replacingQuestions) {
                    discardPendingAnswerMedia();
                    emit questionsRegenerated();
                }
                reloadThread(true);
                refreshProgress();
                loadAgentSteps();
                return;
            }
            if (attempt < 2) {
                QTimer::singleShot(150 * (attempt + 1), this,
                    [this, sessionId, generation, message, baselineQuestionSignature,
                     replacingQuestions, attempt]() {
                        reconcileQuestionGeneration(sessionId, generation, message,
                            baselineQuestionSignature, replacingQuestions, attempt + 1);
                    });
                return;
            }
            m_questionGenerationInFlight = false;
            updateLoading();
            emit errorOccurred(message + QStringLiteral(
                " 같은 모델로 재시도하거나 다른 모델을 선택해 새로 시도해 주세요."));
        });
}

QString InterviewSession::ensureClientSubmissionId(qint64 qid, const QString& answerText,
                                                   const QString& mediaKey)
{
    const QString normalizedText = answerText.trimmed();
    if (!m_pendingClientSubmissionId.isEmpty()
        && m_pendingSubmissionQuestionId == qid
        && m_pendingSubmissionText == normalizedText
        && m_pendingSubmissionMediaKey == mediaKey) {
        return m_pendingClientSubmissionId;
    }

    m_pendingSubmissionQuestionId = qid;
    m_pendingSubmissionText = normalizedText;
    m_pendingSubmissionMediaKey = mediaKey;
    m_pendingClientSubmissionId = QUuid::createUuid().toString(QUuid::WithoutBraces);
    return m_pendingClientSubmissionId;
}

void InterviewSession::clearClientSubmissionId(const QString& clientSubmissionId)
{
    if (!clientSubmissionId.isEmpty() && clientSubmissionId != m_pendingClientSubmissionId) return;
    m_pendingSubmissionQuestionId = -1;
    m_pendingSubmissionText.clear();
    m_pendingSubmissionMediaKey.clear();
    m_pendingClientSubmissionId.clear();
}

bool InterviewSession::shouldPreserveClientSubmissionId(int httpStatus)
{
    // status 0은 연결 단절/timeout으로 서버 저장 여부를 알 수 없다. 408/409/425/429와
    // 5xx도 재조회·재시도 가능 상태이므로 같은 UUID를 유지한다.
    return httpStatus == 0 || httpStatus >= 500
        || httpStatus == 408 || httpStatus == 409
        || httpStatus == 425 || httpStatus == 429;
}

void InterviewSession::submitAnswer(const QString& text)
{
    if (m_currentQid < 0 || m_scoring || text.trimmed().isEmpty()) return;

    const qint64 qid = m_currentQid;
    const int sid = m_sessionId;
    const quint64 generation = m_sessionGeneration;
    const bool hasAudio = m_pendingAudioQuestionId == qid && !m_pendingAudioPath.isEmpty();
    const QString audioPath = hasAudio ? m_pendingAudioPath : QString();
    const QString mediaKey = hasAudio
        ? QStringLiteral("AUDIO:") + QDir::cleanPath(QFileInfo(audioPath).absoluteFilePath())
        : QStringLiteral("TEXT");
    const QString clientSubmissionId = ensureClientSubmissionId(qid, text, mediaKey);
    QByteArray audio;
    if (hasAudio) {
        QFile file(audioPath);
        if (!file.open(QIODevice::ReadOnly)) {
            emit errorOccurred(QStringLiteral("녹음 원본을 열 수 없어 답변을 제출하지 않았습니다"));
            return;
        }
        audio = file.readAll();
    }

    startAnswerSubmission(qid, text, hasAudio, false);
    emit answerSubmissionStarted();

    if (!hasAudio) {
        submitStoredAnswer(qid, sid, text, QString(), QString(), QString(), 0, false,
                           -1, -1, -1, QJsonObject(), QString(), clientSubmissionId);
        return;
    }

    // 기획 정본: 제출된 음성 원본은 답변 기록에 저장한다. 업로드가 끝난 뒤에만
    // 표준 answers API 를 호출하며, 후속 저장 실패 시 미연결 업로드만 정리한다.
    ApiClient::FilePart filePart{
        QStringLiteral("file"),
        QFileInfo(audioPath).fileName(),
        QStringLiteral("audio/mp4"),
        audio
    };
    m_api->postMultipart(QStringLiteral("/api/file/upload"),
        {{QStringLiteral("kind"), QStringLiteral("AUDIO")},
         {QStringLiteral("refType"), QStringLiteral("INTERVIEW_ANSWER")}}, {filePart},
        [this, sid, generation, qid, text, audioPath, clientSubmissionId](
                bool ok, const QJsonValue& data, const QString& msg) {
            const QJsonObject uploaded = data.toObject();
            const qint64 fileId = uploaded.value("id").toInteger();
            if (!isCurrentSession(sid, generation)) {
                if (ok) deleteUnlinkedUpload(fileId);
                return;
            }
            const QString contentUrl = uploaded.value("contentUrl").toString();
            if (!ok || fileId <= 0 || contentUrl.isEmpty()) {
                failAnswerSubmission(qid, text,
                    msg.isEmpty() ? QStringLiteral("녹음 원본 업로드 실패") : msg,
                    fileId, true);
                return;
            }
            submitStoredAnswer(qid, sid, text, contentUrl, QString(), audioPath,
                               fileId, false, -1, -1, -1, QJsonObject(), QString(),
                               clientSubmissionId);
        });
}

void InterviewSession::startAnswerSubmission(qint64 qid, const QString& displayText,
                                             bool hasAudio, bool hasVideo)
{
    m_thread.push_back(QVariantMap{
        {"kind", "answer"}, {"qid", qid}, {"text", displayText},
        {"hasAudio", hasAudio}, {"hasVideo", hasVideo}, {"pending", true}});
    m_thread.push_back(QVariantMap{{"kind", "scoring"}, {"qid", qid}});
    m_scoring = true;
    emit busyChanged();
    emit threadChanged();
}

void InterviewSession::failAnswerSubmission(qint64 qid, const QString& retryText,
                                            const QString& message, qint64 uploadedFileId,
                                            bool restoreText)
{
    if (uploadedFileId > 0) deleteUnlinkedUpload(uploadedFileId);
    for (int i = m_thread.size() - 1; i >= 0; --i) {
        const QVariantMap item = m_thread.at(i).toMap();
        const bool pendingAnswer = item.value("kind") == QStringLiteral("answer")
                && item.value("pending").toBool()
                && item.value("qid").toLongLong() == qid;
        const bool scoringRow = item.value("kind") == QStringLiteral("scoring")
                && item.value("qid").toLongLong() == qid;
        if (pendingAnswer || scoringRow) m_thread.removeAt(i);
    }
    m_scoring = false;
    emit busyChanged();
    emit threadChanged();
    if (restoreText && !retryText.isEmpty()) emit answerSubmissionFailed(retryText);
    emit errorOccurred(message);
}

void InterviewSession::submitStoredAnswer(qint64 qid, int sessionId,
                                          const QString& answerText,
                                          const QString& audioUrl,
                                          const QString& videoUrl,
                                          const QString& localMediaPath,
                                          qint64 uploadedFileId,
                                          bool videoAnswer,
                                          int voiceScore,
                                          int visualScore,
                                          int videoCombined,
                                          const QJsonObject& avatarResult,
                                          const QString& nonverbalWarning,
                                          const QString& clientSubmissionId)
{
    const quint64 operationGeneration = m_sessionGeneration;
    const QString mediaKey = !localMediaPath.isEmpty()
        ? QString(videoAnswer ? QStringLiteral("VIDEO:") : QStringLiteral("AUDIO:"))
              + QDir::cleanPath(QFileInfo(localMediaPath).absoluteFilePath())
        : QStringLiteral("TEXT");
    const QString effectiveSubmissionId = clientSubmissionId.isEmpty()
        ? ensureClientSubmissionId(qid, answerText, mediaKey)
        : clientSubmissionId;
    QJsonObject body;
    body["answerText"] = answerText;
    body["clientSubmissionId"] = effectiveSubmissionId;
    if (!audioUrl.isEmpty()) body["audioUrl"] = audioUrl;
    if (!videoUrl.isEmpty()) body["videoUrl"] = videoUrl;
    if (uploadedFileId > 0) {
        if (videoAnswer) body["videoFileId"] = uploadedFileId;
        else if (!audioUrl.isEmpty()) body["audioFileId"] = uploadedFileId;
    }
    int effectiveVoiceScore = voiceScore;
    if (!videoAnswer && effectiveVoiceScore < 0)
        effectiveVoiceScore = m_voiceScoreByQuestion.value(qid, -1);
    if (effectiveVoiceScore >= 0) body["voiceScore"] = effectiveVoiceScore;
    if (visualScore >= 0) body["visualScore"] = visualScore;

    const auto operation = [this, sessionId, operationGeneration, qid, answerText, localMediaPath,
                            uploadedFileId, audioUrl, videoUrl, effectiveSubmissionId,
                            videoAnswer, effectiveVoiceScore, visualScore, videoCombined,
                            avatarResult, nonverbalWarning, body](
                                const ApiClient::Headers& headers) {
    if (!isCurrentSession(sessionId, operationGeneration)) {
        deleteUnlinkedUpload(uploadedFileId);
        return;
    }
    m_api->postDetailed(QStringLiteral("/api/interview/questions/%1/answers").arg(qid), body,
        headers,
        [this, sessionId, operationGeneration, qid, answerText, localMediaPath, uploadedFileId,
         audioUrl, videoUrl, effectiveSubmissionId,
         videoAnswer, effectiveVoiceScore, visualScore, videoCombined,
         avatarResult, nonverbalWarning](bool ok, const QJsonValue& data,
                                         const QString& msg, int httpStatus) {
            const QJsonObject answer = data.toObject();
            const QString submittedUrl = videoAnswer ? videoUrl : audioUrl;
            const QString savedUrl = answer.value(videoAnswer ? "videoUrl" : "audioUrl").toString();
            const bool replayedWithExistingMedia = ok && uploadedFileId > 0
                && !savedUrl.isEmpty() && savedUrl != submittedUrl;
            if (!isCurrentSession(sessionId, operationGeneration)) {
                // 서버가 이미 표준 답변을 저장했다면 그 답변이 참조하는 원본은 보존한다.
                if (!ok || replayedWithExistingMedia) deleteUnlinkedUpload(uploadedFileId);
                return;
            }
            if (!ok) {
                if (!shouldPreserveClientSubmissionId(httpStatus))
                    clearClientSubmissionId(effectiveSubmissionId);
                failAnswerSubmission(qid, answerText,
                    msg.isEmpty() ? QStringLiteral("답변 제출 실패") : msg,
                    uploadedFileId, !videoAnswer);
                return;
            }

            if (replayedWithExistingMedia) deleteUnlinkedUpload(uploadedFileId);
            clearClientSubmissionId(effectiveSubmissionId);
            const qint64 answerId = answer.value("id").toInteger();
            const qint64 linkedUploadId = replayedWithExistingMedia ? 0 : uploadedFileId;

            for (int i = m_thread.size() - 1; i >= 0; --i) {
                QVariantMap item = m_thread.at(i).toMap();
                if (item.value("kind") == QStringLiteral("scoring")
                        && item.value("qid").toLongLong() == qid) {
                    m_thread.removeAt(i);
                    continue;
                }
                if (item.value("kind") == QStringLiteral("answer")
                        && item.value("pending").toBool()
                        && item.value("qid").toLongLong() == qid) {
                    item["text"] = answerText;
                    item["pending"] = false;
                    item["answerId"] = answerId;
                    item["mediaFileId"] = linkedUploadId;
                    m_thread[i] = item;
                }
            }

            if (!localMediaPath.isEmpty()) {
                QStringList& files = videoAnswer ? m_videoFiles : m_audioFiles;
                if (!files.contains(localMediaPath)) files.push_back(localMediaPath);
                if (answerId > 0) {
                    const QString mediaKind = videoAnswer ? QStringLiteral("VIDEO") : QStringLiteral("AUDIO");
                    m_localMediaPathByAnswerKind.insert(
                        QString::number(answerId) + QStringLiteral(":") + mediaKind, localMediaPath);
                }
            }
            if (!videoAnswer && m_pendingAudioQuestionId == qid
                    && m_pendingAudioPath == localMediaPath) {
                m_pendingAudioPath.clear();
                m_pendingAudioQuestionId = -1;
            }

            int attachedVoiceScore = effectiveVoiceScore;
            if (!videoAnswer && m_voiceScoreByQuestion.contains(qid)) {
                const int queuedVoiceScore = m_voiceScoreByQuestion.take(qid);
                if (attachedVoiceScore < 0) {
                    attachedVoiceScore = queuedVoiceScore;
                    if (answerId > 0) saveVoiceAnalysis(qid, answerId, queuedVoiceScore);
                }
            }

            const int contentScore = answer.value("score").toInt(-1);
            QVariantList reviewItems = m_review.value("items").toList();
            for (int i = 0; i < reviewItems.size(); ++i) {
                QVariantMap reviewItem = reviewItems.at(i).toMap();
                if (reviewItem.value("questionId").toLongLong() != qid) continue;
                reviewItem["answerText"] = answerText;
                reviewItem["answerId"] = answerId;
                reviewItem[videoAnswer ? "videoUrl" : "audioUrl"] =
                        answer.value(videoAnswer ? "videoUrl" : "audioUrl").toString();
                reviewItem["score"] = contentScore;
                reviewItem["feedback"] = answer.value("feedback").toString();
                reviewItem["improvedAnswer"] = answer.value("improvedAnswer").toString();
                if (attachedVoiceScore >= 0) reviewItem["voiceScore"] = attachedVoiceScore;
                if (visualScore >= 0) reviewItem["visualScore"] = visualScore;
                reviewItems[i] = reviewItem;
                m_review["items"] = reviewItems;
                break;
            }
            m_thread.push_back(QVariantMap{
                {"kind", "score"}, {"qid", qid}, {"answerId", answerId}, {"score", contentScore},
                {"feedback", answer.value("feedback").toString()},
                {"improvedAnswer", answer.value("improvedAnswer").toString()},
                {"modelAnswer", ""}, {"voiceScore", attachedVoiceScore},
                {"visualScore", visualScore}, {"videoScore", videoCombined},
                {"hasAudioOriginal", !videoAnswer && !savedUrl.isEmpty()},
                {"hasVideoOriginal", videoAnswer && !savedUrl.isEmpty()}});

            m_scoring = false;
            if (!m_report.isEmpty() || !m_reportError.isEmpty()) {
                m_report.clear();
                m_reportError.clear();
                emit reportChanged();
            }
            emit busyChanged();
            emit threadChanged();
            if (!videoAnswer || videoCombined < 0) emit answerScored(contentScore);
            if (attachedVoiceScore >= 0 && !videoAnswer) emit voiceScored(attachedVoiceScore);
            if (videoAnswer) {
                if (!avatarResult.isEmpty() && answerId > 0)
                    saveVideoAnalysis(qid, answerId, answerText, avatarResult);
                if (videoCombined >= 0) emit videoScored(videoCombined);
                emit videoAnswerSubmitted();
            }
            if (!nonverbalWarning.isEmpty()) emit errorOccurred(nonverbalWarning);

            emit sidebarRefreshRequested();

            advanceAfterAnswer();
        });
    };
    if (m_aiCharge) {
        m_aiCharge->run(QStringLiteral("INTERVIEW_ANSWER_EVAL"), operation,
            [this, sessionId, operationGeneration, qid, answerText, uploadedFileId, videoAnswer](
                    const QString& message) {
                if (!isCurrentSession(sessionId, operationGeneration)) {
                    deleteUnlinkedUpload(uploadedFileId);
                    return;
                }
                // preview/정책 확인 단계에서는 표준 답변 API가 시작되지 않았으므로 UUID를
                // 유지해도 안전하지만, 같은 입력의 사용자 재시도 계약을 위해 그대로 보존한다.
                failAnswerSubmission(qid, answerText, message, uploadedFileId, !videoAnswer);
            });
    } else {
        operation({});
    }
}

void InterviewSession::advanceAfterAnswer()
{
    qint64 nextQid = -1;
    QString nextText;
    QHash<qint64, bool> answered;
    for (const QVariant& value : m_thread) {
        const QVariantMap item = value.toMap();
        if (item.value("kind") == QStringLiteral("score"))
            answered.insert(item.value("qid").toLongLong(), true);
    }
    for (const QVariant& value : m_thread) {
        const QVariantMap item = value.toMap();
        if (item.value("kind") != QStringLiteral("question")) continue;
        const qint64 id = item.value("qid").toLongLong();
        if (!answered.value(id, false)) {
            nextQid = id;
            nextText = item.value("text").toString();
            break;
        }
    }
    setCurrentQuestion(nextQid, nextText);
    refreshProgress();
    if (nextQid < 0) finishCompletedSession();
}

void InterviewSession::finishCompletedSession()
{
    emit sessionFinished();
    const int sid = m_sessionId;
    // 자동 저장 리포트가 첫 답변까지 포함하도록 review 를 먼저 다시 읽는다.
    m_api->get(QStringLiteral("/api/interview/sessions/%1/review").arg(sid),
        [this, sid](bool ok, const QJsonValue& data, const QString&) {
            if (sid != m_sessionId) return;
            if (ok) m_review = data.toObject().toVariantMap();
            // 유료 리포트 생성은 사용자가 리포트 버튼을 눌렀을 때만 실행한다. 자동 저장이
            // 켜져 있으면 그 명시적 생성의 reportChanged를 기다렸다가 한 번 저장한다.
            maybeAutoSave();
        });
}

void InterviewSession::deleteUnlinkedUpload(qint64 fileId)
{
    if (fileId <= 0) return;
    m_api->deleteResource(QStringLiteral("/api/file/%1").arg(fileId),
        [](bool, const QJsonValue&, const QString&) {});
}

void InterviewSession::deleteAnswerMedia(qint64 answerId, const QString& kind)
{
    const QString normalized = kind.trimmed().toUpper();
    if (answerId <= 0 || (normalized != QStringLiteral("AUDIO")
            && normalized != QStringLiteral("VIDEO"))) return;
    const int sid = m_sessionId;
    m_api->deleteResource(
        QStringLiteral("/api/interview/answers/%1/media/%2").arg(answerId).arg(normalized),
        [this, sid, answerId, normalized](bool ok, const QJsonValue&, const QString& msg) {
            if (sid != m_sessionId) return;
            if (!ok) {
                emit errorOccurred(msg.isEmpty()
                    ? QStringLiteral("원본 삭제에 실패했습니다") : msg);
                return;
            }
            for (int i = 0; i < m_thread.size(); ++i) {
                QVariantMap item = m_thread.at(i).toMap();
                if (item.value("answerId").toLongLong() != answerId) continue;
                if (item.value("kind") == QStringLiteral("answer")) {
                    item[normalized == QStringLiteral("AUDIO") ? "hasAudio" : "hasVideo"] = false;
                } else if (item.value("kind") == QStringLiteral("score")) {
                    item[normalized == QStringLiteral("AUDIO")
                            ? "hasAudioOriginal" : "hasVideoOriginal"] = false;
                }
                m_thread[i] = item;
            }
            const QString key = QString::number(answerId) + QStringLiteral(":") + normalized;
            const QString localPath = m_localMediaPathByAnswerKind.take(key);
            if (!localPath.isEmpty()) {
                const QString managedPath = managedLocalMediaPath(localPath);
                if (!managedPath.isEmpty()) QFile::remove(managedPath);
                if (normalized == QStringLiteral("AUDIO")) m_audioFiles.removeAll(localPath);
                else m_videoFiles.removeAll(localPath);
            }
            emit threadChanged();
            emit answerMediaDeleted(normalized);
        });
}

bool InterviewSession::followUpPending(qint64 questionId) const
{
    return m_followUpPendingQuestions.contains(questionId);
}

QVariantList InterviewSession::followUpPendingQuestionIds() const
{
    QVariantList ids;
    ids.reserve(m_followUpPendingQuestions.size());
    for (qint64 id : m_followUpPendingQuestions) ids.push_back(id);
    return ids;
}

bool InterviewSession::modelAnswerPending(qint64 questionId) const
{
    return m_modelAnswerPendingQuestions.contains(questionId);
}

QVariantList InterviewSession::modelAnswerPendingQuestionIds() const
{
    QVariantList ids;
    ids.reserve(m_modelAnswerPendingQuestions.size());
    for (qint64 id : m_modelAnswerPendingQuestions) ids.push_back(id);
    return ids;
}

void InterviewSession::finishFollowUpRequest(qint64 questionId)
{
    if (m_followUpPendingQuestions.remove(questionId) > 0)
        emit followUpPendingChanged();
}

void InterviewSession::finishModelAnswerRequest(qint64 questionId)
{
    if (m_modelAnswerPendingQuestions.remove(questionId) > 0)
        emit modelAnswerPendingChanged();
}

void InterviewSession::requestFollowUp(qint64 questionId)
{
    const bool pressureMode = m_mode == QStringLiteral("PRESSURE")
        || m_mode == QStringLiteral("압박 면접");
    if (!pressureMode || questionId < 0
        || m_followUpPendingQuestions.contains(questionId)) return;

    bool scoredQuestion = false;
    for (const QVariant& value : m_thread) {
        const QVariantMap item = value.toMap();
        if (item.value("kind") == QStringLiteral("score")
            && item.value("qid").toLongLong() == questionId) {
            scoredQuestion = true;
            break;
        }
    }
    if (!scoredQuestion) return;

    m_followUpPendingQuestions.insert(questionId);
    emit followUpPendingChanged();

    const int sid = m_sessionId;
    const quint64 generation = m_sessionGeneration;
    QString actionKey = m_followUpActionKeys.value(questionId);
    if (actionKey.isEmpty()) {
        actionKey = AiChargeCoordinator::createActionKey();
        m_followUpActionKeys.insert(questionId, actionKey);
    }
    QJsonObject body;
    body["count"] = 1;
    const auto operation = [this, sid, generation, questionId, actionKey, body](const ApiClient::Headers& headers) {
    if (!isCurrentSession(sid, generation) || !m_followUpPendingQuestions.contains(questionId)) return;
    m_api->postDetailed(QStringLiteral("/api/interview/questions/%1/follow-ups").arg(questionId), body,
        headers,
        [this, sid, generation, questionId, actionKey](bool ok, const QJsonValue& data,
                                                       const QString& msg, int httpStatus) {
            if (!isCurrentSession(sid, generation)) return;
            if (!ok || data.toArray().isEmpty()) {
                if (!ok && isAmbiguousOperationStatus(httpStatus)) {
                    reconcileFollowUp(sid, generation, questionId,
                        msg.isEmpty() ? QStringLiteral("꼬리질문 생성 결과를 확인하지 못했습니다.") : msg);
                    return;
                }
                finishFollowUpRequest(questionId);
                if (m_followUpActionKeys.value(questionId) == actionKey)
                    m_followUpActionKeys.remove(questionId);
                emit errorOccurred(msg.isEmpty() ? QStringLiteral("꼬리질문 생성 실패") : msg);
                return;
            }
            finishFollowUpRequest(questionId);
            if (m_followUpActionKeys.value(questionId) == actionKey)
                m_followUpActionKeys.remove(questionId);
            const QJsonObject q = data.toArray().first().toObject();
            appendQuestionItem(q);
            setCurrentQuestion(q.value("id").toInteger(), q.value("question").toString());
            emit threadChanged();
            refreshProgress();
        });
    };
    if (m_aiCharge) {
        m_aiCharge->runWithActionKey(QStringLiteral("INTERVIEW_FOLLOWUP_GEN"), actionKey, operation,
            [this, sid, generation, questionId](const QString&) {
                if (isCurrentSession(sid, generation)) finishFollowUpRequest(questionId);
            });
    } else {
        operation({});
    }
}

void InterviewSession::reconcileFollowUp(int sessionId, quint64 generation, qint64 questionId,
                                         const QString& message, int attempt)
{
    if (!isCurrentSession(sessionId, generation)) return;
    m_api->get(QStringLiteral("/api/interview/sessions/%1/questions").arg(sessionId),
        [this, sessionId, generation, questionId, message, attempt](
                bool ok, const QJsonValue& data, const QString&) {
            if (!isCurrentSession(sessionId, generation)) return;
            bool childExists = false;
            if (ok) {
                for (const QJsonValue& value : data.toArray()) {
                    if (value.toObject().value(QStringLiteral("parentQuestionId")).toInteger()
                        == questionId) {
                        childExists = true;
                        break;
                    }
                }
            }
            if (childExists) {
                m_followUpActionKeys.remove(questionId);
                finishFollowUpRequest(questionId);
                reloadThread();
                refreshProgress();
                return;
            }
            if (attempt < 2) {
                QTimer::singleShot(150 * (attempt + 1), this,
                    [this, sessionId, generation, questionId, message, attempt]() {
                        reconcileFollowUp(sessionId, generation, questionId, message, attempt + 1);
                    });
                return;
            }
            finishFollowUpRequest(questionId);
            emit errorOccurred(message + QStringLiteral(" 같은 요청 키로 다시 시도해 주세요."));
        });
}

void InterviewSession::requestModelAnswer(qint64 questionId)
{
    if (questionId < 0 || m_modelAnswerPendingQuestions.contains(questionId)) return;
    m_modelAnswerPendingQuestions.insert(questionId);
    emit modelAnswerPendingChanged();
    const int sid = m_sessionId;
    const quint64 generation = m_sessionGeneration;
    const auto operation = [this, sid, generation, questionId](const ApiClient::Headers& headers) {
    if (!isCurrentSession(sid, generation) || !m_modelAnswerPendingQuestions.contains(questionId)) return;
    m_api->post(QStringLiteral("/api/interview/questions/%1/model-answer").arg(questionId),
        QJsonObject(), headers,
        [this, sid, generation, questionId](bool ok, const QJsonValue& data, const QString& msg) {
            if (!isCurrentSession(sid, generation)) return;
            finishModelAnswerRequest(questionId);
            if (!ok) {
                emit errorOccurred(msg.isEmpty() ? QStringLiteral("모범답안 조회 실패") : msg);
                return;
            }
            const QString model = data.toObject().value("modelAnswer").toString();
            for (int i = 0; i < m_thread.size(); ++i) {
                QVariantMap it = m_thread[i].toMap();
                if (it.value("kind") == QStringLiteral("score")
                    && it.value("qid").toLongLong() == questionId) {
                    it["modelAnswer"] = model;
                    m_thread[i] = it;
                }
            }
            emit threadChanged();
        });
    };
    if (m_aiCharge) {
        m_aiCharge->run(QStringLiteral("INTERVIEW_MODEL_ANSWER"), operation,
            [this, sid, generation, questionId](const QString&) {
                if (isCurrentSession(sid, generation)) finishModelAnswerRequest(questionId);
            });
    } else {
        operation({});
    }
}

// ─────────────────────────── 음성 ───────────────────────────

void InterviewSession::transcribeAudio(const QString& filePath)
{
    if (m_currentQid < 0 || m_scoring || m_transcribing) return;
    QFile f(filePath);
    if (!f.open(QIODevice::ReadOnly)) {
        emit errorOccurred(QStringLiteral("녹음 파일을 열 수 없습니다"));
        return;
    }
    const QByteArray audio = f.readAll();
    f.close();

    const qint64 qid = m_currentQid;
    m_pendingAudioPath = filePath;
    m_pendingAudioQuestionId = qid;
    m_audioSourceByQuestion.insert(qid, filePath);
    m_voiceScoreByQuestion.remove(qid);
    m_transcribing = true;
    emit busyChanged();

    const int sid = m_sessionId;
    QJsonObject body;
    body["audioBase64"]  = QString::fromLatin1(audio.toBase64());
    body["audioFormat"]  = QStringLiteral("m4a");
    body["language"]     = QStringLiteral("ko");

    m_api->post(QStringLiteral("/api/interview/sessions/%1/voice-transcribe").arg(sid), body,
        [this, sid, qid, filePath, audio](bool ok, const QJsonValue& data, const QString& msg) {
            if (sid != m_sessionId) return;
            m_transcribing = false;
            emit busyChanged();
            if (!ok) {
                emit errorOccurred(msg.isEmpty()
                    ? QStringLiteral("전사 실패 — 추론 서버(serve) 상태를 확인하세요") : msg);
                return;
            }
            const QString text = data.toObject().value("text").toString().trimmed();
            if (text.isEmpty()) {
                emit errorOccurred(QStringLiteral("전사 결과가 비어 있습니다 — 녹음을 확인하고 다시 시도하세요"));
                return;
            }
            emit transcribed(text);

            // 전달력 채점(비동기, 실패해도 답변 흐름과 무관)
            QJsonObject sbody;
            sbody["audioBase64"]     = QString::fromLatin1(audio.toBase64());
            sbody["audioFormat"]     = QStringLiteral("m4a");
            sbody["transcriptChars"] = text.length();
            const auto scoreOperation = [this, sid, qid, filePath, sbody](
                    const ApiClient::Headers& headers) {
            if (sid != m_sessionId || m_audioSourceByQuestion.value(qid) != filePath) return;
            m_api->post(QStringLiteral("/api/interview/sessions/%1/voice-score").arg(sid), sbody,
                headers,
                [this, sid, qid, filePath](bool ok2, const QJsonValue& d2, const QString&) {
                    if (sid != m_sessionId || !ok2) return;
                    // 같은 질문을 다시 녹음했다면 이전 녹음의 늦은 점수를 버린다.
                    if (m_audioSourceByQuestion.value(qid) != filePath) return;
                    const int vscore = d2.toObject().value("score").toInt(-1);
                    if (vscore < 0) return;
                    // qid 를 캡처해 이전 질문의 마지막 카드가 아니라 이 녹음이 속한 답변에 연결한다.
                    recordVoiceScore(qid, vscore);
                    m_audioSourceByQuestion.remove(qid);
                });
            };
            if (m_aiCharge) {
                m_aiCharge->run(QStringLiteral("INTERVIEW_VOICE_SCORING"), scoreOperation,
                    [this, sid, qid, filePath](const QString&) {
                        if (sid == m_sessionId && m_audioSourceByQuestion.value(qid) == filePath)
                            m_audioSourceByQuestion.remove(qid);
                    });
            } else {
                scoreOperation({});
            }
        });
}

void InterviewSession::recordVoiceScore(qint64 questionId, int score)
{
    for (int i = m_thread.size() - 1; i >= 0; --i) {
        QVariantMap item = m_thread.at(i).toMap();
        if (item.value("kind") == QStringLiteral("score")
                && item.value("qid").toLongLong() == questionId) {
            item["voiceScore"] = score;
            m_thread[i] = item;
            const qint64 answerId = item.value("answerId").toLongLong();
            if (answerId > 0) saveVoiceAnalysis(questionId, answerId, score);
            emit threadChanged();
            emit voiceScored(score);
            return;
        }
    }
    // 표준 answers 응답보다 점수가 먼저 오면 버리지 않고 질문별로 보류한다.
    m_voiceScoreByQuestion.insert(questionId, score);
}

void InterviewSession::saveVoiceAnalysis(qint64 questionId, qint64 answerId, int score)
{
    if (m_sessionId < 0 || questionId < 0 || answerId <= 0 || score < 0) return;
    const int sid = m_sessionId;
    const QJsonObject body{
        {"kind", QStringLiteral("VOICE")},
        {"questionId", questionId},
        {"answerId", answerId},
        {"score", score},
        {"scoreDetail", QJsonObject{{"voiceScore", score}}}
    };
    m_api->post(QStringLiteral("/api/interview/sessions/%1/media-results").arg(sid), body,
        [this, sid](bool ok, const QJsonValue&, const QString& message) {
            if (sid != m_sessionId || ok) return;
            emit errorOccurred(message.isEmpty()
                ? QStringLiteral("전달력 점수를 답변에 연결해 저장하지 못했습니다.") : message);
        });
}

// ─────────────────────────── 영상 답변 (카메라 면접) ───────────────────────────

void InterviewSession::submitVideoAnswer(const QString& filePath, bool consented)
{
    if (m_sessionId < 0 || m_currentQid < 0 || m_scoring) return;
    if (!consented) {
        emit errorOccurred(QStringLiteral("영상 원본 저장·분석 동의가 필요합니다 — 동의 체크 후 다시 시도하세요"));
        return;
    }

    QFile f(filePath);
    if (!f.open(QIODevice::ReadOnly)) {
        emit errorOccurred(QStringLiteral("녹화 영상 파일을 열 수 없습니다"));
        return;
    }
    const QByteArray video = f.readAll();
    f.close();

    const qint64 qid = m_currentQid;
    const int    sid = m_sessionId;
    const QString mediaKey = QStringLiteral("VIDEO:")
        + QDir::cleanPath(QFileInfo(filePath).absoluteFilePath());
    const QString clientSubmissionId = ensureClientSubmissionId(qid, QString(), mediaKey);

    startAnswerSubmission(qid, QStringLiteral("영상 답변을 전사하고 있습니다…"), false, true);

    // 영상 컨테이너의 오디오 트랙을 먼저 STT 하여 실제 answerText 를 만든다. 빈 설명문을
    // 대신 저장하지 않고 표준 평가·리포트 경로로 보낸다.
    QJsonObject transcribeBody;
    transcribeBody["audioBase64"] = QString::fromLatin1(video.toBase64());
    transcribeBody["audioFormat"] = QStringLiteral("mp4");
    transcribeBody["language"] = QStringLiteral("ko");
    m_api->post(QStringLiteral("/api/interview/sessions/%1/voice-transcribe").arg(sid),
        transcribeBody,
        [this, sid, qid, filePath, video, clientSubmissionId](bool transcribedOk,
                                        const QJsonValue& transcribedData,
                                        const QString& transcribeMessage) {
            if (sid != m_sessionId) return;
            const QString transcript = transcribedData.toObject()
                    .value("text").toString().trimmed();
            if (!transcribedOk || transcript.isEmpty()) {
                failAnswerSubmission(qid, QString(),
                    transcribeMessage.isEmpty()
                        ? QStringLiteral("영상 음성 전사 실패 — 원본을 유지했으니 다시 시도하세요")
                        : transcribeMessage,
                    0, false);
                return;
            }

            for (int i = m_thread.size() - 1; i >= 0; --i) {
                QVariantMap item = m_thread.at(i).toMap();
                if (item.value("kind") == QStringLiteral("answer")
                        && item.value("pending").toBool()
                        && item.value("qid").toLongLong() == qid) {
                    item["text"] = transcript;
                    m_thread[i] = item;
                    emit threadChanged();
                    break;
                }
            }

            QJsonObject scoreBody;
            scoreBody["videoBase64"] = QString::fromLatin1(video.toBase64());
            scoreBody["videoFormat"] = QStringLiteral("mp4");
            scoreBody["transcriptChars"] = transcript.length();
            const auto continueAfterScore =
                [this, sid, qid, filePath, video, transcript, clientSubmissionId](
                    bool scoredOk, const QJsonValue& scoredData,
                    const QString& scoreMessage) {
                    if (sid != m_sessionId) return;
                    const QJsonObject avatarResult = scoredOk
                            ? scoredData.toObject() : QJsonObject();
                    const int combined = avatarResult.value("combined").toInt(-1);
                    const int voiceScore = avatarResult.value("voice")
                            .toObject().value("score").toInt(-1);
                    const int visualScore = avatarResult.value("visual").isObject()
                            ? avatarResult.value("visual").toObject()
                                  .value("score").toInt(-1)
                            : -1;
                    const QString warning = scoredOk ? QString()
                            : (scoreMessage.isEmpty()
                                ? QStringLiteral("영상 전달력 평가는 실패했지만 답변 원본과 내용 평가는 저장했습니다")
                                : QStringLiteral("영상 전달력 평가는 실패했지만 답변은 저장했습니다: %1")
                                      .arg(scoreMessage));

                    // 분석이 끝난 뒤 원본을 올려 pending file_asset 의 체류 시간을 줄인다.
                    // 제출 성공 시 서버가 audioFileId/videoFileId 를 INTERVIEW_ANSWER 로 원자 연결하고,
                    // 실패 시에만 ref_id 없는 업로드를 삭제한다. 로컬 mp4 는 재시도용으로 유지한다.
                    ApiClient::FilePart filePart{
                        QStringLiteral("file"), QFileInfo(filePath).fileName(),
                        QStringLiteral("video/mp4"), video
                    };
                    m_api->postMultipart(QStringLiteral("/api/file/upload"),
                        {{QStringLiteral("kind"), QStringLiteral("VIDEO")},
                         {QStringLiteral("refType"), QStringLiteral("INTERVIEW_ANSWER")}},
                        {filePart},
                        [this, sid, qid, filePath, transcript, avatarResult,
                         clientSubmissionId,
                         warning, voiceScore, visualScore, combined](
                                bool uploadedOk, const QJsonValue& uploadedData,
                                const QString& uploadMessage) {
                            const QJsonObject uploaded = uploadedData.toObject();
                            const qint64 fileId = uploaded.value("id").toInteger();
                            if (sid != m_sessionId) {
                                if (uploadedOk) deleteUnlinkedUpload(fileId);
                                return;
                            }
                            const QString videoUrl = uploaded.value("contentUrl").toString();
                            if (!uploadedOk || fileId <= 0 || videoUrl.isEmpty()) {
                                failAnswerSubmission(qid, QString(),
                                    uploadMessage.isEmpty()
                                        ? QStringLiteral("영상 원본 업로드 실패") : uploadMessage,
                                    fileId, false);
                                return;
                            }

                            // 비언어 점수 실패는 내용 답변 저장을 막지 않는다. 표준 answers API 가
                            // currentQid·진행률·완료·리포트의 유일한 정본이다.
                            submitStoredAnswer(qid, sid, transcript, QString(), videoUrl,
                                filePath, fileId, true, voiceScore, visualScore, combined,
                                avatarResult, warning, clientSubmissionId);
                        });
                };
            const auto scoreOperation = [this, sid, scoreBody, continueAfterScore](
                    const ApiClient::Headers& headers) {
                if (sid != m_sessionId) return;
                m_api->post(QStringLiteral("/api/interview/sessions/%1/avatar-score").arg(sid),
                            scoreBody, headers, continueAfterScore);
            };
            if (m_aiCharge) {
                m_aiCharge->run(QStringLiteral("INTERVIEW_VIDEO_ANALYSIS"), scoreOperation,
                    [continueAfterScore](const QString& message) {
                        // 비언어 분석이 차단되어도 이미 얻은 transcript의 내용 평가·원본 저장은 계속한다.
                        continueAfterScore(false, QJsonValue(), message);
                    });
            } else {
                scoreOperation({});
            }
        });
}

void InterviewSession::saveVideoAnalysis(qint64 questionId, qint64 answerId,
                                         const QString& transcript,
                                         const QJsonObject& avatarResult)
{
    const int combined = avatarResult.value("combined").toInt(-1);
    if (combined < 0 || questionId < 0 || answerId <= 0) return;

    QJsonArray transcriptLines;
    transcriptLines.push_back(QJsonObject{
        {"role", QStringLiteral("user")}, {"text", transcript}});
    QJsonObject metrics{
        {"voice", avatarResult.value("voice").toObject().value("metrics")},
        {"visual", avatarResult.value("visual").toObject().value("metrics")}};
    QJsonObject detail{
        {"voice", avatarResult.value("voice")},
        {"visual", avatarResult.value("visual")},
        {"overall", combined}};
    QJsonObject body{
        {"kind", QStringLiteral("AVATAR")},
        {"questionId", questionId},
        {"answerId", answerId},
        {"transcript", transcriptLines},
        {"metrics", metrics},
        {"score", combined},
        {"scoreDetail", detail}};
    const int sid = m_sessionId;
    m_api->post(QStringLiteral("/api/interview/sessions/%1/media-results").arg(sid),
        body, [this, sid](bool ok, const QJsonValue&, const QString& message) {
            if (sid != m_sessionId || ok) return;
            emit errorOccurred(message.isEmpty()
                ? QStringLiteral("영상 분석 지표를 답변에 연결해 저장하지 못했습니다.") : message);
        });
}

// ─────────────────────────── 리포트/내보내기 ───────────────────────────

void InterviewSession::loadReport()
{
    if (m_sessionId < 0 || m_reportLoadInFlight || !m_report.isEmpty()) return;
    const int sid = m_sessionId;
    const quint64 generation = m_sessionGeneration;
    m_reportLoadInFlight = true;
    if (!m_reportError.isEmpty()) {
        m_reportError.clear();
        emit reportChanged();
    }
    emit busyChanged();
    const auto operation = [this, sid, generation](const ApiClient::Headers& headers) {
    if (!isCurrentSession(sid, generation)) return;
    m_api->get(QStringLiteral("/api/interview/sessions/%1/report").arg(sid), headers,
        [this, sid, generation](bool ok, const QJsonValue& data, const QString& message) {
            if (!isCurrentSession(sid, generation)) return;
            m_reportLoadInFlight = false;
            emit busyChanged();
            if (!ok) {
                m_reportError = message.isEmpty()
                    ? QStringLiteral("리포트를 생성하지 못했습니다. 다시 시도해 주세요.") : message;
                emit reportChanged();
                return;
            }
            m_reportError.clear();
            m_report = data.toObject().toVariantMap();
            emit reportChanged();
            emit sidebarRefreshRequested();
        });
    };
    if (m_aiCharge) {
        m_aiCharge->run(QStringLiteral("INTERVIEW_REPORT"), operation,
            [this, sid, generation](const QString& message) {
                if (!isCurrentSession(sid, generation)) return;
                m_reportLoadInFlight = false;
                m_reportError = message.isEmpty()
                    ? QStringLiteral("리포트 이용 안내를 확인하지 못했습니다.") : message;
                emit busyChanged();
                emit reportChanged();
            });
    } else {
        operation({});
    }
}

QString InterviewSession::sessionFolder() const
{
    QString name = m_title;
    name.replace(QRegularExpression(QStringLiteral("[\\\\/:*?\"<>|]")), QStringLiteral("-"));
    name.replace(QStringLiteral("  "), QStringLiteral(" "));
    return m_store->saveDir() + QStringLiteral("/%1-%2").arg(name).arg(m_sessionId);
}

QString InterviewSession::reportMarkdown() const
{
    QString md;
    md += QStringLiteral("# %1 — 면접 리포트\n\n").arg(m_title);
    md += QStringLiteral("- 모드: %1\n- 세션 ID: %2\n- 저장 시각: %3\n\n")
              .arg(m_mode).arg(m_sessionId)
              .arg(QDateTime::currentDateTime().toString("yyyy-MM-dd HH:mm"));

    if (!m_report.isEmpty()) {
        md += QStringLiteral("## 종합 점수: %1점\n\n").arg(m_report.value("totalScore").toInt());
        const QVariantList cats = m_report.value("categories").toList();
        if (!cats.isEmpty()) {
            md += QStringLiteral("| 항목 | 점수 |\n|---|---|\n");
            for (const QVariant& c : cats) {
                const QVariantMap cm = c.toMap();
                md += QStringLiteral("| %1 | %2 |\n")
                          .arg(cm.value("label").toString())
                          .arg(cm.value("score").toInt());
            }
            md += QStringLiteral("\n");
        }
        const QVariantList fb = m_report.value("summaryFeedback").toList();
        if (!fb.isEmpty()) {
            md += QStringLiteral("## 총평\n\n");
            for (const QVariant& f : fb)
                md += QStringLiteral("- %1\n").arg(f.toString());
            md += QStringLiteral("\n");
        }
    }

    const QVariantList items = m_review.value("items").toList();
    if (!items.isEmpty()) {
        md += QStringLiteral("## 질문별 기록\n\n");
        int n = 0;
        for (const QVariant& v : items) {
            const QVariantMap it = v.toMap();
            md += QStringLiteral("### Q%1. %2\n\n").arg(++n).arg(it.value("question").toString());
            const QString ans = it.value("answerText").toString();
            if (!ans.isEmpty()) {
                md += QStringLiteral("**내 답변**\n\n%1\n\n").arg(ans);
                const QVariant sc = it.value("score");
                if (!sc.isNull() && sc.isValid())
                    md += QStringLiteral("**점수**: %1점\n\n").arg(sc.toInt());
                const QString fb = it.value("feedback").toString();
                if (!fb.isEmpty()) md += QStringLiteral("**피드백**\n\n%1\n\n").arg(fb);
                const QString imp = it.value("improvedAnswer").toString();
                if (!imp.isEmpty()) md += QStringLiteral("**개선 답변 제안**\n\n%1\n\n").arg(imp);
            } else {
                md += QStringLiteral("_미답변_\n\n");
            }
            const QString model = it.value("modelAnswer").toString();
            if (!model.isEmpty()) md += QStringLiteral("**모범답안**\n\n%1\n\n").arg(model);
        }
    }
    return md;
}

void InterviewSession::writeFile(const QString& path, const QByteArray& bytes, const QString& what)
{
    QFile f(path);
    if (!f.open(QIODevice::WriteOnly)) {
        emit errorOccurred(QStringLiteral("저장 실패: %1").arg(path));
        return;
    }
    f.write(bytes);
    f.close();
    emit exported(path, what);
}

void InterviewSession::exportReport(const QString& format)
{
    const QString dir = sessionFolder();
    QDir().mkpath(dir);
    const QString md = reportMarkdown();

    if (format == QStringLiteral("html")) {
        // 간단 변환: 마크다운을 <pre> 로 감싼 다크 테마 HTML (뷰어 없이도 열람)
        QString html = QStringLiteral(
            "<!DOCTYPE html><html lang=\"ko\"><head><meta charset=\"UTF-8\">"
            "<title>%1</title><style>body{background:#0e1014;color:#eceef2;"
            "font-family:'Segoe UI','Malgun Gothic',sans-serif;max-width:820px;"
            "margin:40px auto;padding:0 20px;line-height:1.7}"
            "pre{white-space:pre-wrap;font:inherit}</style></head>"
            "<body><pre>%2</pre></body></html>")
            .arg(m_title, md.toHtmlEscaped());
        writeFile(dir + QStringLiteral("/리포트.html"), html.toUtf8(), QStringLiteral("리포트 (HTML)"));
    } else {
        writeFile(dir + QStringLiteral("/리포트.md"), md.toUtf8(), QStringLiteral("리포트 (Markdown)"));
    }
}

void InterviewSession::exportAll()
{
    const QString dir = sessionFolder();
    QDir().mkpath(dir);

    // 1) 리포트
    writeFile(dir + QStringLiteral("/리포트.md"), reportMarkdown().toUtf8(), QStringLiteral("리포트"));

    // 2) 녹음 파일 복사
    int copied = 0;
    for (const QString& src : m_audioFiles) {
        const QString dst = dir + QStringLiteral("/") + QFileInfo(src).fileName();
        if (QFile::exists(dst)) QFile::remove(dst);
        if (QFile::copy(src, dst)) ++copied;
    }
    if (copied > 0)
        emit exported(dir, QStringLiteral("녹음 %1건").arg(copied));

    // 3) 제출 완료된 영상 원본 복사
    int copiedVideos = 0;
    for (const QString& src : m_videoFiles) {
        const QString dst = dir + QStringLiteral("/") + QFileInfo(src).fileName();
        if (QFile::exists(dst)) QFile::remove(dst);
        if (QFile::copy(src, dst)) ++copiedVideos;
    }
    if (copiedVideos > 0)
        emit exported(dir, QStringLiteral("영상 %1건").arg(copiedVideos));

    // 모바일·웹·이전 데스크톱 실행에서 저장된 원본도 review의 인증 URL을 통해 받는다.
    // 이번 실행의 로컬 원본이 있는 answer/kind는 이미 위에서 복사했으므로 중복 다운로드하지 않는다.
    exportServerMedia(dir);

    // 4) 회사분석/직무분석 문서
    if (m_caseId > 0) {
        const int sid = m_sessionId;
        m_api->get(QStringLiteral("/api/application-cases/%1/company-analysis").arg(m_caseId),
            [this, sid, dir](bool ok, const QJsonValue& data, const QString&) {
                if (sid != m_sessionId || !ok) return;
                const QJsonObject o = data.toObject();
                QString md = QStringLiteral("# 회사 분석\n\n");
                const auto add = [&md, &o](const char* key, const QString& label) {
                    const QString v = o.value(QLatin1String(key)).toString();
                    if (!v.isEmpty()) md += QStringLiteral("## %1\n\n%2\n\n").arg(label, v);
                };
                add("companySummary",  QStringLiteral("회사 요약"));
                add("industry",        QStringLiteral("산업"));
                add("recentIssues",    QStringLiteral("최근 이슈"));
                add("competitors",     QStringLiteral("경쟁사"));
                add("interviewPoints", QStringLiteral("면접 포인트"));
                add("verifiedFacts",   QStringLiteral("검증된 사실"));
                add("aiInferences",    QStringLiteral("AI 추론"));
                writeFile(dir + QStringLiteral("/회사분석.md"), md.toUtf8(), QStringLiteral("회사분석"));
            });
        m_api->get(QStringLiteral("/api/application-cases/%1/job-analysis").arg(m_caseId),
            [this, sid, dir](bool ok, const QJsonValue& data, const QString&) {
                if (sid != m_sessionId || !ok) return;
                const QJsonObject o = data.toObject();
                QString md = QStringLiteral("# 직무 분석\n\n");
                const auto add = [&md, &o](const char* key, const QString& label) {
                    const QString v = o.value(QLatin1String(key)).toString();
                    if (!v.isEmpty()) md += QStringLiteral("## %1\n\n%2\n\n").arg(label, v);
                };
                add("summary",         QStringLiteral("요약"));
                add("duties",          QStringLiteral("주요 업무"));
                add("requiredSkills",  QStringLiteral("필수 역량"));
                add("preferredSkills", QStringLiteral("우대 역량"));
                add("qualifications",  QStringLiteral("자격 요건"));
                add("difficulty",      QStringLiteral("난이도"));
                writeFile(dir + QStringLiteral("/직무분석.md"), md.toUtf8(), QStringLiteral("직무분석"));
            });
    }
}

void InterviewSession::maybeAutoSave()
{
    const int sessionId = m_sessionId;
    if (m_store && m_store->autoSave() && sessionId >= 0) {
        if (!m_report.isEmpty()) {
            exportReport(QStringLiteral("md"));
            return;
        }
        // 리포트 로드가 비동기라 약간 늦게 저장 — reportChanged 후 1회
        QObject* ctx = new QObject(this);
        connect(this, &InterviewSession::reportChanged, ctx, [this, ctx, sessionId]() {
            // 로그아웃/계정 전환 clear()도 reportChanged를 낸다. 그 신호로 session=-1의
            // 빈 리포트를 디스크에 쓰거나 이전 계정 파일을 덮어쓰지 않는다.
            if (m_sessionId == sessionId && sessionId >= 0 && !m_report.isEmpty())
                exportReport(QStringLiteral("md"));
            ctx->deleteLater();
        });
    }
}

QString InterviewSession::authenticatedMediaPath(const QString& mediaUrl) const
{
    const QUrl value(mediaUrl, QUrl::StrictMode);
    if (!value.isValid()) return {};
    if (value.isRelative()) {
        return mediaUrl.startsWith(QLatin1Char('/')) && !mediaUrl.startsWith(QStringLiteral("//"))
            ? mediaUrl : QString();
    }
    const QUrl base(m_api->baseUrl(), QUrl::StrictMode);
    if (value.scheme().compare(base.scheme(), Qt::CaseInsensitive) != 0
        || value.host().compare(base.host(), Qt::CaseInsensitive) != 0
        || value.port(value.scheme() == QStringLiteral("https") ? 443 : 80)
            != base.port(base.scheme() == QStringLiteral("https") ? 443 : 80)) {
        return {};
    }
    QString path = value.path(QUrl::FullyEncoded);
    if (value.hasQuery()) path += QStringLiteral("?") + value.query(QUrl::FullyEncoded);
    return path;
}

void InterviewSession::exportServerMedia(const QString& dir)
{
    const int sid = m_sessionId;
    const quint64 generation = m_sessionGeneration;
    const QVariantList reviewItems = m_review.value(QStringLiteral("items")).toList();
    for (const QVariant& value : reviewItems) {
        const QVariantMap item = value.toMap();
        const qint64 answerId = item.value(QStringLiteral("answerId")).toLongLong();
        if (answerId <= 0) continue;
        const auto queueDownload = [this, sid, generation, answerId, dir](
                const QString& kind, const QString& mediaUrl) {
            const QString localKey = QString::number(answerId) + QStringLiteral(":") + kind;
            if (m_localMediaPathByAnswerKind.contains(localKey)
                && QFile::exists(m_localMediaPathByAnswerKind.value(localKey))) return;
            const QString path = authenticatedMediaPath(mediaUrl);
            if (path.isEmpty()) return;
            m_api->download(path,
                [this, sid, generation, answerId, kind, dir](bool ok, const QByteArray& bytes,
                                                             const QString& contentType) {
                    if (!isCurrentSession(sid, generation) || !ok || bytes.isEmpty()) return;
                    QString extension;
                    const QString mime = contentType.section(QLatin1Char(';'), 0, 0).trimmed().toLower();
                    if (mime == QStringLiteral("audio/mp4")) extension = QStringLiteral("m4a");
                    else if (mime == QStringLiteral("audio/mpeg")) extension = QStringLiteral("mp3");
                    else if (mime == QStringLiteral("audio/wav")) extension = QStringLiteral("wav");
                    else if (mime == QStringLiteral("video/mp4")) extension = QStringLiteral("mp4");
                    else extension = QStringLiteral("bin");
                    const QString label = kind == QStringLiteral("AUDIO")
                        ? QStringLiteral("음성") : QStringLiteral("영상");
                    const QString target = QStringLiteral("%1/답변-%2-%3.%4")
                        .arg(dir).arg(answerId).arg(label, extension);
                    writeFile(target, bytes, QStringLiteral("%1 원본").arg(label));
                });
        };
        queueDownload(QStringLiteral("AUDIO"), item.value(QStringLiteral("audioUrl")).toString());
        queueDownload(QStringLiteral("VIDEO"), item.value(QStringLiteral("videoUrl")).toString());
    }
}
