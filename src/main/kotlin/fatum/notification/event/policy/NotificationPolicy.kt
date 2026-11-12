package fatum.notification.event.policy

import fatum.notification.event.PlatformEvent
import fatum.notification.model.Notification

/**
 * Una regla: "cuando llega este tipo de evento, estas notificaciones".
 *
 * <p>Añadir un evento nuevo es añadir una regla, no tocar el consumidor. Cada regla declara los
 * `detail-type` que atiende y el enrutador las reparte.</p>
 */
interface NotificationPolicy {

    /** Los `detail-type` de EventBridge que esta regla entiende. */
    val detailTypes: Set<String>

    /** Un evento puede producir cero, una o dos notificaciones (cliente y proveedor). */
    fun build(event: PlatformEvent): List<Notification>
}
