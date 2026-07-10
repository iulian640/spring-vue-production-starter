package es.sofrito.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * 401 como RFC 7807 (ProblemDetail), con el mismo shape que las respuestas del
 * GlobalExceptionHandler (type, title, status, detail, instance). Copy neutro
 * a propósito: no se distingue si el token era inválido, había caducado o
 * directamente no venía — ese detalle solo le sirve a un atacante.
 */
class ProblemDetailEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    ProblemDetailEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException excepcion) throws IOException {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, "Autenticación requerida");
        problema.setTitle(HttpStatus.UNAUTHORIZED.getReasonPhrase());
        try {
            problema.setInstance(URI.create(request.getRequestURI()));
        } catch (IllegalArgumentException e) {
            // URI rara en la petición: mejor un 401 sin instance que un 500.
        }

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        // RFC 6750: el desafío Bearer, sin detalles del porqué.
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), problema);
    }
}
