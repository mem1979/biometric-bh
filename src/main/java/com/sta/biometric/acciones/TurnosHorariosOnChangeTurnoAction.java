package com.sta.biometric.acciones;

import java.math.*;

import org.openxava.actions.*;

import com.sta.biometric.enums.*;

/**
 * Acción OnChange que se dispara al cambiar el tipo de turno (turnoNombre).
 * Controla la visibilidad de campos exclusivos del turno ESPECIAL:
 * - porcentajeBonificacion
 * - trabajaFeriadosPuente
 *
 * Cuando el turno NO es ESPECIAL, los campos se ocultan y sus valores se
 * resetean a los defaults (0 y false) para mantener consistencia de datos.
 */
public class TurnosHorariosOnChangeTurnoAction extends OnChangePropertyBaseAction {

    @Override
    public void execute() throws Exception {
        Object valor = getNewValue();
        boolean esEspecial = valor != null && Turnos.ESPECIAL.name().equals(valor.toString());

        // Mostrar/ocultar campos exclusivos de ESPECIAL
        getView().setHidden("porcentajeBonificacion", !esEspecial);
        getView().setHidden("trabajaFeriadosPuente", !esEspecial);

        // Si NO es especial, limpiar valores para consistencia
        if (!esEspecial) {
            getView().setValue("porcentajeBonificacion", BigDecimal.ZERO);
            getView().setValue("trabajaFeriadosPuente", false);
        }
    }
}
