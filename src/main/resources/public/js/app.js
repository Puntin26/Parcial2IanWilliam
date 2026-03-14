document.addEventListener("DOMContentLoaded", () => {

    // ==========================================
    // CAMBIAR VISTA TARJETAS / TABLA
    // ==========================================

    const btnCards = document.getElementById("btnCards");
    const btnTabla = document.getElementById("btnTabla");
    const vistaCards = document.getElementById("vistaCards");
    const vistaTabla = document.getElementById("vistaTabla");

    if (btnCards && btnTabla && vistaCards && vistaTabla) {

        btnCards.addEventListener("click", () => {

            vistaCards.classList.remove("d-none");
            vistaTabla.classList.add("d-none");

            btnCards.classList.remove("btn-outline-primary");
            btnCards.classList.add("btn-primary");

            btnTabla.classList.remove("btn-primary");
            btnTabla.classList.add("btn-outline-primary");

        });

        btnTabla.addEventListener("click", () => {

            vistaTabla.classList.remove("d-none");
            vistaCards.classList.add("d-none");

            btnTabla.classList.remove("btn-outline-primary");
            btnTabla.classList.add("btn-primary");

            btnCards.classList.remove("btn-primary");
            btnCards.classList.add("btn-outline-primary");

        });

    }

    // ==========================================
    // MODALES
    // ==========================================

    const modalInscripcionElement = document.getElementById("modalInscripcion");
    const modalQrElement = document.getElementById("modalQr");
    const eventoTitulo = document.getElementById("eventoSeleccionado");
    const form = document.getElementById("formInscripcion");
    const errorMensaje = document.getElementById("errorMensaje");
    const contenedorQr = document.getElementById("contenedorQr");
    const textoQr = document.getElementById("textoQr");

    if (!modalInscripcionElement || !eventoTitulo || !form || !errorMensaje) {
        console.error("Faltan elementos del modal de inscripción");
        return;
    }

    const modalInscripcion = new bootstrap.Modal(modalInscripcionElement);
    const modalQr = modalQrElement ? new bootstrap.Modal(modalQrElement) : null;

    let eventoActual = null;

    // ==========================================
    // CLICK EN BOTONES
    // ==========================================

    document.addEventListener("click", async (e) => {

        // ---------- INSCRIBIRSE ----------
        const btnInscribirse = e.target.closest(".btn-inscribirse");

        if (btnInscribirse) {

            eventoActual = {
                id: parseInt(btnInscribirse.dataset.id, 10),
                titulo: btnInscribirse.dataset.titulo,
                cupo: parseInt(btnInscribirse.dataset.cupo || "0", 10),
                inscritos: obtenerInscritosActuales(parseInt(btnInscribirse.dataset.id, 10))
            };

            eventoTitulo.innerText = eventoActual.titulo;
            errorMensaje.innerText = "";
            form.reset();
            modalInscripcion.show();

            return;
        }

        // ---------- CANCELAR INSCRIPCIÓN ----------
        const btnCancelar = e.target.closest(".btn-cancelar");

        if (btnCancelar) {

            const eventoId = parseInt(btnCancelar.dataset.id, 10);

            const confirmar = confirm("¿Seguro que quieres cancelar tu inscripción en este evento?");
            if (!confirmar) return;

            try {

                const respuesta = await fetch("/api/inscripciones/" + eventoId, {
                    method: "DELETE"
                });

                const data = await respuesta.json();

                alert(data.mensaje);

                if (!data.ok) return;

                actualizarInscritosEnPantalla(data.eventoId, data.inscritos);
                actualizarAccionesEvento(data.eventoId, false);

            } catch (error) {

                console.error(error);
                alert("Ocurrió un error al cancelar la inscripción");

            }

        }

    });

    // ==========================================
    // CONFIRMAR INSCRIPCIÓN
    // ==========================================

    form.addEventListener("submit", async (e) => {

        e.preventDefault();

        if (!eventoActual) {
            errorMensaje.innerText = "No se seleccionó ningún evento";
            return;
        }

        try {

            const respuesta = await fetch("/api/inscripciones", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify({
                    eventoId: eventoActual.id
                })
            });

            const data = await respuesta.json();

            if (!data.ok) {
                errorMensaje.innerText = data.mensaje;
                return;
            }

            alert(data.mensaje);

            actualizarInscritosEnPantalla(data.eventoId, data.inscritos);
            actualizarAccionesEvento(data.eventoId, true);

            modalInscripcion.hide();

            mostrarQr(data.eventoId, data.usuarioId, data.token);

        } catch (error) {

            console.error(error);
            errorMensaje.innerText = "Ocurrió un error al procesar la inscripción";

        }

    });

    // ==========================================
    // GENERAR QR
    // ==========================================

    function mostrarQr(eventoId, usuarioId, token) {

        if (!contenedorQr || !textoQr || !modalQr) return;

        const contenidoQr = JSON.stringify({
            eventoId: eventoId,
            usuarioId: usuarioId,
            token: token
        });

        contenedorQr.innerHTML = "";
        textoQr.innerText = contenidoQr;

        new QRCode(contenedorQr, {
            text: contenidoQr,
            width: 220,
            height: 220
        });

        modalQr.show();

    }

    // ==========================================
    // ACTUALIZAR INSCRITOS
    // ==========================================

    function actualizarInscritosEnPantalla(eventoId, nuevosInscritos) {

        const spanCard = document.getElementById(`inscritos-card-${eventoId}`);
        const spanTabla = document.getElementById(`inscritos-tabla-${eventoId}`);

        if (spanCard) spanCard.innerText = nuevosInscritos;
        if (spanTabla) spanTabla.innerText = nuevosInscritos;

    }

    function obtenerInscritosActuales(eventoId) {

        const spanCard = document.getElementById(`inscritos-card-${eventoId}`);
        const spanTabla = document.getElementById(`inscritos-tabla-${eventoId}`);

        if (spanCard) return parseInt(spanCard.innerText || "0", 10);
        if (spanTabla) return parseInt(spanTabla.innerText || "0", 10);

        return 0;

    }

    // ==========================================
    // CAMBIAR BOTONES (INSCRIBIR / CANCELAR)
    // ==========================================

    function actualizarAccionesEvento(eventoId, yaInscrito) {

        const inscritos = obtenerInscritosActuales(eventoId);

        const contenedorCard = document.getElementById(`acciones-card-${eventoId}`);
        const contenedorTabla = document.getElementById(`acciones-tabla-${eventoId}`);

        renderAccion(contenedorCard, inscritos, yaInscrito, true);
        renderAccion(contenedorTabla, inscritos, yaInscrito, false);

    }

    function renderAccion(contenedor, inscritos, yaInscrito, esCard) {

        if (!contenedor) return;

        const eventoId = contenedor.dataset.id;
        const titulo = contenedor.dataset.titulo || "";
        const cupo = parseInt(contenedor.dataset.cupo || "0", 10);

        if (yaInscrito) {

            contenedor.innerHTML = `
                <button
                    class="${esCard ? "btn btn-outline-danger w-100" : "btn btn-outline-danger btn-sm"} btn-cancelar"
                    data-id="${eventoId}"
                    data-titulo="${escaparHtml(titulo)}">
                    ${esCard ? "Cancelar inscripción" : "Cancelar"}
                </button>
            `;

            return;

        }

        if (inscritos >= cupo) {

            contenedor.innerHTML = `
                <button class="${esCard ? "btn btn-secondary w-100" : "btn btn-secondary btn-sm"}" disabled>
                    Cupo lleno
                </button>
            `;

            return;

        }

        contenedor.innerHTML = `
            <button
                class="${esCard ? "btn btn-success w-100" : "btn btn-success btn-sm"} btn-inscribirse"
                data-id="${eventoId}"
                data-titulo="${escaparHtml(titulo)}"
                data-cupo="${cupo}"
                data-inscritos="${inscritos}">
                Inscribirse
            </button>
        `;

    }

    // ==========================================
    // ESCAPAR HTML
    // ==========================================

    function escaparHtml(valor) {

        return String(valor)
            .replace(/&/g, "&amp;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#39;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;");

    }

    // ==========================================
    // VALIDACIONES CREAR / EDITAR EVENTOS
    // ==========================================

    const formEventos = document.querySelectorAll('form[action^="/admin/eventos/crear"], form[action^="/admin/eventos/editar"]');

    formEventos.forEach(formEvento => {

        formEvento.addEventListener("submit", function (event) {

            const titulo = formEvento.querySelector('input[name="titulo"]').value.trim();
            const descripcion = formEvento.querySelector('textarea[name="descripcion"]').value.trim();
            const cupo = formEvento.querySelector('input[name="cupoMaximo"]').value;
            const fecha = formEvento.querySelector('input[name="fecha"]').value;

            if (titulo === "" || descripcion === "") {

                alert("El título y la descripción no pueden estar vacíos");
                event.preventDefault();
                return;

            }

            if (cupo < 1) {

                alert("El cupo máximo debe ser al menos 1");
                event.preventDefault();
                return;

            }

            if (formEvento.getAttribute("action") === "/admin/eventos/crear") {

                const fechaSeleccionada = new Date(fecha);
                const hoy = new Date();

                hoy.setHours(0,0,0,0);

                const fechaAjustada = new Date(
                    fechaSeleccionada.getTime() +
                    fechaSeleccionada.getTimezoneOffset() * 60000
                );

                if (fechaAjustada < hoy) {

                    alert("No puedes crear un evento con fecha pasada");
                    event.preventDefault();

                }

            }

        });

    });

    // ==========================================
    // VALIDACIÓN LOGIN
    // ==========================================

    const formLogin = document.getElementById("formLogin");

    if (formLogin) {

        formLogin.addEventListener("submit", function (event) {

            const password = formLogin.querySelector('input[name="password"]').value.trim();
            const correo = formLogin.querySelector('input[name="correo"]').value.trim();

            if (correo === "") {

                alert("Ingresa tu correo");
                event.preventDefault();
                return;

            }

            if (password.length < 4) {

                alert("La contraseña debe tener al menos 4 caracteres");
                event.preventDefault();

            }

        });

    }

});