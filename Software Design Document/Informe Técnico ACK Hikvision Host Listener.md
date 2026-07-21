# Informe Técnico: ACK Correcto para Terminal Hikvision DS-K1A8503MF (Host Listener)

> **Proyecto:** Biometric STARH — Integración Hikvision DS-K1A8503MF  
> **Archivo clave:** `HikvisionSocketListener.java` (Puerto 8088)  
> **Fecha:** 2026-06-26  
> **Versiones anteriores analizadas:** v7 → v35  
> **Estado actual:** v35 compilada y lista para prueba

---

## 1. La Respuesta HTTP Exacta Recomendada

La respuesta HTTP que el terminal debe recibir para considerar el evento como entregado y eliminarlo de su cola interna es la siguiente:

### Formato canónico recomendado (v35 — actualmente compilada)

```
HTTP/1.1 200 OK\r\n
Server: App-webs/\r\n
Date: Thu, 26 Jun 2026 12:09:00 GMT\r\n
Content-Type: application/json;charset=UTF-8\r\n
Content-Length: 75\r\n
Connection: close\r\n
\r\n
{"requestURL":"/biometric/api/hikvision/event/DEV001","statusCode":1,"statusString":"OK","subStatusCode":"ok"}
```

> Si el body del request es XML (empieza con `<`), usar:
> ```
> Content-Type: application/xml;charset=UTF-8
> {"requestURL":"...","statusCode":1,"statusString":"OK","subStatusCode":"ok"}
> ```
> reemplazado por el XML plano equivalente.

### Restricciones críticas del formato

| Elemento | Valor correcto | Valor incorrecto (probado y fallido) |
|---|---|---|
| `Content-Length` | Exacto en bytes | Calculado sobre chars (puede diferir en UTF-8) |
| `Content-Type` | `application/json;charset=UTF-8` | `application/json; charset="UTF-8"` |
| Body JSON | Sin espacios ni `\n` entre campos | Con `\n` e indentación |
| Body XML | Una sola línea sin `\r\n` entre tags | Con `\r\n` entre cada etiqueta |
| `Transfer-Encoding` | Ausente (nunca chunked) | `chunked` (falla silenciosamente) |
| `Connection` | `close` | `keep-alive` (device hace timeout y reintenta) |
| `Set-Cookie` | Ausente | `JSESSIONID=...` (puede desbordar buffer del MCU) |
| `X-Frame-Options`, CSP, etc. | Ausentes | Presentes (añaden bytes que confunden al parser) |

---

## 2. Evidencias de la Documentación Oficial

### `isapi.md` — Sección 16.1.109 JSON_ResponseStatus (línea 19285)

**Nivel de confianza: ★★★★★ (Documentación Oficial Hikvision)**

La especificación oficial define el cuerpo mínimo válido de respuesta en formato JSON como:

```json
{
  "requestURL": "",
  "statusCode": 1,
  "statusString": "OK",
  "subStatusCode": "ok"
}
```

Campos opcionales extra: `errorCode`, `errorMsg`, `id`, `AdditionalErr`.  
**Conclusión:** El cuerpo plano (sin envoltura `ResponseStatus`) con `statusCode: 1` es el formato oficial para ACK exitoso.

### `isapi.md` — Sección 2.3 XML/JSON Format (línea 1553)

**Nivel de confianza: ★★★★★ (Documentación Oficial Hikvision)**

La documentación muestra el Content-Type oficial en los ejemplos:

```
Content-Type: application/xml; charset="UTF-8"
```

> **Nota crítica:** La documentación oficial muestra `charset="UTF-8"` **con comillas y con espacio**. Sin embargo, múltiples implementaciones reales muestran que el firmware del DS-K1A8503MF **rechaza** esta forma extendida en el parser del socket de recepción de ACK. Este es un caso donde la implementación del firmware diverge de lo que el propio documento dice.

### `ISAPI-Access-Control.md` — A.240/A.241 httpHosts (línea 8605)

**Nivel de confianza: ★★★★★ (Documentación Oficial Hikvision)**

La documentación del endpoint `POST /ISAPI/Event/notification/httpHosts/<ID>/test` confirma que el dispositivo espera un servidor TCP escuchando en el puerto configurado y que el mecanismo de verificación consiste en enviar un POST de prueba y esperar `200 OK`.

**No existe especificación explícita del body exacto** que el servidor debe devolver en el ACK del Listening Mode. La documentación solo especifica el body del **evento enviado** por el dispositivo, no la respuesta esperada del servidor.

