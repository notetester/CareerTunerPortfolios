#include "CommunityClient.h"
#include "ApiClient.h"

#include <QJsonArray>
#include <QJsonObject>
#include <QUrl>

CommunityClient::CommunityClient(ApiClient* api, QObject* parent)
    : QObject(parent), m_api(api)
{
}

void CommunityClient::clear()
{
    ++m_postsRequestGeneration;
    ++m_detailRequestGeneration;
    m_postsLoading = false;
    m_mutationLoading = false;
    m_detailPendingRequests = 0;
    m_posts.clear();
    m_comments.clear();
    m_currentPost.clear();
    m_category.clear();
    m_keyword.clear();
    m_page = 0;
    m_total = 0;
    m_currentPostId = -1;
    updateLoading();
    emit postsChanged();
    emit filterChanged();
    emit currentPostChanged();
    emit commentsChanged();
}

void CommunityClient::loadPosts(const QString& category, const QString& keyword, int page)
{
    m_category = category;
    m_keyword = keyword.trimmed();
    m_page = qMax(0, page);
    emit filterChanged();

    QString path = QStringLiteral("/api/community/posts?sort=latest&page=%1&size=%2")
                       .arg(m_page).arg(kPageSize);
    if (!m_category.isEmpty())
        path += QStringLiteral("&category=%1")
                    .arg(QString::fromUtf8(QUrl::toPercentEncoding(m_category)));
    if (!m_keyword.isEmpty())
        path += QStringLiteral("&keyword=%1")
                    .arg(QString::fromUtf8(QUrl::toPercentEncoding(m_keyword)));

    m_postsLoading = true;
    updateLoading();
    const quint64 requestGeneration = ++m_postsRequestGeneration;
    const QString requestedCategory = m_category;
    const QString requestedKeyword = m_keyword;
    const int requestedPage = m_page;
    m_api->get(path, [this, requestGeneration, requestedCategory, requestedKeyword, requestedPage]
        (bool ok, const QJsonValue& data, const QString& message) {
        if (requestGeneration != m_postsRequestGeneration
            || requestedCategory != m_category
            || requestedKeyword != m_keyword
            || requestedPage != m_page) {
            return; // 필터/검색어/page가 바뀐 뒤 도착한 늦은 응답은 상태를 건드리지 않는다
        }
        m_postsLoading = false;
        updateLoading();
        if (!ok) {
            emit errorOccurred(message.isEmpty() ? QStringLiteral("게시글을 불러오지 못했습니다") : message);
            return;
        }

        const QJsonObject pageObj = data.toObject();
        // page 0 = 새 목록, 그 이상 = 더보기(뒤에 이어붙임)
        QVariantList out = requestedPage > 0 ? m_posts : QVariantList{};
        for (const QJsonValue& value : pageObj.value("posts").toArray())
            out.push_back(postMap(value.toObject()));
        m_posts = out;
        m_total = pageObj.value("total").toInt();
        emit postsChanged();
    });
}

void CommunityClient::loadMore()
{
    if (m_loading || !hasMore()) return;
    loadPosts(m_category, m_keyword, m_page + 1);
}

void CommunityClient::openPost(qint64 postId)
{
    if (postId <= 0) return;
    m_currentPostId = postId;
    m_currentPost.clear();
    m_comments.clear();
    emit currentPostChanged();
    emit commentsChanged();

    const quint64 detailGeneration = ++m_detailRequestGeneration;
    m_detailPendingRequests = 2;
    updateLoading();
    m_api->get(QStringLiteral("/api/community/posts/%1").arg(postId),
        [this, postId, detailGeneration](bool ok, const QJsonValue& data, const QString& message) {
            if (detailGeneration != m_detailRequestGeneration || postId != m_currentPostId) return;
            finishDetailRequest(detailGeneration, postId);
            if (!ok) {
                emit errorOccurred(message.isEmpty() ? QStringLiteral("게시글을 불러오지 못했습니다") : message);
                return;
            }
            m_currentPost = postMap(data.toObject());
            emit currentPostChanged();
        });
    loadComments(postId);
}

void CommunityClient::closePost()
{
    ++m_detailRequestGeneration;
    m_detailPendingRequests = 0;
    updateLoading();
    m_currentPostId = -1;
    m_currentPost.clear();
    m_comments.clear();
    emit currentPostChanged();
    emit commentsChanged();
}

