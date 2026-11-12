package fatum.notification.configuration

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/** Mismo criterio que en los otros servicios: orígenes desde `app.cors.allowed-origins`. */
@Configuration
class CorsConfig(
    @Value("\${app.cors.allowed-origins:http://localhost:5173}") private val allowedOriginsSetting: String
) {

    private fun origins(): List<String> =
        allowedOriginsSetting.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource =
        UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", CorsConfiguration().apply {
                allowedOriginPatterns = origins()
                allowedMethods = listOf("GET", "PUT", "DELETE", "OPTIONS")
                allowedHeaders = listOf("*")
                allowCredentials = false
            })
        }
}
