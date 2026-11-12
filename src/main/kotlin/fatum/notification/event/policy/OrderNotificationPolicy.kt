package fatum.notification.event.policy

import fatum.notification.event.PlatformEvent
import fatum.notification.model.Notification
import fatum.notification.model.constant.NotificationType
import org.springframework.stereotype.Component

/**
 * Los hechos de órdenes del CatalogService.
 *
 * <p>La regla general: se avisa a las dos partes de la orden, salvo a quien provocó el cambio
 * (`actorId`). Nadie necesita que le cuenten lo que acaba de hacer.</p>
 */
@Component
class OrderNotificationPolicy(
    private val factory: NotificationFactory
) : NotificationPolicy {

    override val detailTypes = setOf(ORDER_CREATED, ORDER_STATUS_CHANGED, ORDER_CONFIRMATION_CHANGED)

    override fun build(event: PlatformEvent): List<Notification> = when (event.detailType) {
        ORDER_CREATED -> onCreated(event)
        ORDER_STATUS_CHANGED -> onStatusChanged(event)
        ORDER_CONFIRMATION_CHANGED -> onConfirmationChanged(event)
        else -> emptyList()
    }

    private fun onCreated(event: PlatformEvent): List<Notification> {
        val providerId = event.required("providerId")
        val title = event.text("serviceTitle") ?: "un servicio"
        return listOf(
            notification(
                event, providerId,
                title = "Nueva solicitud de servicio",
                body = "Un cliente solicitó \"$title\" para el ${event.text("booking") ?: "día acordado"}.",
                data = event
            )
        )
    }

    private fun onStatusChanged(event: PlatformEvent): List<Notification> {
        val status = event.text("status") ?: return emptyList()
        val reason = event.text("reason")
        val title = event.text("serviceTitle") ?: "tu servicio"

        val (notificationTitle, notificationBody) = when (status) {
            "ACCEPTED" -> "Solicitud aceptada" to "El profesional aceptó tu solicitud de \"$title\"."
            "REJECTED" -> "Solicitud rechazada" to "El profesional rechazó tu solicitud de \"$title\"" + suffix(reason)
            "CANCELLED" -> "Orden cancelada" to "La orden de \"$title\" fue cancelada" + suffix(reason)
            "DELIVERING" -> "El servicio comenzó" to "Ambas partes confirmaron el inicio de \"$title\"."
            "COMPLETED" -> "Servicio completado" to "Ambas partes confirmaron el pago de \"$title\"."
            "DISPUTED" -> "Se abrió una disputa" to "Una de las partes reportó un problema con \"$title\"" + suffix(reason)
            "NOT_DELIVERED" -> "El servicio no se entregó" to "Ambas partes negaron la entrega de \"$title\"" + suffix(reason)
            "SOLVED" -> "Disputa resuelta" to "La disputa de \"$title\" quedó resuelta."
            "REFUNDED" -> "Reembolso registrado" to "Se registró un reembolso para \"$title\"."
            else -> "Actualización de tu orden" to "La orden de \"$title\" cambió a $status."
        }

        val recipients = when (status) {
            // El proveedor es quien respondió: solo se entera el cliente.
            "ACCEPTED", "REJECTED" -> listOf(event.text("clientId"))
            else -> listOf(event.text("clientId"), event.text("providerId"))
        }

        return recipients
            .filterNotNull()
            .filterNot { it == event.text("actorId") }
            .distinct()
            .map { notification(event, it, notificationTitle, notificationBody, event) }
    }

    private fun onConfirmationChanged(event: PlatformEvent): List<Notification> {
        val party = event.text("party") ?: return emptyList()
        val confirmed = event.bool("confirmed") ?: false
        val confirmation = event.text("confirmation") ?: "START"
        val title = event.text("serviceTitle") ?: "tu servicio"

        // El aviso es para la otra parte: la que todavía tiene que responder.
        val recipient = (if (party == "PROVIDER") event.text("clientId") else event.text("providerId"))
            ?: return emptyList()

        val step = when (confirmation) {
            "START" -> "el inicio"
            "COMPLETION" -> "la entrega"
            "PAYMENT" -> "el pago"
            else -> "el paso"
        }
        val who = if (party == "PROVIDER") "El profesional" else "El cliente"

        val bothConfirmed = event.bool("bothConfirmed") ?: false

        val (notificationTitle, notificationBody) = when {
            confirmed && bothConfirmed ->
                "$who confirmó $step" to "Ambas partes confirmaron $step de \"$title\". ${nextStep(confirmation)}"
            confirmed ->
                "$who confirmó $step" to "$who confirmó $step de \"$title\". Falta tu confirmación."
            else ->
                "$who negó $step" to "$who negó $step de \"$title\"" + suffix(event.text("reason"))
        }

        return listOf(notification(event, recipient, notificationTitle, notificationBody, event))
    }

    private fun notification(
        event: PlatformEvent,
        userId: String,
        title: String,
        body: String,
        data: PlatformEvent
    ): Notification = factory.build(
        event = event,
        userId = userId,
        type = NotificationType.ORDER,
        title = title,
        body = body,
        data = buildMap {
            data.text("orderId")?.let { put("orderId", it) }
            data.text("status")?.let { put("status", it) }
        }
    )

    private fun nextStep(confirmation: String): String = when (confirmation) {
        "START" -> "El servicio está en curso."
        "COMPLETION" -> "Siguiente paso: el pago."
        "PAYMENT" -> "La orden quedó completada."
        else -> ""
    }

    private fun suffix(reason: String?): String = reason?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: "."

    private companion object {
        const val ORDER_CREATED = "ORDER_CREATED"
        const val ORDER_STATUS_CHANGED = "ORDER_STATUS_CHANGED"
        const val ORDER_CONFIRMATION_CHANGED = "ORDER_CONFIRMATION_CHANGED"
    }
}
