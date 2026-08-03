package com.sta.biometric.qartzJobs;

import javax.servlet.*;
import javax.servlet.annotation.*;

import org.quartz.*;
import org.quartz.impl.*;

/**
 * Inicializador del Quartz Scheduler que se ejecuta automáticamente al arrancar el contenedor de servlets.
 * 
 * <p>
 * Jobs programados:
 * </p>
 * <ul>
 * <li><strong>AperturaJornadaJob</strong>: 00:01 AM - Crea/verifica jornadas para el día</li>
 * <li><strong>CierreJornadaJob</strong>: 23:59 PM - Cierra jornadas diurnas</li>
 * <li><strong>CierreJornadaNocturnaJob</strong>: 12:00 PM - Cierra jornadas nocturnas del día anterior</li>
 * </ul>
 */
@WebListener
public class ApplicationQuartzInitializer implements ServletContextListener {

    private static Scheduler scheduler;

    @Override
    public synchronized void contextInitialized(ServletContextEvent sce) {
        try {
            if (scheduler != null && !scheduler.isShutdown() && scheduler.isStarted()) {
                System.out.println("[Quartz] Scheduler ya se encuentra activo en este proceso.");
                return;
            }

            scheduler = StdSchedulerFactory.getDefaultScheduler();

            // Si el scheduler ya tenía jobs cargados de una sesión previa, limpiar antes de reprogramar
            if (scheduler.isStarted()) {
                scheduler.clear();
            }

            // =================================================================
            // JOB 1: Apertura diaria - 00:01 AM
            // =================================================================
            JobDetail aperturaJob = JobBuilder.newJob(AperturaJornadaJob.class)
                    .withIdentity("aperturaJob", "asistencia")
                    .build();

            Trigger aperturaTrigger = TriggerBuilder.newTrigger()
                    .withIdentity("aperturaTrigger", "asistencia")
                    .withSchedule(CronScheduleBuilder.dailyAtHourAndMinute(0, 1)
                            .withMisfireHandlingInstructionFireAndProceed())
                    .build();

            scheduler.scheduleJob(aperturaJob, aperturaTrigger);
            System.out.println("[Quartz] Job APERTURA programado: 00:01 AM (con política misfire FireAndProceed)");

            // =================================================================
            // JOB 2: Cierre diario - 23:59 PM
            // =================================================================
            JobDetail cierreJob = JobBuilder.newJob(CierreJornadaJob.class)
                    .withIdentity("cierreJob", "asistencia")
                    .build();

            Trigger cierreTrigger = TriggerBuilder.newTrigger()
                    .withIdentity("cierreTrigger", "asistencia")
                    .withSchedule(CronScheduleBuilder.dailyAtHourAndMinute(23, 59)
                            .withMisfireHandlingInstructionFireAndProceed())
                    .build();

            scheduler.scheduleJob(cierreJob, cierreTrigger);
            System.out.println("[Quartz] Job CIERRE programado: 23:59 PM (con política misfire FireAndProceed)");

            // =================================================================
            // JOB 3: Cierre de jornadas nocturnas - 12:00 PM
            // =================================================================
            JobDetail cierreNocturnoJob = JobBuilder.newJob(CierreJornadaNocturnaJob.class)
                    .withIdentity("cierreNocturnoJob", "asistencia")
                    .build();

            Trigger cierreNocturnoTrigger = TriggerBuilder.newTrigger()
                    .withIdentity("cierreNocturnoTrigger", "asistencia")
                    .withSchedule(CronScheduleBuilder.dailyAtHourAndMinute(12, 0)
                            .withMisfireHandlingInstructionFireAndProceed())
                    .build();

            scheduler.scheduleJob(cierreNocturnoJob, cierreNocturnoTrigger);
            System.out.println("[Quartz] Job CIERRE NOCTURNO programado: 12:00 PM (con política misfire FireAndProceed)");

            // =================================================================
            // Iniciar Scheduler
            // =================================================================
            scheduler.start();
            System.out.println("[Quartz] ===== Scheduler iniciado correctamente (5 hilos activos) =====");

        } catch (SchedulerException e) {
            System.err.println("[Quartz] ERROR CRÍTICO al iniciar Scheduler: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public synchronized void contextDestroyed(ServletContextEvent sce) {
        try {
            if (scheduler != null && !scheduler.isShutdown()) {
                System.out.println("[Quartz] Apagando Quartz Scheduler...");
                scheduler.shutdown(false); // false = apagado ordenado sin bloquear reinicios del servidor
                scheduler = null;
                System.out.println("[Quartz] Scheduler apagado correctamente.");
            }
        } catch (SchedulerException e) {
            System.err.println("[Quartz] Error al apagar Scheduler: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
