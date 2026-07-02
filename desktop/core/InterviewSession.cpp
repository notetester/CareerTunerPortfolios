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
    m_pendingAudioPath.clear();
    m_currentQid = -1;
    m_currentQText.clear();

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
                            m_thread.push_back(QVariantMap{
                                {"kind", "answer"}, {"text", answer}, {"hasAudio", false}});
                            m_thread.push_back(QVariantMap{
                                {"kind", "score"},
                                {"qid", qid},
                                {"score", rv.value("score").toInt(-1)},
                                {"feedback", rv.value("feedback").toString()},
                                {"improvedAnswer", rv.value("improvedAnswer").toString()},
                                {"modelAnswer", rv.value("modelAnswer").toString()},
                                {"voiceScore", -1}});
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
    const bool hasAudio = !m_pendingAudioPath.isEmpty();

    // 낙관적으로 답변 + 채점중 행을 먼저 그림
    m_thread.push_back(QVariantMap{{"kind", "answer"}, {"text", text}, {"hasAudio", hasAudio}});
    m_thread.push_back(QVariantMap{{"kind", "scoring"}});
    emit threadChanged();

    if (hasAudio) {
        m_audioFiles.push_back(m_pendingAudioPath);
        m_pendingAudioPath.clear();
    }

    m_scoring = true;
    emit busyChanged();

    QJsonObject body;
    body["answerText"] = text;
    m_api->post(QStringLiteral("/api/interview/questions/%1/answers").arg(qid), body,
        [this, sid, qid](bool ok, const QJsonValue& data, const QString& msg) {
            if (sid != m_sessionId) return;
            // 채점중 행 제거
            if (!m_thread.isEmpty()
                && m_thread.last().toMap().value("kind") == QStringLiteral("scoring"))
                m_thread.removeLast();

            m_scoring = false;
            emit busyChanged();

            if (!ok) {
                emit threadChanged();
                emit errorOccurred(msg.isEmpty() ? QStringLiteral("답변 제출 실패") : msg);
                return;
            }

            const QJsonObject a = data.toObject();
            const int score = a.value("score").toInt(-1);
            m_thread.push_back(QVariantMap{
                {"kind", "score"},
                {"qid", qid},
                {"score", score},
                {"feedback", a.value("feedback").toString()},
                {"improvedAnswer", a.value("improvedAnswer").toString()},
                {"modelAnswer", ""},
                {"voiceScore", -1}});

            // 다음 미답변 질문으로 이동 (스레드에서 탐색)
            qint64 nextQid = -1;
            QString nextText;
            QHash<qint64, bool> answered;
            for (const QVariant& v : m_thread) {
                const QVariantMap it = v.toMap();
                if (it.value("kind") == QStringLiteral("score"))
                    answered.insert(it.value("qid").toLongLong(), true);
            }
            for (const QVariant& v : m_thread) {
                const QVariantMap it = v.toMap();
                if (it.value("kind") == QStringLiteral("question")) {
                    const qint64 id = it.value("qid").toLongLong();
                    if (!answered.value(id, false)) { nextQid = id; nextText = it.value("text").toString(); break; }
                }
            }
            setCurrentQuestion(nextQid, nextText);
            emit threadChanged();
            emit answerScored(score);
            refreshProgress();

            if (nextQid < 0) {
                emit sessionFinished();
                loadReport();
                maybeAutoSave();
            }
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
    QFile f(filePath);
    if (!f.open(QIODevice::ReadOnly)) {
        emit errorOccurred(QStringLiteral("녹음 파일을 열 수 없습니다"));
        return;
    }
    const QByteArray audio = f.readAll();
    f.close();

    m_pendingAudioPath = filePath;
    m_transcribing = true;
    emit busyChanged();

    const int sid = m_sessionId;
    QJsonObject body;
    body["audioBase64"]  = QString::fromLatin1(audio.toBase64());
    body["audioFormat"]  = QStringLiteral("m4a");
    body["language"]     = QStringLiteral("ko");

    m_api->post(QStringLiteral("/api/interview/sessions/%1/voice-transcribe").arg(sid), body,
        [this, sid, audio](bool ok, const QJsonValue& data, const QString& msg) {
            if (sid != m_sessionId) return;
            m_transcribing = false;
            emit busyChanged();
            if (!ok) {
                emit errorOccurred(msg.isEmpty()
                    ? QStringLiteral("전사 실패 — 추론 서버(serve) 상태를 확인하세요") : msg);
                return;
            }
            const QString text = data.toObject().value("text").toString();
            emit transcribed(text);

            // 전달력 채점(비동기, 실패해도 답변 흐름과 무관)
            QJsonObject sbody;
            sbody["audioBase64"]     = QString::fromLatin1(audio.toBase64());
            sbody["audioFormat"]     = QStringLiteral("m4a");
            sbody["transcriptChars"] = text.length();
            m_api->post(QStringLiteral("/api/interview/sessions/%1/voice-score").arg(sid), sbody,
                [this, sid](bool ok2, const QJsonValue& d2, const QString&) {
                    if (sid != m_sessionId || !ok2) return;
                    const int vscore = d2.toObject().value("score").toInt(-1);
                    if (vscore < 0) return;
                    // 마지막 score 카드에 전달력 병기
                    for (int i = m_thread.size() - 1; i >= 0; --i) {
                        QVariantMap it = m_thread[i].toMap();
                        if (it.value("kind") == QStringLiteral("score")) {
                            it["voiceScore"] = vscore;
                            m_thread[i] = it;
                            emit threadChanged();
                            break;
                        }
                    }
                    emit voiceScored(vscore);
                });
        });
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

    // 3) 회사분석/직무분석 문서
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
