#include "InterviewSession.h"
#include "ApiClient.h"
#include "SettingsStore.h"
#include <QJsonArray>
#include <QJsonObject>
#include <QJsonDocument>
#include <QFile>
#include <QFileInfo>
#include <QDir>
#include <QDateTime>
#include <QRegularExpression>

InterviewSession::InterviewSession(ApiClient* api, SettingsStore* store, QObject* parent)
    : QObject(parent), m_api(api), m_store(store) {}

// ─────────────────────────── 세션 열기 ───────────────────────────

void InterviewSession::open(int sessionId, const QString& title, const QString& mode, int caseId)
{
    m_sessionId = sessionId;
    m_caseId    = caseId;
    m_title     = title;
    m_mode      = mode;
    m_thread.clear();
    m_agentSteps.clear();
    m_report.clear();
    m_review.clear();
    m_audioFiles.clear();
    m_videoFiles.clear();
    m_localMediaPathByAnswerKind.clear();
    m_pendingAudioPath.clear();
    m_pendingAudioQuestionId = -1;
    m_audioSourceByQuestion.clear();
    m_voiceScoreByQuestion.clear();
    m_currentQid = -1;
    m_currentQText.clear();
    m_scoring = false;
    m_transcribing = false;

    emit sessionChanged();
    emit threadChanged();
    emit reportChanged();

    m_loading = true;
    emit busyChanged();

    reloadThread();
    refreshProgress();
    loadAgentSteps();
}

