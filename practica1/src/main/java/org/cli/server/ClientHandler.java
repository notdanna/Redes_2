package org.cli.server;

import java.net.Socket;
import java.util.*;
import java.io.*;

// Maneja la conexión de un cliente con el servidor CLI
public class ClientHandler {
    private final Socket socket;
    private final BusinessLogic logic;

    public ClientHandler(Socket socket, Items items) {
        this.socket = socket;
        Map<Integer, Integer> cart = new TreeMap<>();
        this.logic = new BusinessLogic(items, cart);
    }

    // Inicia la comunicación con el cliente, recibe comandos y envía respuestas
    public void handle() {
        String line;
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true)) {

            out.println("¡Bienvenido a nuestra tienda! ^.^");

            // Recepción de comandos
            while ((line = in.readLine()) != null) {
                String res = handleCommand(line.trim());
                out.println(res);

                if (res.equals("¡Sesión cerrada exitosamente!"))
                    break;
            }
        }
        catch (IOException ignored) {
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    // Procesa un comando recibido desde el cliente y devuelve una respuesta
    private String handleCommand(String line) {
        if(line.isEmpty()) return "Error";
        String[] parts = line.trim().split("\\s");
        String cmd = parts[0].toUpperCase();

        String value = "";
        if(parts.length > 1)
            value = String.join(" ", Arrays.copyOfRange(parts, 1, parts.length));

        return switch(cmd) {
            case "SEARCH" -> handleSearch(value);
            case "LIST" -> handleList(value);
            case "ADD" -> handleAdd(parts);
            case "VIEW" -> handleView();
            case "UPDATE" -> handleUpdate(parts);
            case "CHECKOUT" -> handleCheckout();
            case "EXIT" -> "¡Sesión cerrada exitosamente!";
            default -> "Error: Ingresa un comando válido :(";
        };
    }

    // Maneja el comando de búsqueda por nombre, marca y ID
    private String handleSearch(String value) {
        if(value.isEmpty()) return "Error: SEARCH [name|brand|ID]";

        Map<Integer, Product> res = logic.searchProducts(value);
        if(res.isEmpty())
            return "Lo sentimos, no encontramos ningún producto";

        StringBuilder sb = new StringBuilder(String.format("%d producto%s encontrado%s\n",
                res.size(),
                res.size() == 1 ? "" : "s",
                res.size() == 1 ? "" : "s")
        );

        for(Product p : res.values())
            sb.append(p).append("\n");

        return sb.toString().trim();
    }

    // Maneja el comando de listar todos los productos o por tipo
    private String handleList(String value) {
        Map<Integer, Product> res;
        if(value.isEmpty()) {
            res = logic.listAllProducts();
            if(res.isEmpty())
                return "Lo sentimos, el inventario está vacío :(";
        }
        else {
            res = logic.listProductsByType(value);
            if(res.isEmpty())
                return "Lo sentimos, no encontramos el tipo solicitado :(";
        }

        StringBuilder sb = new StringBuilder(String.format("%d producto%s encontrado%s\n",
                res.size(),
                res.size() == 1 ? "" : "s",
                res.size() == 1 ? "" : "s")
        );

        for(Product p : res.values())
            sb.append(p).append("\n");

        return sb.toString().trim();
    }

    // Maneja el comando de añadir productos al carrito
    private String handleAdd(String[] parts) {
        if(parts.length != 3)
            return "Error: ADD [ID] [quantity]";

        try {
            int id = Integer.parseInt(parts[1]);
            int qty = Integer.parseInt(parts[2]);

            BusinessLogic.AddToCartResult result = logic.addToCart(id, qty);
            return result.success ? result.message : "Error: " + result.message;
        } catch (NumberFormatException e) {
            return "Error: ID y cantidad deben ser números";
        }
    }

    // Maneja el comando de ver el carrito
    private String handleView() {
        Map<Integer, Integer> cart = logic.getCart();
        if(cart.isEmpty())
            return "¡El carrito está vacío!";

        StringBuilder sb = new StringBuilder(String.format("%d producto%s en el carrito\n",
                cart.size(),
                cart.size() == 1 ? "" : "s")
        );
        for(Map.Entry<Integer, Integer> entry : cart.entrySet()) {
            Product p = logic.getProductById(entry.getKey());
            sb.append(String.format("#%d - %s (x%d)\n", entry.getKey(), p.getName(), entry.getValue()));
        }
        return sb.toString().trim();
    }

    // Maneja el comando de actualizar el carrito
    private String handleUpdate(String[] parts) {
        if(parts.length != 3)
            return "Error: UPDATE [ID] [qty]";

        Map<Integer, Integer> cart = logic.getCart();
        if(cart.isEmpty())
            return "El carrito está vacío";

        try {
            int id = Integer.parseInt(parts[1]);
            int qty = Integer.parseInt(parts[2]);

            BusinessLogic.UpdateCartResult result = logic.updateCart(id, qty);
            return result.success ? result.message : "Error: " + result.message;
        } catch (NumberFormatException e) {
            return "Error: ID y cantidad deben ser números";
        }
    }

    // Maneja el comando para finalizar la compra
    private String handleCheckout() {
        BusinessLogic.CheckoutResult result = logic.checkout();

        if (!result.success) {
            return "Error: " + result.message;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("================================================\n");
        sb.append("TICKET\n");
        sb.append(result.datetime).append("\n");
        sb.append("------------------------------------------------\n");

        // Cabecera de la tabla
        sb.append(String.format("%-4s | %-25s | %-3s | %-8s\n", "ID", "Producto", "Qty", "Subtotal"));
        sb.append("------------------------------------------------\n");

        for(BusinessLogic.CheckoutItem item : result.items) {
            sb.append(String.format("%-4d | %-25s | %-3d | $%-7.2f\n",
                    item.id, item.name, item.quantity, item.subtotal));
        }

        sb.append("------------------------------------------------\n");
        sb.append(String.format("%-35s | $%-7.2f\n", "TOTAL", result.total));
        sb.append("================================================\n");

        return sb.toString();
    }
}