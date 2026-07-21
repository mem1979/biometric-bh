package com.sta.biometric.servicios;

import java.time.format.*;
import java.util.*;
import java.util.stream.*;

import com.sta.biometric.dto.*;
import com.sta.biometric.dto.DatosDesempenoDTO.NotaResumenDTO;
import com.sta.biometric.modelo.*;

/**
 * Servicio para generar análisis integral de desempeño anual.
 * 
 * <p>
 * <strong>PRODUCCIÓN (128MB heap):</strong> Usa exclusivamente el sistema
 * experto local basado en reglas. La integración con Google Gemini AI fue
 * removida para eliminar ~15-25 MB de consumo de heap (SDK + Jackson +
 * dependencias transitivas + objetos HTTP por llamada).
 * </p>
 * 
 * <p>
 * El sistema experto local genera análisis de calidad comparable para el
 * caso de uso biométrico, sin dependencias externas ni latencia de red.
 * </p>
 */
public class AnalisisDesempenoService {

    /**
     * Realiza el análisis integral del desempeño anual usando el sistema
     * experto local (basado en reglas).
     *
     * @param empleado Empleado a analizar
     * @param anio     Año del informe
     * @param datos    Datos de desempeño calculados
     * @return DTO con las 4 secciones del análisis
     */
    public AnalisisIntegralDTO realizarAnalisisIntegral(Personal empleado, int anio, DatosDesempenoDTO datos) {
        // Asegurar que tenemos el nombre del empleado en los datos
        if (datos.getNombreEmpleado() == null && empleado != null) {
            datos.setNombreEmpleado(empleado.getNombreCompleto());
            datos.setPuesto(empleado.getPuesto());
            datos.setAnio(anio);
        }

        // Análisis local (sin dependencias externas)
        AnalisisIntegralDTO resultado = realizarAnalisis(datos);
        resultado.setGeneradoPorIA(false);
        resultado.setMensajeEstado("Análisis generado por sistema experto local");
        return resultado;
    }

    // =================== SISTEMA EXPERTO LOCAL ===================

    /**
     * Genera un análisis completo basado en reglas y umbrales configurables.
     */
    private AnalisisIntegralDTO realizarAnalisis(DatosDesempenoDTO datos) {
        AnalisisIntegralDTO dto = new AnalisisIntegralDTO();
        dto.setGeneradoPorIA(false);

        // 1. RESUMEN EJECUTIVO
        dto.setResumenEjecutivo(generarResumenEjecutivo(datos));

        // 2. FORTALEZAS
        dto.setFortalezas(generarFortalezas(datos));

        // 3. DEBILIDADES
        dto.setDebilidades(generarDebilidades(datos));

        // 4. RECOMENDACIONES
        dto.setRecomendaciones(generarRecomendaciones(datos));

        return dto;
    }

    private String generarResumenEjecutivo(DatosDesempenoDTO datos) {
        StringBuilder sb = new StringBuilder();
        double presentismo = datos.getPorcentajePresentismo();
        int tardanzas = datos.getTotalTardanzas();
        long ausencias = datos.getAusenciasInjustificadas();

        // Primer párrafo: evaluación general
        sb.append("Durante el año ").append(datos.getAnio()).append(", ");
        sb.append(datos.getNombreEmpleado());

        if (presentismo >= 95 && tardanzas < 5 && ausencias < 3) {
            sb.append(" demostró un desempeño EXCELENTE, ")
                    .append("destacándose por su compromiso y responsabilidad. ")
                    .append("Los indicadores de asistencia superan ampliamente los estándares ")
                    .append("establecidos por la organización.");
        } else if (presentismo >= 90 && tardanzas < 15) {
            sb.append(" mantuvo un desempeño BUENO, ")
                    .append("cumpliendo satisfactoriamente con las expectativas. ")
                    .append("Los indicadores de asistencia se encuentran dentro de los ")
                    .append("parámetros aceptables para el puesto.");
        } else if (presentismo >= 80) {
            sb.append(" presentó un desempeño REGULAR que requiere atención. ")
                    .append("Si bien cumple con requerimientos mínimos, existen ")
                    .append("oportunidades de mejora significativas.");
        } else {
            sb.append(" registró un desempeño por DEBAJO DE LAS EXPECTATIVAS. ")
                    .append("Los indicadores de asistencia requieren intervención ")
                    .append("inmediata y seguimiento cercano.");
        }

        // Segundo párrafo: métricas específicas
        sb.append("\n\n");
        sb.append("El análisis cuantitativo revela un índice de presentismo del ")
                .append(String.format("%.1f%%", presentismo));

        if (tardanzas > 0) {
            sb.append(", con ").append(tardanzas).append(" tardanzas registradas");
            if (datos.getMinutosTotalesTardanza() > 0) {
                sb.append(" (").append(datos.getMinutosTotalesTardanza()).append(" minutos acumulados)");
            }
        }

        if (ausencias > 0) {
            sb.append(" y ").append(ausencias).append(" ausencias injustificadas");
        }

        sb.append(". ");

        if (datos.getMinutosExtras() > 0) {
            sb.append("Se destaca la realización de ")
                    .append(datos.getHorasExtrasFormateadas())
                    .append(" horas extras durante el período. ");
        }

        if (datos.getEvaluacionNotas() != null && !datos.getEvaluacionNotas().isEmpty()) {
            sb.append("Las notas de desempeño califican al colaborador como '")
                    .append(datos.getEvaluacionNotas()).append("'.");
        }

        return sb.toString();
    }

