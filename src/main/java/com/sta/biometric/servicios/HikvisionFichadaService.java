package com.sta.biometric.servicios;

import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.logging.*;
import javax.persistence.*;
import org.openxava.jpa.*;
import com.sta.biometric.modelo.*;
import com.sta.biometric.enums.TipoMovimiento;

/**
 * Servicio para procesar y registrar fichadas provenientes de dispositivos Hikvision.
 *
 * <p>
 * Se encarga de buscar al empleado por su terminalUserId, parsear el timestamp del evento,
 * deduplicar registros y delegar la lógica de jornadas al AsistenciaDiariaService.
 * </p>
 *
 * @author Sistema STARH
 * @version 1.0
 */
public class HikvisionFichadaService {

    private static final Logger LOG = Logger.getLogger(
            HikvisionFichadaService.class.getName());

    /**
     * Registra una fichada recibida desde un dispositivo Hikvision.
     *
     * @param employeeNo    ID del empleado en el dispositivo (terminalUserId)
     * @param timeStr       Timestamp ISO 8601 del evento
     * @param serialNo      Número de serie del evento (para deduplicación)
     * @param dispositivoId ID del dispositivo en STARH
     * @return Resultado del procesamiento
     */
    public static String registrarFichada(
            String employeeNo,
            String timeStr,
            int serialNo,
            String dispositivoId) {

        EntityManager em = XPersistence.getManager();
        
        int toleranciaSegundos = 1800; // Valor por defecto (30 minutos)

        // 1. Buscar y actualizar dispositivo para deduplicación por serialNo
        if (dispositivoId != null && !dispositivoId.trim().isEmpty()) {
            try {
                DispositivoBiometrico dispositivo = null;
                try {
                    dispositivo = em.createQuery(
                        "SELECT d FROM DispositivoBiometrico d WHERE d.codigo = :codigo", 
                        DispositivoBiometrico.class)
                        .setParameter("codigo", dispositivoId)
                        .getSingleResult();
                } catch (NoResultException e) {
                    LOG.warning("[HV] Dispositivo no encontrado con codigo: " + dispositivoId + ". Se procesara la fichada pero no se verificara el serialNo.");
                }
                if (dispositivo != null) {
                    toleranciaSegundos = dispositivo.getToleranciaDuplicadosSegundos();
                    if (!dispositivo.isActivo()) {
                        LOG.warning("[HV] Dispositivo inactivo: " + dispositivo.getNombre());
                        return "DISPOSITIVO_INACTIVO";
                    }
                    if (serialNo > 0 && serialNo <= dispositivo.getUltimoSerialNo()) {
                        LOG.info("[HV] Evento serialNo=" + serialNo + " menor o igual al último procesado (" 
                                + dispositivo.getUltimoSerialNo() + "). Ignorado.");
                        return "DUPLICADO_SERIAL_IGNORADO";
                    }
                    dispositivo.setUltimoSerialNo(serialNo);
                    em.merge(dispositivo);
                }
            } catch (Exception e) {
                LOG.log(Level.WARNING, "[HV] Error al verificar dispositivo: " + dispositivoId, e);
            }
        }

        // 2. Buscar empleado por terminalUserId
        Personal empleado = buscarPorTerminalUserId(em, employeeNo);
        if (empleado == null) {
            LOG.warning("[HV] Empleado no encontrado: terminalUserId=" + employeeNo);
            return "EMPLEADO_NO_ENCONTRADO";
        }

        if (!empleado.isActivo()) {
            LOG.warning("[HV] Empleado inactivo: " + empleado.getNombreCompleto());
            return "EMPLEADO_INACTIVO";
        }

        // 3. Parsear timestamp
        LocalDateTime fechaHora = parsearTimestamp(timeStr);
        if (fechaHora == null) {
            LOG.warning("[HV] Timestamp inválido: " + timeStr);
            return "TIMESTAMP_INVALIDO";
        }

        LocalDate fechaCalendario = fechaHora.toLocalDate();
        LocalTime horaFichada = fechaHora.toLocalTime();

        // 4. Determinar fecha operativa de la jornada (resuelve turnos nocturnos)
        LocalDate fechaOperativa = InterpreteFichadasService.determinarFechaJornada(
                empleado, fechaCalendario, horaFichada);

        // 5. Obtener fichadas existentes del día
        AuditoriaRegistros auditoriaExistente = buscarAuditoriaDiaria(em, empleado, fechaOperativa);

        List<ColeccionRegistros> registrosDelDia = new ArrayList<>();
        if (auditoriaExistente != null && auditoriaExistente.getRegistros() != null) {
            registrosDelDia.addAll(auditoriaExistente.getRegistros());
        }

        // 6. Verificar duplicado por hora (usando tolerancia del dispositivo)
        for (ColeccionRegistros existente : registrosDelDia) {
            if (existente.getHora() != null
                    && Math.abs(existente.getHora().toSecondOfDay() - horaFichada.toSecondOfDay()) <= toleranciaSegundos) {
                LOG.info("[HV] Fichada duplicada ignorada: " + empleado.getNombreCompleto() + " hora=" + horaFichada);
                return "DUPLICADO_IGNORADO";
            }
        }

        // 7. Crear nuevo registro
        ColeccionRegistros nuevoRegistro = new ColeccionRegistros();
        nuevoRegistro.setFecha(fechaOperativa);
        nuevoRegistro.setHora(horaFichada);
        nuevoRegistro.setObservacion("Fichada Hikvision (serial: " + serialNo + ")");

        registrosDelDia.add(nuevoRegistro);

        // 8. Ordenar cronológicamente
        registrosDelDia.sort(Comparator.comparing(ColeccionRegistros::getHora,
                Comparator.nullsLast(Comparator.naturalOrder())));

        // 9. Asignar tipos alternados (ENTRADA/SALIDA) respetando PAUSAS
        boolean esEntrada = true;
        for (ColeccionRegistros reg : registrosDelDia) {
            if (reg.getTipoMovimiento() == TipoMovimiento.PAUSA_INICIO) {
                // Si hay inicio de pausa (ej. app), la próxima fichada del reloj será ENTRADA (reingreso)
                esEntrada = true;
            } else if (reg.getTipoMovimiento() == TipoMovimiento.PAUSA_FIN) {
                // Si hay fin de pausa explícito, la próxima fichada del reloj será SALIDA
                esEntrada = false;
            } else if (reg.getTipoMovimiento() == null
                    || reg.getTipoMovimiento() == TipoMovimiento.ENTRADA
                    || reg.getTipoMovimiento() == TipoMovimiento.SALIDA) {
                reg.setTipoMovimiento(esEntrada ? TipoMovimiento.ENTRADA : TipoMovimiento.SALIDA);
                esEntrada = !esEntrada;
            }
        }

        // 10. Consolidar la jornada (incluye normalizarSecuencia)
        AsistenciaDiariaService.consolidarDia(empleado, fechaOperativa, registrosDelDia);

        LOG.info("[HV] Fichada registrada: " + empleado.getNombreCompleto()
                + " fecha=" + fechaOperativa + " hora=" + horaFichada
                + " tipo=" + nuevoRegistro.getTipoMovimiento());

        return "FICHADA_REGISTRADA";
    }

