package com.sta.biometric.servicios;

import com.sta.biometric.auxiliares.DiagnosticoInterpretacionSecuencia;
import com.sta.biometric.enums.EvaluacionJornada;
import com.sta.biometric.enums.TipoMovimiento;
import com.sta.biometric.modelo.AuditoriaRegistros;
import com.sta.biometric.modelo.ColeccionRegistros;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Suite de pruebas unitarias para {@link ObservadorSecuenciaFichadasService}.
 * 
 * <p>
 * Su propósito fundamental es documentar y verificar experimentalmente
 * cómo el motor de {@link AuditoriaRegistros} interpreta hoy diversas secuencias
 * de fichadas en situaciones reales de producción.
 * </p>
 */
public class ObservadorSecuenciaFichadasServiceTest {

    private final LocalDate FECHA_PRUEBA = LocalDate.of(2026, 8, 1);

    private ColeccionRegistros crearFichada(LocalTime hora, TipoMovimiento tipo) {
        ColeccionRegistros r = new ColeccionRegistros();
        r.setFecha(FECHA_PRUEBA);
        r.setHora(hora);
        r.setTipoMovimiento(tipo);
        return r;
    }

    private AuditoriaRegistros crearAuditoria(List<ColeccionRegistros> registros, EvaluacionJornada evaluacion, int minutosTrabajados) {
        AuditoriaRegistros a = new AuditoriaRegistros();
        a.setFecha(FECHA_PRUEBA);
        a.setRegistros(registros);
        a.setEvaluacion(evaluacion);
        a.setMinutosTrabajados(minutosTrabajados);
        return a;
    }

    @Test
    @DisplayName("Caso 1: Secuencia estándar completa (Entrada -> Salida)")
    public void testSecuenciaEstandar() {
        List<ColeccionRegistros> regs = new ArrayList<>();
        regs.add(crearFichada(LocalTime.of(8, 0), TipoMovimiento.ENTRADA));
        regs.add(crearFichada(LocalTime.of(16, 0), TipoMovimiento.SALIDA));

        AuditoriaRegistros a = crearAuditoria(regs, EvaluacionJornada.COMPLETA, 480);
        DiagnosticoInterpretacionSecuencia diag = ObservadorSecuenciaFichadasService.diagnosticar(a);

        assertEquals(LocalTime.of(8, 0), diag.getPrimeraFichadaConsiderada());
        assertEquals(LocalTime.of(16, 0), diag.getUltimaFichadaConsiderada());
        assertEquals(2, diag.getCantidadTotalFichadas());
        assertEquals(0, diag.getCantidadPausasDetectadas());
        assertEquals(480, diag.getMinutosTrabajadosCalculados());
        assertEquals(EvaluacionJornada.COMPLETA, diag.getEvaluacionAsignada());
    }

    @Test
    @DisplayName("Caso 2: Re-fichado por duda del empleado (Entrada -> Salida -> Entrada)")
    public void testEntradaSalidaEntrada() {
        List<ColeccionRegistros> regs = new ArrayList<>();
        regs.add(crearFichada(LocalTime.of(8, 0), TipoMovimiento.ENTRADA));
        regs.add(crearFichada(LocalTime.of(12, 0), TipoMovimiento.SALIDA));
        regs.add(crearFichada(LocalTime.of(16, 0), TipoMovimiento.ENTRADA));

        AuditoriaRegistros a = crearAuditoria(regs, EvaluacionJornada.COMPLETA, 480);
        DiagnosticoInterpretacionSecuencia diag = ObservadorSecuenciaFichadasService.diagnosticar(a);

        // La primera (08:00) y la última (16:00) definen el tramo; la Salida de las 12:00 es intermedia sin efecto
        assertEquals(LocalTime.of(8, 0), diag.getPrimeraFichadaConsiderada());
        assertEquals(LocalTime.of(16, 0), diag.getUltimaFichadaConsiderada());
        assertEquals(3, diag.getCantidadTotalFichadas());
        assertTrue(diag.getObservacionesDescriptivas().stream()
                .anyMatch(s -> s.contains("Fichadas intermedias sin efecto directo")));
    }

    @Test
    @DisplayName("Caso 3: Entrada -> Pausa Inicio -> Pausa Fin sin Salida final")
    public void testPausaInicioPausaFinSinSalida() {
        List<ColeccionRegistros> regs = new ArrayList<>();
        regs.add(crearFichada(LocalTime.of(8, 0), TipoMovimiento.ENTRADA));
        regs.add(crearFichada(LocalTime.of(12, 0), TipoMovimiento.PAUSA_INICIO));
        regs.add(crearFichada(LocalTime.of(13, 0), TipoMovimiento.PAUSA_FIN));

        AuditoriaRegistros a = crearAuditoria(regs, EvaluacionJornada.INCOMPLETA, 300);
        DiagnosticoInterpretacionSecuencia diag = ObservadorSecuenciaFichadasService.diagnosticar(a);

        assertEquals(LocalTime.of(8, 0), diag.getPrimeraFichadaConsiderada());
        assertEquals(LocalTime.of(13, 0), diag.getUltimaFichadaConsiderada());
        assertEquals(3, diag.getCantidadTotalFichadas());
        assertEquals(2, diag.getCantidadPausasDetectadas());
        assertTrue(diag.getObservacionesDescriptivas().stream()
                .anyMatch(s -> s.contains("Marcaciones de pausa registradas: 2")));
    }

