package com.sta.biometric.enums;

/**
 * Tipos de movimientos admitidos en el Banco de Horas.
 * 
 * @author Sistema STARH
 * @since 2.0
 */
public enum TipoMovimientoBancoHoras {

    /** Horas extras o sobrantes acreditadas en el banco */
    INGRESO("Ingreso de horas"),

    /** Horas faltantes o ausencias debitadas en el banco (deuda) */
    DESCUENTO("Descuento de horas"),

    /** Uso del saldo del banco para compensar diferencias u otorgar francos */
    COMPENSACION("Compensación"),

    /** Ajuste administrativo manual sobre el saldo */
    AJUSTE_MANUAL("Ajuste manual");

    private final String descripcion;

    TipoMovimientoBancoHoras(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }

    @Override
    public String toString() {
        return descripcion;
    }
}
