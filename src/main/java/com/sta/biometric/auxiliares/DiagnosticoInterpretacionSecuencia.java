package com.sta.biometric.auxiliares;

import com.sta.biometric.enums.EvaluacionJornada;
import java.time.LocalTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * DTO inmutable y liviano que expone una vista resumida y descriptiva de cómo
 * la entidad {@code AuditoriaRegistros} ha interpretado la secuencia de fichadas de una jornada.
 * 
 * <p>
 * No contiene referencias a entidades JPA ni listas complejas de objetos de dominio.
 * Solo almacena métricas escalares y observaciones de diagnóstico en texto plano.
 * </p>
 * 
 * @author STARH Biometric Team
 * @since 1.0
 */
@Getter
@Builder
@ToString
public class DiagnosticoInterpretacionSecuencia {

    private final LocalTime primeraFichadaConsiderada;
    private final LocalTime ultimaFichadaConsiderada;
    private final int cantidadTotalFichadas;
    private final int cantidadPausasDetectadas;
    private final int minutosTrabajadosCalculados;
    private final EvaluacionJornada evaluacionAsignada;
    private final List<String> observacionesDescriptivas;
}