    @Test
    @DisplayName("Caso 4: Entrada sin Salida en día pasado")
    public void testEntradaSinSalida() {
        List<ColeccionRegistros> regs = new ArrayList<>();
        regs.add(crearFichada(LocalTime.of(8, 0), TipoMovimiento.ENTRADA));

        AuditoriaRegistros a = crearAuditoria(regs, EvaluacionJornada.SIN_SALIDA, 0);
        DiagnosticoInterpretacionSecuencia diag = ObservadorSecuenciaFichadasService.diagnosticar(a);

        assertEquals(LocalTime.of(8, 0), diag.getPrimeraFichadaConsiderada());
        assertEquals(LocalTime.of(8, 0), diag.getUltimaFichadaConsiderada());
        assertEquals(1, diag.getCantidadTotalFichadas());
        assertEquals(EvaluacionJornada.SIN_SALIDA, diag.getEvaluacionAsignada());
    }

    @Test
    @DisplayName("Caso 5: Salida sin Entrada en día pasado")
    public void testSalidaSinEntrada() {
        List<ColeccionRegistros> regs = new ArrayList<>();
        regs.add(crearFichada(LocalTime.of(16, 0), TipoMovimiento.SALIDA));

        AuditoriaRegistros a = crearAuditoria(regs, EvaluacionJornada.SIN_ENTRADA, 0);
        DiagnosticoInterpretacionSecuencia diag = ObservadorSecuenciaFichadasService.diagnosticar(a);

        assertEquals(LocalTime.of(16, 0), diag.getPrimeraFichadaConsiderada());
        assertEquals(LocalTime.of(16, 0), diag.getUltimaFichadaConsiderada());
        assertEquals(1, diag.getCantidadTotalFichadas());
        assertEquals(EvaluacionJornada.SIN_ENTRADA, diag.getEvaluacionAsignada());
    }

    @Test
    @DisplayName("Caso 6: Doble Entrada consecutiva")
    public void testDobleEntrada() {
        List<ColeccionRegistros> regs = new ArrayList<>();
        regs.add(crearFichada(LocalTime.of(8, 0), TipoMovimiento.ENTRADA));
        regs.add(crearFichada(LocalTime.of(8, 15), TipoMovimiento.ENTRADA));
        regs.add(crearFichada(LocalTime.of(16, 0), TipoMovimiento.SALIDA));

        AuditoriaRegistros a = crearAuditoria(regs, EvaluacionJornada.COMPLETA, 480);
        DiagnosticoInterpretacionSecuencia diag = ObservadorSecuenciaFichadasService.diagnosticar(a);

        assertEquals(LocalTime.of(8, 0), diag.getPrimeraFichadaConsiderada());
        assertEquals(LocalTime.of(16, 0), diag.getUltimaFichadaConsiderada());
        assertEquals(3, diag.getCantidadTotalFichadas());
    }

    @Test
    @DisplayName("Caso 7: Doble Salida consecutiva")
    public void testDobleSalida() {
        List<ColeccionRegistros> regs = new ArrayList<>();
        regs.add(crearFichada(LocalTime.of(8, 0), TipoMovimiento.ENTRADA));
        regs.add(crearFichada(LocalTime.of(15, 50), TipoMovimiento.SALIDA));
        regs.add(crearFichada(LocalTime.of(16, 0), TipoMovimiento.SALIDA));

        AuditoriaRegistros a = crearAuditoria(regs, EvaluacionJornada.COMPLETA, 480);
        DiagnosticoInterpretacionSecuencia diag = ObservadorSecuenciaFichadasService.diagnosticar(a);

        assertEquals(LocalTime.of(8, 0), diag.getPrimeraFichadaConsiderada());
        assertEquals(LocalTime.of(16, 0), diag.getUltimaFichadaConsiderada());
        assertEquals(3, diag.getCantidadTotalFichadas());
    }

