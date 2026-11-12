package fatum.notification.event

import fatum.notification.event.policy.NotificationPolicy
import fatum.notification.model.Notification
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Reparte cada evento a la regla que lo entiende.
 *
 * <p>Las reglas se inyectan solas: para que un `detail-type` nuevo empiece a producir
 * notificaciones basta con añadir una clase que implemente [NotificationPolicy].</p>
 */
@Component
class NotificationRouter(policies: List<NotificationPolicy>) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val byDetailType: Map<String, NotificationPolicy> =
        policies.flatMap { policy -> policy.detailTypes.map { it to policy } }.toMap()

    fun route(event: PlatformEvent): List<Notification> {
        val policy = byDetailType[event.detailType]
        if (policy == null) {
            // No es un error: el bus puede traer hechos que este servicio todavía no atiende.
            log.debug("Sin regla para {} ({}), se ignora", event.detailType, event.source)
            return emptyList()
        }
        return policy.build(event)
    }
}
