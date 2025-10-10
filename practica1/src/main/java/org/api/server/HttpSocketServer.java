package org.api.server;

import org.cli.server.Items;
import org.cli.server.Product;
import org.cli.server.BusinessLogic;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Servidor HTTP implementado sobre Sockets TCP (java.net.Socket)
 *
 * ARQUITECTURA DE RED:
 * - Capa de Aplicación: HTTP (implementado manualmente)
 * - Capa de Transporte: TCP (ServerSocket/Socket de Java)
 * - No usa frameworks como Spring que ocultan los sockets
 *
 * El servidor maneja:
 * 1. Creación y gestión de sockets TCP
 * 2. Parseo manual del protocolo HTTP
 * 3. Construcción manual de respuestas HTTP
 * 4. Manejo concurrente de múltiples clientes
 */
public class HttpSocketServer {
    private static final int PORT = 8081;
    private static final ObjectMapper mapper = new ObjectMapper();
    private static Items items;
    // Mapa de carritos por sesión (Session-Id del cliente)
    private static final Map<String, Map<Integer, Integer>> carts = new HashMap<>();

    public static void main(String[] args) throws Exception {
        // Cargar inventario desde archivo JSON
        items = new Items("/plants.json");

        // ═══════════════════════════════════════════════════════════
        // SOCKET NIVEL TRANSPORTE (TCP) - Modelo OSI Capa 4
        // ═══════════════════════════════════════════════════════════
        // ServerSocket: Crea un socket de servidor que escucha conexiones TCP entrantes
        // El socket se vincula (bind) al puerto 8081 en todas las interfaces de red
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("HTTP API running on port " + PORT + " (blocking sockets)");

            // Bucle infinito: el servidor acepta conexiones continuamente
            while (true) {
                // ACCEPT: Operación BLOQUEANTE que espera una conexión TCP
                // Cuando un cliente conecta (navegador haciendo HTTP request):
                // 1. Se completa el three-way handshake TCP (SYN, SYN-ACK, ACK)
                // 2. Se crea un nuevo Socket para comunicarse con ese cliente específico
                // 3. El ServerSocket sigue escuchando en el puerto 8081
                Socket clientSocket = serverSocket.accept();

                // MANEJO CONCURRENTE: Cada cliente se maneja en un thread separado
                // Esto permite atender múltiples clientes simultáneamente
                // El socket del cliente se pasa al nuevo thread
                new Thread(() -> handleClient(clientSocket)).start();
            }
        }
    }

    /**
     * Maneja la comunicación con UN cliente específico
     * Este método se ejecuta en un thread separado por cada conexión
     *
     * @param socket Socket TCP ya conectado con el cliente
     */
    private static void handleClient(Socket socket) {
        // ═══════════════════════════════════════════════════════════
        // STREAMS DEL SOCKET: Lectura y escritura de bytes
        // ═══════════════════════════════════════════════════════════
        // BufferedReader: Para leer datos del cliente (del InputStream del socket)
        // OutputStream: Para enviar datos al cliente
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             OutputStream out = socket.getOutputStream()) {

            // PARSEO MANUAL DEL PROTOCOLO HTTP
            // Lee los bytes del socket y los interpreta como HTTP request
            HttpRequest request = parseHttpRequest(in);
            if (request == null) {
                sendError(out, 400, "Bad Request");
                return;
            }

            // LÓGICA DE NEGOCIO: Procesa la petición
            HttpResponse response = routeRequest(request);

            // CONSTRUCCIÓN MANUAL DE LA RESPUESTA HTTP
            // Escribe los bytes al socket siguiendo el formato HTTP
            sendHttpResponse(out, response);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // CIERRE DEL SOCKET: Libera los recursos de red
            // Envía FIN al cliente (cierre graceful de TCP)
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    /**
     * PARSEO MANUAL DEL PROTOCOLO HTTP
     * Lee bytes del socket y los interpreta línea por línea según RFC 2616 (HTTP/1.1)
     *
     * Formato HTTP Request:
     * GET /api/products HTTP/1.1\r\n          <- Request line
     * Host: localhost:8081\r\n                <- Headers
     * Content-Type: application/json\r\n
     * Session-Id: user-123\r\n
     * \r\n                                     <- Línea vacía
     * {"id":101,"quantity":2}                 <- Body (opcional)
     */
    private static HttpRequest parseHttpRequest(BufferedReader in) throws IOException {
        // LEE PRIMERA LÍNEA DEL SOCKET: Request Line
        // Ejemplo: "GET /api/products HTTP/1.1"
        String requestLine = in.readLine();
        if (requestLine == null || requestLine.isEmpty()) return null;

        // PARSEA REQUEST LINE: Método, Path, Versión HTTP
        String[] parts = requestLine.split(" ");
        if (parts.length < 3) return null;

        HttpRequest req = new HttpRequest();
        req.method = parts[0];  // GET, POST, PUT, DELETE, OPTIONS

        // PARSEA PATH Y QUERY STRING
        // Ejemplo: "/api/products?type=Interior" -> path="/api/products", query="type=Interior"
        String fullPath = parts[1];
        int queryIndex = fullPath.indexOf('?');
        if (queryIndex != -1) {
            req.path = fullPath.substring(0, queryIndex);
            req.query = fullPath.substring(queryIndex + 1);
        } else {
            req.path = fullPath;
        }

        // LEE HEADERS DEL SOCKET línea por línea
        // Los headers terminan con una línea vacía (\r\n)
        String line;
        while ((line = in.readLine()) != null && !line.isEmpty()) {
            int colonIndex = line.indexOf(':');
            if (colonIndex > 0) {
                // Parsea "Header-Name: value"
                String key = line.substring(0, colonIndex).trim().toLowerCase();
                String value = line.substring(colonIndex + 1).trim();
                req.headers.put(key, value);
            }
        }

        // LEE BODY DEL SOCKET (solo para POST/PUT)
        // El tamaño del body viene en el header "Content-Length"
        if ("POST".equals(req.method) || "PUT".equals(req.method)) {
            String contentLength = req.headers.get("content-length");
            if (contentLength != null) {
                int length = Integer.parseInt(contentLength);
                // Lee exactamente 'length' bytes del socket
                char[] bodyChars = new char[length];
                in.read(bodyChars, 0, length);
                req.body = new String(bodyChars);
            }
        }
        return req;
    }

    /**
     * ENRUTAMIENTO: Decide qué hacer según el path del HTTP request
     */
    private static HttpResponse routeRequest(HttpRequest req) {
        // CORS: Permite peticiones desde navegadores de otros orígenes
        if ("OPTIONS".equals(req.method)) {
            return new HttpResponse(204, "", "text/plain");
        }

        try {
            // Manejo de imágenes estáticas
            if (req.path.startsWith("/images/")) return handleImages(req);

            // API REST endpoints
            return switch (req.path) {
                case "/api/products" -> handleProducts(req);
                case "/api/search" -> handleSearch(req);
                case "/api/cart" -> handleCart(req);
                case "/api/checkout" -> handleCheckout(req);
                default -> new HttpResponse(404, "{\"error\":\"Not Found\"}", "application/json");
            };
        } catch (Exception e) {
            e.printStackTrace();
            return new HttpResponse(500, "{\"error\":\"Internal error\"}", "application/json");
        }
    }

    /**
     * ENDPOINT: GET /api/products o GET /api/products?type=Interior
     * Retorna lista de productos en formato JSON
     */
    private static HttpResponse handleProducts(HttpRequest req) throws Exception {
        if (!"GET".equals(req.method))
            return new HttpResponse(405, "{\"error\":\"Method not allowed\"}", "application/json");

        // IDENTIFICACIÓN DE SESIÓN: Cada cliente tiene su propio carrito
        // El navegador envía un header "Session-Id" para identificarse
        String sessionId = req.headers.getOrDefault("session-id", "default");
        Map<Integer, Integer> cart = carts.computeIfAbsent(sessionId, k -> new TreeMap<>());
        BusinessLogic logic = new BusinessLogic(items, cart);

        // FILTRADO: Por tipo de producto si se especifica en query string
        Map<Integer, Product> products;
        if (req.query != null && req.query.startsWith("type=")) {
            String type = URLDecoder.decode(req.query.substring(5), StandardCharsets.UTF_8);
            products = logic.listProductsByType(type);
        } else {
            products = logic.listAllProducts();
        }

        // SERIALIZACIÓN A JSON: Convierte objetos Java a JSON
        ArrayNode jsonArray = mapper.createArrayNode();
        for (Product p : products.values()) jsonArray.add(productToJson(p));
        return new HttpResponse(200, mapper.writeValueAsString(jsonArray), "application/json");
    }

    /**
     * ENDPOINT: GET /api/search?q=cactus
     * Busca productos por nombre, marca o ID
     */
    private static HttpResponse handleSearch(HttpRequest req) throws Exception {
        if (!"GET".equals(req.method))
            return new HttpResponse(405, "{\"error\":\"Method not allowed\"}", "application/json");
        if (req.query == null || !req.query.startsWith("q="))
            return new HttpResponse(400, "{\"error\":\"Missing query\"}", "application/json");

        String sessionId = req.headers.getOrDefault("session-id", "default");
        Map<Integer, Integer> cart = carts.computeIfAbsent(sessionId, k -> new TreeMap<>());
        BusinessLogic logic = new BusinessLogic(items, cart);

        // DECODIFICACIÓN URL: "cactus%20espiral" -> "cactus espiral"
        String term = URLDecoder.decode(req.query.substring(2), StandardCharsets.UTF_8);
        Map<Integer, Product> results = logic.searchProducts(term);

        ArrayNode jsonArray = mapper.createArrayNode();
        for (Product p : results.values()) jsonArray.add(productToJson(p));
        return new HttpResponse(200, mapper.writeValueAsString(jsonArray), "application/json");
    }

    /**
     * ENDPOINT: /api/cart
     * GET: Ver carrito
     * POST: Agregar producto
     * PUT: Actualizar cantidad
     */
    private static HttpResponse handleCart(HttpRequest req) throws Exception {
        String sessionId = req.headers.getOrDefault("session-id", "default");
        Map<Integer, Integer> cart = carts.computeIfAbsent(sessionId, k -> new TreeMap<>());
        BusinessLogic logic = new BusinessLogic(items, cart);

        return switch (req.method) {
            case "GET" -> handleGetCart(logic);
            case "POST" -> handleAddToCart(req, logic);
            case "PUT" -> handleUpdateCart(req, logic);
            default -> new HttpResponse(405, "{\"error\":\"Method not allowed\"}", "application/json");
        };
    }

    /**
     * GET /api/cart: Retorna contenido del carrito
     */
    private static HttpResponse handleGetCart(BusinessLogic logic) throws Exception {
        Map<Integer, Integer> cart = logic.getCart();
        ArrayNode jsonArray = mapper.createArrayNode();

        for (Map.Entry<Integer, Integer> entry : cart.entrySet()) {
            Product p = logic.getProductById(entry.getKey());
            if (p != null) {
                ObjectNode item = productToJson(p);
                item.put("quantity", entry.getValue());
                jsonArray.add(item);
            }
        }
        return new HttpResponse(200, mapper.writeValueAsString(jsonArray), "application/json");
    }

    /**
     * POST /api/cart: Agregar producto al carrito
     * Body: {"id":101,"quantity":2}
     */
    private static HttpResponse handleAddToCart(HttpRequest req, BusinessLogic logic) throws Exception {
        // DESERIALIZACIÓN JSON: Convierte JSON del body a objeto Java
        Map<String, Object> data = mapper.readValue(req.body, Map.class);
        int id = (int) data.get("id");
        int qty = (int) data.get("quantity");

        BusinessLogic.AddToCartResult result = logic.addToCart(id, qty);

        if (!result.success) {
            int statusCode = result.message.contains("no encontrado") ? 404 : 400;
            return new HttpResponse(statusCode,
                    "{\"error\":\"" + result.message + "\"}",
                    "application/json");
        }

        return new HttpResponse(200,
                "{\"success\":true,\"message\":\"" + result.message + "\"}",
                "application/json");
    }

    /**
     * PUT /api/cart: Actualizar cantidad de producto en carrito
     * Body: {"id":101,"quantity":5}
     */
    private static HttpResponse handleUpdateCart(HttpRequest req, BusinessLogic logic) throws Exception {
        Map<String, Object> data = mapper.readValue(req.body, Map.class);
        int id = (int) data.get("id");
        int qty = (int) data.get("quantity");

        BusinessLogic.UpdateCartResult result = logic.updateCart(id, qty);

        if (!result.success && result.message.contains("no se encuentra")) {
            return new HttpResponse(404,
                    "{\"error\":\"" + result.message + "\"}",
                    "application/json");
        }

        return new HttpResponse(200,
                "{\"success\":true,\"message\":\"" + result.message + "\"}",
                "application/json");
    }

    /**
     * POST /api/checkout: Finalizar compra
     * Retorna un ticket con los productos comprados
     */
    private static HttpResponse handleCheckout(HttpRequest req) throws Exception {
        if (!"POST".equals(req.method))
            return new HttpResponse(405, "{\"error\":\"Method not allowed\"}", "application/json");

        String sessionId = req.headers.getOrDefault("session-id", "default");
        Map<Integer, Integer> cart = carts.get(sessionId);

        if (cart == null || cart.isEmpty())
            return new HttpResponse(400, "{\"error\":\"Cart is empty\"}", "application/json");

        BusinessLogic logic = new BusinessLogic(items, cart);
        BusinessLogic.CheckoutResult result = logic.checkout();

        if (!result.success) {
            return new HttpResponse(400,
                    "{\"error\":\"" + result.message + "\"}",
                    "application/json");
        }

        // CONSTRUCCIÓN DEL TICKET EN JSON
        ObjectNode receipt = mapper.createObjectNode();
        receipt.put("datetime", result.datetime);

        ArrayNode itemsArray = mapper.createArrayNode();
        for (BusinessLogic.CheckoutItem item : result.items) {
            ObjectNode itemNode = mapper.createObjectNode();
            itemNode.put("id", item.id);
            itemNode.put("name", item.name);
            itemNode.put("quantity", item.quantity);
            itemNode.put("price", item.price);
            itemNode.put("subtotal", item.subtotal);
            itemsArray.add(itemNode);
        }

        receipt.set("items", itemsArray);
        receipt.put("total", result.total);

        return new HttpResponse(200, mapper.writeValueAsString(receipt), "application/json");
    }

    /**
     * MANEJO DE ARCHIVOS BINARIOS: Imágenes de productos
     * GET /images/cactus.jpg
     */
    private static HttpResponse handleImages(HttpRequest req) throws Exception {
        String filename = req.path.substring(req.path.lastIndexOf('/') + 1);
        InputStream is = HttpSocketServer.class.getResourceAsStream("/images/" + filename);

        if (is == null) return new HttpResponse(404, "Not Found", "text/plain");

        // LEE ARCHIVO BINARIO completo
        byte[] bytes = is.readAllBytes();
        is.close();

        // DETERMINA MIME TYPE según extensión
        String contentType = filename.endsWith(".png") ? "image/png" :
                filename.endsWith(".gif") ? "image/gif" : "image/jpeg";

        HttpResponse response = new HttpResponse(200, "", contentType);
        response.binaryData = bytes;
        return response;
    }

    /**
     * CONSTRUCCIÓN MANUAL DE LA RESPUESTA HTTP
     * Escribe bytes al socket siguiendo el formato HTTP/1.1 (RFC 2616)
     *
     * Formato HTTP Response:
     * HTTP/1.1 200 OK\r\n                      <- Status line
     * Content-Type: application/json\r\n       <- Headers
     * Content-Length: 123\r\n
     * Access-Control-Allow-Origin: *\r\n
     * \r\n                                      <- Línea vacía
     * {"id":101,"name":"Cactus"}              <- Body
     */
    private static void sendHttpResponse(OutputStream out, HttpResponse response) throws IOException {
        // PrintWriter: Para escribir texto al socket
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8), false);

        // STATUS LINE: "HTTP/1.1 200 OK"
        writer.write("HTTP/1.1 " + response.statusCode + " " + getStatusText(response.statusCode) + "\r\n");

        // HEADERS CORS: Permiten peticiones desde navegadores de otros orígenes
        writer.write("Access-Control-Allow-Origin: *\r\n");
        writer.write("Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS\r\n");
        writer.write("Access-Control-Allow-Headers: Content-Type, Session-Id\r\n");

        // HEADER Content-Type: Indica formato del body
        writer.write("Content-Type: " + response.contentType + "\r\n");

        // ENVÍA BODY: Puede ser texto (JSON) o binario (imagen)
        if (response.binaryData != null) {
            // DATOS BINARIOS (imágenes)
            writer.write("Content-Length: " + response.binaryData.length + "\r\n");
            writer.write("\r\n");  // Línea vacía = fin de headers
            writer.flush();
            out.write(response.binaryData);  // Escribe bytes al socket
            out.flush();
        } else {
            // DATOS DE TEXTO (JSON)
            byte[] bodyBytes = response.body.getBytes(StandardCharsets.UTF_8);
            writer.write("Content-Length: " + bodyBytes.length + "\r\n");
            writer.write("\r\n");  // Línea vacía = fin de headers
            writer.write(response.body);
            writer.flush();
        }
    }

    /**
     * Envía respuesta HTTP de error
     */
    private static void sendError(OutputStream out, int code, String message) throws IOException {
        HttpResponse response = new HttpResponse(code, "{\"error\":\"" + message + "\"}", "application/json");
        sendHttpResponse(out, response);
    }

    /**
     * CÓDIGOS DE ESTADO HTTP (RFC 2616)
     * 2xx = Éxito
     * 4xx = Error del cliente
     * 5xx = Error del servidor
     */
    private static String getStatusText(int code) {
        return switch (code) {
            case 200 -> "OK";
            case 204 -> "No Content";
            case 400 -> "Bad Request";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            case 500 -> "Internal Server Error";
            default -> "Unknown";
        };
    }

    /**
     * Convierte un objeto Product a JSON
     */
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

    /**
     * ESTRUCTURA DE DATOS: Representa un HTTP Request parseado
     */
    static class HttpRequest {
        String method;       // GET, POST, PUT, DELETE, OPTIONS
        String path;         // /api/products
        String query;        // type=Interior (sin el ?)
        String body = "";    // {"id":101,"quantity":2}
        Map<String, String> headers = new HashMap<>();  // Header-Name -> value
    }

    /**
     * ESTRUCTURA DE DATOS: Representa un HTTP Response a construir
     */
    static class HttpResponse {
        int statusCode;         // 200, 404, 500, etc.
        String body;            // Texto (JSON) del body
        String contentType;     // application/json, image/png, etc.
        byte[] binaryData;      // Datos binarios (imágenes)

        HttpResponse(int statusCode, String body, String contentType) {
            this.statusCode = statusCode;
            this.body = body;
            this.contentType = contentType;
        }
    }
}