package fatum.notification.service

import fatum.notification.model.Notification
import fatum.notification.repository.NotificationPage
import fatum.notification.realtime.SseHub
import fatum.notification.repository.NotificationRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class NotificationService(
    private val repository: NotificationRepository,
    private val sseHub: SseHub
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Guarda lo que produjo un evento.
     *
     * <p>Una notificación que falla no tumba a las demás: si el cliente no se pudo guardar, el
     * proveedor igual se entera. Los fallos se registran para que la alarma de logs los vea.</p>
     */
    fun deliver(notifications: List<Notification>) {
        notifications.forEach { notification ->
            runCatching {
                repository.save(notification)
                // Al que está mirando la pantalla se le avisa al instante, sin esperar a que refresque.
                sseHub.publish(notification)
            }.onFailure { log.error("No se pudo guardar la notificación para {}", notification.userId, it) }
        }
    }

    fun list(userId: String, limit: Int, cursor: String?, unreadOnly: Boolean): NotificationPage =
        repository.findPage(userId, limit.coerceIn(1, MAX_PAGE_SIZE), cursor, unreadOnly)

    fun unreadCount(userId: String): Long = repository.countUnread(userId)

    fun markRead(userId: String, id: String): Boolean = repository.markRead(userId, id)

    fun markAllRead(userId: String): Int = repository.markAllRead(userId)

    fun delete(userId: String, id: String): Boolean = repository.delete(userId, id)

    private companion object {
        const val MAX_PAGE_SIZE = 100
    }
}
