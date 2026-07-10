package com.example.service;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Claves JWT de TEST (secreto fijo, solo para tests). */
final class JwtTestSupport {

    static final Duration DURACION = Duration.ofHours(24);

    private static final String SECRETO_TEST =
            "secreto-de-test-para-jwt-de-32-bytes-o-mas-no-usar-en-produccion";

    private JwtTestSupport() {
    }

    record Claves(JwtEncoder encoder, JwtDecoder decoder) {
    }

    static Claves claves() {
        SecretKeySpec clave = new SecretKeySpec(
                SECRETO_TEST.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(clave));
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(clave)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        return new Claves(encoder, decoder);
    }
}
