package fatum.notification.event

import tools.jackson.databind.JsonNode
import java.time.Instant
import java.util.UUID

/**
 * El evento ya normalizado: lo único que necesitan las reglas de notificación.
 *
 * <p>Lee el detalle sin reventar cuando un campo falta: un evento con un campo de menos debe dejar
 * de producir una notificación, no tumbar el consumidor entero.</p>
 */
data class PlatformEvent(
    val source: String,
    val detailType: String,
    val eventId: String,
    val occurredAt: Instant,
    val detail: JsonNode
) {

    fun text(field: String): String? =
        runCatching {
            detail.path(field).takeIf { !it.isMissingNode && !it.isNull }?.asString()
        }.getOrNull()

    fun bool(field: String): Boolean? =
        runCatching {
            detail.path(field).takeIf { !it.isMissingNode && !it.isNull }?.asBoolean()
        }.getOrNull()

    fun required(field: String): String =
        text(field) ?: throw IllegalArgumentException("El evento ${detailType} no trae '$field'")

    companion object {
        fun from(envelope: EventBridgeEnvelope): PlatformEvent = PlatformEvent(
            source = envelope.source,
            detailType = envelope.detailType,
            eventId = envelope.id ?: UUID.randomUUID().toString(),
            occurredAt = envelope.time?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.now(),
            detail = envelope.detail
        )
    }
}
