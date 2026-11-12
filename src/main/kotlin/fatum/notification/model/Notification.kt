package fatum.notification.model

import fatum.notification.model.constant.NotificationType
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey
import java.time.Instant

/**
 * Una notificación tal como vive en la tabla.
 *
 * <p>La partición es el usuario y el orden es el `id`, que empieza con el instante de creación
 * (`2026-11-12T12:30:00.123Z#uuid`). Así "lo último primero" es una consulta directa por clave, sin
 * índices ni escaneos, y el `id` sirve a la vez como cursor de paginación.</p>
 *
 * <p>`expiresAt` es el TTL de DynamoDB: la notificación se borra sola cuando vence.</p>
 */
@DynamoDbBean
class Notification {

    /** A quién se le muestra. */
    @get:DynamoDbPartitionKey
    @get:DynamoDbAttribute("userId")
    var userId: String = ""

    /** `createdAt#uuid`. Es también la clave de orden y el cursor. */
    @get:DynamoDbSortKey
    @get:DynamoDbAttribute("id")
    var id: String = ""

    @get:DynamoDbAttribute("type")
    var type: NotificationType = NotificationType.SYSTEM

    @get:DynamoDbAttribute("title")
    var title: String = ""

    @get:DynamoDbAttribute("body")
    var body: String = ""

    /** Datos para que el front arme el enlace (orderId, catalogItemId, ...). */
    @get:DynamoDbAttribute("data")
    var data: Map<String, String> = emptyMap()

    @get:DynamoDbAttribute("read")
    var read: Boolean = false

    @get:DynamoDbAttribute("createdAt")
    var createdAt: String = Instant.now().toString()

    @get:DynamoDbAttribute("expiresAt")
    var expiresAt: Long = 0L

    /** De qué servicio vino (`fatum.catalogservice`, `fatum.userservice`). */
    @get:DynamoDbAttribute("source")
    var source: String = ""

    /** El `id` del evento de EventBridge: sirve para deduplicar reintentos de SQS. */
    @get:DynamoDbAttribute("eventId")
    var eventId: String = ""
}
