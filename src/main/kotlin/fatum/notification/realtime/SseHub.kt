package fatum.notification.realtime

import fatum.notification.dto.NotificationResponse
import fatum.notification.model.Notification
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.ConcurrentHashMap

/**
 * Las conexiones abiertas, agrupadas por usuario.
 *
 * <p>Cuando el consumidor de SQS guarda una notificación, se la escribe a todas las pestañas del
 * destinatario y a nadie más.</p>
 *
 * <p>Vive en memoria. Con una tarea por servicio alcanza: SQS entrega cada mensaje a una sola
 * instancia, así que la que lo guarda es la que tiene las conexiones. Si algún día hay varias
 * tareas, las conexiones se reparten entre ellas y un mensaje solo llega a las del azaroso
 * consumidor; ahí habría que pasar por un bus compartido (Redis pub/sub, por ejemplo).</p>
 */
@Component
class SseHub(private val properties: SseProperties) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val connections = ConcurrentHashMap<String, MutableSet<SseEmitter>>()

    fun subscribe(userId: String): SseEmitter {
        val emitter = SseEmitter(properties.timeoutMs)
        val userConnections = connections.computeIfAbsent(userId) { ConcurrentHashMap.newKeySet() }

        if (userConnections.size >= properties.maxConnectionsPerUser) {
            userConnections.firstOrNull()?.let { remove(userId, it) }
        }

        userConnections.add(emitter)
        emitter.onCompletion { remove(userId, emitter) }
        emitter.onTimeout { remove(userId, emitter) }
        emitter.onError { remove(userId, emitter) }

        send(emitter, SseEmitter.event().name("connected").data(mapOf("status" to "connected")))
        return emitter
    }

    /** Le manda la notificación al destinatario, si está conectado. */
    fun publish(notification: Notification) {
        val userId = notification.userId ?: return
        val response = runCatching { NotificationResponse.from(notification) }.getOrNull() ?: return

        connections[userId]?.toList()?.forEach { emitter ->
            try {
                send(emitter, SseEmitter.event().name("notification").data(response))
            } catch (e: Exception) {
                log.debug("Se cierra una conexión de {}: {}", userId, e.message)
                remove(userId, emitter)
            }
        }
    }

    @Scheduled(fixedRateString = "\${fatum.sse.heartbeat-ms:20000}")
    fun heartbeat() {
        connections.forEach { (userId, userConnections) ->
            userConnections.toList().forEach { emitter ->
                try {
                    send(emitter, SseEmitter.event().comment("ping"))
                } catch (e: Exception) {
                    remove(userId, emitter)
                }
            }
        }
    }

    internal fun connectionCount(userId: String): Int = connections[userId]?.size ?: 0

    private fun send(emitter: SseEmitter, event: SseEmitter.SseEventBuilder) {
        synchronized(emitter) {
            emitter.send(event)
        }
    }

    private fun remove(userId: String, emitter: SseEmitter) {
        connections[userId]?.remove(emitter)
        runCatching { emitter.complete() }
    }
}
