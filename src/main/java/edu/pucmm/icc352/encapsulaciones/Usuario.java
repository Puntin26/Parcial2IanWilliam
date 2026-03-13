package edu.pucmm.icc352.encapsulaciones;

import jakarta.persistence.*;

@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String correo;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String rol; // Puede ser "ADMINISTRADOR", "ORGANIZADOR", "PARTICIPANTE"

    private boolean bloqueado;

    // Recuerda generar los Getters, Setters y Constructores vacíos y con parámetros
}