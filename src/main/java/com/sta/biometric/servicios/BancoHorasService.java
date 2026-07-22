package com.sta.biometric.servicios;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;

import org.openxava.jpa.XPersistence;
import org.openxava.util.Users;

import com.sta.biometric.enums.EstadoLiquidacion;
import com.sta.biometric.enums.EvaluacionJornada;
import com.sta.biometric.enums.TipoMovimientoBancoHoras;
import com.sta.biometric.modelo.AuditoriaRegistros;
import com.sta.biometric.modelo.BancoHoras;
import com.sta.biometric.modelo.LiquidacionJornadas;
import com.sta.biometric.modelo.MovimientoBancoHoras;
import com.sta.biometric.modelo.Personal;

/**
 * Servicio central para la gestión del Banco de Horas.
 * 
 * <p>
 * Implementa las operaciones de negocio:
 * </p>
 * <ul>
 * <li>Obtención o creación del banco por empleado</li>
 * <li>Cálculo de diferencia disponible por jornada (soporta envío parcial y ausencias)</li>
 * <li>Envío al banco con actualización transaccional de saldo y registro de auditoría</li>
 * <li>Reversión completa e inmutable mediante nuevos movimientos</li>
 * <li>Reconciliación de saldo (auditoría de consistencia)</li>
 * </ul>
 * 
 * @author Sistema STARH
 * @since 2.0
 */
public class BancoHorasService {

