package com.sta.biometric.acciones;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.openxava.actions.*;
import org.openxava.jpa.*;
import org.openxava.model.*;
import com.sta.biometric.modelo.*;

/**
 * Accion para exportar la lista de personal activo en formato CSV
 * listo para importar en las terminales con la utilidad configure_device.ps1.
 *
 * @author Sistema STARH
 */
public class ExportarPersonalCsvAction extends TabBaseAction implements IJavaScriptPostAction {

    private String javaScript = null;

    @Override
    public String getPostJavaScript() {
        return javaScript;
    }

    @Override
    public void execute() throws Exception {
        try {
            Map<String, Object>[] clavesSeleccionadas = getSelectedKeys();
            List<Personal> personalAExportar = new ArrayList<>();

            if (clavesSeleccionadas == null || clavesSeleccionadas.length == 0) {
                // Exportar todo el personal activo y no eliminado
                personalAExportar = XPersistence.getManager().createQuery(
                    "select p from Personal p where p.eliminado = false and p.activo = true and p.terminalUserId is not null and p.terminalUserId <> ''", 
                    Personal.class).getResultList();
            } else {
                // Obtener sucursales de los dispositivos seleccionados
                Set<String> sucursalIds = new HashSet<>();
                for (Map<String, Object> clave : clavesSeleccionadas) {
                    DispositivoBiometrico disp = (DispositivoBiometrico) MapFacade.findEntity(getModelName(), clave);
                    if (disp != null && disp.getSucursal() != null) {
                        sucursalIds.add(disp.getSucursal().getId());
                    }
                }

                if (sucursalIds.isEmpty()) {
                    addError("Los dispositivos seleccionados no tienen una sucursal/sector asignada");
                    return;
                }

                // Fetch employees for these sucursales
                personalAExportar = XPersistence.getManager().createQuery(
                    "select p from Personal p where p.eliminado = false and p.activo = true and p.terminalUserId is not null and p.terminalUserId <> '' and p.sucursal.id in :sucursalIds", 
                    Personal.class)
                    .setParameter("sucursalIds", sucursalIds)
                    .getResultList();
            }

            if (personalAExportar.isEmpty()) {
                addWarning("No se encontro personal activo con ID de Terminal configurado para exportar");
                return;
            }

            // Generar contenido CSV
            StringBuilder csv = new StringBuilder();
            // Encabezado
            csv.append("employeeNo;nombre;apellido;sector;genero;pin\r\n");

            for (Personal p : personalAExportar) {
                String employeeNo = p.getTerminalUserId() != null ? p.getTerminalUserId().replace(";", "") : "";
                String nombre = p.getNombres() != null ? p.getNombres().replace(";", "") : "";
                String apellido = p.getApellido() != null ? p.getApellido().replace(";", "") : "";
                String sector = p.getSucursal() != null ? p.getSucursal().getNombre().replace(";", "") : "";
                String genero = determinarGenero(p);
                String pin = p.getContrasena() != null ? p.getContrasena().replace(";", "") : "";

                csv.append(employeeNo).append(";")
                   .append(nombre).append(";")
                   .append(apellido).append(";")
                   .append(sector).append(";")
                   .append(genero).append(";")
                   .append(pin).append("\r\n");
            }

            // Convertir a bytes usando ISO-8859-1 (o Windows-1252)
            byte[] csvBytes = csv.toString().getBytes(StandardCharsets.ISO_8859_1);

            // Guardar en sesion
            getRequest().getSession().setAttribute("EXCEL_FILE_NAME", "usuarios_importacion.csv");
            getRequest().getSession().setAttribute("EXCEL_FILE_CONTENT", csvBytes);
            getRequest().getSession().setAttribute("EXCEL_FILE_TYPE", "text/csv");

            // JavaScript para abrir descarga
            String contextPath = getRequest().getContextPath();
            javaScript = "window.open('" + contextPath + "/downloadExcel', '_blank');";

            addMessage("Exportados " + personalAExportar.size() + " usuarios/as a CSV para el fichador");

        } catch (Exception e) {
            addError("Error al generar el archivo de exportacion: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    private String determinarGenero(Personal p) {
        String cuil = p.getCuil();
        if (cuil != null) {
            String clean = cuil.replaceAll("[^0-9]", "");
            if (clean.startsWith("27")) {
                return "femenino";
            } else if (clean.startsWith("20")) {
                return "masculino";
            }
        }
        return "male"; // fallback
    }
}
