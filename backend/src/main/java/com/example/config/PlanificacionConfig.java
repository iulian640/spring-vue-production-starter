package com.example.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Tareas programadas del backend (hoy: la purga diaria de sesiones). Aparte
 * para que quien busque "qué corre solo en este servidor" tenga UN sitio.
 */
@Configuration
@EnableScheduling
public class PlanificacionConfig {
}
