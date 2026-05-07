package com.juegos1000tres.juegos1000tres_backend.modelos;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "juegos_entities")
public class JuegoEntities {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int numeroJugadores;

    @Column(nullable = false)
    private boolean necesitaPantalla;

    public JuegoEntities() {
    }

    public JuegoEntities(int numeroJugadores, boolean necesitaPantalla) {
        this.numeroJugadores = numeroJugadores;
        this.necesitaPantalla = necesitaPantalla;
    }

    public Long getId() {
        return id;
    }

    public int getNumeroJugadores() {
        return numeroJugadores;
    }

    public void setNumeroJugadores(int numeroJugadores) {
        this.numeroJugadores = numeroJugadores;
    }

    public boolean isNecesitaPantalla() {
        return necesitaPantalla;
    }

    public void setNecesitaPantalla(boolean necesitaPantalla) {
        this.necesitaPantalla = necesitaPantalla;
    }

}
