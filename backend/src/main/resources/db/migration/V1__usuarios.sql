-- Usuarios de la app. Minimización de datos (RGPD, D11): solo lo imprescindible
-- para autenticar — email y hash de contraseña. Nada más hasta que una feature
-- lo justifique.
CREATE TABLE usuarios (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(320) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    creado_en     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
