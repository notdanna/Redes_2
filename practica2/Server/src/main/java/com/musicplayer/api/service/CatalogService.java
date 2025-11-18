package com.musicplayer.api.service;

import com.google.gson.*;
import com.musicplayer.api.model.Song;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.*;

@Service
public class CatalogService {
    private static final String BASE_DIR = System.getProperty("user.dir");
    private static final String CATALOG_FILE = BASE_DIR + "/catalog.json";
    private static final String MUSIC_DIR = BASE_DIR + "/musicReceiver";
    private static final String COVER_DIR = BASE_DIR + "/covers";

    private final Gson gson = new Gson();

    public List<Song> loadCatalog() {
        List<Song> songs = new ArrayList<>();
        File catalogFile = new File(CATALOG_FILE);

        if (!catalogFile.exists()) {
            return songs;
        }

        try {
            String json = new String(Files.readAllBytes(Paths.get(CATALOG_FILE)));
            JsonArray catalog = gson.fromJson(json, JsonArray.class);

            if (catalog != null) {
                for (int i = 0; i < catalog.size(); i++) {
                    JsonObject obj = catalog.get(i).getAsJsonObject();
                    Song song = new Song(
                            obj.get("id").getAsInt(),
                            obj.get("titulo").getAsString(),
                            obj.get("artista").getAsString(),
                            obj.get("archivo").getAsString(),
                            obj.get("cover").getAsString()
                    );
                    songs.add(song);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return songs;
    }

    public Song getSongById(int id) {
        return loadCatalog().stream()
                .filter(s -> s.getId() == id)
                .findFirst()
                .orElse(null);
    }

    public Path getSongFilePath(Song song) {
        return Paths.get(MUSIC_DIR, song.getArchivo());
    }

    public Path getCoverFilePath(Song song) {
        return Paths.get(COVER_DIR, song.getCover());
    }
}