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
@Table(name = "amistades", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"usuario1_id", "usuario2_id"})
})
public class Amistad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario1_id", nullable = false)
    private Usuario usuario1;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario2_id", nullable = false)
    private Usuario usuario2;

    @Column(nullable = false)
    private LocalDateTime fechaCreacion;

    public Amistad() {
    }

    public Amistad(Usuario usuario1, Usuario usuario2) {
        // Asegurar orden consistente para evitar duplicados
        if (usuario1.getId() < usuario2.getId()) {
            this.usuario1 = usuario1;
            this.usuario2 = usuario2;
        } else {
            this.usuario1 = usuario2;
            this.usuario2 = usuario1;
        }
        this.fechaCreacion = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Usuario getUsuario1() {
        return usuario1;
    }

    public void setUsuario1(Usuario usuario1) {
        this.usuario1 = usuario1;
    }

    public Usuario getUsuario2() {
        return usuario2;
    }

    public void setUsuario2(Usuario usuario2) {
        this.usuario2 = usuario2;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    /**
     * Obtiene el otro usuario de la amistad
     */
    public Usuario getOtroUsuario(Usuario usuario) {
        if (usuario.getId().equals(usuario1.getId())) {
            return usuario2;
        } else if (usuario.getId().equals(usuario2.getId())) {
            return usuario1;
        }
        return null;
    }
}
