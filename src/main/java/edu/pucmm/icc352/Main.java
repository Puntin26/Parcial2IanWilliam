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
        // TODO: En el próximo paso, cambiaremos esto para usar Hibernate en lugar de HashMaps.
        ctx.json(Map.of(
                "ok", false,
                "mensaje", "El backend real está en construcción. ¡Pronto funcionará con la Base de Datos!"
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
}