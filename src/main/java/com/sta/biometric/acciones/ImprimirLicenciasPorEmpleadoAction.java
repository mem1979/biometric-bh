package com.sta.biometric.acciones;

import java.time.*;
import java.util.*;
import java.util.stream.*;

import org.openxava.actions.*;
import org.openxava.jpa.*;

import com.sta.biometric.auxiliares.*;
import com.sta.biometric.enums.*;
import com.sta.biometric.modelo.*;
import com.sta.biometric.servicios.*;

import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.*;

/**
 * Acción para generar el informe de licencias agrupadas por empleado.
 * 
 * <p>Consulta todas las licencias de los últimos 3 años (año actual y dos anteriores),
 * las ordena por empleado y tipo de licencia, y genera un PDF con agrupación
 * y totales por año devengado y días restantes en tiempo real.</p>
 */
public class ImprimirLicenciasPorEmpleadoAction extends JasperReportBaseAction {

    @Override
    protected JRDataSource getDataSource() throws Exception {
        int anioActual = LocalDate.now().getYear();
        int anioDesde = anioActual - 2;

        // Consultar licencias de los últimos 3 años con sus relaciones cargadas
        List<Licencia> licencias = XPersistence.getManager()
                .createQuery(
                        "SELECT l FROM Licencia l " +
                        "LEFT JOIN FETCH l.empleado e " +
                        "LEFT JOIN FETCH e.sucursal " +
                        "WHERE EXTRACT(YEAR FROM l.fechaInicio) >= :anioDesde " +
                        "  AND EXTRACT(YEAR FROM l.fechaInicio) <= :anioActual " +
                        "ORDER BY e.nombreCompleto ASC, l.tipo ASC, l.fechaInicio ASC",
                        Licencia.class)
                .setParameter("anioDesde", anioDesde)
                .setParameter("anioActual", anioActual)
                .getResultList();

        // Agrupar y resumir las licencias por (empleadoNombre, tipoLicencia, anioDevengamiento)
        Map<GrupoClave, List<Licencia>> grupos = licencias.stream()
                .collect(Collectors.groupingBy(lic -> {
                    int anioDev = lic.getPeriodoDevengado() != null 
                            ? lic.getPeriodoDevengado() 
                            : VacacionesPeriodoService.getInstance().calcularPeriodoDevengado(lic.getEmpleado(), lic.getTipo(), lic.getFechaInicio());
                    return new GrupoClave(lic.getNombreEmpleado(), lic.getTipo(), anioDev);
                }));

        // Convertir cada grupo en una fila para JasperReports, filtrando por el rango de años solicitado
        List<Map<String, Object>> datos = grupos.entrySet().stream()
                .filter(entry -> entry.getKey().anioDevengamiento >= anioDesde && entry.getKey().anioDevengamiento <= anioActual)
                .map(entry -> {
                    GrupoClave clave = entry.getKey();
                    List<Licencia> listaGrupo = entry.getValue();
                    Personal empleado = listaGrupo.isEmpty() ? null : listaGrupo.get(0).getEmpleado();

                    int totalDiasUso = listaGrupo.stream()
                            .mapToInt(l -> l.getDias() != null ? l.getDias() : 0)
                            .sum();

                    // Calcular saldo restante dinámicamente en tiempo real
                    int finalDiasRestantes = 0;
                    if (empleado != null) {
                        finalDiasRestantes = calcularDiasRestantesDinamico(empleado, clave.tipoLicencia, clave.anioDevengamiento);
                    }

                    Map<String, Object> fila = new HashMap<>();
                    fila.put("empleadoNombre", clave.empleadoNombre);
                    fila.put("tipoLicencia", clave.tipoLicencia.getDescripcion());
                    fila.put("anioDevengamiento", clave.anioDevengamiento);
                    fila.put("diasUso", totalDiasUso);
                    fila.put("diasRestantes", finalDiasRestantes);
                    fila.put("cantidadLicencias", listaGrupo.size());
                    return fila;
                })
                // Ordenar los resultados para que JasperReports pueda agruparlos contiguamente
                .sorted(Comparator.comparing((Map<String, Object> m) -> (String) m.get("empleadoNombre"))
                        .thenComparing(m -> (String) m.get("tipoLicencia"))
                        .thenComparing(m -> (Integer) m.get("anioDevengamiento")))
                .collect(Collectors.toList());

        return new JRBeanCollectionDataSource(datos);
    }

