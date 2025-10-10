package org.cli.server;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Lógica de negocio compartida entre el servidor CLI y la API HTTP
 */
public class BusinessLogic {
    private final Items items;
    private final Map<Integer, Integer> cart;

    public BusinessLogic(Items items, Map<Integer, Integer> cart) {
        this.items = items;
        this.cart = cart;
    }

    // Buscar productos por nombre, marca o ID
    public Map<Integer, Product> searchProducts(String query) {
        return items.find(query);
    }

    // Listar todos los productos
    public Map<Integer, Product> listAllProducts() {
        return items.getItems();
    }

    // Listar productos por tipo
    public Map<Integer, Product> listProductsByType(String type) {
        return items.findByType(type);
    }

    // Agregar producto al carrito
    public AddToCartResult addToCart(int id, int quantity) {
        int result = items.addToCart(id, quantity);

        if (result == 0) {
            return new AddToCartResult(false, "Producto no encontrado", null);
        }
        if (result == -1) {
            return new AddToCartResult(false, "El producto no cuenta con suficientes existencias", null);
        }

        int current = cart.getOrDefault(id, 0);
        cart.put(id, current + quantity);
        return new AddToCartResult(true, "Producto agregado correctamente al carrito!", null);
    }

    // Ver carrito
    public Map<Integer, Integer> getCart() {
        return new TreeMap<>(cart);
    }

    // Actualizar cantidad en el carrito
    public UpdateCartResult updateCart(int id, int quantity) {
        if (!cart.containsKey(id)) {
            return new UpdateCartResult(false, "Producto no se encuentra en el carrito!", null);
        }

        if (quantity <= 0) {
            items.returnFromCart(id, cart.get(id));
            cart.remove(id);
            return new UpdateCartResult(true, "Producto eliminado del carrito!", null);
        }

        int current = cart.getOrDefault(id, 0);
        int result = items.addToCart(id, quantity - current);

        if (result == -1) {
            int available = items.findById(id).getStock();
            items.addToCart(id, available);
            cart.put(id, current + available);
            return new UpdateCartResult(false,
                    "No hay suficientes existencias, se agregaron " + available + " al carrito!",
                    available);
        }

        cart.put(id, quantity);
        return new UpdateCartResult(true, "Cantidades actualizadas correctamente!", null);
    }

    // Realizar checkout
    public CheckoutResult checkout() {
        if (cart.isEmpty()) {
            return new CheckoutResult(false, "El carrito está vacío", null, null, 0.0);
        }

        String datetime = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        List<CheckoutItem> checkoutItems = new ArrayList<>();
        double total = 0.0;

        for (Map.Entry<Integer, Integer> entry : cart.entrySet()) {
            Product p = items.findById(entry.getKey());
            double subtotal = p.getPrice() * entry.getValue();
            total += subtotal;

            checkoutItems.add(new CheckoutItem(
                    p.getId(),
                    p.getName(),
                    entry.getValue(),
                    p.getPrice(),
                    subtotal
            ));
        }

        cart.clear();
        return new CheckoutResult(true, "Compra finalizada exitosamente",
                datetime, checkoutItems, total);
    }

    // Obtener producto por ID
    public Product getProductById(int id) {
        return items.findById(id);
    }

    // Clases de resultado
    public static class AddToCartResult {
        public final boolean success;
        public final String message;
        public final Integer newQuantity;

        public AddToCartResult(boolean success, String message, Integer newQuantity) {
            this.success = success;
            this.message = message;
            this.newQuantity = newQuantity;
        }
    }

    public static class UpdateCartResult {
        public final boolean success;
        public final String message;
        public final Integer actualQuantity;

        public UpdateCartResult(boolean success, String message, Integer actualQuantity) {
            this.success = success;
            this.message = message;
            this.actualQuantity = actualQuantity;
        }
    }

    public static class CheckoutResult {
        public final boolean success;
        public final String message;
        public final String datetime;
        public final List<CheckoutItem> items;
        public final double total;

        public CheckoutResult(boolean success, String message, String datetime,
                              List<CheckoutItem> items, double total) {
            this.success = success;
            this.message = message;
            this.datetime = datetime;
            this.items = items;
            this.total = total;
        }
    }

    public static class CheckoutItem {
        public final int id;
        public final String name;
        public final int quantity;
        public final double price;
        public final double subtotal;

        public CheckoutItem(int id, String name, int quantity, double price, double subtotal) {
            this.id = id;
            this.name = name;
            this.quantity = quantity;
            this.price = price;
            this.subtotal = subtotal;
        }
    }
}