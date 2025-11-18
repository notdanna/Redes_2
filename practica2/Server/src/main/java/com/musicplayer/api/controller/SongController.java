package com.musicplayer.api.controller;

import com.musicplayer.api.model.Song;
import com.musicplayer.api.service.CatalogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class SongController {

    @Autowired
    private CatalogService catalogService;

    @GetMapping("/songs")
    public ResponseEntity<List<Song>> listSongs() {
        List<Song> songs = catalogService.loadCatalog();
        return ResponseEntity.ok(songs);
    }

    @GetMapping("/songs/{id}/stream")
    public ResponseEntity<?> streamSong(@PathVariable int id) {
        Song song = catalogService.getSongById(id);

        if (song == null) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Música no encontrada");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }

        Path mp3Path = catalogService.getSongFilePath(song);

        if (!Files.exists(mp3Path)) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Archivo MP3 no encontrado");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }

        Resource resource = new FileSystemResource(mp3Path.toFile());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/mpeg"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(resource);
    }

    @GetMapping("/songs/{id}/cover")
    public ResponseEntity<?> getCover(@PathVariable int id) {
        Song song = catalogService.getSongById(id);

        if (song == null) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Música no encontrada");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }

        Path coverPath = catalogService.getCoverFilePath(song);

        if (!Files.exists(coverPath)) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Cover no encontrado");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }

        Resource resource = new FileSystemResource(coverPath.toFile());

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .body(resource);
    }
}