package com.sta.biometric.acciones;

import java.util.List;

import org.openxava.actions.CollectionBaseAction;

import com.sta.biometric.modelo.MovimientoBancoHoras;

/**
 * Acción que abre el diálogo de reversión para un movimiento del Banco de Horas.
 * 
 * @author Sistema STARH
 * @since 2.0
 */
public class RevertirMovimientoBancoAction extends CollectionBaseAction {

    @Override
    public void execute() throws Exception {
        List<?> selectedObjects = getSelectedObjects();

        if (selectedObjects == null || selectedObjects.isEmpty()) {
            addError("Debe seleccionar un movimiento del Banco de Horas a revertir.");
            return;
        }

        MovimientoBancoHoras mov = (MovimientoBancoHoras) selectedObjects.get(0);
        if (mov == null) {
            addError("No se pudo obtener el movimiento seleccionado.");
            return;
        }

        // Guardar ID del movimiento en contexto
        getContext().put(getRequest(), "reversion_movimiento_id", mov.getId());

        // Mostrar diálogo modal
        showDialog();
        getView().setTitle("🗑️ Eliminar / Revertir Movimiento del Banco de Horas");
        getView().setModelName("DialogoReversionBanco");

        String info = String.format("%s de %s | Fecha Jornada: %s | Saldo Prev: %s → %s",
                mov.getTipo().getDescripcion(),
                mov.getMinutosFormateados(),
                mov.getFechaJornada() != null ? mov.getFechaJornada().toString() : "N/D",
                mov.getSaldoAnteriorFormateado(),
                mov.getSaldoNuevoFormateado());

        getView().setValue("infoMovimiento", info);
        getView().setValue("motivo", "");

        setControllers("DialogoReversionBanco");
    }
}
