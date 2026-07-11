#include "CollaborationClient.h"
#include "ApiClient.h"

#include <QDir>
#include <QFile>
#include <QFileInfo>
#include <QJsonArray>
#include <QJsonObject>
#include <QMimeDatabase>
#include <QRegularExpression>
#include <QSet>
#include <QStandardPaths>
#include <QStringList>
#include <QUrl>

CollaborationClient::CollaborationClient(ApiClient* api, QObject* parent)
    : QObject(parent), m_api(api)
{
}

QVariantList CollaborationClient::withoutAttachmentIds(
    const QVariantList& pending, const QSet<qint64>& sentFileIds)
{
    QVariantList remaining;
    remaining.reserve(pending.size());
    for (const QVariant& item : pending) {
        const qint64 fileId = item.toMap().value("id").toLongLong();
        if (!sentFileIds.contains(fileId))
            remaining.push_back(item);
    }
    return remaining;
}

QSet<qint64> CollaborationClient::attachmentIds(const QVariantList& attachments)
{
    QSet<qint64> ids;
    for (const QVariant& item : attachments) {
        const qint64 fileId = item.toMap().value("id").toLongLong();
        if (fileId > 0) ids.insert(fileId);
    }
    return ids;
}

QSet<qint64> CollaborationClient::cleanupAttachmentIds(
    const QVariantList& attachments, const QSet<qint64>& excludedIds)
{
    QSet<qint64> ids = attachmentIds(attachments);
    ids.subtract(excludedIds);
    return ids;
}

QSet<qint64> CollaborationClient::missingAttachmentIds(
    const QSet<qint64>& candidateIds, const QVariantList& attachments)
{
    QSet<qint64> ids = candidateIds;
    ids.subtract(attachmentIds(attachments));
    return ids;
}

void CollaborationClient::clear()
{
    discardPendingAttachments();
    m_searchResults.clear();
    m_friends.clear();
    m_incomingRequests.clear();
    m_outgoingRequests.clear();
    m_conversations.clear();
    m_discoverableRooms.clear();
    m_messages.clear();
    m_currentConversationId = -1;
    m_currentPeerName.clear();
    m_currentConversationType.clear();
    m_currentConversationMuted = false;
    emit searchResultsChanged();
    emit friendsChanged();
    emit requestsChanged();
    emit conversationsChanged();
    emit discoverableRoomsChanged();
    emit messagesChanged();
    emit currentConversationChanged();
}

void CollaborationClient::refresh()
{
    loadFriends();
    loadRequests();
    loadConversations();
    discoverRooms(QString());
}

void CollaborationClient::searchUsers(const QString& keyword)
{
    const QString q = keyword.trimmed();
    if (q.length() < 2) {
        m_searchResults.clear();
        emit searchResultsChanged();
        return;
    }
    setLoading(true);
    m_api->get(QStringLiteral("/api/collaboration/users/search?keyword=%1&limit=20")
                   .arg(QString::fromUtf8(QUrl::toPercentEncoding(q))),
        [this](bool ok, const QJsonValue& data, const QString& message) {
            setLoading(false);
            if (!ok) {
                emit errorOccurred(message.isEmpty() ? QStringLiteral("사용자 검색에 실패했습니다") : message);
                return;
            }
            m_searchResults = toVariantList(data.toArray(), &CollaborationClient::userMap);
            emit searchResultsChanged();
        });
}

void CollaborationClient::sendFriendRequest(qint64 userId)
{
    QJsonObject body;
    body["targetUserId"] = userId;
    m_api->post(QStringLiteral("/api/collaboration/friend-requests"), body,
        [this](bool ok, const QJsonValue&, const QString& message) {
            if (!ok) {
                emit errorOccurred(message.isEmpty() ? QStringLiteral("친구 요청을 보내지 못했습니다") : message);
                return;
            }
            emit info(QStringLiteral("친구 요청"), QStringLiteral("요청을 보냈습니다"));
            refresh();
        });
}

void CollaborationClient::acceptRequest(qint64 requestId)
{
    m_api->post(QStringLiteral("/api/collaboration/friend-requests/%1/accept").arg(requestId), QJsonObject(),
        [this](bool ok, const QJsonValue&, const QString& message) {
            if (!ok) {
                emit errorOccurred(message.isEmpty() ? QStringLiteral("친구 요청을 수락하지 못했습니다") : message);
                return;
            }
            emit info(QStringLiteral("친구 추가"), QStringLiteral("친구로 연결되었습니다"));
            refresh();
        });
}