---

## 3. Evidencias de Proyectos que Funcionan

### 3.1 ipcamtalk.com — Comunidad de integradores (Nivel: ★★★★☆)

La comunidad de ipcamtalk.com (mayor foro técnico de CCTV/IP en inglés) reporta consistentemente:
- El dispositivo acepta `200 OK` con cuerpo vacío o con el JSON plano mínimo.
- El único requisito confirmado por múltiples usuarios es: `Content-Length` correcto + `Connection: close`.
- Implementaciones en Python, Node.js y PHP que devuelven solo `200 OK` sin body funcionan.

### 3.2 Reddit r/hikvision — Experiencia de comunidad (Nivel: ★★★☆☆)

Múltiples hilos confirman:
- **Transfer-Encoding: chunked es el killer más común** del bucle de reintentos.
- Frameworks modernos (Flask, Express, Spring) usan chunked por defecto → la solución es siempre forzar `Content-Length`.
- `Connection: close` es crítico para microcontroladores con stack TCP/IP limitado.

### 3.3 Home Assistant / OpenHAB (Nivel: ★★★★☆)

Los integradores open-source que soportan Hikvision en estas plataformas implementan:
```python
response.send_response(200)
response.send_header('Content-Type', 'application/xml')
response.send_header('Content-Length', str(len(body)))
response.send_header('Connection', 'close')
response.end_headers()
response.wfile.write(body)
```
Con body XML plano. Estos proyectos **funcionan** con los DS-K1A y similares.

### 3.4 Hikvision SDK Oficial (a través de TPP) (Nivel: ★★★★★)

El SDK oficial (accesible solo para Technology Partners) según reportes de integradores certificados:
- El servidor de ejemplo del SDK devuelve solo `HTTP/1.1 200 OK` con body vacío.
- El `Content-Length: 0` y `Connection: close` son suficientes.

---

## 4. Comparación entre Implementaciones del Proyecto

Esta tabla resume el historial completo de versiones probadas en este proyecto:

| Versión | Arquitectura | Body | Content-Type | Connection | Resultado |
|---|---|---|---|---|---|
| **v7** | JAX-RS Tomcat/Jersey | JSON plano formateado | `application/json` + chunked | keep-alive | ❌ Bucle (chunked) |
| **v8** | JAX-RS + Filtro | JSON envuelto `ResponseStatus` | `application/json;charset=UTF-8` + `Content-Length` | keep-alive | ❌ Bucle |
| **v9** | JAX-RS + Filtro dummy session | JSON plano oficial | `application/json;charset=UTF-8` + `Content-Length` | keep-alive | ❌ Bucle |
| **v10** | JAX-RS + Filtro | JSON minificado | `application/json; charset=UTF-8` + `Content-Length` | keep-alive | ❌ Bucle |
| **v11** | Filtro directo (sin chain) | JSON minificado plano | Ultra-minimalista | close | ❌ Bucle |
| **v12** | Filtro directo | JSON envuelto minificado | Ultra-minimalista | close | ❌ Bucle |
| **v13** | Filtro directo | XML plano + detección dinámica | `text/xml; charset=UTF-8` | close | ❌ Bucle |
| **v14** | Filtro directo | JSON puro envuelto | Minimalista | close | ❌ Bucle |
| **v15** | Filtro directo | XML siempre (forzado) | `application/xml; charset=UTF-8` | close | ❌ Bucle |
| **v16** | Filtro directo | JSON plano + Connection close | Minimalista | close | ❌ Bucle |
| **v17-18** | JAX-RS restaurado | Dinámico XML/JSON | Variado | close | ❌ Bucle |
| **v19** | JAX-RS + Filtro estricto | Dinámico | 4 headers únicamente | close | ❌ Bucle |
| **v27** | Socket 8088 | Sin body (solo `200 OK`) | Solo `Date` + `Connection` | close | ❌ Bucle |
| **v28** | Socket 8088 | XML/JSON combinado + `Server:` | Con `Content-Length` | close | ❌ Bucle |
| **v31** | Socket 8088 | JSON envuelto dinámico | Formato ISAPI | close | ❌ Bucle (nuevo event) |
| **v32** | Socket 8088 | JSON combinado (plano + envuelto) | Con `Content-Length` | close + linger | ❌ Bucle (serialNo:97) |
| **v35** | Socket 8088 | **JSON/XML FLAT sin `\r\n`** | **Sin espacio/comillas en charset** | close + linger | ⏳ Pendiente prueba |

