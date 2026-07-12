#pragma once
#include <QObject>
#include <QJsonObject>
#include <QVariantList>
#include <QVariantMap>
#include <QString>

class ApiClient;

// 커뮤니티 게시판 REST 래퍼 — 글 목록·상세·댓글·좋아요.
// 웹 /community 화면과 같은 백엔드(/api/community/**)를 사용한다.
class CommunityClient : public QObject
{
    Q_OBJECT
    Q_PROPERTY(QVariantList postsModel READ postsModel NOTIFY postsChanged)
    Q_PROPERTY(int totalCount READ totalCount NOTIFY postsChanged)
    Q_PROPERTY(bool hasMore READ hasMore NOTIFY postsChanged)
    Q_PROPERTY(QString category READ category NOTIFY filterChanged)
    Q_PROPERTY(QString keyword READ keyword NOTIFY filterChanged)
    Q_PROPERTY(qint64 currentPostId READ currentPostId NOTIFY currentPostChanged)
    Q_PROPERTY(QVariantMap currentPost READ currentPost NOTIFY currentPostChanged)
    Q_PROPERTY(QVariantList comments READ comments NOTIFY commentsChanged)
    Q_PROPERTY(bool loading READ loading NOTIFY loadingChanged)
public:
    explicit CommunityClient(ApiClient* api, QObject* parent = nullptr);

    QVariantList postsModel() const { return m_posts; }
    int totalCount() const { return m_total; }
    bool hasMore() const { return m_posts.size() < m_total; }
    QString category() const { return m_category; }
    QString keyword() const { return m_keyword; }
    qint64 currentPostId() const { return m_currentPostId; }
    QVariantMap currentPost() const { return m_currentPost; }
    QVariantList comments() const { return m_comments; }
    bool loading() const { return m_loading; }

    Q_INVOKABLE void clear();
    // 글 목록 — page 0 이면 새로 로드, 1 이상이면 기존 목록 뒤에 이어붙인다(더보기).
    // category 빈 문자열 = 전체.
    Q_INVOKABLE void loadPosts(const QString& category, const QString& keyword, int page);
    Q_INVOKABLE void loadMore();
    // 글 상세 + 댓글을 함께 연다
    Q_INVOKABLE void openPost(qint64 postId);
    Q_INVOKABLE void closePost();
    // parentId 0 이면 최상위 댓글, 아니면 해당 댓글에 대한 답글
    Q_INVOKABLE void addComment(qint64 postId, const QString& content,
                                qint64 parentId = 0, bool anonymous = false);
    // 게시글 좋아요 토글 — 응답 active 로 상세/목록의 liked·likeCount 를 갱신한다
    Q_INVOKABLE void toggleLike(qint64 postId);
    // 글 작성 — 성공 시 목록을 새로 고치고 방금 쓴 글을 연다
    Q_INVOKABLE void createPost(const QString& category, const QString& title,
                                const QString& content, bool anonymous);

signals:
    void postsChanged();
    void filterChanged();
    void currentPostChanged();
    void commentsChanged();
    void loadingChanged();
    void errorOccurred(const QString& message);
    void info(const QString& title, const QString& message);

private:
    void setLoading(bool loading);
    void updateLoading();
    void finishDetailRequest(quint64 generation, qint64 postId);
    void loadComments(qint64 postId);
    QVariantMap postMap(const QJsonObject& post) const;
    QVariantMap commentMap(const QJsonObject& comment) const;

    ApiClient* m_api;
    QVariantList m_posts;
    QVariantList m_comments;
    QVariantMap m_currentPost;
    QString m_category;          // 빈 문자열 = 전체
    QString m_keyword;
    int m_page = 0;
    int m_total = 0;
    qint64 m_currentPostId = -1;
    bool m_loading = false;
    quint64 m_postsRequestGeneration = 0;
    quint64 m_detailRequestGeneration = 0;
    bool m_postsLoading = false;
    bool m_mutationLoading = false;
    int m_detailPendingRequests = 0;

    static constexpr int kPageSize = 20;
};
