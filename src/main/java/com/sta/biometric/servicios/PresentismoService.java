package com.sta.biometric.servicios;

import java.util.*;
import com.sta.biometric.auxiliares.*;
import com.sta.biometric.enums.*;
import com.sta.biometric.modelo.AuditoriaRegistros;

/**
 * Servicio ligero y sin estado para evaluar el Presentismo de un conjunto de jornadas.
 * 
 * <p>
 * <b>Responsabilidad Exclusiva:</b>
 * Recorrer las jornadas consolidadas del período, interpretar la evaluación
 * ya realizada por {@link AuditoriaRegistros} y aplicar la política paramétrica
 * leída desde {@link ConfiguracionesPreferencias}.
 * </p>
 * 
 * <p>
 * <b>Invariante:</b>
 * No recalcula fichadas, ni horarios, ni turnos, ni tolerancias.
 * Consume únicamente los datos ya consolidados en el dominio.
 * </p>
 */
public class PresentismoService {

    private PresentismoService() {
        // Servicio estático utilitario
    }

    /**
     * Evalúa la elegibilidad del presentismo para la lista de jornadas del período.
     * 
     * @param jornadas Lista de registros de auditoría de asistencia del período
     * @return DTO {@link ResultadoPresentismoPeriodo} con el resumen e incidencias
     */
    public static ResultadoPresentismoPeriodo evaluarPresentismo(List<AuditoriaRegistros> jornadas) {
        if (jornadas == null) {
            jornadas = Collections.emptyList();
        }

        // Lectura de parámetros de configuración
        boolean habilitado = ConfiguracionesPreferencias.obtenerValor(
                PresentismoProperties.HABILITADO, true, Boolean.class);

        String politicaNombre = ConfiguracionesPreferencias.obtenerValor(
                PresentismoProperties.POLITICA_NOMBRE, "GENERAL", String.class);

        if (!habilitado) {
            return new ResultadoPresentismoPeriodo(
                    true,
                    politicaNombre,
                    "Módulo Deshabilitado",
                    Collections.emptyList(),
                    "El control de presentismo se encuentra deshabilitado por configuración.",
                    0, 0, 0, 0, 0, 0, 0, 0,
                    Collections.emptyList());
        }

        // Feature Toggles
        boolean evalTarde = ConfiguracionesPreferencias.obtenerValor(PresentismoProperties.EVALUAR_LLEGADAS_TARDE, true, Boolean.class);
        boolean evalSalida = ConfiguracionesPreferencias.obtenerValor(PresentismoProperties.EVALUAR_SALIDAS_ANTICIPADAS, true, Boolean.class);
        boolean evalIncompleta = ConfiguracionesPreferencias.obtenerValor(PresentismoProperties.EVALUAR_JORNADAS_INCOMPLETAS, true, Boolean.class);
        boolean evalAusencia = ConfiguracionesPreferencias.obtenerValor(PresentismoProperties.EVALUAR_AUSENCIAS, true, Boolean.class);
        boolean evalPausas = ConfiguracionesPreferencias.obtenerValor(PresentismoProperties.EVALUAR_PAUSAS, false, Boolean.class);

        // Umbrales máximos tolerados en el período
        int maxLlegadasTarde = ConfiguracionesPreferencias.obtenerValor(PresentismoProperties.MAX_LLEGADAS_TARDE, 2, Integer.class);
        int maxSalidasAnticipadas = ConfiguracionesPreferencias.obtenerValor(PresentismoProperties.MAX_SALIDAS_ANTICIPADAS, 1, Integer.class);
        int maxJornadasIncompletas = ConfiguracionesPreferencias.obtenerValor(PresentismoProperties.MAX_JORNADAS_INCOMPLETAS, 1, Integer.class);
        int maxMinutosDemora = ConfiguracionesPreferencias.obtenerValor(PresentismoProperties.MAX_MINUTOS_DEMORA_ACUMULADOS, 15, Integer.class);
        int maxAusencias = ConfiguracionesPreferencias.obtenerValor(PresentismoProperties.MAX_AUSENCIAS_INJUSTIFICADAS, 0, Integer.class);
        int maxBancoDescontado = ConfiguracionesPreferencias.obtenerValor(PresentismoProperties.MAX_BANCO_HORAS_DESCONTADAS, 2, Integer.class);

        boolean computanFichadasIncompletas = ConfiguracionesPreferencias.obtenerValor(PresentismoProperties.FICHADAS_INCOMPLETAS_COMPUTAN, true, Boolean.class);
        boolean computanLicenciasNoJustificadas = ConfiguracionesPreferencias.obtenerValor(PresentismoProperties.LICENCIAS_NO_JUSTIFICADAS_COMPUTAN, true, Boolean.class);

        // Contadores acumulados
        int totalLlegadasTarde = 0;
        int totalSalidasAnticipadas = 0;
        int totalJornadasIncompletas = 0;
        int totalFichadasIncompletas = 0;
        int totalAusenciasInjustificadas = 0;
        int minutosDemoraAcumulados = 0;
        int totalPausasExcedidas = 0;
        int totalLicenciasSinGoce = 0;
        int totalJornadasBancoDescontadas = 0;

        List<DetalleIncidenciaPresentismo> incidencias = new ArrayList<>();

        // Recorrido de jornadas e interpretación del resultado consolidado de AuditoriaRegistros
        for (AuditoriaRegistros reg : jornadas) {
            // Regla Unificada de Banco de Horas:
            // Si la jornada registra movimiento en Banco y el checkbox persistente descontarPresentismo es false, la jornada queda exenta.
            if (reg.getMinutosEnviadosAlBanco() != 0) {
                if (!reg.isDescontarPresentismo()) {
                    continue; // Exenta por decisión explícita en la jornada
                } else {
                    totalJornadasBancoDescontadas++;
                    incidencias.add(new DetalleIncidenciaPresentismo(
                            reg.getFecha(),
                            TipoIncidenciaPresentismo.JORNADA_INCOMPLETA,
                            "Jornada enviada a Banco marcada para descontar Presentismo",
                            0));
                }
            }

            EvaluacionJornada eval = reg.getEvaluacion();
            if (eval == null) continue;

            String nota = reg.getNota() != null ? reg.getNota() : "";

            // 1. Ausencias Injustificadas
            if (eval == EvaluacionJornada.AUSENTE && evalAusencia) {
                totalAusenciasInjustificadas++;
                incidencias.add(new DetalleIncidenciaPresentismo(
                        reg.getFecha(),
                        TipoIncidenciaPresentismo.AUSENCIA,
                        nota.isBlank() ? "Ausencia injustificada" : nota,
                        0));
            }
            // 2. Fichadas Incompletas (Sin entrada o sin salida)
            else if ((eval == EvaluacionJornada.SIN_ENTRADA || eval == EvaluacionJornada.SIN_SALIDA) && computanFichadasIncompletas) {
                totalFichadasIncompletas++;
                incidencias.add(new DetalleIncidenciaPresentismo(
                        reg.getFecha(),
                        TipoIncidenciaPresentismo.FICHADA_INCOMPLETA,
                        nota.isBlank() ? eval.getDescripcion() : nota,
                        0));
            }
            // 3. Licencias No Justificadas (Criterio disciplinario principal: !justificado)
            else if ((eval == EvaluacionJornada.LICENCIA_NO_JUSTIFICADA || eval == EvaluacionJornada.LICENCIA_SIN_GOCE || !reg.isJustificado()) && reg.isLicencia() && computanLicenciasNoJustificadas) {
                totalLicenciasSinGoce++;
                incidencias.add(new DetalleIncidenciaPresentismo(
                        reg.getFecha(),
                        TipoIncidenciaPresentismo.LICENCIA_NO_JUSTIFICADA,
                        nota.isBlank() ? "Licencia no justificada" : nota,
                        0));
            }
            // 4. Jornadas Incompletas / Demoras / Salidas Anticipadas
            else if (eval == EvaluacionJornada.INCOMPLETA || eval == EvaluacionJornada.EN_CURSO) {
                boolean esTarde = nota.contains("Llegada tarde");
                boolean esSalidaAnticipada = nota.contains("Salida anticipada");

                if (esTarde && evalTarde) {
                    totalLlegadasTarde++;
                    incidencias.add(new DetalleIncidenciaPresentismo(
                            reg.getFecha(),
                            TipoIncidenciaPresentismo.LLEGADA_TARDE,
                            nota,
                            0));
                }
                if (esSalidaAnticipada && evalSalida) {
                    totalSalidasAnticipadas++;
                    incidencias.add(new DetalleIncidenciaPresentismo(
                            reg.getFecha(),
                            TipoIncidenciaPresentismo.SALIDA_ANTICIPADA,
                            nota,
                            0));
                }

                if (eval == EvaluacionJornada.INCOMPLETA && evalIncompleta) {
                    totalJornadasIncompletas++;
                    if (!esTarde && !esSalidaAnticipada) {
                        incidencias.add(new DetalleIncidenciaPresentismo(
                                reg.getFecha(),
                                TipoIncidenciaPresentismo.JORNADA_INCOMPLETA,
                                nota.isBlank() ? "Jornada incompleta" : nota,
                                0));
                    }
                }
            }

            // 5. Verificación de Pausas (preparada para cuando AuditoriaRegistros exponga el consolidado de pausas)
            if (evalPausas && reg.getEmpleado() != null && reg.getEmpleado().isAceptaPausa()) {
                // Si la nota consolidada por la auditoría indica exceso de pausa
                if (nota.contains("Exceso de pausa")) {
                    totalPausasExcedidas++;
                    incidencias.add(new DetalleIncidenciaPresentismo(
                            reg.getFecha(),
                            TipoIncidenciaPresentismo.PAUSA_EXCEDIDA,
                            nota,
                            0));
                }
            }
        }

        // Evaluación de límites y construcción de reglas incumplidas
        List<String> reglasIncumplidas = new ArrayList<>();

        if (totalAusenciasInjustificadas > maxAusencias) {
            reglasIncumplidas.add(String.format("%d Ausencia(s) injustificada(s) (máx: %d)", totalAusenciasInjustificadas, maxAusencias));
        }
        if (totalLlegadasTarde > maxLlegadasTarde) {
            reglasIncumplidas.add(String.format("%d Llegada(s) tarde (máx: %d)", totalLlegadasTarde, maxLlegadasTarde));
        }
        if (totalSalidasAnticipadas > maxSalidasAnticipadas) {
            reglasIncumplidas.add(String.format("%d Salida(s) anticipada(s) (máx: %d)", totalSalidasAnticipadas, maxSalidasAnticipadas));
        }
        if (totalJornadasIncompletas > maxJornadasIncompletas) {
            reglasIncumplidas.add(String.format("%d Jornada(s) incompleta(s) (máx: %d)", totalJornadasIncompletas, maxJornadasIncompletas));
        }
        if (minutosDemoraAcumulados > maxMinutosDemora) {
            reglasIncumplidas.add(String.format("%d Minuto(s) de demora acumulados (máx: %d min)", minutosDemoraAcumulados, maxMinutosDemora));
        }
        if (maxBancoDescontado >= 0 && totalJornadasBancoDescontadas > maxBancoDescontado) {
            reglasIncumplidas.add(String.format("%d Jornada(s) en Banco con descuento (máx: %d)", totalJornadasBancoDescontadas, maxBancoDescontado));
        }
        if (totalFichadasIncompletas > 0 && computanFichadasIncompletas) {
            reglasIncumplidas.add(String.format("%d Fichada(s) incompleta(s)", totalFichadasIncompletas));
        }
        if (totalLicenciasSinGoce > 0 && computanLicenciasNoJustificadas) {
            reglasIncumplidas.add(String.format("%d Licencia(s) sin goce/no justificada(s)", totalLicenciasSinGoce));
        }

        boolean cumplePresentismo = reglasIncumplidas.isEmpty();

        String configuracionStr = String.format("Máx. Tarde: %d, Máx. Salidas: %d, Máx. Incompletas: %d, Máx. Ausencias: %d",
                maxLlegadasTarde, maxSalidasAnticipadas, maxJornadasIncompletas, maxAusencias);

        String motivoDetallado;
        if (cumplePresentismo) {
            if (incidencias.isEmpty()) {
                motivoDetallado = "Asistencia perfecta en el período.";
            } else {
                motivoDetallado = "Cumple dentro de las tolerancias permitidas.";
            }
        } else {
            motivoDetallado = String.join(", ", reglasIncumplidas);
        }

        return new ResultadoPresentismoPeriodo(
                cumplePresentismo,
                politicaNombre,
                configuracionStr,
                reglasIncumplidas,
                motivoDetallado,
                totalLlegadasTarde,
                totalSalidasAnticipadas,
                totalJornadasIncompletas,
                totalFichadasIncompletas,
                totalAusenciasInjustificadas,
                minutosDemoraAcumulados,
                totalPausasExcedidas,
                totalLicenciasSinGoce,
                incidencias);
    }
}