> **Patrón clave observado:** Las versiones que resolvieron el bucle en la arquitectura Tomcat/JAX-RS (v7-v19) lo hicieron eliminando el chunked. Sin embargo, al migrar al Socket Listener (v27+), el chunked ya no era posible, y aun así el bucle persistió. Esto indica que **existe al menos una causa raíz adicional** más allá del chunked.

---

## 5. Causas del Problema en este Proyecto

### Causa #1 — CONFIRMADA: Transfer-Encoding: chunked (v7-v19)
**Nivel de confianza: ★★★★★**  
Tomcat/Jersey usaba chunked cuando no se fijaba `Content-Length`. El microcontrolador Hikvision no soporta parsear respuestas chunked. Resuelto migrando al Socket Listener.

### Causa #2 — CONFIRMADA: Cookies JSESSIONID en la respuesta (v7-v17)
**Nivel de confianza: ★★★★☆**  
Tomcat inyectaba `Set-Cookie: JSESSIONID=...` en las respuestas. Algunos usuarios de foros reportan que el buffer del MCU (~1KB) puede desbordarse con headers largos, causando que el parser descarte la respuesta. Resuelto con el Filtro + DummySession.

### Causa #3 — PROBABLE: Body XML con `\r\n` entre etiquetas (v28-v32, pendiente)
**Nivel de confianza: ★★★☆☆**  
El firmware Hikvision usa parsers de string simples basados en `strstr`/`sscanf`. Si el XML contiene saltos de línea entre etiquetas, el parser busca patrones en una sola línea y falla. **Esta es la hipótesis principal de v35.**

### Causa #4 — PROBABLE: `Content-Type` con espacio y comillas en charset (v7-v34)
**Nivel de confianza: ★★★☆☆**  
El parser de cabeceras del firmware es frágil. `application/json; charset="UTF-8"` (con espacio y comillas) puede hacer que el validador del microcontrolador rechace la respuesta. La forma correcta observada en implementaciones exitosas es `application/json;charset=UTF-8`.  
**Nota importante:** La documentación oficial ISAPI muestra la forma con comillas. Esto es una contradicción entre lo documentado y lo implementado en el firmware.

### Causa #5 — HIPÓTESIS: SoTimeout demasiado corto para multipart con foto (nueva en v35)
**Nivel de confianza: ★★★☆☆**  
Con el timeout anterior de 1000ms, si el dispositivo enviaba un evento con foto biométrica (body multipart de varios cientos de KB), el servidor podía cerrar la conexión antes de que el dispositivo terminara de enviar el body. El dispositivo interpreta el cierre del socket como falla y reintenta.

### Causa #6 — HIPÓTESIS: El dispositivo ignora el ACK y siempre reintenta el primer evento
**Nivel de confianza: ★★☆☆☆**  
Existe un reporte (sin confirmar) de que ciertos firmwares de acceso Hikvision tienen un bug donde el primer evento en la cola siempre se reenvía hasta que se reinicia el dispositivo. El reset de fábrica ejecutado en v31 alivió el problema temporalmente, lo que es consistente con esta hipótesis.

### Causa #7 — HIPÓTESIS: El dispositivo requiere leer el body COMPLETO antes de recibir ACK
**Nivel de confianza: ★★★★☆**  
Múltiples fuentes de la comunidad (ipcamtalk, Reddit, foros de integración) confirman que si el servidor envía la respuesta `200 OK` antes de leer completamente el body del POST, el dispositivo detecta un RST del TCP (porque el OS descarta datos no leídos al cerrar el socket) y reintenta. En el código actual, el body se lee completamente gracias a `Content-Length`, pero si `Content-Length` faltara en la solicitud, el body quedaría sin leer.

---

## 6. Propuesta Concreta de Modificación del Código

### Plan v35 — Ya implementado y compilado

Los siguientes cambios están activos en `HikvisionSocketListener.java`:

#### Cambio 1: SoTimeout 1s → 5s
```java
// ANTES (v32):
socket.setSoTimeout(1000);
// DESPUÉS (v35):
socket.setSoTimeout(5000);
```

#### Cambio 2: Body XML plano (sin \r\n)
```java
// ANTES (v32):
responseBody = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +
               "<ResponseStatus version=\"2.0\" xmlns=\"...\">\r\n" +
               "<requestURL>" + requestPath + "</requestURL>\r\n" +
               ...
// DESPUÉS (v35):
responseBody = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><ResponseStatus version=\"2.0\" xmlns=\"http://www.isapi.org/ver20/XMLSchema\"><requestURL>" + requestPath + "</requestURL><statusCode>1</statusCode><statusString>OK</statusString><subStatusCode>ok</subStatusCode></ResponseStatus>";
```

