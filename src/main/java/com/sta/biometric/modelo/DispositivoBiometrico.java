package com.sta.biometric.modelo;

import javax.persistence.*;

import org.openxava.annotations.*;
import org.openxava.jpa.*;
import org.openxava.model.*;
import com.sta.biometric.auxiliares.Sucursales;

import lombok.*;

/**
 * Entidad que representa un dispositivo biométrico Hikvision
 * instalado en una sucursal o sector.
 *
 * <p>
 * Cada dispositivo tiene un {@code ultimoSerialNo} que se utiliza
 * para deduplicación de eventos recibidos vía HTTP Host Push.
 * </p>
 *
 * @author Sistema STARH
 * @version 1.0
 * @see Personal
 * @see Sucursales
 */
@Entity
@Table(name = "DispositivoBiometrico", indexes = {
        @Index(name = "idx_dispositivo_sucursal", columnList = "sucursal_id")
})
@Getter
@Setter
@View(members = "codigo; nombre; sucursal; activo; toleranciaDuplicadosSegundos; ultimoSerialNo")
@Tab(properties = "codigo, nombre, sucursal.nombre, activo, toleranciaDuplicadosSegundos, ultimoSerialNo", defaultOrder = "${nombre} asc")
public class DispositivoBiometrico extends Identifiable {

    /**
     * Código autogenerado para el biométrico (formato TMTxx, lectura únicamente).
     */
    @ReadOnly
    @Column(length = 10, unique = true)
    private String codigo;

    /**
     * Nombre descriptivo del dispositivo.
     * Ejemplo: "Fichador Entrada Principal", "Fichador Planta Baja"
     */
    @Required
    @Column(length = 100)
    @DisplaySize(40)
    private String nombre;

    /**
     * Sucursal o sector donde está instalado el dispositivo.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @DescriptionsList
    private Sucursales sucursal;

    /**
     * Indica si el dispositivo está activo y puede recibir fichadas.
     */
    @Column(columnDefinition = "BOOLEAN DEFAULT TRUE")
    private boolean activo = true;

    /**
     * Tolerancia en segundos para considerar una fichada como duplicada (por defecto 1800 segundos / 30 minutos).
     */
    @Column(name = "tolerancia_duplicados_segundos", columnDefinition = "INTEGER DEFAULT 1800")
    private int toleranciaDuplicadosSegundos = 1800;

    /**
     * Último número de serie (serialNo) procesado desde este dispositivo.
     * Se utiliza para deduplicación de eventos.
     */
    @ReadOnly
    @Column(name = "ultimo_serial_no")
    private int ultimoSerialNo = 0;

    @PrePersist // Ejecutado justo antes de grabar el objeto por primera vez
    private void calcularCodigo() {
        Long total = XPersistence.getManager().createQuery(
            "select count(d) from DispositivoBiometrico d", Long.class)
            .getSingleResult();
        if (total >= 99) {
            throw new javax.validation.ValidationException("Se ha alcanzado el limite maximo de 99 dispositivos biometricos.");
        }

        String maxCodigo = XPersistence.getManager().createQuery(
            "select max(d.codigo) from DispositivoBiometrico d", String.class)
            .getSingleResult();

        int siguiente = 1;
        if (maxCodigo != null && maxCodigo.startsWith("TMT")) {
            try {
                siguiente = Integer.parseInt(maxCodigo.substring(3)) + 1;
            } catch (NumberFormatException e) {
                // Ignorar error de parsing
            }
        }

        if (siguiente > 99) {
            throw new javax.validation.ValidationException("No hay codigos disponibles en el rango TMT01-TMT99.");
        }

        this.codigo = String.format("TMT%02d", siguiente);
    }
}
