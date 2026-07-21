package com.sta.biometric.acciones;


import java.time.*;
import java.util.*;

import org.openxava.actions.*;

import org.openxava.model.*;

import com.sta.biometric.auxiliares.*;
import com.sta.biometric.enums.*;
import com.sta.biometric.modelo.*;
import com.sta.biometric.servicios.*;

/**
 * Acción que completa automáticamente los valores de la licencia según su tipo,
 * antigüedad del empleado, valores por defecto y normativa vigente (LCT).
 *
 * FASE 2: Gestiona el combo dinámico de periodoDevengado para vacaciones
 * acumuladas multi-período. Detecta cambios manuales del combo y recalcula
 * saldo sin sobreescribir la selección del usuario.
 */
public class CompletarObservacionLicenciaAction extends OnChangePropertyBaseAction {

    @Override
    public void execute() throws Exception {

        TipoLicenciaAR tipo = (TipoLicenciaAR) getView().getValue("tipo");
        if (tipo == null)
            tipo = TipoLicenciaAR.VACACIONES;

        boolean esVacaciones = (tipo == TipoLicenciaAR.VACACIONES);
        getView().setHidden("periodoDevengado", !esVacaciones);

        Map<?, ?> clave = getView().getParent().getKeyValuesWithValue();
        Personal empleado = (Personal) MapFacade.findEntity(getView().getParent().getModelName(), clave);

        String keyBase = "licencia." + tipo.name();

        // Obtén el default desde el properties SIEMPRE
        ModoComputoLicencia modoComputoDefault = ConfiguracionesPreferencias.obtenerValor(
                keyBase + ".modoComputo",
                ModoComputoLicencia.DIAS_HABILES,
                ModoComputoLicencia.class);

        // Decide el valor a setear según qué campo cambió
        ModoComputoLicencia modoComputoASetear;
        String changed = getChangedProperty(); // "tipo", "modoComputo", "fechaInicio", "fechaFin" o "periodoDevengado"

        // FASE 2: Si el usuario cambió periodoDevengado manualmente, no re-calcularlo
        boolean periodoManual = "periodoDevengado".equals(changed);

        if ("tipo".equals(changed)) {
            // Cambió el tipo: forzar el default del nuevo tipo
            modoComputoASetear = modoComputoDefault;
        } else {
            // Cambió el propio modoComputo u otro campo: respetar la vista si tiene algo
            modoComputoASetear = (ModoComputoLicencia) Optional
                     .ofNullable(getView().getValue("modoComputo"))
                     .orElse(modoComputoDefault);
        }

        boolean justificado = ConfiguracionesPreferencias.obtenerValor(keyBase + ".justificado", true, Boolean.class);
        boolean conGoce = ConfiguracionesPreferencias.obtenerValor(keyBase + ".conGoce", true, Boolean.class);

        if (!(Boolean) justificado) {
            addError("licencia_no_justificada");
        }

        String descripcionGenerica = ConfiguracionesPreferencias.obtenerValor(keyBase + ".descripcion", "", String.class);
        String descripcionEspecifica = ConfiguracionesPreferencias.obtenerValor(
                keyBase + ".descripcion." + modoComputoASetear.name(),
                descripcionGenerica,
                String.class);

        String observacion = descripcionEspecifica;

        // 1. Obtener fecha de inicio para calcular período devengado
        LocalDate inicio = (LocalDate) getView().getValue("fechaInicio");
        if (inicio == null) inicio = LocalDate.now();

        int periodoDevengado;
        if (periodoManual) {
            // FASE 2: El usuario eligió manualmente desde el combo — respetar su selección
            Integer valorCombo = (Integer) getView().getValue("periodoDevengado");
            periodoDevengado = (valorCombo != null) ? valorCombo
                    : VacacionesPeriodoService.getInstance().calcularPeriodoDevengado(empleado, tipo, inicio);
        } else {
            // Calculado automáticamente (tipo o fecha cambiaron)
            periodoDevengado = VacacionesPeriodoService.getInstance().calcularPeriodoDevengado(empleado, tipo, inicio);
            getView().setValue("periodoDevengado", periodoDevengado);
        }

        int diasPorAnio = 0;
        
        // ---------------------- LÓGICA ESPECÍFICA POR TIPO DE LICENCIA ------------------------
        if (empleado != null && empleado.getInicioActividades() != null) {
            diasPorAnio = VacacionesPeriodoService.getInstance().calcularDiasMaximosPorTipo(empleado, tipo, periodoDevengado, inicio);

            // VACACIONES – ajuste de días por conversión a hábiles y observaciones
            if (tipo == TipoLicenciaAR.VACACIONES) {
                if (modoComputoASetear == ModoComputoLicencia.DIAS_CORRIDOS_HABILES) {
                    diasPorAnio = (diasPorAnio * 5) / 7;
                    observacion = "Imputado al período LCT " + periodoDevengado + " (según antigüedad legal convertida a días hábiles)";
                } else {
                    observacion = "Imputado al período LCT " + periodoDevengado + " (según antigüedad legal)";
                }
            }

            // ENFERMEDAD – observaciones específicas
            else if (tipo == TipoLicenciaAR.ENFERMEDAD) {
                observacion += " (límite legal según antigüedad)";
            }
        }

        // 2. Calcular saldo de días tomados (excluyendo la licencia actual si ya existe)
        String licenciaId = (String) getView().getValue("id");
        int diasTomados = VacacionesPeriodoService.getInstance().obtenerDiasTomados(empleado, tipo, periodoDevengado, licenciaId);
        int diasRestantes = Math.max(0, diasPorAnio - diasTomados);

        getView().setValue("modoComputo", modoComputoASetear);
        getView().setValue("justificado", justificado);
        getView().setValue("conGoce", conGoce);
        getView().setValue("observacion", observacion);
        getView().setValue("diasRestantes", diasRestantes);

        // --- FASE 2: Actualizar combo dinámico de periodoDevengado ---
        if (esVacaciones && empleado != null && empleado.getInicioActividades() != null) {
            actualizarComboPeriodo(empleado, licenciaId, periodoDevengado, modoComputoASetear);
        }

        // 3. Configurar esParcial segun tipo de licencia
        boolean esParcial = false;
        if (tipo == TipoLicenciaAR.VACACIONES) {
            // Deshabilitar esParcial en la UI si es vacación
            getView().setValue("esParcial", false);
            getView().setValue("horaInicio", null);
            getView().setValue("horaFin", null);
            getView().setEditable("horaInicio", false);
            getView().setEditable("horaFin", false);
        } else {
            esParcial = ConfiguracionesPreferencias.obtenerValor(keyBase + ".esParcial", false, Boolean.class);
            getView().setValue("esParcial", esParcial);
            getView().setEditable("horaInicio", esParcial);
            getView().setEditable("horaFin", esParcial);
            if (!esParcial) {
                getView().setValue("horaInicio", null);
                getView().setValue("horaFin", null);
            }
        }

        // 4. Cálculo automático de días solicitados entre fechas
        LocalDate fin = (LocalDate) getView().getValue("fechaFin");

        if (inicio != null && fin != null && empleado != null) {
            int total = 0;
            LocalDate actual = inicio;

            while (!actual.isAfter(fin)) {
                boolean esFeriado = Feriados.existeParaFecha(actual);
                TurnosHorarios turno = empleado.getTurnoParaFecha(actual);
                boolean esLaboral = turno != null && turno.esLaboral(actual.getDayOfWeek());

                switch (modoComputoASetear) {
                    case DIAS_CORRIDOS:
                        total++;
                        break;
                    case DIAS_HABILES:
                    case DIAS_CORRIDOS_HABILES:
                        if (!esFeriado && actual.getDayOfWeek().getValue() < 6)
                            total++;
                        break;
                    case DIAS_LABORALES:
                        if (!esFeriado && esLaboral)
                            total++;
                        break;
                }

                actual = actual.plusDays(1);
            }

            getView().setValueNotifying("dias", total);
            getView().setValue("diasRestantes", diasRestantes);
        }
    }

