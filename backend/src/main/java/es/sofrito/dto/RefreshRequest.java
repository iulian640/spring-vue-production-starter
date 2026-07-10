package es.sofrito.dto;

import jakarta.validation.constraints.NotBlank;

/** El refresh opaco, para /auth/refresh (rotarlo) o /auth/logout (revocarlo). */
public record RefreshRequest(@NotBlank String refreshToken) {
}
