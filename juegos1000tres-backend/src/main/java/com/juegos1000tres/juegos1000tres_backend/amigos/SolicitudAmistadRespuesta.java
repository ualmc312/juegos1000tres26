package com.juegos1000tres.juegos1000tres_backend.amigos;

public class SolicitudAmistadRespuesta {
    private Long id;
    private UsuarioRespuesta usuarioSolicitante;
    private UsuarioRespuesta usuarioReceptor;
    private String estado;
    private String fechaCreacion;

    public SolicitudAmistadRespuesta() {
    }

    public SolicitudAmistadRespuesta(Long id, UsuarioRespuesta usuarioSolicitante, 
                                     UsuarioRespuesta usuarioReceptor, String estado, String fechaCreacion) {
        this.id = id;
        this.usuarioSolicitante = usuarioSolicitante;
        this.usuarioReceptor = usuarioReceptor;
        this.estado = estado;
        this.fechaCreacion = fechaCreacion;
    }

    // Getters y setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UsuarioRespuesta getUsuarioSolicitante() {
        return usuarioSolicitante;
    }

    public void setUsuarioSolicitante(UsuarioRespuesta usuarioSolicitante) {
        this.usuarioSolicitante = usuarioSolicitante;
    }

    public UsuarioRespuesta getUsuarioReceptor() {
        return usuarioReceptor;
    }

    public void setUsuarioReceptor(UsuarioRespuesta usuarioReceptor) {
        this.usuarioReceptor = usuarioReceptor;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(String fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }
}
