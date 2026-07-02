#pragma once
#include <QObject>
#include <QJsonArray>
#include <QJsonObject>
#include <QVariantList>
#include <QString>

class ApiClient;

class CollaborationClient : public QObject
{
    Q_OBJECT
    Q_PROPERTY(QVariantList searchResults READ searchResults NOTIFY searchResultsChanged)
    Q_PROPERTY(QVariantList friends READ friends NOTIFY friendsChanged)
    Q_PROPERTY(QVariantList incomingRequests READ incomingRequests NOTIFY requestsChanged)
    Q_PROPERTY(QVariantList outgoingRequests READ outgoingRequests NOTIFY requestsChanged)
    Q_PROPERTY(QVariantList conversations READ conversations NOTIFY conversationsChanged)
    Q_PROPERTY(QVariantList messages READ messages NOTIFY messagesChanged)
    Q_PROPERTY(QVariantList pendingAttachments READ pendingAttachments NOTIFY pendingAttachmentsChanged)
    Q_PROPERTY(qint64 currentConversationId READ currentConversationId NOTIFY currentConversationChanged)
    Q_PROPERTY(QString currentPeerName READ currentPeerName NOTIFY currentConversationChanged)
    Q_PROPERTY(bool loading READ loading NOTIFY loadingChanged)
public:
    explicit CollaborationClient(ApiClient* api, QObject* parent = nullptr);

    QVariantList searchResults() const { return m_searchResults; }
    QVariantList friends() const { return m_friends; }
    QVariantList incomingRequests() const { return m_incomingRequests; }
    QVariantList outgoingRequests() const { return m_outgoingRequests; }
    QVariantList conversations() const { return m_conversations; }
    QVariantList messages() const { return m_messages; }
    QVariantList pendingAttachments() const { return m_pendingAttachments; }
    qint64 currentConversationId() const { return m_currentConversationId; }
    QString currentPeerName() const { return m_currentPeerName; }
    bool loading() const { return m_loading; }

    Q_INVOKABLE void clear();
    Q_INVOKABLE void refresh();
    Q_INVOKABLE void searchUsers(const QString& keyword);
    Q_INVOKABLE void sendFriendRequest(qint64 userId);
    Q_INVOKABLE void acceptRequest(qint64 requestId);
    Q_INVOKABLE void declineRequest(qint64 requestId);
    Q_INVOKABLE void removeFriend(qint64 userId);
    Q_INVOKABLE void openConversation(qint64 userId, const QString& peerName);
    Q_INVOKABLE void openConversationById(qint64 conversationId, const QString& peerName);
    Q_INVOKABLE void loadMessages(qint64 conversationId);
    Q_INVOKABLE void sendMessage(const QString& kind, const QString& content);
    Q_INVOKABLE void uploadAttachment(const QString& localUrl);
    Q_INVOKABLE void removePendingAttachment(int index);
    Q_INVOKABLE void downloadAttachment(qint64 fileId, const QString& originalName);

signals:
    void searchResultsChanged();
    void friendsChanged();
    void requestsChanged();
    void conversationsChanged();
    void messagesChanged();
    void pendingAttachmentsChanged();
    void currentConversationChanged();
    void loadingChanged();
    void errorOccurred(const QString& message);
    void info(const QString& title, const QString& message);
    void attachmentDownloaded(const QString& path);

private:
    void setLoading(bool loading);
    void loadFriends();
    void loadRequests();
    void loadConversations();
    QVariantMap userMap(const QJsonObject& user) const;
    QVariantMap conversationMap(const QJsonObject& conversation) const;
    QVariantMap messageMap(const QJsonObject& message) const;
    QVariantList toVariantList(const QJsonArray& array,
                               QVariantMap (CollaborationClient::*mapper)(const QJsonObject&) const) const;
    QString readableFileName(const QString& pathOrName) const;
    QString uniqueDownloadPath(const QString& originalName) const;

    ApiClient* m_api;
    QVariantList m_searchResults;
    QVariantList m_friends;
    QVariantList m_incomingRequests;
    QVariantList m_outgoingRequests;
    QVariantList m_conversations;
    QVariantList m_messages;
    QVariantList m_pendingAttachments;
    qint64 m_currentConversationId = -1;
    QString m_currentPeerName;
    bool m_loading = false;
};
