package org.cli.client;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class Client {
    public static void main(String[] args) throws Exception {
        // Host y puerto
        final String HOST = "127.0.0.1";
        final int PORT = 5006;

        // Abre el socket y streams (auto-flush en PrintWriter)
        try(
            Socket socket = new Socket(HOST, PORT);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
            Scanner sc = new Scanner(System.in)
        ) {
            // Mensajes de bienvenida del servidor
            System.out.println("Conectado al servidor en " + HOST + ":" + PORT);
            System.out.println(in.readLine());

            // Mientras la sesión se encuentre activa
            while(true) {
                showMenu();
                System.out.print("-> ");
                String cmd = sc.nextLine().trim();   // Lee respuesta del usuario
                if(cmd.isEmpty()) continue;

                out.println(cmd);               // Envía comando del cliente al servidor


                // Lee primera línea y comprueba si hay una conexión vigente
                String res = in.readLine();
                if(res == null) { 
                    System.out.println("¡Conexión perdida o finalizada!");
                     break;         
                }

                // Leer líneas de respuesta restantes
                StringBuilder block = new StringBuilder(res);
                while (in.ready()) {
                    String line = in.readLine();
                    if (line == null) break;
                    block.append("\n").append(line);
                }

                System.out.println(block);
                // Fin de sesión si el servidor responde con el comando exit
                if(res.equals("¡Sesión cerrada exitosamente!")) {
                    break;
                }
            }
        }
    }
    // Menú de la tienda
    private static void showMenu() {
        System.out.println("""
        \n==================================    ❀  GARDEN CENTER ❀    =============================
        - SEARCH [name|brand|ID]  : Buscar productos por nombre, marca o ID
        - LIST [null|type]        : Listar todos los productos o por tipo                                        
        - ADD [ID] [Quantity]     : Agregar producto al carrito                           /\\_/\\
        - VIEW                    : Ver el contenido del carrito                         (= ._.)
        - UPDATE [ID] [quantity]  : Actualizar cantidad de un producto en el carrito     / > 🌸\\>
        - CHECKOUT                : Finalizar compra y generar ticket
        - EXIT                    : Salir de la aplicación
        ==========================================================================================
        """);
    }
}
