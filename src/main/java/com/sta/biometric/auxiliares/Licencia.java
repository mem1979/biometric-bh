
package com.sta.biometric.auxiliares;

import java.time.*;
import java.util.Map;

import javax.persistence.*;
import javax.validation.constraints.*;

import org.openxava.annotations.*;
import org.openxava.calculators.*;
import org.openxava.jpa.*;
import org.openxava.model.*;

import com.sta.biometric.acciones.*;
import com.sta.biometric.enums.*;
import com.sta.biometric.formateadores.*;
import com.sta.biometric.modelo.*;
import com.sta.biometric.servicios.*;

import lombok.*;

@View(members = "tipo, modoComputo, periodoDevengado;" +
        "fechaInicio, fechaFin, dias, diasRestantes;" +
        "Configuracion { justificado, conGoce;" +
        "esParcial, horaInicio, horaFin };" +
        "Documentacion { certificado; observacion }")

@Tab(editors = "List", properties = "empleado.nombreCompleto, tipo, periodoDevengado, fechaInicio, fechaFin, dias, justificado", defaultOrder = "${empleado.nombreCompleto} asc")

@Entity
@Getter
@Setter
@RemoveValidator(LicenciaRemoveValidator.class)
public class Licencia extends Identifiable {

    @Required
    @NoFrame
    @ReferenceView("simple")
    @ManyToOne(fetch = FetchType.LAZY)
    private Personal empleado;

    @Required
    @LabelFormat(LabelFormatType.SMALL)
    @DefaultValueCalculator(CurrentLocalDateCalculator.class)
    @OnChange(CompletarObservacionLicenciaAction.class)
    private LocalDate fechaInicio;

    @Required
    @LabelFormat(LabelFormatType.SMALL)
    @DefaultValueCalculator(CurrentLocalDateCalculator.class)
    @OnChange(CompletarObservacionLicenciaAction.class)
    private LocalDate fechaFin;

    @Column(name = "periododevengado")
    @LabelFormat(LabelFormatType.SMALL)
    @OnChange(CompletarObservacionLicenciaAction.class)
    private Integer periodoDevengado;

    @AssertTrue(message = "La fecha de inicio no puede ser posterior a la fecha de fin")
    public boolean isFechasValidas() {
        if (fechaInicio == null || fechaFin == null)
            return true;
        return !fechaInicio.isAfter(fechaFin);
    }

    @AssertTrue(message = "Las vacaciones deben gozarse entre el 01/10 del período devengado y el 30/04 del año siguiente")
    public boolean isPeriodoValidoVacaciones() {
        if (tipo != TipoLicenciaAR.VACACIONES || fechaInicio == null || periodoDevengado == null) return true;
        return VacacionesPeriodoService.getInstance().esPeriodoValidoVacaciones(periodoDevengado, fechaInicio);
    }

    @AssertTrue(message = "El período devengado no puede ser posterior al año de inicio de la licencia")
    public boolean isPeriodoConsistente() {
        if (fechaInicio == null || periodoDevengado == null) return true;
        return periodoDevengado <= fechaInicio.getYear();
    }

    @AssertTrue(message = "Las vacaciones anuales no pueden ser parciales (por horas)")
    public boolean isVacacionesCompletas() {
        if (tipo == TipoLicenciaAR.VACACIONES) {
            return !esParcial;
        }
        return true;
    }

    // isSaldoSuficiente() movido a @PrePersist para ejecutarse dentro de los bloqueos pesimistas activos

    @ReadOnly
    @DisplaySize(5)
    @LabelFormat(LabelFormatType.SMALL)
    private Integer dias;

    @DisplaySize(5)
    private Integer diasRestantes;

    @Required
    @LabelFormat(LabelFormatType.SMALL)
    @OnChange(CompletarObservacionLicenciaAction.class)
    @Enumerated(EnumType.STRING)
    private TipoLicenciaAR tipo;

