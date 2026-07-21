package com.sta.biometric.auxiliares;

import org.openxava.util.Messages;
import org.openxava.validators.IRemoveValidator;
import java.time.LocalDate;

public class LicenciaRemoveValidator implements IRemoveValidator {

    private Licencia licencia;

    @Override
    public void setEntity(Object entity) throws Exception {
        this.licencia = (Licencia) entity;
    }

    @Override
    public void validate(Messages errors) throws Exception {
        if (LicenciaBypassThreadLocal.isBypass()) {
            return; // Permitir eliminación si se confirmó explícitamente
        }
        if (licencia != null && licencia.getFechaFin() != null) {
            if (licencia.getFechaFin().isBefore(LocalDate.now())) {
                errors.add("No_puede_eliminar_una_licencia_que_ya_ha_finalizado");
            }
        }
    }
}