void CollaborationClient::declineRequest(qint64 requestId)
{
    m_api->post(QStringLiteral("/api/collaboration/friend-requests/%1/decline").arg(requestId), QJsonObject(),
        [this](bool ok, const QJsonValue&, const QString& message) {
            if (!ok) {
                emit errorOccurred(message.isEmpty() ? QStringLiteral("친구 요청을 거절하지 못했습니다") : message);
                return;
            }
            refresh();
        });
}

void CollaborationClient::removeFriend(qint64 userId)
{
    m_api->deleteResource(QStringLiteral("/api/collaboration/friends/%1").arg(userId),
        [this](bool ok, const QJsonValue&, const QString& message) {
            if (!ok) {
                emit errorOccurred(message.isEmpty() ? QStringLiteral("친구를 삭제하지 못했습니다") : message);
                return;
            }
            refresh();
        });
}

void CollaborationClient::openConversation(qint64 userId, const QString& peerName)
{
    QJsonObject body;
    body["targetUserId"] = userId;
    m_api->post(QStringLiteral("/api/collaboration/conversations/direct"), body,
        [this, peerName](bool ok, const QJsonValue& data, const QString& message) {
            if (!ok) {
                emit errorOccurred(message.isEmpty() ? QStringLiteral("대화방을 열지 못했습니다") : message);
                return;
            }
            openConversationFromObject(data.toObject(), peerName);
            loadConversations();
        });
}

void CollaborationClient::openConversationById(qint64 conversationId, const QString& peerName,
                                               const QString& type, bool muted)
{
    if (m_currentConversationId > 0 && m_currentConversationId != conversationId)
        discardPendingAttachments();
    m_currentConversationId = conversationId;
    m_currentPeerName = peerName;
    m_currentConversationType = type;
    m_currentConversationMuted = muted;
    emit currentConversationChanged();
    loadMessages(conversationId);
}

void CollaborationClient::setConversationMuted(qint64 conversationId, bool muted)
{
    if (conversationId <= 0) return;
    QJsonObject body;
    body["muted"] = muted;
    m_api->patch(QStringLiteral("/api/collaboration/conversations/%1/mute").arg(conversationId), body,
        [this, conversationId](bool ok, const QJsonValue& data, const QString& message) {
            if (!ok) {
                emit errorOccurred(message.isEmpty() ? QStringLiteral("음소거 설정을 변경하지 못했습니다") : message);
                return;
            }
            // 응답은 ConversationSummaryResponse — 현재 열린 방이면 헤더 토글에 즉시 반영
            const QJsonObject conversation = data.toObject();
            if (conversationId == m_currentConversationId) {
                m_currentConversationMuted = conversation.value("muted").toBool();
                emit currentConversationChanged();
            }
            loadConversations();
        });
}

void CollaborationClient::discoverRooms(const QString& keyword)
{
    const QString q = keyword.trimmed();
    m_api->get(QStringLiteral("/api/collaboration/conversations/discover?keyword=%1&limit=30")
                   .arg(QString::fromUtf8(QUrl::toPercentEncoding(q))),
        [this](bool ok, const QJsonValue& data, const QString&) {
            if (!ok) return;
            m_discoverableRooms = toVariantList(data.toArray(), &CollaborationClient::conversationMap);
            emit discoverableRoomsChanged();
        });
}

void CollaborationClient::createRoom(const QString& type, const QString& title, const QString& password)
{
    QJsonObject body;
    body["type"] = type;
    body["title"] = title;
    if (!password.trimmed().isEmpty())
        body["password"] = password;
    body["memberUserIds"] = QJsonArray();

    m_api->post(QStringLiteral("/api/collaboration/conversations"), body,
        [this, title](bool ok, const QJsonValue& data, const QString& message) {
            if (!ok) {
                emit errorOccurred(message.isEmpty() ? QStringLiteral("채팅방을 만들지 못했습니다") : message);
                return;
            }
            openConversationFromObject(data.toObject(), title);
            loadConversations();
            discoverRooms(QString());
        });
}

void CollaborationClient::joinRoom(qint64 conversationId, const QString& password)
{
    QJsonObject body;
    if (!password.trimmed().isEmpty())
        body["password"] = password;

    m_api->post(QStringLiteral("/api/collaboration/conversations/%1/join").arg(conversationId), body,
        [this](bool ok, const QJsonValue& data, const QString& message) {
            if (!ok) {
                emit errorOccurred(message.isEmpty() ? QStringLiteral("채팅방에 참가하지 못했습니다") : message);
                return;
            }
            openConversationFromObject(data.toObject(), QStringLiteral("채팅방"));
            loadConversations();
            discoverRooms(QString());
        });
}

