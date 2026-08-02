package com.sta.biometric.acciones;

import org.openxava.actions.ViewBaseAction;
import org.openxava.jpa.XPersistence;

import com.sta.biometric.enums.Signo;
import com.sta.biometric.modelo.AuditoriaRegistros;
import com.sta.biometric.modelo.MovimientoBancoHoras;
import com.sta.biometric.servicios.BancoHorasService;

/**
 * Acción que procesa y confirma la asignación de horas al Banco de Horas desde el diálogo modal.
 * 
 * @author Sistema STARH
 * @since 2.0
 */
public class ConfirmarEnvioBancoHorasAction extends ViewBaseAction {

    @Override
    public void execute() throws Exception {
        String registroId = (String) getContext().get(getRequest(), "banco_registro_id");
        if (registroId == null) {
            addError("No se encontró la referencia del registro de auditoría.");
            closeDialog();
            return;
        }

        AuditoriaRegistros registro = XPersistence.getManager().find(AuditoriaRegistros.class, registroId);
        if (registro == null) {
            addError("No se pudo cargar el registro de auditoría seleccionado.");
            closeDialog();
            return;
        }

        Signo signo = (Signo) getView().getValue("signo");
        String minutosStr = (String) getView().getValue("minutosAEnviar");
        String observacion = (String) getView().getValue("observacion");

        if (observacion == null || observacion.isBlank()) {
            addError("Debe ingresar obligatoriamente un motivo u observación para el envío al banco.");
            return;
        }

        int minutosParsed = parsearHHMM(minutosStr);
        if (minutosParsed <= 0) {
            addError("Debe ingresar una cantidad de horas/minutos válida (mayor a 00:00).");
            return;
        }

        boolean esConsumoBanco = (signo != null && signo.esNegativo());

        if (esConsumoBanco) {
            Boolean descontarPres = (Boolean) getView().getValue("descontarPresentismo");
            boolean descontarPresentismoVal = (descontarPres != null) ? descontarPres : false;
            registro.setDescontarPresentismo(descontarPresentismoVal);

            // Formatear nota auditada sin duplicar marcas previas de Presentismo:
            String marcaPresentismo = descontarPresentismoVal ? "Presentismo: COMPUTA." : "Presentismo: NO COMPUTA.";
            if (observacion.contains("Presentismo:")) {
                observacion = observacion.replaceAll("Presentismo:.*", marcaPresentismo);
            } else {
                observacion = observacion.trim() + "\n" + marcaPresentismo;
            }
        }

        int multiplicador = (signo != null && signo.esNegativo()) ? -1 : 1;
        int minutosConSigno = multiplicador * minutosParsed;

        try {
            String nombreEmpleado = (registro.getEmpleado() != null) ? registro.getEmpleado().getNombreCompleto() : "";

            // Si la jornada ya poseía un movimiento previo en el banco, eliminarlo primero para aplicar la modificación limpia
            MovimientoBancoHoras movExistente = BancoHorasService.buscarMovimientoDeRegistro(registro);
            if (movExistente != null) {
                BancoHorasService.revertirYEliminarMovimiento(movExistente, "Modificación desde Auditoría");
                registro.setMinutosEnviadosAlBanco(0);
            } else if (registro.getMinutosEnviadosAlBanco() != 0) {
                // Caso Trunco / Desincronizado: Resetear contador en la entidad para permitir re-envío limpio
                registro.setMinutosEnviadosAlBanco(0);
                XPersistence.getManager().merge(registro);
            }

            MovimientoBancoHoras movimiento = BancoHorasService.enviarAlBanco(registro, minutosConSigno, observacion);
            String tipoDesc = movimiento.getTipo().getDescripcion();
            String saldoNuevo = movimiento.getSaldoNuevoFormateado();

            XPersistence.commit();

            addMessage("✅ " + tipoDesc + " registrado/modificado exitosamente para "
                    + nombreEmpleado + ". Nuevo Saldo del Banco: "
                    + saldoNuevo);

            getContext().remove(getRequest(), "banco_registro_id");
            closeDialog();
            getView().refresh();

        } catch (IllegalArgumentException | IllegalStateException e) {
            addError(e.getMessage());
        } catch (Exception e) {
            addError("Error al procesar el envío al Banco de Horas: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private int parsearHHMM(String valor) {
        if (valor == null || valor.isBlank()) return 0;
        String limpio = valor.replace("_", "0").trim();
        String[] partes = limpio.split(":");
        try {
            int h = partes.length >= 1 ? Integer.parseInt(partes[0].trim()) : 0;
            int m = partes.length >= 2 ? Integer.parseInt(partes[1].trim()) : 0;
            return h * 60 + m;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
