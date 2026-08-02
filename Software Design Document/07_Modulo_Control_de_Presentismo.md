# 📘 Módulo de Control de Presentismo — Especificación Técnica y Funcional

**Proyecto:** Biometric-BH (STARH)  
**Tecnología:** OpenXava 7.7.2 / Java 17 / JPA (Hibernate) / PostgreSQL  
**Ubicación de Archivos:** `com.sta.biometric.auxiliares`, `com.sta.biometric.enums`, `com.sta.biometric.servicios`  
**Versión:** 2.0 — Especificación Técnica Definitiva (Banco de Horas Persistente & Licencias Justificadas)  

---

## 1. Arquitectura y Principios de Diseño

El módulo de Control de Presentismo se diseñó como un **consumidor liviano y desacoplado** de la información producida por el motor de auditoría de asistencia.

```text
 ┌────────────────────────────────────────────────────────────────────────────────────────┐
 │ AuditoriaRegistros (Dominio)                                                          │
 │  ├── Única fuente de verdad de la jornada diaria                                      │
 │  ├── Expone EvaluacionJornada (COMPLETA, INCOMPLETA, AUSENTE, SIN_ENTRADA...)         │
 │  ├── Persiste descontarPresentismo (BOOLEAN DEFAULT FALSE) por jornada                │
 │  └── Preserva notas, licencias y minutaje consolidado                                 │
 └───────────────────────────────────────────┬────────────────────────────────────────────┘
                                             │ (List<AuditoriaRegistros>)
                                             ▼
 ┌────────────────────────────────────────────────────────────────────────────────────────┐
 │ PresentismoService (Servicio Utilitario Estático)                                     │
 │  ├── Lee biometricConfiguracion.properties vía ConfiguracionesPreferencias            │
 │  ├── Consulta reg.isDescontarPresentismo() directamente sin re-calcular Banco         │
 │  └── Cero recálculos: NO analiza fichadas brutas, ni tolerancias, ni horarios        │
 └───────────────────────────────────────────┬────────────────────────────────────────────┘
                                             │ (Retorna ResultadoPresentismoPeriodo DTO)
                                             ▼
 ┌────────────────────────────────────────────────────────────────────────────────────────┐
 │ Consumidores de Presentismo (Capa de Presentación)                                    │
 │  ├── LiquidacionJornadas.getResultadoPresentismo() / getPresentismoDisplay()          │
 │  ├── UI OpenXava: Vista DetalleCompletoDialogo (Diálogo "Ver Jornadas")               │
 │  └── ExportarJornadasExcelAction (Fila de Control Presentismo en tabla resumen Excel) │
 └────────────────────────────────────────────────────────────────────────────────────────┘
```

### Reglas Invariantes de Arquitectura:
1. **Decisión Persistida por Jornada:** La decisión de si una jornada con movimiento en el Banco de Horas penaliza o no el presentismo se almacena explícitamente en el atributo `@Column(name = "descontar_presentismo") private boolean descontarPresentismo`.
2. **`AuditoriaRegistros` como Fuente Única de Verdad:** Todo cálculo relativo a la asistencia diaria es responsabilidad exclusiva de `AuditoriaRegistros`.
3. **Prioridad Disciplinaria en Licencias:** La prioridad disciplinaria es `Licencia.justificado`. Una licencia justificada (`justificado = true`) **nunca genera incidencia**. Una licencia no justificada (`justificado = false`) computa si `presentismo.licencias.no.justificadas.computan=true`.
4. **`PresentismoService` Desacoplado:** El servicio es estático y sin estado. Su firma principal es:
   ```java
   public static ResultadoPresentismoPeriodo evaluarPresentismo(List<AuditoriaRegistros> jornadas)
   ```
5. **Cero Duplicación de Lógica:** No se recalculan fichadas, ni horarios esperados, ni tolerancias. El servicio lee los datos estructurados y consolidados.

---

## 2. Script SQL de Migración para PostgreSQL (Ejecutable desde DBeaver)

