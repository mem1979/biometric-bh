package com.sta.biometric.acciones;

import java.time.*;
import java.util.*;
import java.util.stream.*;

import org.openxava.actions.*;
import org.openxava.jpa.*;

import com.sta.biometric.auxiliares.*;

import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.*;

/**
 * Acción para generar el informe de licencias agrupadas por sucursal y mes.
 * 
 * <p>Consulta todas las licencias del año en curso, las ordena por
 * sucursal y mes de inicio, y genera un PDF con doble agrupación.</p>
 */
public class ImprimirLicenciasPorSucursalAction extends JasperReportBaseAction {

    @Override
    protected JRDataSource getDataSource() throws Exception {
        int anioActual = LocalDate.now().getYear();

        // Consultar licencias del año actual con sus relaciones cargadas
        List<Licencia> licencias = XPersistence.getManager()
                .createQuery(
                        "SELECT l FROM Licencia l " +
                        "LEFT JOIN FETCH l.empleado e " +
                        "LEFT JOIN FETCH e.sucursal " +
                        "WHERE EXTRACT(YEAR FROM l.fechaInicio) = :anio " +
                        "ORDER BY e.sucursal.nombre ASC, l.fechaInicio ASC, e.nombreCompleto ASC",
                        Licencia.class)
                .setParameter("anio", anioActual)
                .getResultList();

        // Convertir a mapas para JasperReports
        List<Map<String, Object>> datos = licencias.stream()
                .map(this::licenciaAMapa)
                .collect(Collectors.toList());

        return new JRBeanCollectionDataSource(datos);
    }

    @Override
    protected String getJRXML() throws Exception {
        return "LicenciasPorSucursal.jrxml";
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    protected Map getParameters() throws Exception {
        Map params = new HashMap();
        int anioActual = LocalDate.now().getYear();
        params.put("anioInforme", anioActual);
        params.put("fechaGeneracion",
                LocalDateTime.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        params.put("tituloInforme", "INFORME DE LICENCIAS POR SUCURSAL - " + anioActual);
        return params;
    }

    /**
     * Convierte una entidad Licencia en un mapa de propiedades para el datasource.
     */
    private Map<String, Object> licenciaAMapa(Licencia lic) {
        Map<String, Object> fila = new HashMap<>();
        fila.put("sucursal", lic.getNombreSucursal());
        fila.put("mesAnio", lic.getMesAnio());
        fila.put("mesNombre", lic.getMesNombre());
        fila.put("empleadoNombre", lic.getNombreEmpleado());
        fila.put("tipoLicencia", lic.getTipoDescripcion());
        fila.put("fechaInicio", lic.getFechaInicio() != null
                ? java.sql.Date.valueOf(lic.getFechaInicio()) : null);
        fila.put("fechaFin", lic.getFechaFin() != null
                ? java.sql.Date.valueOf(lic.getFechaFin()) : null);
        fila.put("dias", lic.getDias() != null ? lic.getDias() : 0);
        fila.put("justificado", lic.isJustificado() ? "Sí" : "No");
        fila.put("conGoce", lic.isConGoce() ? "Sí" : "No");
        return fila;
    }
}
