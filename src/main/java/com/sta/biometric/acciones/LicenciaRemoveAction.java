package com.sta.biometric.acciones;

import org.openxava.actions.*;
import org.openxava.view.*;
import com.sta.biometric.auxiliares.LicenciaBypassThreadLocal;
import java.time.LocalDate;

/**
 * Acción personalizada para eliminar una licencia de una colección.
 * 
 * <p>Comportamiento:</p>
 * <ul>
 *   <li>Si la licencia es futura o actual, se elimina de inmediato (previo al confirm de XML).</li>
 *   <li>Si la licencia ya finalizó, en el primer clic se detiene y muestra una advertencia
 *       i18n. Al hacer clic una segunda vez, realiza la eliminación activando el bypass.</li>
 * </ul>
 */
public class LicenciaRemoveAction extends RemoveElementFromCollectionAction {

    @Override
    public void execute() throws Exception {
        View v = getCollectionElementView();
        
        // 1. Obtener la fecha de fin de la licencia
        LocalDate fechaFin = (LocalDate) v.getValue("fechaFin");
        boolean esPasada = (fechaFin != null && fechaFin.isBefore(LocalDate.now()));

        if (esPasada) {
            String idLicencia = String.valueOf(v.getValue("id"));
            String keyConfirmacion = "confirmar_borrado_licencia_" + idLicencia;

            // 2. Verificar si ya se mostró la advertencia en esta sesión
            if (getRequest().getSession().getAttribute(keyConfirmacion) != null) {
                // Confirmado: proceder al borrado definitivo con bypass
                getRequest().getSession().removeAttribute(keyConfirmacion);
                try {
                    LicenciaBypassThreadLocal.setBypass(true);
                    super.execute();
                } finally {
                    LicenciaBypassThreadLocal.clear();
                }
            } else {
                // Registrar confirmación pendiente y mostrar advertencia
                getRequest().getSession().setAttribute(keyConfirmacion, Boolean.TRUE);
                addWarning("advertencia_borrar_licencia_finalizada");
            }
        } else {
            // Licencia futura o actual: borrado directo estándar
            super.execute();
        }
    }
}
