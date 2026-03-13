package edu.pucmm.icc352;

import edu.pucmm.icc352.encapsulaciones.Evento;
import edu.pucmm.icc352.encapsulaciones.Usuario;
import edu.pucmm.icc352.servicios.HibernateUtil;
import edu.pucmm.icc352.encapsulaciones.Inscripcion;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.h2.tools.Server;
import org.hibernate.Session;
import freemarker.template.Configuration;
import freemarker.template.Template;

import java.io.File;
import java.io.StringWriter;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {

    public static void main(String[] args) throws SQLException {

        // 1. INICIAR H2 EN MODO SERVIDOR TCP
        Server.createTcpServer("-tcp", "-tcpAllowOthers", "-tcpPort", "9092").start();
        System.out.println("✅ Servidor H2 iniciado en modo TCP en el puerto 9092");

        // 2. CREAR ADMIN Y EVENTOS SI NO EXISTEN
        crearAdminPorDefecto();
        crearEventosPrueba();

        // 3. INICIAR JAVALIN
        Javalin app = Javalin.create(config -> {
            config.staticFiles.add("/public");
            
            // Configurar Freemarker para renderizar templates
            Configuration fmConfig = new Configuration(Configuration.VERSION_2_3_32);
            try {
                fmConfig.setDirectoryForTemplateLoading(new File("src/main/resources"));
            } catch (Exception e) {
                System.err.println("Error configurando Freemarker: " + e.getMessage());
            }
            
            config.fileRenderer((filePath, model, _) -> {
                try {
                    Template template = fmConfig.getTemplate(filePath);
                    StringWriter out = new StringWriter();
                    template.process(model, out);
                    return out.toString();
                } catch (Exception e) {
                    return "Error renderizando template: " + e.getMessage();
                }
            });

            // Redirección inicial
            config.routes.get("/", ctx -> ctx.redirect("/eventos"));

            // ==========================================
            // RUTAS DE LOGIN Y SESIÓN
            // ==========================================

            // Mostrar formulario de login
            config.routes.get("/login", ctx -> {
                ctx.render("templates/login.html");
            });

            // Procesar las credenciales
            config.routes.post("/login", ctx -> {
                String correo = ctx.formParam("correo");
                String password = ctx.formParam("password");

                try (Session session = HibernateUtil.getSessionFactory().openSession()) {
                    Usuario usuario = session.createQuery("FROM Usuario WHERE correo = :correo", Usuario.class)
                            .setParameter("correo", correo)
                            .uniqueResult();

                    // Validar si el usuario existe y la contraseña coincide
                    if (usuario != null && usuario.getPassword().equals(password)) {
                        if (usuario.isBloqueado()) {
                            ctx.status(403).result("Usuario bloqueado. Contacte al administrador.");
                        } else {
                            // Iniciar sesión guardando el usuario en el Context
                            ctx.sessionAttribute("usuarioActual", usuario);
                            ctx.redirect("/eventos");
                        }
                    } else {
                        // Credenciales incorrectas
                        ctx.status(401).result("Credenciales incorrectas");
                    }
                } catch (Exception e) {
                    ctx.status(500).result("Error en el servidor: " + e.getMessage());
                }
            });

            // Cerrar sesión
            config.routes.get("/logout", ctx -> {
                ctx.req().getSession().invalidate(); // Destruye la sesión
                ctx.redirect("/");
            });


            // ==========================================
            // RUTAS DE EVENTOS
            // ==========================================
            config.routes.get("/eventos", ctx -> {
                // Obtener el usuario de la sesión actual (si existe)
                Usuario usuarioSesion = ctx.sessionAttribute("usuarioActual");

                try (Session session = HibernateUtil.getSessionFactory().openSession()) {
                    List<Evento> eventosReales = session.createQuery("FROM Evento", Evento.class).list();

                    Map<String, Object> modelo = new HashMap<>();
                    modelo.put("titulo", "Eventos Académicos");
                    modelo.put("eventos", eventosReales);
                    // Pasamos el usuario al HTML para que Thymeleaf pueda verificar su rol
                    modelo.put("usuario", usuarioSesion);

                    ctx.render("templates/eventos.html", modelo);
                }
            });

            // ==========================================
            // RUTA DE INSCRIPCIÓN (API)
            // ==========================================
            config.routes.post("/api/inscripciones", Main::procesarInscripcion);

        }).start(7000);
    }

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

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            session.beginTransaction();

            Evento evento = session.get(Evento.class, Long.valueOf(request.getEventoId()));

            if (evento == null) {
                ctx.json(Map.of(
                        "ok", false,
                        "mensaje", "El evento no existe"
                ));
                return;
            }

            String token = "QR-" + System.currentTimeMillis();

            Inscripcion inscripcion = new Inscripcion(
                    evento,
                    request.getNombre().trim(),
                    request.getCorreo().trim().toLowerCase(),
                    token
            );

            session.persist(inscripcion);

            evento.setInscritos(evento.getInscritos() + 1);
            session.merge(evento);

            session.getTransaction().commit();

            ctx.json(Map.of(
                    "ok", true,
                    "mensaje", "Inscripción realizada correctamente",
                    "eventoId", evento.getId(),
                    "correo", inscripcion.getCorreo(),
                    "token", inscripcion.getTokenQr()
            ));

        } catch (Exception e) {
            ctx.json(Map.of(
                    "ok", false,
                    "mensaje", "Error guardando la inscripción: " + e.getMessage()
            ));
        }
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