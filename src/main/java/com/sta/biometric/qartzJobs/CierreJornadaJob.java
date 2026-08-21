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
 * Tarea programada para cerrar automáticamente la jornada diaria consolidando los registros.
 * Se ejecuta todos los días a las 23:59 hs según el patrón oficial de OpenXava.
 */
@DisallowConcurrentExecution
public class CierreJornadaJob implements Job {

    private static final Log log = LogFactory.getLog(CierreJornadaJob.class);

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            LocalDate hoy = LocalDate.now();
            log.info("[CierreJornadaJob] ===== INICIO " + LocalDateTime.now() + " =====");

            EntityManager em = XPersistence.getManager();

            List<AuditoriaRegistros> asistencias = em.createQuery(
                    "SELECT a FROM AuditoriaRegistros a WHERE a.fecha = :fecha", AuditoriaRegistros.class)
                    .setParameter("fecha", hoy)
                    .getResultList();

            log.info("[CierreJornadaJob] Jornadas encontradas: " + asistencias.size());

            int cerrados = 0;
            int postponed = 0;
            int errores = 0;

            for (AuditoriaRegistros asistencia : asistencias) {
                try {
                    if (asistencia.isEsJornadaNocturna() &&
                            asistencia.getEvaluacion() == EvaluacionJornada.EN_CURSO) {
                        postponed++;
                        continue;
                    }

                    GestionJornadasService.getInstance().cerrarJornada(asistencia, em);
                    cerrados++;

                } catch (Exception e) {
                    errores++;
                    log.error("[CierreJornadaJob] Error consolidando " +
                            (asistencia.getEmpleado() != null ? asistencia.getEmpleado().getNombreCompleto()
                                    : "empleado"), e);
                }
            }

            XPersistence.commit();
            log.info("[CierreJornadaJob] Resultado: " + cerrados + " cerrados, " + postponed + " pospuestos, " + errores + " errores.");
            log.info("[CierreJornadaJob] ===== FIN " + LocalDateTime.now() + " =====");

        } catch (Exception e) {
            XPersistence.rollback();
            log.error("[CierreJornadaJob] ERROR GENERAL en la ejecución", e);
        } finally {
            XPersistence.commit(); // Cierre y liberación estricta del ThreadLocal según estándar OpenXava
        }
    }
}