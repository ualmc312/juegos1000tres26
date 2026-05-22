package com.juegos1000tres.juegos1000tres_backend.modelos;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "solicitudes_amistad", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"usuario_solicitante_id", "usuario_receptor_id"})
})
public class SolicitudAmistad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_solicitante_id", nullable = false)
    private Usuario usuarioSolicitante;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_receptor_id", nullable = false)
    private Usuario usuarioReceptor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoSolicitudAmistad estado;

    @Column(nullable = false)
    private LocalDateTime fechaCreacion;

    @Column
    private LocalDateTime fechaRespuesta;

    public SolicitudAmistad() {
    }

    public SolicitudAmistad(Usuario usuarioSolicitante, Usuario usuarioReceptor) {
        this.usuarioSolicitante = usuarioSolicitante;
        this.usuarioReceptor = usuarioReceptor;
        this.estado = EstadoSolicitudAmistad.PENDIENTE;
        this.fechaCreacion = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Usuario getUsuarioSolicitante() {
        return usuarioSolicitante;
    }

    public void setUsuarioSolicitante(Usuario usuarioSolicitante) {
        this.usuarioSolicitante = usuarioSolicitante;
    }

    public Usuario getUsuarioReceptor() {
        return usuarioReceptor;
    }

    public void setUsuarioReceptor(Usuario usuarioReceptor) {
        this.usuarioReceptor = usuarioReceptor;
    }

    public EstadoSolicitudAmistad getEstado() {
        return estado;
    }

    public void setEstado(EstadoSolicitudAmistad estado) {
        this.estado = estado;
        if (estado != EstadoSolicitudAmistad.PENDIENTE) {
            this.fechaRespuesta = LocalDateTime.now();
        }
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public LocalDateTime getFechaRespuesta() {
        return fechaRespuesta;
    }
}
