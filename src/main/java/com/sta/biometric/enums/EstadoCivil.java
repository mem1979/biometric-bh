package com.sta.biometric.enums;

/**
 * Estado civil del empleado.
 * Las descripciones se obtienen desde i18n (EstadoCivil.SOLTERO_A, etc.)
 */
public enum EstadoCivil {
    SOLTERO_A("Soltero/a"),
    CASADO_A("Casado/a"),
    DIVORCIADO_A("Divorciado/a"),
    VIUDO_A("Viudo/a"),
    SEPARADO_A("Separado/a"),
    UNION_HECHO("Unión de Hecho"),
    OTRO("Otro");

    private String estadoCivil;

    EstadoCivil(String estadoCivil) {
        this.estadoCivil = estadoCivil;
    }

    /** Descripción legible para uso programático (no UI) */
    public String getEstadoCivil() {
        return estadoCivil;
    }
}
