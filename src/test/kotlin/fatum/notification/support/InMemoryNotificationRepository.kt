package fatum.notification.support

import fatum.notification.model.Notification
import fatum.notification.repository.NotificationPage
import fatum.notification.repository.NotificationRepository

/**
 * Misma semántica que DynamoDB (clave por usuario y orden descendente por `id`), sin AWS.
 */
class InMemoryNotificationRepository : NotificationRepository {

    private val items = linkedMapOf<String, Notification>()

    private fun key(notification: Notification) = "${notification.userId}|${notification.id}"

    override fun save(notification: Notification) {
        items[key(notification)] = notification
    }

    override fun findPage(
        userId: String,
        limit: Int,
        cursor: String?,
        unreadOnly: Boolean
    ): NotificationPage {
        val filtered = items.values
            .filter { it.userId == userId }
            .filter { !unreadOnly || !it.read }
            .sortedByDescending { it.id }
            .let { list -> if (cursor == null) list else list.filter { it.id < cursor } }

        val page = filtered.take(limit)
        return NotificationPage(page, if (filtered.size > limit) page.last().id else null)
    }

    override fun countUnread(userId: String): Long =
        items.values.count { it.userId == userId && !it.read }.toLong()

    override fun markRead(userId: String, id: String): Boolean {
        val notification = items["$userId|$id"] ?: return false
        notification.read = true
        return true
    }

    override fun markAllRead(userId: String): Int {
        val unread = items.values.filter { it.userId == userId && !it.read }
        unread.forEach { it.read = true }
        return unread.size
    }

    override fun delete(userId: String, id: String): Boolean = items.remove("$userId|$id") != null
}