    @Required
    @LabelFormat(LabelFormatType.SMALL)
    @OnChange(CompletarObservacionLicenciaAction.class)
    @Enumerated(EnumType.STRING)
    private ModoComputoLicencia modoComputo;

    @OnChange(LicenciaOnChangeJustificadoAction.class)
    @DefaultValueCalculator(TrueCalculator.class)
    @Column(columnDefinition = "BOOLEAN DEFAULT TRUE")
    private boolean justificado;

    /**
     * Indica si la licencia tiene goce de sueldo (imputa horas para liquidación).
     * Se establece automáticamente según el tipo de licencia seleccionado.
     */

    @DefaultValueCalculator(TrueCalculator.class)
    @Column(columnDefinition = "BOOLEAN DEFAULT TRUE")
    private boolean conGoce;

    /**
     * Indica si la licencia es parcial (cubre solo un rango horario del día).
     * Cuando es true, habilita la edición de horaInicio y horaFin.
     */

    @OnChange(LicenciaOnChangeParcialAction.class)
    @DefaultValueCalculator(FalseCalculator.class)
    @Column(columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean esParcial;

    /**
     * Hora de inicio de la licencia (opcional).
     * Solo editable si esParcial = true.
     */
    @LabelFormat(LabelFormatType.SMALL)
    private LocalTime horaInicio;

    /**
     * Hora de fin de la licencia (opcional).
     * Solo editable si esParcial = true.
     */
    @LabelFormat(LabelFormatType.SMALL)
    private LocalTime horaFin;

    @Stereotype("TEXT_AREA")
    @Column(length = 500)
    private String observacion;

    @LabelFormat(LabelFormatType.SMALL)
    @File(maxFileSizeInKb = 200)
    @Column(length = 32)
    private String certificado;

    /**
     * Año correspondiente a la licencia, derivado de la fecha desde.
     * Se usa para cálculos y agrupamientos por año calendario.
     */
    @Hidden
    public int getAnio() {
        return fechaInicio != null ? fechaInicio.getYear() : LocalDate.now().getYear();
    }

    // ==================================================================================
    // PROPIEDADES PARA AGRUPACIÓN EN REPORTES
    // ==================================================================================

    /**
     * Nombre de la sucursal del empleado. Usado para agrupar en reportes.
     */
    @Transient
    @Hidden
    public String getNombreSucursal() {
        if (empleado != null && empleado.getSucursal() != null) {
            return empleado.getSucursal().getNombre();
        }
        return "Sin Sucursal";
    }

    /**
     * Clave de agrupación por mes (formato "2026-04") para ordenamiento cronológico.
     */
    @Transient
    @Hidden
    public String getMesAnio() {
        if (fechaInicio == null) return "0000-00";
        return String.format("%04d-%02d", fechaInicio.getYear(), fechaInicio.getMonthValue());
    }

    /**
     * Nombre legible del mes y año (ej: "Abril 2026") para encabezados de reporte.
     */
    @Transient
    @Hidden
    public String getMesNombre() {
        if (fechaInicio == null) return "";
        return fechaInicio.getMonth()
                .getDisplayName(java.time.format.TextStyle.FULL, new java.util.Locale("es", "ES"))
                + " " + fechaInicio.getYear();
    }

    /**
     * Nombre completo del empleado. Atajo para reportes.
     */
    @Transient
    @Hidden
    public String getNombreEmpleado() {
        return empleado != null ? empleado.getNombreCompleto() : "";
    }

    /**
     * Descripción legible del tipo de licencia. Atajo para reportes.
     */
    @Transient
    @Hidden
    public String getTipoDescripcion() {
        return tipo != null ? tipo.getDescripcion() : "";
    }

    /**
     * Determina si la licencia es parcial (tiene horario definido).
     * Una licencia parcial cubre solo un rango de horas del día,
     * permitiendo que el resto de la jornada sea evaluada con fichajes.
     */
    @Transient
    public boolean isParcial() {
        return esParcial && horaInicio != null && horaFin != null;
    }

    /**
     * Calcula los minutos cubiertos por la licencia.
     * - Si es parcial: devuelve la duración entre horaInicio y horaFin
     * - Si es total: devuelve los minutos esperados del turno completo
     * 
     * @param minutosEsperadosTurno Minutos del turno completo
     * @return Minutos a imputar por licencia
     */
    @Transient
    public int getMinutosLicencia(int minutosEsperadosTurno) {
        if (!isParcial()) {
            return minutosEsperadosTurno; // Licencia total
        }
        // Calcular duración del rango de licencia
        return (int) java.time.Duration.between(horaInicio, horaFin).toMinutes();
    }

    public static boolean tieneLicenciaEnFecha(Personal empleado, LocalDate fecha) {
        EntityManager em = XPersistence.getManager();
        Long count = em.createQuery(
                "select count(l) from Licencia l where l.empleado = :emp " +
                        "and :fecha between l.fechaInicio and l.fechaFin",
                Long.class)
                .setFlushMode(javax.persistence.FlushModeType.COMMIT)
                .setParameter("emp", empleado)
                .setParameter("fecha", fecha)
                .getSingleResult();
        return count > 0;
    }

    /**
     * Obtiene la licencia activa para un empleado en una fecha específica.
     * 
     * @return Licencia activa o null si no existe
     */
    public static Licencia getLicenciaEnFecha(Personal empleado, LocalDate fecha) {
        EntityManager em = XPersistence.getManager();
        return em.createQuery(
                "select l from Licencia l where l.empleado = :emp " +
                        "and :fecha between l.fechaInicio and l.fechaFin",
                Licencia.class)
                .setFlushMode(javax.persistence.FlushModeType.COMMIT)
                .setParameter("emp", empleado)
                .setParameter("fecha", fecha)
                .setMaxResults(1)
                .getResultStream()
                .findFirst()
                .orElse(null);
    }

    @PrePersist
    @PreUpdate
    private void validarAntesDeGuardar() {
        if (empleado == null || fechaInicio == null || fechaFin == null)
            return;

        EntityManager em = XPersistence.getManager();

        // 1. Validar solapamiento con otras licencias
        Licencia licenciaConflictiva = em.createQuery(
                "select l from Licencia l " +
                        "where l.empleado = :emp " +
                        "and l.id <> :id " +
                        "and ( " +
                        "    (:inicio between l.fechaInicio and l.fechaFin) or " +
                        "    (:fin between l.fechaInicio and l.fechaFin) or " +
                        "    (l.fechaInicio between :inicio and :fin) or " +
                        "    (l.fechaFin between :inicio and :fin) " +
                        ")",
                Licencia.class)
                .setFlushMode(javax.persistence.FlushModeType.COMMIT)
                .setParameter("emp", empleado)
                .setParameter("id", getId() == null ? "" : getId())
                .setParameter("inicio", fechaInicio)
                .setParameter("fin", fechaFin)
                .setMaxResults(1)
                .getResultStream()
                .findFirst()
                .orElse(null);

        if (licenciaConflictiva != null) {
            throw new javax.validation.ValidationException(
                    String.format(
                            "Ya existe una licencia para %s desde el %s hasta el %s (Tipo: %s).",
                            empleado.getNombreCompleto(),
                            TiempoUtils.formatearFecha(licenciaConflictiva.getFechaInicio()),
                            TiempoUtils.formatearFecha(licenciaConflictiva.getFechaFin()),
                            licenciaConflictiva.getTipo() != null ? licenciaConflictiva.getTipo().name() : "Sin tipo"));
        }

        // 2. Validar saldo disponible de vacaciones (requiere acceso a BD, se ejecuta con locks activos)
        if (tipo == TipoLicenciaAR.VACACIONES && periodoDevengado != null && dias != null) {
            int diasPorAnio = VacacionesPeriodoService.getInstance()
                    .calcularDiasMaximosPorTipo(empleado, tipo, periodoDevengado, fechaInicio);
            if (modoComputo == ModoComputoLicencia.DIAS_CORRIDOS_HABILES) {
                diasPorAnio = (diasPorAnio * 5) / 7;
            }
            int diasTomados = VacacionesPeriodoService.getInstance()
                    .obtenerDiasTomados(empleado, tipo, periodoDevengado, getId());
            int disponible = Math.max(0, diasPorAnio - diasTomados);

            if (dias > disponible) {
                throw new javax.validation.ValidationException(
                        String.format("No dispone de suficientes días de vacaciones para el período %d. " +
                                "Disponibles: %d, Solicitados: %d.",
                                periodoDevengado, disponible, dias));
            }
        }

        // 2b. Validar saldo disponible de enfermedad (tope legal LCT Art. 208/209)
        if (tipo == TipoLicenciaAR.ENFERMEDAD && dias != null) {
            int anio = fechaInicio != null ? fechaInicio.getYear() : java.time.LocalDate.now().getYear();
            int diasMaxEnfermedad = VacacionesPeriodoService.getInstance()
                    .calcularDiasMaximosPorTipo(empleado, tipo, anio);
            int diasTomadosEnf = VacacionesPeriodoService.getInstance()
                    .obtenerDiasTomados(empleado, tipo, anio, getId());
            int disponibleEnf = Math.max(0, diasMaxEnfermedad - diasTomadosEnf);

            if (dias > disponibleEnf) {
                throw new javax.validation.ValidationException(
                        String.format("No dispone de suficientes días de enfermedad para el año %d. " +
                                "Disponibles: %d, Solicitados: %d.",
                                anio, disponibleEnf, dias));
            }
        }

        // 3. Validar prioridad de períodos anteriores de vacaciones
        if (tipo == TipoLicenciaAR.VACACIONES && periodoDevengado != null) {
            Map<Integer, Integer> periodosConSaldo = VacacionesPeriodoService.getInstance()
                    .obtenerPeriodosDisponiblesConSaldo(empleado, getId(), modoComputo);
            
            for (Map.Entry<Integer, Integer> entry : periodosConSaldo.entrySet()) {
                int p = entry.getKey();
                if (p < periodoDevengado) {
                    throw new javax.validation.ValidationException(
                            String.format("Debe gozar primero los días de vacaciones del período %d (Saldo disponible: %d días).",
                                    p, entry.getValue()));
                }
            }
        }
    }

    @PostRemove
    private void alEliminar() {
        try {
            org.hibernate.engine.spi.SessionImplementor session = 
                XPersistence.getManager().unwrap(org.hibernate.engine.spi.SessionImplementor.class);
            session.getActionQueue().registerProcess(new org.hibernate.action.spi.BeforeTransactionCompletionProcess() {
                @Override
                public void doBeforeTransactionCompletion(org.hibernate.engine.spi.SessionImplementor session) {
                    LicenciaRecalculacionService.recalcularPorLicencia(Licencia.this, true);
                }
            });
        } catch (Exception e) {
            // Fallback si no estamos en una sesión de Hibernate o falla el unwrap
            LicenciaRecalculacionService.recalcularPorLicencia(this, true);
        }
    }

    /**
     * Recalcula los registros de asistencia cuando se crea o modifica la licencia.
     * Esto asegura que los cambios en la licencia se reflejen inmediatamente.
     */
    @PostPersist
    @PostUpdate
    private void recalcularAsistenciasAfectadas() {
        try {
            org.hibernate.engine.spi.SessionImplementor session = 
                XPersistence.getManager().unwrap(org.hibernate.engine.spi.SessionImplementor.class);
            session.getActionQueue().registerProcess(new org.hibernate.action.spi.BeforeTransactionCompletionProcess() {
                @Override
                public void doBeforeTransactionCompletion(org.hibernate.engine.spi.SessionImplementor session) {
                    LicenciaRecalculacionService.recalcularPorLicencia(Licencia.this, false);
                }
            });
        } catch (Exception e) {
            // Fallback si no estamos en una sesión de Hibernate o falla el unwrap
            LicenciaRecalculacionService.recalcularPorLicencia(this, false);
        }
    }

}