    @Test
    @DisplayName("Caso 8: Cantidad impar de fichadas")
    public void testCantidadImparFichadas() {
        List<ColeccionRegistros> regs = new ArrayList<>();
        regs.add(crearFichada(LocalTime.of(8, 0), TipoMovimiento.ENTRADA));
        regs.add(crearFichada(LocalTime.of(12, 0), TipoMovimiento.PAUSA_INICIO));
        regs.add(crearFichada(LocalTime.of(16, 0), TipoMovimiento.SALIDA));

        AuditoriaRegistros a = crearAuditoria(regs, EvaluacionJornada.COMPLETA, 480);
        DiagnosticoInterpretacionSecuencia diag = ObservadorSecuenciaFichadasService.diagnosticar(a);

        assertEquals(3, diag.getCantidadTotalFichadas());
        assertEquals(1, diag.getCantidadPausasDetectadas());
        assertEquals(LocalTime.of(8, 0), diag.getPrimeraFichadaConsiderada());
        assertEquals(LocalTime.of(16, 0), diag.getUltimaFichadaConsiderada());
    }

    @Test
    @DisplayName("Caso 9: Importaciones con fichadas duplicadas cercanas")
    public void testImportacionesDuplicados() {
        List<ColeccionRegistros> regs = new ArrayList<>();
        regs.add(crearFichada(LocalTime.of(8, 0), TipoMovimiento.ENTRADA));
        regs.add(crearFichada(LocalTime.of(8, 0), TipoMovimiento.ENTRADA)); // Duplicado persistido
        regs.add(crearFichada(LocalTime.of(16, 0), TipoMovimiento.SALIDA));

        AuditoriaRegistros a = crearAuditoria(regs, EvaluacionJornada.COMPLETA, 480);
        DiagnosticoInterpretacionSecuencia diag = ObservadorSecuenciaFichadasService.diagnosticar(a);

        assertEquals(3, diag.getCantidadTotalFichadas());
        assertEquals(LocalTime.of(8, 0), diag.getPrimeraFichadaConsiderada());
        assertEquals(LocalTime.of(16, 0), diag.getUltimaFichadaConsiderada());
    }

    @Test
    @DisplayName("Caso 10: Jornada completa con pausas intercaladas (Entrada -> PausaInicio -> PausaFin -> Salida)")
    public void testJornadaConPausas() {
        List<ColeccionRegistros> regs = new ArrayList<>();
        regs.add(crearFichada(LocalTime.of(8, 0), TipoMovimiento.ENTRADA));
        regs.add(crearFichada(LocalTime.of(12, 0), TipoMovimiento.PAUSA_INICIO));
        regs.add(crearFichada(LocalTime.of(13, 0), TipoMovimiento.PAUSA_FIN));
        regs.add(crearFichada(LocalTime.of(16, 0), TipoMovimiento.SALIDA));

        AuditoriaRegistros a = crearAuditoria(regs, EvaluacionJornada.COMPLETA, 480);
        DiagnosticoInterpretacionSecuencia diag = ObservadorSecuenciaFichadasService.diagnosticar(a);

        assertEquals(4, diag.getCantidadTotalFichadas());
        assertEquals(2, diag.getCantidadPausasDetectadas());
        assertEquals(LocalTime.of(8, 0), diag.getPrimeraFichadaConsiderada());
        assertEquals(LocalTime.of(16, 0), diag.getUltimaFichadaConsiderada());
        assertEquals(480, diag.getMinutosTrabajadosCalculados());
    }

    @Test
    @DisplayName("Caso 11: Jornada nocturna (22:00 -> 06:00)")
    public void testJornadaNocturna() {
        List<ColeccionRegistros> regs = new ArrayList<>();
        regs.add(crearFichada(LocalTime.of(22, 0), TipoMovimiento.ENTRADA));

        ColeccionRegistros salidaSiguienteDia = crearFichada(LocalTime.of(6, 0), TipoMovimiento.SALIDA);
        salidaSiguienteDia.setFecha(FECHA_PRUEBA.plusDays(1));
        regs.add(salidaSiguienteDia);

        AuditoriaRegistros a = crearAuditoria(regs, EvaluacionJornada.COMPLETA, 480);
        a.setEsJornadaNocturna(true);

        DiagnosticoInterpretacionSecuencia diag = ObservadorSecuenciaFichadasService.diagnosticar(a);

        assertEquals(2, diag.getCantidadTotalFichadas());
        assertEquals(LocalTime.of(22, 0), diag.getPrimeraFichadaConsiderada());
        assertEquals(LocalTime.of(6, 0), diag.getUltimaFichadaConsiderada());
        assertEquals(480, diag.getMinutosTrabajadosCalculados());
    }

    @Test
    @DisplayName("Caso diagnóstico con AuditoriaRegistros nula")
    public void testAuditoriaNula() {
        DiagnosticoInterpretacionSecuencia diag = ObservadorSecuenciaFichadasService.diagnosticar(null);
        assertNotNull(diag);
        assertEquals(0, diag.getCantidadTotalFichadas());
        assertEquals(0, diag.getCantidadPausasDetectadas());
        assertEquals(0, diag.getMinutosTrabajadosCalculados());
        assertNull(diag.getPrimeraFichadaConsiderada());
        assertNull(diag.getUltimaFichadaConsiderada());
    }
}
