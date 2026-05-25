package com.juegos1000tres.juegos1000tres_backend.modelos;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
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
    @JsonIgnore
    private SalaEntities sala;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "juego", nullable = false)
    private JuegoEntities juego;

    @Column(nullable = false)
    private int orden;

    @Column(nullable = false)
    private LocalDate fechaJugado;

    @OneToMany(mappedBy = "salaJuego", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("id ASC")
    private List<SalaJuegoResultado> jugadores = new ArrayList<>();

    public SalaJuegoOrden() {
    }

    public SalaJuegoOrden(SalaEntities sala, JuegoEntities juego, int orden) {
        this.sala = sala;
        this.juego = juego;
        this.orden = orden;
        this.fechaJugado = LocalDate.now();
    }

    public Long getId() {
        return id;
    }

    public SalaEntities getSala() {
        return sala;
    }

    public void setSala(SalaEntities sala) {
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

    public LocalDate getFechaJugado() {
        return fechaJugado;
    }

    public void setFechaJugado(LocalDate fechaJugado) {
        this.fechaJugado = fechaJugado;
    }

    public List<SalaJuegoResultado> getJugadores() {
        return Collections.unmodifiableList(jugadores);
    }

    public void setJugadores(List<SalaJuegoResultado> jugadores) {
        this.jugadores = new ArrayList<>(jugadores);
    }

    public void registrarJugador(SalaJuegoResultado resultado) {
        this.jugadores.add(resultado);
    }

    public void limpiarJugadores() {
        this.jugadores.clear();
    }
}
