package fatum.notification.realtime

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Ajustes del canal en tiempo real.
 *
 * <p>`heartbeat-ms` debe quedar por debajo del idle timeout del ALB (60 s por defecto), o el balanceador
 * corta la conexión cuando el usuario no recibe nada durante un rato.</p>
 */
@ConfigurationProperties(prefix = "fatum.sse")
data class SseProperties(
    val timeoutMs: Long = 30 * 60 * 1000,
    val heartbeatMs: Long = 20 * 1000,
    val maxConnectionsPerUser: Int = 5
)