void InterviewSession::reloadThread()
{
    const int sid = m_sessionId;
    // 1) 질문 목록 (parentQuestionId → 꼬리질문 판별)
    m_api->get(QStringLiteral("/api/interview/sessions/%1/questions").arg(sid),
        [this, sid](bool okQ, const QJsonValue& qData, const QString&) {
            if (sid != m_sessionId) return; // 다른 세션으로 이동함
            QHash<qint64, QJsonObject> qById;
            QList<qint64> order;
            if (okQ) {
                for (const QJsonValue& v : qData.toArray()) {
                    const QJsonObject q = v.toObject();
                    const qint64 id = q.value("id").toInteger();
                    qById.insert(id, q);
                    order.push_back(id);
                }
            }
            // 2) review (답변·채점 병합)
            m_api->get(QStringLiteral("/api/interview/sessions/%1/review").arg(sid),
                [this, sid, qById, order](bool okR, const QJsonValue& rData, const QString&) {
                    if (sid != m_sessionId) return;
                    m_thread.clear();
                    QHash<qint64, QJsonObject> reviewByQid;
                    if (okR) {
                        m_review = rData.toObject().toVariantMap();
                        for (const QJsonValue& v : rData.toObject().value("items").toArray()) {
                            const QJsonObject it = v.toObject();
                            reviewByQid.insert(it.value("questionId").toInteger(), it);
                        }
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
                                {"voiceScore", -1}, {"visualScore", -1}, {"videoScore", -1},
                                {"hasAudioOriginal", hasAudio}, {"hasVideoOriginal", hasVideo}});
                        } else if (firstUnanswered < 0) {
                            firstUnanswered = qid;
                            firstUnansweredText = q.value("question").toString();
                        }
                    }
                    setCurrentQuestion(firstUnanswered, firstUnansweredText);
                    m_loading = false;
                    emit busyChanged();
                    emit threadChanged();
                });
        });
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
    m_api->get(QStringLiteral("/api/interview/sessions/%1/progress").arg(sid),
        [this, sid](bool ok, const QJsonValue& data, const QString&) {
            if (sid != m_sessionId || !ok) return;
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
    m_api->get(QStringLiteral("/api/interview/sessions/%1/agent-steps").arg(sid),
        [this, sid](bool ok, const QJsonValue& data, const QString&) {
            if (sid != m_sessionId) return;
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

void InterviewSession::generateQuestions()
{
    const int sid = m_sessionId;
    m_loading = true;
    emit busyChanged();
    m_api->post(QStringLiteral("/api/interview/sessions/%1/generate-questions").arg(sid),
        QJsonObject(),
        [this, sid](bool ok, const QJsonValue&, const QString& msg) {
            if (sid != m_sessionId) return;
            if (!ok) {
                m_loading = false;
                emit busyChanged();
                emit errorOccurred(msg.isEmpty() ? QStringLiteral("질문 생성 실패") : msg);
                return;
            }
            reloadThread();
            refreshProgress();
            loadAgentSteps();
        });
}

void InterviewSession::submitAnswer(const QString& text)
{
    if (m_currentQid < 0 || m_scoring || text.trimmed().isEmpty()) return;

    const qint64 qid = m_currentQid;
    const int sid = m_sessionId;
    const bool hasAudio = m_pendingAudioQuestionId == qid && !m_pendingAudioPath.isEmpty();
    const QString audioPath = hasAudio ? m_pendingAudioPath : QString();
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
        submitStoredAnswer(qid, sid, text, QString(), QString(), QString(), 0, false);
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
        [this, sid, qid, text, audioPath](bool ok, const QJsonValue& data, const QString& msg) {
            const QJsonObject uploaded = data.toObject();
            const qint64 fileId = uploaded.value("id").toInteger();
            if (sid != m_sessionId) {
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
                               fileId, false);
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
                                          const QString& nonverbalWarning)
{
    QJsonObject body;
    body["answerText"] = answerText;
    if (!audioUrl.isEmpty()) body["audioUrl"] = audioUrl;
    if (!videoUrl.isEmpty()) body["videoUrl"] = videoUrl;
    if (uploadedFileId > 0) {
        if (videoAnswer) body["videoFileId"] = uploadedFileId;
        else if (!audioUrl.isEmpty()) body["audioFileId"] = uploadedFileId;
    }

    m_api->post(QStringLiteral("/api/interview/questions/%1/answers").arg(qid), body,
        [this, sessionId, qid, answerText, localMediaPath, uploadedFileId,
         videoAnswer, voiceScore, visualScore, videoCombined,
         avatarResult, nonverbalWarning](bool ok, const QJsonValue& data, const QString& msg) {
            if (sessionId != m_sessionId) {
                // 서버가 이미 표준 답변을 저장했다면 그 답변이 참조하는 원본은 보존한다.
                if (!ok) deleteUnlinkedUpload(uploadedFileId);
                return;
            }
            if (!ok) {
                failAnswerSubmission(qid, answerText,
                    msg.isEmpty() ? QStringLiteral("답변 제출 실패") : msg,
                    uploadedFileId, !videoAnswer);
                return;
            }

            const QJsonObject answer = data.toObject();
            const qint64 answerId = answer.value("id").toInteger();

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
                    item["mediaFileId"] = uploadedFileId;
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

            int attachedVoiceScore = voiceScore;
            if (!videoAnswer && m_voiceScoreByQuestion.contains(qid))
                attachedVoiceScore = m_voiceScoreByQuestion.take(qid);

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
                {"hasAudioOriginal", !videoAnswer && uploadedFileId > 0},
                {"hasVideoOriginal", videoAnswer && uploadedFileId > 0}});

            m_scoring = false;
            emit busyChanged();
            emit threadChanged();
            if (!videoAnswer || videoCombined < 0) emit answerScored(contentScore);
            if (attachedVoiceScore >= 0 && !videoAnswer) emit voiceScored(attachedVoiceScore);
            if (videoAnswer) {
                if (!avatarResult.isEmpty()) saveVideoAnalysis(answerText, avatarResult);
                if (videoCombined >= 0) emit videoScored(videoCombined);
                emit videoAnswerSubmitted();
            }
            if (!nonverbalWarning.isEmpty()) emit errorOccurred(nonverbalWarning);

            advanceAfterAnswer();
        });
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
            // reportChanged 이전에 연결해야 매우 빠른 응답에서도 자동 저장을 놓치지 않는다.
            maybeAutoSave();
            loadReport();
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
                QFile::remove(localPath);
                if (normalized == QStringLiteral("AUDIO")) m_audioFiles.removeAll(localPath);
                else m_videoFiles.removeAll(localPath);
            }
            emit threadChanged();
            emit answerMediaDeleted(normalized);
        });
}

