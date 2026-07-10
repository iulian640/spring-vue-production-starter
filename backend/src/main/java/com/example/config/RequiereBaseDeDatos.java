package com.example.config;

import org.springframework.context.annotation.Profile;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca beans que necesitan la base de datos: quedan fuera del perfil `local`
 * (modo consulta sin BD). Úsala en TODO servicio/controller respaldado por JPA
 * (usuarios, fichajes, cuadrantes...) en vez de repetir el string del perfil.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Profile("!local")
public @interface RequiereBaseDeDatos {
}