void CommunityClient::addComment(qint64 postId, const QString& content,
                                 qint64 parentId, bool anonymous)
{
    const QString text = content.trimmed();
    if (postId <= 0 || text.isEmpty()) return;

    QJsonObject body;
    body["content"] = text;
    if (parentId > 0)
        body["parentId"] = parentId;
    body["anonymous"] = anonymous;

    m_api->post(QStringLiteral("/api/community/posts/%1/comments").arg(postId), body,
        [this, postId](bool ok, const QJsonValue&, const QString& message) {
            if (!ok) {
                emit errorOccurred(message.isEmpty() ? QStringLiteral("댓글을 등록하지 못했습니다") : message);
                return;
            }
            // 상세를 다시 받으면 조회수가 또 오르므로 댓글만 새로 받고 카운트는 로컬 보정
            if (postId == m_currentPostId) {
                loadComments(postId);
                if (!m_currentPost.isEmpty()) {
                    m_currentPost["commentCount"] = m_currentPost.value("commentCount").toInt() + 1;
                    emit currentPostChanged();
                }
            }
            for (int i = 0; i < m_posts.size(); ++i) {
                QVariantMap item = m_posts.at(i).toMap();
                if (item.value("id").toLongLong() != postId) continue;
                item["commentCount"] = item.value("commentCount").toInt() + 1;
                m_posts[i] = item;
                emit postsChanged();
                break;
            }
        });
}

void CommunityClient::toggleLike(qint64 postId)
{
    if (postId <= 0) return;
    QJsonObject body;
    body["targetType"] = QStringLiteral("POST");
    body["targetId"] = postId;
    body["reactionType"] = QStringLiteral("LIKE");

    m_api->post(QStringLiteral("/api/community/reactions"), body,
        [this, postId](bool ok, const QJsonValue& data, const QString& message) {
            if (!ok) {
                emit errorOccurred(message.isEmpty() ? QStringLiteral("좋아요 처리에 실패했습니다") : message);
                return;
            }
            const bool active = data.toObject().value("active").toBool();
            const int delta = active ? 1 : -1;
            if (postId == m_currentPostId && !m_currentPost.isEmpty()) {
                m_currentPost["liked"] = active;
                m_currentPost["likeCount"] = qMax(0, m_currentPost.value("likeCount").toInt() + delta);
                emit currentPostChanged();
            }
            for (int i = 0; i < m_posts.size(); ++i) {
                QVariantMap item = m_posts.at(i).toMap();
                if (item.value("id").toLongLong() != postId) continue;
                item["liked"] = active;
                item["likeCount"] = qMax(0, item.value("likeCount").toInt() + delta);
                m_posts[i] = item;
                emit postsChanged();
                break;
            }
        });
}

void CommunityClient::createPost(const QString& category, const QString& title,
                                 const QString& content, bool anonymous)
{
    const QString t = title.trimmed();
    const QString c = content.trimmed();
    if (category.isEmpty() || t.isEmpty() || c.isEmpty()) {
        emit errorOccurred(QStringLiteral("카테고리·제목·본문을 모두 입력하세요"));
        return;
    }

    QJsonObject body;
    body["category"] = category;
    body["title"] = t;
    body["content"] = c;
    body["anonymous"] = anonymous;
    body["tags"] = QJsonArray();

    m_mutationLoading = true;
    updateLoading();
    m_api->post(QStringLiteral("/api/community/posts"), body,
        [this](bool ok, const QJsonValue& data, const QString& message) {
            m_mutationLoading = false;
            updateLoading();
            if (!ok) {
                emit errorOccurred(message.isEmpty() ? QStringLiteral("글을 등록하지 못했습니다") : message);
                return;
            }
            emit info(QStringLiteral("글 등록"), QStringLiteral("게시글이 등록되었습니다"));
            loadPosts(m_category, m_keyword, 0);
            const qint64 postId = data.toObject().value("postId").toInteger();
            if (postId > 0)
                openPost(postId);
        });
}

void CommunityClient::setLoading(bool loading)
{
    if (m_loading == loading) return;
    m_loading = loading;
    emit loadingChanged();
}

