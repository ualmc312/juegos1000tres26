package com.juegos1000tres.juegos1000tres_backend.modelos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "salas_entities")
public class SalaEntities {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "host_usuario_id", nullable = false)
    private Usuario host;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "sala_entities_jugadores",
            joinColumns = @JoinColumn(name = "sala_id"),
                inverseJoinColumns = @JoinColumn(name = "usuario_id"))
            private List<Usuario> jugadores = new ArrayList<>();

    public SalaEntities() {
    }

    public SalaEntities(Usuario host) {
        this.host = host;
        this.jugadores.add(host);
    }

    public Long getId() {
        return id;
    }

    public Usuario getHost() {
        return host;
    }

    public void setHost(Usuario host) {
        this.host = host;
    }

    public List<Usuario> getJugadores() {
        return Collections.unmodifiableList(jugadores);
    }

    public void setJugadores(List<Usuario> jugadores) {
        this.jugadores = new ArrayList<>(jugadores);
    }

}
