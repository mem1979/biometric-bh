package com.sta.biometric.qartzJobs;

import java.time.*;
import java.util.*;

import javax.persistence.*;

import org.apache.commons.logging.*;
import org.openxava.jpa.*;
import org.quartz.*;

import com.sta.biometric.enums.*;
import com.sta.biometric.modelo.*;
import com.sta.biometric.servicios.GestionJornadasService;

/**
 * Tarea programada para generar la apertura de jornada diaria para todos los empleados activos.
 * Ejecutada automáticamente a las 00:01 hs según el patrón oficial de OpenXava.
 */
@DisallowConcurrentExecution
public class AperturaJornadaJob implements Job {

    private static final Log log = LogFactory.getLog(AperturaJornadaJob.class);

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            LocalDate hoy = LocalDate.now();
            log.info("[AperturaJornadaJob] ===== INICIO " + LocalDateTime.now() + " =====");

            EntityManager em = XPersistence.getManager();

            List<Personal> empleados = em.createQuery(
                    "SELECT e FROM Personal e WHERE e.activo = true AND e.eliminado = false", Personal.class)
                    .getResultList();

            log.info("[AperturaJornadaJob] Empleados activos encontrados: " + empleados.size());

            int contador = 0;
            int omitidos = 0;

            for (Personal empleado : empleados) {
                try {
                    LocalDate ayer = hoy.minusDays(1);
                    AuditoriaRegistros jornadaNocturnaAbierta = buscarJornadaNocturnaEnCurso(empleado, ayer, em);

                    if (jornadaNocturnaAbierta != null) {
                        omitidos++;
                        continue;
                    }

                    GestionJornadasService.getInstance().abrirOActualizarJornada(empleado, hoy, em);
                    contador++;

                } catch (Exception e) {
                    log.error("[AperturaJornadaJob] Error procesando " + (empleado != null ? empleado.getNombreCompleto() : "desconocido"), e);
                }
            }

            XPersistence.commit();
            log.info("[AperturaJornadaJob] Resultado: " + contador + " abiertos, " + omitidos + " omitidos por nocturna.");
            log.info("[AperturaJornadaJob] ===== FIN " + LocalDateTime.now() + " =====");

        } catch (Exception e) {
            XPersistence.rollback();
            log.error("[AperturaJornadaJob] ERROR GENERAL en la ejecución", e);
        } finally {
            XPersistence.commit(); // Cierre y liberación estricta del ThreadLocal según estándar OpenXava
        }
    }

    private AuditoriaRegistros buscarJornadaNocturnaEnCurso(Personal empleado, LocalDate fecha, EntityManager em) {
        try {
            return em.createQuery(
                    "SELECT a FROM AuditoriaRegistros a " +
                            "WHERE a.empleado = :emp " +
                            "AND a.fecha = :fecha " +
                            "AND a.esJornadaNocturna = true " +
                            "AND a.evaluacion = :estado",
                    AuditoriaRegistros.class)
                    .setParameter("emp", empleado)
                    .setParameter("fecha", fecha)
                    .setParameter("estado", EvaluacionJornada.EN_CURSO)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }
}
