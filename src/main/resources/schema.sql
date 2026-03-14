-- Script de inicialización de la base de datos H2

CREATE TABLE IF NOT EXISTS usuarios (
                                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                        correo VARCHAR(255) NOT NULL UNIQUE,
    username VARCHAR(255) NOT NULL, -- Agregado para el nuevo constructor
    password VARCHAR(255) NOT NULL,
    rol VARCHAR(50) NOT NULL,
    bloqueado BOOLEAN DEFAULT FALSE
    );

CREATE TABLE IF NOT EXISTS eventos (
                                       id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                       titulo VARCHAR(255) NOT NULL,
    descripcion TEXT,
    fecha VARCHAR(50) NOT NULL,
    hora VARCHAR(50), -- Agregado para el nuevo constructor
    lugar VARCHAR(255) NOT NULL,
    cupoMaximo INT NOT NULL,
    inscritos INT DEFAULT 0,
    publicado BOOLEAN DEFAULT FALSE
    );

CREATE TABLE IF NOT EXISTS inscripciones (
                                             id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                             evento_id BIGINT NOT NULL,
                                             nombre VARCHAR(255) NOT NULL,
    correo VARCHAR(255) NOT NULL,
    tokenQr VARCHAR(255) NOT NULL UNIQUE,
    asistio BOOLEAN DEFAULT FALSE,
    fechaInscripcion TIMESTAMP, -- Agregado (LocalDateTime)
    fechaAsistencia TIMESTAMP,  -- Agregado (LocalDateTime)
    FOREIGN KEY (evento_id) REFERENCES eventos(id) ON DELETE CASCADE
    );