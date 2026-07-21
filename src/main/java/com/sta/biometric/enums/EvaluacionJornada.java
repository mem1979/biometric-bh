package com.sta.biometric.enums;

/**
 * Resultado final de la evaluación de una jornada de asistencia.
 * Representa el estado global de toda la jornada diaria.
 */
public enum EvaluacionJornada {

    PENDIENTE,
    EN_CURSO,
    COMPLETA,
    INCOMPLETA,
    AUSENTE,
    SIN_ENTRADA,
    SIN_SALIDA,
    LICENCIA,
    LICENCIA_SIN_GOCE,
    LICENCIA_NO_JUSTIFICADA,
    LICENCIA_PARCIAL,
    FERIADO,
    FERIADO_TRABAJADO,
    DIA_NO_LABORAL,
    DIA_NO_LABORAL_TRABAJADO,
    SIN_TURNO_ASIGNADO,
    SIN_DATOS;

    public String getDescripcion() {
        return org.openxava.util.Labels.get("EvaluacionJornada." + name());
    }

}
