package fatum.notification.configuration

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Traduce el `sub` del token al identificador con el que se guardan las notificaciones.
 *
 * <p>Las notificaciones se escriben con el `userId` del UserService (el UUID de la columna `ID`),
 * que es lo que viaja en los eventos: `clientId` y `providerId` de las órdenes, y el `userId` de
 * los eventos de cuenta. El token de Cognito, en cambio, trae el `sub` (la columna `AWS_ID`). Son
 * valores distintos: si la bandeja se abriera con el `sub`, la partición de DynamoDB sería otra y
 * el usuario no vería nada, ni por REST ni por el canal en vivo.</p>
 *
 * <p>Se pide una vez y se guarda unos minutos: `EventSource` reconecta cada media hora.</p>
 */
@Component
class PlatformUserResolver(
    @Value("\${fatum.base-url}") baseUrl: String,
    builder: RestClient.Builder = RestClient.builder()
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val restClient = builder.baseUrl(baseUrl).build()

    private val cache = ConcurrentHashMap<String, Cached>()

    fun resolve(authentication: Authentication): String {
        val jwt = (authentication as? JwtAuthenticationToken)?.token
            ?: return authentication.name

        val sub = jwt.subject ?: authentication.name
        cache[sub]?.takeIf { it.expiresAt.isAfter(Instant.now()) }?.let { return it.userId }

        val userId = runCatching {
            restClient.get()
                .uri("/users/me")
                .header("Authorization", "Bearer ${jwt.tokenValue}")
                .retrieve()
                .body(PlatformUser::class.java)
                ?.userId
        }.getOrElse { error ->
            log.warn("No se pudo resolver el usuario {} en el UserService: {}", sub, error.message)
            null
        }

        if (userId.isNullOrBlank()) {
            // Con el `sub` la bandeja abre pero sale vacía; queda en el log para no fallar en silencio.
            log.warn("La bandeja de {} se abre con el sub del token: no se verán notificaciones", sub)
            return sub
        }

        cache[sub] = Cached(userId, Instant.now().plus(CACHE_TTL))
        return userId
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class PlatformUser(val userId: String)

    private data class Cached(val userId: String, val expiresAt: Instant)

    private companion object {
        val CACHE_TTL: Duration = Duration.ofMinutes(5)
    }
}
