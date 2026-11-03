package com.repwise.userservice.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Specifies how {@link KeycloakRoleConverter} must turn a Keycloak access token into Spring
 * Security authorities. The tests describe the intended contract (documented on the class), not
 * whatever the implementation happens to do.
 *
 * <p>These are plain unit tests: tokens are built in memory, so no Keycloak, Spring context or
 * network is involved.
 */
class KeycloakRoleConverterTest {

    private final KeycloakRoleConverter converter = new KeycloakRoleConverter();

    /** Builds an in-memory token carrying exactly the given claims. */
    private Jwt tokenWith(Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("fake-token").header("alg", "none");
        claims.forEach(builder::claim);
        return builder.build();
    }

    /** Builds a token shaped like Keycloak's: {@code realm_access: { roles: [...] }}. */
    private Jwt tokenWithRealmRoles(Object roles) {
        return tokenWith(Map.of("realm_access", Map.of("roles", roles)));
    }

    private List<String> authorityNames(Jwt jwt) {
        return converter.convert(jwt).stream().map(GrantedAuthority::getAuthority).toList();
    }

    // the normal case

    @Test
    @DisplayName("the admin realm role becomes the ROLE_ADMIN authority")
    void adminRoleBecomesRoleAdmin() {
        Jwt jwt = tokenWithRealmRoles(List.of("admin"));

        assertThat(authorityNames(jwt)).containsExactly("ROLE_ADMIN");
    }

    @Test
    @DisplayName("every realm role becomes its own authority, none are dropped")
    void everyRoleBecomesAnAuthority() {
        Jwt jwt = tokenWithRealmRoles(List.of("admin", "user", "coach"));

        assertThat(authorityNames(jwt))
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_USER", "ROLE_COACH");
    }

    @ParameterizedTest(name = "role \"{0}\" becomes {1}")
    @DisplayName("role names are prefixed with ROLE_ and upper-cased")
    @CsvSource({
            "admin,                        ROLE_ADMIN",
            "ADMIN,                        ROLE_ADMIN",
            "Admin,                        ROLE_ADMIN",
            "user,                         ROLE_USER",
            "offline_access,               ROLE_OFFLINE_ACCESS",
            "default-roles-repwise-oauth2, ROLE_DEFAULT-ROLES-REPWISE-OAUTH2"
    })
    void roleNamesArePrefixedAndUpperCased(String keycloakRole, String expectedAuthority) {
        Jwt jwt = tokenWithRealmRoles(List.of(keycloakRole));

        assertThat(authorityNames(jwt)).containsExactly(expectedAuthority);
    }

    @Test
    @DisplayName("Keycloak's built-in roles are passed through like any other role")
    void keycloakBuiltInRolesArePassedThrough() {
        Jwt jwt = tokenWithRealmRoles(
                List.of("admin", "default-roles-repwise-oauth2", "offline_access", "uma_authorization"));

        assertThat(authorityNames(jwt)).containsExactlyInAnyOrder(
                "ROLE_ADMIN",
                "ROLE_DEFAULT-ROLES-REPWISE-OAUTH2",
                "ROLE_OFFLINE_ACCESS",
                "ROLE_UMA_AUTHORIZATION");
    }

    // the result must not depend on the machine

    @Test
    @DisplayName("upper-casing does not depend on the JVM's default locale (Turkish dotted-I bug)")
    void upperCasingIsIndependentOfDefaultLocale() {
        Locale original = Locale.getDefault();
        try {
            // In Turkish, "admin".toUpperCase() would give "ADMİN" and hasRole("ADMIN") would fail.
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));

            Jwt jwt = tokenWithRealmRoles(List.of("admin"));

            assertThat(authorityNames(jwt)).containsExactly("ROLE_ADMIN");
        } finally {
            Locale.setDefault(original);
        }
    }

    // tokens with no usable roles must give no authorities, never an error

    @Test
    @DisplayName("a token without a realm_access claim has no authorities")
    void noRealmAccessClaimMeansNoAuthorities() {
        Jwt jwt = tokenWith(Map.of("sub", "some-user"));

        assertThat(converter.convert(jwt)).isEmpty();
    }

    @Test
    @DisplayName("realm_access without a roles entry has no authorities")
    void realmAccessWithoutRolesMeansNoAuthorities() {
        Jwt jwt = tokenWith(Map.of("realm_access", Map.of()));

        assertThat(converter.convert(jwt)).isEmpty();
    }

    @Test
    @DisplayName("an empty roles list has no authorities")
    void emptyRolesListMeansNoAuthorities() {
        Jwt jwt = tokenWithRealmRoles(List.of());

        assertThat(converter.convert(jwt)).isEmpty();
    }

    @Test
    @DisplayName("a roles value that is not a list is ignored instead of causing an error")
    void rolesThatAreNotAListMeanNoAuthorities() {
        Jwt jwt = tokenWithRealmRoles("admin");

        assertThat(converter.convert(jwt)).isEmpty();
    }

    @Test
    @DisplayName("a realm_access value that is not an object is ignored instead of causing an error")
    void realmAccessThatIsNotAnObjectMeansNoAuthorities() {
        Jwt jwt = tokenWith(Map.of("realm_access", "admin"));

        assertThat(converter.convert(jwt)).isEmpty();
    }

    // only realm roles count

    @Test
    @DisplayName("scope claims no longer become authorities, only realm roles do")
    void scopeClaimsAreNotConvertedToAuthorities() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("scope", "openid profile email");
        claims.put("realm_access", Map.of("roles", List.of("admin")));

        assertThat(authorityNames(tokenWith(claims))).containsExactly("ROLE_ADMIN");
    }

    @Test
    @DisplayName("client roles (resource_access) are not read, only realm roles")
    void clientRolesAreIgnored() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("resource_access", Map.of("account", Map.of("roles", List.of("manage-account"))));

        assertThat(converter.convert(tokenWith(claims))).isEmpty();
    }
}
