# Documento de Diseño de Software: STA.RH Biometric

## 06. Componentes Auxiliares, DTOs y Dashboard

La capa visual y las estructuras de datos precalculadas se agrupan en diversos submódulos que asisten a la arquitectura principal, reduciendo tiempos de consulta en la interfaz de usuario OpenXava y manteniendo el código tipado estrictamente.

### 1. Panel de Control (`com.sta.biometric.dashboard`)

OpenXava carece de componentes nativos de reportes gráficos complejos listos para usar sin abstracciones añadidas. El proyecto implementa un módulo Dashboard para orquestar la analítica de Recursos Humanos.

#### `DashboardAsistencia.java`
- Es un controlador y vista anclado al inicio de la aplicación para administradores.
- No es una Entidad JPA pura persistente, sino un modelo transitorio o vista armada "al vuelo" apoyada por métodos factoría.
- Engloba los gráficos de torta, barras transversales y contadores (Presentes, Ausentes, Faltas Justificadas). Usa inyección HTTP mediante OpenXava (JSP) o librerías de javascript integradas de terceros (Chart.js / Jasper).

#### DTOs Analíticos (`com.sta.biometric.dashboard.auxiliares`)
- `ResumenEmpleadoHoy.java`: Estructura para listar los empleados y sus estados actuales (presentes en la instalación física, ausentes, o que llegaron tarde).
- `ResumenPorTurno.java`: Estructura que desglosa las métricas para aislar la analítica por turno (Mañana vs Tarde).
- `DtoLicenciasFeriados.java`: Transportador de objetos para pintar calendarios híbridos (mezcla licencias solicitadas por el operario y los feriados nacionales sin hacer lecturas pesadas a la BD).

### 2. Calculadores (OpenXava)

OpenXava utiliza implementaciones de `ICalculator` para inicializar el estado de la UI y los objetos antes de interactuar con la Base de Datos. Están en `com.sta.biometric.calculadores`.

- **`GeneradorCodigoUserIdCalculator.java`**: Calcula el ID alfanumérico interno para un empleado (ej: para asignar credenciales de forma agnóstica).
- **`CalculadorPassword.java`**: Formatea y auto-hashea un password default temporal usando el DNI del personal cuando es de alta.
- **`CalculadorDefaultFromProperties.java`**: Herramienta muy flexible para recuperar variables de `biometricConfiguracion.properties` y volcarlas dentro del modelo o vista automáticamente en tiempo de renderizado.
- **Asistentes de Fechas**: `InicioMesActualCalculator.java` y `FinMesActualCalculator.java` autocompletan inputs de filtros en las vistas de "Recalcular Fichadas" y "Liquidar".

### 3. Enumeraciones de Sistema (`com.sta.biometric.enums`)

El proyecto usa enumeraciones tipadas estrictamente para prevenir datos inconsistentes.
- **`TipoMovimiento`**: Define acciones de marcado. Posibles valores: `ENTRADA`, `SALIDA`, `PAUSA_INICIO`, `PAUSA_FIN`.
- **`EvaluacionJornada`**: El core flag de `AuditoriaRegistros`. Valores: `COMPLETA`, `INCOMPLETA`, `AUSENTE`, `FERIADO_TRABAJADO`, `LICENCIA`, `EN_CURSO`.
- **`TipoLicenciaAR`**: Define los marcos de la ley de trabajo. `MATERNIDAD`, `ENFERMEDAD`, `EXAMEN`, `VACACIONES`, `MUDANZA`, etc.
- **`EstadoLiquidacion`**: `ABIERTO`, `CERRADO`, `RECALCULADO`. Protege registros históricos.
- **Definiciones Auxiliares**: `Continentes`, `ModalidadTrabajo`, `NivelJerarquico` (utilizados paramétricamente en el ABM `Personal`).

### 4. DTOs de Integración (Transferencia Interna)
En `com.sta.biometric.dto` residen los objetos que arman las vistas de los reportes PDF. Sus métodos getters y setters se emparejan a través de Beans Collection Data Sources orientados a **JasperReports** (ej. `AnalisisIntegralDTO.java`, `InformeMensualDTO.java`).

---

***Con este documento se concluye el esquema fundacional estructurado de la aplicación Biometric de STA.RH. Cualquier otra IA podrá tomar los modelos y métodos detallados para reconstruir o entender integralmente sus comportamientos de UI y Backend.***
