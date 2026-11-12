package fatum.notification.event

import fatum.notification.event.policy.NotificationFactory
import fatum.notification.event.policy.OrderNotificationPolicy
import fatum.notification.event.policy.UserNotificationPolicy
import fatum.notification.model.constant.NotificationType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper
import java.time.Instant

class NotificationRouterTest {

    private val mapper = ObjectMapper()
    private val factory = NotificationFactory(retentionDays = 30)
    private val router = NotificationRouter(
        listOf(OrderNotificationPolicy(factory), UserNotificationPolicy(factory))
    )

    private fun event(detailType: String, json: String, source: String = "fatum.userservice") =
        PlatformEvent(source, detailType, "evt-1", Instant.parse("2026-11-12T10:00:00Z"), mapper.readTree(json))

    @Test
    fun `user events notify the account owner`() {
        val notifications = router.route(
            event("USER_BECAME_PROFESSIONAL", """{"userId":"user-1","userEmail":"a@b.com","userRole":"PROFESSIONAL"}""")
        )

        assertEquals(1, notifications.size)
        assertEquals("user-1", notifications.first().userId)
        assertEquals(NotificationType.ACCOUNT, notifications.first().type)
    }

    @Test
    fun `an active status change tells the user whether it was on or off`() {
        val notifications = router.route(
            event("USER_ACTIVE_STATUS_CHANGED", """{"userId":"user-1","isActive":false}""")
        )

        assertEquals("Tu cuenta fue desactivada", notifications.first().title)
    }

    @Test
    fun `an event without a rule is ignored instead of failing`() {
        val notifications = router.route(event("SOMETHING_ELSE", """{"userId":"user-1"}"""))

        assertTrue(notifications.isEmpty())
    }

    @Test
    fun `an event without userId produces nothing`() {
        val notifications = router.route(event("USER_BECAME_PROFESSIONAL", """{"userEmail":"a@b.com"}"""))

        assertTrue(notifications.isEmpty())
    }
}
