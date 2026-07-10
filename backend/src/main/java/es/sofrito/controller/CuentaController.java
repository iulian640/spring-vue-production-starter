package es.sofrito.controller;

import es.sofrito.config.RequiereBaseDeDatos;
import es.sofrito.dto.BorradoCuentaRequest;
import es.sofrito.service.CuentaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gestión de la propia cuenta. Borrado (RGPD art. 17): siempre sobre el usuario
 * DEL TOKEN — no existe borrar la cuenta de otro — y re-confirmando contraseña.
 */
@RestController
@RequestMapping("/api/v1")
@RequiereBaseDeDatos
public class CuentaController {

    private final CuentaService cuenta;

    public CuentaController(CuentaService cuenta) {
        this.cuenta = cuenta;
    }

    @DeleteMapping("/cuenta")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void borra(@AuthenticationPrincipal Jwt jwt,
                      @Valid @RequestBody BorradoCuentaRequest peticion) {
        cuenta.borraCuenta(UsuarioAutenticado.id(jwt), peticion.password());
    }
}
