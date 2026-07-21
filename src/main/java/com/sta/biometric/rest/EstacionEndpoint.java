package com.sta.biometric.rest;

import java.util.*;
import java.util.stream.*;
import javax.persistence.*;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import org.openxava.jpa.*;
import com.sta.biometric.modelo.*;
import com.sta.biometric.enums.*;
import com.sta.biometric.servicios.*;

/**
 * Endpoint para comunicacion con estaciones de hardware (ESP32).
 * 
 * <p>Protegido por una API Key configurable desde
 * {@code biometricConfiguracion.properties} (clave: {@code estacion.api.key}).
 * </p>
 * 
 * <p><b>Flujo de comunicación con el dispositivo:</b></p>
 * <ol>
 *   <li><b>Sincronización:</b> GET /estacion/usuarios → devuelve {id, nombre}</li>
 *   <li><b>Fichada:</b> POST /estacion/asistencia → recibe {user_id, type, station_id}</li>
 * </ol>
 * 
 * <p><b>Nota:</b> El registro de huella (finger_id) es un proceso exclusivamente
 * local del dispositivo. El backend solo trabaja con el userId (legajo).</p>
 */
@Path("/estacion")
public class EstacionEndpoint {

    /**
     * Valida la API Key recibida contra la configurada en properties.
     * @throws WebApplicationException si la clave es inválida o no está configurada
     */
    private void validarApiKey(String key) {
        String expectedKey = ConfiguracionesPreferencias.obtenerValor("estacion.api.key");
        if (key == null || expectedKey == null || !key.equals(expectedKey)) {
            throw new WebApplicationException(
                Response.status(Response.Status.UNAUTHORIZED)
                    .entity(Map.of("error", "API Key invalida"))
                    .build()
            );
        }
    }

    /**
     * Devuelve la lista de empleados activos para sincronizacion con el Fichador.
     * 
     * <p>Solo envía {@code id} (userId/legajo) y {@code nombre}.
     * El finger_id NO se incluye porque es un dato local del sensor AS608.</p>
     * 
     * @return JSON array: [{id: "A1", nombre: "Apellido, Nombre"}, ...]
     */
    @GET
    @Path("/usuarios")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getUsuarios(@HeaderParam("X-API-Key") String apiKey) {
        validarApiKey(apiKey);

        List<Personal> empleados = XPersistence.getManager()
            .createQuery("FROM Personal p WHERE p.activo = true AND p.eliminado = false", Personal.class)
            .getResultList();

        List<Map<String, Object>> out = empleados.stream().map(p -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", p.getUserId());
            map.put("nombre", p.getNombreCompleto());
            return map;
        }).collect(Collectors.toList());

        return Response.ok(out).build();
    }

    /**
     * Recibe una fichada desde la estación de hardware y la registra usando la hora oficial del servidor.
     * 
     * <p><b>Payload esperado:</b></p>
     * <pre>
     * {
     *   "user_id": "A1",           // Legajo del empleado (requerido)
     *   "type": "ENTRADA",         // Tipo de movimiento: ENTRADA, SALIDA, etc. (requerido)
     *   "station_id": "STATION_01" // Identificador del dispositivo (opcional)
     * }
     * </pre>
     * 
     * <p>El campo {@code type} se interpreta usando
     * {@link InterpreteFichadasService#deducirTipoMovimiento(String)},
     * que soporta múltiples sinónimos configurables desde properties.</p>
     */
    @POST
    @Path("/asistencia")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response registrarAsistencia(Map<String, Object> body, @HeaderParam("X-API-Key") String apiKey) {
        validarApiKey(apiKey);

        String userId = (String) body.get("user_id");
        String typeStr = body.get("type") != null ? body.get("type").toString() : null;
        String stationId = (String) body.get("station_id");

        if (userId == null || typeStr == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "Faltan parametros (user_id, type)"))
                .build();
        }

        // Interpretar tipo de movimiento usando el servicio centralizado
        TipoMovimiento tipoSolicitado = InterpreteFichadasService.deducirTipoMovimiento(typeStr);
        if (tipoSolicitado == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", "Tipo de movimiento no reconocido: " + typeStr))
                .build();
        }

        try {
            Personal empleado = XPersistence.getManager()
                .createQuery("FROM Personal p WHERE p.userId = :u", Personal.class)
                .setParameter("u", userId)
                .getSingleResult();

            // Usamos la hora oficial del servidor
            java.time.LocalDate hoy = java.time.LocalDate.now();
            java.time.LocalDate ayer = hoy.minusDays(1);
            java.time.LocalTime ahora = java.time.LocalTime.now();

            AuditoriaRegistros dia = null;
            boolean esAjusteNocturno = false;

            // Soporte para Jornadas Nocturnas
            if (tipoSolicitado == TipoMovimiento.SALIDA) {
                AuditoriaRegistros jornadaNocturnaAbierta = XPersistence.getManager()
                        .createQuery(
                                "SELECT a FROM AuditoriaRegistros a " +
                                        "WHERE a.empleado = :emp " +
                                        "AND a.fecha = :ayer " +
                                        "AND a.esJornadaNocturna = true " +
                                        "AND a.evaluacion = :estado",
                                AuditoriaRegistros.class)
                        .setParameter("emp", empleado)
                        .setParameter("ayer", ayer)
                        .setParameter("estado", EvaluacionJornada.EN_CURSO)
                        .getResultStream()
                        .findFirst()
                        .orElse(null);

                if (jornadaNocturnaAbierta != null) {
                    dia = jornadaNocturnaAbierta;
                    esAjusteNocturno = true;
                }
            }

            // Crear o buscar auditoría del día
            if (dia == null) {
                dia = XPersistence.getManager()
                        .createQuery(
                                "FROM AuditoriaRegistros a WHERE a.empleado = :emp AND a.fecha = :fecha",
                                AuditoriaRegistros.class)
                        .setParameter("emp", empleado)
                        .setParameter("fecha", hoy)
                        .getResultStream()
                        .findFirst()
                        .orElseGet(() -> {
                            AuditoriaRegistros nuevo = new AuditoriaRegistros();
                            nuevo.setEmpleado(empleado);
                            nuevo.setFecha(hoy);
                            XPersistence.getManager().persist(nuevo);
                            return nuevo;
                        });
            }

            // Prevención de Duplicados
            boolean yaExisteFichada = dia.getRegistros().stream()
                    .anyMatch(r -> r.getTipoMovimiento() == tipoSolicitado);

            if (yaExisteFichada) {
                return Response.status(Response.Status.CONFLICT)
                    .entity(Map.of("error", "FICHADA_DUPLICADA", "mensaje", "Ya existe ese registro."))
                    .build();
            }

            // Crear colección de registros
            ColeccionRegistros reg = new ColeccionRegistros();
            reg.setFecha(hoy);
            reg.setHora(ahora);
            String est = (stationId != null ? stationId : "N/A");
            reg.setObservacion(esAjusteNocturno ? "Fichador Fisico (Ajuste Nocturno): " + est : "Fichador Fisico: " + est);
            reg.setTipoMovimiento(tipoSolicitado);
            reg.setAsistenciaDiaria(dia);
            
            dia.getRegistros().add(reg);
            dia.consolidarDesdeRegistros();

            return Response.ok(Map.of("success", true, "mensaje", "Asistencia registrada")).build();

        } catch (NoResultException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Empleado no encontrado"))
                .build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }
}
