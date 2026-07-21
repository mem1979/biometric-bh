package com.sta.biometric.rest;

import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * Pool de ejecución centralizado para procesar fichadas en segundo plano.
 * Configura un pool de tamaño fijo de 2 hilos y una cola de hasta 100 tareas
 * para prevenir desbordamientos de memoria en el entorno de 128MB heap de cPanel.
 */
public class HikvisionThreadPool {
    private static final Logger LOG = Logger.getLogger(HikvisionThreadPool.class.getName());
    private static ThreadPoolExecutor executor;

    public static synchronized void initialize() {
        if (executor != null && !executor.isShutdown()) {
            return;
        }
        // Pool fijo de 2 hilos, cola de capacidad 100, descartar más antiguo si se llena
        executor = new ThreadPoolExecutor(
            2, 2,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100),
            new ThreadFactory() {
                private int counter = 1;
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "Hikvision-BD-Thread-" + (counter++));
                    t.setDaemon(true);
                    return t;
                }
            },
            new ThreadPoolExecutor.DiscardOldestPolicy()
        );
        LOG.info("[Hikvision-ThreadPool] Pool de hilos inicializado con 2 hilos y cola de 100.");
    }

    public static void submit(Runnable task) {
        if (executor == null) {
            initialize();
        }
        executor.submit(task);
    }

    public static synchronized void shutdown() {
        if (executor != null) {
            LOG.info("[Hikvision-ThreadPool] Apagando pool de hilos...");
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
            }
            executor = null;
            LOG.info("[Hikvision-ThreadPool] Pool de hilos apagado.");
        }
    }
}
