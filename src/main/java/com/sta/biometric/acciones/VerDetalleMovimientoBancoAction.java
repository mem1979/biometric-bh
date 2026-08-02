package com.sta.biometric.acciones;

import java.util.*;
import org.openxava.actions.CollectionBaseAction;
import com.sta.biometric.modelo.AuditoriaRegistros;
import com.sta.biometric.modelo.MovimientoBancoHoras;

/**
 * Acción para ver el detalle de auditoría de registros de la jornada asociada
 * al movimiento del Banco de Horas desde la colección en Personal.
 * 
 * <p>Abre el diálogo modal de lectura de AuditoriaRegistros exactamente como en las demás vistas del sistema.</p>
 * 
 * @author Sistema STARH
 * @since 2.1
 */
public class VerDetalleMovimientoBancoAction extends CollectionBaseAction {

    @Override
    public void execute() throws Exception {
        List<?> selectedObjects = getSelectedObjects();

        if (selectedObjects == null || selectedObjects.isEmpty()) {
            addError("Debe seleccionar un movimiento del Banco de Horas.");
            return;
        }

        MovimientoBancoHoras mov = (MovimientoBancoHoras) selectedObjects.get(0);
        if (mov == null) {
            addError("No se pudo cargar el movimiento seleccionado.");
            return;
        }

        AuditoriaRegistros auditoria = mov.getAuditoriaRegistro();

        // Abrir diálogo modal
        showDialog();

        if (auditoria != null) {
            // Cargar y mostrar la vista completa de AuditoriaRegistros de solo lectura
            getView().setModelName("AuditoriaRegistros");

            Map<String, Object> key = new HashMap<>();
            key.put("id", auditoria.getId());
            getView().setValues(key);
            getView().findObject();
            getView().setEditable(false);

            getView().setTitle("Ver - 📋 AUDITORÍA DE REGISTROS");
        } else {
            // Fallback en caso de movimiento manual sin AuditoriaRegistros vinculada
            getView().setModelName("MovimientoBancoHoras");
            getView().setViewName("DetalleDialogo");

            Map<String, Object> key = new HashMap<>();
            key.put("id", mov.getId());
            getView().setValues(key);
            getView().findObject();
            getView().setEditable(false);

            String fechaStr = (mov.getFechaJornada() != null) ? mov.getFechaJornada().toString() : "";
            getView().setTitle("🏦 Movimiento del Banco de Horas (" + fechaStr + ")");
        }

        // Botón de cierre en el diálogo modal
        setControllers("Dialog");
    }
}
