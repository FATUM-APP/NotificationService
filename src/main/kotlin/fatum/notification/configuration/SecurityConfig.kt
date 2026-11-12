package fatum.notification.configuration

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.web.SecurityFilterChain

/**
 * Misma validación que el resto de servicios: token de Cognito y roles por grupo.
 *
 * <p>Cualquier cuenta con alguno de los grupos conocidos puede ver su propia bandeja. El `sub` del
 * token es el `userId`, así que no hay forma de pedir la bandeja de otro.</p>
 */
@Configuration
@EnableWebSecurity
@ConditionalOnProperty(name = ["app.security.enabled"], havingValue = "true", matchIfMissing = true)
class SecurityConfig(
    @Value("\${app.security.issuer-uri}") private val issuerUri: String
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain = http
        .csrf { it.disable() }
        .cors { }
        .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
        .authorizeHttpRequests { auth ->
            auth.requestMatchers("/actuator/health/**").permitAll()
            auth.anyRequest().hasAnyRole(VERIFIED, PROFESSIONAL, ADMIN)
        }
        .oauth2ResourceServer { oauth2 ->
            oauth2.bearerTokenResolver(bearerTokenResolver())
            oauth2.jwt { jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()) }
        }
        .build()

    /** `EventSource` no puede mandar cabeceras: además de `Authorization` se acepta `?access_token=`. */
    @Bean
    fun bearerTokenResolver(): DefaultBearerTokenResolver =
        DefaultBearerTokenResolver().apply { setAllowUriQueryParameter(true) }

    @Bean
    fun jwtDecoder(): JwtDecoder {
        val jwkSetUri = issuerUri.trimEnd('/') + "/.well-known/jwks.json"
        return NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build().apply {
            setJwtValidator(JwtValidators.createDefaultWithIssuer(issuerUri))
        }
    }

    @Bean
    fun jwtAuthenticationConverter(): JwtAuthenticationConverter =
        JwtAuthenticationConverter().apply {
            setJwtGrantedAuthoritiesConverter { jwt -> CognitoAuthorities.from(jwt) }
        }

    companion object {
        const val ADMIN = "ADMIN"
        const val PROFESSIONAL = "PROFESSIONAL"
        const val VERIFIED = "VERIFIED"
    }
}