```sql
-- =============================================================================
-- SCRIPT DE MIGRACIÓN POSTGRESQL - MÓDULO BIOMETRIC-BH (PRESENTISMO & BANCO DE HORAS)
-- Descripción: Agrega el campo 'descontar_presentismo' a la tabla auditoria_registros.
-- Compatible con PostgreSQL 12+ / Idempotente / DBeaver Ready
-- =============================================================================

-- 1. Agregar la columna descontar_presentismo si no existe
ALTER TABLE auditoria_registros 
ADD COLUMN IF NOT EXISTS descontar_presentismo BOOLEAN DEFAULT FALSE;

-- 2. Actualizar registros existentes para asegurar consistencia (sin valores NULL)
UPDATE auditoria_registros 
SET descontar_presentismo = FALSE 
WHERE descontar_presentismo IS NULL;

-- 3. Documentar la columna en el diccionario de datos PostgreSQL
COMMENT ON COLUMN auditoria_registros.descontar_presentismo IS 
'Indica si la jornada con movimiento en el Banco de Horas computa como penalización de presentismo (true) o si queda exenta (false)';
```

---

## 3. Catálogo de Propiedades de Configuración (`biometricConfiguracion.properties`)

Todas las reglas del módulo son parametrizables sin requerir recompilación del sistema mediante `ConfiguracionesPreferencias.obtenerValor(...)`:

| Propiedad | Tipo | Valor por Defecto | Descripción y Finalidad | Ejemplos de Uso |
|---|---|---|---|---|
| `presentismo.habilitado` | `Boolean` | `true` | Habilitador global del módulo de presentismo. Si es `false`, se aprueba el premio automáticamente. | `true` / `false` |
| `presentismo.politica` | `String` | `"GENERAL"` | Nombre identificatorio de la política o convenio aplicado. | `"GENERAL"`, `"CCT_130_75"` |
| `presentismo.evaluar.llegadas.tarde` | `Boolean` | `true` | Feature toggle para activar/desactivar el control de llegadas tarde. | `true` (evalúa), `false` (ignora) |
| `presentismo.evaluar.salidas.anticipadas` | `Boolean` | `true` | Feature toggle para activar/desactivar el control de salidas antes de hora. | `true` / `false` |
| `presentismo.evaluar.jornadas.incompletas` | `Boolean` | `true` | Feature toggle para controlar déficit de horas sin llegada tarde explícita. | `true` / `false` |
| `presentismo.evaluar.ausencias` | `Boolean` | `true` | Feature toggle para controlar ausencias injustificadas. | `true` / `false` |
| `presentismo.evaluar.pausas` | `Boolean` | `false` | Feature toggle para evaluar excesos de tiempo en pausas laborales. | `false` (preparado para futuro) |
| `presentismo.llegadas.tarde.max` | `Integer` | `2` | Cantidad máxima de llegadas tarde permitidas en el período. | `2` (tolera 2, a la 3ra pierde) |
| `presentismo.salidas.anticipadas.max` | `Integer` | `1` | Cantidad máxima de salidas anticipadas permitidas por período. | `1` (tolera 1) |
| `presentismo.jornadas.incompletas.max` | `Integer` | `1` | Cantidad máxima de jornadas incompletas permitidas por período. | `1` |
| `presentismo.minutos.demora.acumulados.max` | `Integer` | `15` | Minutos máximos acumulados de demora/déficit en el período. | `15` |
| `presentismo.ausencias.max` | `Integer` | `0` | Cantidad máxima de ausencias injustificadas permitidas (0 = 1 falta quita el premio). | `0` (estricto) |
| `presentismo.banco.horas.descontar.default` | `Boolean` | `false` | Valor inicial del checkbox UI "Descontar Presentismo" al operar con Banco de Horas. | `false` (exento por defecto) |
| `presentismo.banco.horas.max` | `Integer` | `2` | Cantidad máxima de jornadas enviadas al Banco con `descontarPresentismo=true` permitidas en el período. | `2` (tolera 2, 3ra pierde; -1 deshabilita) |
| `presentismo.licencias.no.justificadas.computan` | `Boolean` | `true` | Criterio disciplinario principal: determina si licencias NO JUSTIFICADAS (`!justificado`) descuentan el premio. | `true` |
| `presentismo.licencias.sin.goce.computan` | `Boolean` | `false` | Propiedad histórica obsoleta mantenida por compatibilidad hacia atrás. | `false` |
| `presentismo.fichadas.incompletas.computan` | `Boolean` | `true` | Determina si tener fichadas `SIN_ENTRADA` o `SIN_SALIDA` descuenta el premio. | `true` |

