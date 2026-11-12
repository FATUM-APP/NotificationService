package fatum.notification.configuration

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt

/** Los grupos de Cognito (`cognito:groups`) se convierten en roles de Spring. */
object CognitoAuthorities {

    const val GROUPS_CLAIM = "cognito:groups"

    fun from(jwt: Jwt): Collection<GrantedAuthority> =
        (jwt.getClaimAsStringList(GROUPS_CLAIM) ?: emptyList<String>())
            .map { SimpleGrantedAuthority("ROLE_$it") }
}
