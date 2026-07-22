package com.sta.biometric.acciones;

import org.openxava.actions.ViewBaseAction;
import org.openxava.jpa.XPersistence;

import com.sta.biometric.modelo.MovimientoBancoHoras;
import com.sta.biometric.servicios.BancoHorasService;

/**
 * Acción que procesa y confirma la reversión inmutable de un movimiento del Banco de Horas.
 * 
 * @author Sistema STARH
 * @since 2.0
 */
public class ConfirmarReversionBancoAction extends ViewBaseAction {

    @Override
    public void execute() throws Exception {
        String movId = (String) getContext().get(getRequest(), "reversion_movimiento_id");
        if (movId == null) {
            addError("No se encontró la referencia del movimiento a revertir.");
            closeDialog();
            return;
        }

        MovimientoBancoHoras mov = XPersistence.getManager().find(MovimientoBancoHoras.class, movId);
        if (mov == null) {
            addError("No se pudo cargar el movimiento seleccionado desde la base de datos.");
            closeDialog();
            return;
        }

        String motivo = (String) getView().getValue("motivo");
        if (motivo == null || motivo.isBlank()) {
            addError("Debe ingresar obligatoriamente un motivo para la reversión.");
            return;
        }

        try {
            BancoHorasService.revertirYEliminarMovimiento(mov, motivo);
            XPersistence.commit();

            addMessage("✅ Movimiento eliminado del Banco de Horas. Las horas originales fueron restauradas a su estado inicial.");

            getContext().remove(getRequest(), "reversion_movimiento_id");
            closeDialog();
            getView().refresh();

        } catch (IllegalStateException | IllegalArgumentException e) {
            addError(e.getMessage());
        } catch (Exception e) {
            addError("Error al ejecutar la reversión: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
