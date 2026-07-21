package com.sta.biometric.rest;

import java.util.logging.Logger;

/**
 * Clase utilitaria para extraer campos de eventos (JSON y XML) enviados por biométricos Hikvision.
 * Diseñada para ser compartida por el Socket Listener y el Servlet de producción.
 */
public class HikvisionEventParser {
    private static final Logger LOG = Logger.getLogger(HikvisionEventParser.class.getName());

    public static class EventData {
        private final String employeeNo;
        private final String timeStr;
        private final int serialNo;
        private final int majorEventType;
        private final boolean valido;

        public EventData(String employeeNo, String timeStr, int serialNo, int majorEventType, boolean valido) {
            this.employeeNo = employeeNo;
            this.timeStr = timeStr;
            this.serialNo = serialNo;
            this.majorEventType = majorEventType;
            this.valido = valido;
        }

        public String getEmployeeNo() {
            return employeeNo;
        }

        public String getTimeStr() {
            return timeStr;
        }

        public int getSerialNo() {
            return serialNo;
        }

        public int getMajorEventType() {
            return majorEventType;
        }

        public boolean isValido() {
            return valido;
        }
    }

    public static EventData extraerDatosEvento(String body) {
        if (body == null || body.trim().isEmpty()) {
            return new EventData(null, null, 0, -1, false);
        }
        String trimmedBody = body.trim();
        String employeeNo = null;
        String serialNoStr = null;
        String majorStr = null;
        String timeStr = null;

        if (trimmedBody.startsWith("<")) {
            // Formato XML
            employeeNo = extraerCampoXml(trimmedBody, "employeeNoString");
            if (employeeNo == null) {
                employeeNo = extraerCampoXml(trimmedBody, "employeeNo");
            }
            serialNoStr = extraerCampoXml(trimmedBody, "serialNo");
            majorStr = extraerCampoXml(trimmedBody, "majorEventType");
            if (majorStr == null) {
                majorStr = extraerCampoXml(trimmedBody, "major");
            }
            timeStr = extraerCampoXml(trimmedBody, "time");
            if (timeStr == null) {
                timeStr = extraerCampoXml(trimmedBody, "dateTime");
            }
        } else {
            // Formato JSON
            employeeNo = extraerCampoJson(trimmedBody, "employeeNoString");
            if (employeeNo == null) {
                employeeNo = extraerCampoJson(trimmedBody, "employeeNo");
            }
            serialNoStr = extraerCampoJson(trimmedBody, "serialNo");
            majorStr = extraerCampoJson(trimmedBody, "majorEventType");
            if (majorStr == null) {
                majorStr = extraerCampoJson(trimmedBody, "major");
            }
            timeStr = extraerCampoJson(trimmedBody, "time");
            if (timeStr == null) {
                timeStr = extraerCampoJson(trimmedBody, "dateTime");
            }
        }

        if (employeeNo == null || employeeNo.isEmpty()) {
            return new EventData(null, null, 0, -1, false);
        }

        int major = -1;
        try {
            if (majorStr != null) {
                major = Integer.parseInt(majorStr.trim());
            }
        } catch (NumberFormatException e) {
            LOG.warning("[Hikvision-Parser] Error al parsear majorEventType: " + majorStr);
        }

        int serial = 0;
        try {
            if (serialNoStr != null) {
                serial = Integer.parseInt(serialNoStr.trim());
            }
        } catch (NumberFormatException e) {
            LOG.warning("[Hikvision-Parser] Error al parsear serialNo: " + serialNoStr);
        }

        return new EventData(employeeNo.trim(), timeStr != null ? timeStr.trim() : null, serial, major, true);
    }

    public static String extraerCampoXml(String xml, String tag) {
        if (xml == null || tag == null) return null;
        String openTag = "<" + tag;
        int idx = xml.indexOf(openTag);
        if (idx < 0) return null;
        int closeAngle = xml.indexOf('>', idx);
        if (closeAngle < 0) return null;
        String closeTag = "</" + tag + ">";
        int end = xml.indexOf(closeTag, closeAngle);
        if (end < 0) return null;
        return xml.substring(closeAngle + 1, end).trim();
    }

    public static String extraerCampoJson(String json, String campo) {
        if (json == null || campo == null) return null;
        String patron = "\"" + campo + "\"";
        int idx = json.indexOf(patron);
        if (idx < 0) return null;
        int colonIdx = json.indexOf(':', idx + patron.length());
        if (colonIdx < 0) return null;
        int start = colonIdx + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        if (start >= json.length()) return null;
        char first = json.charAt(start);
        if (first == '"') {
            int end = json.indexOf('"', start + 1);
            if (end < 0) return null;
            return json.substring(start + 1, end);
        } else {
            int end = start;
            while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}' && json.charAt(end) != ']' && !Character.isWhitespace(json.charAt(end))) {
                end++;
            }
            return json.substring(start, end);
        }
    }
}