void CollaborationClient::inviteFriendToCurrentRoom(qint64 userId)
{
    if (m_currentConversationId <= 0 || m_currentConversationType == QStringLiteral("DIRECT")) return;
    QJsonObject body;
    QJsonArray ids;
    ids.append(userId);
    body["userIds"] = ids;

    m_api->post(QStringLiteral("/api/collaboration/conversations/%1/invites").arg(m_currentConversationId), body,
        [this](bool ok, const QJsonValue&, const QString& message) {
            if (!ok) {
                emit errorOccurred(message.isEmpty() ? QStringLiteral("초대하지 못했습니다") : message);
                return;
            }
            emit info(QStringLiteral("채팅방 초대"), QStringLiteral("초대를 보냈습니다"));
        });
}

void CollaborationClient::loadMessages(qint64 conversationId)
{
    if (conversationId <= 0) return;
    setLoading(true);
    m_api->get(QStringLiteral("/api/collaboration/conversations/%1/messages?limit=100").arg(conversationId),
        [this, conversationId](bool ok, const QJsonValue& data, const QString& message) {
            setLoading(false);
            if (conversationId != m_currentConversationId) return;
            if (!ok) {
                emit errorOccurred(message.isEmpty() ? QStringLiteral("메시지를 불러오지 못했습니다") : message);
                return;
            }
            m_messages = toVariantList(data.toArray(), &CollaborationClient::messageMap);
            emit messagesChanged();
            loadConversations();
        });
}

void CollaborationClient::sendMessage(const QString& kind,
                                      const QString& content,
                                      const QString& shareMode,
                                      int temporaryHours,
                                      const QString& postingIdsText)
{
    if (m_currentConversationId <= 0 || sendingMessage()) return;
    const qint64 conversationId = m_currentConversationId;
    QJsonObject body;
    body["kind"] = kind;
    body["content"] = content;
    body["attachmentShareMode"] = shareMode.isEmpty() ? QStringLiteral("TEMPORARY") : shareMode;
    body["temporaryHours"] = temporaryHours > 0 ? temporaryHours : 72;
    QJsonArray attachmentIds;
    QSet<qint64> sentAttachmentIds;
    for (const QVariant& item : m_pendingAttachments) {
        const qint64 fileId = item.toMap().value("id").toLongLong();
        attachmentIds.append(fileId);
        if (fileId > 0) sentAttachmentIds.insert(fileId);
    }
    body["attachmentFileIds"] = attachmentIds;
    body["sharedApplicationCaseIds"] = parseIdList(postingIdsText);

    InFlightMessage request;
    request.requestId = ++m_nextMessageRequestId;
    request.conversationId = conversationId;
    request.attachmentIds = sentAttachmentIds;
    request.baseUrl = m_api->baseUrl();
    request.bearerToken = m_api->token();
    m_inFlightMessage = request;
    emit sendingMessageChanged();

    m_api->post(QStringLiteral("/api/collaboration/conversations/%1/messages").arg(conversationId), body,
        [this, request, content, postingIdsText](
            bool ok, const QJsonValue&, const QString& message) {
            if (m_inFlightMessage.requestId != request.requestId) return;
            m_inFlightMessage = InFlightMessage{};
            emit sendingMessageChanged();
            if (!ok) {
                const QSet<qint64> orphanedIds =
                    missingAttachmentIds(request.attachmentIds, m_pendingAttachments);
                for (qint64 fileId : orphanedIds) {
                    m_api->deleteResourceWithToken(
                        QStringLiteral("/api/file/%1").arg(fileId), request.baseUrl,
                        request.bearerToken, nullptr);
                }
                emit errorOccurred(message.isEmpty() ? QStringLiteral("메시지를 보내지 못했습니다") : message);
                return;
            }
            const QVariantList remaining =
                withoutAttachmentIds(m_pendingAttachments, request.attachmentIds);
            if (remaining.size() != m_pendingAttachments.size()) {
                m_pendingAttachments = remaining;
                emit pendingAttachmentsChanged();
            }
            emit messageSent(request.conversationId, content, postingIdsText);
            if (request.conversationId == m_currentConversationId)
                loadMessages(request.conversationId);
            else
                loadConversations();
        });
}

