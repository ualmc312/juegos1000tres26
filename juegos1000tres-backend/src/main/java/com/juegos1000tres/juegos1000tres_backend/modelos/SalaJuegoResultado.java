package com.juegos1000tres.juegos1000tres_backend.modelos;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "sala_juego_resultado", uniqueConstraints = {
    @UniqueConstraint(columnNames = { "sala_juego_id", "nombre_jugador" })
})
public class SalaJuegoResultado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sala_juego_id", nullable = false)
    @JsonIgnore
    private SalaJuegoOrden salaJuego;

    @Column(nullable = false, length = 80)
    private String nombreJugador;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(nullable = false)
    private int puntuacion;

    @Column
    private Integer posicion;

    @Column(nullable = false)
    private boolean victoria;

    public SalaJuegoResultado() {
    }

    public SalaJuegoResultado(SalaJuegoOrden salaJuego, String nombreJugador, int puntuacion, boolean victoria) {
        this(salaJuego, nombreJugador, null, puntuacion, victoria);
    }

    public SalaJuegoResultado(SalaJuegoOrden salaJuego, String nombreJugador, Long usuarioId, int puntuacion, boolean victoria) {
        this.salaJuego = salaJuego;
        this.nombreJugador = nombreJugador;
        this.usuarioId = usuarioId;
        this.puntuacion = puntuacion;
        this.victoria = victoria;
    }

    public Long getId() {
        return id;
    }

    public SalaJuegoOrden getSalaJuego() {
        return salaJuego;
    }

    public void setSalaJuego(SalaJuegoOrden salaJuego) {
        this.salaJuego = salaJuego;
    }

    public String getNombreJugador() {
        return nombreJugador;
    }

    public void setNombreJugador(String nombreJugador) {
        this.nombreJugador = nombreJugador;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(Long usuarioId) {
        this.usuarioId = usuarioId;
    }

    public int getPuntuacion() {
        return puntuacion;
    }

    public void setPuntuacion(int puntuacion) {
        this.puntuacion = puntuacion;
    }

    public Integer getPosicion() {
        return posicion;
    }

    public void setPosicion(Integer posicion) {
        this.posicion = posicion;
    }

    public boolean isVictoria() {
        return victoria;
    }

    public void setVictoria(boolean victoria) {
        this.victoria = victoria;
    }
}
