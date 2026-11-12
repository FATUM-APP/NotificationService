package fatum.notification.event.policy

import fatum.notification.event.PlatformEvent
import fatum.notification.model.Notification
import fatum.notification.model.constant.NotificationType
import org.springframework.stereotype.Component

/**
 * Los hechos de cuentas del UserService. Aquí el destinatario es siempre el mismo usuario:
 * nadie más necesita saber que su verificación cambió.
 */
@Component
class UserNotificationPolicy(
    private val factory: NotificationFactory
) : NotificationPolicy {

    override val detailTypes = setOf(
        VERIFICATION_CHANGED,
        BECAME_PROFESSIONAL,
        BECAME_CLIENT,
        ACTIVE_STATUS_CHANGED,
        PRINCIPAL_ADDRESS_CHANGED
    )

    override fun build(event: PlatformEvent): List<Notification> {
        val userId = event.text("userId") ?: return emptyList()

        val (title, body) = when (event.detailType) {
            VERIFICATION_CHANGED -> {
                val status = event.text("verificationStatus") ?: "actualizado"
                "Tu verificación cambió" to "El estado de verificación de tu cuenta ahora es $status."
            }
            BECAME_PROFESSIONAL -> "Ahora eres profesional" to "Ya puedes publicar servicios en Fatum."
            BECAME_CLIENT -> "Tu cuenta pasó a cliente" to "Ya no puedes publicar servicios. Tus servicios existentes quedaron inactivos."
            ACTIVE_STATUS_CHANGED -> {
                val active = event.bool("isActive") ?: true
                if (active) "Tu cuenta fue activada" to "Ya puedes volver a usar Fatum."
                else "Tu cuenta fue desactivada" to "No podrás usar Fatum mientras la cuenta esté desactivada."
            }
            PRINCIPAL_ADDRESS_CHANGED -> {
                val alias = event.text("addressAlias") ?: "otra"
                "Tu dirección principal cambió" to "Ahora la dirección que te representa es \"$alias\"."
            }
            else -> return emptyList()
        }

        return listOf(
            factory.build(
                event = event,
                userId = userId,
                type = NotificationType.ACCOUNT,
                title = title,
                body = body
            )
        )
    }

    private companion object {
        const val VERIFICATION_CHANGED = "USER_VERIFICATION_CHANGED"
        const val BECAME_PROFESSIONAL = "USER_BECAME_PROFESSIONAL"
        const val BECAME_CLIENT = "PROFESSIONAL_BECAME_CLIENT"
        const val ACTIVE_STATUS_CHANGED = "USER_ACTIVE_STATUS_CHANGED"
        const val PRINCIPAL_ADDRESS_CHANGED = "PROFESSIONAL_PRINCIPAL_ADDRESS_CHANGED"
    }
}
