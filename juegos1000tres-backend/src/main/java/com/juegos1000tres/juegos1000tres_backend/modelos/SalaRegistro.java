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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "sala")
public class SalaRegistro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "host_usuario_id", nullable = false)
    private Usuario hostUsuario;


    @Column(nullable = false)
    private LocalDateTime creadaEn;

    @Column
    private LocalDateTime cerradaEn;

    public SalaRegistro() {
    }

    public SalaRegistro(Usuario hostUsuario) {
        this.hostUsuario = hostUsuario;
    }

    @PrePersist
    @SuppressWarnings("unused")
    private void prepararCreacion() {
        if (creadaEn == null) {
            creadaEn = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Usuario getHostUsuario() {
        return hostUsuario;
    }

    public void setHostUsuario(Usuario hostUsuario) {
        this.hostUsuario = hostUsuario;
    }

    public LocalDateTime getCreadaEn() {
        return creadaEn;
    }

    public void setCreadaEn(LocalDateTime creadaEn) {
        this.creadaEn = creadaEn;
    }

    public LocalDateTime getCerradaEn() {
        return cerradaEn;
    }

    public void setCerradaEn(LocalDateTime cerradaEn) {
        this.cerradaEn = cerradaEn;
    }
}
