package com.sta.biometric.modelo;

import org.openxava.annotations.*;

import lombok.Getter;
import lombok.Setter;

/**
 * Modelo transitorio para el diálogo de reversión de movimientos del Banco de Horas.
 * 
 * @author Sistema STARH
 * @since 2.0
 */
@Getter
@Setter
@View(members = "infoMovimiento; motivo")
public class DialogoReversionBanco {

    /**
     * Resumen informativo del movimiento a revertir (ReadOnly).
     */
    @ReadOnly
    private String infoMovimiento;

    /**
     * Motivo u observación obligatoria de la reversión.
     */
    @Stereotype("MEMO")
    @Required
    private String motivo;
}
