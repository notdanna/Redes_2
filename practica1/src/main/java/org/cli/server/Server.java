package org.cli.server;

import java.net.Socket;
import java.net.ServerSocket;
import java.io.InputStream;

public class Server {
    public static void main(String [] args) throws Exception{
        final int PORT = 5006;
        Items items = new Items("/plants.json");     // Ruta del inventarion.json

        // Crea un servidor socket y espera conexiones entrantes
        try(ServerSocket server = new ServerSocket(PORT)) {
            System.out.println("Listening on port " + PORT + "...");

            // Bucle infinito para atender clientes uno por uno
            while(true) {
                try(Socket socket = server.accept()) {
                    System.out.println("Client connected: " + socket.getInetAddress());
                    new ClientHandler(socket, items).handle();   // Interacción con el cliente
                }
            }
        }
    }
}


