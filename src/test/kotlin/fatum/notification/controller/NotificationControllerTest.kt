package fatum.notification.controller

import fatum.notification.configuration.CognitoAuthorities
import fatum.notification.configuration.SecurityConfig
import fatum.notification.model.Notification
import fatum.notification.model.constant.NotificationType
import fatum.notification.repository.NotificationPage
import fatum.notification.realtime.SseHub
import fatum.notification.service.NotificationService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@WebMvcTest(controllers = [NotificationController::class])
@Import(SecurityConfig::class)
@TestPropertySource(
    properties = [
        "app.security.enabled=true",
        "app.security.issuer-uri=https://cognito-idp.us-east-1.amazonaws.com/us-east-1_test"
    ]
)
class NotificationControllerTest {

    @Autowired lateinit var mvc: MockMvc

    @MockitoBean lateinit var notificationService: NotificationService
    @MockitoBean lateinit var sseHub: SseHub

    @Test
    fun `the live channel opens for the user of the token`() {
        whenever(sseHub.subscribe("client-1")).thenReturn(SseEmitter())

        mvc.perform(get("/notifications/stream").with(verifiedToken()))
            .andExpect(status().isOk())
            .andExpect(request().asyncStarted())
    }

    private fun verifiedToken(subject: String = "client-1") = authentication(
        JwtAuthenticationToken(
            Jwt.withTokenValue("t")
                .header("alg", "none")
                .subject(subject)
                .claim(CognitoAuthorities.GROUPS_CLAIM, listOf("VERIFIED"))
                .build(),
            listOf(SimpleGrantedAuthority("ROLE_VERIFIED"))
        )
    )

    @Test
    fun `the inbox of the token owner is returned`() {
        whenever(notificationService.list(any(), any<Int>(), anyOrNull(), any())).thenReturn(
            NotificationPage(
                items = listOf(
                    Notification().apply {
                        userId = "client-1"
                        id = "2026-11-12T10:00:00Z#a"
                        type = NotificationType.ORDER
                        title = "Solicitud aceptada"
                        body = "El profesional aceptó tu solicitud."
                        data = mapOf("orderId" to "order-1")
                        read = false
                        createdAt = "2026-11-12T10:00:00Z"
                    }
                ),
                nextCursor = null
            )
        )

        mvc.perform(get("/notifications").with(verifiedToken("client-1")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items[0].title").value("Solicitud aceptada"))
            .andExpect(jsonPath("$.items[0].data.orderId").value("order-1"))
            .andExpect(jsonPath("$.nextCursor").doesNotExist())

        // La bandeja se pide con el `sub` del token, no con un parámetro de la URL.
        verify(notificationService).list("client-1", 20, null, false)
    }

    @Test
    fun `without a token the inbox is not reachable`() {
        mvc.perform(get("/notifications")).andExpect(status().isUnauthorized)
    }

    @Test
    fun `a user outside the known groups is forbidden`() {
        val outsider = authentication(
            JwtAuthenticationToken(
                Jwt.withTokenValue("t")
                    .header("alg", "none")
                    .subject("client-9")
                    .claim(CognitoAuthorities.GROUPS_CLAIM, listOf("GUEST"))
                    .build(),
                listOf(SimpleGrantedAuthority("ROLE_GUEST"))
            )
        )

        mvc.perform(get("/notifications").with(outsider)).andExpect(status().isForbidden)
    }
}
