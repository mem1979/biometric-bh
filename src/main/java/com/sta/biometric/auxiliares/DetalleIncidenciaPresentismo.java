package com.sta.biometric.auxiliares;

import java.time.LocalDate;
import com.sta.biometric.enums.TipoIncidenciaPresentismo;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * DTO puro e inmutable que transporta el detalle de una incidencia individual de presentismo.
 * Sin dependencias de JPA ni OpenXava.
 */
@Getter
@AllArgsConstructor
public class DetalleIncidenciaPresentismo {

    private final LocalDate fecha;
    private final TipoIncidenciaPresentismo tipoIncidencia;
    private final String descripcion;
    private final int minutosInvolucrados;

    public String getMinutosFormatted() {
        if (minutosInvolucrados <= 0) return "-";
        return String.format("%02d:%02d hs", minutosInvolucrados / 60, minutosInvolucrados % 60);
    }
}
