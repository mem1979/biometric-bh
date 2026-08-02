package com.sta.biometric.auxiliares;

import com.sta.biometric.formateadores.TiempoUtils;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * DTO inmutable que representa la cantidad neta de minutos a liquidar por categoría de hora
 * para una jornada específica, posterior a la aplicación de la política del Banco de Horas.
 * 
 * <p>
 * Sirve como la representación oficial del resultado de liquidación diaria consumida por:
 * </p>
 * <ul>
 * <li>{@code LiquidacionJornadaService} (para acumulación del período)</li>
 * <li>Visualización en diálogo "Ver Jornadas"</li>
 * <li>Exportador Excel de Jornadas</li>
 * </ul>
 * 
 * @author Sistema STARH
 * @since 2.1
 */
@Getter
@AllArgsConstructor
public class HorasNetasJornada {

    private final int minutosNormales;
    private final int minutosExtras;
    private final int minutosEspeciales;

    /**
     * Retorna los minutos normales netos a liquidar formateados como HH:MM.
     */
    public String getNormalesFormatted() {
        return TiempoUtils.formatearMinutosComoHHMM(minutosNormales);
    }

    /**
     * Retorna los minutos extras netos a liquidar formateados como HH:MM.
     */
    public String getExtrasFormatted() {
        return TiempoUtils.formatearMinutosComoHHMM(minutosExtras);
    }

    /**
     * Retorna los minutos especiales netos a liquidar formateados como HH:MM.
     */
    public String getEspecialesFormatted() {
        return TiempoUtils.formatearMinutosComoHHMM(minutosEspeciales);
    }
}
