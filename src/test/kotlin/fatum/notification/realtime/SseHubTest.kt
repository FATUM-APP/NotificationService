package fatum.notification.realtime

import fatum.notification.model.Notification
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SseHubTest {

    private val hub = SseHub(SseProperties(maxConnectionsPerUser = 2))

    private fun notification(userId: String) = Notification().apply {
        this.userId = userId
        this.id = "2026-11-12T10:00:00Z#a"
        this.title = "t"
        this.body = "b"
    }

    @Test
    fun `a user can keep several tabs open`() {
        hub.subscribe("client-1")
        hub.subscribe("client-1")

        assertEquals(2, hub.connectionCount("client-1"))
    }

    @Test
    fun `beyond the limit the oldest tab is closed`() {
        hub.subscribe("client-1")
        hub.subscribe("client-1")
        hub.subscribe("client-1")

        assertEquals(2, hub.connectionCount("client-1"))
    }

    @Test
    fun `publishing to someone not connected does nothing`() {
        hub.publish(notification("client-9"))

        assertEquals(0, hub.connectionCount("client-9"))
    }

    @Test
    fun `publishing to a connected user does not fail`() {
        hub.subscribe("client-1")
        hub.publish(notification("client-1"))

        assertEquals(1, hub.connectionCount("client-1"))
    }
}
