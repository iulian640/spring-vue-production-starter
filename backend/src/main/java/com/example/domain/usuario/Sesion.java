package com.example.domain.usuario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Una sesión de refresh: el access JWT es corto y stateless; esta fila es
 * lo que se puede REVOCAR. Guarda solo el hash SHA-256 del token opaco (si la
 * BD se filtra, los tokens no se reconstruyen). {@code usadaEn} implementa la
 * rotación: un refresh se gasta una vez; su reutilización delata un robo. Las
 * transiciones de estado (gastar, revocar) van por consultas atómicas del
 * repositorio, no por setters: dos refresh simultáneos no pueden ganar ambos.
 */
@Entity
@Table(name = "sesiones")
public class Sesion implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "usuario_id", nullable = false)
    private UUID usuarioId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "emitida_en", nullable = false)
    private Instant emitidaEn;

    @Column(name = "caduca_en", nullable = false)
    private Instant caducaEn;

    @Column(name = "usada_en")
    private Instant usadaEn;

    @Column(name = "revocada_en")
    private Instant revocadaEn;

    /** true hasta que la entidad se persiste o se carga: save() hace persist(), no merge+SELECT. */
    @Transient
    private boolean nueva = true;

    protected Sesion() {
        // requerido por JPA
    }

    public Sesion(UUID usuarioId, String tokenHash, Instant emitidaEn, Instant caducaEn) {
        this.id = UUID.randomUUID();
        this.usuarioId = Objects.requireNonNull(usuarioId);
        this.tokenHash = Objects.requireNonNull(tokenHash);
        this.emitidaEn = Objects.requireNonNull(emitidaEn);
        this.caducaEn = Objects.requireNonNull(caducaEn);
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return nueva;
    }

    @PostPersist
    @PostLoad
    void yaPersistida() {
        this.nueva = false;
    }

    public UUID getUsuarioId() {
        return usuarioId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getEmitidaEn() {
        return emitidaEn;
    }

    public Instant getCaducaEn() {
        return caducaEn;
    }

    public Instant getUsadaEn() {
        return usadaEn;
    }

    public Instant getRevocadaEn() {
        return revocadaEn;
    }
}
