package com.sta.biometric.qartzJobs;

import java.time.*;
import java.util.*;

import javax.persistence.*;

import org.openxava.jpa.*;
import org.quartz.*;

import com.sta.biometric.enums.*;
import com.sta.biometric.modelo.*;
import com.sta.biometric.servicios.GestionJornadasService;

/**
 * Tarea programada para cerrar automáticamente la jornada diaria consolidando los registros.
 * Se ejecuta todos los días a las 23:59 hs mediante Quartz Scheduler.
 * 
 * <p>
 * <strong>Resiliencia:</strong> Cada jornada se procesa en su propia transacción para asegurar
 * que un fallo aislado no interrumpa el procesamiento del resto.
 * </p>
 */
@DisallowConcurrentExecution
public class CierreJornadaJob implements Job {

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        LocalDate hoy = LocalDate.now();
        System.out.println("[CierreJornadaJob] ===== INICIO " + LocalDateTime.now() + " =====");
        System.out.println("[CierreJornadaJob] Procesando fecha: " + hoy);

        EntityManager em = XPersistence.createManager();

        try {
            List<AuditoriaRegistros> asistencias;
            try {
                em.getTransaction().begin();
                asistencias = em.createQuery(
                        "SELECT a FROM AuditoriaRegistros a WHERE a.fecha = :fecha", AuditoriaRegistros.class)
                        .setParameter("fecha", hoy)
                        .getResultList();
                em.getTransaction().commit();
            } catch (Exception e) {
                if (em.getTransaction().isActive()) em.getTransaction().rollback();
                System.err.println("[CierreJornadaJob] Error consultando asistencias del día: " + e.getMessage());
                return;
            }

            System.out.println("[CierreJornadaJob] Jornadas encontradas: " + asistencias.size());

            int cerrados = 0;
            int postponed = 0;
            int errores = 0;

            for (AuditoriaRegistros asistencia : asistencias) {
                try {
                    em.getTransaction().begin();

                    // === SOPORTE JORNADAS NOCTURNAS ===
                    if (asistencia.isEsJornadaNocturna() &&
                            asistencia.getEvaluacion() == EvaluacionJornada.EN_CURSO) {
                        System.out.println("  [⏳] Postponed (nocturna): " +
                                (asistencia.getEmpleado() != null ? asistencia.getEmpleado().getNombreCompleto()
                                        : "Empleado desconocido"));
                        postponed++;
                        em.getTransaction().commit();
                        continue;
                    }
                    // === FIN SOPORTE NOCTURNAS ===

                    // DELEGACIÓN AL SERVICIO - Pasando EntityManager
                    GestionJornadasService.getInstance().cerrarJornada(asistencia, em);
                    em.getTransaction().commit();
                    cerrados++;

                } catch (Exception e) {
                    errores++;
                    if (em.getTransaction().isActive()) {
                        em.getTransaction().rollback();
                    }
                    System.err.println("[!] Error consolidando " +
                            (asistencia.getEmpleado() != null ? asistencia.getEmpleado().getNombreCompleto()
                                    : "Empleado desconocido")
                            + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }

            System.out.println("[CierreJornadaJob] Resultado: " + cerrados + " cerrados, " + postponed + " postponed, "
                    + errores + " errores.");
            System.out.println("[CierreJornadaJob] ===== FIN " + LocalDateTime.now() + " =====");

        } catch (Exception e) {
            System.err.println("[CierreJornadaJob] ERROR GENERAL: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (em != null && em.isOpen()) {
                em.close();
            }
        }
    }
}