void CollaborationClient::uploadAttachment(const QString& localUrl)
{
    const QUrl url(localUrl);
    const QString path = url.isLocalFile() ? url.toLocalFile() : localUrl;
    QFile file(path);
    if (!file.open(QIODevice::ReadOnly)) {
        emit errorOccurred(QStringLiteral("첨부 파일을 열 수 없습니다"));
        return;
    }

    QFileInfo info(path);
    QMimeDatabase mimeDb;
    const QString mime = mimeDb.mimeTypeForFile(info).name();
    ApiClient::FilePart filePart{
        QStringLiteral("file"),
        info.fileName(),
        mime.isEmpty() ? QStringLiteral("application/octet-stream") : mime,
        file.readAll()
    };
    file.close();

    QList<QPair<QString, QString>> fields{
        {QStringLiteral("kind"), QStringLiteral("ATTACHMENT")},
        {QStringLiteral("refType"), QStringLiteral("COLLAB_MESSAGE")}
    };
    QList<ApiClient::FilePart> files{filePart};
    const quint64 uploadGeneration = m_pendingGeneration;
    const QString uploadToken = m_api->token();
    m_api->postMultipart(QStringLiteral("/api/file/upload"), fields, files,
        [this, name = info.fileName(), uploadGeneration, uploadToken](
            bool ok, const QJsonValue& data, const QString& message) {
            if (!ok) {
                emit errorOccurred(message.isEmpty() ? QStringLiteral("첨부 업로드에 실패했습니다") : message);
                return;
            }
            const QJsonObject file = data.toObject();
            const qint64 fileId = file.value("id").toInteger();
            if (uploadGeneration != m_pendingGeneration) {
                // clear/logout 중 완료된 업로드는 UI에 되살리지 않고 업로드 때의 토큰으로 즉시 정리한다.
                m_api->deleteResourceWithToken(
                    QStringLiteral("/api/file/%1").arg(fileId), uploadToken, nullptr);
                return;
            }
            m_pendingAttachments.push_back(QVariantMap{
                {"id", fileId},
                {"name", file.value("originalName").toString(name)},
                {"sizeBytes", file.value("sizeBytes").toInteger()}
            });
            emit pendingAttachmentsChanged();
        });
}

void CollaborationClient::removePendingAttachment(int index)
{
    if (index < 0 || index >= m_pendingAttachments.size()) return;
    const qint64 fileId = m_pendingAttachments.at(index).toMap().value("id").toLongLong();
    if (m_inFlightMessage.attachmentIds.contains(fileId)) {
        // 전송 실패 시 callback이 captured token으로 정리하고, 성공 시 메시지가 소유권을 갖는다.
        m_pendingAttachments.removeAt(index);
        emit pendingAttachmentsChanged();
        return;
    }
    m_api->deleteResource(QStringLiteral("/api/file/%1").arg(fileId),
        [this, fileId](bool ok, const QJsonValue&, const QString& message) {
            if (!ok) {
                emit errorOccurred(message.isEmpty() ? QStringLiteral("첨부를 제거하지 못했습니다") : message);
                return;
            }
            for (int i = 0; i < m_pendingAttachments.size(); ++i) {
                if (m_pendingAttachments.at(i).toMap().value("id").toLongLong() == fileId) {
                    m_pendingAttachments.removeAt(i);
                    emit pendingAttachmentsChanged();
                    break;
                }
            }
        });
}

void CollaborationClient::clearPendingAttachments()
{
    discardPendingAttachments();
}

void CollaborationClient::discardPendingAttachments()
{
    ++m_pendingGeneration;
    const QVariantList pending = m_pendingAttachments;
    const QString cleanupToken = m_api->token();
    const QString cleanupBaseUrl = m_api->baseUrl();
    const QSet<qint64> cleanupIds =
        cleanupAttachmentIds(pending, m_inFlightMessage.attachmentIds);
    m_pendingAttachments.clear();
    emit pendingAttachmentsChanged();

    for (qint64 fileId : cleanupIds) {
        m_api->deleteResourceWithToken(
            QStringLiteral("/api/file/%1").arg(fileId), cleanupBaseUrl,
            cleanupToken, nullptr);
    }
}

void CollaborationClient::downloadAttachment(qint64 fileId, const QString& originalName)
{
    const QString target = uniqueDownloadPath(originalName);
    m_api->download(QStringLiteral("/api/collaboration/files/%1/content").arg(fileId),
        [this, target](bool ok, const QByteArray& bytes, const QString&) {
            if (!ok) {
                emit errorOccurred(QStringLiteral("첨부 파일을 내려받지 못했습니다"));
                return;
            }
            QFile out(target);
            QDir().mkpath(QFileInfo(target).absolutePath());
            if (!out.open(QIODevice::WriteOnly)) {
                emit errorOccurred(QStringLiteral("첨부 파일을 저장하지 못했습니다"));
                return;
            }
            out.write(bytes);
            out.close();
            emit attachmentDownloaded(target);
        });
}

