package edu.pucmm.icc352;

import edu.pucmm.icc352.encapsulaciones.Evento;
import edu.pucmm.icc352.encapsulaciones.Usuario;
import edu.pucmm.icc352.servicios.HibernateUtil;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.rendering.template.JavalinThymeleaf;
import org.h2.tools.Server;
import org.hibernate.Session;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {

    public static void main(String[] args) throws SQLException {

        // 1. INICIAR H2 EN MODO SERVIDOR TCP (TU REQUERIMIENTO)
        Server.createTcpServer("-tcp", "-tcpAllowOthers", "-tcpPort", "9092").start();
        System.out.println("✅ Servidor H2 iniciado en modo TCP en el puerto 9092");

        // 2. CREAR ADMIN SI NO EXISTE
        crearAdminPorDefecto();
        crearEventosPrueba();

        // 3. INICIAR JAVALIN
        Javalin app = Javalin.create(config -> {
            config.staticFiles.add("/public");
            config.fileRenderer(new JavalinThymeleaf());

            // Redirección inicial
            config.routes.get("/", ctx -> ctx.redirect("/eventos"));

            // ==========================================
            // RUTA MODIFICADA: AHORA LEE DE LA BASE DE DATOS
            // ==========================================
            config.routes.get("/eventos", ctx -> {
                try (Session session = HibernateUtil.getSessionFactory().openSession()) {
                    // Magia de Hibernate: Traer todos los eventos reales
                    List<Evento> eventosReales = session.createQuery("FROM Evento", Evento.class).list();

                    Map<String, Object> modelo = new HashMap<>();
                    modelo.put("titulo", "Eventos Académicos");
                    modelo.put("eventos", eventosReales);

                    ctx.render("templates/eventos.html", modelo);
                }
            });

            // ==========================================
            // RUTA DE INSCRIPCIÓN (Pendiente de conectar a BD)
            // ==========================================
            config.routes.post("/api/inscripciones", Main::procesarInscripcion);

        }).start(7000);
    }

    // Este método lo dejamos casi igual temporalmente para no romperle el JavaScript a tu compañero,
    // pero pronto lo cambiaremos para que guarde la inscripción en Hibernate.
    private static void procesarInscripcion(Context ctx) {
        InscripcionRequest request = ctx.bodyAsClass(InscripcionRequest.class);

        if (request.getNombre() == null || request.getNombre().trim().isEmpty()
                || request.getCorreo() == null || request.getCorreo().trim().isEmpty()
                || request.getEventoId() == null) {

            ctx.json(Map.of(
                    "ok", false,
                    "mensaje", "Todos los campos son obligatorios"
            ));
            return;
        }

        String token = "TEMP-" + System.currentTimeMillis();

        ctx.json(Map.of(
                "ok", true,
                "mensaje", "Inscripción realizada correctamente",
                "eventoId", request.getEventoId(),
                "correo", request.getCorreo(),
                "token", token
        ));
    }

    private static void crearAdminPorDefecto() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            session.beginTransaction();
            Long totalAdmins = session.createQuery("SELECT COUNT(u) FROM Usuario u WHERE u.rol = 'ADMINISTRADOR'", Long.class).uniqueResult();

            if (totalAdmins == 0) {
                Usuario admin = new Usuario("admin@pucmm.edu.do", "admin123", "ADMINISTRADOR");
                session.persist(admin);
                System.out.println("✅ Administrador creado automáticamente.");
            }
            session.getTransaction().commit();
        } catch (Exception e) {
            System.err.println("Error en BD: " + e.getMessage());
        }
    }

    private static void crearEventosPrueba() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            session.beginTransaction();

            Long totalEventos = session.createQuery("SELECT COUNT(e) FROM Evento e", Long.class).uniqueResult();

            if (totalEventos == 0) {
                Evento e1 = new Evento();
                e1.setTitulo("Charla de Java Web");
                e1.setDescripcion("Introducción a Javalin y Thymeleaf");
                e1.setFecha("2026-03-20");
                e1.setLugar("Auditorio 1");
                e1.setCupoMaximo(50);
                e1.setInscritos(20);

                Evento e2 = new Evento();
                e2.setTitulo("Taller de Docker");
                e2.setDescripcion("Contenedores y despliegue básico");
                e2.setFecha("2026-03-22");
                e2.setLugar("Laboratorio 3");
                e2.setCupoMaximo(30);
                e2.setInscritos(30);

                Evento e3 = new Evento();
                e3.setTitulo("Conferencia de IA");
                e3.setDescripcion("Aplicaciones prácticas de inteligencia artificial");
                e3.setFecha("2026-03-25");
                e3.setLugar("Salón A-12");
                e3.setCupoMaximo(100);
                e3.setInscritos(65);

                session.persist(e1);
                session.persist(e2);
                session.persist(e3);

                System.out.println("✅ Eventos de prueba creados automáticamente.");
            }

            session.getTransaction().commit();
        } catch (Exception e) {
            System.err.println("Error creando eventos de prueba: " + e.getMessage());
        }
    }
}