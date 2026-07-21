package com.sta.biometric.acciones;

import org.openxava.actions.*;
import org.openxava.view.*;

/**
 * Acción que abre el diálogo de edición de una licencia en modo 100% CONSULTA.
 *
 * <p>
 * Comportamiento:
 * </p>
 * <ul>
 * <li>Todos los campos quedan deshabilitados (sin excepción)</li>
 * <li>No se presenta botón "Guardar" — el usuario no puede persistir
 * cambios</li>
 * <li>Se presenta el botón "Editar" ({@code Licencia.HabilitarEdicion})
 * para pasar al modo de edición completa</li>
 * <li>Se presenta el botón "Imprimir Constancia"</li>
 * </ul>
 *
 * @see HabilitarEdicionLicenciaAction
 */
public class EditarLicenciaCondicional extends EditElementInCollectionAction {

    @Override
    public void execute() throws Exception {
        super.execute(); // abre el diálogo

        View v = getCollectionElementView();

        // === MODO 100% CONSULTA ===
        // Deshabilitar TODOS los miembros sin excepción
        for (Object o : v.getMembersNames().keySet()) {
            String prop = String.valueOf(o);
            v.setEditable(prop, false);
        }

        // Ocultar/mostrar periodoDevengado según corresponda
        com.sta.biometric.enums.TipoLicenciaAR tipo = (com.sta.biometric.enums.TipoLicenciaAR) v.getValue("tipo");
        boolean esVacaciones = (tipo == com.sta.biometric.enums.TipoLicenciaAR.VACACIONES);
        v.setHidden("periodoDevengado", !esVacaciones);

        // === BOTONES DEL DIÁLOGO ===
        // Remover toda capacidad de guardado y remoción estándar
        removeActions("Licencia.Guardar");
        removeActions("Collection.save");
        removeActions("Collection.remove");
        removeActions("Licencia.remove");

        // Agregar botones de consulta (se mantiene Dialog.cancel)
        addActions("Licencia.HabilitarEdicion");
        addActions("Licencia.ImprimirConstancia");
        addActions("Licencia.remove");

    }
}