package fatum.notification.controller

import fatum.notification.configuration.PlatformUserResolver
import fatum.notification.dto.MarkAllReadResponse
import fatum.notification.dto.NotificationPageResponse
import fatum.notification.dto.NotificationResponse
import fatum.notification.dto.UnreadCountResponse
import fatum.notification.exception.NotificationNotFoundException
import fatum.notification.realtime.SseHub
import fatum.notification.service.NotificationService
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

/**
 * La bandeja del usuario autenticado.
 *
 * <p>El `userId` sale del token, traducido al identificador con el que se guardan las
 * notificaciones (`PlatformUserResolver`), nunca de la URL: así nadie puede pedir la bandeja de
 * otra persona cambiando un parámetro.</p>
 */
@RestController
@RequestMapping("/notifications")
class NotificationController(
    private val notificationService: NotificationService,
    private val sseHub: SseHub,
    private val users: PlatformUserResolver
) {

    /**
     * El canal en vivo. El usuario sale del token; `EventSource` no puede mandar cabeceras, así que
     * también se acepta `?access_token=`.
     */
    @GetMapping("/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun stream(authentication: Authentication): SseEmitter =
        sseHub.subscribe(users.resolve(authentication))

    @GetMapping
    fun list(
        authentication: Authentication,
        @RequestParam(defaultValue = "20") limit: Int,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "false") unreadOnly: Boolean
    ): NotificationPageResponse {
        val page = notificationService.list(users.resolve(authentication), limit, cursor, unreadOnly)
        return NotificationPageResponse(
            items = page.items.map(NotificationResponse::from),
            nextCursor = page.nextCursor
        )
    }

    @GetMapping("/unread-count")
    fun unreadCount(authentication: Authentication): UnreadCountResponse =
        UnreadCountResponse(notificationService.unreadCount(users.resolve(authentication)))

    @PutMapping("/{id}/read")
    fun markRead(authentication: Authentication, @PathVariable id: String): ResponseEntity<Void> {
        if (!notificationService.markRead(users.resolve(authentication), id)) throw NotificationNotFoundException()
        return ResponseEntity.noContent().build()
    }

    @PutMapping("/read-all")
    fun markAllRead(authentication: Authentication): MarkAllReadResponse =
        MarkAllReadResponse(notificationService.markAllRead(users.resolve(authentication)))

    @DeleteMapping("/{id}")
    fun delete(authentication: Authentication, @PathVariable id: String): ResponseEntity<Void> {
        if (!notificationService.delete(users.resolve(authentication), id)) throw NotificationNotFoundException()
        return ResponseEntity.noContent().build()
    }
}
