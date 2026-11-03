package com.repwise.userservice.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns the realm roles in a Keycloak access token into Spring Security authorities.
 *
 * <p>By default Spring builds authorities only from the token's {@code scope} claim, but Keycloak
 * puts roles somewhere else:
 * <pre>{@code
 * "realm_access": { "roles": ["admin", "default-roles-repwise-oauth2"] }
 * }</pre>
 * This converter reads that list and returns one authority per role, named {@code ROLE_} plus the
 * role in upper case, so the Keycloak role {@code admin} becomes {@code ROLE_ADMIN}. The
 * {@code ROLE_} prefix is what Spring's {@code hasRole("ADMIN")} checks for.
 *
 * <p>Only realm roles are read, not client roles ({@code resource_access}). A token with no
 * {@code realm_access} claim, or one whose {@code realm_access} is not an object or whose
 * {@code roles} is not a list, yields no authorities instead of an error. Because it replaces Spring's default converter, {@code scope} claims no
 * longer become {@code SCOPE_*} authorities; nothing in this service uses them.
 *
 * <p>Roles are copied into the token when Keycloak issues it, so a role assigned after a token
 * was issued is only seen in tokens requested afterwards.
 */
public class KeycloakRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    /**
     * Extracts the authorities from a verified token.
     *
     * @param jwt the verified access token
     * @return one {@code ROLE_<NAME>} authority per realm role, or an empty collection if the token
     *         carries no realm roles
     */
    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Object realmAccess = jwt.getClaim("realm_access");

        if (!(realmAccess instanceof Map<?, ?> realmAccessClaim)) return List.of();

        Object roles = realmAccessClaim.get("roles");

        if (!(roles instanceof Collection<?> roleNames)) return List.of();

        List<GrantedAuthority> authorities = new ArrayList<>();

        for (Object role: roleNames) {
            authorities.add(
                    new SimpleGrantedAuthority("ROLE_" + role.toString().toUpperCase(Locale.ROOT))
            );
        }

        return authorities;
    }


}
