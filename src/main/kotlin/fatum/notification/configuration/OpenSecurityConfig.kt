package fatum.notification.configuration

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain

/**
 * Solo para desarrollo local: `SECURITY_ENABLED=false` deja la API abierta.
 *
 * <p>Con la cadena abierta, el `userId` del token no existe, así que los endpoints que dependen de
 * `Authentication` no sirven para probar la bandeja; para eso se corre con Cognito.</p>
 */
@Configuration
@EnableWebSecurity
@ConditionalOnProperty(name = ["app.security.enabled"], havingValue = "false")
class OpenSecurityConfig {

    @Bean
    fun openSecurityFilterChain(http: HttpSecurity): SecurityFilterChain = http
        .csrf { it.disable() }
        .cors { }
        .authorizeHttpRequests { it.anyRequest().permitAll() }
        .build()
}
