package com.sta.biometric.enums;

/**
 * Clasificación funcional de incidencias de asistencia para el módulo de Presentismo.
 * 
 * <p>
 * IMPORTANTE: Este enum representa únicamente una categorización funcional de incidencias
 * para aplicar políticas de presentismo y premios. NO representa una evaluación de la jornada,
 * la cual continúa siendo responsabilidad exclusiva de {@code AuditoriaRegistros}.
 * </p>
 */
public enum TipoIncidenciaPresentismo {

    LLEGADA_TARDE("Llegada Tarde"),
    SALIDA_ANTICIPADA("Salida Anticipada"),
    JORNADA_INCOMPLETA("Jornada Incompleta"),
    FICHADA_INCOMPLETA("Fichada Incompleta (Entrada/Salida Faltante)"),
    AUSENCIA("Ausencia Injustificada"),
    LICENCIA_SIN_GOCE("Licencia Sin Goce de Sueldo"),
    LICENCIA_NO_JUSTIFICADA("Licencia No Justificada"),
    PAUSA_EXCEDIDA("Exceso de Tiempo en Pausa");

    private final String descripcion;

    TipoIncidenciaPresentismo(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
