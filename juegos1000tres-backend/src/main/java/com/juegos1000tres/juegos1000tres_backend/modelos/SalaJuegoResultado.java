package com.juegos1000tres.juegos1000tres_backend.modelos;

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
        @UniqueConstraint(columnNames = { "sala_juego_id", "usuario_id" })
})
public class SalaJuegoResultado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sala_juego_id", nullable = false)
    private SalaJuegoOrden salaJuego;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false)
    private int puntuacion;

    @Column
    private Integer posicion;

    @Column(nullable = false)
    private boolean victoria;

    public SalaJuegoResultado() {
    }

    public SalaJuegoResultado(SalaJuegoOrden salaJuego, Usuario usuario, int puntuacion, boolean victoria) {
        this.salaJuego = salaJuego;
        this.usuario = usuario;
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

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
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
