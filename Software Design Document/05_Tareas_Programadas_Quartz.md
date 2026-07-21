# Documento de Diseño de Software: STA.RH Biometric

## 05. Tareas Programadas (Quartz Jobs)

La ejecución desatendida del sistema descansa sobre el framework de planificación **Quartz**. Es el responsable de crear los contenedores de asistencia diarios antes de que el usuario fiche y de cerrarlos al culminar el día, consolidando ausencias y métricas a gran escala sin intervención humana.

El paquete principal es `com.sta.biometric.qartzJobs`. 

### `ApplicationQuartzInitializer.java`
- Es el configurador global del scheduler. 
- Implementa `javax.servlet.ServletContextListener` (o similar en el ecosistema OpenXava/Tomcat) para arrancar al iniciar la aplicación.
- Construye e inyecta los `JobDetail` y los `CronTrigger`, parametrizando las horas de ejecución leyendo dinámicamente el archivo `biometricConfiguracion.properties`.

### Jobs Principales

#### 1. `AperturaJornadaJob.java`
- **Disparador Frecuente:** 00:00 (Medianoche).
- **Proceso:**
  - Extrae de la base de datos la nómina completa de `Personal` activo.
  - Para cada empleado, consulta su `TurnosHorarios` o rol de semana a través del servicio `GestionJornadasService`.
  - Crea objetos `AuditoriaRegistros` *en blanco* (estado `PENDIENTE` o preasignado a `LICENCIA` o `FERIADO` dependiendo del calendario) para la fecha que acaba de comenzar.
  - Así, cuando el dashboard consulte por la mañana, los paneles analíticos ya mostrarán al empleado como "Ausente" o "Pendiente", en preparación para que llegue y registre su `ENTRADA`.

#### 2. `CierreJornadaJob.java`
- **Disparador Frecuente:** 23:59 (Fin del día).
- **Proceso:**
  - Recupera todos los `AuditoriaRegistros` pendientes o que han quedado en estado `EN_CURSO` (empleados que ficharon entrada pero jamás ficharon salida).
  - Transforma su estado a `SIN_SALIDA` o consolida la inasistencia pura asignando el estado `AUSENTE`.
  - Re-evalúa el recálculo final invocando a `consolidarDesdeRegistros()` para dejar la foto final del registro diario en un estado inmutable para cuando comiencen los ciclos de liquidación (Nómina).

#### 3. `CierreJornadaNocturnaJob.java`
- **Disparador Frecuente:** Varía (generalmente hacia mediodía o madrugada post-cierre).
- **Proceso Específico:**
  - Debido a la complejidad logística de los turnos que cruzan la medianoche (Por ejemplo: Turno empieza 22:00hs del Lunes y finaliza 06:00hs del Martes).
  - Este Job busca las jornadas que quedaron "enganchadas" de un ciclo anterior y se ocupa exclusivamente de correr el `consolidarDesdeRegistros()` verificando que tanto la entidad del Lunes como la fichada del Martes hayan coincidido en una única ventana lógica de liquidación.

#### 4. `ActualizarFeriadosJob.java`
- **Disparador Frecuente:** Semanal o Anual.
- **Proceso:**
  - Podría invocar métodos para pre-calentar o descargar la lista oficial de feriados nacionales hacia la base de datos de manera proactiva, garantizando que el calendario de la empresa coincida con el legal para el correcto recargo de la métrica por `Horas Especiales` trabajadas.

---

**Siguiente Documento Sugerido:** `06_Componentes_Auxiliares.md` (Para explorar Enums, Dashboard e Integración Gráfica).
