package com.sta.biometric.acciones;

import org.openxava.actions.ViewBaseAction;
import org.openxava.jpa.XPersistence;

import com.sta.biometric.modelo.AuditoriaRegistros;
import com.sta.biometric.modelo.MovimientoBancoHoras;
import com.sta.biometric.servicios.BancoHorasService;

/**
 * Acción que elimina la asignación al Banco de Horas de un registro de asistencia
 * desde el propio diálogo de asignación, restaurando la jornada a su estado inicial.
 * 
 * @author Sistema STARH
 * @since 2.0
 */
public class QuitarDelBancoAction extends ViewBaseAction {

    @Override
    public void execute() throws Exception {
        String registroId = (String) getContext().get(getRequest(), "banco_registro_id");
        if (registroId == null || registroId.isBlank()) {
            addError("No se pudo identificar el registro de asistencia en el contexto.");
            return;
        }

        AuditoriaRegistros registro = XPersistence.getManager().find(AuditoriaRegistros.class, registroId);
        if (registro == null) {
            addError("El registro de asistencia ya no existe.");
            return;
        }

        MovimientoBancoHoras mov = BancoHorasService.buscarMovimientoDeRegistro(registro);
        try {
            String nombreEmpleado = (registro.getEmpleado() != null) ? registro.getEmpleado().getNombreCompleto() : "";
            Object fechaJornada = registro.getFecha();

            if (mov != null) {
                BancoHorasService.revertirYEliminarMovimiento(mov, "Quitado desde diálogo de Auditoría");
                registro.setMinutosEnviadosAlBanco(0);
            } else {
                // Caso Trunco / Desincronizado: Reparar entidad directamente
                registro.setMinutosEnviadosAlBanco(0);
                if (registro.getNota() != null) {
                    String[] lineas = registro.getNota().split("\n");
                    StringBuilder sb = new StringBuilder();
                    for (String linea : lineas) {
                        if (!linea.contains("🏦 Banco de Horas") && !linea.contains("↩️")) {
                            if (sb.length() > 0) sb.append("\n");
                            sb.append(linea);
                        }
                    }
                    registro.setNota(sb.toString().trim());
                }
                XPersistence.getManager().merge(registro);
            }

            XPersistence.commit();

            addMessage("✅ Se eliminó la asignación al Banco de Horas para " 
                    + nombreEmpleado 
                    + " (" + fechaJornada + "). La jornada volvió a su estado inicial.");

            getContext().remove(getRequest(), "banco_registro_id");
            closeDialog();
            getView().refresh();

        } catch (Exception e) {
            addError("Error al quitar del banco de horas: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
