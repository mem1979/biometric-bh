# Documento de Diseño de Software: STA.RH Biometric

## 03. Controladores y Acciones (UI OpenXava)

OpenXava autogenera la interfaz basándose en las entidades de JPA, y permite inyectar comportamiento personalizado mediante **Actions** y **Controllers** (frecuentemente configurados en `aplicacion.xml` o mapeados dinámicamente mediante `@Action` / `@OnChange`).

El paquete `com.sta.biometric.acciones` contiene un amplio arsenal de clases que extienden genéricamente `ViewBaseAction`, `TabBaseAction` u otras interfaces nativas de OpenXava.

### Acciones Comunes y Arquitectura de UI

1. **Gestión de Entidades Centrales (Save & Delete):**
   - **`PersonalSaveAction.java`**: Sobrescribe el comportamiento por defecto al "Guardar" un empleado. Realiza validaciones pre-guardado (que los DNI no estén duplicados o que haya un nombre válido), emite alertas UI de confirmación y permite redireccionar.
   - **`EliminarPersonalParaPapeleraAction.java`**: Modifica la funcionalidad de borrado. En lugar de ejecutar instrucción SQL `DELETE`, efectúa un "Soft Delete" marcando el campo `eliminado = true` y capturando la fecha de baja para aislar al registro en la pestaña de `Papelera`.

2. **Liquidaciones y Reporting:**
   - **`GenerarLiquidacionAction.java` / `GenerarInformeDiarioAction.java` / `RecalcularLiquidacionAction.java`**: Recolectan los datos (Ej. Rango de Fechas seleccionado en una pantalla modal previa - `MostrarDialogoLiquidacionAction.java`), invocan los servicios correspondientes y retornan la vista recargada con la información procesada.
   - **`ExportarJornadasExcelAction.java` / `GenerarPDF...Action`**: Lanzan el proceso de generación documental (generalmente conectándose a Apache POI o JasperReports) y envían el binario resultante al flujo web.

3. **Interacciones Dinámicas `@OnChange`**:
   Las acciones OnChange son disparadas por AJAX cada vez que el usuario modifica un control visual (checkbox, dropdown).
   - **`PersonalOnChangeActivoAction.java`**: Si un usuario destilda el campo "activo", el sistema automáticamente reajusta la visibilidad de los controles dependientes (quizás ocultando el panel de turnos).
   - **`LicenciaOnChangeJustificadoAction.java`** / **`LicenciaOnChangeParcialAction.java`**: Al elegir si una licencia es parcial u otro tipo, habilita/deshabilita inputs de rangos horarios para las horas licenciadas en tiempo real.

4. **Ventanas Modales y Diálogos:**
   Existen acciones exclusivamente pensadas para la estructura de `Dialog` de OpenXava.
   - **`MostrarDialogoReevaluacionAction.java` / `EjecutarReevaluacionAction.java`**: La primera muestra un formulario sobre los datos que el gerente debe llenar para reevaluar la jornada asincrónica. La segunda (tras presionar OK), ejecuta la lógica backend y envía alertas transaccionales (Success/Error).

5. **Manejo Geográfico / Biometría Auxiliar:**
   - **`GuardarUbicacionAction.java`** / **`ObtenerCoordenadasGenericaAction.java`**: Apoyos sobre el UI para mapear datos geográficos visualmente.
   - **`VerMapaAction.java`**: Lanza un editor especial o iFrame basado en Maps (Google/Leaflet) para ver desde dónde ocurrió la fichada.

### Declaración en Configuración
En `xava/aplicacion.xml` y `xava/controladores.xml`, la mayor cantidad de estos flujos se agrupan en colecciones nombradas. Por ejemplo, la colección genérica `<controller name="PersonalAcciones">` envuelve todos los botones que figurarán en la caja de opciones de la entidad Personal.

---

**Siguiente Documento Sugerido:** `04_Endpoints_API_REST.md` (Integración externa del modelo hacia otras aplicaciones, vital para la biometría periférica).