    /**
     * Busca un empleado por su terminalUserId (ID del fichador Hikvision).
     */
    private static Personal buscarPorTerminalUserId(EntityManager em, String terminalUserId) {
        try {
            return em.createQuery("SELECT p FROM Personal p WHERE p.terminalUserId = :terminalUserId", Personal.class)
                    .setParameter("terminalUserId", terminalUserId)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    /**
     * Busca la auditoría de un día para un empleado.
     */
    private static AuditoriaRegistros buscarAuditoriaDiaria(EntityManager em, Personal empleado, LocalDate fecha) {
        try {
            List<AuditoriaRegistros> resultados = em.createQuery("SELECT a FROM AuditoriaRegistros a WHERE a.empleado = :emp AND a.fecha = :fecha",
                    AuditoriaRegistros.class)
                    .setParameter("emp", empleado)
                    .setParameter("fecha", fecha)
                    .getResultList();
            if (resultados.isEmpty()) {
                return null;
            }
            if (resultados.size() > 1) {
                LOG.warning("[HV] Se encontraron múltiples AuditoriaRegistros (" + resultados.size() 
                        + ") para empleado=" + empleado.getNombreCompleto() + " fecha=" + fecha + ". Usando la primera.");
            }
            return resultados.get(0);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "[HV] Error al buscar AuditoriaRegistros", e);
            return null;
        }
    }

    /**
     * Parsea un timestamp ISO 8601 con offset de zona horaria.
     * Ejemplo: "2026-06-19T08:00:00-03:00"
     */
    private static LocalDateTime parsearTimestamp(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) return null;
        try {
            // Obtenemos la fecha y hora tal como la registró el reloj del dispositivo,
            // ignorando desplazamientos por zona horaria mal configurada en el aparato.
            // La hora que muestra la pantalla del dispositivo es la que el empleado experimenta al fichar.
            return OffsetDateTime.parse(timeStr, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toLocalDateTime();
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(timeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (DateTimeParseException e2) {
                return null;
            }
        }
    }
}
