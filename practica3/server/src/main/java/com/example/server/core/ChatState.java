package com.example.server.core;

import org.springframework.web.socket.WebSocketSession;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChatState {

    /* Un ConcurrentHashMap

    Permite que varios hilos lean/escriban en el mapa al mismo tiempo.
    Evita bloqueos en operaciones. ¿Como un semaforo?
    - Cada request HTTP puede ejecutarse en un hilo distinto.
    - Tareas async (@Async), schedulers o listeners pueden acceder al mismo mapa.
    - Manejo de sockets (UDP en tu caso) suele ser multihilo.
     */
    // Nombres de usuario
    private final Map<String, InetSocketAddress> udpUsers = new ConcurrentHashMap<>();

    // Para mapear los web sockets
    private final Map<WebSocketSession, String> wsUsers = new ConcurrentHashMap<>();

    // Nombres de las salas
    private final Map<String, Set<String>> rooms = new ConcurrentHashMap<>();

    // Usa la dirección del usuario UDP para registrar
    public void registerUdpUser(String username, InetSocketAddress address) {
        udpUsers.put(username, address);
    }

    // Obtener la dirección del usuario UDP
    public InetSocketAddress getUdpUser(String username) {
        return udpUsers.get(username);
    }

    // Elimina un usuario y lo saca de todas las salas(rooms)
    public void removeUdpUser(String username) {
        udpUsers.remove(username);
        leaveAllRooms(username);
    }

    public Set<String> allUdpUsernames() {
        return udpUsers.keySet();
    }

    // Para meter a un usuario(su nombre) a una sesión
    public void registerSession(WebSocketSession session, String username) {
        wsUsers.put(session, username);
    }

    // Elimina una sesión y retorna su nombre(para saber quien salió)
    public String removeSession(WebSocketSession session) {
        return wsUsers.remove(session);
    }

    // Obtiene nombre de usuario de una sesión
    public String usernameOf(WebSocketSession session) {
        return wsUsers.get(session);
    }

    public Set<WebSocketSession> allSessions() {
        return wsUsers.keySet();
    }

    // Busca sesión de un usuario específico
    public Optional<WebSocketSession> sessionOfUser(String username) {
        return wsUsers.entrySet()
                .stream()
                .filter(e -> username.equals(e.getValue()))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    public void createRoom(String room) {
        rooms.computeIfAbsent(room, r -> ConcurrentHashMap.newKeySet());
    }

    public Set<String> roomNames() {
        return rooms.keySet();
    }

    public void joinRoom(String username, String room) {
        createRoom(room);
        rooms.get(room).add(username);
    }

    public void leaveRoom(String username, String room) {
        Set<String> set = rooms.get(room);
        if (set != null) {
            set.remove(username);
            if (set.isEmpty()) {
                rooms.remove(room);
            }
        }
    }

    public void leaveAllRooms(String username) {
        rooms.forEach((room, set) -> set.remove(username));
        rooms.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    public Set<String> usersOfRoom(String room) {
        return rooms.getOrDefault(room, Set.of());
    }

    public List<String> usersInRoom(String room) {
        return new ArrayList<>(usersOfRoom(room));
    }

    public List<String> roomsOfUser(String username) {
        List<String> result = new ArrayList<>();
        rooms.forEach((room, set) -> {
            if (set.contains(username)) {
                result.add(room);
            }
        });
        return result;
    }

    // Retorna Set de sesiones WebSocket de usuarios en sala
    public Set<WebSocketSession> sessionsInRoom(String room) {
        Set<String> users = usersOfRoom(room);
        Set<WebSocketSession> result = new HashSet<>();
        wsUsers.forEach((session, username) -> {
            if (users.contains(username)) {
                result.add(session);
            }
        });
        return result;
    }
}
