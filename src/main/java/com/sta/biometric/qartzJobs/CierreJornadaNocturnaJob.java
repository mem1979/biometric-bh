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
 * Job para cerrar jornadas nocturnas del día anterior.
 * Se ejecuta a las 12:00 PM (después de que terminan los turnos nocturnos típicos).
 * 
 * <p>
 * <strong>Resiliencia:</strong> Cada jornada nocturna se procesa en su propia transacción.
 * </p>
 */
@DisallowConcurrentExecution
public class CierreJornadaNocturnaJob implements Job {

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        LocalDate ayer = LocalDate.now().minusDays(1);
        System.out.println("[CierreJornadaNocturnaJob] ===== INICIO " + LocalDateTime.now() + " =====");
        System.out.println("[CierreJornadaNocturnaJob] Buscando jornadas nocturnas de: " + ayer);

        EntityManager em = XPersistence.createManager();

        try {
            List<AuditoriaRegistros> nocturnas;
            try {
                em.getTransaction().begin();
                nocturnas = em.createQuery(
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
                em.getTransaction().commit();
            } catch (Exception e) {
                if (em.getTransaction().isActive()) em.getTransaction().rollback();
                System.err.println("[CierreJornadaNocturnaJob] Error al buscar jornadas nocturnas: " + e.getMessage());
                return;
            }

            System.out.println("[CierreJornadaNocturnaJob] Jornadas nocturnas pendientes: " + nocturnas.size());

            if (nocturnas.isEmpty()) {
                System.out.println("[CierreJornadaNocturnaJob] ===== FIN (nada que procesar) =====");
                return;
            }

            int cerradas = 0;
            int errores = 0;
            int pospuestas = 0;
            LocalTime ahora = LocalTime.now();

            for (AuditoriaRegistros asistencia : nocturnas) {
                try {
                    em.getTransaction().begin();

                    // === VERIFICAR HORA DE SALIDA ESPERADA ===
                    LocalTime horaSalidaEsperada = asistencia.getHoraEsperadaSalida();
                    if (horaSalidaEsperada != null && ahora.isBefore(horaSalidaEsperada)) {
                        System.out.println("  [⏳] Pospuesta (termina " + horaSalidaEsperada + "): " +
                                (asistencia.getEmpleado() != null ? asistencia.getEmpleado().getNombreCompleto()
                                        : "Empleado desconocido"));
                        pospuestas++;
                        em.getTransaction().commit();
                        continue;
                    }
                    // === FIN VERIFICACIÓN ===

                    // DELEGACIÓN AL SERVICIO - Pasando EntityManager
                    GestionJornadasService.getInstance().cerrarJornada(asistencia, em);
                    em.getTransaction().commit();
                    cerradas++;
                    System.out.println("  [✓] Cerrada: " +
                            (asistencia.getEmpleado() != null ? asistencia.getEmpleado().getNombreCompleto()
                                    : "Empleado desconocido"));

                } catch (Exception e) {
                    errores++;
                    if (em.getTransaction().isActive()) {
                        em.getTransaction().rollback();
                    }
                    System.err.println("  [!] Error cerrando " +
                            (asistencia.getEmpleado() != null ? asistencia.getEmpleado().getNombreCompleto()
                                    : "Empleado desconocido")
                            + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }

            System.out.println("[CierreJornadaNocturnaJob] Resultado: " + cerradas + " cerradas, " +
                    pospuestas + " pospuestas, " + errores + " errores.");
            System.out.println("[CierreJornadaNocturnaJob] ===== FIN " + LocalDateTime.now() + " =====");

        } catch (Exception e) {
            System.err.println("[CierreJornadaNocturnaJob] ERROR GENERAL: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (em != null && em.isOpen()) {
                em.close();
            }
        }
    }
}
