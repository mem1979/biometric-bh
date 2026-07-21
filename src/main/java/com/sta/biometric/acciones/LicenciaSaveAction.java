package com.sta.biometric.acciones;

import org.openxava.actions.*;
import org.openxava.jpa.*;
import org.openxava.util.*;

import com.sta.biometric.modelo.Personal;
import com.sta.biometric.enums.*;
import com.sta.biometric.auxiliares.Licencia;
import com.sta.biometric.servicios.VacacionesPeriodoService;

/**
 * Acción de guardado de una licencia dentro de una colección.
 *
 * Objetivos:
 * 1) Validar días solicitados vs. restantes SOLO al CREAR (clave editable).
 * 2) Evitar revalidar al ACTUALIZAR (clave NO editable): guardar directo.
 * 3) Aplicar doble bloqueo pesimista para proteger concurrencia (solo en creación).
 * 4) Recalcular diasRestantes dinámicamente desde BD antes de guardar.
 * 5) Unificar mensajes con i18n (error, warning, éxito).
 * 6) Comportamiento híbrido:
 *    - VACACIONES y ENFERMEDAD: bloqueo estricto (no permite exceder cupo legal).
 *    - Otros tipos: flexible (supervisor puede autorizar exceso en 3 pasos).
 */
public class LicenciaSaveAction extends SaveElementInCollectionAction {

    @Override
    public void execute() throws Exception {
        // === Determinar si es creación o actualización ===
        boolean creando = getCollectionElementView().isKeyEditable();

        if (!creando) {
            // === Modo ACTUALIZACIÓN ===
            // No revalidar: guardar tal cual y retornar.
            super.execute();
            return;
        }

        // === Modo CREACIÓN ===

        // 1. Obtener el ID del empleado desde el view padre
        String empleadoId = getCollectionElementView().getParent()
                .getKeyValuesWithValue().get("id").toString();

        javax.persistence.EntityManager em = XPersistence.getManager();

        // 2. Bloqueo de escritura pesimista sobre el Personal
        Personal empleado = em.find(Personal.class, empleadoId,
                javax.persistence.LockModeType.PESSIMISTIC_WRITE);

        TipoLicenciaAR tipo = (TipoLicenciaAR) getCollectionElementView().getValue("tipo");
        Integer periodoDevengado = (Integer) getCollectionElementView().getValue("periodoDevengado");
        String licenciaId = (String) getCollectionElementView().getValue("id");

        if (periodoDevengado == null || periodoDevengado == 0) {
            java.time.LocalDate fechaInicio = (java.time.LocalDate) getCollectionElementView().getValue("fechaInicio");
            periodoDevengado = VacacionesPeriodoService.getInstance()
                    .calcularPeriodoDevengado(empleado, tipo, fechaInicio != null ? fechaInicio : java.time.LocalDate.now());
        }

        // 3. Bloqueo pesimista sobre las licencias del mismo período
        em.createQuery(
                "SELECT l FROM Licencia l WHERE l.empleado = :emp " +
                "AND l.tipo = :tipo AND l.periodoDevengado = :periodo",
                Licencia.class)
          .setParameter("emp", empleado)
          .setParameter("tipo", tipo)
          .setParameter("periodo", periodoDevengado)
          .setLockMode(javax.persistence.LockModeType.PESSIMISTIC_WRITE)
          .getResultList();

        // 4. Recalcular diasRestantes dinámicamente desde BD solo si no es editable
        boolean editable = getCollectionElementView().isEditable("diasRestantes");
        if (!editable) {
            java.time.LocalDate fechaInicio = (java.time.LocalDate)
                    getCollectionElementView().getValue("fechaInicio");
            int diasPorAnio = VacacionesPeriodoService.getInstance()
                    .calcularDiasMaximosPorTipo(empleado, tipo, periodoDevengado, fechaInicio);
            ModoComputoLicencia modoComputoLic = (ModoComputoLicencia)
                    getCollectionElementView().getValue("modoComputo");
            if (tipo == TipoLicenciaAR.VACACIONES
                    && modoComputoLic == ModoComputoLicencia.DIAS_CORRIDOS_HABILES) {
                diasPorAnio = (diasPorAnio * 5) / 7;
            }
            int diasTomados = VacacionesPeriodoService.getInstance()
                    .obtenerDiasTomados(empleado, tipo, periodoDevengado, licenciaId);
            int diasRestantesCalculado = Math.max(0, diasPorAnio - diasTomados);

            // Actualizar el saldo recalculado en la vista
            getCollectionElementView().setValue("diasRestantes", diasRestantesCalculado);
        }

        // 5. Recuperar datos para la validación visual
        Integer diasSolicitados = getCollectionElementView().getValueInt("dias");
        Integer diasRestantes = getCollectionElementView().getValueInt("diasRestantes");

        // 6. Validación visual con retroalimentación (error/warning/continuar)
        if (!validarDias(editable, diasSolicitados, diasRestantes, tipo)) return;

        // 7. Descontar los días solicitados del saldo
        int solicitados = diasSolicitados != null ? diasSolicitados : 0;
        int restantes   = diasRestantes   != null ? diasRestantes   : 0;
        int dias = restantes - solicitados;
        getCollectionElementView().setValueNotifying("diasRestantes", dias);

        // 8. Persistencia (gatilla validaciones JPA @PrePersist)
        super.execute();

        // 9. Mensaje de éxito i18n
        addMessage(XavaResources.getString("licencia_guardada_ok", solicitados));
    }

