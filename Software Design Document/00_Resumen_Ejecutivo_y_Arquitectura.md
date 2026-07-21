# Documento de Diseño de Software: STA.RH Biometric

## 00. Resumen Ejecutivo y Arquitectura

### 1. Introducción
**STA.RH Biometric** es un sistema web integral de control de asistencia desarrollado en Java 17 sobre el framework OpenXava 7.5. Su objetivo principal es registrar, gestionar y consolidar la asistencia diaria de los empleados mediante el fichaje biométrico (huella digital o reconocimiento facial) desde dispositivos Android o lectores USB.
El sistema automatiza el cálculo de minutos trabajados, control de llegadas tarde, cálculo de horas extras, y gestión de licencias e incidencias operando de forma autónoma mediante reglas programadas de turnos, y procesos batch que abren y cierran las jornadas.

### 2. Stack Tecnológico
La arquitectura de la aplicación se fundamenta en un conjunto de tecnologías Java estándar y de código abierto:
- **Lenguaje Base**: Java 17
- **Framework Principal**: OpenXava 7.5.2 (proporciona el patrón MVC, componentes de UI auto-generados y gestión de JPA)
- **Persistencia de Datos**: JPA / Hibernate (vía OpenXava) con H2 como base de datos por defecto en modo desarrollo, configurable a motores robustos como PostgreSQL/MySQL en producción vía `context.xml`.
- **Tareas Programadas (Jobs)**: Quartz Scheduler 2.3.2 (responsable de la apertura y cierre automático de jornadas y recálculos automáticos).
- **Capa API REST**: Jersey 2.35 para exponer los endpoints de fichada hacia dispositivos móviles biométricos.
- **Seguridad y Autenticación**: JSON Web Tokens (JJWT 0.9.1) para la API REST, y Naviox (módulo de OpenXava) para el Backend/UI Administrativo.

### 3. Arquitectura del Sistema
El proyecto emplea una arquitectura monolítica modular, fuertemente acoplada a la especificación de OpenXava, lo cual le permite desarrollar rápidamente vistas basadas exclusivamente en modelos de dominio (`@Entity` y `@View`).

*   **Capa de Presentación (UI)**: Autogenerada por OpenXava basándose en las entidades JPA y clases en `com.sta.biometric.dashboard`.
*   **Capa de Negocio**: Implementada mediante `@Entity` con lógica rica y delegación en servicios ubicados en `com.sta.biometric.servicios`.
*   **Capa REST (External Interfaces)**: Controladores independientes ubicados en `com.sta.biometric.rest` que implementan `Jersey` para que las APPs móviles externas envíen tramas de identificación biométricas de manera segura.
*   **Capa Batch (Jobs)**: Orquestada por Quartz (`com.sta.biometric.qartzJobs`) encargada de inicializar jornadas a media noche, e indagar ausencias o calcular horas al cierre del día.

### 4. Topología del Proyecto y Estructura de Paquetes
```text
src/main/java/com/sta/biometric/
├── acciones/            -> Controladores de UI, botones personalizados OpenXava (Ej. LiquidacionAction).
├── anotaciones/         -> Metadatos de diseño personalizados.
├── auxiliares/          -> DTOs y Wrappers no persistentes y objetos utilitarios.
├── calculadores/        -> DefaultValueCalculator para iniciar valores de OpenXava / UI.
├── dashboard/           -> Controladores y DTOs del panel de control analítico.
├── embebidas/           -> Clases anotadas con @Embeddable (Direccion, JornadaAsignada).
├── enums/               -> Definiciones de estados constantes (TipoMovimiento, EstadoJornada).
├── formateadores/       -> Presentación de datos, parsers de hora y fecha.
├── modelo/              -> Las Entidades de Negocio core (Personal, LiquidacionJornada, etc).
├── qartzJobs/           -> Implementaciones de org.quartz.Job (AperturaJornadaJob, CierreJornadaJob).
├── rest/                -> Controladores API Jersey (@Path) para integraciones externas.
├── servicios/           -> Capa Service que inyecta lógica de negocio agnóstica a la UI.
└── util/                -> Funciones auxiliares de seguridad (JWTUtil) y transformaciones.
```

### 5. Flujo Operativo Principal
1. **00:00 AM:** El sistema (Quartz) dispara el evento de `AperturaJornadaJob`, creando estructuras de datos vacías para los empleados que deben trabajar basándose en su turno o licencia activa.
2. **Durante el Día:** 
   - Los empleados autentican su identidad biométricamente.
   - La API REST (`/api/asistencia/fichar`) recibe la solicitud.
   - El sistema ubica al `Personal`, genera un registro de tipo matriz (`ENTRADA, SALIDA, PAUSA_INICIO, PAUSA_FIN`) y recalcula en vivo el estatus de la jornada.
3. **23:59 PM:** El sistema (Quartz) ejecuta el `CierreJornadaJob`, consolidando el día: asigna "Ausencia" a quienes no ficharon, calcula descuentos por llegadas tarde, feriados trabajados y envía notificaciones o consolida reportes.

---
**Siguiente Documento Sugerido:** `01_Modelo_de_Dominio_y_Entidades.md` (Para explorar exhaustivamente la estructura de la base de datos y modelo de objetos).