---

## 4. Casos Prácticos de Evaluación

### Caso 1: Jornada Compensada por Banco de Horas con Checkbox Desmarcado (`descontarPresentismo = false`)
- **Acción Supervisor:** El supervisor compensa una salida anticipada enviando horas al Banco y deja el checkbox `Descontar Presentismo` desmarcado (`false`).
- **Trazabilidad Auditada:** La nota del movimiento anexa automáticamente: `Presentismo: NO COMPUTA.`
- **Evaluación:** `PresentismoService` detecta `minutosEnviadosAlBanco != 0` y `isDescontarPresentismo() == false`.
- **Resultado:** **La jornada queda exenta.** `✅ CUMPLE PRESENTISMO`.

### Caso 2: Jornada Compensada por Banco de Horas con Checkbox Marcado (`descontarPresentismo = true`)
- **Acción Supervisor:** El supervisor autoriza el movimiento al Banco pero marca el checkbox `Descontar Presentismo` (`true`).
- **Trazabilidad Auditada:** La nota del movimiento anexa automáticamente: `Presentismo: COMPUTA.`
- **Evaluación:** `PresentismoService` detecta `isDescontarPresentismo() == true` e incrementa el contador `totalJornadasBancoDescontadas`. Si supera `presentismo.banco.horas.max=2`, descuenta el premio.
- **Resultado:** `❌ PÉRDIDA DE PRESENTISMO`.

### Caso 3: Licencia Justificada vs. Licencia No Justificada
- **Escenario A (Licencia Justificada con o sin goce):**  
  `registro.isLicencia() == true` y `registro.isJustificado() == true`.  
  **Resultado:** `✅ CUMPLE PRESENTISMO` (No genera incidencia disciplinaria).
- **Escenario B (Licencia No Justificada):**  
  `registro.isLicencia() == true` y `registro.isJustificado() == false` (o `evaluacion == LICENCIA_NO_JUSTIFICADA`).  
  **Resultado:** `❌ PÉRDIDA DE PRESENTISMO` (Registra incidencia `LICENCIA_NO_JUSTIFICADA`).

---

## 5. Integración con Liquidación y Exportación Excel

### 5.1. Visualización en la Interfaz de OpenXava (`LiquidacionJornadas`)
En la vista `@View(name = "DetalleCompletoDialogo")` del diálogo **"Ver Jornadas"**, se presenta la cabecera formateada:

```text
┌────────────────────────────────────────────────────────────────────────────────────────┐
│ Período: 01/07/2026 al 31/07/2026   Estado: ABIERTO                                    │
│ Liq. Norm: 160:00 hs   Liq. Ext: 10:00 hs   Presentismo: ✅ CUMPLE PRESENTISMO          │
│ Gran Total: $ 875.000,00                                                               │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

### 5.2. Reporte Excel (`ExportarJornadasExcelAction`)
En la sección `crearTablaResumen()`, el exportador añade la fila de Presentismo en la tabla de resumen ejecutiva:

| Concepto | Horas A Pagar / Estado | Valor Hora / Campo | Total $ / Detalle |
|---|---|---|---|
| Horas Normales | 160:00 | $ 5.000,00 | $ 800.000,00 |
| Horas Extras | 10:00 | $ 7.500,00 | $ 75.000,00 |
| Horas Especiales | 00:00 | $ 10.000,00 | $ 0,00 |
| **Control Presentismo** | **✅ CUMPLE PRESENTISMO** | **Detalle** | **Asistencia perfecta en el período.** |
| **TOTAL GENERAL** | | | **$ 875.000,00** |
