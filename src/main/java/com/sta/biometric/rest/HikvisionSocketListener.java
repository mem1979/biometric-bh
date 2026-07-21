package com.sta.biometric.rest;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.logging.*;
import org.openxava.jpa.XPersistence;
import com.sta.biometric.servicios.HikvisionFichadaService;

/**
 * Servidor de sockets minimalista autónomo que escucha en el puerto 8088.
 * Recibe los HTTP POST de eventos enviados por el biométrico Hikvision,
 * los procesa asíncronamente en la base de datos de STARH y responde
 * escribiendo directamente en la conexión TCP con la cabecera HTTP/1.1 200 OK.
 * Esto evita las limitaciones de Tomcat que remueve la Reason Phrase 'OK'.
 */
public class HikvisionSocketListener {

    private static final Logger LOG = Logger.getLogger(HikvisionSocketListener.class.getName());
    private static ServerSocket serverSocket;
    private static boolean running = false;
    private static int port = 8088;

    public static synchronized void start(int portNo) {
        if (running) return;
        port = portNo;
        running = true;
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                LOG.info("[Hikvision-Socket] Servidor de sockets iniciado en el puerto " + port);
                while (running) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        new Thread(() -> handleClient(clientSocket), "Hikvision-Socket-Client").start();
                    } catch (IOException e) {
                        if (!running) break;
                        LOG.log(Level.WARNING, "[Hikvision-Socket] Error aceptando conexion", e);
                    }
                }
            } catch (IOException e) {
                LOG.log(Level.SEVERE, "[Hikvision-Socket] No se pudo iniciar el ServerSocket en el puerto " + port + ": " + e.getMessage());
            } catch (SecurityException se) {
                LOG.log(Level.SEVERE, "[Hikvision-Socket] Error de seguridad al iniciar ServerSocket en puerto " + port + ": " + se.getMessage());
            }
        }, "Hikvision-Socket-Listener").start();
    }

    public static synchronized void stop() {
        running = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
                LOG.info("[Hikvision-Socket] Servidor de sockets detenido.");
            } catch (IOException e) {
                LOG.log(Level.WARNING, "[Hikvision-Socket] Error cerrando ServerSocket", e);
            }
        }
    }

    private static void handleClient(Socket socket) {
        try {
            socket.setSoLinger(true, 3);
            socket.setSoTimeout(5000); // 5s timeout
        } catch (Exception e) {
            LOG.warning("[Hikvision-Socket] No se pudo establecer socket options: " + e.getMessage());
        }
        try (
            InputStream is = socket.getInputStream();
            OutputStream os = socket.getOutputStream()
        ) {
            String firstLine = readAsciiLine(is);
            if (firstLine == null) return;

            // Esperar y parsear cabeceras para obtener Content-Length
            int contentLength = 0;
            String line;
            StringBuilder headersLog = new StringBuilder();
            while ((line = readAsciiLine(is)) != null && !line.trim().isEmpty()) {
                headersLog.append(line).append("\n");
                String lowerLine = line.toLowerCase();
                if (lowerLine.startsWith("content-length:")) {
                    contentLength = Integer.parseInt(line.substring(line.indexOf(":") + 1).trim());
                }
            }
            LOG.info("[Hikvision-Socket] Headers recibidos:\n" + headersLog.toString());

            // Leer cuerpo en bytes si existe
            String body = "";
            if (contentLength > 0) {
                byte[] bodyBytes = new byte[contentLength];
                int read = 0;
                while (read < contentLength) {
                    int r = is.read(bodyBytes, read, contentLength - read);
                    if (r == -1) break;
                    read += r;
                }
                body = new String(bodyBytes, StandardCharsets.UTF_8);
            }

            LOG.info("[Hikvision-Socket] Request HTTP recibido en puerto " + port + "\n[First Line]: " + firstLine + "\n[Body]: " + body);

            // Procesar asincronamente en BD si es un request de evento
            if (firstLine.contains("POST") && firstLine.contains("/biometric/api/hikvision/event/")) {
                // Extraer el dispositivoId de la URI
                String uri = firstLine.split(" ")[1];
                String dispositivoId = uri.substring(uri.lastIndexOf("/") + 1);
                
                final String finalBody = body;
                final String finalDispositivoId = dispositivoId;
                
                HikvisionThreadPool.submit(() -> procesarEnBD(finalDispositivoId, finalBody));
            }

            // Extraer requestPath para el requestURL del ACK
            String requestPath = "/biometric/api/hikvision/event/unknown";
            String[] parts = firstLine.split(" ");
            if (parts.length > 1) {
                requestPath = parts[1];
            }

            // Determinar formato de respuesta
            String responseBody;
            String contentType;
            
            if (body.trim().startsWith("<")) {
                responseBody = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><ResponseStatus version=\"2.0\" xmlns=\"http://www.isapi.org/ver20/XMLSchema\"><requestURL>" + requestPath + "</requestURL><statusCode>1</statusCode><statusString>OK</statusString><subStatusCode>ok</subStatusCode></ResponseStatus>";
                contentType = "application/xml;charset=UTF-8";
            } else {
                responseBody = "{\"requestURL\":\"" + requestPath + "\",\"statusCode\":1,\"statusString\":\"OK\",\"subStatusCode\":\"ok\"}";
                contentType = "application/json;charset=UTF-8";
            }
            
            byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);
            
            java.time.ZonedDateTime now = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC);
            String dateStr = java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME.format(now);

            String httpResponse = "HTTP/1.1 200 OK\r\n" +
                                 "Server: App-webs/\r\n" +
                                 "Date: " + dateStr + "\r\n" +
                                 "Content-Type: " + contentType + "\r\n" +
                                 "Content-Length: " + responseBytes.length + "\r\n" +
                                 "Connection: close\r\n" +
                                 "\r\n";

            os.write(httpResponse.getBytes(StandardCharsets.UTF_8));
            os.write(responseBytes);
            os.flush();

            // Cierre ordenado de la conexión TCP (Graceful Shutdown)
            try {
                socket.shutdownOutput();
                byte[] discard = new byte[512];
                while (is.read(discard) != -1) {
                    // Consumir
                }
            } catch (SocketTimeoutException ste) {
                // Timeout esperado
            } catch (Exception ex) {
                // Omitir
            }

        } catch (Exception e) {
            LOG.log(Level.WARNING, "[Hikvision-Socket] Error procesando request de cliente", e);
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                // Omitir
            }
        }
    }

    private static String readAsciiLine(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int b;
        while ((b = is.read()) != -1) {
            if (b == '\n') {
                break;
            }
            if (b != '\r') {
                baos.write(b);
            }
        }
        if (baos.size() == 0 && b == -1) {
            return null;
        }
        return baos.toString("US-ASCII");
    }

    private static void procesarEnBD(String dispositivoId, String body) {
        try {
            XPersistence.getManager();
            LOG.info("[Hikvision-Socket] Procesando fichada en segundo plano...");

            HikvisionEventParser.EventData eventData = HikvisionEventParser.extraerDatosEvento(body);
            if (eventData.isValido()) {
                if (eventData.getSerialNo() == 9999 && "9999".equals(eventData.getEmployeeNo())) {
                    LOG.info("[Hikvision-Socket] Evento de selftest (9999) detectado. Omitiendo BD.");
                    return;
                }
                if (eventData.getMajorEventType() == 5) {
                    String resultado = HikvisionFichadaService.registrarFichada(
                            eventData.getEmployeeNo(),
                            eventData.getTimeStr(),
                            eventData.getSerialNo(),
                            dispositivoId);
                    XPersistence.commit();
                    LOG.info("[Hikvision-Socket] Fichada registrada en BD: empleado=" + eventData.getEmployeeNo() 
                            + " serial=" + eventData.getSerialNo() + " res=" + resultado);
                } else {
                    LOG.info("[Hikvision-Socket] Evento omitido (major=" + eventData.getMajorEventType() 
                            + ", serial=" + eventData.getSerialNo() + ")");
                }
            } else {
                LOG.warning("[Hikvision-Socket] No se pudo extraer datos válidos del body.");
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "[Hikvision-Socket] Error al procesar fichada en BD", e);
            try { XPersistence.rollback(); } catch (Exception rx) {}
        } finally {
            try { XPersistence.reset(); } catch (Exception ex) {}
        }
    }
}
