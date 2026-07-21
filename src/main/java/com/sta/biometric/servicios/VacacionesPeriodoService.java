package com.sta.biometric.servicios;

import java.time.*;
import java.util.*;

import org.openxava.jpa.XPersistence;
import com.sta.biometric.modelo.Personal;
import com.sta.biometric.enums.TipoLicenciaAR;
import com.sta.biometric.enums.ModoComputoLicencia;

/**
 * Servicio centralizado para el cálculo y validación de vacaciones según LCT.
 * NOTA: 'periodoDevengado' representa el año de trabajo acumulado, el cual es independiente
 * del año calendario en el que se efectúa el goce real de la licencia.
 */
public class VacacionesPeriodoService {

    private static VacacionesPeriodoService instance = new VacacionesPeriodoService();

    public static VacacionesPeriodoService getInstance() {
        return instance;
    }

    public static void setInstance(VacacionesPeriodoService newInstance) {
        instance = newInstance;
    }

    public int calcularPeriodoDevengado(TipoLicenciaAR tipo, LocalDate fechaInicio) {
        return calcularPeriodoDevengado(null, tipo, fechaInicio);
    }

    public int calcularPeriodoDevengado(Personal empleado, TipoLicenciaAR tipo, LocalDate fechaInicio) {
        int devengado = calcularPeriodoDevengadoEstandar(empleado, tipo, fechaInicio);
        if (tipo == TipoLicenciaAR.VACACIONES && empleado != null && empleado.getInicioActividades() != null) {
            try {
                Map<Integer, Integer> saldos = obtenerPeriodosDisponiblesConSaldo(empleado, null, null);
                if (saldos != null && !saldos.isEmpty()) {
                    if (saldos.containsKey(devengado) && saldos.get(devengado) > 0) {
                        return devengado;
                    }
                    for (Map.Entry<Integer, Integer> entry : saldos.entrySet()) {
                        if (entry.getValue() > 0) {
                            return entry.getKey();
                        }
                    }
                }
            } catch (Exception e) {
                // Silently fallback if db or context fails
            }
        }
        return devengado;
    }

    public int calcularPeriodoDevengadoEstandar(Personal empleado, TipoLicenciaAR tipo, LocalDate fechaInicio) {
        if (fechaInicio == null) return java.time.LocalDate.now().getYear();
        int devengado = fechaInicio.getYear();
        if (tipo == TipoLicenciaAR.VACACIONES) {
            // Enero-Septiembre corresponden al periodo devengado del año anterior
            if (fechaInicio.getMonthValue() >= 1 && fechaInicio.getMonthValue() <= 9) {
                devengado = fechaInicio.getYear() - 1;
            }
        }
        if (empleado != null && empleado.getInicioActividades() != null) {
            int anioInicio = empleado.getInicioActividades().getYear();
            if (devengado < anioInicio) {
                devengado = anioInicio;
            }
        }
        return devengado;
    }

    public int calcularDiasVacacionesPorAntiguedad(Personal empleado, int periodo) {
        return calcularDiasVacacionesPorAntiguedad(empleado, periodo, null);
    }

    public int calcularDiasVacacionesPorAntiguedad(Personal empleado, int periodo, LocalDate fechaReferencia) {
        if (empleado == null || empleado.getInicioActividades() == null) return 0;
        
        LocalDate fechaRefLCT = LocalDate.of(periodo, 12, 31);
        
        // Determinar fecha límite para evaluar días trabajados
        LocalDate limite = fechaReferencia;
        // Si el periodo es de un año anterior (ya cerrado), el trabajo efectivo se evalúa al cierre del año (31/12/periodo)
        if (periodo < LocalDate.now().getYear()) {
            limite = fechaRefLCT;
        } else if (limite == null) {
            LocalDate hoy = LocalDate.now();
            limite = hoy.isBefore(fechaRefLCT) ? hoy : fechaRefLCT;
        } else if (limite.isAfter(fechaRefLCT)) {
            limite = fechaRefLCT;
        }

        int antiguedadAnios = Period.between(empleado.getInicioActividades(), fechaRefLCT).getYears();
        long totalDiasTrabajados = java.time.temporal.ChronoUnit.DAYS.between(empleado.getInicioActividades(), limite);

        if (totalDiasTrabajados < 0) return 0;

        // Deuda Técnica (Art. 151 LCT): Proporción de 1 día cada 20 días efectivos trabajados 
        // cuando la antigüedad es menor a 6 meses (180 días de corrida).
        if (totalDiasTrabajados < 180) {
            return (int) (totalDiasTrabajados / 20); 
        }
        if (antiguedadAnios < 5) return 14;
        if (antiguedadAnios < 10) return 21;
        if (antiguedadAnios < 20) return 28;
        return 35;
    }

