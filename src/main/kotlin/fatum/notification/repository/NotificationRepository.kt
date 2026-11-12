package fatum.notification.repository

import fatum.notification.model.Notification

/** Una página de la bandeja. `nextCursor` es nulo cuando ya no hay más. */
data class NotificationPage(val items: List<Notification>, val nextCursor: String?)

/**
 * Lo que el servicio necesita de la base. La implementación real es DynamoDB, pero el dominio no
 * la conoce: así los tests corren sin AWS y mañana se puede cambiar el motor sin tocar el servicio.
 */
interface NotificationRepository {

    /** Inserta o reemplaza. El `id` es determinista, así que un reintento sobrescribe el mismo ítem. */
    fun save(notification: Notification)

    fun findPage(userId: String, limit: Int, cursor: String?, unreadOnly: Boolean): NotificationPage

    fun countUnread(userId: String): Long

    /** Devuelve `false` si la notificación no existe o no es del usuario. */
    fun markRead(userId: String, id: String): Boolean

    /** Marca todas como leídas y devuelve cuántas cambiaron. */
    fun markAllRead(userId: String): Int

    fun delete(userId: String, id: String): Boolean
}
