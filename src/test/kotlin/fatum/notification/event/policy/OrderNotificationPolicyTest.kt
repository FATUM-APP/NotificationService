package fatum.notification.event.policy

import fatum.notification.event.PlatformEvent
import fatum.notification.model.constant.NotificationType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper
import java.time.Instant

class OrderNotificationPolicyTest {

    private val mapper = ObjectMapper()
    private val factory = NotificationFactory(retentionDays = 60)
    private val policy = OrderNotificationPolicy(factory)

    private fun event(detailType: String, json: String, id: String = "evt-1") = PlatformEvent(
        source = "fatum.catalogservice",
        detailType = detailType,
        eventId = id,
        occurredAt = Instant.parse("2026-11-12T10:00:00Z"),
        detail = mapper.readTree(json)
    )

    private val orderJson = """
        {
          "orderId": "order-1",
          "clientId": "client-1",
          "providerId": "provider-1",
          "actorId": "client-1",
          "serviceTitle": "Plomería",
          "status": "PENDING",
          "booking": "2026-11-20T09:00",
          "updatedAt": "2026-11-12T10:00:00"
        }
    """.trimIndent()

    @Test
    fun `a new order only notifies the provider`() {
        val notifications = policy.build(event("ORDER_CREATED", orderJson))

        assertEquals(1, notifications.size)
        assertEquals("provider-1", notifications.first().userId)
        assertEquals(NotificationType.ORDER, notifications.first().type)
        assertEquals("order-1", notifications.first().data["orderId"])
    }

    @Test
    fun `an accepted order notifies only the client`() {
        val json = orderJson.replace("\"status\": \"PENDING\"", "\"status\": \"ACCEPTED\"")
            .replace("\"actorId\": \"client-1\"", "\"actorId\": \"provider-1\"")

        val notifications = policy.build(event("ORDER_STATUS_CHANGED", json))

        assertEquals(listOf("client-1"), notifications.map { it.userId })
        assertEquals("Solicitud aceptada", notifications.first().title)
    }

    @Test
    fun `a cancellation reaches both sides except whoever did it`() {
        val json = orderJson.replace("\"status\": \"PENDING\"", "\"status\": \"CANCELLED\"")

        val notifications = policy.build(event("ORDER_STATUS_CHANGED", json))

        // El actor es el cliente: solo se entera el proveedor.
        assertEquals(listOf("provider-1"), notifications.map { it.userId })
    }

    @Test
    fun `a confirmation goes to the party that still has to answer`() {
        val json = orderJson
            .replace("\"status\": \"PENDING\"", "\"status\": \"ACCEPTED\", \"previousStatus\": \"ACCEPTED\"")
            .dropLast(1) + """, "confirmation": "START", "party": "PROVIDER", "confirmed": true}"""

        val notifications = policy.build(event("ORDER_CONFIRMATION_CHANGED", json))

        assertEquals(listOf("client-1"), notifications.map { it.userId })
        assertTrue(notifications.first().body.contains("Falta tu confirmación"))
    }

    @Test
    fun `the same event twice produces the same notification id`() {
        val first = policy.build(event("ORDER_CREATED", orderJson, id = "evt-42"))
        val second = policy.build(event("ORDER_CREATED", orderJson, id = "evt-42"))

        assertEquals(first.first().id, second.first().id)
    }

    @Test
    fun `when both confirmed the delivery the text stops asking for a confirmation`() {
        val json = orderJson
            .replace("\"status\": \"PENDING\"", "\"status\": \"DELIVERING\", \"previousStatus\": \"DELIVERING\"")
            .dropLast(1) +
            """, "confirmation": "COMPLETION", "party": "PROVIDER", "confirmed": true, "bothConfirmed": true}"""

        val notifications = policy.build(event("ORDER_CONFIRMATION_CHANGED", json))

        assertEquals(listOf("client-1"), notifications.map { it.userId })
        assertTrue(notifications.first().body.contains("Siguiente paso: el pago"))
    }
}
