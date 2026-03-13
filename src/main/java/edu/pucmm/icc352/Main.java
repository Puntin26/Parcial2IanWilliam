package edu.pucmm.icc352;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.rendering.template.JavalinThymeleaf;

import java.util.*;

public class Main {

    private static final Map<Integer, Map<String, Object>> eventosPorId = new HashMap<>();
    private static final Map<Integer, Set<String>> correosPorEvento = new HashMap<>();

    public static void main(String[] args) {

        cargarEventosPrueba();

        Javalin app = Javalin.create(config -> {
            config.staticFiles.add("/public");
            config.fileRenderer(new JavalinThymeleaf());

            config.routes.get("/", ctx -> ctx.redirect("/eventos"));

            config.routes.get("/eventos", ctx -> {
                Map<String, Object> modelo = new HashMap<>();
                modelo.put("titulo", "Eventos Académicos");
                modelo.put("eventos", new ArrayList<>(eventosPorId.values()));
                ctx.render("/templates/eventos.html", modelo);
            });

            config.routes.post("/api/inscripciones", Main::procesarInscripcion);
        }).start(7000);
    }

    private static void procesarInscripcion(Context ctx) {
        InscripcionRequest request = ctx.bodyAsClass(InscripcionRequest.class);

        if (request.getNombre() == null || request.getNombre().trim().isEmpty()
                || request.getCorreo() == null || request.getCorreo().trim().isEmpty()) {
            ctx.status(400).json(Map.of(
                    "ok", false,
                    "mensaje", "Todos los campos son obligatorios"
            ));
            return;
        }

        Integer eventoId = request.getEventoId();
        if (eventoId == null || !eventosPorId.containsKey(eventoId)) {
            ctx.status(404).json(Map.of(
                    "ok", false,
                    "mensaje", "Evento no encontrado"
            ));
            return;
        }

        Map<String, Object> evento = eventosPorId.get(eventoId);
        int cupoMaximo = (int) evento.get("cupoMaximo");
        int inscritos = (int) evento.get("inscritos");

        String correo = request.getCorreo().trim().toLowerCase();

        correosPorEvento.putIfAbsent(eventoId, new HashSet<>());
        Set<String> correos = correosPorEvento.get(eventoId);

        if (inscritos >= cupoMaximo) {
            ctx.status(400).json(Map.of(
                    "ok", false,
                    "mensaje", "El evento ya alcanzó el cupo máximo"
            ));
            return;
        }

        if (correos.contains(correo)) {
            ctx.status(400).json(Map.of(
                    "ok", false,
                    "mensaje", "Ya estás inscrito en este evento"
            ));
            return;
        }

        correos.add(correo);
        inscritos++;
        evento.put("inscritos", inscritos);

        ctx.json(Map.of(
                "ok", true,
                "mensaje", "Inscripción realizada correctamente",
                "eventoId", eventoId,
                "inscritos", inscritos,
                "cupoMaximo", cupoMaximo
        ));
    }

    private static void cargarEventosPrueba() {
        eventosPorId.put(1, crearEvento(1, "Charla de Java Web", "Introducción a Javalin y Thymeleaf", "2026-03-20", "Auditorio 1", 50, 20));
        eventosPorId.put(2, crearEvento(2, "Taller de Docker", "Contenedores y despliegue básico", "2026-03-22", "Laboratorio 3", 30, 30));
        eventosPorId.put(3, crearEvento(3, "Conferencia de IA", "Aplicaciones prácticas de inteligencia artificial", "2026-03-25", "Salón A-12", 100, 65));
    }

    private static Map<String, Object> crearEvento(int id, String titulo, String descripcion, String fecha,
                                                   String lugar, int cupoMaximo, int inscritos) {
        Map<String, Object> evento = new HashMap<>();
        evento.put("id", id);
        evento.put("titulo", titulo);
        evento.put("descripcion", descripcion);
        evento.put("fecha", fecha);
        evento.put("lugar", lugar);
        evento.put("cupoMaximo", cupoMaximo);
        evento.put("inscritos", inscritos);
        return evento;
    }
}