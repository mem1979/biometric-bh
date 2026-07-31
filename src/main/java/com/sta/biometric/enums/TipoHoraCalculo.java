package com.sta.biometric.enums;

/**
 * Tipos de hora utilizados para el cálculo centralizado de horas liquidadas.
 * 
 * <p>
 * Cada tipo representa una categoría de hora en la que se distribuye
 * el tiempo efectivamente trabajado por el empleado:
 * </p>
 * <ul>
 * <li>{@link #NORMALES} - Horas dentro del horario planificado del turno</li>
 * <li>{@link #EXTRAS} - Horas excedentes sobre el turno planificado</li>
 * <li>{@link #ESPECIALES} - Horas trabajadas en feriados nacionales (Ley 20.744 Art. 201)</li>
 * </ul>
 * 
 * @see com.sta.biometric.modelo.AuditoriaRegistros#calcularMinutosLiquidados
 */
public enum TipoHoraCalculo {

    /** Horas trabajadas dentro del horario normal del turno asignado. */
    NORMALES,

    /** Horas extras trabajadas fuera del horario normal (bonificación al 50%). */
    EXTRAS,

    /** Horas trabajadas en feriados nacionales (bonificación al 100%). */
    ESPECIALES
}