void CollaborationClient::setLoading(bool loading)
{
    if (m_loading == loading) return;
    m_loading = loading;
    emit loadingChanged();
}

void CollaborationClient::loadFriends()
{
    m_api->get(QStringLiteral("/api/collaboration/friends"),
        [this](bool ok, const QJsonValue& data, const QString&) {
            if (!ok) return;
            QVariantList out;
            for (const QJsonValue& value : data.toArray()) {
                const QJsonObject item = value.toObject();
                QVariantMap map = userMap(item.value("user").toObject());
                map["friendsSince"] = item.value("friendsSince").toString();
                out.push_back(map);
            }
            m_friends = out;
            emit friendsChanged();
        });
}

void CollaborationClient::loadRequests()
{
    m_api->get(QStringLiteral("/api/collaboration/friend-requests/incoming"),
        [this](bool ok, const QJsonValue& data, const QString&) {
            if (!ok) return;
            QVariantList out;
            for (const QJsonValue& value : data.toArray()) {
                const QJsonObject item = value.toObject();
                QVariantMap map;
                map["id"] = item.value("id").toInteger();
                map["createdAt"] = item.value("createdAt").toString();
                map["user"] = userMap(item.value("requester").toObject());
                out.push_back(map);
            }
            m_incomingRequests = out;
            emit requestsChanged();
        });
    m_api->get(QStringLiteral("/api/collaboration/friend-requests/outgoing"),
        [this](bool ok, const QJsonValue& data, const QString&) {
            if (!ok) return;
            QVariantList out;
            for (const QJsonValue& value : data.toArray()) {
                const QJsonObject item = value.toObject();
                QVariantMap map;
                map["id"] = item.value("id").toInteger();
                map["createdAt"] = item.value("createdAt").toString();
                map["user"] = userMap(item.value("receiver").toObject());
                out.push_back(map);
            }
            m_outgoingRequests = out;
            emit requestsChanged();
        });
}

void CollaborationClient::loadConversations()
{
    m_api->get(QStringLiteral("/api/collaboration/conversations"),
        [this](bool ok, const QJsonValue& data, const QString&) {
            if (!ok) return;
            m_conversations = toVariantList(data.toArray(), &CollaborationClient::conversationMap);
            emit conversationsChanged();
        });
}

void CollaborationClient::openConversationFromObject(const QJsonObject& conversation, const QString& fallbackName)
{
    const qint64 conversationId = conversation.value("id").toInteger();
    if (m_currentConversationId > 0 && m_currentConversationId != conversationId)
        discardPendingAttachments();
    m_currentConversationId = conversationId;
    m_currentConversationType = conversation.value("type").toString();
    m_currentConversationMuted = conversation.value("muted").toBool();
    const QJsonObject peer = conversation.value("peer").toObject();
    const QString displayName = conversation.value("displayName").toString(
        peer.value("name").toString(fallbackName));
    m_currentPeerName = displayName.isEmpty() ? fallbackName : displayName;
    emit currentConversationChanged();
    loadMessages(m_currentConversationId);
}

QVariantMap CollaborationClient::userMap(const QJsonObject& user) const
{
    return QVariantMap{
        {"id", user.value("id").toInteger()},
        {"name", user.value("name").toString()},
        {"email", user.value("email").toString()},
        {"relationStatus", user.value("relationStatus").toString()}
    };
}

QVariantMap CollaborationClient::conversationMap(const QJsonObject& conversation) const
{
    const QJsonObject peer = conversation.value("peer").toObject();
    const QJsonObject latest = conversation.value("latestMessage").toObject();
    const QString displayName = conversation.value("displayName").toString(peer.value("name").toString());
    QString preview = latest.value("content").toString();
    if (preview.isEmpty() && !latest.isEmpty())
        preview = latest.value("kind").toString() == QStringLiteral("NOTE")
            ? QStringLiteral("쪽지")
            : QStringLiteral("첨부 파일");
    return QVariantMap{
        {"id", conversation.value("id").toInteger()},
        {"type", conversation.value("type").toString()},
        {"title", conversation.value("title").toString()},
        {"description", conversation.value("description").toString()},
        {"displayName", displayName},
        {"locked", conversation.value("locked").toBool()},
        {"memberCount", conversation.value("memberCount").toInt()},
        {"joined", conversation.value("joined").toBool()},
        {"muted", conversation.value("muted").toBool()},
        {"peer", userMap(peer)},
        {"peerName", displayName},
        {"latestPreview", preview},
        {"unreadCount", conversation.value("unreadCount").toInt()},
        {"updatedAt", conversation.value("updatedAt").toString()}
    };
}

