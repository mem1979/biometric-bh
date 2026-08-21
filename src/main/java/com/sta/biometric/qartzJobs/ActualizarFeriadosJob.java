package com.sta.biometric.qartzJobs;

import org.apache.commons.logging.*;
import org.openxava.jpa.*;
import org.quartz.*;

import com.sta.biometric.servicios.*;

/**
 * Tarea que actualiza los feriados automáticamente el 1 de enero según el patrón oficial de OpenXava.
 */
@DisallowConcurrentExecution
public class ActualizarFeriadosJob implements Job {

    private static final Log log = LogFactory.getLog(ActualizarFeriadosJob.class);

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            ImportadorFeriadosService.importarFeriadosDelAnioActual();
            XPersistence.commit();
            log.info("[ActualizarFeriadosJob] Feriados actualizados automáticamente desde ArgentinaDatos.com");
        } catch (Exception e) {
            XPersistence.rollback();
            log.error("[ActualizarFeriadosJob] Error al actualizar feriados", e);
        } finally {
            XPersistence.commit(); // Cierre y liberación del ThreadLocal
        }
    }
}
