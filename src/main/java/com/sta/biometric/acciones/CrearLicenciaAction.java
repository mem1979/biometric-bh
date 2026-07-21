package com.sta.biometric.acciones;

import java.util.*;

import org.openxava.actions.*;
import org.openxava.model.*;
import org.openxava.view.*;

import com.sta.biometric.enums.*;
import com.sta.biometric.modelo.*;
import com.sta.biometric.servicios.*;

/**
 * Acción para crear una nueva licencia dentro de la colección de un empleado.
 * 
 * FASE 2: Inicializa el combo dinámico de periodoDevengado según saldos
 * disponibles de vacaciones acumuladas de períodos anteriores.
 */
public class CrearLicenciaAction extends CreateNewElementInCollectionAction {

    @Override
    public void execute() throws Exception {
        super.execute();

        View view = getCollectionElementView();
        // En creación, permitimos todos los campos hasta que guarde
        setAllEditable(view, true);
        view.setEditable("diasRestantes", false);
        view.setEditable("horaInicio", false);
        view.setEditable("horaFin", false);
        removeActions("Licencia.ImprimirConstancia");

        // Asignar tipo de licencia vacaciones por defecto
        view.setValueNotifying("tipo", TipoLicenciaAR.VACACIONES);

        // --- FASE 2: Inicializar combo de periodoDevengado ---
        inicializarComboPeriodo(view);
    }

    /**
     * Inicializa el combo dinámico de periodoDevengado según saldos disponibles.
     * 
     * CASO 1 (sin acumulados): readonly, solo muestra período actual.
     * CASO 2 (con acumulados): editable, combo con todos los períodos con saldo.
     */
    private void inicializarComboPeriodo(View view) throws Exception {
        // Obtener empleado desde el view padre
        Map<?, ?> clave = view.getParent().getKeyValuesWithValue();
        Personal empleado = (Personal) MapFacade.findEntity(
                view.getParent().getModelName(), clave);

        if (empleado == null || empleado.getInicioActividades() == null) {
            view.setEditable("periodoDevengado", false);
            return;
        }

        // Período legal/referencia actual
        int periodoActual = VacacionesPeriodoService.getInstance().obtenerPeriodoActualReferencia(empleado);

        // Resolver modo de cómputo por defecto para vacaciones
        ModoComputoLicencia modoComputoDefault = ConfiguracionesPreferencias.obtenerValor(
                "licencia.VACACIONES.modoComputo",
                ModoComputoLicencia.DIAS_HABILES,
                ModoComputoLicencia.class);

        // Obtener períodos con saldo
        Map<Integer, Integer> periodosConSaldo = VacacionesPeriodoService
                .getInstance()
                .obtenerPeriodosDisponiblesConSaldo(empleado, null, modoComputoDefault);

        // Poblar combo con addValidValue
        view.clearValidValues("periodoDevengado");

        for (Map.Entry<Integer, Integer> entry : periodosConSaldo.entrySet()) {
            int periodo = entry.getKey();
            String label = String.valueOf(periodo);
            view.addValidValue("periodoDevengado", periodo, label);
        }

        // Si el período actual no está en el mapa (saldo 0), agregarlo igual como
        // opción
        if (!periodosConSaldo.containsKey(periodoActual)) {
            view.addValidValue("periodoDevengado", periodoActual, String.valueOf(periodoActual));
        }

        // Remover espacio en blanco del combo
        view.removeBlankValidValue("periodoDevengado");

        // Determinar si hay acumulados (más de 1 período con saldo)
        boolean tieneAcumulados = periodosConSaldo.size() > 1;

        // El valor por defecto es el período más antiguo con saldo disponible, o el
        // período actual si no hay otros con saldo
        int periodoPorDefecto = periodosConSaldo.isEmpty() ? periodoActual
                : periodosConSaldo.keySet().iterator().next();
        view.setValueNotifying("periodoDevengado", periodoPorDefecto);

        // CASO 1 vs CASO 2
        view.setEditable("periodoDevengado", tieneAcumulados);
    }

    @SuppressWarnings("unchecked")
    private void setAllEditable(View view, boolean editable) throws Exception {
        Set<Object> props = view.getMembersNames().keySet();
        for (Object propObj : props) {
            view.setEditable(propObj.toString(), editable);
        }
    }
}