void CommunityClient::updateLoading()
{
    setLoading(m_postsLoading || m_mutationLoading || m_detailPendingRequests > 0);
}

void CommunityClient::finishDetailRequest(quint64 generation, qint64 postId)
{
    if (generation != m_detailRequestGeneration || postId != m_currentPostId) return;
    if (m_detailPendingRequests > 0) --m_detailPendingRequests;
    updateLoading();
}

void CommunityClient::loadComments(qint64 postId)
{
    const quint64 detailGeneration = m_detailRequestGeneration;
    m_api->get(QStringLiteral("/api/community/posts/%1/comments").arg(postId),
        [this, postId, detailGeneration](bool ok, const QJsonValue& data, const QString& message) {
            if (detailGeneration != m_detailRequestGeneration || postId != m_currentPostId) return;
            finishDetailRequest(detailGeneration, postId);
            if (!ok) {
                emit errorOccurred(message.isEmpty() ? QStringLiteral("댓글을 불러오지 못했습니다") : message);
                return;
            }
            QVariantList out;
            for (const QJsonValue& value : data.toArray())
                out.push_back(commentMap(value.toObject()));
            m_comments = out;
            emit commentsChanged();
        });
}

QVariantMap CommunityClient::postMap(const QJsonObject& post) const
{
    const QJsonObject author = post.value("author").toObject();
    const QJsonObject stats = post.value("stats").toObject();
    QVariantList tags;
    for (const QJsonValue& value : post.value("tags").toArray())
        tags.push_back(value.toString());

    QVariantMap map{
        {"id", post.value("id").toInteger()},
        {"category", post.value("category").toString()},
        {"categoryLabel", post.value("categoryLabel").toString()},
        {"title", post.value("title").toString()},
        {"content", post.value("content").toString()},
        {"tags", tags},
        {"authorName", author.value("name").toString(QStringLiteral("익명"))},
        {"anonymous", author.value("isAnonymous").toBool()},
        {"viewCount", stats.value("viewCount").toInt()},
        {"commentCount", stats.value("commentCount").toInt()},
        {"likeCount", stats.value("likeCount").toInt()},
        {"bookmarkCount", stats.value("bookmarkCount").toInt()},
        {"createdAt", post.value("createdAt").toString()},
        {"companyName", post.value("companyName").toString()},
        {"jobRole", post.value("jobRole").toString()},
        // 상세 응답에만 있는 필드 — 목록에서는 기본값(false)
        {"liked", post.value("liked").toBool()},
        {"bookmarked", post.value("bookmarked").toBool()}
    };

    // 면접후기 상세에만 내려오는 부가 정보
    const QJsonObject review = post.value("interviewReview").toObject();
    if (!review.isEmpty()) {
        QVariantList questions;
        for (const QJsonValue& value : review.value("questions").toArray())
            questions.push_back(value.toString());
        map["interviewReview"] = QVariantMap{
            {"companyName", review.value("companyName").toString()},
            {"jobRole", review.value("jobRole").toString()},
            {"interviewType", review.value("interviewType").toString()},
            {"difficulty", review.value("difficulty").toInt()},
            {"interviewDate", review.value("interviewDate").toString()},
            {"resultStatus", review.value("resultStatus").toString()},
            {"questions", questions}
        };
    }
    return map;
}

QVariantMap CommunityClient::commentMap(const QJsonObject& comment) const
{
    const QJsonObject author = comment.value("author").toObject();
    return QVariantMap{
        {"id", comment.value("id").toInteger()},
        {"postId", comment.value("postId").toInteger()},
        {"parentId", comment.value("parentId").toInteger()},   // 최상위 댓글은 0
        {"mentionLabel", comment.value("mentionLabel").toString()},
        {"authorName", author.value("name").toString(QStringLiteral("익명"))},
        {"content", comment.value("content").toString()},
        {"likeCount", comment.value("likeCount").toInt()},
        {"isAuthor", comment.value("isAuthor").toBool()},      // 게시글 작성자(OP) 배지
        {"mine", comment.value("mine").toBool()},
        {"createdAt", comment.value("createdAt").toString()},
        {"liked", comment.value("liked").toBool()},
        {"isDeleted", comment.value("isDeleted").toBool()}     // 삭제 tombstone
    };
}
