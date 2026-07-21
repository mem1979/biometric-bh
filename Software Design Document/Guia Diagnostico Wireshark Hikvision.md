# Guía Práctica: Captura y Análisis de Tráfico Hikvision con Wireshark

> **Objetivo:** Capturar byte a byte la comunicación entre el DS-K1A8503MF y el `HikvisionSocketListener` para confirmar exactamente qué está fallando en el ACK HTTP.
>
> **Entorno:** Servidor Windows, Puerto 8088, Dispositivo Hikvision DS-K1A8503MF

---

## ¿Por qué necesitamos esto?

Hemos probado más de 25 variaciones de la respuesta HTTP (v7 → v35). Sin una captura de red real, estamos "disparando en la oscuridad". La captura Wireshark nos dice **con exactitud absoluta**:

1. Los bytes que el dispositivo envía (su POST).
2. Los bytes que nuestro servidor responde.
3. Si el dispositivo descartó la respuesta o la aceptó.
4. Qué hace el dispositivo **después** de recibir nuestra respuesta (si reintenta → falló, si no → éxito).

```
[DS-K1A8503MF] ── POST /biometric/api/hikvision/event/DEV001 ──→ [Servidor:8088]
[DS-K1A8503MF] ←── HTTP/1.1 200 OK ... [respuesta del server] ── [Servidor:8088]
[Wireshark captura TODOS estos bytes en ambas direcciones]
```

---

## Método 1: Wireshark (Recomendado — GUI visual)

### Paso 1: Instalar Wireshark

1. Descarga desde: **https://www.wireshark.org/download.html**
2. Durante la instalación, acepta instalar **Npcap** (es el driver de captura de red en Windows — sin esto Wireshark no puede capturar nada).
3. Reinicia el equipo si Wireshark lo solicita.

> **¿Ya lo tienes instalado?** Escribe en PowerShell:
> ```powershell
> & "C:\Program Files\Wireshark\Wireshark.exe" --version
> ```
> Si muestra la versión, ya está instalado.

---

### Paso 2: Identificar la interfaz de red correcta

Debes capturar en la interfaz que tiene la IP del servidor (la misma que configuraste en el dispositivo Hikvision).

1. Abre **PowerShell** y ejecuta:
   ```powershell
   Get-NetIPAddress | Where-Object {$_.AddressFamily -eq "IPv4" -and $_.IPAddress -ne "127.0.0.1"} | Select-Object IPAddress, InterfaceAlias
   ```
2. Busca la IP que configuraste en el dispositivo (ej: `192.168.1.36`).
3. Anota el nombre de la interfaz (ej: `Ethernet`, `Wi-Fi`).

---

### Paso 3: Configurar el filtro de captura en Wireshark

Un filtro de captura hace que Wireshark **solo guarde** el tráfico del puerto 8088, evitando que el archivo sea enorme.

1. Abre **Wireshark**.
2. En la pantalla principal verás la lista de interfaces de red.
3. Haz clic en el ícono de engranaje o en el campo **"Capture filter"** debajo del nombre de la interfaz.
4. Escribe el siguiente filtro:
   ```
   tcp port 8088
   ```
5. Selecciona la interfaz correcta (la de la IP `192.168.1.36`).
6. Haz doble clic sobre esa interfaz para **iniciar la captura**.

---

### Paso 4: Provocar el evento del dispositivo

Con la captura activa en Wireshark:

1. Ve al dispositivo físico DS-K1A8503MF.
2. Realiza una **fichada de prueba** (pasar dedo, tarjeta o cara).
3. Observa Wireshark: en segundos deberían aparecer paquetes con la IP del dispositivo como origen.

> **Si el bucle ya está activo:** No necesitas hacer nada. Wireshark verá los reintentos automáticamente cada ~5-6 segundos.

---

### Paso 5: Detener la captura

1. Después de capturar al menos **3 ciclos de reintento** (o 1 fichada exitosa), presiona el botón de **Stop** (cuadrado rojo ■).
2. Guarda: `File → Save As → captura_hikvision_8088.pcapng`.

---

### Paso 6: Aplicar filtro de visualización

Con la captura detenida, escribe en la barra superior de Wireshark:

```
tcp.port == 8088
```