    /**
     * Calcula los días restantes de una licencia en tiempo real para un empleado, tipo y período.
     * Respeta la antigüedad legal y el cómputo del modo en que se cargó la licencia (Ej: escalado corrido a hábiles).
     */
    private int calcularDiasRestantesDinamico(Personal empleado, TipoLicenciaAR tipo, int periodo) {
        if (empleado == null || tipo == null) return 0;
        
        VacacionesPeriodoService vps = VacacionesPeriodoService.getInstance();
        int diasPorAnio = vps.calcularDiasMaximosPorTipo(empleado, tipo, periodo);
        
        if (tipo == TipoLicenciaAR.VACACIONES) {
            // Resolver si el período computa en hábiles (si tiene alguna licencia en CORRIDOS A HABILES)
            boolean enHabiles = esPeriodoEnHabiles(empleado, periodo);
            if (enHabiles) {
                diasPorAnio = (diasPorAnio * 5) / 7;
            }
        }
        
        int diasTomados = vps.obtenerDiasTomados(empleado, tipo, periodo, null);
        return Math.max(0, diasPorAnio - diasTomados);
    }

    /**
     * Determina si para un período y empleado específico las vacaciones se computan en días hábiles.
     */
    private boolean esPeriodoEnHabiles(Personal empleado, int periodo) {
        Long count = XPersistence.getManager()
                .createQuery(
                        "SELECT COUNT(l) FROM Licencia l " +
                        "WHERE l.empleado = :empleado " +
                        "  AND l.tipo = :tipo " +
                        "  AND l.periodoDevengado = :periodo " +
                        "  AND l.modoComputo = :modo",
                        Long.class)
                .setParameter("empleado", empleado)
                .setParameter("tipo", TipoLicenciaAR.VACACIONES)
                .setParameter("periodo", periodo)
                .setParameter("modo", ModoComputoLicencia.DIAS_CORRIDOS_HABILES)
                .getSingleResult();
        return count > 0;
    }

    @Override
    protected String getJRXML() throws Exception {
        return "LicenciasPorEmpleado.jrxml";
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    protected Map getParameters() throws Exception {
        Map params = new HashMap();
        int anioActual = LocalDate.now().getYear();
        int anioDesde = anioActual - 2;
        
        params.put("anioDesde", anioDesde);
        params.put("anioActual", anioActual);
        params.put("fechaGeneracion",
                LocalDateTime.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        params.put("tituloInforme", "INFORME DE LICENCIAS POR EMPLEADOS - " + anioDesde + " / " + (anioDesde + 1) + " / " + anioActual);
        return params;
    }

    private static class GrupoClave {
        final String empleadoNombre;
        final TipoLicenciaAR tipoLicencia;
        final int anioDevengamiento;

        GrupoClave(String empleadoNombre, TipoLicenciaAR tipoLicencia, int anioDevengamiento) {
            this.empleadoNombre = empleadoNombre;
            this.tipoLicencia = tipoLicencia;
            this.anioDevengamiento = anioDevengamiento;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            GrupoClave that = (GrupoClave) o;
            return anioDevengamiento == that.anioDevengamiento &&
                    tipoLicencia == that.tipoLicencia &&
                    Objects.equals(empleadoNombre, that.empleadoNombre);
        }

        @Override
        public int hashCode() {
            return Objects.hash(empleadoNombre, tipoLicencia, anioDevengamiento);
        }
    }
}