    private String generarFortalezas(DatosDesempenoDTO datos) {
        List<String> fortalezas = new ArrayList<>();
        double presentismo = datos.getPorcentajePresentismo();
        int tardanzas = datos.getTotalTardanzas();
        long ausencias = datos.getAusenciasInjustificadas();

        // Evaluar cada métrica
        if (presentismo >= 95) {
            fortalezas.add("Excelente índice de presentismo (" + String.format("%.1f%%", presentismo) + ")");
        } else if (presentismo >= 90) {
            fortalezas.add("Buen nivel de asistencia (" + String.format("%.1f%%", presentismo) + ")");
        }

        if (tardanzas < 5) {
            fortalezas.add("Puntualidad ejemplar con mínimas tardanzas");
        } else if (tardanzas < 10 && datos.getPromedioMinutosTardanza() < 10) {
            fortalezas.add("Tardanzas poco significativas en duración");
        }

        if (ausencias == 0) {
            fortalezas.add("Sin ausencias injustificadas durante todo el año");
        } else if (ausencias < 3) {
            fortalezas.add("Muy bajo nivel de ausentismo injustificado");
        }

        if (datos.getTotalLicencias() < 3) {
            fortalezas.add("Bajo uso de licencias, indicador de buena salud");
        }

        if (datos.getMinutosExtras() > 0) {
            fortalezas.add("Disposición para realizar horas extras cuando es necesario");
        }

        if (datos.getTotalCorrecciones() == 0) {
            fortalezas.add("Registros sin necesidad de correcciones manuales");
        }

        // Evaluación de notas
        if ("Excelente".equals(datos.getEvaluacionNotas()) || "Bueno".equals(datos.getEvaluacionNotas())) {
            fortalezas.add("Evaluación positiva en notas de desempeño");
        }

        // Si no hay fortalezas identificadas
        if (fortalezas.isEmpty()) {
            fortalezas.add("Cumplimiento básico de las responsabilidades asignadas");
        }

        return fortalezas.stream()
                .map(f -> "• " + f)
                .collect(Collectors.joining("\n"));
    }

