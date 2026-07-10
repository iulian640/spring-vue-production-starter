-- Sesiones con refresh token REVOCABLE. El access JWT pasa a ser corto
-- (15 min) y sigue stateless; la sesión larga vive aquí y sí se puede matar
-- desde el servidor: logout real, borrado de cuenta (el CASCADE la arrastra)
-- y revocación en bloque si se detecta un refresh robado.
--
-- token_hash = SHA-256 (hex) del refresh opaco: si la BD se filtra, los
-- tokens no se pueden reconstruir. usada_en implementa la ROTACIÓN: cada
-- refresh se gasta una sola vez; que llegue uno ya gastado delata un robo.
CREATE TABLE sesiones (
    id          UUID         PRIMARY KEY,
    usuario_id  UUID         NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64)  NOT NULL UNIQUE,
    emitida_en  TIMESTAMPTZ  NOT NULL,
    caduca_en   TIMESTAMPTZ  NOT NULL,
    usada_en    TIMESTAMPTZ,
    revocada_en TIMESTAMPTZ
);

-- Revocación en bloque por usuario (reuso detectado / "cerrar en todas partes").
CREATE INDEX idx_sesiones_usuario ON sesiones (usuario_id);
