package com.sta.biometric.acciones;

import java.io.*;
import java.time.format.*;
import java.util.*;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.*;
import org.apache.poi.xssf.usermodel.*;
import org.openxava.actions.*;
import org.openxava.jpa.*;
import org.openxava.view.*;

import com.sta.biometric.modelo.*;

/**
 * Acción para exportar la colección calculada jornadasDelPeriodo a Excel.
 * 
 * Esta acción se ejecuta como @ListAction de la colección calculada
 * jornadasDelPeriodo
 * que está dentro de la vista SoloJornadas de LiquidacionJornadas.
 * 
 * Implementa IJavaScriptPostAction para ejecutar JavaScript que abre la URL
 * de descarga en una nueva ventana después de la ejecución de la acción.
 * 
 * @author Sistema STARH
 */
public class ExportarJornadasExcelAction extends CollectionBaseAction implements IJavaScriptPostAction {

    private String javaScript = null;

    @Override
    public String getPostJavaScript() {
        return javaScript;
    }

    @Override
    public void execute() throws Exception {
        try {
            // Obtener el ID de la liquidación desde la jerarquía de vistas
            Object liquidacionId = obtenerLiquidacionId();

            if (liquidacionId == null) {
                addError("No se pudo obtener la liquidación para exportar");
                return;
            }

            // Siempre refrescar desde la BD para obtener los datos completos
            // La entidad desde la vista puede tener campos lazy no cargados
            LiquidacionJornadas liquidacion = XPersistence.getManager().find(LiquidacionJornadas.class, liquidacionId);

            if (liquidacion == null) {
                addError("No se encontró la liquidación en la base de datos");
                return;
            }

            // Obtener la colección de jornadas
            List<AuditoriaRegistros> jornadas = liquidacion.getJornadasDelPeriodo();

            if (jornadas == null || jornadas.isEmpty()) {
                addError("No hay jornadas para exportar en este período");
                return;
            }

            // Crear el archivo Excel
            XSSFWorkbook workbook = crearExcel(jornadas, liquidacion);

            // Generar nombre de archivo
            String nombreArchivo = generarNombreArchivo(liquidacion);

            // Guardar en sesión
            guardarEnSesion(workbook, nombreArchivo);

            // JavaScript para abrir la URL de descarga en nueva ventana
            String contextPath = getRequest().getContextPath();
            javaScript = "window.open('" + contextPath + "/downloadExcel', '_blank');";

            addMessage("Exportando " + jornadas.size() + " jornadas a Excel...");

        } catch (Exception e) {
            addError("Error al generar el archivo Excel: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * Obtiene el ID de la LiquidacionJornadas navegando la jerarquía de vistas.
     * 
     * @return El ID de la liquidación o null si no se puede obtener
     */
    private Object obtenerLiquidacionId() {
        // Método 1: Desde la vista padre de la colección (obtener entity y luego ID)
        try {
            View collectionView = getCollectionElementView();
            if (collectionView != null) {
                View parentView = collectionView.getParent();
                if (parentView != null) {
                    Object entity = parentView.getEntity();
                    if (entity instanceof LiquidacionJornadas) {
                        return ((LiquidacionJornadas) entity).getId();
                    }
                }
            }
        } catch (Exception e) {
            // Continuar con siguiente método
        }

        // Método 2: Desde keyValues de la vista padre
        try {
            View collectionView = getCollectionElementView();
            if (collectionView != null) {
                View parentView = collectionView.getParent();
                if (parentView != null) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> keyValues = parentView.getKeyValues();
                    if (keyValues != null && keyValues.containsKey("id")) {
                        return keyValues.get("id");
                    }
                }
            }
        } catch (Exception e) {
            // Continuar
        }

        // Método 3: Desde getView() directamente
        try {
            Object entity = getView().getEntity();
            if (entity instanceof LiquidacionJornadas) {
                return ((LiquidacionJornadas) entity).getId();
            }
        } catch (Exception e) {
            // Continuar
        }

        // Método 4: Desde getPreviousView()
        try {
            Object entity = getPreviousView().getEntity();
            if (entity instanceof LiquidacionJornadas) {
                return ((LiquidacionJornadas) entity).getId();
            }
        } catch (Exception e) {
            // Continuar
        }

        return null;
    }

    /**
     * Guarda el archivo Excel en la sesión HTTP para que el servlet lo descargue.
     */
    private void guardarEnSesion(XSSFWorkbook workbook, String nombreArchivo) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        workbook.write(baos);
        workbook.close();

        getRequest().getSession().setAttribute("EXCEL_FILE_NAME", nombreArchivo);
        getRequest().getSession().setAttribute("EXCEL_FILE_CONTENT", baos.toByteArray());
        getRequest().getSession().setAttribute("EXCEL_FILE_TYPE",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }

    /**
     * Crea el workbook de Excel con los datos.
     */
    /**
     * Crea el workbook de Excel con los datos.
     */
    private XSSFWorkbook crearExcel(List<AuditoriaRegistros> jornadas, LiquidacionJornadas liquidacion) {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("Jornadas del Período");

        // Estilos
        CellStyle headerStyle = crearEstiloEncabezado(workbook);
        CellStyle dateStyle = crearEstiloFecha(workbook);
        CellStyle normalStyle = crearEstiloNormal(workbook);
        CellStyle moneyStyle = crearEstiloMoneda(workbook);
        CellStyle boldStyle = crearEstiloNegrita(workbook);
        CellStyle titleStyle = crearEstiloTitulo(workbook);
        CellStyle moneyBoldStyle = crearEstiloMonedaNegrita(workbook);

        int currentRow = 0;

        // 1. Encabezado principal
        currentRow = crearEncabezadoPrincipal(sheet, liquidacion, titleStyle, currentRow);

        // 2. Tabla de Resumen
        currentRow = crearTablaResumen(sheet, liquidacion, headerStyle, normalStyle, moneyStyle, boldStyle, moneyBoldStyle,
                currentRow);

        // Espacio antes del detalle
        currentRow++;

        // 3. Detalle de jornadas
        crearEncabezadoDetalle(sheet, headerStyle, currentRow++);

        for (AuditoriaRegistros jornada : jornadas) {
            Row row = sheet.createRow(currentRow++);
            llenarFila(row, jornada, dateStyle, normalStyle, moneyStyle);
        }

        ajustarAnchoColumnas(sheet);

        return workbook;
    }



    private int crearEncabezadoPrincipal(XSSFSheet sheet, LiquidacionJornadas liquidacion, CellStyle titleStyle,
            int startRow) {
        Row row = sheet.createRow(startRow);
        Cell cell = row.createCell(0);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String desde = liquidacion.getPeriodoDesde() != null ? liquidacion.getPeriodoDesde().format(formatter) : "-";
        String hasta = liquidacion.getPeriodoHasta() != null ? liquidacion.getPeriodoHasta().format(formatter) : "-";
        String empleado = liquidacion.getEmpleado() != null ? liquidacion.getEmpleado().getNombreCompleto() : "-";

        cell.setCellValue(
                "Liquidación de Jornada para el período desde " + desde + " al " + hasta + " de: " + empleado);
        cell.setCellStyle(titleStyle);

        // Fusionar celdas A1:K3 (aprox) para el título
        sheet.addMergedRegion(new CellRangeAddress(startRow, startRow + 2, 0, 10));

        return startRow + 4; // Dejar espacio despues del titulo
    }

    private void crearCelda(Row row, int col, Object valor, CellStyle style) {
        Cell cell = row.createCell(col);
        if (valor instanceof String)
            cell.setCellValue((String) valor);
        else if (valor instanceof Double)
            cell.setCellValue((Double) valor);
        else
            cell.setCellValue(valor != null ? valor.toString() : "");
        cell.setCellStyle(style);
    }

    private int crearTablaResumen(XSSFSheet sheet, LiquidacionJornadas liquidacion, CellStyle headerStyle, CellStyle normalStyle,
            CellStyle moneyStyle, CellStyle boldStyle, CellStyle moneyBoldStyle, int startRow) {
        int currentRow = startRow;

        // Encabezados tabla resumen
        Row headerRow = sheet.createRow(currentRow++);
        crearCelda(headerRow, 0, "Concepto", headerStyle);
        crearCelda(headerRow, 1, "Horas A Pagar", headerStyle);
        crearCelda(headerRow, 2, "Valor Hora", headerStyle);
        crearCelda(headerRow, 3, "Total $", headerStyle);

        // Fila Normales
        Row rowNormal = sheet.createRow(currentRow++);
        crearCelda(rowNormal, 0, "Horas Normales", boldStyle);
        crearCelda(rowNormal, 1, liquidacion.getHorasNormalesFormatted() != null ? liquidacion.getHorasNormalesFormatted() : "00:00", normalStyle);
        crearCelda(rowNormal, 2, liquidacion.getValorHoraSnapshot() != null ? liquidacion.getValorHoraSnapshot().doubleValue() : 0.0, moneyStyle);
        crearCelda(rowNormal, 3, liquidacion.getMontoTotalNormales() != null ? liquidacion.getMontoTotalNormales().doubleValue() : 0.0, moneyStyle);

        // Fila Extras
        Row rowExtra = sheet.createRow(currentRow++);
        crearCelda(rowExtra, 0, "Horas Extras", boldStyle);
        crearCelda(rowExtra, 1, liquidacion.getHorasExtrasFormatted() != null ? liquidacion.getHorasExtrasFormatted() : "00:00", normalStyle);
        crearCelda(rowExtra, 2, liquidacion.getValorHoraExtraSnapshot() != null ? liquidacion.getValorHoraExtraSnapshot().doubleValue() : 0.0, moneyStyle);
        crearCelda(rowExtra, 3, liquidacion.getMontoTotalExtras() != null ? liquidacion.getMontoTotalExtras().doubleValue() : 0.0, moneyStyle);

        // Fila Especiales
        Row rowEsp = sheet.createRow(currentRow++);
        crearCelda(rowEsp, 0, "Horas Especiales", boldStyle);
        crearCelda(rowEsp, 1, liquidacion.getHorasEspecialesFormatted() != null ? liquidacion.getHorasEspecialesFormatted() : "00:00", normalStyle);
        crearCelda(rowEsp, 2, liquidacion.getValorHoraEspecialSnapshot() != null ? liquidacion.getValorHoraEspecialSnapshot().doubleValue() : 0.0, moneyStyle);
        crearCelda(rowEsp, 3, liquidacion.getMontoTotalEspeciales() != null ? liquidacion.getMontoTotalEspeciales().doubleValue() : 0.0, moneyStyle);

        // Fila Presentismo
        com.sta.biometric.auxiliares.ResultadoPresentismoPeriodo resPresentismo = liquidacion.getResultadoPresentismo();
        Row rowPres = sheet.createRow(currentRow++);
        crearCelda(rowPres, 0, "Control Presentismo", boldStyle);
        crearCelda(rowPres, 1, resPresentismo != null ? resPresentismo.getEstadoFormatted() : "-", normalStyle);
        crearCelda(rowPres, 2, "Detalle", boldStyle);
        crearCelda(rowPres, 3, resPresentismo != null ? resPresentismo.getMotivoDetalladoPerdida() : "-", normalStyle);

        // Fila Total General
        Row rowTotal = sheet.createRow(currentRow++);
        crearCelda(rowTotal, 0, "TOTAL GENERAL", boldStyle);
        crearCelda(rowTotal, 1, "", boldStyle); // Vacío
        crearCelda(rowTotal, 2, "", boldStyle); // Vacío
        crearCelda(rowTotal, 3, liquidacion.getMontoGranTotal() != null ? liquidacion.getMontoGranTotal().doubleValue() : 0.0, moneyBoldStyle);

        return currentRow;
    }

    private void crearEncabezadoDetalle(XSSFSheet sheet, CellStyle headerStyle, int rowNum) {
        Row headerRow = sheet.createRow(rowNum);
        String[] columnas = {
                "Fecha", "Turno Planificado", "Horario", "Estado Jornada",
                "A Liq. Norm.", "A Liq. Ext.", "A Liq. Esp.", "Banco Horas"
        };

        for (int i = 0; i < columnas.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(columnas[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    private void llenarFila(Row row, AuditoriaRegistros jornada, CellStyle dateStyle, CellStyle normalStyle,
            CellStyle moneyStyle) {
        int colNum = 0;

        // Columna A: Fecha
        Cell cell = row.createCell(colNum++);
        if (jornada.getFecha() != null) {
            cell.setCellValue(jornada.getFecha().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        }
        cell.setCellStyle(dateStyle);

        // Columna B: Turno Planificado
        cell = row.createCell(colNum++);
        cell.setCellValue(jornada.getTurnoPlanificado() != null ? jornada.getTurnoPlanificado() : "");
        cell.setCellStyle(normalStyle);

        // Columna C: Horario
        cell = row.createCell(colNum++);
        cell.setCellValue(jornada.getHorario() != null ? jornada.getHorario() : "");
        cell.setCellStyle(normalStyle);

        // Columna D: Estado Jornada (Evaluación pura del dominio)
        cell = row.createCell(colNum++);
        cell.setCellValue(jornada.getEvaluacion() != null ? jornada.getEvaluacion().toString() : "");
        cell.setCellStyle(normalStyle);

        // Columna E: A Liq. Norm. (Horas Netas Normales a pagar)
        cell = row.createCell(colNum++);
        cell.setCellValue(jornada.getHorasALiquidarNormales() != null ? jornada.getHorasALiquidarNormales() : "00:00");
        cell.setCellStyle(normalStyle);

        // Columna F: A Liq. Ext. (Horas Netas Extras a pagar)
        cell = row.createCell(colNum++);
        cell.setCellValue(jornada.getHorasALiquidarExtras() != null ? jornada.getHorasALiquidarExtras() : "00:00");
        cell.setCellStyle(normalStyle);

        // Columna G: A Liq. Esp. (Horas Netas Especiales a pagar)
        cell = row.createCell(colNum++);
        cell.setCellValue(jornada.getHorasALiquidarEspeciales() != null ? jornada.getHorasALiquidarEspeciales() : "00:00");
        cell.setCellStyle(normalStyle);

        // Columna H: Banco Horas
        cell = row.createCell(colNum++);
        cell.setCellValue(jornada.getBancoHorasDisplay() != null ? jornada.getBancoHorasDisplay() : "-");
        cell.setCellStyle(normalStyle);
    }

    private CellStyle crearEstiloEncabezado(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle crearEstiloFecha(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle crearEstiloNormal(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle crearEstiloMoneda(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.RIGHT);
        // Formato de moneda con 2 decimales
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("#,##0.00"));
        return style;
    }

    private CellStyle crearEstiloNegrita(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    private CellStyle crearEstiloTitulo(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle crearEstiloMonedaNegrita(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.RIGHT);
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("$ #,##0.00"));
        return style;
    }

    private void ajustarAnchoColumnas(XSSFSheet sheet) {
        // 11 columnas: Empleado, Fecha, Turno, Horario, HorasTurno, $Hs, HorasExtras,
        // $HsExtras, HorasEsp, $HsEsp, Estado
        for (int i = 0; i < 11; i++) {
            sheet.autoSizeColumn(i);
            sheet.setColumnWidth(i, sheet.getColumnWidth(i) + 1000);
        }
    }

    private String generarNombreArchivo(LiquidacionJornadas liquidacion) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String empleado = liquidacion.getEmpleado() != null
                ? liquidacion.getEmpleado().getNombreCompleto().replaceAll("[^a-zA-Z0-9]", "_")
                : "SinEmpleado";
        String desde = liquidacion.getPeriodoDesde() != null
                ? liquidacion.getPeriodoDesde().format(formatter)
                : "SinFecha";
        String hasta = liquidacion.getPeriodoHasta() != null
                ? liquidacion.getPeriodoHasta().format(formatter)
                : "SinFecha";

        return String.format("Jornadas_%s_%s_al_%s.xlsx", empleado, desde, hasta);
    }
}
