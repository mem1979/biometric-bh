# Biometric – Sistema de Fichaje y Control de Asistencia Enterprise
**(OpenXava 7.5 / Java 17)**

Proyecto web corporativo destinado al registro, consolidación financiera y control de la asistencia diaria del personal mediante fichaje biométrico (Android Fingerprint / Reconocimiento Facial / ZKTeco USB). El sistema implementa un potente **Motor de Reglas y Auditoría Continua**, manejando desde licencias fraccionadas hasta control inmutable por el método Snapshot Financiero.

---

## 📑 Índice
1. [Características Destacadas](#características-destacadas)
2. [Arquitectura y Stack Tecnológico](#arquitectura-y-stack-tecnológico)
3. [Topología y Módulos de Código](#topología-y-módulos-de-código)
4. [Instrucciones de Despliegue](#instrucciones-de-despliegue)
5. [Estructura de la API REST](#estructura-de-la-api-rest)
6. [Automatización con Quartz (Jobs)](#automatización-con-quartz-jobs)

---

## 🚀 Características Destacadas

*   **Ingeniería Snapshot Financiero:** Congela el valor monetario a la hora exacta en que ocurre cada ficha y cálculo, asegurando que un recálculo de sueldo a futuro no modifique la pre-liquidación histórica.
*   **Ajuste y Redondeo Automático (Service):** Evita la generación de centavos o minutos por horas extras aplicando márgenes de tolerancia rigurosamente configurados en las preferencias.
*   **Integración Hardware/Móvil vía REST:** Dispositivos Android intercambian credenciales a través de un endpoint securizado por **JWT (JSON Web Tokens)** y validación de Hardware `DeviceID`.
*   **Automatización Sin Humanos (Zero-Touch):** Ejecuta trabajos *cronometrados* a medianoche orquestado por **Quartz**, abriendo plantillas de asistencia y evaluando ausentes nocturnos, librando de sobrecarga de trabajo técnico al analista de RRHH.
*   **Control Flexible de Turnos:** Desplazamiento rotativo estricto; el código "sabe" el día de rotación del empleado según fecha base sin input del usuario.

## ⚙️ Arquitectura y Stack Tecnológico

El proyecto sigue una arquitectura monolítica ágil sobre **OpenXava**, lo cual permite acelerar la entrega de software focalizando en la lógica *Backend / Dominio* y auto-generando Frontends completos.

| Tecnología                 | Versión | Rol en la Arquitectura                                             |
| :------------------------- | :---- | :----------------------------------------------------------------- |
| **Java**                   | 17    | Core lógico.                                                       |
| **OpenXava**               | 7.5.2 | Framework MVC-JPA, UI generation y Controller mapping.                |
| **JPA / Hibernate**        | N/A   | Abstracción de Base de Datos y transaccionalidad garantizada (ACID).|
| **Quartz Scheduler**       | 2.3.2 | Hilos paralelos y ejecución Batch a medianoche.                     |
| **Jersey JAX-RS**          | 2.35  | Exposición robusta de la API REST hacia la calle.                    |
| **JJWT**                   | 0.9.1 | Seguridad de tokens JWT con firmas asimétricas para la app.        |

## 📁 Topología y Módulos de Código

La lógica está acotada estrictamente asegurando adherencia a Principios Solid:

```text
src/main/java/com/sta/biometric/
├── acciones/            ← (UI) Controladores de pantalla visual, acciones asíncronas de guardado.
├── embebidas/           ← Componentes encapsulados de JPA (Direccion, Contactos).
├── modelo/              ← EL DOMINIO INTELIGENTE. Entidades como Personal, AuditoriaRegistros.
├── qartzJobs/           ← Tareas como AperturaJornadaJob (madrugada) ó CierreJornadaJob (medianoche).
├── rest/                ← API Jersey con endpoints: /auth, /asistencia.
├── servicios/           ← Business Logic puro: LiquidacionJornadaService, InterpreteFichadas.
└── util/                ← Validaciones genéricas de JWT y Fechas.
```

## 🔌 Instrucciones de Despliegue

### Pre-requisitos
*   JDK 17 instalado en el enviroment.
*   Maven 3.9+ local o de pipeline.

### Puesta en Marcha (Dev Mode)
1.  **Clonación e inicialización:**
    ```bash
    git clone [url-del-repo-biometric]
    cd biometric
    mvn clean package
    ```
2.  **Spinning Up del Servidor Tomcat / H2 Embebido:**
    ```bash
    mvn exec:java -Dexec.mainClass="com.sta.biometric.run.biometric"
    ```
3.  **Accesos locales:**
    *   Web: `http://localhost:8080/biometric`
    *   Credenciales: `admin / admin`

### Puesta en Marcha (Producción)
Se debe modificar el vector de persistencia en `src/main/webapp/META-INF/context.xml` apuntando a su motor DBMS Relacional oficial (PostgreSQL / SQL Server) y desactivar `DBServer.start` dentro de `biometric.java`.

## 📡 Estructura de la API REST

Los endpoints exigen estar provistos del Header estático: `Authorization: Bearer <token_jwt>`

| Method | Endpoint               | Función | Payload/Response |
| :---   | :---                   | :---    | :--- |
| **POST** | `/api/auth/login`      | Handshake Auth e inyección del _Device-ID_ | Devuelve String Token (Tiempo expira por `biometricConfig.properties`) |
| **GET**  | `/api/asistencia/hoy`    | Informa al frontend si tiene turno activo en curso o ausencias. | JSON (Ya fichó, turnos solapados) |
| **POST** | `/api/asistencia` | Insert transaccional de fichada (Long/Lat). | Recibe tipo `ENTRADA`/`SALIDA` geolocalizada. Lanza error interno ante fallos de integridad temporal (via `InterpreteFichadasService`). |

## ⏱️ Automatización con Quartz (Jobs)

| Tarea Batch | Horario Default | Función Principal |
| :--- | :--- | :--- |
| **`AperturaJornadaJob`** | `00:00:01` | Prepara de forma vacía el molde JPA de fichajes a nivel empresarial. |
| **`CierreJornadaJob`** | `23:59:00` | Sella el histórico (Snapshot), bloqueando re-procesos. Penaliza inasistencia pura o recalcula con la tolerancia general permitida. |
| **`CierreJornadaNocturna`** | Dinámico | Consolida turnos cruzando el Meridiano (Ej. Lunes 22hs a Martes 6hs). |

---
**Documentación Extendida disponible en `Software Design Document/` (Planos de Arquitectura, Manual Técnico y Flujos del SDD).**