Presiona **Enter**. Ahora solo verás los paquetes del puerto 8088.

---

### Paso 7: Encontrar el flujo HTTP completo (Follow TCP Stream)

1. Busca un paquete con **Info** que diga `[SYN]` — ese es el inicio de una conexión TCP.
2. Haz **clic derecho** sobre ese paquete.
3. Selecciona: **"Follow" → "TCP Stream"**.

Se abrirá una ventana con la conversación completa. Verás algo así:

```
=== LO QUE ENVÍA EL DISPOSITIVO (en ROJO) ===
POST /biometric/api/hikvision/event/DEV001 HTTP/1.1
Host: 192.168.1.36:8088
Content-Type: application/json;charset=UTF-8
Content-Length: 387
Connection: keep-alive

{"AccessControllerEvent":{"majorEventType":5,...,"serialNo":97,...}}

=== LO QUE RESPONDE NUESTRO SERVIDOR (en AZUL) ===
HTTP/1.1 200 OK
Server: App-webs/
Date: Thu, 26 Jun 2026 14:30:01 GMT
Content-Type: application/json;charset=UTF-8
Content-Length: 75
Connection: close

{"requestURL":"/biometric/api/hikvision/event/DEV001","statusCode":1,"statusString":"OK","subStatusCode":"ok"}
```

---

### Paso 8: Qué buscar y cómo interpretarlo

#### 8A — Verificar el Request del Dispositivo (texto en ROJO)

| Qué buscar | Qué significa |
|---|---|
| `Content-Type: application/json` | Device envía JSON → rama `else` del código es correcta |
| `Content-Type: multipart/form-data; boundary=XXX` | Device envía foto + datos → el body empieza con `--XXX`, NO con `{` |
| `Content-Length: NNNN` | El body tiene exactamente NNNN bytes |
| `Connection: keep-alive` | Device prefiere mantener la conexión abierta |

#### 8B — Verificar el Response de Nuestro Servidor (texto en AZUL)

| Qué buscar | Diagnóstico |
|---|---|
| `HTTP/1.1 200 OK` como primera línea | ✅ Correcto |
| `Transfer-Encoding: chunked` presente | ❌ FALLO CRÍTICO — el device no soporta chunked |
| `Content-Length:` ausente en la respuesta | ❌ FALLO — sin longitud, el device no sabe cuándo termina |
| `Set-Cookie: JSESSIONID=...` presente | ⚠️ PROBLEMA — puede desbordar el buffer del MCU |
| `Connection: close` presente | ✅ Correcto |
| Saltos de línea `\r\n` dentro del body | ❌ Posible fallo — el parser del firmware no los tolera |
| `Content-Type: application/json;charset=UTF-8` (sin espacio) | ✅ Correcto (v35) |
| `Content-Type: application/json; charset="UTF-8"` (con espacio y comillas) | ⚠️ Riesgo — puede confundir el parser |

#### 8C — Verificar el comportamiento TCP DESPUÉS del ACK

Esta es **la prueba definitiva**: lo que hace el dispositivo después de recibir nuestra respuesta.

| Comportamiento TCP después del 200 OK | Diagnóstico |
|---|---|
| `[FIN, ACK]` del dispositivo | ✅ **ÉXITO** — el device aceptó el ACK y cerró limpiamente |
| Nuevo `[SYN]` del mismo device 5-6 segundos después | ❌ **FALLO** — el device rechazó el ACK y reintenta |
| `[RST]` del dispositivo inmediatamente | ❌ **FALLO** — el device recibió algo inesperado y abortó |
| Silencio total por 30+ segundos | ✅ **ÉXITO** — el event fue procesado correctamente |

---

## Método 2: netsh trace (Sin instalar nada extra)

Si no puedes instalar Wireshark, Windows incluye `netsh trace`.

### Paso 1: Iniciar la captura

```powershell
# Ejecutar como Administrador
New-Item -ItemType Directory -Force -Path C:\temp
netsh trace start capture=yes tracefile=C:\temp\captura_hikvision.etl maxsize=100 overwrite=yes
Write-Host "Captura iniciada. Ahora haz una fichada en el dispositivo..."
```

### Paso 2: Esperar y detener

Después de 2-3 ciclos de reintento:

