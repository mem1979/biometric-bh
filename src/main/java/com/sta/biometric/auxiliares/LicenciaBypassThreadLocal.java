package com.sta.biometric.auxiliares;

/**
 * Utilidad ThreadLocal para gestionar el bypass del validador de eliminación
 * de licencias en el hilo de ejecución de la solicitud actual.
 */
public class LicenciaBypassThreadLocal {
    private static final ThreadLocal<Boolean> BYPASS = ThreadLocal.withInitial(() -> false);

    public static boolean isBypass() {
        return BYPASS.get();
    }

    public static void setBypass(boolean value) {
        BYPASS.set(value);
    }

    public static void clear() {
        BYPASS.remove();
    }
}
