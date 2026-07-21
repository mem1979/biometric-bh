package com.sta.biometric.acciones;

import org.openxava.actions.*;
import org.openxava.model.MapFacade;

/**
 * Accion para asignar el valor del Legajo (userId) al ID del Fichador (terminalUserId).
 * 
 * @author Sistema STARH
 */
public class GenerarTerminalUserIdAction extends ViewBaseAction {

    @Override
    public void execute() throws Exception {
        // 0. Verificar que la entidad exista (guardada previamente)
        if (getView().getKeyValues() == null || getView().getKeyValues().isEmpty()
                || getView().getKeyValues().get("id") == null) {
            addError("Primero debe guardar el empleado.");
            return;
        }

        // 1. Obtener el userId del empleado
        String userId = (String) getView().getValue("userId");
        if (userId == null || userId.trim().isEmpty()) {
            addError("El empleado no tiene un Legajo (userId) asignado.");
            return;
        }

        // 2. Asignar el valor a terminalUserId en la vista
        getView().setValue("terminalUserId", userId);

        // 3. Guardar la entidad de inmediato en la base de datos
        MapFacade.setValues(getModelName(), getView().getKeyValues(), getView().getValues());

        addMessage("ID de Terminal asignado con el valor del Legajo (userId): " + userId);
    }
}
