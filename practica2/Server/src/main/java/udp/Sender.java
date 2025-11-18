package udp;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.*;

public class Sender {
    private static final int DEFAULT_PORT = 5000;
    private static final int DEFAULT_MTU = 1024;
    private static final int DEFAULT_WINDOW = 5;
    private static final int DEFAULT_TIMEOUT = 200; // ms

    private final int port;
    private final int mtu;
    private final int windowSize;
    private final int timeout;
    private final String filePath;

    public Sender(String filePath, int port, int mtu, int windowSize, int timeout) {
        this.filePath = filePath;
        this.port = port;
        this.mtu = mtu;
        this.windowSize = windowSize;
        this.timeout = timeout;
    }

    public void send() throws IOException {
        File file = new File(filePath);
        if (!file.exists()) {
            throw new FileNotFoundException("Archivo no encontrado: " + filePath);
        }

        DatagramSocket socket = new DatagramSocket(port);
        System.out.println("[SEND] Esperando receptor en puerto " + port + "...");

        // Esperar READY del receiver
        byte[] buffer = new byte[1024];
        DatagramPacket readyPacket = new DatagramPacket(buffer, buffer.length);
        socket.receive(readyPacket);
        InetAddress clientAddr = readyPacket.getAddress();
        int clientPort = readyPacket.getPort();
        System.out.println("[SEND] Recibido '" + new String(readyPacket.getData(), 0, readyPacket.getLength())
                + "' de " + clientAddr + ":" + clientPort);

        // Cargar archivo en bloques
        List<byte[]> chunks = loadFileChunks(file);
        int totalPackets = chunks.size();
        System.out.println("[SEND] Archivo dividido en " + totalPackets + " paquetes");

        // Variables Go-Back-N
        int base = 0;
        int nextSeq = 0;
        boolean[] acked = new boolean[totalPackets];
        Map<Integer, Long> sendTime = new HashMap<>();

        socket.setSoTimeout(timeout);

        // Loop principal
        while (base < totalPackets) {
            // Enviar paquetes nuevos dentro de la ventana
            while (nextSeq < totalPackets && nextSeq < base + windowSize) {
                byte[] packet = makePacket(nextSeq, nextSeq == totalPackets - 1, chunks.get(nextSeq));
                DatagramPacket dp = new DatagramPacket(packet, packet.length, clientAddr, clientPort);
                socket.send(dp);
                sendTime.put(nextSeq, System.currentTimeMillis());
                System.out.println("[SEND] -> pkt " + nextSeq + " (ventana " + base + "-" + nextSeq + ")");
                nextSeq++;
            }

            try {
                // Recibir ACK
                DatagramPacket ackPacket = new DatagramPacket(buffer, buffer.length);
                socket.receive(ackPacket);
                Integer acknum = parseAck(new String(ackPacket.getData(), 0, ackPacket.getLength()));

                if (acknum != null) {
                    System.out.println("[SEND] <- ACK " + acknum);

                    // Marcar paquetes confirmados
                    for (int i = base; i <= acknum && i < totalPackets; i++) {
                        acked[i] = true;
                    }

                    // Deslizar ventana
                    while (base < totalPackets && acked[base]) {
                        base++;
                    }
                }
            } catch (SocketTimeoutException e) {
                // Timeout - retransmitir ventana
                long now = System.currentTimeMillis();
                if (base < totalPackets) {
                    Long baseTime = sendTime.get(base);
                    if (baseTime != null && now - baseTime >= timeout) {
                        System.out.println("[SEND] TIMEOUT en pkt " + base + ", retransmitiendo desde "
                                + base + " hasta " + (nextSeq - 1));
                        for (int seqR = base; seqR < nextSeq; seqR++) {
                            byte[] packet = makePacket(seqR, seqR == totalPackets - 1, chunks.get(seqR));
                            DatagramPacket dp = new DatagramPacket(packet, packet.length, clientAddr, clientPort);
                            socket.send(dp);
                            sendTime.put(seqR, System.currentTimeMillis());
                            System.out.println("[SEND] -> RE-TX pkt " + seqR);
                        }
                    }
                }
            }
        }

        System.out.println("[SEND] Transferencia completa. Cerrando socket.");
        socket.close();
    }

    private List<byte[]> loadFileChunks(File file) throws IOException {
        List<byte[]> chunks = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[mtu];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                byte[] chunk = Arrays.copyOf(buffer, bytesRead);
                chunks.add(chunk);
            }
        }
        return chunks;
    }

    private byte[] makePacket(int seq, boolean isLast, byte[] payload) {
        ByteBuffer bb = ByteBuffer.allocate(5 + payload.length);
        bb.putInt(seq);
        bb.put((byte) (isLast ? 1 : 0));
        bb.put(payload);
        return bb.array();
    }

    private Integer parseAck(String msg) {
        try {
            String[] parts = msg.trim().split(" ");
            if (parts.length >= 2 && parts[0].equals("ACK")) {
                return Integer.parseInt(parts[1]);
            }
        } catch (Exception e) {
            // Ignorar
        }
        return null;
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Uso: java Sender <archivo> [--port PORT] [--mtu MTU] [--window WINDOW] [--timeout TIMEOUT_MS]");
            return;
        }

        String file = args[0];
        int port = DEFAULT_PORT;
        int mtu = DEFAULT_MTU;
        int window = DEFAULT_WINDOW;
        int timeout = DEFAULT_TIMEOUT;

        // Parse argumentos
        for (int i = 1; i < args.length - 1; i++) {
            switch (args[i]) {
                case "--port":
                    port = Integer.parseInt(args[++i]);
                    break;
                case "--mtu":
                    mtu = Integer.parseInt(args[++i]);
                    break;
                case "--window":
                    window = Integer.parseInt(args[++i]);
                    break;
                case "--timeout":
                    timeout = Integer.parseInt(args[++i]);
                    break;
            }
        }

        try {
            Sender sender = new Sender(file, port, mtu, window, timeout);
            sender.send();
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}