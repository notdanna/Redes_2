package org.cli.server;
import java.net.Socket;
import java.util.*;
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

// Maneja la conexión de un cliente con el servidor
public class ClientHandler {
    /* ATRIBUTOS */
    private final Socket socket;
    private final Items items;
    private final Map<Integer, Integer> cart = new TreeMap<>();

    /* CONSTRUCTORES */
    public ClientHandler(Socket socket, Items items) {
        this.socket = socket;
        this.items = items;
    }

    /* FUNCIONES */
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
            try { socket.close(); } catch (IOException ignored) {} // libera el socket
        }
    }

    // Procesa un comando recibido desde el cliente y devuelve una respuesta
    private String handleCommand(String line) {
        if(line.isEmpty()) return "Error";
        String[] parts = line.trim().split("\\s");
        String cmd = parts[0].toUpperCase();
        Map<Integer, Product> res;

        String value = "";
        if(parts.length > 1)
            value = String.join(" ", Arrays.copyOfRange(parts, 1, parts.length));

        switch(cmd) {
            case "SEARCH" -> { return handleSearch(value); }
            case "LIST" -> { return handleList(value); } 
            case "ADD" -> { return handleAdd(parts); }
            case "VIEW" -> { return handleView(); }
            case "UPDATE" -> { return handleUpdate(parts); }
            case "CHECKOUT" -> { return handleCheckout(); }
            case "EXIT" -> { return "¡Sesión cerrada exitosamente!"; }
            default -> { return "Error: Ingresa un comando válido :("; }
        }
    }

    // Maneja el comando de búsqueda por nombre, marca y ID
    private String handleSearch(String value) {
        if(value.isEmpty()) return "Error: SEARCH [name|brand|ID]";
        
        Map<Integer, Product> res = items.find(value);
        res = items.find(value);
        if(res.isEmpty())
            return "Lo sentimos, no encontramos ningún producto";

        StringBuilder sb = new StringBuilder(String.format("%d producto%s encontrado%s\n",
                    res.size(),
                    res.size() == 1 ? "" : "s",
                    res.size() == 1 ? "" : "s")
        );

        for(Product p : res.values())
            sb.append(p + "\n");

        return sb.toString().trim();
    }

    // Maneja el comando de listar todos los productos o por tipo
    private String handleList(String value) {
        Map<Integer, Product> res;
        if(value.isEmpty()) {
            res = items.getItems();
            if(res.isEmpty())
                return "Lo sentimos, el inventario está vacío :(";
        }
        else {
            res = items.findByType(value);
            if(res.isEmpty()) 
                return "Lo sentimos, no encontramos el tipo solicitado :(";
        }

        StringBuilder sb = new StringBuilder(String.format("%d producto%s encontrado%s\n",
                    res.size(),
                    res.size() == 1 ? "" : "s",
                    res.size() == 1 ? "" : "s")
        );

        for(Product p : res.values())
            sb.append(p + "\n");

        return sb.toString().trim();
    }

    // Maneja el comando de añadir productos al carrito
    private String handleAdd(String[] parts) {
        if(parts.length != 3)
            return "Error: ADD [ID] [quantity]";
        
        int id = Integer.parseInt(parts[1]);
        int qty = Integer.parseInt(parts[2]);

        int ok = items.addToCart(id, qty);
        if(ok == 0)       return "Error: Producto no encontrado";
        else if(ok == -1) return "Error: El producto no cuenta con suficientes existencias";
        else {
            int curr = cart.getOrDefault(id, 0);
            cart.put(id, curr + qty);
            return "Producto agregado correctamente al carrito!";
        }
    }

    // Maneja el comando de ver el carrito
    private String handleView() {
        if(cart.isEmpty())
            return "¡El carrito está vacío!";
        
        StringBuilder sb = new StringBuilder(String.format("%d producto%s en el carrito\n",
                    cart.size(),
                    cart.size() == 1 ? "" : "s")
        );
        for(Map.Entry<Integer, Integer> entry : cart.entrySet()) {
            Product p = items.findById(entry.getKey());
            sb.append(String.format("#%d - %s (x%d)\n", entry.getKey(), p.getName(), entry.getValue()));
        }
        return sb.toString().trim();
    }

    // Maneja el comando de actualizar el carrito
    private String handleUpdate(String[] parts) {
        if(parts.length != 3)
            return "Error: UPDATE [ID] [qty]";
        if(cart.isEmpty())
            return "El carrito está vacío";
        
        int id = Integer.parseInt(parts[1]);
        int qty = Integer.parseInt(parts[2]);

        if(!cart.containsKey(id))
            return "Error: Producto no se encuentra en el carrito!";
        
        if(qty <= 0) {
            items.returnFromCart(id, cart.get(id));
            cart.remove(id);
            return "Producto eliminado del carrito!";
        }
        else {
            int curr = cart.getOrDefault(id, 0);
            int ok = items.addToCart(id, qty - curr);
            if (ok == -1) {
                int mn = items.findById(id).getStock();
                items.addToCart(id, mn);
                cart.put(id, curr + mn);
                return "No hay suficientes existencias, se agregaron " + mn + " al carrito!";
            }
            cart.put(id, qty);
            return "Cantidades agregadas correctamente!";
        }
    }

    // Maneja el comando para finalizar la compra
    private String handleCheckout() {
        if(cart.isEmpty())
            return "Error: El carrito está vacío";

        String datetime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        StringBuilder sb = new StringBuilder();
        sb.append("================================================\n");
        sb.append("TICKET\n");
        sb.append(datetime).append("\n");
        sb.append("------------------------------------------------\n");

        // Cabecera de la tabla
        sb.append(String.format("%-4s | %-25s | %-3s | %-8s\n", "ID", "Producto", "Qty", "Subtotal"));
        sb.append("------------------------------------------------\n");

        double total = 0.0;

        for(Map.Entry<Integer, Integer> entry : cart.entrySet()) {
            Product p = items.findById(entry.getKey());
            double curr = p.getPrice() * entry.getValue();
            sb.append(String.format("%-4d | %-25s | %-3d | $%-7.2f\n", p.getId(), p.getName(), entry.getValue(), curr));
            total += curr;
        }

        sb.append("------------------------------------------------\n");
        sb.append(String.format("%-35s | $%-7.2f\n", "TOTAL", total));
        sb.append("================================================\n");
        cart.clear();

        return sb.toString();
    }
}