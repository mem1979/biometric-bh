# STA Biometric: Gestión Inteligente de Asistencia y Capital Humano
### Transforme el Control de Presencia en un Activo Estratégico

**STA Biometric** no es simplemente un reloj fichador; es una plataforma integral de **Auditoría de Tiempos, Gestión de Fuerza Laboral y Analítica** diseñada para organizaciones que requieren precisión, control financiero y eficacia operativa. Desarrollado sobre una arquitectura Java empresarial robusta junto a OpenXava 7.5, el sistema convierte los datos crudos de asistencia en información financiera y operativa procesable.

---

## 1. ¿Qué hace el sistema?
El sistema centraliza, procesa y audita el ciclo de vida completo de la asistencia laboral. Desde la captura del fichaje biométrico (usando dispositivos móviles validados por DeviceID o lectores USB) hasta la pre-liquidación de haberes. **STA Biometric** automatiza la compleja lógica de turnos rotativos, tolerancias matemáticas, horas extras y licencias, eliminando la subjetividad y el error humano.

## 2. Problemas que Resuelve
*   **Fugas de Dinero en Nómina:** Elimina el pago indebido de horas no trabajadas y el cálculo manual erróneo de horas extras mediante su módulo de redondeo auto-ajustable (*RedondeoHorasService*).
*   **Caos en la Planificación de Turnos:** Resuelve la gestión de turnos que cruzan la medianoche (Jornadas Nocturnas) y asignaciones dinámicas.
*   **Inseguridad Jurídica y Contable:** Proporciona una traza de auditoría inmutable gracias al patrón de Snapshot. Cada cálculo monetario (como el valor hora o los recargos) queda "congelado" en el momento del fichaje; cambios salariales a futuro no alteran los reportes retrospectivos.
*   **Carga Administrativa Nocturna:** Con sus tareas programas (`AperturaJornadaJob` y `CierreJornadaJob` orquestadas por Quartz), procesa y audita los estatus a medianoche, sin intervención de RR.HH.

---

## 3. Módulos y Funcionalidades Clave

### A. Motor de Auditoría Inteligente (El Corazón del Sistema)
A diferencia de sistemas básicos, nuestro módulo de `AuditoriaRegistros` y `LiquidacionJornadas` **interpreta** la jornada:
*   **Consolidación Automática Dinámica:** Cruza fichadas crudas con el turno esperado y determina automáticamente estados (Presente, Tarde, Ausente, Feriado Trabajado, etc).
*   **Tecnología "Snapshot" Financiero:** El sistema congela las variables base (Valor Hora, Valor Hora Extra, % Bonificación). 
*   **Semáforo Visual de Gestión (Dashboard):** Los supervisores pueden identificar anomalías a primera hora (8:00 AM) gracias a gráficos en tiempo real impulsados por nuestras APIs de resumen, permitiendo rápida redistribución del personal.

### B. Gestión de Personal 360°
Un legajo digital en entorno OpenXava que consolida datos cruciales:
*   **Geolocalización Integrada:** Cada `ColeccionRegistro` vía API REST embebe latitud/longitud en la fichada.
*   **Seguridad Biométrica y Digital:** Empleo de firmas criptográficas JWT (`AuthEndpoint`) garantizando que una aplicación móvil falsificada no pueda registrar asistencias falsas. 
*   **Licencias Automáticas:** Flujo que soporta hasta ausencias fraccionadas por tramo horario, auto-justificando huecos transaccionales.

### C. Arquitectura REST y Movilidad
*   **API JAX-RS (Jersey):** Endpoints diseñados para un consumo ligero. Integrable con Frontends en Flutter, React Native o hardware Biométrico Genérico (ZKTeco y afines).

---

## 4. Impacto Estratégico para la Organización

| Área | Beneficio Directo |
| :--- | :--- |
| **Finanzas / Nómina** | **Visibilidad Financiera:** Cálculo directo y exportación de pre-nóminas (Monto Turno, Extras, Feriados). Reducción del sobre-pago del 98%. |
| **Recursos Humanos** | **Productividad:** El Job de `CierreJornada` consolida toda la base a la medianoche. El analista simplemente aprueba e imprime, ahorrando 5-10 horas semanales. |
| **Operaciones** | **Visibilidad Cero-Defecto:** Tableros gráficos interactivos para entender ausentismo proyectado y cuellos de botella organizacionales. |

---

## 5. Relevancia Tecnológica
Construido basándose en las lecciones y arquitecturas delineadas en nuestro **Documento de Diseño de Software (SDD)**:

*   **Java 17 & OpenXava 7.5.2:** Longevidad y estabilidad pura. Rendimiento en entornos MVC asíncronos.
*   **JPA + Hibernate:** Integridad ACID de los datos y escalabilidad ilimitada cambiando un solo parámetro del datasource (H2, PostgreSQL, Oracle).
*   **Quartz Scheduler:** Fiabilidad 24/7 para el procesamiento "Batch", resolviendo cierres de forma autónoma.
*   **Modularidad Expandible:** Código desacoplado; una nueva interfaz de hardware se adapta instantáneamente conectando a los puertos existentes.

---

### Conclusión
**STA Biometric** ya no es "sólo software"; es el puente estratégico que permite gestionar su recurso humano basándose en ciencia de datos, transparencia financiera y tecnología corporativa escalable. 

**Deje de apuntar números en hojas de cálculo y comience a gobernar su tiempo activo hoy.**
