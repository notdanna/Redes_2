package udp;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import com.google.gson.*;

public class SyncAll {
    private static final String BASE_DIR = System.getProperty("user.dir");
    private static final String CATALOG_FILE = BASE_DIR + "/catalog.json";

    public static void main(String[] args) throws Exception {
        File catalogFile = new File(CATALOG_FILE);
        if (!catalogFile.exists()) {
            System.err.println("[SYNC] No existe catalog.json");
            return;
        }

        Gson gson = new Gson();
        String catalogJson = new String(Files.readAllBytes(Paths.get(CATALOG_FILE)));
        JsonArray catalog = gson.fromJson(catalogJson, JsonArray.class);

        if (catalog == null || catalog.size() == 0) {
            System.err.println("[SYNC] catalog.json está vacío o no es lista");
            return;
        }

        System.out.println("[SYNC] Se van a transferir " + catalog.size() + " canciones en orden...");

        for (int i = 0; i < catalog.size(); i++) {
            JsonObject song = catalog.get(i).getAsJsonObject();

            int songId = song.get("id").getAsInt();
            String title = song.get("titulo").getAsString();
            String artist = song.get("artista").getAsString();
            String mp3Name = song.get("archivo").getAsString();
            String coverName = song.get("cover").getAsString();
            String filePath = "musicSender/" + mp3Name;

            System.out.println();
            System.out.println("[SYNC] ***** Canción ID=" + songId + " *****");
            System.out.println("       title: " + title);
            System.out.println("       artist: " + artist);
            System.out.println("       source file: " + filePath);
            System.out.println("       dest mp3_name: " + mp3Name);
            System.out.println("       cover: " + coverName);

            // Lanzar Sender
            ProcessBuilder senderPb = new ProcessBuilder(
                    "mvn", "exec:java",
                    "-Dexec.mainClass=udp.Sender",
                    "-Dexec.args=" + filePath
            );
            senderPb.directory(new File(BASE_DIR));
            senderPb.inheritIO();
            Process senderProc = senderPb.start();

            // Esperar un poco
            Thread.sleep(200);

            // Lanzar Receiver
            ProcessBuilder receiverPb = new ProcessBuilder(
                    "mvn", "exec:java",
                    "-Dexec.mainClass=udp.Receiver",
                    "-Dexec.args=--listen-port 6000 --sender-ip 127.0.0.1 --sender-port 5000 " +
                            "--song-id " + songId + " " +
                            "--title " + title + " " +
                            "--artist " + artist + " " +
                            "--mp3-name " + mp3Name + " " +
                            "--cover-name " + coverName
            );
            receiverPb.directory(new File(BASE_DIR));
            receiverPb.inheritIO();
            Process receiverProc = receiverPb.start();

            // Esperar a que terminen
            int receiverRet = receiverProc.waitFor();
            int senderRet = senderProc.waitFor();

            System.out.println("[SYNC] receiver terminó con " + receiverRet + ", sender terminó con " + senderRet);
            System.out.println("[SYNC] Canción ID=" + songId + " terminada y registrada.\n");
        }

        System.out.println("[SYNC] TODAS LAS CANCIONES LISTAS");
    }
}