```powershell
netsh trace stop
Write-Host "Archivo guardado en: C:\temp\captura_hikvision.etl"
```

### Paso 3: Convertir a formato PCAP

Descarga `etl2pcapng.exe` desde https://github.com/microsoft/etl2pcapng/releases y luego:

```powershell
.\etl2pcapng.exe C:\temp\captura_hikvision.etl C:\temp\captura_hikvision.pcapng
# Abrir el .pcapng en Wireshark con filtro: tcp.port == 8088
```

---

## Método 3: Script PowerShell — Simular el dispositivo localmente

Este script simula exactamente lo que envía el DS-K1A8503MF, sin necesidad del dispositivo físico. Úsalo para verificar que el servidor responde correctamente antes de conectar el dispositivo real.

```powershell
# ============================================================
# SIMULADOR DE EVENTO HIKVISION DS-K1A8503MF
# Ejecutar mientras HikvisionSocketListener está activo (puerto 8088)
# ============================================================

param(
    [string]$ServerIP   = "127.0.0.1",
    [int]$ServerPort    = 8088,
    [string]$DispositivoId = "DEV001",
    [int]$SerialNo      = 97,
    [string]$EmpleadoNo = "12345"
)

Write-Host "=== SIMULADOR HIKVISION DS-K1A8503MF ===" -ForegroundColor Yellow
Write-Host "Conectando a $ServerIP`:$ServerPort..." -ForegroundColor Gray

# Body JSON igual al que envía el dispositivo real
$body = "{""AccessControllerEvent"":{""majorEventType"":5,""subEventType"":75,""minor"":75," +
        """time"":""2026-06-26T14:30:01+03:00"",""serialNo"":$SerialNo," +
        """Extend"":{""netUser"":""""},""netUser"":"""",""verifyNo"":1," +
        """employeeNoString"":""$EmpleadoNo"",""currentVerifyMode"":""fp""," +
        """currentEvent"":true,""frontSerialNo"":$($SerialNo - 1),""attendanceStatus"":""undefined""}}"

$bodyBytes = [System.Text.Encoding]::UTF8.GetBytes($body)
$contentLength = $bodyBytes.Length