    /**
     * Valida diferencias entre días solicitados y días disponibles (restantes).
     *
     * Comportamiento híbrido según tipo de licencia:
     *
     * TIPOS ESTRICTOS (VACACIONES, ENFERMEDAD):
     * - Si solicitados > restantes → ERROR (no guardar, campo NO editable).
     *   El cupo legal no puede excederse bajo ninguna circunstancia.
     *
     * TIPOS FLEXIBLES (todos los demás):
     * - Si solicitados > restantes → ERROR + habilitar edición del campo.
     * - Si solicitados != restantes y campo editable → autoajuste + WARNING.
     * - Si coinciden → continuar normalmente.
     *
     * @return true si puede continuar el guardado; false si se debe frenar.
     */
    private boolean validarDias(boolean editable, Integer diasSolicitados,
            Integer diasRestantes, TipoLicenciaAR tipo) throws Exception {
        int solicitados = diasSolicitados != null ? diasSolicitados : 0;
        int restantes   = diasRestantes   != null ? diasRestantes   : 0;

        int diff = solicitados - restantes;

        // Determinar si el tipo es estricto (topes legales LCT no negociables)
        boolean esEstricto = (tipo == TipoLicenciaAR.VACACIONES
                           || tipo == TipoLicenciaAR.ENFERMEDAD);

        // Caso 1: Exceso de días
        if (diff > 0) {
            if (esEstricto) {
                // ESTRICTO: error sin habilitar edición. No se puede forzar.
                addError(
                    "dias_restantes_distinto_solicitados_estricto",
                    restantes,
                    solicitados,
                    "No se permite exceder el cupo legal para este tipo de licencia"
                );
            } else {
                // FLEXIBLE: error + habilitar edición para corrección/autorización manual.
                getCollectionElementView().setEditable("diasRestantes", true);
                addError(
                    "dias_restantes_distinto_solicitados",
                    restantes,
                    solicitados,
                    "Ajuste manualmente los días disponibles"
                );
            }
            return false;
        }

        // Caso 2: No excede, pero difiere y es editable (solo para tipos flexibles)
        if (!esEstricto && editable && diff != 0) {
            getCollectionElementView().setValue("diasRestantes", solicitados);
            addWarning(
                "dias_restantes_distinto_solicitados",
                restantes,
                solicitados,
                "Se ajustó automáticamente"
            );
            return false;
        }

        // Caso 3: Coincide o no editable → continuar normalmente.
        return true;
    }
}
