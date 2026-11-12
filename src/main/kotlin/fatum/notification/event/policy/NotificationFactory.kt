package fatum.notification.event.policy

import fatum.notification.event.PlatformEvent
import fatum.notification.model.Notification
import fatum.notification.model.constant.NotificationType
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.UUID

/**
 * Arma la notificación con un identificador que no cambia entre reintentos.
 *
 * <p>El `id` sale del evento, del destinatario y del tipo de hecho: si la misma entrega llega dos
 * veces, cae en la misma clave y se sobrescribe. No hay duplicados ni hace falta una tabla de
 * deduplicación.</p>
 */
@Component
class NotificationFactory(
    @Value("\${fatum.notifications.retention-days:60}") private val retentionDays: Long
) {

    fun build(
        event: PlatformEvent,
        userId: String,
        type: NotificationType,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ): Notification {
        val fingerprint = UUID.nameUUIDFromBytes(
            "${event.eventId}|$userId|${event.detailType}".toByteArray()
        ).toString()

        return Notification().apply {
            this.userId = userId
            this.id = "${event.occurredAt}#$fingerprint"
            this.type = type
            this.title = title
            this.body = body
            this.data = data
            this.read = false
            this.createdAt = event.occurredAt.toString()
            this.expiresAt = event.occurredAt.plus(Duration.ofDays(retentionDays)).epochSecond
            this.source = event.source
            this.eventId = event.eventId
        }
    }
}
