package es.sofrito.service;

import es.sofrito.config.RequiereBaseDeDatos;
import es.sofrito.domain.usuario.Sesion;
import es.sofrito.domain.usuario.Usuario;
import es.sofrito.repository.SesionRepository;
import es.sofrito.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

/**
 * Registro, login y ciclo de la sesión (D13.4 + B4). Reglas de seguridad:
 * BCrypt para el hash (nunca se guarda ni se registra la contraseña en claro),
 * mensaje de login único (anti enumeración), y verificación de contraseña
 * también cuando el email no existe (coste constante, anti timing).
 *
 * <p>Sesión en dos piezas (B4): un access JWT CORTO y stateless (los endpoints
 * no tocan BD para validarlo) y un refresh OPACO largo guardado hasheado en
 * {@code sesiones}, que sí se puede revocar (logout real; el borrado de cuenta
 * lo arrastra el cascade). El refresh ROTA en cada uso: gastar dos veces el
 * mismo token delata un robo y revoca todas las sesiones del usuario.
 */
@Service
@RequiereBaseDeDatos
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    static final int MIN_CARACTERES_PASSWORD = 10;

    /** 256 bits de entropía para el refresh opaco. */
    private static final int BYTES_REFRESH = 32;

    private final UsuarioRepository usuarios;
    private final SesionRepository sesiones;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final Clock reloj;
    private final Duration duracionToken;
    private final Duration duracionRefresh;
    private final SecureRandom aleatorio = new SecureRandom();

    /** Hash real de una contraseña aleatoria: iguala el coste del matches() cuando el email no existe (anti timing). */
    private final String hashSenuelo;

    public AuthService(UsuarioRepository usuarios,
                       SesionRepository sesiones,
                       PasswordEncoder passwordEncoder,
                       JwtEncoder jwtEncoder,
                       Clock reloj,
                       @Value("${sofrito.seguridad.jwt.duracion:PT15M}") Duration duracionToken,
                       @Value("${sofrito.seguridad.refresh.duracion:P7D}") Duration duracionRefresh) {
        this.usuarios = usuarios;
        this.sesiones = sesiones;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.reloj = reloj;
        this.duracionToken = duracionToken;
        this.duracionRefresh = duracionRefresh;
        this.hashSenuelo = passwordEncoder.encode("señuelo-" + java.util.UUID.randomUUID());
    }

    @Transactional
    public Usuario registra(String email, String password) {
        String emailNormalizado = normaliza(email);
        if (password == null || password.length() < MIN_CARACTERES_PASSWORD) {
            throw new IllegalArgumentException(
                    "La contraseña debe tener al menos " + MIN_CARACTERES_PASSWORD + " caracteres");
        }
        // BCrypt solo usa los primeros 72 BYTES (ojo tildes/eñes en UTF-8: 2 bytes)
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("La contraseña es demasiado larga (máximo 72 bytes)");
        }
        if (usuarios.findByEmail(emailNormalizado).isPresent()) {
            throw new EmailYaRegistradoException();
        }
        try {
            return usuarios.saveAndFlush(new Usuario(emailNormalizado, passwordEncoder.encode(password)));
        } catch (DataIntegrityViolationException e) {
            // Carrera con otro registro simultáneo del mismo email: la restricción
            // UNIQUE de la BD es la barrera real; el findByEmail solo da mejor mensaje.
            throw new EmailYaRegistradoException();
        }
    }

    /** OJO: ya no es readOnly — abrir sesión PERSISTE la fila del refresh. */
    @Transactional
    public SesionEmitida login(String email, String password) {
        Optional<Usuario> usuario = usuarios.findByEmail(normaliza(email));
        String hash = usuario.map(Usuario::getPasswordHash).orElse(hashSenuelo);
        boolean coincide = passwordEncoder.matches(password, hash);
        if (usuario.isEmpty() || !coincide) {
            throw new CredencialesInvalidasException();
        }
        return emiteSesion(usuario.get());
    }

    /**
     * Cambia un refresh vivo por una sesión nueva (access + refresh ROTADO).
     * Nunca se dice el porqué de un rechazo: 401 idéntico para token
     * desconocido, caducado, revocado o reutilizado (sin oráculo).
     *
     * <p>{@code noRollbackFor} es SEGURIDAD, no un detalle: el camino del reuso
     * revoca todas las sesiones y DESPUÉS lanza el 401 — sin esta cláusula, la
     * excepción haría rollback de la propia revocación y el ladrón seguiría
     * dentro (bug real cazado en la verificación en vivo, no por los mocks).
     * Los demás rechazos no escriben nada, así que no les afecta.
     */
    @Transactional(noRollbackFor = CredencialesInvalidasException.class)
    public SesionEmitida refresca(String refreshToken) {
        Instant ahora = Instant.now(reloj);
        Sesion sesion = sesiones.findByTokenHash(hashSha256(refreshToken))
                .orElseThrow(CredencialesInvalidasException::new);
        if (sesion.getRevocadaEn() != null || ahora.isAfter(sesion.getCaducaEn())) {
            throw new CredencialesInvalidasException();
        }
        if (sesiones.marcaUsadaSiIntacta(sesion.getId(), ahora) == 0) {
            // Un refresh ya gastado vuelve a llegar: o carrera de dos pestañas o
            // un token robado. No se distingue → se cierra TODO para ese usuario
            // (coste: volver a hacer login; beneficio: el ladrón se queda fuera).
            sesiones.revocaTodas(sesion.getUsuarioId(), ahora);
            log.warn("Refresh reutilizado: sesiones revocadas para el usuario {}", sesion.getUsuarioId());
            throw new CredencialesInvalidasException();
        }
        Usuario usuario = usuarios.findById(sesion.getUsuarioId())
                .orElseThrow(CredencialesInvalidasException::new);
        return emiteSesion(usuario);
    }

    /** Logout real: revoca el refresh en el servidor. Idempotente y sin eco. */
    @Transactional
    public void cierraSesion(String refreshToken) {
        sesiones.revocaPorHash(hashSha256(refreshToken), Instant.now(reloj));
    }

    private SesionEmitida emiteSesion(Usuario usuario) {
        Instant ahora = Instant.now(reloj);
        Instant expira = ahora.plus(duracionToken);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("sofrito")
                .subject(usuario.getId().toString())
                .claim("email", usuario.getEmail())
                .issuedAt(ahora)
                .expiresAt(expira)
                .build();
        JwsHeader cabecera = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(cabecera, claims)).getTokenValue();

        byte[] crudo = new byte[BYTES_REFRESH];
        aleatorio.nextBytes(crudo);
        String refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(crudo);
        Instant refreshCaduca = ahora.plus(duracionRefresh);
        sesiones.save(new Sesion(usuario.getId(), hashSha256(refresh), ahora, refreshCaduca));

        return new SesionEmitida(token, expira, refresh, refreshCaduca);
    }

    /** SHA-256 en hex: lo único que toca la BD. El token en claro solo viaja al cliente. */
    private static String hashSha256(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    private static String normaliza(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("El email es obligatorio");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
