package com.sta.biometric.acciones;

import java.util.Map;

import org.openxava.actions.TabBaseAction;
import org.openxava.model.MapFacade;

import com.sta.biometric.enums.EvaluacionJornada;
import com.sta.biometric.enums.Signo;
import com.sta.biometric.modelo.AuditoriaRegistros;
import com.sta.biometric.modelo.BancoHoras;
import com.sta.biometric.servicios.BancoHorasService;

/**
 * Acción que abre el diálogo de asignación de horas al Banco de Horas desde
 * AuditoriaRegistros.
 * 
 * @author Sistema STARH
 * @since 2.0
 */
public class EnviarABancoHorasAction extends TabBaseAction {

    private int row = -1;

    public int getRow() {
        return row;
    }

    public void setRow(int row) {
        this.row = row;
    }

    @Override
    public void execute() throws Exception {
        AuditoriaRegistros registro = obtenerRegistroActual();

        if (registro == null) {
            addError("Debe seleccionar un registro de Auditoría de asistencia.");
            return;
        }

        if (registro.getEmpleado() == null) {
            addError("El registro seleccionado no tiene un empleado asignado.");
            return;
        }

        int yaEnviados = registro.getMinutosEnviadosAlBanco();
        int difTotal;
        if (registro.aplicaExcepcionBancoFeriado()) {
            difTotal = registro.getMinutosTrabajados();
        } else if (registro.getMinutosExtras() > 0) {
            difTotal = registro.getMinutosExtras();
        } else {
            difTotal = registro.getMinutosTrabajados() - registro.getMinutosEsperados();
        }

        if (registro.getEvaluacion() == EvaluacionJornada.AUSENTE && difTotal == 0 && !registro.aplicaExcepcionBancoFeriado()) {
            int minDeuda = registro.getMinutosEsperados() > 0 ? registro.getMinutosEsperados() : 480;
            difTotal = -minDeuda;
        }

        int disponible = (yaEnviados != 0) ? difTotal : BancoHorasService.calcularDiferenciaDisponible(registro);

        if (yaEnviados == 0 && difTotal == 0) {
            addError("El registro del " + registro.getFecha()
                    + " no presenta horas extras ni ausencias/faltantes para enviar al banco.");
            return;
        }

        BancoHoras banco = BancoHorasService.obtenerOCrearBanco(registro.getEmpleado());

        // Guardar ID del registro en el contexto para las acciones del diálogo
        getContext().put(getRequest(), "banco_registro_id", registro.getId());

        // Mostrar diálogo modal
        showDialog();
        String tituloDialogo = (yaEnviados != 0)
                ? "🏦 Modificar / Quitar del Banco: " + registro.getEmpleado().getNombreCompleto() + " ("
                        + registro.getFecha() + ")"
                : "🏦 Banco de Horas: " + registro.getEmpleado().getNombreCompleto() + " (" + registro.getFecha() + ")";

        getView().setTitle(tituloDialogo);
        getView().setModelName("DialogoBancoHoras");

        // Datos informativos ReadOnly
        getView().setValue("infoRegistro", registro.getEmpleado().getNombreCompleto() + " - " + registro.getFecha());

        String evalText = (registro.getEvaluacion() != null) ? registro.getEvaluacion().toString() : "SIN EVALUACIÓN";
        getView().setValue("evaluacionJornada", evalText);

        getView().setValue("diferenciaTotal", formatearMinutos(difTotal));
        getView().setValue("yaEnviado", formatearMinutos(yaEnviados));
        getView().setValue("disponible", formatearMinutos(disponible));

        // Signo y cantidad sugerida por defecto
        int valorSugerido = (yaEnviados != 0) ? Math.abs(yaEnviados)
                : Math.abs(disponible != 0 ? disponible : difTotal);
        boolean esPositivo = (yaEnviados != 0) ? (yaEnviados > 0) : (disponible >= 0);

        getView().setValue("signo", esPositivo ? Signo.MAS : Signo.MENOS);

        int h = valorSugerido / 60;
        int m = valorSugerido % 60;
        getView().setValue("minutosAEnviar", String.format("%02d:%02d", h, m));

        getView().setValue("saldoActual", banco.getSaldoBancoHorasDisplay());
        getView().setValue("observacion", "");

        // Asignar controlador de botones del diálogo
        setControllers("DialogoBancoHoras");
    }

    private AuditoriaRegistros obtenerRegistroActual() {
        // 1. Intentar desde getSelectedKeys() (funciona en Lista para RowAction y
        // ListAction)
        try {
            Map<String, Object>[] keys = getSelectedKeys();
            if (keys != null && keys.length > 0 && keys[0] != null) {
                Object entity = MapFacade.findEntity(getModelName(), keys[0]);
                if (entity instanceof AuditoriaRegistros) {
                    return (AuditoriaRegistros) entity;
                }
            }
        } catch (Exception ignored) {
        }

        // 3. Intentar desde Vista de Detalle
        try {
            Object entity = getView().getEntity();
            if (entity instanceof AuditoriaRegistros) {
                return (AuditoriaRegistros) entity;
            }
        } catch (Exception ignored) {
        }

        // 4. Fallback por Clave de Registro en Vista de Detalle
        try {
            Map<String, Object> key = getView().getKeyValues();
            if (key != null && !key.isEmpty() && key.containsKey("id") && key.get("id") != null) {
                Object entity = MapFacade.findEntity("AuditoriaRegistros", key);
                if (entity instanceof AuditoriaRegistros) {
                    return (AuditoriaRegistros) entity;
                }
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private String formatearMinutos(int totalMinutos) {
        String s = totalMinutos < 0 ? "-" : (totalMinutos > 0 ? "+" : "");
        int abs = Math.abs(totalMinutos);
        int h = abs / 60;
        int m = abs % 60;
        return String.format("%s%02d:%02d hs", s, h, m);
    }
}
