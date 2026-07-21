# **Integración Hikvision DS-K1A8503MF con Sistema RRHH OpenXava** 

## **Objetivo** 

Integrar el reloj biométrico Hikvision DS-K1A8503MF directamente con una aplicación RRHH desarrollada en OpenXava, sin utilizar HikCentral, iVMS, BioTime ni software intermediario. 

La integración se realizará utilizando ISAPI (Intelligent Security API) expuesto por el dispositivo. 

## **Equipo Validado** 

Modelo: 

DS-K1A8503MF 

Firmware: 

V1.4.0 build 230403 

IP de pruebas: 

192.168.1.37 

Autenticación: 

HTTP Digest Authentication 

Usuario: 

admin 

## **Capacidades Verificadas** 

## **DeviceInfo** 

Endpoint: 

GET /ISAPI/System/deviceInfo 

Resultado: 

1 

Operativo. 

Devuelve: 

- Modelo 

- Firmware 

- MAC 

- Número de serie 

Ejemplo: 

<model>DS-K1A8503MF</model> 

## **AccessControl** 

Endpoint: 

GET /ISAPI/AccessControl/capabilities 

Resultado: 

Operativo. 

Capacidades verificadas: 

isSupportUserInfo=true 

isSupportFingerPrintCfg=true 

isSupportAcsEvent=true 

isSupportAcsEventTotalNum=true 

## **Gestión de Usuarios** 

## **Buscar Usuarios** 

Endpoint: 

POST /ISAPI/AccessControl/UserInfo/Search?format=json 

Request: 

{ "UserInfoSearchCond": { "searchID": "1", "searchResultPosition": 0, "maxResults": 100 } } 

Respuesta real: 

2 

{ "employeeNo": "100", "name": "Marcelo", "userVerifyMode": "fp" } 

Campos relevantes: 

employeeNo 

name 

userType 

userVerifyMode 

Valid.beginTime 

Valid.endTime 

## **Capacidades UserInfo** 

Endpoint: 

GET /ISAPI/AccessControl/UserInfo/capabilities 

Respuesta: 

supportFunction: 

post 

put 

delete 

get 

setUp 

Conclusión: 

El dispositivo soporta: 

- Alta de usuario 

- Modificación de usuario 

- Eliminación de usuario 

- Consulta de usuario 

3 

## **Gestión de Huellas** 

Capacidad verificada: 

isSupportFingerPrintCfg=true 

Capacidad verificada: 

isSupportCaptureFingerPrint=true 

Capacidad verificada: 

isSupportFingerPrintDelete=true 

Conclusión: 

El firmware soporta gestión biométrica vía ISAPI. 

No se realizaron pruebas de alta/baja de huellas. 

## **Obtención de Fichadas** 

## **Endpoint Principal** 

POST /ISAPI/AccessControl/AcsEvent?format=json 

Request: 

{ "AcsEventCond": { "searchID": "1", "searchResultPosition": 0, "maxResults": 50, "major": 0, "minor": 0 } } 

## **Respuesta Real** 

{ "employeeNoString":"100", "serialNo":13, "major":5, "minor":38, "time":"2026-06-19T04:36:29+08:00" } 

## **Campos Importantes** 

employeeNoString 

Identificador del empleado. 

Corresponde a UserInfo.employeeNo. 

4 

serialNo 

Identificador único del evento. 

Debe utilizarse para evitar duplicados. 

time 

Fecha y hora del evento. 

major 

Categoría principal del evento. 

minor 

Subtipo de evento. 

attendanceStatus 

Estado de asistencia. 

En las pruebas: 

attendanceStatus="undefined" 

## **Estrategia Recomendada para RRHH** 

## **Tabla Empleado** 

employeeNo 

nombre 

activo 

fechaAlta 

## **Tabla Fichada** 

serialNo 

5 

employeeNo 

fechaHora 

major 

minor 

procesado 

## **Sincronización de Usuarios** 

Proceso: 

1. Ejecutar UserInfo/Search. 

2. Importar usuarios. 

3. Actualizar empleados existentes. 

4. Crear empleados inexistentes. 

OpenXava será el maestro de datos. 

## **Sincronización de Fichadas** 

Proceso: 

1. Consultar AcsEvent. 

2. Obtener serialNo. 

3. Ignorar seriales ya procesados. 

4. Registrar nuevas fichadas. 

Frecuencia sugerida: 

30 segundos. 

## **Integración Push (Tiempo Real)** 

Capacidad verificada. 

Endpoint: 

GET /ISAPI/Event/notification/httpHosts/capabilities 

Respuesta: 

6 

protocolType: 

HTTP 

EHome 

parameterFormatType: 

XML 

JSON 

querystring 

## **Configuración Actual** 

Endpoint: 

GET /ISAPI/Event/notification/httpHosts 

Resultado: 

2 slots configurables. 

## **Arquitectura Recomendada** 

[Dispositivo DS-K1A8503MF] 
       │ (HTTP plano, IP numérica, puerto 8088)
       ▼
[Proxy Local de Relevo (Nginx)] (Traduce a HTTPS, puerto 443, dominio)
       │
       ▼
[Sistema RRHH OpenXava (Cloud)]

## **Estrategia de Producción Recomendada** 

Mecanismo principal: 
HTTP Host Push *(a través de Proxy local Nginx)*

Mecanismo de respaldo: 
AcsEvent Polling *(vía script configure_device.ps1)*

Ventajas: 
- No se pierden eventos. 
- Recuperación automática ante caídas. 
- Trazabilidad completa. 

## **Conclusión** 

El dispositivo DS-K1A8503MF fue validado exitosamente. 

Capacidades comprobadas: 
- ISAPI operativo 
- Digest Authentication 
- UserInfo 
- AcsEvent 
- HTTP Host 
- JSON 
- Gestión de usuarios 
- Consulta de fichadas 
- Sincronización de empleados 

**Nota Crítica sobre la Arquitectura de Producción:** La integración directa directa de extremo a extremo (`Reloj ↔ Nube`) **no es viable sin un proxy intermedio** debido a las severas limitaciones del firmware del dispositivo: no soporta cifrado HTTPS, no permite direccionamiento DNS por hostname y bloquea la salida de eventos por puertos menores a 1024 (ej: 443). Es mandatorio desplegar un **Proxy Local de Relevo (Nginx)** en la red de la sucursal para actuar como puente seguro.

8 