    private String generarDebilidades(DatosDesempenoDTO datos) {
        List<String> debilidades = new ArrayList<>();
        double presentismo = datos.getPorcentajePresentismo();
        int tardanzas = datos.getTotalTardanzas();
        long ausencias = datos.getAusenciasInjustificadas();

        if (presentismo < 80) {
            debilidades.add("Presentismo deficiente (" + String.format("%.1f%%", presentismo)
                    + "), muy por debajo del esperado");
        } else if (presentismo < 90) {
            debilidades.add("Presentismo por debajo del objetivo (" + String.format("%.1f%%", presentismo) + ")");
        }

        if (tardanzas > 30) {
            debilidades.add("Problemas graves de puntualidad con " + tardanzas + " tardanzas");
        } else if (tardanzas > 15) {
            debilidades.add("Frecuencia de tardanzas moderada-alta (" + tardanzas + ")");
        }

        if (datos.getPromedioMinutosTardanza() > 30) {
            debilidades.add("Duración promedio de tardanzas excesiva (" +
                    String.format("%.0f", datos.getPromedioMinutosTardanza()) + " minutos)");
        }

        if (ausencias > 10) {
            debilidades.add("Alto nivel de ausencias injustificadas (" + ausencias + ")");
        } else if (ausencias > 5) {
            debilidades.add("Ausencias injustificadas requieren seguimiento (" + ausencias + ")");
        }

        if (datos.getDiasLicenciaMedica() > 30) {
            debilidades.add("Alto uso de licencias médicas, posible problema de salud");
        } else if (datos.getDiasLicenciaMedica() > 15) {
            debilidades.add("Uso moderado-alto de licencias médicas");
        }

        if (datos.getTotalCorrecciones() > 10) {
            debilidades.add("Múltiples correcciones en registros (" + datos.getTotalCorrecciones() + ")");
        }

        if ("Requiere Mejora".equals(datos.getEvaluacionNotas())) {
            debilidades.add("Evaluación negativa en notas de desempeño");
        }

        // Si no hay debilidades identificadas
        if (debilidades.isEmpty()) {
            debilidades.add("No se detectaron debilidades significativas");
        }

        return debilidades.stream()
                .map(d -> "• " + d)
                .collect(Collectors.joining("\n"));
    }

    private String generarRecomendaciones(DatosDesempenoDTO datos) {
        List<String> recomendaciones = new ArrayList<>();
        double presentismo = datos.getPorcentajePresentismo();
        int tardanzas = datos.getTotalTardanzas();
        long ausencias = datos.getAusenciasInjustificadas();

        // Recomendaciones de reconocimiento
        if (presentismo >= 98 && tardanzas < 3 && ausencias == 0) {
            recomendaciones.add("Candidato para programa de reconocimiento por excelencia en asistencia");
            recomendaciones.add("Considerar como modelo/mentor para otros colaboradores");
        }

        // Recomendaciones de seguimiento
        if (tardanzas > 15) {
            recomendaciones.add("Evaluar posibilidad de ajuste de horario o turno");
            recomendaciones.add("Implementar seguimiento semanal de puntualidad");
        }

        if (ausencias > 5) {
            recomendaciones.add("Reunión de feedback para identificar causas de ausencias");
            if (ausencias > 10) {
                recomendaciones.add("Considerar inicio de proceso disciplinario progresivo");
            }
        }

        if (datos.getDiasLicenciaMedica() > 20) {
            recomendaciones.add("Sugerir revisión médica preventiva integral");
            recomendaciones.add("Evaluar ergonomía del puesto de trabajo");
        }

        if (presentismo < 85) {
            recomendaciones.add("Establecer metas mensuales de presentismo con seguimiento");
            recomendaciones.add("Implementar incentivos por mejora de asistencia");
        }

        if ("Requiere Mejora".equals(datos.getEvaluacionNotas())) {
            recomendaciones.add("Programar plan de desarrollo profesional individualizado");
        }

        // Si tiene buen desempeño y no hay otras recomendaciones
        if (recomendaciones.isEmpty()) {
            recomendaciones.add("Mantener seguimiento estándar del próximo período");
            recomendaciones.add("Continuar con el nivel de compromiso actual");
        }

        return recomendaciones.stream()
                .map(r -> "• " + r)
                .collect(Collectors.joining("\n"));
    }

    // =================== MÉTODOS DE UTILIDAD ===================

    /**
     * Convierte la colección de notas de desempeño a formato DTO.
     */
    public static List<NotaResumenDTO> convertirNotas(Collection<NotaDesempeno> notas) {
        if (notas == null || notas.isEmpty()) {
            return Collections.emptyList();
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        return notas.stream()
                .map(n -> NotaResumenDTO.builder()
                        .contenido(limpiarHTML(n.getContenido()))
                        .calificacion(n.getCalificacion().name())
                        .autor(n.getAutor())
                        .fecha(n.getFechaHora() != null ? n.getFechaHora().format(fmt) : "")
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Limpia tags HTML básicos del contenido de notas.
     */
    private static String limpiarHTML(String html) {
        if (html == null)
            return "";
        return html.replaceAll("<[^>]*>", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
