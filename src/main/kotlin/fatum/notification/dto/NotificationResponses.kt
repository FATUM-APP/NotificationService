package fatum.notification.dto

import fatum.notification.model.Notification
import fatum.notification.model.constant.NotificationType

data class NotificationResponse(
    val id: String,
    val type: NotificationType,
    val title: String,
    val body: String,
    val data: Map<String, String>,
    val read: Boolean,
    val createdAt: String
) {
    companion object {
        fun from(notification: Notification) = NotificationResponse(
            id = notification.id,
            type = notification.type,
            title = notification.title,
            body = notification.body,
            data = notification.data,
            read = notification.read,
            createdAt = notification.createdAt
        )
    }
}

data class NotificationPageResponse(
    val items: List<NotificationResponse>,
    val nextCursor: String?
)

data class UnreadCountResponse(val unread: Long)

data class MarkAllReadResponse(val updated: Int)
