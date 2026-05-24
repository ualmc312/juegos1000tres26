package com.juegos1000tres.juegos1000tres_backend.amigos;

public class UsuarioRespuesta {
    private Long id;
    private String nombre;
    private String email;
    private String salaUuid;

    public UsuarioRespuesta() {
    }

    public UsuarioRespuesta(Long id, String nombre, String email) {
        this(id, nombre, email, null);
    }

    public UsuarioRespuesta(Long id, String nombre, String email, String salaUuid) {
        this.id = id;
        this.nombre = nombre;
        this.email = email;
        this.salaUuid = salaUuid;
    }

    // Getters y setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getSalaUuid() {
        return salaUuid;
    }

    public void setSalaUuid(String salaUuid) {
        this.salaUuid = salaUuid;
    }
}
