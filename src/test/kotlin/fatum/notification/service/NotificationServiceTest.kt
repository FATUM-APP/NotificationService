package fatum.notification.service

import fatum.notification.model.Notification
import fatum.notification.realtime.SseHub
import fatum.notification.support.InMemoryNotificationRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class NotificationServiceTest {

    private val repository = InMemoryNotificationRepository()
    private val hub: SseHub = mock()
    private val service = NotificationService(repository, hub)

    private fun notification(userId: String, id: String, read: Boolean = false) = Notification().apply {
        this.userId = userId
        this.id = id
        this.title = "t"
        this.body = "b"
        this.read = read
    }

    @Test
    fun `a delivered notification also goes to the live channel`() {
        val incoming = notification("client-1", "2026-11-12T10:00:00Z#a")

        service.deliver(listOf(incoming))

        verify(hub).publish(incoming)
    }

    @Test
    fun `the inbox returns the newest first`() {
        service.deliver(
            listOf(
                notification("client-1", "2026-11-10T10:00:00Z#a"),
                notification("client-1", "2026-11-12T10:00:00Z#b"),
                notification("client-2", "2026-11-11T10:00:00Z#c")
            )
        )

        val page = service.list("client-1", 10, null, unreadOnly = false)

        assertEquals(listOf("2026-11-12T10:00:00Z#b", "2026-11-10T10:00:00Z#a"), page.items.map { it.id })
    }

    @Test
    fun `pagination uses the id of the last item as cursor`() {
        service.deliver(
            (1..3).map { notification("client-1", "2026-11-1${it}T10:00:00Z#x$it") }
        )

        val first = service.list("client-1", 2, null, unreadOnly = false)
        assertEquals(2, first.items.size)
        assertNotNull(first.nextCursor)

        val second = service.list("client-1", 2, first.nextCursor, unreadOnly = false)
        assertEquals(1, second.items.size)
        assertEquals(null, second.nextCursor)
    }

    @Test
    fun `unread count and mark all read work together`() {
        service.deliver(
            listOf(
                notification("client-1", "2026-11-10T10:00:00Z#a"),
                notification("client-1", "2026-11-11T10:00:00Z#b"),
                notification("client-1", "2026-11-12T10:00:00Z#c", read = true)
            )
        )

        assertEquals(2, service.unreadCount("client-1"))
        assertEquals(2, service.markAllRead("client-1"))
        assertEquals(0, service.unreadCount("client-1"))
    }

    @Test
    fun `a user cannot touch somebody else's notification`() {
        service.deliver(listOf(notification("client-1", "2026-11-10T10:00:00Z#a")))

        assertFalse(service.markRead("client-2", "2026-11-10T10:00:00Z#a"))
        assertFalse(service.delete("client-2", "2026-11-10T10:00:00Z#a"))
        assertTrue(service.markRead("client-1", "2026-11-10T10:00:00Z#a"))
    }
}