try {
    $client = New-Object System.Net.Sockets.TcpClient($ServerIP, $ServerPort)
    $stream = $client.GetStream()

    # Construir el HTTP POST (igual que el dispositivo real)
    $request  = "POST /biometric/api/hikvision/event/$DispositivoId HTTP/1.1`r`n"
    $request += "Host: $ServerIP`:$ServerPort`r`n"
    $request += "Content-Type: application/json;charset=UTF-8`r`n"
    $request += "Content-Length: $contentLength`r`n"
    $request += "Connection: keep-alive`r`n"
    $request += "`r`n"  # Línea en blanco: separa headers del body

    Write-Host "`n=== REQUEST ENVIADO ===" -ForegroundColor Red
    Write-Host $request -NoNewline
    Write-Host $body

    # Enviar headers
    $requestBytes = [System.Text.Encoding]::ASCII.GetBytes($request)
    $stream.Write($requestBytes, 0, $requestBytes.Length)

    # Enviar body
    $stream.Write($bodyBytes, 0, $bodyBytes.Length)
    $stream.Flush()

    # Leer la respuesta del servidor
    Start-Sleep -Milliseconds 2000
    $responseBuffer = New-Object byte[] 8192
    $bytesRead = $stream.Read($responseBuffer, 0, $responseBuffer.Length)

    Write-Host "`n=== RESPONSE RECIBIDO ===" -ForegroundColor Cyan
    $responseText = [System.Text.Encoding]::UTF8.GetString($responseBuffer, 0, $bytesRead)
    Write-Host $responseText

    # Análisis automático de la respuesta
    Write-Host "`n=== ANÁLISIS AUTOMÁTICO ===" -ForegroundColor Green
    if ($responseText -match "HTTP/1\.1 200 OK") {
        Write-Host "✅ Status: 200 OK" -ForegroundColor Green
    } else {
        Write-Host "❌ Status: NO ES 200 OK" -ForegroundColor Red
    }

    if ($responseText -match "Transfer-Encoding: chunked") {
        Write-Host "❌ Transfer-Encoding: CHUNKED detectado (el device no lo soporta)" -ForegroundColor Red
    } else {
        Write-Host "✅ Sin Transfer-Encoding: chunked" -ForegroundColor Green
    }

    if ($responseText -match "Content-Length: \d+") {
        Write-Host "✅ Content-Length presente" -ForegroundColor Green
    } else {
        Write-Host "❌ Content-Length AUSENTE" -ForegroundColor Red
    }

    if ($responseText -match "Set-Cookie") {
        Write-Host "⚠️  Set-Cookie detectado (puede causar problemas)" -ForegroundColor Yellow
    } else {
        Write-Host "✅ Sin Set-Cookie" -ForegroundColor Green
    }

    if ($responseText -match "Connection: close") {
        Write-Host "✅ Connection: close presente" -ForegroundColor Green
    } else {
        Write-Host "⚠️  Connection: close ausente" -ForegroundColor Yellow
    }

    # Verificar body de la respuesta
    $bodyMatch = $responseText -match '\{.*"statusCode"\s*:\s*1.*\}'
    if ($bodyMatch) {
        Write-Host "✅ Body contiene statusCode:1" -ForegroundColor Green
    } else {
        Write-Host "⚠️  Body no contiene statusCode:1 (puede ser aceptable si está vacío)" -ForegroundColor Yellow
    }

    $client.Close()
} catch {
    Write-Host "❌ ERROR: $_" -ForegroundColor Red
    Write-Host "Asegúrate de que el HikvisionSocketListener está activo en el puerto $ServerPort" -ForegroundColor Yellow
}
```

Guarda este script como `Test-HikvisionSimulator.ps1` y ejecútalo con:

```powershell
powershell -ExecutionPolicy Bypass -File .\Test-HikvisionSimulator.ps1
```

---

## Checklist de los 10 puntos clave

Responde estas preguntas con la captura abierta o con la salida del simulador:

```
□  1. ¿El body del POST empieza con { (JSON) o con < (XML) o con --boundary (multipart)?
□  2. ¿El Content-Type del request dice "multipart/form-data"?
□  3. ¿El body del POST contiene "serialNo" y "employeeNoString"?
□  4. ¿La primera línea del response es exactamente "HTTP/1.1 200 OK\r\n"?
□  5. ¿El response contiene "Transfer-Encoding: chunked"? (MALO si sí)
□  6. ¿El response contiene "Content-Length: NNN" con un número? (BUENO si sí)
□  7. ¿El response contiene "Set-Cookie"? (PROBLEMA si sí)
□  8. ¿El response contiene "Connection: close"? (BUENO si sí)
□  9. ¿El body del response tiene saltos de línea internos? (MALO si sí)
□ 10. ¿El device envía un nuevo SYN 5-6 segundos después? (MALO = reintenta)
```

---

## Resumen visual del proceso

```
1. Instalar Wireshark + Npcap
         ↓
2. Identificar IP del servidor  →  Get-NetIPAddress
         ↓
3. Wireshark → Filtro de captura: "tcp port 8088" → Iniciar
         ↓
4. Hacer fichada en el dispositivo (o esperar el bucle)
         ↓
5. Esperar 2-3 reintentos → Detener captura → Guardar .pcapng
         ↓
6. Filtro de visualización: "tcp.port == 8088"
         ↓
7. Clic derecho en [SYN] → "Follow TCP Stream"
         ↓
8. Responder el Checklist de 10 puntos
         ↓
9. Consultar la Tabla de Diagnóstico
         ↓
10. Aplicar corrección específica → Compilar → Repetir
```

> [!TIP]
> **Truco profesional:** En Wireshark, ve a `Edit → Preferences → Protocols → HTTP` y agrega el puerto `8088` en el campo **"TCP ports"**. Wireshark entonces parsea automáticamente el tráfico del puerto 8088 como HTTP y colorea los requests/responses, mostrando headers y body por separado.

> [!IMPORTANT]
> Para capturar correctamente, la aplicación STARH debe estar corriendo (el HikvisionSocketListener escuchando en 8088) **y** el dispositivo Hikvision debe estar configurado apuntando a la IP del servidor. Si el device está en bucle activo de reintentos, la captura mostrará los reintentos sin necesidad de una fichada adicional.
