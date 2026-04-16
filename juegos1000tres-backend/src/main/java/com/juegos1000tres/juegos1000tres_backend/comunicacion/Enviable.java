package com.juegos1000tres.juegos1000tres_backend.comunicacion;

public abstract class Enviable {

    public abstract String toJson();

    public abstract void fromJson(String json);
}