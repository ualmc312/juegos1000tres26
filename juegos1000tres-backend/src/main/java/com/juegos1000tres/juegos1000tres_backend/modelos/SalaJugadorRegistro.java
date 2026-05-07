package com.juegos1000tres.juegos1000tres_backend.modelos;

import java.time.LocalDateTime;

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
@Table(name = "sala_jugador", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "sala_id", "usuario_id" })
})
public class SalaJugadorRegistro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sala_id", nullable = false)
    private SalaRegistro sala;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false)
    private int victorias;

    @Column(nullable = false)
    private int derrotas;

    @Column(nullable = false)
    private int partidasJugadas;

    @Column(nullable = false)
    private int puntuacionTotal;

    @Column
    private LocalDateTime unidoEn;

    @Column
    private LocalDateTime salidoEn;

    public SalaJugadorRegistro() {
    }

    public SalaJugadorRegistro(SalaRegistro sala, Usuario usuario) {
        this.sala = sala;
        this.usuario = usuario;
        this.victorias = 0;
        this.derrotas = 0;
        this.partidasJugadas = 0;
        this.puntuacionTotal = 0;
    }

    public Long getId() {
        return id;
    }

    public SalaRegistro getSala() {
        return sala;
    }

    public void setSala(SalaRegistro sala) {
        this.sala = sala;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public int getVictorias() {
        return victorias;
    }

    public void setVictorias(int victorias) {
        this.victorias = victorias;
    }

    public int getDerrotas() {
        return derrotas;
    }

    public void setDerrotas(int derrotas) {
        this.derrotas = derrotas;
    }

    public int getPartidasJugadas() {
        return partidasJugadas;
    }

    public void setPartidasJugadas(int partidasJugadas) {
        this.partidasJugadas = partidasJugadas;
    }

    public int getPuntuacionTotal() {
        return puntuacionTotal;
    }

    public void setPuntuacionTotal(int puntuacionTotal) {
        this.puntuacionTotal = puntuacionTotal;
    }

    public LocalDateTime getUnidoEn() {
        return unidoEn;
    }

    public void setUnidoEn(LocalDateTime unidoEn) {
        this.unidoEn = unidoEn;
    }

    public LocalDateTime getSalidoEn() {
        return salidoEn;
    }

    public void setSalidoEn(LocalDateTime salidoEn) {
        this.salidoEn = salidoEn;
    }
}