QVariantMap CollaborationClient::messageMap(const QJsonObject& message) const
{
    QVariantList attachments;
    for (const QJsonValue& value : message.value("attachments").toArray()) {
        const QJsonObject file = value.toObject();
        attachments.push_back(QVariantMap{
            {"fileId", file.value("fileId").toInteger()},
            {"originalName", file.value("originalName").toString(QStringLiteral("file"))},
            {"contentType", file.value("contentType").toString()},
            {"sizeBytes", file.value("sizeBytes").toInteger()},
            {"shareMode", file.value("shareMode").toString()},
            {"availability", file.value("availability").toString()},
            {"expiresAt", file.value("expiresAt").toString()},
            {"downloadUrl", file.value("downloadUrl").toString()},
            // LOCAL 공유일 때만 서버가 세팅 — 소유자 데스크톱 온라인 여부(미세팅 시 false)
            {"ownerDesktopOnline", file.value("ownerDesktopOnline").toBool(false)}
        });
    }
    QVariantList postings;
    for (const QJsonValue& value : message.value("sharedPostings").toArray()) {
        const QJsonObject posting = value.toObject();
        postings.push_back(QVariantMap{
            {"applicationCaseId", posting.value("applicationCaseId").toInteger()},
            {"companyName", posting.value("companyName").toString()},
            {"jobTitle", posting.value("jobTitle").toString()},
            {"deadlineDate", posting.value("deadlineDate").toString()},
            {"sourceType", posting.value("sourceType").toString()}
        });
    }
    return QVariantMap{
        {"id", message.value("id").toInteger()},
        {"mine", message.value("mine").toBool()},
        {"kind", message.value("kind").toString()},
        {"content", message.value("content").toString()},
        {"createdAt", message.value("createdAt").toString()},
        {"sender", userMap(message.value("sender").toObject())},
        {"attachments", attachments},
        {"sharedPostings", postings}
    };
}

QJsonArray CollaborationClient::parseIdList(const QString& text) const
{
    QJsonArray out;
    const QStringList parts = text.split(QRegularExpression(QStringLiteral("[,\\s]+")), Qt::SkipEmptyParts);
    QSet<qint64> seen;
    for (const QString& part : parts) {
        bool ok = false;
        const qint64 id = part.toLongLong(&ok);
        if (ok && id > 0 && !seen.contains(id)) {
            seen.insert(id);
            out.append(id);
        }
    }
    return out;
}

QVariantList CollaborationClient::toVariantList(
    const QJsonArray& array,
    QVariantMap (CollaborationClient::*mapper)(const QJsonObject&) const) const
{
    QVariantList out;
    for (const QJsonValue& value : array)
        out.push_back((this->*mapper)(value.toObject()));
    return out;
}

QString CollaborationClient::readableFileName(const QString& pathOrName) const
{
    QString name = QFileInfo(pathOrName).fileName();
    if (name.trimmed().isEmpty()) name = QStringLiteral("attachment");
    return name.replace(QRegularExpression(QStringLiteral("[\\\\/:*?\"<>|\\r\\n]")), QStringLiteral("_"));
}

QString CollaborationClient::uniqueDownloadPath(const QString& originalName) const
{
    QString dir = QStandardPaths::writableLocation(QStandardPaths::DownloadLocation);
    if (dir.isEmpty())
        dir = QDir::homePath();
    const QString safe = readableFileName(originalName);
    QString path = QDir(dir).filePath(safe);
    QFileInfo info(path);
    int counter = 1;
    while (QFileInfo::exists(path)) {
        const QString base = info.completeBaseName().isEmpty() ? QStringLiteral("attachment") : info.completeBaseName();
        const QString suffix = info.suffix();
        const QString next = suffix.isEmpty()
            ? QStringLiteral("%1-%2").arg(base).arg(counter++)
            : QStringLiteral("%1-%2.%3").arg(base).arg(counter++).arg(suffix);
        path = QDir(dir).filePath(next);
    }
    return path;
}
