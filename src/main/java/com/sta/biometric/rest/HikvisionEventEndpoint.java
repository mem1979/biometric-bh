package com.sta.biometric.rest;

import java.util.logging.Logger;
import javax.ws.rs.*;
import javax.ws.rs.core.*;

/**
 * Endpoint REST JAX-RS para diagnóstico y selftest.
 * El procesamiento real de eventos se movió a HikvisionEventServlet para evitar
 * el paso por Jersey JAX-RS y el overhead de procesamiento en producción.
 *
 * Ruta: GET /api/hikvision/selftest
 */
@Path("/hikvision")
public class HikvisionEventEndpoint {

    private static final Logger LOG = Logger.getLogger(HikvisionEventEndpoint.class.getName());

    /**
     * Endpoint GET para autodiagnosticar las cabeceras y cuerpo
     * de la respuesta que el servidor envía ante un evento Hikvision.
     */
    @GET
    @Path("/selftest")
    @Produces(MediaType.TEXT_PLAIN)
    public String selftest() {
        LOG.info("Ejecutando selftest de HikvisionEventEndpoint...");
        StringBuilder sb = new StringBuilder();
        sb.append("--- HIKVISION ENDPOINT SELFTEST ---\n");
        try {
            java.net.URL url = new java.net.URL("http://localhost:8080/biometric/api/hikvision/event/DEV001");
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
            LOG.severe("Error durante el selftest: " + e.toString());
            sb.append("Error: ").append(e.toString()).append("\n");
            java.io.StringWriter sw = new java.io.StringWriter();
            e.printStackTrace(new java.io.PrintWriter(sw));
            sb.append(sw.toString());
        }
        return sb.toString();
    }
}
