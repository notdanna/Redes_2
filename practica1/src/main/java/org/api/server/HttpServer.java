package org.api.server;

import com.sun.net.httpserver.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.cli.server.Items;
import org.cli.server.Product;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class HttpServer {
    private static final int PORT = 8080;
    private static final ObjectMapper mapper = new ObjectMapper();
    private static Items items;

    // Almacenamiento de carritos por sesión (sessionId -> carrito)
    private static final Map<String, Map<Integer, Integer>> carts = new HashMap<>();

    public static void main(String[] args) throws Exception {
        items = new Items("/plants.json");

        com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(
                new InetSocketAddress(PORT), 0
        );

        // Endpoints
        server.createContext("/api/products", new ProductsHandler());
        server.createContext("/api/search", new SearchHandler());
        server.createContext("/api/cart", new CartHandler());
        server.createContext("/api/checkout", new CheckoutHandler());
        server.createContext("/images", new ImagesHandler());

        server.setExecutor(null);
        server.start();
        System.out.println("Garden Center API running on http://localhost:" + PORT);
    }

    // GET /api/products o GET /api/products?type=X
    static class ProductsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"GET".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }

            try {
                String query = exchange.getRequestURI().getQuery();
                Map<Integer, Product> products;

                if (query != null && query.startsWith("type=")) {
                    String type = java.net.URLDecoder.decode(query.substring(5), StandardCharsets.UTF_8);
                    products = items.findByType(type);
                } else {
                    products = items.getItems();
                }

                ArrayNode jsonArray = mapper.createArrayNode();
                for (Product p : products.values()) {
                    jsonArray.add(productToJson(p));
                }

                sendJson(exchange, 200, jsonArray);
            } catch (Exception e) {
                e.printStackTrace();
                sendError(exchange, 500, "Internal server error: " + e.getMessage());
            }
        }
    }

    // GET /api/search?q=nombre
    static class SearchHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"GET".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }

            try {
                String query = exchange.getRequestURI().getQuery();
                if (query == null || !query.startsWith("q=")) {
                    sendError(exchange, 400, "Missing query parameter 'q'");
                    return;
                }

                String searchTerm = java.net.URLDecoder.decode(query.substring(2), StandardCharsets.UTF_8);
                Map<Integer, Product> results = items.find(searchTerm);

                ArrayNode jsonArray = mapper.createArrayNode();
                for (Product p : results.values()) {
                    jsonArray.add(productToJson(p));
                }

                sendJson(exchange, 200, jsonArray);
            } catch (Exception e) {
                e.printStackTrace();
                sendError(exchange, 500, "Internal server error: " + e.getMessage());
            }
        }
    }

    // GET /api/cart, POST /api/cart, PUT /api/cart
    static class CartHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            String sessionId = getSessionId(exchange);
            Map<Integer, Integer> cart = carts.computeIfAbsent(sessionId, k -> new TreeMap<>());

            try {
                switch (exchange.getRequestMethod()) {
                    case "GET" -> handleGetCart(exchange, cart);
                    case "POST" -> handleAddToCart(exchange, cart);
                    case "PUT" -> handleUpdateCart(exchange, cart);
                    default -> sendError(exchange, 405, "Method not allowed");
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendError(exchange, 500, "Internal server error: " + e.getMessage());
            }
        }

        private void handleGetCart(HttpExchange exchange, Map<Integer, Integer> cart) throws IOException {
            ArrayNode jsonArray = mapper.createArrayNode();
            for (Map.Entry<Integer, Integer> entry : cart.entrySet()) {
                Product p = items.findById(entry.getKey());
                if (p != null) {
                    ObjectNode item = productToJson(p);
                    item.put("quantity", entry.getValue());
                    jsonArray.add(item);
                }
            }
            sendJson(exchange, 200, jsonArray);
        }

        private void handleAddToCart(HttpExchange exchange, Map<Integer, Integer> cart) throws IOException {
            String body = readBody(exchange);
            Map<String, Object> data = mapper.readValue(body, Map.class);

            int id = (int) data.get("id");
            int qty = (int) data.get("quantity");

            int result = items.addToCart(id, qty);

            ObjectNode response = mapper.createObjectNode();
            if (result == 0) {
                sendError(exchange, 404, "Product not found");
            } else if (result == -1) {
                sendError(exchange, 400, "Insufficient stock");
            } else {
                int current = cart.getOrDefault(id, 0);
                cart.put(id, current + qty);
                response.put("success", true);
                response.put("message", "Product added to cart");
                sendJson(exchange, 200, response);
            }
        }

        private void handleUpdateCart(HttpExchange exchange, Map<Integer, Integer> cart) throws IOException {
            String body = readBody(exchange);
            Map<String, Object> data = mapper.readValue(body, Map.class);

            int id = (int) data.get("id");
            int qty = (int) data.get("quantity");

            ObjectNode response = mapper.createObjectNode();

            if (!cart.containsKey(id)) {
                sendError(exchange, 404, "Product not in cart");
                return;
            }

            if (qty <= 0) {
                items.returnFromCart(id, cart.get(id));
                cart.remove(id);
                response.put("success", true);
                response.put("message", "Product removed from cart");
            } else {
                int current = cart.get(id);
                int result = items.addToCart(id, qty - current);

                if (result == -1) {
                    sendError(exchange, 400, "Insufficient stock");
                    return;
                }

                cart.put(id, qty);
                response.put("success", true);
                response.put("message", "Cart updated");
            }

            sendJson(exchange, 200, response);
        }
    }

    // POST /api/checkout
    static class CheckoutHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"POST".equals(exchange.getRequestMethod())) {
                sendError(exchange, 405, "Method not allowed");
                return;
            }

            String sessionId = getSessionId(exchange);
            Map<Integer, Integer> cart = carts.get(sessionId);

            if (cart == null || cart.isEmpty()) {
                sendError(exchange, 400, "Cart is empty");
                return;
            }

            try {
                ObjectNode receipt = mapper.createObjectNode();
                receipt.put("datetime", LocalDateTime.now().format(
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                ));

                ArrayNode itemsArray = mapper.createArrayNode();
                double total = 0.0;

                for (Map.Entry<Integer, Integer> entry : cart.entrySet()) {
                    Product p = items.findById(entry.getKey());
                    double subtotal = p.getPrice() * entry.getValue();
                    total += subtotal;

                    ObjectNode item = mapper.createObjectNode();
                    item.put("id", p.getId());
                    item.put("name", p.getName());
                    item.put("quantity", entry.getValue());
                    item.put("price", p.getPrice());
                    item.put("subtotal", subtotal);
                    itemsArray.add(item);
                }

                receipt.set("items", itemsArray);
                receipt.put("total", total);

                cart.clear();
                sendJson(exchange, 200, receipt);
            } catch (Exception e) {
                e.printStackTrace();
                sendError(exchange, 500, "Internal server error: " + e.getMessage());
            }
        }
    }

    // Servir imágenes
    static class ImagesHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String filename = path.substring(path.lastIndexOf('/') + 1);

            InputStream is = null;
            OutputStream os = null;

            try {
                is = ImagesHandler.class.getResourceAsStream("/images/" + filename);

                if (is == null) {
                    System.err.println("Image not found: /images/" + filename);
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }

                byte[] bytes = is.readAllBytes();

                // Determina el tipo de contenido
                String contentType = "image/jpeg";
                if (filename.endsWith(".png")) {
                    contentType = "image/png";
                } else if (filename.endsWith(".gif")) {
                    contentType = "image/gif";
                } else if (filename.endsWith(".webp")) {
                    contentType = "image/webp";
                }

                exchange.getResponseHeaders().add("Content-Type", contentType);
                exchange.getResponseHeaders().add("Cache-Control", "public, max-age=31536000");
                exchange.sendResponseHeaders(200, bytes.length);

                os = exchange.getResponseBody();
                os.write(bytes);
                os.flush();

            } catch (Exception e) {
                e.printStackTrace();
                if (!exchange.getResponseHeaders().containsKey("Content-Type")) {
                    exchange.sendResponseHeaders(500, -1);
                }
            } finally {
                if (is != null) {
                    try { is.close(); } catch (IOException ignored) {}
                }
                if (os != null) {
                    try { os.close(); } catch (IOException ignored) {}
                }
            }
        }
    }

    // Utilidades
    private static ObjectNode productToJson(Product p) {
        ObjectNode node = mapper.createObjectNode();
        node.put("id", p.getId());
        node.put("name", p.getName());
        node.put("type", p.getType());
        node.put("brand", p.getBrand());
        node.put("info", p.getInfo());
        node.put("price", p.getPrice());
        node.put("stock", p.getStock());
        node.put("imageUrl", p.getImageUrl());
        return node;
    }

    private static void addCorsHeaders(HttpExchange exchange) {
        Headers headers = exchange.getResponseHeaders();
        headers.add("Access-Control-Allow-Origin", "*");
        headers.add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        headers.add("Access-Control-Allow-Headers", "Content-Type, Session-Id");
    }

    private static String getSessionId(HttpExchange exchange) {
        Headers headers = exchange.getRequestHeaders();
        List<String> sessionHeader = headers.get("Session-Id");
        return (sessionHeader != null && !sessionHeader.isEmpty())
                ? sessionHeader.get(0)
                : "default-session";
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        is.close();
        return body;
    }

    private static void sendJson(HttpExchange exchange, int statusCode, Object data) throws IOException {
        String json = mapper.writeValueAsString(data);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);

        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.flush();
        os.close();
    }

    private static void sendError(HttpExchange exchange, int statusCode, String message) throws IOException {
        ObjectNode error = mapper.createObjectNode();
        error.put("error", message);
        sendJson(exchange, statusCode, error);
    }
}