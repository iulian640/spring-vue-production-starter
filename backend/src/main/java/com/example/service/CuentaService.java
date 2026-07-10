package com.example.service;

import com.example.config.RequiereBaseDeDatos;
import com.example.domain.usuario.Usuario;
import com.example.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Borrado de cuenta (RGPD art. 17, derecho de supresión). Borrado REAL, no
 * marcado: la fila de usuarios desaparece y el ON DELETE CASCADE del esquema
 * arrastra perfil, cuadrantes y apuntes — que son la evidencia del usuario,
 * por eso el frontend avisa de descargar los PDF antes. Exige re-confirmar la
 * contraseña: un móvil desbloqueado en la barra no puede destruir la evidencia
 * de meses.
 */
@Service
@RequiereBaseDeDatos
public class CuentaService {

    private static final Logger log = LoggerFactory.getLogger(CuentaService.class);

    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;

    public CuentaService(UsuarioRepository usuarios, PasswordEncoder passwordEncoder) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void borraCuenta(UUID usuarioId, String password) {
        Usuario usuario = usuarios.findById(usuarioId)
                // Token vivo de una cuenta que ya no existe: 401, la sesión no vale.
                .orElseThrow(CredencialesInvalidasException::new);
        if (!passwordEncoder.matches(password, usuario.getPasswordHash())) {
            throw new PasswordIncorrectaException();
        }
        usuarios.delete(usuario);
        // Solo el id técnico: tras el borrado ya no identifica a nadie. Nunca el email.
        log.info("Cuenta borrada a petición del usuario (RGPD art. 17): {}", usuarioId);
    }
}
