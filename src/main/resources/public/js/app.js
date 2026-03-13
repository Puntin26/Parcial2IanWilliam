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
    const modalElement = document.getElementById("modalInscripcion");
    const eventoTitulo = document.getElementById("eventoSeleccionado");
    const form = document.getElementById("formInscripcion");
    const errorMensaje = document.getElementById("errorMensaje");

    if (!modalElement || !eventoTitulo || !form || !errorMensaje) {
        console.error("Faltan elementos del modal en el HTML");
        return;
    }

    const modal = new bootstrap.Modal(modalElement);
    let eventoActual = null;

    botones.forEach(btn => {
        btn.addEventListener("click", () => {
            eventoActual = {
                id: parseInt(btn.dataset.id),
                titulo: btn.dataset.titulo,
                cupo: parseInt(btn.dataset.cupo),
                inscritos: parseInt(btn.dataset.inscritos)
            };

            eventoTitulo.innerText = eventoActual.titulo;
            errorMensaje.innerText = "";
            form.reset();
            modal.show();
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

            actualizarVistaEvento(data.eventoId, data.inscritos, data.cupoMaximo);

            alert(data.mensaje);
            modal.hide();
        } catch (error) {
            console.error(error);
            errorMensaje.innerText = "Ocurrió un error al procesar la inscripción";
        }
    });

    function actualizarVistaEvento(eventoId, inscritos, cupoMaximo) {
        const spanCard = document.getElementById(`inscritos-card-${eventoId}`);
        const spanTabla = document.getElementById(`inscritos-tabla-${eventoId}`);
        const btnCard = document.getElementById(`btn-card-${eventoId}`);
        const btnTabla = document.getElementById(`btn-tabla-${eventoId}`);

        if (spanCard) spanCard.innerText = inscritos;
        if (spanTabla) spanTabla.innerText = inscritos;

        if (btnCard) btnCard.dataset.inscritos = inscritos;
        if (btnTabla) btnTabla.dataset.inscritos = inscritos;

        if (inscritos >= cupoMaximo) {
            if (btnCard) {
                btnCard.disabled = true;
                btnCard.innerText = "Cupo lleno";
                btnCard.classList.remove("btn-success");
                btnCard.classList.add("btn-secondary");
            }

            if (btnTabla) {
                btnTabla.disabled = true;
                btnTabla.innerText = "Cupo lleno";
                btnTabla.classList.remove("btn-success");
                btnTabla.classList.add("btn-secondary");
            }
        }
    }
});