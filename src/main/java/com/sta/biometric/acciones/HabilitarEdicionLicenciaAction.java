package com.sta.biometric.acciones;

import java.util.Set;

import org.openxava.actions.*;
import org.openxava.view.*;

/**
 * Acción que transforma el diálogo de licencia de modo CONSULTA a modo EDICIÓN.
 *
 * <p>
 * Al ejecutarse:
 * </p>
 * <ul>
 * <li>Habilita todos los campos editables (excepto los calculados y tipo)</li>
 * <li>Muestra una leyenda de responsabilidad al usuario</li>
 * <li>Reemplaza el botón "Editar" por "Guardar Modificaciones"</li>
 * </ul>
 *
 * @see EditarLicenciaCondicional
 * @see LicenciaSaveAction
 */
public class HabilitarEdicionLicenciaAction extends CollectionBaseAction {

    /**
     * Campos que NUNCA se habilitan para edición.
     * <ul>
     * <li>{@code dias} — calculado automáticamente según fechas y modo de
     * cómputo</li>
     * <li>{@code diasRestantes} — calculado dinámicamente desde BD</li>
     * <li>{@code tipo} — deshabilitado por ahora para evitar complejidad;
     * pendiente de habilitar tras pruebas funcionales</li>
     * </ul>
     */
    private static final Set<String> SIEMPRE_READONLY = Set.of("dias", "diasRestantes", "tipo", "periodoDevengado");

    @Override
    public void execute() throws Exception {
        View v = getCollectionElementView();

        // 1. Habilitar todos los campos excepto los calculados/restringidos
        for (Object o : v.getMembersNames().keySet()) {
            String prop = String.valueOf(o);
            v.setEditable(prop, !SIEMPRE_READONLY.contains(prop));
        }

        // 2. Mantener visibilidad de periodoDevengado según tipo
        com.sta.biometric.enums.TipoLicenciaAR tipo = (com.sta.biometric.enums.TipoLicenciaAR) v.getValue("tipo");
        boolean esVacaciones = (tipo == com.sta.biometric.enums.TipoLicenciaAR.VACACIONES);
        v.setHidden("periodoDevengado", !esVacaciones);

        // 3. Leyenda informativa de responsabilidad
        addWarning("licencia_edicion_responsabilidad");

        // 4. Transformar botones: quitar "Editar" e "Imprimir Constancia", poner
        // "Guardar Modificaciones"
        removeActions("Licencia.HabilitarEdicion");
        removeActions("Licencia.ImprimirConstancia");
        removeActions("Collection.remove");
        removeActions("Licencia.remove");
        addActions("Licencia.GuardarModificaciones");
    }
}
