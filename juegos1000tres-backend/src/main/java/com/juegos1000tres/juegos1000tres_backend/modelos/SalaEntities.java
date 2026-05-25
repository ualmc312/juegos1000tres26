package com.juegos1000tres.juegos1000tres_backend.modelos;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "salas_entities")
public class SalaEntities {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String uuid;

    @Column(nullable = false, length = 80)
    private String hostNombre;

    @Column(name = "host_usuario_id")
    private Long hostUsuarioId;

    @Column(name = "host_usuario_token", length = 120)
    private String hostUsuarioToken;

    @Column(nullable = false)
    private LocalDate creadaEn;

    @OneToMany(mappedBy = "sala", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("orden ASC")
    private List<SalaJuegoOrden> juegosJugados = new ArrayList<>();

    public SalaEntities() {
    }

    public SalaEntities(String uuid, String hostNombre) {
        this(uuid, hostNombre, LocalDate.now());
    }

    public SalaEntities(String uuid, String hostNombre, LocalDate creadaEn) {
        this.uuid = uuid;
        this.hostNombre = hostNombre;
        this.creadaEn = creadaEn;
    }

    public Long getId() {
        return id;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getHostNombre() {
        return hostNombre;
    }

    public void setHostNombre(String hostNombre) {
        this.hostNombre = hostNombre;
    }

    public Long getHostUsuarioId() {
        return hostUsuarioId;
    }

    public void setHostUsuarioId(Long hostUsuarioId) {
        this.hostUsuarioId = hostUsuarioId;
    }

    public String getHostUsuarioToken() {
        return hostUsuarioToken;
    }

    public void setHostUsuarioToken(String hostUsuarioToken) {
        this.hostUsuarioToken = hostUsuarioToken;
    }

    public LocalDate getCreadaEn() {
        return creadaEn;
    }

    public void setCreadaEn(LocalDate creadaEn) {
        this.creadaEn = creadaEn;
    }

    public List<SalaJuegoOrden> getJuegosJugados() {
        return Collections.unmodifiableList(juegosJugados);
    }

    public void setJuegosJugados(List<SalaJuegoOrden> juegosJugados) {
        this.juegosJugados = new ArrayList<>(juegosJugados);
    }

    public void registrarJuego(SalaJuegoOrden juego) {
        this.juegosJugados.add(juego);
    }

}