void InterviewSession::requestFollowUp()
{
    // 마지막으로 채점된 질문에 꼬리질문 1개
    qint64 lastScored = -1;
    for (auto it = m_thread.crbegin(); it != m_thread.crend(); ++it) {
        const QVariantMap m = it->toMap();
        if (m.value("kind") == QStringLiteral("score")) {
            lastScored = m.value("qid").toLongLong();
            break;
        }
    }
    if (lastScored < 0) return;

    const int sid = m_sessionId;
    QJsonObject body;
    body["count"] = 1;
    m_api->post(QStringLiteral("/api/interview/questions/%1/follow-ups").arg(lastScored), body,
        [this, sid](bool ok, const QJsonValue& data, const QString& msg) {
            if (sid != m_sessionId) return;
            if (!ok || data.toArray().isEmpty()) {
                emit errorOccurred(msg.isEmpty() ? QStringLiteral("꼬리질문 생성 실패") : msg);
                return;
            }
            const QJsonObject q = data.toArray().first().toObject();
            appendQuestionItem(q);
            setCurrentQuestion(q.value("id").toInteger(), q.value("question").toString());
            emit threadChanged();
            refreshProgress();
        });
}

void InterviewSession::requestModelAnswer(qint64 questionId)
{
    const int sid = m_sessionId;
    m_api->post(QStringLiteral("/api/interview/questions/%1/model-answer").arg(questionId),
        QJsonObject(),
        [this, sid, questionId](bool ok, const QJsonValue& data, const QString& msg) {
            if (sid != m_sessionId) return;
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
            m_api->post(QStringLiteral("/api/interview/sessions/%1/voice-score").arg(sid), sbody,
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
            emit threadChanged();
            emit voiceScored(score);
            return;
        }
    }
    // 표준 answers 응답보다 점수가 먼저 오면 버리지 않고 질문별로 보류한다.
    m_voiceScoreByQuestion.insert(questionId, score);
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

    startAnswerSubmission(qid, QStringLiteral("영상 답변을 전사하고 있습니다…"), false, true);

    // 영상 컨테이너의 오디오 트랙을 먼저 STT 하여 실제 answerText 를 만든다. 빈 설명문을
    // 대신 저장하지 않고 표준 평가·리포트 경로로 보낸다.
    QJsonObject transcribeBody;
    transcribeBody["audioBase64"] = QString::fromLatin1(video.toBase64());
    transcribeBody["audioFormat"] = QStringLiteral("mp4");
    transcribeBody["language"] = QStringLiteral("ko");
    m_api->post(QStringLiteral("/api/interview/sessions/%1/voice-transcribe").arg(sid),
        transcribeBody,
        [this, sid, qid, filePath, video](bool transcribedOk,
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
            m_api->post(QStringLiteral("/api/interview/sessions/%1/avatar-score").arg(sid),
                scoreBody,
                [this, sid, qid, filePath, video, transcript](
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
                                avatarResult, warning);
                        });
                });
        });
}

void InterviewSession::saveVideoAnalysis(const QString& transcript,
                                         const QJsonObject& avatarResult)
{
    const int combined = avatarResult.value("combined").toInt(-1);
    if (combined < 0) return;

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
        {"transcript", transcriptLines},
        {"metrics", metrics},
        {"score", combined},
        {"scoreDetail", detail}};
    m_api->post(QStringLiteral("/api/interview/sessions/%1/media-results").arg(m_sessionId),
        body, [](bool, const QJsonValue&, const QString&) {});
}

// ─────────────────────────── 리포트/내보내기 ───────────────────────────

void InterviewSession::loadReport()
{
    const int sid = m_sessionId;
    m_api->get(QStringLiteral("/api/interview/sessions/%1/report").arg(sid),
        [this, sid](bool ok, const QJsonValue& data, const QString&) {
            if (sid != m_sessionId || !ok) return;
            m_report = data.toObject().toVariantMap();
            emit reportChanged();
        });
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
    if (m_store && m_store->autoSave()) {
        // 리포트 로드가 비동기라 약간 늦게 저장 — reportChanged 후 1회
        QObject* ctx = new QObject(this);
        connect(this, &InterviewSession::reportChanged, ctx, [this, ctx]() {
            exportReport(QStringLiteral("md"));
            ctx->deleteLater();
        });
    }
}
