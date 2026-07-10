package es.sofrito.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La barrera fail-fast contra secretos de juguete. El caso crítico: un
 * despliegue SIN perfil activo (application.yml fija spring.profiles.default=dev)
 * NO puede tratarse como dev — sin SPRING_PROFILES_ACTIVE explícito, un secreto
 * público del repo debe tumbar el arranque, no firmar tokens.
 */
@DisplayName("JwtConfig — barrera fail-fast contra secretos de juguete")
class JwtConfigTest {

    private static final String SECRETO_DE_JUGUETE =
            "secreto-de-desarrollo-no-usar-en-produccion-32-bytes!";
    private static final String SECRETO_REAL =
            "un-secreto-real-configurado-por-entorno-de-mas-de-32-bytes";

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(JwtConfig.class);

    @Test
    @DisplayName("sin perfil activo (aunque spring.profiles.default=dev) + secreto de juguete → no arranca")
    void sinPerfilActivoConSecretoDeJugueteNoArranca() {
        contexto.withPropertyValues(
                        "spring.profiles.default=dev",
                        "sofrito.seguridad.jwt.secreto=" + SECRETO_DE_JUGUETE)
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(ctx.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("SOFRITO_SEGURIDAD_JWT_SECRETO");
                });
    }

    @Test
    @DisplayName("perfil dev ACTIVO explícito + secreto de juguete → arranca (uso legítimo en local)")
    void perfilDevActivoArranca() {
        contexto.withPropertyValues(
                        "spring.profiles.active=dev",
                        "sofrito.seguridad.jwt.secreto=" + SECRETO_DE_JUGUETE)
                .run(ctx -> assertThat(ctx).hasNotFailed());
    }

    @Test
    @DisplayName("sin perfil activo + secreto real → arranca (producción bien configurada)")
    void sinPerfilConSecretoRealArranca() {
        contexto.withPropertyValues("sofrito.seguridad.jwt.secreto=" + SECRETO_REAL)
                .run(ctx -> assertThat(ctx).hasNotFailed());
    }

    @Test
    @DisplayName("secreto de menos de 32 bytes → no arranca (mínimo HS256)")
    void secretoCortoNoArranca() {
        contexto.withPropertyValues("sofrito.seguridad.jwt.secreto=demasiado-corto")
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(ctx.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("32 bytes");
                });
    }
}
