package edu.pucmm.icc352;

import edu.pucmm.icc352.encapsulaciones.Evento;
import edu.pucmm.icc352.encapsulaciones.Inscripcion;
import edu.pucmm.icc352.encapsulaciones.Usuario;
import edu.pucmm.icc352.servicios.HibernateUtil;
import freemarker.template.Configuration;
import freemarker.template.Template;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.h2.tools.Server;
import org.hibernate.Session;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.io.StringWriter;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;

public class Main {

    public static void main(String[] args) throws SQLException {

        Server.createTcpServer("-tcp", "-tcpAllowOthers", "-tcpPort", "9092").start();
        System.out.println("✅ Servidor H2 iniciado en modo TCP en el puerto 9092");

        crearAdminPorDefecto();
        crearEventosPrueba();

        Javalin app = Javalin.create(config -> {
            config.staticFiles.add("/public");

            Configuration fmConfig = new Configuration(Configuration.VERSION_2_3_32);
            fmConfig.setClassForTemplateLoading(Main.class, "/");
            fmConfig.setDefaultEncoding("UTF-8");

            config.fileRenderer((filePath, model, context) -> {
                try {
                    Template template = fmConfig.getTemplate(filePath);
                    StringWriter out = new StringWriter();
                    template.process(model, out);
                    return out.toString();
                } catch (Exception e) {
                    return "Error renderizando template: " + e.getMessage();
                }
            });

            config.routes.get("/", ctx -> ctx.redirect("/login"));

            config.routes.get("/login", ctx -> {
                if (usuarioLogueado(ctx)) {
                    ctx.redirect("/eventos");
                    return;
                }
                ctx.render("templates/login.html");
            });

            config.routes.get("/registro", ctx -> {
                if (usuarioLogueado(ctx)) {
                    ctx.redirect("/eventos");
                    return;
                }
                ctx.render("templates/registro.html");
            });

            config.routes.post("/login", Main::procesarLogin);
            config.routes.post("/registro", Main::procesarRegistro);

            config.routes.get("/logout", ctx -> {
                if (ctx.req().getSession(false) != null) {
                    ctx.req().getSession().invalidate();
                }
                ctx.redirect("/login");
            });

            config.routes.get("/eventos", ctx -> {
                System.out.println("SESSION usuarioId EN /eventos: " + ctx.sessionAttribute("usuarioId"));

                if (!usuarioLogueado(ctx)) {
                    ctx.redirect("/login");
                    return;
                }

                String rol = ctx.sessionAttribute("usuarioRol");
                String correo = ctx.sessionAttribute("usuarioCorreo");

                try (Session session = HibernateUtil.getSessionFactory().openSession()) {
                    List<Evento> eventosReales;

                    if ("PARTICIPANTE".equals(rol)) {
                        eventosReales = session.createQuery(
                                "FROM Evento e WHERE e.publicado = true ORDER BY e.id",
                                Evento.class
                        ).list();
                    } else {
                        eventosReales = session.createQuery(
                                "FROM Evento e ORDER BY e.id",
                                Evento.class
                        ).list();
                    }

                    List<Long> eventosInscritosIds = session.createQuery(
                                    "SELECT i.evento.id FROM Inscripcion i WHERE lower(i.correo) = :correo",
                                    Long.class
                            )
                            .setParameter("correo", correo.toLowerCase())
                            .list();

                    Map<String, Object> modelo = new HashMap<>();
                    modelo.put("titulo", "Eventos Académicos");
                    modelo.put("eventos", eventosReales);
                    modelo.put("eventosInscritosIds", eventosInscritosIds);
                    modelo.put("usuario", construirUsuarioModelo(ctx));

                    ctx.render("templates/eventos.html", modelo);
                }
            });

            config.routes.get("/escanear", ctx -> {
                if (!esAdminOOrganizador(ctx)) {
                    ctx.redirect("/eventos");
                    return;
                }

                Map<String, Object> modelo = new HashMap<>();
                modelo.put("usuario", construirUsuarioModelo(ctx));
                ctx.render("templates/escanear.html", modelo);
            });

            config.routes.get("/eventos/{id}/resumen", ctx -> {
                if (!esAdminOOrganizador(ctx)) {
                    ctx.redirect("/eventos");
                    return;
                }

                Long idEvento = Long.valueOf(ctx.pathParam("id"));

                try (Session session = HibernateUtil.getSessionFactory().openSession()) {
                    Evento evento = session.get(Evento.class, idEvento);

                    if (evento == null) {
                        ctx.status(404).result("Evento no encontrado");
                        return;
                    }

                    Map<String, Object> modelo = new HashMap<>();
                    modelo.put("evento", evento);
                    modelo.put("usuario", construirUsuarioModelo(ctx));

                    ctx.render("templates/resumen_evento.html", modelo);
                }
            });

            config.routes.get("/admin/usuarios", ctx -> {
                if (!esAdmin(ctx)) {
                    ctx.status(403).result("Acceso denegado. Solo administradores.");
                    return;
                }

                try (Session session = HibernateUtil.getSessionFactory().openSession()) {
                    List<Usuario> usuarios = session.createQuery("FROM Usuario ORDER BY id", Usuario.class).list();

                    Map<String, Object> modelo = new HashMap<>();
                    modelo.put("usuarios", usuarios);
                    modelo.put("usuario", construirUsuarioModelo(ctx));
                    ctx.render("templates/admin_usuarios.html", modelo);
                }
            });

            config.routes.post("/admin/usuarios/rol/{id}", ctx -> {
                if (!esAdmin(ctx)) {
                    ctx.status(403).result("Acceso denegado.");
                    return;
                }

                Long idUsuario = Long.valueOf(ctx.pathParam("id"));
                String nuevoRol = ctx.formParam("rol");

                try (Session session = HibernateUtil.getSessionFactory().openSession()) {
                    session.beginTransaction();

                    Usuario usuarioTarget = session.get(Usuario.class, idUsuario);
                    if (usuarioTarget != null && !"ADMINISTRADOR".equals(usuarioTarget.getRol())) {
                        usuarioTarget.setRol(nuevoRol);
                        session.merge(usuarioTarget);
                    }

                    session.getTransaction().commit();
                    ctx.redirect("/admin/usuarios");
                }
            });

            config.routes.post("/admin/usuarios/bloquear/{id}", ctx -> {
                if (!esAdmin(ctx)) {
                    ctx.status(403).result("Acceso denegado.");
                    return;
                }

                Long idUsuario = Long.valueOf(ctx.pathParam("id"));

                try (Session session = HibernateUtil.getSessionFactory().openSession()) {
                    session.beginTransaction();

                    Usuario usuarioTarget = session.get(Usuario.class, idUsuario);
                    if (usuarioTarget != null && !"ADMINISTRADOR".equals(usuarioTarget.getRol())) {
                        usuarioTarget.setBloqueado(!usuarioTarget.isBloqueado());
                        session.merge(usuarioTarget);
                    }

                    session.getTransaction().commit();
                    ctx.redirect("/admin/usuarios");
                }
            });

            config.routes.post("/admin/eventos/crear", ctx -> {
                if (!esAdminOOrganizador(ctx)) {
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
                if (!esAdminOOrganizador(ctx)) {
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
                if (!esAdminOOrganizador(ctx)) {
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

            config.routes.post("/admin/eventos/eliminar/{id}", ctx -> {
                if (!esAdmin(ctx)) {
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

            config.routes.post("/api/inscripciones", Main::procesarInscripcion);
            config.routes.post("/api/cancelar-inscripcion", Main::cancelarInscripcion);
            config.routes.post("/api/asistencia", Main::procesarAsistencia);
            config.routes.get("/api/estadisticas/{id}", Main::obtenerEstadisticas);
            config.routes.get("/api/graficos/{id}", Main::obtenerDatosGraficos);

        }).start(7000);
    }

    private static void procesarLogin(Context ctx) {
        String correo = ctx.formParam("correo");
        String username = ctx.formParam("username");
        String password = ctx.formParam("password");

        if (correo == null || correo.trim().isEmpty()
                || username == null || username.trim().isEmpty()
                || password == null || password.trim().isEmpty()) {

            Map<String, Object> modelo = new HashMap<>();
            modelo.put("error", "Todos los campos son obligatorios");
            ctx.render("templates/login.html", modelo);
            return;
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Usuario usuario = session.createQuery(
                            "FROM Usuario u WHERE lower(u.correo) = :correo AND lower(u.username) = :username AND u.password = :password",
                            Usuario.class
                    )
                    .setParameter("correo", correo.trim().toLowerCase())
                    .setParameter("username", username.trim().toLowerCase())
                    .setParameter("password", password.trim())
                    .uniqueResult();

            if (usuario == null) {
                Map<String, Object> modelo = new HashMap<>();
                modelo.put("error", "Credenciales incorrectas");
                ctx.render("templates/login.html", modelo);
                return;
            }

            if (usuario.isBloqueado()) {
                Map<String, Object> modelo = new HashMap<>();
                modelo.put("error", "Tu usuario está bloqueado");
                ctx.render("templates/login.html", modelo);
                return;
            }

            ctx.req().getSession(true);

            ctx.sessionAttribute("usuarioId", usuario.getId());
            ctx.sessionAttribute("usuarioCorreo", usuario.getCorreo());
            ctx.sessionAttribute("usuarioUsername", usuario.getUsername());
            ctx.sessionAttribute("usuarioRol", usuario.getRol());

            System.out.println("LOGIN OK -> usuarioId: " + usuario.getId());

            ctx.redirect("/eventos");

        } catch (Exception e) {
            Map<String, Object> modelo = new HashMap<>();
            modelo.put("error", "Ocurrió un error al iniciar sesión");
            ctx.render("templates/login.html", modelo);
        }
    }

    private static void procesarRegistro(Context ctx) {
        String correo = ctx.formParam("correo");
        String username = ctx.formParam("username");
        String password = ctx.formParam("password");

        if (correo == null || correo.trim().isEmpty()
                || username == null || username.trim().isEmpty()
                || password == null || password.trim().isEmpty()) {

            Map<String, Object> modelo = new HashMap<>();
            modelo.put("error", "Todos los campos son obligatorios");
            ctx.render("templates/registro.html", modelo);
            return;
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            session.beginTransaction();

            Usuario usuarioPorCorreo = session.createQuery(
                            "FROM Usuario u WHERE lower(u.correo) = :correo",
                            Usuario.class
                    )
                    .setParameter("correo", correo.trim().toLowerCase())
                    .uniqueResult();

            if (usuarioPorCorreo != null) {
                rollbackIfActive(session);
                Map<String, Object> modelo = new HashMap<>();
                modelo.put("error", "Ya existe una cuenta con ese correo");
                ctx.render("templates/registro.html", modelo);
                return;
            }

            Usuario usuarioPorUsername = session.createQuery(
                            "FROM Usuario u WHERE lower(u.username) = :username",
                            Usuario.class
                    )
                    .setParameter("username", username.trim().toLowerCase())
                    .uniqueResult();

            if (usuarioPorUsername != null) {
                rollbackIfActive(session);
                Map<String, Object> modelo = new HashMap<>();
                modelo.put("error", "Ese username ya está en uso");
                ctx.render("templates/registro.html", modelo);
                return;
            }

            Usuario nuevoUsuario = new Usuario(
                    correo.trim().toLowerCase(),
                    username.trim(),
                    password.trim(),
                    "PARTICIPANTE"
            );

            session.persist(nuevoUsuario);
            session.getTransaction().commit();

            Map<String, Object> modelo = new HashMap<>();
            modelo.put("exito", "Registro completado. Ya puedes iniciar sesión.");
            ctx.render("templates/login.html", modelo);

        } catch (Exception e) {
            Map<String, Object> modelo = new HashMap<>();
            modelo.put("error", "Ocurrió un error al registrar el usuario");
            ctx.render("templates/registro.html", modelo);
        }
    }

    private static void procesarInscripcion(Context ctx) {
        if (!usuarioLogueado(ctx)) {
            ctx.status(401).json(Map.of(
                    "ok", false,
                    "mensaje", "Debes iniciar sesión"
            ));
            return;
        }

        InscripcionRequest request = ctx.bodyAsClass(InscripcionRequest.class);
        String correoSesion = ctx.sessionAttribute("usuarioCorreo");

        if (request.getNombre() == null || request.getNombre().trim().isEmpty()
                || request.getEventoId() == null
                || correoSesion == null || correoSesion.trim().isEmpty()) {

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
                rollbackIfActive(session);
                ctx.json(Map.of(
                        "ok", false,
                        "mensaje", "El evento no existe"
                ));
                return;
            }

            Long inscripcionesExistentes = session.createQuery(
                            "SELECT COUNT(i) FROM Inscripcion i WHERE i.evento.id = :eventoId AND lower(i.correo) = :correo",
                            Long.class
                    )
                    .setParameter("eventoId", evento.getId())
                    .setParameter("correo", correoSesion.trim().toLowerCase())
                    .uniqueResult();

            if (inscripcionesExistentes != null && inscripcionesExistentes > 0) {
                rollbackIfActive(session);
                ctx.json(Map.of(
                        "ok", false,
                        "mensaje", "Ya estás inscrito en este evento"
                ));
                return;
            }

            if (evento.getInscritos() >= evento.getCupoMaximo()) {
                rollbackIfActive(session);
                ctx.json(Map.of(
                        "ok", false,
                        "mensaje", "El evento ya alcanzó su cupo máximo"
                ));
                return;
            }

            String token = "QR-" + System.currentTimeMillis();

            Inscripcion inscripcion = new Inscripcion(
                    evento,
                    request.getNombre().trim(),
                    correoSesion.trim().toLowerCase(),
                    token
            );
            inscripcion.setFechaInscripcion(LocalDateTime.now());

            session.persist(inscripcion);

            evento.setInscritos(evento.getInscritos() + 1);
            session.merge(evento);

            session.getTransaction().commit();

            ctx.json(Map.of(
                    "ok", true,
                    "mensaje", "Inscripción realizada correctamente",
                    "eventoId", evento.getId(),
                    "correo", inscripcion.getCorreo(),
                    "token", inscripcion.getTokenQr(),
                    "inscritos", evento.getInscritos()
            ));

        } catch (Exception e) {
            ctx.json(Map.of(
                    "ok", false,
                    "mensaje", "Error guardando la inscripción: " + e.getMessage()
            ));
        }
    }

    private static void cancelarInscripcion(Context ctx) {
        if (!usuarioLogueado(ctx)) {
            ctx.status(401).json(Map.of(
                    "ok", false,
                    "mensaje", "Debes iniciar sesión"
            ));
            return;
        }

        InscripcionRequest request = ctx.bodyAsClass(InscripcionRequest.class);
        String correoSesion = ctx.sessionAttribute("usuarioCorreo");

        if (request.getEventoId() == null
                || correoSesion == null || correoSesion.trim().isEmpty()) {

            ctx.json(Map.of(
                    "ok", false,
                    "mensaje", "Datos inválidos"
            ));
            return;
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            session.beginTransaction();

            Inscripcion inscripcion = session.createQuery(
                            "FROM Inscripcion i WHERE i.evento.id = :eventoId AND lower(i.correo) = :correo",
                            Inscripcion.class
                    )
                    .setParameter("eventoId", request.getEventoId())
                    .setParameter("correo", correoSesion.trim().toLowerCase())
                    .uniqueResult();

            if (inscripcion == null) {
                rollbackIfActive(session);
                ctx.json(Map.of(
                        "ok", false,
                        "mensaje", "No existe una inscripción para este usuario en este evento"
                ));
                return;
            }

            Evento evento = inscripcion.getEvento();

            try {
                LocalDate fechaEvento = LocalDate.parse(evento.getFecha());
                LocalDate hoy = LocalDate.now();

                if (fechaEvento.isBefore(hoy)) {
                    rollbackIfActive(session);
                    ctx.json(Map.of(
                            "ok", false,
                            "mensaje", "No puedes cancelar la inscripción porque la fecha del evento ya pasó"
                    ));
                    return;
                }
            } catch (Exception e) {
                rollbackIfActive(session);
                ctx.json(Map.of(
                        "ok", false,
                        "mensaje", "La fecha del evento no es válida"
                ));
                return;
            }

            session.remove(inscripcion);
            evento.setInscritos(Math.max(0, evento.getInscritos() - 1));
            session.merge(evento);

            session.getTransaction().commit();

            ctx.json(Map.of(
                    "ok", true,
                    "mensaje", "Inscripción cancelada correctamente",
                    "eventoId", evento.getId(),
                    "inscritos", evento.getInscritos()
            ));

        } catch (Exception e) {
            ctx.json(Map.of(
                    "ok", false,
                    "mensaje", "Error cancelando inscripción: " + e.getMessage()
            ));
        }
    }

    private static void procesarAsistencia(Context ctx) {
        if (!esAdminOOrganizador(ctx)) {
            ctx.status(403).json(Map.of(
                    "ok", false,
                    "mensaje", "No tienes permisos para registrar asistencia"
            ));
            return;
        }

        Map<?, ?> body = ctx.bodyAsClass(Map.class);
        String token = body.get("token") != null ? body.get("token").toString() : null;

        if (token == null || token.trim().isEmpty()) {
            ctx.status(400).json(Map.of(
                    "ok", false,
                    "mensaje", "Token inválido"
            ));
            return;
        }

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            session.beginTransaction();

            Inscripcion inscripcion = session.createQuery(
                            "FROM Inscripcion i WHERE i.tokenQr = :token",
                            Inscripcion.class
                    )
                    .setParameter("token", token)
                    .uniqueResult();

            if (inscripcion == null) {
                rollbackIfActive(session);
                ctx.json(Map.of(
                        "ok", false,
                        "mensaje", "Código QR no reconocido."
                ));
                return;
            }

            if (inscripcion.isAsistio()) {
                rollbackIfActive(session);
                ctx.json(Map.of(
                        "ok", false,
                        "mensaje", "Este QR ya fue escaneado anteriormente."
                ));
                return;
            }

            inscripcion.setAsistio(true);
            inscripcion.setFechaAsistencia(LocalDateTime.now());
            session.merge(inscripcion);
            session.getTransaction().commit();

            ctx.json(Map.of(
                    "ok", true,
                    "mensaje", "Asistencia registrada para: " + inscripcion.getNombre()
            ));

        } catch (Exception e) {
            ctx.status(500).json(Map.of(
                    "ok", false,
                    "mensaje", "Error en BD: " + e.getMessage()
            ));
        }
    }

    private static void obtenerEstadisticas(Context ctx) {
        if (!esAdminOOrganizador(ctx)) {
            ctx.status(403).json(Map.of(
                    "ok", false,
                    "mensaje", "No tienes permisos para ver estadísticas"
            ));
            return;
        }

        Long idEvento = Long.valueOf(ctx.pathParam("id"));

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Evento evento = session.get(Evento.class, idEvento);

            if (evento == null) {
                ctx.status(404).json(Map.of(
                        "ok", false,
                        "mensaje", "Evento no encontrado."
                ));
                return;
            }

            Long totalAsistentes = session.createQuery(
                            "SELECT COUNT(i) FROM Inscripcion i WHERE i.evento.id = :eventoId AND i.asistio = true",
                            Long.class
                    )
                    .setParameter("eventoId", idEvento)
                    .uniqueResult();

            int inscritos = evento.getInscritos();
            long asistentes = totalAsistentes != null ? totalAsistentes : 0L;
            double porcentaje = inscritos > 0 ? ((double) asistentes / inscritos) * 100 : 0.0;

            ctx.json(Map.of(
                    "ok", true,
                    "evento", evento.getTitulo(),
                    "totalInscritos", inscritos,
                    "totalAsistentes", asistentes,
                    "porcentajeAsistencia", String.format("%.2f%%", porcentaje)
            ));
        } catch (Exception e) {
            ctx.status(500).json(Map.of(
                    "ok", false,
                    "mensaje", "Error: " + e.getMessage()
            ));
        }
    }
    private static void obtenerDatosGraficos(Context ctx) {
        if (!esAdminOOrganizador(ctx)) {
            ctx.status(403).json(Map.of(
                    "ok", false,
                    "mensaje", "No tienes permisos para ver gráficos"
            ));
            return;
        }

        Long idEvento = Long.valueOf(ctx.pathParam("id"));

        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            Evento evento = session.get(Evento.class, idEvento);

            if (evento == null) {
                ctx.status(404).json(Map.of(
                        "ok", false,
                        "mensaje", "Evento no encontrado"
                ));
                return;
            }

            List<Inscripcion> inscripciones = session.createQuery(
                            "FROM Inscripcion i WHERE i.evento.id = :eventoId ORDER BY i.fechaInscripcion ASC",
                            Inscripcion.class
                    )
                    .setParameter("eventoId", idEvento)
                    .list();

            Map<String, Integer> inscripcionesPorDia = new LinkedHashMap<>();
            Map<String, Integer> asistenciaPorHora = new LinkedHashMap<>();

            DateTimeFormatter formatoFecha = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            DateTimeFormatter formatoHora = DateTimeFormatter.ofPattern("HH:00");

            for (Inscripcion inscripcion : inscripciones) {

                if (inscripcion.getFechaInscripcion() != null) {
                    String dia = inscripcion.getFechaInscripcion().format(formatoFecha);
                    inscripcionesPorDia.put(dia, inscripcionesPorDia.getOrDefault(dia, 0) + 1);
                }

                if (inscripcion.getFechaAsistencia() != null) {
                    String hora = inscripcion.getFechaAsistencia().format(formatoHora);
                    asistenciaPorHora.put(hora, asistenciaPorHora.getOrDefault(hora, 0) + 1);
                }
            }

            Map<String, Object> respuesta = new HashMap<>();
            respuesta.put("ok", true);

            Map<String, Object> bloqueInscripciones = new HashMap<>();
            bloqueInscripciones.put("labels", new ArrayList<>(inscripcionesPorDia.keySet()));
            bloqueInscripciones.put("valores", new ArrayList<>(inscripcionesPorDia.values()));

            Map<String, Object> bloqueAsistencia = new HashMap<>();
            bloqueAsistencia.put("labels", new ArrayList<>(asistenciaPorHora.keySet()));
            bloqueAsistencia.put("valores", new ArrayList<>(asistenciaPorHora.values()));

            respuesta.put("inscripciones", bloqueInscripciones);
            respuesta.put("asistencia", bloqueAsistencia);

            ctx.json(respuesta);

        } catch (Exception e) {
            ctx.status(500).json(Map.of(
                    "ok", false,
                    "mensaje", "Error generando gráficos: " + e.getMessage()
            ));
        }
    }

    private static void crearAdminPorDefecto() {
        try (Session session = HibernateUtil.getSessionFactory().openSession()) {
            session.beginTransaction();

            Usuario admin = session.createQuery(
                            "FROM Usuario u WHERE u.correo = :correo",
                            Usuario.class
                    )
                    .setParameter("correo", "admin@pucmm.edu.do")
                    .uniqueResult();

            if (admin == null) {
                Usuario nuevoAdmin = new Usuario(
                        "admin@pucmm.edu.do",
                        "admin",
                        "admin123",
                        "ADMINISTRADOR"
                );
                session.persist(nuevoAdmin);
                System.out.println("✅ Administrador creado automáticamente.");
            } else {
                boolean actualizado = false;

                if (admin.getUsername() == null || admin.getUsername().isEmpty()) {
                    admin.setUsername("admin");
                    actualizado = true;
                }

                if (actualizado) {
                    session.merge(admin);
                    System.out.println("✅ Username del admin actualizado.");
                }
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

    private static Map<String, Object> construirUsuarioModelo(Context ctx) {
        Map<String, Object> usuario = new HashMap<>();
        usuario.put("id", ctx.sessionAttribute("usuarioId"));
        usuario.put("correo", ctx.sessionAttribute("usuarioCorreo"));
        usuario.put("username", ctx.sessionAttribute("usuarioUsername"));
        usuario.put("rol", ctx.sessionAttribute("usuarioRol"));
        return usuario;
    }

    private static boolean usuarioLogueado(Context ctx) {
        return ctx.sessionAttribute("usuarioId") != null;
    }

    private static boolean esAdmin(Context ctx) {
        String rol = ctx.sessionAttribute("usuarioRol");
        return "ADMINISTRADOR".equals(rol);
    }

    private static boolean esAdminOOrganizador(Context ctx) {
        String rol = ctx.sessionAttribute("usuarioRol");
        return "ADMINISTRADOR".equals(rol) || "ORGANIZADOR".equals(rol);
    }

    private static void rollbackIfActive(Session session) {
        try {
            if (session.getTransaction() != null && session.getTransaction().isActive()) {
                session.getTransaction().rollback();
            }
        } catch (Exception ignored) {
        }
    }
}