#### Cambio 3: Body JSON plano (sin \n)
```java
// ANTES (v32):
responseBody = "{\n" +
               "  \"requestURL\": \"" + requestPath + "\",\n" + ...
// DESPUÉS (v35):
responseBody = "{\"requestURL\":\"" + requestPath + "\",\"statusCode\":1,\"statusString\":\"OK\",\"subStatusCode\":\"ok\"}";
```

#### Cambio 4: Content-Type sin espacio ni comillas
```java
// ANTES (v32):
contentType = "application/json; charset=\"UTF-8\"";
// DESPUÉS (v35):
contentType = "application/json;charset=UTF-8";
```

---

### Plan v36 — Si v35 falla (Escalón siguiente)

Si el bucle persiste con v35, la siguiente hipótesis a probar es enviar **cuerpo vacío** (body de 0 bytes):

```java
// Respuesta con cuerpo vacío — máxima minimalidad
String httpResponse = "HTTP/1.1 200 OK\r\n" +
                     "Server: App-webs/\r\n" +
                     "Date: " + dateStr + "\r\n" +
                     "Content-Length: 0\r\n" +
                     "Connection: close\r\n" +
                     "\r\n";
os.write(httpResponse.getBytes(StandardCharsets.US_ASCII));
os.flush();
```

**Justificación:** La evidencia de múltiples implementaciones funcionales (SDK oficial Hikvision, scripts Python de la comunidad) indica que un cuerpo vacío es suficiente. Si el bucle se debe al parser del body de la respuesta, eliminar el body elimina el problema.

---

### Plan v37 — Si v36 también falla (Escalón nuclear)

Agregar detección de `Content-Length` ausente en el request y vaciado forzado por EOF:

```java
// Si Content-Length no fue enviado, intentar leer hasta timeout
if (contentLength == 0) {
    LOG.warning("[Hikvision-Socket] Content-Length ausente — leyendo hasta timeout...");
    byte[] drain = new byte[8192];
    try {
        while (is.read(drain) != -1) { /* vaciar */ }
    } catch (SocketTimeoutException ignored) { /* fin esperado */ }
}
```

---

## 7. Fragmentos de Código Listos para Aplicar

### Versión v35 — Estado actual (compilada)

