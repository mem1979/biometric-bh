package com.sta.biometric.modelo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;

import javax.persistence.*;

import org.openxava.annotations.*;
import org.openxava.model.Identifiable;

import lombok.Getter;
import lombok.Setter;

/**
 * Entidad cabecera del Banco de Horas por empleado.
 * 
 * <p>
 * Mantiene el saldo actual acumulado y los totales históricos para optimizar
 * las consultas de UI en el legajo del empleado.
 * </p>
 * 
 * @author Sistema STARH
 * @since 2.0
 */
@Entity
@Table(name = "banco_horas", indexes = {
        @Index(name = "idx_banco_horas_empleado", columnList = "empleado_id", unique = true)
})
@Getter
@Setter
public class BancoHoras extends Identifiable {

    /**
     * Empleado titular del Banco de Horas.
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empleado_id", unique = true)
    @ReadOnly
    private Personal empleado;

    /**
     * Saldo vivo actual en minutos.
     * Positivo: horas a favor del empleado.
     * Negativo: horas adeudadas por el empleado.
     */
    @Column(nullable = false, columnDefinition = "INTEGER DEFAULT 0")
    @ReadOnly
    private int saldoMinutosActual;

    /**
     * Acumulado histórico de minutos positivos (ingresos).
     */
    @Column(nullable = false, columnDefinition = "INTEGER DEFAULT 0")
    @ReadOnly
    private int totalMinutosPositivos;

    /**
     * Acumulado histórico de minutos negativos (descuentos/deudas).
     */
    @Column(nullable = false, columnDefinition = "INTEGER DEFAULT 0")
    @ReadOnly
    private int totalMinutosNegativos;

    /**
     * Fecha y hora de creación del Banco de Horas.
     */
    @Column(nullable = false)
    @ReadOnly
    private LocalDateTime fechaCreacion;

    /**
     * Fecha y hora de la última actualización del saldo.
     */
    @ReadOnly
    private LocalDateTime fechaUltimaActualizacion;

    /**
     * Observaciones o notas generales sobre la cuenta del banco del empleado.
     */
    @Stereotype("MEMO")
    @Column(length = 2000)
    private String observaciones;

    /**
     * Colección inmutable de movimientos del Banco de Horas.
     */
    @OneToMany(mappedBy = "bancoHoras", cascade = CascadeType.ALL)
    @OrderBy("fechaCreacion DESC")
    @ReadOnly
    @ListProperties("fechaCreacion, fechaJornada, tipo, minutosFormateados, saldoAnteriorFormateado, saldoNuevoFormateado, usuarioOperacion, observacion")
    private Collection<MovimientoBancoHoras> movimientos = new ArrayList<>();

    // =========================================================================
    // MÉTODOS DISPLAY TRANSIENT PARA OPENXAVA
    // =========================================================================

    /**
     * Formateador del saldo actual para la UI.
     */
    @Transient
    @DisplaySize(25)
    public String getSaldoBancoHorasDisplay() {
        return "Saldo Actual: " + formatearMinutos(saldoMinutosActual);
    }

    /**
     * Formateador de total de horas acreditadas positivas.
     */
    @Transient
    @DisplaySize(25)
    public String getTotalPositivosDisplay() {
        return "Total Ingresado: " + formatearMinutos(totalMinutosPositivos);
    }

    /**
     * Formateador de total de horas adeudadas/descontadas.
     */
    @Transient
    @DisplaySize(25)
    public String getTotalNegativosDisplay() {
        return "Total Deuda/Descuento: " + formatearMinutos(totalMinutosNegativos);
    }

    /**
     * Cantidad total de movimientos registrados.
     */
    @Transient
    @DisplaySize(20)
    public String getCantidadMovimientosDisplay() {
        int cant = (movimientos != null) ? movimientos.size() : 0;
        return "Movimientos: " + cant;
    }

    private String formatearMinutos(int totalMinutos) {
        String signo = totalMinutos < 0 ? "-" : (totalMinutos > 0 ? "+" : "");
        int abs = Math.abs(totalMinutos);
        int h = abs / 60;
        int m = abs % 60;
        return String.format("%s%02d:%02d hs", signo, h, m);
    }
}
