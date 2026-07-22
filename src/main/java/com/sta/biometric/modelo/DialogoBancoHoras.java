package com.sta.biometric.modelo;

import org.openxava.annotations.*;

import com.sta.biometric.enums.Signo;

import lombok.Getter;
import lombok.Setter;

/**
 * Modelo transitorio para el diálogo de asignación de horas al Banco de Horas.
 * 
 * @author Sistema STARH
 * @since 2.0
 */
@Getter
@Setter
@View(members = "infoRegistro, evaluacionJornada; " +
        "Diferencia [diferenciaTotal, yaEnviado, disponible]; " +
        "EnvioBanco [signo, minutosAEnviar, saldoActual]; " +
        "observacion")
public class DialogoBancoHoras {

    /**
     * Información del empleado y fecha de la jornada (ReadOnly).
     */
    @ReadOnly
    private String infoRegistro;

    /**
     * Evaluación de la jornada (ReadOnly).
     */
    @ReadOnly
    private String evaluacionJornada;

    /**
     * Diferencia total registrada en la jornada (ReadOnly).
     */
    @ReadOnly
    private String diferenciaTotal;

    /**
     * Minutos ya redirigidos previamente al banco para esta jornada (ReadOnly).
     */
    @ReadOnly
    private String yaEnviado;

    /**
     * Minutos disponibles actualmente para enviar al banco (ReadOnly).
     */
    @ReadOnly
    private String disponible;

    /**
     * Signo de la operación (+ Sumar o - Restar).
     */
    @ReadOnly
    private Signo signo = Signo.MAS;

    /**
     * Cantidad de horas/minutos a enviar en formato HH:MM (Editable).
     */
    @Mask("00:00")
    @Required
    @DisplaySize(6)
    private String minutosAEnviar = "00:00";

    /**
     * Saldo actual del empleado en el Banco de Horas (ReadOnly).
     */
    @ReadOnly
    private String saldoActual;

    /**
     * Motivo u observación obligatoria de la asignación.
     */
    @Stereotype("MEMO")
    @Required
    private String observacion;

    /**
     * Parsea HH:MM a minutos totales absolutos.
     */
    public int getMinutosParsed() {
        if (minutosAEnviar == null || minutosAEnviar.isBlank())
            return 0;
        String limpio = minutosAEnviar.replace("_", "0").trim();
        String[] partes = limpio.split(":");
        try {
            int h = partes.length >= 1 ? Integer.parseInt(partes[0].trim()) : 0;
            int m = partes.length >= 2 ? Integer.parseInt(partes[1].trim()) : 0;
            return h * 60 + m;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
