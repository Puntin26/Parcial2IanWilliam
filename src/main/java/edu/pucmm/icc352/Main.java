package edu.pucmm.icc352;

import io.javalin.Javalin;
import io.javalin.rendering.template.JavalinThymeleaf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {

    public static void main(String[] args) {

        Javalin app = Javalin.create(config -> {
            config.staticFiles.add("/public");
            config.fileRenderer(new JavalinThymeleaf());

            config.routes.get("/", ctx -> ctx.redirect("/eventos"));

            config.routes.get("/eventos", ctx -> {
                List<Map<String, Object>> eventos = new ArrayList<>();

                eventos.add(crearEvento(1, "Charla de Java Web", "Introducción a Javalin y Thymeleaf", "2026-03-20", "Auditorio 1", 50, 20));
                eventos.add(crearEvento(2, "Taller de Docker", "Contenedores y despliegue básico", "2026-03-22", "Laboratorio 3", 30, 30));
                eventos.add(crearEvento(3, "Conferencia de IA", "Aplicaciones prácticas de inteligencia artificial", "2026-03-25", "Salón A-12", 100, 65));

                Map<String, Object> modelo = new HashMap<>();
                modelo.put("titulo", "Eventos Académicos");
                modelo.put("eventos", eventos);

                ctx.render("templates/eventos.html", modelo);
            });
        }).start(7000);
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