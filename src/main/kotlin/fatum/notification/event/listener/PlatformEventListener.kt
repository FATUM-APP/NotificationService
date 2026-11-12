package fatum.notification.event.listener

import fatum.notification.event.EventBridgeEnvelope
import fatum.notification.event.NotificationRouter
import fatum.notification.event.PlatformEvent
import fatum.notification.service.NotificationService
import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * Escucha la cola a la que EventBridge deja caer los hechos de la plataforma.
 *
 * <p>Si el mensaje no se puede leer se registra y se descarta: reintentarlo no lo va a arreglar y
 * solo llena la cola de mensajes venenosos. Los errores de base sí se lanzan, para que SQS reintente
 * y, agotados los intentos, termine en la cola de muertos.</p>
 */
@Component
class PlatformEventListener(
    private val objectMapper: ObjectMapper,
    private val router: NotificationRouter,
    private val notificationService: NotificationService
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @SqsListener("\${fatum.notifications.queue-name}")
    fun onMessage(message: String) {
        val envelope = try {
            objectMapper.readValue(message, EventBridgeEnvelope::class.java)
        } catch (e: Exception) {
            log.error("Mensaje ilegible, se descarta: {}", message.take(500), e)
            return
        }

        val event = PlatformEvent.from(envelope)
        val notifications = router.route(event)

        if (notifications.isEmpty()) {
            log.debug("El evento {} no generó notificaciones", event.detailType)
            return
        }

        notificationService.deliver(notifications)
        log.info("Evento {} -> {} notificación(es)", event.detailType, notifications.size)
    }
}
