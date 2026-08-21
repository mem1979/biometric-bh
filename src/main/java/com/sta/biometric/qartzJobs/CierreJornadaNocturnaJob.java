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
 * Job para cerrar jornadas nocturnas del día anterior.
 * Se ejecuta a las 12:00 PM según el patrón oficial de OpenXava.
 */
@DisallowConcurrentExecution
public class CierreJornadaNocturnaJob implements Job {

    private static final Log log = LogFactory.getLog(CierreJornadaNocturnaJob.class);

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            LocalDate ayer = LocalDate.now().minusDays(1);
            log.info("[CierreJornadaNocturnaJob] ===== INICIO " + LocalDateTime.now() + " =====");

            EntityManager em = XPersistence.getManager();

            List<AuditoriaRegistros> nocturnas = em.createQuery(
                    "SELECT a FROM AuditoriaRegistros a " +
                            "WHERE a.fecha = :fecha " +
                            "AND a.esJornadaNocturna = true " +
                            "AND a.evaluacion IN :estados",
                    AuditoriaRegistros.class)
                    .setParameter("fecha", ayer)
                    .setParameter("estados", java.util.Arrays.asList(
                            EvaluacionJornada.EN_CURSO,
                            EvaluacionJornada.PENDIENTE))
                    .getResultList();

            log.info("[CierreJornadaNocturnaJob] Jornadas nocturnas pendientes: " + nocturnas.size());

            if (nocturnas.isEmpty()) {
                XPersistence.commit();
                log.info("[CierreJornadaNocturnaJob] ===== FIN (nada que procesar) =====");
                return;
            }

            int cerradas = 0;
            int errores = 0;
            int pospuestas = 0;
            LocalTime ahora = LocalTime.now();

            for (AuditoriaRegistros asistencia : nocturnas) {
                try {
                    LocalTime horaSalidaEsperada = asistencia.getHoraEsperadaSalida();
                    if (horaSalidaEsperada != null && ahora.isBefore(horaSalidaEsperada)) {
                        pospuestas++;
                        continue;
                    }

                    GestionJornadasService.getInstance().cerrarJornada(asistencia, em);
                    cerradas++;

                } catch (Exception e) {
                    errores++;
                    log.error("[CierreJornadaNocturnaJob] Error cerrando nocturna de " +
                            (asistencia.getEmpleado() != null ? asistencia.getEmpleado().getNombreCompleto()
                                    : "empleado"), e);
                }
            }

            XPersistence.commit();
            log.info("[CierreJornadaNocturnaJob] Resultado: " + cerradas + " cerradas, " + pospuestas + " pospuestas, " + errores + " errores.");
            log.info("[CierreJornadaNocturnaJob] ===== FIN " + LocalDateTime.now() + " =====");

        } catch (Exception e) {
            XPersistence.rollback();
            log.error("[CierreJornadaNocturnaJob] ERROR GENERAL en la ejecución", e);
        } finally {
            XPersistence.commit(); // Cierre y liberación estricta del ThreadLocal según estándar OpenXava
        }
    }
}