    public int obtenerDiasTomados(Personal empleado, TipoLicenciaAR tipo, int periodo, String excluyendoLicenciaId) {
        if (empleado == null || tipo == null) return 0;

        String jpql;
        boolean tieneIdExclusion = (excluyendoLicenciaId != null && !excluyendoLicenciaId.trim().isEmpty());
        
        LocalDate inicioAnio = LocalDate.of(periodo, 1, 1);
        LocalDate finAnio = LocalDate.of(periodo, 12, 31);

        if (tipo == TipoLicenciaAR.VACACIONES) {
            jpql = "SELECT COALESCE(SUM(l.dias), 0) FROM Licencia l " +
                   "WHERE l.empleado = :empleado AND l.tipo = :tipo " +
                   "AND (l.periodoDevengado = :periodo OR (l.periodoDevengado IS NULL AND l.fechaInicio BETWEEN :inicioAnio AND :finAnio))" +
                   (tieneIdExclusion ? " AND l.id != :excluidoId" : "");
        } else {
            jpql = "SELECT COALESCE(SUM(l.dias), 0) FROM Licencia l " +
                   "WHERE l.empleado = :empleado AND l.tipo = :tipo " +
                   "AND l.fechaInicio BETWEEN :inicioAnio AND :finAnio" +
                   (tieneIdExclusion ? " AND l.id != :excluidoId" : "");
        }

        var query = XPersistence.getManager()
                .createQuery(jpql)
                .setFlushMode(javax.persistence.FlushModeType.COMMIT)
                .setParameter("empleado", empleado)
                .setParameter("tipo", tipo);

        if (tipo == TipoLicenciaAR.VACACIONES) {
            query.setParameter("periodo", periodo);
        }

        query.setParameter("inicioAnio", inicioAnio);
        query.setParameter("finAnio", finAnio);

        if (tieneIdExclusion) {
            query.setParameter("excluidoId", excluyendoLicenciaId);
        }

        return ((Number) query.getSingleResult()).intValue();
    }

    public int calcularDiasMaximosPorTipo(Personal empleado, TipoLicenciaAR tipo, int periodo) {
        return calcularDiasMaximosPorTipo(empleado, tipo, periodo, null);
    }

    public int calcularDiasMaximosPorTipo(Personal empleado, TipoLicenciaAR tipo, int periodo, LocalDate fechaReferencia) {
        if (tipo == TipoLicenciaAR.VACACIONES) {
            return calcularDiasVacacionesPorAntiguedad(empleado, periodo, fechaReferencia);
        } else if (tipo == TipoLicenciaAR.ENFERMEDAD) {
            if (empleado == null || empleado.getInicioActividades() == null) return 0;
            long diasTrabajados = java.time.temporal.ChronoUnit.DAYS.between(empleado.getInicioActividades(), LocalDate.now());
            return diasTrabajados < 5 * 365 ? 90 : 180;
        } else {
            String keyBase = "licencia." + tipo.name();
            return ConfiguracionesPreferencias.obtenerValor(keyBase + ".diasPorAnio", 0, Integer.class);
        }
    }

    public boolean esPeriodoValidoVacaciones(int periodo, LocalDate fechaInicio) {
        if (fechaInicio == null) return true;
        
        boolean permiteFuera = ConfiguracionesPreferencias.obtenerValor("permiteVacacionesFueraPeriodo", true, java.lang.Boolean.class);
        if (permiteFuera) return true;

        LocalDate limiteInferior = LocalDate.of(periodo, 10, 1);
        LocalDate limiteSuperior = LocalDate.of(periodo + 1, 4, 30);

        return !fechaInicio.isBefore(limiteInferior) && !fechaInicio.isAfter(limiteSuperior);
    }

