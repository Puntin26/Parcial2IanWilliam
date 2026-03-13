package edu.pucmm.icc352.encapsulaciones;

import jakarta.persistence.*;

@Entity
@Table(name = "inscripciones")
public class Inscripcion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "evento_id")
    private Evento evento;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String correo;

    @Column(nullable = false, unique = true)
    private String tokenQr;

    @Column(nullable = false)
    private boolean asistio;

    public Inscripcion() {
    }

    public Inscripcion(Evento evento, String nombre, String correo, String tokenQr) {
        this.evento = evento;
        this.nombre = nombre;
        this.correo = correo;
        this.tokenQr = tokenQr;
        this.asistio = false;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Evento getEvento() {
        return evento;
    }

    public void setEvento(Evento evento) {
        this.evento = evento;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public String getTokenQr() {
        return tokenQr;
    }

    public void setTokenQr(String tokenQr) {
        this.tokenQr = tokenQr;
    }

    public boolean isAsistio() {
        return asistio;
    }

    public void setAsistio(boolean asistio) {
        this.asistio = asistio;
    }
}