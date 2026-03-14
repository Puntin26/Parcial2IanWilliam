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
            config.routes.get("/login", ctx -> {
                ctx.render("templates/login.html");
            });

            config.routes.post("/login", ctx -> {
                String correo = ctx.formParam("correo");
                String password = ctx.formParam("password");

                try (Session session = HibernateUtil.getSessionFactory().openSession()) {
                    Usuario usuario = session.createQuery("FROM Usuario WHERE correo = :correo", Usuario.class)
                            .setParameter("correo", correo)
                            .uniqueResult();

                    if (usuario != null && usuario.getPassword().equals(password)) {
                        if (usuario.isBloqueado()) {
                            ctx.status(403).result("Usuario bloqueado. Contacte al administrador.");
                        } else {
                            ctx.sessionAttribute("usuarioActual", usuario);
                            ctx.redirect("/eventos");
                        }
                    } else {
                        ctx.status(401).result("Credenciales incorrectas");
                    }
                } catch (Exception e) {
                    ctx.status(500).result("Error en el servidor: " + e.getMessage());
                }
            });

            config.routes.get("/logout", ctx -> {
                ctx.req().getSession().invalidate();
                ctx.redirect("/");
            });

            // ==========================================
            // RUTAS DE EVENTOS (Vista general)
            // ==========================================
            config.routes.get("/eventos", ctx -> {
                Usuario usuarioSesion = ctx.sessionAttribute("usuarioActual");

                try (Session session = HibernateUtil.getSessionFactory().openSession()) {
                    List<Evento> eventosReales = session.createQuery("FROM Evento", Evento.class).list();

                    Map<String, Object> modelo = new HashMap<>();
                    modelo.put("titulo", "Eventos Académicos");
                    modelo.put("eventos", eventosReales);
                    modelo.put("usuario", usuarioSesion);

                    ctx.render("templates/eventos.html", modelo);
                }
            });

            // ==========================================
            // RUTA PARA EL ESCÁNER QR (Solo Admin y Organizador)
            // ==========================================
            config.routes.get("/escanear", ctx -> {
                Usuario usuario = ctx.sessionAttribute("usuarioActual");

                // Si no está logueado o es un simple participante, lo pateamos a la lista
                if (usuario == null || usuario.getRol().equals("PARTICIPANTE")) {
                    ctx.redirect("/eventos");
                    return;
                }

                Map<String, Object> modelo = new HashMap<>();
                modelo.put("usuario", usuario);
                ctx.render("templates/escanear.html", modelo);
            });

            // ==========================================
            // CRUD DE EVENTOS (Solo Admin y Organizador)
            // ==========================================
            config.routes.post("/admin/eventos/crear", ctx -> {
                Usuario usuario = ctx.sessionAttribute("usuarioActual");
                if (usuario == null || usuario.getRol().equals("PARTICIPANTE")) {
                    ctx.status(403).result("Acceso denegado: No tienes permisos para crear eventos.");
                    return;
                }

                String titulo = ctx.formParam("titulo");
                String descripcion = ctx.formParam("descripcion");
                String fecha = ctx.formParam("fecha");
                String lugar = ctx.formParam("lugar");
                int cupoMaximo = Integer.parseInt(ctx.formParam("cupoMaximo"));

                try (Session session = HibernateUtil.getSessionFactory().openSession()) {
                    session.beginTransaction();
                    Evento nuevoEvento = new Evento(titulo, descripcion, fecha, lugar, cupoMaximo);
                    nuevoEvento.setInscritos(0);
                    nuevoEvento.setPublicado(false);
                    session.persist(nuevoEvento);
                    session.getTransaction().commit();
                    ctx.redirect("/eventos");
                }
            });

            config.routes.post("/admin/eventos/editar/{id}", ctx -> {
                Usuario usuario = ctx.sessionAttribute("usuarioActual");
                if (usuario == null || usuario.getRol().equals("PARTICIPANTE")) {
                    ctx.status(403).result("Acceso denegado.");
                    return;
                }

                Long id = Long.valueOf(ctx.pathParam("id"));
                try (Session session = HibernateUtil.getSessionFactory().openSession()) {
                    session.beginTransaction();
                    Evento evento = session.get(Evento.class, id);

                    if (evento != null) {
                        evento.setTitulo(ctx.formParam("titulo"));
                        evento.setDescripcion(ctx.formParam("descripcion"));
                        evento.setFecha(ctx.formParam("fecha"));
                        evento.setLugar(ctx.formParam("lugar"));
                        evento.setCupoMaximo(Integer.parseInt(ctx.formParam("cupoMaximo")));
                        session.merge(evento);
                    }
                    session.getTransaction().commit();
                    ctx.redirect("/eventos");
                }
            });

            config.routes.post("/admin/eventos/estado/{id}", ctx -> {
                Usuario usuario = ctx.sessionAttribute("usuarioActual");
                if (usuario == null || usuario.getRol().equals("PARTICIPANTE")) {
                    ctx.status(403).result("Acceso denegado.");
                    return;
                }

                Long id = Long.valueOf(ctx.pathParam("id"));
                String accion = ctx.formParam("accion");

                try (Session session = HibernateUtil.getSessionFactory().openSession()) {
                    session.beginTransaction();
                    Evento evento = session.get(Evento.class, id);

                    if (evento != null) {
                        if ("PUBLICAR".equalsIgnoreCase(accion)) {
                            evento.setPublicado(true);
                        } else if ("DESPUBLICAR".equalsIgnoreCase(accion)) {
                            evento.setPublicado(false);
                        } else if ("CANCELAR".equalsIgnoreCase(accion)) {
                            evento.setPublicado(false);
                            if (!evento.getTitulo().startsWith("[CANCELADO]")) {
                                evento.setTitulo("[CANCELADO] " + evento.getTitulo());
                            }
                        }
                        session.merge(evento);
                    }
                    session.getTransaction().commit();
                    ctx.redirect("/eventos");
                }
            });

            // ==========================================
            // PANEL DE ADMINISTRADOR
            // ==========================================
            config.routes.get("/admin/usuarios", ctx -> {
                Usuario admin = ctx.sessionAttribute("usuarioActual");
                if (admin == null || !admin.getRol().equals("ADMINISTRADOR")) {
                    ctx.status(403).result("Acceso denegado. Solo administradores.");
                    return;
                }

                try (Session session = HibernateUtil.getSessionFactory().openSession()) {
                    List<Usuario> usuarios = session.createQuery("FROM Usuario", Usuario.class).list();
                    Map<String, Object> modelo = new HashMap<>();
                    modelo.put("usuarios", usuarios);
                    modelo.put("usuario", admin);
                    ctx.render("templates/admin_usuarios.html", modelo);
                }
            });

            config.routes.post("/admin/usuarios/rol/{id}", ctx -> {
                Usuario admin = ctx.sessionAttribute("usuarioActual");
                if (admin == null || !admin.getRol().equals("ADMINISTRADOR")) return;

                Long idUsuario = Long.valueOf(ctx.pathParam("id"));
                String nuevoRol = ctx.formParam("rol");

                try (Session session = HibernateUtil.getSessionFactory().openSession()) {
                    session.beginTransaction();
                    Usuario usuarioTarget = session.get(Usuario.class, idUsuario);

                    if (usuarioTarget != null && !usuarioTarget.getRol().equals("ADMINISTRADOR")) {
                        usuarioTarget.setRol(nuevoRol);
                        session.merge(usuarioTarget);
                    }
                    session.getTransaction().commit();
                    ctx.redirect("/admin/usuarios");
                }
            });

            config.routes.post("/admin/usuarios/bloquear/{id}", ctx -> {
                Usuario admin = ctx.sessionAttribute("usuarioActual");
                if (admin == null || !admin.getRol().equals("ADMINISTRADOR")) return;

                Long idUsuario = Long.valueOf(ctx.pathParam("id"));

                try (Session session = HibernateUtil.getSessionFactory().openSession()) {
                    session.beginTransaction();
                    Usuario usuarioTarget = session.get(Usuario.class, idUsuario);

                    if (usuarioTarget != null && !usuarioTarget.getRol().equals("ADMINISTRADOR")) {
                        usuarioTarget.setBloqueado(!usuarioTarget.isBloqueado());
                        session.merge(usuarioTarget);
                    }
                    session.getTransaction().commit();
                    ctx.redirect("/admin/usuarios");
                }
            });

            config.routes.post("/admin/eventos/eliminar/{id}", ctx -> {
                Usuario admin = ctx.sessionAttribute("usuarioActual");
                if (admin == null || !admin.getRol().equals("ADMINISTRADOR")) {
                    ctx.status(403).result("Acceso denegado.");
                    return;
                }

                Long idEvento = Long.valueOf(ctx.pathParam("id"));

                try (Session session = HibernateUtil.getSessionFactory().openSession()) {
                    session.beginTransaction();
                    Evento eventoTarget = session.get(Evento.class, idEvento);

                    if (eventoTarget != null) {
                        session.createMutationQuery("DELETE FROM Inscripcion WHERE evento.id = :eventoId")
                                .setParameter("eventoId", idEvento)
                                .executeUpdate();
                        session.remove(eventoTarget);
                    }
                    session.getTransaction().commit();
                    ctx.redirect("/eventos");
                }
            });

            // ==========================================
            // RUTAS DE APIS PARA JAVASCRIPT / FETCH (Persona 2)
            // ==========================================
            config.routes.post("/api/inscripciones", Main::procesarInscripcion);
            config.routes.post("/api/asistencia", Main::procesarAsistencia);
            config.routes.delete("/api/inscripciones/{id}", Main::cancelarInscripcion);
            config.routes.get("/api/estadisticas/{id}", Main::obtenerEstadisticas);

        }).start(7000);
    }

    // ==========================================
    // MÉTODOS DE APOYO Y APIS
    // ==========================================

    private static void procesarInscripcion(Context ctx) {
        InscripcionRequest request = ctx.bodyAsClass(InscripcionRequest.class);

        if (request.getNombre() == null || request.getNombre().trim().isEmpty()
                || request.getCorreo() == null || request.getCorreo().trim().isEmpty()
                || request.getEventoId() == null) {

            ctx.json(Map.of("ok", false, "mensaje", "Todos los campos son obligatorios"));
            return;
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            session.beginTransaction();

            Evento evento = session.get(Evento.class, Long.valueOf(request.getEventoId()));

            if (evento == null) {
                ctx.json(Map.of("ok", false, "mensaje", "El evento no existe"));
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
            ctx.json(Map.of("ok", false, "mensaje", "Error guardando la inscripción: " + e.getMessage()));
        }
    }

    // Procesa el escaneo del código QR
    private static void procesarAsistencia(Context ctx) {
        Map body = ctx.bodyAsClass(Map.class);
        String token = (String) body.get("token");

        if (token == null || token.trim().isEmpty()) {
            ctx.status(400).json(Map.of("ok", false, "mensaje", "Token inválido"));
            return;
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            session.beginTransaction();
            Inscripcion inscripcion = session.createQuery("FROM Inscripcion WHERE tokenQr = :token", Inscripcion.class)
                    .setParameter("token", token)
                    .uniqueResult();

            if (inscripcion == null) {
                ctx.json(Map.of("ok", false, "mensaje", "Código QR no reconocido."));
                return;
            }

            if (inscripcion.isAsistio()) {
                ctx.json(Map.of("ok", false, "mensaje", "Este QR ya fue escaneado anteriormente."));
                return;
            }

            inscripcion.setAsistio(true);
            session.merge(inscripcion);
            session.getTransaction().commit();

            ctx.json(Map.of("ok", true, "mensaje", "Asistencia registrada para: " + inscripcion.getNombre()));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("ok", false, "mensaje", "Error en BD: " + e.getMessage()));
        }
    }

    // Elimina una inscripción si el estudiante cancela
    private static void cancelarInscripcion(Context ctx) {
        Long idInscripcion = Long.valueOf(ctx.pathParam("id"));

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            session.beginTransaction();
            Inscripcion inscripcion = session.get(Inscripcion.class, idInscripcion);

            if (inscripcion != null) {
                Evento evento = inscripcion.getEvento();
                evento.setInscritos(Math.max(0, evento.getInscritos() - 1));

                session.merge(evento);
                session.remove(inscripcion);
                session.getTransaction().commit();

                ctx.json(Map.of("ok", true, "mensaje", "Inscripción cancelada."));
            } else {
                ctx.status(404).json(Map.of("ok", false, "mensaje", "Inscripción no encontrada."));
            }
        } catch (Exception e) {
            ctx.status(500).json(Map.of("ok", false, "mensaje", "Error: " + e.getMessage()));
        }
    }

    // Devuelve los datos JSON para Chart.js o Google Charts
    private static void obtenerEstadisticas(Context ctx) {
        Long idEvento = Long.valueOf(ctx.pathParam("id"));

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Evento evento = session.get(Evento.class, idEvento);

            if (evento == null) {
                ctx.status(404).json(Map.of("ok", false, "mensaje", "Evento no encontrado."));
                return;
            }

            Long totalAsistentes = session.createQuery("SELECT COUNT(i) FROM Inscripcion i WHERE i.evento.id = :eventoId AND i.asistio = true", Long.class)
                    .setParameter("eventoId", idEvento)
                    .uniqueResult();

            int inscritos = evento.getInscritos();
            long asistentes = (totalAsistentes != null) ? totalAsistentes : 0;
            double porcentaje = (inscritos > 0) ? ((double) asistentes / inscritos) * 100 : 0.0;

            ctx.json(Map.of(
                    "ok", true,
                    "evento", evento.getTitulo(),
                    "totalInscritos", inscritos,
                    "totalAsistentes", asistentes,
                    "porcentajeAsistencia", String.format("%.2f%%", porcentaje)
            ));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("ok", false, "mensaje", "Error: " + e.getMessage()));
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
                e1.setPublicado(true);

                Evento e2 = new Evento();
                e2.setTitulo("Taller de Docker");
                e2.setDescripcion("Contenedores y despliegue básico");
                e2.setFecha("2026-03-22");
                e2.setLugar("Laboratorio 3");
                e2.setCupoMaximo(30);
                e2.setInscritos(30);
                e2.setPublicado(true);

                Evento e3 = new Evento();
                e3.setTitulo("Conferencia de IA");
                e3.setDescripcion("Aplicaciones prácticas de inteligencia artificial");
                e3.setFecha("2026-03-25");
                e3.setLugar("Salón A-12");
                e3.setCupoMaximo(100);
                e3.setInscritos(65);
                e3.setPublicado(true);

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