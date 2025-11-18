package com.musicplayer.api.model;

public class Song {
    private int id;
    private String titulo;
    private String artista;
    private String archivo;
    private String cover;
    private String coverUrl;
    private String streamUrl;

    public Song() {}

    public Song(int id, String titulo, String artista, String archivo, String cover) {
        this.id = id;
        this.titulo = titulo;
        this.artista = artista;
        this.archivo = archivo;
        this.cover = cover;
        this.coverUrl = "/api/songs/" + id + "/cover";
        this.streamUrl = "/api/songs/" + id + "/stream";
    }

    // Getters y Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getArtista() { return artista; }
    public void setArtista(String artista) { this.artista = artista; }

    public String getArchivo() { return archivo; }
    public void setArchivo(String archivo) { this.archivo = archivo; }

    public String getCover() { return cover; }
    public void setCover(String cover) { this.cover = cover; }

    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }

    public String getStreamUrl() { return streamUrl; }
    public void setStreamUrl(String streamUrl) { this.streamUrl = streamUrl; }
}