    /**
     * FASE 2: Actualiza el combo dinámico de periodoDevengado con los períodos
     * que tienen saldo disponible y controla su editabilidad.
     */
    private void actualizarComboPeriodo(Personal empleado, String licenciaId, int periodoActual, ModoComputoLicencia modoComputo) {
        Map<Integer, Integer> periodosConSaldo = VacacionesPeriodoService
                .getInstance()
                .obtenerPeriodosDisponiblesConSaldo(empleado, licenciaId, modoComputo);

        getView().clearValidValues("periodoDevengado");

        for (Map.Entry<Integer, Integer> entry : periodosConSaldo.entrySet()) {
            int p = entry.getKey();
            String label = String.valueOf(p);
            getView().addValidValue("periodoDevengado", p, label);
        }

        // Si el período seleccionado no está en el mapa (saldo 0), agregarlo igual
        if (!periodosConSaldo.containsKey(periodoActual)) {
            getView().addValidValue("periodoDevengado", periodoActual, String.valueOf(periodoActual));
        }

        // Remover espacio en blanco del combo
        getView().removeBlankValidValue("periodoDevengado");

        // Habilitar combo solo si hay más de 1 período con saldo
        boolean tieneAcumulados = periodosConSaldo.size() > 1;
        getView().setEditable("periodoDevengado", tieneAcumulados);
    }
}
