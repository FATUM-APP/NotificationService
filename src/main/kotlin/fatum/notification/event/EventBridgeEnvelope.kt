package fatum.notification.event

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import tools.jackson.databind.JsonNode

/**
 * Lo que llega a la cola: el sobre que EventBridge pone encima de cada evento.
 *
 * <p>El `id` del sobre es único por evento y se usa para deduplicar: si SQS reintenta una entrega,
 * la notificación se guarda con el mismo identificador y se sobrescribe en vez de duplicarse.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class EventBridgeEnvelope(
    @JsonProperty("source") val source: String,
    @JsonProperty("detail-type") val detailType: String,
    @JsonProperty("detail") val detail: JsonNode,
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("time") val time: String? = null
)
