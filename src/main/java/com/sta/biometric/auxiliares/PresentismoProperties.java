package com.sta.biometric.auxiliares;

/**
 * Contenedor de constantes de claves de configuración para el módulo de Presentismo.
 * Se utilizan en conjunto con {@link com.sta.biometric.servicios.ConfiguracionesPreferencias}.
 */
public final class PresentismoProperties {

    private PresentismoProperties() {
        // Contenedor estático de constantes
    }

    public static final String HABILITADO = "presentismo.habilitado";
    public static final String POLITICA_NOMBRE = "presentismo.politica";

    // Feature Toggles (Habilitadores por tipo de evaluación)
    public static final String EVALUAR_LLEGADAS_TARDE = "presentismo.evaluar.llegadas.tarde";
    public static final String EVALUAR_SALIDAS_ANTICIPADAS = "presentismo.evaluar.salidas.anticipadas";
    public static final String EVALUAR_JORNADAS_INCOMPLETAS = "presentismo.evaluar.jornadas.incompletas";
    public static final String EVALUAR_AUSENCIAS = "presentismo.evaluar.ausencias";
    public static final String EVALUAR_PAUSAS = "presentismo.evaluar.pausas";

    // Umbrales Permisibles en el Período
    public static final String MAX_LLEGADAS_TARDE = "presentismo.llegadas.tarde.max";
    public static final String MAX_SALIDAS_ANTICIPADAS = "presentismo.salidas.anticipadas.max";
    public static final String MAX_JORNADAS_INCOMPLETAS = "presentismo.jornadas.incompletas.max";
    public static final String MAX_MINUTOS_DEMORA_ACUMULADOS = "presentismo.minutos.demora.acumulados.max";
    public static final String MAX_AUSENCIAS_INJUSTIFICADAS = "presentismo.ausencias.max";
    public static final String MAX_BANCO_HORAS_DESCONTADAS = "presentismo.banco.horas.max";

    // Criterios Especiales y Banco de Horas
    public static final String BANCO_HORAS_DESCONTAR_DEFAULT = "presentismo.banco.horas.descontar.default";
    public static final String FICHADAS_INCOMPLETAS_COMPUTAN = "presentismo.fichadas.incompletas.computan";
    public static final String LICENCIAS_NO_JUSTIFICADAS_COMPUTAN = "presentismo.licencias.no.justificadas.computan";
    /** @deprecated Usar {@link #LICENCIAS_NO_JUSTIFICADAS_COMPUTAN}. Mantenida por compatibilidad histórica. */
    @Deprecated
    public static final String LICENCIAS_SIN_GOCE_COMPUTAN = "presentismo.licencias.sin.goce.computan";
}
