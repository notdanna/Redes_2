package udp;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import com.google.gson.*;

public class Receiver {
    private static final int DEFAULT_LISTEN_PORT = 6000;
    private static final String DEFAULT_SENDER_IP = "127.0.0.1";
    private static final int DEFAULT_SENDER_PORT = 5000;

    private final int listenPort;
    private final String senderIp;
    private final int senderPort;
    private final int songId;
    private final String title;
    private final String artist;
    private final String mp3Name;
    private final String coverName;

    private static final String BASE_DIR = System.getProperty("user.dir");
    private static final String MUSIC_DIR = BASE_DIR + "/musicReceiver";
    private static final String CATALOG_FILE = BASE_DIR + "/catalog.json";

    public Receiver(int listenPort, String senderIp, int senderPort,
                    int songId, String title, String artist, String mp3Name, String coverName) {
        this.listenPort = listenPort;
        this.senderIp = senderIp;
        this.senderPort = senderPort;
        this.songId = songId;
        this.title = title;
        this.artist = artist;
        this.mp3Name = mp3Name;
        this.coverName = coverName;
    }

    public void receive() throws IOException {
        ensureDirs();

        DatagramSocket socket = new DatagramSocket(listenPort);
        InetAddress senderAddr = InetAddress.getByName(senderIp);
        System.out.println("[RECV] Escuchando en puerto " + listenPort);

        // Enviar READY al sender
        byte[] ready = "READY".getBytes();
        DatagramPacket readyPacket = new DatagramPacket(ready, ready.length, senderAddr, senderPort);
        socket.send(readyPacket);
        System.out.println("[RECV] READY enviado a " + senderIp + ":" + senderPort);

        // Variables Go-Back-N
        int esperado = 0;
        int ultimoOk = -1;
        ByteArrayOutputStream fileBytes = new ByteArrayOutputStream();
        boolean termino = false;

        socket.setSoTimeout(2000); // 2 segundos

        while (!termino) {
            try {
                byte[] buffer = new byte[65535];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                PacketData pkt = parsePacket(Arrays.copyOf(packet.getData(), packet.getLength()));
                if (pkt == null) continue;

                System.out.println("[RECV] <- pkt " + pkt.seq + "  bytes=" + pkt.data.length + "  last=" + pkt.isLast);

                // Aceptar solo si es el paquete esperado
                if (pkt.seq == esperado) {
                    fileBytes.write(pkt.data);
                    ultimoOk = pkt.seq;
                    esperado++;

                    if (pkt.isLast) {
                        termino = true;
                    }
                }

                // Enviar ACK acumulativo
                int ackToSend = Math.max(ultimoOk, -1);
                sendAck(socket, packet.getAddress(), packet.getPort(), ackToSend);

                if (pkt.isLast && pkt.seq <= ultimoOk) {
                    termino = true;
                }

            } catch (SocketTimeoutException e) {
                if (termino) break;
            }
        }

        socket.close();
        System.out.println("[RECV] Transferencia terminada, guardando archivo...");

        // Guardar MP3
        saveTrackFile(fileBytes.toByteArray(), mp3Name);

        // Actualizar catálogo
        registerSong(songId, title, artist, mp3Name, coverName);

        System.out.println("[RECV] Listo. Esta canción ya está en el catálogo");
    }

    private static class PacketData {
        int seq;
        boolean isLast;
        byte[] data;
    }

    private PacketData parsePacket(byte[] pktBytes) {
        if (pktBytes.length < 5) return null;

        ByteBuffer bb = ByteBuffer.wrap(pktBytes);
        int seq = bb.getInt();
        byte lastFlag = bb.get();
        byte[] data = new byte[pktBytes.length - 5];
        bb.get(data);

        PacketData pkt = new PacketData();
        pkt.seq = seq;
        pkt.isLast = (lastFlag == 1);
        pkt.data = data;
        return pkt;
    }

