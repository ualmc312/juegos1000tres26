package com.juegos1000tres.juegos1000tres_backend.amigos;

public class AmistadRespuesta {
    private Long id;
    private UsuarioRespuesta usuario1;
    private UsuarioRespuesta usuario2;
    private String fechaCreacion;

    public AmistadRespuesta() {
    }

    public AmistadRespuesta(Long id, UsuarioRespuesta usuario1, UsuarioRespuesta usuario2, String fechaCreacion) {
        this.id = id;
        this.usuario1 = usuario1;
        this.usuario2 = usuario2;
        this.fechaCreacion = fechaCreacion;
    }

    // Getters y setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UsuarioRespuesta getUsuario1() {
        return usuario1;
    }

    public void setUsuario1(UsuarioRespuesta usuario1) {
        this.usuario1 = usuario1;
    }

    public UsuarioRespuesta getUsuario2() {
        return usuario2;
    }

    public void setUsuario2(UsuarioRespuesta usuario2) {
        this.usuario2 = usuario2;
    }

    public String getFechaCreacion() {
        return fechaCreacion;
    }

    public void setFechaCreacion(String fechaCreacion) {
        this.fechaCreacion = fechaCreacion;
    }
}
