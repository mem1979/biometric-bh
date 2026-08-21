package com.sta.biometric.qartzJobs;

import javax.servlet.*;
import javax.servlet.annotation.*;

import org.apache.commons.logging.*;
import org.quartz.*;
import org.quartz.impl.*;

/**
 * Inicializador de Quartz que se ejecuta automáticamente al arrancar Tomcat o
 * el contenedor de Servlets, siguiendo la documentación oficial de OpenXava.
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

    private static final Log log = LogFactory.getLog(ApplicationQuartzInitializer.class);

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        try {
            Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();

            // 1. JOB APERTURA (00:01 AM)
            JobKey aperturaKey = new JobKey("aperturaJob", "asistencia");
            if (!scheduler.checkExists(aperturaKey)) {
                JobDetail aperturaJob = JobBuilder.newJob(AperturaJornadaJob.class)
                        .withIdentity(aperturaKey)
                        .build();

                Trigger aperturaTrigger = TriggerBuilder.newTrigger()
                        .withIdentity("aperturaTrigger", "asistencia")
                        .withSchedule(CronScheduleBuilder.dailyAtHourAndMinute(0, 1))
                        .build();

                scheduler.scheduleJob(aperturaJob, aperturaTrigger);
                log.info("[Quartz] Job APERTURA programado a las 00:01 AM.");
            }

            // 2. JOB CIERRE DIARIO (23:59 PM)
            JobKey cierreKey = new JobKey("cierreJob", "asistencia");
            if (!scheduler.checkExists(cierreKey)) {
                JobDetail cierreJob = JobBuilder.newJob(CierreJornadaJob.class)
                        .withIdentity(cierreKey)
                        .build();

                Trigger cierreTrigger = TriggerBuilder.newTrigger()
                        .withIdentity("cierreTrigger", "asistencia")
                        .withSchedule(CronScheduleBuilder.dailyAtHourAndMinute(23, 59))
                        .build();

                scheduler.scheduleJob(cierreJob, cierreTrigger);
                log.info("[Quartz] Job CIERRE programado a las 23:59 PM.");
            }

            // 3. JOB CIERRE NOCTURNO (12:00 PM)
            JobKey cierreNocturnoKey = new JobKey("cierreNocturnoJob", "asistencia");
            if (!scheduler.checkExists(cierreNocturnoKey)) {
                JobDetail cierreNocturnoJob = JobBuilder.newJob(CierreJornadaNocturnaJob.class)
                        .withIdentity(cierreNocturnoKey)
                        .build();

                Trigger cierreNocturnoTrigger = TriggerBuilder.newTrigger()
                        .withIdentity("cierreNocturnoTrigger", "asistencia")
                        .withSchedule(CronScheduleBuilder.dailyAtHourAndMinute(12, 0))
                        .build();

                scheduler.scheduleJob(cierreNocturnoJob, cierreNocturnoTrigger);
                log.info("[Quartz] Job CIERRE NOCTURNO programado a las 12:00 PM.");
            }

            // 4. JOB ACTUALIZAR FERIADOS (1 de enero, 00:30 AM)
            JobKey feriadosKey = new JobKey("actualizarFeriadosJob", "mantenimiento");
            if (!scheduler.checkExists(feriadosKey)) {
                JobDetail feriadosJob = JobBuilder.newJob(ActualizarFeriadosJob.class)
                        .withIdentity(feriadosKey)
                        .build();

                Trigger feriadosTrigger = TriggerBuilder.newTrigger()
                        .withIdentity("feriadosTrigger", "mantenimiento")
                        .withSchedule(CronScheduleBuilder.cronSchedule("0 30 0 1 1 ? *"))
                        .build();

                scheduler.scheduleJob(feriadosJob, feriadosTrigger);
                log.info("[Quartz] Job ACTUALIZAR FERIADOS programado para el 1 de enero a las 00:30 AM.");
            }

            // Iniciar scheduler si no ha sido iniciado
            if (!scheduler.isStarted()) {
                scheduler.start();
                log.info("[Quartz] ===== Scheduler iniciado correctamente =====");
            }

        } catch (Exception ex) {
            log.error("[Quartz] Error al iniciar Quartz Scheduler", ex);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        try {
            Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();
            if (scheduler != null && !scheduler.isShutdown()) {
                log.info("[Quartz] Apagando Quartz Scheduler...");
                scheduler.shutdown();
                log.info("[Quartz] Scheduler apagado correctamente.");
            }
        } catch (Exception ex) {
            log.error("[Quartz] Error al apagar Quartz Scheduler", ex);
        }
    }
}
