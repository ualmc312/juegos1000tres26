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
@Table(name = "sala_juego", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "sala_id", "orden" })
})
public class SalaJuegoOrden {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sala_id", nullable = false)
    private SalaRegistro sala;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "juego", nullable = false)
    private JuegoEntities juego;

    @Column(nullable = false)
    private int orden;

    @Column
    private LocalDateTime iniciadoEn;

    @Column
    private LocalDateTime finalizadoEn;

    public SalaJuegoOrden() {
    }

    public SalaJuegoOrden(SalaRegistro sala, JuegoEntities juego, int orden) {
        this.sala = sala;
        this.juego = juego;
        this.orden = orden;
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

    public JuegoEntities getJuego() {
        return juego;
    }

    public void setJuego(JuegoEntities juego) {
        this.juego = juego;
    }

    public int getOrden() {
        return orden;
    }

    public void setOrden(int orden) {
        this.orden = orden;
    }

    public LocalDateTime getIniciadoEn() {
        return iniciadoEn;
    }

    public void setIniciadoEn(LocalDateTime iniciadoEn) {
        this.iniciadoEn = iniciadoEn;
    }

    public LocalDateTime getFinalizadoEn() {
        return finalizadoEn;
    }

    public void setFinalizadoEn(LocalDateTime finalizadoEn) {
        this.finalizadoEn = finalizadoEn;
    }
}
