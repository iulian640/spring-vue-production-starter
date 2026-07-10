package com.example.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Cablea el {@link RateLimitFilter} como un bean normal. Deliberadamente
 * separado de {@code SecurityConfig}: así los tests de slice
 * ({@code @WebMvcTest}) que solo importan {@code SecurityConfig} (sin este
 * bean) siguen construyendo la cadena de seguridad sin el filtro, sin
 * romper nada (ver {@code SecurityConfig#securityFilterChain}, que lo
 * añade con un {@code ObjectProvider} opcional).
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {

    @Bean
    public RateLimitFilter rateLimitFilter(RateLimitProperties propiedades, Clock reloj,
                                            ObjectMapper objectMapper) {
        return new RateLimitFilter(propiedades, reloj, objectMapper);
    }
}
