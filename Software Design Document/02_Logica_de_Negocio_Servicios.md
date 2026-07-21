# Documento de Diseño de Software: STA.RH Biometric

## 02. Lógica de Negocio y Servicios

La capa de servicios en el paquete `com.sta.biometric.servicios` encapsula la lógica procedural pesada que no debe residir en las vistas, controladores o clases de entidad. Estos servicios utilizan la API pura de JPA (`XPersistence.getManager()`) para la lectura/escritura en base de datos.
La mayoría de estas clases están expuestas como métodos estáticos o "Singletons" para facilitar su consumo desde OpenXava.

### Clasificación de los Servicios

#### 1. Fichadas y Asistencia
- **`AsistenciaDiariaService.java`**
  - **Función:** Registra, valida y actualiza las transacciones individuales (`ColeccionRegistros`). Busca el turno que debe tener el empleado y determina las banderas de justificaciones, feriados y nocturnidad en tiempo real.
- **`InterpreteFichadasService.java` y `LectorArchivoService.java`**
  - **Función:** Responsables de tomar secuencias brutas (como las generadas por pendrives en lectores biométricos ZKTeco o similares), analizarlas por fecha, DNI/legajo del empleado e invocar dinámicamente al servicio de asistencia.
- **`ResumenAsistenciaHoyService.java`**
  - **Función:** Servicio altamente optimizado (generalmente invocado por el Dashboard / API REST) para entregar métricas rápidas de los presentes, ausentes y tardanzas del día actual sin instanciar grafos JPA pesados.

#### 2. Procesamiento de Liquidaciones e Inteligencia de Nómina
- **`LiquidacionJornadaService.java`**
  - **Función:** Servicio "Batch". Consolida la información agrupando objetos de `AuditoriaRegistros` para un rango de fechas. Procesa la sumatoria monetaria y escribe en las tablas de `LiquidacionJornadas`, fijando los *snapshots* monetarios.
- **`RedondeoHorasService.java`**
  - **Función:** Analiza la discrepancia entre las "horas esperadas" de la jornada y las "horas fichadas". Si la diferencia cae bajo ciertas métricas de tolerancia permitidas en las preferencias del sistema, redondea al turno oficial, evitando generar pequeños saldos en horas extras (ej. evitar pagar 2 min. extra porque la persona fichó a las 18:02).
- **`LicenciaRecalculacionService.java`**
  - **Función:** Al aprobarse o modificarse una instancia de `Licencia`, este servicio retrocede en el calendario para buscar si hubo inasistencias en esos días, cambiándolas a estado `LICENCIA` o `LICENCIA_SIN_GOCE` y regenerando el rastro monetario.
- **`AnalisisDesempenoService.java`**
  - **Función:** Con base en un motor de reglas, evalúa sistemáticamente a un empleado y recomienda la generación automática de una `NotaDesempeno` basada en llegadas tarde y cumplimiento de horas, apoyando las métricas mostradas en la ficha personal.

#### 3. Servicios Periféricos y Soporte Técnico
- **`GestionJornadasService.java`**
  - **Función:** Contiene el *core implementation* invocado por los cron-jobs horarios y nocturnos de Quartz. Evalúa por cada empleado de la nómina activa su turno para el día entrante y prepara las entidades de pre-registro vacías.
- **`ImportadorFeriadosService.java`**
  - **Función:** Encargado de cargar el calendario oficial de feriados a la base de datos (con posibilidad de consumo de fuentes API locales o parseo de archivos).
- **`AsignarCoordenadasService.java`**
  - **Función:** Si la aplicación recibe los registros con longitud y latitud (ej. desde celular), puede emplear ingeniería inversa para validar o geocodificar la ubicación.
- **`ConfiguracionesPreferencias.java`**
  - **Función:** Administrador que recupera los parámetros de tolerancia, horas máximas u opciones visuales directamente enlazadas a `biometricConfiguracion.properties`.

---

**Siguiente Documento Sugerido:** `03_Controladores_y_Acciones.md` (Integración visual de estos servicios y eventos en la UI Web OpenXava).
