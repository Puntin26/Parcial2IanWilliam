document.addEventListener("DOMContentLoaded", () => {
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

    const botones = document.querySelectorAll(".btn-inscribirse");
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

    botones.forEach(btn => {
        btn.addEventListener("click", () => {
            eventoActual = {
                id: parseInt(btn.dataset.id),
                titulo: btn.dataset.titulo,
                cupo: parseInt(btn.dataset.cupo || "0"),
                inscritos: parseInt(btn.dataset.inscritos || "0")
            };

            eventoTitulo.innerText = eventoActual.titulo;
            errorMensaje.innerText = "";
            form.reset();
            modalInscripcion.show();
        });
    });

    form.addEventListener("submit", async e => {
        e.preventDefault();

        const nombre = document.getElementById("nombre").value.trim();
        const correo = document.getElementById("correo").value.trim().toLowerCase();

        if (!nombre || !correo) {
            errorMensaje.innerText = "Todos los campos son obligatorios";
            return;
        }

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
                    eventoId: eventoActual.id,
                    nombre: nombre,
                    correo: correo
                })
            });

            const data = await respuesta.json();

            if (!data.ok) {
                errorMensaje.innerText = data.mensaje;
                return;
            }

            alert(data.mensaje);
            modalInscripcion.hide();

            mostrarQr(data.eventoId, data.correo, data.token);

        } catch (error) {
            console.error(error);
            errorMensaje.innerText = "Ocurrió un error al procesar la inscripción";
        }
    });

    function mostrarQr(eventoId, correo, token) {
        if (!contenedorQr || !textoQr || !modalQr) return;

        const contenidoQr = JSON.stringify({
            eventoId: eventoId,
            correo: correo,
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
});