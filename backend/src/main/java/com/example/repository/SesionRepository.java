package com.example.repository;

import com.example.domain.usuario.Sesion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SesionRepository extends JpaRepository<Sesion, UUID> {

    Optional<Sesion> findByTokenHash(String tokenHash);

    /*
     * clearAutomatically en todos los @Modifying (java review): un bulk update
     * NO toca las entidades ya cargadas en el contexto de persistencia — sin el
     * clear, releer una Sesion tras estas llamadas en la MISMA transacción
     * devolvería la instancia cacheada con el estado viejo (hoy nadie relee,
     * pero ese invariante no debe sostener la seguridad de la rotación).
     */

    /**
     * Reclama la sesión para rotarla: ATÓMICO (el WHERE decide, no una lectura
     * previa). Devuelve 0 si otro refresh la gastó antes o si está revocada —
     * dos refresh simultáneos con el mismo token no pueden ganar los dos.
     */
    @Modifying(clearAutomatically = true)
    @Query("update Sesion s set s.usadaEn = :ahora "
            + "where s.id = :id and s.usadaEn is null and s.revocadaEn is null")
    int marcaUsadaSiIntacta(@Param("id") UUID id, @Param("ahora") Instant ahora);

    /** Revoca TODAS las sesiones vivas del usuario (reuso detectado = posible robo). */
    @Modifying(clearAutomatically = true)
    @Query("update Sesion s set s.revocadaEn = :ahora "
            + "where s.usuarioId = :usuarioId and s.revocadaEn is null")
    int revocaTodas(@Param("usuarioId") UUID usuarioId, @Param("ahora") Instant ahora);

    /** Revoca por hash (logout). Idempotente: 0 filas si no existe o ya estaba revocada. */
    @Modifying(clearAutomatically = true)
    @Query("update Sesion s set s.revocadaEn = :ahora "
            + "where s.tokenHash = :tokenHash and s.revocadaEn is null")
    int revocaPorHash(@Param("tokenHash") String tokenHash, @Param("ahora") Instant ahora);

    /**
     * Purga (security review B4): fuera lo caducado y lo revocado hace tiempo.
     * Las revocadas se conservan {@code limiteRevocadas} por si hay que
     * investigar un incidente (la revocación en bloque delata un robo); una
     * sesión caducada ya no abre nada y no aporta.
     */
    @Modifying
    @Query("delete from Sesion s where s.caducaEn < :ahora "
            + "or (s.revocadaEn is not null and s.revocadaEn < :limiteRevocadas)")
    int purga(@Param("ahora") Instant ahora, @Param("limiteRevocadas") Instant limiteRevocadas);
}
