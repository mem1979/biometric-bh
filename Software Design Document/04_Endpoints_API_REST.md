# Documento de Diseño de Software: STA.RH Biometric

## 04. Endpoints y API REST

El proyecto Biometric integra una API REST diseñada con **JAX-RS (Jersey)** para interoperar con aplicaciones móviles de control de asistencia, frontends alternativos y dispositivos de registro (relojes biométricos, tablets).

Los controladores REST están ubicados en el paquete `com.sta.biometric.rest` y están protegidos, en su mayoría, mediante autenticación de tokens JWT usando `JWTUtil`.

### Rutas Base y Autenticación
Todas las peticiones operan sobre la ruta predefinida del API.
Requieren que el Header contenga: `Authorization: Bearer <TOKEN>`

#### `AuthEndpoint.java` (`/auth`)
- **Propósito:** Endpoints de autenticación general.
- **Rutas clave:**
  - `POST /auth/login`
    - Recibe un JSON payload que contiene credentials y el identificador de dispositivo (`deviceId`). 
    - Valida contra el modelo `Personal` (campo de usuario y hash de contraseña). Si las credenciales y el `deviceId` coinciden, emite un Web Token que será válido por el tiempo definido en `biometricConfiguracion.properties`.

#### `AsistenciaEndpoint.java` (`/asistencia`)
Punto crítico del sistema transaccional donde operan los relojes/celulares operarios:
- **`GET /asistencia/hoy`**
  - Devuelve a un cliente autorizado el estado actual de su jornada en el día.
  - Verifica si ya existe ficha de `ENTRADA` o de `SALIDA`.
  - Contiene lógica dedicada a **Jornadas Nocturnas**: analiza y devuelve si existe una jornada abierta cruzando la medianoche de ayer hacia hoy para guiar visualmente al cliente remoto a registrar una "SALIDA".
- **`POST /asistencia`**
  - **Payload:** Requiere un `MovimientoRequest` (tipo, nota, ubicacion geolocalizada) e inyecta la hora *del servidor* (bloqueando modificaciones de hora en el dispositivo cliente).
  - Valida el `X-Device-ID` en las cabeceras contra la base de datos para impedir suplantación.
  - Instancia nuevas `AuditoriaRegistros` o recupera las existentes y agrega una nueva línea de `ColeccionRegistros`, luego llama a `consolidarDesdeRegistros()`. 
  - Manejo de prevención de fichadas duplicadas.

#### `RegistrosMesualesEndpoint.java` (`/registros`)
- **Propósito:** Servicio para consumo de histórico transaccional. Aplicaciones de empleado lo consumen para graficar su asistencia en el celular.
- **Rutas clave:** 
  - `GET /registros/{mes}/{anio}`: Valida el JWT y entrega el JSON consolidado de las `AuditoriaRegistros` de ese rango, informando minutos trabajados esperados vs reales.

#### `TurnoSemanaEndpoint.java` (`/turnos`)
- **Propósito:** Expone el esquema horario del empleado.
- **Rutas clave:**
  - `GET /turnos/semanaActiva`: Responde con un cronograma semanal JSON para que la app del cliente dibuje la hora a la que el empleado debe trabajar en el futuro inminente.

### Manejo de Seguridad en la API
* `JWTUtil.java`: Se encarga de firmar los tokens con el secreto de la aplicación. Utiliza el usuario (login) y otros claims básicos.
* Filtros o chequeos in-line en Jersey validan que `Bearer ...` sea emitido por STA.RH y rechaza `401 Unauthorized` frente a alteraciones y manipulaciones.

---

**Siguiente Documento Sugerido:** `05_Tareas_Programadas_Quartz.md` (Cómo los jobs ejecutan la validación final del día).
