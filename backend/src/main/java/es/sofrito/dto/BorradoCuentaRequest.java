package es.sofrito.dto;

import jakarta.validation.constraints.NotBlank;

/** Confirmación del borrado de cuenta: la contraseña actual, nada más. */
public record BorradoCuentaRequest(@NotBlank String password) {
}