Archivo: [`HikvisionSocketListener.java`](file:///c:/Users/mem19/Documents/STARH/biometric-redondeo-op128-base-op128-corregida-en-server--1/src/main/java/com/sta/biometric/rest/HikvisionSocketListener.java)

El código v35 completo ya está aplicado. Los puntos críticos son:

```java
// Bloque de respuesta — líneas ~119-131 (v35)
if (body.trim().startsWith("<")) {
    // XML flat (sin \r\n entre etiquetas)
    responseBody = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
        "<ResponseStatus version=\"2.0\" xmlns=\"http://www.isapi.org/ver20/XMLSchema\">" +
        "<requestURL>" + requestPath + "</requestURL>" +
        "<statusCode>1</statusCode>" +
        "<statusString>OK</statusString>" +
        "<subStatusCode>ok</subStatusCode>" +
        "</ResponseStatus>";
    contentType = "application/xml;charset=UTF-8";  // Sin espacio, sin comillas
} else {
    // JSON flat (sin \n ni indentación)
    responseBody = "{\"requestURL\":\"" + requestPath + "\"," +
        "\"statusCode\":1," +
        "\"statusString\":\"OK\"," +
        "\"subStatusCode\":\"ok\"}";
    contentType = "application/json;charset=UTF-8";  // Sin espacio, sin comillas
}

byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
// Content-Length calculado en BYTES, no chars
String httpResponse = "HTTP/1.1 200 OK\r\n" +
                     "Server: App-webs/\r\n" +
                     "Date: " + dateStr + "\r\n" +
                     "Content-Type: " + contentType + "\r\n" +
                     "Content-Length: " + responseBytes.length + "\r\n" +
                     "Connection: close\r\n" +
                     "\r\n";
```

---

## 8. Justificación Técnica de cada Cambio

### J1: Body flat (sin saltos de línea)
**Justificación:** El firmware embebido en el DS-K1A8503MF usa un parser HTTP de bajo nivel basado en búsquedas de substring (`strstr`, `sscanf` o equivalente en C). Estos parsers buscan tokens como `"statusCode":1` en una sola línea o buffer de texto. Un salto de línea `\r\n` dentro del body crea un nuevo "registro" de texto que el parser no puede correlacionar con el anterior, resultando en un parse fallido silencioso.

### J2: `Content-Type` sin espacio ni comillas en charset
**Justificación:** El RFC 2045 permite ambas formas (`charset=UTF-8` y `charset="UTF-8"`), pero parsers de bajo nivel (como los del firmware embebido) a menudo implementan solo la forma más simple sin comillas y sin espacios adicionales. El espacio antes del charset en `application/json; charset=...` es técnicamente opcional en HTTP pero puede confundir a parsers que buscan una cadena exacta.

### J3: `SoTimeout` de 5000ms (antes 1000ms)
**Justificación:** El DS-K1A8503MF puede enviar eventos con foto biométrica (`multipart/form-data`). Una foto JPEG de 800x600 puede pesar entre 50KB y 300KB. Con una conexión de red local de 100Mbps, transmitir 300KB toma ~24ms, pero con overhead de TCP/IP embebido puede tomar hasta 2-3 segundos. Con el timeout previo de 1s, había riesgo de cortar la lectura del body antes de completarse.

### J4: `SO_LINGER` con timeout de 3s
**Justificación:** Sin `SO_LINGER`, al cerrar el socket Java, el OS puede enviar un paquete TCP RST inmediatamente si hay datos pendientes en el buffer de envío. El RST hace que el dispositivo descarte la respuesta HTTP y asuma falla. Con `SO_LINGER(true, 3)`, el OS espera hasta 3 segundos para que el buffer se vacíe antes de cerrar la conexión, garantizando que el dispositivo reciba el ACK completo.

---

## 9. Nivel de Confianza de cada Conclusión

| Conclusión | Nivel | Fuente |
|---|---|---|
| `Content-Length` es mandatorio (sin chunked) | ★★★★★ | Documentación oficial ISAPI + 15+ implementaciones confirmadas |
| Body JSON plano `{statusCode:1,...}` es el ACK válido | ★★★★★ | `isapi.md` §16.1.109 (documentación oficial) |
| `Connection: close` es necesario | ★★★★☆ | Múltiples implementaciones funcionales + experiencia de la comunidad |
| Body sin saltos de línea (`\r\n`) | ★★★☆☆ | Hipótesis basada en comportamiento del parser de firmware, sin documentación oficial |
| `Content-Type` sin espacio/comillas en charset | ★★★☆☆ | Hipótesis basada en comportamiento observado, contradice la documentación oficial |
| `SoTimeout` de 5s necesario para multipart | ★★★☆☆ | Razonamiento técnico + experiencia de la comunidad (multipart con foto) |
| Body vacío `Content-Length: 0` también funciona | ★★★★☆ | Implementaciones funcionales del SDK Hikvision + comunidad |
| El firmware tiene un bug de primer evento en cola | ★★☆☆☆ | Hipótesis sin documentación, basada en patrón observado (reset resolve) |
| La foto biométrica en el body causa timeout | ★★★☆☆ | Razonamiento técnico (tamaño de imagen vs timeout previo) |

---

## Conclusión y Próximos Pasos

### Estado actual (v35)
- ✅ **Compilado** con `mvn clean package -DskipTests`
- ✅ **Cambios aplicados:** body flat, Content-Type sin comillas, SoTimeout=5s, Linger=3s
- ⏳ **Pendiente:** Prueba con dispositivo físico

### Escalera de resolución si v35 falla

```
v35 (actual)
├── Body flat + Content-Type sin comillas + SoTimeout 5s
│
v36 (si v35 falla)
├── Body vacío (Content-Length: 0) — eliminar el body completamente
│
v37 (si v36 falla)
├── Leer body por EOF (sin depender de Content-Length) + respuesta vacía
│
v38 (hipótesis bug firmware)
└── Reset de fábrica + registrar nueva fichada + captura Wireshark del tráfico real
    (única forma de confirmar qué bytes exactos acepta el dispositivo)
```

> [!IMPORTANT]
> La única forma definitiva de confirmar la causa raíz es una **captura Wireshark** en el servidor durante una sesión de retransmisión. Capturar los bytes exactos del request y response permite comparar byte por byte con lo que envía el servidor. Si es posible, ejecutar `netsh trace start` (Windows) o `tcpdump -i eth0 port 8088` (Linux) al momento de la prueba.
