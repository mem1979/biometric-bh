package com.sta.biometric.servicios;

import com.sta.biometric.auxiliares.DiagnosticoInterpretacionSecuencia;
import com.sta.biometric.enums.TipoMovimiento;
import com.sta.biometric.modelo.AuditoriaRegistros;
import com.sta.biometric.modelo.ColeccionRegistros;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Servicio pasivo de observación de secuencias de fichadas.
 * 
 * <p>
 * Su única responsabilidad es inspeccionar una instancia de {@link AuditoriaRegistros}
 * y construir una representación descriptiva en formato {@link DiagnosticoInterpretacionSecuencia}.
 * </p>
 * 
 * <p>
 * <b>Principios de diseño:</b>
 * </p>
 * <ul>
 * <li>100% Pasivo: No modifica estados, no persiste datos y no altera colecciones.</li>
 * <li>Sin reinterpretación: Consume los resultados calculados por {@code AuditoriaRegistros}.</li>
 * <li>Desacoplado: No posee integración directa con controladores o interfaz gráfica.</li>
 * </ul>
 * 
 * @author STARH Biometric Team
 * @since 1.0
 */
public class ObservadorSecuenciaFichadasService {

    /**
     * Analiza una jornada y construye el diagnóstico descriptivo de su interpretación actual.
     * 
     * @param auditoria Entidad consolidada de la jornada
     * @return DTO inmutable con las métricas y descripciones observadas
     */
    public static DiagnosticoInterpretacionSecuencia diagnosticar(AuditoriaRegistros auditoria) {
        if (auditoria == null) {
            return DiagnosticoInterpretacionSecuencia.builder()
                    .cantidadTotalFichadas(0)
                    .cantidadPausasDetectadas(0)
                    .minutosTrabajadosCalculados(0)
                    .observacionesDescriptivas(Collections.singletonList("Jornada nula o sin datos de auditoría"))
                    .build();
        }

        List<ColeccionRegistros> registros = auditoria.getRegistros();
        int totalFichadas = (registros != null) ? registros.size() : 0;

        LocalTime primera = null;
        LocalTime ultima = null;
        int pausasCount = 0;

        if (totalFichadas > 0) {
            primera = registros.get(0).getHora();
            ultima = registros.get(totalFichadas - 1).getHora();

            for (ColeccionRegistros r : registros) {
                if (r.getTipoMovimiento() == TipoMovimiento.PAUSA_INICIO
                        || r.getTipoMovimiento() == TipoMovimiento.PAUSA_FIN) {
                    pausasCount++;
                }
            }
        }

        List<String> obs = new ArrayList<>();
        obs.add("Cantidad total de fichadas registradas: " + totalFichadas);

        if (totalFichadas > 0) {
            obs.add("Intervalo cronológico considerado como jornada: " + primera + " -> " + ultima);

            if (totalFichadas > 2) {
                obs.add("Fichadas intermedias sin efecto directo en el cálculo del total trabajados: " + (totalFichadas - 2));
            } else if (totalFichadas == 1) {
                obs.add("Jornada con fichada única de extremo. Requiere evaluación o cierre de ciclo.");
            }
        } else {
            obs.add("Jornada sin fichadas registradas.");
        }

        if (pausasCount > 0) {
            obs.add("Marcaciones de pausa registradas: " + pausasCount + " (informativas; no restan tiempo del total)");
        }

        if (auditoria.getEvaluacion() != null) {
            obs.add("Estado de evaluación asignado por AuditoriaRegistros: " + auditoria.getEvaluacion());
        }

        if (auditoria.getNota() != null && !auditoria.getNota().trim().isEmpty()) {
            obs.add("Nota textual del sistema: " + auditoria.getNota().trim());
        }

        return DiagnosticoInterpretacionSecuencia.builder()
                .primeraFichadaConsiderada(primera)
                .ultimaFichadaConsiderada(ultima)
                .cantidadTotalFichadas(totalFichadas)
                .cantidadPausasDetectadas(pausasCount)
                .minutosTrabajadosCalculados(auditoria.getMinutosTrabajados())
                .evaluacionAsignada(auditoria.getEvaluacion())
                .observacionesDescriptivas(Collections.unmodifiableList(obs))
                .build();
    }
}