    private static final DateTimeFormatter FORMATO_FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /**
     * Busca el BancoHoras del empleado sin crear uno nuevo (seguro para getters de visualización).
     * 
     * @param empleado Empleado titular
     * @return Entidad BancoHoras o null si no posee banco aún
     */
    public static BancoHoras buscarBanco(Personal empleado) {
        if (empleado == null || empleado.getId() == null) {
            return null;
        }
        EntityManager em = XPersistence.getManager();
        try {
            return em.createQuery(
                    "SELECT b FROM BancoHoras b WHERE b.empleado = :emp", BancoHoras.class)
                    .setParameter("emp", empleado)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    /**
     * Obtiene directamente la lista de movimientos del banco del empleado (sin proxies perezosos).
     * 
     * @param empleado Empleado titular
     * @return Lista de movimientos ordenada desc
     */
    public static List<MovimientoBancoHoras> obtenerMovimientosBanco(Personal empleado) {
        if (empleado == null || empleado.getId() == null) {
            return Collections.emptyList();
        }
        EntityManager em = XPersistence.getManager();
        try {
            return em.createQuery(
                    "SELECT m FROM MovimientoBancoHoras m WHERE m.bancoHoras.empleado = :emp ORDER BY m.fechaCreacion DESC",
                    MovimientoBancoHoras.class)
                    .setParameter("emp", empleado)
                    .getResultList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * Obtiene el BancoHoras del empleado. Si no existe, lo crea transaccionalmente.
     * 
     * @param empleado Empleado titular
     * @return Entidad BancoHoras
     */
    public static BancoHoras obtenerOCrearBanco(Personal empleado) {
        if (empleado == null) {
            throw new IllegalArgumentException("El empleado no puede ser nulo");
        }

        BancoHoras existente = buscarBanco(empleado);
        if (existente != null) {
            return existente;
        }

        EntityManager em = XPersistence.getManager();
        BancoHoras nuevoBanco = new BancoHoras();
        nuevoBanco.setEmpleado(empleado);
        nuevoBanco.setSaldoMinutosActual(0);
        nuevoBanco.setTotalMinutosPositivos(0);
        nuevoBanco.setTotalMinutosNegativos(0);
        nuevoBanco.setFechaCreacion(LocalDateTime.now());
        nuevoBanco.setFechaUltimaActualizacion(LocalDateTime.now());

        em.persist(nuevoBanco);
        return nuevoBanco;
    }

    /**
     * Calcula la diferencia horaria total y la disponible para enviar al banco en un registro.
     * 
     * @param registro Registro de Auditoría de asistencia
     * @return Minutos disponibles (positivo = extras, negativo = faltante/ausencia)
     */
    public static int calcularDiferenciaDisponible(AuditoriaRegistros registro) {
        if (registro == null) return 0;

        int minutosTrabajados = registro.getMinutosTrabajados();
        int minutosEsperados = registro.getMinutosEsperados();
        int minutosExtras = registro.getMinutosExtras();
        int yaEnviados = registro.getMinutosEnviadosAlBanco();

        // 1. Jornada con extras (COMPLETA, FERIADO_TRABAJADO, etc.)
        if (minutosExtras > 0) {
            int disponiblePositivo = Math.max(0, minutosExtras - Math.max(0, yaEnviados));
            return disponiblePositivo;
        }

        // 2. Ausencia total
        if (registro.getEvaluacion() == EvaluacionJornada.AUSENTE) {
            int minDeuda = minutosEsperados > 0 ? minutosEsperados : 480;
            int deudaTotal = -minDeuda;
            int restanteDeuda = deudaTotal - Math.min(0, yaEnviados);
            return Math.min(0, restanteDeuda);
        }

        // 3. Jornada incompleta (trabajó menos de lo esperado)
        if (minutosTrabajados < minutosEsperados && minutosEsperados > 0) {
            int faltanteTotal = minutosTrabajados - minutosEsperados;
            int restanteFaltante = faltanteTotal - Math.min(0, yaEnviados);
            return Math.min(0, restanteFaltante);
        }

        return 0;
    }

    /**
     * Registra el envío de horas (extras o faltantes) al Banco de Horas.
     * 
     * @param registro       Registro de auditoría origen
     * @param minutosAEnviar Minutos con signo (+ si ingresa extras, - si descuenta/ausencia)
     * @param observacion    Motivo obligatorio
     * @return El movimiento generado
     */
    public static MovimientoBancoHoras enviarAlBanco(AuditoriaRegistros registro, int minutosAEnviar, String observacion) {
        if (registro == null || registro.getEmpleado() == null) {
            throw new IllegalArgumentException("Registro de auditoría o empleado no válido");
        }
        if (minutosAEnviar == 0) {
            throw new IllegalArgumentException("La cantidad de minutos a enviar debe ser distinta de cero");
        }
        if (observacion == null || observacion.isBlank()) {
            throw new IllegalArgumentException("Debe especificar un motivo u observación para el envío al banco");
        }

        EntityManager em = XPersistence.getManager();
        AuditoriaRegistros regBD = em.find(AuditoriaRegistros.class, registro.getId());
        if (regBD != null) {
            registro = regBD;
        }

        int disponible = calcularDiferenciaDisponible(registro);
        if (disponible == 0) {
            throw new IllegalStateException("No hay horas disponibles para enviar al banco en este registro");
        }

        // Validaciones estrictas de signo y cantidad
        if (disponible > 0) {
            if (minutosAEnviar <= 0) {
                throw new IllegalArgumentException("El valor a enviar debe ser positivo para este registro");
            }
            if (minutosAEnviar > disponible) {
                throw new IllegalArgumentException("No puede enviar más de " + formatearMinutos(disponible) + " al banco");
            }
        } else {
            if (minutosAEnviar >= 0) {
                throw new IllegalArgumentException("El valor a enviar debe ser negativo para este registro (deuda)");
            }
            if (minutosAEnviar < disponible) {
                throw new IllegalArgumentException("No puede debitar más de " + formatearMinutos(disponible) + " al banco");
            }
        }

        Personal empleado = registro.getEmpleado();
        BancoHoras banco = obtenerOCrearBanco(empleado);

        int saldoAnterior = banco.getSaldoMinutosActual();
        int saldoNuevo = saldoAnterior + minutosAEnviar;

        boolean esIngreso = minutosAEnviar > 0;
        TipoMovimientoBancoHoras tipo = esIngreso ? TipoMovimientoBancoHoras.INGRESO : TipoMovimientoBancoHoras.DESCUENTO;

        // Crear Movimiento Inmutable
        MovimientoBancoHoras movimiento = new MovimientoBancoHoras();
        movimiento.setBancoHoras(banco);
        movimiento.setTipo(tipo);
        movimiento.setMinutos(Math.abs(minutosAEnviar));
        movimiento.setSignoPositivo(esIngreso);
        movimiento.setSaldoAnterior(saldoAnterior);
        movimiento.setSaldoNuevo(saldoNuevo);
        movimiento.setAuditoriaRegistro(registro);
        movimiento.setFechaJornada(registro.getFecha());
        movimiento.setUsuarioOperacion(obtenerUsuarioActual());
        movimiento.setFechaCreacion(LocalDateTime.now());
        movimiento.setObservacion(observacion);

        // Buscar liquidación del período si existe
        LiquidacionJornadas liquidacion = buscarLiquidacionDelPeriodo(registro);
        if (liquidacion != null) {
            movimiento.setLiquidacion(liquidacion);
        }

        em.persist(movimiento);

        // Actualizar BancoHoras cabecera
        banco.setSaldoMinutosActual(saldoNuevo);
        if (esIngreso) {
            banco.setTotalMinutosPositivos(banco.getTotalMinutosPositivos() + minutosAEnviar);
        } else {
            banco.setTotalMinutosNegativos(banco.getTotalMinutosNegativos() + Math.abs(minutosAEnviar));
        }
        banco.setFechaUltimaActualizacion(LocalDateTime.now());
        em.merge(banco);

        // Actualizar AuditoriaRegistros
        registro.setMinutosEnviadosAlBanco(registro.getMinutosEnviadosAlBanco() + minutosAEnviar);
        
        String notaActual = registro.getNota();
        String lineaNota = String.format("[%s] %s | 🏦 Banco de Horas: %s",
                LocalDateTime.now().format(FORMATO_FECHA_HORA),
                obtenerUsuarioActual(),
                formatearMinutos(minutosAEnviar));
        
        if (notaActual == null || notaActual.isBlank()) {
            registro.setNota(lineaNota);
        } else {
            registro.setNota(notaActual + "\n" + lineaNota);
        }
        
        em.merge(registro);

        // Si la liquidación existe y está CERRADA, registrar observación de recálculo pendiente
        if (liquidacion != null && liquidacion.getEstadoPeriodo() == EstadoLiquidacion.CERRADO) {
            String obsLiq = liquidacion.getObservaciones();
            String lineaLiq = String.format("[%s] %s | ⚠️ Movimiento en Banco de Horas (%s) en fecha %s requiere recálculo de liquidación",
                    LocalDateTime.now().format(FORMATO_FECHA_HORA),
                    obtenerUsuarioActual(),
                    formatearMinutos(minutosAEnviar),
                    registro.getFecha());
            liquidacion.setObservaciones(obsLiq == null || obsLiq.isBlank() ? lineaLiq : obsLiq + "\n" + lineaLiq);
            em.merge(liquidacion);
        }

        return movimiento;
    }

    /**
     * Revierte y elimina físicamente un movimiento del Banco de Horas, restaurando 
     * el registro de asistencia y los saldos del banco al estado previo sin dejar rastros.
     * 
     * @param movimientoOriginal Movimiento que se desea eliminar/revertir
     * @param motivo              Motivo u observación opcional de la reversión
     */
    public static void revertirYEliminarMovimiento(MovimientoBancoHoras movimientoOriginal, String motivo) {
        if (movimientoOriginal == null) {
            throw new IllegalArgumentException("Debe especificar el movimiento a eliminar");
        }

        EntityManager em = XPersistence.getManager();
        MovimientoBancoHoras movPersistente = em.find(MovimientoBancoHoras.class, movimientoOriginal.getId());
        if (movPersistente == null) {
            throw new IllegalStateException("El movimiento seleccionado ya no existe en la base de datos");
        }

        BancoHoras banco = movPersistente.getBancoHoras();
        int minutosMov = movPersistente.getMinutos();
        boolean esPositivo = movPersistente.isSignoPositivo();
        int impactoSaldo = esPositivo ? -minutosMov : minutosMov;

        // 1. Restaurar saldo cabecera del banco
        banco.setSaldoMinutosActual(banco.getSaldoMinutosActual() + impactoSaldo);
        if (esPositivo) {
            banco.setTotalMinutosPositivos(Math.max(0, banco.getTotalMinutosPositivos() - minutosMov));
        } else {
            banco.setTotalMinutosNegativos(Math.max(0, banco.getTotalMinutosNegativos() - minutosMov));
        }
        banco.setFechaUltimaActualizacion(LocalDateTime.now());
        em.merge(banco);

        // 2. Restaurar AuditoriaRegistros si estaba vinculado
        AuditoriaRegistros reg = movPersistente.getAuditoriaRegistro();
        if (reg != null) {
            int minutosRestar = esPositivo ? minutosMov : -minutosMov;
            int nuevoEnviado = reg.getMinutosEnviadosAlBanco() - minutosRestar;
            reg.setMinutosEnviadosAlBanco(nuevoEnviado);

            // Si volvió a cero o cambio de signo, limpiar las notas relativas al banco de horas
            if (nuevoEnviado == 0 && reg.getNota() != null) {
                String[] lineas = reg.getNota().split("\n");
                StringBuilder sb = new StringBuilder();
                for (String linea : lineas) {
                    if (!linea.contains("🏦 Banco de Horas") && !linea.contains("↩️")) {
                        if (sb.length() > 0) sb.append("\n");
                        sb.append(linea);
                    }
                }
                reg.setNota(sb.toString().trim());
            }
            em.merge(reg);
        }

        // 3. Eliminar físicamente el movimiento de la base de datos (sin dejar rastros)
        em.remove(movPersistente);
    }

    /**
     * Busca el movimiento de Banco de Horas asociado a un registro de auditoría.
     */
    public static MovimientoBancoHoras buscarMovimientoDeRegistro(AuditoriaRegistros registro) {
        if (registro == null || registro.getId() == null) return null;
        EntityManager em = XPersistence.getManager();
        try {
            return em.createQuery(
                    "SELECT m FROM MovimientoBancoHoras m WHERE m.auditoriaRegistro = :reg",
                    MovimientoBancoHoras.class)
                    .setParameter("reg", registro)
                    .setMaxResults(1)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    /**
     * Busca la LiquidacionJornadas a la que pertenece un registro de auditoría.
     */
    public static LiquidacionJornadas buscarLiquidacionDelPeriodo(AuditoriaRegistros registro) {
        if (registro == null || registro.getEmpleado() == null || registro.getFecha() == null) {
            return null;
        }
        EntityManager em = XPersistence.getManager();
        List<LiquidacionJornadas> lista = em.createQuery(
                "SELECT l FROM LiquidacionJornadas l WHERE l.empleado = :emp AND :fecha BETWEEN l.periodoDesde AND l.periodoHasta",
                LiquidacionJornadas.class)
                .setParameter("emp", registro.getEmpleado())
                .setParameter("fecha", registro.getFecha())
                .getResultList();
        return lista.isEmpty() ? null : lista.get(0);
    }

    /**
     * Reconcilia el saldo cacheado de BancoHoras sumando todos sus movimientos.
     * Útil para verificación de consistencia.
     */
    public static void recalcularSaldo(BancoHoras banco) {
        if (banco == null) return;

        EntityManager em = XPersistence.getManager();
        List<MovimientoBancoHoras> movs = em.createQuery(
                "SELECT m FROM MovimientoBancoHoras m WHERE m.bancoHoras = :banco ORDER BY m.fechaCreacion ASC",
                MovimientoBancoHoras.class)
                .setParameter("banco", banco)
                .getResultList();

        int saldo = 0;
        int totalPos = 0;
        int totalNeg = 0;

        for (MovimientoBancoHoras m : movs) {
            int min = m.getMinutos();
            if (m.isSignoPositivo()) {
                saldo += min;
                totalPos += min;
            } else {
                saldo -= min;
                totalNeg += min;
            }
        }

        banco.setSaldoMinutosActual(saldo);
        banco.setTotalMinutosPositivos(totalPos);
        banco.setTotalMinutosNegativos(totalNeg);
        banco.setFechaUltimaActualizacion(LocalDateTime.now());
        em.merge(banco);
    }

    private static String obtenerUsuarioActual() {
        String u = Users.getCurrent();
        return (u != null && !u.isBlank()) ? u : "SISTEMA";
    }

    private static String formatearMinutos(int totalMinutos) {
        String signo = totalMinutos < 0 ? "-" : (totalMinutos > 0 ? "+" : "");
        int abs = Math.abs(totalMinutos);
        int h = abs / 60;
        int m = abs % 60;
        return String.format("%s%02d:%02d hs", signo, h, m);
    }
}