    /**
     * Determina si las vacaciones para un período específico se están computando en días hábiles.
     */
    private boolean esPeriodoEnHabiles(Personal empleado, int periodo, String excluyendoId, ModoComputoLicencia modoComputoActual) {
        if (modoComputoActual == ModoComputoLicencia.DIAS_CORRIDOS_HABILES) {
            return true;
        }
        
        String jpql = "SELECT COUNT(l) FROM Licencia l WHERE l.empleado = :empleado " +
                      "AND l.tipo = :tipo AND l.periodoDevengado = :periodo " +
                      "AND l.modoComputo = :modo " +
                      (excluyendoId != null ? "AND l.id != :excluidoId" : "");
                      
        var query = XPersistence.getManager().createQuery(jpql)
                .setFlushMode(javax.persistence.FlushModeType.COMMIT)
                .setParameter("empleado", empleado)
                .setParameter("tipo", TipoLicenciaAR.VACACIONES)
                .setParameter("periodo", periodo)
                .setParameter("modo", ModoComputoLicencia.DIAS_CORRIDOS_HABILES);
                
        if (excluyendoId != null) {
            query.setParameter("excluidoId", excluyendoId);
        }
        
        return ((Number) query.getSingleResult()).longValue() > 0;
    }

    /**
     * Obtiene el período de referencia actual considerando si se permiten vacaciones fuera de período.
     */
    public int obtenerPeriodoActualReferencia() {
        return obtenerPeriodoActualReferencia(null);
    }

    /**
     * Obtiene el período de referencia actual considerando si se permiten vacaciones fuera de período
     * y capando inferiormente al año de ingreso del empleado.
     */
    public int obtenerPeriodoActualReferencia(Personal empleado) {
        boolean permiteFuera = ConfiguracionesPreferencias.obtenerValor("permiteVacacionesFueraPeriodo", true, java.lang.Boolean.class);
        int periodo;
        if (permiteFuera) {
            periodo = LocalDate.now().getYear();
        } else {
            periodo = calcularPeriodoDevengadoEstandar(empleado, TipoLicenciaAR.VACACIONES, LocalDate.now());
        }
        if (empleado != null && empleado.getInicioActividades() != null) {
            int anioInicio = empleado.getInicioActividades().getYear();
            if (periodo < anioInicio) {
                periodo = anioInicio;
            }
        }
        return periodo;
    }

    /**
     * FASE 2: Obtiene períodos devengados con saldo positivo de vacaciones.
     * Incluye el período legal actual y los dos anteriores con saldo > 0.
     *
     * @param empleado            Empleado a consultar
     * @param excluyendoId        ID de licencia a excluir del cálculo (para edición), puede ser null
     * @param modoComputoActual   Modo de cómputo actual/solicitado de la licencia (o null)
     * @return Map&lt;Integer, Integer&gt; ordenado ascendente: período → saldo disponible
     */
    public Map<Integer, Integer> obtenerPeriodosDisponiblesConSaldo(
            Personal empleado, String excluyendoId, ModoComputoLicencia modoComputoActual) {

        if (empleado == null || empleado.getInicioActividades() == null) {
            return Collections.emptyMap();
        }

        // Si el modo computo actual no está definido, resolver el por defecto de la configuración
        if (modoComputoActual == null) {
            modoComputoActual = ConfiguracionesPreferencias.obtenerValor(
                    "licencia.VACACIONES.modoComputo",
                    ModoComputoLicencia.DIAS_HABILES,
                    ModoComputoLicencia.class);
        }

        // Período de referencia actual (considerando configuración de fuera de período)
        int periodoActual = obtenerPeriodoActualReferencia(empleado);

        // Año de inicio del empleado
        int anioInicio = empleado.getInicioActividades().getYear();

        // FASE 2: Limitar los períodos devengados al período actual y dos anteriores (total de 3 períodos)
        int periodoInicio = Math.max(anioInicio, periodoActual - 2);

        // Iterar desde periodoInicio hasta período actual inclusive
        Map<Integer, Integer> resultado = new TreeMap<>(); // TreeMap = orden ascendente natural

        for (int periodo = periodoInicio; periodo <= periodoActual; periodo++) {
            int diasPorAnio = calcularDiasMaximosPorTipo(
                    empleado, TipoLicenciaAR.VACACIONES, periodo);
            
            // Si el período computa como hábiles, escalar el límite legal de días corridos a hábiles
            if (esPeriodoEnHabiles(empleado, periodo, excluyendoId, modoComputoActual)) {
                diasPorAnio = (diasPorAnio * 5) / 7;
            }
            
            int diasTomados = obtenerDiasTomados(
                    empleado, TipoLicenciaAR.VACACIONES, periodo, excluyendoId);
            int saldo = diasPorAnio - diasTomados;

            if (saldo > 0) {
                resultado.put(periodo, saldo);
            }
        }

        return resultado;
    }
}
