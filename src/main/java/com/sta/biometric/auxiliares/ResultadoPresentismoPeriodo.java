package com.sta.biometric.auxiliares;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * DTO puro e inmutable que transporta el resultado completo de la evaluación de Presentismo
 * para un conjunto de jornadas (período de liquidación).
 * Sin dependencias de JPA ni OpenXava.
 */
@Getter
@AllArgsConstructor
public class ResultadoPresentismoPeriodo {

    // Status global y trazabilidad de decisión
    private final boolean cumplePresentismo;
    private final String politicaAplicada;
    private final String configuracionUtilizada;
    private final List<String> reglasIncumplidas;
    private final String motivoDetalladoPerdida;

    // Contadores acumulados del período
    private final int totalLlegadasTarde;
    private final int totalSalidasAnticipadas;
    private final int totalJornadasIncompletas;
    private final int totalFichadasIncompletas;
    private final int totalAusenciasInjustificadas;
    private final int minutosDemoraAcumulados;
    private final int totalPausasExcedidas;
    private final int totalLicenciasSinGoce;

    // Colección detallada de incidencias
    private final List<DetalleIncidenciaPresentismo> incidencias;

    public String getEstadoFormatted() {
        return cumplePresentismo ? "✅ CUMPLE PRESENTISMO" : "❌ PÉRDIDA DE PRESENTISMO";
    }
}
