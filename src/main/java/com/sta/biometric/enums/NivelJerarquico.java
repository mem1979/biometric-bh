package com.sta.biometric.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Niveles jerárquicos universales para la estructura organizacional.
 * 
 * <p>
 * Diseñado para ser compatible con la mayoría de empresas y rubros,
 * desde pequeñas empresas hasta corporaciones multinacionales.
 * </p>
 * 
 * <p>
 * El campo {@code orden} permite ordenar de mayor a menor jerarquía,
 * y {@code path} facilita queries jerárquicas para futuros reportes.
 * </p>
 * 
 * @author Sistema STARH
 * @version 1.0
 * @see com.sta.biometric.modelo.ContratoLaboral
 */
@Getter
@RequiredArgsConstructor
public enum NivelJerarquico {

        // =========================================================================
        // NIVEL EJECUTIVO (Alta Dirección)
        // =========================================================================

        PRESIDENTE(1, "/PRES"),
        VICEPRESIDENTE(2, "/PRES/VP"),
        DIRECTOR_GENERAL(3, "/PRES/DG"),

        // =========================================================================
        // NIVEL DIRECTIVO (Direcciones de área)
        // =========================================================================

        DIRECTOR(4, "/PRES/DG/DIR"),
        SUBDIRECTOR(5, "/PRES/DG/DIR/SDIR"),

        // =========================================================================
        // NIVEL GERENCIAL (Gestión de áreas)
        // =========================================================================

        GERENTE(6, "/PRES/DG/DIR/GER"),
        SUBGERENTE(7, "/PRES/DG/DIR/GER/SGER"),

        // =========================================================================
        // NIVEL DE JEFATURA (Supervisión de equipos)
        // =========================================================================

        JEFE(8, "/PRES/DG/DIR/GER/JEF"),
        SUBJEFE(9, "/PRES/DG/DIR/GER/JEF/SJEF"),

        // =========================================================================
        // NIVEL DE COORDINACIÓN (Liderazgo de equipos pequeños)
        // =========================================================================

        COORDINADOR(10, "/PRES/DG/DIR/GER/JEF/COORD"),
        SUPERVISOR(11, "/PRES/DG/DIR/GER/JEF/COORD/SUP"),
        LIDER(12, "/PRES/DG/DIR/GER/JEF/COORD/LID"),

        // =========================================================================
        // NIVEL PROFESIONAL (Especialistas y expertos)
        // =========================================================================

        ESPECIALISTA(13, "/PRES/DG/DIR/GER/ESP"),
        PROFESIONAL_SENIOR(14, "/PRES/DG/DIR/GER/ESP/PSEN"),
        PROFESIONAL_SEMI_SENIOR(15, "/PRES/DG/DIR/GER/ESP/PSSEN"),
        PROFESIONAL_JUNIOR(16, "/PRES/DG/DIR/GER/ESP/PJUN"),

        // =========================================================================
        // NIVEL TÉCNICO (Conocimientos técnicos específicos)
        // =========================================================================

        TECNICO_SENIOR(17, "/PRES/DG/DIR/GER/TEC/TSEN"),
        TECNICO(18, "/PRES/DG/DIR/GER/TEC"),
        TECNICO_JUNIOR(19, "/PRES/DG/DIR/GER/TEC/TJUN"),

        // =========================================================================
        // NIVEL ADMINISTRATIVO (Apoyo administrativo)
        // =========================================================================

        ADMINISTRATIVO_SENIOR(20, "/PRES/DG/DIR/GER/ADM/ASEN"),
        ADMINISTRATIVO(21, "/PRES/DG/DIR/GER/ADM"),
        AUXILIAR_ADMINISTRATIVO(22, "/PRES/DG/DIR/GER/ADM/AUX"),

        // =========================================================================
        // NIVEL OPERATIVO (Ejecución de tareas)
        // =========================================================================

        OPERARIO_SENIOR(23, "/PRES/DG/DIR/GER/OPE/OSEN"),
        OPERARIO(24, "/PRES/DG/DIR/GER/OPE"),
        AYUDANTE(25, "/PRES/DG/DIR/GER/OPE/AYU"),

        // =========================================================================
        // NIVEL DE INGRESO (Formación inicial)
        // =========================================================================

        APRENDIZ(26, "/PRES/DG/DIR/GER/APR"),
        PASANTE(27, "/PRES/DG/DIR/GER/PAS"),
        BECARIO(28, "/PRES/DG/DIR/GER/BEC");

        // =========================================================================
        // CAMPOS
        // =========================================================================

        private final int orden;
        private final String path;

        /**
         * Retorna la profundidad jerárquica basada en el path.
         * Más barras = mayor profundidad = menor jerarquía.
         */
        public int getProfundidad() {
                return (int) path.chars().filter(c -> c == '/').count();
        }

        /**
         * Verifica si este nivel es superior a otro.
         */
        public boolean esSuperiorA(NivelJerarquico otro) {
                return this.orden < otro.orden;
        }

        /**
         * Verifica si este nivel es de categoría ejecutiva.
         */
        public boolean esEjecutivo() {
                return orden <= 3;
        }

        /**
         * Verifica si este nivel es de dirección o gerencia.
         */
        public boolean esDireccionOGerencia() {
                return orden >= 4 && orden <= 7;
        }

        /**
         * Verifica si tiene personal a cargo típicamente.
         */
        public boolean tienePersonalACargo() {
                return orden <= 12;
        }
}
