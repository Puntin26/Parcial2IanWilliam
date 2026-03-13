document.addEventListener("DOMContentLoaded", () => {
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

    // simula base de datos de inscritos
    const inscripciones = {};

    botones.forEach(btn => {
        btn.addEventListener("click", () => {
            const id = btn.dataset.id;
            const titulo = btn.dataset.titulo;
            const cupo = parseInt(btn.dataset.cupo);
            const inscritos = parseInt(btn.dataset.inscritos);

            eventoActual = {
                id,
                titulo,
                cupo,
                inscritos
            };

            eventoTitulo.innerText = titulo;
            errorMensaje.innerText = "";

            form.reset();
            modal.show();
        });
    });

    form.addEventListener("submit", e => {
        e.preventDefault();

        const nombre = document.getElementById("nombre").value.trim();
        const correo = document.getElementById("correo").value.trim();

        if (!nombre || !correo) {
            errorMensaje.innerText = "Todos los campos son obligatorios";
            return;
        }

        if (eventoActual.inscritos >= eventoActual.cupo) {
            errorMensaje.innerText = "El evento ya alcanzó el cupo máximo";
            return;
        }

        if (!inscripciones[eventoActual.id]) {
            inscripciones[eventoActual.id] = [];
        }

        if (inscripciones[eventoActual.id].includes(correo)) {
            errorMensaje.innerText = "Ya estás inscrito en este evento";
            return;
        }

        inscripciones[eventoActual.id].push(correo);
        eventoActual.inscritos++;

        alert("Inscripción realizada correctamente");
        modal.hide();
    });
});