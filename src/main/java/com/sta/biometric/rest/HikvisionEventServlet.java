package com.sta.biometric.rest;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.openxava.jpa.XPersistence;

/**
 * Servlet dedicado para recibir eventos de los dispositivos Hikvision.
 * Mapeado directamente para evitar el paso por Jersey JAX-RS y el overhead de procesamiento.
 * Controla minuciosamente los headers de respuesta (Content-Length exacto, Connection: close)
 * para evitar el ACK Loop y el consumo excesivo de memoria en producción.
 */
public class HikvisionEventServlet extends HttpServlet {
    private static final Logger LOG = Logger.getLogger(HikvisionEventServlet.class.getName());

    @Override
    public void init() throws ServletException {
        LOG.info("[Hikvision-Servlet] Inicializado.");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        
        String uri = req.getRequestURI();
        LOG.info("[Hikvision-Servlet] POST recibido de URI: " + uri);

        // 1. Leer el body en bruto
        int contentLength = req.getContentLength();
        if (contentLength > 1024 * 1024) { // Límite de seguridad 1MB
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Payload too large");
            return;
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int bytesRead;
        InputStream is = req.getInputStream();
        int totalRead = 0;
        while ((bytesRead = is.read(buffer)) != -1) {
            totalRead += bytesRead;
            if (totalRead > 1024 * 1024) {
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Payload too large");
                return;
            }
            baos.write(buffer, 0, bytesRead);
        }
        String body = baos.toString(StandardCharsets.UTF_8.name());

        LOG.fine("[Hikvision-Servlet] Body recibido:\n" + body);

        // 2. Extraer ID del dispositivo
        String dispositivoId = "unknown";
        int lastSlash = uri.lastIndexOf("/");
        if (lastSlash >= 0 && lastSlash < uri.length() - 1) {
            dispositivoId = uri.substring(lastSlash + 1);
        }

        final String finalDispositivoId = dispositivoId;
        final String finalBody = body;

        // 3. Despachar procesamiento asíncrono BD en el pool limitado
        HikvisionThreadPool.submit(() -> {
            try {
                XPersistence.getManager();
                HikvisionEventParser.EventData eventData = HikvisionEventParser.extraerDatosEvento(finalBody);
                if (eventData.isValido()) {
                    // Omitir selftest
                    if (eventData.getSerialNo() == 9999 && "9999".equals(eventData.getEmployeeNo())) {
                        LOG.info("[Hikvision-Servlet] Evento de selftest (9999). Omitiendo persistencia.");
                        return;
                    }
                    if (eventData.getMajorEventType() == 5) {
                        String resultado = com.sta.biometric.servicios.HikvisionFichadaService.registrarFichada(
                            eventData.getEmployeeNo(),
                            eventData.getTimeStr(),
                            eventData.getSerialNo(),
                            finalDispositivoId
                        );
                        XPersistence.commit();
                        LOG.info("[Hikvision-Servlet] Fichada registrada: emp=" + eventData.getEmployeeNo() 
                                + ", serial=" + eventData.getSerialNo() + ", res=" + resultado);
                    } else {
                        LOG.info("[Hikvision-Servlet] Evento omitido (majorEventType=" + eventData.getMajorEventType() + ")");
                    }
                } else {
                    LOG.warning("[Hikvision-Servlet] Datos de evento inválidos en el parsing.");
                }
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "[Hikvision-Servlet] Error al procesar fichada en BD", e);
                try {
                    XPersistence.rollback();
                } catch (Exception rx) {
                    LOG.warning("[Hikvision-Servlet] Error al hacer rollback: " + rx.getMessage());
                }
            } finally {
                try {
                    XPersistence.reset();
                } catch (Exception ex) {
                    LOG.warning("[Hikvision-Servlet] Error en reset JPA: " + ex.getMessage());
                }
            }
        });

        // 4. Generar la respuesta ACK flat esperada por el firmware
        String responseBody;
        String contentType;
        if (body.trim().startsWith("<")) {
            responseBody = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><ResponseStatus version=\"2.0\" xmlns=\"http://www.isapi.org/ver20/XMLSchema\"><requestURL>" + uri + "</requestURL><statusCode>1</statusCode><statusString>OK</statusString><subStatusCode>ok</subStatusCode></ResponseStatus>";
            contentType = "application/xml;charset=UTF-8";
        } else {
            responseBody = "{\"requestURL\":\"" + uri + "\",\"statusCode\":1,\"statusString\":\"OK\",\"subStatusCode\":\"ok\"}";
            contentType = "application/json;charset=UTF-8";
        }

        byte[] responseBytes = responseBody.getBytes(StandardCharsets.UTF_8);

        // 5. Configurar respuesta HTTP y forzar cerrado de conexión
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setContentType(contentType);
        resp.setContentLength(responseBytes.length);
        resp.setHeader("Connection", "close");
        resp.setHeader("Server", "App-webs/");

        // Escribir bytes y hacer flush inmediato para comprometer la respuesta
        OutputStream os = resp.getOutputStream();
        os.write(responseBytes);
        os.flush();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String uri = req.getRequestURI();
        if (uri.endsWith("/selftest")) {
            LOG.info("[Hikvision-Servlet] GET /selftest recibido.");
            StringBuilder sb = new StringBuilder();
            sb.append("--- HIKVISION SERVLET SELFTEST ---\n");
            try {
                String schema = req.getScheme();
                String serverName = req.getServerName();
                int serverPort = req.getServerPort();
                String contextPath = req.getContextPath();
                
                String targetUrl = schema + "://" + serverName + ":" + serverPort + contextPath + "/api/hikvision/event/DEV001";
                LOG.info("[Hikvision-Servlet] Selftest ejecutando POST a: " + targetUrl);
                
                java.net.URL url = new java.net.URL(targetUrl);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                String testBody = "{\n"
                        + "  \"eventType\": \"AccessControllerEvent\",\n"
                        + "  \"AccessControllerEvent\": {\n"
                        + "    \"majorEventType\": 5,\n"
                        + "    \"subEventType\": 38,\n"
                        + "    \"serialNo\": 9999,\n"
                        + "    \"employeeNoString\": \"9999\",\n"
                        + "    \"time\": \"2026-06-24T12:00:00-03:00\"\n"
                        + "  }\n"
                        + "}";

                try (java.io.OutputStream os = conn.getOutputStream()) {
                    os.write(testBody.getBytes("UTF-8"));
                }

                int status = conn.getResponseCode();
                String message = conn.getResponseMessage();
                sb.append("HTTP Status: ").append(status).append(" (").append(message).append(")\n");

                sb.append("Headers:\n");
                for (java.util.Map.Entry<String, java.util.List<String>> entry : conn.getHeaderFields().entrySet()) {
                    sb.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
                }

                sb.append("Body:\n");
                try (java.io.InputStream is = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream()) {
                    if (is != null) {
                        java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(is, "UTF-8"));
                        String line;
                        while ((line = br.readLine()) != null) {
                            sb.append("  ").append(line).append("\n");
                        }
                    } else {
                        sb.append("  (No body)\n");
                    }
                }
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Error durante el selftest", e);
                sb.append("Error: ").append(e.toString()).append("\n");
                java.io.StringWriter sw = new java.io.StringWriter();
                e.printStackTrace(new java.io.PrintWriter(sw));
                sb.append(sw.toString());
            }
            
            byte[] responseBytes = sb.toString().getBytes(StandardCharsets.UTF_8);
            resp.setStatus(HttpServletResponse.SC_OK);
            resp.setContentType("text/plain;charset=UTF-8");
            resp.setContentLength(responseBytes.length);
            resp.getOutputStream().write(responseBytes);
        } else {
            resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "GET not allowed for this URI");
        }
    }
}
