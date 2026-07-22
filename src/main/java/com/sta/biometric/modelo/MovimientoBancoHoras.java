package com.sta.biometric.modelo;

import java.time.LocalDate;
import java.time.LocalDateTime;

import javax.persistence.*;

import org.openxava.annotations.*;
import org.openxava.model.Identifiable;

import com.sta.biometric.enums.TipoMovimientoBancoHoras;

import lombok.Getter;
import lombok.Setter;

/**
 * Representa un movimiento individual e inmutable dentro del Banco de Horas.
 * 
 * <p>
 * Registra cada acreditación, débito, compensación o reversión con
 * trazabilidad completa.
 * </p>
 * 
 * @author Sistema STARH
 * @since 2.0
 */
@Entity
@Table(name = "movimiento_banco_horas", indexes = {
        @Index(name = "idx_mov_banco_banco", columnList = "banco_horas_id"),
        @Index(name = "idx_mov_banco_auditoria", columnList = "auditoria_registro_id"),
        @Index(name = "idx_mov_banco_fecha", columnList = "fechaCreacion"),
        @Index(name = "idx_mov_banco_original", columnList = "movimiento_original_id")
})
@Getter
@Setter
public class MovimientoBancoHoras extends Identifiable {

    /**
     * Cabecera del Banco de Horas al que pertenece este movimiento.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "banco_horas_id")
    @ReadOnly
    private BancoHoras bancoHoras;

    /**
     * Tipo de movimiento (INGRESO, DESCUENTO, COMPENSACION, AJUSTE_MANUAL, REVERSION).
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    @ReadOnly
    private TipoMovimientoBancoHoras tipo;

    /**
     * Cantidad absoluta de minutos involucrados en la operación (siempre positivo).
     */
    @Column(nullable = false)
    @ReadOnly
    private int minutos;

    /**
     * Indica si el movimiento acredita (true) o debita (false) en el saldo.
     */
    @Column(nullable = false)
    @ReadOnly
    private boolean signoPositivo;

    /**
     * Snapshot del saldo del banco en minutos ANTES de aplicar el movimiento.
     */
    @Column(nullable = false)
    @ReadOnly
    private int saldoAnterior;

    /**
     * Snapshot del saldo del banco en minutos DESPUÉS de aplicar el movimiento.
     */
    @Column(nullable = false)
    @ReadOnly
    private int saldoNuevo;

    /**
     * Registro de Auditoría de asistencia origen (opcional).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "auditoria_registro_id")
    @ReadOnly
    private AuditoriaRegistros auditoriaRegistro;

    /**
     * Liquidación de jornadas del período origen (opcional).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "liquidacion_id")
    @ReadOnly
    private LiquidacionJornadas liquidacion;

    /**
     * Referencia al movimiento original en caso de ser una REVERSION.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movimiento_original_id")
    @ReadOnly
    private MovimientoBancoHoras movimientoOriginal;

    /**
     * Fecha de la jornada laboral origen (opcional).
     */
    @ReadOnly
    private LocalDate fechaJornada;

    /**
     * Usuario que ejecutó la operación.
     */
    @Column(length = 50, nullable = false)
    @ReadOnly
    private String usuarioOperacion;

    /**
     * Timestamp de creación del movimiento.
     */
    @Column(nullable = false)
    @ReadOnly
    private LocalDateTime fechaCreacion;

    /**
     * Motivo u observación obligatoria de la operación.
     */
    @Stereotype("MEMO")
    @Column(length = 2000)
    @ReadOnly
    private String observacion;

    // =========================================================================
    // MÉTODOS DE PRESENTACIÓN / FORMATEO (TRANSIENT)
    // =========================================================================

    /**
     * Retorna los minutos formateados con signo (ej: "+02:00 hs" o "-01:30 hs").
     */
    @Transient
    public String getMinutosFormateados() {
        String signo = signoPositivo ? "+" : "-";
        int h = minutos / 60;
        int m = minutos % 60;
        return String.format("%s%02d:%02d hs", signo, h, m);
    }

    /**
     * Retorna el saldo anterior formateado en HH:MM.
     */
    @Transient
    public String getSaldoAnteriorFormateado() {
        return formatearMinutos(saldoAnterior);
    }

    /**
     * Retorna el saldo nuevo formateado en HH:MM.
     */
    @Transient
    public String getSaldoNuevoFormateado() {
        return formatearMinutos(saldoNuevo);
    }

    private String formatearMinutos(int totalMinutos) {
        String signo = totalMinutos < 0 ? "-" : (totalMinutos > 0 ? "+" : "");
        int abs = Math.abs(totalMinutos);
        int h = abs / 60;
        int m = abs % 60;
        return String.format("%s%02d:%02d hs", signo, h, m);
    }
}