    private void sendAck(DatagramSocket socket, InetAddress addr, int port, int seq) throws IOException {
        String msg = "ACK " + seq + "\n";
        byte[] data = msg.getBytes();
        DatagramPacket packet = new DatagramPacket(data, data.length, addr, port);
        socket.send(packet);
        System.out.println("[RECV] -> ACK " + seq);
    }

    private void ensureDirs() throws IOException {
        Files.createDirectories(Paths.get(MUSIC_DIR));
        File catalog = new File(CATALOG_FILE);
        if (!catalog.exists()) {
            try (FileWriter fw = new FileWriter(catalog)) {
                fw.write("[]");
            }
        }
    }

    private void saveTrackFile(byte[] data, String filename) throws IOException {
        Path destPath = Paths.get(MUSIC_DIR, filename);
        Files.write(destPath, data);
        System.out.println("[RECV] Canción guardada en " + destPath);
    }

    private void registerSong(int songId, String titulo, String artista, String mp3Filename, String coverFilename) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        // Leer catálogo
        String catalogJson = new String(Files.readAllBytes(Paths.get(CATALOG_FILE)));
        JsonArray catalog = gson.fromJson(catalogJson, JsonArray.class);
        if (catalog == null) catalog = new JsonArray();

        // Buscar si existe
        boolean found = false;
        for (int i = 0; i < catalog.size(); i++) {
            JsonObject song = catalog.get(i).getAsJsonObject();
            if (song.get("id").getAsInt() == songId) {
                song.addProperty("titulo", titulo);
                song.addProperty("artista", artista);
                song.addProperty("archivo", mp3Filename);
                song.addProperty("cover", coverFilename);
                found = true;
                break;
            }
        }

        // Si no existe, agregar nueva
        if (!found) {
            JsonObject newSong = new JsonObject();
            newSong.addProperty("id", songId);
            newSong.addProperty("titulo", titulo);
            newSong.addProperty("artista", artista);
            newSong.addProperty("archivo", mp3Filename);
            newSong.addProperty("cover", coverFilename);
            catalog.add(newSong);
        }

        // Guardar catálogo
        try (FileWriter fw = new FileWriter(CATALOG_FILE)) {
            gson.toJson(catalog, fw);
        }
        System.out.println("[RECV] catalog.json actualizado.");
    }

    public static void main(String[] args) {
        if (args.length < 8) {
            System.out.println("Uso: java Receiver --song-id ID --title TITLE --artist ARTIST --mp3-name MP3 --cover-name COVER [--listen-port PORT] [--sender-ip IP] [--sender-port PORT]");
            return;
        }

        int listenPort = DEFAULT_LISTEN_PORT;
        String senderIp = DEFAULT_SENDER_IP;
        int senderPort = DEFAULT_SENDER_PORT;
        Integer songId = null;
        String title = null;
        String artist = null;
        String mp3Name = null;
        String coverName = null;

        for (int i = 0; i < args.length - 1; i++) {
            switch (args[i]) {
                case "--listen-port":
                    listenPort = Integer.parseInt(args[++i]);
                    break;
                case "--sender-ip":
                    senderIp = args[++i];
                    break;
                case "--sender-port":
                    senderPort = Integer.parseInt(args[++i]);
                    break;
                case "--song-id":
                    songId = Integer.parseInt(args[++i]);
                    break;
                case "--title":
                    title = args[++i];
                    break;
                case "--artist":
                    artist = args[++i];
                    break;
                case "--mp3-name":
                    mp3Name = args[++i];
                    break;
                case "--cover-name":
                    coverName = args[++i];
                    break;
            }
        }

        if (songId == null || title == null || artist == null || mp3Name == null || coverName == null) {
            System.err.println("Faltan parámetros requeridos");
            return;
        }

        try {
            Receiver receiver = new Receiver(listenPort, senderIp, senderPort, songId, title, artist, mp3Name, coverName);
            receiver.receive();
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}