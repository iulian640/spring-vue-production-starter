package com.example.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Claves JWT (HS256 simétrico). El secreto viene de configuración:
 * en producción SIEMPRE por variable de entorno (APP_SEGURIDAD_JWT_SECRETO),
 * nunca hardcodeado; los perfiles dev/local/test llevan secretos de juguete
 * claramente marcados. Mínimo 32 bytes (HS256).
 */
@Configuration
public class JwtConfig {

    private static final int MIN_BYTES_SECRETO = 32;

    /** Secretos de juguete de los perfiles dev/local/test: JAMÁS válidos fuera de ellos. */
    private static final java.util.Set<String> SECRETOS_DE_JUGUETE = java.util.Set.of(
            "secreto-de-desarrollo-no-usar-en-produccion-32-bytes!",
            "secreto-local-solo-para-consultar-convenios-32-bytes!!",
            "secreto-de-test-para-jwt-de-32-bytes-o-mas-no-usar-en-prod");

    private static final java.util.Set<String> PERFILES_NO_PRODUCTIVOS =
            java.util.Set.of("dev", "local", "test");

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public JwtEncoder jwtEncoder(@Value("${app.seguridad.jwt.secreto}") String secreto,
                                 Environment entorno) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(clave(secreto, entorno)));
    }

    @Bean
    public JwtDecoder jwtDecoder(@Value("${app.seguridad.jwt.secreto}") String secreto,
                                 Environment entorno) {
        return NimbusJwtDecoder.withSecretKey(clave(secreto, entorno))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    private static SecretKeySpec clave(String secreto, Environment entorno) {
        // Barrera fail-fast (hallazgo H1 del security-review): solo cuentan los
        // perfiles ACTIVOS, jamás los por defecto. application.yml fija
        // spring.profiles.default=dev, así que un despliegue sin
        // SPRING_PROFILES_ACTIVE "parecería" dev por defecto y firmaría tokens
        // con el secreto público del repo. Sin perfil activo explícito la
        // barrera APLICA: los secretos conocidos del repo tumban el arranque.
        boolean perfilNoProductivo = java.util.Arrays.stream(entorno.getActiveProfiles())
                .anyMatch(PERFILES_NO_PRODUCTIVOS::contains);
        if (!perfilNoProductivo && SECRETOS_DE_JUGUETE.contains(secreto)) {
            throw new IllegalStateException(
                    "Secreto JWT de desarrollo detectado fuera de dev/local/test: configura "
                            + "APP_SEGURIDAD_JWT_SECRETO con un secreto real (p. ej. openssl rand -base64 48)");
        }
        return clave(secreto);
    }

    private static SecretKeySpec clave(String secreto) {
        byte[] bytes = secreto.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_BYTES_SECRETO) {
            throw new IllegalStateException(
                    "El secreto JWT debe tener al menos " + MIN_BYTES_SECRETO + " bytes");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}
