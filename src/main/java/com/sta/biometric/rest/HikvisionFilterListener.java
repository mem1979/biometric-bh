package com.sta.biometric.rest;

import javax.servlet.*;
import javax.servlet.annotation.WebListener;
import com.sta.biometric.servicios.ConfiguracionesPreferencias;

/**
 * Listener de ciclo de vida del Servlet Context.
 * Inicializa el pool de hilos centralizado al arrancar y detiene los servicios
 * al apagar la aplicación. También levanta condicionalmente el listener de sockets
 * si está habilitado en biometricConfiguracion.properties.
 */
@WebListener
public class HikvisionFilterListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        System.out.println("[Hikvision] Inicializando servicios de integración Hikvision...");
        
        // 1. Inicializar el pool de hilos centralizado
        HikvisionThreadPool.initialize();

        // 2. Pre-cargar MetaValidators de OpenXava de forma síncrona para evitar condiciones de carrera en frío
        try {
            System.out.println("[Hikvision] Pre-cargando MetaValidators de OpenXava...");
            Class.forName("org.openxava.validators.meta.MetaValidators");
            System.out.println("[Hikvision] MetaValidators inicializado correctamente.");
        } catch (ClassNotFoundException e) {
            System.err.println("[Hikvision] Advertencia: No se pudo pre-cargar MetaValidators: " + e.getMessage());
        }

        // Migración automática de códigos de dispositivo vacíos en formato TMTxx
        try {
            javax.persistence.EntityManager em = org.openxava.jpa.XPersistence.getManager();
            java.util.List<com.sta.biometric.modelo.DispositivoBiometrico> sinCodigo = em.createQuery(
                "SELECT d FROM DispositivoBiometrico d WHERE d.codigo IS NULL OR d.codigo = ''", 
                com.sta.biometric.modelo.DispositivoBiometrico.class).getResultList();
            
            if (!sinCodigo.isEmpty()) {
                // Obtener códigos existentes
                java.util.List<String> codigosExistentes = em.createQuery(
                    "SELECT d.codigo FROM DispositivoBiometrico d WHERE d.codigo LIKE 'TMT%'", String.class)
                    .getResultList();
                
                int index = 1;
                for (com.sta.biometric.modelo.DispositivoBiometrico d : sinCodigo) {
                    // Encontrar el primer candidato libre
                    String candidato = null;
                    while (index <= 99) {
                        candidato = String.format("TMT%02d", index++);
                        if (!codigosExistentes.contains(candidato)) {
                            break;
                        }
                    }
                    if (candidato != null && index <= 100) {
                        d.setCodigo(candidato);
                        em.merge(d);
                        codigosExistentes.add(candidato);
                    } else {
                        System.err.println("[Hikvision] No se pudo asignar codigo a dispositivo ID " + d.getId() + " - Limite de 99 excedido.");
                    }
                }
                org.openxava.jpa.XPersistence.commit();
                System.out.println("[Hikvision] Migracion de codigos TMTxx completada (" + sinCodigo.size() + " actualizados).");
            }
        } catch (Exception e) {
            System.err.println("[Hikvision] Error durante la migracion de codigos TMTxx: " + e.getMessage());
            try { org.openxava.jpa.XPersistence.rollback(); } catch (Exception rx) {}
        } finally {
            try { org.openxava.jpa.XPersistence.reset(); } catch (Exception ex) {}
        }

        // 3. Levantar condicionalmente el listener de sockets en base a properties
        boolean socketEnabled = ConfiguracionesPreferencias.obtenerValor("hikvision.socket.enabled", false, Boolean.class);
        int socketPort = ConfiguracionesPreferencias.obtenerValor("hikvision.socket.port", 8088, Integer.class);

        if (socketEnabled) {
            System.out.println("[Hikvision] Iniciando socket listener en puerto " + socketPort + "...");
            HikvisionSocketListener.start(socketPort);
        } else {
            System.out.println("[Hikvision] Socket listener desactivado según configuración.");
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        System.out.println("[Hikvision] Apagando servicios de integración Hikvision...");
        
        // 1. Detener el socket listener
        HikvisionSocketListener.stop();

        // 2. Apagar el pool de hilos centralizado
        HikvisionThreadPool.shutdown();
    }
}
