package com.sta.biometric.acciones;

import java.util.List;

import org.openxava.actions.CollectionBaseAction;
import org.openxava.jpa.XPersistence;

import com.sta.biometric.modelo.MovimientoBancoHoras;
import com.sta.biometric.servicios.BancoHorasService;

/**
 * Acción de eliminación directa de un movimiento del Banco de Horas desde la lista
 * de movimientos en el legajo del empleado.
 * 
 * Revierte el saldo y elimina el movimiento físicamente sin dejar rastros previa confirmación.
 * 
 * @author Sistema STARH
 * @since 2.0
 */
public class EliminarMovimientoBancoAction extends CollectionBaseAction {

    @Override
    public void execute() throws Exception {
        List<?> selectedObjects = getSelectedObjects();

        if (selectedObjects == null || selectedObjects.isEmpty()) {
            addError("Debe seleccionar un movimiento del Banco de Horas a eliminar.");
            return;
        }

        MovimientoBancoHoras mov = (MovimientoBancoHoras) selectedObjects.get(0);
        if (mov == null) {
            addError("No se pudo obtener el movimiento seleccionado.");
            return;
        }

        try {
            Object fechaJornada = mov.getFechaJornada();
            String cantidadStr = mov.getMinutosFormateados();

            BancoHorasService.revertirYEliminarMovimiento(mov, "Eliminado desde legajo");
            XPersistence.commit();

            addMessage("✅ Se eliminó el movimiento de " + cantidadStr + " (" + fechaJornada + "). La jornada volvió a su estado inicial y el saldo fue actualizado.");
            getView().refresh();
        } catch (Exception e) {
            addError("Error al eliminar el movimiento del Banco de Horas: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
